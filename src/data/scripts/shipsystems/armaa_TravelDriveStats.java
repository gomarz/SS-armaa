package data.scripts.shipsystems;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import com.fs.starfarer.api.Global;
import org.lazywizard.lazylib.combat.CombatUtils;
import com.fs.starfarer.api.combat.ShipAPI;
import data.scripts.plugins.armaa_CarrierLaunchManager;
import java.util.ArrayList;
import java.util.List;

public class armaa_TravelDriveStats extends BaseShipSystemScript {

    private boolean runOnce = false;
    private ShipAPI carrier;

    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        if (Global.getCombatEngine().isPaused()) {
            return;
        }

        ShipAPI ship = (ShipAPI) stats.getEntity();

        // Ensure launch manager exists
        ensureLaunchManager();

        boolean standardDeploy = ship.getOwner() == 1
                ? ship.getFacing() == 270f
                : ship.getFacing() == 90f;

        if (runOnce && !ship.isRetreating() && !ship.isDirectRetreat()) {
            ship.getTravelDrive().deactivate();
            unapply(stats, id);
            return;
        }

        boolean alreadyDone = Boolean.TRUE.equals(
                Global.getCombatEngine().getCustomData().get("armaa_carrierDeployDone_" + ship.getId()));
        carrier = getCarrier(ship);
        // No carrier, retreating, wrong facing, or already processed just apply speed boost
        if (carrier == null || ship.isRetreating() || !standardDeploy || alreadyDone) {
            if (state == ShipSystemStatsScript.State.OUT) {
                stats.getMaxSpeed().unmodify(id);
            } else {
                stats.getMaxSpeed().modifyFlat(id, 600f * effectLevel);
                stats.getAcceleration().modifyFlat(id, 600f * effectLevel);
            }
            Global.getCombatEngine().getCustomData().put("armaa_carrierDeployDone_" + ship.getId(), true);
        } else {
            // Carrier found queue staggered launch and hand off to manager
            if (!alreadyDone) {
                carrier = getCarrier(ship);
                if (carrier != null) {
                    armaa_CarrierLaunchManager manager = getLaunchManager();
                    if (manager != null) {
                        manager.queueLaunch(ship, carrier);
                    }
                    Global.getCombatEngine().getCustomData()
                            .put("armaa_carrierDeployDone_" + ship.getId(), true);
                    runOnce = true;
                }
            }
        }
    }

    public void unapply(MutableShipStatsAPI stats, String id) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        stats.getMaxSpeed().unmodify(id);
        stats.getMaxTurnRate().unmodify(id);
        stats.getTurnAcceleration().unmodify(id);
        stats.getAcceleration().unmodify(id);
        stats.getDeceleration().unmodify(id);
    }

    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) {
            return new StatusData("increased engine power", false);
        }
        return null;
    }

    // ---- Helpers ----
    private void ensureLaunchManager() {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine.getCustomData().get("armaa_launchManager") == null) {
            armaa_CarrierLaunchManager manager = new armaa_CarrierLaunchManager();
            engine.addPlugin(manager);
            engine.getCustomData().put("armaa_launchManager", manager);
        }
    }

    private armaa_CarrierLaunchManager getLaunchManager() {
        Object obj = Global.getCombatEngine().getCustomData().get("armaa_launchManager");
        return obj instanceof armaa_CarrierLaunchManager ? (armaa_CarrierLaunchManager) obj : null;
    }

    /**
     * Find the nearest valid carrier for this ship.
     */
    /**
     * Find the nearest valid carrier for this ship.
     */
    private ShipAPI getCarrier(ShipAPI ship) {
        boolean isPlayer = (Global.getCombatEngine().getPlayerShip() == ship);
        List<ShipAPI> candidates = new ArrayList<>();

        for (ShipAPI candidate : CombatUtils.getShipsWithinRange(ship.getLocation(), 20000f)) {
            if (candidate.getOwner() != ship.getOwner()) {
                continue;
            }
            if (candidate.isFighter() || candidate.isFrigate() || candidate == ship) {
                continue;
            }
            if (candidate.isDestroyer() && (!candidate.getVariant().hasHullMod("converted_hangar")
                    && candidate.getHullSpec().getFighterBays() <= 0)) {
                continue;
            }
            if (candidate.isHulk()) {
                continue;
            }
            if (candidate.isAlly() && !ship.isAlly()) {
                continue;
            }
            if (ship.getHullSpec().hasTag("strikecraft_medium") && candidate.isDestroyer()) {
                continue;
            }
            if (ship.getHullSpec().hasTag("strikecraft_large")
                    && (candidate.isCruiser() || candidate.isDestroyer())) {
                continue;
            }
            if (candidate.getNumFighterBays() <= 0
                    && candidate.getHullSpec().getFighterBays() <= 0
                    && candidate.getLaunchBaysCopy().isEmpty()) {
                continue;
            }

            candidates.add(candidate);
        }

        if (candidates.isEmpty()) {
            return null;
        }

        // Sort largest first
        candidates.sort((a, b) -> getShipSize(b) - getShipSize(a));

        if (isPlayer) {
            return candidates.get(0);
        } else {
            // Pick randomly within one size tier of the largest
            int largestSize = getShipSize(candidates.get(0));
            List<ShipAPI> topTier = new ArrayList<>();
            for (ShipAPI c : candidates) {
                if (getShipSize(c) >= largestSize - 1) {
                    topTier.add(c);
                }
            }
            return topTier.get((int) (Math.random() * topTier.size()));
        }
    }

    private int getShipSize(ShipAPI ship) {
        if (ship.isCapital()) {
            return 3;
        }
        if (ship.isCruiser()) {
            return 2;
        }
        if (ship.isDestroyer()) {
            return 1;
        }
        if (ship.isFrigate()) {
            return 0;
        }
        return -1;
    }
}
