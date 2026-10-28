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
 * A field of tiny specks in battlespace that slide across the screen at
 * different rates, so the eye reads depth and movement. Each speck has a depth
 * factor f: 1 moves exactly like the battlefield, near 0 hardly moves at all.
 * Nearer specks are also larger and brighter.
 *
 * Two are meant to be used together:
 * - followCamera = false: the specks slide with the player's SHIP. They show the
 * ship moving, and stretch into short streaks at speed. Panning the camera does
 * not disturb them.
 * - followCamera = true: the specks slide with the CAMERA. Faint and far, they
 * only add a little parallax when the view moves.
 *
 * The specks live on a repeating tile centred on the view, so the field never
 * runs out. Speck size is in screen pixels, so it does not change with zoom.
 */
public class armaa_DustField extends BaseCombatLayeredRenderingPlugin {

    private final boolean followCamera;
    private final float tile;
    private final float sizeMinPx;
    private final float sizeMaxPx;
    private final float alpha;
    private final float streakSeconds;
    private final Color color;

    private final float[] px;
    private final float[] py;
    private final float[] depth;   // f
    private final float[] size;    // pixels, before scaling by depth
    private final float[] bright;

    private final Vector2f ref = new Vector2f();
    private final Vector2f lastRef = new Vector2f();
    private final Vector2f refVel = new Vector2f();
    private final Vector2f lastShip = new Vector2f();
    private boolean haveRef = false;
    private boolean haveShip = false;
    private boolean expired = false;

    /**
     * @param count specks on the whole tile (only those in view are drawn)
     * @param tile side of the repeating tile, world units. Larger than the view.
     * @param depthMin depth factor of the farthest specks, 0 to 1
     * @param depthMax depth factor of the nearest specks, 0 to 1
     * @param sizeMinPx smallest speck, screen pixels
     * @param sizeMaxPx largest speck, screen pixels
     * @param streakSeconds how long a streak is at the reference's velocity, 0 for none
     */
    public armaa_DustField(boolean followCamera, int count, float tile, float depthMin, float depthMax,
            float sizeMinPx, float sizeMaxPx, float alpha, float streakSeconds, Color color, long seed) {
        this.followCamera = followCamera;
        this.tile = tile;
        this.sizeMinPx = sizeMinPx;
        this.sizeMaxPx = sizeMaxPx;
        this.alpha = alpha;
        this.streakSeconds = streakSeconds;
        this.color = color;

        px = new float[count];
        py = new float[count];
        depth = new float[count];
        size = new float[count];
        bright = new float[count];
        Random r = new Random(seed);
        for (int i = 0; i < count; i++) {
            px[i] = r.nextFloat() * tile;
            py[i] = r.nextFloat() * tile;
            float t = r.nextFloat();
            depth[i] = depthMin + (depthMax - depthMin) * t;
            // nearer means larger and brighter
            size[i] = sizeMinPx + (sizeMaxPx - sizeMinPx) * t;
            bright[i] = 0.4f + 0.6f * t;
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

    /** Wraps a distance into -tile/2 .. tile/2. */
    private float wrap(float d) {
        d = (d + tile / 2f) % tile;
        if (d < 0f) {
            d += tile;
        }
        return d - tile / 2f;
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
            refVel.x += ((ref.x - lastRef.x) / amount - refVel.x) * k;
            refVel.y += ((ref.y - lastRef.y) / amount - refVel.y) * k;
        }
        lastRef.set(ref);
        haveRef = true;

        float unitsPerPixel = viewport.getVisibleWidth() / Global.getSettings().getScreenWidth();
        float halfW = viewport.getVisibleWidth() / 2f + 40f * unitsPerPixel;
        float halfH = viewport.getVisibleHeight() / 2f + 40f * unitsPerPixel;
        float a = alpha * viewport.getAlphaMult();

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        float cr = color.getRed() / 255f;
        float cg = color.getGreen() / 255f;
        float cb = color.getBlue() / 255f;

        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < px.length; i++) {
            float f = depth[i];
            float dx = wrap(px[i] + ref.x * (1f - f) - centre.x);
            float dy = wrap(py[i] + ref.y * (1f - f) - centre.y);
            if (Math.abs(dx) > halfW || Math.abs(dy) > halfH) {
                continue;
            }
            float x = centre.x + dx;
            float y = centre.y + dy;
            float s = size[i] * unitsPerPixel;

            // On screen the speck moves against the reference at f times its speed.
            float sx = -refVel.x * f * streakSeconds;
            float sy = -refVel.y * f * streakSeconds;
            float len = (float) Math.sqrt(sx * sx + sy * sy);
            float ux = 1f, uy = 0f, length = s;
            if (len > s) {
                ux = sx / len;
                uy = sy / len;
                length = len + s;
            }
            float hl = length / 2f;
            float hw = s / 2f;
            float nx = -uy * hw;
            float ny = ux * hw;
            float lx = ux * hl;
            float ly = uy * hl;

            GL11.glColor4f(cr, cg, cb, a * bright[i]);
            GL11.glVertex2f(x - lx - nx, y - ly - ny);
            GL11.glVertex2f(x + lx - nx, y + ly - ny);
            GL11.glVertex2f(x + lx + nx, y + ly + ny);
            GL11.glVertex2f(x - lx + nx, y - ly + ny);
        }
        GL11.glEnd();

        GL11.glPopAttrib();
    }
}
