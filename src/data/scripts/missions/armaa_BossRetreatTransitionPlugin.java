package data.scripts.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;

import java.awt.Color;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;

/**
 * Manages the boss retreat/return transition entirely in screenspace.
 *
 * RETREAT: Ghost shrinks + darkens from ship's screen position to center.
 * HOLDING: Ghost sits at screen center at MIN_SCALE/MIN_BRIGHTNESS while
 * foreground attacks play out. Stays until restore() is called. RESTORE: Ghost
 * grows + brightens from center back to re-entry position.
 *
 * Screen positions are recomputed every frame so the ghost tracks correctly
 * even if the camera moves during the transition.
 *
 * Usage: transition.retreat(new Vector2f(boss.getLocation()), () ->
 * beginForegroundPhase()); transition.restore(savedPos, () ->
 * beginCombatPhase());
 */
public class armaa_BossRetreatTransitionPlugin extends BaseEveryFrameCombatPlugin {

    // -----------------------------------------------------------------------
    // State machine
    // -----------------------------------------------------------------------
    private enum State {
        IDLE,
        RETREATING, // shrinking + darkening, drifting toward screen center
        HOLDING,    // ghost sitting at screen center during foreground phase
        RESTORING,  // growing + brightening, drifting toward re-entry pos
    }

    // -----------------------------------------------------------------------
    // Config
    // -----------------------------------------------------------------------
    private static final float MIN_SCALE      = 0.50f;
    private static final float MIN_BRIGHTNESS = 0.50f;

    // Sway config
    private static final float SWAY_X_AMPLITUDE     = 8f;    // left/right pixels
    private static final float SWAY_Y_AMPLITUDE     = 3f;    // up/down pixels (subtle)
    private static final float SWAY_ANGLE_AMPLITUDE = 10f;  // degrees
    private static final float SWAY_X_FREQ          = 1.2f;  // faster lateral
    private static final float SWAY_Y_FREQ          = 0.7f;  // slower vertical
    private static final float SWAY_ANGLE_FREQ      = 0.9f;  // gentle rock

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------
    private final ShipAPI ship;
    private final float   transitionDuration;
    private final Color   tint;

    private State state        = State.IDLE;
    private float elapsed      = 0f;
    private float swayElapsed  = 0f;
    private boolean retreatedOnce = false;

    private Runnable onRetreatComplete = null;
    private Runnable onRestoreComplete = null;

    private Vector2f reEntryWorldPos     = new Vector2f();
    private Vector2f ghostStartScreenPos = new Vector2f();

    private java.util.Map<WeaponAPI, Vector2f> capturedWeaponOffsets = new java.util.HashMap<>();
    private java.util.Map<ShipAPI, Vector2f>   capturedModuleOffsets = new java.util.HashMap<>();
    private float capturedFacing = 0f;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------
    public armaa_BossRetreatTransitionPlugin(ShipAPI ship, float transitionDuration, Color tint) {
        this.ship               = ship;
        this.transitionDuration = Math.max(0.01f, transitionDuration);
        this.tint               = tint;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    public void retreat(Vector2f currentWorldPos, Runnable onComplete) {
        if (state != State.IDLE) return;

        for (int i = 0; i < 3; i++) {
            armaa_TransitionExplosion.spawn(MathUtils.getRandomPointInCircle(currentWorldPos, 50f));
        }

        ghostStartScreenPos = toCenter(currentWorldPos);
        if (capturedFacing == 0f) {
            capturedFacing = ship.getFacing();
        }
        captureOffsets(currentWorldPos);

        state             = State.RETREATING;
        elapsed           = 0f;
        swayElapsed       = 0f;
        onRetreatComplete = onComplete;
        ship.getAIFlags().setFlag(AIFlags.MANEUVER_TARGET);
    }

    public void restore(Vector2f reEntryPos, Runnable onComplete) {
        if (state != State.HOLDING) return;

        ShipAPI player = Global.getCombatEngine().getPlayerShip();
        reEntryWorldPos     = (player != null && player.isAlive() && !player.isHulk())
                ? new Vector2f(0f, -1000f)
                : new Vector2f(0f, 0f);
        ghostStartScreenPos = new Vector2f(0f, 0f);

        state             = State.RESTORING;
        elapsed           = 0f;
        onRestoreComplete = onComplete;
    }

    public boolean isIdle()      { return state == State.IDLE; }
    public boolean isHolding()   { return state == State.HOLDING; }
    public boolean isRetreated() { return retreatedOnce; }

    // -----------------------------------------------------------------------
    // Plugin lifecycle
    // -----------------------------------------------------------------------

    @Override
    public void advance(float amount, java.util.List events) {
        if (state == State.IDLE) return;

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine.isPaused()) return;

        switch (state) {
            case RETREATING: {
                elapsed += amount;
                float progress = Math.min(elapsed / transitionDuration, 1f);
                float scale    = 1f - progress * (1f - MIN_SCALE);
                float bright   = 1f - progress * (1f - MIN_BRIGHTNESS);

                Vector2f endPos = new Vector2f(0f, 0f);
                float x = ghostStartScreenPos.x + (endPos.x - ghostStartScreenPos.x) * progress;
                float y = ghostStartScreenPos.y + (endPos.y - ghostStartScreenPos.y) * progress;

                renderGhost(scale, bright, new Vector2f(x, y), 0f);

                if (progress >= 1f) {
                    retreatedOnce = true;
                    state   = State.HOLDING;
                    elapsed = 0f;
                    swayElapsed = 0f;
                    if (onRetreatComplete != null) { onRetreatComplete.run(); onRetreatComplete = null; }
                }
                break;
            }

            case HOLDING: {
                swayElapsed += amount;

                float swayX     = (float) Math.sin(swayElapsed * SWAY_X_FREQ)     * SWAY_X_AMPLITUDE;
                float swayY     = (float) Math.sin(swayElapsed * SWAY_Y_FREQ)     * SWAY_Y_AMPLITUDE;
                float swayAngle = (float) Math.sin(swayElapsed * SWAY_ANGLE_FREQ) * SWAY_ANGLE_AMPLITUDE;

                renderGhost(MIN_SCALE, MIN_BRIGHTNESS, new Vector2f(swayX, swayY), swayAngle);
                break;
            }

            case RESTORING: {
                elapsed += amount;
                float progress = Math.min(elapsed / transitionDuration, 1f);
                float t        = 1f - progress;
                float scale    = 1f - t * (1f - MIN_SCALE);
                float bright   = 1f - t * (1f - MIN_BRIGHTNESS);

                Vector2f endPos = reEntryWorldPos;
                float x = ghostStartScreenPos.x + (endPos.x - ghostStartScreenPos.x) * progress;
                float y = ghostStartScreenPos.y + (endPos.y - ghostStartScreenPos.y) * progress;

                renderGhost(scale, bright, new Vector2f(x, y), 0f);

                if (progress >= 1f) {
                    ship.getAIFlags().unsetFlag(AIFlags.MANEUVER_TARGET);
                    retreatedOnce = false;
                    state   = State.IDLE;
                    elapsed = 0f;
                    if (onRestoreComplete != null) { onRestoreComplete.run(); onRestoreComplete = null; }
                }
                break;
            }

            default:
                break;
        }
    }

    // -----------------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------------

    private void captureOffsets(Vector2f origin) {
        capturedWeaponOffsets.clear();
        capturedModuleOffsets.clear();

        for (WeaponAPI weapon : ship.getAllWeapons()) {
            capturedWeaponOffsets.put(weapon, new Vector2f(weapon.getLocation().x, weapon.getLocation().y));
        }

        if (ship.getChildModulesCopy() != null) {
            for (ShipAPI module : ship.getChildModulesCopy()) {
                capturedModuleOffsets.put(module, new Vector2f(module.getLocation().x, module.getLocation().y));
                for (WeaponAPI weapon : module.getAllWeapons()) {
                    capturedWeaponOffsets.put(weapon, new Vector2f(
                            weapon.getLocation().x - module.getLocation().x,
                            weapon.getLocation().y - module.getLocation().y
                    ));
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------------

    private void renderGhost(float scale, float brightness, Vector2f screenPos, float swayAngle) {
        Color c    = brightnessColor(brightness);
        float zoom = Global.getCombatEngine().getViewport().getViewMult();

        renderHullScreenspace(scale, c, screenPos, swayAngle);

        for (WeaponAPI weapon : ship.getAllWeapons()) {
            if (weapon.getType() == WeaponAPI.WeaponType.MISSILE) continue;
            renderWeaponScreenspace(weapon, scale, c, screenPos, swayAngle);
        }

        if (ship.getChildModulesCopy() != null) {
            for (ShipAPI module : ship.getChildModulesCopy()) {
                renderModuleScreenspace(module, scale, c, screenPos, zoom, swayAngle);
            }
        }
    }

    private void renderHullScreenspace(float scale, Color c, Vector2f screenPos, float swayAngle) {
        SpriteAPI sprite = Global.getSettings().getSprite(ship.getHullSpec().getSpriteName());
        if (sprite == null) return;

        MagicRender.screenspace(
                sprite,
                MagicRender.positioning.CENTER,
                new Vector2f(screenPos),
                new Vector2f(0f, 0f),
                new Vector2f(sprite.getWidth() * scale, sprite.getHeight() * scale),
                new Vector2f(0f, 0f),
                swayAngle,
                0f,
                c,
                false,
                0f, 0f, 0f, 0f, 0f,
                0f, -1f, 0f,
                CombatEngineLayers.CLOUD_LAYER
        );
    }

    private void renderWeaponScreenspace(WeaponAPI weapon, float scale, Color c,
                                          Vector2f screenPos, float swayAngle) {
        SpriteAPI sprite = Global.getSettings().getSprite(weapon.getSpec().getTurretSpriteName());
        if (sprite == null) sprite = Global.getSettings().getSprite(weapon.getSpec().getHardpointSpriteName());
        if (sprite == null) sprite = Global.getSettings().getSprite(weapon.getSpec().getHardpointUnderSpriteName());
        if (sprite == null) return;

        WeaponSlotAPI slot = weapon.getSlot();
        if (slot.getId().equals("A_GUN2") || slot.getId().equals("WS0004")
                || slot.getId().equals("WS0005") || slot.isHardpoint()) return;

        Vector2f offset = ship.getHullSpec().getWeaponSlot(weapon.getSlot().getId()).getLocation();

        MagicRender.screenspace(
                sprite,
                MagicRender.positioning.CENTER,
                new Vector2f(screenPos.x - offset.y * scale, screenPos.y + offset.x * scale),
                new Vector2f(0f, 0f),
                new Vector2f(sprite.getWidth() * scale, sprite.getHeight() * scale),
                new Vector2f(0f, 0f),
                swayAngle,
                0f,
                c,
                false,
                0f, 0f, 0f, 0f, 0f,
                0f, -1f, 0f,
                CombatEngineLayers.CLOUD_LAYER
        );
    }

    private void renderModuleScreenspace(ShipAPI module, float scale, Color c,
                                          Vector2f screenPos, float zoom, float swayAngle) {
        if (module.getParentStation() == null) return;
        if (module.getStationSlot() == null) return;
        if (module.isHulk() || !module.isAlive()) return;

        SpriteAPI sprite = Global.getSettings().getSprite(module.getHullSpec().getSpriteName());
        Vector2f offset = ship.getHullSpec().getWeaponSlot(module.getStationSlot().getId()).getLocation();

        if (sprite != null) {
            MagicRender.screenspace(
                    sprite,
                    MagicRender.positioning.CENTER,
                    new Vector2f(screenPos.x - offset.y * scale, screenPos.y + offset.x * scale),
                    new Vector2f(0f, 0f),
                    new Vector2f(sprite.getWidth() * scale, sprite.getHeight() * scale),
                    new Vector2f(0f, 0f),
                    swayAngle + ship.getHullSpec().getWeaponSlot(module.getStationSlot().getId()).getAngle(),
                    0f,
                    c,
                    false,
                    0f, 0f, 0f, 1f, 0f,
                    0f, -1f, 0f,
                    CombatEngineLayers.CLOUD_LAYER
            );
        }
    }

    // -----------------------------------------------------------------------
    // Util
    // -----------------------------------------------------------------------

    private static Vector2f toCenter(Vector2f worldPos) {
        ViewportAPI vp = Global.getCombatEngine().getViewport();
        float sx = vp.convertWorldXtoScreenX(worldPos.x);
        float sy = vp.convertWorldYtoScreenY(worldPos.y);
        float hw = Global.getSettings().getScreenWidth()  / 2f;
        float hh = Global.getSettings().getScreenHeight() / 2f;
        return new Vector2f(sx - hw, -(sy - hh));
    }

    private Color brightnessColor(float brightness) {
        return new Color(
                (int)(tint.getRed()   * brightness),
                (int)(tint.getGreen() * brightness),
                (int)(tint.getBlue()  * brightness),
                255
        );
    }
}