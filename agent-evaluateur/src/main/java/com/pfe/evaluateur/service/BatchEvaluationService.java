package com.pfe.evaluateur.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.evaluateur.model.VerdictEvaluation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;

@Service
public class BatchEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(BatchEvaluationService.class);

    private final EvaluateurAgentService evaluateurService;
    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;

    public BatchEvaluationService(EvaluateurAgentService evaluateurService, MetricsService metricsService) {
        this.evaluateurService = evaluateurService;
        this.metricsService = metricsService;
        this.objectMapper = new ObjectMapper();
    }

    public Map<String, Object> executerBatch() {
        log.info("========================================");
        log.info("  BATCH EVALUATION — Dataset complet");
        log.info("========================================");

        List<Map<String, Object>> resultats = new ArrayList<>();
        int total = 0, conformes = 0, nonConformes = 0;

        try {
            InputStream is = new ClassPathResource("static/dataset_evaluation.json").getInputStream();
            JsonNode root = objectMapper.readTree(is);
            JsonNode casDeTest = root.get("cas_de_test");

            for (JsonNode cas : casDeTest) {
                total++;
                String id = cas.get("id").asText();
                String question = cas.get("question").asText();
                String contexte = cas.get("contexte_reference").asText();
                String attendu = cas.get("resultat_attendu").asText();
                String domaine = cas.get("domaine").asText();
                String type = cas.get("type").asText();

                log.info("--- Cas {} ({}) : {} ---", id, domaine, question);

                VerdictEvaluation verdict = evaluateurService.evaluerAgent(id, question, contexte);

                boolean verdictPositif = verdict.pertinent() && verdict.sansHallucination();
                String verdictObtenu = verdictPositif ? "APPROUVE" : "REJETE";
                boolean conforme = verdictObtenu.equals(attendu);

                metricsService.enregistrerEvaluation(conforme);

                if (conforme) {
                    conformes++;
                    log.info("  {} CONFORME — attendu: {}, obtenu: {}", id, attendu, verdictObtenu);
                } else {
                    nonConformes++;
                    log.warn("  {} NON CONFORME — attendu: {}, obtenu: {}", id, attendu, verdictObtenu);
                }

                Map<String, Object> resultatCas = new LinkedHashMap<>();
                resultatCas.put("id", id);
                resultatCas.put("domaine", domaine);
                resultatCas.put("type", type);
                resultatCas.put("question", question);
                resultatCas.put("resultat_attendu", attendu);
                resultatCas.put("resultat_obtenu", verdictObtenu);
                resultatCas.put("conforme", conforme);
                resultatCas.put("scorePertinence", verdict.scorePertinence());
                resultatCas.put("scoreFactuel", verdict.scoreFactuel());
                resultatCas.put("verdict_detail", verdict.verdict());
                resultats.add(resultatCas);
            }

        } catch (Exception e) {
            log.error("Erreur lors du batch: {}", e.getMessage());
        }

        double tauxConformite = total > 0 ? (conformes * 100.0 / total) : 0;

        log.info("========================================");
        log.info("  RAPPORT BATCH");
        log.info("  Total: {} | Conformes: {} | Non conformes: {}", total, conformes, nonConformes);
        log.info("  Taux de conformite: {}%", Math.round(tauxConformite));
        log.info("========================================");

        metricsService.enregistrerTauxConformiteBatch((int) Math.round(tauxConformite));

        Map<String, Object> rapport = new LinkedHashMap<>();
        rapport.put("total", total);
        rapport.put("conformes", conformes);
        rapport.put("nonConformes", nonConformes);
        rapport.put("tauxConformite", Math.round(tauxConformite));
        rapport.put("resultats", resultats);
        return rapport;
    }
}