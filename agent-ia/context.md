# Contexte DevSecOps - Agent IA PFE

## CI/CD Pipeline GitLab

- GitLab CI/CD permet l'automatisation des pipelines de build, test et d�ploiement
- Les stages principaux sont : build, test, evaluate, quality-gate, deploy, sast
- Les quality gates bloquent le pipeline si les seuils ne sont pas atteints
- Le d�ploiement Blue/Green permet un rollback automatique en cas de probl�me

## S�curit� Applicative SAST

- SonarQube analyse le code statiquement pour d�tecter les vuln�rabilit�s
- Les vuln�rabilit�s d�tect�es incluent : injection SQL, XSS, secrets expos�s
- OWASP Dependency-Check identifie les librairies avec des CVE connues
- Le quality gate bloque le build si une vuln�rabilit� critique est d�tect�e (CVSS > 9)

## S�curit� Applicative DAST

- OWASP ZAP effectue des tests dynamiques sur les endpoints API
- Les tests incluent : injection SQL, NoSQL, XSS, DoS
- ZAP scanne les endpoints en mode daemon sur le port 8090
- Les r�sultats sont int�gr�s dans le reporting du pipeline CI/CD

## S�curit� Sp�cifique IA

- Le prompt injection testing d�tecte les tentatives de jailbreak
- La sanitization des entr�es utilisateur prot�ge contre les injections
- Les fuites de donn�es sensibles sont test�es via les outils MCP
- Le seuil maximum d'hallucination est fix� � 20%

## �valuation des Agents IA

- RelevancyEvaluator mesure la pertinence des r�ponses (seuil minimum 80%)
- FactCheckingEvaluator d�tecte les hallucinations via Bespoke Minicheck
- Les Custom Evaluators v�rifient la conformit�, le ton et le format
- Les benchmarks comparent les performances entre diff�rents LLMs

## Spring AI et Spring Boot

- Spring AI permet l'int�gration des LLMs dans les applications Java
- L'API Groq est compatible OpenAI et utilise le mod�le llama-3.3-70b-versatile
- Le ChatClient construit les prompts et appelle le LLM
- Les �valuateurs Spring AI utilisent un LLM juge pour noter les r�ponses

## Docker et Conteneurisation

- Docker Compose orchestre tous les services : GitLab, Runner, SonarQube, ZAP
- Les containers communiquent via le r�seau Docker interne
- Le GitLab Runner ex�cute les pipelines en mode privileged
- Les volumes persistent les donn�es entre les red�marrages

## Tests Automatis�s

- Les tests unitaires utilisent JUnit 5 et Mockito pour mocker les appels LLM
- Les tests d'int�gration valident les interactions agent-LLM-MCP
- Les tests end-to-end simulent des sc�narios m�tier complets
- Le chaos engineering teste la r�silience face aux d�faillances LLM
