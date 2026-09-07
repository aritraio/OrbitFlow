package com.orbitflow.integration;

import com.orbitflow.notification.MailService;
import com.orbitflow.notification.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CollaborationAndNotificationIntegrationTest extends BaseIntegrationTest {
    @Autowired NotificationRepository notificationRepository;
    @Autowired MailService mailService;

    @Test
    void mentionCreatesExactlyOneNotificationAndEmail() throws Exception {
        var alice = register("alice.cn@example.com", "alice_cn");
        var john = register("john.cn@example.com", "john_cn");
        // fix: register returns username; use known emails
        var ws = createWorkspace(alice.accessToken(), "Collab WS");

        // invite john by email
        var inviteRes = mockMvc.perform(post("/api/v1/workspaces/" + ws + "/invitations")
                        .header("Authorization", auth(alice.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + john.email() + "\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isCreated()).andReturn();
        String token = objectMapper.readTree(inviteRes.getResponse().getContentAsString()).get("token").asText();
        mockMvc.perform(post("/api/v1/invitations/" + token + "/accept")
                        .header("Authorization", auth(john.accessToken())))
                .andExpect(status().isOk());

        var proj = createProject(alice.accessToken(), ws, "CN", "Collab Project");
        var taskRes = mockMvc.perform(post("/api/v1/projects/" + proj + "/tasks")
                        .header("Authorization", auth(alice.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Mention task\"}"))
                .andExpect(status().isCreated()).andReturn();
        String taskId = objectMapper.readTree(taskRes.getResponse().getContentAsString()).get("id").asText();

        mailService.clear();
        long notifBefore = notificationRepository.count();

        // comment with @mention (john's username)
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/comments")
                        .header("Authorization", auth(alice.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Hey @" + john.username() + " please take a look\"}"))
                .andExpect(status().isCreated());

        // exactly 1 notification for john
        assertThat(notificationRepository.count()).isEqualTo(notifBefore + 1);
        // email recorded (Mailpit target; in-memory verification)
        assertThat(mailService.getSentEmails()).hasSize(1);
        assertThat(mailService.getSentEmails().get(0).get("to")).isEqualTo(john.email());

        // self-mention does not notify
        long afterFirst = notificationRepository.count();
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/comments")
                        .header("Authorization", auth(alice.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Note to self @" + alice.username() + "\"}"))
                .andExpect(status().isCreated());
        assertThat(notificationRepository.count()).isEqualTo(afterFirst);
    }
}
