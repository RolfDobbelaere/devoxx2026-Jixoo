package io.github.rolfdobbelaere.ledart;

/**
 * Pixoo-friendly cuts of the long stories: at most 60 frames (the documented HTTP GIF buffer of the Pixoo 64),
 * motion at about 8-11 fps (Divoom recommends 8-12 fps for pixel art), well under 1 MB. Pauses in the story are
 * single long frames, so the frame budget goes to what moves.
 */
final class HardwareCuts {

    private HardwareCuts() {}

    /** The 3D coffee factory: the full 20 s story in 60 frames. */
    static final Timeline COFFEE = new Timeline(CoffeeFactory3D::render)
            .hold(0.0, 300)                    // black
            .motion(0.5, 1.8, 5, 110)          // claw lowers the sleeping cup
            .motion(1.85, 3.0, 4, 180)         // close-up: it wakes up and blinks
            .motion(3.0, 3.25, 2, 90)          // belt starts
            .hold(3.6, 700)                    // look up at JAVA
            .motion(4.2, 5.6, 5, 150)          // coffee pours in
            .hold(6.0, 700)                    // steam, content smile
            .motion(6.6, 7.6, 5, 90)           // travel: slosh, the drop flies out
            .hold(8.0, 700)                    // look up at GEMINI
            .motion(8.6, 10.2, 6, 150)         // rainbow soft-serve builds up
            .hold(10.6, 700)                   // admire the swirl
            .motion(11.2, 12.2, 4, 90)         // travel
            .hold(12.6, 600)                   // look up at DEV
            .motion(13.2, 15.2, 6, 150)        // sprinkles, hearts float up
            .motion(15.4, 16.6, 5, 90)         // roll on, jiggle
            .motion(16.6, 18.0, 5, 110)        // claw lifts the cup away
            .motion(18.0, 19.2, 4, 150)        // the heart pops up
            .motion(19.2, 19.8, 2, 150)        // fade
            .hold(19.9, 300);                  // black

    /** The Google -> Gemini fly-through: the full 16 s story in 60 frames. */
    static final Timeline FLYTHROUGH = new Timeline(GeminiFlyThrough::render)
            .hold(0.1, 300)                    // black, cursor
            .motion(0.55, 2.05, 6, 140)        // GOOGLE is typed, one letter per frame
            .hold(2.3, 500)                    // GOOGLE, a glimpse of smoke inside the O
            .motion(2.6, 4.4, 9, 90)           // dive into the O
            .motion(4.4, 5.0, 3, 90)           // smoke tunnel
            .motion(5.0, 6.2, 5, 100)          // the smoke dissolves into the world
            .motion(6.2, 10.3, 12, 110)        // candyland fly-over
            .motion(10.3, 11.3, 4, 100)        // rise through the clouds
            .motion(11.3, 12.6, 5, 100)        // the world becomes a planet
            .motion(12.6, 13.8, 5, 110)        // the planet becomes the dot of GEMINi
            .motion(13.8, 14.5, 3, 120)        // Gemini sparkle
            .hold(14.6, 400)                   // GEMINi
            .motion(14.8, 15.7, 4, 100)        // poof into colorful dust
            .hold(15.9, 300);                  // black
}
