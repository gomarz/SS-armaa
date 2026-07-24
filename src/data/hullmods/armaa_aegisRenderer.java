package data.hullmods;

import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.EnumSet;


public class armaa_aegisRenderer extends BaseCombatLayeredRenderingPlugin {

    private static final int SEGMENTS = 120;
    private static final Color RING_COLOR = new Color(120, 200, 255); // match faction palette

    private final ShipAPI host;
    private float pulse = 0f;

    public armaa_aegisRenderer(ShipAPI host) {
        this.host = host;
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(CombatEngineLayers.BELOW_SHIPS_LAYER);
    }

    @Override
    public float getRenderRadius() {
        return armaa_aegisConfig.RADIUS + 100f;
    }

    @Override
    public boolean isExpired() {
        return host == null || host.isHulk() || !host.isAlive();
    }

    @Override
    public void advance(float amount) {
        pulse += amount * 2f;
        // keep the anchor entity glued to the ship so culling uses the
        // ring's real position
        if (entity != null && host != null) {
            entity.getLocation().set(host.getLocation());
        }
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (layer != CombatEngineLayers.BELOW_SHIPS_LAYER) return;
        if (host == null || host.isHulk() || !host.isAlive()) return;

        float reduction = armaa_aegisConfig.currentReduction(host);
        if (reduction <= 0f) return; // field collapsed

        Vector2f c = host.getLocation();
        float r = armaa_aegisConfig.RADIUS;

        // cheap belt-and-braces guard; engine culling does the real work now
        if (!viewport.isNearViewport(c, r + 50f)) return;

        float strength = reduction / armaa_aegisConfig.MAX_REDUCTION;   // 0..1
        float breathe = 0.85f + 0.15f * (float) Math.sin(pulse);
        float alpha = 0.10f + 0.35f * strength * breathe;

        // set only the state we need, restore it manually — much cheaper
        // than glPushAttrib(GL_ENABLE_BIT) per call
        boolean texWasOn = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE); // additive glow

        GL11.glLineWidth(2.5f);
        GL11.glColor4f(RING_COLOR.getRed() / 255f, RING_COLOR.getGreen() / 255f,
                RING_COLOR.getBlue() / 255f, alpha);
        drawLoop(c, r);

        // faint inner ring for depth, also free
        GL11.glLineWidth(1f);
        GL11.glColor4f(RING_COLOR.getRed() / 255f, RING_COLOR.getGreen() / 255f,
                RING_COLOR.getBlue() / 255f, alpha * 0.4f);
        drawLoop(c, r - 8f);

        // restore expected engine state
        GL11.glLineWidth(1f);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        if (texWasOn) {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        }
    }

    private static void drawLoop(Vector2f c, float radius) {
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < SEGMENTS; i++) {
            double ang = (2.0 * Math.PI * i) / SEGMENTS;
            GL11.glVertex2f(c.x + (float) (Math.cos(ang) * radius),
                            c.y + (float) (Math.sin(ang) * radius));
        }
        GL11.glEnd();
    }
}