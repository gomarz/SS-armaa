package data.scripts.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.util.glu.GLU;

import java.awt.Color;
import java.util.EnumSet;

/**
 * Renders the shaft descent as a true 3D cylinder mesh with perspective projection.
 * The camera moves forward along the Z axis each frame, UVs scroll naturally.
 *
 *   This is way better than the 2d approach. Feature creep for the win ?
 *   CombatEntityAPI e = Global.getCombatEngine().addLayeredRenderingPlugin(new armaa_ShaftCylinderRenderer());
 */
public class armaa_ShaftCylinderRenderer extends BaseCombatLayeredRenderingPlugin {

    // --- Tunables ---
    private static final int SEGMENTS     = 25;
    private static final int RINGS        = 30;
    private static final float RADIUS     = 1.8f;
    private static final float LENGTH     = 8.0f;
    private static final float FOV        = 95f;
    private static final float CAM_NEAR   = 0.1f;
    private static final float CAM_FAR    = 20f;
    private static final float SCROLL_SPEED = 0.2f;
    private static final float SPIN_SPEED   = 0.015f;
    private static final float TAPER       = 0.35f;
    private static final float BRIGHT_NEAR = 0.30f;
    private static final float BRIGHT_FAR  = 0.0f;

    private float camZ      = 0f;
    private float spinAngle = 0f;
    private boolean expired = false;

    private float currentSpeed = 1f;
    private float targetSpeed  = 1f;
    private static final float SPEED_EASE = 3f;

    private float wallR = 1f, wallG = 1f, wallB = 1f;

    // Cached texture ID — fetched once, avoids map lookup every frame
    private int cachedTexId = -1;

    // Precomputed trig arrays — reused every frame, only recomputed when spinAngle changes
    private final float[] cosA = new float[SEGMENTS + 1];
    private final float[] sinA = new float[SEGMENTS + 1];
    private float lastSpinAngle = Float.NaN;

    public void setSpeed(float s)     { this.targetSpeed = s; }
    public void setWallColor(Color c) {
        wallR = c.getRed()   / 255f;
        wallG = c.getGreen() / 255f;
        wallB = c.getBlue()  / 255f;
    }
    public void expire() {
        this.expired = true;
        cleanup();
    }

    @Override public boolean isExpired() { return expired; }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(CombatEngineLayers.CLOUD_LAYER);
    }

    @Override public float getRenderRadius() { return 1e10f; }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (layer != CombatEngineLayers.CLOUD_LAYER) return;

        float amount = Global.getCombatEngine().getElapsedInLastFrame();
        if (!Global.getCombatEngine().isPaused()) {
            float t = 1f - (float) Math.exp(-SPEED_EASE * amount);
            currentSpeed += (targetSpeed - currentSpeed) * t;
            camZ      += SCROLL_SPEED * currentSpeed * amount;
            spinAngle += SPIN_SPEED   * currentSpeed * amount;
        }

        // Fetch texture ID once and cache it
        if (cachedTexId < 0) {
            SpriteAPI spr = Global.getSettings().getSprite("misc", "armaa_shaft");
            cachedTexId = spr.getTextureId();
        }

        // Recompute trig only when spinAngle has changed
        if (spinAngle != lastSpinAngle) {
            lastSpinAngle = spinAngle;
            for (int seg = 0; seg <= SEGMENTS; seg++) {
                float angle = (float) seg / SEGMENTS * 2f * (float) Math.PI + spinAngle;
                cosA[seg] = (float) Math.cos(angle);
                sinA[seg] = (float) Math.sin(angle);
            }
        }

        float screenW = Global.getSettings().getScreenWidth();
        float screenH = Global.getSettings().getScreenHeight();
        float aspect  = screenW / screenH;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GLU.gluPerspective(FOV, aspect, CAM_NEAR, CAM_FAR);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        float uvOffset = (camZ % 1.0f + 1.0f) % 1.0f;

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, cachedTexId);
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendEquation(GL14.GL_FUNC_ADD);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);

        float vTile = 2f;

        for (int ring = 0; ring < RINGS; ring++) {
            float t0 = (float) ring      / RINGS;
            float t1 = (float)(ring + 1) / RINGS;
            float z0 = -t0 * LENGTH;
            float z1 = -t1 * LENGTH;

            float v0 = t0 * vTile + uvOffset;
            float v1 = t1 * vTile + uvOffset;

            float taper0 = 1.0f - t0 * TAPER;
            float taper1 = 1.0f - t1 * TAPER;

            float bright0 = BRIGHT_FAR + (1.0f - t0) * (BRIGHT_NEAR - BRIGHT_FAR);
            float bright1 = BRIGHT_FAR + (1.0f - t1) * (BRIGHT_NEAR - BRIGHT_FAR);

            // Precompute scaled brightness*color for this ring pair
            float r0 = bright0 * wallR, g0 = bright0 * wallG, b0 = bright0 * wallB;
            float r1 = bright1 * wallR, g1 = bright1 * wallG, b1 = bright1 * wallB;
            float rt0 = RADIUS * taper0;
            float rt1 = RADIUS * taper1;

            GL11.glBegin(GL11.GL_QUAD_STRIP);
            for (int seg = 0; seg <= SEGMENTS; seg++) {
                float u = (float) seg / SEGMENTS;

                GL11.glColor4f(r0, g0, b0, 1f);
                GL11.glTexCoord2f(u, v0);
                GL11.glVertex3f(cosA[seg] * rt0, sinA[seg] * rt0, z0);

                GL11.glColor4f(r1, g1, b1, 1f);
                GL11.glTexCoord2f(u, v1);
                GL11.glVertex3f(cosA[seg] * rt1, sinA[seg] * rt1, z1);
            }
            GL11.glEnd();
        }

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }
}