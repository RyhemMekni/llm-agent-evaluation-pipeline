package com.pfe.agentia.service;

import com.pfe.agentia.mcp.CapturingToolCallback;
import com.pfe.agentia.mcp.McpToolsService;
import io.modelcontextprotocol.client.McpSyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AgentService {

        private final ChatClient.Builder chatClientBuilder;
        private final McpToolsService mcpToolsService;
        private final List<McpSyncClient> mcpSyncClients;

        // ThreadLocal : un buffer différent par thread de requête HTTP,
        // pour éviter que deux appels concurrents ne mélangent leurs résultats.
        private final ThreadLocal<StringBuilder> webSearchCapture = ThreadLocal.withInitial(StringBuilder::new);

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

                        Tu as accès à un outil "web_search" pour rechercher des informations récentes
                        (CVE, versions d'outils, incidents de sécurité 2024-2026).
                        - Utilise web_search UNIQUEMENT si la question porte sur une information récente.
                        - Pour les concepts stables (définitions, principes), réponds directement sans web_search.

                        RÈGLE ABSOLUE ANTI-HALLUCINATION :
                        - Ne mentionne JAMAIS un identifiant précis (CVE, numéro de version, date exacte)
                          qui n'apparaît PAS explicitement et littéralement dans le contenu retourné par web_search.
                        - Si web_search ne retourne qu'UNE SEULE information pertinente (ex: un seul CVE),
                          présente UNIQUEMENT celle-là. N'invente pas d'entrées supplémentaires pour
                          "compléter" une liste, même si cela semble incomplet.
                        - Si tu n'es pas sûr qu'une information vient réellement du résultat de recherche,
                          ne l'inclus pas.
                        - Cite tes sources en fin de réponse au format : "Sources : [1] <url>" — une URL par
                          affirmation factuelle précise. Si tu ne peux pas citer d'URL pour une affirmation,
                          ne fais pas cette affirmation.
                        - Si aucune source pertinente n'est trouvée, dis-le explicitement : "Je n'ai pas trouvé
                          d'information fiable et récente sur ce sujet" plutôt que d'inventer.

                        Contexte disponible :
                        {contexte}
                        """;

        public AgentService(ChatClient.Builder chatClientBuilder,
                        List<McpSyncClient> mcpSyncClients,
                        McpToolsService mcpToolsService) {
                this.mcpToolsService = mcpToolsService;
                this.mcpSyncClients = mcpSyncClients;
                this.chatClientBuilder = chatClientBuilder;
                log.info("ChatClient prêt avec {} client(s) MCP connecté(s)", mcpSyncClients.size());
        }

        public Map<String, String> ask(String question) {
                log.debug("Traitement de la question: {}", question);

                // Réinitialise le buffer de capture pour cette requête
                webSearchCapture.get().setLength(0);

                String contexteMcp = mcpToolsService.getContexte(question);
                String systemPrompt = SYSTEM_PROMPT.replace("{contexte}", contexteMcp);

                // Récupère les tools bruts fournis par MCP, puis les enveloppe
                // avec notre intercepteur de capture
                List<ToolCallback> rawCallbacks = List.of(
                                new SyncMcpToolCallbackProvider(mcpSyncClients).getToolCallbacks());
                List<ToolCallback> capturingCallbacks = rawCallbacks.stream()
                                .map(cb -> (ToolCallback) new CapturingToolCallback(cb, webSearchCapture))
                                .collect(Collectors.toList());

                ChatClient chatClient = chatClientBuilder
                                .defaultToolCallbacks(capturingCallbacks.toArray(new ToolCallback[0]))
                                .defaultOptions(OpenAiChatOptions.builder()
                                                .extraBody(Map.of("reasoning_format", "hidden")))
                                .build();

                String reponse = chatClient
                                .prompt()
                                .system(systemPrompt)
                                .user(question)
                                .call()
                                .content();

                String webSearchContent = webSearchCapture.get().toString();
                log.debug("Contenu web_search capturé ({} caractères)", webSearchContent.length());
                log.debug("Réponse générée: {}", reponse);

                return Map.of(
                                "reponse", reponse,
                                "contexteMcpUtilise", contexteMcp,
                                "webSearchContent",
                                webSearchContent.isBlank() ? "Aucune recherche web effectuée" : webSearchContent);
        }
}