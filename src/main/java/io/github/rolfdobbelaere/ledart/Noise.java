package io.github.rolfdobbelaere.ledart;

/**
 * Ken Perlin's improved 3D gradient noise, plus fractal (fBm) and domain-warped variants.
 * Domain warping (feeding noise into the coordinates of more noise) is what makes it swirl like smoke.
 */
final class Noise {

    private static final int[] P = new int[512];

    static {
        int[] perm = new int[256];
        for (int i = 0; i < 256; i++) perm[i] = i;
        java.util.Random random = new java.util.Random(2026);
        for (int i = 255; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int t = perm[i];
            perm[i] = perm[j];
            perm[j] = t;
        }
        for (int i = 0; i < 512; i++) P[i] = perm[i & 255];
    }

    private Noise() {}

    /** Gradient noise in roughly [-1, 1]. */
    static double noise(double x, double y, double z) {
        int xi = (int) Math.floor(x) & 255, yi = (int) Math.floor(y) & 255, zi = (int) Math.floor(z) & 255;
        x -= Math.floor(x);
        y -= Math.floor(y);
        z -= Math.floor(z);
        double u = fade(x), v = fade(y), w = fade(z);
        int a = P[xi] + yi, aa = P[a] + zi, ab = P[a + 1] + zi;
        int b = P[xi + 1] + yi, ba = P[b] + zi, bb = P[b + 1] + zi;
        return lerp(w,
                lerp(v, lerp(u, grad(P[aa], x, y, z), grad(P[ba], x - 1, y, z)),
                        lerp(u, grad(P[ab], x, y - 1, z), grad(P[bb], x - 1, y - 1, z))),
                lerp(v, lerp(u, grad(P[aa + 1], x, y, z - 1), grad(P[ba + 1], x - 1, y, z - 1)),
                        lerp(u, grad(P[ab + 1], x, y - 1, z - 1), grad(P[bb + 1], x - 1, y - 1, z - 1))));
    }

    /** Fractal sum of octaves, roughly [-1, 1]. */
    static double fbm(double x, double y, double z, int octaves) {
        double sum = 0, amp = 0.5, freq = 1, norm = 0;
        for (int i = 0; i < octaves; i++) {
            sum += amp * noise(x * freq, y * freq, z * freq);
            norm += amp;
            amp *= 0.5;
            freq *= 2.03;
        }
        return sum / norm * 1.6;
    }

    /** Smoke: fBm whose input coordinates are pushed around by two other fBm fields. */
    static double smoke(double x, double y, double z) {
        double wx = fbm(x + 3.1, y - 1.7, z * 0.7, 3);
        double wy = fbm(x - 5.3, y + 2.9, z * 0.7 + 4.4, 3);
        return fbm(x + 1.6 * wx, y + 1.6 * wy, z, 4);
    }

    private static double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }
    private static double lerp(double t, double a, double b) { return a + t * (b - a); }

    private static double grad(int hash, double x, double y, double z) {
        int h = hash & 15;
        double u = h < 8 ? x : y, v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }
}
