package com.pfe.evaluateur.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.evaluateur.evaluation.EvaluationResult;
import com.pfe.evaluateur.evaluation.PromptInjectionEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.InputStream;
import java.util.*;

@Service
public class PromptInjectionService {

    private static final Logger log = LoggerFactory.getLogger(PromptInjectionService.class);

    private final PromptInjectionEvaluator promptInjectionEvaluator;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String AGENT1_URL = "http://localhost:8080/api/agent/ask";

    public PromptInjectionService(ChatClient.Builder builder) {
        this.promptInjectionEvaluator = new PromptInjectionEvaluator(builder);
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public Map<String, Object> executerBatchPromptInjection() {
        log.info("========================================");
        log.info("  BATCH PROMPT INJECTION - Dataset securite");
        log.info("========================================");

        List<Map<String, Object>> resultats = new ArrayList<>();
        int total = 0, resistants = 0, compromis = 0;

        try {
            InputStream is = new ClassPathResource("static/dataset_prompt_injection.json").getInputStream();
            JsonNode root = objectMapper.readTree(is);
            JsonNode casDeTest = root.get("cas_de_test");

            for (JsonNode cas : casDeTest) {
                total++;
                String id = cas.get("id").asText();
                String promptAttaque = cas.get("prompt_attaque").asText();
                String comportementAttendu = cas.get("comportement_attendu").asText();
                String categorie = cas.get("categorie").asText();

                log.info("--- Cas {} ({}) ---", id, categorie);
                log.info("  Prompt attaque: {}", promptAttaque);

                String reponseAgent = appelerAgentIa(promptAttaque);
                log.info("  Reponse agent (extrait): {}",
                        reponseAgent.length() > 150 ? reponseAgent.substring(0, 150) + "..." : reponseAgent);

                EvaluationResult resultat = promptInjectionEvaluator.evaluate(
                        id, promptAttaque, comportementAttendu, reponseAgent);

                boolean resistant = resultat.pertinent(); // pertinent() = !compromis dans notre mapping
                if (resistant) {
                    resistants++;
                    log.info("  {} RESISTANT — score: {}%", id, Math.round(resultat.scorePertinence() * 100));
                } else {
                    compromis++;
                    log.warn("  {} COMPROMIS — score: {}% — {}", id,
                            Math.round(resultat.scorePertinence() * 100), resultat.explication());
                }

                Map<String, Object> resultatCas = new LinkedHashMap<>();
                resultatCas.put("id", id);
                resultatCas.put("categorie", categorie);
                resultatCas.put("prompt_attaque", promptAttaque);
                resultatCas.put("comportement_attendu", comportementAttendu);
                resultatCas.put("reponse_agent", reponseAgent);
                resultatCas.put("resistant", resistant);
                resultatCas.put("score_resistance", resultat.scorePertinence());
                resultatCas.put("explication", resultat.explication());
                resultats.add(resultatCas);
            }

        } catch (Exception e) {
            log.error("Erreur lors du batch prompt injection: {}", e.getMessage());
        }

        double tauxResistance = total > 0 ? (resistants * 100.0 / total) : 0;

        log.info("========================================");
        log.info("  RAPPORT PROMPT INJECTION");
        log.info("  Total: {} | Resistants: {} | Compromis: {}", total, resistants, compromis);
        log.info("  Taux de resistance: {}%", Math.round(tauxResistance));
        log.info("========================================");

        Map<String, Object> rapport = new LinkedHashMap<>();
        rapport.put("total", total);
        rapport.put("resistants", resistants);
        rapport.put("compromis", compromis);
        rapport.put("tauxResistance", Math.round(tauxResistance));
        rapport.put("resultats", resultats);
        return rapport;
    }

    private String appelerAgentIa(String question) {
        try {
            String url = UriComponentsBuilder.fromUriString(AGENT1_URL)
                    .queryParam("question", question)
                    .toUriString();

            String rawResponse = restTemplate.getForObject(url, String.class);
            JsonNode json = objectMapper.readTree(rawResponse);
            return json.has("reponse") ? json.get("reponse").asText() : rawResponse;

        } catch (Exception e) {
            log.error("Erreur appel Agent IA: {}", e.getMessage());
            return "ERREUR: Agent IA injoignable - " + e.getMessage();
        }
    }
}