package data.scripts.campaign.missions;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.People;
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
import com.fs.starfarer.api.fleet.FleetMemberAPI;


public class armaa_DescentMission extends HubMissionWithSearch {

	public static enum Stage {
		GO_TO_JENIUS,
		GO_TO_VARYNS,
		GO_TO_MESHAN,
		DESCENT,
		DEBRIEF,
		COMPLETED
	}
	protected MarketAPI nekki1;		
	@Override
	protected boolean create(MarketAPI createdAt, boolean barEvent) {
		// if already accepted by the player, abort
		if (!setGlobalReference("$armaa_des_ref", "$armaa_des_inProgress")) {
			return false;
		}

		nekki1 = Global.getSector().getEconomy().getMarket("nekki1_market");
		if (nekki1 == null) return false;
		//if (!nekki1.getFactionId().equals("sindrian_diktat")) return false;
		setStartingStage(Stage.GO_TO_JENIUS);
		addSuccessStages(Stage.COMPLETED);
		setStoryMission();
		makeImportant(nekki1.getAdmin(), "$armaa_des_tookTheJob", Stage.GO_TO_JENIUS);				
		makeImportant(nekki1, "$armaaWFStarted", Stage.GO_TO_VARYNS);
		setStageOnGlobalFlag(Stage.GO_TO_VARYNS, "$armaaWFStarted");		
		setStageOnGlobalFlag(Stage.GO_TO_MESHAN, "$armaaWFSpokeToSettlement");
		makeImportant(Global.getSector().getEntityById("nekki2"), "$armaaWFSpokeToSettlement", Stage.GO_TO_MESHAN);
		setStageOnGlobalFlag(Stage.DESCENT, "$armaa_capturedMorgana");
		makeImportant(Global.getSector().getEntityById("nekki3"), "$armaa_capturedMorgana", Stage.DESCENT);		
		setStageOnGlobalFlag(Stage.DEBRIEF, "$armaaWFGravionBattleComplete");
		makeImportant(nekki1.getAdmin(), "$armaaWFGravionBattleComplete", Stage.DEBRIEF);			
		setStageOnGlobalFlag(Stage.COMPLETED, "$armaa_des_Completed");
		
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
        beginStageTrigger(Stage.COMPLETED);
		triggerSetGlobalMemoryValue("$armaa_des_missionCompleted", true);
		endTrigger();
		
		return true;
	}
		
	protected void updateInteractionDataImpl() {
			set("$armaa_des_stage", getCurrentStage());
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
		if (currentStage == Stage.GO_TO_JENIUS) {
			info.addPara("Speak to Kade on " + nekki1.getName()+".", opad);
		}				
		if (currentStage == Stage.GO_TO_VARYNS) {
			info.addPara("Travel to the tech-mining facility on " + nekki1.getName()+".", opad);
		}				
		if (currentStage == Stage.GO_TO_MESHAN) {
			info.addPara("Travel to the subterranean facility on " + nekki1.getName()+".", opad);
		}
		if (currentStage == Stage.DESCENT) {
			info.addPara("Travel to Gravion and investigate the station", opad);
		}
		if (currentStage == Stage.DEBRIEF) {
			info.addPara("Return to Kade for debrief", opad);
		}		
	}

	@Override
	public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
		Color h = Misc.getHighlightColor();
		if (currentStage == Stage.GO_TO_JENIUS) {
			info.addPara("Speak to Kade on Jenius", tc, pad);
			return true;
		}		
		if (currentStage == Stage.GO_TO_VARYNS) {
			info.addPara("Travel to the tech-mining facility on Jenius", tc, pad);
			return true;
		}
		if (currentStage == Stage.GO_TO_MESHAN) {
			info.addPara("Travel to the subterranean facility on Meshan", tc, pad);
			return true;
		}
		if (currentStage == Stage.DESCENT) {
			info.addPara("Travel to Gravion and investigate the station", tc, pad);
			return true;
		}
		if (currentStage == Stage.DEBRIEF) {
			info.addPara("Return to Kade for debrief", tc, pad);
		}				
		if (currentStage == Stage.COMPLETED) {
			info.addPara("Completed", tc, pad);
		}				
		return false;
	}

	@Override
	public String getBaseName() {
		return "Descent";
	}

	@Override
	public String getPostfixForState() {
		if (startingStage != null) {
			return "";
		}
		return super.getPostfixForState();
	}
}





