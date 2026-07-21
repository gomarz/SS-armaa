package data.scripts.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.List;

/**
 * Sandevistan-style chromatic afterimage trail for the HIMAC quick boost.
 *
 * One instance per dash: spawn it from boost() via
 *     armaa_ghostTrail.spawn(ship, 0.35f);
 * and it removes itself when the timer expires or the ship dies.
 *
 * Ghosts are spaced by DISTANCE traveled, not time, so trail density
 * directly encodes speed and a stationary ship produces nothing.
 * Re-boosting during an active trail refreshes the existing instance
 * instead of stacking a second one (spawn() handles this via customData).
 */
public class armaa_ghostTrail extends BaseEveryFrameCombatPlugin {

    /** customData key prefix so re-boosts find the live instance. */
    private static final String KEY_PREFIX = "armaa_ghostTrail_";

    /** World-space gap between ghosts. ~8-14 ghosts over a full-speed carry. */
    private static final float GHOST_SPACING = 35f;

    /** Chromatic split half-offset in the ship's local frame (su). */
    private static final float SPLIT_OFFSET = 4f;

    private static final Color RED  = new Color(255, 50, 80);
    private static final Color CYAN = new Color(50, 220, 255);
    private static final Color BODY = new Color(200, 235, 255);

    private final ShipAPI ship;
    private final float duration;
    private float timer;
    private final Vector2f lastGhostLoc = new Vector2f();

    /**
     * Preferred entry point: creates a trail for this ship, or refreshes
     * the one already running so overlapping boosts don't double density.
     */
    public static void spawn(ShipAPI ship, float duration) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || ship == null) return;

        String key = KEY_PREFIX + ship.getId();
        Object existing = engine.getCustomData().get(key);
        if (existing instanceof armaa_ghostTrail) {
            armaa_ghostTrail trail = (armaa_ghostTrail) existing;
            if (!trail.expired()) {
                trail.timer = duration; // refresh, don't stack
                return;
            }
        }
        armaa_ghostTrail trail = new armaa_ghostTrail(ship, duration);
        engine.getCustomData().put(key, trail);
        engine.addPlugin(trail);
    }

    private armaa_ghostTrail(ShipAPI ship, float duration) {
        this.ship = ship;
        this.duration = Math.max(0.01f, duration);
        this.timer = duration;
        this.lastGhostLoc.set(ship.getLocation());
    }

    private boolean expired() {
        return timer <= 0f;
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;

        // Self-removal: expiry or ship gone.
        if (expired() || ship == null || !engine.isEntityInPlay(ship) || !ship.isAlive()) {
            cleanup(engine);
            return;
        }

        timer -= amount;

        // Only spawn when the ship has actually moved a ghost-length,
        // and skip the work entirely when off-screen.
        if (MathUtils.getDistanceSquared(lastGhostLoc, ship.getLocation())
                < GHOST_SPACING * GHOST_SPACING) {
            return;
        }
        if (!engine.getViewport().isNearViewport(ship.getLocation(), 300f)) {
            lastGhostLoc.set(ship.getLocation()); // keep spacing honest off-screen
            return;
        }
        lastGhostLoc.set(ship.getLocation());

        // Freeze ghosts in world space: afterimages ride the ship's frame,
        // so counter-velocity pins them where they were spawned.
        float vx = -ship.getVelocity().x;
        float vy = -ship.getVelocity().y;

        // Trail intensity decays with the remaining window.
        float t = Math.max(0f, timer / duration);
        int a = (int) (110 * t) + 30;

        // Chromatic pair: additive, offset perpendicular in local frame.
        ship.addAfterimage(withAlpha(RED, a),  SPLIT_OFFSET, 0f,
                vx, vy, 0f, 0f, 0f, 0.55f, true, false, false);
        ship.addAfterimage(withAlpha(CYAN, a), -SPLIT_OFFSET, 0f,
                vx, vy, 0f, 0f, 0f, 0.55f, true, false, false);
        // Faint solid body ghost between them keeps the silhouette readable.
        ship.addAfterimage(withAlpha(BODY, (int) (a * 0.6f)), 0f, 0f,
                vx, vy, 0f, 0f, 0f, 0.45f, false, true, false);
    }

    private void cleanup(CombatEngineAPI engine) {
        engine.getCustomData().remove(KEY_PREFIX + (ship != null ? ship.getId() : ""));
        engine.removePlugin(this);
    }

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(),
                Math.max(0, Math.min(255, alpha)));
    }
}