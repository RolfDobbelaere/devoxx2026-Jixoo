# Google-Gemini-Fly-Though-Exciting-World-Of-Possibilities

Storyboard for `output/google-gemini-fly-through_v1-25fps.gif` (scene `gemini-flythrough`,
code in [`GeminiFlyThrough.java`](../src/main/java/io/github/rolfdobbelaere/ledart/GeminiFlyThrough.java)).

## The original prompt

> I want to create a new fluent animation, it will be judged by Gemini AI, and it scored on fluent fps, loop,
> using (google) colors and Slushing color smoke effects. I want to also have an abstract visual story told, using
> the Gemini and Google Logo. Title: "Google-Gemini-Fly-Though-Exciting-World-Of-Possibilities" using the limited
> pixels I want to explore a fly through a big world. Like from black, showing GOOGLE typed with a cursor, and the
> camera flies through the O, revealing a world (colorfull smoke dissolves and reveals a world, inspired by some kind
> of fantasy candyland, colorfull, mountains, waterfalls, colorfull clouds and nature elements mixed with IT, the
> flythough zooms out until the land zooms out to show the entire planet that looks like a colorfull version of
> planet earth, and the earth becomes the DOT on top of the last I of GEMINI, accentuating the world that Gemini is
> able to create. then the letters quickly poof into colorfull dust to black.

## What it is really for

This is the hero entry for the **Best Visual** prize, judged by Gemini. Every decision serves the four judging
criteria:

| Criterion | How the animation scores on it |
|---|---|
| **Fluent fps** | 25 fps for 16 seconds (400 frames). The booth display plays long GIFs, so no 30-frame Pixoo cut is needed. Everything is rendered at 128×128 and downsampled to 64×64, so motion moves by sub-pixel amounts instead of jumping. |
| **Loop** | Starts on black and ends on black: frame 400 flows straight into frame 1. |
| **(Google) colors** | GOOGLE in the four logo colors, GEMINI in the Gemini blue → violet → rose gradient. The smoke, the candy world and the dust all draw from the same palette. |
| **Slushing color smoke** | Domain-warped, swirling noise ("smoke") in Google colors appears in every act: typing puffs, the tunnel inside the O, the dissolve into the world, the clouds, the rush to space, the nebula, letters condensing from smoke, and the final dust poof. |

The story in one line: **Google opens a door to a world; that world becomes the dot on Gemini's "i"**.

## Storyboard (25 fps, 16.0 s, 400 frames)

| Time (s) | Act | What you see | Smoke / color effect |
|---|---|---|---|
| 0.0 – 0.5 | **Black** | Darkness, then a blinking cursor. | – |
| 0.5 – 2.0 | **Typing** | `G O O G L E` is typed letter by letter in Google blue, red, yellow, blue, green, red. Each letter pops in slightly too big and settles. The cursor follows. | Every keystroke releases a puff of smoke in that letter's color. A faint aurora of Google-colored smoke starts drifting behind the word. |
| 2.0 – 2.6 | **Hold** | GOOGLE complete, cursor blinking. Inside the red **O** something is already swirling. | A glimpse of colored smoke through the O's hole. |
| 2.6 – 4.4 | **Into the O** | The camera dives into the first O: it slides to the centre and grows until its hole fills the screen. The other letters rush past the edges. | The hole is a window onto a swirling tunnel of smoke. |
| 4.4 – 6.2 | **Smoke tunnel** | Flying down a tunnel of slushing colored smoke toward a bright light. | Polar smoke tunnel: swirl plus forward motion, so it feels like a fly-through. |
| 5.0 – 6.2 | **Dissolve** | The smoke burns away from the inside, revealing a world. | Dissolve edges glow; leftover wisps become the sky's clouds. |
| 6.0 – 10.4 | **Candyland fly-over** | Low flight over a fantasy world: candy-striped mountains in pastel Google colors, cotton-candy snow caps, mint valleys, blue rivers and waterfalls, lollipop trees, and IT mixed into nature: glowing circuit traces with data packets racing along them, and server towers with blinking lights. The camera banks gently. | Colorful smoke clouds drift in a candy sunset sky; distance haze. |
| 10.3 – 11.3 | **Rise** | The camera tilts down and shoots up; the land falls away. | Colored clouds rush past the camera. |
| 11.2 – 12.6 | **The planet** | Zooming out, the land curves into a colorful Earth: candy continents, blue oceans, swirling clouds, rotating, with a glowing Gemini-gradient atmosphere. | Faint nebula smoke and twinkling stars in space. |
| 12.6 – 13.8 | **The dot** | The planet shrinks and flies to the top right, while **GEMINI** condenses out of smoke; the planet becomes the **dot on the last i**. | Letters solidify out of Gemini-colored smoke. |
| 13.8 – 14.8 | **Spark** | A four-pointed Gemini sparkle flashes on the planet-dot. GEMINI glows with a sheen running through its gradient. | Sparkle glow. |
| 14.8 – 15.7 | **Poof** | Letters and planet burst into colorful dust: every pixel becomes a particle that flies out, drifts, turns Google-colored and fades. | Expanding ring of colorful smoke behind the dust. |
| 15.7 – 16.0 | **Black** | Back to darkness; the loop starts again with the cursor. | – |

## Production notes

- **Rendering:** a 128×128 float buffer with additive light, box-downsampled to the 64×64 LED matrix. GIF frames
  are encoded by Jixoo's `GifEncoder` with a 256-color palette per frame, so the smoke gradients stay smooth.
- **Smoke:** 3D gradient noise with fractal octaves, warped by a second noise field (domain warping) and animated
  over time. Colors cycle through blue, violet, red, yellow and green.
- **World:** a procedural 256×256 height and color map, rendered "voxel space" style (one ray per column, like the
  classic Comanche games), which suits a 64-pixel screen. The same color map is wrapped around the planet, so the
  planet really is the world you just flew over.
- **Versions** are kept as `name_v#-comment`.
