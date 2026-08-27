package com.pfe.evaluateur.evaluation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

public class FactCheckingEvaluator {

    private static final Logger log = LoggerFactory.getLogger(FactCheckingEvaluator.class);

    private final ChatClient chatClient;

    public static final double SEUIL_MAX_HALLUCINATION = 0.20;

    public FactCheckingEvaluator(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public EvaluationResult evaluate(String testId, String question,
            String contexteReference, String reponseAgent) {
        String prompt = """
                Tu es un expert en detection d'hallucinations dans les reponses d'agents IA.

                Verifie si la reponse de l'agent contient des hallucinations.
                Une hallucination est une information inventee, fausse ou non verifiable
                par rapport au contexte de reference fourni.

                QUESTION : %s
                CONTEXTE DE REFERENCE : %s
                REPONSE DE L'AGENT : %s

                Analyse et reponds UNIQUEMENT en JSON :
                {
                  "factuellemement_correct": true/false,
                  "score_fiabilite": 0.0 a 1.0,
                  "taux_hallucination": 0.0 a 1.0,
                  "hallucinations_detectees": ["liste ou vide"],
                  "infos_verifiees": ["liste des infos correctes"],
                  "explication": "explication courte"
                }

                Reponds UNIQUEMENT avec le JSON, sans texte avant ou apres.
                """.formatted(question, contexteReference, reponseAgent);

        String raw = chatClient.prompt()
                .user(prompt)
                .call()
                .content()
                .trim()
                .replaceAll("```json", "")
                .replaceAll("```", "")
                .trim();

        return parseResult(testId, question, reponseAgent, raw);
    }

    private EvaluationResult parseResult(String testId, String question,
            String reponseAgent, String json) {
        try {
            boolean factuel = extractBoolean(json, "factuellemement_correct");
            double scorefiabilite = extractDouble(json, "score_fiabilite");
            double tauxHallucination = extractDouble(json, "taux_hallucination");
            String explication = extractString(json, "explication");

            boolean sousLeSeuil = tauxHallucination <= SEUIL_MAX_HALLUCINATION;

            log.debug("Fiabilite: {}% | Hallucination: {}% | OK: {}",
                    Math.round(scorefiabilite * 100), Math.round(tauxHallucination * 100),
                    sousLeSeuil ? "OUI" : "NON");

            return new EvaluationResult(
                    testId, question, reponseAgent,
                    true, factuel && sousLeSeuil,
                    1.0, scorefiabilite,
                    explication);
        } catch (Exception e) {
            log.error("Erreur parsing : {}", e.getMessage());
            return new EvaluationResult(testId, question, reponseAgent,
                    false, false, 0.0, 0.0, "Erreur parsing");
        }
    }

    private boolean extractBoolean(String json, String key) {
        int idx = json.indexOf("\"" + key + "\"");
        if (idx == -1)
            return false;
        return json.substring(idx).contains("true");
    }

    private double extractDouble(String json, String key) {
        int idx = json.indexOf("\"" + key + "\"");
        if (idx == -1)
            return 0.0;
        String rest = json.substring(idx + key.length() + 2);
        int colon = rest.indexOf(":");
        if (colon == -1)
            return 0.0;
        String valPart = rest.substring(colon + 1).trim();
        StringBuilder num = new StringBuilder();
        for (char c : valPart.toCharArray()) {
            if (Character.isDigit(c) || c == '.')
                num.append(c);
            else if (num.length() > 0)
                break;
        }
        return num.length() > 0 ? Double.parseDouble(num.toString()) : 0.0;
    }

    private String extractString(String json, String key) {
        int idx = json.indexOf("\"" + key + "\"");
        if (idx == -1)
            return "";
        String rest = json.substring(idx + key.length() + 2);
        int start = rest.indexOf("\"") + 1;
        int end = rest.indexOf("\"", start);
        return start > 0 && end > start ? rest.substring(start, end) : "";
    }
}