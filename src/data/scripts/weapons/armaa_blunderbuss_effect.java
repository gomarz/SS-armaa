package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lwjgl.util.vector.Vector2f;
import java.util.ArrayList;
import java.util.List;
import java.awt.Color;
import org.lazywizard.lazylib.VectorUtils;
import org.magiclib.util.MagicRender;
import org.lazywizard.lazylib.MathUtils;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;

public class armaa_blunderbuss_effect implements EveryFrameWeaponEffectPlugin, OnFireEffectPlugin, OnHitEffectPlugin {

    public static final int SHOTS = 8;
    public static final float SPREAD = 15f;
	public static final float SPREAD_EXTRA_PROJS = 10f;
    public static final float PUSH_CONSTANT = 12800f;
    private int lastAmmo = 0;
    private boolean init = false;
    private boolean reloading = true;
    private IntervalUtil cockSound = new IntervalUtil(0.33f,0.38f);
    private IntervalUtil reloadTime = new IntervalUtil(2.8f,2.85f);
	private DamagingProjectileAPI  dummy;
	private List<DamagingProjectileAPI> registeredProjectiles = new ArrayList<DamagingProjectileAPI>();
    private static final Color MUZZLE_FLASH_COLOR = new Color(255, 200, 100, 50);
    private static final Color MUZZLE_FLASH_COLOR_ALT = new Color(255, 255, 255, 100);
    private static final Color MUZZLE_FLASH_COLOR_GLOW = new Color(255, 75, 0, 50);
    private static final float MUZZLE_FLASH_DURATION = 0.20f;
    private static final float MUZZLE_FLASH_SIZE = 30.0f;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{
        if (!init){
            init = true;
            lastAmmo = weapon.getAmmo();
        }

		// for sound fx
		if (weapon.getAmmo() > 0)
			reloading = true;
		else
			reloadTime.setElapsed(0f);
	//}

        // cock sound plays when first shell is loaded after reaching 0 ammo
        if (lastAmmo == 0 && weapon.getAmmo() > 0){
            Global.getSoundPlayer().playSound("armaa_shotgun_cock", 0.5f, 1f, weapon.getLocation(), weapon.getShip().getVelocity());
            weapon.setRemainingCooldownTo(1f);
        }

        lastAmmo = weapon.getAmmo();

        // cock sound plays about 0.35 sec after firing if there is at least 1 ammo
        if (reloading)
		{
            cockSound.advance(amount);
            if (cockSound.intervalElapsed()) {
                reloading = false;
              //  Global.getSoundPlayer().playSound("KT_boomstick_cock", 0.5f, 2.8f, weapon.getLocation(), weapon.getShip().getVelocity());
            }
        }
    }
	
	public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) 
	{
			
		if (Math.random() > 0.75) {
			engine.spawnExplosion(projectile.getLocation(), projectile.getVelocity(), MUZZLE_FLASH_COLOR, MUZZLE_FLASH_SIZE * 0.10f, MUZZLE_FLASH_DURATION);
		} else {
			engine.spawnExplosion(projectile.getLocation(), projectile.getVelocity(), MUZZLE_FLASH_COLOR, MUZZLE_FLASH_SIZE, MUZZLE_FLASH_DURATION);
		}
		engine.addSmoothParticle(projectile.getLocation(), projectile.getVelocity(), MUZZLE_FLASH_SIZE * 3f, 1f, MUZZLE_FLASH_DURATION * 2f, MUZZLE_FLASH_COLOR_GLOW);
			
		for (int f = 0; f < SHOTS; f++){
			float offset = ((float)Math.random() * SPREAD) - ((float)Math.random() * SPREAD * 0.5f);
			float angle = weapon.getCurrAngle() + offset;
			String weaponId = "armaa_blunderbuss";
			Vector2f muzzleLocation = weapon.getSpec().getTurretFireOffsets().get(0); 
			DamagingProjectileAPI proj = (DamagingProjectileAPI)engine.spawnProjectile(weapon.getShip(),weapon,weaponId,projectile.getLocation(),angle,weapon.getShip().getVelocity());
			float randomSpeed = (float)Math.random() * 0.4f + 0.8f;
			proj.getVelocity().scale(randomSpeed);
		}
		
		
		Global.getCombatEngine().removeEntity(projectile);
		
		
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