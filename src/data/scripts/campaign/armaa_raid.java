package data.scripts.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyPlayerHostileActListener;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.TempData;
import data.hullmods.cataphract;
import java.util.*;



public class armaa_raid implements ColonyPlayerHostileActListener {
	
	private static float BASE_DAMAGE_CHANCE = 0.35f;

    @Override
    public void reportRaidForValuablesFinishedBeforeCargoShown(InteractionDialogAPI dialog, MarketAPI market, TempData actionData, CargoAPI cargo) {
	handleRaid(dialog, market, "Deployment",actionData);
    }

    @Override
    public void reportRaidToDisruptFinished(InteractionDialogAPI dialog, MarketAPI market, TempData actionData, Industry industry) {
	handleRaid(dialog, market, "Deployment",actionData);
    }

    @Override
    public void reportTacticalBombardmentFinished(InteractionDialogAPI dialog, MarketAPI market, TempData actionData) {

    }

    @Override
    public void reportSaturationBombardmentFinished(InteractionDialogAPI dialog, MarketAPI market, TempData actionData) {

    }

    public static String getMagnitude(float attackerStr, float defenderStr)
	{
		float mag = defenderStr/attackerStr;
		
		if(mag <= 1.25f && mag >= .5f)
			return "moderate";
		
		if(mag > 1.25f && mag < 2f)
			return "heavy";
		
		if(mag > 2f)
			return "fierce";
		
			return "minor";

    }

    private static void handleRaid(InteractionDialogAPI dialog, MarketAPI market, String desc, TempData actionData) {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) {
            return;
        }
		float attackerStr = actionData.attackerStr;
		float defenderStr = actionData.defenderStr;
		List<FleetMemberAPI> units = fleet.getFleetData().getMembersListCopy();
		Collections.shuffle(units);
        for (FleetMemberAPI member : units) 
		{
            if (member.isMothballed())
                continue;

            if(member.getHullSpec().getBuiltInMods().contains("cataphract") || member.getHullSpec().getBuiltInMods().contains("cataphract2"))
			{
                if (member.getRepairTracker().getCR() < cataphract.getCRPenalty(member.getVariant()))
                    continue;
				/*
                if ((dialog != null) && (dialog.getTextPanel() != null)) 
				{
						String penaltyStr = "" + (int) Math.round(-cataphract.getCRPenalty(member.getVariant()) * 100f) + "%";

					if(member.getCaptain().isPlayer())
					{
						dialog.getTextPanel().addPara("You assisted on this op using the " + member.getShipName() + ", an " + member.getHullSpec().getHullNameWithDashClass() + " " + member.getHullSpec().getDesignation() + " you personally pilot.",Misc.getHighlightColor(),member.getShipName(),member.getHullSpec().getHullNameWithDashClass(),member.getHullSpec().getDesignation());
						dialog.getTextPanel().addPara(member.getShipName() + ": " + penaltyStr + "% CR", Misc.getNegativeHighlightColor(), penaltyStr);						
					}
					else
					{
						dialog.getTextPanel().addPara(member.getShipName() + " was deployed: "+ penaltyStr + "% CR",Misc.getNegativeHighlightColor(),penaltyStr);
					}

					if(Math.random() <= BASE_DAMAGE_CHANCE*(defenderStr/attackerStr))
					{
						String ferocity = getMagnitude(attackerStr,defenderStr);
						float level = member.getCaptain().isDefault() ? 1:member.getCaptain().getStats().getLevel();
						float damg = Math.min(MathUtils.getRandomNumberInRange(0.01f, 0.70f*(defenderStr/attackerStr))/level,.99f);
						String damStr ="" + (int) Math.round(damg * 100f) + "%";
						dialog.getTextPanel().addPara(member.getShipName() + " encountered " + ferocity +" resistance! Took " + damStr + "% damage.",Misc.getNegativeHighlightColor(), Misc.getHighlightColor(),ferocity,damStr);
						member.getStatus().applyHullFractionDamage(damg);
					}
				
                }
                member.getRepairTracker().applyCREvent(-cataphract.getCRPenalty(member.getVariant()),"armaa_raid", desc + " in support of military op at " + market.getName());
				break;
				*/
            }
        }
    }
}
