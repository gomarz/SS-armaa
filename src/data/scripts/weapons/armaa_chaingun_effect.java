package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lwjgl.util.vector.Vector2f;
import org.lazywizard.lazylib.combat.CombatUtils;
import java.util.ArrayList;
import java.util.List;
import java.awt.Color;
import org.lazywizard.lazylib.VectorUtils;
import org.magiclib.util.MagicRender;
import org.lazywizard.lazylib.MathUtils;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;

public class armaa_chaingun_effect implements EveryFrameWeaponEffectPlugin, OnFireEffectPlugin, OnHitEffectPlugin {

 
    private static final Color MUZZLE_FLASH_COLOR = new Color(255, 200, 100, 50);
    private static final Color MUZZLE_FLASH_COLOR_ALT = new Color(255, 255, 255, 100);
    private static final Color MUZZLE_FLASH_COLOR_GLOW = new Color(255, 75, 0, 50);
    private static final float MUZZLE_FLASH_DURATION = 0.10f;
    private static final float MUZZLE_FLASH_SIZE = 8.0f;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{

    }
	
	public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) 
	{
			float angle = 180+weapon.getCurrAngle();
			Vector2f point= MathUtils.getPoint(weapon.getFirePoint(0), 0, angle);
		
		for (int i = 0; i < 3; i++) 
		{
			Vector2f vel = MathUtils.getRandomPointInCone(new Vector2f(), 50, angle-10, angle+10);
			vel.scale((float)Math.random());
			Vector2f.add(vel, weapon.getShip().getVelocity(), vel);
			float grey = MathUtils.getRandomNumberInRange(0.1f, 1f);
			engine.addSmokeParticle(
					MathUtils.getRandomPointInCircle(point, 5),
					vel,
					MathUtils.getRandomNumberInRange(5, 20),
					MathUtils.getRandomNumberInRange(0.25f, 0.75f),
					MathUtils.getRandomNumberInRange(0.25f, 1f),
					new Color(.75f,.75f,.75f,MathUtils.getRandomNumberInRange(0.5f, 0.75f))
			);
		}
			
		if (Math.random() > 0.75) {
			engine.spawnExplosion(projectile.getLocation(), weapon.getShip().getVelocity(), MUZZLE_FLASH_COLOR, MUZZLE_FLASH_SIZE * 0.10f, MUZZLE_FLASH_DURATION);
		} else {
			engine.spawnExplosion(projectile.getLocation(), weapon.getShip().getVelocity(), MUZZLE_FLASH_COLOR, MUZZLE_FLASH_SIZE, MUZZLE_FLASH_DURATION);
		}
		engine.addSmoothParticle(projectile.getLocation(), weapon.getShip().getVelocity(), MUZZLE_FLASH_SIZE * 3f, 1f, MUZZLE_FLASH_DURATION * 2f, MUZZLE_FLASH_COLOR_GLOW);
			
	}
	
    private final Color PARTICLE_COLOR = new Color(255, 0, 255);
    private final float PARTICLE_SIZE = 3f;
    private final float PARTICLE_BRIGHTNESS = 150f;
    private final float PARTICLE_DURATION = 1f;
	private static final int PARTICLE_COUNT = 1;
	private final int max = 40;
	private final int min = 10;
    private final Color EMP_COLOR = new Color(250, 150, 250);
	    // constant that effects the lower end of the particle velocity
    private static final float VEL_MIN = 0.1f;
    // constant that effects the upper end of the particle velocity
    private static final float VEL_MAX = 0.15f;
	    // one half of the angle. used internally, don't mess with thos
	private static final float CONE_ANGLE = 150f;
    private static final float A_2 = CONE_ANGLE / 2;
	
	@Override
	public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine)  
	{
		
		if(MagicRender.screenCheck(0.2f, point))
		{
				engine.addSmoothParticle(projectile.getLocation(), new Vector2f(0,0), 60, 2, 0.10f, Color.white);
				engine.addHitParticle(projectile.getLocation(), new Vector2f(0,0), 50, 1, 0.4f, MUZZLE_FLASH_COLOR_GLOW);
				engine.spawnExplosion(projectile.getLocation(), new Vector2f(0,0), Color.BLACK, 60, 2);
			
			float speed = projectile.getVelocity().length();
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
						MUZZLE_FLASH_COLOR);
			}
			
		}
				
				
    }   

    private Vector2f calculateMuzzle(WeaponAPI weapon){
        float muzzle;
				Vector2f origin = new Vector2f(weapon.getLocation());
				Vector2f offset = new Vector2f(5f, -15f);
				VectorUtils.rotate(offset, weapon.getCurrAngle(), offset);
				Vector2f.add(offset, origin, origin);
				return origin;

    }
}