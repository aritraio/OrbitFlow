package com.orbitflow.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DocumentsAttachmentsAndSearchIntegrationTest extends BaseIntegrationTest {

    @Test
    void documentRevisionRestoreAndSearchAndAttachmentLifecycle() throws Exception {
        var alice = register("alice.da@example.com", "alice_da");
        var ws = createWorkspace(alice.accessToken(), "Docs WS");
        var proj = createProject(alice.accessToken(), ws, "DC", "Docs Project");

        // create document
        var docRes = mockMvc.perform(post("/api/v1/projects/" + proj + "/documents")
                        .header("Authorization", auth(alice.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Spec\",\"body\":\"urgent launch plan v1\"}"))
                .andExpect(status().isCreated()).andReturn();
        String docId = objectMapper.readTree(docRes.getResponse().getContentAsString()).get("id").asText();

        // update -> revision 2
        mockMvc.perform(patch("/api/v1/documents/" + docId)
                        .header("Authorization", auth(alice.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Spec\",\"body\":\"urgent launch plan v2\"}"))
                .andExpect(status().isOk());

        // revisions list has 2
        mockMvc.perform(get("/api/v1/documents/" + docId + "/revisions")
                        .header("Authorization", auth(alice.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // restore v1
        mockMvc.perform(post("/api/v1/documents/" + docId + "/restore/1")
                        .header("Authorization", auth(alice.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("urgent launch plan v1"));

        // task for attachment + search
        var taskRes = mockMvc.perform(post("/api/v1/projects/" + proj + "/tasks")
                        .header("Authorization", auth(alice.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"urgent fix needed\"}"))
                .andExpect(status().isCreated()).andReturn();
        String taskId = objectMapper.readTree(taskRes.getResponse().getContentAsString()).get("id").asText();

        // attachment: reserve -> complete (inline bytes simulate direct S3 PUT) -> ACTIVE
        var reserveRes = mockMvc.perform(post("/api/v1/tasks/" + taskId + "/attachments/reserve")
                        .header("Authorization", auth(alice.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"notes.txt\",\"contentType\":\"text/plain\",\"sizeBytes\":11}"))
                .andExpect(status().isCreated()).andReturn();
        String attId = objectMapper.readTree(reserveRes.getResponse().getContentAsString()).get("attachmentId").asText();
        String b64 = java.util.Base64.getEncoder().encodeToString("hello world".getBytes());
        mockMvc.perform(post("/api/v1/attachments/" + attId + "/complete")
                        .header("Authorization", auth(alice.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentBase64\":\"" + b64 + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // download URL authorized
        mockMvc.perform(get("/api/v1/attachments/" + attId + "/download")
                        .header("Authorization", auth(alice.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.downloadUrl").exists());

        // search
        mockMvc.perform(get("/api/v1/search?q=urgent")
                        .header("Authorization", auth(alice.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }
}
