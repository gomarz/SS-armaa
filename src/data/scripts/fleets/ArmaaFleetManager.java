package data.scripts.fleets;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.fleets.*;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathCells;
import com.fs.starfarer.api.impl.campaign.intel.bases.PirateBaseManager;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.campaign.RepLevel;
import java.util.*;
import org.lazywizard.lazylib.MathUtils;

/**
 * Adds the following types of fleets:
 * 1) Occasional pather fleet in hyper around a populated system
 * 2) A few more in and around systems with pather/church presence
 *  
 * @author Alex Mosolov
 *
 * Copyright 2018 Fractal Softworks, LLC
 */
public class ArmaaFleetManager extends DisposableFleetManager {

	protected Object readResolve() {
		super.readResolve();
		return this;
	}
	
	@Override
	protected String getSpawnId() {
		return "armaa_mercs"; // not a faction id, just an identifier for this spawner
	}
	
	@Override
	protected int getDesiredNumFleetsForSpawnLocation() {
		MarketAPI arma = getLargestMarket("armaarmatura_pirates");
		MarketAPI pirates = getLargestMarket("pirates");
		
		float desiredNumFleets = 1f;
		
		if (pirates != null) {
			desiredNumFleets++;
		}
		if (arma != null) {
			desiredNumFleets += arma.getSize()/2;
		}
		

		return (int) Math.round(desiredNumFleets);
	}

	protected MarketAPI getLargestMarket(String faction) {
		if (currSpawnLoc == null) return null;
		MarketAPI largest = null;
		int maxSize = 0;
		for (MarketAPI market : Global.getSector().getEconomy().getMarkets(currSpawnLoc)) {
			if (market.isHidden()) continue;
			if(!market.getFaction().getId().equals("tritachyon") || !market.getFaction().getId().equals("hegemony") || !market.getFaction().getId().equals("sindrian_diktat"))
				continue;
			if (market.getFaction().getRelationshipLevel("armaarmatura_pirates").isAtWorst(RepLevel.INHOSPITABLE)) continue;
			
			if (market.getSize() > maxSize) {
				maxSize = market.getSize();
				largest = market;
			}
		}
		return largest;
	}
	
	protected CampaignFleetAPI spawnFleetImpl() {
		StarSystemAPI system = currSpawnLoc;
		if (system == null) return null;
		
		CampaignFleetAPI player = Global.getSector().getPlayerFleet();
		if (player == null) return null;
		
		int num = Misc.getMarketsInLocation(system).size();
		if (Misc.getMarketsInLocation(system, Factions.PLAYER).size() == num && num > 0) { 
			return null; // handled by HostileActivityIntel, DisposableHostileActivityFleetManager, etc
		}
		
		
		String fleetType = FleetTypes.MERC_PATROL;

		
		float combat = 1;
		for (int i = 0; i < 3; i++) {
			if ((float) Math.random() > 0.5f) {
				combat++;
			}
		}
		
		float desired = getDesiredNumFleetsForSpawnLocation();
		if (desired > 2) {
			//combat += ((desired - 2) * (0.5f + (float) Math.random() * 0.5f)) * 1f * Math.random()*50f;
			combat += (desired - 2) * (0.5f + (float) Math.random() * 0.5f);
		}
		
		combat *= Math.random()*(2+MathUtils.getRandomNumberInRange(1,desired*3));
		
		FleetParamsV3 params = new FleetParamsV3(
				Global.getSector().getEconomy().getMarket("armaa_meshanii_market"), // source market 
				system.getLocation(),
				"armaarmatura_pirates", 
				null,
				fleetType,
				combat, // combatPts
				0, // freighterPts 
				0, // tankerPts
				0f, // transportPts
				0f, // linerPts
				0f, // utilityPts
				0f // qualityMod
				);
		params.ignoreMarketFleetSizeMult = false;
		
		//params.random = random;
		CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
		if (fleet == null || fleet.isEmpty()) return null;
		
		// setting the below means: transponder off and more "go dark" use when traveling
		//fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PIRATE, true);
		ArrayList<String> list = new ArrayList<String>();
		list.add(MemFlags.FLEET_NO_MILITARY_RESPONSE);
		list.add(MemFlags.FLEET_CHASING_GHOST);
		list.add(MemFlags.FLEET_CHASING_GHOST_RANDOM );
		list.add(MemFlags.FLEET_MILITARY_RESPONSE );
        Random random = new Random();
        int randomIndex = random.nextInt(list.size());

		fleet.getMemoryWithoutUpdate().set(list.get(randomIndex), true);
		
		float nf = getDesiredNumFleetsForSpawnLocation();
		
		if (nf == 1) {
			setLocationAndOrders(fleet, 1f, 1f);
		} else {
			setLocationAndOrders(fleet, 0.5f, 1f);
		}
		fleet.getMemoryWithoutUpdate().set("$armaa_fleetObjective", fleet.getCurrentAssignment().getActionText());		
		return fleet;
	}
	
}

