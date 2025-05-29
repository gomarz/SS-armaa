package data.scripts.weapons;

import com.fs.starfarer.api.util.Misc;
import org.magiclib.util.MagicLensFlare;
import com.fs.starfarer.api.combat.*;
import org.lwjgl.util.vector.Vector2f;
import org.lazywizard.lazylib.combat.CombatUtils;
import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import org.lazywizard.lazylib.MathUtils;
import org.magiclib.util.MagicRender;
import java.awt.Color;


public class armaa_drillEffect extends BaseCombatLayeredRenderingPlugin implements OnFireEffectPlugin,
																					OnHitEffectPlugin,
																					EveryFrameWeaponEffectPlugin {

    private final Color PARTICLE_COLOR = new Color(200, 200, 200);
    private final float PARTICLE_SIZE = 8f;
    private final float PARTICLE_BRIGHTNESS = 150;
    private final float PARTICLE_DURATION = 1f;
	private static final int PARTICLE_COUNT = 2;
    
    private final float FLASH_SIZE = 5f;
    private final Color FLASH_COLOR = new Color(255, 255, 255);
    
    private final float GLOW_SIZE = 10f;
	private static final float CONE_ANGLE = 150f;
    private static final float A_2 = CONE_ANGLE / 2;
	
	// constant that effects the lower end of the particle velocity
    private static final float VEL_MIN = 0.5f;
    // constant that effects the upper end of the particle velocity
    private static final float VEL_MAX = 1f
	;
    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{


    }
	
		
	public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) 
	{
		//if we hit something, we don't want to increment recoil
		//lets keep it in memory i guess
		engine.getCustomData().put("armaa_drillHit_"+projectile.getSource().getId(),"-");
		if(target instanceof ShipAPI)
		{
			CombatUtils.applyForce(target, target.getFacing()-180f, projectile.getSource().getMass()/4);   
		}		
        if(MagicRender.screenCheck(0.1f, point))
		{
			if(Math.random() > 0.30f)
			{
				engine.addNebulaSmokeParticle(projectile.getLocation(),
					new Vector2f(),
					 40f * (0.75f + (float) Math.random() * 0.5f),
					5f + 3f * Misc.getHitGlowSize(75f, projectile.getDamage().getBaseDamage(), damageResult) / 100f,
					0f,
					0f,
					1f,
					new Color(175,100,100,255));			
				// Spawn visual effects
				//flash
				MagicLensFlare.createSmoothFlare(
						engine,
						projectile.getSource(),
						point, 
						15, 
						400,
						0,
						new Color(255,150,75),
						Color.DARK_GRAY
				);
				
				//cloud
				for(int i=0; i<30*Math.random(); i++)
				{
					int grey=MathUtils.getRandomNumberInRange(20, 100);
					engine.addSmokeParticle(
							MathUtils.getRandomPointInCircle(point, 40),
							MathUtils.getRandomPointInCone(
									new Vector2f(),
									30,
									projectile.getFacing()+90,
									projectile.getFacing()+270
							),
							MathUtils.getRandomNumberInRange(30, 60),
							1,
							MathUtils.getRandomNumberInRange(2, 5),
							new Color(
									(int)grey/10,
									(int)grey,
									(int)(grey/MathUtils.getRandomNumberInRange(1.5f, 2)),
									MathUtils.getRandomNumberInRange(8, 32)
							)
					);
				}
				
				for (int x = 0; x < 10*Math.random(); x++) 
				{
					engine.addHitParticle(
							point, 
							MathUtils.getRandomPointInCone(new Vector2f(), x*10, projectile.getFacing()+90, projectile.getFacing()+270),
							5f,
							1f,
							2-(x/10),
							new Color(255,125,20,200));
				}
				if(Math.random() > 0.40f)
				{
					engine.addSmoothParticle(point, new Vector2f(), 50f*(float)Math.random(), 0.8f, 0.25f, new Color(200, 55, 200, 255)); 
					engine.addHitParticle(point, new Vector2f(), GLOW_SIZE + (float)Math.random()*5, 1, 0.1f, PARTICLE_COLOR); 				
				}
			}
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
                        PARTICLE_COLOR);
            }

        }         
		
	}
	
    private static final Color MUZZLE_FLASH_COLOR = new Color(250, 200, 0, 255);
    private static final Color MUZZLE_FLASH_COLOR_ALT = new Color(250, 100, 0, 100);
    private static final Color MUZZLE_FLASH_COLOR_GLOW = new Color(255, 0, 0, 50);
    private static final float MUZZLE_FLASH_DURATION = 0.15f;
    private static final float MUZZLE_FLASH_SIZE = 5.0f;		
	public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) 
	{
		ShipAPI source = weapon.getShip();
		CombatEntityAPI target1 = null;
		CombatUtils.applyForce(weapon.getShip(), weapon.getShip().getFacing()-180f, source.getMass());
		if(MagicRender.screenCheck(0.2f, projectile.getLocation()))
		{
			//engine.addSmoothParticle(projectile.getLocation(), new Vector2f(0,0), 100, 2, 0.1f, Color.white);
			engine.addSmoothParticle(projectile.getLocation(), new Vector2f(), 75f, 0.5f, 0.1f, MUZZLE_FLASH_COLOR_GLOW);
			engine.addHitParticle(projectile.getLocation(), new Vector2f(), 50, 0.5f, 0.1f, Color.white);
			for (int x = 0; x < 8; x++) 
			{
				engine.addHitParticle(projectile.getLocation(),
						MathUtils.getPointOnCircumference(null, MathUtils.getRandomNumberInRange(100f, 200f), 
						MathUtils.getRandomNumberInRange(projectile.getFacing()-45f,projectile.getFacing()+45f)),
						6f, 1f, MathUtils.getRandomNumberInRange(0.1f, 0.6f), MUZZLE_FLASH_COLOR);
			}
			
		}
		
		if (Math.random() > 0.75) {
			engine.spawnExplosion(projectile.getLocation(), projectile.getVelocity(), MUZZLE_FLASH_COLOR, MUZZLE_FLASH_SIZE * 0.10f, MUZZLE_FLASH_DURATION);
		} else {
			engine.spawnExplosion(projectile.getLocation(), projectile.getVelocity(), MUZZLE_FLASH_COLOR, MUZZLE_FLASH_SIZE, MUZZLE_FLASH_DURATION);
		}
		engine.addSmoothParticle(projectile.getLocation(), projectile.getVelocity(), MUZZLE_FLASH_SIZE * 2f, 1f, MUZZLE_FLASH_DURATION * 2f, MUZZLE_FLASH_COLOR_GLOW);
	
	}

}