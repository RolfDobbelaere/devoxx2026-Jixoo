package io.github.rolfdobbelaere.ledart;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.github.rolfdobbelaere.ledart.GoogleColors.*;

/**
 * "Google-Gemini-Fly-Though-Exciting-World-Of-Possibilities": GOOGLE is typed, the camera dives through the O into
 * a smoke tunnel, the smoke dissolves into a candyland world mixed with IT, the camera rises until the world becomes
 * a planet, and the planet becomes the dot on the "i" of GEMINI before everything poofs into colorful dust.
 *
 * <p>See {@code storyboards/google-gemini-fly-through.md}. 25 fps, 16 s, starts and ends on black so it loops.
 * Everything is rendered at 128x128 with additive light and downsampled to 64x64 for smooth sub-pixel motion.
 */
final class GeminiFlyThrough {

    static final int FPS = 25;
    static final double DURATION = 16.0;
    static final int FRAME_COUNT = (int) Math.round(DURATION * FPS);
    static final int DELAY_MS = 1000 / FPS;

    private static final int W = 128;                 // hi-res buffer size
    private static final int[] SMOKE = {BLUE, 0x9177C7, RED, YELLOW, GREEN};
    private static final int[] GEMINI_GRADIENT = {0x4796E3, 0x7B6FE0, 0x9177C7, 0xC76A8E, 0xD96570};

    // ------------------------------------------------------------------ letters (64-pixel units)

    private static final Map<Character, String[]> GLYPHS = Map.of(
            'G', new String[]{"..####..", ".######.", "##....##", "##......", "##......", "##..####",
                    "##..####", "##....##", "##....##", ".######.", "..####.."},
            'O', new String[]{"..####..", ".######.", "##....##", "##....##", "##....##", "##....##",
                    "##....##", "##....##", "##....##", ".######.", "..####.."},
            'L', new String[]{"##", "##", "##", "##", "##", "##", "##", "##", "##", "#######", "#######"},
            'E', new String[]{"#######", "#######", "##", "##", "######", "######", "##", "##", "##",
                    "#######", "#######"},
            'M', new String[]{"##....##", "###..###", "########", "##.##.##", "##.##.##", "##....##",
                    "##....##", "##....##", "##....##", "##....##", "##....##"},
            'N', new String[]{"##....##", "###...##", "###...##", "####..##", "##.##.##", "##.##.##",
                    "##..####", "##...###", "##...###", "##....##", "##....##"},
            'I', new String[]{"######", "######", "..##", "..##", "..##", "..##", "..##", "..##", "..##",
                    "######", "######"},
            'i', new String[]{"", "", "", "##", "##", "##", "##", "##", "##", "##", "##"});

    private record Letter(char ch, double x, double y, int width, int color) {
        double cx() { return x + width / 2.0; }
        double cy() { return y + 5.5; }
    }

    private static final List<Letter> GOOGLE = layout("GOOGLE", 26, new int[]{BLUE, RED, YELLOW, BLUE, GREEN, RED});
    private static final List<Letter> GEMINI = layout("GEMINi", 28, null);
    private static final Letter ZOOM_O = GOOGLE.get(1);                 // the red O we fly through
    private static final double DOT_X = GEMINI.get(5).cx(), DOT_Y = 25.3; // planet = dot of the last i
    private static final double DOT_R = 3.4;

    private static List<Letter> layout(String word, double y, int[] colors) {
        int total = 0;
        for (char ch : word.toCharArray()) total += width(ch);
        total += 2 * (word.length() - 1);
        double x = Math.floor((64 - total) / 2.0);
        List<Letter> letters = new ArrayList<>();
        for (int i = 0; i < word.length(); i++) {
            char ch = word.charAt(i);
            int color = colors != null ? colors[i] : 0;
            letters.add(new Letter(ch, x, y, width(ch), color));
            x += width(ch) + 2;
        }
        return letters;
    }

    private static int width(char ch) {
        int w = 0;
        for (String row : GLYPHS.get(ch)) w = Math.max(w, row.lastIndexOf('#') + 1);
        return w;
    }

    private static boolean cell(char ch, int x, int y) {
        String[] g = GLYPHS.get(ch);
        return y >= 0 && y < g.length && x >= 0 && x < g[y].length() && g[y].charAt(x) == '#';
    }

    /** Smooth coverage of a glyph at fractional cell coordinates (bilinear over the pixel grid). */
    private static double coverage(char ch, double gx, double gy) {
        double fx = gx - 0.5, fy = gy - 0.5;
        int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
        double tx = fx - x0, ty = fy - y0;
        double a = cell(ch, x0, y0) ? 1 : 0, b = cell(ch, x0 + 1, y0) ? 1 : 0;
        double c = cell(ch, x0, y0 + 1) ? 1 : 0, d = cell(ch, x0 + 1, y0 + 1) ? 1 : 0;
        return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty;
    }

    // ------------------------------------------------------------------ hi-res buffer

    private static final class Buffer {
        final double[] r = new double[W * W], g = new double[W * W], b = new double[W * W];

        void add(int i, int color, double a) {
            if (a <= 0) return;
            r[i] += GoogleColors.r(color) * a;
            g[i] += GoogleColors.g(color) * a;
            b[i] += GoogleColors.b(color) * a;
        }

        /** Paints a color over what is there, with opacity a. */
        void over(int i, int color, double a) {
            if (a <= 0) return;
            a = Math.min(1, a);
            r[i] = r[i] * (1 - a) + GoogleColors.r(color) * a;
            g[i] = g[i] * (1 - a) + GoogleColors.g(color) * a;
            b[i] = b[i] * (1 - a) + GoogleColors.b(color) * a;
        }

        void scale(double k) {
            for (int i = 0; i < r.length; i++) { r[i] *= k; g[i] *= k; b[i] *= k; }
        }

        Canvas downsample() {
            Canvas c = new Canvas();
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    int i = (y * 2) * W + x * 2;
                    int[] idx = {i, i + 1, i + W, i + W + 1};
                    double rr = 0, gg = 0, bb = 0;
                    for (int k : idx) { rr += r[k]; gg += g[k]; bb += b[k]; }
                    c.set(x, y, (clamp(rr / 4) << 16) | (clamp(gg / 4) << 8) | clamp(bb / 4));
                }
            }
            return c;
        }

        private static int clamp(double v) { return (int) Math.clamp(Math.round(v), 0, 255); }
    }

    /** Center of hi-res pixel i in 64-pixel units. */
    private static double ux(int x) { return (x + 0.5) / 2.0; }

    // ------------------------------------------------------------------ math helpers

    private static double clamp01(double v) { return Math.clamp(v, 0, 1); }
    private static double smooth(double t) { t = clamp01(t); return t * t * (3 - 2 * t); }
    private static double smoothstep(double a, double b, double x) { return smooth((x - a) / (b - a)); }
    private static double window(double t, double a, double b) { return t >= a && t < b ? 1 : 0; }

    private static int smokeColor(double v) {
        return GoogleColors.cyclic(SMOKE, v);
    }

    private static double hash(int a, int b) {
        long h = a * 374761393L + b * 668265263L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        return ((h ^ (h >>> 16)) & 0xFFFFFF) / (double) 0x1000000;
    }

    // ================================================================== frame

    static Canvas frame(int index) {
        return render(index / (double) FPS);
    }

    static Canvas render(double t) {
        Buffer buf = new Buffer();
        if (t >= 5.0 && t < 11.4) world(buf, t);
        if (t < 4.4) typingAndZoom(buf, t);
        if (t >= 4.4 && t < 6.25) tunnel(buf, t, 1, dissolveThreshold(t));
        if (t >= 11.15 && t < 15.75) space(buf, t);
        if (t >= 10.65 && t < 11.75) cloudRush(buf, t);
        return buf.downsample();
    }

    // ------------------------------------------------------------------ act 1: typing + zoom into the O

    private static double typedAt(int k) { return 0.5 + k * 0.25; }

    private static void typingAndZoom(Buffer buf, double t) {
        double u = clamp01((t - 2.6) / 1.8);                      // zoom progress
        double scale = Math.exp(3.0 * u * u);                     // 1 -> 20
        double cx = ZOOM_O.cx() + (32 - ZOOM_O.cx()) * smooth(u * 1.6);
        double cy = ZOOM_O.cy() + (32 - ZOOM_O.cy()) * smooth(u * 1.6);
        double glimpse = smoothstep(0.8, 1.6, t) * 0.4 + 0.6 * u;  // smoke seen through the O
        double ambient = smoothstep(0.6, 1.8, t) * (1 - u);

        for (int y = 0; y < W; y++) {
            for (int x = 0; x < W; x++) {
                int i = y * W + x;
                double px = ux(x), py = ux(y);
                // Scene coordinates (letters' space) for this screen point.
                double qx = ZOOM_O.cx() + (px - cx) / scale, qy = ZOOM_O.cy() + (py - cy) / scale;

                // Ambient aurora of Google-colored smoke behind the word.
                if (ambient > 0) {
                    double n = Noise.smoke(px * 0.06, py * 0.06, t * 0.25);
                    buf.add(i, smokeColor(n * 1.2 + t * 0.05), smoothstep(0.15, 0.75, n) * 0.2 * ambient);
                }

                // The hole of the O is a window onto the smoke tunnel.
                double ex = (qx - ZOOM_O.cx()) / 2.1, ey = (qy - ZOOM_O.cy()) / 3.7;
                double hole = 1 - smoothstep(0.8, 1.0, ex * ex + ey * ey);
                if (hole > 0 && glimpse > 0 && t >= typedAt(1)) {
                    tunnelPixel(buf, i, px - cx, py - cy, t, hole * glimpse, -10);
                }

                // Keystroke smoke puffs.
                for (int k = 0; k < GOOGLE.size(); k++) {
                    double age = t - typedAt(k);
                    if (age < 0 || age > 0.9) continue;
                    Letter l = GOOGLE.get(k);
                    double dx = qx - l.cx(), dy = qy - l.cy();
                    double d = Math.hypot(dx, dy) * scale;
                    double radius = 2.5 + age * 13;
                    double n = Noise.smoke(px * 0.12 + k * 7, py * 0.12, t * 0.8);
                    double a = Math.exp(-Math.pow(d / radius, 2)) * Math.pow(1 - age / 0.9, 2)
                            * smoothstep(-0.35, 0.45, n);
                    buf.add(i, lerp(l.color(), smokeColor(n + k * 0.2), 0.4), a * 0.9);
                }

                // Letters (after their own typing time), with a pop-in overshoot.
                for (int k = 0; k < GOOGLE.size(); k++) {
                    double age = t - typedAt(k);
                    if (age < 0) continue;
                    Letter l = GOOGLE.get(k);
                    double pop = 1 + 0.45 * Math.exp(-age * 14) * Math.cos(age * 20);
                    double gx = (qx - l.cx()) / pop + l.width() / 2.0, gy = (qy - l.cy()) / pop + 5.5;
                    if (gx < -1 || gy < -1 || gx > l.width() + 1 || gy > 12) continue;
                    double a = smoothstep(0.3, 0.7, coverage(l.ch(), gx, gy));
                    if (a <= 0) continue;
                    int color = lerp(lerp(l.color(), 0xFFFFFF, 0.3), lerp(l.color(), 0, 0.25), gy / 11);
                    buf.over(i, color, a);
                }

                // Blinking cursor.
                if (t < 2.6) {
                    int typed = (int) Math.floor((t - 0.5) / 0.25) + 1;
                    typed = Math.clamp(typed, 0, GOOGLE.size());
                    double curX = typed == 0 ? GOOGLE.getFirst().x() : GOOGLE.get(typed - 1).x() + GOOGLE.get(typed - 1).width() + 0.8;
                    boolean typing = t > 0.5 && t < 2.0;
                    boolean on = typing || (t * 2.2) % 1 < 0.55;
                    if (on && t > 0.15 && qx >= curX && qx < curX + 1.2 && qy >= 25.5 && qy < 37.5) {
                        buf.over(i, 0xFFFFFF, 1);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ act 2: smoke tunnel + dissolve

    private static double dissolveThreshold(double t) {
        return t < 5.0 ? -10 : -0.75 + 1.9 * smooth((t - 5.0) / 1.2);
    }

    private static void tunnel(Buffer buf, double t, double alpha, double threshold) {
        for (int y = 0; y < W; y++) {
            for (int x = 0; x < W; x++) {
                tunnelPixel(buf, y * W + x, ux(x) - 32, ux(y) - 32, t, alpha, threshold);
            }
        }
    }

    /**
     * One pixel of the swirling smoke tunnel, (dx, dy) relative to the tunnel centre in 64-pixel units.
     * With a threshold above -1 the smoke burns away where its density is below the threshold (the dissolve).
     */
    private static void tunnelPixel(Buffer buf, int i, double dx, double dy, double t, double alpha, double threshold) {
        double r = Math.hypot(dx, dy);
        double a = Math.atan2(dy, dx);
        double depth = 16 / (r + 3) + t * 2.0;                      // flying forward = depth scrolls
        double swirl = a + t * 0.55 + 2.2 / (r + 2);
        double polar = Noise.smoke(Math.cos(swirl) * 2.6, Math.sin(swirl) * 2.6, depth * 0.8);
        // Blend with billowing screen-space smoke so it reads as clouds, not rays.
        double cs = Math.cos(t * 0.4), sn = Math.sin(t * 0.4), zoom = 0.09 / (1 + 0.15 * ((t * 1.3) % 4));
        double cloud = Noise.smoke((dx * cs - dy * sn) * 0.09, (dx * sn + dy * cs) * 0.09, t * 0.7);
        double n = polar * 0.55 + cloud * 0.6;
        double density = smoothstep(-0.55, 0.45, n);
        int color = lerp(smokeColor(n * 0.9 + depth * 0.1 + t * 0.05), 0xFFFFFF, 0.08);
        double brightness = density * (0.65 + 0.35 * clamp01(r / 22)) + 0.3;
        double light = Math.exp(-r / 4.5) * 0.9;                    // light at the end of the tunnel
        // Opaque smoke: darkness is baked into the color, so nothing behind it shows through until it dissolves.
        int smoke = lerp(0x0B0620, color, Math.min(1, brightness));
        if (threshold <= -1) {
            buf.over(i, smoke, alpha);
            buf.add(i, 0xFFFFFF, alpha * light);
            return;
        }
        // Dissolve: smoke stays where it is denser than the threshold, with glowing edges.
        double keep = smoothstep(threshold - 0.04, threshold + 0.14, n);
        double edge = Math.exp(-Math.pow((n - threshold) / 0.07, 2)) * smoothstep(-0.75, -0.45, threshold);
        buf.over(i, smoke, alpha * keep);
        buf.add(i, lerp(color, 0xFFFFFF, 0.6), alpha * edge * 0.8 * (threshold < 1 ? 1 : 0));
        buf.add(i, 0xFFFFFF, alpha * light * keep);
    }

    // ------------------------------------------------------------------ act 3: candyland fly-over (voxel space)

    private static final int MAP = 512;
    private static final float[] HEIGHT = new float[MAP * MAP];
    private static final int[] COLOR = new int[MAP * MAP];
    private static final byte[] FLAGS = new byte[MAP * MAP];
    private static final byte WATER = 1, CIRCUIT = 2, TOWER = 4;

    static {
        for (int y = 0; y < MAP; y++) {
            for (int x = 0; x < MAP; x++) {
                double nx = x / 95.0, ny = y / 95.0;
                double base = Noise.fbm(nx, ny, 0.5, 5);
                double ridge = 1 - Math.abs(Noise.fbm(nx * 1.7 + 3, ny * 1.7, 1.5, 4));
                double h = 20 + base * 16 + Math.pow(Math.max(0, ridge), 3) * 30 * smoothstep(-0.15, 0.5, base);
                double river = Math.abs(Noise.fbm(x / 150.0 + 9, y / 150.0, 2.5, 3));
                int i = y * MAP + x;
                if (river < 0.055) {
                    h = h * 0.55 + 2;
                    FLAGS[i] = WATER;
                }
                if (h < 11) {
                    h = 10.5;
                    FLAGS[i] = WATER;
                }
                HEIGHT[i] = (float) h;
            }
        }
        for (int y = 0; y < MAP; y++) {
            for (int x = 0; x < MAP; x++) {
                int i = y * MAP + x;
                double h = HEIGHT[i];
                int color;
                if (FLAGS[i] == WATER) {
                    color = lerp(0x1A73E8, 0x7FD8FF, clamp01((h - 8) / 20));
                } else {
                    if (h < 18) color = lerp(0x16C060, 0x7CF0A6, clamp01((h - 10) / 8));
                    else if (h < 30) color = lerp(0xFF4FA3, 0xFF8CC6, (h - 18) / 12);
                    else if (h < 44) color = lerp(0x9B5CFF, 0xC4A2FF, (h - 30) / 14);
                    else color = lerp(0xFFD6EE, 0xFFFFFF, clamp01((h - 44) / 10)); // pink frosting
                    if (h >= 24 && h < 46) {                                 // candy-stripe layers
                        int band = (int) Math.floor(h / 3.2);
                        if (band % 2 == 0) color = lerp(color, lerp(FOUR[(band / 2) % 4], 0xFFFFFF, 0.15), 0.75);
                    }
                    double shade = 1 + (height(x - 1, y) - height(x + 1, y)) * 0.06 + (height(x, y - 1) - height(x, y + 1)) * 0.03;
                    color = brighten(color, Math.clamp(shade, 0.7, 1.2));
                    double hs = hash(x, y);
                    boolean grid = (x % 24 == 0 || y % 24 == 0) && h < 26 && Noise.noise(x / 40.0, y / 40.0, 9) > -0.1;
                    if (grid) {                                              // circuit traces in the meadows
                        FLAGS[i] = CIRCUIT;
                        color = (x % 24 == 0 && y % 24 == 0) ? 0xFFFFFF : FOUR[((x / 24) + (y / 24)) % 4];
                    } else if (hs < 0.0022 && h < 30) {                     // server towers
                        HEIGHT[i] += 13;
                        FLAGS[i] = TOWER;
                        color = 0xDADCE0;
                    } else if (hs < 0.02 && h > 11 && h < 34) {             // lollipop trees
                        HEIGHT[i] += 3.5f;
                        color = lerp(FOUR[(int) (hs * 1000) % 4], 0xFFFFFF, 0.15);
                    }
                }
                COLOR[i] = color;
            }
        }
    }

    private static int brighten(int c, double k) {
        int r = (int) Math.min(255, GoogleColors.r(c) * k), g = (int) Math.min(255, GoogleColors.g(c) * k);
        int b = (int) Math.min(255, GoogleColors.b(c) * k);
        return (r << 16) | (g << 8) | b;
    }

    private static double camX(double t) { return 40 + 30 * (t - 5.0); }
    private static double camY(double t) { return 256 + 22 * Math.sin(0.33 * (t - 5.0)); }

    /**
     * Flying low: the camera keeps ~10 units above the highest ground just ahead of it,
     * averaged over a short time window so it glides over hills instead of jumping.
     */
    private static double cameraHeight(double t) {
        double sum = 0;
        int n = 0;
        for (double s = t - 0.5; s <= t + 0.5; s += 0.1, n++) {
            double yaw = Math.atan2(22 * 0.33 * Math.cos(0.33 * (s - 5.0)), 30);
            double top = 0;
            for (double z = 0; z <= 28; z += 4) {
                top = Math.max(top, heightAt(camX(s) + Math.cos(yaw) * z, camY(s) + Math.sin(yaw) * z));
            }
            sum += top;
        }
        return sum / n + 10;
    }

    private static double height(int x, int y) {
        return HEIGHT[Math.floorMod(y, MAP) * MAP + Math.floorMod(x, MAP)];
    }

    private static double heightAt(double x, double y) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        double tx = x - x0, ty = y - y0;
        return (height(x0, y0) * (1 - tx) + height(x0 + 1, y0) * tx) * (1 - ty)
                + (height(x0, y0 + 1) * (1 - tx) + height(x0 + 1, y0 + 1) * tx) * ty;
    }

    private static void world(Buffer buf, double t) {
        double tt = t - 5.0;
        double camX = camX(t), camY = camY(t);
        double yaw = Math.atan2(22 * 0.33 * Math.cos(0.33 * tt), 30);
        double rise = smooth((t - 10.3) / 1.0);
        double camH = cameraHeight(t) + 420 * rise * rise;
        double horizon = 58 - 170 * rise;
        double focal = 82;
        double maxDist = 190;
        int[] sky = {0xFFC7A8, 0xFF8AC6, 0xB06BFF, 0x3A1F8A, 0x160A3A};

        for (int x = 0; x < W; x++) {
            double ang = (x + 0.5) / W - 0.5;
            double rayAng = yaw + ang * 1.05;
            double cosA = Math.cos(rayAng), sinA = Math.sin(rayAng), corr = Math.cos(ang * 1.05);
            int yBuf = W;
            double z = 1, dz = 0.35;
            while (z < maxDist && yBuf > 0) {
                double wx = camX + cosA * z, wy = camY + sinA * z;
                double h = heightAt(wx, wy);
                int sy = (int) Math.floor((camH - h) / (z * corr) * focal + horizon);
                if (sy < yBuf) {
                    int mx = Math.floorMod((int) Math.floor(wx), MAP), my = Math.floorMod((int) Math.floor(wy), MAP);
                    int mi = my * MAP + mx;
                    int base = COLOR[mi];
                    double fog = smoothstep(0.4, 1.0, z / maxDist) * (1 - rise * 0.7);
                    for (int y = Math.max(0, sy); y < yBuf; y++) {
                        int col = base;
                        if (FLAGS[mi] == WATER) {
                            boolean fall = yBuf - sy > 3;                     // a steep river span = waterfall
                            double flow = fall ? ((y - t * 55) % 4 + 4) % 4 : 0;
                            double shimmer = Math.max(0, Math.sin(mx * 0.35 + my * 0.21 + t * 5));
                            col = lerp(col, 0xFFFFFF, fall ? (flow < 1.5 ? 0.55 : 0.1) : 0.3 * shimmer);
                        } else if (FLAGS[mi] == CIRCUIT) {
                            double pulse = 0.5 + 0.5 * Math.sin(t * 8 - (mx + my) * 0.25);
                            boolean packet = Math.floorMod((int) (mx + my - t * 45), 26) < 2;
                            col = packet ? 0xFFFFFF : lerp(col, 0xFFFFFF, 0.35 * pulse);
                        } else if (FLAGS[mi] == TOWER && y == sy && ((int) (t * 3 + mx)) % 2 == 0) {
                            col = RED;                                         // blinking tower light
                        }
                        col = lerp(col, sky[0], fog);
                        buf.over(y * W + x, col, 1);
                    }
                    yBuf = sy;
                }
                z += dz;
                dz *= 1.022;
            }
            // Sky with drifting colorful clouds.
            for (int y = 0; y < yBuf; y++) {
                double v = clamp01((horizon - y) / 95.0);
                int col = GoogleColors.gradient(sky, v);
                double sx = x + yaw * 90, n = Noise.fbm(sx * 0.03, y * 0.07, t * 0.12 + 7, 4);
                double cloud = smoothstep(0.05, 0.55, n) * 0.85;
                int cc = lerp(smokeColor(Noise.fbm(sx * 0.012, y * 0.02, 3, 2) * 1.5 + 0.2), 0xFFFFFF, 0.45);
                buf.over(y * W + x, lerp(col, cc, cloud), 1);
            }
        }
    }

    /** Colorful clouds rushing past as the camera shoots up; covers the switch from world to space. */
    private static void cloudRush(Buffer buf, double t) {
        double k = Math.pow(Math.sin(Math.PI * clamp01((t - 10.65) / 1.1)), 0.8);
        for (int y = 0; y < W; y++) {
            for (int x = 0; x < W; x++) {
                double n = Noise.smoke(x * 0.03, y * 0.03 + t * 2.6, t * 0.6);
                double a = k * smoothstep(-0.45, 0.35, n);
                buf.over(y * W + x, lerp(smokeColor(n * 1.3 + t * 0.1), 0xFFFFFF, 0.45), a);
            }
        }
    }

    // ------------------------------------------------------------------ act 4: planet -> dot of GEMINi -> poof

    private static void space(Buffer buf, double t) {
        double fadeIn = smoothstep(11.15, 11.4, t);
        // Nebula smoke and stars.
        double nebula = 0.22 * (1 - smoothstep(14.6, 15.4, t));
        for (int y = 0; y < W; y++) {
            for (int x = 0; x < W; x++) {
                int i = y * W + x;
                double n = Noise.smoke(x * 0.022, y * 0.022, t * 0.12 + 20);
                buf.over(i, 0, fadeIn);                                     // space replaces the world
                buf.add(i, smokeColor(n * 1.4 + t * 0.03), fadeIn * nebula * smoothstep(0.05, 0.75, n));
            }
        }
        for (int s = 0; s < 46; s++) {
            int x = (int) (hash(s, 1) * W), y = (int) (hash(s, 2) * W);
            double tw = 0.35 + 0.65 * Math.max(0, Math.sin(t * (2 + hash(s, 3) * 4) + s));
            buf.add(y * W + x, 0xFFFFFF, tw * fadeIn * (1 - smoothstep(14.8, 15.4, t)));
        }

        // Planet radius and position (hi-res pixels).
        double r, cx = 64, cy = 64;
        if (t < 12.6) {
            r = 260 * Math.exp(Math.log(40 / 260.0) * smooth((t - 11.15) / 1.45));
        } else {
            double k = smooth((t - 12.6) / 1.2);
            r = 40 * Math.exp(Math.log(DOT_R * 2 / 40) * k);
            cx = 64 + (DOT_X * 2 - 64) * k;
            cy = 64 + (DOT_Y * 2 - 64) * k - 18 * Math.sin(Math.PI * k);   // a little arc on the way
        }
        if (t < 14.8) planet(buf, t, cx, cy, r);

        if (t >= 12.6 && t < 14.8) geminiWord(buf, t);
        if (t >= 13.7 && t < 14.55) sparkle(buf, t, DOT_X * 2, DOT_Y * 2);
        if (t >= 14.8) poof(buf, t);
    }

    private static void planet(Buffer buf, double t, double cx, double cy, double r) {
        double rot = t * 0.45;
        double lx = -0.55, ly = -0.6, lz = 0.58;
        int reach = (int) Math.ceil(r * 1.25) + 2;
        for (int y = (int) cy - reach; y <= cy + reach; y++) {
            for (int x = (int) cx - reach; x <= cx + reach; x++) {
                if (x < 0 || y < 0 || x >= W || y >= W) continue;
                int i = y * W + x;
                double dx = (x + 0.5 - cx) / r, dy = (y + 0.5 - cy) / r;
                double d2 = dx * dx + dy * dy, d = Math.sqrt(d2);
                double ang = Math.atan2(dy, dx);
                if (d < 1) {
                    double dz = Math.sqrt(1 - d2);
                    double lat = Math.asin(-dy), lon = Math.atan2(dx, dz) + rot;
                    // Ping-pong longitude so the world map wraps around the planet without a seam.
                    double u = Math.abs(((lon / Math.PI) % 2 + 2) % 2 - 1) * (MAP - 1);
                    double v = (0.5 - lat / Math.PI) * (MAP - 1);
                    int mi = (int) Math.clamp(v, 0, MAP - 1) * MAP + (int) Math.clamp(u, 0, MAP - 1);
                    int col = COLOR[mi];
                    if (FLAGS[mi] == WATER) col = lerp(0x1A73E8, 0x4FC3F7, 0.5);
                    // Swirling cloud layer.
                    double cl = Noise.fbm(dx * 2.2 + Math.cos(rot) * 2, dy * 2.2, dz * 2.2 + t * 0.15, 4);
                    col = lerp(col, lerp(0xFFFFFF, smokeColor(cl + t * 0.05), 0.25), smoothstep(0.15, 0.55, cl) * 0.85);
                    double light = 0.28 + 0.85 * Math.max(0, dx * lx + dy * ly + dz * lz);
                    col = lerp(0, col, Math.min(1.15, light) / 1.15);
                    double rim = Math.pow(1 - dz, 3);                         // atmosphere at the limb
                    col = lerp(col, GoogleColors.gradient(GEMINI_GRADIENT, (ang + Math.PI) / (2 * Math.PI)), rim * 0.7);
                    double edgeAa = clamp01((1 - d) * r);                       // anti-aliased edge
                    buf.over(i, col, edgeAa);
                }
                if (d >= 0.9 && d < 1.25) {                                    // atmosphere glow
                    double glow = Math.pow(1 - clamp01((d - 0.95) / 0.3), 2) * 0.75;
                    buf.add(i, GoogleColors.gradient(GEMINI_GRADIENT, (ang + Math.PI) / (2 * Math.PI)), glow);
                }
            }
        }
    }

    private static int geminiColor(double x64, double t) {
        Letter first = GEMINI.getFirst(), last = GEMINI.getLast();
        double p = (x64 - first.x()) / (last.x() + last.width() - first.x());
        int col = GoogleColors.gradient(GEMINI_GRADIENT, clamp01(p));
        double sheen = Math.exp(-Math.pow((x64 - (first.x() - 6 + (t - 13.9) * 110)) / 3.5, 2));
        return lerp(col, 0xFFFFFF, 0.6 * sheen);
    }

    /** GEMINi condenses out of Gemini-colored smoke. */
    private static void geminiWord(Buffer buf, double t) {
        double reveal = smooth((t - 12.8) / 1.0);
        double haze = Math.sin(Math.PI * clamp01((t - 12.6) / 1.5)) * 0.55;
        for (int y = 36; y < 82; y++) {
            for (int x = 0; x < W; x++) {
                int i = y * W + x;
                double px = ux(x), py = ux(y);
                double n = Noise.smoke(px * 0.16, py * 0.16, t * 0.6 + 3);
                if (haze > 0) {
                    double band = Math.exp(-Math.pow((py - 33) / 7, 2));
                    buf.add(i, GoogleColors.cyclic(GEMINI_GRADIENT, n + px / 64 + t * 0.2),
                            haze * band * smoothstep(-0.2, 0.6, n) * (1 - reveal * 0.7));
                }
                for (Letter l : GEMINI) {
                    double gx = px - l.x(), gy = py - l.y();
                    if (gx < -1 || gy < -1 || gx > l.width() + 1 || gy > 12) continue;
                    double a = smoothstep(0.3, 0.7, coverage(l.ch(), gx, gy));
                    double nv = n * 0.5 + 0.5;
                    double thr = 1.05 - 1.25 * reveal;
                    a *= smoothstep(thr - 0.06, thr + 0.06, nv);
                    if (a > 0) {
                        int col = geminiColor(px, t);
                        col = lerp(lerp(col, 0xFFFFFF, 0.25), lerp(col, 0, 0.2), gy / 11);
                        buf.over(i, col, a);
                    }
                }
            }
        }
    }

    /** The four-pointed Gemini sparkle flashing on the planet dot. */
    private static void sparkle(Buffer buf, double t, double cx, double cy) {
        double k = Math.sin(Math.PI * clamp01((t - 13.7) / 0.85));
        double len = 4 + 20 * k;
        for (int y = (int) (cy - len - 2); y <= cy + len + 2; y++) {
            for (int x = (int) (cx - len - 2); x <= cx + len + 2; x++) {
                if (x < 0 || y < 0 || x >= W || y >= W) continue;
                double dx = Math.abs(x + 0.5 - cx), dy = Math.abs(y + 0.5 - cy);
                // Concave four-point star: |x|^p + |y|^p with p < 1.
                double s = Math.pow(dx / len, 0.5) + Math.pow(dy / len, 0.5);
                if (s > 1.4) continue;
                double a = k * clamp01(1.25 - s) * 1.3;
                int col = lerp(0xFFFFFF, GoogleColors.gradient(GEMINI_GRADIENT, clamp01((dx + dy) / len)), clamp01(s));
                buf.add(y * W + x, col, a);
            }
        }
    }

    // ------------------------------------------------------------------ the poof

    private record Particle(double x, double y, int color, double seed) {}

    private static final List<Particle> PARTICLES = new ArrayList<>();

    static {
        for (Letter l : GEMINI) {
            String[] g = GLYPHS.get(l.ch());
            for (int gy = 0; gy < g.length; gy++) {
                for (int gx = 0; gx < g[gy].length(); gx++) {
                    if (g[gy].charAt(gx) != '#') continue;
                    double x = l.x() + gx + 0.5;
                    PARTICLES.add(new Particle(x, l.y() + gy + 0.5, geminiColor(x, 14.8), hash((int) x * 7, gy * 13 + 1)));
                }
            }
        }
        for (int k = 0; k < 40; k++) {
            double a = hash(k, 77) * Math.PI * 2, d = Math.sqrt(hash(k, 78)) * DOT_R;
            PARTICLES.add(new Particle(DOT_X + Math.cos(a) * d, DOT_Y + Math.sin(a) * d,
                    k % 2 == 0 ? 0x4FC3F7 : 0x6DD58C, hash(k, 79)));
        }
    }

    private static void poof(Buffer buf, double t) {
        double tau = t - 14.8;
        if (tau > 0.95) return;
        double fade = Math.pow(1 - tau / 0.95, 1.5);
        // Ring of colorful smoke pushed outward by the blast.
        double ring = 8 + tau * 75;
        for (int y = 0; y < W; y++) {
            for (int x = 0; x < W; x++) {
                double dx = x + 0.5 - 64, dy = y + 0.5 - 64;
                double r = Math.hypot(dx, dy);
                double n = Noise.smoke(x * 0.05, y * 0.05, t * 1.2 + 40);
                double a = Math.exp(-Math.pow((r - ring) / 12, 2)) * fade * smoothstep(-0.3, 0.5, n) * 0.75;
                buf.add(y * W + x, smokeColor(n + Math.atan2(dy, dx) / (2 * Math.PI) + t * 0.2), a);
            }
        }
        // Every pixel of the word becomes a particle of dust.
        for (Particle p : PARTICLES) {
            double dx = p.x() - 32, dy = p.y() - 32;
            double len = Math.max(0.5, Math.hypot(dx, dy));
            double jitter = (p.seed() - 0.5) * 1.6;
            double dirX = dx / len * Math.cos(jitter) - dy / len * Math.sin(jitter);
            double dirY = dx / len * Math.sin(jitter) + dy / len * Math.cos(jitter);
            double speed = 22 + 30 * p.seed(), drag = 3.2;
            double travel = speed * (1 - Math.exp(-drag * tau)) / drag;
            double swirl = Math.sin(tau * 5 + p.seed() * 12) * 2.2 * tau;
            double x = p.x() + dirX * travel - dirY * swirl;
            double y = p.y() + dirY * travel + dirX * swirl - 5 * tau * tau;
            int col = lerp(p.color(), lerp(FOUR[(int) (p.seed() * 4) % 4], 0xFFFFFF, 0.2), smooth(tau / 0.35));
            dust(buf, x * 2, y * 2, col, fade);
        }
    }

    private static void dust(Buffer buf, double hx, double hy, int color, double a) {
        for (int y = (int) Math.floor(hy - 2); y <= hy + 2; y++) {
            for (int x = (int) Math.floor(hx - 2); x <= hx + 2; x++) {
                if (x < 0 || y < 0 || x >= W || y >= W) continue;
                double d = Math.hypot(x + 0.5 - hx, y + 0.5 - hy);
                buf.add(y * W + x, color, a * Math.exp(-d * d / 1.3));
            }
        }
    }
}
