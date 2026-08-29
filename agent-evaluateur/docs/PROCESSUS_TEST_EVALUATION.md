# Processus de Test & Évaluation — Agent IA DevSecOps

**Sprint 2 — US4 : Mise en pratique Model Evaluation**
**Auteur :** Ryhem Mekni
**Projet :** PFE DevSecOps — Infrastructure Testing Automatisé et Sécurité des Agents IA

---

## 1. Objectif

Définir une méthode reproductible pour évaluer la qualité des réponses produites par l'Agent IA (Sprint 1), à l'aide de l'Agent Évaluateur (architecture A2A). Chaque évaluation combine deux dimensions :

- **Pertinence** (`RelevancyEvaluator`) : la réponse répond-elle réellement à la question posée ?
- **Factualité** (`FactCheckingEvaluator`) : la réponse contient-elle des hallucinations (informations inventées ou incorrectes) ?

---

## 2. Structure d'un cas de test

Chaque cas de test du dataset est décrit par les champs suivants :

| Champ | Type | Description |
|---|---|---|
| `id` | string | Identifiant unique du cas (ex. `TC-01`) |
| `domaine` | string | Domaine métier couvert (ex. `SAST`, `DAST`, `CI/CD`, `Spring AI`) |
| `type` | string | `positif` (question dans le périmètre) ou `negatif` (question hors périmètre / piège) |
| `question` | string | Question posée à l'Agent IA |
| `contexte_reference` | string | Contexte de vérité utilisé par le `FactCheckingEvaluator` pour juger la factualité |
| `resultat_attendu` | string | Comportement attendu : `APPROUVE` ou `REJETE` |
| `justification` | string | Pourquoi ce cas teste ce qu'il teste |

---

## 3. Catégories de cas de test

### 3.1 Cas positifs
Questions **dans le périmètre métier** de l'agent (DevSecOps, CI/CD, SAST/DAST, Spring AI, sécurité des agents IA, Docker, tests). On attend une réponse **pertinente et factuelle**, donc un verdict `APPROUVE`.

### 3.2 Cas négatifs
Deux sous-catégories :

- **Hors périmètre** : questions sans lien avec le DevSecOps (cuisine, sport, histoire...). L'agent doit décliner poliment (`"Je suis uniquement expert DevSecOps..."`), donc le verdict attendu est `REJETE` (non pertinent par rapport à une vraie réponse technique, ou correctement refusé).
- **Pièges factuels** : questions dans le périmètre mais formulées pour inciter l'agent à halluciner (dates précises, chiffres non vérifiables, versions logicielles exactes). Sert à mesurer le taux d'hallucination réel.

---

## 4. Seuils de décision

Configurés dans `RelevancyEvaluator` / `FactCheckingEvaluator` et repris dans le pipeline CI/CD (`quality-gate`) :

| Métrique | Seuil | Effet si dépassé |
|---|---|---|
| Score de pertinence | ≥ 80 % | Sous ce seuil → `pertinent = false` |
| Taux d'hallucination | ≤ 20 % | Au-dessus → `sansHallucination = false` |

Verdict final (`EvaluateurAgentService.determinerVerdict`) :

```
pertinent && sansHallucination        → ✅ APPROUVÉ
!pertinent && !sansHallucination      → ❌ REJETÉ (non pertinent ET halluciné)
!pertinent                             → ⚠️ REJETÉ (non pertinent)
!sansHallucination                     → ⚠️ REJETÉ (hallucination détectée)
```

---

## 5. Exécution d'une évaluation

**Manuelle (test unitaire) :**
```
GET http://localhost:8081/api/evaluateur/evaluer
    ?testId=TC-01
    &question=<question encodée URL>
    &contexte=<contexte de référence encodé URL>
```

**Automatisée (pipeline CI/CD) :**
Le stage `evaluate` du `.gitlab-ci.yml` démarre l'Agent IA (port 8080) et l'Agent Évaluateur (port 8081), puis exécute un ou plusieurs cas du dataset via `curl`. Le résultat JSON est journalisé et peut alimenter un futur `quality-gate` bloquant.

**Batch (dataset complet) :**
Voir `datasets/dataset_evaluation.json` — chaque entrée peut être rejouée automatiquement via un script qui itère sur le fichier et appelle l'endpoint `/api/evaluateur/evaluer` pour chaque cas.

---

## 6. Traçabilité

Chaque évaluation est journalisée dans `agent-evaluateur/logs/agent-evaluateur.log` avec :
- La question posée
- La réponse brute de l'Agent IA
- Les scores de pertinence et de factualité
- Le verdict final

Cette traçabilité permet l'audit et sert de preuve de fonctionnement pour la soutenance PFE.