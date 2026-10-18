package com.zynelabs.flowplay.data;

/** Android-free labels and sizing math for player video aspect modes. */
public final class VideoScaleModes {
    public static final int COUNT = 7;

    private VideoScaleModes() { }

    public static String label(int mode) {
        switch (mode) {
            case 0: return "Fit";
            case 1: return "Fill";
            case 2: return "Zoom";
            case 3: return "16:9";
            case 4: return "4:3";
            case 5: return "21:9";
            case 6: return "1:1";
            default: return "Fit";
        }
    }

    public static String shortLabel(int mode) {
        switch (mode) {
            case 0: return "FIT";
            case 1: return "FILL";
            case 2: return "ZOOM";
            case 3: return "16:9";
            case 4: return "4:3";
            case 5: return "21:9";
            case 6: return "1:1";
            default: return "FIT";
        }
    }

    /**
     * Returns the displayed surface width and height, in that order.
     * Modes 0, 1 and 2 intentionally retain PlayerScreen's original math.
     */
    public static int[] dimensions(int videoW, int videoH, int boxW, int boxH,
                                   int mode, float scale) {
        if (videoW <= 0 || videoH <= 0 || boxW <= 0 || boxH <= 0) {
            return new int[] { Math.max(1, boxW), Math.max(1, boxH) };
        }
        int normalizedMode = mode >= 0 && mode < COUNT ? mode : 0;
        float safeScale = Float.isNaN(scale) || Float.isInfinite(scale) || scale <= 0f
                ? 1f : scale;
        float videoRatio = videoW / (float) videoH;
        float containerRatio = boxW / (float) boxH;
        int width;
        int height;
        switch (normalizedMode) {
            case 1: // Fill: stretch to container.
                width = boxW;
                height = boxH;
                break;
            case 0: // Fit: letterbox, preserving source aspect.
                if (videoRatio > containerRatio) {
                    width = boxW;
                    height = (int) (boxW / videoRatio);
                } else {
                    width = (int) (boxH * videoRatio);
                    height = boxH;
                }
                break;
            case 2: // Zoom: crop overflow, preserving source aspect.
                if (videoRatio > containerRatio) {
                    width = (int) (boxH * videoRatio);
                    height = boxH;
                } else {
                    width = boxW;
                    height = (int) (boxW / videoRatio);
                }
                break;
            default: // Forced ratio: fit that ratio inside the container.
                float forcedRatio = ratioFor(normalizedMode);
                if (containerRatio > forcedRatio) {
                    width = (int) (boxH * forcedRatio);
                    height = boxH;
                } else {
                    width = boxW;
                    height = (int) (boxW / forcedRatio);
                }
                break;
        }
        return new int[] {
                Math.max(1, (int) (width * safeScale)),
                Math.max(1, (int) (height * safeScale))
        };
    }

    private static float ratioFor(int mode) {
        switch (mode) {
            case 3: return 16f / 9f;
            case 4: return 4f / 3f;
            case 5: return 21f / 9f;
            case 6: return 1f;
            default: return 16f / 9f;
        }
    }
}
