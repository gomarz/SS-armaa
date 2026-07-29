package data.scripts.shipsystems.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.combat.BeamAPI;

import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;
import org.lazywizard.lazylib.VectorUtils;

/**
 * AI for the heavy charge cannon (BETA/ALPHA charge-and-release ship system).
 *
 * The system is a TOGGLE: useSystem() flips it. - while IDLE -> useSystem()
 * STARTS the charge - while CHARGING -> useSystem() RELEASES (fires at current
 * tier)
 *
 * So this AI is a two-phase machine. It never tracks phase with a local
 * boolean; it reads the real system state (system.isActive()) and the charge
 * tier the effect script publishes to custom data
 * (armaa_chargeCannonTier_<shipId>).
 *
 * Mode (BETA vs ALPHA) is owned by the transform AI in armaa_guarDualEffect2 —
 * this script does NOT pick the mode. It just plays the cannon well in whatever
 * mode the ship is currently in.
 */
public class armaa_heavyChargeCannonAI implements ShipSystemAIScript {

    private ShipAPI ship;
    private ShipSystemAPI system;
    private ShipwideAIFlags flags;
    private CombatEngineAPI engine;

    // ---- tunables ----------------------------------------------------------
    // firing geometry
    private static final float FIRE_ARC_HALF = 15f;       // degrees off-facing the target may be to count as "lined up" (AoE forgives imprecision)
    private static final float RANGE = 600f;              // must match BETA_HITSCAN_RANGE / alpha range
    private static final float RANGE_SLACK = 1.25f;       // allow targets a touch beyond nominal range when deciding to start
// vs fighters/drones, tier 2 (500 HE blast + EMP splash) already wrecks a wing;
// charging to 4 wastes ~3s pinned slow and eats the 18s cooldown on a target
// that dies to tier 2, leaving the cannon down for whatever they were screening.
    private static final int FIGHTER_TIER = 2;
    // flux safety: don't commit to charging (slow + extra shield damage) when near overload
    private static final float MAX_FLUX_TO_START = 0.50f; // above this hard-flux, don't START a charge
    private static final float ABORT_FLUX = 0.70f;        // above this hard-flux while charging, bail

    // tier the AI is happy to release at when the window is stable (1..4); it
    // will release EARLIER if the window is closing, and hold toward this if safe.
    private static final int TARGET_TIER = 4;

    // failsafe: if we sit at MAX charge (tier 4) this long without finding a clean
    // shot, fire anyway rather than loiter slowed forever. The AoE may still catch
    // something, and we stop being pinned at COMMIT_SPEED_MULT.
    private static final int MAX_TIER = 4;
    private static final float MAX_CHARGE_DWELL_LIMIT = 5f;
    private float maxChargeDwell = 0f;                    // persists between frames (instance is per-ship)

    // don't start a charge against a target we can't plausibly aim at: if it's
    // crossing our facing faster than we can turn (being kited/circled), charging
    // just pins us slow with no payoff.
    private static final float MAX_TRACK_LATERAL = 260f;  // su/s of lateral (perp) target velocity we'll still commit to

    // re-eval cadence so the AI doesn't toggle multiple times in adjacent frames
    private float decisionCooldown = 0f;
    private static final float DECISION_INTERVAL = 0.15f;

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.system = system;
        this.flags = flags;
        this.engine = engine;
    }

    @Override
    public void advance(float amount, Vector2f shipLocation, Vector2f shipTarget, ShipAPI target) {
        if (ship == null || system == null || engine == null) {
            return;
        }
        if (!ship.isAlive() || engine.isPaused()) {
            return;
        }

        // Track time spent sitting at MAX charge, every frame (before the decision
        // throttle), so the failsafe fires even if decisionCooldown would skip a frame.
        boolean chargingNow = system.isActive();
        if (chargingNow) {
            maxChargeDwell += amount;
            if (shipTarget != null) {
                if (MathUtils.getDistance(ship, shipTarget) > 900f) {
                    engine.headInDirectionWithoutTurning(ship, VectorUtils.getAngle(ship.getLocation(), shipTarget), ship.getMaxSpeed());
                }
            }
        } else {
            maxChargeDwell = 0f;
        }

        // throttle decisions: a toggle should not be re-issued every frame
        decisionCooldown -= amount;
        if (decisionCooldown > 0f) {
            return;
        }

        // can't toggle while the system is on cooldown / unusable
        if (!system.isActive() && !AIUtils.canUseSystemThisFrame(ship) && system.getCooldownRemaining() > 0f) {
            return;
        }

        boolean charging = system.isActive();
        int tier = readTier();

        if (!charging) {
            // ---- PHASE IDLE: decide whether to START charging -------------------
            if (shouldStartCharge(target)) {
                ship.useSystem();
                decisionCooldown = DECISION_INTERVAL;
            }
        } else {
            // ---- PHASE CHARGING: abandon / fire / keep --------------------------
            int decision = chargingDecision(target, tier);
            if (decision != KEEP) {
                // both ABANDON and FIRE physically toggle off; the difference is
                // only whether resolveFire produces a shot (tier 0 = free cancel).
                ship.useSystem();
                decisionCooldown = DECISION_INTERVAL;
            }
        }
    }

    // ---- IDLE: should we begin a charge? --------------------------------------
    private boolean shouldStartCharge(ShipAPI target) {
        if (target == null || !target.isAlive()) {
            return false;
        }
        if (target.getOwner() == ship.getOwner()) {
            return false;
        }
        // flux: charging slows us and increases shield damage taken; don't commit when high
        if (ship.getHardFluxLevel() > MAX_FLUX_TO_START) {
            return false;
        }
        // don't start a charge while being hammered (commitment penalty too costly)
        if (isUnderHeavyFire()) {
            return false;
        }
        // target must be roughly in front and within range to be worth charging at
        if (!isLinedUp(target, RANGE * RANGE_SLACK)) {
            return false;
        }
        // don't commit to a charge against a target crossing our facing faster than we
        // can track: it'll just pin us slow (COMMIT_SPEED_MULT) while it kites out of arc.
        if (lateralSpeed(target) > MAX_TRACK_LATERAL) {
            return false;
        }
        // prefer charging when the shot WON'T be eaten by the target's shield (the "flank" case).
        // not a hard requirement — a shielded target is still worth pressuring — but it raises priority.
        // here we simply allow it; weighting toward unshielded happens by being more eager (no extra gate).
        return true;
    }

    // ---- CHARGING: keep / abandon / fire --------------------------------------
    private static final int KEEP = 0;
    private static final int FIRE = 1;
    private static final int ABANDON = 2;

    private int chargingDecision(ShipAPI target, int tier) {
        boolean belowTier1 = tier <= 0;

        // hard bail conditions
        boolean fluxCritical = ship.getHardFluxLevel() > ABORT_FLUX;
        boolean targetGone = (target == null || !target.isAlive() || target.getOwner() == ship.getOwner());
        boolean targetOutOfPlay = !targetGone && !isLinedUp(target, RANGE * RANGE_SLACK);
        boolean heavyFire = isUnderHeavyFire();

        boolean badNow = fluxCritical || targetGone || targetOutOfPlay || heavyFire;

        if (belowTier1) {
            // below tier 1 there is no shot and no cooldown: toggling off is a FREE cancel.
            // so cancel the instant conditions go bad; otherwise keep charging toward tier 1.
            return badNow ? ABANDON : KEEP;
        }

        // tier 1+: there is no free cancel. Toggling off FIRES at current tier and
        // incurs that tier's cooldown. So "abandon" and "fire" are the same action;
        // we fire if things went bad (might as well get the shot we paid for) OR if
        // we've reached our target tier OR the firing window is closing.
        boolean windowClosing = isWindowClosing(target);
boolean reachedTargetTier = tier >= desiredTier(target);   // was: tier >= TARGET_TIER

        // failsafe: we've sat at max charge too long without a clean release -> just fire.
        // stops the "stuck full-charge, pinned slow, can't line up" loiter.
        boolean dwellExpired = maxChargeDwell >= MAX_CHARGE_DWELL_LIMIT;

        if (badNow || windowClosing || reachedTargetTier || dwellExpired) {
            return FIRE;
        }
        return KEEP;
    }


    private int readTier() {
        Object v = engine.getCustomData().get("armaa_chargeCannonTier_" + ship.getId());
        return (v instanceof Integer) ? (Integer) v : 0;
    }

    private boolean isFighterTarget(ShipAPI target) {
        return target != null && (target.isFighter() || target.isDrone());
    }

    private int desiredTier(ShipAPI target) {
        return isFighterTarget(target) ? FIGHTER_TIER : TARGET_TIER;
    }

 
    private boolean isLinedUp(ShipAPI target, float range) {
        if (target == null) {
            return false;
        }
        float dist = MathUtils.getDistance(ship, target);
        if (dist > range) {
            return false;
        }
        float angleToTarget = Misc_angleFromShipTo(target);
        float off = Math.abs(MathUtils.getShortestRotation(ship.getFacing(), angleToTarget));
        return off <= FIRE_ARC_HALF;
    }

    /**
     * Window closing = target drifting out of the fire arc (about to break the
     * shot line).
     */
    private boolean isWindowClosing(ShipAPI target) {
        if (target == null || !target.isAlive()) {
            return true;
        }
        float angleToTarget = Misc_angleFromShipTo(target);
        float off = Math.abs(MathUtils.getShortestRotation(ship.getFacing(), angleToTarget));
        // if it's near the edge of our arc, the window is closing
        return off > FIRE_ARC_HALF * 0.75f;
    }

    private float Misc_angleFromShipTo(ShipAPI target) {
        return org.lazywizard.lazylib.VectorUtils.getAngle(ship.getLocation(), target.getLocation());
    }

    /**
     * Target's velocity component PERPENDICULAR to our line of sight to it
     * (su/s). High lateral speed = it's crossing our aim and we likely can't
     * keep it in arc while charging.
     */
    private float lateralSpeed(ShipAPI target) {
        if (target == null) {
            return 0f;
        }
        Vector2f vel = target.getVelocity();
        float losAngle = Misc_angleFromShipTo(target); // degrees, ship -> target
        // unit vector perpendicular to LoS
        double rad = Math.toRadians(losAngle);
        float perpX = (float) -Math.sin(rad);
        float perpY = (float) Math.cos(rad);
        return Math.abs(vel.x * perpX + vel.y * perpY);
    }

    /**
     * Heavy incoming fire: AI flag, or an active beam currently targeting us.
     */
    private boolean isUnderHeavyFire() {
        if (flags != null && flags.hasFlag(AIFlags.HAS_INCOMING_DAMAGE) && ship.getHardFluxLevel() > 0.30f) {
            return true;
        }
        for (BeamAPI beam : engine.getBeams()) {
            if (beam.getDamageTarget() == ship) {
                return true;
            }
        }
        return false;
    }
}
