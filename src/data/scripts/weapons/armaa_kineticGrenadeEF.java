package data.scripts.weapons;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.FaderUtil;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import data.scripts.ai.armaa_kineticGrenadeEffect;
import org.magiclib.util.MagicRender;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lazywizard.lazylib.FastTrig;
import org.magiclib.util.MagicLensFlare;


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




