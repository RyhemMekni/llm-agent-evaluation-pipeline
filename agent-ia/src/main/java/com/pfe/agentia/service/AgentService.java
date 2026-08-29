package com.pfe.agentia.service;

import com.pfe.agentia.mcp.McpToolsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentService {

    private final ChatClient.Builder chatClientBuilder;
    private final McpToolsService mcpToolsService;

    private static final String SYSTEM_PROMPT = """
            Tu es un expert DevSecOps spécialisé dans la sécurité des agents IA.
            Tu réponds UNIQUEMENT aux questions concernant :
            - DevSecOps, CI/CD, pipelines GitLab
            - Sécurité applicative (SAST, DAST, OWASP)
            - Tests automatisés et évaluation des agents IA
            - Spring Boot, Spring AI, Java
            - Docker, conteneurisation
            - Qualité logicielle et bonnes pratiques

            Si la question n'est pas liée à ces domaines, réponds :
            "Je suis uniquement expert DevSecOps. Je ne peux pas répondre à cette question."

            Contexte disponible :
            {contexte}
            """;

    /**
     * Retourne à la fois la réponse ET le contexte MCP utilisé pour la générer.
     * Permet à l'Agent Évaluateur de mesurer la cohérence réponse / contexte MCP
     * réel.
     */
    public Map<String, String> ask(String question) {
        log.debug("Traitement de la question: {}", question);

        String contexteMcp = mcpToolsService.getContexte(question);
        String systemPrompt = SYSTEM_PROMPT.replace("{contexte}", contexteMcp);

        ChatClient chatClient = chatClientBuilder.build();

        String reponse = chatClient
                .prompt()
                .system(systemPrompt)
                .user(question)
                .call()
                .content();

        log.debug("Réponse générée: {}", reponse);

        return Map.of(
                "reponse", reponse,
                "contexteMcpUtilise", contexteMcp);
    }
}