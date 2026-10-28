package data.scripts.campaign.stealth;

/**
 * Follows the player around a level bigger than the view. No game classes in
 * here, so the panel and the test harness share it.
 *
 * x, y is the bottom-left corner of the view in world units. The camera eases
 * toward the player, leans a little way ahead of where they are heading so
 * there is more to see in front than behind, and never shows past the edge of
 * the level.
 */
public class armaa_stealthCamera {

    /** How quickly the view catches up, per second (higher is stiffer). */
    public static final float FOLLOW_RATE = 5f;
    /** Seconds of the player's current velocity to look ahead by. */
    public static final float LOOK_AHEAD = 0.5f;

    public float x, y;

    /** Jumps straight to where the camera should be for this player, with no easing. */
    public void snap(armaa_stealthSim sim) {
        x = targetX(sim);
        y = targetY(sim);
    }

    public void follow(armaa_stealthSim sim, float dt) {
        float k = Math.min(1f, FOLLOW_RATE * dt);
        x += (targetX(sim) - x) * k;
        y += (targetY(sim) - y) * k;
    }

    private float targetX(armaa_stealthSim sim) {
        float t = sim.px + sim.vx * LOOK_AHEAD - armaa_stealthSim.VIEW_W / 2f;
        return clamp(t, sim.width - armaa_stealthSim.VIEW_W);
    }

    private float targetY(armaa_stealthSim sim) {
        float t = sim.py + sim.vy * LOOK_AHEAD - armaa_stealthSim.VIEW_H / 2f;
        return clamp(t, sim.height - armaa_stealthSim.VIEW_H);
    }

    /** Keeps the view inside the level; a level smaller than the view just sits at the corner. */
    private static float clamp(float v, float max) {
        return Math.max(0f, Math.min(Math.max(0f, max), v));
    }
}
