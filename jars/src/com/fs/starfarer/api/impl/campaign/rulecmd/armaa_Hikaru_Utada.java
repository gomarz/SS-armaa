package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import java.util.List;
import java.util.Map;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;


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
				if (member.getHullSpec().getHullId().equals("armaa_valkazard")) 
				{
					memory.set("$foundValkId", member.getId(),1f);				
					memory.set("$foundValkClass", member.getHullSpec().getNameWithDesignationWithDashClass(),1f);				
					memory.set("$foundValkName", member.getShipName(),1f);				
					return true;
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
			Global.getSoundPlayer().playCustomMusic(2, 2, id, true);
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
		return false;
	}
}