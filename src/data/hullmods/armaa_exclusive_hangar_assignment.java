package data.hullmods;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.fleet.FleetMemberAPI;



public class armaa_exclusive_hangar_assignment extends BaseHullMod {

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
    public boolean isApplicableToShip(ShipAPI ship) {
        if(ship.getFleetMember() != null && ship.getFleetMember().getFleetData() != null)
        {
            for(FleetMemberAPI member :ship.getFleetMember().getFleetData().getMembersListCopy())
            {
                if(member.getVariant().hasHullMod("armaa_exclusive_hangar_assignment"))
                    return false;
            }
        }
        if(ship.isCapital() || ship.isCruiser())
            if(ship.hasLaunchBays())
                return true;
        
        return false;
    }
    public String getUnapplicableReason(ShipAPI ship) 
    {
        if(ship.getFleetMember() != null && ship.getFleetMember().getFleetData() != null)
        {
            for(FleetMemberAPI member :ship.getFleetMember().getFleetData().getMembersListCopy())
            {
                if(member.getVariant().hasHullMod("armaa_exclusive_hangar_assignment"))
                    return "Only installable on a single ship in the fleet";
            }
        }        
        return "Only can be installed on cruisers and carriers with launch bays.";
    }
    public String getDescriptionParam(int index, HullSize hullSize) {
        return null;
    }

}
