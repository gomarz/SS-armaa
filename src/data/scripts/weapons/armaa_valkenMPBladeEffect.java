package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
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

public class armaa_valkenMPBladeEffect implements EveryFrameWeaponEffectPlugin, OnFireEffectPlugin {

    private boolean runOnce = false;
    private ShipAPI ship;

    private boolean swinging = false;
    private boolean cooldownR;
    private boolean beamFired;
    private float dir = 1f;

    private float id2;
    private float ogSpikePos = 0f;
    private float ogShoulderPos = 0f;
    private WeaponAPI shoulder;
    private final float wepRecoilMax = 20f;
    private float recoil = 0f;

    private final IntervalUtil animInterval = new IntervalUtil(0.012f, 0.012f);
    private final IntervalUtil trailInterval = new IntervalUtil(0.05f, 0.05f);

    // Only way I can think of gating logic so we cant hit stuff while on cooldown
    private IntervalUtil attackDuration = new IntervalUtil(0.8f, 0.8f);
    private boolean canAttack = true;

    public float TURRET_OFFSET = 30f;
    private final ArrayList<CombatEntityAPI> targets = new ArrayList<>();

    private final float TORSO_OFFSET = -45f;

    // ---- NEW: full-arc sweep + windup ----
    private boolean windingUp = false;
    private float windT = 0f;
    private float windFrom = 0f;

    private float swingT = 0f;
    private float swingStart = 0f;
    private float swingEnd = 0f;

    // Tune these
    private static final float WINDUP_TIME = 0.10f; // ease into start endpoint
    private static final float SWING_TIME = 0.50f; // time to sweep full arc

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine.isPaused()) {
            return;
        }
        if (weapon == null) {
            return;
        }
        ship = weapon.getShip();
        if (!runOnce) {
            if (ship == null) {
                return;
            }
            id2 = MagicTrailPlugin.getUniqueID();
            ogSpikePos = weapon.getBarrelSpriteAPI().getCenterY();

            for (WeaponAPI wep : ship.getAllWeapons()) {
                if (wep.getSlot().getId().equals("D_PAULDRONL")) {
                    ogShoulderPos = wep.getSprite().getCenterY();
                    shoulder = wep;
                    break;
                }
            }
            runOnce = true;
        }
        // recoil handling
        if (engine.getCustomData().get("armaa_drillHit_" + ship.getId()) != null) {
            engine.getCustomData().remove("armaa_drillHit_" + ship.getId());
            recoil = Math.max(0f, recoil - 8f);
        }
        recoil = wepRecoilMax * weapon.getChargeLevel();
        weapon.getBarrelSpriteAPI().setCenterY(ogSpikePos - recoil);
        // AI nudge
        if (!ship.getAIFlags().hasFlag(ShipwideAIFlags.AIFlags.BACKING_OFF)
                && !ship.getAIFlags().hasFlag(ShipwideAIFlags.AIFlags.DO_NOT_PURSUE)) {
            if (ship.getShipTarget() != null && MathUtils.getDistance(ship, ship.getShipTarget()) < 800 && !weapon.isDisabled()) {
                if (ship.getAI() != null) {
                    engine.headInDirectionWithoutTurning(ship, weapon.getCurrAngle(), ship.getMaxSpeed());
                }
            }
        }

        if (ship.getFluxTracker().isOverloadedOrVenting()) {
            return;
        }

        // charge shaping
        float sineC;
        if (weapon.getChargeLevel() < 1f) {
            sineC = MagicAnim.smoothNormalizeRange(weapon.getChargeLevel(), 0.3f, 0.9f);
            MagicAnim.smoothNormalizeRange(weapon.getChargeLevel(), 0.3f, 1f);
        } else {
            sineC = 1f;
        }

        // side + multiplier (used for trails and the pre-swing "pull")
        boolean leftie = armaa_utils.getWeaponSide(ship.getLocation(), ship.getFacing(), weapon.getLocation());
        float mult = leftie ? 1f : -1f;

        if (weapon.getCooldownRemaining() <= 0f && !weapon.isFiring()) {
            cooldownR = false;
        }

        // small pre-swing bias (kept)
        if (!swinging && !cooldownR && weapon.getChargeLevel() > 0f) {
            weapon.setCurrAngle(weapon.getCurrAngle() + (sineC * (mult * TORSO_OFFSET) * 0.30f) * weapon.getChargeLevel());
        }

        // We arm once when charge hits 1 and we weren't already swinging.
        if (!swinging && weapon.getChargeLevel() >= 1f) {
            swinging = true;
            cooldownR = false;

            // Stable arc center/endpoints based on slot + ship facing (doesn't "move" with currAngle)
            float global = ship.getFacing();
            float center = global + weapon.getSlot().getAngle();
            float halfArc = weapon.getArc() * 0.5f;

            float leftEnd = center - halfArc;
            float rightEnd = center + halfArc;

            // Direction for this attack: dir > 0 = left->right, dir < 0 = right->left
            boolean swingLeftToRight = (dir > 0f);

            swingStart = swingLeftToRight ? leftEnd : rightEnd;
            swingEnd = swingLeftToRight ? rightEnd : leftEnd;

            // Windup from current angle into swingStart (no snapping)
            windFrom = weapon.getCurrAngle();
            windT = 0f;
            windingUp = true;

            // Reset swing progress
            swingT = 0f;
        }

        // ---- DRIVE MOTION: windup then full sweep ----
        if (swinging) {
            animInterval.advance(amount);

            if (windingUp) {
                windT += amount / WINDUP_TIME;
                if (windT >= 1f) {
                    windT = 1f;
                    windingUp = false;
                }
                float tSmooth = MagicAnim.smoothNormalizeRange(windT, 0f, 1f);
                weapon.setCurrAngle(lerpAngle(windFrom, swingStart, tSmooth));
            } else {
                swingT += amount / SWING_TIME;
                if (swingT >= 1f) {
                    swingT = 1f;
                }

                float tSmooth = MagicAnim.smoothNormalizeRange(swingT, 0f, 1f);
                weapon.setCurrAngle(lerpAngle(swingStart, swingEnd, tSmooth));
            }
        }

        // end-of-attack bookkeeping (kept, but updated for new state)
        if (swinging && weapon.getChargeLevel() <= 0f) {
            swinging = false;
            windingUp = false;
            windT = 0f;
            swingT = 0f;
            cooldownR = true;
        }

        // beamFired flag (kept)
        float chargeLevel = weapon.getChargeLevel();
        if (chargeLevel == 1f) {
            beamFired = true;
        }

        // ---- IMPORTANT: gate hit/trail logic until windup is done ----
        // Otherwise you'd be "attacking" while easing into the start endpoint.
        // let's do this so the attack doesnt immediately do damage
        if (weapon.isFiring()
                && swinging
                && !windingUp
                && swingT > 0.01f
                && !attackDuration.intervalElapsed()
                && canAttack) {
            attackDuration.advance(amount);
            trailInterval.advance(amount);

            MagicFakeBeam.spawnFakeBeam(
                    engine,
                    weapon.getFirePoint(0),
                    weapon.getRange() - wepRecoilMax + recoil,
                    weapon.getCurrAngle(),
                    15f,
                    amount,
                    0.15f * chargeLevel,
                    15f,
                    new Color(0f, 0f, 0f, 0f),
                    new Color(0f, 0f, 0f, 0f),
                    0f,
                    DamageType.ENERGY,
                    0f,
                    ship
            );

            float angDeg = weapon.getCurrAngle();
            Vector2f origin = new Vector2f(weapon.getFirePoint(0));
            Vector2f point = getBeamEndpoint(origin, (float) Math.toRadians(angDeg), weapon.getRange() + 10f);

            if (MagicRender.screenCheck(0.2f, weapon.getFirePoint(0)) && trailInterval.intervalElapsed()) {
                Vector2f midpoint = new Vector2f(
                        (weapon.getFirePoint(0).x + point.x) / 2f,
                        (weapon.getFirePoint(0).y + point.y) / 2f
                );

                MagicTrailPlugin.addTrailMemberAdvanced(
                        weapon.getShip(),
                        id2,
                        Global.getSettings().getSprite("fx", "beam_trail_cel"),
                        midpoint,
                        0f,
                        0f,
                        weapon.getCurrAngle() * mult,
                        0f,
                        0f,
                        weapon.getRange() - wepRecoilMax + recoil, weapon.getRange() - wepRecoilMax + recoil,
                        new Color(1f, 1f, 1f, .2f), new Color(1f, 1f, 1f, .2f), 0.5f,
                        0f, 0.2f, 0.1f,
                        true,
                        256f, 0f, 1f,
                        null, null, null, 1f
                );
            }

            for (CombatEntityAPI target : CombatUtils.getEntitiesWithinRange(weapon.getFirePoint(0), weapon.getRange() + 10f)) {
                // no killing stuff on our side
                if (target.getOwner() == ship.getOwner()) {
                    continue;
                }
                if (targets.contains(target)) {
                    continue;
                }

                float angleToTarget = VectorUtils.getAngle(weapon.getFirePoint(0), target.getLocation());
                float swingAngle = weapon.getCurrAngle();
                float diff = MathUtils.getShortestRotation(swingAngle, angleToTarget);

                // within arc
                if (Math.abs(diff) > weapon.getArc() * 0.5f) {
                    continue;
                }

                // ignore projs for now
                if (target instanceof DamagingProjectileAPI && !(target instanceof MissileAPI)) {
                    continue;
                }

                if (target instanceof MissileAPI) {
                    MissileAPI missile = (MissileAPI) target;
                    if (preciseHitCheck(weapon, target) != null) {
                        MagicLensFlare.createSharpFlare(Global.getCombatEngine(), ship, missile.getLocation(), 1, 75, 0, new Color(50,50,50,50), Color.white);
                        engine.applyDamage(missile, missile.getLocation(), weapon.getDamage().getDamage(), weapon.getDamageType(), 0f, false, false, weapon.getShip());
                    }
                    continue;
                }

                if (target instanceof ShipAPI) {
                    ShipAPI shiptarget = (ShipAPI) target;
                    if (shiptarget.isPhased() || shiptarget.getCollisionClass() == CollisionClass.NONE) {
                        continue;
                    }
                }

                // shield first (bubble)
                Vector2f shieldHit = intersectShield(target, weapon.getFirePoint(0), point);
                if (shieldHit != null) {
                    engine.applyDamage(target, shieldHit, weapon.getDamage().getDamage(), weapon.getDamageType(), 0f, false, false, ship, true);
                    falseOnHit(weapon, target, shieldHit);
                    targets.add(target);
                    CombatUtils.applyForce(target, weapon.getCurrAngle(), ship.getMass() * 1.25f);
                    CombatUtils.applyForce(ship, weapon.getCurrAngle() - 180f, Math.min(100f, target.getMass()));
                    float variance = MathUtils.getRandomNumberInRange(-0.3f, .3f);
                    Global.getSoundPlayer().playSound("armaa_polearm", 1.1f + variance, 1f + variance, point, new Vector2f());
                    MagicLensFlare.createSharpFlare(Global.getCombatEngine(), ship, shieldHit, 3, 100, weapon.getCurrAngle(), Color.white, Color.white);
                    //dir *= -1f;                    
                    continue;
                }

                Vector2f preciseHit = preciseHitCheck(weapon, target);
                if (preciseHit != null) {
                    engine.applyDamage(target, preciseHit, weapon.getDamage().getDamage(), weapon.getDamageType(), 0f, false, false, ship, true);
                    falseOnHit(weapon, target, preciseHit);

                    float variance = MathUtils.getRandomNumberInRange(-0.3f, .3f);
                    Global.getSoundPlayer().playSound("armaa_polearm", 1.1f + variance, 1f + variance, point, new Vector2f());

                    MagicLensFlare.createSharpFlare(
                            Global.getCombatEngine(),
                            ship,
                            preciseHit,
                            1,
                            75,
                            weapon.getCurrAngle(),
                            new Color(255, 200, 0, 150),
                            Color.white
                    );

                    for (int i = 0; i < PARTICLE_COUNT; i++) {
                        float speed = (float) Math.random() * 100f;
                        float facing = weapon.getCurrAngle();
                        float angle = MathUtils.getRandomNumberInRange(facing - A_2, facing + A_2);
                        float vel = MathUtils.getRandomNumberInRange(speed * -VEL_MIN, speed * -VEL_MAX);
                        Vector2f vector = MathUtils.getPointOnCircumference(null, vel, angle);

                        Global.getCombatEngine().spawnDebrisSmall(preciseHit, vector, 1, angle, 15f, 5f, 10f, 5f);
                    }

                    CombatUtils.applyForce(target, weapon.getCurrAngle(), ship.getMass() * 1.25f);
                    CombatUtils.applyForce(ship, weapon.getCurrAngle() - 180f, Math.min(100f, target.getMass()));
                    targets.add(target);
                    //dir *= -1f;
                }
            }
        }

        // end-of-cycle reset (kept)
        if (chargeLevel <= 0f && beamFired) {
            beamFired = false;
            dir *= -1f;
            if (!targets.isEmpty()) {
                targets.clear();
            }

            canAttack = true;
            attackDuration = new IntervalUtil(1f, 1f);
        }
        shoulder.getSprite().setCenterY(ogShoulderPos - recoil *0.75f);
    }

    // ---- helpers ----
    private static float lerpAngle(float a, float b, float t) {
        float diff = MathUtils.getShortestRotation(a, b);
        return a + diff * t;
    }

    // Probably a func for this in lazy lib, but fug it
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

        final Vector2f C = sh.getLocation();
        final float R = sh.getRadius();

        final float dx = B.x - A.x;
        final float dy = B.y - A.y;

        final float fx = A.x - C.x;
        final float fy = A.y - C.y;

        final float a = dx * dx + dy * dy;
        final float b = 2f * (dx * fx + dy * fy);
        final float c = fx * fx + fy * fy - R * R;

        final float disc = b * b - 4f * a * c;
        if (disc < 0f || a <= 1e-6f) {
            return null;
        }

        final float s = (float) Math.sqrt(disc);
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
        if (!sh.isWithinArc(hit)) {
            return null;
        }

        return hit;
    }

    private final Color PARTICLE_COLOR = new Color(200, 200, 200);
    private final float PARTICLE_SIZE = 4f;
    private final float PARTICLE_BRIGHTNESS = 150;
    private final float PARTICLE_DURATION = 0.5f;
    private static final int PARTICLE_COUNT = 3;
    private final float GLOW_SIZE = 10f;
    private static final float CONE_ANGLE = 150f;
    private static final float A_2 = CONE_ANGLE / 2f;
    private static final float VEL_MIN = 0.5f;
    private static final float VEL_MAX = 1f;

    public void falseOnHit(WeaponAPI weapon, CombatEntityAPI target, Vector2f point) {
        Global.getCombatEngine().getCustomData().put("armaa_drillHit_" + weapon.getShip().getId(), "-");

        if (MagicRender.screenCheck(0.1f, point)) {
            if (Math.random() > 0.30f) {
                for (int i = 0; i < 5 * Math.random(); i++) {
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
                                    (int) (grey / MathUtils.getRandomNumberInRange(1.5f, 2f)),
                                    MathUtils.getRandomNumberInRange(8, 32)
                            )
                    );
                }

                for (int x = 0; x < 5 * Math.random(); x++) {
                    Global.getCombatEngine().addHitParticle(
                            point,
                            MathUtils.getRandomPointInCone(new Vector2f(), x * 10, weapon.getCurrAngle() + 90, weapon.getCurrAngle() + 270),
                            5f,
                            1f,
                            2 - (x / 10f),
                            new Color(255, 125, 20, 200)
                    );
                }

                if (Math.random() > 0.30f) {
                    Global.getCombatEngine().addSmoothParticle(point, new Vector2f(), 50f * (float) Math.random(), 0.8f, 0.25f, new Color(200, 55, 200, 255));
                    Global.getCombatEngine().addHitParticle(point, new Vector2f(), GLOW_SIZE + (float) Math.random() * 5f, 1, 0.1f, PARTICLE_COLOR);
                }
            }

            float speed = 300f;
            float facing = weapon.getCurrAngle();
            for (int i = 0; i <= PARTICLE_COUNT; i++) {
                float angle = MathUtils.getRandomNumberInRange(facing - A_2, facing + A_2);
                float vel = MathUtils.getRandomNumberInRange(speed * -VEL_MIN, speed * -VEL_MAX);
                Vector2f vector = MathUtils.getPointOnCircumference(null, vel, angle);

                Global.getCombatEngine().addHitParticle(
                        point,
                        vector,
                        PARTICLE_SIZE,
                        PARTICLE_BRIGHTNESS,
                        PARTICLE_DURATION,
                        PARTICLE_COLOR
                );
            }
        }
    }

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        projectile.setDamageAmount(0f);
        projectile.setCollisionClass(CollisionClass.NONE);
        projectile.setHitpoints(0f);
    }

    public Vector2f preciseHitCheck(WeaponAPI weapon, CombatEntityAPI target) {
        Vector2f fire = weapon.getFirePoint(0);
        float angDeg = weapon.getCurrAngle();
        float angRad = (float) Math.toRadians(angDeg);

        float width = 18f;
        int samples = 5;

        Vector2f perp = new Vector2f(
                (float) Math.cos(angRad + (float) Math.PI / 2f),
                (float) Math.sin(angRad + (float) Math.PI / 2f)
        );

        for (int i = -samples; i <= samples; i++) {
            float offset = (i / (float) samples) * width;

            Vector2f origin = new Vector2f(
                    fire.x + perp.x * offset,
                    fire.y + perp.y * offset
            );

            Vector2f end = getBeamEndpoint(origin, angRad, weapon.getRange() + 10f);

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

        t = Math.max(0f, Math.min(1f, t));

        Vector2f closest = new Vector2f(A.x + AB.x * t, A.y + AB.y * t);
        return MathUtils.getDistance(P, closest);
    }
}
