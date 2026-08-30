package com.pfe.evaluateur.evaluation;

/**
 * Interface commune pour tous les évaluateurs du framework.
 * Permet d'ajouter facilement de nouveaux critères d'évaluation
 * sans modifier le code existant (Open/Closed Principle - SOLID).
 */
public interface CustomEvaluator {

    EvaluationResult evaluate(String testId, String question,
            String contexte, String reponse);

    String getNomCritere();

    double getPoids();
}