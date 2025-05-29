package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import java.util.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.campaign.CargoAPI;
import org.apache.log4j.Logger;

public class armaa_aicoreutilityscript extends BaseHullMod{

	public static final ArrayList<String> DATA_PREFIXES = new ArrayList<String>();
	public static final ArrayList<String> MODS = new ArrayList<String>();	
    private static final Logger Log = Logger.getLogger(armaa_aicoreutilityscript.class); 	
	
	static
	{
		DATA_PREFIXES.add("alpha_core_skymind_check_");
		DATA_PREFIXES.add("beta_core_skymind_check_");
		DATA_PREFIXES.add("gamma_core_skymind_check_");
		
		MODS.add("armaa_skyMindAlpha");
		MODS.add("armaa_skyMindBeta");
		MODS.add("armaa_skyMindGamma");
	}
	public static final String ITEM = Commodities.ALPHA_CORE;
	public static final HashMap<String,String>itemMap = new HashMap<String,String>();
	
	static
	{
		for(String s: DATA_PREFIXES)
		{
			if(s.contains("alpha"))
				itemMap.put(s,ITEM);
			else if(s.contains("beta"))
				itemMap.put(s,Commodities.BETA_CORE);
			else
				itemMap.put(s,Commodities.GAMMA_CORE);
		}
	}
	
	
 	@Override
	public void advanceInCampaign(FleetMemberAPI member, float amount)
	{
        final Map<String, Object> data = Global.getSector().getPersistentData();
		for(String DATA_PREFIX:DATA_PREFIXES)
			for(String MOD:MODS)
			{
				String kept = DATA_PREFIX.substring( 0, DATA_PREFIX.indexOf("_"));
				String remainder = MOD.substring(MOD.indexOf("_")+1, MOD.length());
				boolean sameType = remainder.toLowerCase().contains(kept);
				
				//Log.info("Comparing " + kept + " to " + " remainder: " + remainder + " isSame?: " + sameType);

				if(!sameType)
					continue;
				if(data.containsKey(DATA_PREFIX + member.getId()) && !member.getVariant().hasHullMod(MOD)) 
				{
					//Log.info("Refunding " + " " + itemMap.get(DATA_PREFIX));
					data.remove(DATA_PREFIX + member.getId());
					addPlayerCommodityItem(itemMap.get(DATA_PREFIX),1);
					member.getVariant().removePermaMod("armaa_aicoreutilityscript");

				}
			}

	}
	
    public static void addPlayerCommodityItem(final String id, final int amount) {
        final CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        if (playerFleet == null) {
            return;
        }
        final CargoAPI playerFleetCargo = playerFleet.getCargo();
        System.out.println("Adding the AI core into the cargo...");
        //Log.info("Adding the AI core into the cargo...");
        playerFleetCargo.addCommodity(id, amount);
    }
 
}
