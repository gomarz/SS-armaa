package data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

public class armaa_serviceBays extends BaseHullMod {

public static final String REFIT_STAT_KEY = "armaa_strikeCraftRefitMod";
private static final float REFIT_BONUS = 50f;

    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getDynamic().getStat(REFIT_STAT_KEY).modifyPercent(id, REFIT_BONUS);

    }

    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) {
            return "Strikecraft";
        }
        if (index == 1) {
            return "50%";
        }
        return null;
    }

    //Built-in only
    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return false;
    }

    @Override
    public boolean affectsOPCosts() {
        return true;
    }

}
