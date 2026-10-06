package io.github.rolfdobbelaere.ledart;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.github.rolfdobbelaere.ledart.GoogleColors.*;

/**
 * The Devoxx coffee factory: a robot claw puts a DEVOXX cup on a conveyor belt, the camera follows the
 * cup past three dispensers (JAVA coffee, a Gemini rainbow soft-serve, DEV heart sprinkles), the topping
 * jiggles, the claw takes the cup away and a heart is left behind before everything fades to black.
 *
 * <p>The cup always stays in the middle of the screen: the camera moves, so the belt and the dispensers
 * scroll past. The story is written on a timeline in <em>seconds</em>, then sampled into frames, each with
 * its own delay: {@link #SMOOTH} (about 10 fps while moving, for displays without a frame limit) and
 * {@link #PIXOO} (30 frames, the Pixoo 64 limit, where each pause is one long frame).
 */
final class CoffeeFactory {

    // ------------------------------------------------------------- timeline (seconds)

    private static final double PLACE_START = 0.4, PLACE_END = 1.2, RELEASE_END = 1.5, RETRACT_END = 1.9;
    private static final double TRAVEL = 1.0, LOOK = 1.0; // travel between stations, pause before/after pouring
    private static final double JAVA_ARRIVE = RETRACT_END + TRAVEL;            // 2.9
    private static final double JAVA_POUR = JAVA_ARRIVE + LOOK, JAVA_DONE = JAVA_POUR + 1.2;
    private static final double JAVA_LEAVE = JAVA_DONE + LOOK;                  // 6.1
    private static final double GEMINI_ARRIVE = JAVA_LEAVE + TRAVEL;
    private static final double GEMINI_POUR = GEMINI_ARRIVE + LOOK, GEMINI_DONE = GEMINI_POUR + 1.4;
    private static final double GEMINI_LEAVE = GEMINI_DONE + LOOK;              // 10.5
    private static final double DEV_ARRIVE = GEMINI_LEAVE + TRAVEL;
    private static final double DEV_POUR = DEV_ARRIVE + LOOK, DEV_DONE = DEV_POUR + 1.0;
    private static final double DEV_LEAVE = DEV_DONE + LOOK;                    // 14.5
    private static final double EXIT_DONE = DEV_LEAVE + TRAVEL;
    private static final double JIGGLE_END = EXIT_DONE + 0.8;
    private static final double CLAW_DOWN = JIGGLE_END + 0.5, GRIP_END = CLAW_DOWN + 0.3, LIFT_END = GRIP_END + 0.5;
    private static final double HEART_HOLD = LIFT_END + 1.0, FADE_END = HEART_HOLD + 0.8;
    private static final double LOOP_END = FADE_END + 0.4;

    /** One way of turning the timeline into GIF frames: a time and its own delay for each frame. */
    record Cut(double[] times, int[] delays) {
        Canvas frame(int i) { return CoffeeFactory.frame(times[i]); }
        int delayMs(int i) { return delays[i]; }
        int frameCount() { return times.length; }
    }

    /** About 10 fps while things move, fewer and longer frames during the pauses. */
    static final Cut SMOOTH = sample(new double[][]{
            // start, end, frame duration (ms)
            {0, PLACE_START, 400},
            {PLACE_START, RETRACT_END, 100},
            {RETRACT_END, JAVA_ARRIVE, 100},
            {JAVA_ARRIVE, JAVA_POUR, 500},
            {JAVA_POUR, JAVA_DONE, 120},
            {JAVA_DONE, JAVA_LEAVE, 250},
            {JAVA_LEAVE, GEMINI_ARRIVE, 100},
            {GEMINI_ARRIVE, GEMINI_POUR, 500},
            {GEMINI_POUR, GEMINI_DONE, 140},
            {GEMINI_DONE, GEMINI_LEAVE, 250},
            {GEMINI_LEAVE, DEV_ARRIVE, 100},
            {DEV_ARRIVE, DEV_POUR, 500},
            {DEV_POUR, DEV_DONE, 125},
            {DEV_DONE, DEV_LEAVE, 200},
            {DEV_LEAVE, CLAW_DOWN, 100},
            {CLAW_DOWN, LIFT_END, 100},
            {LIFT_END, HEART_HOLD, 250},
            {HEART_HOLD, FADE_END, 100},
            {FADE_END, LOOP_END, 400}});

    /** 30 frames for the Pixoo 64: every 1-second pause is a single long frame, travels get 2 frames. */
    static final Cut PIXOO = new Cut(
            new double[]{
                    0.0,
                    0.75, 1.35,                                   // claw places the cup
                    2.25, 2.65, JAVA_ARRIVE,                      // travel, then look at JAVA
                    4.1, 4.5, 4.9, JAVA_DONE + 0.3,               // pour, then admire the coffee
                    6.3, 6.75, GEMINI_ARRIVE,                     // travel (slosh!), look at Gemini
                    8.3, 8.8, 9.3, GEMINI_DONE + 0.3,             // soft-serve builds up, admire it
                    10.75, 11.2, DEV_ARRIVE,                      // travel, look at DEV
                    12.65, 13.1, DEV_DONE + 0.3,                  // sprinkles, hearts float up
                    14.9, 15.5,                                   // travel out with the big jiggle
                    16.6, 17.25,                                  // claw grabs and lifts the cup
                    17.6, 18.9, 19.6},                            // heart, fade, black
            new int[]{
                    400,
                    300, 400,
                    330, 330, 1000,
                    350, 350, 400, 1000,
                    330, 330, 1000,
                    350, 350, 400, 1000,
                    330, 330, 1000,
                    400, 400, 1000,
                    350, 350,
                    350, 350,
                    1000, 300, 400});

    private static Cut sample(double[][] segments) {
        List<Double> times = new ArrayList<>();
        List<Integer> delays = new ArrayList<>();
        for (double[] seg : segments) {
            double step = seg[2] / 1000;
            int n = Math.max(1, (int) Math.round((seg[1] - seg[0]) / step));
            for (int i = 0; i < n; i++) {
                times.add(seg[0] + (seg[1] - seg[0]) * i / n);
                delays.add((int) Math.round((seg[1] - seg[0]) * 1000 / n));
            }
        }
        return new Cut(times.stream().mapToDouble(Double::doubleValue).toArray(),
                delays.stream().mapToInt(Integer::intValue).toArray());
    }

    // ---------------------------------------------------------------- geometry

    private static final int CX = 32;           // the cup's screen x, fixed: the camera follows it
    private static final int RIM_Y = 32;        // cup rim when standing on the belt
    private static final int CUP_H = 17;        // rim to bottom
    private static final int BELT_Y = 50;
    private static final double TOP_HALF = 13, BOTTOM_HALF = 10;

    private static final int JAVA_X = 100, GEMINI_X = 160, DEV_X = 220; // dispenser world positions
    private static final double TOPPING_H = 12;

    private static final int COFFEE = 0x6F4E37, CREMA = 0xC69C6D, DEVOXX_ORANGE = 0xF57C00;
    private static final int[] RAINBOW = {BLUE, 0x9177C7, RED, YELLOW, GREEN};

    // Camera keyframes (seconds, world x of the left screen edge); smoothstep between them.
    private static final double[][] CAMERA = {
            {0, JAVA_X - CX - 60}, {RETRACT_END, JAVA_X - CX - 60}, {JAVA_ARRIVE, JAVA_X - CX},
            {JAVA_LEAVE, JAVA_X - CX}, {GEMINI_ARRIVE, GEMINI_X - CX},
            {GEMINI_LEAVE, GEMINI_X - CX}, {DEV_ARRIVE, DEV_X - CX},
            {DEV_LEAVE, DEV_X - CX}, {EXIT_DONE, DEV_X - CX + 60}, {LOOP_END, DEV_X - CX + 60}};

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

    static Canvas frame(double s) {
        Canvas c = new Canvas();
        double cam = camera(s);
        double scene = sceneAlpha(s);

        dispenser(c, s, cam, JAVA_X, scene);
        dispenser(c, s, cam, GEMINI_X, scene);
        dispenser(c, s, cam, DEV_X, scene);
        belt(c, cam, scene);

        double dy = cupOffset(s);
        if (dy > -60) {
            streams(c, s, dy);
            cup(c, s, dy);
            topping(c, s, dy);
            sprinkles(c, s, dy);
        }
        steam(c, s);
        sideHearts(c, s);
        drop(c, s);
        claws(c, s, cam, dy);
        endHeart(c, s);
        return c;
    }

    // ------------------------------------------------------------- animation curves

    private static double clamp01(double v) { return Math.clamp(v, 0, 1); }
    private static double smooth(double t) { t = clamp01(t); return t * t * (3 - 2 * t); }
    private static double between(double s, double a, double b) { return clamp01((s - a) / (b - a)); }

    private static double camera(double s) {
        for (int i = 0; i < CAMERA.length - 1; i++) {
            if (s <= CAMERA[i + 1][0]) {
                double t = smooth((s - CAMERA[i][0]) / (CAMERA[i + 1][0] - CAMERA[i][0]));
                return CAMERA[i][1] + (CAMERA[i + 1][1] - CAMERA[i][1]) * t;
            }
        }
        return CAMERA[CAMERA.length - 1][1];
    }

    /** Camera speed in pixels per second. */
    private static double velocity(double s) {
        return (camera(s + 0.02) - camera(s - 0.02)) / 0.04;
    }

    /** Belt and dispensers fade in from black and back out at the end. */
    private static double sceneAlpha(double s) {
        return between(s, PLACE_START - 0.2, PLACE_START + 0.3) * (1 - between(s, HEART_HOLD, FADE_END));
    }

    /** Vertical offset of the cup: lowered in by the claw, later lifted away. */
    private static double cupOffset(double s) {
        if (s < PLACE_START) return -100;
        if (s < PLACE_END) {
            double t = between(s, PLACE_START, PLACE_END);
            return -42 * (1 - t) * (1 - t);                       // ease out: slows down as it lands
        }
        if (s < GRIP_END) return 0;
        if (s < LIFT_END) {
            double t = between(s, GRIP_END, LIFT_END);
            return -70 * t * t;                                   // ease in: accelerates upward
        }
        return -100;
    }

    private static double coffeeLevel(double s) { return between(s, JAVA_POUR, JAVA_DONE); }
    private static double toppingAmount(double s) { return between(s, GEMINI_POUR, GEMINI_DONE); }

    private static boolean pouring(int worldX, double s) {
        return switch (worldX) {
            case JAVA_X -> s >= JAVA_POUR && s < JAVA_DONE;
            case GEMINI_X -> s >= GEMINI_POUR && s < GEMINI_DONE;
            default -> s >= DEV_POUR && s < DEV_DONE;
        };
    }

    // ----------------------------------------------------------------- scenery

    private static void belt(Canvas c, double cam, double alpha) {
        if (alpha <= 0) return;
        int shift = (int) Math.round(cam);
        for (int x = 0; x < 64; x++) {
            int w = x + shift;
            for (int y = BELT_Y; y <= BELT_Y + 4; y++) {
                int color;
                if (y == BELT_Y) color = 0x80868B;                                                 // lit top edge
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

    private static void dispenser(Canvas c, double s, double cam, int worldX, double alpha) {
        double sx = worldX - cam;
        if (sx < -16 || sx > 80 || alpha <= 0) return;
        int x0 = (int) Math.round(sx) - 12;
        boolean active = pouring(worldX, s);
        // Metal body with a vertical gradient and rounded bottom corners.
        for (int y = 0; y <= 14; y++) {
            for (int x = 0; x < 24; x++) {
                if (y == 14 && (x == 0 || x == 23)) continue;
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
                // The sparkle gently pulses so the Gemini panel feels alive while you look at it.
                double pulse = 0.75 + 0.25 * Math.sin(s * 6);
                for (int y = 0; y < 7; y++) {
                    for (int x = 0; x < 7; x++) {
                        if (SPARK_7[y].charAt(x) != '#') continue;
                        int col = GoogleColors.gradient(new int[]{0x4796E3, 0x9177C7, 0xD96570}, (x + y) / 12.0);
                        c.set(x0 + 9 + x, 4 + y, lerp(0, col, alpha * (x == 3 && y == 3 ? 1 : pulse)));
                    }
                }
                for (int i = 0; i < 4; i++) c.set(x0 + 3 + i * 5 % 17, 4 + i * 3 % 7, lerp(0, FOUR[i], alpha * 0.8));
            }
            default -> {
                text(c, "DEV", x0 + 3, 5, 0x5F2120, alpha);
                double beat = Math.sin(s * 8) > 0.6 ? 0.15 : 0; // heartbeat flash
                sprite(c, HEART_7, x0 + 14, 5, alpha, (x, y) -> y == 0 ? 0xFF8FAB : lerp(0xE91E63, 0xFFFFFF, beat));
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

    private static void cup(Canvas c, double s, double dy) {
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
        text(c, label, CX - textW / 2, rim + 8, 0xFFFFFF, 1);

        // Rim lip and the opening, seen slightly from above.
        double level = coffeeLevel(s);
        double slosh = Math.clamp(-velocity(s) * 0.012, -1, 1);
        for (int y = rim - 3; y <= rim + 2; y++) {
            for (int x = CX - 15; x <= CX + 15; x++) {
                double ex = (x + 0.5 - CX) / 13.6, ey = (y + 0.5 - rim) / 2.6;
                if (ex * ex + ey * ey > 1) continue;
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
    private static void topping(Canvas c, double s, double dy) {
        double amount = toppingAmount(s);
        if (amount <= 0) return;
        Transform tr = toppingTransform(s);
        double rim = RIM_Y + dy - 1;
        for (int y = (int) (rim - 18); y <= rim + 1; y++) {
            for (int x = CX - 17; x <= CX + 17; x++) {
                double h = (rim - (y + 0.5)) / tr.sy();
                if (h < -0.6 || h > TOPPING_H * amount) continue;
                double lx = (x + 0.5 - CX) / tr.sx() - tr.lean() * Math.max(0, h);
                double hw = profile(Math.max(0, h));
                if (Math.abs(lx) > hw) continue;
                double roll = ((h + 0.4) % 3.1) / 3.1;
                int col = GoogleColors.cyclic(RAINBOW, lx / 22 + h * 0.13 + s * 0.15);
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

    private static Transform toppingTransform(double s) {
        double lean = Math.clamp(-velocity(s) * 0.004, -0.35, 0.35);
        double sx = 1, sy = 1;
        // A soft wobble when the cup stops at DEV.
        if (s > DEV_ARRIVE && s < DEV_ARRIVE + 1.2) {
            lean += 0.2 * Math.sin((s - DEV_ARRIVE) * 11) * Math.exp(-(s - DEV_ARRIVE) * 3);
        }
        if (s > EXIT_DONE - 0.5 && s < JIGGLE_END + 0.5) {                    // the big jiggle
            double t = s - (EXIT_DONE - 0.5);
            double k = Math.sin(t * 10) * Math.exp(-t * 1.6);
            sy = 1 + 0.16 * k;
            sx = 1 - 0.1 * k;
            lean += 0.12 * Math.cos(t * 10) * Math.exp(-t * 1.6);
        }
        return new Transform(sx, sy, lean);
    }

    private static void sprinkles(Canvas c, double s, double dy) {
        if (s < DEV_POUR) return;
        Transform tr = toppingTransform(s);
        double rim = RIM_Y + dy - 1;
        double window = DEV_DONE - DEV_POUR - 0.35;
        for (int i = 0; i < SPRINKLES.length; i++) {
            double start = DEV_POUR + window * i / SPRINKLES.length;
            double p = (s - start) / 0.35; // fall progress
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

    private static void streams(Canvas c, double s, double dy) {
        int top = 18;
        if (pouring(JAVA_X, s)) {
            int bottom = (int) (RIM_Y + dy - 1);
            for (int y = top; y <= bottom; y++) {
                int wob = (int) Math.round(Math.sin(y * 0.7 + s * 20) * 0.4);
                c.set(CX - 1 + wob, y, 0x8B5E3C);
                c.set(CX + wob, y, COFFEE);
            }
            // Splash at the surface.
            boolean flip = ((int) (s * 8)) % 2 == 0;
            c.add(CX + (flip ? -3 : 3), bottom, CREMA, 0.6);
            c.add(CX + (flip ? 2 : -2), bottom - 1, CREMA, 0.6);
        }
        if (pouring(GEMINI_X, s)) {
            int bottom = (int) Math.round(RIM_Y + dy - 1 - TOPPING_H * toppingAmount(s));
            for (int y = top; y <= bottom; y++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int col = GoogleColors.cyclic(RAINBOW, y * 0.09 + dx * 0.2 + s * 2.5);
                    c.set(CX + dx, y, lerp(col, 0xFFFFFF, dx == -1 ? 0.35 : 0.1));
                }
            }
        }
    }

    /** Steam curls up from the fresh coffee while you admire it. */
    private static void steam(Canvas c, double s) {
        if (s < JAVA_DONE - 0.3 || s > GEMINI_ARRIVE) return;
        double k = 0.55 * (1 - between(s, JAVA_LEAVE, GEMINI_ARRIVE));
        double rise = (s - JAVA_DONE) * 4;
        for (int w = 0; w < 2; w++) {
            int bx = CX - 4 + w * 7;
            for (int i = 0; i < 6; i++) {
                double y = RIM_Y - 4 - i - (((rise % 4) + 4) % 4);
                int x = bx + (int) Math.round(Math.sin(i * 0.9 + s * 6 + w * 2) * 1.2);
                c.add(x, (int) Math.round(y), 0xBDC1C6, k * (1 - i / 7.0));
            }
        }
    }

    /** A drop of coffee flies out backwards as the cup accelerates away from JAVA, then splats on the belt. */
    private static void drop(Canvas c, double s) {
        double start = JAVA_LEAVE + 0.15;
        if (s < start || s > start + 1.0) return;
        double a = (s - start) / 0.45;
        int x = (int) Math.round(CX - 11 - 5 * a), y = (int) Math.round(RIM_Y - 3 - 5 * a + 22 * a * a);
        if (y < BELT_Y - 1) {
            c.set(x, y, COFFEE);
            c.set(x, y - 1, 0x8B5E3C);
            c.add(x + 1, y, COFFEE, 0.5);
            return;
        }
        // The splat lies on the belt, so it travels away with it.
        int splat = (int) Math.round(CX - 17 - (camera(s) - camera(start + 0.5)));
        c.set(splat - 1, BELT_Y - 1, COFFEE);
        c.set(splat + 1, BELT_Y - 1, COFFEE);
        c.set(splat, BELT_Y, 0x8B5E3C);
    }

    private static void sideHearts(Canvas c, double s) {
        double start = DEV_POUR + 0.4;
        if (s < start || s > DEV_LEAVE + 0.3) return;
        int[][] starts = {{8, 38}, {51, 36}, {13, 30}, {47, 28}};
        for (int i = 0; i < starts.length; i++) {
            double age = s - start - i * 0.25;
            if (age < 0) continue;
            double alpha = clamp01(1.2 - age * 0.75);
            int x = starts[i][0] + (int) Math.round(Math.sin(age * 4 + i) * 1.5);
            int y = starts[i][1] - (int) Math.round(age * 7);
            int body = i % 2 == 0 ? 0xF06292 : 0xE91E63;
            sprite(c, HEART_5, x, y, alpha, (px, py) -> py == 0 ? 0xFF8FAB : body);
        }
    }

    // ----------------------------------------------------------------- claws

    private static void claws(Canvas c, double s, double cam, double dy) {
        if (s >= PLACE_START && s < RELEASE_END) {
            claw(c, CX, RIM_Y + dy - 5, 10, s >= PLACE_END);                  // holding, then letting go
        } else if (s >= RELEASE_END && s < RETRACT_END + 0.3) {
            double t = between(s, RELEASE_END, RETRACT_END);
            claw(c, CX - (cam - camera(RETRACT_END)), RIM_Y - 5 - 40 * t * t, 10, true);
        } else if (s >= CLAW_DOWN - 0.5 && s < CLAW_DOWN) {
            double t = smooth(between(s, CLAW_DOWN - 0.5, CLAW_DOWN));
            claw(c, CX, -32 + 44 * t, 27, true);                              // comes down, open
        } else if (s >= CLAW_DOWN && s < LIFT_END + 0.2) {
            claw(c, CX, 12 + Math.max(dy, -60), 27, false);                   // grip and lift
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

    /** The heart left behind where the cup was: pops in, wobbles, then fades to black. */
    private static void endHeart(Canvas c, double s) {
        double appear = GRIP_END + 0.2;
        if (s < appear || s > FADE_END) return;
        double t = s - appear;
        double scale = t < 0.3 ? 0.4 + t / 0.3 * 0.75 : 1 + 0.15 * Math.exp(-(t - 0.3) * 4) * Math.cos((t - 0.3) * 14);
        double alpha = 1 - between(s, HEART_HOLD, FADE_END);
        double cy = 38, size = 8.5 * scale;
        c.glowDot(CX, cy, 0, 0xE91E63, 11 * scale, 0.3 * alpha);
        for (int y = (int) (cy - size - 2); y <= cy + size + 2; y++) {
            for (int x = (int) (CX - size - 2); x <= CX + size + 2; x++) {
                double cover = 0;
                for (int k = 0; k < 9; k++) {
                    double u = (x + (k % 3 + 0.5) / 3 - CX) / size * 1.25;
                    double v = -(y + (k / 3 + 0.5) / 3 - cy) / size * 1.25 + 0.2;
                    double q = u * u + v * v - 1;
                    if (q * q * q - u * u * v * v * v <= 0) cover++;
                }
                if (cover == 0) continue;
                double ty = (y - (cy - size)) / (2 * size);
                int col = lerp(0xFF8FAB, 0xC2185B, clamp01(ty));
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
