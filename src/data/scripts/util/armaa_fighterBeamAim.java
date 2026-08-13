package data.scripts.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.AutofireAIPlugin;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponGroupAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;

/**
 * Per-weapon aiming controller for beam weapons mounted on fighters.
 *
 * Solves three problems:
 *   1) Fighter AI peels off to rearm the instant ammo hits 0, even if the beam
 *      is still firing. Fixed by holding ammo at 1 for the duration of the burst.
 *   2) The fighter's own facing wanders during the burn. Fixed by locking a
 *      validated target for the whole burst and steering toward it smoothly.
 *   3) The beam snapping across the screen when a target dies mid-burn. Fixed by
 *      slewing the barrel at a bounded rate, holding the last known heading for a
 *      moment before re-acquiring, and refusing re-acquires that need a wild swing.
 *
 * LIFECYCLE: no registration or cleanup needed. EveryFrameWeaponEffectPlugin is
 * instantiated once per weapon instance, so one of these lives alongside each
 * weapon and is garbage collected with it when the fighter dies. The only strong
 * reference held (lockedTarget) is nulled the frame the beam stops.
 */
public class armaa_fighterBeamAim {

    /** Hold ammo at 1 while the beam is live so fighter AI won't bail to rearm. */
    public static final boolean HOLD_AMMO_WHILE_FIRING = true;

    /** If true, only take over aiming on the final shot (original behavior). */
    public static final boolean AIM_ONLY_WHEN_LAST_SHOT = false;

    /** Multiplier on the fighter's own turn rate for the HULL while locked on. */
    public static final float TURN_RATE_MULT = 1.5f;

    /** How fast the barrel tracks onto a target, deg/sec. */
    public static final float AIM_SLEW_RATE = 200f;

    /** Seconds to hold the last heading after losing a target before re-acquiring. */
    public static final float REACQUIRE_DELAY = 0.35f;

    /** Reject a mid-burst re-acquire that would need more than this much swing, deg. */
    public static final float MAX_REACQUIRE_SWING = 100f;

    /** Slack past nominal weapon range before a locked target is dropped. */
    public static final float RANGE_TOLERANCE = 1.15f;

    /** Also block forward/back thrust during the burn. Off by default. */
    public static final boolean BLOCK_THRUST = false;

    private CombatEntityAPI lockedTarget = null;
    private Vector2f lastKnownLoc = null;
    private float aimAngle = 0f;
    private float reacquireTimer = 0f;
    private boolean holdingAmmo = false;
    private boolean wasFiring = false;

    /**
     * Call once per frame from the weapon effect plugin, BEFORE any screen-check
     * or rendering early-returns.
     */
    public void advance(float amount, ShipAPI ship, WeaponAPI weapon) {
        if (ship == null || weapon == null || !ship.isFighter()) {
            return;
        }

        final boolean firing = weapon.isFiring();
        final boolean usesAmmo = weapon.usesAmmo();

        // ---- 1. ammo hold: never let the AI see "out of ammo" mid-beam ----
        if (HOLD_AMMO_WHILE_FIRING && usesAmmo) {
            if (firing) {
                if (weapon.getAmmo() <= 0) {
                    weapon.setAmmo(1);
                    holdingAmmo = true;
                }
            } else if (holdingAmmo) {
                // Beam dropped this frame -- hand the zero straight back so the
                // weapon can't chain into another burst on borrowed ammo.
                weapon.setAmmo(0);
                holdingAmmo = false;
            }
        }

        // ---- 2. release everything as soon as the beam stops ----
        if (!firing) {
            resetBurstState();
            return;
        }

        // Optional: restrict takeover to the last shot, matching the original code.
        if (AIM_ONLY_WHEN_LAST_SHOT && usesAmmo && !holdingAmmo && weapon.getAmmo() > 0) {
            resetBurstState();
            return;
        }

        // ---- 3. burst start: seed the aim from where the barrel already points ----
        if (!wasFiring) {
            wasFiring = true;
            aimAngle = weapon.getCurrAngle();
            reacquireTimer = 0f;
            lastKnownLoc = null;
            lockedTarget = pickTarget(ship, weapon, false);
        }

        // ---- 4. target lost: remember where it was, then wait before re-picking ----
        if (!isValidTarget(ship, weapon, lockedTarget)) {
            if (lockedTarget != null) {
                lastKnownLoc = new Vector2f(lockedTarget.getLocation());
                lockedTarget = null;
                reacquireTimer = REACQUIRE_DELAY;
            }
            if (reacquireTimer > 0f) {
                reacquireTimer -= amount;
            } else {
                lockedTarget = pickTarget(ship, weapon, true);
            }
        }

        Vector2f aimAt = (lockedTarget != null) ? lockedTarget.getLocation() : lastKnownLoc;
        if (aimAt == null) {
            return;
        }

        // ---- 5. take the wheel ----
        ship.blockCommandForOneFrame(ShipCommand.TURN_LEFT);
        ship.blockCommandForOneFrame(ShipCommand.TURN_RIGHT);
        if (BLOCK_THRUST) {
            ship.blockCommandForOneFrame(ShipCommand.ACCELERATE);
            ship.blockCommandForOneFrame(ShipCommand.ACCELERATE_BACKWARDS);
        }

        if (lockedTarget instanceof ShipAPI) {
            ship.setShipTarget((ShipAPI) lockedTarget);
        }

        final float desired = VectorUtils.getAngle(ship.getLocation(), aimAt);

        // Barrel eases toward the target instead of teleporting onto it.
        aimAngle = approach(aimAngle, desired, AIM_SLEW_RATE * amount);
        weapon.setCurrAngle(aimAngle);

        // Hull follows the barrel, not the raw target angle, so the two agree.
        final float hullStep =
                ship.getMutableStats().getMaxTurnRate().getModifiedValue() * TURN_RATE_MULT * amount;
        ship.setFacing(approach(ship.getFacing(), aimAngle, hullStep));
        // Otherwise the fighter keeps drifting the moment we let go.
        ship.setAngularVelocity(0f);
    }

    private void resetBurstState() {
        lockedTarget = null;
        lastKnownLoc = null;
        reacquireTimer = 0f;
        wasFiring = false;
    }

    /** Rotate current toward target by at most maxStep degrees. */
    private static float approach(float current, float target, float maxStep) {
        float diff = MathUtils.getShortestRotation(current, target);
        if (Math.abs(diff) <= maxStep) {
            return Misc.normalizeAngle(target);
        }
        return Misc.normalizeAngle(current + Math.signum(diff) * maxStep);
    }

    /**
     * Pulls the weapon's own autofire target. The plugin lives on the weapon
     * GROUP, and the group can be null on fighters / built-in mounts, so both
     * lookups are guarded.
     */
    private ShipAPI autofireTarget(ShipAPI ship, WeaponAPI weapon) {
        WeaponGroupAPI group = ship.getWeaponGroupFor(weapon);
        if (group == null) {
            return null;
        }
        AutofireAIPlugin af = group.getAutofirePlugin(weapon);
        return (af == null) ? null : af.getTargetShip();
    }

    /**
     * Best-to-worst target sources. The autofire plugin knows what the beam is
     * actually on. When midBurst is true, candidates that would require a wild
     * swing are rejected -- better to keep burning where we're pointed.
     */
    private CombatEntityAPI pickTarget(ShipAPI ship, WeaponAPI weapon, boolean midBurst) {
        ShipAPI afTarget = autofireTarget(ship, weapon);
        if (accept(ship, weapon, afTarget, midBurst)) {
            return afTarget;
        }

        CombatEntityAPI carrierTarget = flagAsEntity(ship, AIFlags.CARRIER_FIGHTER_TARGET);
        if (accept(ship, weapon, carrierTarget, midBurst)) {
            return carrierTarget;
        }

        CombatEntityAPI maneuverTarget = flagAsEntity(ship, AIFlags.MANEUVER_TARGET);
        if (accept(ship, weapon, maneuverTarget, midBurst)) {
            return maneuverTarget;
        }

        ShipAPI shipTarget = ship.getShipTarget();
        if (accept(ship, weapon, shipTarget, midBurst)) {
            return shipTarget;
        }

        ShipAPI nearest = AIUtils.getNearestEnemy(ship);
        if (accept(ship, weapon, nearest, midBurst)) {
            return nearest;
        }

        return null;
    }

    private boolean accept(ShipAPI ship, WeaponAPI weapon, CombatEntityAPI target, boolean midBurst) {
        if (!isValidTarget(ship, weapon, target)) {
            return false;
        }
        if (!midBurst) {
            return true;
        }
        float angleTo = VectorUtils.getAngle(ship.getLocation(), target.getLocation());
        return Math.abs(MathUtils.getShortestRotation(aimAngle, angleTo)) <= MAX_REACQUIRE_SWING;
    }

    /**
     * AI flag customs are typed Object -- a blind cast to ShipAPI will throw if a
     * flag ever holds a missile or other entity.
     */
    private CombatEntityAPI flagAsEntity(ShipAPI ship, AIFlags flag) {
        ShipwideAIFlags flags = ship.getAIFlags();
        if (flags == null || !flags.hasFlag(flag)) {
            return null;
        }
        Object raw = flags.getCustom(flag);
        return (raw instanceof CombatEntityAPI) ? (CombatEntityAPI) raw : null;
    }

    private boolean isValidTarget(ShipAPI ship, WeaponAPI weapon, CombatEntityAPI target) {
        if (target == null) {
            return false;
        }
        if (target.getOwner() == ship.getOwner()) {
            return false;
        }
        if (!Global.getCombatEngine().isEntityInPlay(target)) {
            return false;
        }
        if (target instanceof ShipAPI) {
            ShipAPI ts = (ShipAPI) target;
            if (!ts.isAlive() || ts.isHulk() || ts.isPhased()) {
                return false;
            }
        }
        return MathUtils.getDistance(ship, target) <= weapon.getRange() * RANGE_TOLERANCE;
    }
}