package io.github.rolfdobbelaere.ledart;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleFunction;

/**
 * Samples a story written in seconds into a fixed frame budget, each frame with its own delay.
 * Used for the Pixoo-friendly "hw" cuts (at most 60 frames, about 8-12 fps of motion): a pause in the
 * story costs a single long frame, so the frames go to the parts that move.
 */
final class Timeline {

    private final List<Double> times = new ArrayList<>();
    private final List<Integer> delays = new ArrayList<>();
    private final DoubleFunction<Canvas> renderer;

    Timeline(DoubleFunction<Canvas> renderer) {
        this.renderer = renderer;
    }

    /** A single frame showing story time t for delayMs (a pause or a hold). */
    Timeline hold(double t, int delayMs) {
        times.add(t);
        delays.add(delayMs);
        return this;
    }

    /** n frames evenly spread over story time [from, to), each shown for delayMs. */
    Timeline motion(double from, double to, int n, int delayMs) {
        for (int i = 0; i < n; i++) {
            times.add(from + (to - from) * i / n);
            delays.add(delayMs);
        }
        return this;
    }

    int frameCount() { return times.size(); }
    int delayMs(int i) { return delays.get(i); }
    Canvas frame(int i) { return renderer.apply(times.get(i)); }
}
