package data.missions.armaa_transport;

import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;


public class MissionDefinition implements MissionDefinitionPlugin {

        @Override
	public void defineMission(MissionDefinitionAPI api) {
                
                // Set up the fleets
		api.initFleet(FleetSide.PLAYER, "BLU", FleetGoal.ATTACK, false);
		api.initFleet(FleetSide.ENEMY, "TTS", FleetGoal.ATTACK, true);

		// Set a blurb for each fleet
		api.setFleetTagline(FleetSide.PLAYER, "Strike Team");
		api.setFleetTagline(FleetSide.ENEMY, "Tri-Tachyon Raiders");
		
		// These show up as items in the bulleted list under 
		// "Tactical Objectives" on the mission detail screen
		api.addBriefingItem("Utilize the Einhander's phase system to outmanuever the enemy and evade fire.");
		api.addBriefingItem("Keep friendly carriers on the field to rearm and repair the Mobius Squadron.");
		api.addBriefingItem("Draw the enemy cruiser's attention to allow bombers to do their job.");
                api.addBriefingItem("Mobius-3 must survive.");
		
		// Set up the player's fleet
		api.addToFleet(FleetSide.PLAYER, "armaa_einhander_Strike", FleetMemberType.SHIP, "Mobius-3", true);
		api.addToFleet(FleetSide.PLAYER, "armaa_valkenx_frig_assault", FleetMemberType.SHIP, "Mobius-1", false).getCaptain().setPersonality("aggressive");
		api.addToFleet(FleetSide.PLAYER, "condor_Strike", FleetMemberType.SHIP,"HSS Virtue", false).getCaptain().setPersonality("steady");
		api.addToFleet(FleetSide.PLAYER, "enforcer_XIV_Elite", FleetMemberType.SHIP,"HSS Tortoise", false).getCaptain().setPersonality("aggressive");
		api.addToFleet(FleetSide.PLAYER, "buffalo2_Fighter_Support", FleetMemberType.SHIP,"ISS Gibbon", false).getCaptain().setPersonality("steady");		
		api.addToFleet(FleetSide.PLAYER, "condor_Support", FleetMemberType.SHIP,"HSS Strider", false).getCaptain().setPersonality("steady");

		
		// Mark a ship as essential, if you want
		api.defeatOnShipLoss("Mobius-3");
		
		// Set up the enemy fleet

		api.addToFleet(FleetSide.ENEMY, "wolf_d_Attack", FleetMemberType.SHIP, false).getCaptain().setPersonality("aggressive");
		api.addToFleet(FleetSide.ENEMY, "wolf_Overdriven", FleetMemberType.SHIP, false).getCaptain().setPersonality("aggressive");
		api.addToFleet(FleetSide.ENEMY, "wolf_Assault", FleetMemberType.SHIP, false).getCaptain().setPersonality("aggressive");
		api.addToFleet(FleetSide.ENEMY, "aurora_Balanced", FleetMemberType.SHIP, true).getCaptain().setPersonality("reckless");

		api.addToFleet(FleetSide.ENEMY, "vigilance_Starting", FleetMemberType.SHIP, false).getCaptain().setPersonality("steady");
		api.addToFleet(FleetSide.ENEMY, "drover_Starting", FleetMemberType.SHIP, false).getCaptain().setPersonality("steady");
		
		// Set up the map.
		float width = 10000f;
		float height = 10000f;
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
						 
		//api.addPlanet(minX + width * 0.5f, minY + height * 0.5f, 200f, "SCY_homePlanet", 0f);
	}

}






