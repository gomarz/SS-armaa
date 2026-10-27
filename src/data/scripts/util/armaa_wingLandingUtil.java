package data.scripts.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.FighterLaunchBayAPI;
import com.fs.starfarer.api.combat.FighterWingAPI.ReturningFighter;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.armaa_combat_docking_AI_fighter;
import data.scripts.ai.armaa_combat_retreat_AI_fighter;
import java.util.List;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

/**
 * Landing and relaunch for hulls whose fighters have no real bay to return to (WINGCOM and the
 * OVERLORD suites): returning fighters are routed to a nearby carrier, and relaunch from wherever
 * they last landed.
 */
public class armaa_wingLandingUtil {

    private static final float RETREAT_AREA_SIZE = 2000f;
    private static final String TICKER_KEY = "armaa_wingLanding_ticker";

    /** Call every frame from the hullmod's advanceInCombat. */
    public static void advanceLanding(ShipAPI ship, FighterLaunchBayAPI bay, float amount) {
        IntervalUtil ticker = getTicker(ship);
        ticker.advance(amount);

        if (ship.isLanding()) {
            ShipAPI defaultCarrier = getCarrier(ship);
            if (defaultCarrier != null) {
                Global.getCombatEngine().getCustomData()
                        .put("armaa_wingCommander_landingLocation_default" + ship.getId(), defaultCarrier);
            }
        }

        if (bay.getWing() != null && !bay.getWing().getWingMembers().isEmpty()) {
            final float mapHalfW = Global.getCombatEngine().getMapWidth() / 2f;
            final float mapHalfH = Global.getCombatEngine().getMapHeight() / 2f;
            final Vector2f rawLL = new Vector2f(-mapHalfW, -mapHalfH);
            final Vector2f rawUR = new Vector2f(mapHalfW, mapHalfH);

            final String shipId = ship.getId();

            List<ShipAPI> wingMembers = bay.getWing().getWingMembers();
            for (int i = 0; i < wingMembers.size(); i++) {
                ShipAPI fighter = wingMembers.get(i);
                if (fighter == null || fighter.isHulk()) {
                    continue;
                }

                final String setKey = "armaa_wingCommander_landingLocation_" + fighter.getId() + "_set";
                Object setFlag = Global.getCombatEngine().getCustomData().get(setKey);
                boolean done = setFlag instanceof Boolean && (Boolean) setFlag;

                if (fighter.isLiftingOff() && !done) {
                    Vector2f landingLoc = null;

                    final String indexKey = "armaa_wingCommander_landingLocation_" + shipId + "_" + i;

                    ShipAPI potentialLaunchPoint = (ShipAPI) Global.getCombatEngine()
                            .getCustomData().get("armaa_wingCommander_landingLocation_default" + shipId);

                    Object indexedCarrier = Global.getCombatEngine().getCustomData().get(indexKey);
                    if (indexedCarrier instanceof ShipAPI) {
                        potentialLaunchPoint = (ShipAPI) indexedCarrier;
                    }

                    if (potentialLaunchPoint != null
                            && (!potentialLaunchPoint.isAlive() || potentialLaunchPoint.isHulk())) {
                        potentialLaunchPoint = null;
                    }
                    if (potentialLaunchPoint == null) {
                        potentialLaunchPoint = getCarrier(fighter);
                    }

                    if (potentialLaunchPoint != null
                            && Global.getCombatEngine().isEntityInPlay(potentialLaunchPoint)
                            && potentialLaunchPoint.isAlive()) {
                        for (FighterLaunchBayAPI wep : potentialLaunchPoint.getLaunchBaysCopy()) {
                            if (wep.getWeaponSlot() != null) {
                                WeaponSlotAPI w = wep.getWeaponSlot();
                                landingLoc = new Vector2f(
                                        potentialLaunchPoint.getLocation().x + w.getLocation().y,
                                        potentialLaunchPoint.getLocation().y + w.getLocation().x);
                                if (Math.random() <= .50f) {
                                    break;
                                }
                            }
                        }
                    }

                    if (landingLoc == null) {
                        if (potentialLaunchPoint != null
                                && potentialLaunchPoint.getLocation() != null
                                && potentialLaunchPoint.isAlive()) {
                            landingLoc = potentialLaunchPoint.getLocation();
                        } else {
                            if (fighter.getOwner() == 0) {
                                if (ship.getLocation().getY() > rawLL.getY() - RETREAT_AREA_SIZE) {
                                    landingLoc = new Vector2f(ship.getLocation().getX(),
                                            ship.getLocation().getY() - 2000);
                                } else {
                                    landingLoc = new Vector2f((rawLL.x + rawUR.x) / 2, rawLL.y);
                                }
                            } else {
                                landingLoc = new Vector2f((rawLL.x + rawUR.x) / 2, rawUR.y);
                            }
                        }
                    }

                    armaa_utils.setLocation(fighter, landingLoc);
                    Global.getCombatEngine().getCustomData().put(setKey, true);
                }
            }

            // Every frame, so a fighter that starts returning gets the docking AI before the stock
            // return behaviour can land it on its own (frigate) parent. Rechecks stay on the ticker.
            if (!bay.getWing().getReturning().isEmpty()) {
                doLanding(bay, ship, ticker.intervalElapsed());
            }
        }
    }

    // Per ship, in custom data: hullmod instances are shared between every ship that has the mod
    private static IntervalUtil getTicker(ShipAPI ship) {
        Object o = ship.getCustomData().get(TICKER_KEY);
        if (o instanceof IntervalUtil) {
            return (IntervalUtil) o;
        }
        IntervalUtil iv = new IntervalUtil(0.5f, 1.0f);
        ship.setCustomData(TICKER_KEY, iv);
        return iv;
    }

    public static ShipAPI getCarrier(ShipAPI ship) {
        boolean isFakeFighter = ship.getVariant().getHullSize() == HullSize.FRIGATE;

        if (!isFakeFighter && ship.getWing() != null && ship.getWing().getSourceShip() != null) {
            // Normal fighter: look close to the source ship first.
            ShipAPI source = ship.getWing().getSourceShip();
            Vector2f sourceLoc = source.getLocation();
            ShipAPI best = null;
            float bestDist = Float.MAX_VALUE;

            // Tight pass: 3000 units around source, which covers most cases.
            for (ShipAPI carrier : CombatUtils.getShipsWithinRange(sourceLoc, 3000f)) {
                if (!isValidCarrier(carrier, ship)) {
                    continue;
                }
                float d = MathUtils.getDistance(carrier, source);
                if (d < bestDist) {
                    bestDist = d;
                    best = carrier;
                }
            }
            if (best != null) {
                return best;
            }

            // Wide fallback: only reached if no carrier was near the source.
            for (ShipAPI carrier : CombatUtils.getShipsWithinRange(sourceLoc, 8000f)) {
                if (!isValidCarrier(carrier, ship)) {
                    continue;
                }
                float d = MathUtils.getDistance(carrier, source);
                if (d < bestDist) {
                    bestDist = d;
                    best = carrier;
                }
            }
            return best;
        }

        // Fake-fighter / station path: return the first valid carrier found.
        for (ShipAPI carrier : CombatUtils.getShipsWithinRange(ship.getLocation(), 8000f)) {
            if (!isValidCarrier(carrier, ship)) {
                continue;
            }
            if (!carrier.isFrigate() || carrier.isStationModule()) {
                if (carrier.hasLaunchBays()) {
                    return carrier;
                }
            }
        }
        return null;
    }

    private static boolean isValidCarrier(ShipAPI carrier, ShipAPI ship) {
        if (carrier.getOwner() != ship.getOwner()) {
            return false;
        }
        if (!carrier.isAlive() || carrier.isHulk()) {
            return false;
        }
        if (carrier.isFrigate() && carrier.getVariant().getNonBuiltInWings().size() <= 1) {
            return false;
        }
        if ((carrier.isFighter() && !carrier.getHullSpec().hasTag("strikecraft_with_bays"))
                || carrier.getHullSpec().getFighterBays() < 1) {
            return false;
        }
        return true;
    }

    private static void doLanding(FighterLaunchBayAPI bay, ShipAPI ship, boolean recheck) {
        List<ReturningFighter> returning = bay.getWing().getReturning();
        final String shipId = ship.getId();

        for (int i = 0; i < returning.size(); i++) {
            ShipAPI fighter = returning.get(i).fighter;
            if (fighter == null) {
                continue;
            }

            final String retreatKey = "armaa_wingCommander_fighterRetreat_" + fighter.getId();
            Object retreatFlag = fighter.getCustomData().get(retreatKey);
            boolean alreadyHasAI = retreatFlag instanceof Boolean && (Boolean) retreatFlag;
            boolean docking = fighter.getShipAI() instanceof armaa_combat_docking_AI_fighter;

            // Newly returning fighters are handled at once; ones already docking or retreating only
            // on the ticker, since getCarrier scans thousands of units for every fighter
            if (bay.getWing().isReturning(fighter) && (recheck || (!docking && !alreadyHasAI))) {
                ShipAPI posCarrier = getCarrier(fighter);
                if (posCarrier != null) {
                    // Only hand over once: a fresh docking AI re-runs init() and restarts the approach.
                    // The docking AI re-picks a carrier itself when its current one dies.
                    if (!docking) {
                        armaa_combat_docking_AI_fighter DockingAI
                                = new armaa_combat_docking_AI_fighter(fighter);
                        fighter.setShipAI(DockingAI);
                        DockingAI.init();
                    }
                } else {
                    if (!alreadyHasAI) {
                        fighter.setShipAI(new armaa_combat_retreat_AI_fighter(fighter));
                        fighter.getCustomData().put(retreatKey, true);
                    }
                }
            }

            if (fighter.isLanding()) {
                for (ShipAPI carrier : CombatUtils.getShipsWithinRange(fighter.getLocation(), 100f)) {
                    if (carrier.getOwner() != fighter.getOwner()) {
                        continue;
                    }
                    if (carrier.isFighter()) {
                        continue;
                    }
                    if (!carrier.isFrigate() || carrier.isStationModule()) {
                        if (carrier.hasLaunchBays()) {
                            Global.getCombatEngine().getCustomData()
                                    .put("armaa_wingCommander_landingLocation_" + shipId + "_" + i, carrier);
                            break;
                        }
                    }
                }
            }

            if (fighter.isFinishedLanding()) {
                bay.land(fighter);
                Global.getCombatEngine().removeEntity(fighter);
            }
        }
    }
}
