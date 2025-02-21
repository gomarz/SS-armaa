package data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.util.Misc.Token;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import java.util.Map;
import java.util.Random;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.impl.campaign.intel.contacts.ContactIntel;
import com.fs.starfarer.api.impl.campaign.intel.contacts.ContactIntel.ContactState;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import data.scripts.campaign.intel.armaa_liberationIntel;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import java.util.List;
import java.util.ArrayList;
import data.scripts.campaign.armaa_mrcReprisalListener;
//import com.fs.starfarer.api.impl.campaign.fleets.ArmaaHunterFleetManager;
//wtf i love MOOD RUNES

public class armaa_Hikaru_Utada extends BaseCommandPlugin {
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) 
	{
		String action = params.get(0).getString(memoryMap);
		CampaignFleetAPI pf = Global.getSector().getPlayerFleet();		
		MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
		if (memory == null) return false; // should not be possible unless there are other big problems already

		if ("findValk".equals(action))
		{
			for (FleetMemberAPI member : pf.getFleetData().getMembersListCopy()) {
				if (member.getHullSpec().getBaseHullId().equals("armaa_valkazard") || member.getHullSpec().getHullId().equals("armaa_valkazard")) 
				{
					memory.set("$foundValkId", member.getId(),1f);				
					memory.set("$foundValkClass", member.getHullSpec().getNameWithDesignationWithDashClass(),1f);				
					memory.set("$foundValkName", member.getShipName(),1f);				
					return true;
				}
			}
		}	
		if ("getPlayerFleetId".equals(action))
		{
			memory.set("$armaa_playerFleetId", pf.getId(),1f);	
			return pf.getId().equals(dialog.getInteractionTarget().getId());
		}
		
		if ("removeContact".equals(action))
		{
			for (IntelInfoPlugin curr : Global.getSector().getIntelManager().getIntel(ContactIntel.class)) 
			{
				ContactIntel intel = (ContactIntel) curr;
				if (intel.isEnding() || intel.isEnded() || intel.getState() == ContactState.POTENTIAL) continue;
				
				int importance = intel.getPerson().getImportance().ordinal();
				float rel = intel.getPerson().getRelToPlayer().getRel();
				if(intel.getPerson().getId().equals("armaa_dawn"))
				{
					intel.loseContact(dialog);
				}
			}
		}
		
		else if ("giveSSMech".equals(action))
		{
			String variantId = "armaa_vx_silversword_Hull";
			ShipVariantAPI variant = Global.getSettings().getVariant(variantId).clone();
			FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);
			//assignShipName(member, Factions.INDEPENDENT);
			memory.set("$armaa_giftMech", member,1f);				
			//memory.set("$foundValkClass", member.getHullSpec().getNameWithDesignationWithDashClass());				
			//memory.set("$foundValkName", member.getShipName());				
			return true;
		}
		else if ("giveGuarDual".equals(action))
		{
			String variantId = "armaa_guardual_Hull";
			ShipVariantAPI variant = Global.getSettings().getVariant(variantId).clone();
			FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);
			//assignShipName(member, Factions.INDEPENDENT);
			memory.set("$armaa_giftMech", member,1f);				
			//memory.set("$foundValkClass", member.getHullSpec().getNameWithDesignationWithDashClass());				
			//memory.set("$foundValkName", member.getShipName());				
			return true;
		}		
		else if ("giveCeylon".equals(action))
		{
			String variantId = "armaa_ceylon_Hull";
			ShipVariantAPI variant = Global.getSettings().getVariant(variantId).clone();
			FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);
			//assignShipName(member, Factions.INDEPENDENT);
			memory.set("$armaa_giftCeylong", member,1f);				
			//memory.set("$foundValkClass", member.getHullSpec().getNameWithDesignationWithDashClass());				
			//memory.set("$foundValkName", member.getShipName());				
			return true;
		}		
		/*
		else if ("spawnValkHunters".equals(action))
		{
			if (!Global.getSector().hasScript(ArmaaHunterFleetManager.class))
			{
				Global.getSector().addScript(new ArmaaHunterFleetManager());
				Global.getSector().getCampaignUI().addMessage("You get an ominous feeling..");	
			}
			return true;
		}
		*/

		else if ("addIntel".equals(action)) 
		{
			SectorEntityToken entity = dialog.getInteractionTarget();
			if (params.size() > 1) {
				String id = params.get(1).getString(memoryMap);
				entity = armaa_liberationIntel.getEntity(id);
			}
			//armaa_liberationIntel.addIntelIfNeeded(entity, dialog.getTextPanel());
			armaa_liberationIntel.startExpedition(dialog.getInteractionTarget().getMarket(),entity.getMarket(),new Random());
			return true;
		}
		
		else if ("startReprisal".equals(action)) 
		{
			SectorEntityToken entity = dialog.getInteractionTarget();
			if (params.size() > 1) 
			{
				String id = params.get(1).getString(memoryMap);
				entity = armaa_liberationIntel.getEntity(id);
			}
			else
			{
				List<MarketAPI> potMarkets = new ArrayList();
				potMarkets = Global.getSector().getEconomy().getMarketsCopy();
				//potMarkets.shuffle();
				for( MarketAPI market : potMarkets)
				{
					if(!Global.getSector().getFaction("armaarmatura_pirates").isHostileTo(market.getFaction()))
						continue;
					if(market.getFaction().getId().equals("independent"))
						continue;
					if(!market.hasSpaceport() || market.isHidden())
					{
						continue;
					}						
					
						entity = armaa_liberationIntel.getEntity(market.getId());
				}				

			}
			String str = "UNDETERMINED";
			if(!armaa_mrcReprisalListener.getReprisalTargetName().equals(""))
				str =  armaa_mrcReprisalListener.getReprisalTargetName();
			memory.set("$armaa_reprisalTargetName", str,1f);			
			int value = armaa_mrcReprisalListener.getDaysLeft();
			String reprisalStart= String.valueOf(value);
			if( value <= 0)
				reprisalStart="CURRENTLY ACTIVE";
			memory.set("$armaa_reprisalDaysUntil", reprisalStart,1f);
			String funds = "0c";
			if(Global.getSector().getMemoryWithoutUpdate().get("$armaa_mrcFunds") != null)
				funds = (int)Global.getSector().getMemoryWithoutUpdate().get("$armaa_mrcFunds")+"c";
			memory.set("$armaa_mrcFunds", (String)funds,1f);
			memory.set("$armaa_mrcRep",Global.getSector().getFaction("armaarmatura_pirates").getRelToPlayer().getRel());
			//armaa_liberationIntel.addIntelIfNeeded(entity, dialog.getTextPanel());
			//armaa_liberationIntel.startExpedition(dialog.getInteractionTarget().getMarket(),entity.getMarket(),new Random());
			return true;
		}

		else if ("setReprisalDaysLeft".equals(action)) 
		{
			int daysLeft = 0;
			if (params.size() > 1) 
			{
				daysLeft = params.get(1).getInt(memoryMap);
			}
			else
				return false;
	
			armaa_mrcReprisalListener.setDaysLeft(daysLeft);
			return true;
		}		
		
		else if ("restoreFleet".equals(action)) 
		{
			CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
			ArrayList<FleetMemberAPI> removedShips = new ArrayList();			
			if(Global.getSector().getPlayerFleet().getMemoryWithoutUpdate().contains("$nonAtmoShips"))
			{
				removedShips = (ArrayList<FleetMemberAPI>)Global.getSector().getPlayerFleet().getMemoryWithoutUpdate().get("$nonAtmoShips");
			}
			
			for(FleetMemberAPI member: removedShips)
			{
					Global.getSector().getPlayerFleet().getFleetData().addFleetMember(member);
			}
				Global.getSector().getPlayerFleet().getMemoryWithoutUpdate().unset("$nonAtmoShips");
		}			
		

		else if ("getHireCost".equals(action))
		{
			CampaignFleetAPI mercFleet = (CampaignFleetAPI)dialog.getInteractionTarget();
			int cost = 0;
			for (FleetMemberAPI member : mercFleet.getFleetData().getMembersListCopy()) 
			{
				cost+= member.getBaseValue();
			}
			cost = cost/12;
			memory.set("$armaa_mercCost", cost,1f);
			return true;
		}
		
		else if ("followMe".equals(action))
		{
			CampaignFleetAPI fleet = (CampaignFleetAPI)dialog.getInteractionTarget();
		if(Global.getSector().getPlayerFleet() != null)
			fleet.removeFirstAssignment();
			fleet.addAssignmentAtStart(FleetAssignment.ORBIT_PASSIVE, Global.getSector().getPlayerFleet(), 10f, "Following player", null);
			return true;			
		}
		
		else if ("getSeraphRel".equals(action))
		{
			String id = params.get(1).getString(memoryMap);
			PersonAPI seraph = Global.getSector().getImportantPeople().getPerson(id);
			return seraph.getRelToPlayer().getRel() >= 0.10;	
		}

		else if ("playMusic".equals(action))
		{
			String id = params.get(1).getString(memoryMap);
			Global.getSoundPlayer().playCustomMusic(1, 1, id, true);
			return true;	
		}
		else if ("stopMusic".equals(action))
		{
			Global.getSoundPlayer().pauseCustomMusic();
			return true;	
		}		
		else if ("hasOfficer".equals(action))
		{
			String id = params.get(1).getString(memoryMap);
			for(OfficerDataAPI officer: Global.getSector().getPlayerFleet().getFleetData().getOfficersCopy())
			{
				if(officer.getPerson().getId().equals(id))
					return true;
			}
		}
		else if ("AddSkillToOfficer".equals(action))
		{
			String id = params.get(1).getString(memoryMap);	
			for(OfficerDataAPI officer: Global.getSector().getPlayerFleet().getFleetData().getOfficersCopy())
			{
				if(officer.getPerson().getId().equals(id))
				{
					officer.getPerson().getStats().setSkillLevel("point_defense",1);
					
					return true;
				}
			}			
		}
		else if ("checkKadeOnMarket".equals(action))
		{
			MarketAPI target = Global.getSector().getEntityById("nekki1").getMarket();
			if(target.getAdmin().getId().equals("armaa_kade"))
				return true;
			target.setFactionId("independent");
			target.setPlayerOwned(false);
			Global.getSector().getEconomy().addMarket(target, false);
			target.getPrimaryEntity().setMarket(target);
			for (SectorEntityToken curr : target.getConnectedEntities()) {
				curr.setFaction("independent");
			}
			if(!target.getConstructionQueue().hasItem(Industries.SPACEPORT))
				target.getConstructionQueue().addToEnd(Industries.SPACEPORT,0);
			target.setPlanetConditionMarketOnly(false);
			target.setAdmin(Global.getSector().getImportantPeople().getPerson("armaa_kade"));
			target.getCommDirectory().addPerson(Global.getSector().getImportantPeople().getPerson("armaa_kade"),0);
			target.setFreePort(true); 				
			if (!target.hasIndustry(Industries.POPULATION))
			{
				target.addIndustry(Industries.POPULATION);
			}
			
			if (!target.hasSubmarket(Submarkets.SUBMARKET_OPEN)) {
				target.addSubmarket(Submarkets.SUBMARKET_OPEN);
			}
			if (!target.hasSubmarket(Submarkets.SUBMARKET_BLACK)) {
				target.addSubmarket(Submarkets.SUBMARKET_BLACK);
			}
			if (Misc.isMilitary(target) || 
					target.hasIndustry(Industries.MILITARYBASE) || 
					target.hasIndustry(Industries.HIGHCOMMAND)) {
				if (!target.hasSubmarket(Submarkets.GENERIC_MILITARY)) {
					target.addSubmarket(Submarkets.GENERIC_MILITARY);
				}
			}
			target.addCondition(Conditions.DISSIDENT);
			//RecentUnrest.get(target).setPenalty(0);
			target.getMemoryWithoutUpdate().unset("$playerHostileTimeout");
			target.getMemoryWithoutUpdate().unset("$playerHostileTimeoutStr");		
			return true;
		}
		return false;
	}
}