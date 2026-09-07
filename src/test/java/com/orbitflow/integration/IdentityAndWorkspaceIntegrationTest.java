package com.orbitflow.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class IdentityAndWorkspaceIntegrationTest extends BaseIntegrationTest {

    @Test
    void registrationLoginAndJwt() throws Exception {
        var reg = register("alice.iw@example.com", "alice_iw");
        // login with email
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailOrUsername\":\"alice.iw@example.com\",\"password\":\"Password123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists());
        // me
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", auth(reg.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice.iw@example.com"));
    }

    @Test
    void workspaceCreationAndSlugUniqueness() throws Exception {
        var alice = register("bob.iw@example.com", "bob_iw");
        var ws1 = createWorkspace(alice.accessToken(), "Acme Team");
        // second workspace with same name gets unique slug (both succeed)
        var ws2 = createWorkspace(alice.accessToken(), "Acme Team");
        org.junit.jupiter.api.Assertions.assertNotEquals(ws1, ws2);
    }

    @Test
    void invitationLifecycle() throws Exception {
        var owner = register("owner.iw@example.com", "owner_iw");
        var member = register("member.iw@example.com", "member_iw");
        var ws = createWorkspace(owner.accessToken(), "Invite WS");

        // invite
        var inviteRes = mockMvc.perform(post("/api/v1/workspaces/" + ws + "/invitations")
                        .header("Authorization", auth(owner.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"member.iw@example.com\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(inviteRes.getResponse().getContentAsString()).get("token").asText();

        // accept
        mockMvc.perform(post("/api/v1/invitations/" + token + "/accept")
                        .header("Authorization", auth(member.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ws.toString()));

        // member can now read workspace
        mockMvc.perform(get("/api/v1/workspaces/" + ws)
                        .header("Authorization", auth(member.accessToken())))
                .andExpect(status().isOk());
    }
}
