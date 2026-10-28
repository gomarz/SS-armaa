package data.scripts.campaign.missions.plugins;

import java.awt.Color;
import java.io.IOException;
import java.util.EnumSet;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * One large dark shape drawn between the sky and the ships, that slides across
 * the screen more slowly than the ships do. A sky at infinity has no parallax;
 * this is what tells the eye there is depth between the two.
 *
 * factor is 1 for something fixed to the battlefield (moves exactly as the ships
 * do) and 0 for something fixed to the screen. Around 0.3 to 0.5 reads as a big
 * object a long way behind the fight.
 */
public class armaa_ParallaxSilhouette extends BaseCombatLayeredRenderingPlugin {

    private final Vector2f anchor;
    private final float factor;
    private final float width;
    private final float spinPerSecond;
    private final Color tint;
    private final float alpha;

    private SpriteAPI sprite;
    private boolean loaded = false;
    private float angle;
    private boolean expired = false;

    /**
     * @param anchor where it sits on the battlefield, in world units
     * @param factor parallax, 0 to 1
     * @param width drawn width in world units
     * @param angle starting rotation, degrees
     * @param spinPerSecond slow drift in rotation, degrees per second
     */
    public armaa_ParallaxSilhouette(Vector2f anchor, float factor, float width,
            float angle, float spinPerSecond, Color tint, float alpha) {
        this.anchor = anchor;
        this.factor = factor;
        this.width = width;
        this.angle = angle;
        this.spinPerSecond = spinPerSecond;
        this.tint = tint;
        this.alpha = alpha;
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
        return EnumSet.of(CombatEngineLayers.BELOW_SHIPS_LAYER);
    }

    @Override
    public float getRenderRadius() {
        return 1e10f;
    }

    /**
     * Prefers a purpose-made transparent PNG. Until one exists it borrows a hull
     * sprite, which reads as a silhouette once it is tinted dark.
     */
    private void load() {
        loaded = true;
        String custom = "graphics/armaa/backgrounds/armaa_sfo_silhouette.png";
        try {
            Global.getSettings().loadTexture(custom);
            sprite = Global.getSettings().getSprite(custom);
            return;
        } catch (IOException | RuntimeException e) {
            // not there yet
        }
        try {
            sprite = Global.getSettings().getSprite(
                    Global.getSettings().getHullSpec("armaa_whitebase").getSpriteName());
        } catch (RuntimeException e) {
            Global.getLogger(armaa_ParallaxSilhouette.class).warn("no silhouette sprite available: " + e);
        }
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (layer != CombatEngineLayers.BELOW_SHIPS_LAYER) {
            return;
        }
        if (!loaded) {
            load();
        }
        if (sprite == null) {
            return;
        }
        if (!Global.getCombatEngine().isPaused()) {
            angle += spinPerSecond * Global.getCombatEngine().getElapsedInLastFrame();
        }

        // On screen it sits (anchor - centre) * factor from the middle of the view.
        Vector2f centre = viewport.getCenter();
        float x = centre.x + (anchor.x - centre.x) * factor;
        float y = centre.y + (anchor.y - centre.y) * factor;

        float aspect = sprite.getHeight() / Math.max(1f, sprite.getWidth());
        sprite.setSize(width, width * aspect);
        sprite.setAngle(angle);
        sprite.setColor(tint);
        sprite.setAlphaMult(alpha * viewport.getAlphaMult());
        sprite.renderAtCenter(x, y);
    }
}
