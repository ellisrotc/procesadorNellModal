package com.nellmodal.nellmodal.service;

import java.awt.Color;
import java.awt.image.BufferedImage;

public final class ImagePreprocessor {

    private ImagePreprocessor() {
    }

    /**
     * Converts the image to a black-white mask so red/black digits become pure black on white.
     *
     * @param source     original screenshot
     * @param threshold  0-255 cutoff; lower keeps darker pixels as black
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

                // Normalize both red and black digits; use luminance
                int gray = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                int bw = gray < threshold ? 0 : 255;
                int packed = new Color(bw, bw, bw).getRGB();
                output.setRGB(x, y, packed);
            }
        }
        return output;
    }
}
