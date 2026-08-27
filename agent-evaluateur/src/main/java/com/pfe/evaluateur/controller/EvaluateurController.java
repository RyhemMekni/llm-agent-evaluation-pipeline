package com.pfe.evaluateur.controller;

import com.pfe.evaluateur.model.EvaluationRecord;
import com.pfe.evaluateur.model.VerdictEvaluation;
import com.pfe.evaluateur.service.EvaluateurAgentService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/evaluateur")
public class EvaluateurController {

    private final EvaluateurAgentService evaluateurService;
    private final List<EvaluationRecord> history = new ArrayList<>();

    public EvaluateurController(EvaluateurAgentService evaluateurService) {
        this.evaluateurService = evaluateurService;
    }

    @GetMapping("/evaluer")
    public VerdictEvaluation evaluer(
            @RequestParam String testId,
            @RequestParam String question,
            @RequestParam(defaultValue = "") String contexte) {
        VerdictEvaluation verdict = evaluateurService.evaluerAgent(testId, question, contexte);
        history.add(new EvaluationRecord(verdict));
        return verdict;
    }

    @GetMapping("/history")
    public List<EvaluationRecord> getHistory() { return history; }

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        int total      = history.size();
        long approuves = history.stream().filter(EvaluationRecord::isApprouve).count();
        double pertMoy = history.stream().mapToDouble(EvaluationRecord::getScorePertinence).average().orElse(0);
        double facMoy  = history.stream().mapToDouble(EvaluationRecord::getScoreFactuel).average().orElse(0);
        double hallMoy = history.stream().mapToDouble(r -> 1 - r.getScoreFactuel()).average().orElse(0);
        stats.put("total",                total);
        stats.put("approuves",            approuves);
        stats.put("rejetes",              total - approuves);
        stats.put("tauxApprobation",      total > 0 ? (int) Math.round((double) approuves / total * 100) : 0);
        stats.put("pertinenceMoyenne",    Math.round(pertMoy * 100));
        stats.put("fiabiliteMoyenne",     Math.round(facMoy  * 100));
        stats.put("hallucinationMoyenne", Math.round(hallMoy * 100));
        return stats;
    }

    @DeleteMapping("/clear")
    public Map<String, String> clear() {
        history.clear();
        return Map.of("status", "cleared");
    }

    @GetMapping("/health")
    public String health() { return "Agent Evaluateur operationnel sur port 8081"; }
}