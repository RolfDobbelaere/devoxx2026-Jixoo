package io.github.rolfdobbelaere.ledart;

import java.util.Map;

import static io.github.rolfdobbelaere.ledart.GoogleColors.*;

/**
 * The Devoxx coffee factory: a robot claw puts a DEVOXX cup on a conveyor belt, the camera follows the
 * cup past three dispensers (JAVA coffee, a Gemini rainbow soft-serve, DEV heart sprinkles), the topping
 * jiggles, the claw takes the cup away and a heart is left behind before everything fades to black.
 *
 * <p>The cup always stays in the middle of the screen: the camera moves, so the belt and the dispensers
 * scroll past. 30 frames with per-frame delays, so the pours linger and the travel is snappy.
 */
final class CoffeeFactory {

    private static final int CX = 32;           // the cup's screen x, fixed: the camera follows it
    private static final int RIM_Y = 32;        // cup rim when standing on the belt
    private static final int CUP_H = 17;        // rim to bottom
    private static final int BELT_Y = 50;
    private static final double TOP_HALF = 13, BOTTOM_HALF = 10;

    private static final int JAVA_X = 100, GEMINI_X = 160, DEV_X = 220; // dispenser world positions
    private static final double TOPPING_H = 12;

    private static final int COFFEE = 0x6F4E37, CREMA = 0xC69C6D, DEVOXX_ORANGE = 0xF57C00, PINK = 0xF06292;
    private static final int[] RAINBOW = {BLUE, 0x9177C7, RED, YELLOW, GREEN};

    // Camera keyframes (frame, world x of the left screen edge); smoothstep between them.
    private static final double[][] CAMERA = {
            {0, JAVA_X - CX - 60}, {3.5, JAVA_X - CX - 60}, {6, JAVA_X - CX}, {9, JAVA_X - CX},
            {11.2, GEMINI_X - CX}, {15, GEMINI_X - CX}, {17, DEV_X - CX}, {20, DEV_X - CX},
            {22.6, DEV_X - CX + 60}, {30, DEV_X - CX + 60}};

    private static final Map<Character, String[]> FONT = Map.of(
            'J', new String[]{"###", "..#", "..#", "#.#", ".#."},
            'A', new String[]{".#.", "#.#", "###", "#.#", "#.#"},
            'V', new String[]{"#.#", "#.#", "#.#", ".#.", ".#."},
            'D', new String[]{"##.", "#.#", "#.#", "#.#", "##."},
            'E', new String[]{"###", "#..", "##.", "#..", "###"},
            'O', new String[]{"###", "#.#", "#.#", "#.#", "###"},
            'X', new String[]{"#.#", "#.#", ".#.", "#.#", "#.#"});

    private static final String[] HEART_7 = {".##.##.", "#######", "#######", ".#####.", "..###..", "...#..."};
    private static final String[] HEART_5 = {"##.##", "#####", ".###.", "..#.."};
    private static final String[] SPARK_7 = {"...#...", "...#...", "..###..", "#######", "..###..", "...#...", "...#..."};

    // Sprinkles: (local x, height above rim) on the topping, revealed one after another.
    private static final double[][] SPRINKLES = {
            {-6, 3.2}, {4, 2.6}, {-1, 5.5}, {7, 4.8}, {-8, 1.4}, {2, 8.2}, {-4, 7}, {9, 1.8},
            {0, 10.5}, {5, 6.8}, {-3, 2}, {-7, 4.5}, {3, 4}, {1, 1.2},
            {-2, 9}, {6, 3.3}, {-9, 3}, {2, 6}, {-5, 5.6}, {8, 2.6}};

    private CoffeeFactory() {}

    static int delayMs(int f) {
        if (f == 0) return 350;
        if (f <= 4) return 120;
        if (f <= 5) return 90;
        if (f <= 8) return 140;
        if (f <= 10) return 90;
        if (f <= 14) return 130;
        if (f <= 16) return 90;
        if (f <= 19) return 140;
        if (f <= 22) return 100;
        if (f <= 25) return 120;
        if (f == 26) return 280;
        return f == 29 ? 350 : 160;
    }

    static Canvas frame(int f) {
        Canvas c = new Canvas();
        double cam = camera(f);
        double scene = sceneAlpha(f);

        dispenser(c, f, cam, JAVA_X, scene);
        dispenser(c, f, cam, GEMINI_X, scene);
        dispenser(c, f, cam, DEV_X, scene);
        belt(c, cam, scene);

        double dy = cupOffset(f);
        if (dy > -60) {
            streams(c, f, dy);
            cup(c, f, dy);
            topping(c, f, dy);
            sprinkles(c, f, dy);
        }
        steam(c, f);
        sideHearts(c, f);
        drop(c, f);
        claws(c, f, cam, dy);
        endHeart(c, f);
        return c;
    }

    // ----------------------------------------------------------------- timing

    private static double camera(double f) {
        for (int i = 0; i < CAMERA.length - 1; i++) {
            if (f <= CAMERA[i + 1][0]) {
                double t = (f - CAMERA[i][0]) / (CAMERA[i + 1][0] - CAMERA[i][0]);
                t = t * t * (3 - 2 * t);
                return CAMERA[i][1] + (CAMERA[i + 1][1] - CAMERA[i][1]) * t;
            }
        }
        return CAMERA[CAMERA.length - 1][1];
    }

    private static double velocity(double f) {
        return (camera(f + 0.25) - camera(f - 0.25)) * 2;
    }

    /** Belt and dispensers fade in from black and back out at the end. */
    private static double sceneAlpha(int f) {
        return switch (f) {
            case 0, 29 -> 0;
            case 1 -> 0.45;
            case 26 -> 0.7;
            case 27 -> 0.4;
            case 28 -> 0.15;
            default -> 1;
        };
    }

    /** Vertical offset of the cup: lowered in by the claw, later lifted away. */
    private static double cupOffset(int f) {
        return switch (f) {
            case 0 -> -100;
            case 1 -> -30;
            case 2 -> -11;
            case 25 -> -17;
            default -> f >= 26 ? -100 : 0;
        };
    }

    private static double clamp01(double v) {
        return Math.clamp(v, 0, 1);
    }

    private static double coffeeLevel(int f) { return clamp01((f - 5.6) / 2.8); }
    private static double toppingAmount(int f) { return clamp01((f - 10.8) / 3.7); }

    // ----------------------------------------------------------------- scenery

    private static void belt(Canvas c, double cam, double alpha) {
        if (alpha <= 0) return;
        int shift = (int) Math.round(cam);
        for (int x = 0; x < 64; x++) {
            int w = x + shift;
            for (int y = BELT_Y; y <= BELT_Y + 4; y++) {
                int color;
                if (y == BELT_Y) color = 0x80868B;                                  // lit top edge
                else if (y <= BELT_Y + 2) color = Math.floorMod(w, 5) == 0 ? 0x5F6368 : 0x3C4043; // treads
                else color = 0x2A2C2F;
                c.add(x, y, color, alpha);
            }
            c.add(x, BELT_Y + 11, 0x3C4043, alpha); // return strand under the rollers
        }
        // Rollers turn with the belt: a bright notch rotates around each one.
        for (int k = -1; k <= 7; k++) {
            int wx = Math.floorDiv(shift, 10) * 10 + k * 10;
            double sx = wx - cam + 5, sy = BELT_Y + 8;
            for (int y = (int) sy - 3; y <= sy + 3; y++) {
                for (int x = (int) sx - 3; x <= sx + 3; x++) {
                    double d = Math.hypot(x + 0.5 - sx, y + 0.5 - sy);
                    if (d < 2.6) c.add(x, y, d < 1 ? 0x9AA0A6 : 0x5F6368, alpha);
                }
            }
            double a = cam / 2.5;
            c.add((int) Math.round(sx + Math.cos(a) * 1.6 - 0.5), (int) Math.round(sy + Math.sin(a) * 1.6 - 0.5),
                    0xE8EAED, alpha);
        }
    }

    private static void dispenser(Canvas c, int f, double cam, int worldX, double alpha) {
        double sx = worldX - cam;
        if (sx < -16 || sx > 80 || alpha <= 0) return;
        int x0 = (int) Math.round(sx) - 12;
        boolean active = switch (worldX) {
            case JAVA_X -> f >= 6 && f <= 8;
            case GEMINI_X -> f >= 11 && f <= 14;
            default -> f >= 17 && f <= 19;
        };
        // Hanging pipe, metal body with a vertical gradient, rounded corners.
        for (int y = 0; y <= 14; y++) {
            for (int x = 0; x < 24; x++) {
                boolean corner = (y == 14 && (x == 0 || x == 23));
                if (corner) continue;
                int body = lerp(0x9AA0A6, 0x3C4043, y / 14.0);
                if (x == 0 || y == 14) body = lerp(body, 0, 0.35);
                if (x == 23) body = lerp(body, 0, 0.5);
                if (x == 1) body = lerp(body, 0xFFFFFF, 0.25);
                c.add(x0 + x, y, body, alpha);
            }
        }
        // Label panel and its icon or text.
        int panel = switch (worldX) {
            case JAVA_X -> 0xE76F00;
            case GEMINI_X -> 0x202124;
            default -> 0xFCE4EC;
        };
        for (int y = 3; y <= 11; y++) {
            for (int x = 2; x <= 21; x++) {
                c.set(x0 + x, y, lerp(0, lerp(panel, 0xFFFFFF, y == 3 ? 0.25 : 0), alpha));
            }
        }
        switch (worldX) {
            case JAVA_X -> text(c, "JAVA", x0 + 5, 5, 0xFFFFFF, alpha);
            case GEMINI_X -> {
                for (int y = 0; y < 7; y++) {
                    for (int x = 0; x < 7; x++) {
                        if (SPARK_7[y].charAt(x) != '#') continue;
                        int col = GoogleColors.gradient(new int[]{0x4796E3, 0x9177C7, 0xD96570}, (x + y) / 12.0);
                        c.set(x0 + 9 + x, 4 + y, lerp(0, col, alpha));
                    }
                }
                for (int i = 0; i < 4; i++) c.set(x0 + 3 + i * 5 % 17, 4 + i * 3 % 7, lerp(0, FOUR[i], alpha * 0.8));
            }
            default -> {
                text(c, "DEV", x0 + 3, 5, 0x5F2120, alpha);
                sprite(c, HEART_7, x0 + 14, 5, alpha, (x, y) -> y == 0 ? 0xFF8FAB : 0xE91E63);
            }
        }
        // Status light: green while pouring.
        c.set(x0 + 21, 1, lerp(0, active ? 0x34A853 : 0x8B1A1A, alpha));
        if (active) c.add(x0 + 21, 0, 0x34A853, 0.5 * alpha);
        // Nozzle.
        int[][] rows = {{-3, 3}, {-2, 2}, {-1, 1}};
        for (int r = 0; r < rows.length; r++) {
            for (int x = rows[r][0]; x < rows[r][1]; x++) {
                c.set(x0 + 12 + x, 15 + r, lerp(0, x == rows[r][0] ? 0x80868B : 0x3C4043, alpha));
            }
        }
    }

    // ------------------------------------------------------------------- cup

    private static double halfWidth(double yFromRim) {
        return TOP_HALF - (TOP_HALF - BOTTOM_HALF) * yFromRim / CUP_H;
    }

    private static void cup(Canvas c, int f, double dy) {
        int rim = (int) Math.round(RIM_Y + dy);
        String label = "DEVOXX";
        int textW = label.length() * 4 - 1;
        for (int y = rim; y <= rim + CUP_H; y++) {
            double hw = halfWidth(y - rim);
            int left = (int) Math.round(CX - hw), right = (int) Math.round(CX + hw) - 1;
            for (int x = left; x <= right; x++) {
                double across = (double) (x - left) / Math.max(1, right - left);
                int color;
                int row = y - rim;
                if (row >= 6 && row <= 13) {
                    color = lerp(lerp(DEVOXX_ORANGE, 0xFFFFFF, 0.2), lerp(DEVOXX_ORANGE, 0, 0.3), across); // sleeve
                    if (row == 6 || row == 13) color = lerp(color, 0, 0.3);
                } else {
                    color = lerp(0xFFFFFF, 0xAEB4BA, across * across);                                 // paper
                }
                if (x == left) color = lerp(color, 0xFFFFFF, 0.4);
                if (x == right || row == CUP_H) color = lerp(color, 0, 0.35);
                c.set(x, y, color);
            }
        }
        // DEVOXX on the sleeve.
        text(c, label, CX - textW / 2, rim + 8, 0xFFFFFF, 1);

        // Rim lip and the opening, seen slightly from above.
        double level = coffeeLevel(f);
        double slosh = Math.clamp(-velocity(f) * 0.05, -1, 1);
        for (int y = rim - 3; y <= rim + 2; y++) {
            for (int x = CX - 15; x <= CX + 15; x++) {
                double ex = (x + 0.5 - CX) / 13.6, ey = (y + 0.5 - rim) / 2.6;
                double outer = ex * ex + ey * ey;
                if (outer > 1) continue;
                double ix = (x + 0.5 - CX) / 12, iy = (y + 0.5 - rim) / 1.8;
                if (ix * ix + iy * iy > 1) {
                    c.set(x, y, y < rim ? 0xFFFFFF : 0xDADCE0); // lip
                    continue;
                }
                int inside = lerp(0x241A14, COFFEE, level);
                // Crema ring once it's full, pushed to one side while the cup moves (slosh).
                double crema = Math.hypot(ix + slosh * 0.5, iy * 1.4);
                if (level > 0.7 && crema > 0.55) inside = lerp(inside, CREMA, (level - 0.7) / 0.3 * 0.8);
                if (level > 0 && Math.abs(slosh) > 0.2 && ix * Math.signum(slosh) < -0.6) inside = lerp(inside, CREMA, 0.6);
                c.set(x, y, inside);
            }
        }
    }

    /** The Gemini soft-serve: stacked rainbow rolls, swirl-striped, leaning and jiggling. */
    private static void topping(Canvas c, int f, double dy) {
        double amount = toppingAmount(f);
        if (amount <= 0) return;
        Transform tr = toppingTransform(f);
        double rim = RIM_Y + dy - 1;
        for (int y = (int) (rim - 18); y <= rim + 1; y++) {
            for (int x = CX - 17; x <= CX + 17; x++) {
                double h = (rim - (y + 0.5)) / tr.sy();
                if (h < -0.6 || h > TOPPING_H * amount) continue;
                double lx = (x + 0.5 - CX) / tr.sx() - tr.lean() * Math.max(0, h);
                double hw = profile(Math.max(0, h));
                if (Math.abs(lx) > hw) continue;
                double roll = ((h + 0.4) % 3.1) / 3.1;
                int col = GoogleColors.cyclic(RAINBOW, lx / 22 + h * 0.13 + f * 0.02);
                col = lerp(col, 0xFFFFFF, 0.18);                               // creamy, pastel-ish
                if (roll > 0.72) col = lerp(col, 0xFFFFFF, 0.35);             // top of each roll catches light
                else if (roll < 0.22) col = lerp(col, 0, 0.28);               // crease under each roll
                if (Math.abs(lx) > hw - 1) col = lerp(col, 0, 0.22);           // rounded edges
                if (lx < -hw + 2.5 && roll > 0.4) col = lerp(col, 0xFFFFFF, 0.25);
                c.set(x, y, col);
            }
        }
    }

    private static double profile(double h) {
        double base = 12.6 * Math.pow(Math.max(0, 1 - h / (TOPPING_H + 0.6)), 0.75);
        double bumps = 1.1 * Math.max(0, Math.sin((h + 0.4) / 3.1 * Math.PI));
        return Math.max(0.6, base + bumps - 0.6);
    }

    private record Transform(double sx, double sy, double lean) {}

    private static Transform toppingTransform(int f) {
        double v = velocity(f);
        double lean = Math.clamp(-v * 0.012, -0.35, 0.35);
        double sx = 1, sy = 1;
        if (f >= 15 && f <= 17) lean += 0.18 * Math.sin(f * 2.4);          // little wobble when leaving Gemini
        if (f >= 20 && f <= 24) {                                            // the big jiggle
            double k = Math.sin((f - 20) * 2.3) * Math.exp(-(f - 20) * 0.25);
            sy = 1 + 0.16 * k;
            sx = 1 - 0.1 * k;
            lean += 0.12 * Math.cos((f - 20) * 2.3);
        }
        return new Transform(sx, sy, lean);
    }

    private static void sprinkles(Canvas c, int f, double dy) {
        if (f < 17) return;
        Transform tr = toppingTransform(f);
        double rim = RIM_Y + dy - 1;
        for (int i = 0; i < SPRINKLES.length; i++) {
            double p = (f - 16.6 - i * 0.18) / 1.1; // fall progress
            if (p <= 0) continue;
            double lx = SPRINKLES[i][0], h = SPRINKLES[i][1];
            if (Math.abs(lx) > profile(h) - 0.5) lx = Math.signum(lx) * (profile(h) - 0.8);
            double tx = CX + (lx + tr.lean() * h) * tr.sx(), ty = rim - h * tr.sy();
            double x = tx, y = ty;
            if (p < 1) { // still falling from the nozzle
                x = CX + (tx - CX) * p;
                y = 18 + (ty - 18) * p * p;
            }
            int color = i % 3 == 0 ? 0xFFFFFF : (i % 3 == 1 ? 0xFF80AB : 0xFF4081);
            int px = (int) Math.round(x), py = (int) Math.round(y);
            // 2-pixel sprinkles with a dark outline below, so they pop on the rainbow topping.
            boolean vertical = i % 2 == 1;
            c.set(px, py, color);
            c.set(vertical ? px : px + 1, vertical ? py - 1 : py, color);
            if (p >= 1) c.set(px, py + 1, 0x3B1020);
        }
    }

    // ------------------------------------------------------------- pouring etc.

    private static void streams(Canvas c, int f, double dy) {
        int top = 18;
        if (f >= 6 && f <= 8) {
            int bottom = (int) (RIM_Y + dy - 1);
            for (int y = top; y <= bottom; y++) {
                int wob = (int) Math.round(Math.sin(y * 0.7 + f * 2) * 0.4);
                c.set(CX - 1 + wob, y, 0x8B5E3C);
                c.set(CX + wob, y, COFFEE);
            }
            // Splash at the surface.
            c.add(CX - 3, bottom, CREMA, 0.6);
            c.add(CX + 2, bottom - 1, CREMA, 0.6);
        }
        if (f >= 11 && f <= 14) {
            double amount = toppingAmount(f);
            int bottom = (int) Math.round(RIM_Y + dy - 1 - TOPPING_H * amount);
            for (int y = top; y <= bottom; y++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int col = GoogleColors.cyclic(RAINBOW, y * 0.09 + dx * 0.2 + f * 0.3);
                    c.set(CX + dx, y, lerp(col, 0xFFFFFF, dx == -1 ? 0.35 : 0.1));
                }
            }
        }
    }

    private static void steam(Canvas c, int f) {
        if (f < 8 || f > 11) return;
        double k = f == 11 ? 0.3 : 0.55;
        for (int s = 0; s < 2; s++) {
            int bx = CX - 4 + s * 7;
            for (int i = 0; i < 6; i++) {
                int y = RIM_Y - 4 - i - (f - 8);
                int x = bx + (int) Math.round(Math.sin(i * 0.9 + f + s * 2) * 1.2);
                c.add(x, y, 0xBDC1C6, k * (1 - i / 7.0));
            }
        }
    }

    /** A drop of coffee flies out backwards as the cup accelerates away from JAVA. */
    private static void drop(Canvas c, int f) {
        if (f < 9 || f > 11) return;
        double a = f - 8.6;
        int x = (int) Math.round(CX - 11 - 4 * a), y = (int) Math.round(RIM_Y - 3 - 4 * a + 3 * a * a);
        c.set(x, y, COFFEE);
        c.set(x, y - 1, 0x8B5E3C);
        c.add(x + 1, y, COFFEE, 0.5);
        if (f == 11) { // splat on the belt
            c.set(CX - 21, BELT_Y - 1, COFFEE);
            c.set(CX - 19, BELT_Y - 1, COFFEE);
        }
    }

    private static void sideHearts(Canvas c, int f) {
        if (f < 18 || f > 23) return;
        int[][] starts = {{8, 38}, {51, 36}, {13, 30}, {47, 28}};
        for (int i = 0; i < starts.length; i++) {
            double age = f - 18 - i * 0.6;
            if (age < 0) continue;
            double alpha = clamp01(1.2 - age * 0.3);
            int x = starts[i][0] + (int) Math.round(Math.sin(age * 1.5 + i) * 1.5);
            int y = starts[i][1] - (int) Math.round(age * 2.5);
            int body = i % 2 == 0 ? 0xF06292 : 0xE91E63;
            sprite(c, HEART_5, x, y, alpha, (px, py) -> py == 0 ? 0xFF8FAB : body);
        }
    }

    // ----------------------------------------------------------------- claws

    private static void claws(Canvas c, int f, double cam, double dy) {
        switch (f) {
            case 1, 2, 3 -> claw(c, CX, RIM_Y + dy - 5, 10, false);
            case 4 -> claw(c, CX - (cam - camera(3)), RIM_Y - 14, 10, true);
            case 5 -> claw(c, CX - (cam - camera(3)), RIM_Y - 30, 10, true);
            case 23 -> claw(c, CX, -7, 27, true);
            case 24 -> claw(c, CX, 12, 27, false);
            case 25 -> claw(c, CX, 12 + dy, 27, false);
            default -> { }
        }
    }

    /** A robot claw hanging from the ceiling, fingers tipped in Google colors. */
    private static void claw(Canvas c, double x, double palmY, int fingerLen, boolean open) {
        int cx = (int) Math.round(x), py = (int) Math.round(palmY);
        for (int y = 0; y < py; y++) {                      // arm
            for (int dx = -2; dx <= 1; dx++) {
                c.set(cx + dx, y, dx == -2 ? 0xDADCE0 : (dx == 1 ? 0x5F6368 : 0x9AA0A6));
            }
            if (y % 6 == 3) c.set(cx - 1, y, 0x5F6368);       // segment joints
        }
        int span = open ? 18 : 15;
        for (int dx = -span; dx <= span; dx++) {            // palm bar
            c.set(cx + dx, py, 0xBDC1C6);
            c.set(cx + dx, py + 1, 0x5F6368);
        }
        c.set(cx - 1, py - 1, BLUE);                        // wrist joint
        c.set(cx, py - 1, BLUE);
        for (int side = -1; side <= 1; side += 2) {         // fingers with inward hooks
            int fx = cx + side * span;
            for (int y = py + 2; y <= py + 1 + fingerLen; y++) {
                c.set(fx, y, 0x9AA0A6);
                c.set(fx - side, y, 0x5F6368);
            }
            int tipY = py + 2 + fingerLen;
            int tip = side < 0 ? BLUE : RED;
            c.set(fx, tipY, tip);
            c.set(fx - side, tipY, tip);
            c.set(fx - 2 * side, tipY, tip);
        }
        c.set(cx - 6, py, YELLOW);
        c.set(cx + 5, py, GREEN);
    }

    // -------------------------------------------------------------- the end

    /** The heart left behind where the cup was, popping in and fading to black. */
    private static void endHeart(Canvas c, int f) {
        double scale, alpha;
        switch (f) {
            case 25 -> { scale = 0.45; alpha = 0.8; }
            case 26 -> { scale = 1.15; alpha = 1; }
            case 27 -> { scale = 1.0; alpha = 0.85; }
            case 28 -> { scale = 1.0; alpha = 0.4; }
            default -> { return; }
        }
        double cy = 38, size = 8.5 * scale;
        c.glowDot(CX, cy, 0, 0xE91E63, 11 * scale, 0.3 * alpha);
        for (int y = (int) (cy - size - 2); y <= cy + size + 2; y++) {
            for (int x = (int) (CX - size - 2); x <= CX + size + 2; x++) {
                double cover = 0;
                for (int s = 0; s < 9; s++) {
                    double u = (x + (s % 3 + 0.5) / 3 - CX) / size * 1.25;
                    double v = -(y + (s / 3 + 0.5) / 3 - cy) / size * 1.25 + 0.2;
                    double q = u * u + v * v - 1;
                    if (q * q * q - u * u * v * v * v <= 0) cover++;
                }
                if (cover == 0) continue;
                double t = (y - (cy - size)) / (2 * size);
                int col = lerp(0xFF8FAB, 0xC2185B, clamp01(t));
                if (x < CX - size * 0.3 && y < cy - size * 0.35) col = lerp(col, 0xFFFFFF, 0.45); // shine
                c.add(x, y, col, alpha * cover / 9);
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private interface PixelColor { int at(int x, int y); }

    private static void sprite(Canvas c, String[] rows, int x0, int y0, double alpha, PixelColor color) {
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                if (rows[y].charAt(x) == '#') c.set(x0 + x, y0 + y, lerp(0, color.at(x, y), alpha));
            }
        }
    }

    private static void text(Canvas c, String s, int x0, int y0, int color, double alpha) {
        for (int i = 0; i < s.length(); i++) {
            sprite(c, FONT.get(s.charAt(i)), x0 + i * 4, y0, alpha, (x, y) -> color);
        }
    }
}
