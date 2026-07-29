package data.scripts.weapons;

import java.awt.Color;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.magiclib.util.MagicRender;

/**
 *
 */
public class armaa_mKnuckleEffect extends BaseCombatLayeredRenderingPlugin implements OnFireEffectPlugin,
        OnHitEffectPlugin,
        EveryFrameWeaponEffectPlugin {

    public armaa_mKnuckleEffect() {
    }
    private boolean soundPlayed = false;
    private boolean windingUp = true;
    private boolean hasFired = false;
    private boolean beganReload = false;
    public float TURRET_OFFSET = 40f;
    private float charge = 0f;
    // Fallback for if missile is destroyed somehow
    private IntervalUtil reloadTimer = new IntervalUtil(30f, 30f);
    private IntervalUtil projCheck = new IntervalUtil(0.05f, 2f);

    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        TURRET_OFFSET = weapon.getSpec().getTurretFireOffsets().get(0).x;
        float OFFSET_Y = weapon.getSpec().getTurretFireOffsets().get(0).y;
        ShipAPI ship = weapon.getShip();
        if (weapon.isFiring()) {
            charge = weapon.getChargeLevel();
        }

        //hasFired = charge >= 1 ? true : false;
        if (weapon.getChargeLevel() > 0 && charge != 1) {
            if (!hasFired && weapon.getCooldownRemaining() <= 0) {
                Vector2f origin = new Vector2f(weapon.getLocation());
                Vector2f offset = new Vector2f(TURRET_OFFSET, OFFSET_Y);
                VectorUtils.rotate(offset, weapon.getCurrAngle(), offset);
                Vector2f.add(offset, origin, origin);
                Vector2f loc = MathUtils.getPoint((Vector2f) weapon.getLocation(), (float) 18.5f, (float) weapon.getCurrAngle());
                Vector2f vel = weapon.getShip().getVelocity();
                //float charge = weapon.getChargeLevel();
                float size = 1.2f;
                windingUp = true;
                //engine.addHitParticle(origin, vel, MathUtils.getRandomNumberInRange((float)20.0f*size, (float)(charge*size * 60.0f + 20.0f)), MathUtils.getRandomNumberInRange((float)0.5f, (float)(0.5f + charge)), MathUtils.getRandomNumberInRange((float)0.1f, (float)(0.1f + charge / 10.0f)), new Color(charge / 1.5f, charge / 2f, charge/10f,.5f));
            }
        } else {
            windingUp = false;
            soundPlayed = false;
        }

        if (!soundPlayed && windingUp) {
            Global.getSoundPlayer().playSound("mechmove", 1f, 1f, weapon.getLocation(), weapon.getShip().getVelocity());
            soundPlayed = true;
        }

        if (weapon.getAmmo() == 0) {
            hasFired = true;
            weapon.getSprite().setColor(new Color(0, 0, 0, 0));
            ship.getMutableStats().getMaxSpeed().modifyMult(ship.getId() + "_powerfist", 1.1f);
            projCheck.advance(amount);
            boolean noProjs = true;
            if (projCheck.intervalElapsed()) {
                for (MissileAPI missile : CombatUtils.getMissilesWithinRange(ship.getLocation(), 10000)) {
                    if (missile.getWeapon() == weapon || missile.getWeapon() != null && missile.getWeapon().getId().equals("armaa_valkazard_geant_mknuckle_return")) {
                        noProjs = false;
                        break;
                    }
                }
            }
            if (noProjs && !beganReload) {
                reloadTimer.advance(amount);
            }
            if (reloadTimer.intervalElapsed() && !beganReload) {
                beganReload = true;
                //engine.addPlugin(new armaa_pocketDimensionScript("armaa_valkazard_geant_mknuckle_return", MathUtils.getRandomPointInCircle(ship.getLocation(), 1000f), weapon.getShip(), weapon));
            }
        } else {
            hasFired = false;
            weapon.getSprite().setColor(new Color(255, 255, 255, 255));
            reloadTimer = new IntervalUtil(10f, 10f);
            beganReload = false;
            ship.getMutableStats().getMaxSpeed().unmodify(ship.getId() + "_powerfist");
        }

    }

    private static final Color MUZZLE_FLASH_COLOR = new Color(250, 146, 0, 255);
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
                "armaa_valkazard_geant_mknuckle_return",
                spawnPoint,
                bounceAngle,
                newVel
        );
        engine.addSmoothParticle(newProj.getLocation(), new Vector2f(), MUZZLE_FLASH_SIZE * 3f, 1f, MUZZLE_FLASH_DURATION * 2f, MUZZLE_FLASH_COLOR_GLOW);
        engine.addSmoothParticle(newProj.getLocation(), new Vector2f(0, 0), 300, 2, 0.15f, Color.white);
        engine.spawnExplosion(newProj.getLocation(), new Vector2f(0, 0), Color.DARK_GRAY, 125, 2);
        engine.spawnExplosion(newProj.getLocation(), new Vector2f(0, 0), Color.BLACK, 60 * 5, 3);
        projectile.getCustomData().put("armaa_mknuckleHit", true);
        engine.addPlugin(new armaa_mknuckleGuidance(newProj, source));
        CombatUtils.applyForce(target, target.getFacing() - 180f, projectile.getMass() * 2f);
    }

    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        engine.addHitParticle(projectile.getLocation(), projectile.getVelocity(), 100, 1f, .2f, new Color(1f, 1f / 1.5f, 1f / 10f));
        ShipAPI source = weapon.getShip();
        Vector2f point = projectile.getLocation();
        //VectorUtils.get
        if (MagicRender.screenCheck(0.2f, projectile.getLocation())) {
            float damage = projectile.getDamage().getBaseDamage();
            for (int x = 0; x < 15; x++) {
                float facing = source.getFacing(); // ship facing in degrees
                float sideSpeed = MathUtils.getRandomNumberInRange(-200f, 200f);

                // unit vector pointing to the ship's RIGHT (relative to facing)
                Vector2f right = MathUtils.getPointOnCircumference(new Vector2f(), 1f, facing + 90f);

                // base velocity + sideways (left/right) component in ship-space
                Vector2f vel = new Vector2f(source.getVelocity());
                vel.x += right.x * sideSpeed;
                vel.y += right.y * sideSpeed;
                engine.addNebulaParticle(point,
                        vel,
                        10f * (0.75f + (float) Math.random() * 0.5f),
                        5f + 2f * Misc.getHitGlowSize(100f, projectile.getDamage().getBaseDamage(), DamageType.KINETIC, damage * 2, damage / 2, damage / 2, 0f) / 100f,
                        0f,
                        0f,
                        1f,
                        new Color(100, 100, 100, 150));
            }
        }
        if (source.getWeaponGroupFor(weapon) != null) {
            //WEAPON IN AUTOFIRE
            if (source.getWeaponGroupFor(weapon).isAutofiring() //weapon group is autofiring
                    && source.getSelectedGroupAPI() != source.getWeaponGroupFor(weapon)) { //weapon group is not the selected group
                source.getWeaponGroupFor(weapon).getAutofirePlugin(weapon).getTargetShip();
            } else {
                source.getShipTarget();
            }
        }
        if (projectile.getCustomData().get("armaa_mknuckleScript") == null) {
            projectile.getCustomData().put("armaa_mknuckleScript", true);
            engine.addPlugin(new armaa_mknuckleProjectileEffect(projectile));
        }

    }
}
