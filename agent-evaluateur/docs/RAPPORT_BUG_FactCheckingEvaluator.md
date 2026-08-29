# Rapport d'incident — Incohérence logique dans FactCheckingEvaluator

**Projet :** PFE DevSecOps — Infrastructure Testing Automatisé et Sécurité des Agents IA
**Composant :** `agent-evaluateur` — Sprint 2 (US3/US4, Model Evaluation)
**Sévérité :** Moyenne (faux négatifs sur le batch d'évaluation, aucun impact sécurité)
**Statut :** Résolu

---

## 1. Contexte

Dans le cadre de l'US4 (mise en pratique du Model Evaluation), un dataset de 10 cas de test a été exécuté automatiquement via l'endpoint `/api/evaluateur/batch`. Ce batch appelle successivement l'Agent IA, puis évalue chaque réponse avec `RelevancyEvaluator` (pertinence) et `FactCheckingEvaluator` (détection d'hallucinations), avant de calculer un verdict consolidé.

## 2. Symptôme observé

Plusieurs cas de test attendus comme `APPROUVE` étaient systématiquement classés `REJETE — Hallucinations détectées`, alors même que les logs de debug du `FactCheckingEvaluator` indiquaient un taux d'hallucination sous le seuil autorisé.

Exemple représentatif (cas TC-10, avant correction) :

```
DEBUG FactCheckingEvaluator - Fiabilite: 95% | Hallucination: 0% | OK: OUI
INFO  EvaluateurAgentService - Score Factuel : 95%
INFO  EvaluateurAgentService - VERDICT FINAL : REJETE — Hallucinations détectées
```

Le log affichait `OK: OUI` (taux d'hallucination de 0 %, largement sous le seuil de 20 %), mais le verdict final rejetait pourtant la réponse pour hallucination. Cette contradiction rendait le taux de conformité du batch artificiellement bas (30 % puis 50 % selon les exécutions) et non représentatif de la qualité réelle de l'Agent IA.

## 3. Démarche de diagnostic

L'hypothèse initiale portait sur un possible mélange de champs entre les objets `EvaluationResult` retournés par `RelevancyEvaluator` et `FactCheckingEvaluator` (les deux classes utilisant le même record positionnel). Une analyse assistée par Claude Code a permis d'écarter cette piste : les cinq fichiers concernés (`EvaluationResult`, les deux évaluateurs, `EvaluateurAgentService`, `VerdictEvaluation`) construisaient et lisaient correctement les objets, sans inversion positionnelle.

Le vrai problème se trouvait localisé dans une seule méthode : `FactCheckingEvaluator.parseResult()`.

## 4. Cause racine

Le LLM juge retourne deux informations indépendantes dans sa réponse JSON :
- `taux_hallucination` (un score continu 0–100 %)
- `factuellemement_correct` (un booléen global de jugement du LLM)

Le code calculait correctement `sousLeSeuil` (le taux d'hallucination comparé au seuil `SEUIL_MAX_HALLUCINATION`) et l'utilisait pour le message de log. Mais la valeur réellement **stockée** dans l'objet `EvaluationResult` — celle qui alimente ensuite `VerdictEvaluation` et `determinerVerdict()` — combinait silencieusement les deux critères :

```java
boolean sousLeSeuil = tauxHallucination <= SEUIL_MAX_HALLUCINATION;

log.debug("Fiabilite: {}% | Hallucination: {}% | OK: {}",
        Math.round(scorefiabilite * 100), Math.round(tauxHallucination * 100),
        sousLeSeuil ? "OUI" : "NON");                 // log : sousLeSeuil seul

return new EvaluationResult(
        testId, question, reponseAgent,
        true, factuel && sousLeSeuil,                 // stocké : factuel ET sousLeSeuil
        1.0, scorefiabilite,
        explication);
```

Le champ `sansHallucination` ne mesurait donc pas uniquement le taux d'hallucination (comme son nom l'indique et comme le seuil `SEUIL_MAX_HALLUCINATION` le suggère), mais aussi un second critère (`factuellemement_correct`) jamais visible dans les logs. Dès que le LLM jugeait la réponse « pas totalement correcte » pour une nuance quelconque — même avec un taux d'hallucination nul — le verdict final basculait en rejet, sans que rien dans les logs ne l'explique.

## 5. Correction appliquée

Le correctif aligne la valeur stockée sur ce que le nom du champ et le log annoncent : un seuil pur sur le taux d'hallucination. Le critère `factuellemement_correct` est conservé mais loggé séparément, pour rester visible sans influencer silencieusement le verdict.

```java
boolean sousLeSeuil = tauxHallucination <= SEUIL_MAX_HALLUCINATION;

log.debug("Fiabilite: {}% | Hallucination: {}% | Factuel: {} | OK: {}",
        Math.round(scorefiabilite * 100), Math.round(tauxHallucination * 100),
        factuel ? "OUI" : "NON",
        sousLeSeuil ? "OUI" : "NON");

return new EvaluationResult(
        testId, question, reponseAgent,
        true, sousLeSeuil,
        1.0, scorefiabilite,
        explication);
```

## 6. Validation

Après correction, le batch de 10 cas de test a été rejoué. Les logs affichent désormais la cohérence attendue :

```
DEBUG FactCheckingEvaluator - Fiabilite: 82% | Hallucination: 15% | Factuel: NON | OK: OUI
INFO  EvaluateurAgentService - VERDICT FINAL : APPROUVE — Réponse pertinente et fiable
```

Le taux de conformité du batch, initialement faussé à 30–50 %, s'est stabilisé à **70 %**, un résultat cohérent avec les scores individuels observés et représentatif de la qualité réelle de l'Agent IA évalué.

## 7. Enseignement retenu

Un log de debug affichant une valeur intermédiaire du calcul (`sousLeSeuil`) sans afficher la valeur finale réellement stockée (`factuel && sousLeSeuil`) a masqué le bug pendant plusieurs itérations de test, faisant porter à tort le soupçon sur la qualité du dataset et des prompts d'évaluation plutôt que sur le code lui-même. Ce cas illustre l'intérêt de toujours logger la valeur exacte qui est effectivement propagée dans le système, plutôt qu'une valeur intermédiaire dont le nom peut induire en erreur lors du débogage.
