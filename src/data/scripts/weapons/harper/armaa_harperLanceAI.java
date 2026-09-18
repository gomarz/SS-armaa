package data.scripts.weapons.harper;

import java.awt.Color;

import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.util.IntervalUtil;

public class armaa_harperLanceAI {

    /** Floats the current state over the ship on every transition. */
    public static final boolean DEBUG = armaa_harperLanceEffect.DEBUG;

    /** How far out the Harper will start driving at something. */
    private static final float APPROACH_RANGE = 1000f;
    /** How closely lined up before committing, degrees. */
    private static final float AIM_TOLERANCE = 20f;
    /** Once committed, abandon only past this much error, degrees. */
    private static final float ABORT_TOLERANCE = 35f;
    /** Safety valve: never hold a commit longer than this, seconds. */
    private static final float MAX_COMMIT_TIME = 5f;
    /**
     * Slack on the commit envelope. Slightly generous on purpose - committing
     * a little early wastes a bit of lunge, committing late means never
     * committing at all, which is the failure mode that matters.
     */
    private static final float ENVELOPE_SLACK = 60f;

    public enum State { IDLE, CLOSING, COMMIT }

    private final armaa_harperLanceEffect lance;

    /** Gates the target scan ONLY. Never the fire control. */
    private final IntervalUtil scanTimer = new IntervalUtil(0.15f, 0.25f);

    private State state = State.IDLE;
    private ShipAPI target = null;
    private ShipAPI committedTarget = null;
    private float commitElapsed = 0f;

    public armaa_harperLanceAI(armaa_harperLanceEffect lance) {
        this.lance = lance;
    }

    public State getState() {
        return state;
    }

    /**
     * What the AI has committed to, if anything. The effect reads this so a
     * lunge can home onto the same target the AI chose, rather than flying the
     * straight line it was launched on.
     */
    public ShipAPI getCommittedTarget() {
        return committedTarget;
    }

    /** Called by the effect when a strike finishes, so the latch can release. */
    public void onStrikeComplete() {
        committedTarget = null;
        commitElapsed = 0f;
    }

    public void advance(float amount, CombatEngineAPI engine,
            WeaponAPI weapon, ShipAPI ship) {

        if (weapon.isDisabled()) {
            return;
        }
        if(weapon.getCooldownRemaining() > 0 )
            return;
        // Mid-strike: hold the line and let the effect finish the sweep.
        if (lance.isThrusting()) {
            holdCourse(ship);
            return;
        }

        if (committedTarget != null) {
            advanceCommit(amount, engine, weapon, ship);
            return;
        }

        // ---- target scan: the only thing worth gating ----
        scanTimer.advance(amount);
        if (scanTimer.intervalElapsed() || target == null || !target.isAlive()) {
            target = pickTarget(ship);
        }

        // BACKING_OFF is respected - that is the hull AI saying it is about to
        // die, and overriding it would just feed the Harper into a grinder.
        // DO_NOT_PURSUE is deliberately ignored: it is the ordinary caution
        // this whole mechanic exists to overcome.
        boolean backing = ship.getAIFlags().hasFlag(ShipwideAIFlags.AIFlags.BACKING_OFF) || ship.getAIFlags().hasFlag(ShipwideAIFlags.AIFlags.NEEDS_HELP) ;

        if (target == null || !target.isAlive() || backing
                || MathUtils.getDistance(ship, target) > APPROACH_RANGE) {
            setState(engine, ship, State.IDLE);
            return;
        }

        float dist = MathUtils.getDistance(ship, target);
        float aim = VectorUtils.getAngle(ship.getLocation(), target.getLocation());
        float off = Math.abs(MathUtils.getShortestRotation(weapon.getCurrAngle(), aim));

        boolean inEnvelope = dist < lance.effectiveReach(weapon)
                + target.getCollisionRadius() + ENVELOPE_SLACK;
        boolean linedUp = off < AIM_TOLERANCE;

        if (inEnvelope && linedUp && lance.canFire(weapon)) {
            committedTarget = target;
            commitElapsed = 0f;
            setState(engine, ship, State.COMMIT);
            weapon.setForceFireOneFrame(true);
            return;
        }

        advanceClosing(engine, weapon, ship, aim);
    }

    /**
     * Nearest living hostile. The hull AI's own target is preferred when it has
     * a valid one, so the Harper works with the fleet rather than peeling onto
     * its own private target. Fighters are skipped - a lance charge at a
     * fighter is a wasted pass.
     */
    private ShipAPI pickTarget(ShipAPI ship) {

        ShipAPI hullTarget = ship.getShipTarget();
        if (hullTarget != null && hullTarget.isAlive() && !hullTarget.isHulk()
                && !hullTarget.isFighter()
                && MathUtils.getDistance(ship, hullTarget) <= APPROACH_RANGE) {
            return hullTarget;
        }

        ShipAPI best = null;
        float bestDist = Float.MAX_VALUE;
        for (ShipAPI candidate : AIUtils.getNearbyEnemies(ship, APPROACH_RANGE)) {
            if (!candidate.isAlive() || candidate.isHulk() || candidate.isFighter()) {
                continue;
            }
            float d = MathUtils.getDistance(ship, candidate);
            if (d < bestDist) {
                bestDist = d;
                best = candidate;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // states
    // ------------------------------------------------------------------

    /**
     * Drive at the target. Steers toward the target's actual bearing rather
     * than the turret angle so it still closes when the mount is lagging, and
     * blocks DECELERATE every frame because easing off on the approach is
     * precisely the hull AI behaviour that makes a melee frame useless.
     *
     * Fire is held here: a shot from out here would be uncharged, and the
     * effect's contact check would burn the cooldown for nothing.
     */
    private void advanceClosing(CombatEngineAPI engine, WeaponAPI weapon,
            ShipAPI ship, float aim) {
        setState(engine, ship, State.CLOSING);
        suppressFire(weapon);
        ship.blockCommandForOneFrame(ShipCommand.DECELERATE);
        engine.headInDirectionWithoutTurning(ship, aim, ship.getMaxSpeed());
    }

    private void advanceCommit(float amount, CombatEngineAPI engine,
            WeaponAPI weapon, ShipAPI ship) {

        commitElapsed += amount;

        float aim = VectorUtils.getAngle(ship.getLocation(),
                committedTarget.getLocation());
        float off = Math.abs(MathUtils.getShortestRotation(weapon.getCurrAngle(), aim));

        boolean lost = !committedTarget.isAlive()
                || committedTarget.isHulk()
                || off > ABORT_TOLERANCE
                || commitElapsed > MAX_COMMIT_TIME
                || ship.getAIFlags().hasFlag(ShipwideAIFlags.AIFlags.BACKING_OFF);

        if (lost) {
            onStrikeComplete();
            setState(engine, ship, State.IDLE);
            return;
        }

        // Held EVERY frame. A chargeup weapon released even briefly loses the
        // charge it has built.
        if (lance.canFire(weapon)) {
            weapon.setForceFireOneFrame(true);
        }
        ship.blockCommandForOneFrame(ShipCommand.DECELERATE);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private void holdCourse(ShipAPI ship) {
        ship.blockCommandForOneFrame(ShipCommand.DECELERATE);
        ship.blockCommandForOneFrame(ShipCommand.TURN_LEFT);
        ship.blockCommandForOneFrame(ShipCommand.TURN_RIGHT);
    }

    /**
     * Stop the stock weapon AI taking a free poke. Called EVERY frame while
     * closing - a single missed frame is a free shot, and that shot cancels the
     * charge through the effect's contact check.
     */
    private void suppressFire(WeaponAPI weapon) {
        weapon.setForceNoFireOneFrame(true);
    }

    private void setState(CombatEngineAPI engine, ShipAPI ship, State next) {
        if (state == next) {
            return;
        }
        state = next;
        if (DEBUG) {
            engine.addFloatingText(ship.getLocation(), next.name(),
                    18f, Color.green, ship, 1f, 1f);
        }
    }
}