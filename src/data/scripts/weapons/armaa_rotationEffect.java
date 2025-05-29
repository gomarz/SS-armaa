package data.scripts.weapons;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.Global;


public class armaa_rotationEffect implements EveryFrameWeaponEffectPlugin {

	public float angle = 0f;


    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{
		
		if(Global.getCombatEngine().isPaused())
			return;
		
		weapon.setCurrAngle(angle);
		angle+=0.5f;
			
    }
}
