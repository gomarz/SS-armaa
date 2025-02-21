package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.ShipAPI;

public class armaa_spinEffect implements EveryFrameWeaponEffectPlugin {

	private float spinSpeed = 1f;
	private float counterSpeed = 0f;	
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{
        if(engine.isPaused()) 
            return;
		
		ShipAPI ship = weapon.getShip();
		
		if(!ship.isAlive())
			return;

        float curr = weapon.getCurrAngle();
		if(ship.getFluxTracker().isVenting())
			counterSpeed-= amount;
		else
			counterSpeed+= counterSpeed >= 1f ? 0 : amount;
        curr += spinSpeed + Math.max(-1f,counterSpeed);
		

        weapon.setCurrAngle(curr);
	}
}
