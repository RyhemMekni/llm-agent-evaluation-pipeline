package com.pfe.evaluateur.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Expose des metriques metier custom via Micrometer/Prometheus (US18).
 * Suit les scores d'evaluation dans le temps pour visualisation Grafana.
 */
@Service
public class MetricsService {

    private final MeterRegistry meterRegistry;

    private final AtomicInteger dernierTauxConformite = new AtomicInteger(0);
    private final AtomicInteger dernierTauxResistancePromptInjection = new AtomicInteger(0);

    private final Counter evaluationsTotal;
    private final Counter evaluationsApprouvees;
    private final Counter evaluationsRejetees;

    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Gauges - dernier score connu (pour graphe d'evolution dans le temps)
        meterRegistry.gauge("agent_evaluateur_taux_conformite_pourcent", dernierTauxConformite);
        meterRegistry.gauge("agent_evaluateur_taux_resistance_prompt_injection_pourcent",
                dernierTauxResistancePromptInjection);

        // Counters - cumul total depuis le demarrage
        this.evaluationsTotal = Counter.builder("agent_evaluateur_evaluations_total")
                .description("Nombre total d'evaluations executees")
                .register(meterRegistry);

        this.evaluationsApprouvees = Counter.builder("agent_evaluateur_evaluations_verdict_total")
                .tag("verdict", "approuve")
                .description("Nombre d'evaluations approuvees")
                .register(meterRegistry);

        this.evaluationsRejetees = Counter.builder("agent_evaluateur_evaluations_verdict_total")
                .tag("verdict", "rejete")
                .description("Nombre d'evaluations rejetees")
                .register(meterRegistry);
    }

    public void enregistrerEvaluation(boolean approuve) {
        evaluationsTotal.increment();
        if (approuve) {
            evaluationsApprouvees.increment();
        } else {
            evaluationsRejetees.increment();
        }
    }

    public void enregistrerTauxConformiteBatch(int tauxPourcent) {
        dernierTauxConformite.set(tauxPourcent);
    }

    public void enregistrerTauxResistancePromptInjection(int tauxPourcent) {
        dernierTauxResistancePromptInjection.set(tauxPourcent);
    }
}