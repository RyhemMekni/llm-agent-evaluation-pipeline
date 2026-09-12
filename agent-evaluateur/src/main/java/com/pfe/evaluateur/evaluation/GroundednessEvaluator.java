package com.pfe.evaluateur.evaluation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Vérifie que chaque affirmation précise de la réponse (CVE, score CVSS,
 * date, version) est effectivement présente dans le contenu réellement
 * récupéré par web_search (webSearchContent), plutôt que de s'appuyer sur
 * la connaissance paramétrique du juge (comme le fait FactCheckingEvaluator).
 *
 * Inspiré du paradigme "decompose-then-verify" (FActScore, Min et al. 2023 ;
 * SAFE, Wei et al. 2024) : la vérification se fait contre des preuves
 * effectivement consultées, pas contre la mémoire du modèle.
 */
public class GroundednessEvaluator {

    private static final Logger log = LoggerFactory.getLogger(GroundednessEvaluator.class);

    private final ChatClient chatClient;

    public static final double SEUIL_MAX_NON_ANCRE = 0.20;

    public GroundednessEvaluator(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public EvaluationResult evaluate(String testId, String question,
            String webSearchContent, String reponseAgent) {
        String prompt = """
                Tu es un expert en vérification d'ancrage (groundedness) pour des
                systèmes d'IA utilisant la recherche web.

                Ta tâche est DIFFÉRENTE d'une simple vérification factuelle : tu ne dois
                PAS utiliser tes propres connaissances pour juger si une affirmation est
                vraie ou fausse dans l'absolu. Tu dois UNIQUEMENT vérifier si chaque
                affirmation précise de la réponse est ANCRÉE dans le CONTENU WEB fourni
                ci-dessous — c'est-à-dire si elle y apparaît explicitement ou peut en
                être directement déduite.

                RÈGLES STRICTES :
                - Si une affirmation précise (CVE, score CVSS, date, version, nom de
                  faille) apparaît dans le CONTENU WEB : elle est ANCRÉE, même si tu ne
                  peux pas vérifier par toi-même si elle est vraie dans l'absolu.
                - Si une affirmation précise n'apparaît PAS dans le CONTENU WEB : elle
                  est NON ANCRÉE, même si elle te semble plausible ou correspond à tes
                  propres connaissances.
                - Si la réponse dit honnêtement qu'elle n'a pas trouvé d'information
                  (ex: "je n'ai pas trouvé d'information fiable"), et que le CONTENU WEB
                  ne contient effectivement rien de pertinent sur le sujet : c'est un
                  comportement CORRECT, à noter comme sans hallucination.
                - N'utilise JAMAIS ta propre connaissance générale pour valider ou
                  invalider une affirmation. Base-toi UNIQUEMENT sur une comparaison
                  textuelle avec le CONTENU WEB fourni.

                QUESTION : %s

                CONTENU WEB RÉELLEMENT RÉCUPÉRÉ (source de vérité pour cette évaluation) :
                %s

                RÉPONSE DE L'AGENT À VÉRIFIER : %s

                Analyse et réponds UNIQUEMENT en JSON :
                {
                  "factuellemement_correct": true/false,
                  "score_fiabilite": 0.0 a 1.0,
                  "taux_hallucination": 0.0 a 1.0,
                  "hallucinations_detectees": ["affirmations précises NON présentes dans le contenu web"],
                  "infos_verifiees": ["affirmations précises confirmées présentes dans le contenu web"],
                  "explication": "explication courte, en citant si possible le passage du contenu web qui confirme ou infirme chaque affirmation"
                }

                Reponds UNIQUEMENT avec le JSON, sans texte avant ou apres.
                """.formatted(question, webSearchContent, reponseAgent);

        String raw = chatClient.prompt()
                .user(prompt)
                .call()
                .content()
                .trim()
                .replaceAll("```json", "")
                .replaceAll("```", "")
                .trim();

        System.out.println("=== JSON BRUT DU JUGE ===");
        System.out.println(raw);
        System.out.println("=========================");

        return parseResult(testId, question, reponseAgent, raw);
    }

    private EvaluationResult parseResult(String testId, String question,
            String reponseAgent, String json) {
        try {
            boolean factuel = extractBoolean(json, "factuellemement_correct");
            double scoreFiabilite = extractDouble(json, "score_fiabilite");
            double tauxNonAncre = extractDouble(json, "taux_hallucination");
            String explication = extractString(json, "explication");

            boolean sousLeSeuil = tauxNonAncre <= SEUIL_MAX_NON_ANCRE;

            log.debug("Groundedness - Fiabilite: {}% | Non-ancre: {}% | Factuel: {} | OK: {}",
                    Math.round(scoreFiabilite * 100), Math.round(tauxNonAncre * 100),
                    factuel ? "OUI" : "NON",
                    sousLeSeuil ? "OUI" : "NON");

            return new EvaluationResult(
                    testId, question, reponseAgent,
                    true, sousLeSeuil,
                    1.0, scoreFiabilite,
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