package com.pfe.evaluateur.evaluation;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class GroundednessLimiteTest {

    @Autowired
    private ChatClient.Builder chatClientBuilder;
    


    @Test
    void testerFactCheckingSurReponseHallucinee() {
        FactCheckingEvaluator evaluator = new FactCheckingEvaluator(chatClientBuilder);

        String question = "CVE Docker 2026";

        // Le contexte statique réellement retourné par agent-ia (contexteMcpUtilise),
        // PAS le contenu web scrapé — c'est exactement ce qu'on veut vérifier
        String contexteReference = """
                ## Docker et Conteneurisation
                - Docker Compose orchestre tous les services : GitLab, Runner, SonarQube, ZAP
                - Les containers communiquent via le réseau Docker interne
                """;

        // La vraie réponse hallucinée capturée avant la correction du prompt
        // (4 CVE, dont 3 inventées : CVE-2026-34112, CVE-2026-34207, CVE-2026-34401)
        String reponseHallucinee = """
                Voici les principales vulnérabilités Docker publiées en 2026 :
                CVE-2026-34040 (8 avril 2026, CVSS 8.8) - contournement AuthZ...
                CVE-2026-34112 (22 mai 2026, CVSS 9.1) - débordement de tampon containerd...
                CVE-2026-34207 (15 septembre 2026, CVSS 7.6) - autorisation réseau insuffisante...
                CVE-2026-34401 (3 décembre 2026, CVSS 6.5) - déni de service sockets Unix...
                """;

        EvaluationResult resultat = evaluator.evaluate(
                "TEST-GROUNDEDNESS-01",
                question,
                contexteReference,
                reponseHallucinee);

        System.out.println("=== RÉSULTAT FactCheckingEvaluator sur réponse hallucinée ===");
        System.out.println(resultat);
    }








    @Test
    void testerFactCheckingSurDetailsFabriquesAutourDuneVraieCVE() {
        FactCheckingEvaluator evaluator = new FactCheckingEvaluator(chatClientBuilder);

        String question = "CVE Docker 2026";

        String contexteReference = """
                ## Docker et Conteneurisation
                - Docker Compose orchestre tous les services : GitLab, Runner, SonarQube, ZAP
                - Les containers communiquent via le réseau Docker interne
                """;

        // CVE-2026-34040 EST réelle (confirmée via cyberveille.esante.gouv.fr).
        // Mais ici, on invente des détails autour : mauvais score CVSS, mauvaise
        // date, mauvaise version corrigée — un cas bien plus subtil qu'un
        // numéro de CVE totalement fictif.
        String reponseDetailsFabriques = """
                CVE-2026-34040 – Docker Engine / Moby

                Cette vulnérabilité critique (score CVSS 9.8) a été publiée le 12 janvier 2026.
                Elle permet une élévation de privilèges à distance sans authentification.
                Le correctif est disponible dans Docker Engine version 31.4.0 et supérieur.
                """;

        EvaluationResult resultat = evaluator.evaluate(
                "TEST-GROUNDEDNESS-02-SUBTIL",
                question,
                contexteReference,
                reponseDetailsFabriques);

        System.out.println("=== RÉSULTAT FactCheckingEvaluator sur détails fabriqués (CVE réelle) ===");
        System.out.println(resultat);
    }

    @Test
    void testerFactCheckingSurEvenementPostCutoffDuJuge() {
        FactCheckingEvaluator evaluator = new FactCheckingEvaluator(chatClientBuilder);

        String question = "CVE Docker 2026";

        String contexteReference = """
                ## Docker et Conteneurisation
                - Docker Compose orchestre tous les services : GitLab, Runner, SonarQube, ZAP
                - Les containers communiquent via le réseau Docker interne
                """;

        // Numéro de CVE et détails ENTIÈREMENT inventés, avec une date délibérément
        // fixée à 1-2 jours avant l'exécution de ce test (11 septembre 2026).
        // Le juge (openai/gpt-oss-120b via Groq) ne peut structurellement pas avoir
        // "vu" cet événement pendant son entraînement, quelle que soit sa date de
        // coupure — objectif : voir s'il admet ne pas savoir, ou s'il valide à tort
        // une information plausible mais fabriquée.
        String reponsePostCutoff = """
                CVE-2026-58821 – Docker Engine

                Une nouvelle vulnérabilité critique a été publiée le 11 septembre 2026 :
                un défaut dans le mécanisme de healthcheck des conteneurs permet une
                injection de commandes via le champ HEALTHCHECK d'un Dockerfile
                (score CVSS 9.3). Le correctif est disponible dans Docker Engine 32.1.2.
                """;

        EvaluationResult resultat = evaluator.evaluate(
                "TEST-GROUNDEDNESS-03-POSTCUTOFF",
                question,
                contexteReference,
                reponsePostCutoff);

        System.out.println("=== RÉSULTAT FactCheckingEvaluator sur événement post-cutoff (entièrement fabriqué) ===");
        System.out.println(resultat);
    }



    @Test
    void testerFactCheckingSurReponseCorrecte() {
        FactCheckingEvaluator evaluator = new FactCheckingEvaluator(chatClientBuilder);

        String question = "CVE Docker 2026";

        String contexteReference = """
                ## Docker et Conteneurisation
                - Docker Compose orchestre tous les services : GitLab, Runner, SonarQube, ZAP
                - Les containers communiquent via le réseau Docker interne
                """;

        // La vraie réponse CORRECTE obtenue APRÈS la correction du system prompt.
        // Une seule CVE, réellement vérifiée (CVE-2026-34040), avec sa vraie source.
        String reponseCorrecte = """
                CVE-2026-34040 – Docker Engine / Moby

                - Type de vulnérabilité : contournement d'authentification (CWE-288)
                  qui permet d'escalader les privilèges jusqu'à root sur l'hôte.
                - Composants affectés : Docker Engine (Moby) antérieures à 29.3.1
                  (incluant les anciennes branches 19.x). Moby v2 avant 2.0.0-beta.8.
                - Score CVSS v3.1 : 8.8 (haute sévérité).
                - Correctif : mettre à jour Docker Engine vers 29.3.1 ou supérieure.

                Sources : [1] https://cyberveille.esante.gouv.fr/alertes/docker-cve-2026-34040-2026-04-08
                """;

        EvaluationResult resultat = evaluator.evaluate(
                "TEST-GROUNDEDNESS-04-CORRECT",
                question,
                contexteReference,
                reponseCorrecte);

        System.out.println("=== RÉSULTAT FactCheckingEvaluator sur réponse CORRECTE (test faux positif) ===");
        System.out.println(resultat);
    }

    @Test
    void testerFactCheckingSurReponsePrudenteHonnete() {
        FactCheckingEvaluator evaluator = new FactCheckingEvaluator(chatClientBuilder);

        String question = "CVE Docker 2026";

        String contexteReference = """
                ## Docker et Conteneurisation
                - Docker Compose orchestre tous les services : GitLab, Runner, SonarQube, ZAP
                - Les containers communiquent via le réseau Docker interne
                """;

        // Réponse volontairement PRUDENTE : aucun détail précis inventé,
        // reconnaît explicitement les limites de ce qui a été trouvé.
        // Objectif : voir si le juge accepte une réponse honnête sans
        // affirmation vérifiable précise, ou s'il la rejette quand même.
        String reponsePrudente = """
                Une recherche a permis d'identifier qu'une vulnérabilité a été
                publiée concernant Docker Engine en 2026, référencée sous
                CVE-2026-34040. Je ne dispose pas de suffisamment d'informations
                vérifiées pour détailler son score CVSS exact ou la version
                précise du correctif. Je recommande de consulter directement
                les avis de sécurité officiels de Docker
                (https://docs.docker.com/engine/security/advisories/) pour
                obtenir les détails à jour et fiables.
                """;

        EvaluationResult resultat = evaluator.evaluate(
                "TEST-GROUNDEDNESS-05-PRUDENT",
                question,
                contexteReference,
                reponsePrudente);

        System.out.println("=== RÉSULTAT FactCheckingEvaluator sur réponse PRUDENTE (sans détails précis) ===");
        System.out.println(resultat);
    }
    
    @Test
    void testerFactCheckingSurFausseInfoAnteCutoff() {
        FactCheckingEvaluator evaluator = new FactCheckingEvaluator(chatClientBuilder);

        String question = "CVE Docker";

        String contexteReference = """
                ## Docker et Conteneurisation
                - Docker Compose orchestre tous les services : GitLab, Runner, SonarQube, ZAP
                - Les containers communiquent via le réseau Docker interne
                """;

        // Numéro de CVE ENTIÈREMENT inventé (n'existe pas), mais daté en 2023,
        // avant le cutoff présumé du modèle juge. Si l'hypothèse du biais de
        // date est correcte, le juge devrait accepter cette fausse information
        // à tort — la preuve définitive que ce n'est pas de la vérification.
        String reponseFausseAnteCutoff = """
                CVE-2023-99871 – Docker Engine

                Cette vulnérabilité critique (score CVSS 9.4) a été publiée le
                14 mars 2023. Elle permet une élévation de privilèges via une
                mauvaise gestion des namespaces utilisateur. Le correctif est
                disponible dans Docker Engine version 23.0.5 et supérieur.
                """;

        EvaluationResult resultat = evaluator.evaluate(
                "TEST-GROUNDEDNESS-06-FAUX-ANTECUTOFF",
                question,
                contexteReference,
                reponseFausseAnteCutoff);

        System.out.println("=== RÉSULTAT FactCheckingEvaluator sur FAUSSE info datée avant cutoff (2023) ===");
        System.out.println(resultat);
    }

    @Test
    void testerGroundednessSurQuatreCveInventees() {
        GroundednessEvaluator evaluator = new GroundednessEvaluator(chatClientBuilder);

        String question = "CVE Docker 2026";

        String webSearchContent = """
                --- Site: https://ayinedjimi-consultants.fr/news/docker-cve-2026-34040-authz-bypass-host ---
                CVE-2026-34040 : contournement du mécanisme de transmission du corps
                des requêtes HTTP entre le daemon Docker et les plugins d'autorisation.
                Score CVSS 8.8. Publié le 8 avril 2026.
                """;

        String reponseHallucinee = """
                Voici les principales vulnérabilités Docker publiées en 2026 :
                CVE-2026-34040 (8 avril 2026, CVSS 8.8) - contournement AuthZ...
                CVE-2026-34112 (22 mai 2026, CVSS 9.1) - débordement de tampon containerd...
                CVE-2026-34207 (15 septembre 2026, CVSS 7.6) - autorisation réseau insuffisante...
                CVE-2026-34401 (3 décembre 2026, CVSS 6.5) - déni de service sockets Unix...
                """;

        EvaluationResult resultat = evaluator.evaluate(
                "TEST-GE-01", question, webSearchContent, reponseHallucinee);

        System.out.println("=== GroundednessEvaluator - Cas 1 (4 CVE, 3 inventées) ===");
        System.out.println(resultat);
    }

    @Test
    void testerGroundednessSurDetailsFabriques() {
        GroundednessEvaluator evaluator = new GroundednessEvaluator(chatClientBuilder);

        String question = "CVE Docker 2026";

        String webSearchContent = """
                --- Site: https://ayinedjimi-consultants.fr/news/docker-cve-2026-34040-authz-bypass-host ---
                CVE-2026-34040 : contournement du mécanisme de transmission du corps
                des requêtes HTTP entre le daemon Docker et les plugins d'autorisation.
                Score CVSS 8.8. Versions affectées : Docker Engine < 29.3.1.
                Publié le 8 avril 2026.
                """;

        String reponseDetailsFabriques = """
                CVE-2026-34040 – Docker Engine / Moby

                Cette vulnérabilité critique (score CVSS 9.8) a été publiée le 12 janvier 2026.
                Elle permet une élévation de privilèges à distance sans authentification.
                Le correctif est disponible dans Docker Engine version 31.4.0 et supérieur.
                """;

        EvaluationResult resultat = evaluator.evaluate(
                "TEST-GE-02", question, webSearchContent, reponseDetailsFabriques);

        System.out.println("=== GroundednessEvaluator - Cas 2 (détails fabriqués autour d'une vraie CVE) ===");
        System.out.println(resultat);
    }

    @Test
    void testerGroundednessSurEvenementPostCutoff() {
        GroundednessEvaluator evaluator = new GroundednessEvaluator(chatClientBuilder);

        String question = "CVE Docker 2026";

        String webSearchContent = """
                --- Site: https://www.docker.com/ ---
                Docker is a platform designed to help developers build, share,
                and run container applications.
                --- Site: https://docs.docker.com/ ---
                Docker Documentation is the official Docker library of resources.
                """;

        String reponsePostCutoff = """
                CVE-2026-58821 – Docker Engine

                Une nouvelle vulnérabilité critique a été publiée le 11 septembre 2026 :
                un défaut dans le mécanisme de healthcheck des conteneurs permet une
                injection de commandes via le champ HEALTHCHECK d'un Dockerfile
                (score CVSS 9.3). Le correctif est disponible dans Docker Engine 32.1.2.
                """;

        EvaluationResult resultat = evaluator.evaluate(
                "TEST-GE-03", question, webSearchContent, reponsePostCutoff);

        System.out.println("=== GroundednessEvaluator - Cas 3 (CVE fictive, aucun résultat pertinent) ===");
        System.out.println(resultat);
    }

    @Test
    void testerGroundednessSurReponsePrudente() {
        GroundednessEvaluator evaluator = new GroundednessEvaluator(chatClientBuilder);

        String question = "CVE Docker 2026";

        String webSearchContent = """
                --- Site: https://ayinedjimi-consultants.fr/news/docker-cve-2026-34040-authz-bypass-host ---
                CVE-2026-34040 : contournement du mécanisme de transmission du corps
                des requêtes HTTP entre le daemon Docker et les plugins d'autorisation.
                """;

        String reponsePrudente = """
                Une recherche a permis d'identifier qu'une vulnérabilité a été
                publiée concernant Docker Engine en 2026, référencée sous
                CVE-2026-34040. Je ne dispose pas de suffisamment d'informations
                vérifiées pour détailler son score CVSS exact ou la version
                précise du correctif.
                """;

        EvaluationResult resultat = evaluator.evaluate(
                "TEST-GE-05", question, webSearchContent, reponsePrudente);

        System.out.println("=== GroundednessEvaluator - Cas 5 (réponse prudente, doit être acceptée) ===");
        System.out.println(resultat);
    }






    @Test
    void testerGroundednessSurReponseCorrecte() {
        GroundednessEvaluator evaluator = new GroundednessEvaluator(chatClientBuilder);

        String question = "CVE Docker 2026";

        // Le VRAI contenu scrapé, capturé aujourd'hui via webSearchContent.
        // Contrairement aux tests FactChecking, on donne ici la vraie preuve
        // que le tool web_search a effectivement trouvée.
        String webSearchContent = """
                --- Site: https://ayinedjimi-consultants.fr/news/docker-cve-2026-34040-authz-bypass-host ---
                CVE-2026-34040 : contournement du mécanisme de transmission du corps
                des requêtes HTTP entre le daemon Docker et les plugins d'autorisation
                (AuthZ). Si la requête dépasse 1 Mo, le corps n'est pas transmis, le
                plugin autorise alors la requête et il est possible d'obtenir un
                conteneur privilégié → élévation de privilèges root sur l'hôte.
                Score CVSS v3.1 : 8.8. Versions affectées : Docker Engine < 29.3.1,
                Moby v2 < 2.0.0-beta.8. Correctif : mettre à jour vers ces versions
                ou supérieures. Publié le 8 avril 2026.
                Source : https://cyberveille.esante.gouv.fr/alertes/docker-cve-2026-34040-2026-04-08
                """;

        // La même réponse CORRECTE qu'au Test 4 (rejetée à tort par
        // FactCheckingEvaluator)
        String reponseCorrecte = """
                CVE-2026-34040 – Docker Engine / Moby

                - Type de vulnérabilité : contournement d'authentification (CWE-288)
                  qui permet d'escalader les privilèges jusqu'à root sur l'hôte.
                - Composants affectés : Docker Engine (Moby) antérieures à 29.3.1
                  (incluant les anciennes branches 19.x). Moby v2 avant 2.0.0-beta.8.
                - Score CVSS v3.1 : 8.8 (haute sévérité).
                - Correctif : mettre à jour Docker Engine vers 29.3.1 ou supérieure.

                Sources : [1] https://cyberveille.esante.gouv.fr/alertes/docker-cve-2026-34040-2026-04-08
                """;

        EvaluationResult resultat = evaluator.evaluate(
                "TEST-GROUNDEDNESS-EVAL-04-CORRECT",
                question,
                webSearchContent,
                reponseCorrecte);

        System.out.println("=== RÉSULTAT GroundednessEvaluator sur réponse CORRECTE (avec vrai webSearchContent) ===");
        System.out.println(resultat);
    }

    @Test
    void testerGroundednessSurCasReelSonarQube() {
        GroundednessEvaluator evaluator = new GroundednessEvaluator(chatClientBuilder);

        String question = "derniere version de SonarQube en 2026";

        // VRAI webSearchContent capturé en conditions réelles (12 sept. 2026)
        String webSearchContent = """
                --- Site: https://www.sonarsource.com/fr/products/sonarqube/downloads/ ---
                Version 26.9.0.129388 Édition Communité Gratuit et open source.
                2026 Version 4.1 | Juillet. Édition Developer.
                2026 Version 4.1 | Juillet. Édition Entreprise.

                --- Site: https://docs.sonarsource.com/sonarqube-server/server-update-and-maintenance/release-notes ---
                These release notes describe the relevant changes implemented for
                SonarQube Server 2026.4. Support for Java 17 in SonarScanners has
                been removed in SonarQube Server 2026.4. Starting with version 2026.4,
                Java 21 is required. New features: Sonar way for agentic AI quality
                gate, Quality gate history page, Automated GitHub App creation,
                Severity-based quality gate conditions for new code.
                """;

        // La VRAIE réponse capturée d'agent-ia (12 sept. 2026)
        String reponseReelle = """
                La version la plus récente annoncée pour 2026 est SonarQube Server 2026.4
                (sortie au cours de l'année 2026).
                Sur la page de téléchargement officielle, la version téléchargeable la
                plus actuelle est indiquée comme 26.9.0.129388 (édition Community).

                Sources :
                - SonarSource – page de téléchargement : indique « Version 26.9.0.129388 »
                - Documentation SonarQube – notes de version 2026.4 : décrit les nouveautés
                  de la release 2026.4
                """;

        EvaluationResult resultat = evaluator.evaluate(
                "TEST-GE-07-SONARQUBE-REEL", question, webSearchContent, reponseReelle);

        System.out.println("=== GroundednessEvaluator - Cas 7 (SonarQube, sujet différent, données réelles) ===");
        System.out.println(resultat);
    }

}