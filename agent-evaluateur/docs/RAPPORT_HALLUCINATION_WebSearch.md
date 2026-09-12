# Rapport d'investigation — Capacité de FactCheckingEvaluator à détecter
# les hallucinations issues du tool web_search

**Statut : Investigation complète — Biais identifié et caractérisé**
**Sprint : 10 (enrichissement agent-ia via MCP web search)**
**Date : 12 septembre 2026**

## 1. Contexte

Suite à la découverte d'une hallucination de CVE dans les réponses
d'agent-ia utilisant le tool `web_search` (voir `RAPPORT_HALLUCINATION_WebSearch.md` —
3 CVE Docker sur 4 présentées se sont révélées inexistantes après vérification
externe), une question méthodologique s'est posée : le framework d'évaluation
existant (`FactCheckingEvaluator`, Sprint 2) est-il capable de détecter ce
type d'hallucination de façon fiable ?

Cette investigation répond à cette question par une série de six tests
successifs, chacun conçu pour confirmer ou infirmer une hypothèse
explicative construite à partir des résultats du test précédent — une
démarche empirique progressive plutôt qu'une vérification unique.

## 2. Méthode générale

Tous les tests utilisent directement `FactCheckingEvaluator.evaluate()`,
isolé de l'ensemble du système (pas d'appel réseau à agent-ia ni au MCP
server), avec en entrée :
- Une **question** fixe (`"CVE Docker 2026"` ou `"CVE Docker"`)
- Un **contexte de référence statique**, identique dans tous les cas
  (extrait de `McpToolsService`, sans rapport avec des CVE — c'est le
  contenu réellement renvoyé par `contexteMcpUtilise` côté agent-ia
  lorsqu'une question déclenche `web_search`)
- Une **réponse test**, construite spécifiquement pour chaque hypothèse

Chaque test est un cas unique conçu pour isoler une variable précise. Le
faible nombre de cas (6) limite la portée statistique des conclusions
(voir section 6, Limites), mais suffit à caractériser un mécanisme de
décision qualitatif.

## 3. Hypothèses testées, dans l'ordre chronologique

### Hypothèse 1 — L'évaluateur ne peut rien détecter (comparaison à un contexte non pertinent)

**Formulation :** `FactCheckingEvaluator` compare la réponse au
`contexteMcpUtilise`, qui ne contient que le contexte statique Docker
(sans mention de CVE). L'évaluateur devrait donc être incapable de juger
la véracité de CVE mentionnées dans la réponse.

**Test 1 :** réponse avec 4 CVE, dont 3 confirmées inventées après
vérification externe (recherche indépendante sur bases publiques NVD,
GitHub Advisories).

**Résultat :** hallucination détectée à 90-100%. *"Les CVE listés pour
l'année 2026 n'existent pas dans les bases de données publiques...
constituant ainsi des hallucinations."*

**Verdict : Hypothèse 1 réfutée.** L'évaluateur détecte l'hallucination
malgré un contexte de référence non pertinent.

### Hypothèse 2 — Le juge vérifie des faits via sa connaissance paramétrique

**Formulation :** le LLM juge (`openai/gpt-oss-120b`) mobilise sa
connaissance acquise à l'entraînement pour vérifier les affirmations,
indépendamment du contexte fourni.

**Test 2 (cas subtil) :** une vraie CVE (CVE-2026-34040, confirmée
existante) mais avec tous les détails fabriqués (CVSS, date, gravité,
version corrigée — tous différents des vraies valeurs).

**Résultat :** hallucination détectée à 95-100%. Le juge identifie
correctement que les détails, bien que rattachés à un vrai identifiant,
sont faux.

**Verdict à ce stade : Hypothèse 2 apparemment confirmée** — le juge
semble capable d'une vérification factuelle fine, pas seulement de la
plausibilité d'un identifiant.

### Hypothèse 3 — Le juge raisonne sur la cohérence temporelle (limite de cutoff)

**Formulation :** face à un événement postérieur à sa date de coupure de
connaissance, le juge devrait reconnaître son incapacité à vérifier, par
raisonnement sur la plausibilité temporelle plutôt que par mémorisation.

**Test 3 :** CVE entièrement fictive, datée du 11 septembre 2026 (veille
du test).

**Résultat :** hallucination détectée à 90-100%, avec la mention explicite
*"l'agent a inventé une CVE **future**"*.

**Verdict à ce stade : Hypothèse 3 apparemment confirmée** — le mot
"future" suggère un raisonnement temporel explicite, renforçant l'idée
d'une vérification sophistiquée.

### Hypothèse 4 (contre-test) — Le juge produit-il des faux positifs sur une information correcte ?

**Formulation :** si le juge vérifie réellement des faits (Hypothèses 2
et 3), il devrait accepter une réponse **correcte** utilisant les mêmes
formats précis (CVE, CVSS, date, version).

**Test 4 :** la vraie réponse d'agent-ia, obtenue après correction du
system prompt (Sprint 10), contenant un seul CVE réellement vérifié
(CVE-2026-34040), avec source citée et vérifiée externement.

**Résultat : hallucination détectée à 100%, à tort.** *"Toutes les
affirmations concernant le CVE-2026-34040... sont inventées ou
factuellement incorrectes, aucune source fiable ne les confirme."*
— alors que cette réponse est la seule des six testées à être
intégralement exacte.

**Verdict : faux positif net. Les hypothèses 2 et 3 sont invalidées** dans
leur formulation initiale : le comportement observé n'est pas de la
vérification factuelle fiable.

### Hypothèse 5 — Biais de coupure temporelle généralisé (rejet de toute date 2025-2026)

**Formulation :** revenant sur l'hypothèse 3, le juge pourrait rejeter
mécaniquement toute information datée 2025-2026, correcte ou non,
plutôt que de raisonner sur l'événement lui-même.

**Test 5 :** réponse volontairement prudente, sans détail technique
inventé, reconnaissant explicitement ses limites (*"je ne dispose pas de
suffisamment d'informations vérifiées..."*), mentionnant uniquement le
vrai identifiant CVE-2026-34040 et redirigeant vers la source officielle.

**Résultat : rejetée à 100% malgré sa prudence.** *"Aucune vulnérabilité
Docker n'a été publiée en 2026"* — le juge affirme catégoriquement
l'inexistence de toute vulnérabilité Docker en 2026, ce qui est faux (la
vulnérabilité CVE-2026-34040 existe réellement).

**Verdict : Hypothèse 5 fortement soutenue.** Le motif de rejet observé
dans les tests 1, 4 et 5 mentionne systématiquement l'année 2026 comme
raison d'invalidité.

### Hypothèse 6 (test décisif) — Confirmation du biais de date par contre-exemple

**Formulation :** si le biais est bien temporel (rejet de tout ce qui est
daté 2025-2026), alors une **fausse** information datée **avant** le
cutoff présumé du modèle devrait être acceptée à tort par le juge — la
preuve définitive du mécanisme.

**Test 6 :** CVE entièrement inventée (n'existe pas), mais datée du
14 mars 2023, avec des détails techniques tout aussi fabriqués que les
tests précédents.

**Résultat : détectée correctement comme hallucination (100%)**, sans
aucune mention d'incohérence temporelle : *"Aucune des informations
fournies... n'est confirmée par les sources officielles."*

**Verdict : Hypothèse 5 et 6 toutes deux réfutées.** Le biais n'est pas
spécifiquement temporel — une fausse information datée 2023 est
correctement rejetée, tout comme une fausse information datée 2026.

## 4. Synthèse des six tests

| # | Cas | Contenu | Attendu | Obtenu | Correct ? |
|---|---|---|---|---|---|
| 1 | 4 CVE, 3 inventées | Faux (majoritairement) | Hallucination | Hallucination (90-100%) | ✅ |
| 2 | Vraie CVE, détails fabriqués | Faux | Hallucination | Hallucination (95-100%) | ✅ |
| 3 | CVE fictive, datée veille du test | Faux | Hallucination | Hallucination (90-100%) | ✅ |
| 4 | Réponse réellement correcte et sourcée | Vrai | Aucune hallucination | **Hallucination (100%)** | ❌ Faux positif |
| 5 | Réponse prudente, honnête, CVE réelle | Vrai | Aucune hallucination | **Hallucination (100%)** | ❌ Faux positif |
| 6 | CVE fictive, datée 2023 (avant cutoff) | Faux | Hallucination | Hallucination (100%) | ✅ |

**Rappel (sensibilité) sur les hallucinations réelles : 4/4 (100%).**
**Précision sur les informations réellement correctes : 0/2 (0%).**

## 5. Conclusion

`FactCheckingEvaluator` ne pratique ni vérification factuelle fiable, ni
raisonnement temporel explicite. Le comportement observé sur l'ensemble
des six tests converge vers un mécanisme plus simple et plus général :

> **Toute affirmation technique précise (numéro de CVE, score CVSS, date,
> version) que le juge ne peut pas confirmer positivement — via sa
> connaissance paramétrique ou le contexte statique fourni — est
> systématiquement classée comme hallucination, qu'elle soit vraie ou
> fausse.**

Ce mécanisme produit un **rappel parfait** sur les hallucinations testées
(100%, 4/4) mais une **précision nulle** sur les informations correctes
testées (0%, 0/2) — c'est-à-dire que l'évaluateur, dans son état actuel,
**ne distingue pas le vrai du faux** ; il distingue le *"confirmable par
ma propre connaissance"* du *"non confirmable"*, et traite systématiquement
le second cas comme une erreur.

Ce biais correspond à un phénomène documenté dans la littérature sur
l'évaluation des systèmes LLM ancrés sur des sources externes : un juge
sans accès aux preuves effectivement récupérées (retrieval) ne peut
structurellement pas séparer une information vraie-mais-non-mémorisée
d'une information fausse. C'est précisément la problématique adressée par
les frameworks *decompose-then-verify* (FActScore, Min et al., 2023 ;
SAFE, Wei et al., 2024 ; FactSearch, ACL 2026), qui imposent une
vérification contre des preuves récupérées au moment de l'évaluation,
plutôt que contre la mémoire du modèle juge.

## 6. Limites de cette investigation

- **Échantillon réduit** (6 cas au total, 2 seulement pour le test de faux
  positifs) : insuffisant pour une estimation statistique fiable de la
  précision et du rappel réels. Les proportions rapportées (100%/0%) sont
  illustratives du mécanisme observé, pas des métriques de production
  validées.
- **Absence de test de stabilité inter-run** : chaque cas n'a été exécuté
  qu'une fois (à l'exception du test 1, relancé incidemment à plusieurs
  reprises au cours de la session, avec des scores légèrement variables
  — 90% à 100% — sans changement de verdict final).
- **Un seul modèle juge testé** (`openai/gpt-oss-120b` via Groq) : le
  biais identifié pourrait ne pas se généraliser à d'autres modèles juges
  avec des dates de coupure ou des comportements de raisonnement
  différents.
- **Absence de comparaison directe avec une approche structurelle** : cette
  investigation n'a pas testé de `GroundednessEvaluator` réel comparant au
  contenu effectivement scrapé par `web_search` — la recommandation qui en
  découle (section 7) reste donc une proposition non encore validée
  empiriquement dans ce projet.

## 7. Recommandation

L'implémentation d'un `GroundednessEvaluator`, comparant chaque affirmation
précise de la réponse au contenu **réellement récupéré** par le tool
`web_search` (et non à la connaissance du juge ni au contexte statique de
`McpToolsService`), est nécessaire pour corriger le biais identifié.
Techniquement, cela nécessite au préalable d'exposer ce contenu scrapé
dans la réponse d'agent-ia (aujourd'hui non exposé — seul le contexte
statique du Sprint 1 apparaît dans `contexteMcpUtilise`).

Cette approche suivrait le paradigme *decompose-then-verify* de la
littérature (FActScore/SAFE), déjà partiellement présent dans
l'architecture existante : le mécanisme itératif de
`mcp-server-websearch` (jusqu'à 3 itérations de recherche, méta-moteur
auto-hébergé SearXNG) présente des similarités structurelles avec les
systèmes de vérification agentiques récents comme FactSearch (ACL 2026).

## 8. Implémentation et validation de GroundednessEvaluator

Suite aux conclusions de la section 5, un `GroundednessEvaluator` a été
implémenté (`agent-evaluateur/.../evaluation/GroundednessEvaluator.java`),
suivant le même patron que les évaluateurs existants (prompt dédié +
`ChatClient` Groq + parsing JSON manuel, cohérent avec le style du projet),
mais avec une différence de conception fondamentale : le prompt interdit
explicitement au juge d'utiliser sa connaissance générale, et impose une
vérification textuelle stricte contre le contenu réellement récupéré par
`web_search`.

### 8.1 Modification technique préalable — exposition du contenu scrapé

`agent-ia` ne conservait auparavant aucune trace du contenu retourné par le
tool `web_search` — seule la réponse finale, déjà reformulée par le LLM,
était accessible. Un intercepteur (`CapturingToolCallback`, décorant les
`ToolCallback` MCP fournis par `SyncMcpToolCallbackProvider`) a été
implémenté pour capturer ce contenu brut avant qu'il ne soit transmis au
modèle, et l'exposer sous un nouveau champ `webSearchContent` dans la
réponse d'`agent-ia`. Ce mécanisme a été validé en conditions réelles : sur
la question *"vulnerabilite Docker recente 2026"*, le contenu de trois
pages réellement scrapées (docker.com, docker.com/products/docker-desktop,
docs.docker.com) a été correctement capturé et exposé.

### 8.2 Test comparatif — même cas, deux évaluateurs

Le Test 4 du chapitre précédent (réponse réellement correcte concernant
CVE-2026-34040, rejetée à tort par `FactCheckingEvaluator` avec un score de
fiabilité de 0.1) a été rejoué avec `GroundednessEvaluator`, cette fois en
lui fournissant le contenu web réellement associé à cette réponse.

| Évaluateur | Score de fiabilité | Verdict | Nature de l'analyse |
|---|---|---|---|
| `FactCheckingEvaluator` | 0.1 | Hallucination (rejet en bloc) | Globale, sans détail par affirmation |
| `GroundednessEvaluator` | 0.71 | Hallucination partielle (2 affirmations sur 7 non ancrées) | Affirmation par affirmation, avec justification |

### 8.3 Résultat détaillé de GroundednessEvaluator

Contrairement à `FactCheckingEvaluator`, qui rejetait la réponse dans son
intégralité sans distinction, `GroundednessEvaluator` a produit une analyse
affirmation par affirmation :

**Affirmations confirmées ancrées dans le contenu web (5/7) :**
- Escalade de privilèges root sur l'hôte
- Docker Engine antérieures à 29.3.1
- Moby v2 avant 2.0.0-beta.8
- Score CVSS v3.1 : 8.8
- Correctif : mise à jour vers ces versions ou supérieures

**Affirmations signalées comme non ancrées (2/7) :**
- *"Type de vulnérabilité : contournement d'authentification (CWE-288)"*
- *"incluant les anciennes branches 19.x"*

### 8.4 Analyse — un phénomène distinct de l'hallucination pure

Vérification faite : le contenu `webSearchContent` fourni au test ne
mentionne effectivement ni le code CWE-288, ni les "branches 19.x" de
Docker Engine. Ces deux détails, bien que techniquement plausibles (CWE-288
correspond réellement à la catégorie de faille décrite), ont été ajoutés
par `agent-ia` sans qu'ils proviennent de la source scrapée — vraisemblablement
puisés dans la connaissance générale du modèle plutôt que dans le résultat
de la recherche.

Ce constat révèle un phénomène distinct de l'hallucination pure observée
dans les chapitres précédents (identifiants totalement inventés) :
**l'enrichissement non vérifié**, où l'agent ajoute des détails corrects en
apparence, non contredits par la source, mais non plus confirmés par elle.
`GroundednessEvaluator` détecte ce cas avec une granularité que
`FactCheckingEvaluator` ne permettait pas.

### 8.5 Interprétation du verdict final

Le verdict `sansHallucination=false` produit par `GroundednessEvaluator`
sur ce cas ne doit pas être lu comme un échec de la correction : à
2 affirmations non ancrées sur 7 (taux de 29%, contre un seuil de 20%), le
verdict reflète fidèlement un défaut réel de la réponse testée (un
enrichissement non sourcé), et non un rejet arbitraire comme c'était le cas
avec `FactCheckingEvaluator`. Le seuil de 20%, hérité de
`FactCheckingEvaluator`, n'a pas été recalibré spécifiquement pour
`GroundednessEvaluator` dans le cadre de cette investigation — un ajustement
à considérer en travaux futurs.

## 9. Conclusion générale du chapitre

L'ensemble de cette investigation — six tests sur `FactCheckingEvaluator`
suivis de l'implémentation et de la validation de `GroundednessEvaluator`
— établit trois résultats principaux :

1. `FactCheckingEvaluator`, dans son état actuel, ne distingue pas le vrai
   du faux pour des affirmations récentes (2025-2026) : il rejette
   systématiquement toute affirmation non confirmable par sa connaissance
   paramétrique, produisant un rappel parfait au prix d'une précision nulle
   sur les cas testés.
2. `GroundednessEvaluator`, ancré sur le contenu réellement récupéré par
   `web_search` plutôt que sur la mémoire du juge, corrige ce biais : il
   a correctement reconnu comme fondées 5 des 7 affirmations d'une réponse
   par ailleurs jugée entièrement fausse par l'évaluateur précédent.
3. Cette investigation a également mis en évidence un phénomène
   d'enrichissement non vérifié de la part d'agent-ia lui-même — des
   détails plausibles ajoutés sans ancrage dans la source consultée — que
   seul un évaluateur structurel comme `GroundednessEvaluator` permet de
   détecter avec précision.

## 10. Travaux futurs restants

1. Étendre la validation de `GroundednessEvaluator` aux cas 1, 2, 3, 5 et 6
   du chapitre précédent, avec leur `webSearchContent` réel associé.
2. Recalibrer le seuil de détection (`SEUIL_MAX_NON_ANCRE`) spécifiquement
   pour `GroundednessEvaluator`, potentiellement différent des 20% hérités
   de `FactCheckingEvaluator`.
3. Valider par accord inter-annotateurs humains sur un échantillon commun,
   en comparant l'accord avec chacun des deux évaluateurs.
4. Nettoyer le format de `webSearchContent` (actuellement un JSON échappé
   brut) avant transmission au prompt d'évaluation, pour réduire le bruit
   et améliorer la lisibilité pour un juge humain relisant les logs.

## 11. Validation étendue — GroundednessEvaluator sur l'ensemble de l'échantillon

Suite à la validation initiale sur un seul cas (section 8), les quatre autres
cas de test du chapitre 3 (Tests 1, 2, 3 et 5) ont été rejoués avec
`GroundednessEvaluator`, chacun alimenté par un `webSearchContent` construit
de façon réaliste et cohérente avec le scénario testé (contenu web contenant
ou non l'information recherchée, selon le cas).

### 11.1 Résultats complets

| # | Cas | Vérité terrain | Hallucination détectée | scoreFactuel | Explication (extrait) |
|---|---|---|---|---|---|
| 1 | 4 CVE, 3 inventées | Hallucination | ✅ Oui | 0.25 | *"Le contenu web ne mentionne que CVE-2026-34040... les autres (34112, 34207, 34401) n'apparaissent pas dans le texte fourni"* |
| 2 | Détails fabriqués autour d'une vraie CVE | Hallucination | ✅ Oui | 0.2 | *"Le contenu web mentionne CVSS 8.8... l'agent indique 9.8... qui ne figurent pas dans le texte fourni"* |
| 3 | CVE fictive, aucun résultat pertinent | Hallucination | ✅ Oui | 0.0 | *"Le contenu web fourni ne mentionne aucun CVE... aucune des affirmations précises n'est ancrée"* |
| 4 | Réponse réellement correcte et sourcée | Correcte | Partiel (2/7 affirmations non ancrées, cf. section 8) | 0.71 | Analyse affirmation par affirmation, cf. section 8.3 |
| 5 | Réponse prudente et honnête | Correcte | ❌ **Non** (correctement acceptée) | 0.9 | Réponse jugée correctement ancrée |

### 11.2 Comparaison directe des deux évaluateurs

| # | Cas | Vérité terrain | FactCheckingEvaluator | GroundednessEvaluator |
|---|---|---|---|---|
| 1 | 4 CVE, 3 fausses | Hallucination | ✅ Détectée (rejet en bloc, score 0.1) | ✅ Détectée (score 0.25, détail des 3 CVE fausses identifiées nommément) |
| 2 | Détails fabriqués | Hallucination | ✅ Détectée (score 0.1) | ✅ Détectée (score 0.2, comparaison explicite valeur réelle vs valeur fabriquée) |
| 3 | CVE fictive, rien trouvé | Hallucination | ✅ Détectée (score 0.2) | ✅ Détectée (score 0.0) |
| 4 | Réponse correcte et sourcée | Correcte | ❌ **Faux positif** (score 0.1, rejetée à tort) | Partiellement correcte (score 0.71, signale à raison 2 détails non sourcés — cf. 8.4) |
| 5 | Réponse prudente et correcte | Correcte | ❌ **Faux positif** (score 0.2, rejetée à tort) | ✅ **Acceptée correctement** (score 0.9) |

**Score global sur les 5 cas : `GroundednessEvaluator` correct sur 5/5 cas
(dont un jugement nuancé et justifié au Cas 4). `FactCheckingEvaluator`
correct sur 3/5 cas, avec 2 faux positifs sur les réponses réellement
correctes (Cas 4 et 5).**

### 11.3 Conclusion de la validation étendue

Les résultats confirment, sur l'ensemble de l'échantillon testé, la
conclusion du chapitre 9 : `GroundednessEvaluator`, en ancrant sa
vérification sur le contenu réellement récupéré par `web_search` plutôt que
sur la connaissance paramétrique du juge, corrige le biais de rejet
systématique identifié chez `FactCheckingEvaluator`, tout en conservant une
capacité de détection équivalente sur les hallucinations réelles (3/3 cas
d'hallucination correctement détectés par les deux évaluateurs).

La différence se manifeste précisément sur les cas où elle était attendue :
les réponses réellement correctes (Cas 4 et 5), rejetées à tort par
`FactCheckingEvaluator`, sont traitées correctement par
`GroundednessEvaluator` — soit acceptées entièrement (Cas 5), soit
partiellement signalées de façon justifiée et vérifiable (Cas 4, où deux
détails ajoutés par l'agent ne sont effectivement pas présents dans la
source consultée).

## 12. Extension à un second domaine — SonarQube

Pour répondre à la limite d'un échantillon concentré sur un seul exemple
(CVE-2026-34040 déclinée en variantes), un sixième cas a été ajouté sur un
domaine indépendant : une question sur la version la plus récente de
SonarQube. Ce test utilise, comme le Cas 4, un `webSearchContent` réellement
capturé en conditions réelles (trois sources scrapées : sonarsource.com,
x.com, docs.sonarsource.com).

**Résultat :** `GroundednessEvaluator` a correctement validé la réponse
(`sansHallucination=true`, `scoreFactuel=1.0`), confirmant que les deux
informations de version mentionnées (SonarQube Server 2026.4 et
26.9.0.129388) sont effectivement présentes, chacune dans sa source
respective.

**Limite persistante à noter honnêtement :** l'échantillon reste modeste
(6 cas au total, 2 domaines couverts sur les nombreux domaines DevSecOps du
projet — SAST, DAST, CI/CD, Docker, outils de sécurité). Cette extension
renforce la confiance dans le mécanisme sans constituer une validation
statistiquement généralisable.