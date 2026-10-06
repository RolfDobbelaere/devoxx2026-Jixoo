# The Devoxx Coffee Factory, Pixar-style 3D remake

Storyboard for `output/coffee-factory_v3-pixar3d.gif` (scene `coffee-factory-3d`, code in
[`CoffeeFactory3D.java`](../src/main/java/io/github/rolfdobbelaere/ledart/CoffeeFactory3D.java)).
The original 2D story is in [`coffee-factory-love-story.md`](coffee-factory-love-story.md).

## The request

> Now I want to make another NEW more 3D "pixar style" version of the coffee factory story, please figure out what I
> really want, and what I really expect from a reworked version of the coffee factory remake. Knowing, I LOVE the
> flythrough story.

## What "Pixar-style remake" means here

What the fly-through got right, and the 2D coffee factory didn't have yet:

| Expectation | How the remake delivers it |
|---|---|
| **It's a short film, not a diagram** | Real 3D: every pixel is computed by tracing a ray through mathematically defined shapes, with a warm key light, a cool fill, a violet rim light, soft shadows, ambient occlusion and glossy highlights. |
| **The cup is the hero** | The DEVOXX cup has big Pixar eyes, eyelids and a mouth. It wakes up, looks up at each dispenser in anticipation, and reacts: blissful at the coffee, amazed at Gemini, blushing at the love. Squash and stretch on landing, lean when it moves, a jiggle at the end. |
| **Cinematic camera** | The camera tracks alongside the belt, slowly orbits for parallax, and pushes in on each pour. Out-of-focus bokeh lights in Google colors drift in the background for depth. |
| **Fluid like the fly-through** | 25 fps, rendered at 128×128 and downsampled. |
| **Google colors and smoke** | Google-colored bokeh, a rainbow soft-serve swirl, a Gemini pour wrapped in swirling colored smoke and sparkles, coffee steam, glowing hearts. |
| **Seamless loop** | Black to black. |

## Storyboard (25 fps, 20 s, 500 frames)

| Time (s) | Shot | Action | Character beat |
|---|---|---|---|
| 0.0 – 0.8 | Fade in, low angle | Warm light rises on the factory: the belt, rollers, Google-colored bokeh in the dark. | – |
| 0.8 – 1.8 | Medium shot | A robot claw with Google-colored joints lowers the DEVOXX cup and sets it down: squash on landing, stretch back. The claw lets go and rises. | The cup is asleep (eyes closed). |
| 1.8 – 3.2 | **Character close-up** | The camera pushes in until the cup's face and DEVOXX sleeve fill the frame, then pulls back as the belt starts. | It wakes up, blinks twice and looks around, curious. |
| 3.2 – 4.2 | Push-in | Stop under the **JAVA** dispenser (orange label). | The cup looks up at it, eyes wide: "coffee?" |
| 4.2 – 5.6 | Close | A glossy coffee stream pours in and the cup fills up. | Eyes close blissfully (^ ^), big smile. |
| 5.6 – 6.6 | Hold | Steam curls up from the coffee. | Content smile. |
| 6.6 – 7.6 | Tracking shot | Moving on: the coffee sloshes and a shiny drop flies out backwards. | Glances back at the drop: "oops". |
| 7.6 – 8.6 | Push-in | Stop under **Gemini**: a wide dark-glass signboard reading **GEMINI** in the blue → violet → rose gradient (drawn pixel-exact on the LED grid so it stays readable), and a Gemini sparkle twinkling at its nozzle. | Amazed: eyes wide, "o" mouth. |
| 8.6 – 10.2 | Close | A rainbow stream builds a turning **soft-serve swirl**, wrapped in swirling Google-colored smoke and sparkles. | Delighted grin. |
| 10.2 – 11.2 | Hold | The swirl glistens. | Looks up at its new hat. |
| 11.2 – 12.2 | Tracking shot | Moving on; the topping wobbles. | – |
| 12.2 – 13.2 | Push-in | Stop under **DEV ♥** (pink, the heart beating). | Looks up, starting to blush. |
| 13.2 – 14.4 | Close | Pink and white sprinkles rain down; glowing hearts float up around the cup. | Full blush, happy closed eyes. |
| 14.4 – 15.4 | Hold | Hearts drift up. | Smiling. |
| 15.4 – 16.6 | Tracking shot | Rolls on and stops: the topping **jiggles** (squash and stretch). | Giggles (bounces). |
| 16.6 – 18.0 | Medium shot | The claw comes down, grips the cup gently and lifts it up and out of frame. | Looks up at the claw, happy, and is carried away. |
| 18.0 – 19.2 | Hold | A glossy 3D heart pops up where the cup stood, spinning and glowing. | – |
| 19.2 – 20.0 | Fade out | Everything fades to black; the loop restarts. | – |

## Production notes

- **Renderer:** ray marching through signed distance fields (SDF). Each shape (tapered cup with a hollow inside, liquid
  surface, soft-serve made of stacked tori, belt, rollers, dispensers, claw, heart, eyes) is a small distance
  function. Lighting: soft shadows, ambient occlusion, Blinn-Phong highlights, rim light, then tone mapping.
- **Decals:** DEVOXX on the sleeve, the face, the dispenser labels and the belt treads are painted onto the surfaces
  in surface coordinates.
- **Effects:** steam, Gemini smoke and sparkles, sprinkles and floating hearts are layered on top in screen space,
  positioned by projecting their 3D locations, and reuse the smoke noise of the fly-through.
- **Readable names:** at 64×64 a word rendered in 3D blurs, so **GEMINI** is drawn pixel-exact on the final LED grid,
  pinned to where the projected Gemini panel is, and fades in once the panel is wide enough on screen (v4).
- **Speed:** rows are rendered in parallel; about 500 frames.
