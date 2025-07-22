
package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import java.awt.Color;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.ReadableVector2f;
import org.lwjgl.util.vector.Vector2f;
import org.lazywizard.lazylib.VectorUtils;
import org.magiclib.util.MagicRender;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.loading.ProjectileSpecAPI;



public class armaa_crusherEffect implements EveryFrameWeaponEffectPlugin, OnFireEffectPlugin, OnHitEffectPlugin {
    private boolean runOnce = false;
    private boolean hasFired = false;
	public float TURRET_OFFSET = 45f;
    private final IntervalUtil particle = new IntervalUtil(0.025f, 0.05f);	
	float charge = 0f;
	private static final int SMOKE_SIZE_MIN = 10;
    private static final int SMOKE_SIZE_MAX = 30;
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {


		int numbarrels = weapon.getSpec().getTurretFireOffsets().size();

	
        if (weapon.isFiring() && charge != 1f) 
		{
			ProjectileSpecAPI spec = (ProjectileSpecAPI)weapon.getSpec().getProjectileSpec();
			float r = spec.getFringeColor().getRed()/255f;
			float b = spec.getFringeColor().getBlue()/255f;
			float g = spec.getFringeColor().getGreen()/255f;
			float alpha = spec.getFringeColor().getAlpha()/255f;
			for(int i = 0; i< numbarrels; i++)
			{
				int barrel = i;
				TURRET_OFFSET = weapon.getSpec().getTurretFireOffsets().get(barrel).x;
				float OFFSET_Y = weapon.getSpec().getTurretFireOffsets().get(barrel).y;
				float charge = weapon.getChargeLevel();
				float size = 1;
				if(weapon.getSize() ==  WeaponAPI.WeaponSize.LARGE)
					size = 2;
				if (!hasFired) 
				{				
					Global.getSoundPlayer().playLoop("beamchargeM", (Object)weapon, 1.5f, 1.0f, weapon.getLocation(), weapon.getShip().getVelocity());
					particle.advance(amount);
					if (particle.intervalElapsed()) 
					{					
						Vector2f origin = new Vector2f(weapon.getLocation());
						Vector2f offset = new Vector2f(TURRET_OFFSET,OFFSET_Y);
						VectorUtils.rotate(offset, weapon.getCurrAngle(), offset);
						Vector2f.add(offset, origin, origin);
						Vector2f loc = MathUtils.getPoint((Vector2f)weapon.getLocation(), (float)18.5f, (float)weapon.getCurrAngle());
						Vector2f vel = weapon.getShip().getVelocity();
						engine.addHitParticle(origin, vel, MathUtils.getRandomNumberInRange((float)20.0f*size, (float)(charge*size * 60.0f + 20.0f)), MathUtils.getRandomNumberInRange((float)0.5f, (float)(0.5f + charge)), MathUtils.getRandomNumberInRange((float)0.1f, (float)(0.1f + charge / 10.0f)), new Color(r*charge, g*charge, b*charge,alpha*charge));

						Vector2f particleVel = MathUtils.getRandomPointInCircle((Vector2f)new Vector2f(), (float)(35.0f * charge));
						Vector2f particleLoc = new Vector2f();
						Vector2f.sub((Vector2f)origin, (Vector2f)new Vector2f((ReadableVector2f)particleVel), (Vector2f)particleLoc);
						Vector2f.add((Vector2f)vel, (Vector2f)particleVel, (Vector2f)particleVel);
						for (int j = 0; j < 5; ++j) 
						{
							 engine.addHitParticle(particleLoc, particleVel, MathUtils.getRandomNumberInRange((float)1.0f, (float)(charge * 2.0f + 1.0f)), MathUtils.getRandomNumberInRange((float)0.5f, (float)(0.5f + charge)), MathUtils.getRandomNumberInRange((float)0.75f, (float)(0.75f + charge / 4.0f)), new Color(r*charge, g*charge, b*charge,alpha*charge));
						}
					}
				}
				if (charge == 1.0f) 
				{
					hasFired = true;
					if(barrel >= 1)
						barrel = 0;
					else barrel++;
				}
			}
        } else {
            hasFired = false;
		}
		
	}

	public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) 
	{
		ProjectileSpecAPI spec = (ProjectileSpecAPI)weapon.getSpec().getProjectileSpec();
		final Color MUZZLE_FLASH_COLOR = spec.getFringeColor();
		final Color MUZZLE_FLASH_COLOR_ALT = new Color(255, 255, 255, 100);
		final Color MUZZLE_FLASH_COLOR_GLOW = spec.getGlowColor();
		final float MUZZLE_FLASH_DURATION = 0.20f;
		final float MUZZLE_FLASH_SIZE = 30.0f;			
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
					
	}

	//@Override
	public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine)  
	{
		ProjectileSpecAPI spec = (ProjectileSpecAPI)projectile.getProjectileSpec();	
		final Color PARTICLE_COLOR = spec.getFringeColor();
		final Color BLAST_COLOR = new Color(255, 16, 16, 255);
		final Color CORE_COLOR = spec.getCoreColor();
		final Color FLASH_COLOR = spec.getFringeColor();
		final int NUM_PARTICLES = 10;		

		if(MagicRender.screenCheck(0.2f, point))
		{

			engine.addNebulaParticle(projectile.getLocation(),
				new Vector2f(),
				 40f * (0.75f + (float) Math.random() * 0.5f),
				2f + 3f * Misc.getHitGlowSize(100f, projectile.getDamage().getBaseDamage(), damageResult) / 100f,
				0f,
				0f,
				1f,
				PARTICLE_COLOR,
				true);	
			
			engine.addSmoothParticle(projectile.getLocation(), new Vector2f(0,0), 75, 2, 0.1f, Color.white);
			engine.addSmoothParticle(point, new Vector2f(), 100f, 0.5f, 0.1f, PARTICLE_COLOR);
			engine.addHitParticle(point, new Vector2f(), 75f, 0.5f, 0.25f, FLASH_COLOR);
			for (int x = 0; x < NUM_PARTICLES; x++) 
			{
				engine.addHitParticle(point,
						MathUtils.getPointOnCircumference(null, MathUtils.getRandomNumberInRange(50f, 100f), (float) Math.random() * 360f),
						MathUtils.getRandomNumberInRange(1f,10f), 1f, MathUtils.getRandomNumberInRange(0.5f, 2f), PARTICLE_COLOR);
			}	
		}	
    }   
}