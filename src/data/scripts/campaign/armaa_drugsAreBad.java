package data.scripts.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.Misc;
import data.hullmods.armaa_sykoStims;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin.DataForEncounterSide;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import java.util.*;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

//import com.fs.starfarer.api.impl.campaign.*;


public class armaa_drugsAreBad extends BaseCampaignEventListener implements EveryFrameScript 
{
	public armaa_drugsAreBad() { super(true); }	
	transient EngagementResultAPI batResult;
	transient boolean isWin = false;
	static long previousXP = Long.MAX_VALUE;
	private int numEngagements = 0;
	
    public static long getReAdjustedXp() {
        long xpEarned = Global.getSector().getPlayerStats().getXP() - previousXP;

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

		if(!battle.isPlayerInvolved())
			return;
		
		if(batResult == null)
			return;

		isWin = battle == null || primaryWinner == null || battle.getPlayerSide() == null ? null
			: battle.getPlayerSide().contains(primaryWinner);
		
		List<DataForEncounterSide> sideData = new ArrayList<DataForEncounterSide>();
		long xpEarned = getReAdjustedXp();		
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
		EngagementResultForFleetAPI playerFleet = loserResult.isPlayer() ? loserResult : winnerResult;
		List<FleetMemberAPI> allyCasualties = new ArrayList<FleetMemberAPI>();		
		
		allyCasualties.addAll(playerFleet.getDestroyed());
		allyCasualties.addAll(playerFleet.getDisabled());
		allyCasualties.addAll(playerFleet.getRetreated());
		int numDrugs = 0;
		for (FleetMemberAPI member : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) 
		{
			if(member.getHullId().equals("armaa_valkazard") || member.getVariant().hasHullMod("armaa_sykoStims"))
				if(Global.getSector().getPlayerFleet().getCargo().getCommodityQuantity(Commodities.DRUGS) > 0 && (playerFleet.getDeployed().contains(member) || allyCasualties.contains(member)))
				{
					numDrugs += (member.getHullId().equals("armaa_valkazard") ? 1 : armaa_sykoStims.DRUGS_CONSUMED);
				}
		}
		if(numDrugs > 0)
		{
			String quant = numEngagements*numDrugs > 1 ? "units": "unit";
			Global.getSector().getCampaignUI().addMessage(numEngagements*numDrugs+" "+quant+" of " + Commodities.DRUGS.toString() + " consumed to replenish combat stims across fleet.", Misc.getNegativeHighlightColor()); 
			Global.getSoundPlayer().playUISound("ui_cargo_drugs",1f,1f);
			Global.getSector().getPlayerFleet().getCargo().removeCommodity(Commodities.DRUGS,numEngagements*numDrugs);
		}
		numDrugs = 0;	
		numEngagements =0;
		batResult = null;
	}
}
