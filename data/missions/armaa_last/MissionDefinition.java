package data.missions.armaa_last;

import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;


import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.combat.EscapeRevealPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;


public class MissionDefinition implements MissionDefinitionPlugin {

        @Override
	public void defineMission(MissionDefinitionAPI api) {
                
                // Set up the fleets
		api.initFleet(FleetSide.PLAYER, "AASV", FleetGoal.ESCAPE, false,10);
		api.initFleet(FleetSide.ENEMY, "TTS", FleetGoal.ATTACK, true,30);

		// Set a blurb for each fleet
		api.setFleetTagline(FleetSide.PLAYER, "ArmaA Security Forces");
		api.setFleetTagline(FleetSide.ENEMY, "Tri-Tachyon Invaders");
		
		// These show up as items in the bulleted list under 
		// "Tactical Objectives" on the mission detail screen
		api.addBriefingItem("Use the Altagrave's system to protect ships from otherwise fatal weapons fire.");
		api.addBriefingItem("Your flagship loses speed and manuverability as it loses modules. Don't get flanked!");
		api.addBriefingItem("Meshan is all but lost. Don't dawdle, or you will be overwhelmed.");
		api.addBriefingItem("The AASV Chimera and Grapefruit must survive.");
		
		// Set up the player's fleet
		api.addToFleet(FleetSide.PLAYER, "armaa_altagrave_standard",FleetMemberType.SHIP,"AASV Chimera",true);
	//	api.addToFleet(FleetSide.PLAYER, "heron_Attack", FleetMemberType.SHIP, false).getCaptain().setPersonality("steady");
		FleetMemberAPI oddy =	api.addToFleet(FleetSide.PLAYER, "odyssey_Balanced", FleetMemberType.SHIP,"AASV Grapefruit", false);
	//	api.addToFleet(FleetSide.PLAYER, "gryphon_FS", FleetMemberType.SHIP, false).getCaptain().setPersonality("steady");		
	//	api.addToFleet(FleetSide.PLAYER, "armaa_condor_Strike", FleetMemberType.SHIP, false).getCaptain().setPersonality("steady");
	//	api.addToFleet(FleetSide.PLAYER, "drover_Strike", FleetMemberType.SHIP, false).getCaptain().setPersonality("steady");
		api.addToFleet(FleetSide.PLAYER, "kite_original_Stock", FleetMemberType.SHIP,"AASV Pointman", false).getCaptain().setPersonality("steady");

		
		// Mark a ship as essential, if you want
		api.defeatOnShipLoss("AASV Chimera");
		//api.defeatOnShipLoss("AASV Pointman");
		//api.defeatOnShipLoss("AASV Grapefruit");
		
		// Set up the enemy fleet

		api.addToFleet(FleetSide.ENEMY, "scintilla_Support", FleetMemberType.SHIP, false).getCaptain().setPersonality("aggressive");
		api.addToFleet(FleetSide.ENEMY, "radiant_Standard", FleetMemberType.SHIP, false).getCaptain().setPersonality("aggressive");
		api.addToFleet(FleetSide.ENEMY, "glimmer_Support", FleetMemberType.SHIP, false).getCaptain().setPersonality("aggressive");
		api.addToFleet(FleetSide.ENEMY, "lumen_Standard", FleetMemberType.SHIP, false).getCaptain().setPersonality("reckless");
		api.addToFleet(FleetSide.ENEMY, "glimmer_Support", FleetMemberType.SHIP, false).getCaptain().setPersonality("aggressive");
		api.addToFleet(FleetSide.ENEMY, "lumen_Standard", FleetMemberType.SHIP, false).getCaptain().setPersonality("reckless");
		
		// Set up the map.
		float width = 20000f;
		float height = 30000f;
		api.initMap((float)-width/2f, (float)width/2f, (float)-height/2f, (float)height/2f);
		
		float minX = -width/2;
		float minY = -height/2;
		
		// All the addXXX methods take a pair of coordinates followed by data for
		// whatever object is being added.
		
		// Add two big nebula clouds
		//api.addNebula(minX + width * 0.66f, minY + height * 0.5f, 2000);
		
		// And a few random ones to spice up the playing field.
		// A similar approach can be used to randomize everything
		// else, including fleet composition.
		for (int i = 0; i < 2; i++) {
			float x = (float) Math.random() * width - width/2;
			float y = (float) Math.random() * height - height/2;
			float radius = 100f + (float) Math.random() * 400f; 
			api.addNebula(x, y, radius);
		}
		
		// Add objectives. These can be captured by each side
		// and provide stat bonuses and extra command points to
		// bring in reinforcements.
		// Reinforcements only matter for large fleets - in this
		// case, assuming a 100 command point battle size,
		// both fleets will be able to deploy fully right away.
		api.addObjective(minX + width * 0.5f, minY + height * 0.5f, "sensor_array");
						 
		api.addPlanet(minX + width * 0.5f, minY + height * 0.5f, 3000f, "terran-eccentric", 0f);
		api.addPlugin(new Plugin());
		
		BattleCreationContext context = new BattleCreationContext(null, null, null, null);
        context.setInitialEscapeRange(2000f);
        api.addPlugin(new EscapeRevealPlugin(context));
	}
	
	    public final static class Plugin extends BaseEveryFrameCombatPlugin {

        private static boolean runOnce = false;
        private static int wave = 0;

        private  final IntervalUtil TIMER = new IntervalUtil(40f, 60f);

        private final Vector2f BOT_LEFT = new Vector2f();
        private  final Vector2f BOT_RIGHT = new Vector2f();
        private  final Vector2f MID_LEFT = new Vector2f();
        private final Vector2f MID_RIGHT = new Vector2f();
	    private  final Vector2f TOP = new Vector2f();
        private  final List<Vector2f> LOCS = new ArrayList(Arrays.asList(
              //  BOT_LEFT,
                //BOT_RIGHT,
                MID_LEFT,
                MID_RIGHT,
				TOP));

        private  final WeightedRandomPicker<String> FF = new WeightedRandomPicker<>();
		private  final WeightedRandomPicker<String> FF2 = new WeightedRandomPicker<>();
        private  final WeightedRandomPicker<String> DD = new WeightedRandomPicker<>();
        private  final WeightedRandomPicker<String> DD2 = new WeightedRandomPicker<>();
        private  final WeightedRandomPicker<String> CA = new WeightedRandomPicker<>();
        private  final WeightedRandomPicker<String> BB = new WeightedRandomPicker<>();
        private  final WeightedRandomPicker<String> CIV = new WeightedRandomPicker<>();
        private  final WeightedRandomPicker<String> CIV_NAME = new WeightedRandomPicker<>();

        @Override
        public void init(CombatEngineAPI engine) {
            runOnce = false;
            wave = 0;
			TOP.set(engine.getMapWidth()/2f,engine.getMapHeight()-6000f);
            BOT_LEFT.set(-(engine.getMapWidth() / 2f), -(engine.getMapHeight() / 2f));
            BOT_RIGHT.set((engine.getMapWidth() / 2f), -(engine.getMapHeight() / 2f));
            MID_LEFT.set(-(engine.getMapWidth() / 2f), 0f);
            MID_RIGHT.set((engine.getMapWidth() / 2f), 0f);

            FF.add("lumen_Standard", 0.25f);
            FF.add("glimmer_Assault", 0.25f);
            FF.add("wolf_Starting", 0.25f);
            FF.add("scarab_Experimental", 0.1f);
			
			DD.add("medusa_Attack", 0.15f);
            DD.add("scintilla_Support", 0.25f);
			
			FF2.add("brawler_Elite", .10f);
            FF2.add("lasher_CS", 0.25f);
            FF2.add("tempest_Attack", 0.05f);
            FF2.add("hound_Standard", 0.25f);
			FF2.add("armaa_condor_Support",.1f);

            DD2.add("sunder_Overdriven", 0.5f);
			
			CA.add("fulgent_Assault", 0.25f);
            CA.add("fulgent_Support", 0.25f);
			
			BB.add("brilliant_Standard", 0.2f);
            BB.add("brilliant_Standard", 0.5f);

            CIV.add("armaa_condor_Strike",0.2f);
            CIV.add("buffalo2_FS",0.2f);
            CIV.add("falcon_d_CS", 0.2f);
            CIV.add("vigilance_AP",0.2f);
			CIV.add("centurion_Assault",0.2f);
			CIV.add("armaa_einhander_strike",0.2f);
			CIV.add("armaa_einhander_Overclocked",0.2f);
            //CIV.add("vayra_mendicant_refugee");

            boolean swp = Global.getSettings().getModManager().isModEnabled("swp");

            if (swp) {

            }
        }

        @Override
        public void advance(float amount, List<InputEventAPI> events) {

            CombatEngineAPI engine = Global.getCombatEngine();

            if (CIV_NAME.isEmpty()) {
                CIV_NAME.add("AASV Dragoon");
                CIV_NAME.add("AASV Titan");
                CIV_NAME.add("AASV Wizard");
                CIV_NAME.add("AASV Active");
                CIV_NAME.add("ISS Philanthropist");
                CIV_NAME.add("ISS Barghest");
                CIV_NAME.add("ISS DMZ");
                CIV_NAME.add("ISS SAMURAI");
                CIV_NAME.add("ISS Brickyard");
                CIV_NAME.add("ISS Dove");
                CIV_NAME.add("AASV Quarter");
                CIV_NAME.add("ISS Kangaroo");
                CIV_NAME.add("ISS Monk");
                CIV_NAME.add("ISS Jenius");
                CIV_NAME.add("ISS White Knight");
                CIV_NAME.add("ISS Rojin");
            }

            if (!engine.isPaused()) {
                TIMER.advance(amount);
            }

            if (!runOnce) {
                TIMER.forceIntervalElapsed();
                runOnce = true;
				engine.getCombatUI().addMessage(1, "Dear Ludd...Meshan is falling..",Color.red);
				/*
				for (ShipAPI ship : engine.getShips()) {

                    // blow up Caliph shield generator
                    if (ship.getHullSpec().getHullId().equals("odyssey") && ship.isAlive()) 
					{
						ship.setAlly(true);
                    }

				}
				*/
			}

            if (TIMER.intervalElapsed()) {
                wave++;
                Vector2f botMid = new Vector2f(0f, -(engine.getMapHeight() / 2f));
				if(wave < 8)
				for(int i = 0; i < 3; i++)
				{
					Global.getSoundPlayer().playUISound("cr_allied_critical", 0.77f, 10f);
					Vector2f point = MathUtils.getRandomPointInCircle(botMid, 2000f);
					ShipAPI civ = CombatUtils.spawnShipOrWingDirectly(
							CIV.pick(),
							FleetMemberType.SHIP,
							FleetSide.PLAYER,
							0.6f,
							point,
							90f);
					civ.getFleetMember().setShipName(CIV_NAME.pickAndRemove());
					civ.setAlly(true);
				}
                //civ.setRetreating(true, true);
                //civ.setControlsLocked(true);
                for (Vector2f loc : LOCS) {
                    if (wave % 2f == 0f && wave < 6) {
                        List<String> toSpawn = new ArrayList<>();
                        for (int i = 0; i < 1; i++) {
                            for (int ff = 0; ff < 3; ff++) {
                                toSpawn.add(FF.pick());
                            }
                            for (int dd = 0; dd < 1; dd++) {
                                toSpawn.add(DD.pick());
                            }
                            for (int ca = 0; ca < 1; ca++) {
                                toSpawn.add(CA.pick());
                            }
                            for (int bb = 0; bb < 1; bb++) {
                                toSpawn.add(BB.pick());
                            }
                        }
                        for (String var : toSpawn) {
                            Vector2f point = MathUtils.getRandomPointInCircle(loc, 2000f);
                            ShipAPI enemy = CombatUtils.spawnShipOrWingDirectly(
                                    var,
                                    FleetMemberType.SHIP,
                                    FleetSide.ENEMY,
                                    0.6f,
                                    point,
                                    VectorUtils.getAngle(point, engine.getPlayerShip().getLocation()));
                            enemy.setOriginalOwner(1);
                            enemy.setOwner(1);
                        }
                    }
				
				for (Vector2f loc2 : LOCS) {
                    if (wave % 2f == 0f && wave < 4) {
                        List<String> toSpawn = new ArrayList<>();
                        for (int i = 0; i < 1; i++) {
                            for (int ff = 0; ff < 1; ff++) {
                                toSpawn.add(FF2.pick());
                            }
                            for (int dd = 0; dd < 1; dd++) {
                                toSpawn.add(FF2.pick());
                            }
                        }
                        for (String var : toSpawn) {
                            Vector2f point = MathUtils.getRandomPointInCircle(loc2, 2000f);
                            ShipAPI ally = CombatUtils.spawnShipOrWingDirectly(
                                    var,
                                    FleetMemberType.SHIP,
                                    FleetSide.PLAYER,
                                    0.6f,
                                    point,
                                    VectorUtils.getAngle(point, engine.getPlayerShip().getLocation()));
									ally.getFleetMember().setShipName(CIV_NAME.pickAndRemove());
								ally.setAlly(true);
                        }
                    }
                }
			}
            }
        }
    }
}




