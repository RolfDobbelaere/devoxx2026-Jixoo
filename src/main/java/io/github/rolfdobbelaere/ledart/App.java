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
 *   scene dev-runner [out-name]  render a single scene, optionally to a versioned name (name_v2-comment)
 *   sheet dev-runner [out-name]  write a contact sheet of all frames of a scene
 *   ai "a robot" [name] [--snap]  generate with Nano Banana, convert to 64x64 + shimmer animation
 *   convert img.png name [--snap] turn an existing image into 64x64 still + shimmer animation
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
                if (a.size() < 2) throw new IllegalArgumentException("Usage: ai \"<subject>\" [name] [--snap]");
                String subject = a.get(1);
                String name = a.size() > 2 && !a.get(2).startsWith("--") ? a.get(2) : slug(subject);
                // --snap forces every color onto the Google palette; the default keeps Nano Banana's colors
                generateAi(subject, name, a.contains("--snap"));
            }
            case "convert" -> { // re-process an image you already have, e.g. a saved Nano Banana source
                if (a.size() < 3) throw new IllegalArgumentException("Usage: convert <image file> <name> [--snap]");
                convert(Files.readAllBytes(Path.of(a.get(1))), a.get(2), a.contains("--snap"));
            }
            // Optional second argument: the output name, e.g. "coffee-factory_v2-smooth" to keep earlier versions.
            case "scene" -> save(outName(a), Scenes.render(a.get(1), DELAY_MS));
            case "sheet" -> LedPreview.writeSheet(Scenes.render(a.get(1), DELAY_MS),
                    OUT.resolve("preview").resolve(outName(a) + "-sheet.png"));
            case "strip" -> { // consecutive frames side by side, to check motion: strip <scene> <first> <count>
                PixooAnimation all = Scenes.render(a.get(1), DELAY_MS);
                int first = Integer.parseInt(a.get(2)), count = Integer.parseInt(a.get(3));
                LedPreview.writeSheet(new PixooAnimation(all.frames().subList(first, first + count)),
                        OUT.resolve("preview").resolve(a.get(1) + "-strip.png"));
            }
            case "sprites" -> { // sprites <reference.png> <out.png> "<prompt>": a sprite sheet of the same character
                byte[] ref = Files.readAllBytes(Path.of(a.get(1)));
                NanoBanana nano = new NanoBanana();
                System.out.println("Asking " + nano.model() + " for a sprite sheet based on " + a.get(1));
                Files.write(Path.of(a.get(2)), nano.generate(a.get(3), ref, "21:9"));
                System.out.println("  -> " + a.get(2));
            }
            case "models" -> new NanoBanana().printImageModels();
            default -> System.err.println("Unknown command '" + cmd + "'. Use: scenes | ai \"<subject>\" [name] [--snap] | models");
        }
    }

    private static void generateAi(String subject, String name, boolean snap) throws Exception {
        NanoBanana nano = new NanoBanana();
        System.out.println("Asking " + nano.model() + " for: " + subject);
        byte[] bytes = nano.generate(NanoBanana.GOOGLE_STYLE.formatted(subject));
        Files.write(OUT.resolve("preview").resolve(name + "-source.png"), bytes);
        convert(bytes, name, snap);
    }

    private static void convert(byte[] bytes, String name, boolean snap) throws Exception {
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

    private static String outName(List<String> args) {
        return args.size() > 2 ? args.get(2) : args.get(1);
    }

    private static String slug(String s) {
        String slug = s.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.length() > 40 ? slug.substring(0, 40) : slug;
    }
}
