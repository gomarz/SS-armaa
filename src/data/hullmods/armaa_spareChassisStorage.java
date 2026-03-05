package data.hullmods;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.IntervalUtil;



public class armaa_spareChassisStorage extends BaseHullMod {


    private IntervalUtil interval = new IntervalUtil(1.5f, 1.5f);

    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {

    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, java.lang.String id) {

    }

    @Override
    public void advanceInCampaign(FleetMemberAPI member, float amount) 
    {
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) 
    {

    }

    public String getDescriptionParam(int index, HullSize hullSize) {
        return null;
    }

}
