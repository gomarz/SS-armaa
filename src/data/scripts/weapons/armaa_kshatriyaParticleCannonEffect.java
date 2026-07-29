package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import java.awt.Color;
import com.fs.starfarer.api.Global;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.ReadableVector2f;

import java.util.List;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.magiclib.util.MagicRender;

public class armaa_kshatriyaParticleCannonEffect implements EveryFrameWeaponEffectPlugin {

    public float TURRET_OFFSET = 30f;
    //sliver cannon charging fx
    private final IntervalUtil interval = new IntervalUtil(0.015f, 0.015f);
    protected IntervalUtil interval2 = new IntervalUtil(0.015f, 0.25f);
    private float level = 0f;
    public static float TARGET_RANGE = 100f;
    public static float RIFT_RANGE = 50f;
    private boolean even = false;

    private boolean hasFired = false;
    //public float TURRET_OFFSET = 45f;
    private final IntervalUtil particle = new IntervalUtil(0.025f, 0.05f);
    private final IntervalUtil particle2 = new IntervalUtil(.1f, .2f);

    private static final int SMOKE_SIZE_MIN = 10;
    private static final int SMOKE_SIZE_MAX = 30;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        ShipAPI ship = weapon.getShip();
        if (engine.isPaused() || weapon.getShip().getOriginalOwner() == -1 || ship.getFluxTracker().isOverloaded() || weapon.isDisabled()) {
            return;
        }

        TURRET_OFFSET = weapon.getSpec().getTurretFireOffsets().get(0).x;
        float OFFSET_Y = weapon.getSpec().getTurretFireOffsets().get(0).y;
        if (weapon.getChargeLevel() > 0f && weapon.getCooldownRemaining() > 0f) {
            particle2.advance(amount);
            if (particle2.intervalElapsed()) {
                Vector2f origin = new Vector2f(weapon.getLocation());
                Vector2f offset = new Vector2f(TURRET_OFFSET, OFFSET_Y);
                VectorUtils.rotate(offset, weapon.getCurrAngle(), offset);
                Vector2f.add(offset, origin, origin);
                float size = MathUtils.getRandomNumberInRange(SMOKE_SIZE_MIN, SMOKE_SIZE_MAX);
                Global.getCombatEngine().addNebulaSmoothParticle(origin, offset, size * weapon.getChargeLevel(), 1f * weapon.getChargeLevel(), 0.5f * weapon.getChargeLevel(), 0.25f, 1f, new Color(0.7f, 0.7f, 0.7f, 0.8f), true);
                //Global.getCombatEngine().addSmokeParticle(origin, offset, size*weapon.getChargeLevel(), 1f*weapon.getChargeLevel(), 1f*weapon.getChargeLevel(), new Color(1f,1f,1f,.7f));
            }
        }
        if (weapon.isFiring()) {
            float charge = weapon.getChargeLevel();
            float size = 1;
            Color color = weapon.getBeams().size() > 0 ? weapon.getBeams().get(0).getFringeColor() : new Color(0,200,255,255);
            if (weapon.getSize() == WeaponAPI.WeaponSize.LARGE) {
                size = 1;
            }
            if (!hasFired) {

                Global.getSoundPlayer().playLoop("beamchargeMeso", (Object) weapon, 1.0f, 1.0f, weapon.getLocation(), weapon.getShip().getVelocity());
                particle.advance(amount);
                if (particle.intervalElapsed()) {

                    Vector2f origin = new Vector2f(weapon.getFirePoint(0));
                    Vector2f offset = new Vector2f(TURRET_OFFSET, OFFSET_Y);
                    VectorUtils.rotate(offset, weapon.getCurrAngle(), offset);
                    Vector2f.add(offset, origin, origin);
                    Vector2f vel = weapon.getShip().getVelocity();
                    engine.addHitParticle(origin, vel, MathUtils.getRandomNumberInRange((float) 20.0f * size, (float) (charge * size * 60.0f + 20.0f)), MathUtils.getRandomNumberInRange((float) 0.5f, (float) (0.5f + charge)), MathUtils.getRandomNumberInRange((float) 0.1f, (float) (0.1f + charge / 10.0f)), color);
                    engine.addSwirlyNebulaParticle(origin, new Vector2f(0.0f, 0.0f), MathUtils.getRandomNumberInRange((float) 20.0f * size, (float) (charge * size * 60.0f + 20.0f)), 1.2f, 0.15f, 0.0f, 0.35f * charge, color, false);

                    Vector2f particleVel = MathUtils.getRandomPointInCircle((Vector2f) new Vector2f(), (float) (35.0f * charge));
                    Vector2f particleLoc = new Vector2f();
                    Vector2f.sub((Vector2f) origin, (Vector2f) new Vector2f((ReadableVector2f) particleVel), (Vector2f) particleLoc);
                    Vector2f.add((Vector2f) vel, (Vector2f) particleVel, (Vector2f) particleVel);

                    for (int i = 0; i < 5; ++i) {
                        engine.addHitParticle(particleLoc, particleVel, MathUtils.getRandomNumberInRange((float) 2.0f, (float) (charge * 2.0f + 2.0f)), MathUtils.getRandomNumberInRange((float) 0.5f, (float) (0.5f + charge)), MathUtils.getRandomNumberInRange((float) 0.75f, (float) (0.75f + charge / 4.0f)), color);
                    }
                }
            }
            if (charge == 1.0f) {
                Vector2f origin = new Vector2f(weapon.getLocation());
                Vector2f offset = new Vector2f(TURRET_OFFSET, OFFSET_Y);
                VectorUtils.rotate(offset, weapon.getCurrAngle(), offset);
                Vector2f.add(offset, origin, origin);
                Vector2f vel = weapon.getShip().getVelocity();
                hasFired = true;
                engine.addHitParticle(origin, vel, MathUtils.getRandomNumberInRange((float) 20.0f * size, (float) (charge * size * 60.0f + 20.0f)), MathUtils.getRandomNumberInRange((float) 0.5f, (float) (0.5f + charge)), MathUtils.getRandomNumberInRange((float) 0.1f, (float) (0.1f + charge / 10.0f)), color);
            }
        } else {
            hasFired = false;
        }

        List<BeamAPI> beams = weapon.getBeams();
        if (beams.isEmpty()) {
            return;
        }
        BeamAPI beam = beams.get(0);
        if (beam.getBrightness() < 1f) {
            return;
        }
        interval.advance(amount);
        interval2.advance(amount * 2f);
        if (interval2.intervalElapsed()) {
            if (beam.getLengthPrevFrame() < 10) {
                return;
            }
            onFire(weapon,Global.getCombatEngine());
        }
    }


    public void onFire(WeaponAPI weapon, CombatEngineAPI engine) {
        ShipAPI ship = weapon.getShip();
            String weaponId = "armaa_percept_homing";
            String weaponId2 = "armaa_percept_homing";
            int level = 1;
            for (int i = 0; i < level; i++) {

                ShipAPI target1 = null;
                if (ship.getWeaponGroupFor(weapon) != null) {
                    //WEAPON IN AUTOFIRE
                    if (ship.getWeaponGroupFor(weapon).isAutofiring() //weapon group is autofiring
                            && ship.getSelectedGroupAPI() != ship.getWeaponGroupFor(weapon)) { //weapon group is not the selected group
                        target1 = ship.getWeaponGroupFor(weapon).getAutofirePlugin(weapon).getTargetShip();
                    } else {
                        target1 = ship.getShipTarget();

                    }
                }

                if (target1 == null) {
                    for (ShipAPI target : CombatUtils.getShipsWithinRange(ship.getLocation(), 2000f)) {
                        if (target.getOwner() == ship.getOwner()) {
                            continue;
                        }

                        if (!target.isAlive() || target.isHulk()) {
                            continue;
                        }

                        target = target1;
                    }
                }

                float side = even ? 1f : -1f;
                float angle = weapon.getCurrAngle() + side * MathUtils.getRandomNumberInRange(25f, 45f);               
                String id = weaponId;
                DamagingProjectileAPI proj = (DamagingProjectileAPI) engine.spawnProjectile(ship, weapon, id, MathUtils.getRandomPointInCircle(weapon.getFirePoint(0), 10f), angle, new Vector2f());
                float fxAngle = proj.getFacing() + MathUtils.getRandomNumberInRange(-45f, 45f);
                Vector2f vel = MathUtils.getPointOnCircumference(
                        null,
                        MathUtils.getRandomNumberInRange(340f, 520f), // speed = outward punch
                        fxAngle
                );
                Vector2f shipVel = new Vector2f(ship.getVelocity());
                shipVel.scale(0.25f);
                Vector2f.add(vel, (Vector2f) shipVel, vel);

                for (int x = 0; x < 5; x++) {
                    engine.addHitParticle(proj.getLocation(),
                            vel,
                            (float) Math.random() * 10f,
                            1f,
                            MathUtils.getRandomNumberInRange(0.5f, 1f), new Color(128f / 255f, 180f / 255f, 242f / 255f,1f)
                    );
                }

                float variance = MathUtils.getRandomNumberInRange(-0.6f, 0f);

                Global.getSoundPlayer().playSound("vajra_fire", 1f + variance, 1f + variance, proj.getLocation(), proj.getVelocity());
                if (MagicRender.screenCheck(0.25f, proj.getLocation())) {
                    engine.addSmoothParticle(weapon.getFirePoint(0), ship.getVelocity(), 100, 0.5f, 0.25f, new Color(128f / 255f, 180f / 255f, 242f / 255f, 1f));
                    //engine.addHitParticle(proj.getLocation(), new Vector2f(), 50, 1f, 0.1f, Color.white);
                }
                if (engine.isEntityInPlay(proj) && !proj.didDamage()) {
                    engine.addPlugin(new armaa_counterShieldScript(proj, target1));
                    //alreadyRegisteredProjectiles.add(projectile);
                }
                even = even == true ? false : true;
            }
    }
}
