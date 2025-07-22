package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import org.lazywizard.lazylib.MathUtils;
import org.magiclib.util.MagicRender;
import data.scripts.weapons.armaa_vajraProjectileScript;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;


public class armaa_VajraEveryFrameEffect extends BaseCombatLayeredRenderingPlugin implements OnFireEffectPlugin,
																					OnHitEffectPlugin,
																					EveryFrameWeaponEffectPlugin {

    private int lastAmmo = 0;
	private boolean soundPlayed = false;
    private boolean windingUp = true;
    private IntervalUtil cockSound = new IntervalUtil(0.33f,0.38f);
    private IntervalUtil reloadTime = new IntervalUtil(2.8f,2.85f);
	private DamagingProjectileAPI  dummy;
    private List<DamagingProjectileAPI> alreadyRegisteredProjectiles = new ArrayList<DamagingProjectileAPI>();

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{


    }
	
		
	public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) 
	{
      
		
	}
	
    private static final Color MUZZLE_FLASH_COLOR = new Color(250, 146, 0, 255);
    private static final Color MUZZLE_FLASH_COLOR_ALT = new Color(255, 255, 255, 100);
    private static final Color MUZZLE_FLASH_COLOR_GLOW = new Color(255, 0, 0, 50);
    private static final float MUZZLE_FLASH_DURATION = 0.20f;
    private static final float MUZZLE_FLASH_SIZE = 50.0f;		
	public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) 
	{
		ShipAPI source = weapon.getShip();
		CombatEntityAPI target1 = null;
		
		if(MagicRender.screenCheck(0.2f, projectile.getLocation()))
		{
			
			engine.addSmoothParticle(projectile.getLocation(), new Vector2f(0,0), 75, 2, 0.1f, Color.white);
			engine.addSmoothParticle(projectile.getLocation(), new Vector2f(), 100f, 0.5f, 0.1f, MUZZLE_FLASH_COLOR_GLOW);
			engine.addHitParticle(projectile.getLocation(), new Vector2f(), 75f, 0.5f, 0.25f, MUZZLE_FLASH_COLOR);
			for (int x = 0; x < 5; x++) 
			{
				engine.addHitParticle(projectile.getLocation(),
						MathUtils.getPointOnCircumference(null, MathUtils.getRandomNumberInRange(100f, 150f), (float) Math.random() * 360f),
						5f, 1f, MathUtils.getRandomNumberInRange(0.6f, 1f), MUZZLE_FLASH_COLOR);
			}	
		}
		
		if (Math.random() > 0.75) {
			engine.spawnExplosion(projectile.getLocation(), projectile.getVelocity(), MUZZLE_FLASH_COLOR, MUZZLE_FLASH_SIZE * 0.10f, MUZZLE_FLASH_DURATION);
		} else {
			engine.spawnExplosion(projectile.getLocation(), projectile.getVelocity(), MUZZLE_FLASH_COLOR, MUZZLE_FLASH_SIZE, MUZZLE_FLASH_DURATION);
		}
		engine.addSmoothParticle(projectile.getLocation(), projectile.getVelocity(), MUZZLE_FLASH_SIZE * 3f, 1f, MUZZLE_FLASH_DURATION * 2f, MUZZLE_FLASH_COLOR_GLOW);
		
		if(source.getWeaponGroupFor(weapon)!=null ){
			//WEAPON IN AUTOFIRE
			if(source.getWeaponGroupFor(weapon).isAutofiring()  //weapon group is autofiring
					&& source.getSelectedGroupAPI()!=source.getWeaponGroupFor(weapon)){ //weapon group is not the selected group
				target1 = source.getWeaponGroupFor(weapon).getAutofirePlugin(weapon).getTargetShip();
			}
			else {
				target1 = source.getShipTarget();
			}
		}
		
		if (projectile.getWeapon() == weapon && !alreadyRegisteredProjectiles.contains(projectile) && engine.isEntityInPlay(projectile) && !projectile.didDamage()) 
		{
			engine.addPlugin(new armaa_vajraProjectileScript(projectile, target1));
			alreadyRegisteredProjectiles.add(projectile);
		}
		
		List<DamagingProjectileAPI> cloneList = new ArrayList<>(alreadyRegisteredProjectiles);
		for (DamagingProjectileAPI proj : cloneList) {
			if (!engine.isEntityInPlay(proj) || proj.didDamage()) {
				alreadyRegisteredProjectiles.remove(proj);
			}
		}		
	}

}