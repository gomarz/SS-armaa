package data.scripts.world;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SectorGeneratorPlugin;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import data.scripts.world.systems.armaa_K;
import data.scripts.world.systems.armaa_nekki;
import java.util.ArrayList;

public class ARMAAWorldGen implements SectorGeneratorPlugin {
    //Shorthand function for adding a market, just copy it
    public static MarketAPI addMarketplace(String factionID, SectorEntityToken primaryEntity, ArrayList<SectorEntityToken> connectedEntities, String name,
                                           int size, ArrayList<String> marketConditions, ArrayList<String> submarkets, ArrayList<String> industries, float tarrif,
                                           boolean freePort, boolean withJunkAndChatter) {
        EconomyAPI globalEconomy = Global.getSector().getEconomy();
        String planetID = primaryEntity.getId();
        String marketID = planetID + "_market";

        MarketAPI newMarket = Global.getFactory().createMarket(marketID, name, size);
        newMarket.setFactionId(factionID);
        newMarket.setPrimaryEntity(primaryEntity);
        newMarket.getTariff().modifyFlat("generator", tarrif);

        //Adds submarkets
        if (null != submarkets) {
            for (String market : submarkets) {
                newMarket.addSubmarket(market);
            }
        }

        //Adds market conditions
        for (String condition : marketConditions) {
            newMarket.addCondition(condition);
        }

        //Add market industries
        for (String industry : industries) {
            newMarket.addIndustry(industry);
        }

        //Sets us to a free port, if we should
        newMarket.setFreePort(freePort);

        //Adds our connected entities, if any
        if (null != connectedEntities) {
            for (SectorEntityToken entity : connectedEntities) {
                newMarket.getConnectedEntities().add(entity);
            }
        }

        globalEconomy.addMarket(newMarket, withJunkAndChatter);
        primaryEntity.setMarket(newMarket);
        primaryEntity.setFaction(factionID);

        if (null != connectedEntities) {
            for (SectorEntityToken entity : connectedEntities) {
                entity.setMarket(newMarket);
                entity.setFaction(factionID);
            }
        }

        //Finally, return the newly-generated market
        return newMarket;
    }

    @Override
    public void generate(SectorAPI sector) {
        FactionAPI mek = sector.getFaction("armaarmatura");
        //Generate your system
        new armaa_K().generate(sector);
        //new armaa_nekki().generate(sector);
        //Add faction to bounty system
        //SharedData.getData().getPersonBountyEventData().addParticipatingFaction("mek");
        //set relationship
        mek.setRelationship(Factions.HEGEMONY, .10f);
        mek.setRelationship(Factions.LUDDIC_CHURCH, -0.10f);
        mek.setRelationship(Factions.LUDDIC_PATH, -0.51f);
        mek.setRelationship(Factions.PERSEAN, 0.25f);
		mek.setRelationship(Factions.INDEPENDENT, 0.15f);
        mek.setRelationship(Factions.PIRATES, -0.5f);
        mek.setRelationship("armaarmatura_pirates", -0.5f);
		mek.setRelationship(Factions.TRITACHYON, -0.3f);
		mek.setRelationship(Factions.REMNANTS, -1.0f);

        mek = sector.getFaction("armaarmatura_market");
        mek.setRelationship("armaarmatura", 100f);
        mek.setRelationship("independent", 50f);
        mek.setRelationship(Factions.HEGEMONY, .10f);
        mek.setRelationship(Factions.LUDDIC_CHURCH, -0.10f);
        mek.setRelationship(Factions.LUDDIC_PATH, -0.50f);
        mek.setRelationship(Factions.PERSEAN, 0.10f);
		mek.setRelationship(Factions.INDEPENDENT, 0.15f);
        mek.setRelationship(Factions.PIRATES, -0.5f);
		mek.setRelationship(Factions.TRITACHYON, -0.3f);
		mek.setRelationship(Factions.REMNANTS, -1.0f);		
		
		mek = sector.getFaction("armaarmatura_pirates");

        mek.setRelationship(Factions.LUDDIC_CHURCH, 0.1f); // tacit support from some officials?
        mek.setRelationship(Factions.LUDDIC_PATH, 0.3f);  // 
        mek.setRelationship(Factions.PERSEAN, 0.0f);		//
        mek.setRelationship("armaarmatura", -0.10f);		//	
        mek.setRelationship("armaarmatura_market", -0.10f);		//			
		mek.setRelationship(Factions.INDEPENDENT, -0.30f);	//
        mek.setRelationship(Factions.PIRATES, 1f);	// connections with some elements of the pirate groups
		mek.setRelationship(Factions.TRITACHYON, -1f);	// planetkillers
		mek.setRelationship(Factions.REMNANTS, -1f);	// tritachyon equipment
		mek.setRelationship(Factions.DIKTAT, -1f);		// despots
		mek.setRelationship(Factions.HEGEMONY, -0.8f);		// funding from some officials?	
    }
}
