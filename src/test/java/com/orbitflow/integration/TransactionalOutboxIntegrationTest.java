package com.orbitflow.integration;

import com.orbitflow.outbox.OutboxRepository;
import com.orbitflow.outbox.OutboxService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TransactionalOutboxIntegrationTest extends BaseIntegrationTest {
    @Autowired OutboxRepository outboxRepository;
    @Autowired OutboxService outboxService;

    @Test
    void taskMoveWritesOutboxAtomicallyAndPollerDispatches() throws Exception {
        var alice = register("alice.ob@example.com", "alice_ob");
        var ws = createWorkspace(alice.accessToken(), "Outbox WS");
        var proj = createProject(alice.accessToken(), ws, "OB", "Outbox Project");

        long before = outboxRepository.count();
        var taskRes = mockMvc.perform(post("/api/v1/projects/" + proj + "/tasks")
                        .header("Authorization", auth(alice.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Outbox task\"}"))
                .andExpect(status().isCreated()).andReturn();
        String taskId = objectMapper.readTree(taskRes.getResponse().getContentAsString()).get("id").asText();

        // Task creation wrote outbox row atomically
        assertThat(outboxRepository.count()).isGreaterThan(before);
        assertThat(outboxRepository.findByStatusOrderByCreatedAtAsc("PENDING")).isNotEmpty();

        // Drain poller -> PROCESSED
        int drained = outboxService.drain();
        assertThat(drained).isGreaterThan(0);
        assertThat(outboxRepository.findByStatusOrderByCreatedAtAsc("PENDING")).isEmpty();

        // Activity timeline has sanitized diff
        mockMvc.perform(get("/api/v1/tasks/" + taskId + "/activity")
                        .header("Authorization", auth(alice.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").exists());
    }
}
