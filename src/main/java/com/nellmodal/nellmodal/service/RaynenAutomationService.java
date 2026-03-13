package com.nellmodal.nellmodal.service;

import lombok.extern.slf4j.Slf4j;
import org.sikuli.basics.Settings;
import org.sikuli.script.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

@Slf4j
@Service
public class RaynenAutomationService {

    @Value("${raynen.exe-path}")
    private String raynenExePath;

    @Value("${raynen.window-title}")
    private String raynenWindowTitle;

    private final Screen screen = new Screen();
    private Region raynenWindow = null;
    private int currentPageOffset = 0;

    private final String IMG_PATH = "src/main/java/com/nellmodal/nellmodal/fotosReferencia/";

    /**
     * Intenta enfocar la ventana de Raynen. Si no se puede enfocar (no está abierta),
     * intenta ejecutar el archivo .exe configurado.
     */
    public void ensureAppIsReady() {
        log.info(">>> ASEGURANDO QUE RAYNEN ESTÉ LISTO...");
        App app = new App(raynenWindowTitle);
        if (!app.focus()) {
            log.warn(">>> NO SE PUDO ENFOCAR LA VENTANA '{}'. INTENTANDO ABRIR EL PROGRAMA EN '{}'...", raynenWindowTitle, raynenExePath);
            try {
                // Ejecuta el archivo .exe de Raynen
                Runtime.getRuntime().exec(raynenExePath);
                Thread.sleep(3000); // Esperar unos segundos a que cargue
                app.focus();
            } catch (IOException | InterruptedException e) {
                log.error(">>> ERROR AL INTENTAR ABRIR RAYNEN: ", e);
            }
        } else {
            log.info(">>> VENTANA '{}' ENFOCADA CORRECTAMENTE.", raynenWindowTitle);
        }
    }

    public Map<Integer, String> normalizePayload(Map<String, String> raw) {
        Map<Integer, String> result = new HashMap<>();
        if (raw == null || raw.isEmpty()) return result;
        raw.forEach((k, v) -> {
            try { result.put(Integer.parseInt(k.trim()), v); } catch (Exception e) {}
        });
        return result;
    }

    private void addImagePath() {
        ImagePath.setBundlePath(new java.io.File(IMG_PATH).getAbsolutePath());
    }

    private boolean setWindowFromApp() {
        try {
            App app = new App(raynenWindowTitle);
            if (app.focus()) {
                Region win = app.window();
                if (win != null) {
                    raynenWindow = win;
                    log.info(">>> VENTANA '{}' DETECTADA POR TÍTULO EN ({}, {}) TAM {}x{}", raynenWindowTitle, win.x, win.y, win.w, win.h);
                    return true;
                }
            }

            Region focused = App.focusedWindow();
            if (focused != null) {
                raynenWindow = focused;
                log.info(">>> USANDO VENTANA EN FOCO COMO REGIÓN ({}, {}) TAM {}x{}", focused.x, focused.y, focused.w, focused.h);
                return true;
            }
        } catch (Exception e) {
            log.warn(">>> NO SE PUDO OBTENER LA VENTANA POR TÍTULO: {}", e.getMessage());
        }
        return false;
    }

    private boolean ensureWindowIsSet() {
        addImagePath();
        if (raynenWindow != null) return true;

        if (setWindowFromApp()) return true;
        
        log.info(">>> BUSCANDO ANCLAJE (1.png)...");
        if (findRaynenWindowVisual()) return true;
        
        log.warn(">>> NO SE ENCONTRÓ EL ANCLAJE VISUAL. POR FAVOR, SELECCIONA EL ÁREA DE TRABAJO...");
        calibrateBySelection();
        return raynenWindow != null;
    }

    private boolean findRaynenWindowVisual() {
        try {
            // Buscamos el "1" rojo (1.png) con una similitud alta (0.8) para mayor precisión
            Pattern row1Pattern = new Pattern("1.png").similar(0.8f);
            Match row1 = screen.exists(row1Pattern, 3.0);
            
            if (row1 != null) {
                log.info(">>> ANCLAJE '1.png' ENCONTRADO EN ({}, {})", row1.x, row1.y);
                // El "1" ahora es nuestro (0,0). 
                // Definimos la región de trabajo empezando exactamente ahí.
                raynenWindow = new Region(row1.x, row1.y, 1000, 700);
                return true;
            }
// ... (resto del código de búsqueda si falla el anclaje)

            // PRIORIDAD 2: El menú superior (siMenu.png)
            Pattern menuPattern = new Pattern("siMenu.png").similar(0.7f);
            Match match = screen.exists(menuPattern, 1.0); 
            if (match != null) {
                raynenWindow = match.grow(400, 600); 
                log.info(">>> VENTANA ENCONTRADA POR IMAGEN.");
                return true;
            }
        } catch (Exception e) {}
        return false;
    }

    private void calibrateBySelection() {
        final CountDownLatch latch = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame();
            frame.setUndecorated(true);
            frame.setBackground(new Color(0, 0, 0, 80));
            frame.setAlwaysOnTop(true);
            frame.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            frame.setBounds(0, 0, Toolkit.getDefaultToolkit().getScreenSize().width, Toolkit.getDefaultToolkit().getScreenSize().height);

            SelectionPanel panel = new SelectionPanel(frame, latch);
            frame.add(panel);
            frame.setVisible(true);
        });
        try { latch.await(); } catch (Exception e) { log.error("Error en calibración: ", e); }
    }

    private class SelectionPanel extends JPanel {
        private Point start = null, end = null;
        private final JFrame frame;
        private final CountDownLatch latch;

        public SelectionPanel(JFrame frame, CountDownLatch latch) {
            this.frame = frame;
            this.latch = latch;
            setOpaque(false);
            MouseAdapter adapter = new MouseAdapter() {
                public void mousePressed(MouseEvent e) { start = e.getPoint(); repaint(); }
                public void mouseDragged(MouseEvent e) { end = e.getPoint(); repaint(); }
                public void mouseReleased(MouseEvent e) {
                    if (start != null && end != null) {
                        int x = Math.min(start.x, end.x);
                        int y = Math.min(start.y, end.y);
                        int w = Math.abs(start.x - end.x);
                        int h = Math.abs(start.y - end.y);
                        if (w > 10 && h > 10) {
                            raynenWindow = new Region(x, y, w, h);
                            log.info(">>> ZONA SELECCIONADA: x={}, y={}, w={}, h={}", x, y, w, h);
                            frame.dispose();
                            latch.countDown();
                        }
                    }
                }
            };
            addMouseListener(adapter);
            addMouseMotionListener(adapter);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setColor(Color.ORANGE);
            g.setFont(new Font("Arial", Font.BOLD, 20));
            g.drawString("DIBUJA EL RECUADRO SOBRE LAS FILAS A AUTOMATIZAR", 50, 50);
            if (start != null && end != null) {
                int x = Math.min(start.x, end.x);
                int y = Math.min(start.y, end.y);
                int w = Math.abs(start.x - end.x);
                int h = Math.abs(start.y - end.y);
                g.setColor(Color.CYAN);
                g.drawRect(x, y, w, h);
            }
        }
    }

    private Location clampToWindow(Location loc) {
        if (raynenWindow == null) return loc;
        int x = Math.max(raynenWindow.x, Math.min(loc.x, raynenWindow.x + raynenWindow.w - 1));
        int y = Math.max(raynenWindow.y, Math.min(loc.y, raynenWindow.y + raynenWindow.h - 1));
        return new Location(x, y);
    }

    public void runAutomation(Map<Integer, String> data) {
        ensureAppIsReady();
        log.info(">>> Verifica que la ventana 'Knit param' este activa; el '1' rojo servira como origen (0,0).");
        if (!ensureWindowIsSet()) return;

        try {
            Settings.MoveMouseDelay = 0.2f; 
            Settings.ClickDelay = 0.2f;
            Settings.TypeDelay = 0.05f;

            log.info(">>> INICIANDO AUTOMATIZACIÓN");

            // CALIBRACIÓN POR IMAGEN '1.png' DENTRO DEL ÁREA
            int startY;
            int rowHeight = raynenWindow.h / 15; 
            
            Match row1Match = raynenWindow.exists(new Pattern("1.png").similar(0.6f), 2.0);
            if (row1Match != null) {
                log.info(">>> DETECTADA FILA 1 POR IMAGEN EN ({}, {})", row1Match.x, row1Match.y);
                startY = row1Match.y + (row1Match.h / 2);
            } else {
                log.warn(">>> NO SE DETECTÓ '1.png' EN EL ÁREA, USANDO ESTIMACIÓN.");
                startY = raynenWindow.y + (raynenWindow.h / 30);
            }

            // Calculamos el centro horizontal (ajustado si encontramos el "1")
            int centerX = (row1Match != null) ? row1Match.x + 450 : raynenWindow.x + (raynenWindow.w / 2);

            for (int i = 0; i < 15; i++) {
                int rowNum = currentPageOffset + i + 1;
                String val = data.get(rowNum);
                if (val == null) continue;

                int targetY = startY + (i * rowHeight);
                log.info(">>> FILA {}: ESCRIBIENDO '{}' en ({}, {})", rowNum, val, centerX, targetY);

                Location loc = clampToWindow(new Location(centerX, targetY));
                
                // 1. Click inicial para asegurar el foco en la celda
                screen.click(loc);
                Thread.sleep(150);

                // 2. Doble click para entrar en modo edición (crucial en Raynen)
                screen.doubleClick(loc);
                Thread.sleep(250);
                
                // 3. Seleccionar todo, borrar y escribir el nuevo valor
                screen.type("a", KeyModifier.CTRL);
                Thread.sleep(100);
                screen.type(Key.BACKSPACE);
                Thread.sleep(100);
                screen.type(val);
                screen.type(Key.ENTER);
                
                Thread.sleep(500); 
            }

            boolean more = data.keySet().stream().anyMatch(k -> k > currentPageOffset + 15);
            if (more) {
                currentPageOffset += 15;
                log.info(">>> AVANZANDO PÁGINA...");
                Thread.sleep(1500);
                runAutomation(data);
            } else {
                log.info(">>> PROCESO COMPLETADO.");
                currentPageOffset = 0;
                raynenWindow = null; 
            }

        } catch (Exception e) {
            log.error("Error: ", e);
        }
    }
}
