package data.scripts.campaign.missions.plugins;

import java.awt.Color;
import java.io.IOException;
import java.util.EnumSet;

import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.util.glu.GLU;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * A background image on the inside of a sphere, seen from its centre, with the
 * camera tumbling about like a fighter in a dogfight. Built the same way as
 * armaa_ShaftCylinderRenderer: a real mesh under a perspective projection,
 * drawn on the cloud layer beneath the ships.
 *
 * The image is read as an equirectangular map (a plain 2:1 panorama). The top
 * and bottom edges pinch towards the poles, so keep anything important away
 * from them. The camera's yaw, pitch and roll are sums of sines at unrelated
 * frequencies, so the motion is irregular rather than a steady spin.
 */
public class armaa_SkySphereRenderer extends BaseCombatLayeredRenderingPlugin {

    // --- Tunables ---
    private static final int LON_SEGMENTS = 48;
    private static final int LAT_BANDS = 24;
    private static final float RADIUS = 5f;
    private static final float FOV = 90f;
    private static final float CAM_NEAR = 0.1f;
    private static final float CAM_FAR = 20f;
    /** Degrees each axis swings, multiplied by intensity. */
    private static final float YAW_AMP = 45f;
    private static final float PITCH_AMP = 22f;
    private static final float ROLL_AMP = 35f;
    /** Steady yaw drift, degrees per second at intensity 1. */
    private static final float YAW_DRIFT = 6f;
    private static final float INTENSITY_EASE = 2.5f;
    /**
     * The sky follows the player's ship, not the camera. Flying across the battlefield turns it, at this
     * many degrees per 1000 units (capped), and turning the ship turns it back the
     * other way by HEADING_GAIN of the change, easing back to neutral so it never
     * drifts for good. This is what makes the motion feel caused by the player.
     */
    private static final float PARALLAX_DEG_PER_1000 = 4f;
    private static final float PARALLAX_MAX_DEG = 30f;
    private static final float HEADING_GAIN = 0.25f;
    private static final float HEADING_DECAY_PER_SEC = 0.08f;

    private final String textureKey;
    private final String fallbackKey;
    private int texId = -1;
    private boolean expired = false;
    /**
     * Runs the image out and back across the sphere (0 to 1 to 0) so its left and
     * right edges never meet, which hides the seam of an image that is not a
     * seamless panorama. It doubles the horizontal stretch, so it suits a roughly
     * square picture better than a 2:1 one. Switched on automatically when the
     * primary texture is missing and the fallback is used.
     */
    private boolean mirror = false;
    /**
     * Starsector stores images in textures padded up to a fixed size, so the
     * picture only fills the first texW x texH of the 0..1 texture space (the
     * rest is transparent). Reading UVs as 0..1 shows the picture as a small
     * patch with black around it. These come from the sprite.
     */
    private float texW = 1f;
    private float texH = 1f;
    /** If the sky comes out upside down, flip this. */
    private static final boolean FLIP_V = false;

    private float time = 0f;
    private float intensity = 1f;
    private float targetIntensity = 1f;
    private float tintR = 1f, tintG = 1f, tintB = 1f;
    private float headingYaw = 0f;
    private float curYaw = 0f, curPitch = 0f, curRoll = 0f;
    private final Vector2f lastPlayerPos = new Vector2f();
    private float lastFacing = 0f;
    private boolean haveFacing = false;

    private final float[] lonCos = new float[LON_SEGMENTS + 1];
    private final float[] lonSin = new float[LON_SEGMENTS + 1];
    private final float[] latCos = new float[LAT_BANDS + 1];
    private final float[] latSin = new float[LAT_BANDS + 1];

    /**
     * @param textureKey key in the armaa_missionBGs settings map
     * @param fallbackKey key used instead if that texture cannot be loaded
     */
    public armaa_SkySphereRenderer(String textureKey, String fallbackKey) {
        this.textureKey = textureKey;
        this.fallbackKey = fallbackKey;
        for (int j = 0; j <= LON_SEGMENTS; j++) {
            double theta = (double) j / LON_SEGMENTS * 2.0 * Math.PI;
            lonCos[j] = (float) Math.cos(theta);
            lonSin[j] = (float) Math.sin(theta);
        }
        for (int i = 0; i <= LAT_BANDS; i++) {
            double phi = -Math.PI / 2.0 + (double) i / LAT_BANDS * Math.PI;
            latCos[i] = (float) Math.cos(phi);
            latSin[i] = (float) Math.sin(phi);
        }
    }

    /** The camera's current angles, in degrees, for anything drawn in the same 3D frame. */
    public float getYaw() {
        return curYaw;
    }

    public float getPitch() {
        return curPitch;
    }

    public float getRoll() {
        return curRoll;
    }

    public static float getFovDegrees() {
        return FOV;
    }

    public void setMirrorMode(boolean mirror) {
        this.mirror = mirror;
    }

    /** 0 is a still sky, 1 is the full swing. Eased, so it can be changed mid-battle. */
    public void setIntensity(float intensity) {
        this.targetIntensity = intensity;
    }

    public void setTint(Color c) {
        tintR = c.getRed() / 255f;
        tintG = c.getGreen() / 255f;
        tintB = c.getBlue() / 255f;
    }

    public void expire() {
        expired = true;
    }

    @Override
    public boolean isExpired() {
        return expired;
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(CombatEngineLayers.CLOUD_LAYER);
    }

    @Override
    public float getRenderRadius() {
        return 1e10f;
    }

    private int loadTexture() {
        String[] keys = {textureKey, fallbackKey};
        for (String key : keys) {
            String path = Global.getSettings().getString("armaa_missionBGs", key);
            try {
                Global.getSettings().loadTexture(path);
                SpriteAPI sprite = Global.getSettings().getSprite(path);
                int id = sprite.getTextureId();
                // the small margin keeps the edge from bleeding in the padding
                texW = sprite.getTextureWidth() - 0.001f;
                texH = sprite.getTextureHeight() - 0.001f;
                if (key == fallbackKey && key != textureKey) {
                    mirror = true;   // the fallback is not a panorama, so hide its seam
                }
                return id;
            } catch (IOException | RuntimeException e) {
                Global.getLogger(armaa_SkySphereRenderer.class).warn(
                        "sky texture " + key + " (" + path + ") not available: " + e);
            }
        }
        return 0;
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (layer != CombatEngineLayers.CLOUD_LAYER) {
            return;
        }
        if (texId < 0) {
            texId = loadTexture();
        }
        if (texId == 0) {
            return;   // nothing to draw
        }

        float amount = Global.getCombatEngine().getElapsedInLastFrame();
        if (!Global.getCombatEngine().isPaused()) {
            intensity += (targetIntensity - intensity) * (1f - (float) Math.exp(-INTENSITY_EASE * amount));
            time += amount;
        }

        // Unrelated frequencies and phases so the axes never line up.
        float yaw = intensity * (YAW_AMP * (0.6f * (float) Math.sin(time * 0.31f)
                + 0.4f * (float) Math.sin(time * 0.83f + 1.3f)) + YAW_DRIFT * time);
        float pitch = intensity * PITCH_AMP * (0.65f * (float) Math.sin(time * 0.47f + 0.5f)
                + 0.35f * (float) Math.sin(time * 1.21f));
        float roll = intensity * ROLL_AMP * (0.6f * (float) Math.sin(time * 0.23f + 2f)
                + 0.4f * (float) Math.sin(time * 0.9f + 4f));

        // Tie the sky to the player's ship: where it is, and which way it turns. Not to
        // the camera, so panning the view does not move the sky. If the ship is gone
        // (destroyed, or the player is commanding from the map) the last position holds.
        ShipAPI player = Global.getCombatEngine().getPlayerShip();
        if (player != null && player.isAlive()) {
            lastPlayerPos.set(player.getLocation());
        }
        float px = MathUtils.clamp(-lastPlayerPos.x / 1000f * PARALLAX_DEG_PER_1000, -PARALLAX_MAX_DEG, PARALLAX_MAX_DEG);
        float py = MathUtils.clamp(lastPlayerPos.y / 1000f * PARALLAX_DEG_PER_1000, -PARALLAX_MAX_DEG, PARALLAX_MAX_DEG);
        if (player != null && !Global.getCombatEngine().isPaused()) {
            if (haveFacing) {
                headingYaw -= HEADING_GAIN * MathUtils.getShortestRotation(lastFacing, player.getFacing());
            }
            lastFacing = player.getFacing();
            haveFacing = true;
            headingYaw -= headingYaw * Math.min(1f, HEADING_DECAY_PER_SEC * amount);
        }
        yaw += px + headingYaw;
        pitch += py;
        curYaw = yaw;
        curPitch = pitch;
        curRoll = roll;

        float aspect = (float) Global.getSettings().getScreenWidth() / Global.getSettings().getScreenHeight();

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GLU.gluPerspective(FOV, aspect, CAM_NEAR, CAM_FAR);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glRotatef(roll, 0f, 0f, 1f);
        GL11.glRotatef(pitch, 1f, 0f, 0f);
        GL11.glRotatef(yaw, 0f, 1f, 0f);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendEquation(GL14.GL_FUNC_ADD);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glColor4f(tintR, tintG, tintB, 1f);

        for (int i = 0; i < LAT_BANDS; i++) {
            float v0 = (float) i / LAT_BANDS;
            float v1 = (float) (i + 1) / LAT_BANDS;
            if (FLIP_V) {
                v0 = 1f - v0;
                v1 = 1f - v1;
            }
            v0 *= texH;
            v1 *= texH;
            GL11.glBegin(GL11.GL_QUAD_STRIP);
            for (int j = 0; j <= LON_SEGMENTS; j++) {
                float u = (float) j / LON_SEGMENTS;
                if (mirror) {
                    u = u <= 0.5f ? u * 2f : (1f - u) * 2f;
                }
                u *= texW;

                GL11.glTexCoord2f(u, v0);
                GL11.glVertex3f(latCos[i] * lonSin[j] * RADIUS, latSin[i] * RADIUS,
                        -latCos[i] * lonCos[j] * RADIUS);
                GL11.glTexCoord2f(u, v1);
                GL11.glVertex3f(latCos[i + 1] * lonSin[j] * RADIUS, latSin[i + 1] * RADIUS,
                        -latCos[i + 1] * lonCos[j] * RADIUS);
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
