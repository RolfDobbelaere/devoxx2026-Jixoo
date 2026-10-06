package io.github.rolfdobbelaere.ledart;

import io.github.glaforge.jixoo.image.PixooImage;

/**
 * A 64x64 floating point RGB canvas with additive blending, so overlapping lights glow
 * the way they do on a real LED matrix. Converts to a Jixoo {@link PixooImage}.
 */
public final class Canvas {

    public static final int SIZE = 64;

    private final double[] r = new double[SIZE * SIZE];
    private final double[] g = new double[SIZE * SIZE];
    private final double[] b = new double[SIZE * SIZE];

    public void add(int x, int y, int color, double intensity) {
        if (x < 0 || y < 0 || x >= SIZE || y >= SIZE || intensity <= 0) return;
        int i = y * SIZE + x;
        r[i] += GoogleColors.r(color) * intensity;
        g[i] += GoogleColors.g(color) * intensity;
        b[i] += GoogleColors.b(color) * intensity;
    }

    public void set(int x, int y, int color) {
        if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) return;
        int i = y * SIZE + x;
        r[i] = GoogleColors.r(color);
        g[i] = GoogleColors.g(color);
        b[i] = GoogleColors.b(color);
    }

    /** Solid anti-aliased disc with a soft halo around it. */
    public void glowDot(double cx, double cy, double radius, int color, double haloRadius, double haloStrength) {
        int reach = (int) Math.ceil(radius + haloRadius) + 1;
        for (int y = (int) cy - reach; y <= (int) cy + reach; y++) {
            for (int x = (int) cx - reach; x <= (int) cx + reach; x++) {
                double d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
                double core = Math.clamp(radius + 0.5 - d, 0, 1);
                double halo = d > radius ? haloStrength * Math.exp(-Math.pow((d - radius) / haloRadius, 2) * 2) : 0;
                add(x, y, color, core + halo * (1 - core));
            }
        }
    }

    /** Multiplies every pixel, used for motion trails between frames. */
    public void fade(double factor) {
        for (int i = 0; i < r.length; i++) {
            r[i] *= factor;
            g[i] *= factor;
            b[i] *= factor;
        }
    }

    public PixooImage toImage() {
        int[] argb = new int[SIZE * SIZE];
        for (int i = 0; i < argb.length; i++) {
            argb[i] = 0xFF000000 | (clamp(r[i]) << 16) | (clamp(g[i]) << 8) | clamp(b[i]);
        }
        return new PixooImage(SIZE, SIZE, argb);
    }

    private static int clamp(double v) {
        return (int) Math.clamp(Math.round(v), 0, 255);
    }
}
