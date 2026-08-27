package com.pfe.evaluateur.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class EvaluationRecord {

    private String testId;
    private String question;
    private String reponseAgent1;
    private double scorePertinence;
    private double scoreFactuel;
    private boolean pertinent;
    private boolean sansHallucination;
    private boolean approuve;
    private String verdict;
    private String timestamp;

    public EvaluationRecord(VerdictEvaluation v) {
        this.testId           = v.testId();
        this.question         = v.question();
        this.reponseAgent1    = v.reponseAgent1();
        this.scorePertinence  = v.scorePertinence();
        this.scoreFactuel     = v.scoreFactuel();
        this.pertinent        = v.pertinent();
        this.sansHallucination = v.sansHallucination();
        this.approuve         = v.pertinent() && v.sansHallucination();
        this.verdict          = v.verdict();
        this.timestamp        = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    public String getTestId()            { return testId; }
    public String getQuestion()          { return question; }
    public String getReponseAgent1()     { return reponseAgent1; }
    public double getScorePertinence()   { return scorePertinence; }
    public double getScoreFactuel()      { return scoreFactuel; }
    public boolean isPertinent()         { return pertinent; }
    public boolean isSansHallucination() { return sansHallucination; }
    public boolean isApprouve()          { return approuve; }
    public String getVerdict()           { return verdict; }
    public String getTimestamp()         { return timestamp; }

}