import com.zynelabs.flowplay.data.VideoScaleModes;

/** Run with assertions enabled or simply execute main; failures throw AssertionError. */
public final class VideoScaleModesTest {
    private static final float EPSILON = 0.015f;

    public static void main(String[] args) {
        assertEquals(7, VideoScaleModes.COUNT, "COUNT");
        assertEquals("Fit", VideoScaleModes.label(0), "label fit");
        assertEquals("Fill", VideoScaleModes.label(1), "label fill");
        assertEquals("Zoom", VideoScaleModes.label(2), "label zoom");
        assertEquals("16:9", VideoScaleModes.label(3), "label 16:9");
        assertEquals("4:3", VideoScaleModes.label(4), "label 4:3");
        assertEquals("21:9", VideoScaleModes.label(5), "label 21:9");
        assertEquals("1:1", VideoScaleModes.label(6), "label 1:1");
        assertEquals("FIT", VideoScaleModes.shortLabel(0), "short fit");
        assertEquals("FILL", VideoScaleModes.shortLabel(1), "short fill");
        assertEquals("ZOOM", VideoScaleModes.shortLabel(2), "short zoom");
        assertEquals("21:9", VideoScaleModes.shortLabel(5), "short ratio");
        assertEquals("Fit", VideoScaleModes.label(99), "invalid label fallback");
        assertEquals("FIT", VideoScaleModes.shortLabel(-1), "invalid short label fallback");

        int[][] sources = {{1920, 1080}, {1440, 1080}};
        int[][] boxes = {{1280, 720}, {720, 1280}};
        for (int[] source : sources) {
            for (int[] box : boxes) {
                for (int mode = 0; mode < VideoScaleModes.COUNT; mode++) {
                    checkDimensions(source[0], source[1], box[0], box[1], mode, 1f);
                    checkDimensions(source[0], source[1], box[0], box[1], mode, 1.3f);
                }
            }
        }

        int[] fill = VideoScaleModes.dimensions(1920, 1080, 1280, 720, 1, 1f);
        assertPair(fill, 1280, 720, "fill 100%");
        int[] fit = VideoScaleModes.dimensions(1920, 1080, 720, 1280, 0, 1f);
        assertPair(fit, 720, 405, "portrait fit 16:9");
        int[] zoom = VideoScaleModes.dimensions(1920, 1080, 720, 1280, 2, 1f);
        assertPair(zoom, 2275, 1280, "portrait zoom 16:9");
        int[] forced = VideoScaleModes.dimensions(1920, 1080, 1280, 720, 4, 1f);
        assertPair(forced, 960, 720, "forced 4:3");
        int[] forcedScaled = VideoScaleModes.dimensions(1920, 1080, 1280, 720, 6, 1.3f);
        assertPair(forcedScaled, 935, 935, "forced square 130%");

        assertPair(VideoScaleModes.dimensions(0, 1080, 0, -4, 0, 1.3f), 1, 1,
                "invalid dimensions safe fallback");
        assertPair(VideoScaleModes.dimensions(1920, 1080, 1280, 720, 99, 1f), 1280, 720,
                "invalid mode fit fallback");
        assertPair(VideoScaleModes.dimensions(1920, 1080, 1280, 720, 1, 0f), 1280, 720,
                "invalid scale zero fallback");
        assertPair(VideoScaleModes.dimensions(1920, 1080, 1280, 720, 1, Float.NaN), 1280, 720,
                "invalid scale NaN fallback");
        System.out.println("VideoScaleModesTest: PASS");
    }

    private static void checkDimensions(int videoW, int videoH, int boxW, int boxH,
                                        int mode, float scale) {
        int[] actual = VideoScaleModes.dimensions(videoW, videoH, boxW, boxH, mode, scale);
        float ratio;
        if (mode == 3) ratio = 16f / 9f;
        else if (mode == 4) ratio = 4f / 3f;
        else if (mode == 5) ratio = 21f / 9f;
        else if (mode == 6) ratio = 1f;
        else if (mode == 1) ratio = boxW / (float) boxH;
        else ratio = videoW / (float) videoH;
        if (mode != 1) assertClose(actual[0] / (float) actual[1], ratio, "ratio mode " + mode);
        assertTrue(actual[0] >= 1 && actual[1] >= 1, "positive mode " + mode);
        int[] unscaled = VideoScaleModes.dimensions(videoW, videoH, boxW, boxH, mode, 1f);
        assertEquals((int) (unscaled[0] * scale), actual[0], "scale width mode " + mode);
        assertEquals((int) (unscaled[1] * scale), actual[1], "scale height mode " + mode);
    }

    private static void assertPair(int[] pair, int width, int height, String message) {
        assertEquals(width, pair[0], message + " width");
        assertEquals(height, pair[1], message + " height");
    }

    private static void assertClose(float actual, float expected, String message) {
        if (Math.abs(actual - expected) > EPSILON) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
