package com.nellmodal.nellmodal.service;

import com.nellmodal.nellmodal.model.DetectedRow;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.Word;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.AWTException;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class RaynenAutomationService {

    private static final Map<Integer, String> MOCK_MAP = Map.of(
            1, "110",
            2, "300",
            4, "140-0",
            9, "300",
            16, "260",
            21, "300"
    );

    private final Tesseract tesseract;

    public RaynenAutomationService(
            @Value("${raynen.tessdata-path:}") String tessDataPath,
            @Value("${raynen.language:eng}") String language
    ) {
        this.tesseract = new Tesseract();
        if (tessDataPath != null && !tessDataPath.isBlank()) {
            this.tesseract.setDatapath(tessDataPath);
        }
        this.tesseract.setLanguage(language);
        this.tesseract.setTessVariable("user_defined_dpi", "300");
    }

    public Map<Integer, String> normalizePayload(Map<String, String> raw) {
        if (raw == null || raw.isEmpty()) {
            return new HashMap<>(MOCK_MAP);
        }
        Map<Integer, String> result = new HashMap<>();
        raw.forEach((k, v) -> {
            try {
                int key = Integer.parseInt(k.trim());
                result.put(key, v);
            } catch (NumberFormatException e) {
                log.warn("Ignoring non-numeric key {}", k);
            }
        });
        if (result.isEmpty()) {
            result.putAll(MOCK_MAP);
        }
        return result;
    }

    public void runAutomation(Map<Integer, String> data) {
        try {
            Robot robot = new Robot();
            Rectangle screen = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            BufferedImage screenshot = robot.createScreenCapture(screen);
            BufferedImage bin = ImagePreprocessor.toBinary(screenshot, 180);

            List<Word> words = tesseract.getWords(bin, ITessAPI.TessPageIteratorLevel.RIL_WORD);
            List<DetectedRow> rows = extractRows(words);

            if (rows.isEmpty()) {
                log.warn("No rows detected on screen");
                return;
            }

            int deltaY = computeDelta(rows);
            log.info("Detected {} rows, deltaY={}", rows.size(), deltaY);

            int[] front = findLabelCenter(words, "Front");
            int[] back = findLabelCenter(words, "Back");
            int[] copy = findLabelCenter(words, "Copy");
            int[] copyFront = findLabelCenter(words, "Copy Front");
            int[] copyBack = findLabelCenter(words, "Copy Back");
            int[] pageDown = findLabelCenter(words, "Page Down");

            if (front[0] < 0 || back[0] < 0) {
                log.warn("Front/Back anchors not found on screen");
                return;
            }

            for (DetectedRow row : rows) {
                String value = data.get(row.rowNumber());
                if (value == null) {
                    continue;
                }
                log.info("Writing row {} -> {}", row.rowNumber(), value);
                writeValue(robot, row, value, front[0], back[0]);

                if (value.contains("-") && copyFront[0] > 0 && copyBack[0] > 0) {
                    click(robot, copyFront[0], copyFront[1]);
                    click(robot, copyBack[0], copyBack[1]);
                } else if (copy[0] > 0) {
                    click(robot, copy[0], copy[1]);
                }
            }

            if (data.size() > rows.size() && pageDown[0] > 0) {
                click(robot, pageDown[0], pageDown[1]);
                Thread.sleep(600);
                if (!verifyPageIndicator(robot, screen)) {
                    log.warn("Page change could not be validated with OCR");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Automation interrupted", e);
        } catch (AWTException e) {
            log.error("Automation failed", e);
        } catch (Exception e) {
            log.error("Unexpected automation error", e);
        }
    }

    private List<DetectedRow> extractRows(List<Word> words) {
        List<DetectedRow> rows = new ArrayList<>();
        for (Word w : words) {
            String text = w.getText().trim();
            if (text.matches("\\d+")) {
                int number = Integer.parseInt(text);
                Rectangle box = w.getBoundingBox();
                int centerY = box.y + box.height / 2;
                rows.add(new DetectedRow(number, centerY, box));
            }
        }
        rows.sort((a, b) -> Integer.compare(a.centerY(), b.centerY()));
        return rows;
    }

    private int computeDelta(List<DetectedRow> rows) {
        Optional<DetectedRow> one = rows.stream().filter(r -> r.rowNumber() == 1).findFirst();
        Optional<DetectedRow> two = rows.stream().filter(r -> r.rowNumber() == 2).findFirst();
        if (one.isPresent() && two.isPresent()) {
            return Math.abs(two.get().centerY() - one.get().centerY());
        }
        if (rows.size() < 2) {
            return 0;
        }
        List<Integer> deltas = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            deltas.add(rows.get(i).centerY() - rows.get(i - 1).centerY());
        }
        Collections.sort(deltas);
        return deltas.get(deltas.size() / 2);
    }

    private int[] findLabelCenter(List<Word> words, String label) {
        String[] tokens = label.split(" ");
        for (int i = 0; i < words.size(); i++) {
            boolean match = true;
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
            int maxX = 0, maxY = 0;
            for (int t = 0; t < tokens.length; t++) {
                int idx = i + t;
                if (idx >= words.size()) {
                    match = false;
                    break;
                }
                if (!tokens[t].equalsIgnoreCase(words.get(idx).getText().trim())) {
                    match = false;
                    break;
                }
                Rectangle b = words.get(idx).getBoundingBox();
                minX = Math.min(minX, b.x);
                minY = Math.min(minY, b.y);
                maxX = Math.max(maxX, b.x + b.width);
                maxY = Math.max(maxY, b.y + b.height);
            }
            if (match) {
                return new int[]{(minX + maxX) / 2, (minY + maxY) / 2};
            }
        }
        return new int[]{-1, -1};
    }

    private void writeValue(Robot robot, DetectedRow row, String value, int frontX, int backX) throws InterruptedException {
        String[] parts = value.split("-", 2);
        if (parts.length == 2) {
            writeCell(robot, frontX, row.centerY(), parts[0]);
            writeCell(robot, backX, row.centerY(), parts[1]);
        } else {
            writeCell(robot, frontX, row.centerY(), value);
        }
    }

    private void writeCell(Robot robot, int x, int y, String value) throws InterruptedException {
        click(robot, x, y);
        selectAll(robot);
        backspace(robot);
        typeText(robot, value);
    }

    private boolean verifyPageIndicator(Robot robot, Rectangle screen) {
        try {
            BufferedImage shot = robot.createScreenCapture(screen);
            BufferedImage bin = ImagePreprocessor.toBinary(shot, 180);
            String text = tesseract.doOCR(bin);
            return text.contains("/") && text.toLowerCase().contains("the");
        } catch (Exception e) {
            log.debug("Page indicator OCR failed", e);
            return false;
        }
    }

    private void click(Robot robot, int x, int y) throws InterruptedException {
        robot.mouseMove(x, y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        Thread.sleep(80);
    }

    private void selectAll(Robot robot) throws InterruptedException {
        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.keyPress(KeyEvent.VK_A);
        robot.keyRelease(KeyEvent.VK_A);
        robot.keyRelease(KeyEvent.VK_CONTROL);
        Thread.sleep(50);
    }

    private void backspace(Robot robot) throws InterruptedException {
        robot.keyPress(KeyEvent.VK_BACK_SPACE);
        robot.keyRelease(KeyEvent.VK_BACK_SPACE);
        Thread.sleep(30);
    }

    private void typeText(Robot robot, String value) throws InterruptedException {
        for (char c : value.toCharArray()) {
            if (Character.isDigit(c)) {
                int key = KeyEvent.VK_0 + (c - '0');
                robot.keyPress(key);
                robot.keyRelease(key);
            } else if (c == '-') {
                robot.keyPress(KeyEvent.VK_MINUS);
                robot.keyRelease(KeyEvent.VK_MINUS);
            }
            Thread.sleep(30);
        }
    }
}
