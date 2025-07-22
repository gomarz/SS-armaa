package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;
import org.lazywizard.lazylib.MathUtils;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import java.util.*;
import com.fs.starfarer.api.util.IntervalUtil;

public class armaa_fighterStrafe extends BaseHullMod 
{
    private final IntervalUtil interval = new IntervalUtil(1f, 10f);
    @Override
    public void advanceInCombat(ShipAPI ship, float amount)
    {
		float range = 0f;
		ShipCommand strafeDir = ShipCommand.STRAFE_RIGHT;
		interval.advance(amount);
		if(interval.intervalElapsed())
		{
			Global.getCombatEngine().getCustomData().remove("armaa_fighterStrafeRange"+ship.getId());
			return;
		}		
		if(Global.getCombatEngine().getCustomData().get("armaa_fighterStrafeRange"+ship.getId()) != null)
		{
			range = (float)Global.getCombatEngine().getCustomData().get("armaa_fighterStrafeRange"+ship.getId());
			strafeDir = (ShipCommand)Global.getCombatEngine().getCustomData().get("armaa_fighterStrafeDir"+ship.getId());
		}
		else
		{
			List<WeaponAPI> weapons = ship.getAllWeapons();
			float minRange = 9999f;
			for(WeaponAPI wep : weapons)
			{
				if(wep.isDecorative() || wep.getOriginalSpec().getAIHints().contains(AIHints.PD_ONLY))
					continue;
				if(wep.getRange() < minRange)
					minRange = wep.getRange();
			}
			Global.getCombatEngine().getCustomData().put("armaa_fighterStrafeRange"+ship.getId(),minRange);
			strafeDir = Math.random() < 0.50f ? ShipCommand.STRAFE_LEFT : ShipCommand.STRAFE_RIGHT;
			Global.getCombatEngine().getCustomData().put("armaa_fighterStrafeDir"+ship.getId(),strafeDir);
			range = minRange-Math.min(range/2,((float)Math.random()*(range)));
			
		}
		if(ship.isFighter())
		{
			if(ship.getShipTarget() != null && MathUtils.getDistance(ship,ship.getShipTarget()) <= range-Math.random()*(range/2))
			{	
				//ship.giveCommand(ShipCommand.ACCELERATE_BACKWARDS,null,0);
				ship.giveCommand(strafeDir,null,0);	
				if(MathUtils.getDistance(ship,ship.getShipTarget()) <= range/2)
				{
					ship.blockCommandForOneFrame(ShipCommand.ACCELERATE);
					if(Math.random() < 0.20f)
					ship.giveCommand(ShipCommand.DECELERATE,null,0);						
				}					
			}
		}
	}		
}
