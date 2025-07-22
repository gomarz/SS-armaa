package data.scripts.weapons;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.combat.*;
import data.scripts.ai.armaa_kineticGrenadeEffect;


/**

 */
 

public class armaa_kineticGrenadeEF extends BaseCombatLayeredRenderingPlugin implements OnFireEffectPlugin,
																					EveryFrameWeaponEffectPlugin {
private static final Color AFTERIMAGE_COLOR = new Color(255,165,90,155);
private static final float AFTERIMAGE_THRESHOLD = 0.4f;
private static final List<DamagingProjectileAPI> FIST_TRACKER = new ArrayList<>();
    private static final Color MUZZLE_FLASH_COLOR = new Color(100, 200, 255, 50);
    private static final Color MUZZLE_FLASH_COLOR_ALT = new Color(255, 255, 255, 100);
    private static final Color MUZZLE_FLASH_COLOR_GLOW = new Color(0, 75, 255, 50);
	

	public armaa_kineticGrenadeEF() {
	}

	public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{
		
		 
	}
	

	
	public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) 
	{

		engine.addPlugin(new armaa_kineticGrenadeEffect(projectile, weapon.getShip()))	;	
		
	}
}




