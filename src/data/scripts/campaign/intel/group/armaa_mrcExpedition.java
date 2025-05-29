package data.scripts.campaign.intel.group;

import java.awt.Color;
import java.util.Random;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.command.WarSimScript;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.OptionalFleetData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.econ.impl.OrbitalStation;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.HasslePlayerScript;
import com.fs.starfarer.api.impl.campaign.NPCHassler;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.impl.campaign.ids.Sounds;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.intel.events.LuddicChurchHostileActivityFactor;
import com.fs.starfarer.api.impl.campaign.intel.group.FGBlockadeAction.FGBlockadeParams;
import com.fs.starfarer.api.impl.campaign.missions.FleetCreatorMission;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithTriggers.FleetQuality;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithTriggers.FleetSize;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.impl.campaign.intel.group.*;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

import lunalib.lunaSettings.LunaSettings;
import exerelin.campaign.SectorManager;
import data.scripts.campaign.armaa_mrcReprisalListener;

import org.lazywizard.lazylib.MathUtils;

public class armaa_mrcExpedition extends BlockadeFGI implements EconomyTickListener {

	public static int STABILITY_PER_MONTH_FULL = 2;
	public static int STABILITY_PER_MONTH_PARTIAL = 1;
	
	public static float NUM_OTHER_FLEETS_MULT = 0.25f;
	
	public static final String STABILITY_UPDATE = "stability_update";
	public static final String TAKEOVER_UPDATE = "takeover_update";
	
	public static final String BLOCKADING = "$armaa_isBlockading";
	
	public static final String ARMAA_FLEET = "$ARMAA_mrc_fleet";
	public static final String ARMADA = "$ARMAA_mrc_armada";
	public static final String PICKET = "$ARMAA_mrc_picket";
	public static boolean spawnedDefenders = false;
	public static boolean defeated = false;
	public static String KEY = "$ARMAA_mrc_ref";
	public static boolean hasLL = Global.getSettings().getModManager().isModEnabled("lunalib");
	public static boolean hasNex = Global.getSettings().getModManager().isModEnabled("nexerelin");
	public boolean reprisalEnabled= false;
	public static armaa_mrcExpedition get() {
		return (armaa_mrcExpedition) Global.getSector().getMemoryWithoutUpdate().get(KEY);
	}
	
	
	public armaa_mrcExpedition(GenericRaidParams params, FGBlockadeParams blockadeParams) {
		super(params, blockadeParams);
		
		Global.getSector().getMemoryWithoutUpdate().set(KEY, this);
		Global.getSector().getListenerManager().addListener(this);
	}
	
	@Override
	protected void notifyEnding() {
		super.notifyEnding();
		float reward =  armaa_mrcReprisalListener.getLoot();
		if(reward >= 0)
		{
			boolean playerParticipated = armaa_mrcReprisalListener.hasPlayerParticipated() || armaa_mrcReprisalListener.getPlayerDamage() > 0 ? true:false;
			if(playerParticipated)
			Global.getSector().getCampaignUI().addMessage("MRC acquired "+String.valueOf(reward)+" c from recent reprisal",Color.white,String.valueOf(reward),"c",Misc.getHighlightColor(),Misc.getHighlightColor());
			if(playerParticipated)
			{
				float ratio =  armaa_mrcReprisalListener.getPlayerDamage()/ armaa_mrcReprisalListener.getAiDamage();
				Global.getSector().getCampaignUI().addMessage("Received "+ reward*ratio,Color.white,String.valueOf(reward),"c",Misc.getHighlightColor(),Misc.getHighlightColor());
				Global.getSector().getPlayerFleet().getCargo().getCredits().add(reward*ratio);
				reward -= reward*ratio;
			}
			int currentLoot = Global.getSector().getMemoryWithoutUpdate().get("$armaa_mrcFunds") != null ? (int)Global.getSector().getMemoryWithoutUpdate().get("$armaa_mrcFunds") :0;
			Global.getSector().getMemoryWithoutUpdate().set("$armaa_mrcFunds",currentLoot+(int)reward);
		}
		Global.getSector().getMemoryWithoutUpdate().unset("$armaa_reprisalData");		
		Global.getSector().getMemoryWithoutUpdate().unset(KEY);
		Global.getSector().getListenerManager().removeListener(this);
	}
	
	@Override
	protected void notifyEnded() {
		super.notifyEnded();
	}
	
	

	@Override
	protected CampaignFleetAPI createFleet(int size, float damage) {
		
		Random r = getRandom();
		
		Vector2f loc = origin.getLocationInHyperspace();
		
		FleetCreatorMission m = new FleetCreatorMission(r);
		
		m.beginFleet();
		
		m.createFleet(params.style, size, "armaarmatura_pirates", loc);
		
		if (size == 10) {
			m.triggerSetFleetDoctrineOther(5, 0);
			m.triggerSetFleetSize(FleetSize.MAXIMUM);
			m.triggerSetFleetSizeFraction(1.4f);
			
			m.triggerSetFleetQuality(FleetQuality.HIGHER);
		}
		
		m.triggerSetFleetFlag(ARMAA_FLEET);
		
		m.setFleetSource(params.source);
		m.setFleetDamageTaken(damage);
	
		m.triggerSetWarFleet();
		m.triggerMakeLowRepImpact();

		//m.triggerMakeHostile();
		m.triggerMakeAlwaysSpreadTOffHostility();
		
		if (size >= 8) {
			m.triggerFleetAddCommanderSkill(Skills.COORDINATED_MANEUVERS, 1);
			m.triggerFleetAddCommanderSkill(Skills.TACTICAL_DRILLS, 1);
			m.triggerFleetAddCommanderSkill(Skills.CARRIER_GROUP, 1);
		} else {
			// only set during the action phase, not here
			//m.triggerSetFleetHasslePlayer(LuddicChurchHostileActivityFactor.HASSLE_REASON);
		}
		
		CampaignFleetAPI fleet = m.createFleet();

		if (fleet != null) 
		{
			fleet.setFaction(params.factionId);
			int numCraft = 0;
			int numCrew = 0;
			if(Global.getSector().getMemoryWithoutUpdate().contains("$armaa_reprisalNumCrew"))
			{
				numCrew = (int)Global.getSector().getMemoryWithoutUpdate().get("$armaa_reprisalNumCrew");
			}
			for(FleetMemberAPI member : fleet.getMembersWithFightersCopy())
			{
				numCrew+= MathUtils.getRandomNumberInRange(member.getMinCrew(),member.getMaxCrew());
			}
			if (size >= 5) {
				setNeverStraggler(fleet);
			} else {
				fleet.addScript(new NPCHassler(fleet, getTargetSystem()));
				fleet.getMemoryWithoutUpdate().set(PICKET, true);
				
				fleet.setName("Reprisal Raiders");
				fleet.setNoFactionInName(true);
			}
			
			if (size >= 5) {
				fleet.setName("Reprisal Armada");
				fleet.setNoFactionInName(true);
				fleet.getMemoryWithoutUpdate().set(ARMADA, true);
				fleet.getCommander().setRankId(Ranks.SPACE_ADMIRAL);
			}
			Global.getSector().getMemoryWithoutUpdate().set("$armaa_reprisalNumCrew",numCrew);
		}
		
		
		return fleet;
	}
	
	public void autoresolve() {
		if (isEnded() || isEnding() || isAborted() || isCurrent(RETURN_ACTION)) return;
		MarketAPI objective = blockadeParams.specificMarket;		
		float str = WarSimScript.getFactionStrength(Global.getSector().getFaction("armaarmatura_pirates"), objective.getStarSystem());
		if (!isSpawnedFleets()) 
		{
			OptionalFleetData data = route.getExtra();	
			if (data != null) str += data.getStrengthModifiedByDamage();
		}

		boolean playerTargeted = isPlayerTargeted();
				
		float enemyStr = WarSimScript.getEnemyStrength(objective.getFaction(), objective.getStarSystem(), playerTargeted);
		enemyStr +=  WarSimScript.getStationStrength(objective.getFaction(), objective.getStarSystem(), objective.getPrimaryEntity());
		float origStr = str;
		float strMult = 1f;

		for(CampaignFleetAPI enemyFleet: objective.getStarSystem().getFleets())
		{
			//enemyFleetStr
			if(enemyFleet.getFaction().isHostileTo("armaarmatura_pirates"))
			{
				
			}
		}

		for (MarketAPI target : Misc.getMarketsInLocation(objective.getContainingLocation())) {
			if (!target.getFaction().isHostileTo("armaarmatura_pirates")) continue;
			
			float defensiveStr = enemyStr + WarSimScript.getStationStrength(target.getFaction(), target.getStarSystem(), target.getPrimaryEntity());
			float damage = 0.5f * defensiveStr / Math.max(str, 1f);
			if (damage > 0.75f) damage = 0.75f;
			strMult *= (1f - damage);
			
			if (defensiveStr >= str) {
				continue;
			}

			Industry station = Misc.getStationIndustry(target);
			if (station != null && !station.getId().equals("ass_arkoship_base") && (!station.getId().equals("ass_arkoship_base2"))) {
				OrbitalStation.disrupt(station);
				station.reapply();				
			}

			// just in case I change my mind
			//for (int i = 0; i < params.raidsPerColony; i++) {
			//	performRaid(null, target);
			//}
			
			str -= defensiveStr * 0.5f;
			str = origStr * strMult;
			//System.out.println(target);
			if(target == objective)
			{
				if(getCurrentAction() instanceof FGBlockadePlanetAction && target.getStabilityValue() <= 0)
				{
					FGBlockadePlanetAction action = (FGBlockadePlanetAction) getCurrentAction();
					action.setSuccessFractionOverride(1f);
					action.setActionFinished(true);
					notifyActionFinished(action); 
					if(hasNex)
					{
						if(hasLL)
						{
							reprisalEnabled = LunaSettings.getBoolean("armaa", "armaa_enableReprisal");
						}
						if(reprisalEnabled)
							performTakeover(false);
					}						
					float reward =  armaa_mrcReprisalListener.getLoot();
					int currFunds = Global.getSector().getMemoryWithoutUpdate().get("$armaa_mrcFunds") != null ? (int)Global.getSector().getMemoryWithoutUpdate().get("$armaa_mrcFunds") : 0;
					Global.getSector().getMemoryWithoutUpdate().set("$armaa_mrcFunds",currFunds+(int)(Math.random()*200000)+(int)reward);						
					finish(false);
					notifyEnded();
					return;
				}
				else
				{
					int penalty = getStabilityPenaltyPerMonth();
					if (penalty > 0) {
						RecentUnrest.get(target).add(penalty, "MRC Reprisal operation");
						target.reapplyConditions();
						sendUpdateIfPlayerHasIntel(STABILITY_UPDATE, false);
					}
				}
				
			}
		}
		
		if (isSpawnedFleets() && strMult < 1f) {
			for (CampaignFleetAPI fleet : getFleets()) {
				Random random = new Random();
				FleetFactoryV3.applyDamageToFleet(fleet, 1f - strMult, false, random);
			}
		}
		OptionalFleetData extra = route.getExtra();		
		if (!isSpawnedFleets() && strMult < 1) {
			if (extra != null) {
				if (extra.damage == null) {
					extra.damage = 0f;
				}
				extra.damage = 1f - (1f - extra.damage) * strMult;
				//extra.damage = 1f;
				//System.out.println(extra.damage);
				if (extra.damage > 1f) extra.damage = 1f;
			}
		} else if (isSpawnedFleets()) {
			//abort();
		}
		if(extra != null && extra.damage != null && extra.damage > 0.80f) {
			float reward =  armaa_mrcReprisalListener.getLoot() ;
			//setFailedButNotDefeated(true);
			int currFunds = Global.getSector().getMemoryWithoutUpdate().get("$armaa_mrcFunds") != null ? (int)Global.getSector().getMemoryWithoutUpdate().get("$armaa_mrcFunds") : 0;
			Global.getSector().getMemoryWithoutUpdate().set("$armaa_mrcFunds",currFunds+(int)(Math.random()*100000)+(int)reward);			
			finish(true);
			// if fleets were not spawned and it needs to abort due to the damage taken,
			// that's handled in FleetGroupIntel
		}
		
	}	

	@Override
	public void advance(float amount) 
	{
		super.advance(amount);	
		//System.out.println(getCurrentAction());		
		if(hasLL)
		{
			if(reprisalEnabled != LunaSettings.getBoolean("armaa", "armaa_enableReprisal"))
				reprisalEnabled= LunaSettings.getBoolean("armaa", "armaa_enableReprisal");
		}
		if (isSpawnedFleets())
		{
			if (isEnded() || isEnding() || isAborted() || isCurrent(RETURN_ACTION)) {
				for (CampaignFleetAPI curr : getFleets()) {
					curr.getMemoryWithoutUpdate().set(BLOCKADING, false);
				}
				return;
			}			
			if (isCurrent(PAYLOAD_ACTION)) 
			{
				for (CampaignFleetAPI curr : getFleets()) {
					curr.getMemoryWithoutUpdate().set(BLOCKADING, true);
				}
				MarketAPI target = blockadeParams.specificMarket;
				if(target.getFaction().getRelationship("armaarmatura_pirates") > -0.50f)
				{
					abort();
				}
			}
							
		}
	}

	@Override
	protected void periodicUpdate() {
		super.periodicUpdate();
		
		if ((isEnded() || isEnding() || isSucceeded() || isFailed() || isAborted())) {
			return;
		}
			
		//if (HostileActivityEventIntel.get() == null) { // possible if the player has no colonies 
		//	abort();
		//	return;
		//}
		
		MarketAPI target = blockadeParams.specificMarket;
		//if (target != null && !target.hasCondition(Conditions.LUDDIC_MAJORITY)) {
		//	//setFailedButNotDefeated(true);
		//	finish(false);
		//	return;
		//}
		if(target.getFaction().getRelationship("armaarmatura_pirates") > -0.50f)
			abortNonHostile(); 		
		FGAction action = getCurrentAction();
		//if (action instanceof FGBlockadeAction) {
		//	MutableStatWithTempMods stat = HostileActivityEventIntel.get().getNumFleetsStat(getTargetSystem());
			//stat.addTemporaryModMult(1f, "KOLBlockade", null, NUM_OTHER_FLEETS_MULT);
		//}

		if (!isSpawnedFleets() || isSpawning()) return;
		
		int armada = 0;
		for (CampaignFleetAPI curr : getFleets()) {
			if (curr.getMemoryWithoutUpdate().getBoolean(ARMADA)) {
				armada++;
			}
		}
		
		if (armada <= 0) {
			abort();
		}
		
		if (action instanceof FGBlockadePlanetAction) {
			FGBlockadePlanetAction blockade = (FGBlockadePlanetAction) action;
			if (blockade.getPrimary() != null) {
				for (CampaignFleetAPI curr : getFleets()) {
					if (blockade.getPrimary().getContainingLocation() != curr.getContainingLocation()) {
						continue;
					}
					if (!curr.getMemoryWithoutUpdate().getBoolean(PICKET)) {
						curr.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED, true, 0.4f);
					}
				}
			}
		}
	}

	protected String getOfString() {
		return "targeting";
	}
	
	protected GenericPayloadAction createPayloadAction() {
		FGBlockadePlanetAction action = new FGBlockadePlanetAction(blockadeParams, params.payloadDays);
		action.setSuccessFractionOverride(0f);
		return action;
	}
	
	protected void applyBlockadeCondition() {
//		int str = getRelativeFGStrength(getTargetSystem());
//		if (str < 0) {
//			unapplyBlockadeCondition();
//			return;
//		}
//		
//		for (MarketAPI market : Misc.getMarketsInLocation(getTargetSystem(), blockadeParams.targetFaction)) {
//			if (!market.hasCondition(Conditions.BLOCKADED)) {
//				market.addCondition(Conditions.BLOCKADED, this);
//			}
//		}
	}
	
	protected void unapplyBlockadeCondition() {
//		for (MarketAPI market : Misc.getMarketsInLocation(getTargetSystem(), blockadeParams.targetFaction)) {
//			market.removeCondition(Conditions.BLOCKADED);
//		}
	}
	
	@Override
	protected void addUpdateBulletPoints(TooltipMakerAPI info, Color tc, Object param, ListInfoMode mode,
										float initPad) {
		
		Object p = getListInfoParam();
	
		if (STABILITY_UPDATE.equals(p)) {
			int penalty = getStabilityPenaltyPerMonth();
			MarketAPI target = blockadeParams.specificMarket;
			LabelAPI label = info.addPara(target.getName() + " stability reduced by %s", initPad, tc,
					Misc.getHighlightColor(), "" + penalty);
			label.setHighlightColors(target.getFaction().getBaseUIColor(), Misc.getHighlightColor());
			label.setHighlight(target.getName(), "" + penalty);
		} else if (TAKEOVER_UPDATE.equals(p)) {
			
		}

		else {
			super.addUpdateBulletPoints(info, tc, param, mode, initPad);
		}
	}

	protected void addTargetingBulletPoint(TooltipMakerAPI info, Color tc, Object param, ListInfoMode mode, float initPad) {
		MarketAPI target = blockadeParams.specificMarket;
//		StarSystemAPI system = raidAction.getWhere();
//		Color s = raidAction.getSystemNameHighlightColor();
		LabelAPI label = info.addPara("Targeting " + target.getName(), tc, initPad);
		label.setHighlightColors(target.getFaction().getBaseUIColor());
		label.setHighlight(target.getName());
	}
	
	@Override
	protected void addBasicDescription(TooltipMakerAPI info, float width, float height, float opad) {
		info.addImage(getFaction().getLogo(), width, 128, opad);
		
		MarketAPI target = blockadeParams.specificMarket;
		
		StarSystemAPI system = raidAction.getWhere();
		
		String noun = getNoun();
		
		LabelAPI label = info.addPara(
				Misc.ucFirst(faction.getPersonNamePrefixAOrAn()) + " %s " + noun + " " + getOfString() + " "
				+ target.getName() + " in the " + system.getNameWithLowercaseType() + ".", opad,
				faction.getBaseUIColor(), faction.getPersonNamePrefix());
		label.setHighlightColors(faction.getBaseUIColor(), target.getFaction().getBaseUIColor());
		label.setHighlight(faction.getPersonNamePrefix(), target.getName());
	}
	
	protected void addAssessmentSection(TooltipMakerAPI info, float width, float height, float opad) {
		Color h = Misc.getHighlightColor();
		Color bad = Misc.getNegativeHighlightColor();
		
		FactionAPI faction = getFaction();
		MarketAPI target = blockadeParams.specificMarket;
		
		String noun = getNoun();
		String forcesNoun = getForcesNoun();
		if (!isEnding() && !isSucceeded() && !isFailed()) {
			
			FactionAPI other = Global.getSector().getFaction(blockadeParams.targetFaction);
			boolean hostile = getFaction().isHostileTo(blockadeParams.targetFaction);
			
			info.addSectionHeading("Assessment", 
							faction.getBaseUIColor(), faction.getDarkUIColor(), Alignment.MID, opad);
			
			boolean started = isCurrent(PAYLOAD_ACTION);
			float remaining = getETAUntil(PAYLOAD_ACTION, true) - getETAUntil(TRAVEL_ACTION, true);
			if (remaining > 0 && remaining < 1) remaining = 1;
			String days = (int)remaining == 1 ? "day" : "days";
			
			if (started) days = "more " + days;
			
			LabelAPI label = info.addPara("The operation will last for approximately %s " + days
					+ ", causing a progressive loss of stability " + target.getOnOrAt() + 
					" %s. If stability reaches zero, %s will permanently fall under %s control." ,
					opad, h,
					"" + (int) remaining,
					target.getName(), target.getName(),
					faction.getDisplayName());
			label.setHighlight("" + (int) remaining, 
								target.getName(), target.getName(),
								"will permanently fall",
								faction.getDisplayName());
			label.setHighlightColors(h, other.getBaseUIColor(), Misc.getTextColor(), 
					Misc.getNegativeHighlightColor(), faction.getBaseUIColor());
			//hostile = true;
			int numCrew = 0;
			if(Global.getSector().getMemoryWithoutUpdate().contains("$armaa_reprisalNumCrew"))
				numCrew = (int)Global.getSector().getMemoryWithoutUpdate().get("$armaa_reprisalNumCrew");

			if (!hostile) {
				info.addPara("The " + forcesNoun + ", " + numCrew + " strong, will "
						+ "attempt to maintain control of the volume around the planet. ", 
						opad, 
						Misc.getHighlightColor(), "not nominally hostile",""+numCrew);
			} else {
				info.addPara("The " + forcesNoun + "are actively hostile, and will engage any orbital defenses, and "
						+ " pressure the local government to cut ties to " +target.getFaction().getDisplayName(), 
						opad, Misc.getHighlightColor(),"actively hostile");
			}
			
			addStrengthDesc(info, opad, target, forcesNoun, 
					"the colony is unlikely to be in danger",
					"the colony may be in danger",
					"the colony is in danger");
			
			addPostAssessmentSection(info, width, height, opad);
		}
	}
	
	@Override
	protected void addPostAssessmentSection(TooltipMakerAPI info, float width, float height, float opad) {
	}

	protected void addPayloadActionStatus(TooltipMakerAPI info, float width, float height, float opad) {
		StarSystemAPI to = raidAction.getWhere();
		info.addPara("Conducting operations in the " +
				to.getNameWithLowercaseTypeShort() + ".", opad);
		
		int penalty = getStabilityPenaltyPerMonth();
		MarketAPI target = blockadeParams.specificMarket;
		bullet(info);
		if (penalty > 0) {
			LabelAPI label = info.addPara(target.getName() + " stability: %s per month", opad,
					Misc.getHighlightColor(), "-" + penalty);
			label.setHighlightColors(target.getFaction().getBaseUIColor(), Misc.getHighlightColor());
			label.setHighlight(target.getName(), "-" + penalty);
		} else {
			info.addPara("%s stability unaffected", opad,
					target.getFaction().getBaseUIColor(), target.getName());

			unindent(info);
		}
		
	}

	public int getStabilityPenaltyPerMonth() {
		int str = getRelativeFGStrength(getTargetSystem());
		if (str < 0) {
			return 0;
		} else if (str == 0) {
			return STABILITY_PER_MONTH_PARTIAL;
		} else {
			return STABILITY_PER_MONTH_FULL;
		}
	}
	
	public void reportEconomyTick(int iterIndex) {
		// do here so that it's not synched up with the month-end income report etc
		if (iterIndex == 0) {
			//sendUpdateIfPlayerHasIntel("armaa_WFCompletedAtmoBattle", false);
			boolean inSpawnRange = RouteManager.isPlayerInSpawnRange(blockadeParams.where.getCenter());
			boolean arrived = route.getCurrent().from.getContainingLocation().equals(blockadeParams.specificMarket.getContainingLocation());
			if (!inSpawnRange && arrived) 
			{
				autoresolve();
				return;
			}					
			if (!isCurrent(PAYLOAD_ACTION)) return;
			MarketAPI target = blockadeParams.specificMarket;
			
			int penalty = getStabilityPenaltyPerMonth();
			if (penalty > 0) {
				RecentUnrest.get(target).add(penalty, "MRC Reprisal operation");
				target.reapplyConditions();
				sendUpdateIfPlayerHasIntel(STABILITY_UPDATE, false);
			}

			if(target.getStabilityValue() <= 0 && hasNex)
			{
				if(hasLL)
				{
					reprisalEnabled = LunaSettings.getBoolean("armaa", "armaa_enableReprisal");
				}
				if(reprisalEnabled)
					performTakeover(false);
			}			
		}
	}

	public void reportEconomyMonthEnd() {
		
	}
	
	protected boolean voluntary;
	public void performTakeover(boolean voluntary) 
	{
		// Need to unset target, give player reward
		// Add HQ
		// 
		this.voluntary = voluntary;
		MarketAPI target = blockadeParams.specificMarket;
		String factionId = target.hasCondition(Conditions.LUDDIC_MAJORITY) ? "luddic_church":"pirates";
		target.setFactionId(factionId);
		SectorManager.transferMarket(target,Global.getSector().getFaction(factionId),Global.getSector().getFaction(factionId),false,true,null,0);
		for (SectorEntityToken curr : target.getConnectedEntities()) {
			curr.setFaction(factionId);
		}

		//target.setPlanetConditionMarketOnly(false);
		
		if(factionId.equals("pirates"))
		{ 			
			target.addIndustry("armaa_mrc");
		}
		if (Misc.isMilitary(target) || 
				target.hasIndustry(Industries.MILITARYBASE) || 
				target.hasIndustry(Industries.HIGHCOMMAND)) {
			if (target.hasSubmarket(Submarkets.GENERIC_MILITARY)) {
				target.removeSubmarket(Submarkets.GENERIC_MILITARY);
				target.addSubmarket(Submarkets.GENERIC_MILITARY);
			}
		}
		//target.addCondition(Conditions.DISSIDENT);
		RecentUnrest.get(target).setPenalty(0);
		//target.getMemoryWithoutUpdate().unset("$playerHostileTimeout");
		//target.getMemoryWithoutUpdate().unset("$playerHostileTimeoutStr");		
		
		if (getCurrentAction() instanceof FGBlockadePlanetAction) {
			FGBlockadePlanetAction action = (FGBlockadePlanetAction) getCurrentAction();
			action.setSuccessFractionOverride(1f);
			action.setActionFinished(true);
		}
		
		if (target.getStarSystem() != null) {
			for (CampaignFleetAPI fleet : target.getStarSystem().getFleets()) {
				MemoryAPI mem = fleet.getMemoryWithoutUpdate();
				String type = mem.getString(MemFlags.HASSLE_TYPE);
				if (LuddicChurchHostileActivityFactor.HASSLE_REASON.equals(type)) {
					mem.unset(MemFlags.WILL_HASSLE_PLAYER);
					mem.unset(MemFlags.HASSLE_TYPE);
					mem.set(HasslePlayerScript.HASSLE_COMPLETE_KEY, true);
					fleet.removeScriptsOfClass(NPCHassler.class);
				}
			}
		}	
	}

	public void abortNonHostile() 
	{
		MarketAPI target = blockadeParams.specificMarket;		
		if (getCurrentAction() instanceof FGBlockadePlanetAction) {
			FGBlockadePlanetAction action = (FGBlockadePlanetAction) getCurrentAction();
			action.setSuccessFractionOverride(1f);
			action.setActionFinished(true);
		}
		
		if (target.getStarSystem() != null) {
			for (CampaignFleetAPI fleet : target.getStarSystem().getFleets()) {
				MemoryAPI mem = fleet.getMemoryWithoutUpdate();
				String type = mem.getString(MemFlags.HASSLE_TYPE);
				if (LuddicChurchHostileActivityFactor.HASSLE_REASON.equals(type)) {
					mem.unset(MemFlags.WILL_HASSLE_PLAYER);
					mem.unset(MemFlags.HASSLE_TYPE);
					mem.set(HasslePlayerScript.HASSLE_COMPLETE_KEY, true);
					fleet.removeScriptsOfClass(NPCHassler.class);
				}
			}
		}	
	}	

	public String getCommMessageSound() {
		if (isSendingUpdate() && isSucceeded() && 
				isCurrent(RETURN_ACTION) && !voluntary) {
			return Sounds.STORY_POINT_SPEND_INDUSTRY;
		}
		return super.getCommMessageSound();
	}	
}