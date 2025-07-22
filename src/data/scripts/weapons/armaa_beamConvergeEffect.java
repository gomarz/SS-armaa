package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.*;
//CombatFleetManagerAPI.AssignmentInfo;

public class armaa_beamConvergeEffect implements EveryFrameWeaponEffectPlugin {
    
    private static final Color CHARGEUP_PARTICLE_COLOR = new Color(130, 190, 160, 100);
    private static final float CHARGEUP_PARTICLE_DISTANCE_MIN = 4.0f;
    private static final float CHARGEUP_PARTICLE_DISTANCE_MAX = 60.0f;
    private static final float CHARGEUP_PARTICLE_SIZE_MIN = 1.0f;
    private static final float CHARGEUP_PARTICLE_SIZE_MAX = 4.0f;
    private static final float CHARGEUP_PARTICLE_ANGLE_SPREAD = 180.0f;
    private static final float CHARGEUP_PARTICLE_DURATION = 0.2f;
    private static final float CHARGEUP_PARTICLE_COUNT_FACTOR = 5.0f;
    private static final float CHARGEUP_PARTICLE_BRIGHTNESS = 0.7f;
    
    private static final Vector2f ZERO = new Vector2f();
    
    public static final float MAX_OFFSET = 5f; 
    public static final float SWEEP_INTERVAL = 1.5f;
    private List <Float> ANGLES = new ArrayList();
    
    protected float timer = 0;
    protected int dir = 1;
    
    private float last_charge_level = 0.0f;
    private boolean charging = false;
    private boolean firing = false;
    private boolean restart = true;
    private boolean runOnce = false;
	ShipAPI wingman;
    
    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{
        if (engine.isPaused()) {
            return;
        }
		
		ShipAPI ship = weapon.getShip();
 
		if (weapon.isFiring()) 
		{
			if(wingman == null)
			wingman = Global.getCombatEngine().getFleetManager(weapon.getShip().getOwner()).spawnShipOrWing("armaa_valkenmp_wing", weapon.getLocation(), weapon.getCurrAngle());
		
		}
		
		if(wingman != null)
		{
		//	else
			//{
				if(ship.getShipTarget() != null && ship.getShipTarget() != wingman.getShipTarget())
				{
				DeployedFleetMemberAPI target  = Global.getCombatEngine().getFleetManager(ship.getShipTarget().getOwner()).getDeployedFleetMember(ship.getShipTarget());
				CombatFleetManagerAPI.AssignmentInfo assign;	
			//	if(ship.getOwner() != ship.getShipTarget().getOwner())
				for(int i = 0; i < 3;i++)
				{
					ShipAPI fighter = wingman.getWing().getWingMembers().get(i);
					DeployedFleetMemberAPI fm = Global.getCombatEngine().getFleetManager(ship.getOwner()).getDeployedFleetMember(fighter);
					wingman.getWing().getWingMembers().get(i).getAIFlags().setFlag(AIFlags.CARRIER_FIGHTER_TARGET,1f,ship.getShipTarget().getOwner());
					wingman.getWing().getWingMembers().get(i).getAIFlags().setFlag(AIFlags.CARRIER_FIGHTER_TARGET,1f,ship.getShipTarget().getOwner());
					//wingman.getWing().getWingMembers().get(i).setHullSize(HullSize.FIGHTER);
				assign = Global.getCombatEngine().getFleetManager(weapon.getShip().getOwner()).getTaskManager(true).createAssignment(CombatAssignmentType.LIGHT_ESCORT,target,false);					
				Global.getCombatEngine().getFleetManager(ship.getOwner()).getTaskManager(true).giveAssignment(fm,assign,false);	
				}
				//else	
				}				
			//}
		}
		 
    }
}
