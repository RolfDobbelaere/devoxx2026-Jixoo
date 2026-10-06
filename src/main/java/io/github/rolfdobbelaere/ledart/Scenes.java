package io.github.rolfdobbelaere.ledart;

import io.github.glaforge.jixoo.model.PixooAnimation;
import io.github.glaforge.jixoo.model.PixooFrame;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;

import static io.github.rolfdobbelaere.ledart.GoogleColors.*;

/**
 * Procedural 64x64 animations in Google / Gemini colors. Every scene loops seamlessly
 * and stays at 30 frames or fewer, the limit the Pixoo 64 plays before restarting a GIF.
 */
public final class Scenes {

    public static final int FRAMES = 30;
    private static final double TAU = Math.PI * 2;

    public static final Map<String, IntFunction<Canvas>> ALL = Map.of(
            "google-dots", Scenes::googleDots,
            "gemini-sparkle", Scenes::geminiSparkle,
            "matrix-rain", Scenes::matrixRain,
            "google-spinner", Scenes::googleSpinner,
            "devoxx-gemini", Scenes::devoxxGemini,
            "dev-runner", DevRunner::frame,
            "googly-letters", GooglyLetters::frame,
            "coffee-factory", CoffeeFactory::frame);

    /** Scenes with their own per-frame timing; the others use the delay passed to {@link #render}. */
    private static final Map<String, IntUnaryOperator> DELAYS = Map.of(
            "googly-letters", GooglyLetters::delayMs,
            "coffee-factory", CoffeeFactory::delayMs);

    private Scenes() {}

    public static PixooAnimation render(String name, int delayMs) {
        IntFunction<Canvas> scene = ALL.get(name);
        if (scene == null) throw new IllegalArgumentException("Unknown scene: " + name + ", choose from " + ALL.keySet());
        List<PixooFrame> frames = new ArrayList<>();
        for (int f = 0; f < FRAMES; f++) {
            int delay = DELAYS.containsKey(name) ? DELAYS.get(name).applyAsInt(f) : delayMs;
            frames.add(scene.apply(f).toImage().toFrame(delay));
        }
        return new PixooAnimation(frames);
    }

    /** The Google Assistant "listening" dots: four bouncing glowing dots with trails. */
    static Canvas googleDots(int f) {
        Canvas c = new Canvas();
        for (int trail = 4; trail >= 0; trail--) {
            double t = (f - trail * 0.35) / FRAMES;
            for (int i = 0; i < 4; i++) {
                double x = 11 + i * 14;
                double y = 32 - Math.max(0, Math.sin(TAU * (2 * t - i * 0.12))) * 14;
                double r = trail == 0 ? 4.5 : 4.5 - trail * 0.6;
                double strength = trail == 0 ? 1 : 0.18 / trail;
                if (trail == 0) c.glowDot(x, y, r, FOUR[i], 3.5, 0.55);
                else c.glowDot(x, y, r * 0.8, FOUR[i], 0.5, strength);
            }
        }
        // Reflection on a dark "floor".
        for (int i = 0; i < 4; i++) {
            double h = Math.max(0, Math.sin(TAU * (2.0 * f / FRAMES - i * 0.12)));
            double x = 11 + i * 14;
            for (int dx = -5; dx <= 5; dx++) {
                c.add((int) x + dx, 45, FOUR[i], (0.5 - h * 0.35) * (1 - Math.abs(dx) / 6.0));
            }
        }
        return c;
    }

    /** The Gemini four-point star: breathing, rotating, with orbiting sparkles. */
    static Canvas geminiSparkle(int f) {
        Canvas c = new Canvas();
        double t = (double) f / FRAMES;
        double scale = 29 + 3 * Math.sin(TAU * t);
        double rot = Math.sin(TAU * t) * 0.25;
        double cos = Math.cos(rot), sin = Math.sin(rot);
        for (int y = 0; y < Canvas.SIZE; y++) {
            for (int x = 0; x < Canvas.SIZE; x++) {
                double dx = x + 0.5 - 32, dy = y + 0.5 - 32;
                double u = (dx * cos - dy * sin) / scale;
                double v = (dx * sin + dy * cos) / scale;
                // Astroid-like concave star: |u|^p + |v|^p <= 1 with p < 1.
                double s = Math.pow(Math.abs(u), 0.62) + Math.pow(Math.abs(v), 0.62);
                double color = cyclicGemini((u - v + 1.2) / 2.4 + t);
                if (s <= 1) {
                    double edge = Math.clamp((1 - s) * 6, 0.35, 1);
                    c.add(x, y, (int) color, edge);
                    c.add(x, y, 0xFFFFFF, Math.max(0, 0.6 - s) * 0.9);
                } else {
                    c.add(x, y, (int) color, Math.exp(-(s - 1) * 4) * 0.35);
                }
            }
        }
        // Orbiting mini sparkles in Google colors.
        for (int i = 0; i < 4; i++) {
            double a = TAU * (t + i / 4.0);
            double px = 32 + Math.cos(a) * 27, py = 32 + Math.sin(a) * 27;
            double twinkle = 0.6 + 0.4 * Math.sin(TAU * (3 * t + i / 4.0));
            miniStar(c, px, py, FOUR[i], twinkle);
        }
        return c;
    }

    private static double cyclicGemini(double t) {
        return GoogleColors.cyclic(new int[]{0x2B7BFF, 0x7B6FE0, 0xD96570, 0x7B6FE0}, t);
    }

    private static void miniStar(Canvas c, double x, double y, int color, double k) {
        int cx = (int) Math.round(x), cy = (int) Math.round(y);
        c.add(cx, cy, 0xFFFFFF, k);
        for (int d = 1; d <= 2; d++) {
            double a = k * (d == 1 ? 1 : 0.45);
            c.add(cx + d, cy, color, a);
            c.add(cx - d, cy, color, a);
            c.add(cx, cy + d, color, a);
            c.add(cx, cy - d, color, a);
        }
    }

    /** "Light up the Matrix": digital rain where each column is a Google color. */
    static Canvas matrixRain(int f) {
        Canvas c = new Canvas();
        Random seed = new Random(2026);
        int cycle = 90; // 3 px/frame * 30 frames: every drop returns to its start -> perfect loop.
        for (int col = 0; col < 16; col++) {
            int x0 = col * 4;
            int color = FOUR[seed.nextInt(4)];
            for (int drop = 0; drop < 2; drop++) {
                int offset = seed.nextInt(cycle);
                int length = 14 + seed.nextInt(16);
                int head = (offset + f * 3) % cycle - 8;
                for (int k = 0; k <= length; k++) {
                    int y = head - k;
                    if (y < 0 || y >= Canvas.SIZE) continue;
                    double fadeOut = 1 - (double) k / length;
                    int glyphRow = Math.floorMod(y, 6);
                    if (glyphRow == 5) continue;
                    // A tiny 3-px-wide glyph row whose pattern flickers over time.
                    int bits = hash(col, Math.floorDiv(y, 6), (f + k) / 3) & 0b111;
                    for (int bx = 0; bx < 3; bx++) {
                        if ((bits >> bx & 1) == 0 && k > 0) continue;
                        if (k <= 1) c.add(x0 + bx, y, 0xFFFFFF, 0.9);
                        else c.add(x0 + bx, y, color, 0.15 + fadeOut * 1.1);
                    }
                }
            }
        }
        return c;
    }

    private static int hash(int a, int b, int c) {
        int h = a * 73856093 ^ b * 19349663 ^ c * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        return h ^ h >>> 15;
    }

    /** A Material-style spinner ring cycling through the Google colors around a pulsing core. */
    static Canvas googleSpinner(int f) {
        Canvas c = new Canvas();
        double t = (double) f / FRAMES;
        double head = TAU * t * 2;
        double arc = Math.PI * (0.6 + 0.5 * Math.sin(TAU * t));
        for (int y = 0; y < Canvas.SIZE; y++) {
            for (int x = 0; x < Canvas.SIZE; x++) {
                double dx = x + 0.5 - 32, dy = y + 0.5 - 32;
                double d = Math.hypot(dx, dy);
                double a = Math.atan2(dy, dx);
                // Faint four-color track.
                double track = Math.clamp(1.5 - Math.abs(d - 24) / 2, 0, 1);
                int quadrant = (int) Math.floor(((a + Math.PI) / TAU) * 4) & 3;
                c.add(x, y, FOUR[quadrant], track * 0.12);
                // Bright rotating arc, colored by how far along it you are.
                double behind = ((head - a) % TAU + TAU) % TAU;
                if (behind < arc) {
                    double ring = Math.clamp(3.2 - Math.abs(d - 24), 0, 1);
                    int color = GoogleColors.gradient(new int[]{BLUE, RED, YELLOW, GREEN}, behind / arc);
                    c.add(x, y, color, ring * (1 - 0.5 * behind / arc));
                    c.add(x, y, color, Math.max(0, 1 - Math.abs(d - 24) / 6) * 0.25);
                }
            }
        }
        double pulse = 0.5 + 0.5 * Math.sin(TAU * t * 2);
        c.glowDot(32, 32, 5 + pulse * 2, GoogleColors.cyclic(FOUR, t), 4, 0.5);
        return c;
    }

    /** Waving "DEVOXX" and "GEMINI" letters, each letter its own Google color. */
    static Canvas devoxxGemini(int f) {
        Canvas c = new Canvas();
        double t = (double) f / FRAMES;
        drawWave(c, "DEVOXX", 13, t, 0);
        drawWave(c, "GEMINI", 37, t, 0.5);
        // Sparkle underline sweeping across, Gemini gradient.
        int sweep = (int) (t * 80) - 8;
        for (int x = 0; x < Canvas.SIZE; x++) {
            double glow = Math.max(0, 1 - Math.abs(x - sweep) / 10.0);
            c.add(x, 58, GoogleColors.gradient(GEMINI, x / 63.0), 0.25 + glow);
        }
        return c;
    }

    private static void drawWave(Canvas c, String text, int baseY, double t, double phase) {
        int x = (Canvas.SIZE - (text.length() * 9 - 2)) / 2;
        for (int i = 0; i < text.length(); i++) {
            int dy = (int) Math.round(Math.sin(TAU * (t + phase) - i * 0.8) * 3);
            int color = FOUR[(i + (phase > 0 ? 2 : 0)) % 4];
            PixelFont.draw(c, text.charAt(i), x, baseY + dy, color);
            x += 9;
        }
    }
}
