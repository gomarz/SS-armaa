package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lwjgl.util.vector.Vector2f;
import org.lazywizard.lazylib.combat.CombatUtils;
import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.MathUtils;
import org.magiclib.util.MagicRender;
import data.scripts.weapons.armaa_vajraProjectileScript;

import java.util.Random;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;


public class armaa_valkazardGunEffect extends BaseCombatLayeredRenderingPlugin implements OnFireEffectPlugin,
																					OnHitEffectPlugin,
																					EveryFrameWeaponEffectPlugin {

    private int lastAmmo = 0;
	private boolean soundPlayed = false;
    private boolean windingUp = true;
    private IntervalUtil cockSound = new IntervalUtil(0.33f,0.38f);
    private IntervalUtil reloadTime = new IntervalUtil(2.8f,2.85f);
	private DamagingProjectileAPI  dummy;
    private final Color PARTICLE_COLOR = new Color(200, 55, 200);
    private final float PARTICLE_SIZE = 4f;
    private final float PARTICLE_BRIGHTNESS = 150;
    private final float PARTICLE_DURATION = 1f;
	private static final int PARTICLE_COUNT = 2;

    private final float EXPLOSION_SIZE = 15f;      
    private final Color EXPLOSION_COLOR = new Color(200, 50, 200);
    
    private final float FLASH_SIZE = 5f;
    private final Color FLASH_COLOR = new Color(255, 166, 239);
    
    private final float GLOW_SIZE = 10f;
	private static final float CONE_ANGLE = 150f;
    private static final float A_2 = CONE_ANGLE / 2;
	
	// constant that effects the lower end of the particle velocity
    private static final float VEL_MIN = 0.5f;
    // constant that effects the upper end of the particle velocity
    private static final float VEL_MAX = 1f;
	
	@Override
	public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{


	}	
	
	public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) 
	{
	         
        if(MagicRender.screenCheck(0.1f, point)){
			
			engine.addSmoothParticle(point, new Vector2f(), 50f, 0.8f, 0.25f, projectile.getProjectileSpec().getCoreColor()); 
			engine.addHitParticle(point, new Vector2f(), GLOW_SIZE + (float)Math.random()*5, 1, 0.1f, projectile.getProjectileSpec().getFringeColor()); 				


			float speed = 300;
            float facing = projectile.getFacing();
            for (int i = 0; i <= PARTICLE_COUNT; i++)
            {
                float angle = MathUtils.getRandomNumberInRange(facing - A_2,
                        facing + A_2);
                float vel = MathUtils.getRandomNumberInRange(speed * -VEL_MIN,
                        speed * -VEL_MAX);
                Vector2f vector = MathUtils.getPointOnCircumference(null,
                        vel,
                        angle);
                engine.addHitParticle(point,
                        vector,
                        PARTICLE_SIZE,
                        PARTICLE_BRIGHTNESS,
                        PARTICLE_DURATION,
                        projectile.getProjectileSpec().getFringeColor());
            }

        }         
		
	}
	
    private static final float MUZZLE_FLASH_DURATION = 0.15f;
    private static final float MUZZLE_FLASH_SIZE = 5.0f;		
	public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) 
	{
		ShipAPI source = weapon.getShip();
		CombatEntityAPI target1 = null;
		
		if(MagicRender.screenCheck(0.2f, projectile.getLocation()))
		{
			
			//engine.addSmoothParticle(projectile.getLocation(), new Vector2f(0,0), 100, 2, 0.1f, Color.white);
			engine.addSmoothParticle(projectile.getLocation(), new Vector2f(), 75f, 0.5f, 0.1f, projectile.getProjectileSpec().getFringeColor());
			engine.addHitParticle(projectile.getLocation(), new Vector2f(), 50, 0.5f, 0.1f, Color.white);
			for (int x = 0; x < 4; x++) 
			{
				engine.addHitParticle(projectile.getLocation(),
						MathUtils.getPointOnCircumference(null, MathUtils.getRandomNumberInRange(100f, 200f), 
						MathUtils.getRandomNumberInRange(projectile.getFacing()-45f,projectile.getFacing()+45f)),
						6f, 1f, MathUtils.getRandomNumberInRange(0.1f, 0.6f), projectile.getProjectileSpec().getFringeColor());
			}
			
		}
		
		if (Math.random() > 0.75) {
			engine.spawnExplosion(projectile.getLocation(), projectile.getVelocity(), projectile.getProjectileSpec().getCoreColor(), MUZZLE_FLASH_SIZE * 0.10f, MUZZLE_FLASH_DURATION);
		} else {
			engine.spawnExplosion(projectile.getLocation(), projectile.getVelocity(), projectile.getProjectileSpec().getFringeColor(), MUZZLE_FLASH_SIZE, MUZZLE_FLASH_DURATION);
		}
		engine.addSmoothParticle(projectile.getLocation(), projectile.getVelocity(), MUZZLE_FLASH_SIZE * 2f, 1f, MUZZLE_FLASH_DURATION * 2f, projectile.getProjectileSpec().getFringeColor());
	
	}

}