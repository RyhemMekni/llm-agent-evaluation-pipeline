package com.pfe.evaluateur.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.evaluateur.evaluation.ConformityEvaluator;
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
    private final ConformityEvaluator conformityEvaluator;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String AGENT1_URL = "http://localhost:8080/api/agent/ask?question={question}";

    public EvaluateurAgentService(ChatClient.Builder builder) {
        this.relevancyEvaluator = new RelevancyEvaluator(builder);
        this.factCheckingEvaluator = new FactCheckingEvaluator(builder);
        this.conformityEvaluator = new ConformityEvaluator(builder);
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public VerdictEvaluation evaluerAgent(String testId, String question, String contexteReference) {

        log.info("========================================");
        log.info("  AGENT ÉVALUATEUR INDÉPENDANT");
        log.info("========================================");
        log.info("  Question : {}", question);

        // ÉTAPE 1 — Appel Agent 1
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

        // ÉTAPE 2 — Évaluation Pertinence (avec contexte MCP réel)
        log.info("--- Évaluation Pertinence (RelevancyEvaluator) ---");
        EvaluationResult resultRelevancy = relevancyEvaluator.evaluate(
                testId + "-REL", question, contexteMcpUtilise, reponseAgent1);

        // ÉTAPE 3 — Détection Hallucinations (avec contexte de référence)
        log.info("--- Détection Hallucinations (FactCheckingEvaluator) ---");
        EvaluationResult resultFactChecking = factCheckingEvaluator.evaluate(
                testId + "-FC", question, contexteReference, reponseAgent1);

        // ÉTAPE 4 — Évaluation Conformité (nouveau - Sprint 4)
        log.info("--- Évaluation Conformité (ConformityEvaluator) ---");
        EvaluationResult resultConformity = conformityEvaluator.evaluate(
                testId + "-CONF", question, contexteReference, reponseAgent1);

        // ÉTAPE 5 — Verdict consolidé multi-critères
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
                                + " | Factuel: " + resultFactChecking.explication()
                                + " | Conformité: " + resultConformity.explication())
                .verdict(determinerVerdict(resultRelevancy, resultFactChecking, resultConformity))
                .build();

        log.info("========================================");
        log.info("  VERDICT FINAL : {}", verdict.verdict());
        log.info("  Score Pertinence  : {}%", Math.round(verdict.scorePertinence() * 100));
        log.info("  Score Factuel     : {}%", Math.round(verdict.scoreFactuel() * 100));
        log.info("  Score Conformité  : {}%", Math.round(resultConformity.scorePertinence() * 100));
        log.info("========================================");

        return verdict;
    }

    private String determinerVerdict(EvaluationResult relevancy,
            EvaluationResult factChecking,
            EvaluationResult conformity) {
        boolean pertinent = relevancy.pertinent();
        boolean fiable = factChecking.sansHallucination();
        boolean conforme = conformity.pertinent(); // pertinent() = estConforme dans ConformityEvaluator

        if (pertinent && fiable && conforme) {
            return "✅ APPROUVÉ — Réponse pertinente, fiable et conforme";
        } else if (!conforme) {
            return "❌ REJETÉ — Non conforme au périmètre métier";
        } else if (!pertinent && !fiable) {
            return "❌ REJETÉ — Réponse non pertinente ET hallucinée";
        } else if (!pertinent) {
            return "⚠️ REJETÉ — Réponse non pertinente";
        } else {
            return "⚠️ REJETÉ — Hallucinations détectées";
        }
    }
}