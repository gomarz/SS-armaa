package data.scripts.shipsystems.ai;

import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
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
                    if (needsToGetToSafety && ship.getHardFluxLevel() <= 0.50f) {
                        ship.getCustomData().put("armaa_isFallingBack", false);
                        ShipAPI newTarget = AIUtils.getNearestEnemy(ship);
                        ship.setShipTarget(newTarget); // can be null   
                        needsToGetToSafety = false;
                        return;
                    }
                    if (ship.getShipTarget() == null) {
                        ship.setShipTarget(target);
                    }
                    if (needsToGetToSafety || (flags.hasFlag(AIFlags.NEEDS_HELP) || flags.hasFlag(AIFlags.IN_CRITICAL_DPS_DANGER)) || ship.getHardFluxLevel() >= 0.70f) {
                        // force the core AI to back off for a bit
                        flags.setFlag(AIFlags.BACK_OFF, 1.0f);
                        flags.setFlag(AIFlags.BACKING_OFF, 1.0f);
                        ShipAPI ally = pickSafeAlly();
                        if (ally != null && AIUtils.canUseSystemThisFrame(ship)) {
                            ship.useSystem();
                            ship.setShipTarget(ally);
                            ship.getCustomData().put("retreatTarget", ally);
                        }
                        ship.getCustomData().put("armaa_isFallingBack", true);
                        return;
                    }
                    if (target == null || target.getOwner() == ship.getOwner()) {
                        return;
                    }

                    float dangerLevel = computeNetDangerAtPoint(target.getLocation(), 2000f);
                    if (dangerLevel >= 7f) {
                        return;
                    }
                    //Vector2f end = MathUtils.getPoint(ship.getLocation(), 600f, ship.getFacing());
                    if (ship.getCollisionClass() != CollisionClass.FIGHTER) {
                        List<ShipAPI> entity = AIUtils.getNearbyAllies(ship, 450f);
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
                    float flanking = 1f;
                    float angle = normalizeAngle(target.getFacing()) - normalizeAngle(ship.getFacing());
                    angle = normalizeAngle(angle);
                    if (angle <= 150f * 0.5f || angle >= 360f - (150f * 0.5f) || target.getFluxTracker().isOverloaded()) {
                        flanking = 3f;
                    }
                    if (Math.random() > ((float) approachComfort.get(target.getHullSize())) * flanking * (1 + (1f * (target.getFluxLevel() - ship.getFluxLevel())))) {
                        return;
                    }
                    if (AIUtils.canUseSystemThisFrame(ship)) {
                        ShipAPI st = ship.getShipTarget();
                        boolean pursuing = flags.hasFlag(AIFlags.PURSUING) || flags.hasFlag(AIFlags.HARASS_MOVE_IN);

                        boolean enemyTarget = (st != null && st.getOwner() != ship.getOwner());
                        boolean targetVuln = (st != null && (st.getHullLevel() <= 0.5f || st.getHardFluxLevel() >= 0.70f));

                        boolean okDespiteFlux = targetVuln && ship.getHardFluxLevel() < 0.85f; // example
                        if (enemyTarget && ((targetVuln && okDespiteFlux) || (pursuing && ship.getFluxLevel() < 0.70f))) {
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

    private ShipAPI pickSafeAlly() {
        List<ShipAPI> allies = AIUtils.getNearbyAllies(ship, 5000f);
        ShipAPI best = null;
        float bestScore = -99999f;

        for (ShipAPI a : allies) {
            if (a == ship || !a.isAlive() || a.isHulk()) {
                continue;
            }
            if (a.isFighter() || a.isDrone()) {
                continue;
            }

            float danger = computeNetDangerAtPoint(a.getLocation(), 1500f);
            float dist = MathUtils.getDistance(ship, a);

            // prefer safer + closer + larger hull
            float hullBonus = a.isCapital() ? 2f : a.isCruiser() ? 1f : 0f;
            float score = (-danger * 2f) - (dist / 800f) + hullBonus;

            if (score > bestScore) {
                bestScore = score;
                best = a;
            }
        }
        return best;
    }

    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.flags = flags;
        this.engine = engine;
        ship.getCustomData().put("armaa_isFallingBack", false);
    }
}
