package data.scripts.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListenerAndScript;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import static data.hullmods.cataphract2.getCRPenalty;
import java.util.ArrayList;

public class armaa_cataphractGBListener extends BaseCampaignEventListenerAndScript {

    @Override
    public void reportPlayerOpenedMarket(MarketAPI market) {
        StatBonus mod = Global.getSector().getPlayerFleet().getStats()
                .getDynamic().getMod(Stats.PLANETARY_OPERATIONS_MOD);

        // Remove all existing armaa_ flat mods
        for (String key : new ArrayList<>(mod.getFlatBonuses().keySet())) {
            if (key.startsWith("armaa_")) {
                mod.unmodifyFlat(key);
            }
        }

        // Reapply from current fleet
        if (Global.getSector().getPlayerFleet().getCargo().getMarines() < 0) {
            return;
        }
        for (FleetMemberAPI f : Global.getSector().getPlayerFleet()
                .getFleetData().getMembersListCopy()) {
            if (!f.getVariant().hasHullMod("cataphract") 
                    && !f.getVariant().hasHullMod("cataphract2") 
                    && !f.getVariant().hasHullMod("armaa_variableUnit")
                    && (!f.getVariant().hasHullMod("armaa_comboUnit"))) {
                continue;
            }
            if (f.getRepairTracker().getBaseCR() >= getCRPenalty(f.getVariant())
                    && f.getFleetData().getFleet() != null) {
                float level = f.getCaptain().isDefault() ? 1f
                        : f.getCaptain().getStats().getLevel();
                Float groundVal = f.getDeploymentPointsCost();
                float bonus = (groundVal != null) ? groundVal : f.getFleetPointCost();
                mod.modifyFlat("armaa_" + f.getId(), Math.min(325f, bonus * level), f.getShipName());
            }
        }
    }
}
