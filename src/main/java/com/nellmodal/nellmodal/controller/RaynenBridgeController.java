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
        
        // Ejecutar la automatización en un hilo separado para no bloquear la respuesta HTTP
        new Thread(() -> {
            try {
                // Pequeña espera para permitir que el cliente reciba la respuesta
                // y que el usuario ponga la ventana correcta en foco
                Thread.sleep(1000); 
                automationService.runAutomation(normalized);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();

        return ResponseEntity.accepted().body(Map.of("processed", normalized.size()));
    }
}
