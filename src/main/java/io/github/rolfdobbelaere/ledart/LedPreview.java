package io.github.rolfdobbelaere.ledart;

import io.github.glaforge.jixoo.model.PixooAnimation;
import io.github.glaforge.jixoo.model.PixooFrame;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Renders an upscaled "what it looks like on the LED wall" preview GIF, with each pixel drawn
 * as a round LED. Only for the README / your eyes: upload the real 64x64 GIF to the raffle.
 */
public final class LedPreview {

    private static final int CELL = 6;

    private LedPreview() {}

    public static void write(PixooAnimation animation, Path target) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(target.toFile())) {
            writer.setOutput(out);
            writer.prepareWriteSequence(null);
            boolean first = true;
            for (PixooFrame frame : animation.frames()) {
                BufferedImage img = render(frame);
                IIOMetadata meta = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(img), null);
                configure(meta, frame.delayMs(), first);
                writer.writeToSequence(new IIOImage(img, null, meta), null);
                first = false;
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
    }

    /** All frames side by side in a grid of 6 columns, handy to check an animation frame by frame. */
    public static void writeSheet(PixooAnimation animation, Path target) throws IOException {
        int cols = 6, size = 64 * CELL, gap = 8;
        int rows = (animation.frameCount() + cols - 1) / cols;
        BufferedImage sheet = new BufferedImage(cols * (size + gap), rows * (size + gap), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        for (int i = 0; i < animation.frameCount(); i++) {
            g.drawImage(render(animation.frames().get(i)), (i % cols) * (size + gap), (i / cols) * (size + gap), null);
        }
        g.dispose();
        ImageIO.write(sheet, "png", target.toFile());
    }

    private static BufferedImage render(PixooFrame frame) {
        byte[] rgb = frame.rgbData();
        BufferedImage img = new BufferedImage(64 * CELL, 64 * CELL, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x0A0A0A));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int i = 0; i < 64 * 64; i++) {
            int r = rgb[i * 3] & 0xFF, gr = rgb[i * 3 + 1] & 0xFF, b = rgb[i * 3 + 2] & 0xFF;
            g.setColor(new Color(Math.max(r, 0x1A), Math.max(gr, 0x1A), Math.max(b, 0x1A)));
            g.fillOval((i % 64) * CELL, (i / 64) * CELL, CELL - 1, CELL - 1);
        }
        g.dispose();
        return img;
    }

    private static void configure(IIOMetadata meta, int delayMs, boolean first) throws IOException {
        String format = meta.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(format);
        IIOMetadataNode gce = child(root, "GraphicControlExtension");
        gce.setAttribute("disposalMethod", "none");
        gce.setAttribute("userInputFlag", "FALSE");
        gce.setAttribute("transparentColorFlag", "FALSE");
        gce.setAttribute("delayTime", Integer.toString(Math.max(2, delayMs / 10)));
        gce.setAttribute("transparentColorIndex", "0");
        if (first) {
            IIOMetadataNode app = new IIOMetadataNode("ApplicationExtension");
            app.setAttribute("applicationID", "NETSCAPE");
            app.setAttribute("authenticationCode", "2.0");
            app.setUserObject(new byte[]{1, 0, 0}); // loop forever
            child(root, "ApplicationExtensions").appendChild(app);
        }
        meta.setFromTree(format, root);
    }

    private static IIOMetadataNode child(IIOMetadataNode root, String name) {
        for (int i = 0; i < root.getLength(); i++) {
            if (root.item(i).getNodeName().equalsIgnoreCase(name)) return (IIOMetadataNode) root.item(i);
        }
        IIOMetadataNode node = new IIOMetadataNode(name);
        root.appendChild(node);
        return node;
    }
}
