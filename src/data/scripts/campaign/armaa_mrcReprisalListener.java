package data.scripts.campaign;

import com.fs.starfarer.api.campaign.BaseCampaignEventListenerAndScript;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Random;
import java.util.Collections;
import data.scripts.campaign.intel.armaa_reprisalIntel;
import lunalib.lunaSettings.LunaSettings;

public class armaa_mrcReprisalListener extends BaseCampaignEventListenerAndScript
{
	//Logger log = Logger.getLogger(armaa_hyperSpaceImmunity.class.getName());
	private IntervalUtil interval = new IntervalUtil(1f,1f);
	private IntervalUtil fleetinterval = new IntervalUtil(1f,1f);	
	private long days = Global.getSector().getClock().getTimestamp();
	private final float DAYS_MONTH = 30f;
	private int maxWait = 24; // Maximum cooldown in months
	private int minWait = 6;  // Minimum cooldown in months
	private int minHQs = 1;   // Minimum HQ count
	private int maxHQs = 4;   // HQ count at which cooldown is minimu
	
	private static class reprisalData
	{
		float loot = 0f;
		int daysLeft = 0;
		boolean playerParticipated = false;
		SectorEntityToken reprisalTarget = null;
		String reprisalTargetName = "";
		float playerDamage = 0f;
		float aiDamage = 0f;
		//MRC won't ever attack these guys
		String backingFaction;
	}
    // Add a getter for reprisalData instance
    private static reprisalData getReprisalData() 
	{
        if (Global.getSector().getMemoryWithoutUpdate().get("$armaa_reprisalData") instanceof reprisalData) {
			reprisalData data = (reprisalData) Global.getSector().getMemoryWithoutUpdate().get("$armaa_reprisalData");
            return data;
        }
        return null;
    }
	public static float getLoot() {
		if(getReprisalData() == null)
			return 0;
		reprisalData data = getReprisalData();
		return data.loot;
	}

	public static int getDaysLeft() {
		if(getReprisalData() == null)
			return 0;
		reprisalData data = getReprisalData();			
		return data.daysLeft;
	}
	public static void setDaysLeft(int days) {
		if(getReprisalData() == null)
			return;
		reprisalData data = getReprisalData();			
		data.daysLeft = days;
	}
	public static boolean hasPlayerParticipated() {
		if(getReprisalData() == null)
			return false;
		reprisalData data = getReprisalData();			
		return data.playerParticipated;
	}

	public static SectorEntityToken getReprisalTarget() {
		if(getReprisalData() == null)
			return null;
		reprisalData data = getReprisalData();			
		return data.reprisalTarget;
	}

	public static String getReprisalTargetName() {
		if(getReprisalData() == null)
			return "UNDETERMINED";
		reprisalData data = getReprisalData();			
		return data.reprisalTargetName;
	}

	public static float getPlayerDamage() {
		if(getReprisalData() == null)
			return 0;
		reprisalData data = getReprisalData();			
		return data.playerDamage;
	}

	public static float getAiDamage() {
		if(getReprisalData() == null)
			return 0;
		reprisalData data = getReprisalData();			
		return data.aiDamage;
	}	
	@Override
	public boolean runWhilePaused() 
	{
		return false;
	}
	public static double calculateCooldown(int hqCount, int maxWait, int minWait, int minHQs, int maxHQs) {
        // Clamp HQ count between minHQs and maxHQs
        int effectiveHQ = Math.max(Math.min(hqCount, maxHQs), minHQs);
        
        // Linear scaling formula
		return minWait + (maxWait - minWait) * (double)(maxHQs - effectiveHQ) / (maxHQs - minHQs);
        //return 2;
    }
	public int checkHQDisruptionMod()
	{
		int extraDays = 0;
		for(MarketAPI market :Global.getSector().getEconomy().getMarketsCopy())
		{
			if(market.hasIndustry("armaa_mrc") && market.getIndustry("armaa_mrc").isDisrupted())
				extraDays+=market.getIndustry("armaa_mrc").getDisruptedDays();
		}
		if(!Global.getSector().getMemoryWithoutUpdate().contains("$armaa_reprisalDisruptMod"))
		{
			Global.getSector().getMemoryWithoutUpdate().set("$armaa_reprisalDisruptMod",0);			
		}

		int oldDays = (Integer)Global.getSector().getMemoryWithoutUpdate().get("$armaa_reprisalDisruptMod");
		// this likely means the disruption time has just decreased
		// just set this to our new value
		// if we disrupted 100 days previously, 
		System.out.println("OLD: " + oldDays + " VS extraDays:" + extraDays); 
		if(oldDays > extraDays)
			oldDays = extraDays;
		// the new value is greater, so something was disrupted again
		// OR another HQ was disrupted
		else
		{
			int mod = extraDays - oldDays;
			oldDays = extraDays;
			Global.getSector().getMemoryWithoutUpdate().set("$armaa_reprisalDisruptMod",oldDays);			
			return mod;
		}
		Global.getSector().getMemoryWithoutUpdate().set("$armaa_reprisalDisruptMod",oldDays);
		return 0;
	}	
	public int getNumHQs()
	{
		int numHQs = 0;
		int disruptedHQs = 0;
		for(MarketAPI market :Global.getSector().getEconomy().getMarketsCopy())
		{
			if(market.hasIndustry("armaa_mrc"))
			{
				numHQs++;
				if(market.getIndustry("armaa_mrc").isDisrupted() || !market.getIndustry("armaa_mrc").isFunctional())
					disruptedHQs++;
			}
		}
		return numHQs-disruptedHQs;
	}
	@Override
	public void advance(float amount) 
	{
		boolean hasLL = Global.getSettings().getModManager().isModEnabled("lunalib");
		boolean reprisalEnabled = false;
		if(hasLL)
		{
			reprisalEnabled = LunaSettings.getBoolean("armaa", "armaa_enableReprisal");
		}
		if(!reprisalEnabled)
			return;
		if(getNumHQs() <= 0)
		{
			return;
		}		
		if(Global.getSector().getPlayerFleet() == null)
			return;
		// Store the time in global memory?
		// should only have one reprisal occur at a time, so that should be fine
		// Math.randomInRange(DAYS_MONTH*24,DAYS_MONTH*24
		//System.out.println("IN");
		//omfg
		reprisalData data = null;
		if(Global.getSector().getMemoryWithoutUpdate().get("$armaa_reprisalData") != null)
		{
			data = getReprisalData();
		}
		else
		{
			data = new reprisalData();
			Double val = calculateCooldown(getNumHQs(), maxWait, minWait, minHQs, maxHQs)*30.44;
			data.daysLeft = val.intValue();
			Global.getSector().getMemoryWithoutUpdate().set("$armaa_reprisalData",data);
			//for(FactionAPI faction :Global.getSector().getAllFactions())
			//{
			//	if(faction.getRelationship("armaarmatura_pirates")
				
				//data.backingFaction
			//}
		}

		if(data.reprisalTarget == null && data.daysLeft <= 0)
		{
			List<MarketAPI> potMarkets = new ArrayList();
			List<FactionAPI> badBoys = new ArrayList();
			potMarkets = Global.getSector().getEconomy().getMarketsCopy();
			Collections.shuffle(potMarkets);
			SectorEntityToken entity = null;
			boolean hasNex = false;
			if (Global.getSettings().getModManager().isModEnabled("nexerelin"))
			{
				hasNex = true;
				for(FactionAPI fac : Global.getSector().getAllFactions())
				{
					if(fac.getId().equals("armaarmatura_pirates"))
						continue;
					if(!fac.isHostileTo(Global.getSector().getFaction("armaarmatura_pirates")))
						continue;					
					if(fac.getMemoryWithoutUpdate().get("$nex_badboy") != null)
					{
						badBoys.add(fac);
						Global.getSector().getFaction("armaarmatura_pirates").setRelationship(fac.getId(),Global.getSector().getFaction("armaarmatura_pirates").getRelationship(fac.getId())-(float)fac.getMemoryWithoutUpdate().get("$nex_badboy")/1000f);
					}
				}
			}
			float sinLevel = -999999f;
			for( MarketAPI market : potMarkets)
			{
				if(!Global.getSector().getFaction("armaarmatura_pirates").isHostileTo(market.getFaction()))
					continue;
				if(market.getFaction().getId().equals("independent"))
					continue;
				
				if(market.getMemory().get("$story_critical") instanceof Boolean)
				{
					boolean storyCrit = (Boolean)market.getMemory().get("$story_critical");
					if (storyCrit)
						continue;
				}
					
				if(!market.hasSpaceport() || market.isHidden())
				{
					continue;
				}
				if(!hasNex || badBoys.isEmpty())
				{
					entity = armaa_reprisalIntel.getEntity(market.getId());
					break;
				}
				else
				{
					if(!badBoys.contains(market.getFaction()))
						continue;
					float infamy = (float)market.getFaction().getMemoryWithoutUpdate().get("$nex_badboy");
					if(infamy > sinLevel)
					{
						entity = armaa_reprisalIntel.getEntity(market.getId());
						sinLevel = infamy;
					}
					if(entity.getFaction().getRelationship("armaarmatura_pirates") > -0.50f)
					{
						entity.getFaction().setRelationship("armaarmatura_pirates",-0.50f);
					}
				}
			}			
			if(entity == null)
				return;
			data.reprisalTarget = entity;	
			data.reprisalTargetName = entity.getName();				
			//armaa_reprisalIntel.addIntelIfNeeded(entity, null);
			if(Global.getSector().getEconomy().getMarket("exsedol_station_market") == null)
				for(MarketAPI market:Global.getSector().getEconomy().getMarketsCopy())
				{
					if(market.hasIndustry("armaa_mrc") && !market.getIndustry("armaa_mrc").isDisrupted())
					{
						armaa_reprisalIntel.startExpedition(market,"armaarmatura_pirates",entity.getMarket(),new Random());						
						return;
					}
				}
			else
				armaa_reprisalIntel.startExpedition(Global.getSector().getEconomy().getMarket("exsedol_station_market"),"armaarmatura_pirates",entity.getMarket(),new Random());	
		}
		else if(Global.getSector().getClock().getElapsedDaysSince(days) > 1)
		{
			int mod = checkHQDisruptionMod();
			data.daysLeft = mod+data.daysLeft - (int)Global.getSector().getClock().getElapsedDaysSince(days);
			days = Global.getSector().getClock().getTimestamp();	
		}
	}
	
    @Override
    public void reportPlayerEngagement(EngagementResultAPI result) 
	{
		if(getReprisalData() == null)
			return;
		reprisalData data = getReprisalData();
		SectorEntityToken target = data.reprisalTarget;
		if(target == null)
			return;
		LocationAPI loc = target.getContainingLocation();		
		if(Global.getSector().getPlayerFleet().getContainingLocation() != loc)
			return;
		
		// We need to figure out which side is MRC affiliated and which side isn't
		// I think the best way to do this, is flag any fleet hostile to MRC as a valid belligerent
		// This can get weird because what if fighting fleet hostile to MRC, reinforced by
		// ships in a faction friendly to MRC? I don't know how likely this is, but I guess
		// we can say the MRC would just consider them accessories
		BattleAPI battle = result.getBattle();
		EngagementResultForFleetAPI mrcSide = null;		
		EngagementResultForFleetAPI enemySide = null;		
		if(result.getLoserResult().getFleet().getFaction().getRelationship("armaarmatura_pirates") <= -0.50)
		{
			mrcSide = result.getWinnerResult();
			enemySide = result.getLoserResult();
		}
		else
		{
			mrcSide = result.getLoserResult();
			enemySide = result.getWinnerResult();
		}

		if(mrcSide == null)
			return;
		float totalDamage = 0f;
		float playerDamage = 0f;
		//can be null if autoresolve
		//use this data to see how much dmg player contributes
		if(result.getLastCombatDamageData() != null)
		for(FleetMemberAPI member : mrcSide.getFleet().getMembersWithFightersCopy())
		{
			Map<FleetMemberAPI,CombatDamageData.DamageToFleetMember> dmgData =	result.getLastCombatDamageData().getDealtBy(member).getDamage();

			for(CombatDamageData.DamageToFleetMember value : dmgData.values())
			{
				totalDamage+= value.hullDamage;
				if(member.getCaptain().getFaction().getId().equals("player"))
					playerDamage+=value.hullDamage;
			}					
		}
		if(playerDamage > 0 && data.playerParticipated == false)
			data.playerParticipated = true;
		data.playerDamage += playerDamage;
		data.aiDamage += totalDamage - playerDamage;
		System.out.println("Total Damage: " + totalDamage + "\n Player Damage: " + playerDamage+ "\n Contribution: " + playerDamage/totalDamage);
	}

	@Override
    public void reportBattleOccurred(CampaignFleetAPI primaryWinner, BattleAPI battle) 
	{
		if(getReprisalData() == null)
			return;
		reprisalData data = getReprisalData();
		if(data.reprisalTarget == null)
			return;
		SectorEntityToken target = data.reprisalTarget;		
		LocationAPI loc = target.getContainingLocation();		
		if(primaryWinner.getContainingLocation() != loc)
			return;
		// need to account for friendly forces that don't include MRC in the battle ?
		// so lets check if mrc actual or just friendly to mrc
		//campaignFleetAPI enemySide = 
		if(primaryWinner.getFaction().equals(Global.getSector().getFaction("armaarmatura_pirates")) || 
			primaryWinner.getFaction().getRelationship("armaarmatura_pirates") >0.10f || 
				primaryWinner.getFaction().equals(Global.getSector().getFaction("armaarmatura_pirates"))
					&& battle.isPlayerInvolved() && battle.isOnPlayerSide(primaryWinner))
		{
			float points = 0f;
			float truePoints = 0f;
			float totalHull = 0f;
			boolean playerHelped = battle.isPlayerInvolved() && battle.isOnPlayerSide(primaryWinner);
			// we need to see whats in thesnapshot, then compare to actual ig
			// anything in snapshotbut not in actual is dead, us that to determine value of fleet
			
			for(CampaignFleetAPI enemy :battle.getOtherSideSnapshotFor(primaryWinner))
			{
				for(FleetMemberAPI member : enemy.getFleetData().getSnapshot())
				{
					if(member.isFrigate())
						points+= 1000f;
					else if(member.isDestroyer())
						points+=2000f;
					else if(member.isCruiser())
						points+=3000f;
					else if(member.isCapital())
						points+=4000f;
					System.out.println("HP for: " + member.getHullId() + ":"+ member.getHullSpec().getHitpoints()*member.getStatus().getHullFraction());					
					if(member.getStatus().getHullFraction() <= 0)
					{
						totalHull += member.getHullSpec().getHitpoints();
						System.out.println(member.getHullId()+" DESTROYED !! Adding " + member.getHullSpec().getHitpoints());
						
					}
					else if(member.getStatus().getHullFraction() < 1)
					{
						float remainder = member.getHullSpec().getHitpoints() - member.getHullSpec().getHitpoints()*member.getStatus().getHullFraction();
						totalHull +=  member.getHullSpec().getHitpoints() - member.getHullSpec().getHitpoints()*member.getStatus().getHullFraction();
						System.out.println(member.getHullId()+" took damage. Adding " + remainder +", full HP was " + member.getHullSpec().getHitpoints());
					}
				}
			}			
			for(CampaignFleetAPI enemy :battle.getOtherSideFor(primaryWinner))
			{
				truePoints+=enemy.getNumFrigates()*1000f;
				truePoints+=enemy.getNumDestroyers()*2000f;
				truePoints+=enemy.getNumCruisers()*3000f;
				truePoints+=enemy.getNumCapitals()*4000f;
				System.out.println(enemy.getFaction().getDisplayName() + " : pts =" + truePoints);
			}
			float destroyedPoints = points - truePoints;
			System.out.println("Snapshot points:" + points + "; post battle points:" + truePoints + "- Loot: " + destroyedPoints);
			System.out.println("Total damage inflicted (HULL) "+ ":" +totalHull);
			data.loot+=destroyedPoints;
			if(!playerHelped)
				data.aiDamage+=totalHull;
			else
			{
				data.aiDamage+=totalHull*0.75f;
				data.playerDamage+=totalHull*0.25f;				
			}
			System.out.println("Total Loot: " + data.loot);				

		}
	}	

    @Override
    public void reportBattleFinished(CampaignFleetAPI primaryWinner, BattleAPI battle) 
	{

	}
}
