package data.scripts.campaign;

import com.fs.starfarer.api.campaign.BaseCampaignEventListenerAndScript;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.util.IntervalUtil;
public class armaa_hyperSpaceImmunity extends BaseCampaignEventListenerAndScript
{
	//Logger log = Logger.getLogger(armaa_hyperSpaceImmunity.class.getName());
	private IntervalUtil interval = new IntervalUtil(1f,1f);
	private IntervalUtil fleetinterval = new IntervalUtil(1f,1f);	
	private long days = Global.getSector().getClock().getTimestamp();
	@Override
	public boolean runWhilePaused() 
	{
		return false;
	}
	
	@Override
	public void advance(float amount) 
	{
		if(Global.getSector().getPlayerFleet() == null)
			return;

		if(!Global.getSector().getPersistentData().containsKey("armaa_hyperSpaceBuff"))
			return;
		if(!Global.getSector().getPlayerFleet().isInHyperspace())
			return;
		
		interval.advance(amount);
		if(interval.intervalElapsed())
		if(Global.getSector().getPlayerFleet() != null && Global.getSector().getPlayerFleet().getFleetData() != null)
		{
			FleetDataAPI fleet = Global.getSector().getPlayerFleet().getFleetData();
			
			for(FleetMemberAPI ship : fleet.getMembersInPriorityOrder())
			{
				if(!ship.getVariant().hasHullMod("strikeCraft"))
					continue;
				
				ship.getStats().getDynamic().getStat(Stats.CORONA_EFFECT_MULT).modifyMult("armaa_carrierStorageHyper",0.0f);
				//ship.setVariant(ship.getVariant(), false, true); 
			}
				Global.getSector().getPersistentData().put("armaa_hyperSpaceBuff","-");
			
		}
	}
	
    @Override
    public void reportPlayerEngagement(EngagementResultAPI result) 
	{
		Global.getSector().getPersistentData().remove("armaa_hyperSpaceBuff");
	}		
	
	@Override
	public void reportShownInteractionDialog(InteractionDialogAPI dialog)
	{
		if(Global.getSector().getPlayerFleet() != null && Global.getSector().getPlayerFleet().getFleetData() != null)
		{
			FleetDataAPI fleet = Global.getSector().getPlayerFleet().getFleetData();
			
			for(FleetMemberAPI ship : fleet.getMembersInPriorityOrder())
			{
				if(!ship.getVariant().hasHullMod("strikeCraft"))
					continue;
				
				ship.getStats().getDynamic().getStat(Stats.CORONA_EFFECT_MULT).unmodify("armaa_carrierStorageHyper");
				//ship.setVariant(ship.getVariant(), false, true); 
				//fleet.syncIfNeeded();

			}
			Global.getSector().getPersistentData().put("armaa_hyperSpaceBuff","-");
			
		}
	}
}
