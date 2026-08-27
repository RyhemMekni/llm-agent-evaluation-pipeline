package com.pfe.evaluateur.service;

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

    // URL de l'Agent 1 (projet-ia) — port 8080
    private static final String AGENT1_URL = "http://localhost:8080/api/agent/ask?question={question}";

    public EvaluateurAgentService(ChatClient.Builder builder) {
        this.relevancyEvaluator = new RelevancyEvaluator(builder);
        this.factCheckingEvaluator = new FactCheckingEvaluator(builder);
        this.restTemplate = new RestTemplate();
    }

    /**
     * Pipeline complet :
     * 1. Envoie la question à l'Agent 1
     * 2. Récupère la réponse
     * 3. Évalue la pertinence (RelevancyEvaluator)
     * 4. Détecte les hallucinations (FactCheckingEvaluator)
     * 5. Retourne un VerdictEvaluation consolidé
     */
    public VerdictEvaluation evaluerAgent(String testId, String question, String contexte) {

        log.info("========================================");
        log.info("  AGENT ÉVALUATEUR INDÉPENDANT");
        log.info("========================================");
        log.info("  Question : {}", question);

        // ÉTAPE 1 — Appel Agent 1
        String reponseAgent1;
        try {
            reponseAgent1 = restTemplate.getForObject(AGENT1_URL, String.class, question);
            log.info("  Réponse Agent 1 reçue : {}", reponseAgent1);
        } catch (Exception e) {
            log.error("  ERREUR : Agent 1 inaccessible — {}", e.getMessage());
            return VerdictEvaluation.erreur(testId, question, "Agent 1 inaccessible : " + e.getMessage());
        }

        // ÉTAPE 2 — Évaluation Pertinence
        log.info("--- Évaluation Pertinence (RelevancyEvaluator) ---");
        EvaluationResult resultRelevancy = relevancyEvaluator.evaluate(
                testId + "-REL", question, contexte, reponseAgent1);

        // ÉTAPE 3 — Détection Hallucinations
        log.info("--- Détection Hallucinations (FactCheckingEvaluator) ---");
        EvaluationResult resultFactChecking = factCheckingEvaluator.evaluate(
                testId + "-FC", question, contexte, reponseAgent1);

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