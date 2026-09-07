package com.orbitflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OptimisticLockTest extends BaseIntegrationTest {

    @Test
    void concurrentUpdatesSecondReturns409WithLatestState() throws Exception {
        var alice = register("alice.ol@example.com", "alice_ol");
        var ws = createWorkspace(alice.accessToken(), "Lock WS");
        var proj = createProject(alice.accessToken(), ws, "OL", "Lock Project");

        var taskRes = mockMvc.perform(post("/api/v1/projects/" + proj + "/tasks")
                        .header("Authorization", auth(alice.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Versioned\"}"))
                .andExpect(status().isCreated()).andReturn();
        JsonNode task = objectMapper.readTree(taskRes.getResponse().getContentAsString());
        String taskId = task.get("id").asText();
        long v0 = task.get("version").asLong();

        // First update with v0 succeeds
        mockMvc.perform(patch("/api/v1/tasks/" + taskId)
                        .header("Authorization", auth(alice.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"First edit\",\"expectedVersion\":" + v0 + "}"))
                .andExpect(status().isOk());

        // Second update with stale v0 -> 409 with currentState
        mockMvc.perform(patch("/api/v1/tasks/" + taskId)
                        .header("Authorization", auth(alice.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Stale edit\",\"expectedVersion\":" + v0 + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("conflict"));
    }
}
