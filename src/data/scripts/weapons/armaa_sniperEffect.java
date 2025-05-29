package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;

public class  armaa_sniperEffect implements EveryFrameWeaponEffectPlugin {

	private float speedMalus = 0.60f;
 
    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{
		
		weapon.getShip().getMutableStats().getMaxSpeed().modifyMult(weapon.getShip().getId()+"armaa_sniper",1-(speedMalus*weapon.getChargeLevel())); 

    }

    private static int clamp255(int x) {
        return Math.max(0, Math.min(255, x));
    }
}
