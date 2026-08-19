# Contexte DevSecOps — Agent IA PFE

## CI/CD Pipeline GitLab
- GitLab CI/CD permet l'automatisation des pipelines de build, test et déploiement
- Les stages principaux sont : build, test, evaluate, quality-gate, deploy, sast
- Les quality gates bloquent le pipeline si les seuils ne sont pas atteints
- Le déploiement Blue/Green permet un rollback automatique en cas de problème

## Sécurité Applicative SAST
- SonarQube analyse le code statiquement pour détecter les vulnérabilités
- Les vulnérabilités détectées incluent : injection SQL, XSS, secrets exposés
- OWASP Dependency-Check identifie les librairies avec des CVE connues
- Le quality gate bloque le build si une vulnérabilité critique est détectée (CVSS > 9)

## Sécurité Applicative DAST
- OWASP ZAP effectue des tests dynamiques sur les endpoints API
- Les tests incluent : injection SQL, NoSQL, XSS, DoS
- ZAP scanne les endpoints en mode daemon sur le port 8090
- Les résultats sont intégrés dans le reporting du pipeline CI/CD

## Sécurité Spécifique IA
- Le prompt injection testing détecte les tentatives de jailbreak
- La sanitization des entrées utilisateur protège contre les injections
- Les fuites de données sensibles sont testées via les outils MCP
- Le seuil maximum d'hallucination est fixé à 20%

## Évaluation des Agents IA
- RelevancyEvaluator mesure la pertinence des réponses (seuil minimum 80%)
- FactCheckingEvaluator détecte les hallucinations via Bespoke Minicheck
- Les Custom Evaluators vérifient la conformité, le ton et le format
- Les benchmarks comparent les performances entre différents LLMs

## Spring AI et Spring Boot
- Spring AI permet l'intégration des LLMs dans les applications Java
- L'API Groq est compatible OpenAI et utilise le modèle llama-3.3-70b-versatile
- Le ChatClient construit les prompts et appelle le LLM
- Les évaluateurs Spring AI utilisent un LLM juge pour noter les réponses

## Docker et Conteneurisation
- Docker Compose orchestre tous les services : GitLab, Runner, SonarQube, ZAP
- Les containers communiquent via le réseau Docker interne
- Le GitLab Runner exécute les pipelines en mode privileged
- Les volumes persistent les données entre les redémarrages

## Tests Automatisés
- Les tests unitaires utilisent JUnit 5 et Mockito pour mocker les appels LLM
- Les tests d'intégration valident les interactions agent-LLM-MCP
- Les tests end-to-end simulent des scénarios métier complets
- Le chaos engineering teste la résilience face aux défaillances LLM