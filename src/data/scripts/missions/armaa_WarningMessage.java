package data.scripts.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.EnumSet;
import java.util.Random;

/**
 * Shmup-style fullscreen warning banner.
 *
 * Coordinate system: we do NOT set up a custom glOrtho. Instead we use screen
 * pixel dimensions directly so the banner is immune to zoom. LazyFont.draw() is
 * called in the same space.
 *
 * All Y values increase UPWARDs (SS convention). Band Y positions are measured
 * from the bottom of the screen.
 *
 * Usage: Global.getCombatEngine().addLayeredRenderingPlugin( new
 * armaa_WarningMessage("WARNING", "ENEMY APPROACHING. BE ON THE LOOKOUT FOR AN
 * ATTACK.", "ui_wave_incoming"));
 */
public class armaa_WarningMessage extends BaseCombatLayeredRenderingPlugin {

    // ---- Tunables ----
    // Band fractions of viewport height, measured from BOTTOM
    private static final float BAND_BOT_FRAC = 0.30f;
    private static final float BAND_H_FRAC = 0.40f;
    private static final float STRIPE_H_FRAC = 0.025f;

    private static final int NOISE_COLS = 40;
    private static final int NOISE_ROWS = 10;
    private static final float NOISE_CELL_IN = 0.08f;
    private static final float MAX_CELL_DELAY = 0.35f;

    private static final float STRIPE_SPEED = 80f;

    private static final float PULSE_DURATION = 2f;
    private static final float PULSE_PEAK = 0.5f;

    // Ghost horizontal offsets as fraction of font size
    private static final float GHOST_OFFSET_1 = 0.18f;
    private static final float GHOST_OFFSET_2 = 0.40f;
    private static final float GHOST_ALPHA_1 = 0.18f;
    private static final float GHOST_ALPHA_2 = 0.07f;

    // Chevron
    private static final float CHEV_SIZE_FRAC = 0.42f; // fraction of stripe height
    private static final float CHEV_GAP_CHARS = 1.0f;  // gap in char widths

    // Text vertical positions as fraction of viewport height from BOTTOM.
    private static final float HEADLINE_CY_FRAC = 0.52f;
    private static final float SUB_CY_FRAC = 0.46f;

    private static final float CYCLE_BANNER_IN = 0.50f;
    private static final float CYCLE_BANNER_HOLD = 1.50f;
    private static final float CYCLE_BANNER_OUT = 0.50f;
    private static final int CYCLE_COUNT = 2;

    private static final float[] COL_BAND = {0.18f, 0.02f, 0.0f};
    private static final float[] COL_STRIPE = {1.0f, 0.38f, 0.08f};

    // ---- Fields ----
    private final String headline;
    private final String subtitle;
    private final String sound;

    private float elapsed = 0f;
    private boolean expired = false;
    private boolean soundPlayed = false;
    private float stripeOffset = 0f;

    private final float[] cellDelay = new float[NOISE_COLS * NOISE_ROWS];

    private LazyFont headlineFont = null;
    private LazyFont subtitleFont = null;
    private boolean fontLoadAttempted = false;

    public armaa_WarningMessage(String headline, String subtitle, String sound) {
        this.headline = headline != null ? headline.toUpperCase() : "";
        this.subtitle = subtitle != null ? subtitle.toUpperCase() : null;
        this.sound = sound;
        Random rng = new Random();
        for (int i = 0; i < cellDelay.length; i++) {
            cellDelay[i] = rng.nextFloat() * MAX_CELL_DELAY;
        }
    }

    @Override
    public boolean isExpired() {
        return expired;
    }

    @Override
    public float getRenderRadius() {
        return Float.MAX_VALUE;
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(CombatEngineLayers.JUST_BELOW_WIDGETS);
    }

    @Override
    public void advance(float amount) {
        if (Global.getCombatEngine().isPaused()) {
            return;
        }
        elapsed += amount;
        stripeOffset += STRIPE_SPEED * amount;
        if (!soundPlayed && sound != null) {
            soundPlayed = true;
            ShipAPI player = Global.getCombatEngine().getPlayerShip();
            Vector2f loc = player != null ? player.getLocation() : new Vector2f();
            Global.getSoundPlayer().playSound(sound, 1f, 1f, loc, new Vector2f());
        }
        if (elapsed >= (CYCLE_BANNER_IN + CYCLE_BANNER_HOLD + CYCLE_BANNER_OUT) * CYCLE_COUNT) {
            expired = true;
            Global.getLogger(armaa_WarningMessage.class).info("armaa_WarningMessage expired after " + elapsed + "s");
            cleanup();
        }
    }

    private void ensureFonts() {
        if (fontLoadAttempted) {
            return;
        }
        fontLoadAttempted = true;
        try {
            headlineFont = LazyFont.loadFont("graphics/armaa/fonts/orbitron96aa.fnt");
        } catch (Exception e) {
            Global.getLogger(armaa_WarningMessage.class).error("Failed to load headline font", e);
        }
        try {
            subtitleFont = LazyFont.loadFont("graphics/fonts/orbitron24aabold.fnt");
        } catch (Exception e) {
            Global.getLogger(armaa_WarningMessage.class).error("Failed to load subtitle font", e);
        }
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (layer != CombatEngineLayers.JUST_BELOW_WIDGETS) {
            return;
        }
        if (expired) {
            return;
        }
        ensureFonts();

        // Use screen pixel dimensions — not viewport world coords — so the banner
        // stays the same size regardless of zoom level.
        float vx = 0f;
        float vy = 0f;
        float vw = Global.getSettings().getScreenWidth();
        float vh = Global.getSettings().getScreenHeight();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, Global.getSettings().getScreenWidth(),
                0, Global.getSettings().getScreenHeight(), -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendEquation(GL14.GL_FUNC_ADD);

        float cycleDur = CYCLE_BANNER_IN + CYCLE_BANNER_HOLD + CYCLE_BANNER_OUT;

        for (int c = 0; c < CYCLE_COUNT; c++) {
            float cycleT = elapsed - c * cycleDur;

            float pulseT = cycleT / PULSE_DURATION;
            if (pulseT > 0f && pulseT < 1f) {
                drawPulse(vx, vy, vw, vh, pulseT);
            }

            float ba;
            if (cycleT < 0f) {
                ba = 0f;
            } else if (cycleT < CYCLE_BANNER_IN) {
                ba = easeOut(cycleT / CYCLE_BANNER_IN);
            } else if (cycleT < CYCLE_BANNER_IN + CYCLE_BANNER_HOLD) {
                ba = 1f;
            } else if (cycleT < cycleDur) {
                ba = 1f - easeOut((cycleT - CYCLE_BANNER_IN - CYCLE_BANNER_HOLD) / CYCLE_BANNER_OUT);
            } else {
                ba = 0f;
            }
            ba = Math.max(0f, Math.min(1f, ba));
            if (ba <= 0f) {
                continue;
            }

            renderBackground(vx, vy, vw, vh, ba);

            float fontSize = 96f;
            float ghostOff1 = fontSize * GHOST_OFFSET_1;
            float ghostOff2 = fontSize * GHOST_OFFSET_2;

            renderText(vx, vy, vw, vh, -ghostOff2, ba * GHOST_ALPHA_2);
            renderText(vx, vy, vw, vh, ghostOff2, ba * GHOST_ALPHA_2);
            renderText(vx, vy, vw, vh, -ghostOff1, ba * GHOST_ALPHA_1);
            renderText(vx, vy, vw, vh, ghostOff1, ba * GHOST_ALPHA_1);
            renderText(vx, vy, vw, vh, 0f, ba);
        }

        GL11.glPopAttrib();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
    }

    private void renderBackground(float vx, float vy, float vw, float vh, float alpha) {
        float bandBot = vy + vh * BAND_BOT_FRAC;
        float bandH = vh * BAND_H_FRAC;
        float bandTop = bandBot + bandH;
        float stripeH = vh * STRIPE_H_FRAC;
        float innerBot = bandBot + stripeH;
        float innerTop = bandTop - stripeH;
        float innerH = innerTop - innerBot;

        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(COL_BAND[0], COL_BAND[1], COL_BAND[2], alpha * 0.65f);
        fillRect(vx, bandBot, vw, bandH);

        // Noise dissolve
        float cellW = vw / NOISE_COLS, cellH = innerH / NOISE_ROWS;
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        for (int i = 0; i < cellDelay.length; i++) {
            float ca = noiseCellAlpha(elapsed, cellDelay[i], alpha);
            if (ca <= 0f) {
                continue;
            }
            int col = i % NOISE_COLS, row = i / NOISE_COLS;
            float grain = 0.25f + (((col * 7 + row * 13 + (int) (elapsed * 30)) % 10) / 10f) * 0.25f;
            GL11.glColor4f(COL_STRIPE[0], COL_STRIPE[1] * 0.4f, 0f, ca * grain * 0.4f);
            fillRect(vx + col * cellW, innerBot + row * cellH, cellW + 1, cellH + 1);
        }

        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        drawStripes(vx, vw, bandBot, stripeH, stripeOffset, alpha);
        drawStripes(vx, vw, bandTop - stripeH, stripeH, -stripeOffset, alpha);

        GL11.glColor4f(COL_STRIPE[0], COL_STRIPE[1], COL_STRIPE[2], alpha * 0.8f);
        GL11.glLineWidth(1.2f);
        hline(vx, vx + vw, innerBot);
        hline(vx, vx + vw, innerTop);

        // Vignette
        float vigH = innerH * 0.22f;
        float vigA = alpha * (0.5f + 0.1f * (float) Math.sin(elapsed * 3.5f));
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(0, 0, 0, vigA);
        GL11.glVertex2f(vx, innerBot);
        GL11.glColor4f(0, 0, 0, vigA);
        GL11.glVertex2f(vx + vw, innerBot);
        GL11.glColor4f(0, 0, 0, 0);
        GL11.glVertex2f(vx + vw, innerBot + vigH);
        GL11.glColor4f(0, 0, 0, 0);
        GL11.glVertex2f(vx, innerBot + vigH);
        GL11.glEnd();
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(0, 0, 0, 0);
        GL11.glVertex2f(vx, innerTop - vigH);
        GL11.glColor4f(0, 0, 0, 0);
        GL11.glVertex2f(vx + vw, innerTop - vigH);
        GL11.glColor4f(0, 0, 0, vigA);
        GL11.glVertex2f(vx + vw, innerTop);
        GL11.glColor4f(0, 0, 0, vigA);
        GL11.glVertex2f(vx, innerTop);
        GL11.glEnd();

        // Scanlines
        GL11.glColor4f(0, 0, 0, alpha * 0.13f);
        GL11.glBegin(GL11.GL_QUADS);
        for (float ly = innerBot; ly < innerTop; ly += 4f) {
            GL11.glVertex2f(vx, ly);
            GL11.glVertex2f(vx + vw, ly);
            GL11.glVertex2f(vx + vw, ly + 2f);
            GL11.glVertex2f(vx, ly + 2f);
        }
        GL11.glEnd();
    }

    private void renderText(float vx, float vy, float vw, float vh, float ox, float alpha) {
        if (alpha <= 0f) {
            return;
        }

        float stripeH = vh * STRIPE_H_FRAC;
        float chevSize = stripeH * CHEV_SIZE_FRAC;
        float fontSize = 96f;
        float subFontSize = 24f;

        float textY = vy + vh * HEADLINE_CY_FRAC;
        float subY = vy + vh * SUB_CY_FRAC;

        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        float headlineW = 0f;
        float headlineH = 0f;

        if (headlineFont != null) {
            LazyFont.DrawableString measure = headlineFont.createText(headline, Color.WHITE, fontSize);
            headlineW = measure.getWidth();
            headlineH = measure.getHeight();
            float tx = vx + vw * 0.5f - headlineW * 0.5f + ox;

            headlineFont.createText(headline, new Color(1f, 0.13f, 0f, alpha * 0.25f), fontSize).draw(tx, textY);
            headlineFont.createText(headline, new Color(1f, 0.38f, 0.08f, alpha), fontSize).draw(tx, textY);
            headlineFont.createText(headline, new Color(1f, 0.80f, 0.50f, alpha * 0.55f), fontSize).draw(tx, textY);
        }

        if (subtitle != null && subtitleFont != null) {
            LazyFont.DrawableString subStr = subtitleFont.createText(
                    subtitle, new Color(1f, 0.38f, 0.08f, alpha * 0.75f), subFontSize);
            float sx = vx + vw * 0.5f - subStr.getWidth() * 0.5f + ox;
            subStr.draw(sx, subY);
        }

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        if (headlineW <= 0f) {
            headlineW = fontSize * 5.5f;
        }
        if (headlineH <= 0f) {
            headlineH = fontSize;
        }
        float charW = headlineW / Math.max(1, headline.length());
        float chevGap = charW * CHEV_GAP_CHARS;
        float chevCY = textY - headlineH * 0.4f;
        float warnLeft = vx + vw * 0.5f - headlineW * 0.5f + ox;
        float warnRight = vx + vw * 0.5f + headlineW * 0.5f + ox;

        drawChevron(warnLeft - chevGap, chevCY, chevSize, -1, COL_STRIPE, alpha * 0.95f);
        drawChevron(warnLeft - chevGap - chevSize, chevCY, chevSize, -1, COL_STRIPE, alpha * 0.30f);
        drawChevron(warnRight + chevGap, chevCY, chevSize, 1, COL_STRIPE, alpha * 0.95f);
        drawChevron(warnRight + chevGap + chevSize, chevCY, chevSize, 1, COL_STRIPE, alpha * 0.30f);
    }

    private void drawPulse(float vx, float vy, float vw, float vh, float pPhase) {
        float alpha;
        if (pPhase < PULSE_PEAK) {
            alpha = pPhase / PULSE_PEAK;
        } else {
            float t = (pPhase - PULSE_PEAK) / (1f - PULSE_PEAK);
            alpha = (float) Math.pow(1f - t, 2.5f);
        }
        alpha = Math.max(0f, alpha);
        float diag = (float) Math.sqrt(vw * vw + vh * vh);
        float r = pPhase * diag * 1.1f;
        float cx = vx + vw * 0.5f;
        float cy = vy + vh * 0.5f;
        int steps = 32;

        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glColor4f(1f, 0.18f, 0f, alpha * 0.85f);
        GL11.glVertex2f(cx, cy);
        GL11.glColor4f(0.5f, 0.03f, 0f, 0f);
        for (int i = 0; i <= steps; i++) {
            double a = Math.PI * 2.0 * i / steps;
            GL11.glVertex2f(cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r);
        }
        GL11.glEnd();
        float haloR = r * 0.55f + diag * 0.1f;
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glColor4f(0.8f, 0.06f, 0f, alpha * 0.35f);
        GL11.glVertex2f(cx, cy);
        GL11.glColor4f(0.2f, 0.01f, 0f, 0f);
        for (int i = 0; i <= steps; i++) {
            double a = Math.PI * 2.0 * i / steps;
            GL11.glVertex2f(cx + (float) Math.cos(a) * haloR, cy + (float) Math.sin(a) * haloR);
        }
        GL11.glEnd();
    }

    private void drawStripes(float vx, float vw, float y, float h, float offset, float alpha) {
        float stripeW = h * 0.55f, shear = h * 0.55f, period = stripeW * 3f;
        float off = ((offset % period) + period) % period;
        GL11.glColor4f(COL_STRIPE[0], COL_STRIPE[1], COL_STRIPE[2], alpha * 0.92f);
        for (float x = -period + off; x < vw + period; x += period) {
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2f(vx + x, y);
            GL11.glVertex2f(vx + x + stripeW, y);
            GL11.glVertex2f(vx + x + stripeW - shear, y + h);
            GL11.glVertex2f(vx + x - shear, y + h);
            GL11.glEnd();
        }
    }

    private void drawChevron(float cx, float cy, float size, float dir, float[] col, float alpha) {
        GL11.glColor4f(col[0], col[1], col[2], alpha);
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2f(cx + dir * size * 0.7f, cy);
        GL11.glVertex2f(cx - dir * size * 0.3f, cy - size);
        GL11.glVertex2f(cx - dir * size * 0.3f, cy + size);
        GL11.glEnd();
    }

    private void fillRect(float x, float y, float w, float h) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + w, y);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x, y + h);
        GL11.glEnd();
    }

    private void hline(float x1, float x2, float y) {
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x1, y);
        GL11.glVertex2f(x2, y);
        GL11.glEnd();
    }

    private float noiseCellAlpha(float phase, float delay, float globalAlpha) {
        float localT = phase - delay;
        if (localT <= 0f) {
            return 0f;
        }
        float ca = Math.min(1f, localT / NOISE_CELL_IN);
        if (localT < 0.25f && Math.random() < 0.12f) {
            ca *= Math.random();
        }
        return ca * globalAlpha;
    }

    private static float easeOut(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }
}
