package data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.Global;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lazywizard.lazylib.VectorUtils;
import com.fs.starfarer.api.util.IntervalUtil;
import java.awt.Color;
import com.fs.starfarer.api.combat.DamageType;


public class armaa_assaultPodKiller extends BaseHullMod {

	public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {

	}
	
	public String getDescriptionParam(int index, HullSize hullSize) 
	{
		return "";
	}

    //Built-in only
    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return false;
    }

	@Override
	public boolean affectsOPCosts() {
		return true;
	}

    @Override
    public void advanceInCombat(ShipAPI ship, float amount)
    {
		ship.setHitpoints(ship.getHitpoints()-amount*6);
		for(ShipAPI target :CombatUtils.getShipsWithinRange(ship.getLocation(), 1000f))
		{
			if(ship.getOwner() == target.getOwner())
				continue;
			Global.getCombatEngine().headInDirectionWithoutTurning(ship, VectorUtils.getAngle(ship.getLocation(),target.getLocation()), ship.getMaxSpeed());
			break;
		}
		boolean justDie = Global.getCombatEngine().getCustomData().get("armaa_killPod_" + ship.getId()) != null;
		IntervalUtil dieTimer = null;
		if((ship.getSharedFighterReplacementRate() < 0.95f || ship.getHullLevel() < 0.50f) && !justDie)
		{
			Global.getCombatEngine().getCustomData().put("armaa_killPod_"+ship.getId(),new IntervalUtil(1f,3f));
		}
		else if(justDie)
		{
			dieTimer = (IntervalUtil)Global.getCombatEngine().getCustomData().get("armaa_killPod_"+ship.getId());
			dieTimer.advance(amount);
			//do jitter
			float elapsed = dieTimer.getElapsed();
			float maxInterval = dieTimer.getMaxInterval();			
			float dist = elapsed / maxInterval;
			float jitterFraction = dist;
			jitterFraction = Math.max(jitterFraction, dist);
			float jitterMax = 1f + 10f * jitterFraction;
			ship.setJitter(this, new Color(255,100,50, (int)(25 + 50 * jitterFraction)), 1f, 10, 1f, jitterMax);			
		}
		
		if(dieTimer != null && dieTimer.intervalElapsed())
		{
			Global.getCombatEngine().applyDamage(ship,ship.getLocation(),100000f,DamageType.ENERGY,0f,true,false,ship,false);
		}
		if(Global.getCombatEngine().isCombatOver() || Global.getCombatEngine().getFleetManager(0).getTaskManager(false).isInFullRetreat() || Global.getCombatEngine().isEnemyInFullRetreat())
		{
			ship.setHitpoints(0f);
		}
    }	

}
