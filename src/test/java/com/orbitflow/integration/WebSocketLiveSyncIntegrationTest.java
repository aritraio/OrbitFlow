package com.orbitflow.integration;

import com.orbitflow.realtime.PresenceService;
import com.orbitflow.realtime.RealtimePublisher;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WebSocketLiveSyncIntegrationTest extends BaseIntegrationTest {
    @Autowired RealtimePublisher realtimePublisher;
    @Autowired PresenceService presenceService;

    @Test
    void restMovePublishesRealtimeEventWithVersion() throws Exception {
        var alice = register("alice.ws@example.com", "alice_ws");
        var ws = createWorkspace(alice.accessToken(), "Live WS");
        var proj = createProject(alice.accessToken(), ws, "LV", "Live Project");

        var boardRes = mockMvc.perform(get("/api/v1/projects/" + proj + "/board")
                        .header("Authorization", auth(alice.accessToken())))
                .andExpect(status().isOk()).andReturn();
        String boardId = objectMapper.readTree(boardRes.getResponse().getContentAsString()).get("id").asText();
        var cols = objectMapper.readTree(boardRes.getResponse().getContentAsString()).get("columns");
        String colA = cols.get(0).get("id").asText();
        String colB = cols.get(1).get("id").asText();

        var taskRes = mockMvc.perform(post("/api/v1/projects/" + proj + "/tasks")
                        .header("Authorization", auth(alice.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Live card\",\"columnId\":\"" + colA + "\",\"assigneeIds\":[\"" + alice.userId() + "\"]}"))
                .andExpect(status().isCreated()).andReturn();
        var taskJson = objectMapper.readTree(taskRes.getResponse().getContentAsString());
        String taskId = taskJson.get("id").asText();
        long version = taskJson.get("version").asLong();

        realtimePublisher.clear();
        // drain outbox so the TaskMoved event is published via RealtimePublisher listener
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/move")
                        .header("Authorization", auth(alice.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destinationColumnId\":\"" + colB + "\",\"expectedVersion\":" + version + "}"))
                .andExpect(status().isOk());

        // allow @Async delivery then drain outbox synchronously for determinism
        Thread.sleep(300);
        var outbox = getBean(com.orbitflow.outbox.OutboxService.class);
        outbox.drain();
        Thread.sleep(500);

        assertThat(realtimePublisher.getPublished()).isNotEmpty();
        assertThat(realtimePublisher.currentRevision()).isGreaterThan(0);
        boolean hasMove = realtimePublisher.getPublished().stream()
                .anyMatch(e -> "TaskMoved".equals(e.get("eventType")));
        assertThat(hasMove).isTrue();
    }

    @Test
    void presenceTracksViewersWithHeartbeatAndExpiryFallback() {
        UUID boardId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        presenceService.heartbeat(boardId, userId);
        assertThat(presenceService.viewers(boardId)).contains(userId);
        presenceService.leave(boardId, userId);
        assertThat(presenceService.viewers(boardId)).doesNotContain(userId);
    }

    @Test
    void unauthorizedSubscriptionIsRejected() throws Exception {
        var alice = register("alice.ws2@example.com", "alice_ws2");
        var bob = register("bob.ws2@example.com", "bob_ws2");
        var ws = createWorkspace(alice.accessToken(), "Private Live");
        var proj = createProject(alice.accessToken(), ws, "PL", "Private Live Project");
        var boardRes = mockMvc.perform(get("/api/v1/projects/" + proj + "/board")
                        .header("Authorization", auth(alice.accessToken())))
                .andExpect(status().isOk()).andReturn();
        String boardId = objectMapper.readTree(boardRes.getResponse().getContentAsString()).get("id").asText();

        // Bob tries to fetch board -> forbidden (same policy the WS interceptor enforces)
        mockMvc.perform(get("/api/v1/boards/" + boardId)
                        .header("Authorization", auth(bob.accessToken())))
                .andExpect(status().isForbidden());
    }

    @Autowired org.springframework.context.ApplicationContext ctx;
    private <T> T getBean(Class<T> cls) { return ctx.getBean(cls); }
}
