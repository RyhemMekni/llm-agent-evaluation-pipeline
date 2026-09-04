package com.pfe.agentia.controller;

import com.pfe.agentia.service.AgentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentControllerTest {

    @Mock
    private AgentService agentService;

    private AgentController agentController;

    @BeforeEach
    void setUp() {
        agentController = new AgentController(agentService);
    }

    @Test
    void devrait_retourner_400_si_question_null() {
        ResponseEntity<Map<String, Object>> response = agentController.ask(null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().containsKey("error"));
        verifyNoInteractions(agentService);
    }

    @Test
    void devrait_retourner_400_si_question_vide() {
        ResponseEntity<Map<String, Object>> response = agentController.ask("   ");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().containsKey("error"));
        verifyNoInteractions(agentService);
    }

    @Test
    void devrait_retourner_400_si_question_trop_longue() {
        String questionTropLongue = "a".repeat(501);

        ResponseEntity<Map<String, Object>> response = agentController.ask(questionTropLongue);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("error").toString().contains("500"));
        verifyNoInteractions(agentService);
    }

    @Test
    void devrait_accepter_question_de_exactement_500_caracteres() {
        String question = "a".repeat(500);
        Map<String, String> resultatService = Map.of(
                "reponse", "reponse test",
                "contexteMcpUtilise", "contexte test");
        when(agentService.ask(question)).thenReturn(resultatService);

        ResponseEntity<Map<String, Object>> response = agentController.ask(question);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(agentService, times(1)).ask(question);
    }

    @Test
    void devrait_retourner_200_avec_reponse_complete_pour_question_valide() {
        String question = "Qu'est-ce que le SAST ?";
        Map<String, String> resultatService = Map.of(
                "reponse", "Le SAST est une technique d'analyse statique...",
                "contexteMcpUtilise", "SAST designe l'analyse statique du code");
        when(agentService.ask(question)).thenReturn(resultatService);

        ResponseEntity<Map<String, Object>> response = agentController.ask(question);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(question, response.getBody().get("question"));
        assertEquals("Le SAST est une technique d'analyse statique...", response.getBody().get("reponse"));
        assertEquals("SAST designe l'analyse statique du code", response.getBody().get("contexteMcpUtilise"));
        assertEquals("DevSecOps Expert", response.getBody().get("agent"));
        assertEquals("success", response.getBody().get("status"));
    }

    @Test
    void health_devrait_retourner_200_avec_statut_up() {
        ResponseEntity<Map<String, String>> response = agentController.health();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UP", response.getBody().get("status"));
        assertEquals("DevSecOps Expert IA", response.getBody().get("agent"));
    }
}