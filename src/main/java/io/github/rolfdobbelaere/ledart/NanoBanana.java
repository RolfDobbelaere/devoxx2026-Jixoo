package io.github.rolfdobbelaere.ledart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * Minimal client for Gemini image generation ("Nano Banana") using {@code generateContent}.
 *
 * <p>Configuration via environment variables:
 * <ul>
 *   <li>{@code GEMINI_API_KEY}: API key (required)</li>
 *   <li>{@code GEMINI_IMAGE_MODEL}: model id, default {@value #DEFAULT_MODEL}</li>
 *   <li>{@code GEMINI_BACKEND}: {@code gemini} (Gemini API, default) or {@code vertex}
 *       (Vertex AI / Agent Platform, billed to your Google Cloud billing account and credits)</li>
 *   <li>{@code GOOGLE_CLOUD_PROJECT}: project id, used by the {@code vertex} backend</li>
 * </ul>
 */
public final class NanoBanana {

    public static final String DEFAULT_MODEL = "gemini-3.1-flash-image";

    /** Style wrapper that steers Nano Banana towards LED-friendly, Google-colored pixel art. */
    public static final String GOOGLE_STYLE = """
            Bold 64x64 pixel art for a 64x64 RGB LED matrix display. \
            Large chunky square pixels on a perfectly flat pure black background (black = LED off). \
            Use ONLY the Google brand colors: blue #4285F4, red #EA4335, yellow #FBBC05, green #34A853, \
            plus white highlights and the Gemini blue-to-violet-to-rose gradient for glows. \
            High contrast, vivid, glowing neon look, subject centered and filling most of the square, \
            simple readable silhouette, no text, no letters, no border, no frame, no dithering noise. \
            Subject: %s""";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final String apiKey;
    private final String model;
    private final boolean vertex;

    public NanoBanana() {
        this.apiKey = setting("GEMINI_API_KEY")
                .or(() -> setting("GOOGLE_API_KEY"))
                .orElseThrow(() -> new IllegalStateException(
                        "Set GEMINI_API_KEY, or put GEMINI_API_KEY=... in a .env file (see README: 'Google Cloud setup')."));
        this.model = setting("GEMINI_IMAGE_MODEL").orElse(DEFAULT_MODEL);
        this.vertex = setting("GEMINI_BACKEND").map("vertex"::equalsIgnoreCase).orElse(false);
    }

    /** Reads a setting from the environment, falling back to a git-ignored {@code .env} file. */
    private static Optional<String> setting(String name) {
        Optional<String> env = Optional.ofNullable(System.getenv(name)).filter(v -> !v.isBlank());
        if (env.isPresent()) return env;
        Path dotEnv = Path.of(".env");
        if (!Files.exists(dotEnv)) return Optional.empty();
        try {
            return Files.readAllLines(dotEnv).stream()
                    .map(String::strip)
                    .filter(line -> line.startsWith(name + "="))
                    .map(line -> line.substring(name.length() + 1).strip().replaceAll("^[\"']|[\"']$", ""))
                    .filter(v -> !v.isBlank())
                    .findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public String model() {
        return model;
    }

    /** Generates a square image and returns its encoded bytes (PNG or JPEG). */
    public byte[] generate(String prompt) throws IOException, InterruptedException {
        return generate(prompt, null, "1:1");
    }

    /**
     * Generates an image, optionally guided by a reference image (e.g. to keep a character consistent),
     * with the given aspect ratio ("1:1", "16:9", "21:9", ...).
     */
    public byte[] generate(String prompt, byte[] referencePng, String aspectRatio) throws IOException, InterruptedException {
        ObjectNode body = JSON.createObjectNode();
        ObjectNode content = body.putArray("contents").addObject();
        content.put("role", "user");
        var parts = content.putArray("parts");
        if (referencePng != null) {
            ObjectNode inline = parts.addObject().putObject("inlineData");
            inline.put("mimeType", "image/png");
            inline.put("data", Base64.getEncoder().encodeToString(referencePng));
        }
        parts.addObject().put("text", prompt);
        ObjectNode config = body.putObject("generationConfig");
        config.putArray("responseModalities").add("TEXT").add("IMAGE");
        config.putObject("imageConfig").put("aspectRatio", aspectRatio);

        String url = vertex
                ? "https://aiplatform.googleapis.com/v1/" + setting("GOOGLE_CLOUD_PROJECT")
                        .map(p -> "projects/" + p + "/locations/global/").orElse("")
                        + "publishers/google/models/" + model + ":generateContent"
                : "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent";

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(3))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Gemini returned HTTP " + response.statusCode() + " for model '" + model + "':\n"
                    + response.body() + hint(response.statusCode()));
        }
        JsonNode image = findImagePart(JSON.readTree(response.body()));
        if (image == null) {
            throw new IOException("No image in Gemini response (maybe blocked by safety filters):\n" + response.body());
        }
        return Base64.getDecoder().decode(image.path("data").asText());
    }

    private static String hint(int status) {
        return switch (status) {
            case 400, 401, 403 -> "\nHint: check the API key and that the API is enabled in your Google Cloud project.";
            case 404 -> "\nHint: model not found. Run `mvn -q exec:java -Dexec.args=models` to list image models, "
                    + "then set GEMINI_IMAGE_MODEL.";
            case 429 -> "\nHint: quota exceeded, wait a minute or check billing / credits on the project.";
            default -> "";
        };
    }

    /** Walks the response tree to find an inline image part, whatever the exact response shape. */
    private static JsonNode findImagePart(JsonNode node) {
        if (node.isObject()) {
            String mime = node.path("mimeType").asText(node.path("mime_type").asText(""));
            if (mime.startsWith("image/") && node.hasNonNull("data")) return node;
            for (JsonNode child : node) {
                JsonNode found = findImagePart(child);
                if (found != null) return found;
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode found = findImagePart(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** Lists models from the Gemini API whose name suggests image output. */
    public void printImageModels() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000"))
                .header("x-goog-api-key", apiKey).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
        for (JsonNode m : JSON.readTree(response.body()).path("models")) {
            String name = m.path("name").asText().replace("models/", "");
            if (name.contains("image")) System.out.println(name + "  -  " + m.path("displayName").asText());
        }
    }
}
