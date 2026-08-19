package com.pfe.agentia.controller;

import com.pfe.agentia.service.AgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AgentController {

    private final AgentService agentService;

    @GetMapping("/ask")
    public ResponseEntity<Map<String, Object>> ask(@RequestParam String question) {

        log.info("Question reçue: {}", question);

        if (question == null || question.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "La question ne peut pas être vide"));
        }
        if (question.length() > 500) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Question trop longue (max 500 caractères)"));
        }

        String reponse = agentService.ask(question);

        return ResponseEntity.ok(Map.of(
                "question", question,
                "reponse", reponse,
                "agent", "DevSecOps Expert",
                "status", "success"));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "agent", "DevSecOps Expert IA",
                "version", "1.0.0"));
    }
}