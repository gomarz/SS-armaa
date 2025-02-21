package data.scripts.shipsystems.ai;

import com.fs.starfarer.api.combat.FluxTrackerAPI;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipCommand;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import java.util.ArrayList;
import com.fs.starfarer.api.Global;
import java.util.List;
import java.awt.*;
import data.scripts.util.armaa_utils;

public class armaa_perceptAI implements ShipSystemAIScript
{
    private ShipAPI ship;
    private CombatEngineAPI engine;
    private ShipwideAIFlags flags;
    private ShipSystemAPI system;
    private IntervalUtil tracker;
	private boolean useSystem;
    
    public armaa_perceptAI() {
        this.tracker = new IntervalUtil(0.25f, 0.25f);
    }
    
    public void init(final ShipAPI ship, final ShipSystemAPI system, final ShipwideAIFlags flags, final CombatEngineAPI engine) {
        this.ship = ship;
        this.flags = flags;
        this.engine = engine;
        this.system = ship.getSystem();
    }
    
	public void forceTransform()
	{
		if(!engine.getCustomData().containsKey("armaa_transformNow_"+ship.getId()))	
			engine.getCustomData().put("armaa_transformNow_"+ship.getId(),"-");	
	}
	
	
    public void advance(final float amount, final Vector2f missileDangerDir, final Vector2f collisionDangerDir, final ShipAPI target) 
	{
        this.tracker.advance(amount);
        if (engine == null) {
            return;
        }
        if (engine.isPaused()) {
            return;
        }
		
		if(ship.getFluxTracker().isVenting() || ship.getFluxTracker().isOverloaded() || ship.getSystem().getCooldownRemaining() > 0 || ship.getSystem().getAmmo() <= 0)
			return;
		
		boolean isRobot = engine.getCustomData().get("armaa_tranformState_"+ship.getId()) != null ? 
			(Boolean)engine.getCustomData().get("armaa_tranformState_"+ship.getId()) : false;
		boolean transformingAlready = engine.getCustomData().get("armaa_transformNow_"+ship.getId()) != null ? 
			true : false;
		//Global.getCombatEngine().maintainStatusForPlayerShip("adrenal", "graphics/ui/icons/icon_search_and_destroy.png","DEBUG","transforming:" + transformingAlready,false);				
        if (tracker.intervalElapsed())
		{		
            float dicision = 0.0f;
			boolean abort = false;
			List<MissileAPI> missiles =  CombatUtils.getMissilesWithinRange(ship.getLocation(),1000f);
			List<MissileAPI> orbs = new ArrayList<MissileAPI>();
			for(MissileAPI missile : missiles)
			{
				if(missile.getOwner() != ship.getOwner())
					continue;
				if(missile.getSource() != ship)
					continue;
				if(missile.isArmed())
					continue;
				if(orbs.contains(missile))
					continue;
				if( missile.getWeaponSpec() != null && missile.getWeaponSpec().getWeaponId().equals("armaa_curvyLaser"))
					orbs.add(missile);
			}
			List<ShipAPI> targets = new ArrayList<ShipAPI>();
			for(ShipAPI potTarget:CombatUtils.getShipsWithinRange(ship.getLocation(), 1000f))
			{
				if(potTarget.getOwner() == ship.getOwner())
					continue;
				if(potTarget.isHulk() || !potTarget.isAlive())
					continue;
				targets.add(potTarget);
			}
			if(orbs.size() > 0 && targets != null && targets.size() > 0 && !flags.hasFlag(ShipwideAIFlags.AIFlags.HAS_INCOMING_DAMAGE))
			{
				if(isRobot && !transformingAlready)
					forceTransform();
				ship.useSystem();
				return;
			}				
			// No point using if we don't have any flux to do meaningful damage or orbs to shoot
			// Or there are no targets
			else if (!ship.areAnyEnemiesInRange() || (ship.getFluxLevel() < 0.05f  && orbs.size() <= 0)) 
			{
				return;
				
			}
			// We can either fire off a homing attack off enemy is some ways away or deploy an orb to 
			// try to pressure/delay them with EMP
			if(!flags.hasFlag(ShipwideAIFlags.AIFlags.HAS_INCOMING_DAMAGE) && !isRobot)
				flags.setFlag(ShipwideAIFlags.AIFlags.DO_NOT_USE_SHIELDS,1f);
			if(ship.getFluxLevel() >= 0.25f)
			{
				flags.setFlag(ShipwideAIFlags.AIFlags.DO_NOT_VENT,1f);				
				if(!transformingAlready && isRobot)
					forceTransform();
				ship.useSystem();
				return;
			}
			if(flags.hasFlag(ShipwideAIFlags.AIFlags.SAFE_VENT)) 
			{
				if(ship.getFluxLevel() >= 0.25f)
				{
					flags.setFlag(ShipwideAIFlags.AIFlags.DO_NOT_VENT,1f);
					flags.setFlag(ShipwideAIFlags.AIFlags.DO_NOT_USE_SHIELDS,1f);					
					if(isRobot)
						forceTransform();
					ship.useSystem();
					return;
				}
				
			}
			if(flags.hasFlag(ShipwideAIFlags.AIFlags.BACKING_OFF) && AIUtils.getNearestEnemy(ship) != null) 
			{
				//They're close enough to get EMPed, probably
				if(MathUtils.getDistance(AIUtils.getNearestEnemy(ship),ship) <= 700f)
				{
					//CHeck if Robot, if not transform
					if(isRobot && orbs.size() < 1 && ship.getFluxLevel() >= 0.15f)
					{
						useSystem = true;
					}
				}
				
				else
				{
					if(isRobot && !transformingAlready && orbs.size() > 0 && ship.getFluxLevel() >= 0.10f)
					{
						forceTransform();
					}
					useSystem = true;
				}
			}
			else if(flags.hasFlag(ShipwideAIFlags.AIFlags.PURSUING) && ship.getFluxLevel() >= 0.20)
			{
				if(!isRobot)
					useSystem = true;
				else if(!transformingAlready)
				{
					forceTransform();
					useSystem = true;
				}
			}
			//general logic
			if(Math.random() < 0.30f)
			if(ship.getFluxLevel() < 0.75f && ship.getFluxLevel() >= 0.2f )
			{
				ShipAPI nearest = AIUtils.getNearestEnemy(ship);
				if(orbs.size() > 1 && isRobot && !transformingAlready)
					forceTransform();
				else if(isRobot && (nearest == null || MathUtils.getDistance(nearest,ship) > 700f))
					return;
				if(useSystem)
					if(!ship.getSystem().isActive())
						ship.useSystem();
			}
		}
	}
}