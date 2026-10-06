package io.github.rolfdobbelaere.ledart;

import java.util.Map;

import static io.github.rolfdobbelaere.ledart.GoogleColors.*;

/**
 * A looping side-scroller: a developer runs over a curved Google-colored planet, stomps two bugs,
 * grabs a spinning "G" coin, while a Google Cloud "Lakitu" floats along. Keyboard and mouse props
 * scroll past.
 *
 * <p>The world scrolls 3 px per frame, so after 30 frames it has moved exactly one 90 px
 * "period". Every object repeats with that period, which makes the GIF loop seamlessly.
 */
final class DevRunner {

    private static final int SPEED = 3;
    private static final int PERIOD = Scenes.FRAMES * SPEED; // 90 world pixels per loop
    private static final int DEV_X = 16;                      // left edge of the developer sprite
    private static final int DEV_CX = DEV_X + 4;

    // World X positions chosen so each object reaches the developer at the right frame.
    private static final int BUG_A_STOMP = 8, BUG_B_STOMP = 20, COIN_FRAME = 14;
    private static final int BUG_A_X = DEV_CX + SPEED * BUG_A_STOMP - 4;
    private static final int BUG_B_X = DEV_CX + SPEED * BUG_B_STOMP - 4;
    private static final int COIN_X = DEV_CX + SPEED * COIN_FRAME;
    private static final int COIN_Y = 26;
    private static final int KEYBOARD_X = 2, MOUSE_X = 30;

    private static final Map<Character, Integer> DEV_COLORS = Map.of(
            'h', 0x5A3420, 's', 0xF1C27D, 'g', 0xFFFFFF, 'b', BLUE, 'd', 0x2A5CB0,
            'j', 0x23408F, 'r', RED, 'y', YELLOW);

    private static final String[] DEV_RUN_A = {
            "..hhhh..",
            ".hhhhhh.",
            ".hssgsg.",
            ".hsssss.",
            "..ssss..",
            ".bbybbb.",
            "bbbbbbbs",
            "sdbbbbb.",
            ".bbbbbb.",
            ".jjjjjj.",
            ".jj..jj.",
            "jj....jj",
            "rr....rr"};
    private static final String[] DEV_RUN_B = {
            "..hhhh..",
            ".hhhhhh.",
            ".hssgsg.",
            ".hsssss.",
            "..ssss..",
            ".bbybbb.",
            ".bbbbbs.",
            ".bsbbbb.",
            ".bbbbbb.",
            "..jjjj..",
            "..jjjj..",
            "..j..j..",
            ".rr.rr.."};
    private static final String[] DEV_JUMP = {
            "s.hhhh.s",
            "bhhhhhhb",
            "bhssgsgb",
            ".hsssss.",
            "..ssss..",
            ".bbybbb.",
            ".bbbbbb.",
            ".bbbbbb.",
            ".bbbbbb.",
            ".jjjjjj.",
            "jjj..jjj",
            "jj....rr",
            "rr......"};

    private static final String[] BUG_A = {
            ".a.....a.",
            "..accca..",
            ".cWkcWkc.",
            "cdccdccdc",
            ".ccccccc.",
            "l.l.l.l.l"};
    private static final String[] BUG_B = {
            ".a.....a.",
            "..accca..",
            ".cWkcWkc.",
            "cdccdccdc",
            ".ccccccc.",
            ".l.l.l.l."};
    private static final String[] BUG_SQUISHED = {
            "ccdcWcdcc",
            "l.l.l.l.l"};

    private static final String[] COIN = {
            "..RRRR.",
            ".R.....",
            "Y......",
            "Y...BBB",
            "Y.....B",
            ".G...G.",
            "..GGG.."};

    private static final String[] KEYBOARD = {
            "kkkkkkkkkkk",
            "kBkRkYkGkBk",
            "kwkwkwkwkwk",
            "kkwwwwwwwkk"};
    private static final String[] MOUSE = {
            "..k..",
            ".www.",
            "wwrww",
            "wwwww",
            "wwwww",
            ".www."};

    private static final String[] CLOUD = {
            ".....OOOO......",
            "...OOWWWWOO....",
            "..OWWWWWWWWOO..",
            ".OWWkWWWkWWWWO.",
            "OWWWWWWWWWWWWWO",
            "OWWpkWWWkpWWWWO",
            ".OWWWkkkWWWWWO.",
            "..OOOOOOOOOOO.."};

    private DevRunner() {}

    static Canvas frame(int f) {
        Canvas c = new Canvas();
        double t = (double) f / Scenes.FRAMES;

        stars(c, f, t);
        cloud(c, t);
        ground(c, f);

        for (int k = -1; k <= 1; k++) {
            int shift = -SPEED * f + PERIOD * k;
            prop(c, KEYBOARD, KEYBOARD_X + shift);
            prop(c, MOUSE, MOUSE_X + shift);
        }

        // k = 0 is this loop's copy (gets stomped / collected), k = 1 is the fresh copy for the next loop.
        for (int k = 0; k <= 1; k++) {
            int shift = -SPEED * f + PERIOD * k;
            boolean current = k == 0;
            bug(c, BUG_A_X + shift, RED, current && f >= BUG_A_STOMP, f);
            bug(c, BUG_B_X + shift, GREEN, current && f >= BUG_B_STOMP, f);
            if (!(current && f >= COIN_FRAME)) coin(c, COIN_X + shift, t);
        }

        developer(c, f);

        burst(c, f, BUG_A_STOMP, DEV_CX, ground(DEV_CX) - 2, RED);
        burst(c, f, BUG_B_STOMP, DEV_CX, ground(DEV_CX) - 2, GREEN);
        coinBurst(c, f);
        return c;
    }

    /** Surface height of the small curved planet. */
    private static int ground(int x) {
        return (int) Math.round(49 + Math.pow(x - 32, 2) / 110.0);
    }

    private static void ground(Canvas c, int f) {
        for (int x = 0; x < Canvas.SIZE; x++) {
            int top = ground(x);
            int u = x + SPEED * f; // world coordinate, so the texture scrolls with the world
            for (int y = top; y < Canvas.SIZE; y++) {
                int depth = y - top;
                if (depth < 2) {
                    c.set(x, y, depth == 0 && u % 3 == 0 ? 0x7EE787 : GREEN);
                    continue;
                }
                int row = (depth - 2) / 3;
                int col = Math.floorMod((u + (row % 2) * 3) / 6, PERIOD / 6);
                boolean mortar = (depth - 2) % 3 == 2 || Math.floorMod(u + (row % 2) * 3, 6) == 0;
                int brick = FOUR[(col + row) % 4];
                c.set(x, y, lerp(0, brick, mortar ? 0.08 : 0.38 - row * 0.06));
            }
        }
    }

    private static void stars(Canvas c, int f, double t) {
        int[][] stars = {{4, 6}, {13, 17}, {24, 10}, {9, 28}, {27, 22}};
        for (int i = 0; i < stars.length; i++) {
            // Far layer: 1 px/frame, pattern period 30 -> also loops.
            int x = Math.floorMod(stars[i][0] - f, 30);
            double k = 0.35 + 0.35 * Math.sin(Math.PI * 2 * (2 * t + i / 5.0));
            for (int rep = 0; rep < 3; rep++) c.add(x + rep * 30, stars[i][1], 0xFFFFFF, k);
        }
    }

    /** The Google Cloud "Lakitu": a smiling cloud with a four-color outline, bobbing above the player. */
    private static void cloud(Canvas c, double t) {
        int cx = (int) Math.round(31 + 5 * Math.sin(Math.PI * 2 * t));
        int cy = (int) Math.round(3 + 1.5 * Math.sin(Math.PI * 4 * t));
        for (int row = 0; row < CLOUD.length; row++) {
            for (int col = 0; col < CLOUD[row].length(); col++) {
                char ch = CLOUD[row].charAt(col);
                int color = switch (ch) {
                    case 'W' -> 0xE8EAED;
                    case 'k' -> 0x202124;
                    case 'p' -> 0xF28B82;
                    case 'O' -> outline(col - 7, row - 4);
                    default -> -1;
                };
                if (color >= 0) c.set(cx + col, cy + row, color);
            }
        }
    }

    private static int outline(int dx, int dy) {
        if (dy >= 2) return BLUE;
        if (dx <= -4) return YELLOW;
        if (dx >= 3) return GREEN;
        return RED;
    }

    private static void prop(Canvas c, String[] sprite, int x) {
        int w = sprite[0].length();
        int bottom = ground(Math.clamp(x + w / 2, 0, 63)) - 1;
        draw(c, sprite, x, bottom - sprite.length + 1, Map.of(
                'k', 0x3C4043, 'w', 0xBDC1C6, 'B', BLUE, 'R', RED, 'Y', YELLOW, 'G', GREEN, 'r', RED));
    }

    private static void bug(Canvas c, int x, int color, boolean squished, int f) {
        if (x < -10 || x > 64) return;
        int bottom = ground(Math.clamp(x + 4, 0, 63)) - 1;
        String[] sprite = squished ? BUG_SQUISHED : (f % 2 == 0 ? BUG_A : BUG_B);
        draw(c, sprite, x, bottom - sprite.length + 1, Map.of(
                'c', color, 'd', lerp(color, 0, 0.45), 'W', 0xFFFFFF, 'k', 0x000000, 'a', 0xBDC1C6, 'l', 0xBDC1C6));
    }

    private static void coin(Canvas c, int x, double t) {
        if (x < -6 || x > 70) return;
        int y = COIN_Y + (int) Math.round(Math.sin(Math.PI * 2 * t) * 1.5);
        // Spin: squash the sprite horizontally with |cos|; flip it when the back side shows.
        double spin = Math.cos(Math.PI * 4 * t);
        double w = Math.max(0.2, Math.abs(spin));
        // Dim gold disc behind the letter so the four G colors stay readable.
        c.glowDot(x, y, 3.5 * w + 0.5, lerp(0, YELLOW, 0.3), 1.5, 0.5);
        for (int row = 0; row < 7; row++) {
            for (int px = -3; px <= 3; px++) {
                int col = (int) Math.round(px / w) + 3;
                if (col < 0 || col > 6) continue;
                if (spin < 0) col = 6 - col;
                char ch = COIN[row].charAt(col);
                int color = switch (ch) {
                    case 'R' -> RED; case 'Y' -> YELLOW; case 'B' -> BLUE; case 'G' -> GREEN;
                    default -> -1;
                };
                if (color >= 0) c.set(x + px, y - 3 + row, color);
            }
        }
    }

    /** Height of the developer's feet above the ground: run, stomp bug A, big bounce through the coin, stomp bug B. */
    private static double jumpHeight(int f) {
        if (f < 3 || f >= 26) return 0;
        if (f < BUG_A_STOMP) return arc(f, 3, BUG_A_STOMP, 0, 6, 9);
        if (f < BUG_B_STOMP) return arc(f, BUG_A_STOMP, BUG_B_STOMP, 6, 6, 13);
        return arc(f, BUG_B_STOMP, 26, 6, 0, 5);
    }

    private static double arc(int f, int f0, int f1, double h0, double h1, double apex) {
        double u = (double) (f - f0) / (f1 - f0);
        return h0 + (h1 - h0) * u + 4 * apex * u * (1 - u);
    }

    private static void developer(Canvas c, int f) {
        double h = jumpHeight(f);
        String[] sprite = h > 0 && f != BUG_A_STOMP && f != BUG_B_STOMP ? DEV_JUMP
                : (f % 4 < 2 ? DEV_RUN_A : DEV_RUN_B);
        int feet = ground(DEV_CX) - 1 - (int) Math.round(h);
        draw(c, sprite, DEV_X, feet - sprite.length + 1, DEV_COLORS);
    }

    private static void burst(Canvas c, int f, int start, int x, int y, int color) {
        int age = f - start;
        if (age < 0 || age > 3) return;
        x -= SPEED * age;
        double k = 1 - age / 4.0;
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * i / 5;
            c.add(x + (int) Math.round(Math.cos(a) * (3 + age * 2)), y - (int) Math.round(Math.sin(a) * (1 + age)),
                    i % 2 == 0 ? 0xFFFFFF : color, k);
        }
    }

    private static void coinBurst(Canvas c, int f) {
        int age = f - COIN_FRAME;
        if (age < 0 || age > 4) return;
        double k = 1 - age / 5.0;
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            int r = 3 + age * 3;
            c.glowDot(DEV_CX + Math.cos(a) * r, COIN_Y + Math.sin(a) * r, 0.6, FOUR[i % 4], 1.2, 0.4 * k);
        }
        c.add(DEV_CX, COIN_Y, 0xFFFFFF, k);
    }

    private static void draw(Canvas c, String[] sprite, int x, int y, Map<Character, Integer> colors) {
        for (int row = 0; row < sprite.length; row++) {
            for (int col = 0; col < sprite[row].length(); col++) {
                Integer color = colors.get(sprite[row].charAt(col));
                if (color != null) c.set(x + col, y + row, color);
            }
        }
    }
}
