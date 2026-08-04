package data.scripts.shipsystems.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.combat.CombatTaskManagerAPI;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.shipsystems.armaa_rampagedrive2;
import java.util.List;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;
import java.util.HashMap;
import java.util.Map;
import org.lazywizard.lazylib.combat.CombatUtils;

public class armaa_ironrampage2 implements ShipSystemAIScript {

    private ShipAPI ship;
    private CombatEngineAPI engine;
    private ShipwideAIFlags flags;
    private ShipSystemAPI system;
    private IntervalUtil tracker = new IntervalUtil(0.05f, 0.05f);

    private static final float ATTACK_MAX_FLUX = 0.75f; // won't dash offensively at/above this
    private static final float FALLBACK_FLUX   = 0.70f; // start retreating at/above this
    private static final float RECOVER_FLUX    = 0.40f; // stop retreating once at/below this
    // Offensive target/affordability gates.
    private static final float TARGET_VULN_HULL    = 0.50f; // target counts as "vulnerable" below this hull
    private static final float TARGET_VULN_FLUX    = 0.70f; // ...or at/above this target hard flux
    private static final float ATTACK_HARDFLUX_CAP = 0.85f; // legacy "okDespiteFlux" ceiling (hard flux)
    private static final float PURSUE_FLUX_CAP     = 0.70f; // pursue-dash allowed below this total flux
    // Threat / danger scan.
    private static final float DANGER_ABORT_THREAT = 7f;    // skip attack if net danger at target area >= this
    private static final float DANGER_SCAN_RADIUS  = 1800f; // radius for the danger/threat-center scans
    private static final float ALLY_AVOID_RADIUS   = 450f;  // look this far for an ally in our dash path
    private static final float ESCAPE_ALLY_ARC     = 60f;   // bias toward a safe ally only if within this arc of the flee heading
    // Retreat trigger. Flux and a dire hull are our own state. no number of
    // friends fixes either, so they force a fallback outright. The critical AI
    // flags only mean "taking heavy fire" and carry no notion of whether anyone
    // is covering us, so they get gated on local ally support.
    private static final float FALLBACK_HULL_FLOOR     = 0.25f; // retreat regardless of support below this hull level
    private static final float FALLBACK_SUPPORT_RADIUS = 1200f; // allies within this range count as covering us
    private static final float FALLBACK_DANGER_FLOOR   = 0f;    // net danger above this = we are NOT covered
    // Escape steering. The flee heading is chosen by scoring candidate headings
    // rather than committing to "directly away from the enemy" blind, so the
    // ship can slide along an arena edge instead of pinning itself on it.
    private static final float ESCAPE_REACH_MIN           = 300f;  // look-ahead used to score candidate headings
    private static final float ESCAPE_ARENA_MARGIN     = 600f;  // keep escape landings this far inside the map edge
    private static final float ESCAPE_HEADING_STEP     = 15f;   // candidate heading resolution, degrees
    private static final float ESCAPE_BORDER_WEIGHT    = 0.02f; // danger-equivalent penalty per unit past the margin
    private static final float ESCAPE_DEVIATION_WEIGHT = 3f;    // max danger-equivalent penalty for a full 180 bend
    // Angled charge: dash arcs toward the target's flank/rear instead of straight
    // down its weapon centerline. The effect script applies this offset to the
    // live angle-to-target each frame and fades it to 0 as the ship closes.
    private static final float FLANK_OFFSET_DEG    = 60f;   // how far toward the rear to arc (degrees)
    private static final float FLANK_AIM_PADDING   = 100f;  // aim this far past the target's hull, toward its flank
    // Facing precondition: don't commit the dash if the desired charge heading
    // is more than this many degrees off our current facing. The system has a
    // reduced turn rate while active, so firing while pointed away wastes most
    // of the uptime slewing around. Checked against the FLANKED heading (where
    // we'll actually be going), not the direct angle-to-target.
    private static final float MAX_DASH_FACING_ERROR = 45f;
    // ========================================================================

    private static Map approachComfort = new HashMap();

    static {

        approachComfort.put(ShipAPI.HullSize.FIGHTER, 0f);
        approachComfort.put(ShipAPI.HullSize.FRIGATE, 1f);
        approachComfort.put(ShipAPI.HullSize.DESTROYER, 1.25f);
        approachComfort.put(ShipAPI.HullSize.CRUISER, 0.75f);
        approachComfort.put(ShipAPI.HullSize.CAPITAL_SHIP, 0.20f);
    }

    private float normalizeAngle(float a) {
        a %= 360f;
        if (a < 0f) {
            a += 360f;
        }
        return a;
    }

    /**
     * True if the combat task manager has an eliminate order on our current
     * ship target. An eliminate order is an explicit "commit to this target"
     * signal, so when present we relax most of the offensive caution gates
     * (probability roll, vuln requirement) while keeping the flux cap and
     * retreat ladder intact -- the order says "be aggressive," not "suicide."
     *
     * The in-game Eliminate command surfaces as CombatAssignmentType.INTERCEPT.
     */
    private boolean hasEliminateOrderOn(ShipAPI target) {
        if (target == null || target.getOwner() == ship.getOwner()) {
            return false;
        }
        CombatFleetManagerAPI fm = engine.getFleetManager(ship.getOwner());
        if (fm == null) {
            return false;
        }
        CombatTaskManagerAPI tm = fm.getTaskManager(ship.isAlly());
        if (tm == null) {
            return false;
        }
        AssignmentInfo assignment = tm.getAssignmentFor(ship);

        if (assignment == null || assignment.getType() != CombatAssignmentType.INTERCEPT) {
            return false;
        }
        if (assignment.getTarget() == null) {
            return false;
        }
        DeployedFleetMemberAPI tar = (DeployedFleetMemberAPI) assignment.getTarget();
        return tar.getShip() == target;
    }

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        if (engine.isPaused()) {
            return;
        }
        Global.getLogger(this.getClass()).info("map " + engine.getMapWidth() + "x" + engine.getMapHeight()
        + " ship at " + ship.getLocation().x + "," + ship.getLocation().y);
        tracker.advance(amount);
        if (!tracker.intervalElapsed()) {
            return;
        }

        if (ship.isDirectRetreat()) {
            ship.useSystem();
            return;
        }
        // remember that this can still be null if no enemies are present
        if (target == null) {
            target = AIUtils.getNearestEnemy(ship);
        }
        // I guess this can run before init? somehow?
        if (ship.getCustomData().get("armaa_isFallingBack") == null) {
            ship.getCustomData().put("armaa_isFallingBack", false);
            //lol it can still be null
            return;
        }

        boolean inCriticalState = flags.hasFlag(AIFlags.NEEDS_HELP) || flags.hasFlag(AIFlags.IN_CRITICAL_DPS_DANGER);
        boolean needsToGetToSafety = (Boolean) ship.getCustomData().get("armaa_isFallingBack");

        // Recovery now also requires the critical flags to be clear. Previously
        // this keyed on flux alone, which was fine when flux was the only
        // trigger (FALLBACK_FLUX > RECOVER_FLUX gives hysteresis) but broke once
        // a critical-flag fallback could fire at low flux: the state would set
        // and clear on alternating ticks and the ship would jitter in place.
        if (needsToGetToSafety && ship.getFluxLevel() <= RECOVER_FLUX && !inCriticalState) {
            ship.getCustomData().put("armaa_isFallingBack", false);
            ShipAPI newTarget = AIUtils.getNearestEnemy(ship);
            ship.setShipTarget(newTarget); // can be null
            return;
        }

        // Ally crediting on the retreat trigger. The critical flags respond to
        // incoming damage pressure with no headcount awareness, which is why one
        // high-DPS boss read identically to being swarmed. Now they only force a
        // fallback when local net danger says nobody is actually covering us.
        boolean fluxForcesFallback = ship.getFluxLevel() >= FALLBACK_FLUX;
        boolean hullForcesFallback = ship.getHullLevel() <= FALLBACK_HULL_FLOOR;
        boolean unsupported = computeNetDangerAtPoint(ship.getLocation(), FALLBACK_SUPPORT_RADIUS) > FALLBACK_DANGER_FLOOR;

        if (needsToGetToSafety || fluxForcesFallback || hullForcesFallback || (inCriticalState && unsupported)) {
            // force the core AI to back off for a bit
            flags.setFlag(AIFlags.BACK_OFF, 1.0f);
            flags.setFlag(AIFlags.BACKING_OFF, 1.0f);
            ship.getCustomData().put("armaa_isFallingBack", true);
            if (AIUtils.canUseSystemThisFrame(ship)) {
                tryEscapeDash();
            }
            return;
        }

        // Single resolved target for everything below. The danger scan used to
        // read the `target` parameter while the eliminate-order and vulnerability
        // checks read ship.getShipTarget(), so those two could diverge -- most
        // easily right after an escape, where the ship target is the ALLY we fled
        // toward. The owner/liveness check below also cleans that up.
        ShipAPI attackTarget = ship.getShipTarget();
        if (attackTarget == null || !attackTarget.isAlive() || attackTarget.isHulk()
                || attackTarget.getOwner() == ship.getOwner()) {
            attackTarget = target;
            ship.setShipTarget(attackTarget);
        }
        if (attackTarget == null || attackTarget.getOwner() == ship.getOwner()) {
            return;
        }

        // Explicit eliminate order on our target = player/admiral has
        // committed us to this kill. Read it once here so the gates
        // below can relax for it.
        boolean hasEliminateOrder = hasEliminateOrderOn(attackTarget);

        float dangerLevel = computeNetDangerAtPoint(attackTarget.getLocation(), DANGER_SCAN_RADIUS);
        if (dangerLevel >= DANGER_ABORT_THREAT) {
            return;
        }
        if (ship.getCollisionClass() != CollisionClass.FIGHTER) {
            List<ShipAPI> entity = AIUtils.getNearbyAllies(ship, ALLY_AVOID_RADIUS);
            if (!entity.isEmpty()) {
                for (ShipAPI e : entity) {
                    if (e.getCollisionClass() != CollisionClass.NONE && e.getCollisionClass() != CollisionClass.FIGHTER) {
                        if (Math.abs(MathUtils.getShortestRotation(VectorUtils.getAngle(ship.getLocation(), e.getLocation()), ship.getFacing())) <= MathUtils.getRandomNumberInRange(25f, 65f)) {
                            return;
                        }
                    }
                }
            }
        }
        if (attackTarget.isFighter() || attackTarget.isDrone() || attackTarget.isStation()
                || attackTarget.isStationModule() || ship.isRetreating()) {
            return;
        }

        // Facing precondition. Compute the flank offset once here (it's
        // also what we hand the effect script below, so no double work)
        // and check our facing against the heading we'll ACTUALLY charge
        // along -- the direct-to-target angle plus that flank arc. Bail
        // if it's too far off our nose so we don't burn the dash's
        // reduced-turn-rate uptime spinning to face the target.
        // Kept active even under an eliminate order: the order is about
        // willingness, this is about not wasting the system.
        float flankOffset = computeFlankOffset(attackTarget);
        float toTarget = VectorUtils.getAngle(ship.getLocation(), attackTarget.getLocation());
        float desiredHeading = normalizeAngle(toTarget + flankOffset);
        float facingError = Math.abs(MathUtils.getShortestRotation(ship.getFacing(), desiredHeading));
        if (facingError > MAX_DASH_FACING_ERROR) {
            return;
        }

        float flanking = 1f;
        float angle = normalizeAngle(attackTarget.getFacing()) - normalizeAngle(ship.getFacing());
        angle = normalizeAngle(angle);
        if (angle <= 150f * 0.5f || angle >= 360f - (150f * 0.5f) || attackTarget.getFluxTracker().isOverloaded()) {
            flanking = 3f;
        }
        // Probability gate: skipped entirely under an eliminate order so
        // the ship commits to a healthy target instead of waiting for it
        // to be pressured.
        if (!hasEliminateOrder && Math.random() > ((float) approachComfort.get(attackTarget.getHullSize())) * flanking * (1 + (1f * (attackTarget.getFluxLevel() - ship.getFluxLevel())))) {
            return;
        }
        if (AIUtils.canUseSystemThisFrame(ship)) {
            boolean pursuing = flags.hasFlag(AIFlags.PURSUING) || flags.hasFlag(AIFlags.HARASS_MOVE_IN);

            boolean targetVuln = (attackTarget.getHullLevel() <= TARGET_VULN_HULL
                    || attackTarget.getHardFluxLevel() >= TARGET_VULN_FLUX);

            boolean okDespiteFlux = targetVuln && ship.getHardFluxLevel() < ATTACK_HARDFLUX_CAP;

            boolean fluxOkToAttack = ship.getFluxLevel() < ATTACK_MAX_FLUX;
            if (fluxOkToAttack && (hasEliminateOrder || (targetVuln && okDespiteFlux) || (pursuing && ship.getFluxLevel() < PURSUE_FLUX_CAP))) {

                ship.getCustomData().put("armaa_rampageFlankOffset", flankOffset);
                ship.getCustomData().remove("armaa_rampageHeading");  // not an escape
                ship.getCustomData().remove("armaa_isEscaping");
                ship.useSystem();
            }
        }
    }

    /**
     * Raw hull-size threat weight, before any state modifiers.
     */
    private float hullWeight(ShipAPI s) {
        if (s.isFrigate()) {
            return 1f;
        } else if (s.isDestroyer()) {
            return 2f;
        } else if (s.isCruiser()) {
            return 3f;
        } else if (s.isCapital()) {
            return 4f;
        }
        return 0.25f;
    }

    /**
     * Hull-size weight reduced for ships that can't currently bring their guns
     * to bear (overloaded, venting, engines flamed out).
     */
    private float dangerWeight(ShipAPI s) {
        float w = hullWeight(s);
        if (s.getFluxTracker().isOverloaded() || s.getFluxTracker().isVenting()) {
            w *= 0.5f;
        }
        if (s.getEngineController().isFlamedOut()) {
            w *= 0.7f;
        }
        return w;
    }

    private float computeNetDangerAtPoint(Vector2f point, float radius) {
        return computeNetDangerAtPoint(point, radius, CombatUtils.getShipsWithinRange(point, radius));
    }

    /**
     * Pool-based variant so a batch of candidate points can be scored against a
     * single range query instead of one query per point.
     */
    private float computeNetDangerAtPoint(Vector2f point, float radius, List<ShipAPI> pool) {
        float danger = 0f; // enemy-positive, ally-negative
        float radiusSq = radius * radius;
        for (ShipAPI s : pool) {
            if (s == null || !s.isAlive() || s.isHulk()) {
                continue;
            }
            if (!ship.isFighter() && s.isFighter()) {
                continue;
            }
            if (MathUtils.getDistanceSquared(s.getLocation(), point) > radiusSq) {
                continue;
            }
            float w = dangerWeight(s);
            danger += (s.getOwner() == ship.getOwner()) ? -w : w;
        }
        return danger;
    }

    /**
     * Threat-weighted center of nearby enemies, or null if none in range.
     * Used to flee directly away when there's no safe ally to run to.
     */
    private Vector2f computeEnemyThreatCenter(float radius) {
        float sumX = 0f, sumY = 0f, sumW = 0f;
        for (ShipAPI s : CombatUtils.getShipsWithinRange(ship.getLocation(), radius)) {
            if (s == null || !s.isAlive() || s.isHulk()) {
                continue;
            }
            if (!ship.isFighter() && s.isFighter()) {
                continue;
            }
            if (s.getOwner() == ship.getOwner()) {
                continue;
            }
            float w = hullWeight(s);
            sumX += s.getLocation().x * w;
            sumY += s.getLocation().y * w;
            sumW += w;
        }
        if (sumW <= 0f) {
            return null;
        }
        return new Vector2f(sumX / sumW, sumY / sumW);
    }

    /**
     * How far past the safe inset a point sits, expressed as a danger-equivalent
     * penalty. Zero anywhere comfortably inside the arena. The combat map is
     * centered on the origin, so the usable span is +/- half the map dimension.
     */
    private float borderPenalty(Vector2f p) {
        float limitX = (engine.getMapWidth() * 0.5f) - ESCAPE_ARENA_MARGIN;
        float limitY = (engine.getMapHeight() * 0.5f) - ESCAPE_ARENA_MARGIN;
        float overX = Math.abs(p.x) - limitX;
        float overY = Math.abs(p.y) - limitY;
        float over = Math.max(0f, Math.max(overX, overY));
        return over * ESCAPE_BORDER_WEIGHT;
    }

    /**
     * Picks a flee heading by scoring candidates fanned out from the ideal one.
     * Each candidate is charged for the danger at its landing point, for how far
     * past the arena margin that landing sits, and for how far it bends off the
     * ideal. The border term is what stops the ship from running itself into an
     * edge: once pinned, every heading straight away from the enemy is walled and
     * a tangential one wins instead.
     */
    private float pickEscapeHeading(float idealHeading) {
        List<ShipAPI> pool = CombatUtils.getShipsWithinRange(ship.getLocation(), DANGER_SCAN_RADIUS + estimateDashReach());
        float best = idealHeading;
        float bestScore = Float.MAX_VALUE;
        for (float offset = 0f; offset <= 180.001f; offset += ESCAPE_HEADING_STEP) {
            for (int sign = 1; sign >= -1; sign -= 2) {
                float candidate = normalizeAngle(idealHeading + (offset * sign));
                Vector2f landing = MathUtils.getPoint(ship.getLocation(), estimateDashReach(), candidate);
                float score = computeNetDangerAtPoint(landing, DANGER_SCAN_RADIUS, pool)
                        + borderPenalty(landing)
                        + ((offset / 180f) * ESCAPE_DEVIATION_WEIGHT);
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                }
                if (offset <= 0f) {
                    break; // 0 and -0 are the same heading
                }
            }
        }
        return best;
    }

    /**
     * Direction-aware escape. Flees away from the weighted center of enemy
     * threat, biased toward a safe ally if one lies within ESCAPE_ALLY_ARC of
     * the flee heading (so we never dash back through the fight to reach a
     * friend), then steered around the arena edge and any worse pocket by
     * pickEscapeHeading. Writes the escape heading + flag the effect script
     * steers by and uses for the escape-cutoff.
     */
    private void tryEscapeDash() {
        Vector2f threatCenter = computeEnemyThreatCenter(DANGER_SCAN_RADIUS);
        if (threatCenter == null) {
            // Nothing inside the scan radius to run from. Fall back to the
            // nearest enemy at any range rather than ship.getFacing() -- facing
            // is just wherever we happened to be pointed and can aim straight
            // into a wall or back through the fight.
            ShipAPI nearest = AIUtils.getNearestEnemy(ship);
            if (nearest == null) {
                // No enemies on the field at all: nothing to escape, don't burn
                // a charge on a heading with no meaning.
                ship.getCustomData().remove("armaa_rampageHeading");
                return;
            }
            threatCenter = nearest.getLocation();
        }
        float idealHeading = normalizeAngle(VectorUtils.getAngle(threatCenter, ship.getLocation()));

        ShipAPI ally = pickSafeAllyInArc(idealHeading, ESCAPE_ALLY_ARC);
        if (ally != null) {
            idealHeading = VectorUtils.getAngle(ship.getLocation(), ally.getLocation());
        }

        float escapeHeading = pickEscapeHeading(idealHeading);

        // Don't flee into a worse spot
        //If we're inside the margin, sitting still is the failure mode
        // so a marginally worse landing still beats staying put.
        Vector2f landing = MathUtils.getPoint(ship.getLocation(), estimateDashReach(), escapeHeading);
        float here = computeNetDangerAtPoint(ship.getLocation(), DANGER_SCAN_RADIUS);
        float there = computeNetDangerAtPoint(landing, DANGER_SCAN_RADIUS);
        if (there > here && borderPenalty(ship.getLocation()) <= 0f) {
            ship.getCustomData().remove("armaa_rampageHeading");
            return;
        }

        // Only adopt the ally as the retreat target if the steered heading still
        // actually goes toward them -- wall avoidance may have bent us off it.
        if (ally != null) {
            float toAlly = VectorUtils.getAngle(ship.getLocation(), ally.getLocation());
            if (Math.abs(MathUtils.getShortestRotation(toAlly, escapeHeading)) <= ESCAPE_ALLY_ARC) {
                ship.setShipTarget(ally);
                ship.getCustomData().put("retreatTarget", ally);
            }
        }

        ship.getCustomData().put("armaa_rampageHeading", escapeHeading);
        ship.getCustomData().put("armaa_isEscaping", true);
        ship.getCustomData().remove("armaa_rampageFlankOffset"); // escape, not an attack arc
        ship.useSystem();
    }


    private ShipAPI pickSafeAllyInArc(float escapeHeading, float arcDeg) {
        List<ShipAPI> allies = AIUtils.getNearbyAllies(ship, 5000f);
        ShipAPI best = null;
        float bestScore = -99999f;
        for (ShipAPI a : allies) {
            if (a == ship || !a.isAlive() || a.isHulk() || a.isFighter() || a.isDrone()) {
                continue;
            }
            float toAlly = VectorUtils.getAngle(ship.getLocation(), a.getLocation());
            if (Math.abs(MathUtils.getShortestRotation(toAlly, escapeHeading)) > arcDeg) {
                continue;
            }
            float danger = computeNetDangerAtPoint(a.getLocation(), 1500f);
            float dist = MathUtils.getDistance(ship, a);
            float hullBonus = a.isCapital() ? 2f : a.isCruiser() ? 1f : 0f;
            float score = (-danger * 2f) - (dist / 800f) + hullBonus;
            if (score > bestScore) {
                bestScore = score;
                best = a;
            }
        }
        return best;
    }

    private float computeFlankOffset(ShipAPI target) {
        if (target == null || target.isFighter() || target.isDrone()) {
            return 0f;
        }
        float direct = VectorUtils.getAngle(ship.getLocation(), target.getLocation());
        float rear = normalizeAngle(target.getFacing() + 180f);
        float toUsFromTarget = VectorUtils.getAngle(target.getLocation(), ship.getLocation());
        float side = MathUtils.getShortestRotation(target.getFacing(), toUsFromTarget);
        float flankAmt = (side >= 0f) ? FLANK_OFFSET_DEG : -FLANK_OFFSET_DEG;
        float flankDir = normalizeAngle(rear - flankAmt);
        Vector2f flankPoint = MathUtils.getPoint(target.getLocation(),
                target.getCollisionRadius() + FLANK_AIM_PADDING, flankDir);
        float flankHeading = VectorUtils.getAngle(ship.getLocation(), flankPoint);
        return MathUtils.getShortestRotation(direct, flankHeading);
    }

private static final float DASH_REACH_EFFICIENCY = 0.75f; // spool-up + chargeup vs. nominal speed*duration

private float estimateDashReach() {
    Object boost = armaa_rampagedrive2.SPEED_BOOST.get(ship.getHullSize());
    float bonus = (boost instanceof Number) ? ((Number) boost).floatValue() : 1f;
    float dur = system.getSpecAPI().getActive();
    Global.getLogger(this.getClass()).info("Max Speed: " + ship.getMaxSpeed() + " Bonus:" +bonus + " Total:" +Math.max(ESCAPE_REACH_MIN, (ship.getMaxSpeed() + bonus) * dur * DASH_REACH_EFFICIENCY ));
    return Math.max(ESCAPE_REACH_MIN, (ship.getMaxSpeed() + bonus) * dur * DASH_REACH_EFFICIENCY);
}
    
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.flags = flags;
        this.engine = engine;
        this.system = system;
        ship.getCustomData().put("armaa_isFallingBack", false);
    }
}