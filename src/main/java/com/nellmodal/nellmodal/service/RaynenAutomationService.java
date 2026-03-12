package com.nellmodal.nellmodal.service;

import lombok.extern.slf4j.Slf4j;
import org.sikuli.script.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class RaynenAutomationService {

    private final Screen screen = new Screen();
    private Region raynenWindow = null;
    private int currentPageOffset = 0;

    // Ruta a tus imágenes de referencia
    private final String IMG_PATH = "src/main/java/com/nellmodal/nellmodal/fotosReferencia/";

    public Map<Integer, String> normalizePayload(Map<String, String> raw) {
        Map<Integer, String> result = new HashMap<>();
        if (raw == null || raw.isEmpty()) return result;
        raw.forEach((k, v) -> {
            try { result.put(Integer.parseInt(k.trim()), v); } catch (Exception e) {}
        });
        return result;
    }

    /**
     * Busca la ventana de Raynen en pantalla usando una imagen de referencia (siMenu.png)
     */
    private boolean findRaynenWindow() {
        try {
            log.info(">>> BUSCANDO VENTANA DE RAYNEN...");
            Match match = screen.exists(IMG_PATH + "siMenu.png", 5.0);
            if (match != null) {
                // Definimos la región de trabajo alrededor de donde encontró el menú
                // Ajustamos el tamaño para cubrir toda la tabla de la aplicación
                raynenWindow = match.grow(400, 600); 
                raynenWindow.highlight(1); // Muestra un recuadro rojo brevemente para confirmar
                log.info(">>> VENTANA ENCONTRADA EN: {}", raynenWindow);
                return true;
            }
        } catch (Exception e) {
            log.error("Error al buscar ventana: ", e);
        }
        log.error(">>> NO SE ENCONTRÓ LA VENTANA DE RAYNEN.");
        return false;
    }

    public void runAutomation(Map<Integer, String> data) {
        if (raynenWindow == null) {
            if (!findRaynenWindow()) return;
        }

        try {
            // Configuramos velocidades de Sikuli (más humano y seguro)
            Settings.MoveMouseDelay = 0.2f; // Segundos (ajusta si quieres más velocidad)
            Settings.ClickDelay = 0.1f;

            log.info(">>> INICIANDO PROCESO DE CARGA (OFFSET: {})", currentPageOffset);

            // Supongamos que la primera celda está a una distancia fija del menú encontrado
            // Estos offsets (100, 150, etc) los ajustaremos si es necesario
            int startX = raynenWindow.x + 50; 
            int startY = raynenWindow.y + 100;
            int rowHeight = 25; // Altura aproximada de cada fila en Raynen

            for (int i = 0; i < 15; i++) {
                int rowNum = currentPageOffset + i + 1;
                String val = data.get(rowNum);
                if (val == null) continue;

                int targetY = startY + (i * rowHeight);
                log.info(">>> FILA {}: ESCRIBIENDO '{}'", rowNum, val);

                // 1. Click en la celda
                raynenWindow.click(new Location(startX, targetY));
                
                // 2. Limpiar y Escribir (Sikuli maneja combinaciones de teclas mejor)
                raynenWindow.type("a", KeyModifier.CTRL); // Seleccionar todo
                raynenWindow.type(Key.BACKSPACE);
                raynenWindow.type(val);
                raynenWindow.type(Key.ENTER);

                // 3. Click en el botón de copiar (si estuviera a la derecha de la celda)
                // raynenWindow.click(new Location(startX + 300, targetY)); 
            }

            // Lógica de avance de página si hay más datos
            boolean more = data.keySet().stream().anyMatch(k -> k > currentPageOffset + 15);
            if (more) {
                log.info(">>> AVANZANDO PÁGINA...");
                // Aquí deberías tener una imagen del botón "Page Down" para ser 100% preciso
                // raynenWindow.click(IMG_PATH + "pagedown.png"); 
                Thread.sleep(2000);
                currentPageOffset += 15;
                runAutomation(data);
            } else {
                log.info(">>> PROCESO FINALIZADO CON ÉXITO.");
                currentPageOffset = 0;
                raynenWindow = null;
            }

        } catch (Exception e) {
            log.error("Error durante la automatización: ", e);
        }
    }
}
