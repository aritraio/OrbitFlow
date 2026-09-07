package com.orbitflow.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class S3UploadTest extends BaseIntegrationTest {
    @Test
    void presignedUploadLifecycle() throws Exception {
        var alice = register("alice.s3@example.com", "alice_s3");
        var ws = createWorkspace(alice.accessToken(), "S3 WS");
        var proj = createProject(alice.accessToken(), ws, "S3", "S3 Project");
        var taskRes = mockMvc.perform(post("/api/v1/projects/" + proj + "/tasks")
                        .header("Authorization", auth(alice.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"file task\"}"))
                .andExpect(status().isCreated()).andReturn();
        String taskId = objectMapper.readTree(taskRes.getResponse().getContentAsString()).get("id").asText();

        var reserve = mockMvc.perform(post("/api/v1/tasks/" + taskId + "/attachments/reserve")
                        .header("Authorization", auth(alice.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"a.bin\",\"contentType\":\"application/octet-stream\",\"sizeBytes\":4}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uploadUrl").exists())
                .andReturn();
        String attId = objectMapper.readTree(reserve.getResponse().getContentAsString()).get("attachmentId").asText();

        String b64 = java.util.Base64.getEncoder().encodeToString(new byte[]{1, 2, 3, 4});
        mockMvc.perform(post("/api/v1/attachments/" + attId + "/complete")
                        .header("Authorization", auth(alice.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentBase64\":\"" + b64 + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }
}
