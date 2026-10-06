package io.github.rolfdobbelaere.ledart;

import io.github.glaforge.jixoo.image.GifEncoder;
import io.github.glaforge.jixoo.image.PixooImage;
import io.github.glaforge.jixoo.model.PixooAnimation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Command line entry point.
 *
 * <pre>
 *   scenes                       render all procedural Google/Gemini scenes (no API key needed)
 *   ai "a robot" [name] [--raw]  generate with Nano Banana, convert to 64x64 + shimmer animation
 *   models                       list image-capable Gemini models for your key
 * </pre>
 */
public final class App {

    private static final Path OUT = Path.of("output");
    private static final int DELAY_MS = 90;

    public static void main(String[] args) throws Exception {
        List<String> a = Arrays.asList(args);
        String cmd = a.isEmpty() ? "scenes" : a.getFirst();
        Files.createDirectories(OUT.resolve("preview"));
        switch (cmd) {
            case "scenes" -> {
                for (String name : Scenes.ALL.keySet().stream().sorted().toList()) {
                    save(name, Scenes.render(name, DELAY_MS));
                }
            }
            case "ai" -> {
                if (a.size() < 2) throw new IllegalArgumentException("Usage: ai \"<subject>\" [name] [--raw]");
                String subject = a.get(1);
                String name = a.size() > 2 && !a.get(2).startsWith("--") ? a.get(2) : slug(subject);
                boolean raw = a.contains("--raw"); // raw = don't snap colors to the Google palette
                generateAi(subject, name, !raw);
            }
            case "models" -> new NanoBanana().printImageModels();
            default -> System.err.println("Unknown command '" + cmd + "'. Use: scenes | ai \"<subject>\" [name] [--raw] | models");
        }
    }

    private static void generateAi(String subject, String name, boolean snap) throws Exception {
        NanoBanana nano = new NanoBanana();
        System.out.println("Asking " + nano.model() + " for: " + subject);
        byte[] bytes = nano.generate(NanoBanana.GOOGLE_STYLE.formatted(subject));
        Files.write(OUT.resolve("preview").resolve(name + "-source.png"), bytes);

        PixooImage led = PixelArt.toLed(PixelArt.decode(bytes), snap);
        save(name, PixooAnimation.singleImage(led));
        save(name + "-shimmer", PixelArt.shimmer(led, DELAY_MS));
    }

    private static void save(String name, PixooAnimation animation) throws Exception {
        Path gif = OUT.resolve(name + ".gif");
        GifEncoder.encode(animation, gif);
        LedPreview.write(animation, OUT.resolve("preview").resolve(name + "-led.gif"));
        System.out.printf("  %-28s %2d frames -> %s%n", name, animation.frameCount(), gif);
    }

    private static String slug(String s) {
        String slug = s.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.length() > 40 ? slug.substring(0, 40) : slug;
    }
}
