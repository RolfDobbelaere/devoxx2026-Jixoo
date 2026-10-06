# Dev Runner: World Tour (remake of dev-runner)

Storyboard for `output/dev-runner_v2-world-tour.gif` (scene `dev-runner-2`, code in
[`DevRunnerWorldTour.java`](../src/main/java/io/github/rolfdobbelaere/ledart/DevRunnerWorldTour.java)).

## The request

> Make a remake (or re-imagining) of the dev-runner.gif, using the character of nano-developer.gif and make it a loop,
> and also the same high quality as we are doing right now. Make sure the level is passing following themes: Jungle,
> Beach, City, BouncyLand, Grass, (back to jungle) in a loop.
>
> No colored mist at each theme change, make it seamless without interruption of continuity.

## Interpretation

- **The hero is the Nano Banana developer.** Nano Banana 2 drew a 6-pose sprite sheet (4 running frames, a jump, a
  landing) using `nano-developer` as the reference image, so it really is the same character:
  [`assets/nano-developer-runsheet_v1.png`](../assets/nano-developer-runsheet_v1.png).
- **One continuous journey.** The camera never cuts or fades. Each theme is a stretch of the same world, so when the
  runner reaches the beach, the sand starts under his feet, palm trees scroll in on the middle layer, the far horizon
  already showed the sea a few seconds earlier (distant scenery moves slower, so it reaches the screen first, like real
  parallax), and the sky slowly changes, like time and place passing.
- **Same quality bar as the fly-through and the 3D coffee factory:** 25 fps, rendered at 128×128 and downsampled,
  5 parallax depth layers, sub-pixel smooth scrolling, a seamless loop.
- **The original dev-runner's game DNA stays:** stomping bugs, Google "G" coins, and the smiling Google Cloud
  companion floating along like Lakitu.

## The loop (25 fps, 24 s, 600 frames; 4.8 s per theme)

| Time (s) | Theme | Scenery | Gameplay |
|---|---|---|---|
| 0.0 – 4.8 | **Jungle** | Misty green mountains, giant trees with hanging vines, ferns, fireflies, light shafts through the canopy, mossy soil. | Stomps a red bug, bounces up to grab a G coin, jumps a mossy log. |
| 4.8 – 9.6 | **Beach** | Bright blue sky with a big sun, the sea glittering on the horizon, palm trees, beach umbrellas in Google colors, sand. | Jumps a sandcastle, stomps a crab-bug, grabs a coin. |
| 9.6 – 14.4 | **City** | Sunset over a violet skyline with windows lighting up in Google colors, mid-rise buildings, street lamps, asphalt with lane markings. | Hops a traffic cone, stomps a blue bug, clears a fire hydrant. |
| 14.4 – 19.2 | **BouncyLand** | Pastel candy sky with a rainbow, candy hills, giant lollipops and gumdrops, floating bubbles, jelly ground. | Trampolines launch him into big bounces through coin arcs. |
| 19.2 – 24.0 | **Grass** | Clear day, rolling hills, round trees, flowers in Google colors, butterflies. Trees start thickening into the jungle again. | Stomps a bug, jumps a rock, grabs a coin, and runs back into the jungle: the loop. |

Throughout: the run cycle advances with the distance covered (no sliding feet), with dust puffs on landing, colorful
smoke poofs when bugs are stomped, sparkle bursts for coins, and the cloud companion bobbing overhead.

## How the seamless loop works

- The camera covers exactly 960 world pixels in 24 s. Every layer repeats with a period that divides its own scroll
  distance, and every time-based animation (waves, flicker, fireflies) completes a whole number of cycles in 24 s, so
  frame 600 flows into frame 1.
- A theme is a region of the world, not a moment in time: each layer decides what to show from its own world
  coordinate, so transitions are continuous and spatial, never a fade or wipe.
