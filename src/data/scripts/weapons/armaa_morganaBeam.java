package data.scripts.weapons;

import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.BeamEffectPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.magiclib.util.MagicRender;

import java.awt.Color;
import org.dark.shaders.distortion.DistortionShader;
import org.dark.shaders.distortion.RippleDistortion;

public class armaa_morganaBeam implements BeamEffectPlugin {

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

    private static final float CHARGEUP_PARTICLE_ANGLE_SPREAD = 150f;
    private static final float CHARGEUP_PARTICLE_BRIGHTNESS = 1f;
    private static final float CHARGEUP_PARTICLE_DISTANCE_MAX = 150f;
    private static final float CHARGEUP_PARTICLE_DISTANCE_MIN = 100f;
    private static final float CHARGEUP_PARTICLE_DURATION = 0.5f;
    private static final float CHARGEUP_PARTICLE_SIZE_MAX = 5f;
    private static final float CHARGEUP_PARTICLE_SIZE_MIN = 1f;
    private static final float VEL_MIN = 1f;
    private static final float VEL_MAX = 1.5f;

    private static final Color PARTICLE_COLOR = new Color(0, 255, 200, 255);
    private static final float PARTICLE_SIZE_MIN = 5f;
    private static final float PARTICLE_SIZE_MAX = 10f;
    private static final float PARTICLE_DURATION_MIN = 0.4f;
    private static final float PARTICLE_DURATION_MAX = 0.9f;
    private static final float PARTICLE_INERTIA_MULT = 0.5f;
    private static final float PARTICLE_DRIFT = 50f;
    private static final float PARTICLE_DENSITY = 0.05f;
    private static final float PARTICLE_SPAWN_WIDTH_MULT = 0.5f;

    private static final int IMPACT_STREAK_COUNT = 10;
    private static final float IMPACT_STREAK_CONE = 35f;
    private static final float IMPACT_STREAK_SPD_MIN = 300f;
    private static final float IMPACT_STREAK_SPD_MAX = 600f;
    private static final float IMPACT_STREAK_FADE_OUT = 0.2f;
    private static final Color IMPACT_STREAK_COLOR = new Color(200, 255, 240, 180);

    public float TURRET_OFFSET = 20f;

    private boolean runOnce = false;
    private boolean wasZero = true;
    private boolean firstStrike = false;
    private final IntervalUtil fireInterval = new IntervalUtil(1.2f, 1.2f);

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

    private void spawnInwardStreak(CombatEngineAPI engine, Vector2f from, Vector2f to, float charge) {
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
            engine.addHitParticle(pos, new Vector2f(), size, brightness, STREAK_DURATION,
                    new Color(
                            STREAK_COLOR.getRed() / 255f,
                            STREAK_COLOR.getGreen() / 255f,
                            STREAK_COLOR.getBlue() / 255f,
                            alpha
                    ));
        }
    }

    private void spawnOutwardStreaks(CombatEngineAPI engine, WeaponAPI weapon, Vector2f muzzle) {
        float facing = weapon.getCurrAngle() + 180f;
        SpriteAPI sprite = null;
        Color color = Math.random() < 0.30f ? new Color(0, 0, 0, 1) : IMPACT_STREAK_COLOR;
        boolean additive = !color.equals(new Color(0, 0, 0, 1)) ? true : false;
        // could be one loop but after how long this took, fuk it
        if (Math.random() < 0.50f) {
            for (int i = 0; i < IMPACT_STREAK_COUNT / 2f; i++) {
                float angle = facing + MathUtils.getRandomNumberInRange(-25f, 25f);
                float speed = MathUtils.getRandomNumberInRange(IMPACT_STREAK_SPD_MIN, IMPACT_STREAK_SPD_MAX);
                Vector2f vel = MathUtils.getPointOnCircumference(null, speed, angle);

                sprite = Global.getSettings().getSprite("misc", "armaa_streak");
                MagicRender.battlespace(
                        sprite,
                        new Vector2f(muzzle),
                        new Vector2f(vel),
                        new Vector2f(sprite.getWidth(), sprite.getHeight()),
                        new Vector2f(0f, 0f), // no growth
                        angle + 90f, // rotate sprite to align with travel direction
                        0f, // no spin
                        color,
                        additive, // additive blend
                        0f, // no fade in
                        0.1f, // no full duration, just fade
                        IMPACT_STREAK_FADE_OUT
                );

            }
        }
        float leftie = 1f;
        for (int i = 0; i < IMPACT_STREAK_COUNT; i++) {
            float angle = facing + MathUtils.getRandomNumberInRange(0f, 45f) * leftie;
            float speed = MathUtils.getRandomNumberInRange(IMPACT_STREAK_SPD_MIN, IMPACT_STREAK_SPD_MAX);
            Vector2f vel = MathUtils.getPointOnCircumference(null, speed, angle);

            // Get perpendicular direction to weapon facing
            // Actually just do it manually:
            float perpX = (float) Math.sin(Math.toRadians(weapon.getCurrAngle())) * leftie;
            float perpY = -(float) Math.cos(Math.toRadians(weapon.getCurrAngle())) * leftie;
            float offsetDist = MathUtils.getRandomNumberInRange(20f, 25f);
            Vector2f spawnPos = new Vector2f(
                    muzzle.x + perpX * offsetDist,
                    muzzle.y + perpY * offsetDist
            );
            sprite = Global.getSettings().getSprite("misc", "armaa_streak");
            MagicRender.battlespace(
                    sprite,
                    new Vector2f(spawnPos),
                    new Vector2f(vel),
                    new Vector2f(sprite.getWidth(), sprite.getHeight()),
                    new Vector2f(0f, 0f), // no growth
                    angle + leftie * 90f, // rotate sprite to align with travel direction
                    0f, // no spin
                    color,
                    true, // additive blend
                    0f, // no fade in
                    0.1f, // no full duration, just fade
                    IMPACT_STREAK_FADE_OUT
            );
            leftie *= -1f;
        }
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {

        WeaponAPI weapon = beam.getWeapon();
        ShipAPI ship = weapon.getShip();
        float charge = weapon.getChargeLevel();

        ship.setJitterUnder(ship,
                new Color(0.8f * charge, 0f, 0.2f, (200f / 255f) * charge),
                1f * charge, 4, 10f * charge);

        if (charge >= 1f && !runOnce) {
            runOnce = true;
            beam.getWidth();
        }

        // Reset streak when weapon finishes firing cycle
        if (charge <= 0f) {
        }

        if (!MagicRender.screenCheck(0.2f, weapon.getLocation()) || engine.isPaused()) {
            return;
        }

        Vector2f muzzle = getMuzzlePos(weapon);


        if (charge > 0f) {

            if (Math.random() < CORE_FLICKER_CHANCE) {
                float coreSize = (CORE_SIZE_MIN + (CORE_SIZE_MAX - CORE_SIZE_MIN) * charge)
                        * (0.75f + (float) Math.random() * 0.5f);
                float brightness = CORE_BRIGHTNESS_MIN
                        + (CORE_BRIGHTNESS_MAX - CORE_BRIGHTNESS_MIN) * charge;

                engine.addHitParticle(muzzle, new Vector2f(),
                        coreSize * 1.8f, brightness * 0.35f, CORE_DURATION * 2f, CORE_COLOR_OUTER);
                engine.addHitParticle(muzzle, new Vector2f(),
                        coreSize, brightness, CORE_DURATION, CORE_COLOR_INNER);
            }

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
                spawnInwardStreak(engine, tail, head, charge);
            }

            if (Math.random() < GLOW_CHANCE * charge) {
                Vector2f glowPos = muzzle;
                engine.addSmoothParticle(glowPos, new Vector2f(),
                        MathUtils.getRandomNumberInRange(GLOW_SIZE_MIN, GLOW_SIZE_MAX),
                        charge,
                        MathUtils.getRandomNumberInRange(GLOW_DURATION_MIN, GLOW_DURATION_MAX),
                        GLOW_COLOR);
            }
            if (Math.random() < 0.40f) {
                spawnOutwardStreaks(engine, weapon, muzzle);
            }
        }

        CombatEntityAPI target = beam.getDamageTarget();
        if (target != null) {

            float dur = beam.getDamage().getDpsDuration();
            if (!wasZero) {
                dur = 0;
            }
            wasZero = beam.getDamage().getDpsDuration() <= 0;
            fireInterval.advance(dur);
            if (target instanceof ShipAPI && fireInterval.intervalElapsed()) {
                boolean hitShield = target.getShield() != null && target.getShield().isWithinArc(beam.getRayEndPrevFrame());
                if (hitShield) {
                    float fluxLevel = ((ShipAPI) target).getHardFluxLevel();
                    float pierceChance = (1f - fluxLevel) * 0.9f;
                    boolean piercedShield = hitShield && (float) Math.random() < pierceChance;
                    //if (!firstStrike) {
                        if (piercedShield) {

                            if (Global.getSettings().getModManager().isModEnabled("shaderLib")) {
                                float time = 1.25f;
                                RippleDistortion ripple = new RippleDistortion(beam.getTo(), new Vector2f());
                                ripple.setSize(400f);
                                ripple.setIntensity(100f * 1.25f);
                                ripple.setFrameRate(60f / (time));
                                ripple.fadeInSize(time);
                                ripple.fadeOutIntensity(time);
                                DistortionShader.addDistortion(ripple);
                            }
                            firstStrike = true;
                            engine.applyDamage(target, beam.getTo(), beam.getDamage().getDamage() * 0.05f, DamageType.KINETIC, 0f, false, false, ship);
                            Global.getSoundPlayer().playSound("hit_shield_heavy_gun", 1f, 1f, beam.getTo(), new Vector2f());
                        }
                    //}
                }
            }
            float facing = weapon.getCurrAngle();
            float A_2 = CHARGEUP_PARTICLE_ANGLE_SPREAD / 2f;

            if ((float) Math.random() <= 0.5f) {
                int count = 1 + (int) (charge * 3);
                for (int i = 0; i < count; i++) {
                    Color color = Math.random() <= 0.75f
                            ? beam.getFringeColor()
                            : new Color(255, 255, 200, 255);

                    float distance = MathUtils.getRandomNumberInRange(
                            CHARGEUP_PARTICLE_DISTANCE_MIN + 1f,
                            CHARGEUP_PARTICLE_DISTANCE_MAX + 1f) * charge;

                    float speed = 0.75f * distance / CHARGEUP_PARTICLE_DURATION * charge;
                    float angle = MathUtils.getRandomNumberInRange(facing - A_2, facing + A_2);
                    float vel = MathUtils.getRandomNumberInRange(speed * -VEL_MIN, speed * -VEL_MAX);

                    Vector2f vector = MathUtils.getPointOnCircumference(null, vel, angle);
                    float size = MathUtils.getRandomNumberInRange(
                            CHARGEUP_PARTICLE_SIZE_MIN + 1f,
                            CHARGEUP_PARTICLE_SIZE_MAX + 5f) * charge;

                    engine.addHitParticle(beam.getTo(), vector, size,
                            CHARGEUP_PARTICLE_BRIGHTNESS
                            * Math.min(charge + 0.5f, 1f)
                            * MathUtils.getRandomNumberInRange(0.75f, 1.25f),
                            CHARGEUP_PARTICLE_DURATION,
                            new Color(
                                    color.getRed() / 255f,
                                    color.getGreen() / 255f,
                                    color.getBlue() / 255f,
                                    (color.getAlpha() / 255f) * charge
                            ));
                }
            }

            // B2 — Hit-point flash
            if (Math.random() <= 0.05f) {
                float beamWidth = beam.getWidth();
                engine.addHitParticle(beam.getTo(), new Vector2f(),
                        beamWidth * 1.75f,
                        0.1f + charge * 0.3f,
                        0.2f,
                        beam.getCoreColor());
            }
        }

        if (runOnce) {
            float beamWidth = beam.getWidth();
            float particleCount = beamWidth * PARTICLE_SPAWN_WIDTH_MULT
                    * MathUtils.getDistance(beam.getTo(), beam.getFrom())
                    * amount * PARTICLE_DENSITY * charge;

            if (Math.random() < 0.5f) {
                for (int i = 0; i < (int) particleCount; i++) {
                    Vector2f spawnPoint = MathUtils.getRandomPointOnLine(beam.getFrom(), beam.getTo());
                    spawnPoint = MathUtils.getRandomPointInCircle(spawnPoint, beamWidth * PARTICLE_SPAWN_WIDTH_MULT);

                    if (!engine.getViewport().isNearViewport(spawnPoint, PARTICLE_SIZE_MAX * 3f)) {
                        continue;
                    }

                    Vector2f velocity = new Vector2f(
                            ship.getVelocity().x * PARTICLE_INERTIA_MULT,
                            ship.getVelocity().y * PARTICLE_INERTIA_MULT
                    );
                    velocity = MathUtils.getRandomPointInCircle(velocity, PARTICLE_DRIFT);

                    engine.addSmoothParticle(spawnPoint, velocity,
                            MathUtils.getRandomNumberInRange(PARTICLE_SIZE_MIN, PARTICLE_SIZE_MAX),
                            charge,
                            MathUtils.getRandomNumberInRange(PARTICLE_DURATION_MIN, PARTICLE_DURATION_MAX),
                            PARTICLE_COLOR);
                }
            }
        }
    }
}
