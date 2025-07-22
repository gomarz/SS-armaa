package data.scripts.campaign;

import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin.DataForEncounterSide;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import java.util.*;
import com.fs.starfarer.api.campaign.RepLevel;

public class armaa_skyMindBattleResultListener extends BaseCampaignEventListener implements EveryFrameScript
{
	public armaa_skyMindBattleResultListener() { super(true); }
	
	static long previousXP = Long.MAX_VALUE;
	private Float repRewardPerson = null;
	private Float repPenaltyPerson = null;
	private Float repRewardFaction = null;
	transient int numEngagements = 0;
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
	
	@Override
    public void advance(float amount) 
	{
		return;
	}
	
    @Override
    public boolean runWhilePaused() 
	{
        return false;
    }

	@Override
    public boolean isDone() { return false; }
			
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

		List<DataForEncounterSide> sideData = new ArrayList<DataForEncounterSide>();
		
		EngagementResultForFleetAPI winnerResult =batResult.getWinnerResult();
		EngagementResultForFleetAPI loserResult = batResult.getLoserResult();
		clearNoSourceMembers(winnerResult,battle);
		clearNoSourceMembers(loserResult,battle);
		
		DataForEncounterSide winnerData = getDataFor(winnerResult.getFleet(),battle,sideData);
		DataForEncounterSide loserData = getDataFor(loserResult.getFleet(),battle,sideData);		

		EngagementResultForFleetAPI enemyFleet = loserResult.isPlayer() ? winnerResult : loserResult;
		EngagementResultForFleetAPI playerFleet = loserResult.isPlayer() ? loserResult : winnerResult;

		List<FleetMemberAPI> allyCasualties = new ArrayList<FleetMemberAPI>();		
		
		allyCasualties.addAll(playerFleet.getDestroyed());
		allyCasualties.addAll(playerFleet.getDisabled());
		
		for (FleetMemberAPI ship : allyCasualties) 
		{
			
			if (ship.getVariant().getHullMods().contains("armaa_skyMindAlpha")){
				ship.getVariant().removeMod("armaa_skyMindAlpha");
			}
			else if (ship.getVariant().getHullMods().contains("armaa_skyMindBeta")){
				ship.getVariant().removeMod("armaa_skyMindBeta");
			}
			else if (ship.getVariant().getHullMods().contains("armaa_skyMindGamma")){
				ship.getVariant().removeMod("armaa_skyMindGamma");
			}
		}
		batResult = null;		
		allyCasualties.clear();
		numEngagements = 0;
	}
}
