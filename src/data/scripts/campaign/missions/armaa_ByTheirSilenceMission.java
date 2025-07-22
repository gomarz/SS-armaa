package data.scripts.campaign.missions;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Voices;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithSearch;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;

import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.campaign.PersonImportance;

import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;


public class armaa_ByTheirSilenceMission extends HubMissionWithSearch {

	public static enum Stage {
		GO_TO_PLANET,
		GET_FLINT_COMMS,
		MEET_FLINT,
		CONFRONT_FLINT,
		DEBRIEF,
		COMPLETED
	}
	protected MarketAPI market;
	@Override
	protected boolean create(MarketAPI createdAt, boolean barEvent) {
		// if already accepted by the player, abort
		if (!setGlobalReference("$armaa_bts_ref", "$armaa_bts_inProgress")) {
			return false;
		}

		resetSearch();
		requireMarketIsNot(createdAt);
		requireMarketFaction("tritachyon");
		requireMarketNotHidden();
		requireMarketNotInHyperspace();
		preferMarketSizeAtLeast(3);
		preferMarketSizeAtMost(7);
		preferMarketInDirectionOfOtherMissions();
		market = pickMarket();
		//variation = Variation.COLONY;
		//danger = RaidDangerLevel.MEDIUM;
		createMissionCharacters();		
		setStartingStage(Stage.GO_TO_PLANET);
		addSuccessStages(Stage.COMPLETED);
		setStoryMission();
		makeImportant(market, "$armaa_bts_tookTheJob", Stage.GO_TO_PLANET);				
		//makeImportant(market, "$armaa_bts_tookTheJob", Stage.GET_FLINT_COMMS);
		setStageOnGlobalFlag(Stage.GET_FLINT_COMMS, "$armaa_btsMetDrunkExec");	
		makeImportant(Global.getSector().getImportantPeople().getPerson("armaa_felner"), "$armaa_btsMetDrunkExec", Stage.GET_FLINT_COMMS);		
		setStageOnGlobalFlag(Stage.MEET_FLINT, "$armaa_btsInvitedToChalet");
		//setStageOnGlobalFlag(Stage.CONFRONT_FLINT, "$armaa_btsInvitedToChalet");
		makeImportant(market, "$armaa_btsInvitedToChalet", Stage.CONFRONT_FLINT);		
		setStageOnGlobalFlag(Stage.DEBRIEF, "$armaa_btsDealtWithFlint");
		makeImportant(createdAt.getAdmin(), "$armaa_btsDealtWithFlint", Stage.DEBRIEF);			
		setStageOnGlobalFlag(Stage.COMPLETED, "$armaa_bts_Completed");
		
		setRepFactionChangesNone();
		setRepPersonChangesNone();
		
		// spawn Diktat Patrol fleet to intercept the player
		beginEnteredLocationTrigger(market.getStarSystem(), false, Stage.DEBRIEF);
		triggerCreateFleet(FleetSize.MEDIUM, FleetQuality.DEFAULT, Factions.TRITACHYON, FleetTypes.PATROL_LARGE, market.getStarSystem());
        triggerAutoAdjustFleetStrengthMajor();
        triggerMakeHostileAndAggressive();
        triggerFleetAllowLongPursuit();
        triggerSetFleetAlwaysPursue();
		triggerMakeLowRepImpact();
        triggerPickLocationTowardsPlayer(market.getPlanetEntity() != null ? market.getPlanetEntity() : Global.getSector().getPlayerFleet(), 90f, getUnits(0.25f));
        triggerSpawnFleetAtPickedLocation("$armaa_bts_tritachyonPatrol", null);
        triggerSetFleetMissionRef("$armaa_bts_ref");
        triggerOrderFleetInterceptPlayer();
        triggerFleetMakeImportant(null, Stage.DEBRIEF);
        endTrigger();
		//*/
        beginStageTrigger(Stage.COMPLETED);
		triggerSetGlobalMemoryValue("$armaa_bts_missionCompleted", true);
		endTrigger();
		
		return true;
	}
		
	protected void updateInteractionDataImpl() {
			set("$armaa_bts_stage", getCurrentStage());
		    set("$armaa_bts_marketName", market.getName());
			set("$armaa_bts_marketId",market.getId());
	}
	
	
	private void createMissionCharacters()
	{
		//felner
		PersonAPI felner = Global.getSector().getFaction("tritachyon").createRandomPerson();
		felner.setRankId(Ranks.CITIZEN);
		felner.setPostId(Ranks.POST_EXECUTIVE);
		felner.setPortraitSprite("graphics/armaa/portraits/armaa_felner.png");
		
		//felner.setGender(FullName.Gender.FEMALE);
		//felner.addTag(Tags.CONTACT_MILITARY);	
		
		//use callsign, maybe have her name change when rep is high enough?
		felner.getName().setFirst("Adam");
		felner.getName().setLast("Felner");
		felner.setId("armaa_felner");
		felner.setImportanceAndVoice(PersonImportance.HIGH, new Random());
		felner.setVoice(Voices.SPACER);
		felner.setPersonality("cautious");
		market.addPerson(felner);
		market.getCommDirectory().addPerson(felner, 99);
		market.getCommDirectory().getEntryForPerson(felner).setHidden(true);				
		Global.getSector().getImportantPeople().addPerson(felner);
		
	}		

	@Override
	protected boolean callAction(String action, String ruleId, final InteractionDialogAPI dialog,
								 List<Token> params, final Map<String, MemoryAPI> memoryMap) {

		return super.callAction(action, ruleId, dialog, params, memoryMap);
	}

	@Override
	public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;
		Color h = Misc.getHighlightColor();
		if (currentStage == Stage.GO_TO_PLANET) {
			info.addPara("Locate the wherabouts of FLINT. Last seen on " + market.getName()+".", opad);
		}				
		if (currentStage == Stage.GET_FLINT_COMMS) {
			info.addPara("Contact FLINT. He was located on " + market.getName()+".", opad);
		}				
		if (currentStage == Stage.MEET_FLINT) {
			info.addPara("Travel to chalet to speak with Flint, on " + market.getName()+".", opad);
		}
		if (currentStage == Stage.CONFRONT_FLINT) {
			info.addPara("Travel to chalet to speak with Flint", opad);
		}
		if (currentStage == Stage.DEBRIEF) {
			info.addPara("Return for debrief", opad);
		}		
	}

	@Override
	public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
		Color h = Misc.getHighlightColor();
		if (currentStage == Stage.GO_TO_PLANET) {
			info.addPara("Locate the wherabouts of FLINT. Last seen on " + market.getName()+".", pad);
			return true;
		}		
		if (currentStage == Stage.GET_FLINT_COMMS) {
			info.addPara("Contact FLINT. He was located on " + market.getName()+".", pad);
			return true;
		}
		if (currentStage == Stage.MEET_FLINT) {
			info.addPara("Travel to chalet to speak with Flint, on " + market.getName()+".", pad);
			return true;
		}
		if (currentStage == Stage.CONFRONT_FLINT) {
			info.addPara("Travel to chalet to speak with Flint", pad);
			return true;
		}
		if (currentStage == Stage.DEBRIEF) {
			info.addPara("Return for debrief", pad);
		}				
		if (currentStage == Stage.COMPLETED) {
			info.addPara("Completed", tc, pad);
		}				
		return false;
	}

	@Override
	public String getBaseName() {
		return "By Their Silence";
	}

	@Override
	public String getPostfixForState() {
		if (startingStage != null) {
			return "";
		}
		return super.getPostfixForState();
	}
}





