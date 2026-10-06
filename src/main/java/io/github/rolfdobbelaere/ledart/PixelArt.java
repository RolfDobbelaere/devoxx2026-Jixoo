package io.github.rolfdobbelaere.ledart;

import io.github.glaforge.jixoo.image.ImageProcessor;
import io.github.glaforge.jixoo.image.PixooImage;
import io.github.glaforge.jixoo.model.PixooAnimation;
import io.github.glaforge.jixoo.model.PixooFrame;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/** Turns a large AI-generated image into crisp 64x64 LED pixel art, and animates stills. */
public final class PixelArt {

    /** Google colors + white + Gemini violet/rose, used when snapping to a palette. */
    private static final int[] PALETTE = {
            GoogleColors.BLUE, GoogleColors.RED, GoogleColors.YELLOW, GoogleColors.GREEN,
            0xFFFFFF, 0x9177C7, 0xD96570, 0x1C7DFF};

    private PixelArt() {}

    public static PixooImage decode(byte[] bytes) {
        return ImageProcessor.loadImage(new ByteArrayInputStream(bytes), "gemini.png");
    }

    /**
     * Center-crops to a square and downsamples to 64x64. Each target pixel takes the
     * <em>median</em> color of its block (sharper than averaging for pixel-art sources),
     * then dark pixels become pure black and colors are optionally snapped to the Google palette.
     */
    public static PixooImage toLed(PixooImage src, boolean snapToPalette) {
        int side = Math.min(src.width(), src.height());
        int ox = (src.width() - side) / 2, oy = (src.height() - side) / 2;
        int[] out = new int[64 * 64];
        for (int ty = 0; ty < 64; ty++) {
            for (int tx = 0; tx < 64; tx++) {
                int x0 = ox + tx * side / 64, x1 = ox + Math.max(tx * side / 64 + 1, (tx + 1) * side / 64);
                int y0 = oy + ty * side / 64, y1 = oy + Math.max(ty * side / 64 + 1, (ty + 1) * side / 64);
                // Sample only the inner part of each block to avoid bleeding from neighbours.
                int mx = (x1 - x0) / 4, my = (y1 - y0) / 4;
                int rgb = median(src, x0 + mx, x1 - mx, y0 + my, y1 - my);
                out[ty * 64 + tx] = 0xFF000000 | ledify(rgb, snapToPalette);
            }
        }
        return new PixooImage(64, 64, out);
    }

    private static int median(PixooImage img, int x0, int x1, int y0, int y1) {
        List<int[]> px = new ArrayList<>();
        for (int y = y0; y < Math.max(y1, y0 + 1); y++) {
            for (int x = x0; x < Math.max(x1, x0 + 1); x++) {
                int c = img.getPixel(x, y);
                px.add(new int[]{(c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF});
            }
        }
        int[] res = new int[3];
        for (int ch = 0; ch < 3; ch++) {
            final int channel = ch;
            px.sort((a, b) -> Integer.compare(a[channel], b[channel]));
            res[ch] = px.get(px.size() / 2)[ch];
        }
        return (res[0] << 16) | (res[1] << 8) | res[2];
    }

    private static int ledify(int rgb, boolean snap) {
        int r = GoogleColors.r(rgb), g = GoogleColors.g(rgb), b = GoogleColors.b(rgb);
        int max = Math.max(r, Math.max(g, b));
        if (max < 40) return 0; // near-black -> LED off, gives the matrix its contrast
        if (!snap) return boost(r, g, b);
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (int p : PALETTE) {
            // Compare hue/shape of the color independent of brightness, then keep the source brightness.
            double d = dist(r, g, b, p);
            if (d < bestD) { bestD = d; best = p; }
        }
        double brightness = Math.min(1, max / 200.0);
        return GoogleColors.lerp(0, best, brightness);
    }

    private static double dist(int r, int g, int b, int p) {
        double max = Math.max(1, Math.max(r, Math.max(g, b)));
        double pm = Math.max(GoogleColors.r(p), Math.max(GoogleColors.g(p), GoogleColors.b(p)));
        double dr = r / max - GoogleColors.r(p) / pm;
        double dg = g / max - GoogleColors.g(p) / pm;
        double db = b / max - GoogleColors.b(p) / pm;
        return 0.3 * dr * dr + 0.59 * dg * dg + 0.11 * db * db;
    }

    /** Slight saturation + contrast boost: LEDs look washed out otherwise. */
    private static int boost(int r, int g, int b) {
        double avg = (r + g + b) / 3.0;
        r = (int) Math.clamp(avg + (r - avg) * 1.25, 0, 255);
        g = (int) Math.clamp(avg + (g - avg) * 1.25, 0, 255);
        b = (int) Math.clamp(avg + (b - avg) * 1.25, 0, 255);
        return (r << 16) | (g << 8) | b;
    }

    /**
     * Animates a still: a Gemini-tinted shimmer sweeps diagonally across lit pixels,
     * while sparkles twinkle in Google colors. 30 frames, loops seamlessly.
     */
    public static PixooAnimation shimmer(PixooImage still, int delayMs) {
        List<PixooFrame> frames = new ArrayList<>();
        int n = Scenes.FRAMES;
        for (int f = 0; f < n; f++) {
            Canvas c = new Canvas();
            double t = (double) f / n;
            double band = t * 160 - 32; // diagonal position x + y of the shimmer band
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    int px = still.getPixel(x, y) & 0xFFFFFF;
                    if (px == 0) continue;
                    double breathe = 0.85 + 0.15 * Math.sin(Math.PI * 2 * t);
                    c.add(x, y, px, breathe);
                    double s = Math.max(0, 1 - Math.abs(x + y - band) / 7.0);
                    c.add(x, y, GoogleColors.lerp(0xFFFFFF, 0x9177C7, (x + y) / 126.0), s * 0.7);
                }
            }
            // Twinkles on black background around the subject.
            for (int i = 0; i < 6; i++) {
                int sx = (i * 23 + 7) % 60 + 2, sy = (i * 37 + 11) % 60 + 2;
                if ((still.getPixel(sx, sy) & 0xFFFFFF) != 0) continue;
                double k = Math.max(0, Math.sin(Math.PI * 2 * (t * 2 + i / 6.0)));
                c.add(sx, sy, 0xFFFFFF, k);
                int color = GoogleColors.FOUR[i % 4];
                c.add(sx + 1, sy, color, k * 0.6);
                c.add(sx - 1, sy, color, k * 0.6);
                c.add(sx, sy + 1, color, k * 0.6);
                c.add(sx, sy - 1, color, k * 0.6);
            }
            frames.add(c.toImage().toFrame(delayMs));
        }
        return new PixooAnimation(frames);
    }
}
