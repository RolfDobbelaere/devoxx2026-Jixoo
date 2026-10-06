package io.github.rolfdobbelaere.ledart;

/** Google brand and Gemini gradient colors as 0xRRGGBB ints. */
public final class GoogleColors {

    public static final int BLUE = 0x4285F4;
    public static final int RED = 0xEA4335;
    public static final int YELLOW = 0xFBBC05;
    public static final int GREEN = 0x34A853;

    /** The four classic Google colors, in logo order. */
    public static final int[] GOOGLE = {BLUE, RED, YELLOW, BLUE, GREEN, RED};
    public static final int[] FOUR = {BLUE, RED, YELLOW, GREEN};

    /** Gemini sparkle gradient: blue -> violet -> rose. */
    public static final int[] GEMINI = {0x1C7DFF, 0x4796E3, 0x9177C7, 0xCA6673, 0xD96570};

    private GoogleColors() {}

    public static int r(int c) { return (c >> 16) & 0xFF; }
    public static int g(int c) { return (c >> 8) & 0xFF; }
    public static int b(int c) { return c & 0xFF; }

    public static int lerp(int c1, int c2, double t) {
        t = Math.clamp(t, 0, 1);
        int r = (int) Math.round(r(c1) + (r(c2) - r(c1)) * t);
        int g = (int) Math.round(g(c1) + (g(c2) - g(c1)) * t);
        int b = (int) Math.round(b(c1) + (b(c2) - b(c1)) * t);
        return (r << 16) | (g << 8) | b;
    }

    /** Samples a multi-stop gradient at position t in [0,1]. */
    public static int gradient(int[] stops, double t) {
        t = Math.clamp(t, 0, 1) * (stops.length - 1);
        int i = Math.min((int) t, stops.length - 2);
        return lerp(stops[i], stops[i + 1], t - i);
    }

    /** Samples a looping gradient (last stop blends back into the first). */
    public static int cyclic(int[] stops, double t) {
        t = ((t % 1) + 1) % 1 * stops.length;
        int i = (int) t;
        return lerp(stops[i % stops.length], stops[(i + 1) % stops.length], t - i);
    }
}
