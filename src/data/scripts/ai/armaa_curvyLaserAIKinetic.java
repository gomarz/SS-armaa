package data.scripts.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.EmpArcEntityAPI.EmpArcParams;
import com.fs.starfarer.api.impl.combat.MoteControlScript;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import data.scripts.util.armaa_utils;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicFakeBeam;
import org.magiclib.util.MagicRender;
import org.magiclib.util.MagicTargeting;

public class armaa_curvyLaserAIKinetic extends BaseCombatLayeredRenderingPlugin implements MissileAIPlugin, GuidedMissileAI {
    //This script combines the cyroflamer script with the Projectile Tracking script developed by Nicke535
    //Modified by shoi

    //////////////////////
    //     SETTINGS     //
    //////////////////////

    //Damping of the turn speed when closing on the desired aim. The smaller the snappier.
    private final float DAMPING = 0.1f;

    //max speed of the missile after modifiers.
    private final float MAX_SPEED;
    private CombatEngineAPI engine;
    private final MissileAPI missile;
    private CombatEntityAPI target;
    private CombatEntityAPI moveTarget;
    private Vector2f lead = new Vector2f();
    private Vector2f ZERO = new Vector2f();
    private boolean launch = true;
    private IntervalUtil trailtimer = new IntervalUtil(0.15f, 0.15f);
    private IntervalUtil timer = new IntervalUtil(0.2f, 0.3f);
    private IntervalUtil empTimer = new IntervalUtil(1f, 2f);
    private IntervalUtil fireTimer = new IntervalUtil(1f, 1f);
    private IntervalUtil armingTimer = new IntervalUtil(2f, 4f);
    private IntervalUtil targetingTimer = new IntervalUtil (0.5f,0.5f);
    private static final Color MUZZLE_FLASH_COLOR = new Color(255, 255, 255, 50);
    private static final Color MUZZLE_FLASH_COLOR_END = new Color(199, 0, 0, 100);
    private static final Color MUZZLE_FLASH_COLOR_ALT = new Color(200, 200, 200, 100);
    private static final Color MUZZLE_FLASH_COLOR_ALT_END = new Color(200, 0, 0, 100);
    private static final Color MUZZLE_FLASH_COLOR_GLOW = new Color(200, 200, 200, 50);
    private static final Color MUZZLE_FLASH_COLOR_GLOW_END = new Color(255, 0, 0, 100);
    private static final float MUZZLE_FLASH_DURATION = 0.10f;
    private static final float MUZZLE_FLASH_SIZE = 40.0f;

    private boolean inRange = false;
    private int count = 0; // number of proj's that have been created;
    private int total = 0;
    private int beamLength = 1; //The number of projectiles making up the stream;
    private int beamNo = 0;
    private boolean primed = false;
    private float fluxLevel = 0f;
    private float angle = 0f;
    private float angleIncrease = 0f;
    private int side = 1;
    private String targetPointKey;
    private static final float CONE_ANGLE = 180f;
    // one half of the angle. used internally, don't mess with thos
    private static final float A_2 = CONE_ANGLE / 2;
    private List<MissileAPI> alreadyRegisteredProjectiles = new ArrayList<MissileAPI>();

    public armaa_curvyLaserAIKinetic(MissileAPI missile, ShipAPI launchingShip) {

        if (engine != Global.getCombatEngine()) {
            this.engine = Global.getCombatEngine();
        }
        this.missile = missile;
        moveTarget = missile.getSource();
        armingTimer = new IntervalUtil(missile.getArmingTime(), missile.getArmingTime());
        fluxLevel = launchingShip.getFluxTracker().getFluxLevel();
        WeaponAPI weapon = missile.getWeapon();
        MAX_SPEED = missile.getMaxSpeed();
        missile.setCollisionClass(CollisionClass.MISSILE_NO_FF);
        if (Math.random() > 0.5f) {
            angle = (MathUtils.getRandomNumberInRange(missile.getFacing() - A_2, missile.getFacing() + A_2)) * side;
        }
        targetPointKey = "armaa_beamTarget_" + weapon.getShip().getId() + "_" + weapon.getSlot().getId() + "_" + beamNo;

    }

    @Override
    public void advance(float amount) {
        Color color = armaa_utils.shiftColor(MUZZLE_FLASH_COLOR, MUZZLE_FLASH_COLOR_END, missile.getFlightTime() / missile.getMaxFlightTime());
        Color colorGlow = armaa_utils.shiftColor(MUZZLE_FLASH_COLOR_GLOW, MUZZLE_FLASH_COLOR_GLOW_END, missile.getFlightTime() / missile.getMaxFlightTime());
        missile.setDestroyedExplosionColorOverride(colorGlow);
        engine.addSmoothParticle(missile.getLocation(), ZERO, MUZZLE_FLASH_SIZE, 1f, MUZZLE_FLASH_DURATION * 4f, colorGlow);
        if (missile.isFizzling() || missile.isFading() || primed) {
            if (MagicRender.screenCheck(0.25f, missile.getLocation())) {
                engine.addHitParticle(missile.getLocation(), missile.getVelocity(), 100 * fluxLevel, 1f, 0.1f, Color.white);
            }
            DamagingExplosionSpec boom = new DamagingExplosionSpec(
                    1f * fluxLevel,
                    Math.max(75, 150 * fluxLevel),
                    75,
                    missile.getDamage().getDamage() * fluxLevel,
                    missile.getDamage().getDamage() * fluxLevel,
                    CollisionClass.MISSILE_FF,
                    CollisionClass.PROJECTILE_FIGHTER,
                    2,
                    5,
                    5,
                    25,
                    colorGlow,
                    color
            );
            boom.setDamageType(DamageType.ENERGY);
            boom.setShowGraphic(false);
            boom.setSoundSetId("mine_explosion");
            engine.spawnDamagingExplosion(boom, missile.getSource(), missile.getLocation(), false);

            //visual effect
            engine.addNebulaParticle(missile.getLocation(),
                    new Vector2f(missile.getVelocity().getX() / 2, missile.getVelocity().getY() / 2),
                    30f * (0.75f + (float) Math.random() * 0.5f) * fluxLevel,
                    5f + 3f * 15f * fluxLevel,
                    0f,
                    0f,
                    1f,
                    color);
            engine.addNebulaSmoothParticle(missile.getLocation(),
                    new Vector2f(missile.getVelocity().getX() / 2, missile.getVelocity().getY() / 2),
                    25f * (0.75f + (float) Math.random() * 0.5f) * fluxLevel,
                    5f + 2f * 15f * fluxLevel,
                    0f,
                    0f,
                    1f,
                    color);
            engine.addSmoothParticle(missile.getLocation(), new Vector2f(0, 0), Math.max(75, 300 * 2 * fluxLevel), 2, 0.1f, Color.white);
            engine.addHitParticle(missile.getLocation(), new Vector2f(0, 0), Math.max(75, 300 * 2 * fluxLevel), 1, 0.4f, MUZZLE_FLASH_COLOR_ALT);
            engine.spawnExplosion(missile.getLocation(), new Vector2f(0, 0), color, Math.max(50, 125 * 2 * fluxLevel), 2);
            engine.spawnExplosion(missile.getLocation(), new Vector2f(0, 0), Color.BLACK, Math.max(25, 60 * 7 * fluxLevel), 3);
            //engine.spawnExplosion(missile.getLocation(),new Vector2f(0,0), MUZZLE_FLASH_COLOR_GLOW, 50*8*fluxLevel, 1.5f);
            engine.removeEntity(missile);
            return;
        }
        if (launch) {
            setTarget((CombatEntityAPI) MagicTargeting.pickTarget(missile, MagicTargeting.targetSeeking.NO_RANDOM, (int) missile.getWeapon().getRange(), 90, 0, 1, 1, 1, 1, false));
            timer.forceIntervalElapsed();
            launch = false;
        }

        trailtimer.advance(amount);
        empTimer.advance(amount);
        if (!missile.isArmed()) {

            armingTimer.advance(amount);
            missile.giveCommand(ShipCommand.ACCELERATE);
            if (Math.random() > 0.75) {
                engine.spawnExplosion(missile.getLocation(), missile.getVelocity(), MUZZLE_FLASH_COLOR_ALT, MUZZLE_FLASH_SIZE * 0.10f, MUZZLE_FLASH_DURATION);
            } else {
                engine.spawnExplosion(missile.getLocation(), missile.getVelocity(), color, MUZZLE_FLASH_SIZE, MUZZLE_FLASH_DURATION);
            }
            engine.addSmoothParticle(missile.getLocation(), missile.getVelocity(), MUZZLE_FLASH_SIZE * 2f * fluxLevel, 1f, MUZZLE_FLASH_DURATION * 2f, colorGlow);
        } else {
            targetingTimer.advance(amount);
            if(target!=null && MathUtils.getDistance(target,missile) <= 700f && targetingTimer.intervalElapsed())
            MagicFakeBeam.spawnFakeBeam(
                    engine,
                    missile.getLocation(),
                    2000,
                    VectorUtils.getAngle(missile.getLocation(),target.getLocation()),
                    40f*fluxLevel,
                    amount,
                    amount/2,
                    100f*fluxLevel,
                    new Color(255,255,255),
                    new Color(199,146,0),
                    150f*amount*fluxLevel,
                    DamageType.ENERGY,
                    0f,
                    missile.getSource()
            );
            missile.giveCommand(ShipCommand.ACCELERATE);
            if (Math.random() > 0.75) {
                engine.spawnExplosion(missile.getLocation(), missile.getVelocity(), MUZZLE_FLASH_COLOR_ALT, MUZZLE_FLASH_SIZE * 0.20f * fluxLevel, MUZZLE_FLASH_DURATION);
            } else {
                engine.spawnExplosion(missile.getLocation(), missile.getVelocity(), color, MUZZLE_FLASH_SIZE * 2f * fluxLevel, MUZZLE_FLASH_DURATION);
            }
            engine.addSmoothParticle(missile.getLocation(), missile.getVelocity(), MUZZLE_FLASH_SIZE * 3f * fluxLevel, 1f, MUZZLE_FLASH_DURATION * 2f * fluxLevel, colorGlow);
        }
        if (missile.getSource().getSystem().isOn()) {
            ShipAPI ship = missile.getSource();
            boolean isRobot = engine.getCustomData().get("armaa_tranformState_" + ship.getId()) != null
                    ? (Boolean) engine.getCustomData().get("armaa_tranformState_" + ship.getId()) : false;
            if (isRobot) {
            } else {
                if (ship.getShipTarget() != null) {
                    target = ship.getShipTarget();
                }
                if (target == null) {
                    reacquireTarget();
                }

                if (target != moveTarget && target != null) {
                    missile.setArmingTime(0f);

                    EmpArcParams params = new EmpArcParams();

                    params.segmentLengthMult = 8f;
                    params.zigZagReductionFactor = 0.15f;
                    params.brightSpotFullFraction = 0.5f;
                    params.brightSpotFadeFraction = 0.5f;

                    float dist = Misc.getDistance(missile.getSource().getLocation(), target.getLocation());
                    params.flickerRateMult = 0.6f - dist / 3000f;
                    if (params.flickerRateMult < 0.3f) {
                        params.flickerRateMult = 0.3f;
                    }
                    float emp = 0;
                    float dam = 0;
                    EmpArcEntityAPI arc = (EmpArcEntityAPI) engine.spawnEmpArc(missile.getSource(), missile.getSource().getLocation(), missile.getSource(), target,
                            DamageType.ENERGY,
                            dam,
                            emp, // emp
                            100000f, // max range
                            "tachyon_lance_emp_impact",
                            40f, // thickness
                            //new Color(100,165,255,255),
                            colorGlow,
                            new Color(255, 255, 255, 255),
                            params
                    );
                    arc.setCoreWidthOverride(30f);

                    //arc.setFadedOutAtStart(true);
                    //arc.setRenderGlowAtStart(false);
                    arc.setSingleFlickerMode(true);
                }
                moveTarget = target;
                if (moveTarget != null) {
                    EveryFrameCombatPlugin plugin = new MoteCaller().pickJitterPlugin(
                            (ShipAPI) moveTarget,
                            0.1f,
                            0.4f,
                            colorGlow
                    );
                    Global.getCombatEngine().addPlugin(plugin);
                }
            }
        }
        if (trailtimer.intervalElapsed()) {
            for (int i = 0; i < 4; i++) {
                if (Math.random() > 0.75) {
                    engine.spawnExplosion(missile.getLocation(), ZERO, MUZZLE_FLASH_COLOR_ALT, MUZZLE_FLASH_SIZE * 0.50f * fluxLevel, MUZZLE_FLASH_DURATION);
                } else {
                    engine.spawnExplosion(missile.getLocation(), ZERO, color, MUZZLE_FLASH_SIZE * fluxLevel, MUZZLE_FLASH_DURATION);
                }
                engine.addSmoothParticle(missile.getLocation(), ZERO, MUZZLE_FLASH_SIZE * 3f * fluxLevel, 1f, MUZZLE_FLASH_DURATION * 2f, colorGlow);
                engine.addSmoothParticle(missile.getLocation(), ZERO, MUZZLE_FLASH_SIZE * 3f * 0.5f * fluxLevel, 1f, MUZZLE_FLASH_DURATION * 2f, MUZZLE_FLASH_COLOR_ALT);
                engine.addNebulaSmokeParticle(missile.getLocation(),
                        new Vector2f(missile.getVelocity().getX() / 2, missile.getVelocity().getY() / 2),
                        40f * (0.75f + (float) Math.random() * 0.5f) * fluxLevel,
                        3f + 1f * (float) Math.random() * 2f,
                        0f,
                        0f,
                        1f,
                        colorGlow);
            }
            if (missile.isArmed()) {
                for (ShipAPI target : CombatUtils.getShipsWithinRange(missile.getLocation(), 50f)) {
                    if (target.getOwner() == missile.getSource().getOwner()) {
                        continue;
                    }
                    if (CollisionUtils.isPointWithinBounds(missile.getLocation(), target)) {
                        primed = true;
                        break;
                    }

                }
            }
        }
        if (empTimer.intervalElapsed()) {
            float numStrikes = 1 * fluxLevel;
            for (MissileAPI target : CombatUtils.getMissilesWithinRange(missile.getLocation(), 700f)) {
                if (target.getOwner() == missile.getSource().getOwner()) {
                    continue;
                }
                engine.spawnEmpArc(missile.getSource(), missile.getLocation(), missile, target, DamageType.ENERGY, 0f, (float) missile.getDamage().getDamage() * fluxLevel, 10000f, "tachyon_lance_emp_impact", 100f * fluxLevel, colorGlow, MUZZLE_FLASH_COLOR_ALT);
                numStrikes--;
                if (numStrikes <= 0) {
                    break;
                }
            }
            numStrikes = 2 * fluxLevel;
            for (ShipAPI target : CombatUtils.getShipsWithinRange(missile.getLocation(), 700f)) {
                if (target.getOwner() == missile.getSource().getOwner()) {
                    continue;
                }
                if (target.isPhased()) {
                    continue;
                }
                engine.spawnEmpArc(missile.getSource(), missile.getLocation(), missile, target, DamageType.ENERGY, (missile.getDamage().getDamage() / 4) * fluxLevel, (float) missile.getDamage().getDamage() * fluxLevel, 10000f, "tachyon_lance_emp_impact", 100f * fluxLevel, colorGlow, MUZZLE_FLASH_COLOR_ALT);
                numStrikes--;
                if (numStrikes <= 0) {
                    break;
                }
            }
        }
        if (target == null || (target instanceof ShipAPI && ((!((ShipAPI) target).isAlive()) || ((ShipAPI) target).isPhased())) || !engine.isEntityInPlay(target)) {
            setTarget((CombatEntityAPI) MagicTargeting.pickTarget(missile, MagicTargeting.targetSeeking.NO_RANDOM, (int) missile.getWeapon().getRange(), 90, 0, 1, 1, 1, 1, false));
        }

        if (moveTarget == null) {
            if (target != null) {
                moveTarget = target;
            }
        }
        if (moveTarget instanceof ShipAPI) {
            ShipAPI shipTarget = (ShipAPI) moveTarget;
            if (shipTarget.isHulk() || !shipTarget.isAlive()) {
                moveTarget = (CombatEntityAPI) MagicTargeting.pickTarget(missile, MagicTargeting.targetSeeking.NO_RANDOM, (int) missile.getWeapon().getRange(), 90, 0, 1, 1, 1, 1, false);
            }
        }
        timer.advance(amount);
        //finding lead point to aim to
        if (timer.intervalElapsed() && moveTarget != null) {
            //best intercepting point
            lead = AIUtils.getBestInterceptPoint(
                    missile.getLocation(),
                    MAX_SPEED, //if eccm is intalled the point is accurate, otherwise it's placed closer to the target (almost tailchasing)
                    moveTarget.getLocation(),
                    moveTarget.getVelocity()
            );
            //null pointer protection
            if (lead == null) {
                lead = moveTarget.getLocation();
            }
        }

        //best velocity vector angle for interception
        float correctAngle = VectorUtils.getAngle(
                missile.getLocation(),
                lead
        );

        //target angle for interception
        float aimAngle = MathUtils.getShortestRotation(missile.getFacing(), correctAngle);

        if (aimAngle < 0) {
            missile.giveCommand(ShipCommand.TURN_RIGHT);
        } else {
            missile.giveCommand(ShipCommand.TURN_LEFT);
        }

        // Damp angular velocity if the missile aim is getting close to the targeted angle
        if (Math.abs(aimAngle) < Math.abs(missile.getAngularVelocity()) * DAMPING) {
            missile.setAngularVelocity(aimAngle / DAMPING);
        }
    }

    private void reacquireTarget() {
        List<CombatEntityAPI> potentialTargets = new ArrayList<>();
        CombatEntityAPI newTarget = null;
        for (ShipAPI potTarget : CombatUtils.getShipsWithinRange(missile.getLocation(), 1000f)) {
            if (potTarget.getOwner() == missile.getOwner()
                    || Math.abs(VectorUtils.getAngle(missile.getLocation(), potTarget.getLocation()) - missile.getFacing()) > 1000f
                    || potTarget.isHulk()) {
                continue;
            }
            if (potTarget.isPhased()) {
                continue;
            }
            if (potTarget.getHullSize().equals(ShipAPI.HullSize.FIGHTER)) {
                potentialTargets.add(potTarget);
            }
            if (potTarget.getHullSize().equals(ShipAPI.HullSize.FRIGATE)) {
                potentialTargets.add(potTarget);
            }
            if (potTarget.getHullSize().equals(ShipAPI.HullSize.DESTROYER)) {
                potentialTargets.add(potTarget);
            }
            if (potTarget.getHullSize().equals(ShipAPI.HullSize.CRUISER)) {
                potentialTargets.add(potTarget);
            }
            if (potTarget.getHullSize().equals(ShipAPI.HullSize.CAPITAL_SHIP)) {
                potentialTargets.add(potTarget);
            }
        }
        //If we found any eligible target, continue selection, otherwise we'll have to stay with no target
        if (!potentialTargets.isEmpty()) {
            for (CombatEntityAPI potTarget : potentialTargets) {
                if (newTarget == null) {
                    newTarget = potTarget;
                } else if (MathUtils.getDistance(newTarget, missile.getLocation()) > MathUtils.getDistance(potTarget, missile.getLocation())) {
                    newTarget = potTarget;
                }
            }

            //Once all that is done, set our target to the new target and select a new swarm point (if appropriate)
            target = newTarget;
        }
    }

    @Override
    public CombatEntityAPI getTarget() {
        return target;
    }

    @Override
    public void setTarget(CombatEntityAPI target) {
        this.target = target;
    }

    //dont reinvent the wheel I guess
    private class MoteCaller extends MoteControlScript {

        public EveryFrameCombatPlugin pickJitterPlugin(ShipAPI ship, float intensity, float range, Color color) {
            return createTargetJitterPlugin(ship, intensity, range, color);
        }
    }
}
