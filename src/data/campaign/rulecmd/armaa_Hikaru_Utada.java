package data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.util.Misc.Token;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.NascentGravityWellAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin.DebrisFieldParams;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin.DebrisFieldSource;
import com.fs.starfarer.api.impl.campaign.procgen.themes.SalvageSpecialAssigner;
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
import data.scripts.world.systems.armaa_nekki;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import java.util.List;
import java.util.ArrayList;
import data.scripts.campaign.armaa_mrcReprisalListener;
import data.scripts.campaign.intel.events.armaa_combatDataEventIntel;
import exerelin.campaign.SectorManager;
//wtf i love MOOD RUNES

public class armaa_Hikaru_Utada extends BaseCommandPlugin {
    @Override
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
		
		if ("removeDawn".equals(action))
		{
                    boolean permanent = true;
			if (params.size() > 1) 
                        {
                            // really, I should evaluate the string
                            // but the only time anything extra goes here is if i want it to be
                            // non permanent
                            permanent = false;
			}
			for(FleetMemberAPI member:pf.getFleetData().getMembersListCopy())
			{
				if(member.getCaptain() == Global.getSector().getImportantPeople().getPerson("armaa_dawn"))
                                {                                  
                                    if(!permanent)
                                    {
                                        ArrayList<MarketAPI> markets = new ArrayList<MarketAPI>();
                                        for(MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
                                        {
                                            if(market.getFaction().isHostileTo("luddic_church"))
                                                continue;
                                            markets.add(market);
                                        }
                                        MarketAPI randomMarket = markets.get((int)(Math.random() * markets.size()));
                                        member.getCaptain().setMarket(randomMarket);
                                        member.getCaptain().getMarket().getCommDirectory().addPerson(member.getCaptain());                                    
                                        member.getCaptain().getMarket().getCommDirectory().getEntryForPerson(member.getCaptain()).setHidden(false);
                                        Global.getSector().getCampaignUI().addMessage(randomMarket.getName());	
                                        memory.set("$armaa_dawnHomeId", randomMarket.getName(),1f);
                                        ContactIntel intel = new ContactIntel(member.getCaptain(), randomMarket);
                                        Global.getSector().getIntelManager().addIntel(intel, false, dialog.getTextPanel());
					OfficerManagerEvent event = new OfficerManagerEvent();
					OfficerManagerEvent.AvailableOfficer f = new OfficerManagerEvent.AvailableOfficer(member.getCaptain(),randomMarket.getId(),10,1000);
					 member.getCaptain().getMemoryWithoutUpdate().set("$ome_hireable", true);
					 member.getCaptain().getMemoryWithoutUpdate().set("$ome_eventRef", event);
					 member.getCaptain().getMemoryWithoutUpdate().set("$ome_hiringBonus", Misc.getWithDGS(f.hiringBonus));		
					 member.getCaptain().getMemoryWithoutUpdate().set("$ome_salary", Misc.getWithDGS(f.salary));	
					event.addAvailable(f);                                        
                                        //member.getCaptain().setPostId(Ranks.POST_MERCENARY);
                                        member.getCaptain().getMemoryWithoutUpdate().set("$postId","officer_for_hire");                                        
                                    }
                                    member.setCaptain(null);  
                                    break;
                                }
			}
			pf.getFleetData().removeOfficer(Global.getSector().getImportantPeople().getPerson("armaa_dawn"));
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
		else if ("buffDawn".equals(action))
		{
			for(FleetMemberAPI member:pf.getFleetData().getMembersListCopy())
			{
				Global.getSector().getImportantPeople().getPerson("armaa_dawn").getFleet().getFleetData().getOfficerData(Global.getSector().getImportantPeople().getPerson("armaa_dawn")).canLevelUp(true);
			}
		}
		else if ("isPlayerFriendlyToMRC".equals(action))
		{
                        return Global.getSector().getFaction("armaarmatura_pirates").getRelToPlayer().getRel() > 25f;
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
		else if ("AddATACIntel".equals(action))
		{
			armaa_combatDataEventIntel foo = new armaa_combatDataEventIntel(dialog.getTextPanel(),true);

			return true;
		}
		else if ("setJeniusOwner".equals(action))
		{
			SectorEntityToken jenius = Global.getSector().getEntityById("nekki1");
			jenius.getMarket().setFactionId("pirates");
			for (SectorEntityToken curr : jenius.getMarket().getConnectedEntities()) {
				curr.setFaction("pirates");
			}

			return true;
		}
		else if ("transferMarketNex".equals(action))
		{
			SectorManager.transferMarket(Global.getSector().getEconomy().getMarket("exsedol_station_market"), Global.getSector().getFaction("armaarmatura_pirates"), Global.getSector().getFaction("pirates"), false, false, null, 0);			
		}
		else if ("doGravionAftermath".equals(action))
		{
			SectorEntityToken jenius = Global.getSector().getEntityById("nekki1");
			jenius.getMarket().getCommDirectory().addPerson(Global.getSector().getImportantPeople().getPerson("armaa_imelda"),999);		
			SectorEntityToken gravion = Global.getSector().getEntityById("nekki3");
			SectorEntityToken magec_gate = Global.getSector().getEntityById("magec_gate");			
			for(SectorEntityToken gate : Global.getSector().getCustomEntitiesWithTag("gate"))
			{
				if(Math.random() < 0.30f || magec_gate == null)
				{
					magec_gate = gate;
					break;
				}
			}
		
			//Spawn Valk if player didn't have it at the startExpedition
			if(!Global.getSector().getPlayerPerson().getMemoryWithoutUpdate().contains("$nex_startingFactionId") || !Global.getSector().getPlayerPerson().getMemoryWithoutUpdate().get("$nex_startingFactionId").equals("armaarmatura_pirates"))
			{
				SectorEntityToken valkazard = armaa_nekki.addDerelict(magec_gate.getStarSystem(), "armaa_valkazard_standard", magec_gate.getOrbit(), ShipRecoverySpecial.ShipCondition.GOOD, true, null);
				// Debris
				DebrisFieldParams param = new DebrisFieldParams(
						800f, // field radius - should not go above 1000 for performance reasons
						4f, // density, visual - affects number of debris pieces
						50f, // duration in days
						0.25f); // days the field will keep generating glowing pieces
				param.source = DebrisFieldSource.MIXED;
				param.baseSalvageXP = 500; // base XP for scavenging in field
				SectorEntityToken debris = Misc.addDebrisField(magec_gate.getContainingLocation(), param, StarSystemGenerator.random);
				SalvageSpecialAssigner.assignSpecialForDebrisField(debris);

				// makes the debris field always visible on map/sensors and not give any xp or notification on being discovered
				debris.setSensorProfile(null);
				debris.setDiscoverable(null);
				debris.setCircularOrbit(valkazard, 45 + 10, 200, 250);
				valkazard.setLocation(magec_gate.getLocation().x,magec_gate.getLocation().y);
				Misc.makeImportant(valkazard,"sick loot");
				valkazard.setDiscoverable(true);			
				valkazard.getMemoryWithoutUpdate().set("$valkazard", true);				
			}
				Global.getSector().getPlayerFleet().getContainingLocation().removeEntity(Global.getSector().getPlayerFleet());
				magec_gate.getContainingLocation().addEntity(Global.getSector().getPlayerFleet());
				Global.getSector().setCurrentLocation(magec_gate.getContainingLocation());		
				Global.getSector().getPlayerFleet().setLocation(magec_gate.getLocation().x,magec_gate.getLocation().y);
				
				StarSystemAPI gamlin = gravion.getStarSystem();
				for(SectorEntityToken token:gamlin.getTerrainCopy())
				{
					if(token.getOrbitFocus() == gravion)
					{
						token.getContainingLocation().removeEntity(token);	
						Global.getSector().getEntityById("magec_gate").getContainingLocation().addEntity(token);						
					}
				}
				for(NascentGravityWellAPI token:gamlin.getGravityWells())
				{
					if(token.getTarget() == gravion)
					{
						gamlin.removeEntity(token);						
						//token.getContainingLocation().removeEntity(token);
						//Global.getSector().getEntityById("magec_gate").getContainingLocation().addEntity(token);
						//token.setLocation(Global.getSector().getEntityById("magec_gate").getStarSystem().getLocation().x,Global.getSector().getEntityById("magec_gate").getStarSystem().getLocation().y);	
						
					}
				}				
				gravion.getContainingLocation().removeEntity(gravion);
				Global.getSector().getEntityById("magec_gate").getContainingLocation().addEntity(gravion);
				//gravion.getStarSystem().autogenerateHyperspaceJumpPoints(true,true);					
				gravion.setLocation(Global.getSector().getEntityById("magec_gate").getLocation().x,Global.getSector().getEntityById("magec_gate").getLocation().y);	
				gravion.setCircularOrbit(Global.getSector().getEntityById("magec_gate").getStarSystem().getCenter(),90,9000,365);	
				for(SectorEntityToken token:Global.getSector().getHyperspace().getEntitiesWithTag("jump_point"))
				{
					if(token.getName().contains("Gravion"))
					{
						Global.getSector().getHyperspace().removeEntity(token);
						Global.getSector().getCampaignUI().addMessage(token.getName()+"", Misc.getNegativeHighlightColor()); 
					}						
					//JumpPointAPI jp = (JumpPointAPI)token;
				}
			
				//doHyperspaceTransition(CampaignFleetAPI fleet, SectorEntityToken jumpLocation, JumpPointAPI.JumpDestination dest)			
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