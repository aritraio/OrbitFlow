package com.orbitflow.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TenantIsolationTest extends BaseIntegrationTest {

    @Test
    void userBCannotReadOrAlterUserAWorkspace() throws Exception {
        var alice = register("alice.ti@example.com", "alice_ti");
        var bob = register("bob.ti@example.com", "bob_ti");
        var wsA = createWorkspace(alice.accessToken(), "Alice Private");

        // Bob cannot read Alice's workspace -> 403
        mockMvc.perform(get("/api/v1/workspaces/" + wsA)
                        .header("Authorization", auth(bob.accessToken())))
                .andExpect(status().isForbidden());

        // Bob cannot list Alice's projects
        mockMvc.perform(get("/api/v1/workspaces/" + wsA + "/projects")
                        .header("Authorization", auth(bob.accessToken())))
                .andExpect(status().isForbidden());

        // Bob cannot create project in Alice's workspace
        mockMvc.perform(post("/api/v1/workspaces/" + wsA + "/projects")
                        .header("Authorization", auth(bob.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"XX\",\"name\":\"Hack\"}"))
                .andExpect(status().isForbidden());

        // Bob cannot invite to Alice's workspace
        mockMvc.perform(post("/api/v1/workspaces/" + wsA + "/invitations")
                        .header("Authorization", auth(bob.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@y.com\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void crossWorkspaceTaskAccessIsRejected() throws Exception {
        var alice = register("alice.ti2@example.com", "alice_ti2");
        var bob = register("bob.ti2@example.com", "bob_ti2");
        var wsA = createWorkspace(alice.accessToken(), "WS A");
        var projA = createProject(alice.accessToken(), wsA, "PA", "Project A");

        // create task in A
        var taskRes = mockMvc.perform(post("/api/v1/projects/" + projA + "/tasks")
                        .header("Authorization", auth(alice.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Secret task\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String taskId = objectMapper.readTree(taskRes.getResponse().getContentAsString()).get("id").asText();

        // Bob (no membership) cannot read task -> 403
        mockMvc.perform(get("/api/v1/tasks/" + taskId)
                        .header("Authorization", auth(bob.accessToken())))
                .andExpect(status().isForbidden());
    }
}
