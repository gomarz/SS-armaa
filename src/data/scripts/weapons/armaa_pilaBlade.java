package data.scripts.weapons;

import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.util.IntervalUtil;
import org.magiclib.util.MagicRender;
import org.magiclib.plugins.MagicTrailPlugin;
import java.util.List;
import java.util.ArrayList;

import org.lazywizard.lazylib.combat.CombatUtils;

import java.awt.Color;
import org.lazywizard.lazylib.MathUtils;
import org.magiclib.util.MagicLensFlare;

public class armaa_pilaBlade implements BeamEffectPlugin {

    private IntervalUtil fireInterval = new IntervalUtil(0.05f, 0.05f);
    private IntervalUtil targetInterval = new IntervalUtil(0.3f, 0.3f);
    private final Vector2f ZERO = new Vector2f();
    private final float BLADE_KNOCKBACK_MAX = 125f;
    // -- stuff for tweaking particle characteristics ------------------------
    // color of spawned particles
    private static final Color PARTICLE_COLOR = new Color(214, 156, 56, 200);
    // size of spawned particles (possibly in pixels?)
    private static final float PARTICLE_SIZE = 8f;
    // brightness of spawned particles (i have no idea what this ranges from)
    private static final float PARTICLE_BRIGHTNESS = 150f;
    // how long the particles last (i'm assuming this is in seconds)
    private static final float PARTICLE_DURATION = .8f;
    private static final int PARTICLE_COUNT = 10;

    private static final float PARTICLE_SIZE_MIN = 1f;
    private static final float PARTICLE_SIZE_MAX = 3f;
    private static final float PARTICLE_DURATION_MIN = 0.4f;
    private static final float PARTICLE_DURATION_MAX = 0.9f;
    private static final float PARTICLE_INERTIA_MULT = 0.5f; //This is how much the particles retain their spawning ship's velocity; 0f means no retained velocity, 1f means full retained velocity.
    private static final float PARTICLE_DRIFT = 50f; //This is how much the particles "move" in a random direction when spawned, at most; their actual speed is between 0 and this value (plus any inertia, see above)
    private static final float PARTICLE_DENSITY = 0.15f; //Measured in particles per SU^2 (area unit) and second; lower means less particles. This is multiplied by charge level, too
    private static final float PARTICLE_SPAWN_WIDTH_MULT = 0.1f; //Multiplier for how wide the particles are allowed to spawn from the beam center; at 1f, it's equal to beam width, at 0f they only spawn in the center. Note that true beam width is usually much bigger than the visual beam width

    // -- particle geometry --------------------------------------------------
    // cone angle in degrees
    private static final float CONE_ANGLE = 150f;
    // constant that effects the lower end of the particle velocity
    private static final float VEL_MIN = 0.1f;
    // constant that effects the upper end of the particle velocity
    private static final float VEL_MAX = 0.3f;

    // one half of the angle. used internally, don't mess with thos
    private static final float A_2 = CONE_ANGLE / 2;
    private boolean firstStrike = false;
    private float id2;
    private boolean runOnce = false;
    private boolean runOnce2 = false;
    private WeaponAPI weapon;
    private List<CombatEntityAPI> targets = new ArrayList<CombatEntityAPI>();
    private List<CombatEntityAPI> hitTargets = new ArrayList<CombatEntityAPI>();

    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
        weapon = beam.getWeapon();
        ShipAPI ship = weapon.getShip();
        if (!runOnce) {
            MagicTrailPlugin.getUniqueID();
            id2 = MagicTrailPlugin.getUniqueID();
            runOnce = true;
        }
        if (weapon.getChargeLevel() >= 1f) {
            if (!runOnce2) {
                runOnce2 = true;
            }

        }
        beam.getDamage().setDamage(0);
        targetInterval.advance(amount);
        if (targetInterval.intervalElapsed()) {
            targets.clear();
            hitTargets.clear();
            firstStrike = false;
        }
        CombatEntityAPI target = beam.getDamageTarget();
        if (!targets.contains(target) && target != null) {
            targets.add(target);
        }

        Vector2f spawnPoint = MathUtils.getRandomPointOnLine(beam.getFrom(), beam.getTo());
        Vector2f dir = Vector2f.sub(beam.getTo(), beam.getFrom(), new Vector2f());
        Vector2f point = Vector2f.sub(beam.getTo(), dir, new Vector2f());
        if (weapon.isFiring()) {
            for (CombatEntityAPI enemy : targets) {
                if (enemy == beam.getDamageTarget()) {
                    if (hitTargets.contains(beam.getDamageTarget())) {
                        continue;
                    } else {
                        boolean softFlux = true;

                        if (weapon.getDamage().isForceHardFlux() || weapon.getShip().getVariant().getHullMods().contains("high_scatter_amp")) {
                            softFlux = false;
                        }

                        float dmg = weapon.getDamage().getDamage() * (weapon.getShip().getMutableStats().getBeamWeaponDamageMult().computeMultMod() + weapon.getShip().getMutableStats().getBeamWeaponDamageMult().getPercentMod() / 100f) * (weapon.getShip().getMutableStats().getEnergyWeaponDamageMult().computeMultMod() + weapon.getShip().getMutableStats().getEnergyWeaponDamageMult().getPercentMod() / 100f);

                        float mag = weapon.getShip().getFluxBasedEnergyWeaponDamageMultiplier() - 1f;
                        if (mag > 0) {
                            dmg = dmg * (1 + mag);
                        }
                        // damage penalty if beam is greater than 50% of base range
                        // mostly for stuff that gives a flat range increase
                        if (beam.getLength() > beam.getWeapon().getOriginalSpec().getMaxRange() * 1.5f) {
                            dmg *= Math.max(0.5f, beam.getWeapon().getOriginalSpec().getMaxRange() / beam.getLength());
                        }

                        engine.applyDamage(enemy, beam.getTo(), dmg, weapon.getDamageType(), beam.getDamage().getFluxComponent(), false, softFlux, weapon.getShip());
                        hitTargets.add(enemy);
                        if (enemy instanceof ShipAPI) {
                            ShipAPI enemyShip = (ShipAPI) target;
                            if (!enemyShip.isFighter() && enemyShip.isAlive()) {
                                CombatUtils.applyForce(weapon.getShip(), weapon.getShip().getFacing() - 180f, Math.max(enemy.getMass(), BLADE_KNOCKBACK_MAX));
                            }
                        }

                    }
                }
            }

            fireInterval.advance(amount);

            if (fireInterval.intervalElapsed() && beam.getBrightness() == 1f) {
                float angle = weapon.getCurrAngle() - 90f;
                if (MagicRender.screenCheck(0.2f, beam.getFrom())) {
                    Vector2f midpoint = new Vector2f((beam.getFrom().x + beam.getTo().x) / 2f, (beam.getFrom().y + beam.getTo().y) / 2f);
                    MagicTrailPlugin.addTrailMemberAdvanced(
                            weapon.getShip(), id2, Global.getSettings().getSprite("fx", "base_trail_smooth"),
                            midpoint,
                            0f,
                            0f,
                            angle,
                            weapon.getShip().getAngularVelocity(),
                            0f,
                            beam.getLength() * 2, beam.getLength() * 2,
                            beam.getCoreColor(), beam.getFringeColor(), 1f,
                            amount, amount, .1f,
                            true,
                            256f, 0f, 0f,
                            null, null, null, 2f);
                }
            }
        }

        if (target instanceof CombatEntityAPI) {
            if (MagicRender.screenCheck(0.2f, point)) {
                Color color = beam.getFringeColor();
                Color core = beam.getCoreColor();
                Color particolor = Math.random() > 0.50f ? color : PARTICLE_COLOR;
                if (Math.random() > 0.50) {
                    for (int i = 0; i <= PARTICLE_COUNT / 2; i++) {
                        point = beam.getTo();
                        float speed = 500f;
                        float facing = beam.getWeapon().getCurrAngle();
                        float radius = 10f + (weapon.getChargeLevel() * weapon.getChargeLevel() * MathUtils.getRandomNumberInRange(25f, 75f));
                        float angle = MathUtils.getRandomNumberInRange(facing - A_2,
                                facing + A_2);
                        float vel = MathUtils.getRandomNumberInRange(speed * -VEL_MIN,
                                speed * -VEL_MAX);
                        Vector2f vector = MathUtils.getPointOnCircumference(null,
                                vel,
                                angle);
                        engine.addHitParticle(beam.getTo(),
                                vector,
                                PARTICLE_SIZE,
                                PARTICLE_BRIGHTNESS,
                                PARTICLE_DURATION,
                                particolor);
                        radius = 10f + (weapon.getChargeLevel() * weapon.getChargeLevel() * MathUtils.getRandomNumberInRange(25f, 75f));
                        angle = MathUtils.getRandomNumberInRange(facing - A_2,
                                facing + A_2);
                        vel = MathUtils.getRandomNumberInRange(speed * -VEL_MIN,
                                speed * -VEL_MAX);
                        vector = MathUtils.getPointOnCircumference(null,
                                vel,
                                angle);
                        engine.addSmoothParticle(beam.getTo(), vector, radius, 0.1f + weapon.getChargeLevel() * 0.25f, 0.1f, color);
                        radius = 10f + (weapon.getChargeLevel() * weapon.getChargeLevel() * MathUtils.getRandomNumberInRange(25f, 75f));
                        angle = MathUtils.getRandomNumberInRange(facing - A_2,
                                facing + A_2);
                        vel = MathUtils.getRandomNumberInRange(speed * -VEL_MIN,
                                speed * -VEL_MAX);
                        vector = MathUtils.getPointOnCircumference(null,
                                vel,
                                angle);
                        engine.addHitParticle(beam.getTo(), vector, radius * .70f, 0.1f + weapon.getChargeLevel() * 0.1f, 0.1f, core);
                    }
                }
            }
            if (!firstStrike) {
                if (target instanceof ShipAPI) {
                    ShipAPI enemy = (ShipAPI) target;
                    if (!enemy.isFighter() && enemy.isAlive()) {
                        weapon.getShip().getFluxTracker().increaseFlux(200, true);
                    }
                    boolean hitShield = target.getShield() != null && target.getShield().isWithinArc(beam.getRayEndPrevFrame());
                    float pierceChance = ((ShipAPI) target).getHardFluxLevel() - 0.1f;
                    pierceChance *= ship.getMutableStats().getDynamic().getValue(Stats.SHIELD_PIERCED_MULT);

                    boolean piercedShield = hitShield && (float) Math.random() < pierceChance;
                    //piercedShield = true;

                    if (!hitShield || piercedShield) {
                        Vector2f empPoint = beam.getRayEndPrevFrame();
                        float emp = beam.getDamage().getFluxComponent() * 1f;
                        float dam = beam.getDamage().getDamage() * 1f;
                        engine.spawnEmpArcPierceShields(
                                beam.getSource(), empPoint, beam.getDamageTarget(), beam.getDamageTarget(),
                                DamageType.ENERGY,
                                dam, // damage
                                emp, // emp 
                                100000f, // max range 
                                "tachyon_lance_emp_impact",
                                beam.getWidth() + 9f,
                                beam.getFringeColor(),
                                beam.getCoreColor()
                        );
                    }
                }
                float variance = MathUtils.getRandomNumberInRange(-0.3f, .3f);
                Global.getSoundPlayer().playSound("armaa_saber_slash", 0.8f + variance, 0.7f + variance, point, ZERO);
                firstStrike = true;
                MagicLensFlare.createSharpFlare(
                        Global.getCombatEngine(),
                        ship,
                        point,
                        5,
                        500f,
                        weapon.getCurrAngle(),
                        beam.getFringeColor(),
                        Color.white
                );

                for (int i = 0; i < PARTICLE_COUNT; i++) {
                    float speed = 400f;
                    float facing = weapon.getCurrAngle();
                    float angle = MathUtils.getRandomNumberInRange(facing - A_2,
                            facing + A_2);
                    float vel = MathUtils.getRandomNumberInRange(speed * -VEL_MIN,
                            speed * -VEL_MAX);
                    Vector2f vector = MathUtils.getPointOnCircumference(null,
                            vel,
                            angle);
                    if (Math.random() < 0.25f) {
                        Global.getCombatEngine().spawnDebrisMedium(point, vector, 1, angle, 15f, 10f, 25f, 50f);
                    } else {
                        Global.getCombatEngine().spawnDebrisSmall(point, vector, 1, angle, 15f, 10f, 25f, 50f);
                    }
                }
            }

            if (dir.lengthSquared() > 0) {
                dir.normalise();
            }
            dir.scale(50f);
        }
        if (MagicRender.screenCheck(0.2f, point)) {
            //yoinked from LoA
            if (runOnce2) {
                /*
                //Calculate how many particles should be spawned this frame
                float particleCount = beamWidth * PARTICLE_SPAWN_WIDTH_MULT * MathUtils.getDistance(beam.getTo(), beam.getFrom()) * amount * PARTICLE_DENSITY * weapon.getChargeLevel();

                //Generate the particles and assign them their random characteristics
                for (int i = 0; i < particleCount; i++) {
                    spawnPoint = MathUtils.getRandomPointInCircle(spawnPoint, beamWidth * PARTICLE_SPAWN_WIDTH_MULT);
                    //If this point is too far off-screen, we go on to the next particle instead
                    if (!Global.getCombatEngine().getViewport().isNearViewport(spawnPoint, PARTICLE_SIZE_MAX * 3f)) {
                        continue;
                    }

                    //After that, we calculate our velocity
                    Vector2f velocity = new Vector2f(ship.getVelocity().x * PARTICLE_INERTIA_MULT, ship.getVelocity().y * PARTICLE_INERTIA_MULT);
                    velocity = MathUtils.getRandomPointInCircle(velocity, PARTICLE_DRIFT);

                    if ((float) Math.random() <= 0.01f) {
                        engine.addNebulaParticle(spawnPoint,
                                velocity,
                                40f * (0.75f + (float) Math.random() * 0.5f),
                                MathUtils.getRandomNumberInRange(1.0f, 3f),
                                0f,
                                0f,
                                1f,
                                new Color(beam.getFringeColor().getRed(), beam.getFringeColor().getGreen(), beam.getFringeColor().getBlue(), 100),
                                true);

                    }

                    //And finally spawn the particle
                    if(Math.random() <= 0.01f)
                    engine.addSmoothParticle(spawnPoint, velocity, MathUtils.getRandomNumberInRange(PARTICLE_SIZE_MIN, PARTICLE_SIZE_MAX), weapon.getChargeLevel(),
                            MathUtils.getRandomNumberInRange(PARTICLE_DURATION_MIN, PARTICLE_DURATION_MAX), beam.getFringeColor());
                }*/
            }
        }
    }
}
