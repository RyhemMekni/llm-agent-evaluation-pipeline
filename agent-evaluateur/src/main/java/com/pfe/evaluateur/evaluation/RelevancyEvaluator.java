package com.pfe.evaluateur.evaluation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

public class RelevancyEvaluator {

    private static final Logger log = LoggerFactory.getLogger(RelevancyEvaluator.class);

    private final ChatClient chatClient;

    public RelevancyEvaluator(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public EvaluationResult evaluate(String testId, String question,
            String contexteMcp, String reponseAgent) {
        String prompt = """
                Tu es un evaluateur expert en DevSecOps et qualite des agents IA.

                Analyse la reponse de l'agent selon ces 3 criteres :

                QUESTION POSEE : %s
                CONTEXTE MCP DISPONIBLE : %s
                REPONSE DE L'AGENT : %s

                Evalue selon ces criteres et reponds UNIQUEMENT en JSON :
                {
                  "pertinent": true/false,
                  "score_pertinence": 0.0 a 1.0,
                  "precision": 0.0 a 1.0,
                  "rappel": 0.0 a 1.0,
                  "coherence_contexte": true/false,
                  "explication": "explication courte"
                }

                Reponds UNIQUEMENT avec le JSON, sans texte avant ou apres.
                """.formatted(question, contexteMcp, reponseAgent);

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
            boolean pertinent = extractBoolean(json, "pertinent");
            double scorePertinence = extractDouble(json, "score_pertinence");
            double precision = extractDouble(json, "precision");
            double rappel = extractDouble(json, "rappel");
            boolean coherence = extractBoolean(json, "coherence_contexte");
            String explication = extractString(json, "explication");

            log.debug("Precision: {}% | Rappel: {}% | Coherence: {}",
                    Math.round(precision * 100), Math.round(rappel * 100), coherence ? "OUI" : "NON");

            return new EvaluationResult(
                    testId, question, reponseAgent,
                    pertinent, true,
                    scorePertinence, 1.0,
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