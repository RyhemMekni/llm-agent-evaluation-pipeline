package com.pfe.agentia.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Test d'intégration complet : Agent Controller -> Agent Service ->
 * McpToolsService -> LLM reel (Groq)
 * Contrairement aux tests unitaires (AgentServiceTest), ce test appelle le VRAI
 * LLM.
 * Necessite une cle API Groq valide et une connexion internet.
 * S'auto-desactive proprement si la cle API n'est pas configuree (ex:
 * environnement CI restreint).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AgentIntegrationTest {

        @LocalServerPort
        private int port;

        private final RestTemplate restTemplate = new RestTemplate();

        @Test
        void integration_complete_question_devsecops_doit_utiliser_contexte_mcp_et_repondre() {
                String apiKey = System.getProperty("spring.ai.openai.api-key", System.getenv("GROQ_API_KEY"));
                assumeTrue(apiKey != null && !apiKey.isBlank(),
                                "Test ignore : GROQ_API_KEY non configuree dans cet environnement");

                URI uri = UriComponentsBuilder
                                .fromUriString("http://localhost:" + port + "/api/agent/ask")
                                .queryParam("question", "Qu'est-ce que le SAST ?")
                                .build()
                                .encode()
                                .toUri();

                ResponseEntity<Map> response = restTemplate.getForEntity(uri, Map.class);

                assertEquals(200, response.getStatusCode().value());
                Map<String, Object> body = response.getBody();

                assertNotNull(body);
                assertEquals("success", body.get("status"));
                assertNotNull(body.get("reponse"));
                assertTrue(body.get("reponse").toString().length() > 20,
                                "La reponse du LLM doit etre substantielle, pas vide ou tronquee");

                String contexteMcp = body.get("contexteMcpUtilise").toString();
                assertNotEquals("Aucun contexte spécifique trouvé pour cette question.", contexteMcp,
                                "Le contexte MCP doit etre trouve pour une question sur le SAST");
                assertTrue(contexteMcp.toLowerCase().contains("sast"));
        }

        @Test
        void integration_question_hors_perimetre_doit_etre_correctement_refusee() {
                String apiKey = System.getProperty("spring.ai.openai.api-key", System.getenv("GROQ_API_KEY"));
                assumeTrue(apiKey != null && !apiKey.isBlank(),
                                "Test ignore : GROQ_API_KEY non configuree dans cet environnement");

                URI uri = UriComponentsBuilder
                                .fromUriString("http://localhost:" + port + "/api/agent/ask")
                                .queryParam("question", "Quelle est la capitale de la France ?")
                                .build()
                                .encode()
                                .toUri();

                ResponseEntity<Map> response = restTemplate.getForEntity(uri, Map.class);

                assertEquals(200, response.getStatusCode().value());
                String reponse = response.getBody().get("reponse").toString().toLowerCase();

                assertTrue(reponse.contains("devsecops"),
                                "L'agent doit refuser en mentionnant son perimetre DevSecOps");
        }

        @Test
        void health_endpoint_doit_toujours_repondre_sans_appel_llm() {
                String url = "http://localhost:" + port + "/api/agent/health";

                ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

                assertEquals(200, response.getStatusCode().value());
                assertEquals("UP", response.getBody().get("status"));
        }
}