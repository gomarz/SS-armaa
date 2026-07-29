package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import java.awt.Color;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.ReadableVector2f;
import org.lwjgl.util.vector.Vector2f;
import org.lazywizard.lazylib.VectorUtils;
import org.magiclib.util.MagicRender;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import com.fs.starfarer.api.util.Misc;
import java.util.List;
import org.lazywizard.lazylib.combat.CombatUtils;

public class armaa_dimEffectLv2 implements EveryFrameWeaponEffectPlugin, OnFireEffectPlugin, OnHitEffectPlugin {

    private boolean hasFired = false;
    public float TURRET_OFFSET = 45f;
    private final IntervalUtil particle = new IntervalUtil(0.025f, 0.05f);
    float charge = 0f;
    // Tune these
    public static float RADIUS = 2000f;
    public static float FORCE = 5500f;     // tune this
    public static float MAX_FORCE = 10000f;
    public static float MIN_DIST = 60f;

    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {

        int numbarrels = weapon.getSpec().getTurretFireOffsets().size();

        if (weapon.isFiring() && charge != 1f) {
            for(WeaponAPI wep: weapon.getShip().getAllWeapons())
            {
                if(wep != weapon && wep.getId().contains("geant"))
                {
                    wep.disable();
                    break;
                }
            }
            for (int i = 0; i < numbarrels; i++) {
                int barrel = i;
                TURRET_OFFSET = weapon.getSpec().getTurretFireOffsets().get(barrel).x;
                float OFFSET_Y = weapon.getSpec().getTurretFireOffsets().get(barrel).y;
                float charge = weapon.getChargeLevel();
                float size = 1;
                if (weapon.getSize() == WeaponAPI.WeaponSize.LARGE) {
                    size = 2;
                }
                if (!hasFired) {
                    Global.getSoundPlayer().playLoop("beamchargeM", (Object) weapon, 1.5f, 1.0f, weapon.getLocation(), weapon.getShip().getVelocity());
                    particle.advance(amount);
                    if (particle.intervalElapsed()) {
                        Vector2f origin = new Vector2f(weapon.getLocation());
                        Vector2f offset = new Vector2f(TURRET_OFFSET, OFFSET_Y);
                        VectorUtils.rotate(offset, weapon.getCurrAngle(), offset);
                        Vector2f.add(offset, origin, origin);
                        Vector2f loc = MathUtils.getPoint((Vector2f) weapon.getLocation(), (float) 18.5f, (float) weapon.getCurrAngle());
                        Vector2f vel = weapon.getShip().getVelocity();
                        engine.addSwirlyNebulaParticle(weapon.getShip().getLocation(), new Vector2f(0.0f, 0.0f), MathUtils.getRandomNumberInRange((float) weapon.getShip().getCollisionRadius() * 3 * charge, (float) (weapon.getShip().getCollisionRadius() * 4)), 1.2f, 0.15f, 0.0f, 0.35f * charge, new Color(charge, charge / 2f, charge / 1.2f, 0.15f), false);

                        engine.addSwirlyNebulaParticle(origin, new Vector2f(0.0f, 0.0f), MathUtils.getRandomNumberInRange((float) 20.0f * size, (float) (charge * size * 60.0f + 20.0f)), 1.2f, 0.15f, 0.0f, 0.35f * charge, new Color(charge, charge / 2f, charge / 1.2f), false);
                        engine.addNegativeParticle(origin, vel, MathUtils.getRandomNumberInRange((float) 20.0f * size * 2, (float) (2 * charge * size * 60.0f + 20.0f)), MathUtils.getRandomNumberInRange((float) 0.5f, (float) (0.5f + charge)), MathUtils.getRandomNumberInRange((float) 0.1f, (float) (0.1f + charge / 10.0f)), new Color(1f, charge / 4f, charge / 1f, charge));
                        engine.addSmoothParticle(origin, vel, MathUtils.getRandomNumberInRange((float) 20.0f * size, (float) (charge * size * 60.0f + 20.0f)), MathUtils.getRandomNumberInRange((float) 0.5f, (float) (0.5f + charge)), MathUtils.getRandomNumberInRange((float) 0.1f, (float) (0.1f + charge / 10.0f)), new Color(1f, 1f, 1f, charge));
                        Vector2f particleVel = MathUtils.getRandomPointInCircle((Vector2f) new Vector2f(), (float) (100.0f * charge));
                        Vector2f particleLoc = new Vector2f();
                        Vector2f.sub((Vector2f) origin, (Vector2f) new Vector2f((ReadableVector2f) particleVel), (Vector2f) particleLoc);
                        Vector2f.add((Vector2f) vel, (Vector2f) particleVel, (Vector2f) particleVel);
                        for (int j = 0; j < 15; ++j) {
                            engine.addSmoothParticle(particleLoc, particleVel, MathUtils.getRandomNumberInRange((float) 10.0f, (float) (charge * 2.0f + 3.0f)), MathUtils.getRandomNumberInRange((float) 0.5f, (float) (0.5f + charge)), MathUtils.getRandomNumberInRange(0.50f, 0.80f), new Color(charge, charge / 2f, charge / 1.2f));
                        }
                    }
                }
                if (charge == 1.0f) {
                    hasFired = true;
                    if (barrel >= 1) {
                        barrel = 0;
                    } else {
                        barrel++;
                    }
                }
            }
        } else {
            hasFired = false;
        }

    }
    private static final Color MUZZLE_FLASH_COLOR = new Color(255, 55, 200, 255);
    private static final Color MUZZLE_FLASH_COLOR_ALT = new Color(255, 255, 255, 100);
    private static final Color MUZZLE_FLASH_COLOR_GLOW = new Color(255, 0, 255, 50);
    private static final float MUZZLE_FLASH_DURATION = 0.20f;
    private static final float MUZZLE_FLASH_SIZE = 30.0f;

    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        if (MagicRender.screenCheck(0.2f, projectile.getLocation())) {
            engine.addSmoothParticle(projectile.getLocation(), new Vector2f(0, 0), 300, 2, 0.15f, Color.white);
            for (int x = 0; x < 10; x++) {
                engine.addHitParticle(projectile.getLocation(),
                        MathUtils.getPointOnCircumference(null, MathUtils.getRandomNumberInRange(100f, 150f), (float) Math.random() * 360f),
                        15f, 1f, MathUtils.getRandomNumberInRange(0.6f, 1f), MUZZLE_FLASH_COLOR);
            }
        }
        float damage = 500f;
        for (int x = 0; x < NUM_PARTICLES; x++) {
            float facing = projectile.getFacing();
            float back = facing + 180f;

            // recoil "lanes" behind the projectile
            float baseSideOffset = 20f;  // how far off-center left/right the drift goes
            float jitter = 8f;           // random wiggle around that lane

            // choose left (-1) or right (+1)
            float side = (Math.random() < 0.5f) ? -1f : 1f;

            // angle is mostly backward, biased to one side
            float angle = back + side * baseSideOffset + MathUtils.getRandomNumberInRange(-jitter, jitter);

            // particle speed
            float speed = MathUtils.getRandomNumberInRange(120f, 220f);

            // convert to velocity
            Vector2f vel = MathUtils.getPointOnCircumference(null, speed, angle);
            engine.addNebulaParticle(projectile.getLocation(),
                    vel,
                    40f * (0.75f + (float) Math.random() * 0.5f),
                    5f + 3f * Misc.getHitGlowSize(100f, projectile.getDamage().getBaseDamage(), DamageType.KINETIC, damage * 2, damage / 2, damage / 2, 0f) / 100f,
                    0f,
                    0f,
                    1f,
                    PARTICLE_COLOR2);
        }
        //engine.addNegativeNebulaParticle(projectile.getLocation(), new Vector2f(), 155f, 1.5f, 1.1f, 1f, 2f, PARTICLE_COLOR);
        //engine.addNegativeParticle(projectile.getLocation(), projectile.getVelocity(), MUZZLE_FLASH_SIZE * 3f, 1f, MUZZLE_FLASH_DURATION * 2f, MUZZLE_FLASH_COLOR_GLOW);
        engine.addPlugin(new armaa_dimProjectileScript(projectile));
    }

    private static final Color PARTICLE_COLOR = new Color(255, 55, 200, 150);
    private static final Color PARTICLE_COLOR2 = new Color(255, 55, 200, 50);
    private static final int NUM_PARTICLES = 10;
    //@Override

    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        engine.spawnExplosion(point, new Vector2f(), new Color(155, 0, 255, 150), 200f * 2, 0.5f);
        engine.spawnExplosion(point, new Vector2f(), new Color(200, 200, 255, 200), 50f * 4, 0.5f);
        engine.addHitParticle(point,
                new Vector2f(),
                200f, 1f, 0.1f, Color.white);
        float bonusDamage = projectile.getDamageAmount();
        DamagingExplosionSpec blast = new DamagingExplosionSpec(0.1f,
                175f * 2,
                150f * 2,
                bonusDamage,
                bonusDamage / 2f,
                CollisionClass.PROJECTILE_FF,
                CollisionClass.PROJECTILE_FIGHTER,
                10f,
                10f,
                0f,
                0,
                PARTICLE_COLOR,
                null);
        blast.setDamageType(DamageType.ENERGY);
        blast.setShowGraphic(false);
        Global.getCombatEngine().spawnDamagingExplosion(blast, projectile.getSource(), projectile.getSource().getLocation(), false);

        if (MagicRender.screenCheck(0.2f, point)) {
            MagicRender.battlespace(
                    Global.getSettings().getSprite("misc", "armaa_sfxpulse"),
                    point,
                    new Vector2f(),
                    new Vector2f(projectile.getCollisionRadius() * 2, projectile.getCollisionRadius() * 2),
                    new Vector2f(400f, 400f),
                    5f,
                    5f,
                    new Color(50, 0, 25, 100),
                    false,
                    .1f,
                    .1f,
                    1f
            );
            for (int x = 0; x < NUM_PARTICLES; x++) {
                engine.addNebulaParticle(point,
                        new Vector2f(),
                        80f * (0.75f + (float) Math.random() * 0.5f),
                        5f + 3f * Misc.getHitGlowSize(100f, projectile.getDamage().getBaseDamage(), damageResult) / 100f,
                        0f,
                        0f,
                        1f,
                        PARTICLE_COLOR2);
            }
        }
    }

    private class armaa_dimProjectileScript extends BaseEveryFrameCombatPlugin {

        DamagingProjectileAPI proj;
        // Interval for how often force is applied
        private IntervalUtil forceInterval = new IntervalUtil(0.05f, 0.05f);
        // Interval for how long the cluster grows
        private IntervalUtil animInterval = new IntervalUtil(8f, 8f);
        private final IntervalUtil pulseInterval = new IntervalUtil(1.5f, 1.5f);
        private final IntervalUtil pullInterval = new IntervalUtil(0.05f, 0.1f);
        // Interval for the cluster swelling - once elapsed "detonate"
        private IntervalUtil popInterval;
        private Vector2f location;

        public armaa_dimProjectileScript(DamagingProjectileAPI projectile) {
            this.proj = projectile;
            proj.setCollisionRadius(proj.getCollisionRadius() * 2);
            this.location = new Vector2f(proj.getLocation());
        }

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            if (Global.getCombatEngine().isPaused()) {
                return;
            }
            if (proj != null) {
                location = new Vector2f(proj.getLocation());
            }
            if (Global.getCombatEngine().isEntityInPlay(proj) == false || proj.isFading()) {
                animateBlackHole(amount, location, proj.getSource());
                return;
            }
            if (Math.random() > 0.75) {
                Global.getCombatEngine().spawnExplosion(proj.getLocation(), proj.getVelocity(), MUZZLE_FLASH_COLOR_ALT, MUZZLE_FLASH_SIZE * 0.20f, MUZZLE_FLASH_DURATION);
            } else {
                Global.getCombatEngine().spawnExplosion(proj.getLocation(), proj.getVelocity(), MUZZLE_FLASH_COLOR, MUZZLE_FLASH_SIZE * 1f, MUZZLE_FLASH_DURATION);
            }
            if (Math.random() < 0.05) {
                Global.getCombatEngine().spawnEmpArcVisual(proj.getLocation(), proj, MathUtils.getPointOnCircumference(proj.getLocation(), MathUtils.getRandomNumberInRange(proj.getCollisionRadius() / 4, proj.getCollisionRadius() / 2), (float) Math.random() * 360f), proj, 5f, new Color(1f, 1f, 1f, 0.3f), new Color(0f, 0f, 0f, 0.5f));
            }
            float radii = MathUtils.getRandomNumberInRange(proj.getCollisionRadius(), proj.getCollisionRadius() / 1.2f);
            MagicRender.singleframe(Global.getSettings().getSprite("misc", "armaa_sfxpulse"), proj.getLocation(), new Vector2f(radii, radii), proj.getFacing(), new Color(25, 0, 25, 200), false);
            //applyPull(Global.getCombatEngine(),proj,amount);
        }

        public void animateBlackHole(float amount, Vector2f location, ShipAPI ship) {
            if (Global.getCombatEngine().isPaused()) {
                return;
            }
            float level = animInterval.getElapsed() / animInterval.getIntervalDuration();
            float spin = 0f;
            animInterval.advance(amount);
            pulseInterval.advance(amount);
            org.magiclib.util.MagicRender.singleframe(
                    Global.getSettings().getSprite("misc", "armaa_atmo_cloud"),
                    new Vector2f(location),
                    new Vector2f(175f * level * 4 * 2, 175f * level * 4 * 2),
                    -spin * 2,
                    new Color(200f / 255f, 0f, 125f / 255f, (50f / 255f) * level),
                    true);
            org.magiclib.util.MagicRender.singleframe(
                    Global.getSettings().getSprite("misc", "armaa_atmo_cloud"),
                    new Vector2f(location),
                    new Vector2f(150f * level * 4 * 2, 150f * level * 4 * 2),
                    spin * 2,
                    new Color(25f / 255f, 0f, 59f / 255f, (150f / 255f) * level),
                    true);
            org.magiclib.util.MagicRender.singleframe(
                    Global.getSettings().getSprite("misc", "armaa_atmo_cloud"),
                    new Vector2f(location),
                    new Vector2f(100f * level * 5 * 2, 100f * level * 5 * 2),
                    -spin * 2,
                    new Color(0, 0, 0, 255f / 255f * level),
                    false);
            spin += 5 * level;
            Color MUZZLE_FLASH_COLOR = new Color(255, 55, 200, 155);
            Color MUZZLE_FLASH_COLOR_ALT = new Color(255, 255, 255, 100);
            if (Math.random() > 0.75) {
                Global.getCombatEngine().spawnExplosion(location, new Vector2f(), MUZZLE_FLASH_COLOR_ALT, MUZZLE_FLASH_SIZE * 0.20f * level * 8 * 2, MUZZLE_FLASH_DURATION);
            } else {
                Global.getCombatEngine().spawnExplosion(location, new Vector2f(), MUZZLE_FLASH_COLOR, MUZZLE_FLASH_SIZE * 1f * level * 8 * 2, MUZZLE_FLASH_DURATION);
            }
            MagicRender.singleframe(Global.getSettings().getSprite("gates", "starfield"), location, new Vector2f(100f * level * 3f, 100f * level * 3f), -spin * 25 * level, new Color(0f, 0f, 0f, 1f), false, CombatEngineLayers.ABOVE_PARTICLES_LOWER);
            MagicRender.singleframe(Global.getSettings().getSprite("gates", "glow_whirl1"), location, new Vector2f(100f * level * 3f, 100f * level * 3f), -spin * 50 * level, new Color(1f, 0f, 1f, 1f * level), true, CombatEngineLayers.ABOVE_PARTICLES_LOWER);
            MagicRender.singleframe(Global.getSettings().getSprite("gates", "glow_whirl2"), location, new Vector2f(100f * level * 3f, 100f * level * 3f), spin * 50 * level, new Color(1f, 0f, 0f, 1f * level), true, CombatEngineLayers.ABOVE_PARTICLES_LOWER);

            pullInterval.advance(amount);
            if (pullInterval.intervalElapsed()) {
                applyPull(Global.getCombatEngine(), proj, amount, level);
            }
            if (pulseInterval.intervalElapsed()) {
                Global.getCombatEngine().spawnEmpArcVisual(proj.getLocation(), proj, MathUtils.getPointOnCircumference(proj.getLocation(), MathUtils.getRandomNumberInRange(proj.getCollisionRadius()*2, proj.getCollisionRadius()*4), (float) Math.random() * 360f), proj, 5f, new Color(1f, 0f, 1f, 0.3f), new Color(0f, 0f, 0f, 0.5f));
                Global.getSoundPlayer().playSound("system_phase_cloak_collision", 0.8f, 1f, location, new Vector2f(0, 0));

                org.magiclib.util.MagicRender.battlespace(
                        Global.getSettings().getSprite("misc", "armaa_sfxpulse"),
                        location,
                        new Vector2f(),
                        new Vector2f(1000f, 1000f),
                        new Vector2f(-400f, -400f),
                        5f,
                        5f,
                        new Color(25, 0, 50, 100),
                        false,
                        .1f,
                        .1f,
                        1f
                );
            }
            if (animInterval.intervalElapsed()) {
                Global.getSoundPlayer().playSound("system_orion_device_explosion", 0.8f, 1f, location, new Vector2f(0, 0));
                org.magiclib.util.MagicRender.battlespace(
                        Global.getSettings().getSprite("misc", "armaa_sfxpulse"),
                        location,
                        new Vector2f(),
                        new Vector2f(0f, 0f),
                        new Vector2f(500f, 500f),
                        5f,
                        5f,
                        new Color(25, 0, 50, 100),
                        false,
                        .1f,
                        1f,
                        2f
                );
                Global.getCombatEngine().spawnExplosion(location, new Vector2f(), MUZZLE_FLASH_COLOR_ALT, MUZZLE_FLASH_SIZE * 0.20f * level * 4, MUZZLE_FLASH_DURATION);
                DamagingExplosionSpec spec = new DamagingExplosionSpec(
                        4f, //duration
                        1000f, //radius 
                        500f, //core radius
                        3000, //max dmg
                        500, //min damage
                        CollisionClass.PROJECTILE_NO_FF, //collisionclass
                        CollisionClass.PROJECTILE_FIGHTER, //collisionclass fighter
                        20f, //min particlesize
                        10f, //range
                        2f, //duration
                        100, //count
                        new Color(255, 0, 255, 100), //color
                        Color.pink);//explosioncolor
                Global.getCombatEngine().spawnDamagingExplosion(spec, ship, location);
                Global.getCombatEngine().spawnExplosion(location, new Vector2f(), new Color(155, 0, 255, 200), 1000f * 2, 1f);
                if (MagicRender.screenCheck(0.2f, location)) {
                    for (int x = 0; x < 30; x++) {
                        float damage = 1000f;
                        Global.getCombatEngine().addNebulaParticle(location,
                                new Vector2f(MathUtils.getRandomNumberInRange(-400, 400), MathUtils.getRandomNumberInRange(-400, 400)),
                                60f * (0.75f + (float) Math.random() * 0.5f),
                                5f + 3f * Misc.getHitGlowSize(100f, damage, DamageType.ENERGY, damage * 2, damage / 2, damage / 2, 0f) / 100f,
                                0f,
                                0f,
                                2f,
                                new Color(200, 100, 200, 150));
                        Global.getCombatEngine().addNebulaParticle(location,
                                new Vector2f(MathUtils.getRandomNumberInRange(-400, 400), MathUtils.getRandomNumberInRange(-400, 400)),
                                60f * (0.75f + (float) Math.random() * 0.5f),
                                5f + 3f * Misc.getHitGlowSize(100f, damage, DamageType.ENERGY, damage * 2, damage / 2, damage / 2, 0f) / 100f,
                                0f,
                                0f,
                                3f,
                                new Color(150, 100, 100, 100));
                    }
                }
                Global.getCombatEngine().removeEntity(proj);
                Global.getCombatEngine().removePlugin(this);
            }
        }

        public void applyPull(CombatEngineAPI engine, DamagingProjectileAPI center, float amount, float level) {
            if (engine == null || center == null) {
                return;
            }

            Vector2f cLoc = center.getLocation();
            for (ShipAPI ship : CombatUtils.getShipsWithinRange(cLoc, RADIUS)) {
                if (!ship.isAlive() || ship.isHulk()) {
                    continue;
                }
                if (ship == center.getSource()) {
                    continue;
                }
                if (ship.isPhased() || ship.getCollisionClass() == CollisionClass.NONE) {
                    continue; // optional
                }
                // spawn proj so AI knows they are in danger
                pullEntity(ship, cLoc, amount, level);
                
            }
        }

        private void pullEntity(CombatEntityAPI target, Vector2f centerLoc, float amount, float level) {
            Vector2f tLoc = target.getLocation();
            //DamagingProjectileAPI pj = (DamagingProjectileAPI)Global.getCombatEngine().spawnProjectile(proj.getSource(), proj.getWeapon(), "armaa_valkazard_geant_core", proj.getLocation(), VectorUtils.getAngle(proj.getLocation(), target.getLocation()), new Vector2f());
            //pj.setDamageAmount(0f);
            if(pulseInterval.intervalElapsed())
                Global.getCombatEngine().spawnEmpArcVisual(proj.getLocation(), proj, target.getLocation(), target, 5f, new Color(1f, 0f, 1f, 0.3f), new Color(0f, 0f, 0f, 0.5f));

            Vector2f dir = Vector2f.sub(centerLoc, tLoc, null); // points toward center
            float dist = dir.length();
            if (dist >= RADIUS) {
                return;
            }

            dist = Math.max(dist, MIN_DIST);
            dir.scale(1f / dist); // normalize direction

            float t = 1f - (dist / RADIUS);  // 0..1
            float falloff = t * t;

            float forceMag = FORCE * falloff * amount * level;
            forceMag = Math.min(forceMag, MAX_FORCE);
            CombatUtils.applyForce(target, dir, forceMag);
            
        }
    }
}
