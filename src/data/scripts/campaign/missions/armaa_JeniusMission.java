package data.scripts.campaign.missions;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Voices;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithSearch;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;

import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.campaign.PersonImportance;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.campaign.FleetAssignment;


public class armaa_JeniusMission extends HubMissionWithSearch {

	public static enum Stage {
		GO_TO_JENIUS,
		DESCENT,
		WAIT_FOR_FOB,
		CITY_RAID,
		DECISION,
		CONTACT,
		VS_KADE,
		VS_IK,
		COMPLETED
	}
	
	protected PersonAPI armaa_kade;
	//protected PersonAPI some_kid; 
	//protected PersonAPI robot; 
	
	protected MarketAPI nekki1;
	//protected MarketAPI asharu;
	
	public static float MISSION_DAYS = 120f;
	
	protected int payment;
	protected int paymentHigh;
	
	@Override
	protected boolean create(MarketAPI createdAt, boolean barEvent) {
		// if already accepted by the player, abort
		if (!setGlobalReference("$armaa_coc_ref", "$armaa_coc_inProgress")) {
			return false;
		}
		
		//setPersonOverride(null);
		
		armaa_kade = getImportantPerson("armaa_kade");
		if (armaa_kade == null) return false;
		
		
		nekki1 = Global.getSector().getEconomy().getMarket("nekki1_market");
		if (nekki1 == null) return false;
		//if (!nekki1.getFactionId().equals("sindrian_diktat")) return false;
		
		setStartingStage(Stage.GO_TO_JENIUS);
		addSuccessStages(Stage.COMPLETED);
		
		setStoryMission();
		
		// yes, these exact numbers.
		payment = 10000;
		paymentHigh = 17000; 
		
		makeImportant(nekki1, "$armaa_coc_tookTheJob", Stage.GO_TO_JENIUS);
		//setStageOnMemoryFlag(Stage.COMPLETED, baird.getMarket(), "$gaTTB_completed");
		
		setStageOnGlobalFlag(Stage.DESCENT, "$armaa_liberationDefeatedDefenders");
		setStageOnGlobalFlag(Stage.WAIT_FOR_FOB, "$armaa_WFCompletedAtmoBattle");
		setStageOnGlobalFlag(Stage.CITY_RAID, "$armaa_startedWFStage2");
		//put these in another mission
		//setStageOnGlobalFlag(Stage.DECISION, "$armaa_WFCompletedAtmoBattle2");
		//setStageOnGlobalFlag(Stage.CONTACT, "$armaa_IKContact");
		//setStageOnGlobalFlag(Stage.VS_IK, "$armaa_IKContact");	
		//setStageOnGlobalFlag(Stage.VS_KADE, "$armaa_IKContact");				
		setStageOnGlobalFlag(Stage.COMPLETED, "$armaa_coc_Completed");
		
		setRepFactionChangesNone();
		setRepPersonChangesNone();
		
		// spawn Diktat Patrol fleet to intercept the player
		/*
		beginEnteredLocationTrigger(nekki1.getStarSystem(), false, Stage.GO_TO_JENIUS);
		triggerCreateFleet(FleetSize.SMALL, FleetQuality.DEFAULT, Factions.DIKTAT, FleetTypes.PATROL_SMALL, nekki1.getStarSystem());
        triggerAutoAdjustFleetStrengthMajor();
        triggerMakeHostileAndAggressive();
        triggerFleetAllowLongPursuit();
        triggerSetFleetAlwaysPursue();
        triggerPickLocationTowardsPlayer(nekki1.getPlanetEntity(), 90f, getUnits(0.25f));
        triggerSpawnFleetAtPickedLocation("$anh_diktatPatrol", null);
        triggerSetFleetMissionRef("$armaa_coc_ref");
        triggerOrderFleetInterceptPlayer();
        triggerFleetMakeImportant(null, Stage.GO_TO_JENIUS);
        endTrigger();
		*/
		
		//not sure if this is best practice, but since these characters don't appear elsewhere...
		createMissionCharacters();
		if(Global.getSector().getMemoryWithoutUpdate().get("$armaa_liberationSpawnedDefenders") == null)
		for(int i = 0; i < 6; i++)
		{
			float fleetpoints = Math.max(100f,(float)Math.random()*200f);
			if(i==5)
				fleetpoints = Math.min(200f/6f,Global.getSector().getPlayerFleet().getFleetPoints()/6f);
			FleetParamsV3 fparams = new FleetParamsV3(
				Global.getSector().getEntityById("armaa_research_station").getLocationInHyperspace(),
				"derelict",
				null,
				FleetTypes.PATROL_SMALL,
				fleetpoints, // combatPts
				0f, // freighterPts 
				0f, // tankerPts
				0f, // transportPts
				0f, // linerPts
				0f, // utilityPts
				.1f // qualityMod
				);
			CampaignFleetAPI fleet =FleetFactoryV3.createFleet(fparams);
			if(i==6 || Math.random()>0.50f)
			FleetFactoryV3.addCommanderAndOfficersV2(fleet,fparams, new Random());
			SectorEntityToken loc = Global.getSector().getEntityById("nekki1");
			// Spawn fleet around player
			loc.getContainingLocation().spawnFleet(
					loc, 25, 25, fleet);
			fleet.setLocation(loc.getLocation().x,loc.getLocation().y);
			fleet.addAssignment(FleetAssignment.ORBIT_AGGRESSIVE, loc, 600f);
			fleet.setFaction("armaarmatura_arusthai",false);
			fleet.getMemoryWithoutUpdate().set("$armaa_liberationDefenders","-");
			Misc.makeImportant(fleet,"$armaa_liberationDefenders");
		}
		Global.getSector().getMemoryWithoutUpdate().set("$armaa_liberationSpawnedDefenders",true);
		for(FactionAPI fac:Global.getSector().getAllFactions())
		{
			if(fac.getId().equals("pirates") || fac.getId().equals("armaarmatura_arusthai"))
				continue;
			
			fac.setRelationship("armaarmatura_arusthai",-99f);
		}
        beginStageTrigger(Stage.COMPLETED);
		triggerSetGlobalMemoryValue("$armaa_coc_missionCompleted", true);
		endTrigger();
		
		return true;
	}
	
	private void createMissionCharacters()
	{
		//redeye
		PersonAPI redeye = Global.getSector().getFaction("independent").createRandomPerson();
		redeye.setRankId(Ranks.PILOT);
		redeye.setPostId(Ranks.POST_SPACER);
		redeye.setPortraitSprite("graphics/armaa/portraits/armaa_redeye.png");
		
		//redeye.setGender(FullName.Gender.FEMALE);
		//redeye.addTag(Tags.CONTACT_MILITARY);	
		
		//use callsign, maybe have her name change when rep is high enough?
		redeye.getName().setFirst("Deadeye");
		redeye.getName().setLast("");
		redeye.setId("armaa_redeye");
		redeye.setImportanceAndVoice(PersonImportance.LOW, new Random());
		redeye.setVoice(Voices.SPACER);
		redeye.setPersonality("aggressive");
		//market.getCommDirectory().addPerson(redeye, 99);
		//market.getCommDirectory().getEntryForPerson(redeye).setHidden(true);				
		//Global.getSector().getImportantPeople().addPerson(admin);
		Global.getSector().getImportantPeople().addPerson(redeye);

		//skills
		redeye.getStats().increaseSkill(Skills.COMBAT_ENDURANCE);
		redeye.getStats().increaseSkill(Skills.HELMSMANSHIP);
		redeye.getStats().increaseSkill(Skills.ENERGY_WEAPON_MASTERY);
		redeye.getStats().increaseSkill(Skills.BALLISTIC_MASTERY);
		redeye.getStats().increaseSkill(Skills.FIELD_MODULATION);
		redeye.getStats().increaseSkill(Skills.TARGET_ANALYSIS);
		redeye.getStats().increaseSkill(Skills.IMPACT_MITIGATION);
		redeye.getStats().increaseSkill(Skills.DAMAGE_CONTROL);
		redeye.getStats().increaseSkill(Skills.POLARIZED_ARMOR);
		redeye.getStats().increaseSkill(Skills.POINT_DEFENSE);
		redeye.getStats().increaseSkill(Skills.MISSILE_SPECIALIZATION);
		redeye.getStats().increaseSkill(Skills.SYSTEMS_EXPERTISE);
		redeye.getStats().setLevel(12);
		

		//imelda
		PersonAPI imelda = Global.getSector().getFaction("luddic_path").createRandomPerson();
		imelda.setRankId(Ranks.SISTER);
		imelda.setPostId(Ranks.POST_SPACER);
		imelda.setPortraitSprite("graphics/armaa/portraits/armaa_imelda.png");			
		//imelda.setGender(FullName.Gender.FEMALE);
		//imelda.addTag(Tags.CONTACT_MILITARY);	
		
		//use callsign, maybe have her name change when rep is high enough?
		imelda.getName().setFirst("Imelda");
		imelda.getName().setLast("Nessus");
		imelda.setId("armaa_imelda");
		imelda.setImportanceAndVoice(PersonImportance.LOW, new Random());
		imelda.setVoice(Voices.SPACER);
		imelda.setPersonality("reckless");
		//market.getCommDirectory().addPerson(imelda, 99);
		//market.getCommDirectory().getEntryForPerson(imelda).setHidden(true);				
		//Global.getSector().getImportantPeople().addPerson(admin);
		Global.getSector().getImportantPeople().addPerson(imelda);
		
		//skills
		imelda.getStats().increaseSkill(Skills.COMBAT_ENDURANCE);
		imelda.getStats().increaseSkill(Skills.HELMSMANSHIP);
		//validSkills.add(Skills.ENERGY_WEAPON_MASTERY);
		imelda.getStats().increaseSkill(Skills.BALLISTIC_MASTERY);
		//validSkills.add(Skills.FIELD_MODULATION);
		//validSkills.add(Skills.TARGET_ANALYSIS);
		imelda.getStats().increaseSkill(Skills.IMPACT_MITIGATION);
		imelda.getStats().increaseSkill(Skills.DAMAGE_CONTROL);
		imelda.getStats().increaseSkill(Skills.POLARIZED_ARMOR);
		imelda.getStats().increaseSkill(Skills.POINT_DEFENSE);
		imelda.getStats().increaseSkill(Skills.MISSILE_SPECIALIZATION);
		imelda.getStats().increaseSkill(Skills.SYSTEMS_EXPERTISE);
		imelda.getStats().increaseSkill(Skills.WOLFPACK_TACTICS);
		imelda.getStats().increaseSkill(Skills.SUPPORT_DOCTRINE);
		imelda.getStats().setLevel(11);
		
	}
	
	protected void updateInteractionDataImpl() {
			set("$armaa_coc_stage", getCurrentStage());
			//set("$anh_robedman", armaa_kade);
			//set("$anh_payment", Misc.getWithDGS(payment));
			//set("$anh_paymentHigh", Misc.getWithDGS(paymentHigh));
	}

	@Override
	protected boolean callAction(String action, String ruleId, final InteractionDialogAPI dialog,
								 List<Token> params, final Map<String, MemoryAPI> memoryMap) {
//		if ("THEDUEL".equals(action)) {
//			TextPanelAPI text = dialog.getTextPanel();
//			text.setFontOrbitronUnnecessarilyLarge();
//			Color color = Misc.getBasePlayerColor();
//			color = Global.getSector().getFaction(Factions.HEGEMONY).getBaseUIColor();
//			text.addPara("THE DUEL", color);
//			text.setFontInsignia();
//			text.addImage("misc", "THEDUEL");
//			return true;
//		}
		
		return super.callAction(action, ruleId, dialog, params, memoryMap);
	}

	@Override
	public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;
		Color h = Misc.getHighlightColor();
				
		if (currentStage == Stage.GO_TO_JENIUS) {
			info.addPara("Assist the mercenary forces with clearing out the enemies surrounding " + nekki1.getName()+".", opad);
		}
		if (currentStage == Stage.DESCENT) {
			info.addPara("Travel to " + nekki1.getName()+" and begin planetfall", opad);
		}
		if (currentStage == Stage.WAIT_FOR_FOB) {
			info.addPara("While the main objective waits, there's no shortage of side work to be done. Kade's lined up a variety of missions - each completely tangential to the larger plan but useful in building trust and a reputation. From shoring up allies to rooting out potential meddlers, these tasks might feel like detours, but they'll get you closer to the inner circle and, ultimately, Kade's next big operation.", opad);
		}
		if (currentStage == Stage.CITY_RAID) {
			info.addPara("After establishing a strong foothold on Jenius, Kade has identified a nearby Arusthaian city as a potential threat. Intelligence indicates this city could mobilize against the new occupation, tipping the scales back in Arusthai's favor.", opad);
			info.addImage(Global.getSettings().getSpriteName("illustrations","armaa_ceylon_illus"),300f,10f);			
			info.addPara("To prevent this, you're tasked with a preemptive strike on the DAS Ceylon - the largest and most symbolic ship in the Arusthai fleet. Eliminating this vessel will cripple their defense capabilities and send an unmistakable message to other potential resistors.",opad);
		}
		if (currentStage == Stage.COMPLETED) 
		{
			info.addPara("With the DAS Ceylon reduced to wreckage, Arusthaian forces lie in ruin, but the aftermath is dire. The carrier's fall has scattered burning debris across the city below, igniting fires that yet rage through civilian districts.", opad);			
			info.addPara("Not long after the dust settles, Kade’s comm signal flashes on your TriPad. He seems pleased with the results, though there's an unsettling intensity as he speaks of 'terms'—the leverage he now holds over Arusthai's shattered defenses. As he hints at the broader picture, a sliver of doubt might creep in about his true motives. But for now, there’s nothing more to do but stand by..",opad);
			info.addPara("With ground teams scouring the ruins for resources, intel and ill-gotten goods, Kade urges you to be ready. The operation may have slowed for the moment, but it’s clear he’s only just beginning—and your role is far from over.",opad);
		}
		if (currentStage == Stage.CONTACT) 
		{
			info.addPara("The sender of the mysterious message insists that Kade used you to wipe out a potential ally. If you want the truth, you’re given coordinates and told to come alone.", opad);			
			info.addPara("Following the lead, you arrived at a hidden bunker on Jenius and met a man named Darius. He explained that Kade's ambitions weren't about liberation—they were about domination. Once, Kade had worked alongside an AI called IRON KING to protect Jenius, but twisted its purpose to secure control over the planet. When the AI defied him to defend Jenius, Kade deemed it a threat. Now, Darius suggested, the AI remains focused on its original mission, while Kade wages a war to reclaim his 'throne'.",opad);
			info.addPara("Darius left you with a choice—follow Kade or challenge his narrative. You know you’ll have to act soon, but for now, you return to your ship, taking in everything you’ve learned. The truth weighs heavily, and with the fate of Jenius hanging in the balance, you prepare for the next step.",opad);
		}
		if (currentStage == Stage.VS_KADE) 
		{
			info.addPara("The sender of the mysterious message insists that Kade used you to wipe out a potential ally. If you want the truth, you’re given coordinates and told to come alone.", opad);			
			info.addPara("Following the lead, you arrived at a hidden bunker on Jenius and met a man named Darius. He explained that Kade's ambitions weren't about liberation—they were about domination. Once, Kade had worked alongside an AI called IRON KING to protect Jenius, but twisted its purpose to secure control over the planet. When the AI defied him to defend Jenius, Kade deemed it a threat. Now, Darius suggested, the AI remains focused on its original mission, while Kade wages a war to reclaim his 'throne'.",opad);
			info.addPara("Darius left you with a choice—follow Kade or challenge his narrative. You know you’ll have to act soon, but for now, you return to your ship, taking in everything you’ve learned. The truth weighs heavily, and with the fate of Jenius hanging in the balance, you prepare for the next step.",opad);
		}	
		if (currentStage == Stage.VS_IK) 
		{
			info.addPara("The sender of the mysterious message insists that Kade used you to wipe out a potential ally. If you want the truth, you’re given coordinates and told to come alone.", opad);			
			info.addPara("Following the lead, you arrived at a hidden bunker on Jenius and met a man named Darius. He explained that Kade's ambitions weren't about liberation—they were about domination. Once, Kade had worked alongside an AI called IRON KING to protect Jenius, but twisted its purpose to secure control over the planet. When the AI defied him to defend Jenius, Kade deemed it a threat. Now, Darius suggested, the AI remains focused on its original mission, while Kade wages a war to reclaim his 'throne'.",opad);
			info.addPara("Darius left you with a choice—follow Kade or challenge his narrative. You know you’ll have to act soon, but for now, you return to your ship, taking in everything you’ve learned. The truth weighs heavily, and with the fate of Jenius hanging in the balance, you prepare for the next step.",opad);
		}			
	}

	@Override
	public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
		Color h = Misc.getHighlightColor();
		if (currentStage == Stage.GO_TO_JENIUS) {
			info.addPara("Clear out the drone fleets surrounding Jenius", tc, pad);
			return true;
		}
		if (currentStage == Stage.DESCENT) {
			info.addPara("Travel to " + nekki1.getName()+" and begin planetfall", tc, pad);
			return true;
		}	
		if (currentStage == Stage.WAIT_FOR_FOB) {
			info.addPara("Complete tasks for Kade", tc, pad);
			return true;
		}
		if (currentStage == Stage.CITY_RAID) {
			info.addPara("Eliminate the DAS Ceylon", tc, pad);
		}
		if (currentStage == Stage.COMPLETED) {
			info.addPara("Completed", tc, pad);
		}
		if (currentStage == Stage.CONTACT) {
			info.addPara("Confront Kade or IRON KING", tc, pad);
		}					
		return false;
	}

	@Override
	public String getBaseName() {
		return "A Crown of Cinders";
	}

	@Override
	public String getPostfixForState() {
		if (startingStage != null) {
			return "";
		}
		return super.getPostfixForState();
	}
}





