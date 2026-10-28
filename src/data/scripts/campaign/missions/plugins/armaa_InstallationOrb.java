package data.scripts.campaign.missions.plugins;

import java.awt.Color;
import java.util.EnumSet;
import java.util.Random;

import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;

/**
 * A huge faceted sphere with a pulsing eye, painted onto the sky at one fixed
 * direction (an AC6 "Closure Satellite" read on the Pather installation). It
 * shares the sky's camera: same field of view, same yaw/pitch/roll, drawn as
 * real geometry so panning past it shows genuine rotation, not a sprite
 * sliding across the screen.
 *
 * "Fixed direction" means it never gets nearer, the same way the sky texture
 * never does: only the shared camera rotation moves it, never the player's
 * position. The body also has its own slow independent tumble, which is what
 * reads as a real object rather than paint on the backdrop.
 */
public class armaa_InstallationOrb extends BaseCombatLayeredRenderingPlugin {

    private static final int LON_SEGMENTS = 10;
    private static final int LAT_BANDS = 6;
    private static final float CAM_NEAR = 0.1f;
    private static final float CAM_FAR = 20f;
    /** Fixed relative to the shared camera frame, not the object's own tumble. */
    private static final float[] LIGHT_DIR = normalize(0.4f, 0.6f, 0.7f);
    /** Axis the body tumbles around; picked once, arbitrary and off-axis. */
    private static final float[] SPIN_AXIS = normalize(0.25f, 1f, 0.15f);

    private final armaa_SkySphereRenderer sky;
    private final float anchorX, anchorY, anchorZ;
    private final float distance;
    private final float radius;
    private final float eyeRadius;
    private final Color bodyColor;
    private final Color eyeColor;
    private final float spinDegPerSec;
    private final float pulseSpeed;

    private final float[] lonCos = new float[LON_SEGMENTS + 1];
    private final float[] lonSin = new float[LON_SEGMENTS + 1];
    private final float[] latCos = new float[LAT_BANDS + 1];
    private final float[] latSin = new float[LAT_BANDS + 1];
    /** One brightness jitter per quad face, for plating variety. */
    private final float[] panelJitter = new float[LAT_BANDS * LON_SEGMENTS];

    private float time = 0f;
    private boolean expired = false;

    /**
     * @param sky whose camera (fov, yaw, pitch, roll) this shares
     * @param azimuthDeg direction on the sky, degrees around
     * @param elevationDeg direction on the sky, degrees above the horizon
     * @param distance camera units to the body's centre; only affects apparent
     * size, since the shared frame never translates
     * @param radius body radius, camera units
     * @param eyeRadius glow radius, camera units
     * @param spinDegPerSec the body's own slow tumble
     * @param pulseSpeed how fast the eye pulses, radians per second
     */
    public armaa_InstallationOrb(armaa_SkySphereRenderer sky, float azimuthDeg, float elevationDeg,
            float distance, float radius, float eyeRadius, Color bodyColor, Color eyeColor,
            float spinDegPerSec, float pulseSpeed, long seed) {
        this.sky = sky;
        this.distance = distance;
        this.radius = radius;
        this.eyeRadius = eyeRadius;
        this.bodyColor = bodyColor;
        this.eyeColor = eyeColor;
        this.spinDegPerSec = spinDegPerSec;
        this.pulseSpeed = pulseSpeed;

        double az = Math.toRadians(azimuthDeg);
        double el = Math.toRadians(elevationDeg);
        anchorX = (float) (Math.cos(el) * Math.sin(az));
        anchorY = (float) Math.sin(el);
        anchorZ = (float) (-Math.cos(el) * Math.cos(az));

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

        Random r = new Random(seed);
        for (int k = 0; k < panelJitter.length; k++) {
            panelJitter[k] = 0.8f + 0.4f * r.nextFloat();
        }
    }

    private static float[] normalize(float x, float y, float z) {
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[]{x / len, y / len, z / len};
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
        return EnumSet.of(CombatEngineLayers.UNDER_SHIPS_LAYER);
    }

    @Override
    public float getRenderRadius() {
        return 1e10f;
    }

    private float[] vertex(int i, int j) {
        return new float[]{latCos[i] * lonSin[j], latSin[i], -latCos[i] * lonCos[j]};
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (layer != CombatEngineLayers.UNDER_SHIPS_LAYER || sky == null) {
            return;
        }

        if (!Global.getCombatEngine().isPaused()) {
            time += Global.getCombatEngine().getElapsedInLastFrame();
        }

        float aspect = (float) Global.getSettings().getScreenWidth() / Global.getSettings().getScreenHeight();

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LIGHTING_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GLU.gluPerspective(armaa_SkySphereRenderer.getFovDegrees(), aspect, CAM_NEAR, CAM_FAR);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glRotatef(sky.getRoll(), 0f, 0f, 1f);
        GL11.glRotatef(sky.getPitch(), 1f, 0f, 0f);
        GL11.glRotatef(sky.getYaw(), 0f, 1f, 0f);
        GL11.glTranslatef(anchorX * distance, anchorY * distance, anchorZ * distance);

        // The light stays fixed in this frame (before the body's own spin below),
        // so the lit side turns with the body instead of riding along with it.
        GL11.glLightf(GL11.GL_LIGHT0, GL11.GL_CONSTANT_ATTENUATION, 1f);
        GL11.glLight(GL11.GL_LIGHT0, GL11.GL_POSITION,
                floatBuffer(LIGHT_DIR[0], LIGHT_DIR[1], LIGHT_DIR[2], 0f));
        GL11.glLight(GL11.GL_LIGHT0, GL11.GL_DIFFUSE, floatBuffer(1f, 1f, 1f, 1f));
        GL11.glLight(GL11.GL_LIGHT0, GL11.GL_AMBIENT, floatBuffer(0.18f, 0.18f, 0.2f, 1f));

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);

        GL11.glPushMatrix();
        GL11.glRotatef(time * spinDegPerSec, SPIN_AXIS[0], SPIN_AXIS[1], SPIN_AXIS[2]);
        drawBody();
        GL11.glPopMatrix();

        // Drawn after the (opaque) body, with nothing depth-tested, so it paints on
        // top instead of being covered by it. Back in the un-spun frame, so the eye
        // itself never spins with the plating.
        drawGlow();

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    private void drawBody() {
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_LIGHT0);
        GL11.glEnable(GL11.GL_COLOR_MATERIAL);
        GL11.glColorMaterial(GL11.GL_FRONT, GL11.GL_AMBIENT_AND_DIFFUSE);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        float cr = bodyColor.getRed() / 255f;
        float cg = bodyColor.getGreen() / 255f;
        float cb = bodyColor.getBlue() / 255f;

        GL11.glBegin(GL11.GL_QUADS);
        int face = 0;
        for (int i = 0; i < LAT_BANDS; i++) {
            for (int j = 0; j < LON_SEGMENTS; j++) {
                float[] p00 = vertex(i, j);
                float[] p01 = vertex(i, j + 1);
                float[] p10 = vertex(i + 1, j);
                float[] p11 = vertex(i + 1, j + 1);
                float nx = p00[0] + p01[0] + p10[0] + p11[0];
                float ny = p00[1] + p01[1] + p10[1] + p11[1];
                float nz = p00[2] + p01[2] + p10[2] + p11[2];
                float[] n = normalize(nx, ny, nz);

                float jitter = panelJitter[face++];
                GL11.glColor4f(cr * jitter, cg * jitter, cb * jitter, 1f);
                GL11.glNormal3f(n[0], n[1], n[2]);
                GL11.glVertex3f(p00[0] * radius, p00[1] * radius, p00[2] * radius);
                GL11.glVertex3f(p01[0] * radius, p01[1] * radius, p01[2] * radius);
                GL11.glVertex3f(p11[0] * radius, p11[1] * radius, p11[2] * radius);
                GL11.glVertex3f(p10[0] * radius, p10[1] * radius, p10[2] * radius);
            }
        }
        GL11.glEnd();

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_LIGHT0);
        GL11.glDisable(GL11.GL_COLOR_MATERIAL);
    }

    /** Two additive fans: a soft outer glow and a brighter, tighter core. Pulses over time. */
    private void drawGlow() {
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        float pulse = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(time * pulseSpeed));

        float cr = eyeColor.getRed() / 255f;
        float cg = eyeColor.getGreen() / 255f;
        float cb = eyeColor.getBlue() / 255f;

        fan(eyeRadius * (0.85f + 0.15f * pulse), cr, cg, cb, 0.55f * pulse);
        fan(eyeRadius * 0.35f, 1f, 1f, 1f, 0.8f * pulse);
    }

    private void fan(float r, float cr, float cg, float cb, float centerAlpha) {
        int segs = 16;
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glColor4f(cr, cg, cb, centerAlpha);
        GL11.glVertex3f(0f, 0f, 0f);
        GL11.glColor4f(cr, cg, cb, 0f);
        for (int k = 0; k <= segs; k++) {
            double a = (double) k / segs * 2.0 * Math.PI;
            GL11.glVertex3f((float) (Math.cos(a) * r), (float) (Math.sin(a) * r), 0f);
        }
        GL11.glEnd();
    }

    private static java.nio.FloatBuffer floatBuffer(float a, float b, float c, float d) {
        java.nio.FloatBuffer buf = org.lwjgl.BufferUtils.createFloatBuffer(4);
        buf.put(a).put(b).put(c).put(d);
        buf.flip();
        return buf;
    }
}
