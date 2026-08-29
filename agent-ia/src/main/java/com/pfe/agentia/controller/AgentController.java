package com.pfe.agentia.controller;

import com.pfe.agentia.service.AgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
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

        Map<String, String> result = agentService.ask(question);

        Map<String, Object> response = new HashMap<>();
        response.put("question", question);
        response.put("reponse", result.get("reponse"));
        response.put("contexteMcpUtilise", result.get("contexteMcpUtilise"));
        response.put("agent", "DevSecOps Expert");
        response.put("status", "success");

        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "agent", "DevSecOps Expert IA",
                "version", "1.0.0"));
    }
}