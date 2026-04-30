package data.scripts.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.util.EnumSet;

/**
 * Fullscreen atmospheric reentry heat effect.
 *
 * Renders a vignette + color overlay suggesting heat buildup.
 * No shader required — pure GL blending.
 *
 * Usage:
 *   armaa_ReentryEffect fx = new armaa_ReentryEffect();
 *   Global.getCombatEngine().addLayeredRenderingPlugin(fx);
 *   // later, to begin fading out:
 *   fx.beginFadeOut();
 */
public class armaa_ReentryEffect extends BaseCombatLayeredRenderingPlugin {

    // --- Tunables ---
    private static final float RAMP_IN_DURATION  = 30.0f;  // seconds to reach full intensity
    private static final float FADE_OUT_DURATION = 20.0f;  // seconds to fade out once triggered

    // Edge  deep red/orange ring around screen
    private static final float VIGNETTE_R = 0.85f;
    private static final float VIGNETTE_G = 0.18f;
    private static final float VIGNETTE_B = 0.02f;
    private static final float VIGNETTE_ALPHA_MAX = 0.5f; // peak alpha at screen edges

    // Center glow  softer orange tint across the whole screen
    private static final float GLOW_R = 0.70f;
    private static final float GLOW_G = 0.12f;
    private static final float GLOW_B = 0.0f;
    private static final float GLOW_ALPHA_MAX = 0.30f; // subtle center tint

    // Flicker  very subtle brightness variation to sell heat
    private static final float FLICKER_SPEED     = 7.5f;
    private static final float FLICKER_MAGNITUDE = 0.04f; // fraction of vignette alpha

    // Streak lines bright horizontal streaks at screen edges suggesting plasma
    private static final int   STREAK_COUNT      = 6;
    private static final float STREAK_ALPHA_MAX  = 0.35f;
    private static final float STREAK_HEIGHT     = 0.015f; // fraction of screen height

    // Static interference horizontal bands and noise simulating EM interference
    private static final float STATIC_ALPHA_MAX   = 0.12f; // max alpha of interference bands
    private static final int   STATIC_BAND_COUNT  = 8;     // number of scanline bands
    private static final float STATIC_FLICKER_CHANCE = 0.015f; // chance of a full screen flicker per frame
    private static final float STATIC_GLITCH_CHANCE  = 0.04f;  // chance of a glitch band per frame
    private static final int   STATIC_NOISE_DOTS  = 40;    // random bright noise dots per frame

    // ---- State ----
    private float   elapsed    = 0f;
    private boolean fadingOut  = false;
    private float   fadeOutT   = 0f;
    private boolean expired    = false;

    @Override public boolean isExpired()       { return expired; }
    @Override public float   getRenderRadius() { return Float.MAX_VALUE; }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(CombatEngineLayers.JUST_BELOW_WIDGETS);
    }

    /** Call this to begin fading the overlay out. */
    public void beginFadeOut() {
        fadingOut = true;
        fadeOutT  = 0f;
    }

    @Override
    public void advance(float amount) {
        // Time tracking handled in render() to guarantee it runs
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (layer != CombatEngineLayers.JUST_BELOW_WIDGETS) return;
        if (expired) return;

        // Track time
        if (!Global.getCombatEngine().isPaused()) {
            float amount = Global.getCombatEngine().getElapsedInLastFrame();
            elapsed += amount;
            if (fadingOut) {
                fadeOutT += amount;
                if (fadeOutT >= FADE_OUT_DURATION) {
                    expired = true;
                    cleanup();
                    return;
                }
            }
        }

        float sw = Global.getSettings().getScreenWidth();
        float sh = Global.getSettings().getScreenHeight();

        // Master intensity
        float intensity;
        if (!fadingOut) {
            intensity = Math.min(1f, elapsed / RAMP_IN_DURATION);
        } else {
            float holdIntensity = Math.min(1f, (elapsed - fadeOutT) / RAMP_IN_DURATION);
            intensity = holdIntensity * (1f - easeOut(fadeOutT / FADE_OUT_DURATION));
        }
        if (intensity <= 0f) return;

        // Subtle flicker
        float flicker = 1f + FLICKER_MAGNITUDE * (float) Math.sin(elapsed * FLICKER_SPEED)
                            + FLICKER_MAGNITUDE * 0.5f * (float) Math.sin(elapsed * FLICKER_SPEED * 2.3f);

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, sw, 0, sh, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendEquation(GL14.GL_FUNC_ADD);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);

        // --- Center glow flat additive tint ---
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        float glowA = GLOW_ALPHA_MAX * intensity * flicker;
        GL11.glColor4f(GLOW_R, GLOW_G, GLOW_B, glowA);
        fillRect(0, 0, sw, sh);

        // --- Edge vignette gradient quads on each side ---
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        float va = VIGNETTE_ALPHA_MAX * intensity * flicker;
        float vigW = sw * 0.25f;
        float vigH = sh * 0.35f;

        vignetteBottom(0, 0,        sw, vigH, va); // bottom edge fades upward
        vignetteTop   (0, sh - vigH, sw, vigH, va); // top edge fades downward
        vignetteLeft  (0, 0,        vigW, sh, va); // left edge fades rightward
        vignetteRight (sw - vigW, 0, vigW, sh, va); // right edge fades leftward

        // --- Plasma streaks along top and bottom ---
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        float streakH = sh * STREAK_HEIGHT;
        float streakA = STREAK_ALPHA_MAX * intensity * flicker;

        // Streaks are at fixed seed positions, flickering independently
        for (int i = 0; i < STREAK_COUNT; i++) {
            float seed  = i * 1.37f + 0.5f;
            float xFrac = (seed % 1.0f);
            float xPos  = xFrac * sw;
            float width = sw * (0.05f + (((seed * 7.3f) % 1f) * 0.15f));
            float aFlic = streakA * (0.6f + 0.4f * (float) Math.sin(elapsed * (3f + seed * 2f)));

            // Bottom streak
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glColor4f(1f, 0.4f, 0.05f, aFlic);
            GL11.glVertex2f(xPos,         0f);
            GL11.glVertex2f(xPos + width, 0f);
            GL11.glColor4f(VIGNETTE_R, VIGNETTE_G, VIGNETTE_B, 0f);
            GL11.glVertex2f(xPos + width, streakH);
            GL11.glVertex2f(xPos,         streakH);
            GL11.glEnd();

            // Top streak (mirrored)
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glColor4f(0f, 0f, 0f, 0f);
            GL11.glVertex2f(xPos,         sh - streakH);
            GL11.glVertex2f(xPos + width, sh - streakH);
            GL11.glColor4f(1f, 0.4f, 0.05f, aFlic);
            GL11.glVertex2f(xPos + width, sh);
            GL11.glVertex2f(xPos,         sh);
            GL11.glEnd();
        }

        // --- Static interference ---
        renderStatic(sw, sh, intensity);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    private void renderStatic(float sw, float sh, float intensity) {
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // --- Horizontal scanline bands always present at low alpha ---
        float bandAlpha = STATIC_ALPHA_MAX * intensity;
        for (int i = 0; i < STATIC_BAND_COUNT; i++) {
            // Each band has a fixed Y position that drifts slowly over time
            float drift = (elapsed * (0.4f + i * 0.07f)) % 1.0f;
            float yFrac = ((i / (float) STATIC_BAND_COUNT) + drift) % 1.0f;
            float y     = yFrac * sh;
            float h     = 1.5f + (i % 3); // 1.5 to 4.5px thick
            float a     = bandAlpha * (0.4f + 0.6f * (float) Math.sin(elapsed * (2f + i * 0.5f)));
            GL11.glColor4f(0.7f, 0.8f, 1.0f, a); // slight blue tint like CRT
            fillRect(0, y, sw, h);
        }

        // --- Random glitch band occasional horizontal slice that shifts ---
        if (Math.random() < STATIC_GLITCH_CHANCE * intensity) {
            float y = (float) Math.random() * sh;
            float h = (float) Math.random() * sh * 0.04f + 2f;
            float a = (float) Math.random() * 0.18f * intensity;
            // Alternate between darkening and brightening glitch
            if (Math.random() < 0.5f) {
                GL11.glColor4f(0f, 0f, 0f, a * 2f);  // dark glitch
            } else {
                GL11.glColor4f(0.9f, 0.85f, 1.0f, a); // bright glitch
            }
            fillRect(0, y, sw, h);
        }

        // --- Random noise dots --- additive bright specks
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        int dotCount = (int)(STATIC_NOISE_DOTS * intensity);
        for (int i = 0; i < dotCount; i++) {
            float x = (float) Math.random() * sw;
            float y = (float) Math.random() * sh;
            float s = (float) Math.random() * 2f + 0.5f; // 0.5 to 2.5px
            float a = (float) Math.random() * 0.5f * intensity;
            // Mostly white with occasional orange tint matching reentry palette
            if (Math.random() < 0.3f) {
                GL11.glColor4f(1f, 0.5f, 0.1f, a); // orange spark
            } else {
                GL11.glColor4f(0.9f, 0.9f, 1.0f, a); // white noise
            }
            fillRect(x, y, s, s);
        }

        // --- Full screen flicker rare, brief brightness spike ---
        if (Math.random() < STATIC_FLICKER_CHANCE * intensity) {
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            float a = (float) Math.random() * 0.07f * intensity;
            GL11.glColor4f(0.9f, 0.85f, 0.8f, a);
            fillRect(0, 0, sw, sh);
        }
    }

    // Gradient from opaque at y to transparent at y+h
    private void vignetteBottom(float x, float y, float w, float h, float alpha) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(VIGNETTE_R, VIGNETTE_G, VIGNETTE_B, alpha);
        GL11.glVertex2f(x,     y);
        GL11.glVertex2f(x + w, y);
        GL11.glColor4f(VIGNETTE_R, VIGNETTE_G, VIGNETTE_B, 0f);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x,     y + h);
        GL11.glEnd();
    }

    // Gradient from transparent at y to opaque at y+h
    private void vignetteTop(float x, float y, float w, float h, float alpha) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(VIGNETTE_R, VIGNETTE_G, VIGNETTE_B, 0f);
        GL11.glVertex2f(x,     y);
        GL11.glVertex2f(x + w, y);
        GL11.glColor4f(VIGNETTE_R, VIGNETTE_G, VIGNETTE_B, alpha);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x,     y + h);
        GL11.glEnd();
    }

    // Gradient from opaque at x to transparent at x+w
    private void vignetteLeft(float x, float y, float w, float h, float alpha) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(VIGNETTE_R, VIGNETTE_G, VIGNETTE_B, alpha);
        GL11.glVertex2f(x,     y);
        GL11.glColor4f(VIGNETTE_R, VIGNETTE_G, VIGNETTE_B, 0f);
        GL11.glVertex2f(x + w, y);
        GL11.glVertex2f(x + w, y + h);
        GL11.glColor4f(VIGNETTE_R, VIGNETTE_G, VIGNETTE_B, alpha);
        GL11.glVertex2f(x,     y + h);
        GL11.glEnd();
    }

    // Gradient from transparent at x to opaque at x+w
    private void vignetteRight(float x, float y, float w, float h, float alpha) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(VIGNETTE_R, VIGNETTE_G, VIGNETTE_B, 0f);
        GL11.glVertex2f(x,     y);
        GL11.glColor4f(VIGNETTE_R, VIGNETTE_G, VIGNETTE_B, alpha);
        GL11.glVertex2f(x + w, y);
        GL11.glVertex2f(x + w, y + h);
        GL11.glColor4f(VIGNETTE_R, VIGNETTE_G, VIGNETTE_B, 0f);
        GL11.glVertex2f(x,     y + h);
        GL11.glEnd();
    }

    private void fillRect(float x, float y, float w, float h) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x,     y);
        GL11.glVertex2f(x + w, y);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x,     y + h);
        GL11.glEnd();
    }

    private static float easeOut(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }
}