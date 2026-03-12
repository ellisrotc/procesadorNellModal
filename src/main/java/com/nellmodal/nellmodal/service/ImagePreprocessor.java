package com.nellmodal.nellmodal.service;

import java.awt.image.BufferedImage;

public final class ImagePreprocessor {

    private ImagePreprocessor() {
    }

    /**
     * Optimizado para capturar texto rojo y negro sobre fondo claro.
     */
    public static BufferedImage toBinary(BufferedImage source, int threshold) {
        int width = source.getWidth();
        int height = source.getHeight();
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = source.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                // Si el píxel es predominantemente rojo (como tus números), lo forzamos a negro
                // O si es oscuro en general (negro)
                boolean isRed = (r > 150 && g < 100 && b < 100);
                int gray = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                
                int bit = (isRed || gray < threshold) ? 0 : 1; // 0 = Negro, 1 = Blanco
                output.getRaster().setSample(x, y, 0, bit);
            }
        }
        return output;
    }
}
