package data.scripts.world.systems;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.terrain.AsteroidFieldTerrainPlugin.AsteroidFieldParams;
import org.lazywizard.lazylib.MathUtils;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.procgen.DefenderDataOverride;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.SalvageSpecialAssigner;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator; 
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.campaign.OrbitAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.jetbrains.annotations.Nullable;
import org.magiclib.util.MagicCampaign;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import org.lazywizard.lazylib.campaign.CampaignUtils;

import java.util.ArrayList;
import java.util.Arrays;

import static data.scripts.world.ARMAAWorldGen.addMarketplace;
import static com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin.DebrisFieldParams;
import static com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin.DebrisFieldSource;
public class armaa_nekki {
public static String MECH_ID = "valkazard";
	public void generate(SectorAPI sector) {
		
		StarSystemAPI system = sector.createStarSystem("Nekki");
		//private SectorEntityToken mecha;		
		//requireEntityMemoryFlags("$mecha");
		//mecha = pickEntity();
		
		LocationAPI hyper = Global.getSector().getHyperspace();
		system.getLocation().set(-10200f, -16000f);
		if(sector.getEntityById("hybrasil") != null)
		{
			switch (MathUtils.getRandomNumberInRange(1,4)) {
				case 1:
					system.getLocation().set(sector.getEntityById("hybrasil").getLocation().getX()+10000,sector.getEntityById("hybrasil").getLocation().getY()+4500);
					break;
				case 2:
					system.getLocation().set(sector.getEntityById("hybrasil").getLocation().getX()+15200,sector.getEntityById("hybrasil").getLocation().getY()+8500);

					break;
				case 3:
					system.getLocation().set(sector.getEntityById("hybrasil").getLocation().getX()-30000,sector.getEntityById("hybrasil").getLocation().getY()+8500);
					break;
				case 4:
					system.getLocation().set(sector.getEntityById("hybrasil").getLocation().getX()-10000,sector.getEntityById("hybrasil").getLocation().getY()+12500);
			}
		}		
		system.setBackgroundTextureFilename("graphics/backgrounds/background4.jpg");
		
		// create the star and generate the hyperspace anchor for this system
		PlanetAPI nekki_star = system.initStar("nekki", // unique id for this star 
											StarTypes.RED_GIANT,  // id in planets.json
										    1100f, 		  // radius (in pixels at default zoom)
										    500); // corona radius, from star edge
		system.setLightColor(new Color(255, 200, 210)); // light color in entire system, affects all entities
		
		// hot asteroid belt
//		system.addAsteroidBelt(nekki_star, 50, 2200, 100, 30, 40, Terrain.ASTEROID_BELT, null);
//		system.addRingBand(nekki_star, "misc", "rings_asteroids0", 256f, 3, Color.white, 256f, 13750, 345f, Terrain.ASTEROID_BELT, null);
		
		system.addAsteroidBelt(nekki_star, 50, 2200, 100, 30, 40, Terrain.ASTEROID_BELT, null);
		system.addRingBand(nekki_star, "misc", "rings_asteroids0", 256f, 3, Color.white, 256f, 2200, 345f, null, null);
		

		PlanetAPI nekki1 = system.addPlanet("nekki1", nekki_star, "Jenius", "jungle", 200, 230, 10000, 500);
	   MarketAPI jeniusMarket = addMarketplace("pirates", nekki1, null
			, nekki1.getName(), 4,
			new ArrayList<>(
					Arrays.asList(
							Conditions.POPULATION_1, // population
							Conditions.DECIVILIZED_SUBPOP
							
					)),
			new ArrayList<>(
					Arrays.asList(
							Submarkets.SUBMARKET_OPEN,
							Submarkets.SUBMARKET_BLACK,
							Submarkets.SUBMARKET_STORAGE
					)),
			new ArrayList<>(
					Arrays.asList(
							Industries.POPULATION,
							Industries.HEAVYBATTERIES
					)),
			0.3f,
			false,
			true);							 

		PlanetAPI meshan = system.addPlanet("nekki2", nekki_star, "Meshan", "toxic", 50, 225, 4500, 135);
		system.addAsteroidBelt( meshan,
                                50,
                                 400,
                                 50,
                                 39,
                                 49,
                                Terrain.ASTEROID_BELT,
                                 null);
		system.addAsteroidBelt( meshan,
                                100,
                                 600,
                                 70,
                                 59,
                                 69,
                                Terrain.ASTEROID_BELT,
                                 null);	
		meshan.setCustomDescriptionId("armaa_meshan");
		meshan.getSpec().setPlanetColor(new Color(126,230,155,255));
		//a new market for planet
        MarketAPI meshanMarket = meshan.getMarket();
		meshanMarket.addCondition(Conditions.RUINS_EXTENSIVE);
		meshanMarket.addCondition(Conditions.TOXIC_ATMOSPHERE);
		meshanMarket.addCondition(Conditions.EXTREME_WEATHER);
		meshanMarket.addCondition(Conditions.IRRADIATED);
		meshanMarket.addCondition(Conditions.INIMICAL_BIOSPHERE);		
		meshanMarket.addSubmarket(Submarkets.SUBMARKET_STORAGE); 
		//meshanMarket.getMemoryWithoutUpdate().set(MUSIC_SET_MEM_KEY,"");
         //   if (musicSetId != null) return musicSetId;
			Misc.setAllPlanetsSurveyed(system,true);
			meshanMarket.setHidden(true);
			SectorEntityToken ithaca_loc = system.addCustomEntity(null,null, "stable_location",Factions.NEUTRAL); 
			ithaca_loc.setCircularOrbitPointingDown( nekki_star, 50 + 60, 4500, 135);	
		
		// Ogygia, terraforming target B
		PlanetAPI penelope3 = system.addPlanet("nekki3", nekki_star, "Aznable", "barren-bombarded", 80, 130, 6800, 225);
		penelope3.getSpec().setPlanetColor(new Color(230,240,255,255));
		penelope3.applySpecChanges();
		
			PlanetAPI penelope3a = system.addPlanet("nekki3a", penelope3, "Union", "barren-bombarded", 80, 60, 400, 25);
			penelope3a.getSpec().setTexture(Global.getSettings().getSpriteName("planets", "barren02"));
			penelope3a.getSpec().setPlanetColor(new Color(220,230,255,255));
			penelope3a.applySpecChanges();
			
			// Penelope's Jumppoint - L4 (ahead)
			JumpPointAPI p_jumpPoint = Global.getFactory().createJumpPoint("nekki_jump", "Nekki's Inner Jump-point");
			p_jumpPoint.setCircularOrbit(system.getEntityById("nekki"), 80+60, 6800, 225);
			p_jumpPoint.setRelatedPlanet(penelope3);
			
			p_jumpPoint.setStandardWormholeToHyperspaceVisual();
			system.addEntity(p_jumpPoint);
			
			// Penelope trojans
			SectorEntityToken penelopeL4 = system.addTerrain(Terrain.ASTEROID_FIELD,
					new AsteroidFieldParams(
						400f, // min radius
						600f, // max radius
						16, // min asteroid count
						24, // max asteroid count
						4f, // min asteroid radius 
						16f, // max asteroid radius
						"Penelope L4 Asteroids")); // null for default name
			
			SectorEntityToken penelopeL5 = system.addTerrain(Terrain.ASTEROID_FIELD,
					new AsteroidFieldParams(
						400f, // min radius
						600f, // max radius
						16, // min asteroid count
						24, // max asteroid count
						4f, // min asteroid radius 
						16f, // max asteroid radius
						"Penelope L5 Asteroids")); // null for default name
			
			penelopeL4.setCircularOrbit(nekki1, 230 + 60, 9500, 450);
			penelopeL5.setCircularOrbit(nekki1, 230 - 60, 9500, 450);
			
			SectorEntityToken aeolus_l5_loc = system.addCustomEntity(null,null, "stable_location",Factions.NEUTRAL); 
			aeolus_l5_loc.setCircularOrbitPointingDown( nekki_star, 230 - 60, 9500, 450);		
			
			SectorEntityToken aeolus_counter_loc = system.addCustomEntity(null,null, "stable_location",Factions.NEUTRAL); 
			aeolus_counter_loc.setCircularOrbitPointingDown( nekki_star, 230 - 180, 9500, 450);


			
		PlanetAPI penelope5 = system.addPlanet("penelope5", nekki_star, "Raven", "gas_giant", 250, 280, 12050, 650);
		penelope5.getSpec().setPlanetColor(new Color(170,190,255,255));
		penelope5.getSpec().setGlowTexture(Global.getSettings().getSpriteName("hab_glows", "banded"));
		penelope5.getSpec().setGlowColor(new Color(150,225,255,32));
		penelope5.applySpecChanges();
		
		// Telepylus station : staging area for volatiles transport Oxen
		SectorEntityToken exsedol_station = system.addCustomEntity("exsedol_station", "Fort Exsedol", "station_sporeship_derelict", "neutral");
		exsedol_station.setCircularOrbitPointingDown(system.getEntityById("nekki2"), 90, 420, 25);		
		exsedol_station.setCustomDescriptionId("armaa_station_exsedol");
		exsedol_station.setInteractionImage("illustrations", "abandoned_station3");

       MarketAPI exsedolMarket = addMarketplace("armaarmatura_pirates", exsedol_station, null
                , exsedol_station.getName(), 4,
                new ArrayList<>(
                        Arrays.asList(
                                Conditions.POPULATION_4, // population
                                "armaa_mechbase"
                        )),
                new ArrayList<>(
                        Arrays.asList(
                                Submarkets.SUBMARKET_OPEN,
                                Submarkets.GENERIC_MILITARY,
                                Submarkets.SUBMARKET_BLACK,
                                Submarkets.SUBMARKET_STORAGE
                        )),
                new ArrayList<>(
                        Arrays.asList(
                                Industries.POPULATION,
                                Industries.SPACEPORT,
                                Industries.STARFORTRESS_MID,
								Industries.HEAVYINDUSTRY,
                                Industries.HIGHCOMMAND,
                                Industries.HEAVYBATTERIES
                        )),
                0.3f,
                false,
                true);
		
				
        //make a custom description which is specified in descriptions.csv
		exsedol_station.setInteractionImage("illustrations","armaa_exsedol_illus");
		exsedol_station.setCustomDescriptionId("armaa_station_exsedol");	

		SectorEntityToken research_station = system.addCustomEntity("armaa_research_station", "Abandoned Research Station", "station_side07", "neutral");
		research_station.setCircularOrbitPointingDown(system.getEntityById("penelope5"), 90, 420, 25);		
		research_station.setCustomDescriptionId("armaa_station_research");
		research_station.setInteractionImage("illustrations", "abandoned_station");
		research_station.addTag("armaa_tachie_ambush"); 
		Misc.setAbandonedStationMarket("armaa_research_station", research_station);
		research_station.getMarket().getSubmarket(Submarkets.SUBMARKET_STORAGE).getCargo().addMothballedShip(FleetMemberType.SHIP, "armaa_aleste_swordsman", null);
		
		system.addRingBand(meshan, "misc", "rings_special0", 256f, 1, new Color(200,200,200,255), 256f, 600, 30f, Terrain.RING, null); 

		SectorEntityToken valkazard = addDerelict(system, "armaa_valkazard_standard", research_station.getOrbit(), ShipRecoverySpecial.ShipCondition.GOOD, true, null);
		valkazard.getMemoryWithoutUpdate().set("$valkazard", true);	
		
        // Debris
        DebrisFieldParams params = new DebrisFieldParams(
                300f, // field radius - should not go above 1000 for performance reasons
                2f, // density, visual - affects number of debris pieces
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
		debris.setCircularOrbit(valkazard, 45 + 10, 0, 250);
		
		// Outer system jump-point
			// Telepylus Jumppoint - L5 (behind)
			JumpPointAPI p_jumpPoint2 = Global.getFactory().createJumpPoint("nekki_jump", "Nekki's Outer Jump-point");
			p_jumpPoint2.setCircularOrbit(system.getEntityById("nekki"), 250-60, 12050, 650);
			//p_jumpPoint2.setRelatedPlanet(penelope5);
			
			p_jumpPoint2.setStandardWormholeToHyperspaceVisual();
			system.addEntity(p_jumpPoint2);
			
		system.addAsteroidBelt(nekki_star, 100, 13750, 200, 330, 360, Terrain.ASTEROID_BELT, "Planetoid Debris");
		system.addRingBand(nekki_star, "misc", "rings_asteroids0", 256f, 0, Color.white, 256f, 13750, 345f, null, null);

		system.autogenerateHyperspaceJumpPoints(true, true);
		
		FleetParamsV3 fparams = new FleetParamsV3(
							Global.getSector().getEntityById("armaa_research_station").getLocationInHyperspace(),
							"tritachyon",
							null,
							FleetTypes.PATROL_MEDIUM,
							25f, // combatPts
							0f, // freighterPts 
							0f, // tankerPts
							0f, // transportPts
							0f, // linerPts
							0f, // utilityPts
							1f // qualityMod
							);
					CampaignFleetAPI fleet =FleetFactoryV3.createFleet(fparams);
					SectorEntityToken loc = Global.getSector().getEntityById("armaa_research_station");
					
					// Spawn fleet around player
					loc.getContainingLocation().spawnFleet(
							loc, 25, 25, fleet);
					Global.getSector().addPing(fleet, "danger");
					fleet.setLocation(loc.getLocation().x,loc.getLocation().y);
					fleet.setId("armaa_valkHunter");
					fleet.getMemoryWithoutUpdate().set("$armaa_valkHunters",true);
					CampaignUtils.addShipToFleet("hyperion_Attack",FleetMemberType.SHIP,fleet);
					CampaignUtils.addShipToFleet("armaa_aleste_swordsman",FleetMemberType.SHIP,fleet);
					CampaignUtils.addShipToFleet("afflictor_Strike",FleetMemberType.SHIP,fleet);				
					CampaignUtils.addShipToFleet("hyperion_Attack",FleetMemberType.SHIP,fleet);
					fleet.addAssignment(FleetAssignment.ORBIT_AGGRESSIVE, loc, 200f);
					
		//
        if (Global.getSector().getStarSystem("Nekki") != null)
		{
            system = sector.getStarSystem("Nekki");
            final SectorEntityToken fort_ex = system.getEntityById("exsedol_station");
            final CampaignFleetAPI mrcGuardFleet = (CampaignFleetAPI)MagicCampaign.createFleetBuilder()
            .setFleetName("Meshan Vigil Fleet")
            .setFleetFaction("armaarmatura_pirates")
            .setFleetType("taskForce")
            .setFlagshipName("MRV R-2")
            .setFlagshipVariant("armaa_kshatriya_boss")
            .setFlagshipAlwaysRecoverable(true)
            .setQualityOverride(3f)
            //.setCaptain((PersonAPI)Global.getSector().getImportantPeople().getPerson(BBPlus_People.OLIVER))
            .setMinFP(400)
            .setReinforcementFaction("armaarmatura_pirates")
            .setAssignment(FleetAssignment.ORBIT_AGGRESSIVE)
            .setSpawnLocation(fort_ex)
            .setSupportAutofit(true)
            .setIsImportant(false)
            .create();
            mrcGuardFleet.setDiscoverable(false);
            mrcGuardFleet.getMemoryWithoutUpdate().set("$canOnlyBeEngagedWhenVisibleToPlayer", true);
            mrcGuardFleet.addTag("armaa_mrcguardfleet");
        }
		
	}
	
 //Mini-function for generating derelicts
    public static SectorEntityToken addDerelict(StarSystemAPI system, String variantId, OrbitAPI orbit,
                                                ShipRecoverySpecial.ShipCondition condition, boolean recoverable,
                                                 @Nullable DefenderDataOverride defenders) {

        DerelictShipEntityPlugin.DerelictShipData params = new DerelictShipEntityPlugin.DerelictShipData(new ShipRecoverySpecial.PerShipData(variantId, condition), false);
        SectorEntityToken ship = BaseThemeGenerator.addSalvageEntity(system, Entities.WRECK, "derelict", params);
		//SectorEntityToken debris = BaseThemeGenerator.addDebrisField(BaseThemeGenerator.StarSystemData, ship, 250f);
		WeightedRandomPicker<String> graveships = new WeightedRandomPicker<>();
		graveships.add(Factions.TRITACHYON);
		graveships.add(Factions.REMNANTS);
		graveships.add(Factions.HEGEMONY);		
        ship.setDiscoverable(true);
		ship.setId("valkazard"); 
        ship.setOrbit(orbit);
		ship.setName("Derelict Cataphract");
        if (recoverable) {
            SalvageSpecialAssigner.ShipRecoverySpecialCreator creator = new SalvageSpecialAssigner.ShipRecoverySpecialCreator(null, 0, 0, false, null, null);
            Misc.setSalvageSpecial(ship, creator.createSpecial(ship, null));
        }
        if (defenders == null) {
            
        }
        return ship;
    }
}
