package com.pfe.agentia.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class McpToolsService {

    private static final String CONTEXT_FILE = "context.md";

    public String getContexte(String question) {
        try {
            List<String> lignes = chargerContexte();
            List<String> lignesFiltrees = filtrerParMotsCles(lignes, question);

            if (lignesFiltrees.isEmpty()) {
                return "Aucun contexte spécifique trouvé pour cette question.";
            }

            return String.join("\n", lignesFiltrees);

        } catch (Exception e) {
            log.warn("Impossible de charger le contexte: {}", e.getMessage());
            return "Contexte non disponible.";
        }
    }

    private List<String> chargerContexte() throws Exception {
        var inputStream = getClass()
                .getClassLoader()
                .getResourceAsStream(CONTEXT_FILE);

        if (inputStream == null) {
            log.warn("Fichier context.md non trouvé");
            return new ArrayList<>();
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.toList());
        }
    }

    private List<String> filtrerParMotsCles(List<String> lignes, String question) {
        String questionLower = question.toLowerCase();
        String questionNettoyee = questionLower.replaceAll("[?!.,;:'\"()\\[\\]]", " ");
        String[] motsCles = questionNettoyee.split("\\s+");

        return lignes.stream()
                .filter(ligne -> {
                    String ligneLower = ligne.toLowerCase();
                    for (String mot : motsCles) {
                        if (mot.length() > 3 && ligneLower.contains(mot)) {
                            return true;
                        }
                    }
                    return false;
                })
                .limit(20)
                .collect(Collectors.toList());
    }
}