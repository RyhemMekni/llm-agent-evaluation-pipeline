package com.pfe.agentia.mcp;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.chat.model.ToolContext;

/**
 * Décore un ToolCallback MCP pour capturer le résultat brut du tool
 * (ex: le contenu web_search) avant qu'il ne soit transmis au LLM,
 * afin de pouvoir l'exposer dans la réponse finale d'agent-ia.
 */
public class CapturingToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final ThreadLocal<StringBuilder> capturedOutput;

    public CapturingToolCallback(ToolCallback delegate, ThreadLocal<StringBuilder> capturedOutput) {
        this.delegate = delegate;
        this.capturedOutput = capturedOutput;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String toolInput) {
        String result = delegate.call(toolInput);
        StringBuilder buffer = capturedOutput.get();
        if (buffer != null) {
            buffer.append(result).append("\n---\n");
        }
        return result;
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String result = delegate.call(toolInput, toolContext);
        StringBuilder buffer = capturedOutput.get();
        if (buffer != null) {
            buffer.append(result).append("\n---\n");
        }
        return result;
    }
}