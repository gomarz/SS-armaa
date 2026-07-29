package data.scripts.weapons;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.weapons.armaa_BoomerangShieldGuidance;
import java.awt.Color;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.magiclib.util.MagicRender;

public class armaa_ashuraHammerEffect implements EveryFrameWeaponEffectPlugin, OnHitEffectPlugin, OnFireEffectPlugin {

    // handle the effects for the hammer actually impacting, and add a script to control the sprite
    // -- stuff for tweaking particle characteristics ------------------------
    // color of spawned particles
    private static final Color PARTICLE_COLOR = new Color(250, 236, 111, 255);
    // size of spawned particles (possibly in pixels?)
    private static final float PARTICLE_SIZE = 4f;
    // brightness of spawned particles (i have no idea what this ranges from)
    private static final float PARTICLE_BRIGHTNESS = 150f;
    // how long the particles last (i'm assuming this is in seconds)
    private static final float PARTICLE_DURATION = .8f;
    private static final int PARTICLE_COUNT = 4;

    // -- particle geometry --------------------------------------------------
    // cone angle in degrees
    private static final float CONE_ANGLE = 150f;
    // constant that effects the lower end of the particle velocity
    private static final float VEL_MIN = 0.1f;
    // constant that effects the upper end of the particle velocity
    private static final float VEL_MAX = 0.3f;

    // one half of the angle. used internally, don't mess with thos
    private static final float A_2 = CONE_ANGLE / 2;
    private static final Color MUZZLE_FLASH_COLOR = new Color(250, 146, 0, 255);
    private static final Color MUZZLE_FLASH_COLOR_ALT = new Color(255, 255, 255, 100);
    private static final Color MUZZLE_FLASH_COLOR_GLOW = new Color(255, 0, 0, 100);
    private static final float MUZZLE_FLASH_DURATION = 1f;
    private static final float MUZZLE_FLASH_SIZE = 50.0f;

    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        ShipAPI source = projectile.getSource();
        WeaponAPI weapon = projectile.getWeapon();

        if (MagicRender.screenCheck(0.2f, point)) {
            if (Math.random() > 0.75) {
                engine.spawnExplosion(point, new Vector2f(), MUZZLE_FLASH_COLOR, MUZZLE_FLASH_SIZE * 0.10f, MUZZLE_FLASH_DURATION);
            } else {
                engine.spawnExplosion(point, new Vector2f(), MUZZLE_FLASH_COLOR, MUZZLE_FLASH_SIZE, MUZZLE_FLASH_DURATION);
            }
            engine.addSmoothParticle(point, new Vector2f(), MUZZLE_FLASH_SIZE * 3f, 1f, MUZZLE_FLASH_DURATION * 2f, MUZZLE_FLASH_COLOR_GLOW);
            engine.addSmoothParticle(point, new Vector2f(0, 0), 300, 2, 0.15f, Color.white);
            engine.spawnExplosion(point, new Vector2f(0, 0), Color.DARK_GRAY, 125, 2);
            engine.spawnExplosion(point, new Vector2f(0, 0), Color.BLACK, 60 * 5, 3);

            for (int x = 0; x < 15; x++) {
                engine.addHitParticle(point,
                        MathUtils.getPointOnCircumference(null, MathUtils.getRandomNumberInRange(150f, 325f), (float) Math.random() * 360f),
                        6f, 1f, MathUtils.getRandomNumberInRange(0.6f, 1f), Color.white);
            }

        }

        if (source == null) {
            return;
        }
        Vector2f v = new Vector2f(projectile.getVelocity());
        v.normalise();
        Vector2f n = Vector2f.sub(point, target.getLocation(), null);
        if (n.lengthSquared() == 0f) {
            n = Misc.getUnitVectorAtDegreeAngle(projectile.getFacing());
        }
        n.normalise();
        // reflect v over n
        float dot = v.x * n.x + v.y * n.y;
        Vector2f r = new Vector2f(
            v.x - 2 * dot * n.x,
            v.y - 2 * dot * n.y
        );
        r.normalise();
        float scatter = MathUtils.getRandomNumberInRange(-20f, 20f);
        float bounceAngle = Misc.getAngleInDegrees(r) + scatter;
        Vector2f newVel = Misc.getUnitVectorAtDegreeAngle(bounceAngle);
        newVel.scale(projectile.getVelocity().length());
        Vector2f spawnPoint = new Vector2f(point);

        Vector2f pushDir = Misc.getUnitVectorAtDegreeAngle(bounceAngle);
        float step = 5f;               // distance per iteration
        float maxPush = 150f;          // safety cap (never needed but safe)

        float pushed = 0f;

        while (CollisionUtils.isPointWithinBounds(spawnPoint, target) && pushed < maxPush) {
            spawnPoint.x += pushDir.x * step;
            spawnPoint.y += pushDir.y * step;
            pushed += step;
        }

        DamagingProjectileAPI newProj = (DamagingProjectileAPI) engine.spawnProjectile(
                source,
                weapon,
                weapon.getId() + "_return",
                spawnPoint,
                bounceAngle,
                newVel
        );
        engine.addPlugin(new armaa_ashuraHammerGuidance(newProj, source));
        CombatUtils.applyForce(target, target.getFacing() - 180f, projectile.getMass() * 2f);
    }

    @Override
    public void onFire(DamagingProjectileAPI dpapi, WeaponAPI wapi, CombatEngineAPI engine) {
        engine.addPlugin(new armaa_ashuraHammerProjectileEffect(dpapi));
    }

    @Override
    public void advance(float f, CombatEngineAPI ceapi, WeaponAPI wapi) {

    }
}
