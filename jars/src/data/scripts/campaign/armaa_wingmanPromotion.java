package data.scripts.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.TempData;
import com.fs.starfarer.api.util.Misc;
import data.hullmods.cataphract;
import data.scripts.campaign.intel.armaa_promoteWingman;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin.DataForEncounterSide.OfficerEngagementData;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin.DataForEncounterSide;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin.FleetMemberData;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent.*;
import java.util.*;
import java.awt.Color;
import com.fs.starfarer.api.characters.*;

import com.fs.starfarer.api.characters.MutableCharacterStatsAPI.*;
import com.fs.starfarer.api.campaign.CombatDamageData.DamageToFleetMember;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.SettingsAPI;
//import com.fs.starfarer.api.impl.campaign.*;

import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.MissionCompletionRep;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.*;
import com.fs.starfarer.api.campaign.ReputationActionResponsePlugin.ReputationAdjustmentResult;
import com.fs.starfarer.api.campaign.RepLevel;
import org.lazywizard.lazylib.MathUtils;

public class armaa_wingmanPromotion extends BaseCampaignEventListener implements EveryFrameScript 
{
	public armaa_wingmanPromotion() { super(true); }
	
	static long previousXP = Long.MAX_VALUE;
	private Float repRewardPerson = null;
	private Float repPenaltyPerson = null;
	private Float repRewardFaction = null;
	private int numEngagements = 0;
	Float repPenaltyFaction = null;	
	RepLevel rewardLimitPerson = null; 
	RepLevel rewardLimitFaction = null; 
	RepLevel penaltyLimitPerson = null; 
	RepLevel penaltyLimitFaction = null;
	transient int memberToPromote;
	transient boolean isWin = false;
	transient boolean canPromote = false;
	transient Map<FleetMemberAPI,List<Integer>> promoteablePilots = new HashMap<>();	
	transient EngagementResultAPI batResult;

    public static long getReAdjustedXp() {
        long xpEarned = Global.getSector().getPlayerStats().getXP()+Global.getSector().getPlayerStats().getBonusXp() - previousXP;

        return xpEarned;
    }
	
	public TextPanelAPI textPanelForXPGain = null;
	
	public TextPanelAPI getTextPanelForXPGain() {
		return textPanelForXPGain;
	}

	public void setTextPanelForXPGain(TextPanelAPI textPanelForXPGain) {
		this.textPanelForXPGain = textPanelForXPGain;
	}
	
	private Random salvageRandom = null;
	public Random getSalvageRandom() {
		return salvageRandom;
	}
	public void setSalvageRandom(Random salvageRandom) {
		this.salvageRandom = salvageRandom;
	}
	
    @Override
    public boolean runWhilePaused() 
	{
        return false;
    }
	@Override
    public boolean isDone() { return false; }
	
	@Override
    public void advance(float amount) 
	{
		long xp = Global.getSector().getPlayerStats().getXP();

		if(previousXP == Long.MAX_VALUE) {
			previousXP = xp;
		} else if(previousXP < xp) {
			// Don't change the order of these two lines
			long dXp = Math.min(10000, Global.getSector().getPlayerStats().getXP() - previousXP);
			previousXP = xp;
		}
	}
	
	public DataForEncounterSide getDataFor(CampaignFleetAPI participantOrCombined, BattleAPI battle,List <DataForEncounterSide> sideData) {
		CampaignFleetAPI combined = battle.getCombinedFor(participantOrCombined);
		if (combined == null) {
			return new DataForEncounterSide(participantOrCombined);
		}
		
		for (DataForEncounterSide curr : sideData) {
			if (curr.getFleet() == combined) return curr;
		}
		DataForEncounterSide dfes = new DataForEncounterSide(combined);
		sideData.add(dfes);
		
		return dfes;
	}
	
	protected void clearNoSourceMembers(EngagementResultForFleetAPI result, BattleAPI battle) 
	{
		Iterator<FleetMemberAPI> iter = result.getDeployed().iterator();
		while (iter.hasNext()) {
			FleetMemberAPI member = iter.next();
			if (battle.getSourceFleet(member) == null) {
				iter.remove();
			}
		}
		iter = result.getReserves().iterator();
		while (iter.hasNext()) {
			FleetMemberAPI member = iter.next();
			if (battle.getSourceFleet(member) == null) {
				iter.remove();
			}
		}
		iter = result.getDestroyed().iterator();
		while (iter.hasNext()) {
			FleetMemberAPI member = iter.next();
			if (battle.getSourceFleet(member) == null) {
				iter.remove();
			}
		}
		iter = result.getDisabled().iterator();
		while (iter.hasNext()) {
			FleetMemberAPI member = iter.next();
			if (battle.getSourceFleet(member) == null) {
				iter.remove();
			}
		}
		iter = result.getRetreated().iterator();
		while (iter.hasNext()) {
			FleetMemberAPI member = iter.next();
			if (battle.getSourceFleet(member) == null) {
				iter.remove();
			}
		}
	}
	
    @Override
    public void reportPlayerEngagement(EngagementResultAPI result) 
	{
		batResult = result;
		numEngagements++;
	}		

	@Override
    public void reportBattleOccurred(CampaignFleetAPI primaryWinner, BattleAPI battle) 
	{

		if(!battle.isPlayerInvolved() || batResult == null)
			return;

		isWin = battle == null || primaryWinner == null || battle.getPlayerSide() == null ? null
			: battle.getPlayerSide().contains(primaryWinner);
		
		List<DataForEncounterSide> sideData = new ArrayList<DataForEncounterSide>();
		long xpEarned = getReAdjustedXp();
		FleetMemberAPI potentialSquadron = null;
		
		if (xpEarned <= 0) return;
		EngagementResultForFleetAPI winnerResult =batResult.getWinnerResult();
		EngagementResultForFleetAPI loserResult = batResult.getLoserResult();
		clearNoSourceMembers(winnerResult,battle);
		clearNoSourceMembers(loserResult,battle);
		
		DataForEncounterSide winnerData = getDataFor(winnerResult.getFleet(),battle,sideData);
		DataForEncounterSide loserData = getDataFor(loserResult.getFleet(),battle,sideData);		
		DataForEncounterSide sideOne = winnerData;
		DataForEncounterSide sideTwo = loserData;
		
		DataForEncounterSide player = sideOne;
		DataForEncounterSide enemy = sideTwo;
		if (battle.isPlayerSide(battle.getSideFor(sideTwo.getFleet()))) {
			player = sideTwo;
			enemy = sideOne;
		}
		EngagementResultForFleetAPI enemyFleet = loserResult.isPlayer() ? winnerResult : loserResult;
		EngagementResultForFleetAPI playerFleet = loserResult.isPlayer() ? loserResult : winnerResult;
		List<FleetMemberAPI> enemyCasualties = new ArrayList<FleetMemberAPI>();
		List<FleetMemberAPI> allyCasualties = new ArrayList<FleetMemberAPI>();		
		
		//we only want to add the casualties whom have wingcom array
		enemyCasualties.addAll(enemyFleet.getDestroyed());
		enemyCasualties.addAll(enemyFleet.getDisabled());
		allyCasualties.addAll(playerFleet.getDestroyed());
		allyCasualties.addAll(playerFleet.getDisabled());
		allyCasualties.addAll(playerFleet.getRetreated());
		
		float fpDestroyed = 0f;
		for (FleetMemberAPI data : enemyCasualties) {
			float fp = data.getFleetPointCost();
			fp *= 1f + data.getCaptain().getStats().getLevel() / 5f;
			fpDestroyed += fp;
		}
		fpDestroyed *= 1f;
		for (FleetMemberAPI data : allyCasualties) 
		{
			if (data.isAlly()) continue;
			float fp = data.getFleetPointCost();
			fp *= 1f + data.getCaptain().getStats().getLevel() / 5f;
			fpDestroyed += fp;
		}
		
		float fpInFleet = 0f;
		
		for (FleetMemberAPI member : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) 
		{
			float fp = member.getFleetPointCost();
			fp *= 1f + member.getCaptain().getStats().getLevel() / 5f;

			if(Global.getSector().getPersistentData().get("armaa_wingCommander_wingman_"+0+"_"+member.getCaptain().getId()) instanceof PersonAPI)
			{
				
				if(member.getVariant().hasHullMod("armaa_wingCommander") && (playerFleet.getDeployed().contains(member) || allyCasualties.contains(member)))
				{
					float damageDealt = 0f;
					if(batResult.getLastCombatDamageData() != null)
						if(batResult.getLastCombatDamageData().getDealtBy(member).getDamage() != null)
						{
							for (CombatDamageData.DamageToFleetMember f : batResult.getLastCombatDamageData().getDealtBy(member).getDamage().values())
							{
								damageDealt += f.hullDamage;
							}
							damageDealt = damageDealt/member.getMemberStrength()/5f;
							//Global.getSector().getCampaignUI().addMessage(String.valueOf(damageDealt),Color.white); 
						}
					adjustRelations(member,allyCasualties,damageDealt);
				}
			}
			fpInFleet += fp;
		}
		
		float maxProb = Global.getSettings().getFloat("maxOfficerPromoteProb");
		int max = Misc.getMaxOfficers(Global.getSector().getPlayerFleet());
		int curr = Misc.getNumNonMercOfficers(Global.getSector().getPlayerFleet());

		ArrayList<String> alreadyConsidered = new ArrayList<>();
		if(curr < max)
		{
			if(Global.getSector().getIntelManager().getIntel(armaa_promoteWingman.class) != null)
			{
				int size = Global.getSector().getIntelManager().getIntel(armaa_promoteWingman.class).size();
				for(int i = 0; i < size; i++)
				{
					armaa_promoteWingman pastIntel = (armaa_promoteWingman)Global.getSector().getIntelManager().getIntel(armaa_promoteWingman.class).get(i);
					alreadyConsidered.add(pastIntel.getCandidate().getId());
				}
			}

			for (Map.Entry mapElement : promoteablePilots.entrySet()) 
			{
				FleetMemberAPI key = (FleetMemberAPI)mapElement.getKey();
				if (key != null) 
				{
					for(Integer pilot : (List<Integer>)mapElement.getValue())
					{
						boolean alreadyEligible = false;
						PersonAPI candidate = null;
						if(Global.getSector().getIntelManager().getIntel(armaa_promoteWingman.class) != null)
						{
							if(Global.getSector().getPersistentData().get("armaa_wingCommander_wingman_"+pilot+"_"+key.getCaptain().getId()) instanceof PersonAPI)
								candidate = (PersonAPI)Global.getSector().getPersistentData().get("armaa_wingCommander_wingman_"+pilot+"_"+key.getCaptain().getId());
							if(alreadyConsidered.contains(candidate.getId()))
								alreadyEligible = true;
						}
						if(candidate != null && candidate.hasTag("armaa_doNotAutoConsiderAgain"))
							continue;
						
						if(!alreadyEligible)
						{
							armaa_promoteWingman intel = new armaa_promoteWingman(textPanelForXPGain,key,pilot);
							Global.getSector().getIntelManager().addIntel(intel, false, textPanelForXPGain);
						}
					}
				}
			}
		}		
		promoteablePilots.clear();
		batResult = null;		
		previousXP = Global.getSector().getPlayerStats().getXP();
		enemyCasualties.clear();
		allyCasualties.clear();
		numEngagements = 0;
	}
	
	private void adjustRelations(FleetMemberAPI member, List<FleetMemberAPI> members, float damageDealt)
	{
		List<RepActions> bonus = new ArrayList<RepActions>();
		bonus.add(RepActions.COMBAT_HELP_MINOR);
		bonus.add(RepActions.COMBAT_HELP_MAJOR);
		bonus.add(RepActions.MISSION_SUCCESS);
		//CustomRepImpact impact = new CustomRepImpact();
		//impact.delta = damageDealt;
		Random rand = new Random();
		
		if(members != null)
		{
			if(members.contains(member))
			{
				bonus.clear();
				bonus.add(RepActions.COMBAT_NORMAL_TOFF);
				bonus.add(RepActions.COMBAT_AGGRESSIVE_TOFF);
				bonus.add(RepActions.MISSION_FAILURE);
				if(isWin)
					bonus.add(RepActions.MISSION_SUCCESS);
			}
			
		}
		int count = 0;
		if(Global.getSector().getPersistentData().get("armaa_wingCommander_squadSize_"+member.getCaptain().getId()) instanceof Integer)
		{
			count = (Integer)Global.getSector().getPersistentData().get("armaa_wingCommander_squadSize_"+member.getCaptain().getId());
		}
		
		ArrayList<Integer> pilots = new ArrayList<>();
		for(int j = 0; j < count; j++)
		{
			repRewardPerson = null;
			repPenaltyPerson = null;
			repRewardFaction = null;
			repPenaltyFaction = null;	
			rewardLimitPerson = null; 
			rewardLimitFaction = null; 
			penaltyLimitPerson = null; 
			penaltyLimitFaction = null;
			
			if(Global.getSector().getPersistentData().get("armaa_wingCommander_wingman_"+j+"_"+member.getCaptain().getId()) instanceof PersonAPI)
			{
				TextPanelAPI textPanel = null;
				boolean withMessage = j == 0 ? true:false;
				PersonAPI pilot = (PersonAPI)Global.getSector().getPersistentData().get("armaa_wingCommander_wingman_"+j+"_"+member.getCaptain().getId());
				String callsign = (String)Global.getSector().getPersistentData().get("armaa_wingCommander_wingman_"+j+"_"+"callsign_"+member.getCaptain().getId());				
				MissionCompletionRep completionRepPerson = new MissionCompletionRep(getRepRewardSuccessPerson(), getRewardLimitPerson(),
					-getRepPenaltyFailurePerson(), getPenaltyLimitPerson());

				ReputationAdjustmentResult rep = Global.getSector().adjustPlayerReputation(
					new RepActionEnvelope(bonus.get(rand.nextInt(bonus.size())), completionRepPerson,textPanel, true, false),pilot);

				levelUpIfApplicable(pilot,j,callsign,member);
				
				if(pilot.getRelToPlayer().getRel() >= .70f)
				{
					canPromote = true;
					memberToPromote = j;
					pilots.add(j);
				}
			}
		}
		promoteablePilots.put(member,pilots);
	}
	public float getRepRewardSuccessPerson() {
		if (repRewardPerson != null) return repRewardPerson;
		return MathUtils.getRandomNumberInRange(RepRewards.SMALL,RepRewards.VERY_HIGH)*numEngagements;
	}

	public float getRepPenaltyFailurePerson() {
		if (repPenaltyPerson != null) return repPenaltyPerson;
		return MathUtils.getRandomNumberInRange(RepRewards.SMALL,RepRewards.VERY_HIGH);
	}
		public RepLevel getRewardLimitPerson() {
		return rewardLimitPerson != null ? rewardLimitPerson : RepLevel.COOPERATIVE;
	}
		public RepLevel getPenaltyLimitPerson() {
		return penaltyLimitPerson != null ? penaltyLimitPerson : RepLevel.VENGEFUL;
	}
	
	public void levelUpIfApplicable(PersonAPI pilot, int j, String callsign, FleetMemberAPI member)
	{
		MutableCharacterStatsAPI playerStats = Global.getSector().getPlayerFleet().getFleetData().getCommander().getStats();					
		int maxLevel = (int)playerStats.getDynamic().getMod(Stats.OFFICER_MAX_LEVEL_MOD).computeEffective(0)+(int)Misc.MAX_OFFICER_LEVEL;
		int maxElite = (int)Global.getSettings().getInt("officerMaxEliteSkills")+(int)playerStats.getDynamic().getMod(Stats.OFFICER_MAX_ELITE_SKILLS_MOD).computeEffective(0);
		float levelUpChance = pilot.getStats().getLevel() != maxLevel ? (maxLevel - pilot.getStats().getLevel())*.1f : .4f;
		levelUpChance*=numEngagements;
		float overLevelChance = pilot.hasTag("armaa_latentTalent") ? .05f :.01f;
		if(Math.random() <= levelUpChance && pilot.getRelToPlayer().getRel() >= .25f)
		{
			List<String> validSkills = new ArrayList<String>();
			validSkills.add(Skills.COMBAT_ENDURANCE);
			validSkills.add(Skills.HELMSMANSHIP);
			validSkills.add(Skills.ENERGY_WEAPON_MASTERY);
			validSkills.add(Skills.BALLISTIC_MASTERY);
			validSkills.add(Skills.FIELD_MODULATION);
			validSkills.add(Skills.TARGET_ANALYSIS);
			validSkills.add(Skills.IMPACT_MITIGATION);
			validSkills.add(Skills.DAMAGE_CONTROL);
			validSkills.add(Skills.POLARIZED_ARMOR);
			validSkills.add(Skills.POINT_DEFENSE);
			validSkills.add(Skills.MISSILE_SPECIALIZATION);
			validSkills.add(Skills.SYSTEMS_EXPERTISE);
			int maxOverLevel = 7;
			String newSkill = OfficerManagerEvent.pickSkill(pilot,validSkills,OfficerManagerEvent.SkillPickPreference.YES_ENERGY_YES_BALLISTIC_YES_MISSILE_YES_DEFENSE,0, new Random());
			MutableCharacterStatsAPI stats = pilot.getStats();
			String skillName = Global.getSettings().getSkillSpec(newSkill).getName();
			List<SkillLevelAPI> skills = new ArrayList(stats.getSkillsCopy());
			boolean skilledUp = false;
			if(Misc.getNumEliteSkills(pilot) < maxElite)
				for(SkillLevelAPI skill : stats.getSkillsCopy())
				{
					if(!skill.getSkill().getName().equals(skillName) && pilot.getStats().getLevel() < maxLevel)
						continue;
					if(!validSkills.contains(skill.getSkill().getId()))
						continue;
					//Global.getSector().getCampaignUI().addMessage(skill.getSkill().getName() +" vs " + skillName,Misc.getHighlightColor());							
					int level = (int) stats.getSkillLevel(skill.getSkill().getId());
					if (level <= 1) 
					{				
						Global.getSector().getCampaignUI().addMessage("Pilot, callsign \""+ callsign + "\" 's aptitude in " + skill.getSkill().getName() +" increased to Elite!",Misc.getHighlightColor());
						stats.increaseSkill(skill.getSkill().getId());
						skilledUp = true;
						break;
					}
				}
			if(!skilledUp && pilot.getStats().getLevel() < maxLevel && !stats.hasSkill(newSkill) || (!skilledUp && pilot.getStats().getLevel() < maxOverLevel && pilot.getStats().getLevel() >= maxLevel && !stats.hasSkill(newSkill) && maxOverLevel - pilot.getStats().getLevel() * overLevelChance > Math.random() ) )
			{
				Global.getSector().getCampaignUI().addMessage("Pilot, callsign \""+ callsign + "\" learned " + skillName +"!",Misc.getHighlightColor());
				stats.increaseSkill(newSkill);
				stats.setLevel(stats.getLevel()+1);
			}
			
			Global.getSector().getPersistentData().put("armaa_wingCommander_wingman_"+j+"_"+member.getCaptain().getId(),pilot); 
		}
	}

}
