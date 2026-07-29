package data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

public class armaa_noSupplies extends BaseHullMod {

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {

        stats.getSuppliesPerMonth().modifyMult(id, 0f);
        stats.getSuppliesToRecover().modifyMult(id, 0f);        

    }
    
    @Override
    public void advanceInCampaign (FleetMemberAPI member, float amount)
    {
        if(member.needsRepairs())
            member.getRepairTracker().performRepairsFraction(1f);
        //member.set
        
    }
}
