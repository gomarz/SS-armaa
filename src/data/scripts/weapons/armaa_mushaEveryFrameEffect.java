package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lwjgl.util.vector.Vector2f;
import java.awt.Color;
import org.lazywizard.lazylib.VectorUtils;
import org.magiclib.util.MagicRender;
import org.lazywizard.lazylib.MathUtils;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;

public class armaa_mushaEveryFrameEffect implements EveryFrameWeaponEffectPlugin, OnFireEffectPlugin, OnHitEffectPlugin {

    public static final int SHOTS = 4;
    public static final float SPREAD = 10f;
    private int lastAmmo = 0;
    private boolean init = false;
    private boolean reloading = true;
    private IntervalUtil reloadTime = new IntervalUtil(2.8f, 2.85f);
    private static final Color MUZZLE_FLASH_COLOR = new Color(255, 200, 100, 50);
    private static final Color MUZZLE_FLASH_COLOR_ALT = new Color(255, 255, 255, 100);
    private static final Color MUZZLE_FLASH_COLOR_GLOW = new Color(255, 75, 0, 50);
    private static final float MUZZLE_FLASH_DURATION = 0.10f;
    private static final float MUZZLE_FLASH_SIZE = 15.0f;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
    {

    }

    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {

        if (Math.random() > 0.75) {
            engine.spawnExplosion(projectile.getLocation(), projectile.getVelocity(), MUZZLE_FLASH_COLOR, MUZZLE_FLASH_SIZE * 0.10f, MUZZLE_FLASH_DURATION);
        } else {
            engine.spawnExplosion(projectile.getLocation(), projectile.getVelocity(), MUZZLE_FLASH_COLOR, MUZZLE_FLASH_SIZE, MUZZLE_FLASH_DURATION);
        }
        engine.addSmoothParticle(projectile.getLocation(), projectile.getVelocity(), MUZZLE_FLASH_SIZE * 3f, 1f, MUZZLE_FLASH_DURATION * 2f, MUZZLE_FLASH_COLOR_GLOW);

        for (int f = 0; f < SHOTS; f++) {
            float offset = ((float) Math.random() * SPREAD) - ((float) Math.random() * SPREAD * 0.5f);
            float angle = weapon.getCurrAngle() + offset;
            String weaponId = "armaa_einhanderGunS2";
            Vector2f muzzleLocation = weapon.getSpec().getTurretFireOffsets().get(0);
            DamagingProjectileAPI proj = (DamagingProjectileAPI) engine.spawnProjectile(weapon.getShip(), weapon, weaponId, projectile.getLocation(), angle, weapon.getShip().getVelocity());
            float randomSpeed = (float) Math.random() * 0.4f + 0.8f;
            proj.getVelocity().scale(randomSpeed);
        }

        Global.getCombatEngine().removeEntity(projectile);

    }

    private final Color PARTICLE_COLOR = new Color(255, 0, 255);
    private final float PARTICLE_SIZE = 2f;
    private final float PARTICLE_BRIGHTNESS = 150f;
    private final float PARTICLE_DURATION = 1f;
    private static final int PARTICLE_COUNT = 1;
    private static final float VEL_MIN = 0.1f;
    private static final float VEL_MAX = 0.15f;
    private static final float CONE_ANGLE = 150f;
    private static final float A_2 = CONE_ANGLE / 2;

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {

        if (MagicRender.screenCheck(0.2f, point)) 
        {
            float speed = projectile.getVelocity().length();
            float facing = projectile.getFacing();
            for (int i = 0; i <= PARTICLE_COUNT; i++) {
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
}
