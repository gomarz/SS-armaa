
package data.scripts.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.combat.*;

public class armaa_armorPodAI implements MissileAIPlugin, GuidedMissileAI {
    
    private CombatEngineAPI engine;
    private final MissileAPI missile;
    private CombatEntityAPI target;
    private IntervalUtil blink = new IntervalUtil(0.4f,0.4f);
	private ShipAPI launchingShip;

    public armaa_armorPodAI(MissileAPI missile, ShipAPI launchingShip) {	        
        if (engine != Global.getCombatEngine()) {
            this.engine = Global.getCombatEngine();
        }
        this.missile = missile;  
		this.launchingShip = launchingShip;
        missile.setArmingTime(missile.getArmingTime()-(float)(Math.random()/4));
    }

    @Override
    public void advance(float amount) {        
        //skip the AI if the game is paused, the missile is engineless or fading
        if (engine.isPaused()){return;}
        
		if(!CollisionUtils.isPointWithinCollisionCircle(missile.getLocation(), launchingShip))
		for(ShipAPI potentialTarget: CombatUtils.getShipsWithinRange(missile.getLocation(),500f))
		{
			if(potentialTarget.getOwner() != missile.getOwner() && MathUtils.getDistance(missile,potentialTarget) < 500f && !CollisionUtils.isPointWithinBounds(missile.getLocation(),launchingShip))
			{
				missile.setArmingTime(0f);
				CombatFleetManagerAPI cfm = engine.getFleetManager(1);
				cfm.setSuppressDeploymentMessages(true);
				ShipAPI pod = cfm.spawnShipOrWing("armaa_assaultPod_hull_pa",missile.getLocation(),0f);
				pod.setFacing(missile.getFacing());
				pod.getVelocity().set(missile.getVelocity());
				pod.setOwner(missile.getSource().getOriginalOwner());
				pod.setOriginalOwner(missile.getSource().getOriginalOwner());
				pod.getMutableStats().getFighterRefitTimeMult().modifyPercent(pod.getId(),9999f);
				
				cfm.setSuppressDeploymentMessages(false);				
				break;
			}
		}
        
        if(missile.isArmed())
		{
            engine.removeEntity(missile);
        }
    }

    @Override
    public CombatEntityAPI getTarget() {
        return target;
    }

    @Override
    public void setTarget(CombatEntityAPI target) {
        this.target = target;
    }
}