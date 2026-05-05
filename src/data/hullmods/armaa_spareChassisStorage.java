package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import static com.fs.starfarer.api.combat.ShipAPI.HullSize.CAPITAL_SHIP;
import static com.fs.starfarer.api.combat.ShipAPI.HullSize.CRUISER;
import static com.fs.starfarer.api.combat.ShipAPI.HullSize.DESTROYER;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class armaa_spareChassisStorage extends BaseHullMod {

    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {

    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, java.lang.String id) {

    }

    @Override
    public void advanceInCampaign(FleetMemberAPI member, float amount) {
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {

    }

    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) {
            return "Spare Chassis";
        } else if (index == 1) {
            return "3";
        } else if (index == 2) {
            return "";
        } else if (index == 3) {
            return "";
        }
        return null;
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
        if(ship.isCapital() || ship.isCruiser() || ship.isDestroyer())
            if(ship.hasLaunchBays())
                return true;
        return false;
    }
    
    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        if (ship != null && ship.getHullSize() == HullSize.FRIGATE) {
            return "Cannot be installed on frigates.";
        }
        return null;
    }

    public int getChassisContribution(ShipAPI ship) 
    {
        if(ship == null)
            return 0;
        int fleetTotal = 0;
        switch (ship.getHullSize()) {
            case DESTROYER:
                fleetTotal += 1;
                break;
            case CRUISER:
                fleetTotal += 2;
                break;
            case CAPITAL_SHIP:
                fleetTotal += 3;
                break;
            default:
                break;
        }
        return fleetTotal;
    }

    public int getChassisContribution(FleetMemberAPI member) {
        if(member == null)
            return 0;
        int fleetTotal = 0;
        switch (member.getHullSpec().getHullSize()) {
            case DESTROYER:
                fleetTotal += 1;
                break;
            case CRUISER:
                fleetTotal += 2;
                break;
            case CAPITAL_SHIP:
                fleetTotal += 3;
                break;
            default:
                break;
        }
        return fleetTotal;
    }

    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        if (playerFleet == null) {
            return;
        }
        int fleetAddition = getChassisContribution(ship);
        int fleetTotal = 0;
        for (FleetMemberAPI member : playerFleet.getFleetData().getMembersListCopy()) {
            if (member.getVariant() != null && member.getVariant().hasHullMod("armaa_spare_chassis_storage")) {
                fleetTotal += getChassisContribution(member);
            }
        }

        float pad = 10f;
        tooltip.addSectionHeading("Spare Chassis Storage", Alignment.MID, pad);
        tooltip.addPara("Increases capacity for spare chassis replacement by hull size, from %s/%s/%s/%s", pad, Misc.getHighlightColor(), "0","1","2","3");
        if(ship != null)            
        tooltip.addPara("This ship carries %s spare chassis, available to any ship in the fleet equipped with the Spare Chassis hullmod.", pad, Misc.getHighlightColor(), ""+fleetAddition);
        tooltip.addPara("Fleet-wide spare chassis available per engagement: %s", pad, Misc.getHighlightColor(), "" + fleetTotal);
    }

}
