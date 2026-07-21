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
    // Cruiser/capital rebound is latched here and applied between swings.
    // dir must never change mid-windup/mid-swing: startAngle/endAngle are
    // recomputed from it every frame, so an immediate flip swaps the arc
    // edges under the blade and teleports it (the inside-bounds glitch).
    private boolean pendingDirFlip = false;
    private float id2;
    private float ogSpikePos = 0f;
    private final float wepRecoilMax = 100f;
    private float recoil = 0f;
    private final float TORSO_OFFSET = -45;
    private SpriteAPI spr;

    // Measured blade motion: ground truth for which way the blade is actually
    // sweeping this frame, immune to dir/mult bookkeeping and the windup phase.
    private float prevWeaponAngle = 0f;
    private float lastSweepSign = 1f;

    // Windup: captured once when charging starts, then interpolated to zero
    // as chargeLevel rises. Drives the blade angle ABSOLUTELY each frame so
    // nothing else (engine aim slewing) can fight it, and the blade arrives
    // at the start edge exactly when the swing begins -- no snap needed.
    private float windupOffset = 0f;
    private boolean windupCaptured = false;
    // Last frame's charge level, used to detect a RISING charge (= windup phase)
    private float prevChargeLevel = 0f;

    // How hard the wielder leans into the swing, in deg/sec^2
    private static final float BODY_ENGLISH_ACCEL = 100f;
    // Spin imparted to targets on hit (scaled by mass), deg/sec
    private static final float TARGET_SPIN_IMPULSE = 540f;

    // Margin kept from the weapon arc's true edges, so setCurrAngle never
    // fights the engine's own arc clamping at the extremes.
    private static final float ARC_EDGE_MARGIN = 1f;

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
            prevWeaponAngle = weapon.getCurrAngle();
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

            // The direction the UPCOMING (or in-progress) sweep travels.
            // Derived the same way the swing block applies motion below, so the
            // windup start side, the swing motion, and the swing end target all
            // agree with each other. Recomputed every frame so the mid-swing
            // dir flip (cruiser/capital hits) stays consistent too.
            float upcomingSweep = (mult == 1f) ? Math.signum(dir) : -Math.signum(dir);

            // Swing spans the weapon's FULL arc: from one edge of the slot's
            // arc to the other. getArcFacing() is the arc center relative to
            // ship facing, getArc() is its total width.
            float arcCenter = ship.getFacing() + weapon.getArcFacing();
            float swingHalfArc = Math.max(0f, weapon.getArc() / 2f - ARC_EDGE_MARGIN);
            float startAngle = arcCenter - upcomingSweep * swingHalfArc;
            float endAngle = arcCenter + upcomingSweep * swingHalfArc;

            if (weapon.getCooldownRemaining() <= 0f && !weapon.isFiring()) {
                cooldownR = false;
            }

            // Windup runs whenever the charge is RISING. Gating on cooldownR
            // deadlocked under sustained attacking: the next charge starts the
            // same frame cooldown ends, so isFiring() never reads false and
            // cooldownR never clears -- skipping the windup entirely and
            // leaving the swing-start snap as a visible full-arc jump on
            // every other swing.
            float curCharge = weapon.getChargeLevel();
            boolean chargingUp = curCharge > prevChargeLevel;
            prevChargeLevel = curCharge;

            if (!swinging && chargingUp) {
                // Capture where the blade is relative to the start edge once,
                // at the moment charging begins.
                if (!windupCaptured) {
                    windupOffset = MathUtils.getShortestRotation(startAngle, weapon.getCurrAngle());
                    windupCaptured = true;
                }
                // Interpolate the offset to zero across the full charge, with
                // smooth easing. Setting the angle absolutely (rather than
                // nudging it) means the engine's own aim slewing can't fight
                // us, and the blade lands on the start edge exactly as the
                // charge completes -- so the swing-start snap below is a no-op.
                float eased = MagicAnim.smoothNormalizeRange(weapon.getChargeLevel(), 0f, 1f);
                weapon.setCurrAngle(startAngle + windupOffset * (1f - eased));
            }
            if (weapon.getChargeLevel() <= 0f) {
                windupCaptured = false;
            }
            if (weapon.getChargeLevel() >= 1f) {
                if (!swinging) {
                    // Safety net: the windup interpolation should have landed us
                    // here already, so this is normally a zero-distance set.
                    weapon.setCurrAngle(startAngle);
                    windupCaptured = false;
                }
                swinging = true;
            }

            if (swinging && Math.abs(MathUtils.getShortestRotation(weapon.getCurrAngle(), endAngle)) > 0.1f) {
                animInterval.advance(amount);

                // Sweep toward the far arc edge, clamped so we stop exactly on
                // it instead of oscillating around the end condition.
                float remaining = MathUtils.getShortestRotation(weapon.getCurrAngle(), endAngle);
                float step = swingLevel * upcomingSweep;
                if (Math.abs(step) > Math.abs(remaining)) {
                    step = remaining;
                }
                weapon.setCurrAngle(weapon.getCurrAngle() + step);
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

            // Measure actual blade motion this frame. All setCurrAngle updates for
            // this frame have already happened above, so the delta is ground truth.
            // Subtract the hull's own rotation so we isolate pure blade movement.
            float angleDelta = MathUtils.getShortestRotation(prevWeaponAngle, weapon.getCurrAngle());
            prevWeaponAngle = weapon.getCurrAngle();
            float bladeDelta = angleDelta - ship.getAngularVelocity() * amount;
            if (Math.abs(bladeDelta) > 0.05f) {
                lastSweepSign = Math.signum(bladeDelta);
            }
            float sweepSign = lastSweepSign;

            // Gated on swinging: isFiring() is true during the entire charge-up,
            // so without this the blade is live mid-windup  inside a big ship's
            // bounds that registers a hit on frame one and triggers the rebound
            // flip while the sweep geometry is still being derived.
            if (weapon.isFiring() && !attackDuration.intervalElapsed() && canAttack && swinging) {
                // Body english: lean the hull into the cut. Scaled by amount so it
                // doesn't accumulate per-frame, and gated on swinging so the windup
                // doesn't torque us the wrong way.
                if (swinging) {
                    ship.setAngularVelocity(ship.getAngularVelocity() + BODY_ENGLISH_ACCEL * sweepSign * amount);
                }
                attackDuration.advance(amount);
                trailInterval.advance(amount);
                MagicFakeBeam.spawnFakeBeam(engine, weapon.getFirePoint(0), weapon.getRange() - wepRecoilMax + recoil, weapon.getCurrAngle(), 15f, amount, 0.15f * chargeLevel, 15f, new Color(0f, 0f, 0f, 0f), new Color(0f, 0f, 0f, 0f), 0f, DamageType.ENERGY, 0f, ship);

                float angDeg = weapon.getCurrAngle();
                Vector2f origin = new Vector2f(weapon.getFirePoint(0));

                // Endpoint scales with recoil the same way preciseHitCheck does,
                // so shield hits respect the blade's current extension instead
                // of always reaching full range.
                Vector2f point = armaa_utils.getBeamEndpoint(origin, (float) Math.toRadians(angDeg), weapon.getRange() + 10f - wepRecoilMax + recoil);

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
                            weapon.getRange() - wepRecoilMax + recoil, weapon.getRange() - wepRecoilMax + recoil,
                            new Color(1f, 1f, 1f, .2f), new Color(1f, 1f, 1f, .2f), 0.5f,
                            0f, 0.2f, 0.1f,
                            true,
                            256f, 0f, 1f,
                            null, null, null, 1f);
                    //firedTrail = true;
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
                        if (armaa_utils.preciseHitCheck(weapon, target,wepRecoilMax, recoil) != null) {
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
                    Vector2f shieldHit = armaa_utils.intersectShield(target, weapon.getFirePoint(0), point);
                    float forceAngle = weapon.getCurrAngle() + 90f * sweepSign;
                    if (shieldHit != null) {
                        if (target instanceof ShipAPI) {
                            ShipAPI enemy = (ShipAPI) target;

                            Vector2f f = MathUtils.getPointOnCircumference(null, 1f, forceAngle);

                            // lever arm from target's center of mass to the impact point
                            Vector2f r = Vector2f.sub(shieldHit, enemy.getLocation(), null);

                            // 2D cross product gives the torque direction
                            float spinSign = Math.signum(r.x * f.y - r.y * f.x);

                            float massFactor = 250f / Math.max(50f, enemy.getMass());
                            enemy.setAngularVelocity(enemy.getAngularVelocity() + TARGET_SPIN_IMPULSE * spinSign * massFactor);

                            if (enemy.isCruiser() || enemy.isCapital()) {
                                pendingDirFlip = true;
                            }
                        }
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
                    Vector2f preciseHit = armaa_utils.preciseHitCheck(weapon, target,wepRecoilMax, recoil);
                    if (preciseHit != null) {
                        // Direction the blade edge is moving at the moment of impact:
                        // tangential, perpendicular to the blade angle, signed by the
                        // measured sweep direction.

                        if (target instanceof ShipAPI) {
                            ShipAPI enemy = (ShipAPI) target;

                            Vector2f f = MathUtils.getPointOnCircumference(null, 1f, forceAngle);

                            // lever arm from target's center of mass to the impact point
                            Vector2f r = Vector2f.sub(preciseHit, enemy.getLocation(), null);

                            // 2D cross product gives the torque direction
                            float spinSign = Math.signum(r.x * f.y - r.y * f.x);

                            float massFactor = 250f / Math.max(50f, enemy.getMass());
                            enemy.setAngularVelocity(enemy.getAngularVelocity() + TARGET_SPIN_IMPULSE * spinSign * massFactor);

                            if (enemy.isCruiser() || enemy.isCapital()) {
                                pendingDirFlip = true;
                            }
                        }
                        engine.applyDamage(target, preciseHit, weapon.getDamage().getDamage(), weapon.getDamageType(), 0f, false, false, ship, true);
                        // Hulk harvesting: chew up wrecks to regenerate hull and armor
                        if (target instanceof ShipAPI) {
                            ShipAPI enemy = (ShipAPI) target;
                            if (enemy.isHulk()) {
                                ship.setHitpoints(Math.min(ship.getHullLevelAtDeployment() * ship.getMaxHitpoints(), ship.getHitpoints() + Math.max(weapon.getDamage().getDamage() / 10f, 20f)));
                                ArmorGridAPI armorGrid = ship.getArmorGrid();
                                float[][] grid = armorGrid.getGrid();
                                float max = armorGrid.getMaxArmorInCell();
                                float statusMult = (ship.getFluxTracker().isOverloaded()) ? 0.5f : 1f;
                                float maxArmor = armorGrid.getMaxArmorInCell() * ship.getHullSpec().getArmorRating();
                                float baseCell = Math.min(maxArmor, 2000f) / armorGrid.getArmorRating();
                                float repairAmount = baseCell * (10f / 100f) * statusMult * engine.getElapsedInLastFrame();

                                for (int x = 0; x < grid.length; x++) {
                                    for (int y = 0; y < grid[0].length; y++) {
                                        if (grid[x][y] < max) {
                                            float regen = Math.min(grid[x][y] + repairAmount, max);
                                            armorGrid.setArmorValue(x, y, regen);
                                        }
                                    }
                                }
                                ship.syncWithArmorGridState();
                            }
                        }
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
                        // Shove the target along the direction of the sweep, not down the blade axis
                        CombatUtils.applyForce(target, forceAngle, ship.getMass() * 1.5f);
                        targets.add(target);
                    }
                }
            }

            if (chargeLevel <= 0 && beamFired) {
                beamFired = false;
                dir = dir * -1;
                // Rebound off a cruiser/capital: cancel the alternation so the
                // next swing comes back from the same side, matching the old
                // net behavior without ever moving the arc mid-swing.
                if (pendingDirFlip) {
                    dir = dir * -1;
                    pendingDirFlip = false;
                }
                //engine.addFloatingText(ship.getLocation(), "emptying target list", 25f, Color.yellow, null, 1f, 1f);
                if (!targets.isEmpty()) {
                    targets.clear();
                }
                canAttack = true;
                attackDuration = new IntervalUtil(1f, 1f);
            }
        }
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