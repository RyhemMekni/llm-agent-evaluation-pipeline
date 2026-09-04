package com.pfe.agentia.service;

import com.pfe.agentia.mcp.McpToolsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @Mock
    private McpToolsService mcpToolsService;

    private AgentService agentService;

    @BeforeEach
    void setUp() {
        agentService = new AgentService(chatClientBuilder, mcpToolsService);
    }

    @Test
    void devrait_construire_reponse_avec_contexte_mcp() {
        String question = "Qu'est-ce que le SAST ?";
        String contexteMcp = "SAST designe l'analyse statique du code";
        String reponseLlm = "Le SAST est une technique d'analyse de securite...";

        when(mcpToolsService.getContexte(question)).thenReturn(contexteMcp);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system((String) any())).thenReturn(requestSpec);
        when(requestSpec.user(question)).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(reponseLlm);

        var resultat = agentService.ask(question);

        assertEquals(reponseLlm, resultat.get("reponse"));
        assertEquals(contexteMcp, resultat.get("contexteMcpUtilise"));
        verify(mcpToolsService, times(1)).getContexte(question);
    }

    @Test
    void devrait_inclure_le_contexte_mcp_dans_le_system_prompt() {
        String question = "Qu'est-ce que Docker ?";
        String contexteMcp = "Docker Compose orchestre les services";

        when(mcpToolsService.getContexte(question)).thenReturn(contexteMcp);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system((String) any())).thenReturn(requestSpec);
        when(requestSpec.user(question)).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("reponse test");

        agentService.ask(question);

        verify(requestSpec).system(argThat((String arg) -> arg.contains(contexteMcp)));
    }

    @Test
    void devrait_appeler_mcptoolsservice_avec_la_bonne_question() {
        String question = "Comment fonctionne le DAST ?";

        when(mcpToolsService.getContexte(anyString())).thenReturn("contexte generique");
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system((String) any())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("reponse");

        agentService.ask(question);

        verify(mcpToolsService).getContexte(question);
        verify(requestSpec).user(question);
    }
}