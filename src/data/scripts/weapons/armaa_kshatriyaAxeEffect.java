package data.scripts.weapons;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;

import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.plugins.MagicTrailPlugin;
import org.magiclib.util.MagicAnim;
import org.magiclib.util.MagicFakeBeam;
import org.magiclib.util.MagicLensFlare;
import org.magiclib.util.MagicRender;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ArmorGridAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.FaderUtil;
import com.fs.starfarer.api.util.IntervalUtil;

import data.scripts.util.armaa_meleeSweep;
import data.scripts.util.armaa_meleeSwing;
import data.scripts.util.armaa_utils;


public class armaa_kshatriyaAxeEffect implements EveryFrameWeaponEffectPlugin, OnFireEffectPlugin {

    // ---- swing ramp, configured to match this weapon's original curve ----
    private static final float TICK = 0.012f;
    private static final float RAMP_PER_TICK = 0.5f;
    private static final float PEAK = 9f;
    /** Zero: this blade held its level rather than tapering. */
    private static final float TAPER_PER_TICK = 0f;
    /** Was a separate 1s attackDuration gate. */
    private static final float MAX_SWING_DURATION = 1f;
    /** Zero: damage ran for the whole swing, not just above a threshold. */
    private static final float DAMAGE_FLOOR = 0f;

    // How hard the wielder leans into the swing, in deg/sec^2
    private static final float BODY_ENGLISH_ACCEL = 100f;
    // Spin imparted to targets on hit (scaled by mass), deg/sec
    private static final float TARGET_SPIN_IMPULSE = 540f;
    /**
     * Margin kept from the weapon arc's true edges, so setCurrAngle never
     * fights the engine's own arc clamping at the extremes.
     */
    private static final float ARC_EDGE_MARGIN = 1f;

    private final Color PARTICLE_COLOR = new Color(200, 200, 200);
    private final float PARTICLE_SIZE = 8f;
    private final float PARTICLE_BRIGHTNESS = 150;
    private final float PARTICLE_DURATION = 1f;
    private static final int PARTICLE_COUNT = 15;
    private final float GLOW_SIZE = 10f;
    private static final float CONE_ANGLE = 150f;
    private static final float A_2 = CONE_ANGLE / 2;
    private static final float VEL_MIN = 0.5f;
    private static final float VEL_MAX = 1f;

    private boolean runOnce = false;
    private ShipAPI ship;

    private final armaa_meleeSwing swing = new armaa_meleeSwing(
            TICK, RAMP_PER_TICK, PEAK, TAPER_PER_TICK, MAX_SWING_DURATION, DAMAGE_FLOOR);

    private final IntervalUtil trailInterval = new IntervalUtil(0.05f, 0.05f);
    private final FaderUtil redLevel = new FaderUtil(1f, 2f, 2f);
    private final Set<CombatEntityAPI> targets = new HashSet<CombatEntityAPI>();

    public float TURRET_OFFSET = 30f;
    private float dir = 1f;
    /**
     * Cruiser/capital rebound is latched here and applied between swings. dir
     * must never change mid-windup or mid-swing: startAngle/endAngle are
     * recomputed from it every frame, so an immediate flip swaps the arc edges
     * under the blade and teleports it.
     */
    private boolean pendingDirFlip = false;

    private float id2;
    private float ogSpikePos = 0f;
    private final float wepRecoilMax = 100f;
    private float recoil = 0f;
    private SpriteAPI spr;

    /**
     * Measured blade motion: ground truth for which way the blade is actually
     * sweeping this frame, immune to dir/mult bookkeeping and the windup phase.
     */
    private float prevWeaponAngle = 0f;
    private float lastSweepSign = 1f;

    /**
     * Windup: captured once when charging starts, then interpolated to zero as
     * chargeLevel rises. Drives the blade angle ABSOLUTELY each frame so
     * nothing else (engine aim slewing) can fight it, and the blade arrives at
     * the start edge exactly when the swing begins - no snap needed.
     */
    private float windupOffset = 0f;
    private boolean windupCaptured = false;
    private float prevChargeLevel = 0f;

    // previous blade pose, for the swept hit test
    private final Vector2f prevA = new Vector2f();
    private final Vector2f prevB = new Vector2f();
    private boolean prevPoseValid = false;

    // per-hit scratch, set before each sweep so the handler can read them
    private float hitForceAngle = 0f;
    private Vector2f hitBeamPoint = new Vector2f();

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
        }
        recoil = wepRecoilMax * weapon.getChargeLevel();

        renderGhost(amount, weapon);
        weapon.getBarrelSpriteAPI().setCenterY(ogSpikePos - recoil);
        spr.setCenterY(ogSpikePos - recoil);

        if (!ship.getAIFlags().hasFlag(ShipwideAIFlags.AIFlags.BACKING_OFF)
                && !ship.getAIFlags().hasFlag(ShipwideAIFlags.AIFlags.DO_NOT_PURSUE)) {
            if (ship.getShipTarget() != null
                    && MathUtils.getDistance(ship, ship.getShipTarget()) < 800
                    && !weapon.isDisabled()
                    && ship.getAI() != null) {
                engine.headInDirectionWithoutTurning(ship, weapon.getCurrAngle(), ship.getMaxSpeed());
            }
        }

        if (ship.getFluxTracker().isOverloadedOrVenting()) {
            swing.reset();
            prevPoseValid = false;
            return;
        }

        // ---- geometry of the upcoming or in-progress sweep ----
        boolean leftie = armaa_utils.getWeaponSide(ship.getLocation(), ship.getFacing(),
                weapon.getLocation());
        float mult = leftie ? 1f : -1f;
        float upcomingSweep = (mult == 1f) ? Math.signum(dir) : -Math.signum(dir);

        float arcCenter = ship.getFacing() + weapon.getArcFacing();
        float swingHalfArc = Math.max(0f, weapon.getArc() / 2f - ARC_EDGE_MARGIN);
        float startAngle = arcCenter - upcomingSweep * swingHalfArc;
        float endAngle = arcCenter + upcomingSweep * swingHalfArc;

        swing.advance(amount, weapon);

        // ---- windup: runs whenever the charge is RISING ----
        float curCharge = weapon.getChargeLevel();
        boolean chargingUp = curCharge > prevChargeLevel;
        prevChargeLevel = curCharge;

        if (!swing.isSwinging() && chargingUp) {
            if (!windupCaptured) {
                windupOffset = MathUtils.getShortestRotation(startAngle, weapon.getCurrAngle());
                windupCaptured = true;
            }
            float eased = MagicAnim.smoothNormalizeRange(curCharge, 0f, 1f);
            weapon.setCurrAngle(startAngle + windupOffset * (1f - eased));
        }
        if (curCharge <= 0f) {
            windupCaptured = false;
        }
        if (curCharge >= 1f && !swing.isSwinging()) {
            // Safety net: the windup interpolation should have landed us here
            // already, so this is normally a zero-distance set.
            weapon.setCurrAngle(startAngle);
            windupCaptured = false;
            swing.beginSwing();
        }

        // ---- sweep the blade toward the far arc edge ----
        if (swing.isSwinging()
                && Math.abs(MathUtils.getShortestRotation(weapon.getCurrAngle(), endAngle)) > 0.1f) {
            float remaining = MathUtils.getShortestRotation(weapon.getCurrAngle(), endAngle);
            float step = swing.getLevel() * upcomingSweep;
            if (Math.abs(step) > Math.abs(remaining)) {
                step = remaining;
            }
            weapon.setCurrAngle(weapon.getCurrAngle() + step);
        }

        // Measure actual blade motion this frame. All setCurrAngle updates have
        // already happened, so the delta is ground truth. Subtract the hull's
        // own rotation to isolate pure blade movement.
        float angleDelta = MathUtils.getShortestRotation(prevWeaponAngle, weapon.getCurrAngle());
        prevWeaponAngle = weapon.getCurrAngle();
        float bladeDelta = angleDelta - ship.getAngularVelocity() * amount;
        if (Math.abs(bladeDelta) > 0.05f) {
            lastSweepSign = Math.signum(bladeDelta);
        }
        final float sweepSign = lastSweepSign;

        // ---- damage window ----
        if (swing.isDamaging()) {

            if (swing.isSwinging()) {
                ship.setAngularVelocity(
                        ship.getAngularVelocity() + BODY_ENGLISH_ACCEL * sweepSign * amount);
            }
            trailInterval.advance(amount);

            float reach = weapon.getRange() + 10f - wepRecoilMax + recoil;
            Vector2f bladeA = new Vector2f(weapon.getFirePoint(0));
            Vector2f bladeB = armaa_utils.getBeamEndpoint(bladeA,
                    (float) Math.toRadians(weapon.getCurrAngle()), reach);

            float chargeLevel = weapon.getChargeLevel();
            MagicFakeBeam.spawnFakeBeam(engine, bladeA, weapon.getRange() - wepRecoilMax + recoil,
                    weapon.getCurrAngle(), 15f, amount, 0.15f * chargeLevel, 15f,
                    new Color(0f, 0f, 0f, 0f), new Color(0f, 0f, 0f, 0f),
                    0f, DamageType.ENERGY, 0f, ship);

            renderTrail(weapon, bladeA, bladeB, mult);

            if (!prevPoseValid) {
                prevA.set(bladeA);
                prevB.set(bladeB);
                prevPoseValid = true;
            }

            hitForceAngle = weapon.getCurrAngle() + 90f * sweepSign;
            hitBeamPoint = bladeB;

            final WeaponAPI wep = weapon;
            final CombatEngineAPI eng = engine;

            armaa_meleeSweep.sweep(ship, prevA, prevB, bladeA, bladeB, targets,
                    new armaa_meleeSweep.HitHandler() {
                @Override
                public void onHit(CombatEntityAPI target, Vector2f point,
                        boolean shieldHit, float sweepAngle) {
                    applyAxeHit(eng, wep, target, point, shieldHit);
                }
            });

            prevA.set(bladeA);
            prevB.set(bladeB);
        } else {
            prevPoseValid = false;
        }

        // ---- swing finished: alternate sides and reset the hit set ----
        if (swing.consumeEnded()) {
            dir = dir * -1;
            // Rebound off a cruiser/capital: cancel the alternation so the next
            // swing comes back from the same side, without ever moving the arc
            // mid-swing.
            if (pendingDirFlip) {
                dir = dir * -1;
                pendingDirFlip = false;
            }
            targets.clear();
        }
    }

    // ------------------------------------------------------------------
    // hits
    // ------------------------------------------------------------------

    private void applyAxeHit(CombatEngineAPI engine, WeaponAPI weapon,
            CombatEntityAPI target, Vector2f point, boolean shieldHit) {

        // Missiles are simply cut down.
        if (target instanceof MissileAPI) {
            MagicLensFlare.createSharpFlare(engine, ship, target.getLocation(),
                    5, 600, 0, Color.white, Color.white);
            engine.applyDamage(target, target.getLocation(),
                    weapon.getDamage().getDamage(), weapon.getDamageType(),
                    0f, false, false, ship);
            return;
        }

        if (target instanceof ShipAPI) {
            spinTarget((ShipAPI) target, point);
        }

        engine.applyDamage(target, point, weapon.getDamage().getDamage(),
                weapon.getDamageType(), 0f, false, false, ship, true);

        falseOnHit(weapon, target, point);

        float variance = MathUtils.getRandomNumberInRange(-0.3f, .3f);
        Global.getSoundPlayer().playSound("armaa_polearm",
                1.1f + variance, 1f + variance, hitBeamPoint, new Vector2f());

        if (shieldHit) {
            MagicLensFlare.createSharpFlare(engine, ship, point,
                    10, 400, weapon.getCurrAngle(), Color.white, Color.white);
            return;
        }

        // Hull contact only: the axe feeds off what it cuts.
        if (target instanceof ShipAPI && !((ShipAPI) target).isHulk()) {
            regenArmor(engine);
        }

        MagicLensFlare.createSharpFlare(engine, ship, point,
                10, 600, weapon.getCurrAngle(),
                new Color(255, 200, 0, 200), Color.white);

        for (int i = 0; i < PARTICLE_COUNT; i++) {
            float speed = (float) Math.random() * 400f;
            float facing = weapon.getCurrAngle();
            float angle = MathUtils.getRandomNumberInRange(facing - A_2, facing + A_2);
            float vel = MathUtils.getRandomNumberInRange(speed * -VEL_MIN, speed * -VEL_MAX);
            Vector2f vector = MathUtils.getPointOnCircumference(null, vel, angle);
            if (Math.random() > 0.25f) {
                engine.spawnDebrisMedium(hitBeamPoint, vector,
                        MathUtils.getRandomNumberInRange(1, 3), angle, 25f, 5f, 30f, 45f);
            } else {
                engine.spawnDebrisLarge(hitBeamPoint, vector,
                        MathUtils.getRandomNumberInRange(1, 3), angle, 25f, 5f, 30f, 45f);
            }
        }

        // Shove the target along the direction of the sweep, not down the blade axis
        org.lazywizard.lazylib.combat.CombatUtils.applyForce(target, hitForceAngle,
                ship.getMass() * 1.5f);
    }

    /**
     * Torque the target about its own centre of mass. The lever arm from centre
     * to impact point crossed with the sweep direction gives the spin sign, so
     * a hit near an edge spins harder than one through the middle.
     */
    private void spinTarget(ShipAPI enemy, Vector2f point) {
        Vector2f f = MathUtils.getPointOnCircumference(null, 1f, hitForceAngle);
        Vector2f r = Vector2f.sub(point, enemy.getLocation(), null);
        float spinSign = Math.signum(r.x * f.y - r.y * f.x);
        float massFactor = 250f / Math.max(50f, enemy.getMass());
        enemy.setAngularVelocity(
                enemy.getAngularVelocity() + TARGET_SPIN_IMPULSE * spinSign * massFactor);

        if (enemy.isCruiser() || enemy.isCapital()) {
            pendingDirFlip = true;
        }
    }

    private void regenArmor(CombatEngineAPI engine) {
        ArmorGridAPI armorGrid = ship.getArmorGrid();
        float[][] grid = armorGrid.getGrid();
        float max = armorGrid.getMaxArmorInCell();
        float statusMult = ship.getFluxTracker().isOverloaded() ? 0.5f : 1f;
        float maxArmor = armorGrid.getMaxArmorInCell() * ship.getHullSpec().getArmorRating();
        float baseCell = Math.min(maxArmor, 2000f) / armorGrid.getArmorRating();
        float repairAmount = baseCell * (10f / 100f) * statusMult * engine.getElapsedInLastFrame();

        for (int x = 0; x < grid.length; x++) {
            for (int y = 0; y < grid[0].length; y++) {
                if (grid[x][y] < max) {
                    armorGrid.setArmorValue(x, y, Math.min(grid[x][y] + repairAmount, max));
                }
            }
        }
        ship.syncWithArmorGridState();
    }

    // ------------------------------------------------------------------
    // presentation
    // ------------------------------------------------------------------

    private void renderGhost(float amount, WeaponAPI weapon) {
        Vector2f recoilOffset = MathUtils.getPointOnCircumference(null, recoil, weapon.getCurrAngle());
        Vector2f renderLoc = new Vector2f(weapon.getLocation().x + recoilOffset.x,
                weapon.getLocation().y + recoilOffset.y);

        redLevel.advance(amount);
        if (redLevel.getBrightness() >= 1f) {
            redLevel.fadeOut();
        } else if (redLevel.getBrightness() <= 0f) {
            redLevel.fadeIn();
        }

        MagicRender.battlespace(spr, renderLoc, new Vector2f(),
                new Vector2f(spr.getWidth(), spr.getHeight()), new Vector2f(),
                weapon.getCurrAngle() - 90f, 0,
                new Color(1f, 1f, 1f, 0.95f * redLevel.getBrightness()),
                false, 0, 0, 0, 0, 0, 0f, amount, 0f,
                CombatEngineLayers.FIGHTERS_LAYER);
    }

    private void renderTrail(WeaponAPI weapon, Vector2f from, Vector2f to, float mult) {
        if (!MagicRender.screenCheck(0.2f, from) || !trailInterval.intervalElapsed()) {
            return;
        }
        Vector2f midpoint = new Vector2f((from.x + to.x) / 2f, (from.y + to.y) / 2f);
        float len = weapon.getRange() - wepRecoilMax + recoil;
        MagicTrailPlugin.addTrailMemberAdvanced(
                ship, id2,
                Global.getSettings().getSprite("fx", "beam_trail_cel"),
                midpoint, 0f, 0f,
                weapon.getCurrAngle() + 90f * mult, 0f, 0f,
                len, len,
                new Color(1f, 1f, 1f, .2f), new Color(1f, 1f, 1f, .2f), 0.5f,
                0f, 0.2f, 0.1f, true,
                256f, 0f, 1f, null, null, null, 1f);
    }

    public void falseOnHit(WeaponAPI weapon, CombatEntityAPI target, Vector2f point) {
        // if we hit something, we don't want to increment recoil
        Global.getCombatEngine().getCustomData()
                .put("armaa_drillHit_" + weapon.getShip().getId(), "-");

        if (!MagicRender.screenCheck(0.1f, point)) {
            return;
        }

        if (Math.random() > 0.30f) {
            for (int i = 0; i < 15 * Math.random(); i++) {
                int grey = MathUtils.getRandomNumberInRange(20, 100);
                Global.getCombatEngine().addSmokeParticle(
                        MathUtils.getRandomPointInCircle(point, 40),
                        MathUtils.getRandomPointInCone(new Vector2f(), 30,
                                weapon.getCurrAngle() + 90, weapon.getCurrAngle() + 270),
                        MathUtils.getRandomNumberInRange(30, 60), 1,
                        MathUtils.getRandomNumberInRange(2, 5),
                        new Color(grey / 10, grey,
                                (int) (grey / MathUtils.getRandomNumberInRange(1.5f, 2)),
                                MathUtils.getRandomNumberInRange(8, 32)));
            }

            for (int x = 0; x < 10 * Math.random(); x++) {
                Global.getCombatEngine().addHitParticle(point,
                        MathUtils.getRandomPointInCone(new Vector2f(), x * 10,
                                weapon.getCurrAngle() + 90, weapon.getCurrAngle() + 270),
                        15f, 1f, 2 - (x / 10), new Color(255, 125, 20, 200));
            }
            if (Math.random() > 0.30f) {
                Global.getCombatEngine().addSmoothParticle(point, new Vector2f(),
                        50f * (float) Math.random(), 0.8f, 0.25f, new Color(200, 55, 200, 255));
                Global.getCombatEngine().addHitParticle(point, new Vector2f(),
                        GLOW_SIZE + (float) Math.random() * 25, 1, 0.1f, PARTICLE_COLOR);
            }
        }

        float speed = 300;
        float facing = weapon.getCurrAngle();
        for (int i = 0; i <= PARTICLE_COUNT; i++) {
            float angle = MathUtils.getRandomNumberInRange(facing - A_2, facing + A_2);
            float vel = MathUtils.getRandomNumberInRange(speed * -VEL_MIN, speed * -VEL_MAX);
            Vector2f vector = MathUtils.getPointOnCircumference(null, vel, angle);
            Global.getCombatEngine().addHitParticle(point, vector,
                    PARTICLE_SIZE, PARTICLE_BRIGHTNESS, PARTICLE_DURATION + 1f,
                    new Color(255, 200, 75, 200));
        }
    }

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        projectile.setDamageAmount(0f);
        projectile.setCollisionClass(CollisionClass.NONE);
        projectile.setHitpoints(0f);
    }
}
