package io.github.rolfdobbelaere.ledart;

import java.util.Map;
import java.util.stream.IntStream;

import static io.github.rolfdobbelaere.ledart.GoogleColors.*;

/**
 * Pixar-style 3D remake of the Devoxx coffee factory (see {@code storyboards/coffee-factory-pixar-3d.md}).
 *
 * <p>Every hi-res pixel is ray marched through a scene of signed distance functions: a hollow DEVOXX cup with big
 * eyes and a mouth, coffee, a soft-serve swirl of stacked tori, a conveyor belt with rollers and an LED strip,
 * three dispensers, a robot claw and a puffy heart. Lighting uses a warm key light with soft shadows, a cool fill,
 * a violet rim light, ambient occlusion and Blinn-Phong highlights, then ACES tone mapping. Steam, Gemini smoke,
 * sparkles, sprinkles and floating hearts are layered on top in screen space. 25 fps, 20 s, black to black.
 */
final class CoffeeFactory3D {

    static final int FPS = 25;
    static final double DURATION = 20.0;
    static final int FRAME_COUNT = (int) Math.round(DURATION * FPS);
    static final int DELAY_MS = 1000 / FPS;

    private static final int W = 128;
    private static final double FOV = Math.toRadians(56);
    private static final double[] STATIONS = {10, 20, 30};   // JAVA, Gemini, DEV dispensers (world x)
    private static final double END_X = 36;
    private static final int[] RAINBOW = {BLUE, 0x9177C7, RED, YELLOW, GREEN};
    private static final int[] GEMINI_GRADIENT = {0x4796E3, 0x9177C7, 0xD96570};

    // ------------------------------------------------------------------ timeline (seconds)

    private static final double PLACE = 0.8, LAND = 1.8, RELEASE = 1.95;
    private static final double[] ARRIVE = {3.2, 7.6, 12.2}, POUR = {4.2, 8.6, 13.2}, DONE = {5.6, 10.2, 14.4},
            LEAVE = {6.6, 11.2, 15.4};
    private static final double STOP_END = 16.2, CLAW_DOWN = 16.6, GRIP = 17.1, LIFT = 17.4, GONE = 18.0;
    private static final double HEART = 17.9, FADE_OUT = 19.2, BLACK = 19.8;

    // ------------------------------------------------------------------ small vector type

    private record V(double x, double y, double z) {
        V add(V o) { return new V(x + o.x, y + o.y, z + o.z); }
        V sub(V o) { return new V(x - o.x, y - o.y, z - o.z); }
        V mul(double k) { return new V(x * k, y * k, z * k); }
        double dot(V o) { return x * o.x + y * o.y + z * o.z; }
        double len() { return Math.sqrt(x * x + y * y + z * z); }
        V norm() { double l = len(); return l == 0 ? this : mul(1 / l); }
        V cross(V o) { return new V(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x); }
    }

    private static double clamp01(double v) { return Math.clamp(v, 0, 1); }
    private static double smooth(double t) { t = clamp01(t); return t * t * (3 - 2 * t); }
    private static double smoothstep(double a, double b, double x) { return smooth((x - a) / (b - a)); }
    private static double between(double t, double a, double b) { return clamp01((t - a) / (b - a)); }
    private static double mix(double a, double b, double t) { return a + (b - a) * t; }

    private static double smin(double a, double b, double k) {
        double h = clamp01(0.5 + 0.5 * (b - a) / k);
        return mix(b, a, h) - k * h * (1 - h);
    }

    // ------------------------------------------------------------------ the story as curves

    /** Cup position along the belt. */
    static double cupX(double t) {
        double[][] keys = {{0, 0}, {2.2, 0}, {ARRIVE[0], 10}, {LEAVE[0], 10}, {ARRIVE[1], 20}, {LEAVE[1], 20},
                {ARRIVE[2], 30}, {LEAVE[2], 30}, {STOP_END, END_X}, {99, END_X}};
        for (int i = 0; i < keys.length - 1; i++) {
            if (t <= keys[i + 1][0]) {
                return mix(keys[i][1], keys[i + 1][1], smooth((t - keys[i][0]) / (keys[i + 1][0] - keys[i][0])));
            }
        }
        return END_X;
    }

    private static double accel(double t) {
        double h = 0.04;
        return (cupX(t + h) - 2 * cupX(t) + cupX(t - h)) / (h * h);
    }

    /** Height of the cup's base: lowered in by the claw, lifted away at the end. */
    static double cupY(double t) {
        if (t < PLACE) return 6;
        if (t < LAND) return 6 * Math.pow(1 - between(t, PLACE, LAND), 3);
        if (t < LIFT) return 0;
        if (t < GONE) return 8 * Math.pow(between(t, LIFT, GONE), 2);
        return 50;
    }

    private static double damped(double t, double start, double amp, double freq, double decay) {
        if (t < start) return 0;
        double tau = t - start;
        return amp * Math.exp(-tau * decay) * Math.cos(tau * freq);
    }

    private static double cupSquash(double t) {
        double sy = 1;
        if (t > 1.3 && t < LAND) sy += 0.12 * smoothstep(1.3, 1.75, t);       // stretch while dropping
        sy -= damped(t, LAND, 0.22, 18, 6);                                     // squash on landing
        for (double stop : new double[]{ARRIVE[0], ARRIVE[1], ARRIVE[2], STOP_END}) sy -= damped(t, stop, 0.06, 16, 6);
        if (t > 15.7 && t < 16.7) sy += 0.05 * Math.sin(t * 26) * Math.sin(Math.PI * between(t, 15.7, 16.7)); // giggle
        if (t > LIFT) sy += 0.08 * smoothstep(LIFT, LIFT + 0.2, t);            // stretch when lifted
        return sy;
    }

    private static double cupLean(double t) {
        return Math.clamp(-accel(t) * 0.006, -0.2, 0.2);
    }

    private static double toppingAmount(double t) { return between(t, POUR[1], DONE[1]); }
    private static double coffeeLevel(double t) { return between(t, POUR[0], DONE[0]); }

    private static double toppingLean(double t) {
        double lean = cupLean(t) * 1.7;
        for (double stop : new double[]{ARRIVE[2], STOP_END}) lean += damped(t, stop, 0.16, 11, 2.6);
        return lean;
    }

    private static double toppingSquash(double t) {
        return 1 - damped(t, STOP_END, 0.2, 13, 2.2) - damped(t, ARRIVE[2], 0.06, 13, 3);
    }

    /** The cup's face: eyelids, expression, where it looks. */
    private record Face(double lid, boolean happy, double lookX, double lookY, double wide, int mouth, double blush) {}

    private static final int SLEEP = 0, SMILE = 1, GRIN = 2, OH = 3, OOPS = 4;

    private static Face face(double t) {
        double lid = 0, lookX = 0, lookY = 0.1, wide = 0, blush = 0;
        boolean happy = false;
        int mouth = SMILE;
        if (t < 2.0) {
            lid = 1;
            mouth = SLEEP;
        } else if (t < ARRIVE[0]) {
            lid = 1 - smoothstep(2.0, 2.2, t);
            lookX = 0.6 * Math.sin((t - 2.2) * 5);                            // looks around, curious
        } else if (t < POUR[0]) {
            lookY = 0.85; wide = 0.6; mouth = OH;
        } else if (t < DONE[0]) {
            lid = 1; happy = true; mouth = GRIN;                              // blissful coffee
        } else if (t < LEAVE[0]) {
            lookY = 0.3;
        } else if (t < ARRIVE[1]) {
            lookX = -0.85; lookY = 0.1; mouth = t > 6.75 && t < 7.4 ? OOPS : SMILE; // looks back at the drop
        } else if (t < POUR[1]) {
            lookY = 0.85; wide = 1; mouth = OH;                               // amazed by Gemini
        } else if (t < DONE[1]) {
            lookY = 0.7; wide = 0.4; mouth = GRIN;
        } else if (t < LEAVE[1]) {
            lookY = 1; mouth = SMILE;                                         // admires its new hat
        } else if (t < ARRIVE[2]) {
            lookY = 0.1;
        } else if (t < POUR[2]) {
            lookY = 0.85; blush = 0.6 * between(t, ARRIVE[2], POUR[2]);
        } else if (t < DONE[2]) {
            lid = 1; happy = true; mouth = GRIN; blush = 1;                   // in love
        } else if (t < LEAVE[2]) {
            lookX = 0.7 * Math.sin((t - DONE[2]) * 3); lookY = 0.5; blush = 0.8;
        } else if (t < CLAW_DOWN) {
            lid = 1; happy = true; mouth = GRIN; blush = 0.5;                 // giggles at the jiggle
        } else if (t < LIFT) {
            lookY = 1; wide = 0.3;
        } else {
            lid = 1; happy = true; mouth = GRIN;
        }
        for (double b : new double[]{2.45, 2.75, 6.1, 11.7, 16.9}) {         // blinks
            if (t > b && t < b + 0.16) lid = Math.max(lid, Math.sin(Math.PI * (t - b) / 0.16));
        }
        return new Face(lid, happy, lookX, lookY, wide, mouth, blush);
    }

    // ------------------------------------------------------------------ per-frame scene

    private static final int M_NONE = 0, M_CUP = 1, M_LIQUID = 2, M_TOPPING = 3, M_EYE = 4, M_BELT = 5,
            M_ROLLER = 6, M_FLOOR = 7, M_DISPENSER = 8, M_NOZZLE = 9, M_CLAW = 10, M_JOINT = 11, M_HEART = 12,
            M_COFFEE_STREAM = 13, M_GEMINI_STREAM = 14, M_LAMP = 15;

    private static final class Scene {
        final double t, cupX, cupY, lean, sy, sxz, coffee, slosh, topping, tLean, tsy, tsxz, beltOffset;
        final Face face;
        final V camPos, camFwd, camRight, camUp;
        final double focal;
        final boolean clawVisible, clawOpen;
        final double clawX, palmY, fingerLen;
        final double heartScale, heartSpin;
        final double eyeR;
        final double[][] bokeh;

        Scene(double t) {
            this.t = t;
            cupX = cupX(t);
            cupY = cupY(t);
            lean = cupLean(t);
            sy = cupSquash(t);
            sxz = 1 / Math.sqrt(sy);
            coffee = coffeeLevel(t);
            slosh = Math.clamp(-accel(t) * 0.004, -0.25, 0.25);
            topping = toppingAmount(t);
            tLean = toppingLean(t);
            tsy = toppingSquash(t);
            tsxz = 1 / Math.sqrt(tsy);
            beltOffset = cupX;
            face = face(t);
            eyeR = 0.27 + 0.03 * face.wide();

            // Claw.
            if (t < RELEASE + 0.6) {
                clawVisible = t >= 0;
                clawOpen = t >= RELEASE;
                clawX = 0;
                fingerLen = 1.15;
                double rise = t < RELEASE ? 0 : 6 * Math.pow(between(t, RELEASE, RELEASE + 0.55), 2);
                palmY = cupY + 2.55 + rise;
            } else if (t >= CLAW_DOWN - 0.1 && t < GONE + 0.2) {
                clawVisible = true;
                clawOpen = t < GRIP;
                clawX = END_X;
                fingerLen = 2.3;
                double descend = t < GRIP ? 6 * Math.pow(1 - between(t, CLAW_DOWN - 0.1, GRIP), 2) : 0;
                palmY = cupY + 3.75 + descend;
            } else {
                clawVisible = false; clawOpen = true; clawX = 0; fingerLen = 0; palmY = 99;
            }

            // Heart left behind.
            double tau = t - HEART;
            heartScale = tau < 0 ? 0 : Math.max(0, 1 - Math.exp(-tau * 5) * Math.cos(tau * 11));
            heartSpin = t * 2.0;

            // Camera: tracks the cup with a little lag, slowly orbits, pushes in at each station.
            // Character intro: push in on the cup's face and DEVOXX sleeve as it wakes up, then pull back.
            double closeUp = smoothstep(1.75, 2.15, t) * (1 - smoothstep(2.75, 3.3, t));
            // The camera trails the cup slightly while it travels, except in the close-up where it must stay framed.
            double targetX = t > GONE ? END_X : cupX(t - 0.15 * (1 - closeUp));
            double targetY = 1.75 + Math.clamp(cupY, 0, 6) * 0.3;
            double push = 0;
            for (int s = 0; s < 3; s++) {
                push = Math.max(push, smoothstep(ARRIVE[s], ARRIVE[s] + 0.8, t) * (1 - smoothstep(LEAVE[s] - 0.2, LEAVE[s] + 0.5, t)));
            }
            // At a station the camera cranes up so the dispenser's label and the cup share the frame.
            targetY += 0.7 * push;
            double dist = 7.4 - 0.3 * push;
            if (t < 2.4) dist = 7.0;
            dist = mix(dist, 3.6, closeUp);
            targetY = mix(targetY, 1.2, closeUp);
            if (t > HEART - 0.3) {
                double k = smoothstep(HEART - 0.3, HEART + 0.6, t);
                targetY = mix(targetY, 1.0, k);
                dist = mix(dist, 5.4, k);
            }
            double yaw = -0.1 + 0.22 * Math.sin(t * 0.33);
            V target = new V(targetX, targetY, 0);
            camPos = target.add(new V(Math.sin(yaw) * dist, 1.35 + 0.25 * Math.sin(t * 0.21), Math.cos(yaw) * dist));
            camFwd = target.sub(camPos).norm();
            camRight = camFwd.cross(new V(0, 1, 0)).norm();
            camUp = camRight.cross(camFwd);
            focal = (W / 2.0) / Math.tan(FOV / 2);

            // Out-of-focus lights far behind the belt, in Google colors (screen x, y, radius, color index).
            bokeh = new double[12][];
            for (int i = 0; i < 12; i++) {
                V p = new V(i * 7.3 - 4 + Math.floor((targetX + 20) / 84) * 84, 3 + (i * 37 % 11) * 0.7, -24);
                double[] s = project(p);
                bokeh[i] = s == null ? null : new double[]{s[0], s[1], 5 + (i * 13 % 7), i % 4};
            }
        }

        /** World -> hi-res screen (x, y, depth), or null when behind the camera. */
        double[] project(V p) {
            V d = p.sub(camPos);
            double z = d.dot(camFwd);
            if (z <= 0.1) return null;
            return new double[]{W / 2.0 + d.dot(camRight) / z * focal, W / 2.0 - d.dot(camUp) / z * focal, z};
        }

        // ---------------- transforms of the cup group ----------------

        /** World -> cup-local (cup base at origin, before squash and lean). */
        V toCup(V p) {
            double x = p.x - cupX, y = p.y - cupY, z = p.z;
            double c = Math.cos(lean), s = Math.sin(lean);
            double rx = x * c - y * s, ry = x * s + y * c;        // undo the lean (rotation around z at the base)
            return new V(rx / sxz, ry / sy, z / sxz);
        }

        V fromCup(V q) {
            double x = q.x * sxz, y = q.y * sy, z = q.z * sxz;
            double c = Math.cos(lean), s = Math.sin(lean);
            return new V(x * c + y * s + cupX, -x * s + y * c + cupY, z);
        }

        /** Cup-local -> topping-local (origin at the rim centre, before jiggle and lean). */
        V toTopping(V q) {
            double y = (q.y - 2.2) / tsy;
            double x = (q.x - tLean * (q.y - 2.2)) / tsxz;
            return new V(x, y, q.z / tsxz);
        }

        V fromTopping(V r) {
            double y = r.y * tsy;
            return new V(r.x * tsxz + tLean * y, y + 2.2, r.z * tsxz);
        }

        V eyeCenter(int side) {
            double a = side * 0.33, rc = 0.9;
            return new V(Math.sin(a) * rc, 1.82, Math.cos(a) * rc);
        }

        // ---------------- distance functions ----------------

        double map(V p) {
            return mapWithMaterial(p, false)[0];
        }

        /** Returns {distance, material}. */
        double[] mapWithMaterial(V p, boolean wantMaterial) {
            double best = 1e9;
            int mat = M_NONE;

            // Floor and belt.
            double floor = p.y + 1.7;
            if (floor < best) { best = floor; mat = M_FLOOR; }
            double belt = sdRoundBox(new V(0, p.y + 0.18, p.z), 1e6, 0.17, 1.75, 0.06);
            if (belt < best) { best = belt; mat = M_BELT; }
            double rx = ((p.x + beltOffset) % 1.1 + 1.1) % 1.1 - 0.55;            // rollers turn with the belt
            double roller = Math.max(Math.hypot(rx, p.y + 0.62) - 0.3, Math.abs(p.z) - 1.65);
            if (roller < best) { best = roller; mat = M_ROLLER; }

            // Dispensers (only the ones near the camera are evaluated).
            for (int i = 0; i < STATIONS.length; i++) {
                double sx = STATIONS[i], hw = dispenserHalfWidth(i);
                if (Math.abs(p.x - sx) > 4) { best = Math.min(best, Math.abs(p.x - sx) - hw - 0.2); continue; }
                double body = sdRoundBox(new V(p.x - sx, p.y - 4.8, p.z), hw, 0.62, 0.72, 0.12);
                if (body < best) { best = body; mat = M_DISPENSER; }
                double nozzle = sdCappedCone(Math.hypot(p.x - sx, p.z), p.y - 3.98, 0.2, 0.12, 0.26);
                double pipe = Math.max(Math.hypot(p.x - sx, p.z) - 0.11, Math.abs(p.y - 8) - 2.6);
                double metal = Math.min(nozzle, pipe);
                if (metal < best) { best = metal; mat = M_NOZZLE; }
                double lamp = new V(p.x - sx - hw + 0.23, p.y - 5.45, p.z - 0.45).len() - 0.09;
                if (lamp < best) { best = lamp; mat = M_LAMP; }
            }

            // Pour streams.
            if (t >= POUR[0] && t < DONE[0]) {
                double bottom = cupY + 0.25 + coffee * 1.8;
                double s = Math.max(Math.hypot(p.x - STATIONS[0], p.z) - 0.065, Math.abs(p.y - (3.8 + bottom) / 2) - (3.8 - bottom) / 2);
                if (s < best) { best = s; mat = M_COFFEE_STREAM; }
            }
            if (t >= POUR[1] && t < DONE[1]) {
                double bottom = 2.3 + topping * 1.25;
                double wob = Math.sin(p.y * 3 + t * 8) * 0.02;
                double s = Math.max(Math.hypot(p.x - STATIONS[1] + wob, p.z) - 0.1, Math.abs(p.y - (3.8 + bottom) / 2) - (3.8 - bottom) / 2);
                if (s < best) { best = s; mat = M_GEMINI_STREAM; }
            }
            // The drop that sloshes out after the coffee.
            if (t > 6.75 && t < 7.5) {
                double a = (t - 6.75) / 0.75;
                V drop = new V(cupX - 0.9 - 1.6 * a, 2.3 + 1.2 * a - 4 * a * a, 0.4);
                if (drop.y > 0.05) {
                    double d = p.sub(drop).len() - 0.08;
                    if (d < best) { best = d; mat = M_COFFEE_STREAM; }
                }
            }

            // Cup group, with a bounding sphere so far rays skip it cheaply.
            if (cupY < 20) {
                double bound = new V(p.x - cupX, p.y - cupY - 1.8, p.z).len() - 2.9;
                if (bound > 0.5) {
                    best = Math.min(best, bound);
                } else {
                    double[] cup = cupGroup(p);
                    if (cup[0] < best) { best = cup[0]; mat = (int) cup[1]; }
                }
            }

            // Claw.
            if (clawVisible) {
                double[] c = claw(p);
                if (c[0] < best) { best = c[0]; mat = (int) c[1]; }
            }

            // Heart.
            if (heartScale > 0.01) {
                double s = 1.0 * heartScale;
                V q = p.sub(new V(END_X, 0.15 + 0.12 * Math.sin(t * 4), 0));
                double c = Math.cos(heartSpin), sn = Math.sin(heartSpin);
                q = new V(q.x * c - q.z * sn, q.y, q.x * sn + q.z * c).mul(1 / s);
                double d = sdHeart(q) * s;
                if (d < best) { best = d; mat = M_HEART; }
            }
            return new double[]{best, mat};
        }

        double[] cupGroup(V p) {
            V q = toCup(p);
            double k = Math.min(sy, sxz);
            double best = 1e9;
            int mat = M_NONE;

            double r = Math.hypot(q.x, q.z);
            double outer = sdCappedCone(r, q.y - 1.1, 1.1, 0.78, 1.0);
            double inner = sdCappedCone(r, q.y - 1.25, 1.1, 0.725, 0.945);
            double shell = Math.max(outer, -inner);
            double lip = Math.hypot(r - 0.965, q.y - 2.2) - 0.055;
            double cup = Math.min(shell, lip) * k;
            if (cup < best) { best = cup; mat = M_CUP; }

            if (coffee > 0) {
                double level = 0.25 + coffee * 1.78;
                double radius = 0.725 + 0.22 * (level - 0.15) / 2.2;
                double liquid = Math.max(Math.abs(q.y - level - slosh * q.x) - 0.02, r - radius) * k;
                if (liquid < best) { best = liquid; mat = M_LIQUID; }
            }

            for (int side = -1; side <= 1; side += 2) {
                double eye = (q.sub(eyeCenter(side)).len() - eyeR) * k;
                if (eye < best) { best = eye; mat = M_EYE; }
            }

            if (topping > 0) {
                double d = toppingDistance(toTopping(q)) * k * Math.min(tsy, tsxz) / (1 + Math.abs(tLean));
                if (d < best) { best = d; mat = M_TOPPING; }
            }
            return new double[]{best, mat};
        }

        /** Soft-serve: four stacked tori and a curled tip, growing in as it's poured. */
        double toppingDistance(V p) {
            double grow = topping * 5.2;
            double d = 1e9;
            double r = Math.hypot(p.x, p.z);
            for (int k = 0; k < 4; k++) {
                double appear = clamp01(grow - k);
                if (appear <= 0) break;
                double y = 0.05 + k * 0.31, major = (0.8 - k * 0.19) * (0.4 + 0.6 * appear), minor = (0.24 - k * 0.035) * appear;
                double torus = Math.hypot(r - major, p.y - y) - minor;
                d = k == 0 ? torus : smin(d, torus, 0.12);
            }
            double tip = clamp01(grow - 4);
            if (tip > 0) {
                double cone = new V(p.x + 0.05, p.y - 1.3, p.z).len() - 0.17 * tip;
                d = smin(d, cone, 0.12);
            }
            return d;
        }

        double[] claw(V p) {
            double best, mat;
            double rod = sdCapsule(p, new V(clawX, 12, 0), new V(clawX, palmY + 0.1, 0), 0.11);
            double palm = sdRoundBox(new V(p.x - clawX, p.y - palmY, p.z), 1.3, 0.09, 0.28, 0.06);
            best = Math.min(rod, palm);
            mat = M_CLAW;
            double span = clawOpen ? 1.45 : (fingerLen > 2 ? 1.2 : 1.1);
            for (int side = -1; side <= 1; side += 2) {
                V top = new V(clawX + side * span, palmY, 0);
                V tip = new V(clawX + side * (span - 0.12), palmY - fingerLen, 0);
                double finger = sdCapsule(p, top, tip, 0.085);
                if (finger < best) { best = finger; mat = M_CLAW; }
                double joint = Math.min(p.sub(top).len() - 0.15, p.sub(tip).len() - 0.12);
                if (joint < best) { best = joint; mat = M_JOINT; }
            }
            double wrist = p.sub(new V(clawX, palmY + 0.25, 0)).len() - 0.2;
            if (wrist < best) { best = wrist; mat = M_JOINT; }
            return new double[]{best, mat};
        }
    }

    // ------------------------------------------------------------------ SDF primitives

    private static double sdRoundBox(V p, double bx, double by, double bz, double r) {
        double qx = Math.abs(p.x) - bx + r, qy = Math.abs(p.y) - by + r, qz = Math.abs(p.z) - bz + r;
        double outside = Math.sqrt(Math.pow(Math.max(qx, 0), 2) + Math.pow(Math.max(qy, 0), 2) + Math.pow(Math.max(qz, 0), 2));
        return outside + Math.min(Math.max(qx, Math.max(qy, qz)), 0) - r;
    }

    /** Capped cone around the y axis, given radial distance qx and height qy (centred), bottom r1, top r2. */
    private static double sdCappedCone(double qx, double qy, double h, double r1, double r2) {
        double k1x = r2, k1y = h, k2x = r2 - r1, k2y = 2 * h;
        double cax = qx - Math.min(qx, qy < 0 ? r1 : r2), cay = Math.abs(qy) - h;
        double f = clamp01(((k1x - qx) * k2x + (k1y - qy) * k2y) / (k2x * k2x + k2y * k2y));
        double cbx = qx - k1x + k2x * f, cby = qy - k1y + k2y * f;
        double s = (cbx < 0 && cay < 0) ? -1 : 1;
        return s * Math.sqrt(Math.min(cax * cax + cay * cay, cbx * cbx + cby * cby));
    }

    private static double sdCapsule(V p, V a, V b, double r) {
        V pa = p.sub(a), ba = b.sub(a);
        double h = clamp01(pa.dot(ba) / ba.dot(ba));
        return pa.sub(ba.mul(h)).len() - r;
    }

    /** A puffy heart: Inigo Quilez's 2D heart, extruded with rounded edges. */
    private static double sdHeart(V p) {
        double x = Math.abs(p.x), y = p.y + 0.15;
        double d2;
        if (y + x > 1) {
            d2 = Math.hypot(x - 0.25, y - 0.75) - Math.sqrt(2) / 4;
        } else {
            double m = 0.5 * Math.max(x + y, 0);
            d2 = Math.sqrt(Math.min(x * x + (y - 1) * (y - 1), (x - m) * (x - m) + (y - m) * (y - m))) * Math.signum(x - y);
        }
        double wz = Math.abs(p.z) - 0.12;
        return Math.hypot(Math.max(d2, 0), Math.max(wz, 0)) + Math.min(Math.max(d2, wz), 0) - 0.14;
    }

    // ------------------------------------------------------------------ rendering

    private static final V KEY = new V(-0.5, 0.85, 0.55).norm(), FILL = new V(0.7, 0.25, 0.65).norm(), RIM = new V(0.35, 0.45, -0.85).norm();
    private static final double[] KEY_C = {1.25, 1.1, 0.92}, FILL_C = {0.22, 0.3, 0.5}, RIM_C = {0.8, 0.55, 1.1}, AMB_C = {0.13, 0.11, 0.2};

    static Canvas frame(int index) {
        return render(index / (double) FPS);
    }

    static Canvas render(double t) {
        Scene s = new Scene(t);
        double[] rgb = new double[W * W * 3];
        IntStream.range(0, W).parallel().forEach(y -> {
            for (int x = 0; x < W; x++) {
                double[] c = tracePixel(s, x + 0.5, y + 0.5);
                int i = (y * W + x) * 3;
                rgb[i] = c[0]; rgb[i + 1] = c[1]; rgb[i + 2] = c[2];
            }
        });
        // Exposure, tone map (ACES approximation) and gamma, to 0..255.
        for (int i = 0; i < rgb.length; i++) {
            double v = rgb[i] * 1.45;
            v = (v * (2.51 * v + 0.03)) / (v * (2.43 * v + 0.59) + 0.14);
            rgb[i] = 255 * Math.pow(clamp01(v), 1 / 2.2);
        }
        Overlay o = new Overlay(s, rgb);
        o.draw();
        double fade = smoothstep(0, PLACE, t) * (1 - smoothstep(FADE_OUT, BLACK, t));

        Canvas c = new Canvas();
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                double r = 0, g = 0, b = 0;
                for (int k = 0; k < 4; k++) {
                    int i = ((y * 2 + k / 2) * W + x * 2 + k % 2) * 3;
                    r += rgb[i]; g += rgb[i + 1]; b += rgb[i + 2];
                }
                c.set(x, y, (to8(r / 4 * fade) << 16) | (to8(g / 4 * fade) << 8) | to8(b / 4 * fade));
            }
        }
        for (int station = 0; station < STATIONS.length; station++) crispSign(c, s, station, fade);
        return c;
    }

    private static double dispenserHalfWidth(int station) {
        // Wide signboards so each name fits on the LED grid: GEMINI needs 23 LEDs, JAVA 15, the heart + DEV 17.
        return station == 1 ? 1.65 : 1.25;
    }

    private static final String[] HEART_5 = {".#.#.", "#####", "#####", ".###.", "..#.."};

    /**
     * The dispenser's name, drawn pixel-exact on the 64x64 grid where its panel is on screen: JAVA (white on Java
     * orange), GEMINI (Gemini gradient on dark glass), a beating heart + DEV (on pink). A word rendered in 3D would
     * fall between LEDs and blur when downsampled; this keeps every stroke one crisp LED.
     */
    private static void crispSign(Canvas c, Scene s, int station, double fade) {
        double half = dispenserHalfWidth(station) - 0.17;
        double[] left = s.project(new V(STATIONS[station] - half, 4.8, 0.73));
        double[] right = s.project(new V(STATIONS[station] + half, 4.8, 0.73));
        if (left == null || right == null) return;
        String word = switch (station) { case 0 -> "JAVA"; case 1 -> "GEMINI"; default -> "DEV"; };
        int iconW = station == 2 ? 6 : 0;                                  // heart (5) + 1 gap before DEV
        int textW = word.length() * 4 - 1, totalW = iconW + textW;
        double width = (right[0] - left[0]) / 2;                           // panel width in LED pixels
        double alpha = smoothstep(totalW - 3, totalW + 1, width) * fade;
        if (alpha <= 0) return;
        int x0 = (int) Math.round((left[0] + right[0]) / 4 - totalW / 2.0);
        int y0 = (int) Math.round((left[1] + right[1]) / 4 - 2.5);
        double pulse = 0.85 + 0.15 * Math.sin(s.t * 5);
        int plate = switch (station) { case 0 -> 0xE76F00; case 1 -> 0x0D0D1A; default -> 0xFFF0F5; };
        for (int y = -1; y <= 5; y++) {
            for (int x = -1; x <= totalW; x++) c.blend(x0 + x, y0 + y, plate, alpha);   // backing plate for contrast
        }
        if (station == 2) {                                                // beating heart icon
            double beat = Math.max(0, Math.sin(s.t * 7));
            int heart = lerp(0xE91E63, 0xFF6F9F, beat);
            for (int y = 0; y < 5; y++) {
                for (int x = 0; x < 5; x++) {
                    if (HEART_5[y].charAt(x) == '#') c.blend(x0 + x, y0 + y, heart, alpha);
                }
            }
        }
        for (int cx = 0; cx < textW; cx++) {
            for (int cy = 0; cy < 5; cy++) {
                if (!textAt(word, (cx + 0.5) / textW, (cy + 0.5) / 5)) continue;
                int col = switch (station) {
                    case 0 -> 0xFFFFFF;
                    case 1 -> lerp(lerp(GoogleColors.gradient(GEMINI_GRADIENT, cx / (double) (textW - 1)), 0xFFFFFF, 0.25), 0, 1 - pulse);
                    default -> 0x8B1A3A;
                };
                c.blend(x0 + iconW + cx, y0 + cy, col, alpha);
            }
        }
    }

    private static int to8(double v) { return (int) Math.clamp(Math.round(v), 0, 255); }

    private static double[] tracePixel(Scene s, double px, double py) {
        V rd = s.camFwd.mul(s.focal).add(s.camRight.mul(px - W / 2.0)).add(s.camUp.mul(W / 2.0 - py)).norm();
        V ro = s.camPos;
        double tt = 0.05;
        for (int step = 0; step < 110 && tt < 40; step++) {
            V p = ro.add(rd.mul(tt));
            double d = s.map(p);
            if (d < 0.0015 * tt) {
                double[] hit = s.mapWithMaterial(p, true);
                return shade(s, p, rd, (int) hit[1], tt, px, py);
            }
            tt += d * 0.9;
        }
        return background(s, px, py);
    }

    private static double[] background(Scene s, double px, double py) {
        double v = py / W;
        double r = mix(0.025, 0.06, v), g = mix(0.015, 0.03, v), b = mix(0.06, 0.09, v);
        for (double[] k : s.bokeh) {
            if (k == null) continue;
            double d = Math.hypot(px - k[0], py - k[1]);
            double a = smoothstep(k[2], k[2] - 2.5, d) * 0.22 + smoothstep(k[2] + 3, k[2] - 1, d) * 0.06;
            int col = FOUR[(int) k[3]];
            r += lin(GoogleColors.r(col)) * a; g += lin(GoogleColors.g(col)) * a; b += lin(GoogleColors.b(col)) * a;
        }
        return new double[]{r, g, b};
    }

    private static double lin(int c8) { return Math.pow(c8 / 255.0, 2.2); }

    private static double[] linColor(int rgb) {
        return new double[]{lin(GoogleColors.r(rgb)), lin(GoogleColors.g(rgb)), lin(GoogleColors.b(rgb))};
    }

    private static V normal(Scene s, V p) {
        double e = 0.0015;
        double a = s.map(new V(p.x + e, p.y - e, p.z - e)), b = s.map(new V(p.x - e, p.y - e, p.z + e));
        double c = s.map(new V(p.x - e, p.y + e, p.z - e)), d = s.map(new V(p.x + e, p.y + e, p.z + e));
        return new V(a - b - c + d, -a - b + c + d, -a + b - c + d).norm();
    }

    private static double softShadow(Scene s, V p, V l) {
        double res = 1, tt = 0.03;
        for (int i = 0; i < 28 && tt < 8; i++) {
            double h = s.map(p.add(l.mul(tt)));
            if (h < 0.001) return 0;
            res = Math.min(res, 9 * h / tt);
            tt += Math.clamp(h, 0.03, 0.5);
        }
        return clamp01(res);
    }

    private static double ambientOcclusion(Scene s, V p, V n) {
        double occ = 0, w = 1;
        for (int i = 1; i <= 4; i++) {
            double h = 0.06 * i;
            occ += (h - s.map(p.add(n.mul(h)))) * w;
            w *= 0.7;
        }
        return clamp01(1 - 2.2 * occ);
    }

    /** Surface look at a hit point: albedo, specular strength, shininess, emission, wrap lighting. */
    private record Surface(double[] albedo, double spec, double shininess, double[] emission, double wrap) {
        static Surface of(int rgb, double spec, double shin) { return new Surface(linColor(rgb), spec, shin, new double[3], 0); }
    }

    private static double[] shade(Scene s, V p, V rd, int mat, double dist, double px, double py) {
        V n = normal(s, p);
        Surface sf = surface(s, p, n, mat);
        double ao = ambientOcclusion(s, p, n);
        double sh = softShadow(s, p.add(n.mul(0.01)), KEY);
        double nl = n.dot(KEY);
        double diff = Math.max(0, (nl + sf.wrap()) / (1 + sf.wrap()));
        double fill = Math.max(0, n.dot(FILL));
        double fres = Math.pow(1 - Math.max(0, n.dot(rd.mul(-1))), 3);
        double rim = fres * (0.35 + 0.65 * Math.max(0, n.dot(RIM)));
        V h = KEY.sub(rd).norm();
        double spec = sf.spec() * Math.pow(Math.max(0, n.dot(h)), sf.shininess()) * sh;
        double[] out = new double[3];
        for (int c = 0; c < 3; c++) {
            double light = KEY_C[c] * diff * sh + FILL_C[c] * fill + AMB_C[c] * ao;
            out[c] = sf.albedo()[c] * light * (0.55 + 0.45 * ao) + KEY_C[c] * spec + RIM_C[c] * rim * 0.45 * ao + sf.emission()[c];
        }
        // Depth haze toward the background color.
        double[] bg = background(s, px, py);
        double fog = 1 - Math.exp(-Math.max(0, dist - 9) * 0.06);
        for (int c = 0; c < 3; c++) out[c] = mix(out[c], bg[c], fog);
        return out;
    }

    private static Surface surface(Scene s, V p, V n, int mat) {
        double t = s.t;
        switch (mat) {
            case M_FLOOR -> {
                double pool = Math.exp(-Math.pow((p.x - s.cupX) / 6, 2) - Math.pow(p.z / 3, 2));
                int c = lerp(0x0E0C16, 0x2A2236, pool);
                boolean grid = ((p.x % 2 + 2) % 2) < 0.05 || ((p.z % 2 + 2) % 2) < 0.05;
                return Surface.of(grid ? lerp(c, 0x3C4043, 0.5) : c, 0.15, 30);
            }
            case M_BELT -> {
                if (n.y > 0.6) {                                                   // rubber top with moving treads
                    double u = ((p.x + s.beltOffset) % 0.45 + 0.45) % 0.45;
                    return Surface.of(u < 0.07 ? 0x4A4D55 : 0x24262C, 0.25, 20);
                }
                if (n.z > 0.6) {                                                   // front edge: running LED strip
                    double u = p.x * 1.6 - t * 3;
                    int col = FOUR[(int) Math.floorMod((long) Math.floor(u), 4L)];
                    Surface base = Surface.of(0x202124, 0.3, 40);
                    double on = Math.abs(p.y + 0.18) < 0.07 ? 1 : 0;
                    double[] e = linColor(col);
                    return new Surface(base.albedo(), 0.3, 40, new double[]{e[0] * on * 1.4, e[1] * on * 1.4, e[2] * on * 1.4}, 0);
                }
                return Surface.of(0x2A2C31, 0.3, 30);
            }
            case M_ROLLER -> {
                double ang = Math.atan2(p.y + 0.62, ((p.x + s.beltOffset) % 1.1 + 1.1) % 1.1 - 0.55) + s.beltOffset / 0.3;
                return Surface.of(Math.sin(ang * 3) > 0.7 ? 0xBDC1C6 : 0x5F6368, 0.8, 60);
            }
            case M_DISPENSER -> {
                int station = nearestStation(p.x);
                int body = switch (station) { case 0 -> 0xECE7E1; case 1 -> 0x1B1B2A; default -> 0xF8BBD0; };
                double panel = dispenserHalfWidth(station) - 0.17;
                if (n.z > 0.8 && Math.abs(p.x - STATIONS[station]) < panel && Math.abs(p.y - 4.8) < 0.45) {
                    return label(s, station, (p.x - STATIONS[station]) / panel, (p.y - 4.8) / 0.45);
                }
                return Surface.of(body, station == 1 ? 0.9 : 0.6, station == 1 ? 90 : 50);
            }
            case M_NOZZLE -> { return Surface.of(0x9AA0A6, 1.0, 80); }
            case M_LAMP -> {
                int station = nearestStation(p.x);
                boolean on = t >= POUR[station] && t < DONE[station];
                double[] e = linColor(on ? GREEN : 0xC5221F);
                return new Surface(linColor(0x202124), 0.5, 60, new double[]{e[0] * 2, e[1] * 2, e[2] * 2}, 0);
            }
            case M_COFFEE_STREAM -> { return Surface.of(0x5A3216, 1.2, 70); }
            case M_GEMINI_STREAM -> {
                int col = GoogleColors.cyclic(RAINBOW, p.y * 0.5 + t * 1.5);
                double[] e = linColor(col);
                return new Surface(e, 0.8, 50, new double[]{e[0] * 0.5, e[1] * 0.5, e[2] * 0.5}, 0.4);
            }
            case M_CLAW -> { return Surface.of(0xC9CDD2, 1.0, 70); }
            case M_JOINT -> {
                int col = FOUR[(int) (Math.floorMod((long) Math.floor(p.x * 1.3 + p.y * 2), 4L))];
                double[] e = linColor(col);
                return new Surface(e, 0.9, 60, new double[]{e[0] * 0.25, e[1] * 0.25, e[2] * 0.25}, 0);
            }
            case M_HEART -> {
                double[] e = linColor(0xFF4D7E);
                double glow = 0.35 + 0.15 * Math.sin(t * 8);
                return new Surface(e, 1.0, 60, new double[]{e[0] * glow, e[1] * glow, e[2] * glow}, 0.5);
            }
            case M_LIQUID -> {
                V q = s.toCup(p);
                double level = 0.25 + s.coffee * 1.78, radius = 0.725 + 0.22 * (level - 0.15) / 2.2;
                double edge = Math.hypot(q.x + s.slosh * 2, q.z) / radius;
                int col = edge > 0.72 ? 0xB98A5B : 0x3B2010;                       // crema ring
                return Surface.of(col, 1.2, 90);
            }
            case M_TOPPING -> {
                V q = s.toTopping(s.toCup(p));
                double ang = Math.atan2(q.x, q.z) / (2 * Math.PI);
                int col = lerp(GoogleColors.cyclic(RAINBOW, ang + q.y * 0.9 + t * 0.08), 0xFFFFFF, 0.2);
                return new Surface(linColor(col), 0.7, 40, new double[3], 0.45);
            }
            case M_EYE -> { return eye(s, p); }
            default -> { return cupSurface(s, p); }
        }
    }

    private static int nearestStation(double x) {
        int best = 0;
        for (int i = 1; i < 3; i++) if (Math.abs(x - STATIONS[i]) < Math.abs(x - STATIONS[best])) best = i;
        return best;
    }

    private static final Map<Character, String[]> FONT = Map.ofEntries(
            Map.entry('J', new String[]{"###", "..#", "..#", "#.#", ".#."}),
            Map.entry('A', new String[]{".#.", "#.#", "###", "#.#", "#.#"}),
            Map.entry('V', new String[]{"#.#", "#.#", "#.#", ".#.", ".#."}),
            Map.entry('D', new String[]{"##.", "#.#", "#.#", "#.#", "##."}),
            Map.entry('E', new String[]{"###", "#..", "##.", "#..", "###"}),
            Map.entry('O', new String[]{"###", "#.#", "#.#", "#.#", "###"}),
            Map.entry('X', new String[]{"#.#", "#.#", ".#.", "#.#", "#.#"}),
            Map.entry('G', new String[]{".##", "#..", "#.#", "#.#", ".##"}),
            Map.entry('M', new String[]{"#.#", "###", "###", "#.#", "#.#"}),
            Map.entry('N', new String[]{"##.", "#.#", "#.#", "#.#", "#.#"}),
            Map.entry('I', new String[]{"###", ".#.", ".#.", ".#.", "###"}));

    /** Is the text lit at (u, v) in [0,1]x[0,1] of its box? */
    private static boolean textAt(String text, double u, double v) {
        int cols = text.length() * 4 - 1;
        int cx = (int) Math.floor(u * cols), cy = (int) Math.floor(v * 5);
        if (cx < 0 || cy < 0 || cx >= cols || cy >= 5 || cx % 4 == 3) return false;
        return FONT.get(text.charAt(cx / 4))[cy].charAt(cx % 4) == '#';
    }

    private static boolean heartAt(double u, double v) {
        double x = (u - 0.5) * 2.4, y = (0.55 - v) * 2.4;
        double q = x * x + y * y - 1;
        return q * q * q - x * x * y * y * y <= 0;
    }

    /** Front label panels: (lx, ly) in [-1, 1] across the panel. */
    private static Surface label(Scene s, int station, double lx, double ly) {
        double u = (lx + 1) / 2, v = (1 - ly) / 2;
        double t = s.t;
        switch (station) {
            case 0 -> {
                boolean on = textAt("JAVA", (u - 0.12) / 0.76, (v - 0.2) / 0.6);
                return Surface.of(on ? 0xFFFFFF : 0xE76F00, 0.4, 40);
            }
            case 1 -> {
                // Dark glass with a glowing gradient frame; the word GEMINI is drawn pixel-exact on top (crispSign).
                double frame = Math.max(Math.abs(lx), Math.abs(ly));
                if (frame > 0.9) {
                    double pulse = 0.8 + 0.4 * Math.sin(t * 5);
                    double[] e = linColor(GoogleColors.gradient(GEMINI_GRADIENT, clamp01((lx + 1) / 2)));
                    return new Surface(e, 0.5, 40, new double[]{e[0] * pulse, e[1] * pulse, e[2] * pulse}, 0);
                }
                return Surface.of(0x0D0D1A, 0.9, 90);
            }
            default -> {
                boolean text = textAt("DEV", (u - 0.03) / 0.56, (v - 0.15) / 0.7);
                double beat = 1 + 0.25 * Math.max(0, Math.sin(t * 7));
                boolean heart = heartAt((u - 0.62) / 0.34 * beat - (beat - 1) / 2, (v - 0.12) / 0.76 * beat - (beat - 1) / 2);
                if (heart) {
                    double[] e = linColor(0xE91E63);
                    return new Surface(e, 0.5, 40, new double[]{e[0] * 0.6, e[1] * 0.6, e[2] * 0.6}, 0);
                }
                return Surface.of(text ? 0x5F2120 : 0xFFF5F8, 0.4, 40);
            }
        }
    }

    /** Paper cup: DEVOXX sleeve, a mouth, blushing cheeks; darker inside. */
    private static Surface cupSurface(Scene s, V p) {
        V q = s.toCup(p);
        double r = Math.hypot(q.x, q.z);
        double outerR = 0.78 + 0.22 * q.y / 2.2;
        if (r < outerR - 0.035 && q.y > 0.2) return Surface.of(0xCFC6BA, 0.1, 10);   // inside wall
        double ang = Math.atan2(q.x, q.z);
        double u = ang * outerR, v = q.y;                                           // arc length, height
        Face f = s.face;
        // Sleeve with DEVOXX.
        if (v > 0.55 && v < 1.32) {
            boolean edge = v < 0.6 || v > 1.27;
            boolean text = textAt("DEVOXX", (u + 0.8) / 1.6, (1.2 - v) / 0.5);   // on the front, facing camera
            int col = text ? 0xFFFFFF : (edge ? 0xC25E00 : 0xF57C00);
            return Surface.of(col, 0.35, 30);
        }
        // Mouth.
        double mx = u, my = v - 1.45;
        boolean mouthDark = switch (f.mouth()) {
            case SLEEP -> Math.abs(my - 0.25 * mx * mx * 4) < 0.025 && Math.abs(mx) < 0.1;
            case SMILE -> Math.abs(my - 0.9 * mx * mx) < 0.032 && Math.abs(mx) < 0.2;
            case GRIN -> my < 0.06 && my > -0.1 + 1.6 * mx * mx && Math.abs(mx) < 0.24;
            case OH -> Math.hypot(mx / 0.8, my) < 0.075;
            default -> Math.abs(my - 0.03 * Math.sin(mx * 40)) < 0.025 && Math.abs(mx) < 0.15; // oops
        };
        if (mouthDark) {
            boolean tongue = f.mouth() == GRIN && my < -0.03 + 1.6 * mx * mx + 0.05 && my > -0.1 + 1.6 * mx * mx;
            return Surface.of(tongue ? 0xF28B82 : 0x3B1414, 0.2, 20);
        }
        // Blushing cheeks.
        double cheek = Math.min(Math.hypot((Math.abs(u) - 0.5) / 1.5, v - 1.55), 1);
        int paper = 0xCFC7BD;                                            // not pure white, so shading reads
        if (f.blush() > 0 && cheek < 0.09) paper = lerp(paper, 0xFF8FAB, f.blush() * 0.85);
        return Surface.of(paper, 0.25, 25);
    }

    /** Big glossy Pixar eyes: iris in Google blue, pupil, catch light, eyelids, happy "^ ^" when closed. */
    private static Surface eye(Scene s, V p) {
        V q = s.toCup(p);
        int side = q.x < 0 ? -1 : 1;
        V c = s.eyeCenter(side);
        V d = q.sub(c).norm();
        Face f = s.face;
        // Lids close from the top.
        double lidLine = 1 - 2 * f.lid();
        if (d.y > lidLine - 0.02 || f.lid() > 0.97) {
            if (f.lid() > 0.97) {
                double arc = f.happy() ? 0.15 - 1.4 * d.x * d.x : -0.1 + 0.6 * d.x * d.x;
                if (Math.abs(d.y - arc) < 0.11 && d.z > 0.3) return Surface.of(0x202124, 0.2, 20);
            }
            return Surface.of(0xEDE6DD, 0.3, 25);
        }
        V look = new V(f.lookX() * 0.6 + side * 0.04, f.lookY() * 0.55, 1).norm();
        double a = d.dot(look);
        double pupil = 0.9 + 0.02 * f.wide();
        if (a > pupil) {
            V catchDir = new V(look.x - 0.25, look.y + 0.3, look.z).norm();
            return Surface.of(d.dot(catchDir) > 0.985 ? 0xFFFFFF : 0x0A0A12, 1.2, 120);
        }
        if (a > 0.78) return Surface.of(lerp(0x1A5FD0, 0x6FA8FF, (a - 0.78) / 0.12), 1.2, 120);
        return Surface.of(0xE8E8EC, 1.0, 100);
    }

    // ------------------------------------------------------------------ screen-space effects

    private static final class Overlay {
        final Scene s;
        final double[] rgb;

        Overlay(Scene s, double[] rgb) { this.s = s; this.rgb = rgb; }

        void add(int x, int y, int color, double a) {
            if (x < 0 || y < 0 || x >= W || y >= W || a <= 0) return;
            int i = (y * W + x) * 3;
            rgb[i] += GoogleColors.r(color) * a;
            rgb[i + 1] += GoogleColors.g(color) * a;
            rgb[i + 2] += GoogleColors.b(color) * a;
        }

        void glow(double cx, double cy, double radius, int color, double a) {
            int r = (int) Math.ceil(radius * 2.5);
            for (int y = (int) cy - r; y <= cy + r; y++) {
                for (int x = (int) cx - r; x <= cx + r; x++) {
                    double d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
                    add(x, y, color, a * Math.exp(-d * d / (radius * radius)));
                }
            }
        }

        void draw() {
            double t = s.t;
            steam(t);
            if (t > POUR[1] - 0.2 && t < LEAVE[1] + 0.3) geminiMagic(t);
            if (t > POUR[2]) sprinkles(t);
            if (t > POUR[2] + 0.3 && t < CLAW_DOWN) floatingHearts(t);
            if (s.heartScale > 0.01) {
                double[] h = s.project(new V(END_X, 0.75, 0));
                if (h != null) glow(h[0], h[1], 18 * s.heartScale, 0xFF4D7E, 0.4);
            }
            if (t > ARRIVE[1] - 1.6 && t < LEAVE[1] + 1.2) {                   // Gemini sparkle twinkles at the nozzle
                double[] g = s.project(new V(STATIONS[1], 3.72, 0.3));
                double k = 0.75 + 0.25 * Math.sin(t * 5);
                if (g != null) {
                    glow(g[0], g[1], 5, 0x9177C7, 0.45 * k);
                    sparkle(g[0], g[1], 5 * k, 0x7B9CFF, 1);
                }
            }
            motes(t);
        }

        /** Steam curling up from the fresh coffee. */
        void steam(double t) {
            double k = smoothstep(DONE[0] - 0.4, DONE[0], t) * (1 - smoothstep(LEAVE[0], ARRIVE[1], t));
            if (k <= 0) return;
            for (int w = 0; w < 3; w++) {
                for (int i = 0; i < 26; i++) {
                    double life = ((t * 0.6 + i / 26.0 + w * 0.33) % 1);
                    V p = s.fromCup(new V(-0.35 + w * 0.35 + 0.18 * Math.sin(life * 9 + w + t * 2), 2.2 + life * 2.4, 0.2));
                    double[] sc = s.project(p);
                    if (sc != null) glow(sc[0], sc[1], 2.2 + life * 3, 0xE8EAED, 0.05 * k * Math.sin(Math.PI * life));
                }
            }
        }

        /** Swirling Google-colored smoke and sparkles around the Gemini pour. */
        void geminiMagic(double t) {
            double k = smoothstep(POUR[1] - 0.2, POUR[1] + 0.4, t) * (1 - smoothstep(DONE[1], LEAVE[1] + 0.3, t));
            double[] a = s.project(new V(STATIONS[1], 3.8, 0)), b = s.project(s.fromCup(new V(0, 2.6, 0)));
            if (a == null || b == null || k <= 0) return;
            double cx = (a[0] + b[0]) / 2, cy = (a[1] + b[1]) / 2, ry = Math.abs(b[1] - a[1]) / 2 + 10, rx = 26;
            for (int y = (int) (cy - ry - 6); y <= cy + ry + 6; y++) {
                for (int x = (int) (cx - rx - 6); x <= cx + rx + 6; x++) {
                    double ex = (x - cx) / rx, ey = (y - cy) / ry;
                    double m = Math.exp(-(ex * ex + ey * ey) * 2.2);
                    if (m < 0.02) continue;
                    double n = Noise.smoke(x * 0.045, y * 0.045 - t * 0.6, t * 0.5 + 11);
                    add(x, y, GoogleColors.cyclic(RAINBOW, n * 1.3 + t * 0.15), k * m * smoothstep(-0.2, 0.55, n) * 0.75);
                }
            }
            for (int i = 0; i < 9; i++) {                                    // orbiting sparkles
                double ang = t * 2.2 + i * 0.7;
                V p = s.fromCup(new V(Math.cos(ang) * 1.5, 2.2 + (i % 4) * 0.45 + 0.2 * Math.sin(t * 3 + i), Math.sin(ang) * 1.5));
                double[] sc = s.project(p);
                if (sc == null) continue;
                double tw = k * Math.max(0, Math.sin(t * 9 + i * 1.7));
                sparkle(sc[0], sc[1], 3.5 * tw, GoogleColors.gradient(GEMINI_GRADIENT, i / 8.0), tw);
            }
        }

        void sparkle(double cx, double cy, double len, int color, double a) {
            if (a <= 0) return;
            for (int d = -(int) len; d <= len; d++) {
                double f = 1 - Math.abs(d) / (len + 1);
                add((int) Math.round(cx + d), (int) Math.round(cy), color, 255 / 255.0 * a * f * 0.9);
                add((int) Math.round(cx), (int) Math.round(cy + d), color, a * f * 0.9);
            }
            add((int) Math.round(cx), (int) Math.round(cy), 0xFFFFFF, a);
        }

        /** Sprinkles falling from the DEV nozzle and sticking to the swirl (they follow its jiggle). */
        void sprinkles(double t) {
            if (s.cupY > 15) return;
            for (int i = 0; i < 22; i++) {
                double start = POUR[2] + i * 0.045;
                double p = (t - start) / 0.4;
                if (p <= 0) continue;
                int level = i % 4;
                double ang = -1.1 + (i * 0.73 % 2.2);
                double major = 0.8 - level * 0.19, minor = 0.24 - level * 0.035;
                V local = s.fromTopping(new V(Math.sin(ang) * (major + minor * 0.7), 0.05 + level * 0.31 + minor * 0.75,
                        Math.cos(ang) * (major + minor * 0.7)));
                V target = s.fromCup(local);
                V pos = p >= 1 ? target : new V(mix(STATIONS[2], target.x, p), mix(3.8, target.y, p * p), mix(0, target.z, p));
                double[] sc = s.project(pos);
                if (sc == null) continue;
                int col = i % 3 == 0 ? 0xFFFFFF : (i % 3 == 1 ? 0xFF80AB : 0xFF4081);
                int x = (int) Math.round(sc[0]), y = (int) Math.round(sc[1]);
                boolean vertical = i % 2 == 0;
                for (int k = 0; k < 3; k++) {
                    int xx = vertical ? x : x + k, yy = vertical ? y + k : y;
                    int idx = (yy * W + xx) * 3;
                    if (xx < 0 || yy < 0 || xx >= W || yy >= W) continue;
                    rgb[idx] = GoogleColors.r(col); rgb[idx + 1] = GoogleColors.g(col); rgb[idx + 2] = GoogleColors.b(col);
                }
            }
        }

        void floatingHearts(double t) {
            for (int i = 0; i < 6; i++) {
                double age = t - (POUR[2] + 0.3) - i * 0.3;
                if (age < 0 || age > 2.2) continue;
                double side = i % 2 == 0 ? -1 : 1;
                V p = s.fromCup(new V(side * (1.3 + 0.2 * Math.sin(age * 3 + i)), 1.4 + age * 1.3, 0.6));
                double[] sc = s.project(p);
                if (sc == null) continue;
                double a = Math.sin(Math.PI * age / 2.2);
                double size = 3.2 + i % 2;
                for (int y = (int) (sc[1] - size); y <= sc[1] + size; y++) {
                    for (int x = (int) (sc[0] - size); x <= sc[0] + size; x++) {
                        if (heartAt((x - sc[0]) / (2 * size) + 0.5, (y - sc[1]) / (2 * size) + 0.5)) {
                            add(x, y, i % 2 == 0 ? 0xFF4081 : 0xF06292, a * 0.9);
                        }
                    }
                }
                glow(sc[0], sc[1], size * 1.4, 0xFF4081, 0.25 * a);
            }
        }

        /** Dust motes drifting through the warm key light, for atmosphere. */
        void motes(double t) {
            for (int i = 0; i < 14; i++) {
                double x = ((i * 37.7 + t * (4 + i % 3)) % (W + 20)) - 10;
                double y = ((i * 53.3 + Math.sin(t * 0.7 + i) * 6) % W);
                double a = 0.25 * Math.max(0, Math.sin(t * 1.3 + i * 2.1));
                add((int) x, (int) y, 0xFFE0B2, a);
            }
        }
    }
}
