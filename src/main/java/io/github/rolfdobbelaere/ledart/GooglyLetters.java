package io.github.rolfdobbelaere.ledart;

import static io.github.rolfdobbelaere.ledart.GoogleColors.*;

/**
 * "GOOGLE" / "DEVOXX" letters with googly eyes jump in one by one, land with squash and stretch,
 * look around and blink, spot a friendly cloud, and get blown off screen, leaving black again.
 *
 * <p>Story in 30 frames (the Pixoo limit), with per-frame delays so holds last longer than the fast parts:
 * <pre>
 *   0        black
 *   1 - 12   letters jump in, staggered, squash on landing, dust puffs
 *   13 - 17  hold: pupils wander, blink, then everyone looks left
 *   17 - 26  cloud swooshes in from the left, blowing every letter away
 *   27 - 29  last sparkles fade to black
 * </pre>
 * Letters are drawn analytically in local coordinates and sampled with 3x3 supersampling, so they
 * can rotate, stretch and fly smoothly instead of jumping a whole pixel at a time.
 */
final class GooglyLetters {

    private static final String WORD_TOP = "GOOGLE";
    private static final String WORD_BOTTOM = "DEVOXX";
    private static final int[] TOP_COLORS = {BLUE, RED, YELLOW, BLUE, GREEN, RED};
    // Bottom row uses the Gemini gradient so the two words read as two different "voices".
    private static final int[] BOTTOM_COLORS = {0x4796E3, 0x6C86D8, 0x9177C7, 0xB06DA0, 0xCA6673, 0xE0717A};

    private static final int GLYPH_W = 8, GLYPH_H = 11;
    private static final int ADVANCE = 10;
    private static final int LEFT = 3;
    private static final int TOP_ROW_Y = 15, BOTTOM_ROW_Y = 43; // letter top edges

    private static final double CLOUD_START = 16.5, CLOUD_SPEED = 11, CLOUD_X0 = -30;
    private static final int SS = 3; // supersampling per axis

    private static final java.util.Map<Character, String[]> GLYPHS = java.util.Map.of(
            'G', new String[]{
                    "..####..",
                    ".######.",
                    "##....##",
                    "##......",
                    "##......",
                    "##..####",
                    "##..####",
                    "##....##",
                    "##....##",
                    ".######.",
                    "..####.."},
            'O', new String[]{
                    "..####..",
                    ".######.",
                    "##....##",
                    "##....##",
                    "##....##",
                    "##....##",
                    "##....##",
                    "##....##",
                    "##....##",
                    ".######.",
                    "..####.."},
            'L', new String[]{
                    "##......",
                    "##......",
                    "##......",
                    "##......",
                    "##......",
                    "##......",
                    "##......",
                    "##......",
                    "##......",
                    "#######.",
                    "#######."},
            'E', new String[]{
                    "#######.",
                    "#######.",
                    "##......",
                    "##......",
                    "######..",
                    "######..",
                    "##......",
                    "##......",
                    "##......",
                    "#######.",
                    "#######."},
            'D', new String[]{
                    "######..",
                    "#######.",
                    "##...###",
                    "##....##",
                    "##....##",
                    "##....##",
                    "##....##",
                    "##....##",
                    "##...###",
                    "#######.",
                    "######.."},
            'V', new String[]{
                    "##....##",
                    "##....##",
                    "##....##",
                    "##....##",
                    "##....##",
                    ".##..##.",
                    ".##..##.",
                    ".##..##.",
                    "..####..",
                    "..####..",
                    "...##..."},
            'X', new String[]{
                    "##....##",
                    "##....##",
                    ".##..##.",
                    ".##..##.",
                    "..####..",
                    "...##...",
                    "..####..",
                    ".##..##.",
                    ".##..##.",
                    "##....##",
                    "##....##"});

    private GooglyLetters() {}

    /** Per-frame timing: linger on the empty screen and the hold, rush through the swoosh. */
    static int delayMs(int f) {
        if (f == 0) return 400;
        if (f <= 12) return 70;
        if (f <= 16) return 160;
        if (f <= 26) return 55;
        return 140;
    }

    static Canvas frame(int f) {
        Canvas c = new Canvas();
        for (int i = 0; i < 12; i++) {
            letter(c, i, f);
        }
        dust(c, f);
        cloud(c, f);
        sparkles(c, f);
        return c;
    }

    // ---------------------------------------------------------------- letters

    private record Pose(double x, double y, double sx, double sy, double rot, double alpha,
                        double pupilX, double pupilY, boolean blink, double eyeScale) {}

    private static char ch(int i) { return (i < 6 ? WORD_TOP : WORD_BOTTOM).charAt(i % 6); }
    private static int color(int i) { return i < 6 ? TOP_COLORS[i] : BOTTOM_COLORS[i - 6]; }
    private static double homeX(int i) { return LEFT + (i % 6) * ADVANCE + GLYPH_W / 2.0; }
    private static double homeY(int i) { return (i < 6 ? TOP_ROW_Y : BOTTOM_ROW_Y) + GLYPH_H; } // baseline

    private static double startFrame(int i) {
        // GOOGLE first, then DEVOXX; a slight random-feeling shuffle keeps it lively.
        int[] order = {0, 2, 1, 4, 3, 5, 6, 8, 7, 10, 9, 11};
        return 1 + order[i] * 0.62;
    }

    private static double hitFrame(int i) {
        // Wind reaches ~24 px ahead of the cloud centre, so letters fly before the cloud touches them.
        return CLOUD_START + (homeX(i) - 24 - CLOUD_X0) / CLOUD_SPEED + (i < 6 ? 0 : 0.25);
    }

    private static Pose pose(int i, double f) {
        double x = homeX(i), y = homeY(i);
        double start = startFrame(i);
        double jump = 3.2; // frames in the air
        double t = f - start;
        if (t < 0) return null;

        double hit = hitFrame(i);
        if (f >= hit) {
            // Blown away: accelerate right, curl upward, spin, eyes look back at the cloud.
            double a = f - hit;
            double dir = i % 2 == 0 ? 1 : -1;
            double alpha = Math.clamp(1.6 - a * 0.45, 0, 1);
            return new Pose(x + 5 * a + 3.2 * a * a, y - 2.5 * a - (i < 6 ? 1.2 : 0.4) * a * a,
                    1 + 0.08 * a, 1 - 0.06 * a, dir * 0.45 * a, alpha,
                    -1, -0.6, false, 1.12);
        }

        if (t < jump) {
            // Jump in from below the screen on a parabola that overshoots, stretched while flying.
            double u = t / jump;
            double fromY = 64 + GLYPH_H + 4;
            double yy = fromY + (y - fromY) * u - 26 * u * (1 - u) * 1.0;
            double vy = (y - fromY) - 26 * (1 - 2 * u); // derivative, negative = rising
            double stretch = 1 + Math.min(0.35, Math.abs(vy) / 120);
            double tilt = (i % 2 == 0 ? -1 : 1) * 0.25 * (1 - u);
            return new Pose(x, yy, 1 / Math.sqrt(stretch), stretch, tilt, 1,
                    0, Math.clamp(-vy / 40, -1, 1), false, 1);
        }

        double land = t - jump;
        // Squash on impact, then a damped wobble back to rest.
        double squash = 0.32 * Math.exp(-land * 1.4) * Math.cos(land * 2.6);
        double sy = 1 - squash, sx = 1 + squash * 0.8;
        double px = 0, py = Math.clamp(0.9 * Math.exp(-land * 0.9) * Math.cos(land * 3), -1, 1);

        // Hold choreography (absolute frames).
        boolean blink = false;
        if (f >= 13 && f < 15.5) {
            px = Math.sin(f * 1.9 + i * 1.3) * 0.9; // pupils wander, everyone different
            py = Math.cos(f * 1.4 + i) * 0.5;
            blink = (i % 3 == 0 && f >= 14 && f < 15) || (i % 3 == 1 && f >= 15 && f < 16);
        } else if (f >= 15.5) {
            px = -1; // something is coming from the left...
            py = -0.2;
        }
        double eyes = f >= 16 ? 1.12 : 1; // ...and the eyes go wide
        double bob = f >= 12.5 && f < 16 ? Math.sin(f * 2.2 + i) * 0.4 : 0;
        return new Pose(x, y + bob, sx, sy, 0, 1, px, py, blink, eyes);
    }

    private static void letter(Canvas c, int i, int f) {
        Pose p = pose(i, f);
        if (p == null || p.alpha() <= 0) return;
        String[] glyph = GLYPHS.get(ch(i));
        int base = color(i);
        double cos = Math.cos(-p.rot()), sin = Math.sin(-p.rot());

        // Pivot at the bottom centre of the letter, so squash happens "on the floor".
        int minX = (int) Math.floor(p.x() - 12), maxX = (int) Math.ceil(p.x() + 12);
        int minY = (int) Math.floor(p.y() - 24), maxY = (int) Math.ceil(p.y() + 6);
        for (int py = minY; py <= maxY; py++) {
            for (int px = minX; px <= maxX; px++) {
                if (px < 0 || py < 0 || px >= Canvas.SIZE || py >= Canvas.SIZE) continue;
                double r = 0, g = 0, b = 0, cover = 0, shadow = 0;
                for (int sy = 0; sy < SS; sy++) {
                    for (int sx = 0; sx < SS; sx++) {
                        double wx = px + (sx + 0.5) / SS - p.x();
                        double wy = py + (sy + 0.5) / SS - p.y();
                        // Inverse transform into local letter space (u right, v up from baseline).
                        double lx = (wx * cos - wy * sin) / p.sx();
                        double ly = (wx * sin + wy * cos) / p.sy();
                        int col = sample(glyph, base, lx + GLYPH_W / 2.0, ly + GLYPH_H, p);
                        if (col >= 0) {
                            r += (col >> 16) & 0xFF; g += (col >> 8) & 0xFF; b += col & 0xFF;
                            cover++;
                        } else if (sample(glyph, base, lx + GLYPH_W / 2.0 - 0.8, ly + GLYPH_H - 0.8, p) >= 0) {
                            shadow++; // 3D drop shadow down-right
                        }
                    }
                }
                double n = SS * SS;
                if (cover > 0) {
                    double k = p.alpha();
                    c.add(px, py, ((int) (r / cover) << 16) | ((int) (g / cover) << 8) | (int) (b / cover),
                            k * cover / n);
                }
                if (shadow > 0) c.add(px, py, lerp(0, base, 0.22), p.alpha() * shadow / n);
            }
        }
        // Soft glow halo around the letter body.
        c.glowDot(p.x(), p.y() - GLYPH_H / 2.0, 0, base, 7, 0.06 * p.alpha());
    }

    /**
     * Colour of a letter (with its googly eyes) at local coordinates, or -1 when transparent.
     * (u, v): u in [0, 8) left to right, v in [0, 11) top to bottom of the glyph.
     */
    private static int sample(String[] glyph, int base, double u, double v, Pose p) {
        // Googly eyes sit on the top edge of the letter, sticking out above it.
        for (int e = 0; e < 2; e++) {
            double ex = e == 0 ? 1.7 : 6.3, ey = -1.0;
            double rad = 2.25 * p.eyeScale();
            double dx = u - ex, dy = v - ey;
            double d = Math.hypot(dx, dy);
            if (d <= rad) {
                if (d > rad - 0.35) return 0x3C4043; // thin dark rim keeps eyes crisp on bright letters
                if (p.blink()) return Math.abs(dy) < 0.5 ? 0x202124 : lerp(base, 0x000000, 0.15);
                double pr = 1.25;
                double room = rad - pr - 0.15;
                double pupilX = ex + p.pupilX() * room, pupilY = ey + p.pupilY() * room;
                if (Math.hypot(u - pupilX, v - pupilY) <= pr) return 0x050505;
                return 0xFFFFFF;
            }
        }
        int gx = (int) Math.floor(u), gy = (int) Math.floor(v);
        if (gx < 0 || gy < 0 || gx >= GLYPH_W || gy >= GLYPH_H || glyph[gy].charAt(gx) != '#') return -1;
        // Vertical gradient: light top, rich middle, darker bottom, plus a bevel on the lit edges.
        double t = v / GLYPH_H;
        int color = t < 0.5 ? lerp(lerp(base, 0xFFFFFF, 0.35), base, t * 2) : lerp(base, lerp(base, 0, 0.35), (t - 0.5) * 2);
        boolean litEdge = isEmpty(glyph, gx, gy - 1) || isEmpty(glyph, gx - 1, gy);
        boolean darkEdge = isEmpty(glyph, gx, gy + 1) || isEmpty(glyph, gx + 1, gy);
        if (litEdge && !darkEdge) color = lerp(color, 0xFFFFFF, 0.25);
        if (darkEdge && !litEdge) color = lerp(color, 0, 0.25);
        return color;
    }

    private static boolean isEmpty(String[] glyph, int x, int y) {
        return x < 0 || y < 0 || x >= GLYPH_W || y >= GLYPH_H || glyph[y].charAt(x) != '#';
    }

    /** Little dust puffs where letters land. */
    private static void dust(Canvas c, int f) {
        for (int i = 0; i < 12; i++) {
            double age = f - (startFrame(i) + 3.2);
            if (age < 0 || age > 2.5 || f >= hitFrame(i)) continue;
            double k = 1 - age / 2.5;
            double spread = 3 + age * 3;
            for (int s = -1; s <= 1; s += 2) {
                c.glowDot(homeX(i) + s * spread, homeY(i) - 0.5 - age * 0.6, 0.5, 0xBDC1C6, 0.8, 0.5 * k);
            }
        }
    }

    // ------------------------------------------------------------------ cloud

    private static void cloud(Canvas c, int f) {
        double t = f - CLOUD_START;
        if (t < 0) return;
        double cx = CLOUD_X0 + t * CLOUD_SPEED, cy = 32 + Math.sin(t * 1.3) * 1.5;
        if (cx > 64 + 40) return;

        // Speed lines behind the cloud, and wind streaks in Gemini colors ahead of its mouth.
        for (int k = 0; k < 6; k++) {
            double ly = cy - 9 + k * 3.6;
            double len = 10 + (k * 7) % 9;
            for (int s = 0; s < len; s++) {
                double fade = 1 - s / len;
                c.add((int) Math.round(cx - 16 - s - (k % 2) * 3), (int) Math.round(ly), 0xE8EAED, 0.35 * fade);
            }
        }
        for (int k = 0; k < 5; k++) {
            double ly = cy - 6 + k * 3 + Math.sin(t * 2 + k) * 1.2;
            double len = 14 + k % 3 * 4;
            for (int s = 0; s < len; s++) {
                double fade = Math.sin(Math.PI * s / len);
                c.add((int) Math.round(cx + 15 + s), (int) Math.round(ly + Math.sin(s * 0.5 + t * 3) * 0.8),
                        GoogleColors.gradient(GEMINI, (double) s / len), 0.55 * fade);
            }
        }

        double stretchX = 1.12 * 0.8, stretchY = 0.92 * 0.8; // motion squash, scaled down to fit the matrix
        for (int py = (int) (cy - 16); py <= cy + 14; py++) {
            for (int px = (int) (cx - 24); px <= cx + 24; px++) {
                if (px < 0 || py < 0 || px >= 64 || py >= 64) continue;
                double r = 0, g = 0, b = 0;
                int cover = 0;
                for (int sy = 0; sy < SS; sy++) {
                    for (int sx = 0; sx < SS; sx++) {
                        double u = (px + (sx + 0.5) / SS - cx) / stretchX;
                        double v = (py + (sy + 0.5) / SS - cy) / stretchY;
                        int col = cloudColor(u, v, t);
                        if (col >= 0) {
                            r += (col >> 16) & 0xFF; g += (col >> 8) & 0xFF; b += col & 0xFF;
                            cover++;
                        }
                    }
                }
                if (cover > 0) {
                    int avg = ((int) (r / cover) << 16) | ((int) (g / cover) << 8) | (int) (b / cover);
                    c.set(px, py, lerp(0, avg, (double) cover / (SS * SS)));
                }
            }
        }
    }

    /** Fluffy cloud built from overlapping circles, with a friendly blowing face looking right. */
    private static int cloudColor(double u, double v, double t) {
        double[][] puffs = {{0, 1, 9}, {-10, 4, 6.5}, {10, 3, 7}, {-5, -5, 7}, {5, -6, 6.5}, {15, 6, 4}};
        double inside = -1;
        for (double[] p : puffs) {
            inside = Math.max(inside, p[2] - Math.hypot(u - p[0], v - p[1]));
        }
        if (inside < 0 || v > 10.5) return -1;

        // Face (offset right, the direction of travel).
        double fx = 4, fy = 0;
        boolean blinkEye = ((int) (t * 2)) % 5 == 4;
        for (int e = 0; e < 2; e++) {
            double ex = fx - 3.5 + e * 7, ey = fy - 2;
            if (blinkEye ? Math.abs(v - ey) < 0.5 && Math.abs(u - ex) < 1.4 : Math.hypot(u - ex, v - ey) < 1.4) {
                return 0x202124;
            }
            if (!blinkEye && Math.hypot(u - ex + 0.5, v - ey + 0.5) < 0.5) return 0xFFFFFF;
        }
        // Puffed pink cheeks.
        if (Math.hypot(u - (fx - 5.5), v - (fy + 1.5)) < 1.6 || Math.hypot(u - (fx + 5.5), v - (fy + 1.5)) < 1.6) {
            return 0xF6AEA9;
        }
        // Round "blowing" mouth.
        double mouth = Math.hypot((u - (fx + 1)) / 1.3, v - (fy + 3));
        if (mouth < 1.5) return mouth < 0.8 ? 0x5F2120 : 0x202124;

        // Soft shading: bright top-left, cool blue-grey underside, Google-colored rim like the Cloud logo.
        if (inside < 0.9) {
            double a = Math.atan2(v, u);
            if (v > 5) return BLUE;
            if (a < -2.0 || a > 2.6) return YELLOW;
            if (a < -1.0) return RED;
            return GREEN;
        }
        double light = Math.clamp(0.5 - v / 18 - u / 60, 0, 1);
        return lerp(0xC9D7F2, 0xFFFFFF, light + 0.25);
    }

    // --------------------------------------------------------------- sparkles

    /** A few twinkles where the letters used to be, fading out as the screen returns to black. */
    private static void sparkles(Canvas c, int f) {
        if (f < 24 || f > 28) return;
        double k = f <= 26 ? (f - 23) / 3.0 : (29 - f) / 3.0;
        int[][] spots = {{8, 20}, {27, 14}, {46, 22}, {15, 46}, {36, 50}, {55, 43}, {30, 32}};
        for (int s = 0; s < spots.length; s++) {
            if ((s + f) % 3 == 0) continue;
            int color = FOUR[s % 4];
            int x = spots[s][0], y = spots[s][1];
            c.add(x, y, 0xFFFFFF, k);
            for (int d = 1; d <= 2; d++) {
                double a = k * (d == 1 ? 0.8 : 0.35);
                c.add(x + d, y, color, a);
                c.add(x - d, y, color, a);
                c.add(x, y + d, color, a);
                c.add(x, y - d, color, a);
            }
        }
    }
}
