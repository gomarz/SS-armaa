package data.hullmods;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import java.awt.Color;

public class armaa_comboUnit extends BaseHullMod {

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        if (stats.getVariant().getModuleVariant("MODULE") != null) {
            ShipVariantAPI var = stats.getVariant().getModuleVariant("MODULE").clone();
            var.addPermaMod("armaa_dpReduction");
            stats.getVariant().setModuleVariant("MODULE", var);
            stats.getDynamic().getMod(Stats.DEPLOYMENT_POINTS_MOD).modifyFlat(id, var.getHullSpec().getFleetPoints());
        }

    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {

    }
    
    @Override
    public void advanceInCampaign(FleetMemberAPI member, float amount)
    {
         Object didExplanation = Global.getSector().getMemoryWithoutUpdate().get("$armaa_comboUnitTutorial");
        if(didExplanation == null && Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy().contains(member))
        {
            Global.getSector().getMemoryWithoutUpdate().set("$armaa_comboUnitTutorial", true);     
            Global.getSector().getMemoryWithoutUpdate().set("$armaa_comboUnitTutorialUnit", member,10); 
        }        
    }

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0 || index == 1) {
            return "X";
        }
        return null;
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize,
            ShipAPI ship, float width, boolean isForModSpec) {
        Color h = Misc.getHighlightColor();
        float pad = 10f;

        tooltip.addSectionHeading("Core Unit", Alignment.MID, pad);

        tooltip.addPara("This vessel operates as a paired system: a core unit integrated into a "
                + "larger frame. The core unit is any strikecraft-class hull, and determines the "
                + "combined unit's identity for reputation and progression.", pad);

        tooltip.addPara("If the frame's hull is depleted, the core unit automatically ejects."
                + "If the frame does not require the core unit to function, it will continue to fight even when manually ejecting." + "Dependent frames instead lock "
                + "controls and go inert if lost.", pad);
        String independent = "Core Dependent";
        String disabled = "will";
        if (ship.getHullSpec().getTags().contains("armaa_core_independent")) {
            independent = "Autonomous";
            disabled = "will not";
        }
        tooltip.addPara("%s: If the core unit is destroyed while connected, the weapons platform %s be disabled.", pad,
                h, independent, disabled);
        tooltip.addPara("The core unit can be swapped at %s or at any player colony.", pad,
                h, "New Meshan");

        tooltip.addPara("Swapping preserves reputation history: the outgoing core inherits the "
                + "combined unit's standing, and the incoming core's standing transfers to the frame.", pad);
    }
}
