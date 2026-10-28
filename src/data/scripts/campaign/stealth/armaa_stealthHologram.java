package data.scripts.campaign.stealth;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.opengl.GL11;

/**
 * What the drone looks like, and the emblem it projects: IRON KING's crown, ring, floating spikes and a tracking eye, all as lines, with a
 * signal distortion over it (torn bands, cut lines, static, a colour split, and now and then a burst). Drawing only: the panel calls it
 * between GL calls, with the same world-to-screen offset it uses for everything else. y runs up.
 */
public class armaa_stealthHologram {

    private static final Color HULL = new Color(18, 34, 46);
    // IRON KING is a blue-tinted grey like his portrait: the ring, the crown and the eye's lid in grey, the inner wedges darker, the tips of the spikes and the
    // two prongs of the crown white
    private static final Color LINE = new Color(163, 180, 198);
    private static final Color INNER = new Color(106, 122, 140);
    private static final Color TIP = new Color(242, 248, 255);
    // the drone is the same blue-tinted grey
    private static final Color DRONE_LINE = new Color(163, 180, 198);
    private static final Color DRONE_TIP = new Color(242, 248, 255);
    /** What a damaged monitor flickers through: green, blue, purple, cyan, yellow, dim red, magenta. */
    private static final Color[] MONITOR = {
        new Color(60, 255, 154), new Color(58, 91, 255), new Color(176, 75, 255), new Color(25, 224, 255),
        new Color(255, 225, 74), new Color(255, 74, 58), new Color(255, 79, 216)
    };
    private static final Color SPLIT_BLUE = new Color(58, 91, 255);
    private static final Color EYE = new Color(255, 90, 74);
    private static final Color GHOST = new Color(255, 79, 216);
    private static final Color DARK = new Color(11, 17, 24);
    private static final Color WARN = new Color(255, 90, 74);

    /** The drone is drawn at this share of its design size, so it stays discreet. */
    public static final float DRONE_SCALE = 0.62f;
    /** The emblem's ring radius, in world units. Originally 16; 85% of that (13.6) for a while; now another 20% smaller (10.88). */
    public static final float EMBLEM_R = 10.88f;
    /** How strong the distortion is (0 to 1); a burst raises it for a moment. */
    public static final float DISTORTION = 0.55f;

    /** GL line widths are physical pixels: the panel sets this to the interface scale (and its fit) so the lines keep their thickness. */
    public static float lineScale = 1f;

    private static final float GAP = 1.12f;
    // degrees from straight up, inner wedge: start, length, base width, end width; outer tip: gap, length, width (units of the ring radius); 1 if only one
    private static final float[][] RAYS = {
        {41f, GAP, 0.62f, 0.44f, 0.24f, 0.2f, 1.0f, 0.17f, 0f},
        {61f, GAP, 0.55f, 0.42f, 0.22f, 0.2f, 0.6f, 0.15f, 0f},
        {90f, GAP, 0.72f, 0.52f, 0.26f, 0.2f, 0.62f, 0.17f, 0f},
        {126f, GAP, 1.5f, 0.46f, 0.04f, 0f, 0f, 0f, 0f},
        {148f, GAP, 0.8f, 0.3f, 0.04f, 0f, 0f, 0f, 0f},
        {180f, GAP, 0.7f, 0.44f, 0.22f, 0.2f, 0.8f, 0.17f, 1f}
    };
    // the crown, from the reference portrait (x, y up, units of the ring radius from the ring's centre)
    private static final float[][] CROWN = {
        {-0.357f, 1.12f}, {-0.437f, 1.334f}, {-0.657f, 1.971f}, {-0.171f, 1.406f}, {-0.12f, 1.4f}, {0f, 2.497f},
        {0.12f, 1.4f}, {0.171f, 1.406f}, {0.657f, 1.971f}, {0.437f, 1.334f}, {0.357f, 1.12f}
    };

    private final int[] tipRay;
    private final int[] tipSide;
    private final int[] tipType;                                // 0 still, 1 flipping, 2 flickering
    private final float[] tipNext, tipT0, tipDur, tipHold;
    private float crownNext, crownT0;
    private int crownType;
    private final List<float[]> tears = new ArrayList<float[]>();     // y offset from the emblem's centre, height, shift, until
    private final List<float[]> cuts = new ArrayList<float[]>();      // y offset, height, width, x offset, until
    private float t;
    private float burstUntil;
    private boolean burstFlag;
    private float nextBurst = 3f;
    private float fxCy;
    private float fxK;
    private float fxR = EMBLEM_R;
    private boolean glitchPass;                                 // the main emblem pass: its edges break and flicker
    private int edgeId;
    private float[][] bands = new float[0][];                   // the stripe bands of a burst: x offset, width, seed (units of the ring radius)
    private float[][] crack = new float[0][];

    public armaa_stealthHologram() {
        int n = 0;
        for (float[] r : RAYS) {
            if (r[6] > 0f) {
                n += r[8] > 0f ? 1 : 2;
            }
        }
        tipRay = new int[n];
        tipSide = new int[n];
        tipType = new int[n];
        tipNext = new float[n];
        tipT0 = new float[n];
        tipDur = new float[n];
        tipHold = new float[n];
        int i = 0;
        for (int k = 0; k < RAYS.length; k++) {
            if (RAYS[k][6] <= 0f) {
                continue;
            }
            for (int side = -1; side <= 1; side += 2) {
                if (RAYS[k][8] > 0f && side < 0) {
                    continue;
                }
                tipRay[i] = k;
                tipSide[i] = side;
                tipNext[i] = 1f + (float) Math.random() * 4f;
                i++;
            }
        }
        crownNext = 2f + (float) Math.random() * 4f;
    }

    // ------------------------------------------------------------------ state
    /** Advances the glitching of the spikes and the crown and the distortion. Call every frame the emblem is up. */
    public void update(float dt) {
        t += dt;
        if (t >= nextBurst) {
            burstUntil = t + 0.25f + (float) Math.random() * 0.25f;
            burstFlag = true;
            int nb = 2 + (int) (Math.random() * 3);
            bands = new float[nb][];
            for (int i = 0; i < nb; i++) {
                bands[i] = new float[]{(float) (Math.random() * 3.0 - 3.4), 0.7f + (float) Math.random() * 2.2f, (float) Math.random()};
            }
            crack = new float[9][];
            float kx = -2.6f;
            float ky = (float) (Math.random() - 0.5) * 4f;
            for (int i = 0; i < 9; i++) {
                crack[i] = new float[]{kx, ky};
                kx += 0.5f + (float) Math.random() * 0.5f;
                ky += (float) (Math.random() - 0.35) * 0.7f;
            }
            nextBurst = t + 3.5f + (float) Math.random() * 4f;
        }
        for (int i = 0; i < tipType.length; i++) {
            if (tipType[i] == 0 && t >= tipNext[i]) {
                tipType[i] = Math.random() < 0.55 ? 1 : 2;
                tipT0[i] = t;
                tipDur[i] = tipType[i] == 1 ? 0.14f : 0.2f;
                tipHold[i] = tipType[i] == 1 ? 0.25f + (float) Math.random() : 0f;
            }
            if (tipType[i] != 0 && t > tipT0[i] + tipDur[i] + tipHold[i]) {
                tipType[i] = 0;
                tipNext[i] = t + 1.2f + (float) Math.random() * 3.5f;
            }
        }
        if (crownType == 0 && t >= crownNext) {
            crownType = 2;
            crownT0 = t;
        }
        if (crownType != 0 && t > crownT0 + 0.2f) {
            crownType = 0;
            crownNext = t + 3.5f + (float) Math.random() * 6f;
        }
        float k = distortion();
        if (Math.random() < dt * (2f + 9f * k)) {
            tears.add(new float[]{(float) (Math.random() - 0.5) * 6f * EMBLEM_R, 3f + (float) Math.random() * 12f,
                (Math.random() < 0.5 ? -1f : 1f) * (1.5f + (float) Math.random() * (3f + 9f * k)), t + 0.05f + (float) Math.random() * 0.16f});
        }
        if (Math.random() < dt * (4f + 22f * k)) {
            cuts.add(new float[]{(float) (Math.random() - 0.5) * 5.6f * EMBLEM_R, 1f + (float) Math.floor(Math.random() * 2),
                EMBLEM_R * (3f + (float) Math.random() * 3f), (float) (Math.random() - 0.5) * EMBLEM_R, t + 0.04f + (float) Math.random() * 0.14f});
        }
        for (int i = tears.size() - 1; i >= 0; i--) {
            if (tears.get(i)[3] <= t) {
                tears.remove(i);
            }
        }
        for (int i = cuts.size() - 1; i >= 0; i--) {
            if (cuts.get(i)[4] <= t) {
                cuts.remove(i);
            }
        }
    }

    /** True once for each burst of static that starts (for its sound). */
    public boolean takeBurst() {
        boolean b = burstFlag;
        burstFlag = false;
        return b;
    }

    private float burst() {
        return t < burstUntil ? 1f : 0f;
    }

    private float distortion() {
        return Math.min(1f, DISTORTION * (1f + 1.6f * burst()));
    }

    // ------------------------------------------------------------------ GL helpers
    private static void prep() {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private static void rgba(Color c, float a) {
        GL11.glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, Math.max(0f, Math.min(1f, a)));
    }

    private static void startLines(float width) {
        prep();
        GL11.glLineWidth(Math.max(1f, width * lineScale));
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
    }

    private static void endLines() {
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(1f);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private float shiftAt(float y) {
        float dx = 0f;
        for (float[] tr : tears) {
            float y0 = fxCy + tr[0];
            if (y >= y0 && y < y0 + tr[1]) {
                dx = tr[2] * (0.6f + 0.4f * (float) Math.sin((y - y0) * 0.9f));
            }
        }
        return dx;
    }

    private void v(float x, float y) {
        GL11.glVertex2f(x + shiftAt(y), y);
    }

    /** An outline through the points (x, y pairs), torn by the distortion. */
    private void poly(float[] p, int n, boolean closed, Color c, float a, float width) {
        if (glitchPass) {
            glitchedEdges(p, n, closed, c, a, width);
            return;
        }
        startLines(width);
        rgba(c, a);
        GL11.glBegin(closed ? GL11.GL_LINE_LOOP : GL11.GL_LINE_STRIP);
        for (int i = 0; i < n; i++) {
            v(p[i * 2], p[i * 2 + 1]);
        }
        GL11.glEnd();
        endLines();
    }

    private static float hash(int a, int b) {
        int x = a * 374761393 + b * 668265263;
        x = (x ^ (x >>> 13)) * 1274126177;
        x ^= x >>> 16;
        return (x & 0xffff) / 65535f;
    }

    /**
     * The outline edge by edge, like a damaged monitor: now and then an edge loses a piece, jumps out of place or flickers through the monitor
     * colours. Which edge, and for how long, comes from a hash of its number and the time, so nothing has to be remembered.
     */
    private void glitchedEdges(float[] p, int n, boolean closed, Color c, float a, float width) {
        startLines(width);
        float chance = 0.03f + 0.26f * burst();
        int bucket = (int) (t / 0.12f);
        int segs = closed ? n : n - 1;
        GL11.glBegin(GL11.GL_LINES);
        for (int i = 0; i < segs; i++) {
            int j = (i + 1) % n;
            float ax = p[i * 2], ay = p[i * 2 + 1], bx = p[j * 2], by = p[j * 2 + 1];
            int id = edgeId++;
            Color col = c;
            if (hash(id, bucket) < chance) {
                float m = hash(id + 7919, bucket);
                col = MONITOR[(int) (hash(id + 104729, bucket) * MONITOR.length) % MONITOR.length];
                float cut = 0.3f + 0.4f * hash(id + 31, bucket);
                float jx = (hash(id + 57, bucket) - 0.5f) * 0.32f * fxR;
                float jy = (hash(id + 91, bucket) - 0.5f) * 0.32f * fxR;
                if (m < 0.4f) {                                      // loses a piece from its middle
                    rgba(c, a);
                    float g = 0.2f;
                    GL11.glVertex2f(ax + shiftAt(ay), ay);
                    GL11.glVertex2f(ax + (bx - ax) * (cut - g) - jx * 0.3f, ay + (by - ay) * (cut - g) - jy * 0.3f);
                    GL11.glVertex2f(ax + (bx - ax) * (cut + g) + jx * 0.3f, ay + (by - ay) * (cut + g) + jy * 0.3f);
                    GL11.glVertex2f(bx + shiftAt(by), by);
                    continue;
                }
                if (m >= 0.8f) {                                     // jumps out of place
                    ax += jx;
                    bx += jx;
                    ay += jy;
                    by += jy;
                }
            }
            rgba(col, a);
            GL11.glVertex2f(ax + shiftAt(ay), ay);
            GL11.glVertex2f(bx + shiftAt(by), by);
        }
        GL11.glEnd();
        endLines();
    }

    private void circle(float cx, float cy, float rx, float ry, Color c, float a, float width) {
        int segs = 28;
        float[] p = new float[segs * 2];
        for (int i = 0; i < segs; i++) {
            double ang = i * Math.PI * 2.0 / segs;
            p[i * 2] = cx + (float) Math.cos(ang) * rx;
            p[i * 2 + 1] = cy + (float) Math.sin(ang) * ry;
        }
        poly(p, segs, true, c, a, width);
    }

    private static void disc(float cx, float cy, float rx, float ry, Color c, float a) {
        prep();
        rgba(c, a);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(cx, cy);
        for (int i = 0; i <= 20; i++) {
            double ang = i / 20.0 * Math.PI * 2.0;
            GL11.glVertex2f(cx + (float) Math.cos(ang) * rx, cy + (float) Math.sin(ang) * ry);
        }
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    // ------------------------------------------------------------------ the drone
    /** The drone: rotors, a dark body, a cracked lens turned toward what it is talking about, and a status light. */
    public void drone(float ox, float oy, armaa_stealthSim.Drone d, float clock, float alpha, float lookX, float lookY, boolean projecting) {
        float s = DRONE_SCALE;
        float lift = d.lift;
        float hover = (float) Math.sin(clock * 2.2f) * 1.6f * s * lift;
        float x = ox + d.x + (float) Math.sin(clock * 40f) * (1f - lift) * lift * 4f * s;
        float y = oy + d.y + hover - (1f - lift) * 2f * s;
        disc(x + 3f * s, oy + d.y - 11f * s + (1f - lift) * 8f * s, 7.5f * s, 3.4f * s, Color.black, (0.4f + 0.2f * (1f - lift)) * alpha);
        double face = Math.toRadians(d.face);
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 4.0 + k * Math.PI / 2.0 + face;
            float ax = x + (float) Math.cos(a) * 10f * s;
            float ay = y + (float) Math.sin(a) * 10f * s;
            startLines(1f);
            rgba(DRONE_LINE, 0.5f * alpha);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2f(x + (float) Math.cos(a) * 5f * s, y + (float) Math.sin(a) * 5f * s);
            GL11.glVertex2f(ax, ay);
            GL11.glEnd();
            rgba(DRONE_LINE, 0.3f * alpha);
            GL11.glBegin(GL11.GL_LINE_LOOP);
            for (int i = 0; i < 10; i++) {
                double b = i * Math.PI * 2.0 / 10.0;
                GL11.glVertex2f(ax + (float) Math.cos(b) * 4.2f * s, ay + (float) Math.sin(b) * 4.2f * s);
            }
            GL11.glEnd();
            rgba(DRONE_TIP, 0.7f * alpha);
            GL11.glBegin(GL11.GL_LINE_STRIP);
            double sa = clock * 38.0 * lift + k * 1.7;
            for (int i = 0; i <= 5; i++) {
                double b = sa + i * 0.26;
                GL11.glVertex2f(ax + (float) Math.cos(b) * 4.2f * s, ay + (float) Math.sin(b) * 4.2f * s);
            }
            GL11.glEnd();
            endLines();
        }
        disc(x, y, 7.2f * s, 7.2f * s, HULL, 0.92f * alpha);
        circle0(x, y, 7.2f * s, DRONE_LINE, 0.85f * alpha, 1.3f);
        float lx = x;
        float ly = y;
        if (projecting) {
            float len = (float) Math.hypot(lookX, lookY);
            if (len > 0.001f) {
                lx += lookX / len * 1.8f * s;
                ly += lookY / len * 1.8f * s;
            }
        }
        disc(lx, ly, 3.4f * s, 3.4f * s, new Color(5, 10, 14), alpha);
        circle0(lx, ly, 3.4f * s, DRONE_LINE, 0.8f * alpha, 1f);
        startLines(1f);
        rgba(Color.white, 0.55f * alpha);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        GL11.glVertex2f(lx - 2.2f * s, ly + 1.8f * s);
        GL11.glVertex2f(lx + 0.6f * s, ly - 0.2f * s);
        GL11.glVertex2f(lx + 2f * s, ly - 2.4f * s);
        GL11.glEnd();
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(lx + 0.6f * s, ly - 0.2f * s);
        GL11.glVertex2f(lx + 2.4f * s, ly + 1.4f * s);
        GL11.glEnd();
        endLines();
        float pulse = 0.5f + 0.5f * (float) Math.sin(clock * 3f);
        float la = (projecting ? 0.95f : 0.3f + 0.25f * pulse) * lift;
        disc(x + (float) Math.cos(face + Math.PI) * 5.6f * s, y + (float) Math.sin(face + Math.PI) * 5.6f * s, 1.9f * s, 1.9f * s, projecting ? DRONE_LINE : new Color(70, 170, 200), la * alpha);
    }

    private static void circle0(float cx, float cy, float r, Color c, float a, float width) {
        startLines(width);
        rgba(c, a);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < 20; i++) {
            double ang = i * Math.PI * 2.0 / 20.0;
            GL11.glVertex2f(cx + (float) Math.cos(ang) * r, cy + (float) Math.sin(ang) * r);
        }
        GL11.glEnd();
        endLines();
    }

    // ------------------------------------------------------------------ the emblem
    /**
     * The emblem at (cx, cy) with ring radius R. (lookX, lookY) is the way the eye turns (a vector, y up; its length up to 1), open 0 to 1 is
     * the lid. Drawn as the hologram: torn by the distortion, split in colour, with static and scanlines over it.
     */
    public void hologram(float cx, float cy, float R, float alpha, float lookX, float lookY, float open) {
        float k = distortion();
        float b = burst();
        fxCy = cy;
        fxK = k;
        fxR = R;
        if (k > 0f) {
            float off = 2f + 4f * k + 6f * b;
            emblem(cx - off * 0.5f, cy, R, alpha * 0.35f * k, lookX, lookY, open, SPLIT_BLUE, false);
            emblem(cx + off * 0.5f, cy, R, alpha * 0.3f * k * (0.4f + 0.6f * b), lookX, lookY, open, WARN, false);
        }
        glitchPass = true;
        edgeId = 0;
        emblem(cx, cy, R, alpha, lookX, lookY, open, null, true);
        glitchPass = false;
        if (k > 0f) {
            overlay(cx, cy, R, alpha, k, b);
        }
    }

    private void emblem(float cx, float cy, float R, float a, float lookX, float lookY, float open, Color tint, boolean solid) {
        float lw = Math.max(1.2f, R * 0.04f);
        for (float[] r : RAYS) {
            for (int side = -1; side <= 1; side += 2) {
                if (r[8] > 0f && side < 0) {
                    continue;
                }
                double th = Math.toRadians(r[0]);
                float ux = (float) Math.sin(th) * side;
                float uy = (float) Math.cos(th);
                float px = -uy;
                float py = ux;
                float[] p = new float[8];
                float s0 = r[1] * R;
                float s1 = (r[1] + r[2]) * R;
                float w0 = r[3] * R / 2f;
                float w1 = r[4] * R / 2f;
                p[0] = cx + ux * s0 - px * w0;
                p[1] = cy + uy * s0 - py * w0;
                p[2] = cx + ux * s1 - px * w1;
                p[3] = cy + uy * s1 - py * w1;
                p[4] = cx + ux * s1 + px * w1;
                p[5] = cy + uy * s1 + py * w1;
                p[6] = cx + ux * s0 + px * w0;
                p[7] = cy + uy * s0 + py * w0;
                poly(p, 4, true, tint != null ? tint : INNER, a, lw);
            }
        }
        for (int i = 0; i < tipRay.length; i++) {
            float[] r = RAYS[tipRay[i]];
            int side = tipSide[i];
            double th = Math.toRadians(r[0]);
            float ux = (float) Math.sin(th) * side;
            float uy = (float) Math.cos(th);
            float px = -uy;
            float py = ux;
            float[] st = state(tipType[i], tipT0[i], tipDur[i], tipHold[i]);
            float len = r[6] * R;
            float mid = (r[1] + r[2] + r[5]) * R + len / 2f;
            float cr = (float) Math.cos(st[0]);
            float sr = (float) Math.sin(st[0]);
            float[][] local = {{-len / 2f, -r[7] * R / 2f}, {len / 2f, 0f}, {-len / 2f, r[7] * R / 2f}};
            float[] p = new float[6];
            for (int q = 0; q < 3; q++) {
                float lx = local[q][0] * cr - local[q][1] * sr;
                float ly = local[q][0] * sr + local[q][1] * cr;
                p[q * 2] = cx + ux * (mid + lx) + px * ly;
                p[q * 2 + 1] = cy + uy * (mid + lx) + py * ly;
            }
            poly(p, 3, true, tint != null ? tint : TIP, a * st[1], lw);
        }
        float[] cs = state(crownType, crownT0, 0.2f, 0f);
        float[] cp = new float[CROWN.length * 2];
        for (int i = 0; i < CROWN.length; i++) {
            cp[i * 2] = cx + CROWN[i][0] * R;
            cp[i * 2 + 1] = cy + CROWN[i][1] * R;
        }
        poly(cp, CROWN.length, true, tint != null ? tint : LINE, a * cs[1], Math.max(1.4f, R * 0.045f));
        poly(new float[]{cx - 0.437f * R, cy + 1.334f * R, cx + 0.437f * R, cy + 1.334f * R}, 2, false, tint != null ? tint : LINE, a * cs[1], Math.max(1f, R * 0.03f));
        // the two prongs at the sides of the crown are white
        for (int side = -1; side <= 1; side += 2) {
            poly(new float[]{cx + side * 0.437f * R, cy + 1.334f * R, cx + side * 0.657f * R, cy + 1.971f * R, cx + side * 0.171f * R, cy + 1.406f * R}, 3, true,
                    tint != null ? tint : TIP, a * cs[1], Math.max(1.4f, R * 0.045f));
        }
        if (solid) {
            disc(cx, cy, R, R, DARK, 0.85f * a);
        }
        circle(cx, cy, R, R, tint != null ? tint : LINE, a, Math.max(1.5f, R * 0.06f));
        float ew = R * 0.58f;
        float up = R * 0.34f * open;
        float dn = R * 0.26f * open;
        float[] al = new float[(2 * 12 + 1) * 2];
        int n = 0;
        for (int i = 0; i <= 12; i++) {
            float u = i / 12f;
            float x = -ew * (1 - u) * (1 - u) + ew * u * u;
            float y = 2f * (1 - u) * u * up * 2f;
            al[n * 2] = cx + x;
            al[n * 2 + 1] = cy + y;
            n++;
        }
        for (int i = 1; i < 12; i++) {
            float u = i / 12f;
            float x = ew * (1 - u) * (1 - u) - ew * u * u;
            float y = -2f * (1 - u) * u * dn * 2f;
            al[n * 2] = cx + x;
            al[n * 2 + 1] = cy + y;
            n++;
        }
        float len = (float) Math.hypot(lookX, lookY);
        float lx = len > 1f ? lookX / len : lookX;
        float ly = len > 1f ? lookY / len : lookY;
        float ir = R * 0.21f;
        float iy = ly * R * 0.1f;
        float ry = Math.max(0.02f * R, Math.min(ir, up * 0.95f - Math.abs(iy)));
        if (open > 0.25f && tint == null) {
            circle(cx + lx * R * 0.2f, cy + iy, ir, ry, EYE, a, Math.max(1.2f, R * 0.045f));
            disc(cx + lx * R * 0.2f, cy + iy, ir * 0.38f, Math.min(ir * 0.38f, ry), EYE, a);
        }
        poly(al, n, true, tint != null ? tint : LINE, a, Math.max(1.5f, R * 0.05f));
    }

    /** {rotation (radians), alpha} of a glitching spike or crown at the current time. */
    private float[] state(int type, float t0, float dur, float hold) {
        float rot = 0f;
        float al = 1f;
        if (type == 1) {
            float u = Math.min(1f, (t - t0) / dur);
            float e = u * u * (3f - 2f * u);
            float back = t > t0 + dur + hold ? Math.min(1f, (t - (t0 + dur + hold)) / dur) : 0f;
            rot = (float) Math.PI * (e - back * e);
        }
        if (type == 2) {
            al = ((int) Math.floor((t - t0) * 40f) % 2 == 0) ? 0.15f : 1f;
        }
        return new float[]{rot, al};
    }

    /** The cuts, the static and the scanlines over the emblem. */
    private void overlay(float cx, float cy, float R, float a, float k, float b) {
        prep();
        rgba(DARK, 0.9f * a);
        GL11.glBegin(GL11.GL_QUADS);
        for (float[] c : cuts) {
            float x0 = cx - c[2] / 2f + c[3];
            float y0 = cy + c[0];
            GL11.glVertex2f(x0, y0);
            GL11.glVertex2f(x0 + c[2], y0);
            GL11.glVertex2f(x0 + c[2], y0 + c[1]);
            GL11.glVertex2f(x0, y0 + c[1]);
        }
        GL11.glEnd();
        int specks = (int) (50f * k * (1f + b));
        float span = R * 3.1f;
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < specks; i++) {
            float x = cx + ((float) Math.random() - 0.5f) * span * 2f;
            float y = cy + ((float) Math.random() - 0.5f) * span * 2f;
            float w = 1f + (float) Math.floor(Math.random() * 3);
            if (Math.random() < 0.7) {
                rgba(new Color(222, 232, 244), a * (0.25f + (float) Math.random() * 0.5f));
            } else {
                rgba(MONITOR[(int) (Math.random() * MONITOR.length)], a * (0.25f + (float) Math.random() * 0.4f));
            }
            GL11.glVertex2f(x, y);
            GL11.glVertex2f(x + w, y);
            GL11.glVertex2f(x + w, y + 1f);
            GL11.glVertex2f(x, y + 1f);
        }
        GL11.glEnd();
        int streaks = (int) (3f + 8f * k * (1f + b));
        GL11.glBegin(GL11.GL_LINES);
        for (int i = 0; i < streaks; i++) {
            float y = cy + ((float) Math.random() - 0.5f) * R * 5.4f;
            float x = cx + ((float) Math.random() - 0.5f) * R * 5f;
            float len = 14f + (float) Math.random() * (40f + 60f * b);
            rgba(new Color(196, 208, 222), a * (0.12f + (float) Math.random() * 0.3f));
            GL11.glVertex2f(x - len / 2f, y);
            GL11.glVertex2f(x + len / 2f, y);
        }
        GL11.glEnd();
        // dead pixel columns: a few faint ones always, and in a burst bands of them, with blocks of colour, gaps and a crack
        float y0 = cy - R * 2.2f;
        float y1 = cy + R * 3f;
        GL11.glBegin(GL11.GL_LINES);
        for (int i = 0; i < 1 + (int) (4 * b); i++) {
            rgba(MONITOR[(int) (Math.random() * MONITOR.length)], a * (0.15f + (float) Math.random() * 0.25f));
            float x = cx + ((float) Math.random() - 0.5f) * R * 6.6f;
            GL11.glVertex2f(x, y0);
            GL11.glVertex2f(x, y1);
        }
        if (b > 0f) {
            for (float[] bd : bands) {
                for (float x = cx + bd[0] * R; x < cx + (bd[0] + bd[1]) * R; x += 2f + (float) Math.random() * 3f) {
                    rgba(MONITOR[(int) (Math.random() * MONITOR.length)], a * (0.35f + (float) Math.random() * 0.5f));
                    float ya = y0 + (float) Math.random() * R * 0.4f;
                    float yb = Math.random() < 0.5 ? y1 : ya + (y1 - y0) * (0.3f + (float) Math.random() * 0.5f);
                    GL11.glVertex2f(x, ya);
                    GL11.glVertex2f(x, yb);
                }
            }
            rgba(new Color(207, 216, 220), 0.5f * a);
            for (int i = 0; i + 1 < crack.length; i++) {
                GL11.glVertex2f(cx + crack[i][0] * R, cy + crack[i][1] * R);
                GL11.glVertex2f(cx + crack[i + 1][0] * R, cy + crack[i + 1][1] * R);
            }
        }
        GL11.glEnd();
        if (b > 0f) {
            GL11.glBegin(GL11.GL_QUADS);
            for (float[] bd : bands) {
                float bx = cx + (bd[0] + bd[2] * bd[1] * 0.5f) * R;
                float bw = bd[1] * R * (0.2f + 0.5f * bd[2]);
                float by = y0 + bd[2] * (y1 - y0 - 30f);
                rgba(bd[2] < 0.5f ? MONITOR[2] : MONITOR[1], 0.45f * a);
                GL11.glVertex2f(bx, by);
                GL11.glVertex2f(bx + bw, by);
                GL11.glVertex2f(bx + bw, by + 6f + 20f * bd[2]);
                GL11.glVertex2f(bx, by + 6f + 20f * bd[2]);
                rgba(DARK, 0.85f * a);
                float gy = y0 + (1f - bd[2]) * (y1 - y0 - 20f);
                GL11.glVertex2f(bx - 6f, gy);
                GL11.glVertex2f(bx + bd[1] * R + 6f, gy);
                GL11.glVertex2f(bx + bd[1] * R + 6f, gy + 4f + 8f * bd[2]);
                GL11.glVertex2f(bx - 6f, gy + 4f + 8f * bd[2]);
            }
            GL11.glEnd();
        }
        rgba(Color.black, 0.14f * a);
        GL11.glBegin(GL11.GL_LINES);
        for (float y = cy - R * 2.7f; y < cy + R * 3f; y += 3f) {
            GL11.glVertex2f(cx - R * 2.7f, y);
            GL11.glVertex2f(cx + R * 2.7f, y);
        }
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }
}
