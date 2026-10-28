package data.scripts.campaign.missions.plugins;

import java.awt.Color;
import java.util.EnumSet;
import java.util.Random;

import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;

/**
 * Specks of dust as real points in 3D space, seen through the same camera as the
 * sky sphere (armaa_SkySphereRenderer): same field of view, and rotated by the
 * same yaw, pitch and roll, so the dust and the sky tumble together.
 *
 * The specks fill a cube around the camera and repeat forever. Moving the camera
 * shifts them in screen space, so near specks sweep past and far ones barely
 * move, with real perspective: size, brightness and speed all follow distance.
 * The projection is done here on the CPU and the result is drawn as small
 * screen-facing quads, so no 3D GL state is touched.
 *
 * Two are meant to be used together, as with armaa_DustField:
 * followCamera = false slides with the player's ship, and streaks at speed;
 * followCamera = true slides with the view, fainter and farther.
 */
public class armaa_DustField3D extends BaseCombatLayeredRenderingPlugin {

    private static final float NEAR = 0.3f;
    /** World units the reference moves for one unit of camera travel. */
    private final float worldUnitsPerCameraUnit;

    private final armaa_SkySphereRenderer sky;
    private final boolean followCamera;
    private final float cubeHalf;
    private final float sizePx;
    private final float sizeMinPx;
    private final float sizeMaxPx;
    private final float alpha;
    private final float streakSeconds;
    private final Color color;
    /** 0 leaves the dust still in the frame, 1 rotates it fully with the sky. */
    private final float rotationFollow;

    private final float[] x;
    private final float[] y;
    private final float[] z;
    private final float[] bright;

    private final Vector2f ref = new Vector2f();
    private final Vector2f lastRef = new Vector2f();
    private final Vector2f refVel = new Vector2f();   // camera units per second
    private final Vector2f lastShip = new Vector2f();
    private boolean haveRef = false;
    private boolean haveShip = false;
    private boolean expired = false;

    /**
     * @param sky the sky whose camera to share (may be null for a fixed frame)
     * @param count specks in the cube
     * @param cubeHalf half the side of the cube, in camera units
     * @param worldUnitsPerCameraUnit larger means the dust reacts less to movement
     * @param sizePx size of a speck one camera unit away, screen pixels
     * @param sizeMinPx smallest a speck is drawn
     * @param sizeMaxPx largest a speck is drawn
     * @param streakSeconds streak length, in seconds of the reference's velocity; 0 for none
     * @param rotationFollow how much of the sky's rotation the dust takes, 0 to 1
     */
    public armaa_DustField3D(armaa_SkySphereRenderer sky, boolean followCamera, int count, float cubeHalf,
            float worldUnitsPerCameraUnit, float sizePx, float sizeMinPx, float sizeMaxPx,
            float alpha, float streakSeconds, float rotationFollow, Color color, long seed) {
        this.sky = sky;
        this.followCamera = followCamera;
        this.cubeHalf = cubeHalf;
        this.worldUnitsPerCameraUnit = worldUnitsPerCameraUnit;
        this.sizePx = sizePx;
        this.sizeMinPx = sizeMinPx;
        this.sizeMaxPx = sizeMaxPx;
        this.alpha = alpha;
        this.streakSeconds = streakSeconds;
        this.rotationFollow = rotationFollow;
        this.color = color;

        x = new float[count];
        y = new float[count];
        z = new float[count];
        bright = new float[count];
        Random r = new Random(seed);
        for (int i = 0; i < count; i++) {
            x[i] = (r.nextFloat() * 2f - 1f) * cubeHalf;
            y[i] = (r.nextFloat() * 2f - 1f) * cubeHalf;
            z[i] = (r.nextFloat() * 2f - 1f) * cubeHalf;
            bright[i] = 0.5f + 0.5f * r.nextFloat();
        }
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

    private float wrap(float d) {
        float period = cubeHalf * 2f;
        d = (d + cubeHalf) % period;
        if (d < 0f) {
            d += period;
        }
        return d - cubeHalf;
    }

    // Rotations, matching the order GL applies them to a vertex: roll(pitch(yaw(v))).
    private static float[] rotY(float[] v, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new float[]{(float) (v[0] * c + v[2] * s), v[1], (float) (-v[0] * s + v[2] * c)};
    }

    private static float[] rotX(float[] v, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new float[]{v[0], (float) (v[1] * c - v[2] * s), (float) (v[1] * s + v[2] * c)};
    }

    private static float[] rotZ(float[] v, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new float[]{(float) (v[0] * c - v[1] * s), (float) (v[0] * s + v[1] * c), v[2]};
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (layer != CombatEngineLayers.UNDER_SHIPS_LAYER) {
            return;
        }
        CombatEngineAPI engine = Global.getCombatEngine();
        Vector2f centre = viewport.getCenter();

        if (followCamera) {
            ref.set(centre);
        } else {
            ShipAPI player = engine.getPlayerShip();
            if (player != null && player.isAlive()) {
                lastShip.set(player.getLocation());
                haveShip = true;
            }
            ref.set(haveShip ? lastShip : centre);
        }

        float amount = engine.getElapsedInLastFrame();
        if (haveRef && amount > 0f && !engine.isPaused()) {
            float k = Math.min(1f, 8f * amount);
            refVel.x += ((ref.x - lastRef.x) / amount / worldUnitsPerCameraUnit - refVel.x) * k;
            refVel.y += ((ref.y - lastRef.y) / amount / worldUnitsPerCameraUnit - refVel.y) * k;
        }
        lastRef.set(ref);
        haveRef = true;

        // Camera position, in camera units, along the screen's own axes.
        float camX = ref.x / worldUnitsPerCameraUnit;
        float camY = ref.y / worldUnitsPerCameraUnit;

        double yaw = 0, pitch = 0, roll = 0;
        if (sky != null) {
            yaw = Math.toRadians(sky.getYaw() * rotationFollow);
            pitch = Math.toRadians(sky.getPitch() * rotationFollow);
            roll = Math.toRadians(sky.getRoll() * rotationFollow);
        }

        float screenW = Global.getSettings().getScreenWidth();
        float screenH = Global.getSettings().getScreenHeight();
        float aspect = screenW / screenH;
        float tanHalf = (float) Math.tan(Math.toRadians(armaa_SkySphereRenderer.getFovDegrees()) / 2.0);
        float unitsPerPixel = viewport.getVisibleWidth() / screenW;
        float a = alpha * viewport.getAlphaMult();

        float cr = color.getRed() / 255f;
        float cg = color.getGreen() / 255f;
        float cb = color.getBlue() / 255f;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glBegin(GL11.GL_QUADS);

        for (int i = 0; i < x.length; i++) {
            // Undo the frame's rotation on the camera shift, so the shift stays aligned with the
            // screen, then wrap the speck into the cube around the camera.
            float[] shift = {camX, camY, 0f};
            shift = rotZ(shift, -roll);
            shift = rotX(shift, -pitch);
            shift = rotY(shift, -yaw);
            float[] q = {wrap(x[i] - shift[0]), wrap(y[i] - shift[1]), wrap(z[i] - shift[2])};

            float[] e = rotY(q, yaw);
            e = rotX(e, pitch);
            e = rotZ(e, roll);

            float d = -e[2];   // distance in front of the camera
            if (d < NEAR) {
                continue;
            }
            float ndcX = e[0] / d / (tanHalf * aspect);
            float ndcY = e[1] / d / tanHalf;
            if (Math.abs(ndcX) > 1.05f || Math.abs(ndcY) > 1.05f) {
                continue;
            }

            float sx = (ndcX * 0.5f + 0.5f) * screenW;
            float sy = (ndcY * 0.5f + 0.5f) * screenH;
            float wx = viewport.getLLX() + sx * unitsPerPixel;
            float wy = viewport.getLLY() + sy * unitsPerPixel;

            // Nearer is larger, brighter and moves faster on screen.
            float px = Math.max(sizeMinPx, Math.min(sizeMaxPx, sizePx / d));
            float s = px * unitsPerPixel;
            float fade = Math.min(1f, Math.max(0f, 1.15f - d / (cubeHalf * 1.7f)));

            // The speck slides against the reference's velocity, at 1/d of it in screen terms.
            float pxPerCameraUnit = (screenH * 0.5f) / (tanHalf * d);
            float vx = -refVel.x * pxPerCameraUnit * streakSeconds * unitsPerPixel;
            float vy = -refVel.y * pxPerCameraUnit * streakSeconds * unitsPerPixel;
            float len = (float) Math.sqrt(vx * vx + vy * vy);
            float ux = 1f, uy = 0f, length = s;
            if (len > s) {
                ux = vx / len;
                uy = vy / len;
                length = len + s;
            }
            float hl = length / 2f;
            float hw = s / 2f;
            float nx = -uy * hw;
            float ny = ux * hw;
            float lx = ux * hl;
            float ly = uy * hl;

            GL11.glColor4f(cr, cg, cb, a * bright[i] * fade);
            GL11.glVertex2f(wx - lx - nx, wy - ly - ny);
            GL11.glVertex2f(wx + lx - nx, wy + ly - ny);
            GL11.glVertex2f(wx + lx + nx, wy + ly + ny);
            GL11.glVertex2f(wx - lx + nx, wy - ly + ny);
        }
        GL11.glEnd();
        GL11.glPopAttrib();
    }
}
