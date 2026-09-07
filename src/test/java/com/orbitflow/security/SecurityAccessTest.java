package com.orbitflow.security;

import com.orbitflow.integration.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SecurityAccessTest extends BaseIntegrationTest {

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/workspaces"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void guestCannotEnumerateBeyondSharedProject() throws Exception {
        var owner = register("owner.sec@example.com", "owner_sec");
        var guest = register("guest.sec@example.com", "guest_sec");
        var ws = createWorkspace(owner.accessToken(), "Sec WS");
        var proj = createProject(owner.accessToken(), ws, "SC", "Sec Project");

        // guest with no membership cannot access project
        mockMvc.perform(get("/api/v1/projects/" + proj)
                        .header("Authorization", auth(guest.accessToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void archivedProjectIsReadOnlyForTaskCreation() throws Exception {
        var alice = register("alice.sec@example.com", "alice_sec");
        var ws = createWorkspace(alice.accessToken(), "Arch WS");
        var proj = createProject(alice.accessToken(), ws, "AR", "Arch Project");

        mockMvc.perform(post("/api/v1/projects/" + proj + "/archive")
                        .header("Authorization", auth(alice.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"archived\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/projects/" + proj + "/tasks")
                        .header("Authorization", auth(alice.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Should fail\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedMarkdownIsSanitized() throws Exception {
        var alice = register("alice.xss@example.com", "alice_xss");
        var ws = createWorkspace(alice.accessToken(), "XSS WS");
        var proj = createProject(alice.accessToken(), ws, "XS", "XSS Project");
        var taskRes = mockMvc.perform(post("/api/v1/projects/" + proj + "/tasks")
                        .header("Authorization", auth(alice.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"xss\"}"))
                .andExpect(status().isCreated()).andReturn();
        String taskId = objectMapper.readTree(taskRes.getResponse().getContentAsString()).get("id").asText();

        var cRes = mockMvc.perform(post("/api/v1/tasks/" + taskId + "/comments")
                        .header("Authorization", auth(alice.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"<script>alert(1)</script> hello\"}"))
                .andExpect(status().isCreated()).andReturn();
        String html = objectMapper.readTree(cRes.getResponse().getContentAsString()).get("bodyHtml").asText();
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("<script>");
    }
}
