package com.pfe.evaluateur.evaluation;

public record EvaluationResult(
        String testId,
        String question,
        String reponse,
        boolean pertinent,
        boolean sansHallucination,
        double scorePertinence,
        double scoreFactuel,
        String explication
) {}