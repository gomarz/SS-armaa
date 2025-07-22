package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lwjgl.util.vector.Vector2f;
import java.awt.Color;


public class armaa_shotgunEveryFrameEffect implements EveryFrameWeaponEffectPlugin, OnFireEffectPlugin {

    public static final int SHOTS = 35;
    public static final float SPREAD = 15f;
    public static final float PUSH_CONSTANT = 12800f;
    private int lastAmmo = 0;
    private boolean init = false;
    private boolean reloading = true;
    private IntervalUtil cockSound = new IntervalUtil(0.33f,0.38f);
    private IntervalUtil reloadTime = new IntervalUtil(2.8f,2.85f);
	private DamagingProjectileAPI  dummy;
	private static final Color MUZZLE_FLASH_COLOR = new Color(255, 200, 100, 50);
    private static final Color MUZZLE_FLASH_COLOR_ALT = new Color(255, 255, 255, 100);
    private static final Color MUZZLE_FLASH_COLOR_GLOW = new Color(255, 75, 0, 50);
    private static final float MUZZLE_FLASH_DURATION = 0.10f;
    private static final float MUZZLE_FLASH_SIZE = 15.0f;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {

    }

    private Vector2f calculateMuzzle(WeaponAPI weapon){
        float muzzle;
        if (weapon.getSlot().isHardpoint()) {
            muzzle = weapon.getSpec().getHardpointFireOffsets().get(0).getX();
        } else {
            muzzle = weapon.getSpec().getTurretFireOffsets().get(0).getX();
        }
        double angle = Math.toRadians(weapon.getCurrAngle());
        Vector2f dir = new Vector2f((float)Math.cos(angle),(float)Math.sin(angle));
        if (dir.lengthSquared() > 0f) dir.normalise();
        dir.scale(muzzle);
        Vector2f loc = new Vector2f(weapon.getLocation().getX(),weapon.getLocation().getY()-20);
        return Vector2f.add(loc,dir,new Vector2f());

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
			String weaponId = "armaa_einhanderGunS";
			Vector2f muzzleLocation = weapon.getSpec().getTurretFireOffsets().get(0); 
			DamagingProjectileAPI proj = (DamagingProjectileAPI)engine.spawnProjectile(weapon.getShip(),weapon,weaponId,projectile.getLocation(),angle,weapon.getShip().getVelocity());
			float randomSpeed = (float)Math.random() * 0.4f + 0.8f;
			proj.getVelocity().scale(randomSpeed);
		}
		
		
		Global.getCombatEngine().removeEntity(projectile);
		
		
	}
}