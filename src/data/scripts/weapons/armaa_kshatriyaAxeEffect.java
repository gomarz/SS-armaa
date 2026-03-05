package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.FaderUtil;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.util.armaa_utils;
import org.magiclib.util.MagicAnim;
import java.awt.Color;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import java.util.ArrayList;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.magiclib.plugins.MagicTrailPlugin;
import org.magiclib.util.MagicFakeBeam;
import org.magiclib.util.MagicLensFlare;
import org.magiclib.util.MagicRender;

public class armaa_kshatriyaAxeEffect implements EveryFrameWeaponEffectPlugin, OnFireEffectPlugin {

    private boolean runOnce = false;
    private ShipAPI ship;

    private float swingLevel = 0f;
    private boolean swinging = false;
    private final IntervalUtil animInterval = new IntervalUtil(0.012f, 0.012f);
    private final IntervalUtil trailInterval = new IntervalUtil(0.05f, 0.05f);

    // Only way I can think of gating logic so we cant hit stuff while on cooldown
    private IntervalUtil attackDuration = new IntervalUtil(1f, 1f);
    private final FaderUtil redLevel = new FaderUtil(1f, 2f, 2f);
    boolean canAttack = true;
    public float TURRET_OFFSET = 30f;
    private final ArrayList<CombatEntityAPI> targets = new ArrayList();
    boolean cooldownR;
    boolean beamFired;
    float dir = 1f;
    private float id2;
    private float ogSpikePos = 0f;
    private final float wepRecoilMax = 100f;
    private float recoil = 0f;
    private final float TORSO_OFFSET = -45;
    private SpriteAPI spr;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine.isPaused()) {
            return;
        }
        if (!runOnce) {

            id2 = MagicTrailPlugin.getUniqueID();
            ogSpikePos = weapon.getBarrelSpriteAPI().getCenterY();
            runOnce = true;
            spr = Global.getSettings().getSprite(weapon.getSpec().getHardpointSpriteName());
        }
        ship = weapon.getShip();
        if (engine.getCustomData().get("armaa_drillHit_" + ship.getId()) != null) {
            engine.getCustomData().remove("armaa_drillHit_" + ship.getId());
            recoil = Math.max(0, recoil - 8);
        } //else if (recoil < wepRecoilMax) {
        recoil = wepRecoilMax * weapon.getChargeLevel();
        Vector2f recoilOffset = MathUtils.getPointOnCircumference(null, recoil, weapon.getCurrAngle());
        Vector2f renderLoc = new Vector2f(weapon.getLocation().x + recoilOffset.x,
                weapon.getLocation().y + recoilOffset.y);
        redLevel.advance(amount);
        if (redLevel.getBrightness() >= 1f) {
            redLevel.fadeOut();
        } else if (redLevel.getBrightness() <= 0f) {
            redLevel.fadeIn();
        }
        MagicRender.battlespace(
                spr,
                renderLoc,
                new Vector2f(),
                new Vector2f(spr.getWidth(), spr.getHeight()),
                new Vector2f(),
                weapon.getCurrAngle() - 90f,
                0,
                new Color(1f, 1f, 1f, 0.95f * redLevel.getBrightness()),
                false,
                0,
                0,
                0,
                0,
                0,
                0f,
                amount,
                0f,
                CombatEngineLayers.FIGHTERS_LAYER
        );
        weapon.getBarrelSpriteAPI().setCenterY(ogSpikePos - recoil);
        spr.setCenterY(ogSpikePos - recoil);
        if (!ship.getAIFlags().hasFlag(ShipwideAIFlags.AIFlags.BACKING_OFF) && !ship.getAIFlags().hasFlag(ShipwideAIFlags.AIFlags.DO_NOT_PURSUE)) {
            if (ship.getShipTarget() != null && MathUtils.getDistance(ship, ship.getShipTarget()) < 800 && !weapon.isDisabled()) {
                if (ship.getAI() != null) {
                    //ship.blockCommandForOneFrame(ShipCommand.DECELERATE);
                    engine.headInDirectionWithoutTurning(ship, weapon.getCurrAngle(), ship.getMaxSpeed());
                }
            }
        }
        if (ship.getFluxTracker().isOverloadedOrVenting()) {
            return;
        }
        if (weapon != null) {
            float sineC;
            if (weapon.getChargeLevel() < 1) {
                sineC = MagicAnim.smoothNormalizeRange(weapon.getChargeLevel(), 0.3f, 0.9f);
                MagicAnim.smoothNormalizeRange(weapon.getChargeLevel(), 0.3f, 1f);
            } else {
                sineC = 1;
            }
            boolean leftie = armaa_utils.getWeaponSide(ship.getLocation(), ship.getFacing(), weapon.getLocation());
            float mult = -1f;
            if (leftie) {
                mult = 1f;
            }
            if (weapon.getCooldownRemaining() <= 0f && !weapon.isFiring()) {
                cooldownR = false;
            }

            if (!swinging && !cooldownR && weapon.getChargeLevel() > 0f) {
                weapon.setCurrAngle(weapon.getCurrAngle() + (sineC * (mult * TORSO_OFFSET) * 0.30f) * weapon.getChargeLevel());
            }
            if (weapon.getChargeLevel() >= 1f) {
                swinging = true;
            }

            if (swinging && Math.abs(weapon.getCurrAngle() - (weapon.getShip().getFacing() - (mult * 45f))) > 0.1f) {
                animInterval.advance(amount);

                if (mult == 1) {
                    // Left swing: use min to limit the angle correctly
                    weapon.setCurrAngle((float) Math.min(weapon.getCurrAngle() + swingLevel * dir, weapon.getCurrAngle() + weapon.getArc() / 2));
                } else {
                    // Right swing: use max to limit the angle correctly
                    weapon.setCurrAngle((float) Math.max(weapon.getCurrAngle() - swingLevel * dir, weapon.getCurrAngle() - weapon.getArc() / 2));
                }
            }

            if (swinging == true && (weapon.getChargeLevel() <= 0f)) {
                swinging = false;
                swingLevel = 0f;
                cooldownR = true;
            }

            if (animInterval.intervalElapsed() && swinging) {
                swingLevel += 0.5;
            }

            if (swingLevel > 9) {
                swingLevel = 9;
            }
            if (swinging == false) {
                swingLevel = 0f;
            }
            float chargeLevel = weapon.getChargeLevel();
            if (chargeLevel == 1f) {
                beamFired = true;
            }
            if (beamFired && swingLevel > 0) {
                swingLevel -= 0.25f;
            }
            if (attackDuration.intervalElapsed()) {
                canAttack = false;
            }
            if (weapon.isFiring() && !attackDuration.intervalElapsed() && canAttack) {
                ship.setFacing(ship.getFacing() + dir * -1 * 0.5f);
                attackDuration.advance(amount);
                trailInterval.advance(amount);
                MagicFakeBeam.spawnFakeBeam(engine, weapon.getFirePoint(0), weapon.getRange()-wepRecoilMax+recoil, weapon.getCurrAngle(), 15f, amount, 0.15f * chargeLevel, 15f, new Color(0f, 0f, 0f, 0f), new Color(0f, 0f, 0f, 0f), 0f, DamageType.ENERGY, 0f, ship);

                float angDeg = weapon.getCurrAngle();
                Vector2f origin = new Vector2f(weapon.getFirePoint(0));

                Vector2f point = getBeamEndpoint(origin, (float) Math.toRadians(angDeg), weapon.getRange() + 10f);

                //Vector2f point = getBeamEndpoint(weapon.getFirePoint(0), weapon.getCurrAngle(), weapon.getRange());
                if (MagicRender.screenCheck(0.2f, weapon.getFirePoint(0)) && trailInterval.intervalElapsed()) {
                    Vector2f midpoint = new Vector2f((weapon.getFirePoint(0).x + point.x) / 2f, (weapon.getFirePoint(0).y + point.y) / 2f);
                    MagicTrailPlugin.addTrailMemberAdvanced(
                            weapon.getShip(),
                            id2,
                            Global.getSettings().getSprite("fx", "beam_trail_cel"),
                            midpoint,
                            0f,
                            0f,
                            weapon.getCurrAngle() + 90f * mult,
                            0f,
                            0f,
                            weapon.getRange()-wepRecoilMax+recoil, weapon.getRange()-wepRecoilMax+recoil,
                            new Color(1f, 1f, 1f, .2f), new Color(1f, 1f, 1f, .2f), 0.5f,
                            0f, 0.2f, 0.1f,
                            true,
                            256f, 0f, 1f,
                            null, null, null, 1f);
                    //firedTrail = true;
                }
                for (CombatEntityAPI target : CombatUtils.getEntitiesWithinRange(weapon.getFirePoint(0), weapon.getRange() + 10f)) {
                    // no killing stuff on our side
                    if(target.getOwner() == ship.getOwner())
                        continue;
                    if (targets.contains(target)) {
                        continue;
                    }
                    float angleToTarget = VectorUtils.getAngle(weapon.getFirePoint(0), target.getLocation());
                    float swingAngle = weapon.getCurrAngle();

                    float diff = MathUtils.getShortestRotation(swingAngle, angleToTarget);

                    if (Math.abs(diff) > weapon.getArc() * 0.5f) {
                        continue; // target is NOT within the blade arc → ignore
                    }
                    //ignore projs for now
                    if (target instanceof DamagingProjectileAPI && !(target instanceof MissileAPI)) {

                        continue;
                    }
                    // need to be careful here and make sure
                    // we dont spawn projs where it isnt needed
                    if (target instanceof MissileAPI) {
                        MissileAPI missile = (MissileAPI) target;
                        if (preciseHitCheck(weapon, target) != null) {
                            MagicLensFlare.createSharpFlare(
                                    Global.getCombatEngine(),
                                    ship,
                                    missile.getLocation(),
                                    5,
                                    600,
                                    0,
                                    Color.white,
                                    Color.white
                            );
                            engine.applyDamage(missile, missile.getLocation(), weapon.getDamage().getDamage(), weapon.getDamageType(), 0f, false, false, weapon.getShip());
                        }
                        continue;
                    }

                    // only precise check where its needed
                    if (!(target instanceof ShipAPI || target instanceof CombatAsteroidAPI)) {
                        continue;
                    }
                    if (target instanceof ShipAPI) {
                        ShipAPI shiptarget = (ShipAPI) target;
                        if (shiptarget.isPhased() || shiptarget.getCollisionClass() == CollisionClass.NONE) {
                            continue;
                        }
                    }
                    // 1) try shield. dont need to be precise here since shield is a bubble
                    Vector2f shieldHit = intersectShield(target, weapon.getFirePoint(0), point);
                    if (shieldHit != null) {
                        engine.applyDamage(target, shieldHit, weapon.getDamage().getDamage(), weapon.getDamageType(), 0f, false, false, ship, true);
                        falseOnHit(weapon, target, shieldHit);
                        targets.add(target);
                        float variance = MathUtils.getRandomNumberInRange(-0.3f, .3f);
                        Global.getSoundPlayer().playSound("armaa_polearm", 1.1f + variance, 1f + variance, point, new Vector2f());
                        MagicLensFlare.createSharpFlare(
                                Global.getCombatEngine(),
                                ship,
                                shieldHit,
                                10,
                                400,
                                weapon.getCurrAngle(),
                                Color.white,
                                Color.white
                        );
                        continue;

                    }
                    Vector2f preciseHit = preciseHitCheck(weapon, target);
                    if (preciseHit != null) {
                        if (target instanceof ShipAPI) {
                            ShipAPI enemy = (ShipAPI) target;
                            if (enemy.isCruiser() || enemy.isCapital()) {
                                dir = dir * -1;
                            }
                        }
                        engine.applyDamage(target, preciseHit, weapon.getDamage().getDamage(), weapon.getDamageType(), 0f, false, false, ship, true);
                        falseOnHit(weapon, target, preciseHit);
                        float variance = MathUtils.getRandomNumberInRange(-0.3f, .3f);
                        Global.getSoundPlayer().playSound("armaa_polearm", 1.1f + variance, 1f + variance, point, new Vector2f());
                        //engine.addFloatingText(point, "no shield point", 25f, Color.yellow, null, 1f, 1f);
                        MagicLensFlare.createSharpFlare(
                                Global.getCombatEngine(),
                                ship,
                                preciseHit,
                                10,
                                600,
                                weapon.getCurrAngle(),
                                new Color(255, 200, 0, 200),
                                Color.white
                        );
                        for (int i = 0; i < PARTICLE_COUNT; i++) {
                            float speed = (float) Math.random() * 400f;
                            float facing = weapon.getCurrAngle();
                            float angle = MathUtils.getRandomNumberInRange(facing - A_2,
                                    facing + A_2);
                            float vel = MathUtils.getRandomNumberInRange(speed * -VEL_MIN,
                                    speed * -VEL_MAX);
                            Vector2f vector = MathUtils.getPointOnCircumference(null,
                                    vel,
                                    angle);
                            if (Math.random() > 0.25f) {
                                Global.getCombatEngine().spawnDebrisMedium(point, vector, MathUtils.getRandomNumberInRange(1, 3), angle, 25f, 5f, 30f, 45f);
                            } else {
                                Global.getCombatEngine().spawnDebrisLarge(point, vector, MathUtils.getRandomNumberInRange(1, 3), angle, 25f, 5f, 30f, 45f);
                            }
                        }
                        CombatUtils.applyForce(target, target.getFacing() - 180f, ship.getMass() * 1.5f);
                        targets.add(target);
                    }
                }
            }

            if (chargeLevel <= 0 && beamFired) {
                beamFired = false;
                dir = dir * -1;
                //engine.addFloatingText(ship.getLocation(), "emptying target list", 25f, Color.yellow, null, 1f, 1f);
                if (!targets.isEmpty()) {
                    targets.clear();
                }
                canAttack = true;
                attackDuration = new IntervalUtil(1f, 1f);
            }
        }
    }

    //Probably a func for this in lazy lib, but fug it
    public Vector2f getBeamEndpoint(Vector2f origin, float angleRadians, float length) {
        float dx = length * (float) Math.cos(angleRadians);
        float dy = length * (float) Math.sin(angleRadians);
        return new Vector2f(origin.x + dx, origin.y + dy);
    }

    /**
     * Returns the first intersection point of segment [A,B] with target's
     * shield, or null if no shield hit.
     */
    public static Vector2f intersectShield(CombatEntityAPI target, Vector2f A, Vector2f B) {
        if (target == null) {
            return null;
        }
        if (!(target instanceof ShipAPI)) {
            return null;
        }
        ShieldAPI sh = target.getShield();
        if (sh == null || sh.isOff() || sh.getActiveArc() <= 0f) {
            return null;
        }

        // Shield circle
        final Vector2f C = sh.getLocation();
        final float R = sh.getRadius();

        // Segment parametric: P(t) = A + t*(B - A), t in [0,1]
        final float dx = B.x - A.x;
        final float dy = B.y - A.y;

        // Solve |A + t*d - C|^2 = R^2
        final float fx = A.x - C.x;
        final float fy = A.y - C.y;

        final float a = dx * dx + dy * dy;
        final float b = 2f * (dx * fx + dy * fy);
        final float c = fx * fx + fy * fy - R * R;

        final float disc = b * b - 4f * a * c;
        if (disc < 0f || a <= 1e-6f) {
            return null; // no intersection or degenerate segment
        }
        final float s = (float) Math.sqrt(disc);
        // two roots, pick the closest valid t in [0,1]
        float t1 = (-b - s) / (2f * a);
        float t2 = (-b + s) / (2f * a);

        Float bestT = null;
        if (t1 >= 0f && t1 <= 1f) {
            bestT = t1;
        }
        if (t2 >= 0f && t2 <= 1f) {
            if (bestT == null || t2 < bestT) {
                bestT = t2;
            }
        }
        if (bestT == null) {
            return null;
        }

        Vector2f hit = new Vector2f(A.x + dx * bestT, A.y + dy * bestT);

        // Verify the point lies within the shield's current arc
        if (!sh.isWithinArc(hit)) {
            return null;
        }

        return hit;
    }
    private final Color PARTICLE_COLOR = new Color(200, 200, 200);
    private final float PARTICLE_SIZE = 8f;
    private final float PARTICLE_BRIGHTNESS = 150;
    private final float PARTICLE_DURATION = 1f;
    private static final int PARTICLE_COUNT = 15;
    private final float GLOW_SIZE = 10f;
    private static final float CONE_ANGLE = 150f;
    private static final float A_2 = CONE_ANGLE / 2;
    // constant that effects the lower end of the particle velocity
    private static final float VEL_MIN = 0.5f;
    // constant that effects the upper end of the particle velocity
    private static final float VEL_MAX = 1f;

    public void falseOnHit(WeaponAPI weapon, CombatEntityAPI target, Vector2f point) {
        //if we hit something, we don't want to increment recoil
        //lets keep it in memory i guess
        Global.getCombatEngine().getCustomData().put("armaa_drillHit_" + weapon.getShip().getId(), "-");
        if (MagicRender.screenCheck(0.1f, point)) {
            if (Math.random() > 0.30f) {

                // Spawn visual effects
                //cloud
                for (int i = 0; i < 15 * Math.random(); i++) {
                    int grey = MathUtils.getRandomNumberInRange(20, 100);
                    Global.getCombatEngine().addSmokeParticle(
                            MathUtils.getRandomPointInCircle(point, 40),
                            MathUtils.getRandomPointInCone(
                                    new Vector2f(),
                                    30,
                                    weapon.getCurrAngle() + 90,
                                    weapon.getCurrAngle() + 270
                            ),
                            MathUtils.getRandomNumberInRange(30, 60),
                            1,
                            MathUtils.getRandomNumberInRange(2, 5),
                            new Color(
                                    (int) grey / 10,
                                    (int) grey,
                                    (int) (grey / MathUtils.getRandomNumberInRange(1.5f, 2)),
                                    MathUtils.getRandomNumberInRange(8, 32)
                            )
                    );
                }

                for (int x = 0; x < 10 * Math.random(); x++) {
                    Global.getCombatEngine().addHitParticle(
                            point,
                            MathUtils.getRandomPointInCone(new Vector2f(), x * 10, weapon.getCurrAngle() + 90, weapon.getCurrAngle() + 270),
                            15f,
                            1f,
                            2 - (x / 10),
                            new Color(255, 125, 20, 200));
                }
                if (Math.random() > 0.30f) {
                    Global.getCombatEngine().addSmoothParticle(point, new Vector2f(), 50f * (float) Math.random(), 0.8f, 0.25f, new Color(200, 55, 200, 255));
                    Global.getCombatEngine().addHitParticle(point, new Vector2f(), GLOW_SIZE + (float) Math.random() * 25, 1, 0.1f, PARTICLE_COLOR);
                }
            }
            float speed = 300;
            float facing = weapon.getCurrAngle();
            for (int i = 0; i <= PARTICLE_COUNT; i++) {
                float angle = MathUtils.getRandomNumberInRange(facing - A_2,
                        facing + A_2);
                float vel = MathUtils.getRandomNumberInRange(speed * -VEL_MIN,
                        speed * -VEL_MAX);
                Vector2f vector = MathUtils.getPointOnCircumference(null,
                        vel,
                        angle);
                Global.getCombatEngine().addHitParticle(point,
                        vector,
                        PARTICLE_SIZE,
                        PARTICLE_BRIGHTNESS,
                        PARTICLE_DURATION + 1f,
                        new Color(255, 200, 75, 200));
            }
        }

    }

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        //engine.removeEntity(projectile);
        projectile.setDamageAmount(0f);
        projectile.setCollisionClass(CollisionClass.NONE);
        projectile.setHitpoints(0f); // optional; will let engine clean it up

    }

    public Vector2f preciseHitCheck(WeaponAPI weapon, CombatEntityAPI target) {
        Vector2f fire = weapon.getFirePoint(0);
        float angDeg = weapon.getCurrAngle();
        float angRad = (float) Math.toRadians(angDeg);

        // how thick the blade is
        float width = 18f;
        int samples = 5;

        // perpendicular vector to blade
        Vector2f perp = new Vector2f(
                (float) Math.cos(angRad + (float) Math.PI / 2f),
                (float) Math.sin(angRad + (float) Math.PI / 2f)
        );

        for (int i = -samples; i <= samples; i++) {
            float offset = (i / (float) samples) * width;

            // shifted blade origin
            Vector2f origin = new Vector2f(
                    fire.x + perp.x * offset,
                    fire.y + perp.y * offset
            );

            // end of blade swing ray
            Vector2f end = getBeamEndpoint(origin, angRad, weapon.getRange() + 10f);

            // 🔥 LazyLib does the hard work for us
            Vector2f hit = CollisionUtils.getCollisionPoint(origin, end, target);

            if (hit != null && CollisionUtils.isPointWithinBounds(hit, target)) {
                return hit;
            }
        }
        return null;
    }

    public static float distanceFromSegment(Vector2f A, Vector2f B, Vector2f P) {
        Vector2f AB = new Vector2f(B.x - A.x, B.y - A.y);
        Vector2f AP = new Vector2f(P.x - A.x, P.y - A.y);

        float ab2 = AB.lengthSquared();
        if (ab2 == 0) {
            return MathUtils.getDistance(A, P);
        }

        float ap_ab = AP.x * AB.x + AP.y * AB.y;
        float t = ap_ab / ab2;

        t = Math.max(0, Math.min(1, t));

        // projection point: A + t*AB
        Vector2f closest = new Vector2f(A.x + AB.x * t, A.y + AB.y * t);

        return MathUtils.getDistance(P, closest);
    }
}
