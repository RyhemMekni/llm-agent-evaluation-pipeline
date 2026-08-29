package com.pfe.evaluateur.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.evaluateur.evaluation.FactCheckingEvaluator;
import com.pfe.evaluateur.evaluation.RelevancyEvaluator;
import com.pfe.evaluateur.evaluation.EvaluationResult;
import com.pfe.evaluateur.model.VerdictEvaluation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class EvaluateurAgentService {

    private static final Logger log = LoggerFactory.getLogger(EvaluateurAgentService.class);

    private final RelevancyEvaluator relevancyEvaluator;
    private final FactCheckingEvaluator factCheckingEvaluator;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // URL de l'Agent 1 (projet-ia) — port 8080
    private static final String AGENT1_URL = "http://localhost:8080/api/agent/ask?question={question}";

    public EvaluateurAgentService(ChatClient.Builder builder) {
        this.relevancyEvaluator = new RelevancyEvaluator(builder);
        this.factCheckingEvaluator = new FactCheckingEvaluator(builder);
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Pipeline complet :
     * 1. Envoie la question à l'Agent 1
     * 2. Récupère la réponse ET le contexte MCP utilisé par l'Agent 1
     * 3. Évalue la pertinence (RelevancyEvaluator) avec le vrai contexte MCP
     * 4. Détecte les hallucinations (FactCheckingEvaluator) avec le contexte de
     * référence du dataset
     * 5. Retourne un VerdictEvaluation consolidé
     */
    public VerdictEvaluation evaluerAgent(String testId, String question, String contexteReference) {

        log.info("========================================");
        log.info("  AGENT ÉVALUATEUR INDÉPENDANT");
        log.info("========================================");
        log.info("  Question : {}", question);

        // ÉTAPE 1 — Appel Agent 1 (récupère réponse + contexte MCP réel)
        String rawResponse;
        String reponseAgent1;
        String contexteMcpUtilise;
        try {
            rawResponse = restTemplate.getForObject(AGENT1_URL, String.class, question);
            JsonNode json = objectMapper.readTree(rawResponse);
            reponseAgent1 = json.has("reponse") ? json.get("reponse").asText() : rawResponse;
            contexteMcpUtilise = json.has("contexteMcpUtilise")
                    ? json.get("contexteMcpUtilise").asText()
                    : "Contexte MCP non disponible";
            log.info("  Réponse Agent 1 reçue (extrait) : {}",
                    reponseAgent1.length() > 200 ? reponseAgent1.substring(0, 200) + "..." : reponseAgent1);
            log.debug("  Contexte MCP utilisé par Agent 1 : {}", contexteMcpUtilise);
        } catch (Exception e) {
            log.error("  ERREUR : Agent 1 inaccessible — {}", e.getMessage());
            return VerdictEvaluation.erreur(testId, question, "Agent 1 inaccessible : " + e.getMessage());
        }

        // ÉTAPE 2 — Évaluation Pertinence avec le CONTEXTE MCP RÉEL de l'Agent 1
        log.info("--- Évaluation Pertinence (RelevancyEvaluator) — avec contexte MCP réel ---");
        EvaluationResult resultRelevancy = relevancyEvaluator.evaluate(
                testId + "-REL", question, contexteMcpUtilise, reponseAgent1);

        // ÉTAPE 3 — Détection Hallucinations avec le contexte de référence du dataset
        log.info("--- Détection Hallucinations (FactCheckingEvaluator) — avec contexte de vérité ---");
        EvaluationResult resultFactChecking = factCheckingEvaluator.evaluate(
                testId + "-FC", question, contexteReference, reponseAgent1);

        // ÉTAPE 4 — Verdict consolidé
        VerdictEvaluation verdict = VerdictEvaluation.builder()
                .testId(testId)
                .question(question)
                .reponseAgent1(reponseAgent1)
                .scorePertinence(resultRelevancy.scorePertinence())
                .pertinent(resultRelevancy.pertinent())
                .scoreFactuel(resultFactChecking.scoreFactuel())
                .sansHallucination(resultFactChecking.sansHallucination())
                .explications(
                        "Pertinence: " + resultRelevancy.explication()
                                + " | Factuel: " + resultFactChecking.explication())
                .verdict(determinerVerdict(resultRelevancy, resultFactChecking))
                .build();

        log.info("========================================");
        log.info("  VERDICT FINAL : {}", verdict.verdict());
        log.info("  Score Pertinence : {}%", Math.round(verdict.scorePertinence() * 100));
        log.info("  Score Factuel    : {}%", Math.round(verdict.scoreFactuel() * 100));
        log.info("========================================");

        return verdict;
    }

    private String determinerVerdict(EvaluationResult relevancy, EvaluationResult factChecking) {
        if (relevancy.pertinent() && factChecking.sansHallucination()) {
            return "✅ APPROUVÉ — Réponse pertinente et fiable";
        } else if (!relevancy.pertinent() && !factChecking.sansHallucination()) {
            return "❌ REJETÉ — Réponse non pertinente ET hallucinée";
        } else if (!relevancy.pertinent()) {
            return "⚠️ REJETÉ — Réponse non pertinente";
        } else {
            return "⚠️ REJETÉ — Hallucinations détectées";
        }
    }
}