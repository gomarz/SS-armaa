package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import data.scripts.util.armaa_guardualBladeParticleEffect;
import data.scripts.util.armaa_utils;
import org.magiclib.util.MagicAnim;
import org.magiclib.util.MagicFakeBeam;
import org.magiclib.util.MagicLensFlare;
import java.awt.Color;
import java.util.ArrayList;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

public class armaa_guarDualBlade implements EveryFrameWeaponEffectPlugin, OnFireEffectPlugin {

    private boolean runOnce = false;
    private ShipAPI ship;
    private WeaponAPI pauldronR, torso;
    private int limbInit = 0;
    private float swingLevel = 0f;
    private boolean swinging = false;
    private boolean cooldownR = false;
    private boolean beamFired = false;
    private final IntervalUtil animInterval = new IntervalUtil(0.012f, 0.012f);
    private final ArrayList<CombatEntityAPI> targets = new ArrayList<>();
    private final float TORSO_OFFSET = -45;

    public void init() {
        runOnce = true;
        for (WeaponAPI w : ship.getAllWeapons()) {
            switch (w.getSlot().getId()) {
                case "B_TORSO":
                    if (torso == null) { torso = w; limbInit++; }
                    break;
                case "D_PAULDRONR":
                    if (pauldronR == null) { pauldronR = w; limbInit++; }
                    break;
            }
        }
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        ship = weapon.getShip();
        if (!runOnce) {
            init();
            weapon.ensureClonedSpec();
        }

        if (engine.isPaused()) return;

        // AI movement push
        if (!ship.getAIFlags().hasFlag(ShipwideAIFlags.AIFlags.BACKING_OFF)
                && !ship.getAIFlags().hasFlag(ShipwideAIFlags.AIFlags.DO_NOT_PURSUE)) {
            if (ship.getShipTarget() != null
                    && MathUtils.getDistance(ship, ship.getShipTarget()) < 500
                    && !weapon.isDisabled()
                    && ship.getAI() != null) {
                ship.blockCommandForOneFrame(ShipCommand.DECELERATE);
                engine.headInDirectionWithoutTurning(ship, weapon.getCurrAngle(), ship.getMaxSpeed());
            }
        }

        if (ship.getFluxTracker().isOverloadedOrVenting()) return;

        float sineC;
        if (weapon.getChargeLevel() < 1f) {
            sineC = MagicAnim.smoothNormalizeRange(weapon.getChargeLevel(), 0.3f, 0.9f);
        } else {
            sineC = 1f;
        }

        boolean leftie = armaa_utils.getWeaponSide(ship.getLocation(), ship.getFacing(), weapon.getLocation());
        float mult = leftie ? 1f : -1f;

        if (weapon.getCooldownRemaining() <= 0f && !weapon.isFiring()) {
            cooldownR = false;
        }

        if (!swinging && !cooldownR && weapon.getChargeLevel() > 0f) {
            weapon.setCurrAngle(weapon.getCurrAngle() + (sineC * (mult * TORSO_OFFSET) * 0.30f) * weapon.getChargeLevel());
        }

        if (weapon.getChargeLevel() >= 1f) {
            swinging = true;
        }

        if (swinging && Math.abs(weapon.getCurrAngle() - (ship.getFacing() - (mult * 45f))) > 0.1f) {
            animInterval.advance(amount);
            if (mult == 1f) {
                weapon.setCurrAngle((float) Math.min(weapon.getCurrAngle() + swingLevel, weapon.getCurrAngle() + weapon.getArc() / 2f));
            } else {
                weapon.setCurrAngle((float) Math.max(weapon.getCurrAngle() - swingLevel, weapon.getCurrAngle() - weapon.getArc() / 2f));
            }
        }

        if (swinging && weapon.getChargeLevel() <= 0f) {
            swinging = false;
            swingLevel = 0f;
            cooldownR = true;
        }

        if (animInterval.intervalElapsed() && !beamFired) {
            swingLevel += 0.5f;
        }
        if (swingLevel > 9f) swingLevel = 9f;
        if (!swinging) swingLevel = 0f;

        // The dual blade uses cooldownRemaining/cooldown as its chargeLevel proxy
        float chargeLevel = weapon.getCooldown() > 0f ? weapon.getCooldownRemaining() / weapon.getCooldown() : 0f;

        if (swingLevel >= 9f && chargeLevel > 0.5f) {
            beamFired = true;
        }
        if (beamFired && swingLevel > 0f) {
            swingLevel -= 0.25f;
        }

        if (chargeLevel > 0.5f && weapon.isFiring() && (!beamFired || swingLevel >= 5f)) {
            MagicFakeBeam.spawnFakeBeam(engine, weapon.getFirePoint(0), 50f, weapon.getCurrAngle(),
                    20f, amount, 0.15f * chargeLevel, 20f,
                    new Color(.9f, 1f, .9f, 1f * chargeLevel),
                    new Color(0f, 1f, 0.75f, .9f * chargeLevel),
                    0f, DamageType.ENERGY, 0f, ship);

            Vector2f origin = weapon.getFirePoint(0);
            Vector2f point = armaa_utils.getBeamEndpoint(origin, weapon.getCurrAngle(), 50f);

            for (CombatEntityAPI target : CombatUtils.getEntitiesWithinRange(origin, 55f)) {
                if (target.getOwner() == ship.getOwner()) continue;
                if (targets.contains(target)) continue;
                if (target instanceof DamagingProjectileAPI && !(target instanceof MissileAPI)) continue;

                // Arc check — same early-out as naginata
                float angleToTarget = VectorUtils.getAngle(origin, target.getLocation());
                float diff = MathUtils.getShortestRotation(weapon.getCurrAngle(), angleToTarget);
                if (Math.abs(diff) > weapon.getArc() * 0.5f) continue;

                if (target instanceof MissileAPI) {
                    engine.removeEntity((MissileAPI) target);
                    continue;
                }

                if (target instanceof ShipAPI) {
                    ShipAPI shipTarget = (ShipAPI) target;
                    if (shipTarget.isPhased() || shipTarget.getCollisionClass() == CollisionClass.NONE) continue;
                }

                float finalDamage = weapon.getDamage().getDamage()*2f;
                float variance = MathUtils.getRandomNumberInRange(-0.3f, 0.3f);

                // 1) Shield check
                Vector2f shieldHit = armaa_utils.intersectShield(target, origin, point);
                if (shieldHit != null) {
                    engine.applyDamage(target, shieldHit, finalDamage, weapon.getDamageType(), 0f, false, false, ship, true);
                    Global.getSoundPlayer().playSound("armaa_saber_slash", 1.1f + variance, 1f + variance, shieldHit, new Vector2f());
                    MagicLensFlare.createSharpFlare(engine, ship, shieldHit, 4f, 150f, weapon.getCurrAngle(),
                            new Color(0, 255, 0, 150), new Color(200, 255, 200, 255));
                    targets.add(target);
                    continue;
                }

                // 2) Hull precise check
                Vector2f hullHit = armaa_utils.preciseHitCheck(weapon, target);
                if (hullHit != null) {
                    engine.applyDamage(target, hullHit, finalDamage, weapon.getDamageType(), 0f, false, false, ship, true);
                    Global.getSoundPlayer().playSound("armaa_saber_slash", 1.1f + variance, 1f + variance, hullHit, new Vector2f());
                    MagicLensFlare.createSharpFlare(engine, ship, hullHit, 4f, 150f, weapon.getCurrAngle(),
                            new Color(0, 255, 0, 150), new Color(200, 255, 200, 255));
                    targets.add(target);
                }
            }
        }

        if (chargeLevel <= 0f && beamFired) {
            beamFired = false;
            targets.clear();
        }
    }

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        projectile.setDamageAmount(0f);
        Vector2f vector = projectile.getVelocity();
        Vector2f loc = weapon.getFirePoint(0);

        MagicLensFlare.createSharpFlare(engine, weapon.getShip(), projectile.getLocation(),
                4f, 150f, projectile.getFacing(),
                new Color(0, 255, 0, 150), new Color(200, 255, 200, 255));
        engine.spawnExplosion(weapon.getLocation(), new Vector2f(), new Color(0, 111, 50, 150), 75f, 0.25f);
        engine.spawnExplosion(weapon.getLocation(), new Vector2f(), Color.white, 25f, 0.1f);
        engine.addNebulaParticle(projectile.getLocation(), new Vector2f(),
                20f * (0.75f + (float) Math.random() * 0.5f),
                5f + 3f * Misc.getHitGlowSize(100f, projectile.getDamage().getBaseDamage(), null) / 100f,
                0f, 0f, 1f, new Color(41, 239, 111, 150), true);
        engine.addNebulaSmoothParticle(loc, vector, 100f, 1.5f, 0.5f, 1f, 1f, new Color(0, 111, 50, 150), true);

        float targetArc = weapon.distanceFromArc(ship.getMouseTarget()) == 0
                ? VectorUtils.getAngle(weapon.getFirePoint(0), ship.getMouseTarget())
                : ship.getFacing();
        DamagingProjectileAPI proj = (DamagingProjectileAPI) engine.spawnProjectile(
                ship, weapon, "armaa_guardual_bs_arm_left_bullet",
                weapon.getFirePoint(0), targetArc, ship.getVelocity());

        for (int i = 0; i < 10; i++) {
            float angle = (float) (Math.random() * 360f);
            float speed = 150f + (float) Math.random() * 150f;
            Vector2f vel = new Vector2f(
                    (float) Math.cos(Math.toRadians(angle)) * speed,
                    (float) Math.sin(Math.toRadians(angle)) * speed);
            engine.addHitParticle(weapon.getFirePoint(0), vel, (float) Math.random() * 10f, 1f, 1f, Color.green);
        }

        engine.spawnEmpArcVisual(weapon.getFirePoint(0), null, proj.getLocation(), null,
                25f, new Color(0, 111, 50, 150), Color.white);
        engine.addPlugin(new armaa_guardualBladeParticleEffect(proj));
        engine.removeEntity(projectile);
    }
}