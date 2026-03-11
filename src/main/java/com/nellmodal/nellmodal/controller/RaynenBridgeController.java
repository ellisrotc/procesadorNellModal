package com.nellmodal.nellmodal.controller;

import com.nellmodal.nellmodal.service.RaynenAutomationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/raynen")
public class RaynenBridgeController {

    private final RaynenAutomationService automationService;

    public RaynenBridgeController(RaynenAutomationService automationService) {
        this.automationService = automationService;
    }

    @PostMapping("/push")
    public ResponseEntity<Map<String, Object>> push(@RequestBody(required = false) Map<String, String> payload) {
        var normalized = automationService.normalizePayload(payload);
        automationService.runAutomation(normalized);
        return ResponseEntity.accepted().body(Map.of("processed", normalized.size()));
    }
}
