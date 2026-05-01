package data.hullmods;

import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;

public class armaa_comboUnit extends BaseHullMod 
{
    
    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        if(stats.getVariant().getModuleVariant("MODULE") != null)
        {
            ShipVariantAPI var = stats.getVariant().getModuleVariant("MODULE").clone();
            var.addPermaMod("armaa_dpReduction");
            stats.getVariant().setModuleVariant("MODULE", var);
            stats.getDynamic().getMod(Stats.DEPLOYMENT_POINTS_MOD).modifyFlat(id, var.getHullSpec().getFleetPoints());
        }
        
    }

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) {
            return "X";
        }
        return null;
    }

}
