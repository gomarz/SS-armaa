package data.scripts.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.EnumSet;
import java.awt.Color;

/**
 * Renders destroyed ship hulks with animated color/depth effects.
 *
 * Falling ships darken over time, suggesting they're receding into the shaft below.
 * Ascending ships brighten briefly then fade, suggesting they're rising past the camera.
 * A secondary explosion fires at the peak brightness moment on ascent.
 *
 * Usage: create once, keep a reference, call addHulk() when a ship dies.
 *   armaa_HulkRenderer hulkRenderer = new armaa_HulkRenderer();
 *   Global.getCombatEngine().addLayeredRenderingPlugin(hulkRenderer);
 *   hulkRenderer.addHulk(ship, true); // true = ascending, false = falling
 */
public class armaa_HulkRenderer extends BaseCombatLayeredRenderingPlugin {

    // --- Tunables ---
    private static final float FALL_DURATION     = 7.0f;  // total seconds for falling hulk
    private static final float ASCEND_DURATION   = 5.0f;  // total seconds for ascending hulk

    // Falling: start color → end color (gets darker, more red-shifted)
    private static final float FALL_START_R = 0.314f, FALL_START_G = 0.196f, FALL_START_B = 0.196f; // (80,50,50)
    private static final float FALL_END_R   = 0.1f,  FALL_END_G   = 0.05f,  FALL_END_B   = 0.05f;  // near black

    // Ascending: start → peak → end
    private static final float ASCEND_START_R = 0.314f, ASCEND_START_G = 0.196f, ASCEND_START_B = 0.196f; // (80,50,50)
    private static final float ASCEND_PEAK_R  = 0.70f,  ASCEND_PEAK_G  = 0.51f,  ASCEND_PEAK_B  = 0.39f;  // warm bright cap
// warm bright cap
    private static final float ASCEND_PEAK_T  = 0.4f;
    // when to fire secondary explosion (just before peak)
     // fraction of duration where brightness peaks

    private final List<HulkEntry> hulks = new ArrayList<>();
    private boolean expired = false;

    @Override public boolean isExpired() { return expired; }
    @Override public float getRenderRadius() { return Float.MAX_VALUE; }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(CombatEngineLayers.BELOW_SHIPS_LAYER, CombatEngineLayers.ABOVE_SHIPS_LAYER);
    }

    public void expire() {
        expired = true;
        cleanup();
    }

    /**
     * Register a hulk for animated rendering.
     * Call this immediately before removing the ship from the engine.
     *
     * @param ship       the ship that just died
     * @param ascending  true = ship rises and brightens, false = falls and darkens
     */
    public void addHulk(com.fs.starfarer.api.combat.ShipAPI ship, boolean ascending) {
        HulkEntry e = new HulkEntry();
        e.spriteName  = ship.getHullSpec().getSpriteName();
        e.x           = ship.getLocation().x;
        e.y           = ship.getLocation().y;
        e.vx          = ship.getVelocity().x + (float)(Math.random() - 0.5f) * 50f;
        e.vy          = ship.getVelocity().y + (ascending ? 80f : -80f)
                        + (float)(Math.random() - 0.5f) * 50f;
        e.facing      = ship.getFacing() - 90f;
        e.angularVel  = (float)(Math.random() - 0.5f) * 25f; // random spin matching original
        e.ascending   = ascending;
        e.duration    = ascending ? ASCEND_DURATION : FALL_DURATION;
        e.elapsed     = 0f;
        e.flashFired  = false;

        SpriteAPI spr = Global.getSettings().getSprite(e.spriteName);
        e.width  = spr.getWidth();
        e.height = spr.getHeight();

        // Store actual atlas UV coords from the sprite

        // Scale end values matching original MagicRender sizeChange params exactly:
        // falling: -width/8 total change → end = width - width/8 = width * 0.875
        // ascending: +width/6 total change → end = width + width/6 = width * 1.167
        // Falling shrinks (receding), ascending grows (approaching)
        e.scaleEndW = ascending ? e.width  * 2.2f : e.width  * 0.2f;
        e.scaleEndH = ascending ? e.height * 2.2f : e.height * 0.2f;

        hulks.add(e);
    }

    @Override
    public void advance(float amount) {
        if (Global.getCombatEngine().isPaused()) return;

        Iterator<HulkEntry> it = hulks.iterator();
        while (it.hasNext()) {
            HulkEntry e = it.next();
            e.elapsed += amount;

            // Update position first so explosion fires at current position
            e.x += e.vx * amount;
            e.y += e.vy * amount;
            e.facing += e.angularVel * amount;

            float t = Math.min(1f, e.elapsed / e.duration);

            if (!e.flashFired && t >= 0.92f) {
                e.flashFired = true;
                if (e.ascending) {
                    // Ascending: bright warm final explosion as ship recedes upward
                    Global.getCombatEngine().spawnExplosion(
                            new Vector2f(e.x, e.y),
                            new Vector2f(e.vx * 0.2f, e.vy * 0.2f),
                            new Color(1.0f, 0.55f, 0.25f, 1.0f),
                            Math.max(e.width, e.height) * 2f,
                            0.6f
                    );
                    Global.getCombatEngine().addHitParticle(
                            new Vector2f(e.x, e.y),
                            new Vector2f(e.vx * 0.2f, e.vy * 0.2f),
                            Math.max(e.width, e.height)*2f,                            
                            0.8f,
                            1f,
                            new Color(1f,0.8f,0.8f,1f)
                    );     
                } else {
                    Global.getCombatEngine().spawnExplosion(
                            new Vector2f(e.x, e.y),
                            new Vector2f(e.vx * 0.2f, e.vy * 0.2f),
                            Color.black,
                            Math.max(e.width, e.height) * 2f,
                            0.1f
                    );                        
                    Global.getCombatEngine().addHitParticle(
                            new Vector2f(e.x, e.y),
                            new Vector2f(e.vx * 0.2f, e.vy * 0.2f),
                            Math.max(e.width, e.height),                            
                            0.1f,
                            1f,
                            new Color(0.7f,0.5f,0.5f,1f)
                    );                    
                    // Falling: dark reddish final explosion as ship hits the shaft below
                    Global.getCombatEngine().spawnExplosion(
                            new Vector2f(e.x, e.y),
                            new Vector2f(e.vx * 0.1f, e.vy * 0.1f),
                            new Color(0.5f, 0.12f, 0.05f, 0.9f),
                            Math.max(e.width, e.height) * 0.7f,
                            0.5f
                    );
                }
            }

            if (t >= 1f) it.remove();
        }
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (hulks.isEmpty()) return;

        org.lwjgl.opengl.GL11.glPushAttrib(org.lwjgl.opengl.GL11.GL_ENABLE_BIT | org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT | org.lwjgl.opengl.GL11.GL_TEXTURE_BIT);
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
        org.lwjgl.opengl.GL14.glBlendEquation(org.lwjgl.opengl.GL14.GL_FUNC_ADD);
        org.lwjgl.opengl.GL11.glBlendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);

        for (HulkEntry e : hulks) {
            boolean wantBelow = !e.ascending;
            boolean wantAbove =  e.ascending;
            if (wantBelow && layer != CombatEngineLayers.BELOW_SHIPS_LAYER) continue;
            if (wantAbove && layer != CombatEngineLayers.ABOVE_SHIPS_LAYER) continue;

            float t = Math.min(1f, e.elapsed / e.duration);
            float r, g, b, alpha;

            if (!e.ascending) {
                r = FALL_START_R + (FALL_END_R - FALL_START_R) * t;
                g = FALL_START_G + (FALL_END_G - FALL_START_G) * t;
                b = FALL_START_B + (FALL_END_B - FALL_START_B) * t;
                alpha = t < 0.90f ? 1f : 1f - (t - 0.90f) / 0.10f;
            } else {
                if (t < ASCEND_PEAK_T) {
                    float pt = t / ASCEND_PEAK_T;
                    r = ASCEND_START_R + (ASCEND_PEAK_R - ASCEND_START_R) * pt;
                    g = ASCEND_START_G + (ASCEND_PEAK_G - ASCEND_START_G) * pt;
                    b = ASCEND_START_B + (ASCEND_PEAK_B - ASCEND_START_B) * pt;
                } else {
                    float pt = (t - ASCEND_PEAK_T) / (1f - ASCEND_PEAK_T);
                    r = ASCEND_PEAK_R * (1f - pt);
                    g = ASCEND_PEAK_G * (1f - pt);
                    b = ASCEND_PEAK_B * (1f - pt);
                }
                alpha = t < 0.90f ? 1f : 1f - (t - 0.90f) / 0.10f;
            }

            float curW = e.width  + (e.scaleEndW - e.width)  * t;
            float curH = e.height + (e.scaleEndH - e.height) * t;
            float hw = curW * 0.5f;
            float hh = curH * 0.5f;

            float rad = (float) Math.toRadians(e.facing);
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);

            SpriteAPI spr = Global.getSettings().getSprite(e.spriteName);
            org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, spr.getTextureId());

            // getTextureWidth/Height return the UV fraction [0..1] of the atlas used by this sprite
            float tw = spr.getTextureWidth();
            float th = spr.getTextureHeight();

            org.lwjgl.opengl.GL11.glColor4f(
                Math.max(0f, Math.min(1f, r)),
                Math.max(0f, Math.min(1f, g)),
                Math.max(0f, Math.min(1f, b)),
                Math.max(0f, Math.min(1f, alpha))
            );

            // Local corners: bl, br, tr, tl
            float[] lx = { -hw,  hw,  hw, -hw };
            float[] ly = { -hh, -hh,  hh,  hh };
            float[] us = {  0f,  tw,  tw,  0f };
            float[] vs = {  0f,  0f,  th,  th };

            org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_QUADS);
            for (int i = 0; i < 4; i++) {
                float wx = e.x + lx[i] * cos - ly[i] * sin;
                float wy = e.y + lx[i] * sin + ly[i] * cos;
                org.lwjgl.opengl.GL11.glTexCoord2f(us[i], vs[i]);
                org.lwjgl.opengl.GL11.glVertex2f(wx, wy);
            }
            org.lwjgl.opengl.GL11.glEnd();
        }

        org.lwjgl.opengl.GL11.glPopAttrib();
    }

    // --- Internal hulk state ---
    private static class HulkEntry {
        String spriteName;
        float x, y, vx, vy;
        float facing, angularVel;
        float width, height;
        float scaleEndW, scaleEndH; // target size at end of animation
        float elapsed, duration;
        boolean ascending;
        boolean flashFired;
    }
}