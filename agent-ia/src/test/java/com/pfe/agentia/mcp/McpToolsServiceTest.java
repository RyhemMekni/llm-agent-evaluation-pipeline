package com.pfe.agentia.mcp;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class McpToolsServiceTest {

    private final McpToolsService mcpToolsService = new McpToolsService();

    @Test
    void devrait_trouver_contexte_pour_question_sur_sast() {
        String contexte = mcpToolsService.getContexte("Qu'est-ce que le SAST ?");

        assertNotEquals("Aucun contexte spécifique trouvé pour cette question.", contexte);
        assertTrue(contexte.toLowerCase().contains("sast"));
    }

    @Test
    void devrait_ignorer_la_ponctuation_dans_la_question() {
        // Vérifie le fix du bug decouvert au Sprint 3 : "sast?" ne matchait pas "sast"
        String avecPonctuation = mcpToolsService.getContexte("Qu'est-ce que le SAST?");
        String sansPonctuation = mcpToolsService.getContexte("Qu'est-ce que le SAST");

        assertEquals(avecPonctuation, sansPonctuation);
        assertNotEquals("Aucun contexte spécifique trouvé pour cette question.", avecPonctuation);
    }

    @Test
    void devrait_retourner_message_par_defaut_si_aucun_mot_cle_ne_matche() {
        String contexte = mcpToolsService.getContexte("xyzabc123 inexistant motclefantome");

        assertEquals("Aucun contexte spécifique trouvé pour cette question.", contexte);
    }

    @Test
    void devrait_ignorer_les_mots_de_3_caracteres_ou_moins() {
        // Les mots courts (le, la, de, un...) ne doivent pas polluer le filtrage
        String contexte = mcpToolsService.getContexte("le la de un");

        assertEquals("Aucun contexte spécifique trouvé pour cette question.", contexte);
    }

    @Test
    void devrait_trouver_contexte_pour_docker() {
        String contexte = mcpToolsService.getContexte("Pourquoi utiliser Docker Compose ?");

        assertTrue(contexte.toLowerCase().contains("docker"));
    }

    @Test
    void devrait_limiter_le_nombre_de_lignes_retournees() {
        String contexte = mcpToolsService.getContexte("securite test docker pipeline agent");

        long nombreLignes = contexte.lines().count();
        assertTrue(nombreLignes <= 20, "Le contexte ne doit pas depasser 20 lignes");
    }
}