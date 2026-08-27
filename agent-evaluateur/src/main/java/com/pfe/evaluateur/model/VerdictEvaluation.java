package com.pfe.evaluateur.model;

public record VerdictEvaluation(
        String testId,
        String question,
        String reponseAgent1,
        double scorePertinence,
        boolean pertinent,
        double scoreFactuel,
        boolean sansHallucination,
        String explications,
        String verdict
) {

    // Builder manuel simple
    public static Builder builder() { return new Builder(); }

    public static VerdictEvaluation erreur(String testId, String question, String message) {
        return new VerdictEvaluation(
                testId, question, "N/A",
                0.0, false, 0.0, false,
                message, "❌ ERREUR — " + message
        );
    }

    public static class Builder {
        private String testId, question, reponseAgent1, explications, verdict;
        private double scorePertinence, scoreFactuel;
        private boolean pertinent, sansHallucination;

        public Builder testId(String v)           { this.testId = v; return this; }
        public Builder question(String v)         { this.question = v; return this; }
        public Builder reponseAgent1(String v)    { this.reponseAgent1 = v; return this; }
        public Builder scorePertinence(double v)  { this.scorePertinence = v; return this; }
        public Builder pertinent(boolean v)       { this.pertinent = v; return this; }
        public Builder scoreFactuel(double v)     { this.scoreFactuel = v; return this; }
        public Builder sansHallucination(boolean v) { this.sansHallucination = v; return this; }
        public Builder explications(String v)     { this.explications = v; return this; }
        public Builder verdict(String v)          { this.verdict = v; return this; }

        public VerdictEvaluation build() {
            return new VerdictEvaluation(
                    testId, question, reponseAgent1,
                    scorePertinence, pertinent,
                    scoreFactuel, sansHallucination,
                    explications, verdict
            );
        }
    }
}