package io.github.rolfdobbelaere.ledart;

import java.util.Map;

/** A tiny 5x7 pixel font, drawn bold (6x7) with a soft glow. Only the letters we need. */
final class PixelFont {

    private static final Map<Character, String[]> GLYPHS = Map.of(
            'D', new String[]{"####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."},
            'E', new String[]{"#####", "#....", "#....", "####.", "#....", "#....", "#####"},
            'V', new String[]{"#...#", "#...#", "#...#", "#...#", ".#.#.", ".#.#.", "..#.."},
            'O', new String[]{".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."},
            'X', new String[]{"#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"},
            'G', new String[]{".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".###."},
            'M', new String[]{"#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"},
            'I', new String[]{"#####", "..#..", "..#..", "..#..", "..#..", "..#..", "#####"},
            'N', new String[]{"#...#", "##..#", "#.#.#", "#.#.#", "#..##", "#...#", "#...#"});

    private PixelFont() {}

    static void draw(Canvas c, char ch, int x, int y, int color) {
        String[] rows = GLYPHS.get(ch);
        if (rows == null) return;
        for (int row = 0; row < rows.length; row++) {
            for (int col = 0; col < 5; col++) {
                if (rows[row].charAt(col) != '#') continue;
                for (int bold = 0; bold < 2; bold++) {
                    int px = x + col + bold, py = y + row;
                    c.set(px, py, row == 0 ? GoogleColors.lerp(color, 0xFFFFFF, 0.45) : color);
                    // Soft halo around each lit pixel.
                    c.add(px - 1, py, color, 0.12);
                    c.add(px + 1, py, color, 0.12);
                    c.add(px, py - 1, color, 0.12);
                    c.add(px, py + 1, color, 0.12);
                }
            }
        }
    }
}
