package com.nellmodal.nellmodal.model;

import java.awt.Rectangle;

public record DetectedRow(int rowNumber, int centerY, Rectangle bounds) {
}
