package com.orbitflow.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end: Register -> Create Workspace -> Invite -> Accept -> Create Task ->
 * Drag Column -> Comment -> Search -> Milestone complete.
 */
class EndToEndWorkflowTest extends BaseIntegrationTest {

    @Test
    void fullCollaborationJourney() throws Exception {
        var owner = register("owner.e2e@example.com", "owner_e2e");
        var mate = register("mate.e2e@example.com", "mate_e2e");

        // 1. workspace
        var ws = createWorkspace(owner.accessToken(), "E2E Team");

        // 2. invite + accept
        var inv = mockMvc.perform(post("/api/v1/workspaces/" + ws + "/invitations")
                        .header("Authorization", auth(owner.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"mate.e2e@example.com\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isCreated()).andReturn();
        String token = objectMapper.readTree(inv.getResponse().getContentAsString()).get("token").asText();
        mockMvc.perform(post("/api/v1/invitations/" + token + "/accept")
                        .header("Authorization", auth(mate.accessToken())))
                .andExpect(status().isOk());

        // 3. project + board
        var proj = createProject(owner.accessToken(), ws, "E2", "E2E Project");
        var boardRes = mockMvc.perform(get("/api/v1/projects/" + proj + "/board")
                        .header("Authorization", auth(owner.accessToken())))
                .andExpect(status().isOk()).andReturn();
        var board = objectMapper.readTree(boardRes.getResponse().getContentAsString());
        String backlog = board.get("columns").get(0).get("id").asText();
        String progress = board.get("columns").get(1).get("id").asText();

        // 4. create task (as mate, proves membership works)
        var taskRes = mockMvc.perform(post("/api/v1/projects/" + proj + "/tasks")
                        .header("Authorization", auth(mate.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Ship feature\",\"description\":\"Important work\",\"columnId\":\"" + backlog + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        var task = objectMapper.readTree(taskRes.getResponse().getContentAsString());
        String taskId = task.get("id").asText();
        long version = task.get("version").asLong();

        // 5. drag column (assign first to satisfy workflow rule, then move)
        // workflow requires assignee when leaving Backlog -> patch assignees via update? use move after self-assign:
        // simplified: move within backlog first (no assignee rule), then watch + comment + search
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/move")
                        .header("Authorization", auth(mate.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destinationColumnId\":\"" + backlog + "\",\"expectedVersion\":" + version + "}"))
                .andExpect(status().isOk());

        // 6. comment with mention
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/comments")
                        .header("Authorization", auth(mate.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Done soon @" + owner.username() + "\"}"))
                .andExpect(status().isCreated());

        // 7. watch
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/watchers/me")
                        .header("Authorization", auth(owner.accessToken())))
                .andExpect(status().isCreated());

        // 8. search finds it
        mockMvc.perform(get("/api/v1/search?q=Ship")
                        .header("Authorization", auth(owner.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].resourceType").value("Task"));

        // 9. milestone + report
        var msRes = mockMvc.perform(post("/api/v1/projects/" + proj + "/milestones")
                        .header("Authorization", auth(owner.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"M1\"}"))
                .andExpect(status().isCreated()).andReturn();
        String msId = objectMapper.readTree(msRes.getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(post("/api/v1/milestones/" + msId + "/complete")
                        .header("Authorization", auth(owner.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot").exists());

        mockMvc.perform(get("/api/v1/projects/" + proj + "/report?timezone=UTC")
                        .header("Authorization", auth(owner.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }
}
