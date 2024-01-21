package data.scripts.world.systems;

import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.HeavyIndustry;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.procgen.NebulaEditor;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.SalvageSpecialAssigner;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.Global;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;

import static com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin.DebrisFieldParams;
import static com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin.DebrisFieldSource;
import static data.scripts.world.ARMAAWorldGen.addMarketplace;

public class armaa_K {
	
	boolean hasIndEvo = false;
    public void generate(SectorAPI sector) {
        //create a star system
        StarSystemAPI system = sector.createStarSystem("Gamlin");
        //set its location
        system.getLocation().set(-16200f, -13000f);
        //set background image
        system.setBackgroundTextureFilename("graphics/armaa/backgrounds/armaa_homesystem.jpg");
		if(Global.getSettings().getModManager().isModEnabled("deconomics"))
		hasIndEvo = true;

        //the star
        PlanetAPI pg_Star = system.initStar("Gamlin", "star_yellow", 600f, 350f);
        //background light color
        system.setLightColor(new Color(255, 185, 50));

        //make asteroid belt surround it
        system.addAsteroidBelt(pg_Star, 100, 2200f, 150f, 180, 360, Terrain.ASTEROID_BELT, "");

        //a new planet for people
        PlanetAPI meshanii = system.addPlanet("armaa_meshanii", pg_Star, "New Meshan", "arid", 215, 120f, 4500f, 365f);
        //a new market for planet
        MarketAPI meshaniiMarket = addMarketplace("independent", meshanii, null
                , meshanii.getName(), 5,
                new ArrayList<>(
                        Arrays.asList(
                                Conditions.POPULATION_5, // population
                                Conditions.HABITABLE,
                                "armaa_mechbase",
				Conditions.POLLUTION,
				Conditions.HOT,
				Conditions.INDUSTRIAL_POLITY,
				Conditions.FARMLAND_ADEQUATE,
				Conditions.VICE_DEMAND
                        )),
                new ArrayList<>(
                        Arrays.asList(
                                Submarkets.SUBMARKET_OPEN,
                                Submarkets.GENERIC_MILITARY,
				"armaa_market",
                                Submarkets.SUBMARKET_BLACK,
                                Submarkets.SUBMARKET_STORAGE
                        )),
                new ArrayList<>(
                        Arrays.asList(
                                Industries.POPULATION,
                                Industries.MEGAPORT,
                                Industries.BATTLESTATION_HIGH,
                                Industries.MILITARYBASE,
                                Industries.ORBITALWORKS,
                                Industries.WAYSTATION,
                                Industries.HEAVYBATTERIES,
								Industries.COMMERCE
                        )),
                0.3f,
                false,
                true);
		
				
        //make a custom description which is specified in descriptions.csv
		meshanii.setInteractionImage("illustrations","armaa_newmeshan_illus");
        meshanii.setCustomDescriptionId("armaa_homeworld");
        //give the orbital works a gamma core
       meshaniiMarket.getIndustry(Industries.BATTLESTATION_HIGH).setAICoreId(Commodities.ALPHA_CORE);
        //and give it a nanoforge
       // ((HeavyIndustry) meshaniiMarket.getIndustry(Industries.ORBITALWORKS)).setNanoforge(new SpecialItemData(Items.CORRUPTED_NANOFORGE, null));


        // Debris
        DebrisFieldParams params = new DebrisFieldParams(
                150f, // field radius - should not go above 1000 for performance reasons
                1f, // density, visual - affects number of debris pieces
                10000000f, // duration in days
                0f); // days the field will keep generating glowing pieces
        params.source = DebrisFieldSource.MIXED;
        params.baseSalvageXP = 500; // base XP for scavenging in field
        SectorEntityToken debris = Misc.addDebrisField(system, params, StarSystemGenerator.random);
        SalvageSpecialAssigner.assignSpecialForDebrisField(debris);

        // makes the debris field always visible on map/sensors and not give any xp or notification on being discovered
        debris.setSensorProfile(null);
        debris.setDiscoverable(null);

        // makes it discoverable and give 200 xp on being found
        // sets the range at which it can be detected (as a sensor contact) to 2000 units
        // commented out.
        debris.setDiscoverable(true);
        debris.setDiscoveryXP(200f);
        debris.setSensorProfile(1f);
        debris.getDetectedRangeMod().modifyFlat("gen", 2000);
	SectorEntityToken gate = system.addCustomEntity("armaa_gate", "Gamlin Gate", "inactive_gate", Factions.NEUTRAL);
	gate.setCircularOrbitPointingDown(pg_Star, 245-60, 8500, 1000);
	system.autogenerateHyperspaceJumpPoints(true, true);
	system.addAsteroidBelt(system.getEntityById("armaa_meshanii"), 50, 2000, 100, 100, 254); 
	SectorEntityToken relay = system.addCustomEntity("armaa_relay", "Comm Relay", "comm_relay", Factions.NEUTRAL);
	relay.setCircularOrbitPointingDown(system.getEntityById("armaa_meshanii"), 245-60, 2250, 200);

        debris.setCircularOrbit(pg_Star, 45 + 10, 1600, 250);
        //Finally cleans up hyperspace
        cleanup(system);

    }

    //Learning from Tart scripts
    //Clean nearby Nebula(nearby system)
    private void cleanup(StarSystemAPI system) {
        HyperspaceTerrainPlugin plugin = (HyperspaceTerrainPlugin) Misc.getHyperspaceTerrain().getPlugin();
        NebulaEditor editor = new NebulaEditor(plugin);
        float minRadius = plugin.getTileSize() * 2f;

        float radius = system.getMaxRadiusInHyperspace();
        editor.clearArc(system.getLocation().x, system.getLocation().y, 0, radius + minRadius * 0.5f, 0, 360f);
        editor.clearArc(system.getLocation().x, system.getLocation().y, 0, radius + minRadius, 0, 360f, 0.25f);
    }
}
