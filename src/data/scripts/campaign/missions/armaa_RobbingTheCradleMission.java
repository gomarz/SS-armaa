package data.scripts.campaign.missions;

import java.awt.Color;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.impl.campaign.missions.*;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class armaa_RobbingTheCradleMission extends HubMissionWithBarEvent {

	public static float PROB_COMPLICATIONS = 0.5f;
	public static float PROB_PATROL_AFTER = 0.5f;
	public static float MISSION_DAYS = 120f;
	
	public static enum Stage {
		DROP_OFF,
		COMPLETED,
		FAILED,
	}
	
	protected String thing;
	protected SectorEntityToken target;
	protected StarSystemAPI system;
	
	@Override
	protected boolean create(MarketAPI createdAt, boolean barEvent) {
		//genRandom = Misc.random;
		

		
		// I may have had a bit too much fun with this
		
		// might be useful - leave it for now
		
		thing = pickOne("an apparently inert data chip",
						"a rad-shielded salvors' TriPad", 
						"a cloudy, damaged data crystal",
						"a small EM-shielded safe",
						"a hardcopy book made of paper",
						"a sewing kit with strangely curved needles",
						"a single rose in a miniaturized stasis field",
						"a small packet of seeds embossed with a Luddic sigil",
						"an aged, twisted piece of wood",
						"an irradiated bulkhead in a shielded crate",
						"a black Volturnian lobster shell",
						"a biosample in sealed cylinder",
						"a peculiar vial of blood",
						"a powdered substance in a triple-sealed container",
						"a single vacuum-desiccated finger",
						"a tiny unicorn pendant",
						"a delicate crane made of folded paper", 
						"a scratched golden ring",
						"a gravimatic trap holding some kind of glowing mote",
						"a battered Hegemony officer's pistol",
						"a large brilliant-cut diamond",
						"a captain's safe cut from its bulkhead",
						"a glowing blue crystal in a sealed tube"
						);
		
		PersonAPI person = getPerson();
		if (person == null) return false;
		
		
		if (!setPersonMissionRef(person, "$armaa_rtc_ref")) {
			return false;
		}
				
		requireSystemNot(createdAt.getStarSystem());
		requireSystemInterestingAndNotUnsafeOrCore();
		preferSystemInInnerSector();
		preferSystemUnexplored();
		preferSystemInDirectionOfOtherMissions();
		
		system = pickSystem();
		if (system == null) return false;
		
		target = spawnMissionNode(new LocData(EntityLocationType.HIDDEN_NOT_NEAR_STAR, null, system));
		target.addTag("armaa_rtcTarget");
		if (!setEntityMissionRef(target, "$armaa_rtc_ref")) return false;
		
		makeImportant(target, "$armaa_rtc_target", Stage.DROP_OFF);
		//setMapMarkerNameColor(Misc.getGrayColor());

		setStartingStage(Stage.DROP_OFF);
		setSuccessStage(Stage.COMPLETED);
		setFailureStage(Stage.FAILED);
		
		setStageOnMemoryFlag(Stage.COMPLETED, target, "$armaa_rtc_completed");
		setTimeLimit(Stage.FAILED, MISSION_DAYS, null);
		

		setCreditReward(CreditReward.HIGH);
		
		if (rollProbability(PROB_COMPLICATIONS)) {
			triggerComplicationBegin(Stage.DROP_OFF, ComplicationSpawn.APPROACHING_OR_ENTERING,
					system, Factions.PIRATES,
					"the " + getWithoutArticle(thing), "it",
					"the " + getWithoutArticle(thing) + " given to you by " + person.getNameString(),
					0,
					true, ComplicationRepImpact.NONE, null);
			triggerComplicationEnd(true);
		}
		
		return true;
	}
	
	@Override
	protected void notifyEnding() {
		super.notifyEnding();
		
		if (isSucceeded() && rollProbability(PROB_PATROL_AFTER)) {
			PersonAPI person = getPerson();
			if (person == null || person.getMarket() == null) return;
			String patrolFaction = person.getMarket().getFactionId();
			if (patrolFaction.equals(person.getFaction().getId()) || 
					Misc.isPirateFaction(person.getMarket().getFaction()) ||
					Factions.PLAYER.equals(patrolFaction)) {
				return;
			}
			
			DelayedFleetEncounter e = new DelayedFleetEncounter(genRandom, getMissionId());
			e.setDelayNone();
			e.setLocationInnerSector(true, patrolFaction);
			e.beginCreate();
			e.triggerCreateFleet(FleetSize.LARGE, FleetQuality.DEFAULT, patrolFaction, FleetTypes.PATROL_LARGE, new Vector2f());
			e.setFleetWantsThing(patrolFaction, 
					"the dead drop coordinates", "they",
					"the dead drop coordinates given to you by " + person.getNameString(),
					0,
					true, ComplicationRepImpact.LOW,
					DelayedFleetEncounter.TRIGGER_REP_LOSS_MEDIUM, getPerson());
			e.triggerSetAdjustStrengthBasedOnQuality(true, getQuality());
			e.triggerMakeLowRepImpact();
			e.triggerSetPatrol();
			e.triggerSetStandardAggroInterceptFlags();
			e.endCreate();
		}
	}

	protected void updateInteractionDataImpl() {
		set("$armaa_rtc_barEvent", isBarEvent());
		set("$armaa_rtc_manOrWoman", getPerson().getManOrWoman());
		set("$armaa_rtc_heOrShe", getPerson().getHeOrShe());
		set("$armaa_rtc_reward", Misc.getWithDGS(getCreditsReward()));
		
		set("$armaa_rtc_aOrAnThing", thing);
		set("$armaa_rtc_thing", getWithoutArticle(thing));
		
		set("$armaa_rtc_personName", getPerson().getNameString());
		set("$armaa_rtc_systemName", system.getNameWithLowercaseTypeShort());
		set("$armaa_rtc_dist", getDistanceLY(target));
	}
	
	@Override
	public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;
		Color h = Misc.getHighlightColor();
		if (currentStage == Stage.DROP_OFF) {
			info.addPara("Deliver " + thing + " to the dead drop location at the " +
					"specified coordinates in the " + system.getNameWithLowercaseTypeShort() + ".", opad);
		}
	}

	@Override
	public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
		Color h = Misc.getHighlightColor();
		if (currentStage == Stage.DROP_OFF) {
			info.addPara("Deliver " + getWithoutArticle(thing) + " to specified location in the " +  
					system.getNameWithLowercaseTypeShort(), tc, pad);
			return true;
		}
		return false;
	}	
	
	@Override
	public String getBaseName() {
		return "Dead Drop";
	}
	
}






