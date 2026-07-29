package data.scripts.world.systems;

import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.procgen.NebulaEditor;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.SalvageSpecialAssigner;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.terrain.MagneticFieldTerrainPlugin.MagneticFieldParams;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator.StarSystemType;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;

import static com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin.DebrisFieldParams;
import static com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin.DebrisFieldSource;
import com.fs.starfarer.api.impl.campaign.terrain.StarCoronaTerrainPlugin.CoronaParams;
import data.scripts.fleets.armaa_LuddObserverFleetScript;
import static data.scripts.world.ARMAAWorldGen.addMarketplace;
import org.magiclib.util.MagicCampaign;

public class armaa_K {

    public void generate(SectorAPI sector) {
        //create a star system
        StarSystemAPI system = sector.createStarSystem("Gamlin");
        //set its location
        system.getLocation().set(-16200f, -13000f);
        //set background image
        system.setBackgroundTextureFilename("graphics/armaa/backgrounds/armaa_homesystem.jpg");
        if (Global.getSettings().getModManager().isModEnabled("deconomics")) {
        }

        //the star
        PlanetAPI pg_Star = system.initStar("Gamlin", "star_yellow", 800f, 450f);
        //background light color
        system.setLightColor(new Color(255, 185, 50));

        //make asteroid belt surround it
        system.addAsteroidBelt(pg_Star, 100, 2200f, 150f, 180, 360, Terrain.ASTEROID_BELT, "");
        system.addAsteroidBelt(pg_Star, 200, 4200, 300, 100, 254);
        system.addRingBand(pg_Star, "misc", "rings_dust0", 256f, 3, new Color(200, 200, 255, 150), 1024f, 5600f, 365f, Terrain.RING, null);
        system.addRingBand(pg_Star, "misc", "rings_dust0", 256f, 5, new Color(200, 200, 255, 150), 1024f, 4200f, 150f, Terrain.RING, null);
        system.addRingBand(pg_Star, "misc", "rings_dust0", 256f, 2, new Color(200, 200, 200, 180), 1024f, 2400f, 265f, Terrain.RING, null);
        system.addRingBand(pg_Star, "misc", "rings_dust0", 256f, 4, new Color(200, 200, 255, 180), 256f, 1800f, 200f, Terrain.RING, null);
        system.addAsteroidBelt(pg_Star, 300, 8200, 400, 200, 354);
        //a new planet for people
        PlanetAPI meshanii = system.addPlanet("armaa_meshanii", pg_Star, "New Meshan", "arid", 215, 180f, 3100f, 365f);
        //a new market for planet
        MarketAPI meshaniiMarket = addMarketplace("independent", meshanii, null,
                meshanii.getName(), 5,
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
                                Industries.BATTLESTATION_MID,
                                Industries.FARMING,
                                Industries.PATROLHQ,
                                Industries.ORBITALWORKS,
                                Industries.WAYSTATION,
                                Industries.HEAVYBATTERIES,
                                Industries.COMMERCE,
                                "armaa_armaarmatura"
                        )),
                0.3f,
                false,
                true);
        meshanii.setInteractionImage("illustrations", "armaa_newmeshan_illus");
        meshanii.setCustomDescriptionId("armaa_homeworld");
        meshaniiMarket.getIndustry(Industries.BATTLESTATION_MID).setAICoreId(Commodities.ALPHA_CORE);

        PlanetAPI nekki_Star = system.addPlanet("nekki_Star", pg_Star, "Saotome", "star_browndwarf", 180f, 525f, 12500, 650);
        //nekki_Star.getSpec().setCloudColor(new Color(140,185,225,175));
        //nekki_Star.getSpec().setPlanetColor(new Color(55,90,175,255));
        //nekki_Star.getSpec().setGlowTexture(Global.getSettings().getSpriteName("hab_glows", "banded"));
        //nekki_Star.getSpec().setGlowColor(new Color(20, 75, 180, 64));
        //nekki_Star.applySpecChanges();     
        system.addAsteroidBelt(nekki_Star, 200, 3200, 100, 100, 254);
        float coronaSize = 300f; // however far you want it to extend past the star

        SectorEntityToken nekki_corona = system.addTerrain(Terrain.CORONA,
                new CoronaParams(nekki_Star.getRadius() + coronaSize, // band width
                        (nekki_Star.getRadius() + coronaSize) / 2f, // middle radius
                        nekki_Star,
                        1f, // windBurnLevel
                        1f, // flareProbability
                        1f));  // crLossMult
        nekki_corona.setCircularOrbit(nekki_Star, 0, 0, 100);
        system.setSecondary(nekki_Star);           // setTertiary() for a third
        system.setType(StarSystemType.BINARY_FAR);      // or BINARY_CLOSE, TRINARY_2FAR, etc.
        //nekki_star_field.setCircularOrbit(nekki_Star, 0, 0, 50);

        PlanetAPI nekki1 = system.addPlanet("nekki1", pg_Star, "Jenius", "jungle", 0, 180, 5000, 400);

        nekki1.getSpec().setPlanetColor(new Color(255, 255, 255, 255));
        MarketAPI jeniusMarket = addMarketplace("armaarmatura_arusthai", nekki1, null,
                nekki1.getName(), 4,
                new ArrayList<>(
                        Arrays.asList(
                                Conditions.POPULATION_4, // population
                                Conditions.DECIVILIZED_SUBPOP,
                                Conditions.FARMLAND_RICH,
                                Conditions.HABITABLE
                        )),
                new ArrayList<>(
                        Arrays.asList(
                                Submarkets.SUBMARKET_STORAGE
                        )),
                new ArrayList<>(
                        Arrays.asList(
                                Industries.POPULATION,
                                Industries.FARMING,
                                Industries.MILITARYBASE,
                                "armaa_orbitalFabricator"
                        )),
                0.3f,
                true,
                true);
        jeniusMarket.getMemory().set("$story_critical", true);
        nekki1.setCustomDescriptionId("armaa_jenius");
        jeniusMarket.getMemoryWithoutUpdate().set("$nex_uninvadable", true);
        PlanetAPI meshan = system.addPlanet("nekki2", nekki_Star, "Meshan", "irradiated", 90, 200, 2550, 400);
        system.addAsteroidBelt(meshan,
                50,
                400,
                50,
                39,
                49,
                Terrain.ASTEROID_BELT,
                null);
        system.addAsteroidBelt(meshan,
                100,
                600,
                70,
                59,
                69,
                Terrain.ASTEROID_BELT,
                null);
        jeniusMarket.setHidden(true);
        meshan.setCustomDescriptionId("armaa_meshan");
        //meshan.getSpec().setPlanetColor(new Color(126,230,100,255));
        meshan.getSpec().setGlowColor(new Color(55, 245, 55, 255));
        meshan.applySpecChanges();
        SectorEntityToken meshan_star_field = system.addTerrain(Terrain.MAGNETIC_FIELD,
                new MagneticFieldParams(meshan.getRadius(), // terrain effect band width 
                        meshan.getRadius() / 2f, // terrain effect middle radius
                        meshan, // entity that it's around
                        meshan.getRadius() + 25f, // visual band start
                        meshan.getRadius() + 25f + 200f, // visual band end
                        new Color(50, 165, 75, 75), // base color
                        1.0f, // probability to spawn aurora sequence, checked once/day when no aurora in progress
                        new Color(55, 60, 140),
                        new Color(65, 85, 155),
                        new Color(175, 105, 165),
                        new Color(90, 130, 180),
                        new Color(105, 150, 190),
                        new Color(120, 175, 205),
                        new Color(135, 200, 220)));

        meshan_star_field.setCircularOrbit(meshan, 0, 0, 50);
        //a new market for planet
        // nekki3, magnetic storms
        PlanetAPI nekki3 = system.addPlanet("nekki3", pg_Star, "Gravion", "gas_giant", 0, 450, 6500, 500);
        nekki3.getSpec().setPlanetColor(new Color(245, 150, 55, 255));
        nekki3.getSpec().setGlowTexture(Global.getSettings().getSpriteName("hab_glows", "banded"));
        nekki3.getSpec().setGlowColor(new Color(250, 155, 55, 64));
        nekki3.getSpec().setUseReverseLightForGlow(true);
        nekki3.applySpecChanges();

        PlanetAPI nekki4 = system.addPlanet("nekki4", pg_Star, "Immelman", "toxic_cold", 90, 250, 9500, 600);

        MarketAPI meshanMarket = meshan.getMarket();
        meshanMarket.addCondition(Conditions.RUINS_EXTENSIVE);
        meshanMarket.addCondition(Conditions.TOXIC_ATMOSPHERE);
        meshanMarket.addCondition(Conditions.EXTREME_WEATHER);
        meshanMarket.addCondition(Conditions.IRRADIATED);
        meshanMarket.addCondition(Conditions.INIMICAL_BIOSPHERE);
        meshanMarket.addSubmarket(Submarkets.SUBMARKET_STORAGE);

        Misc.setAllPlanetsSurveyed(system, true);

        SectorEntityToken exsedol_station = system.addCustomEntity("exsedol_station", "Fort Exsedol", "station_sporeship_derelict", "neutral");
        exsedol_station.setCircularOrbitPointingDown(system.getEntityById("nekki2"), 90, 420, 25);
        exsedol_station.setInteractionImage("illustrations", "armaa_exsedol_illus");
        exsedol_station.setCustomDescriptionId("armaa_station_exsedol");
        MarketAPI exsedolMarket = addMarketplace("pirates", exsedol_station, null,
                exsedol_station.getName(), 4,
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
                                Submarkets.SUBMARKET_STORAGE,
                                "armaa_mrcMarket"
                        )),
                new ArrayList<>(
                        Arrays.asList(
                                Industries.POPULATION,
                                Industries.MEGAPORT,
                                Industries.BATTLESTATION_MID,
                                Industries.HEAVYINDUSTRY,
                                Industries.PATROLHQ,
                                Industries.HEAVYBATTERIES,
                                "armaa_mrc"
                        )),
                0.3f,
                false,
                true);
        SectorEntityToken research_station = system.addCustomEntity("armaa_research_station", "Abandoned Research Station", "station_side07", "neutral");
        research_station.setCircularOrbitPointingDown(pg_Star, 90, 12000, 625);
        research_station.setCustomDescriptionId("armaa_station_research");
        research_station.setInteractionImage("illustrations", "abandoned_station");
        research_station.addTag("armaa_tachie_ambush");
        Misc.setAbandonedStationMarket("armaa_research_station", research_station);
        research_station.getMarket().getSubmarket(Submarkets.SUBMARKET_STORAGE).getCargo().addMothballedShip(FleetMemberType.SHIP, "armaa_aleste_swordsman", null);

        system.addRingBand(meshan, "misc", "rings_dust0", 256f, 1, new Color(200, 200, 200, 150), 256f, 600, 30f, Terrain.RING, null);
        SectorEntityToken ithaca_loc = system.addCustomEntity(null, null, "stable_location", Factions.NEUTRAL);
        ithaca_loc.setCircularOrbitPointingDown(pg_Star, 50 + 60, 4500, 135);
        DebrisFieldParams params = new DebrisFieldParams(
                150f, // field radius - should not go above 1000 for performance reasons
                1f, // density, visual - affects number of debris pieces
                10000000f, // duration in days
                0f); // days the field will keep generating glowing pieces
        params.source = DebrisFieldSource.MIXED;
        params.baseSalvageXP = 500; // base XP for scavenging in field
        SectorEntityToken debris = Misc.addDebrisField(system, params, StarSystemGenerator.random);
        SalvageSpecialAssigner.assignSpecialForDebrisField(debris);
        debris.setSensorProfile(null);
        debris.setDiscoverable(null);
        debris.setDiscoverable(true);
        debris.setDiscoveryXP(200f);
        debris.setSensorProfile(1f);
        debris.getDetectedRangeMod().modifyFlat("gen", 2000);
        SectorEntityToken gate = system.addCustomEntity("armaa_gate", "Gamlin Gate", "inactive_gate", Factions.NEUTRAL);
        gate.setCircularOrbitPointingDown(pg_Star, 245 - 60, 6500, 1000);

        JumpPointAPI p_jumpPoint2 = Global.getFactory().createJumpPoint("gamlin_jump", "Gamlin's Inner Jump-point");
        p_jumpPoint2.setCircularOrbit(pg_Star, 250 - 60, 2000, 650);
        p_jumpPoint2.setStandardWormholeToHyperspaceVisual();
        system.addEntity(p_jumpPoint2);
        JumpPointAPI j_jumpPoint2 = Global.getFactory().createJumpPoint("jenius_jump", "Saotome's Inner Jump-point");
        j_jumpPoint2.setCircularOrbit(nekki_Star, 250 - 60, 1000, 650);
        j_jumpPoint2.setStandardWormholeToHyperspaceVisual();
        system.addEntity(j_jumpPoint2);
        system.autogenerateHyperspaceJumpPoints(true, true);

        SectorEntityToken relay = system.addCustomEntity("armaa_relay", "Comm Relay", "comm_relay", Factions.PIRATES);
        relay.setCircularOrbitPointingDown(system.getEntityById("nekki_Star"), 270, 3500, 300);
        debris.setCircularOrbit(pg_Star, 45 + 10, 1600, 250);
        system = sector.getStarSystem("Gamlin");
        final SectorEntityToken fort_ex = system.getEntityById("exsedol_station");
        final CampaignFleetAPI mrcGuardFleet = (CampaignFleetAPI) MagicCampaign.createFleetBuilder()
                .setFleetName("Meshan Vigil Fleet")
                .setFleetFaction("armaarmatura_pirates")
                .setFleetType("taskForce")
                .setFlagshipName("R-2")
                .setFlagshipVariant("armaa_exilium_custom")
                .setFlagshipAlwaysRecoverable(true)
                .setQualityOverride(3f)
                //.setCaptain((PersonAPI)Global.getSector().getImportantPeople().getPerson(BBPlus_People.OLIVER))
                .setMinFP(400)
                .setReinforcementFaction("armaarmatura_pirates")
                .setAssignment(FleetAssignment.DEFEND_LOCATION)
                .setSpawnLocation(fort_ex)
                .setSupportAutofit(true)
                .setIsImportant(false)
                .create();
        mrcGuardFleet.setDiscoverable(false);
        //mrcGuardFleet.getMemoryWithoutUpdate().set("$canOnlyBeEngagedWhenVisibleToPlayer", true);
        mrcGuardFleet.addTag("armaa_guardfleet");
        mrcGuardFleet.setFaction("armaarmatura_pirates");

        String faction = Global.getSettings().getModManager().isModEnabled("knights_of_ludd") ? "knights_of_selkie" : "luddic_church";
        String fleetName = Global.getSettings().getModManager().isModEnabled("knights_of_ludd") ? "Hallowed Sword of the Watcher" : "Knights of Ludd Task Force";
        final CampaignFleetAPI luddicKnightsFleet = (CampaignFleetAPI) MagicCampaign.createFleetBuilder()
                .setFleetName(fleetName)
                .setFleetFaction(faction)
                .setFleetType("taskForce")
                .setFlagshipAlwaysRecoverable(true)
                .setQualityOverride(3f)
                //.setCaptain((PersonAPI)Global.getSector().getImportantPeople().getPerson(BBPlus_People.OLIVER))
                .setMinFP(150)
                .setReinforcementFaction("luddic_church")
                .setAssignment(FleetAssignment.ORBIT_AGGRESSIVE)
                .setSpawnLocation(meshanii)
                .setSupportAutofit(true)
                .setIsImportant(false)
                .create();
        luddicKnightsFleet.setDiscoverable(true);
        luddicKnightsFleet.getMemoryWithoutUpdate().set("$canOnlyBeEngagedWhenVisibleToPlayer", true);
        //mrcGuardFleet.addTag("armaa_guardfleet");
        armaa_LuddObserverFleetScript src = new armaa_LuddObserverFleetScript(luddicKnightsFleet);
        luddicKnightsFleet.addEventListener(src);
        luddicKnightsFleet.addScript(src);
        luddicKnightsFleet.setId("armaa_luddicMeshanFleet");
        cleanup(system);
    }

    private void cleanup(StarSystemAPI system) {
        HyperspaceTerrainPlugin plugin = (HyperspaceTerrainPlugin) Misc.getHyperspaceTerrain().getPlugin();
        NebulaEditor editor = new NebulaEditor(plugin);
        float minRadius = plugin.getTileSize() * 2f;

        float radius = system.getMaxRadiusInHyperspace();
        editor.clearArc(system.getLocation().x, system.getLocation().y, 0, radius + minRadius * 0.5f, 0, 360f);
        editor.clearArc(system.getLocation().x, system.getLocation().y, 0, radius + minRadius, 0, 360f, 0.25f);
    }

}
