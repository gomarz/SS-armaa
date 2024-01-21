
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
import org.magiclib.util.MagicLensFlare;
import org.magiclib.util.MagicInterference;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;


public class armaa_curvyEffect implements EveryFrameWeaponEffectPlugin, OnFireEffectPlugin, OnHitEffectPlugin {
    private boolean runOnce = false;
    private boolean hasFired = false;
	public float TURRET_OFFSET = 45f;
    private final IntervalUtil particle = new IntervalUtil(0.025f, 0.05f);
	private final IntervalUtil particle2 = new IntervalUtil(.1f, .2f);
	
	float charge = 0f;
	private static final int SMOKE_SIZE_MIN = 10;
    private static final int SMOKE_SIZE_MAX = 30;

    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {

		TURRET_OFFSET = weapon.getSpec().getTurretFireOffsets().get(0).x;
		float OFFSET_Y = weapon.getSpec().getTurretFireOffsets().get(0).y;
		if(weapon.getShip().getOriginalOwner()<0 && !weapon.getSlot().isBuiltIn())
		{
			MagicInterference.ApplyInterference(weapon.getShip().getVariant());
		}
		if(weapon.getChargeLevel() > 0f && weapon.getCooldownRemaining() > 0f)
		{
			particle2.advance(amount);
			if (particle2.intervalElapsed()) 
			{
				Vector2f origin = new Vector2f(weapon.getLocation());
				Vector2f offset = new Vector2f(TURRET_OFFSET,OFFSET_Y);
				VectorUtils.rotate(offset, weapon.getCurrAngle(), offset);
				Vector2f.add(offset, origin, origin);
				float size = MathUtils.getRandomNumberInRange(SMOKE_SIZE_MIN , SMOKE_SIZE_MAX);
				float x = MathUtils.getRandomNumberInRange(-20,20);
				float y = MathUtils.getRandomNumberInRange(-20,20);
				Global.getCombatEngine().addSmokeParticle(origin, new Vector2f(x,y), size*weapon.getChargeLevel(), 1f*weapon.getChargeLevel(), 1f*weapon.getChargeLevel(), Color.gray);
				origin = new Vector2f(weapon.getLocation());
				offset = new Vector2f(TURRET_OFFSET,OFFSET_Y);
				VectorUtils.rotate(offset, weapon.getCurrAngle(), offset);
				Vector2f.add(offset, origin, origin);
				size = MathUtils.getRandomNumberInRange(SMOKE_SIZE_MIN , SMOKE_SIZE_MAX);
				x = MathUtils.getRandomNumberInRange(-50,50);
				y = MathUtils.getRandomNumberInRange(-50,50);				
				Global.getCombatEngine().addSmokeParticle(weapon.getLocation(), new Vector2f(x,y), size*weapon.getChargeLevel(), 1f*weapon.getChargeLevel(), 1f*weapon.getChargeLevel(), Color.gray);
			}
		}
        if (weapon.isFiring() && charge != 1f) {
            float charge = weapon.getChargeLevel();
			float size = 1;
			if(weapon.getSize() ==  WeaponAPI.WeaponSize.LARGE)
				size = 2;
            if (!hasFired) {
				
                    Global.getSoundPlayer().playLoop("beamchargeM", (Object)weapon, 1.0f, 1.0f, weapon.getLocation(), weapon.getShip().getVelocity());
                    particle.advance(amount);
                if (particle.intervalElapsed()) {
					
					Vector2f origin = new Vector2f(weapon.getLocation());
					Vector2f offset = new Vector2f(TURRET_OFFSET,OFFSET_Y);
					VectorUtils.rotate(offset, weapon.getCurrAngle(), offset);
					Vector2f.add(offset, origin, origin);
                    Vector2f loc = MathUtils.getPoint((Vector2f)weapon.getLocation(), (float)18.5f, (float)weapon.getCurrAngle());
                    Vector2f vel = weapon.getShip().getVelocity();
                    engine.addHitParticle(origin, vel, MathUtils.getRandomNumberInRange((float)20.0f*size, (float)(charge*size * 60.0f + 20.0f)), MathUtils.getRandomNumberInRange((float)0.5f, (float)(0.5f + charge)), MathUtils.getRandomNumberInRange((float)0.1f, (float)(0.1f + charge / 10.0f)), new Color(charge, charge, charge));
					engine.addSwirlyNebulaParticle(origin, new Vector2f(0.0f, 0.0f),MathUtils.getRandomNumberInRange((float)20.0f*size, (float)(charge*size * 60.0f + 20.0f)), 1.2f, 0.15f, 0.0f, 0.35f*charge, new Color(0, charge / 4.0f, charge),false);

					Vector2f particleVel = MathUtils.getRandomPointInCircle((Vector2f)new Vector2f(), (float)(35.0f * charge));
					Vector2f particleLoc = new Vector2f();
					Vector2f.sub((Vector2f)origin, (Vector2f)new Vector2f((ReadableVector2f)particleVel), (Vector2f)particleLoc);
					Vector2f.add((Vector2f)vel, (Vector2f)particleVel, (Vector2f)particleVel);
					//if((float) Math.random() <= 0.5f)	
					//MagicLensFlare.createSharpFlare(engine, weapon.getShip(), particleLoc, 2, 6, 0, new Color(50f/255f, 150f/255f, 1f,1f*charge), new Color(1f, 1f, 1f,1f*charge));

                    for (int i = 0; i < 5; ++i) {
						 engine.addHitParticle(particleLoc, particleVel, MathUtils.getRandomNumberInRange((float)2.0f, (float)(charge * 2.0f + 2.0f)), MathUtils.getRandomNumberInRange((float)0.5f, (float)(0.5f + charge)), MathUtils.getRandomNumberInRange((float)0.75f, (float)(0.75f + charge / 4.0f)), new Color(charge / 4.0f, charge / 2.0f, charge));
                    }
                }
            }
            if (charge == 1.0f) 
			{
                hasFired = true;
            }
        } else {
            hasFired = false;
		}
	}
    private static final Color MUZZLE_FLASH_COLOR = new Color(100, 200, 255, 50);
    private static final Color MUZZLE_FLASH_COLOR_ALT = new Color(255, 255, 255, 100);
    private static final Color MUZZLE_FLASH_COLOR_GLOW = new Color(0, 75, 255, 50);
    private static final float MUZZLE_FLASH_DURATION = 0.20f;
    private static final float MUZZLE_FLASH_SIZE = 30.0f;	
	public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) 
	{		
		if (Math.random() > 0.75) {
			engine.spawnExplosion(projectile.getLocation(), projectile.getVelocity(), MUZZLE_FLASH_COLOR, MUZZLE_FLASH_SIZE * 0.10f, MUZZLE_FLASH_DURATION);
		} else {
			engine.spawnExplosion(projectile.getLocation(), projectile.getVelocity(), MUZZLE_FLASH_COLOR, MUZZLE_FLASH_SIZE, MUZZLE_FLASH_DURATION);
		}
		engine.addSmoothParticle(projectile.getLocation(), projectile.getVelocity(), MUZZLE_FLASH_SIZE * 3f, 1f, MUZZLE_FLASH_DURATION * 2f, MUZZLE_FLASH_COLOR_GLOW);
					
	}

    private static final Color PARTICLE_COLOR = new Color(50, 150, 255, 174);
    private static final Color BLAST_COLOR = new Color(255, 16, 16, 255);
    private static final Color CORE_COLOR = new Color(119, 194, 255);
    private static final Color FLASH_COLOR = new Color(152, 225, 255);
    private static final int NUM_PARTICLES = 30;
	//@Override
	public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine)  
	{
        engine.spawnExplosion(point, new Vector2f(), PARTICLE_COLOR, 150f, 1f);
        engine.spawnExplosion(point, new Vector2f(), CORE_COLOR, 75f, 1f);
		MagicLensFlare.createSharpFlare(engine, projectile.getSource(), projectile.getLocation(), 8, 400, 0, new Color(186, 240, 255), new Color(255, 255, 255));
        float bonusDamage = projectile.getDamageAmount()/10f;
        DamagingExplosionSpec blast = new DamagingExplosionSpec(0.1f,
                75f,
                25f,
                bonusDamage,
                bonusDamage/2f,
                CollisionClass.PROJECTILE_FF,
                CollisionClass.PROJECTILE_FIGHTER,
                10f,
                10f,
                0f,
                0,
                BLAST_COLOR,
                null);
        blast.setDamageType(DamageType.FRAGMENTATION);
        blast.setShowGraphic(false);
        engine.spawnDamagingExplosion(blast,projectile.getSource(),point,false);
		
		if(MagicRender.screenCheck(0.2f, point))
		{
			
			engine.addSmoothParticle(projectile.getLocation(), new Vector2f(0,0), 200, 2, 0.1f, Color.white);
			engine.addSmoothParticle(point, new Vector2f(), 400f, 0.5f, 0.1f, PARTICLE_COLOR);
			engine.addHitParticle(point, new Vector2f(), 200f, 0.5f, 0.25f, FLASH_COLOR);
			for (int x = 0; x < NUM_PARTICLES; x++) 
			{
				engine.addHitParticle(point,
						MathUtils.getPointOnCircumference(null, MathUtils.getRandomNumberInRange(150f, 300f), (float) Math.random() * 360f),
						5f, 1f, MathUtils.getRandomNumberInRange(0.6f, 1f), PARTICLE_COLOR);
			}	
		}	
    }   
}