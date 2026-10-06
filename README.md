# Devoxx Belgium 2026: Google-colored LED visuals ✨

64×64 visuals for the [Google Cloud Raffle](https://devoxx-raffle.cloud.run/) LED matrix, built with
[Jixoo](https://github.com/glaforge/jixoo) (Java 21) and **Nano Banana** (Gemini image generation).

Everything uses the Google palette (blue `#4285F4`, red `#EA4335`, yellow `#FBBC05`, green `#34A853`) and the Gemini
blue → violet → rose gradient.

| google-dots | gemini-sparkle | google-spinner | matrix-rain | devoxx-gemini |
|---|---|---|---|---|
| ![](output/preview/google-dots-led.gif) | ![](output/preview/gemini-sparkle-led.gif) | ![](output/preview/google-spinner-led.gif) | ![](output/preview/matrix-rain-led.gif) | ![](output/preview/devoxx-gemini-led.gif) |

*Previews are upscaled to show the LED look. The real 64×64 files to upload are in [`output/`](output).*

## How it works

- **Procedural scenes** ([`Scenes.java`](src/main/java/io/github/rolfdobbelaere/ledart/Scenes.java)): drawn on an
  additive-light canvas so overlapping colors glow like real LEDs. Each loops seamlessly in exactly 30 frames, because
  the Pixoo 64 only plays the first ~30 frames of a GIF.
- **Nano Banana pipeline** ([`NanoBanana.java`](src/main/java/io/github/rolfdobbelaere/ledart/NanoBanana.java),
  [`PixelArt.java`](src/main/java/io/github/rolfdobbelaere/ledart/PixelArt.java)): your subject gets wrapped in a
  prompt that asks for chunky Google-colored pixel art on pure black. The result is center-cropped and downsampled
  with a per-block median (keeps pixel edges sharp). Near-black becomes "LED off" and colors snap to the Google
  palette. A second, animated version adds a Gemini shimmer sweep and twinkles.
- **Jixoo** does the image decoding (`ImageProcessor`), the frame model (`PixooImage`, `PixooFrame`,
  `PixooAnimation`) and the GIF89a encoding (`GifEncoder`). If you have a Pixoo, `pixoo-cli gif -f output/x.gif`
  shows the result on the device.

## Build

Requires Java 21 and Maven. Jixoo isn't on Maven Central, so install it locally first:

```bash
git clone https://github.com/glaforge/jixoo.git
cd jixoo && mvn install -DskipTests && cd ..
```

Then, in this repo:

```bash
mvn -q compile exec:java -Dexec.args="scenes"
```

## Generate with Nano Banana

```bash
mvn -q compile exec:java -Dexec.args="ai 'a cute robot mascot holding a coffee cup' robot"
```

This writes `output/robot.gif` (still), `output/robot-shimmer.gif` (animated) and previews plus the raw Gemini image
in `output/preview/`. Add `--raw` to keep the original colors instead of snapping to the Google palette.

### Google Cloud setup

1. Use the Google Cloud project that holds the Devoxx credits, and check that **Billing → Credits** shows them.
2. Enable the **Generative Language API**: *APIs & Services → Library*, search for it, then click **Enable**.
3. Create a key: *APIs & Services → Credentials → Create credentials → API key*. Restrict it to the Generative
   Language API. (Or create it at [aistudio.google.com/apikey](https://aistudio.google.com/apikey) and pick the same
   project.)
4. Set it for the session (never commit it):
   - PowerShell: `$env:GEMINI_API_KEY="AIza..."`
   - bash: `export GEMINI_API_KEY="AIza..."`
5. Optional: run `mvn -q exec:java -Dexec.args="models"` to list the image models your key can use, and set
   `GEMINI_IMAGE_MODEL` if the default (`gemini-3.1-flash-image`, Nano Banana 2) isn't one of them.

If you created a Vertex AI / Agent Platform express-mode key instead, also set `GEMINI_BACKEND=vertex`.

## Credits

[Jixoo](https://github.com/glaforge/jixoo) by Guillaume Laforge (Apache 2.0).
