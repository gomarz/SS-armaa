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
    private IntervalUtil tracker = new IntervalUtil(0.05f, 0.05f);

    // === TUNABLES ===========================================================
    // Flux thresholds (all TOTAL flux, 0..1). The ladder is intentionally
    // non-overlapping: ATTACK_MAX_FLUX < FALLBACK_FLUX so we never charge
    // offensively in flux territory where we should be retreating.
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
// radius for the danger/threat-center scans
    private static final float ALLY_AVOID_RADIUS   = 450f;
    // (unused by heading escape; kept for reference)
     // look this far for an ally in our dash path
    private static final float ESCAPE_ALLY_ARC     = 60f;   // bias toward a safe ally only if within this arc of the flee heading
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
    // so it doesn't abort immediately on activation    

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
                //Global.getLogger(this.getClass()).info(assignment.getType());        
        if (assignment.getTarget() == null) {
            return false;
        }
        DeployedFleetMemberAPI tar = (DeployedFleetMemberAPI) assignment.getTarget();
        //Global.getLogger(this.getClass()).info(tar.getShip().getName());
        return tar.getShip() == target;
    }

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        if (!engine.isPaused()) {
            tracker.advance(amount);
            if (1 == 1) {
                if (tracker.intervalElapsed()) {
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
                    boolean needsToGetToSafety = (Boolean) ship.getCustomData().get("armaa_isFallingBack");
                    if (needsToGetToSafety && ship.getFluxLevel() <= RECOVER_FLUX) {
                        ship.getCustomData().put("armaa_isFallingBack", false);
                        ShipAPI newTarget = AIUtils.getNearestEnemy(ship);
                        ship.setShipTarget(newTarget); // can be null   
                        needsToGetToSafety = false;
                        return;
                    }
                    if (ship.getShipTarget() == null) {
                        ship.setShipTarget(target);
                    }
                    if (needsToGetToSafety || (flags.hasFlag(AIFlags.NEEDS_HELP) || flags.hasFlag(AIFlags.IN_CRITICAL_DPS_DANGER)) || ship.getFluxLevel() >= FALLBACK_FLUX) {
                        // force the core AI to back off for a bit
                        flags.setFlag(AIFlags.BACK_OFF, 1.0f);
                        flags.setFlag(AIFlags.BACKING_OFF, 1.0f);
                        ship.getCustomData().put("armaa_isFallingBack", true);
                        if (AIUtils.canUseSystemThisFrame(ship)) {
                            tryEscapeDash();
                        }
                        return;
                    }
                    if (target == null || target.getOwner() == ship.getOwner()) {
                        return;
                    }

                    // Explicit eliminate order on our target = player/admiral has
                    // committed us to this kill. Read it once here so the gates
                    // below can relax for it.
                    boolean hasEliminateOrder = hasEliminateOrderOn(ship.getShipTarget());

                    float dangerLevel = computeNetDangerAtPoint(target.getLocation(), DANGER_SCAN_RADIUS);
                    if (dangerLevel >= DANGER_ABORT_THREAT) {
                        return;
                    }
                    //Vector2f end = MathUtils.getPoint(ship.getLocation(), 600f, ship.getFacing());
                    if (ship.getCollisionClass() != CollisionClass.FIGHTER) {
                        List<ShipAPI> entity = AIUtils.getNearbyAllies(ship, ALLY_AVOID_RADIUS);
                        if (!entity.isEmpty()) {
                            for (ShipAPI e : entity) {
                                if (e.getCollisionClass() != CollisionClass.NONE && e.getCollisionClass() != CollisionClass.FIGHTER) {
                                    //if (getShipCollisionPoint(ship.getLocation(), end, s, ship.getFacing()) != null) {
                                    if (Math.abs(MathUtils.getShortestRotation(VectorUtils.getAngle(ship.getLocation(), e.getLocation()), ship.getFacing())) <= MathUtils.getRandomNumberInRange(25f, 65f)) {
                                        //engine.addFloatingText(ship.getLocation(), "Nya get out!!!", 30f, Color.WHITE, ship, 1f, 0.5f);
                                        return;
                                    }
                                    //}
                                }
                            }
                        }
                    }
                    if (target.isFighter() || target.isDrone() || target.isStation() || target.isStationModule() || ship.isRetreating()) {
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
                    float flankOffset = computeFlankOffset(ship.getShipTarget());
                    float toTarget = VectorUtils.getAngle(ship.getLocation(), target.getLocation());
                    float desiredHeading = normalizeAngle(toTarget + flankOffset);
                    float facingError = Math.abs(MathUtils.getShortestRotation(ship.getFacing(), desiredHeading));
                    if (facingError > MAX_DASH_FACING_ERROR) {
                        return;
                    }

                    float flanking = 1f;
                    float angle = normalizeAngle(target.getFacing()) - normalizeAngle(ship.getFacing());
                    angle = normalizeAngle(angle);
                    if (angle <= 150f * 0.5f || angle >= 360f - (150f * 0.5f) || target.getFluxTracker().isOverloaded()) {
                        flanking = 3f;
                    }
                    // Probability gate: skipped entirely under an eliminate order so
                    // the ship commits to a healthy target instead of waiting for it
                    // to be pressured.
                    if (!hasEliminateOrder && Math.random() > ((float) approachComfort.get(target.getHullSize())) * flanking * (1 + (1f * (target.getFluxLevel() - ship.getFluxLevel())))) {
                        return;
                    }
                    if (AIUtils.canUseSystemThisFrame(ship)) {
                        ShipAPI st = ship.getShipTarget();
                        boolean pursuing = flags.hasFlag(AIFlags.PURSUING) || flags.hasFlag(AIFlags.HARASS_MOVE_IN);

                        boolean enemyTarget = (st != null && st.getOwner() != ship.getOwner());
                        boolean targetVuln = (st != null && (st.getHullLevel() <= TARGET_VULN_HULL || st.getHardFluxLevel() >= TARGET_VULN_FLUX));

                        boolean okDespiteFlux = targetVuln && ship.getHardFluxLevel() < ATTACK_HARDFLUX_CAP;

                        boolean fluxOkToAttack = ship.getFluxLevel() < ATTACK_MAX_FLUX;
                        if (fluxOkToAttack && enemyTarget && (hasEliminateOrder || (targetVuln && okDespiteFlux) || (pursuing && ship.getFluxLevel() < PURSUE_FLUX_CAP))) {
 
                            ship.getCustomData().put("armaa_rampageFlankOffset", flankOffset);
                            ship.getCustomData().remove("armaa_rampageHeading");  // not an escape
                            ship.getCustomData().remove("armaa_isEscaping");
                            ship.useSystem();
                        }
                    }
                }
            }
        }
    }

    private float computeNetDangerAtPoint(Vector2f point, float radius) {
        float danger = 0f; // enemy-positive, ally-negative
        for (ShipAPI s : CombatUtils.getShipsWithinRange(point, radius)) {
            if (s == null || !s.isAlive() || s.isHulk()) {
                continue;
            }
            if (!ship.isFighter() && s.isFighter()) {
                continue;
            }

            float w;
            if (s.isFrigate()) {
                w = 1f;
            } else if (s.isDestroyer()) {
                w = 2f;
            } else if (s.isCruiser()) {
                w = 3f;
            } else if (s.isCapital()) {
                w = 4f;
            } else {
                w = 0.25f;
            }

            // reduce threat if disabled
            if (s.getFluxTracker().isOverloaded() || s.getFluxTracker().isVenting()) {
                w *= 0.5f;
            }
            if (s.getEngineController().isFlamedOut()) {
                w *= 0.7f;
            }

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
            float w;
            if (s.isFrigate()) {
                w = 1f;
            } else if (s.isDestroyer()) {
                w = 2f;
            } else if (s.isCruiser()) {
                w = 3f;
            } else if (s.isCapital()) {
                w = 4f;
            } else {
                w = 0.25f;
            }
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
     * Direction-aware escape. Flees away from the weighted center of enemy
     * threat, biased toward a safe ally if one lies within ESCAPE_ALLY_ARC of
     * the flee heading (so we never dash back through the fight to reach a
     * friend). Refuses to flee into a worse spot. Writes the escape heading +
     * flag the effect script steers by and uses for the escape-cutoff.
     */
    private void tryEscapeDash() {
        Vector2f threatCenter = computeEnemyThreatCenter(DANGER_SCAN_RADIUS);
        float escapeHeading;
        if (threatCenter != null) {
            escapeHeading = normalizeAngle(VectorUtils.getAngle(threatCenter, ship.getLocation()));
        } else {
            escapeHeading = ship.getFacing();
        }

        ShipAPI ally = pickSafeAllyInArc(escapeHeading, ESCAPE_ALLY_ARC);
        if (ally != null) {
            escapeHeading = VectorUtils.getAngle(ship.getLocation(), ally.getLocation());
            ship.setShipTarget(ally);
            ship.getCustomData().put("retreatTarget", ally);
        }

        // Don't flee into a worse spot: only refuse if the destination is more
        // dangerous than where we are now.
        float reach = 300f; // approximate look-ahead; effect script does the real dash
        Vector2f landing = MathUtils.getPoint(ship.getLocation(), reach, escapeHeading);
        float here = computeNetDangerAtPoint(ship.getLocation(), DANGER_SCAN_RADIUS);
        float there = computeNetDangerAtPoint(landing, DANGER_SCAN_RADIUS);
        if (there > here) {
            ship.getCustomData().remove("armaa_rampageHeading");
            return;
        }

        ship.getCustomData().put("armaa_rampageHeading", escapeHeading);
        ship.getCustomData().put("armaa_isEscaping", true);
        ship.getCustomData().remove("armaa_rampageFlankOffset"); // escape, not an attack arc
        ship.useSystem();
    }

    /**
     * Best safe ally to flee toward, but only if within arcDeg of the flee
     * heading. Uses net danger near the ally so a friend buried in enemies
     * doesn't look safe.
     */
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

    /**
     * Signed flank offset (degrees) to add to the live angle-toward-target so
     * the charge arcs into the target's flank/rear instead of straight down its
     * weapon centerline. 0 for fighters/drones (no meaningful facing). The
     * effect script applies this to the CURRENT angle each frame (so it tracks
     * the target) and fades it to 0 as the ship closes (so the ram lands clean).
     */
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

    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.flags = flags;
        this.engine = engine;
        ship.getCustomData().put("armaa_isFallingBack", false);
    }
}