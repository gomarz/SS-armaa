package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import data.scripts.util.armaa_utils;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import org.lwjgl.util.vector.Vector2f;
import org.lazywizard.lazylib.MathUtils;
import java.awt.Color;
import org.lazywizard.lazylib.VectorUtils;
import com.fs.starfarer.api.util.IntervalUtil;
import org.magiclib.util.MagicRender;

import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;

public class armaa_morganaBeamEffect implements EveryFrameWeaponEffectPlugin {

    private static final Vector2f ZERO = new Vector2f();
    public float TURRET_OFFSET = 40f;

    private boolean charging = false;
    private boolean cooling = false;
    private boolean firing = false;
    private float level = 0f;
    private final IntervalUtil interval = new IntervalUtil(0.015f, 0.015f);

    private static final float CORE_SIZE_MIN = 10f;
    private static final float CORE_SIZE_MAX = 50f;
    private static final float CORE_FLICKER_CHANCE = 0.55f;
    private static final float CORE_BRIGHTNESS_MIN = 0.4f;
    private static final float CORE_BRIGHTNESS_MAX = 1.0f;
    private static final float CORE_DURATION = 0.06f;
    private static final Color CORE_COLOR_INNER = new Color(255, 255, 240, 255);
    private static final Color CORE_COLOR_OUTER = new Color(0, 220, 180, 200);

    private static final int STREAK_COUNT_MAX = 6;
    private static final float STREAK_SPAWN_RADIUS_MIN = 40f;
    private static final float STREAK_SPAWN_RADIUS_MAX = 200f;
    private static final float STREAK_SIZE_HEAD = 4f;
    private static final float STREAK_SIZE_TAIL = 2f;
    private static final int STREAK_TAIL_STEPS = 6;
    private static final float STREAK_DURATION = 0.12f;
    private static final Color STREAK_COLOR = new Color(0, 240, 190, 255);

    private static final float GLOW_CHANCE = 0.4f;
    private static final float GLOW_SIZE_MIN = 8f;
    private static final float GLOW_SIZE_MAX = 22f;
    private static final float GLOW_DURATION_MIN = 0.15f;
    private static final float GLOW_DURATION_MAX = 0.35f;
    private static final Color GLOW_COLOR = new Color(0, 200, 160, 120);

    private Vector2f getMuzzlePos(WeaponAPI weapon) {
        Vector2f origin = new Vector2f(weapon.getLocation());
        Vector2f offset;
        if (weapon.getSlot().isTurret()) {
            offset = new Vector2f(weapon.getSpec().getTurretFireOffsets().get(0));
        } else {
            offset = new Vector2f(weapon.getSpec().getHardpointFireOffsets().get(0));
        }
        VectorUtils.rotate(offset, weapon.getCurrAngle(), offset);
        Vector2f.add(offset, origin, origin);
        return origin;
    }

    private void spawnStreak(CombatEngineAPI engine, Vector2f from, Vector2f to, float charge) {
        Vector2f dir = Vector2f.sub(to, from, new Vector2f());
        float len = dir.length();
        if (len < 1f) {
            return;
        }
        dir.scale(1f / len);

        for (int s = 0; s < STREAK_TAIL_STEPS; s++) {
            float t = (float) s / (STREAK_TAIL_STEPS - 1);
            Vector2f pos = new Vector2f(
                    from.x + dir.x * len * t,
                    from.y + dir.y * len * t
            );
            float size = STREAK_SIZE_TAIL + (STREAK_SIZE_HEAD - STREAK_SIZE_TAIL) * t;
            float brightness = (0.4f + 0.6f * t) * charge;
            float alpha = (0.3f + 0.7f * t) * charge;
            engine.addHitParticle(pos, ZERO, size, brightness, STREAK_DURATION,
                    new Color(
                            STREAK_COLOR.getRed() / 255f,
                            STREAK_COLOR.getGreen() / 255f,
                            STREAK_COLOR.getBlue() / 255f,
                            alpha
                    ));
        }
    }

    private void spawnChargeUpFX(CombatEngineAPI engine, WeaponAPI weapon, float charge) {
        Vector2f muzzle = getMuzzlePos(weapon);

        // Flickering core orb
        if (Math.random() < CORE_FLICKER_CHANCE) {
            float coreSize = (CORE_SIZE_MIN + (CORE_SIZE_MAX - CORE_SIZE_MIN) * charge)
                    * (0.75f + (float) Math.random() * 0.5f);
            float brightness = CORE_BRIGHTNESS_MIN
                    + (CORE_BRIGHTNESS_MAX - CORE_BRIGHTNESS_MIN) * charge;

            engine.addHitParticle(muzzle, ZERO,
                    coreSize * 1.8f, brightness * 0.35f, CORE_DURATION * 2f, CORE_COLOR_OUTER);
            engine.addHitParticle(muzzle, ZERO,
                    coreSize, brightness, CORE_DURATION, CORE_COLOR_INNER);
        }

        // Inward streaks
        int streakCount = (int) (charge * STREAK_COUNT_MAX);
        if (charge > 0.9f) {
            streakCount += 3;
        }

        for (int i = 0; i < streakCount; i++) {
            float spawnRadius = MathUtils.getRandomNumberInRange(
                    STREAK_SPAWN_RADIUS_MIN + (1f - charge) * 60f,
                    STREAK_SPAWN_RADIUS_MAX
            );
            float spawnAngle = MathUtils.getRandomNumberInRange(0f, 360f);
            Vector2f tail = MathUtils.getPointOnCircumference(muzzle, spawnRadius, spawnAngle);

            float headFrac = 0.6f + charge * 0.35f;
            Vector2f head = new Vector2f(
                    tail.x + (muzzle.x - tail.x) * headFrac,
                    tail.y + (muzzle.y - tail.y) * headFrac
            );
            spawnStreak(engine, tail, head, charge);
        }

        // Ambient glow cloud
        if (Math.random() < GLOW_CHANCE * charge) {
            float glowRadius = MathUtils.getRandomNumberInRange(5f, CORE_SIZE_MAX * 0.6f * charge);
            Vector2f glowPos = MathUtils.getRandomPointInCircle(muzzle, glowRadius);
            engine.addSmoothParticle(glowPos, ZERO,
                    MathUtils.getRandomNumberInRange(GLOW_SIZE_MIN, GLOW_SIZE_MAX),
                    charge,
                    MathUtils.getRandomNumberInRange(GLOW_DURATION_MIN, GLOW_DURATION_MAX),
                    GLOW_COLOR);
        }
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine.isPaused() || weapon.getShip().getOriginalOwner() == -1) {
            return;
        }
        if (!MagicRender.screenCheck(0.2f, weapon.getLocation())) {
            return;
        }

        ShipAPI ship = weapon.getShip();
        if (ship == null) {
            return;
        }

        if (ship.isFighter()) {
            if (weapon.isFiring() && weapon.getAmmo() <= 0) {
                ship.blockCommandForOneFrame(ShipCommand.TURN_LEFT);
                ship.blockCommandForOneFrame(ShipCommand.TURN_RIGHT);
                if (ship.getAIFlags().hasFlag(ShipwideAIFlags.AIFlags.CARRIER_FIGHTER_TARGET)) {
                    ship.setShipTarget((ShipAPI) ship.getAIFlags().getCustom(ShipwideAIFlags.AIFlags.CARRIER_FIGHTER_TARGET));
                }
                if (ship.getShipTarget() != null) {
                    weapon.setCurrAngle(VectorUtils.getAngle(ship.getLocation(), ship.getShipTarget().getLocation()));
                    ship.setFacing(weapon.getCurrAngle());
                }
            }
        }

        float chargeLevel = weapon.getChargeLevel();

        // Colour lerp for the glow orb (unchanged from original)
        Color color = new Color(150, 150, 150, 100);
        Color red = new Color(255, 0, 0, 255);
        float r = (red.getRed() / 255f) + chargeLevel * (color.getRed() / 255f) - (red.getRed() / 255f) * chargeLevel;
        float g = (red.getGreen() / 255f) + chargeLevel * (color.getGreen() / 255f) - (red.getGreen() / 255f) * chargeLevel;
        float b = (red.getBlue() / 255f) + chargeLevel * (color.getBlue() / 255f) - (red.getBlue() / 255f) * chargeLevel;

        Vector2f origin = getMuzzlePos(weapon);

        if (charging || (!charging && chargeLevel < 1f && chargeLevel > 0f)) {
            float radius = 25f + (chargeLevel * chargeLevel * MathUtils.getRandomNumberInRange(25f, 50f));
            engine.addHitParticle(origin, ZERO, radius, chargeLevel * 0.3f, 0.2f,
                    new Color(r * chargeLevel, b * chargeLevel, g * chargeLevel, chargeLevel));
        }

        if (charging) {
            if(weapon.isDisabled() || weapon.getCooldownRemaining() > 0 || ship.getFluxTracker().isVenting())
                charging = false;
            if (firing && chargeLevel < 1f) {
                charging = false;
                cooling = true;
                firing = false;
            } else if (chargeLevel < 1f) {
                Global.getSoundPlayer().playLoop("beamchargeL", weapon.getShip(),1f, 1f, weapon.getFirePoint(0), weapon.getShip().getVelocity());

                spawnChargeUpFX(engine, weapon, chargeLevel);
            } else {
                // Fully charged / firing
                firing = true;
                spawnChargeUpFX(engine, weapon, chargeLevel); // keep orb visible at full charge too
                interval.advance(amount);
                if (interval.intervalElapsed()) {
                    int count = 1 + (int) (chargeLevel * 2);
                    float A_2 = 150f / 2f;
                    float facing = weapon.getCurrAngle();
                    for (int i = 0; i < count; i++) {
                        float distance = MathUtils.getRandomNumberInRange(101f, 201f) * chargeLevel;
                        float speed = 0.5f * distance / 0.5f * chargeLevel;
                        float angle = MathUtils.getRandomNumberInRange(facing - A_2, facing + A_2);
                        float vel = MathUtils.getRandomNumberInRange(speed * -0.5f, speed * -1f);
                        Vector2f vector = MathUtils.getPointOnCircumference(weapon.getShip().getVelocity(), vel, angle);
                        float size = MathUtils.getRandomNumberInRange(2f, 8f) * chargeLevel;
                        engine.addHitParticle(origin, vector, size,
                                Math.min(chargeLevel + 0.5f, 1f) * MathUtils.getRandomNumberInRange(0.75f, 1.25f),
                                0.5f, color);
                    }
                }
            }
        } else {
            if (cooling) {
                if (chargeLevel <= 0f) {
                    cooling = false;
                }
            } else if (chargeLevel > level) {
                charging = true;
            }
        }

        level = chargeLevel;
    }

    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        Global.getSoundPlayer().playSound("beamchargeL", 1f, 1f, weapon.getFirePoint(0), weapon.getShip().getVelocity());
    }
}
