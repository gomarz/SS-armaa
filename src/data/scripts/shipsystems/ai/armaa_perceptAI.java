package data.scripts.shipsystems.ai;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

public class armaa_perceptAI implements ShipSystemAIScript {

    private ShipAPI ship;
    private CombatEngineAPI engine;
    private ShipwideAIFlags flags;
    private ShipSystemAPI system;
    private final IntervalUtil tick = new IntervalUtil(0.06f, 0.12f);

    // === Tunables ===
    // Orb building band (only build in this flux range)
    private static final float BUILD_FLUX_MIN = 0.35f;
    private static final float BUILD_FLUX_MAX = 0.75f;

    // Fighter-system baseline preference (after flux gate is cleared)
    private static final float SHOOT_BASE_BIAS = 0.35f;

    // Flux gating & hysteresis for fighter shots
    private static final float SHOOT_FLUX_GATE = 0.15f; // don’t consider SHOOT below this
    private static final float SHOOT_FLUX_RAMP = 0.70f; // where shooting becomes VERY attractive
    private static final float SHOOT_FLUX_HYST = 0.08f; // need to drop below (gate - HYST) to “wait” again

    // Only pre-transform to fighter when flux is close to the gate (or SHOOT dominates)
    private static final float PREPARE_TO_SHOOT_FLUX = 0.45f;

    // Press pacing
    private static final float MIN_USE_GAP = 0.9f; // seconds between system uses

    // Ranges & angles
    private static final float TARGET_PICK_RANGE = 1200f; // find candidate target
    private static final float LAUNCH_RANGE      = 1100f; // orb launch good-shot range
    private static final float LAUNCH_ARC        = 45f;   // front cone for orb launch
    private static final float FAR_R             = 1200f; // clamp for enemy queries

    // Favor SHOOT if enemies are far away on average
    private static final float FAR_DIST_FOR_SHOOT = 800f;

    // Orb cap: we try to keep ammo - 1 orbs, but never above this hard cap
    private static final int DESIRED_ORB_CAP_HARD = 3;

    // Proximity bubbles for “pressure” (affects build inclination)
    private static final float NEAR_R = 450f;
    private static final float MID_R  = 800f;

    // Orb missile weapon id (unarmed holder)
    private static final String ORB_WEAPON_ID = "armaa_curvyLaser";

    // --- State & bookkeeping ---
    private enum Phase { BUILD, HOLD, LAUNCH, SHOOT, COOLDOWN }
    private Phase state = Phase.BUILD;

    private String KEY_STATE, KEY_LAST_USE;
    private float lastShootFluxDecision = 0f;

    private float now() { return engine != null ? engine.getTotalElapsedTime(false) : 0f; }

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.flags = flags;
        this.engine = engine;
        this.system = (system != null) ? system : ship.getSystem();

        KEY_STATE    = "armaa_guardual_state_" + ship.getId();
        KEY_LAST_USE = "armaa_guardual_lastuse_" + ship.getId();

        Object s = engine.getCustomData().get(KEY_STATE);
        if (s instanceof Phase) state = (Phase) s;
    }

    // Hint transformation via customData when safe
    public void forceTransform() {
        for (BeamAPI beam : engine.getBeams()) {
            if (beam.getDamageTarget() == ship) return;
        }
        if (flags.hasFlag(ShipwideAIFlags.AIFlags.HAS_INCOMING_DAMAGE)) return;
        if (!engine.getCustomData().containsKey("armaa_transformNow_" + ship.getId())) {
            engine.getCustomData().put("armaa_transformNow_" + ship.getId(), "-");
        }
    }

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI targetHint) {
        if (engine == null || engine.isPaused()) return;
        if(!ship.areAnyEnemiesInRange())
            return;
        tick.advance(amount);
        if (!tick.intervalElapsed()) return;
        if (ship.getFluxTracker().isOverloaded() || ship.getFluxTracker().isVenting()) return;
        if (system == null || system.getCooldownRemaining() > 0f || system.getAmmo() <= 0) return;

        // Transform state from  effect class
        boolean isRobot = engine.getCustomData().get("armaa_tranformState_" + ship.getId()) instanceof Boolean
                && (Boolean) engine.getCustomData().get("armaa_tranformState_" + ship.getId());

        int ammo   = system.getAmmo();
        int orbs   = countMyOrbs();
        float flux = ship.getFluxLevel();

        lastShootFluxDecision = flux; // used by hysteresis helper

        int desiredOrbs = Math.max(0, Math.min(DESIRED_ORB_CAP_HARD, ammo - 1));

        float lastUse = 0f;
        Object lu = engine.getCustomData().get(KEY_LAST_USE);
        if (lu instanceof Float) lastUse = (Float) lu;
        boolean canPress = (now() - lastUse) >= MIN_USE_GAP;

        // Keep flux when holding orbs
        if (orbs > 0) flags.setFlag(ShipwideAIFlags.AIFlags.DO_NOT_VENT, 0.75f);

        // Must-launch rule: if we’re on last charge and have orbs, prep to LAUNCH
        if (ammo == 1 && orbs > 0) state = Phase.LAUNCH;

        // Contacts
        List<ShipAPI> enemies = gatherEnemies(FAR_R);
        ShipAPI tgt = pickTarget(enemies);
        boolean launchSolution = hasLaunchSolution(tgt);

        float pressure = enemyPressure(enemies);     // weighted count by proximity
        float avgDist  = averageDistance(enemies);
        boolean enemiesFar = (Float.isNaN(avgDist) || avgDist > FAR_DIST_FOR_SHOOT);

        // ---- Build vs Shoot Scores ----
        float buildScore = 0f;
        buildScore += isRobot ? 0.35f : 0f;                          // easier to build in robot
        buildScore += Math.max(0, (desiredOrbs - orbs)) * 0.30f;     // fill to desired
        if (flux >= BUILD_FLUX_MIN && flux <= BUILD_FLUX_MAX) buildScore += 0.25f;
        buildScore += Math.min(1f, pressure * 0.25f);                // more/closer enemies - build more
        if (ammo <= 1) buildScore *= 0.25f;                          // don’t build if we can’t spare a charge

        float shootScore;
        float fluxWeight = shootFluxWeight(flux);                    // 0..1
        if (!fluxClearsShootGate(flux)) {
            shootScore = 0.05f * SHOOT_BASE_BIAS;                    // basically "not now"
        } else {
            shootScore  = SHOOT_BASE_BIAS * 0.6f;                    // smaller baseline once gated
            shootScore += (!isRobot ? 0.25f : 0f);                   // already in fighter mode
            shootScore += launchSolution ? 0.25f : 0f;               // decent shot lined up
            shootScore += 0.6f * fluxWeight;                         // MAIN driver: more flux - more desire
            shootScore += enemiesFar ? 0.2f : 0f;                    // if far, bias to shoot
        }

        // === FSM ===
        switch (state) {
            case BUILD:
                if (orbs >= desiredOrbs) { state = Phase.HOLD; break; }

                // SHOOT clearly better? pivot
                if (shootScore > buildScore * 1.15f) { state = Phase.SHOOT; break; }

                // Ensure robot mode to create orbs
                if (!isRobot) {
                    if (!flags.hasFlag(ShipwideAIFlags.AIFlags.HAS_INCOMING_DAMAGE)) forceTransform();
                    break;
                }

                // Build only inside flux band, paced
                if (flux >= BUILD_FLUX_MIN && flux <= BUILD_FLUX_MAX && canPress) {
                    ship.useSystem(); stampUse();
                }
                break;

            case HOLD:
                if (orbs < desiredOrbs) { state = Phase.BUILD; break; }

                // Pre-transform to fighter only when near gate or SHOOT dominates
                if (isRobot && !flags.hasFlag(ShipwideAIFlags.AIFlags.HAS_INCOMING_DAMAGE)) {
                    if (flux >= PREPARE_TO_SHOOT_FLUX || shootScore > buildScore * 1.25f) {
                        forceTransform();
                    }
                }

                // If shooting looks better right now → SHOOT
                if (shootScore > buildScore * 1.05f) state = Phase.SHOOT;
                break;

            case LAUNCH:
                // Need to be in plane to launch buffered orbs
                if (isRobot) {
                    if (!flags.hasFlag(ShipwideAIFlags.AIFlags.HAS_INCOMING_DAMAGE)) forceTransform();
                    break;
                }
                if (orbs > 0 && canPress && launchSolution) {
                    ship.useSystem(); stampUse();
                    state = Phase.COOLDOWN;
                }
                break;

            case SHOOT:
                // Opportunistic fighter-mode swarmer usage
                if (isRobot) {
                    // If still robot, either keep building or prep to plane
                    if (buildScore > shootScore * 1.05f) { state = Phase.BUILD; }
                    else if (!flags.hasFlag(ShipwideAIFlags.AIFlags.HAS_INCOMING_DAMAGE)
                            && (flux >= PREPARE_TO_SHOOT_FLUX || shootScore > buildScore * 1.25f)) {
                        forceTransform();
                    }
                    break;
                }
                if (canPress && tgt != null && ship.areAnyEnemiesInRange() && fluxClearsShootGate(flux)) {
                    ship.useSystem(); stampUse();
                    state = Phase.COOLDOWN;
                } else {
                    state = (orbs < desiredOrbs) ? Phase.BUILD : Phase.HOLD;
                }
                break;

            case COOLDOWN:
                if (canPress) {
                    if (ammo == 1 && orbs > 0) state = Phase.LAUNCH;
                    else if (orbs < desiredOrbs && buildScore >= shootScore) state = Phase.BUILD;
                    else if (shootScore > buildScore) state = Phase.SHOOT;
                    else state = Phase.HOLD;
                }
                break;
        }

        engine.getCustomData().put(KEY_STATE, state);
    }

    private void stampUse() { engine.getCustomData().put(KEY_LAST_USE, now()); }

    // ==== Helpers ====

    /** Count our unarmed “orb” missiles. */
    private int countMyOrbs() {
        int n = 0;
        for (MissileAPI m : CombatUtils.getMissilesWithinRange(ship.getLocation(), FAR_R)) {
            if (m.getOwner() != ship.getOwner()) continue;
            if (m.getSource() != ship) continue;
            if (m.isArmed()) continue; // your orbs are the unarmed holders
            WeaponSpecAPI spec = m.getWeaponSpec();
            if (spec != null && ORB_WEAPON_ID.equals(spec.getWeaponId())) n++;
        }
        return n;
    }

    /** Enemies within clamp radius. */
    private List<ShipAPI> gatherEnemies(float clamp) {
        List<ShipAPI> out = new ArrayList<ShipAPI>();
        for (ShipAPI s : CombatUtils.getShipsWithinRange(ship.getLocation(), clamp)) {
            if (s.getOwner() == ship.getOwner() || s.isHulk() || !s.isAlive()) continue;
            out.add(s);
        }
        return out;
    }

    /** Prefer high-flux, closer targets. */
    private ShipAPI pickTarget(List<ShipAPI> enemies) {
        ShipAPI best = null;
        float bestScore = -1f;
        for (ShipAPI s : enemies) {
            float d = Math.max(100f, MathUtils.getDistance(ship, s));
            if (d > TARGET_PICK_RANGE) continue;
            float score = (1f + s.getFluxLevel()) * (1f / d);
            if (score > bestScore) { best = s; bestScore = score; }
        }
        return best;
    }

    /** “Good shot” check for launching orbs. */
    private boolean hasLaunchSolution(ShipAPI tgt) {
        if (tgt == null) return false;
        float dist = MathUtils.getDistance(ship, tgt);
        if (dist > LAUNCH_RANGE) return false;
        float angleTo = MathUtils.getShortestRotation(ship.getFacing(),
                VectorUtils.getAngle(ship.getLocation(), tgt.getLocation()));
        return Math.abs(angleTo) <= LAUNCH_ARC;
    }

    /** Enemy pressure: weighted count by proximity (near > mid > far). */
    private float enemyPressure(List<ShipAPI> enemies) {
        float p = 0f;
        for (ShipAPI s : enemies) {
            float d = MathUtils.getDistance(ship, s);
            if (d <= NEAR_R) p += 1.0f;
            else if (d <= MID_R) p += 0.6f;
            else if (d <= FAR_R) p += 0.3f;
        }
        return p;
    }

    private float averageDistance(List<ShipAPI> enemies) {
        if (enemies.isEmpty()) return Float.NaN;
        float sum = 0f;
        for (ShipAPI s : enemies) sum += MathUtils.getDistance(ship, s);
        return sum / enemies.size();
    }

    // ----- Flux gating for fighter shots -----

    private float shootFluxWeight(float flux) {
        // 0 below gate, ramps to 1 near RAMP using smoothstep
        if (flux <= SHOOT_FLUX_GATE) return 0f;
        if (flux >= SHOOT_FLUX_RAMP) return 1f;
        float t = (flux - SHOOT_FLUX_GATE) / Math.max(0.01f, (SHOOT_FLUX_RAMP - SHOOT_FLUX_GATE));
        return t * t * (3f - 2f * t);
    }

    private boolean fluxClearsShootGate(float flux) {
        // hysteresis: if we recently were above gate, don’t flip back until below gate - HYST
        if (lastShootFluxDecision >= SHOOT_FLUX_GATE) {
            return flux >= Math.max(0f, SHOOT_FLUX_GATE - SHOOT_FLUX_HYST);
        }
        return flux >= SHOOT_FLUX_GATE;
    }
}
