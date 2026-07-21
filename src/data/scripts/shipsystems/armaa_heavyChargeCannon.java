package data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.combat.listeners.DamageListener;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import com.fs.starfarer.api.util.IntervalUtil;

import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;

import java.awt.Color;
import org.magiclib.util.MagicFakeBeam;
import org.magiclib.util.MagicLensFlare;

public class armaa_heavyChargeCannon extends BaseShipSystemScript {

    private static final Vector2f ZERO = new Vector2f();

    // ---- commit-cost tunables s
    private static final float COMMIT_SPEED_MULT = 0.70f;
    private static final float COMMIT_SHIELD_TAKEN_MULT = 1.25f;

    // ---- charge timing / tiers (seconds) 
    private static final float CHARGE_MAX_SECONDS = 4.75f;
    private static final float TIER_1_TIME = 0.5f;
    private static final float TIER_2_TIME = 1.50f;
    private static final float TIER_3_TIME = 2.50f;
    private static final float TIER_4_TIME = 4.75f;

    private static final float[] TIER_COOLDOWN = {1f, 5f, 8f, 18f};
    private static final float[] TIER_DAMAGE = {250f, 500f, 900f, 1500f};

    // ---- degrade-on-hit
    private static final float DEGRADE_SECONDS_PER_DAMAGE = 0.0015f;
    // ---- shots 
    private static final float AIM_ASSIST_MAX_DEVIATION = 25f; // degrees the shot may snap off the weapon/facing angle

    private static final float BETA_HITSCAN_RANGE = 600f;
    private static final float BETA_EXPLOSION_RADIUS = 140f;
    private static final DamageType BETA_DAMAGE_TYPE = DamageType.HIGH_EXPLOSIVE;

    // ---- BETA AoE HE blast (falloff) ----
    private static final float BETA_BLAST_RADIUS_BASE = 90f;      // damaging-blast radius at tier 1
    private static final float BETA_BLAST_RADIUS_PER_TIER = 35f;  // +radius per tier
    private static final float BETA_BLAST_CORE_FRAC = 0.35f;      // core (full-damage) radius fraction
    private static final float BETA_BLAST_MIN_DAMAGE_FRAC = 0.5f; // edge damage = this * maxDamage

    // ---- BETA EMP-heavy disable splash ----
    private static final float BETA_EMP_RADIUS_BASE = 80f;        // EMP reach at tier 1
    private static final float BETA_EMP_RADIUS_PER_TIER = 45f;    // +EMP reach per tier
    private static final float BETA_EMP_PER_TIER = 100f;          // EMP magnitude per arc, * tier
    private static final float BETA_EMP_ENERGY_FRAC = 0.1f;       // tiny energy component (EMP-heavy)
    private static final int BETA_EMP_ARCS_BASE = 1;            // arcs to each target at tier 1
    private static final int BETA_EMP_ARCS_PER_TIER = 1;        // +arcs per tier

// BETA detonation palette (orange-red HE blast)
    private static final Color DETO_RING_COLOR = new Color(255, 140, 50, 200);
    private static final Color DETO_CORE_COLOR = new Color(255, 180, 90, 200);
    private static final Color DETO_FLASH_COLOR = new Color(255, 255, 235, 200);
    private static final Color DETO_SECONDARY_COLOR = new Color(255, 110, 40, 200);
    private static final Color DETO_SPARK_COLOR = new Color(255, 200, 120, 200);
    private static final Color DETO_NEBULA_COLOR = new Color(255, 130, 40, 110);

    private static final String STAT_ID = "armaa_heavyChargeCannon";

    // ---- VFX: muzzle anchor (per mode) ------------------------------------------------------
    private static final String MUZZLE_SLOT_BETA = "A_GUN";  // mech mode
    private static final String MUZZLE_SLOT_ALPHA = "F_GPOD"; // fighter mode

    // ---- VFX: core orb ----------------------------------------------------------------------
    private static final float CORE_SIZE_MIN = 5f;
    private static final float CORE_SIZE_MAX = 25f;
    private static final float CORE_FLICKER_CHANCE = 0.55f;
    private static final float CORE_BRIGHTNESS_MIN = 0.4f;
    private static final float CORE_BRIGHTNESS_MAX = 1.0f;
    private static final float CORE_DURATION = 0.06f;

    // ---- VFX: streaks -----------------------------------------------------------------------
    private static final int STREAK_COUNT_MAX = 6;
    private static final float STREAK_SPAWN_RADIUS_MIN = 40f;
    private static final float STREAK_SPAWN_RADIUS_MAX = 100f;
    private final IntervalUtil streakInterval = new IntervalUtil(STREAK_INTERVAL_MIN, STREAK_INTERVAL_MAX);

    private static final float STREAK_INTERVAL_MIN = 0.05f;
    private static final float STREAK_INTERVAL_MAX = 0.1f;
    private static final float STREAK_SPEED_MIN = 120f;
    private static final float STREAK_SPEED_MAX = 220f;
    private static final float STREAK_ALPHA = 0.7f;
    private static final float STREAK_FULL_DURATION = 0.2f;
    private static final float STREAK_FADE_OUT = 0.3f;
    private static final float STREAK_RADIUS_CHARGE_BIAS = 60f; // was the inline 60f

    // ---- VFX: glow cloud --------------------------------------------------------------------
    private static final float GLOW_CHANCE = 0.4f;
    private static final float GLOW_SIZE_MIN = 8f;
    private static final float GLOW_SIZE_MAX = 22f;
    private static final float GLOW_DURATION_MIN = 0.15f;
    private static final float GLOW_DURATION_MAX = 0.35f;

    // ---- VFX: at-max spray ------------------------------------------------------------------
    private final IntervalUtil sprayInterval = new IntervalUtil(0.015f, 0.015f);

    // ---- VFX: mode hues (C). Inner is the hot core, outer the surrounding tint. --------------
    private static final Color BETA_INNER = new Color(255, 245, 220, 255); // hot amber-white core (HE)
    private static final Color BETA_OUTER = new Color(255, 120, 40, 200);  // orange-red
    private static final Color BETA_STREAK = new Color(255, 160, 70, 255);  // bright orange
    private static final Color BETA_GLOW = new Color(255, 110, 40, 120);  // warm glow

    private static final Color ALPHA_INNER = new Color(220, 240, 255, 255); // hot blue-white core (energy)
    private static final Color ALPHA_OUTER = new Color(90, 180, 255, 200);  // blue
    private static final Color ALPHA_STREAK = new Color(140, 210, 255, 255); // light blue
    private static final Color ALPHA_GLOW = new Color(80, 160, 255, 120);  // blue glow

    // ---- sound ------------------------------------------------------------------------------
    private static final String CHARGE_LOOP_SOUND = "beamchargeL";

    // ---- per-instance state -----------------------------------------------------------------
    private float chargeTime = 0f;
    private boolean wasCharging = false;
    private int lastTierFx = 0;
    private float lastChargeClock = -1f;
    private DegradeListener listener = null;

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = null;
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
        }
        if (ship == null) {
            return;
        }
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) {
            return;
        }

        boolean charging = (state == State.ACTIVE || state == State.IN);

        if (charging) {
            stats.getMaxSpeed().modifyMult(STAT_ID, mix(1f, COMMIT_SPEED_MULT, effectLevel));
            stats.getShieldDamageTakenMult().modifyMult(STAT_ID, mix(1f, COMMIT_SHIELD_TAKEN_MULT, effectLevel));
            String slotId = MUZZLE_SLOT_BETA;
            WeaponAPI w = findWeapon(ship, slotId);
            float facing = w.getCurrAngle();
            boolean isRobot = readIsRobot(ship, engine);
            MagicFakeBeam.spawnFakeBeam(
                    engine,
                    muzzleOrShip(ship),
                    isRobot ? BETA_HITSCAN_RANGE : 1000f,
                    facing,
                    3f,
                    engine.getElapsedInLastFrame(),
                    0f,
                    1f,
                    new Color(1f, 0f, 0f, 1f * effectLevel),
                    new Color(1f, 0f, 0f, 1f * effectLevel),
                    0f,
                    DamageType.OTHER,
                    0f,
                    ship);
            if (ship.getShipAI() != null && ship.getShipTarget() != null) {
                w.setCurrAngle(VectorUtils.getAngle(w.getFirePoint(0), ship.getShipTarget().getLocation()));
            }

            if (!wasCharging) {
                chargeTime = 0f;
                lastTierFx = 0;
                lastChargeClock = -1f;
                registerListener(ship);
            }

            if (!engine.isPaused()) {
                // Advance chargeTime off the authoritative sim clock, not getElapsedInLastFrame().
                // Deriving dt from the clock delta means it's correct whether apply() fires once or
                // several times per frame (the clock only moves once), and sidesteps any extra
                // scaling getElapsedInLastFrame() applies under time dilation.
                float clock = engine.getTotalElapsedTime(false);
                if (lastChargeClock < 0f) {
                    lastChargeClock = clock;
                }
                float dt = clock - lastChargeClock;
                lastChargeClock = clock;
                chargeTime = Math.min(CHARGE_MAX_SECONDS, chargeTime + dt);

                float chargeFrac = chargeTime / CHARGE_MAX_SECONDS;
                boolean atMax = chargeTime >= TIER_4_TIME;

                float vol = MathUtils.clamp(0.6f + 0.7f * chargeFrac, 0.6f, 1.3f);
                float pitch = MathUtils.clamp(0.7f + 0.5f * chargeFrac, 0.7f, 1.3f);
                Global.getSoundPlayer().playLoop("system_high_energy_focus_loop", ship, pitch, vol,
                        ship.getLocation(), ship.getVelocity());

                int tier = tierForCharge(chargeTime);
                if (tier > lastTierFx) {
                    fireTierTell(ship, engine, tier);   // fires on 0->1, 1->2, 2->3, 3->4
                }
                lastTierFx = tier;

                // Publish charge state so the system AI (separate script) can read it.
                // Mirrors how the effect script shares armaa_tranformState_ / armaa_tranformLevel_.
                engine.getCustomData().put("armaa_chargeCannonTier_" + ship.getId(), tier);
                engine.getCustomData().put("armaa_chargeCannonTime_" + ship.getId(), chargeTime);
                engine.getCustomData().put("armaa_chargeCannonFrac_" + ship.getId(), chargeFrac);

                chargeFrac = Math.max(chargeFrac, 0.2f);
                spawnChargeUpFX(engine, ship, chargeFrac, isRobot);

                if (atMax) {
                    sprayInterval.advance(dt);
                    if (sprayInterval.intervalElapsed()) {
                        spawnMaxSpray(engine, ship, isRobot);
                    }
                }
            }
        } else {
            if (wasCharging) {
                resolveFire(ship, engine);
                unregisterListener(ship);
                chargeTime = 0f;
                lastTierFx = 0;
                lastChargeClock = -1f;
            }
            // not charging -> publish zeroed charge state so the AI reads "idle"
            engine.getCustomData().put("armaa_chargeCannonTier_" + ship.getId(), 0);
            engine.getCustomData().put("armaa_chargeCannonTime_" + ship.getId(), 0f);
            engine.getCustomData().put("armaa_chargeCannonFrac_" + ship.getId(), 0f);
            unapply(stats, id);
        }

        wasCharging = charging;
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        stats.getMaxSpeed().unmodify(STAT_ID);
        stats.getShieldDamageTakenMult().unmodify(STAT_ID);
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (state == State.IN || state == State.ACTIVE) {
            int tier = tierForCharge(chargeTime);
            if (index == 0) {
                return new StatusData(tier == 0 ? "charging - not ready" : "charging - tier " + tier, false);
            }
        }
        return null;
    }

    // ---- firing -----------------------------------------------------------------------------
    private void resolveFire(ShipAPI ship, CombatEngineAPI engine) {
        int tier = tierForCharge(chargeTime);
        if (tier <= 0) {
            return;
        }
        float damage = TIER_DAMAGE[Math.max(0, Math.min(TIER_DAMAGE.length - 1, tier - 1))];

        float cd = TIER_COOLDOWN[tier - 1];
        ShipSystemAPI sys = ship.getSystem();
        if (sys != null) {
            sys.setCooldown(cd);
            sys.setCooldownRemaining(cd);
        }

        boolean isRobot = readIsRobot(ship, engine);
        float chargeFrac = chargeTime / CHARGE_MAX_SECONDS;
        spawnFireBurst(engine, ship, chargeFrac, isRobot);
        if (isRobot) {
            fireBetaHitscan(ship, engine, damage, tier);
        } else {
            fireAlphaBolt(ship, engine, damage);
        }
    }
    /**
     * Muzzle discharge for the fire event — the release punch at the gun,
     * distinct from the impact detonation. Inverse of the charge streaks:
     * energy expelled forward along facing instead of drawn inward.
     * Mode-tinted.
     */
// ---- VFX: fire burst (muzzle discharge) -------------------------------------------------
    private static final float FIRE_CORE_SIZE_MULT = 2.5f;   // x CORE_SIZE_MAX
    private static final float FIRE_CORE_DURATION = 0.18f;
    private static final float FIRE_RING_START_MULT = 0.6f;  // x coreSize, matches detonation
    private static final float FIRE_RING_GROW_MULT = 3.0f;   // x coreSize, matches detonation
    private static final float FIRE_RING_ALPHA = 0.8f;
    private static final float FIRE_RING_FADE_OUT = 0.35f;
    private static final int FIRE_CONE_COUNT_MAX = 14;
    private static final float FIRE_CONE_SPREAD = 18f;       // degrees each side of facing
    private static final float FIRE_CONE_SPEED_MIN = 200f;
    private static final float FIRE_CONE_SPEED_MAX = 400f;
    private static final float FIRE_CONE_ALPHA = 0.8f;

    private static final int FIRE_PUFF_COUNT_MAX = 4;
    private static final float FIRE_PUFF_SPREAD = 25f;          // wider than the cone, looser cloud
    private static final float FIRE_PUFF_SPEED_MIN = 40f;       // slow — well under the cone's 200-400
    private static final float FIRE_PUFF_SPEED_MAX = 110f;
    private static final float FIRE_PUFF_SIZE = 35f;
    private static final float FIRE_PUFF_END_SIZE_MULT_MIN = 1.6f;
    private static final float FIRE_PUFF_END_SIZE_MULT_MAX = 2.4f;
    private static final float FIRE_PUFF_DUR_MIN = 0.5f;
    private static final float FIRE_PUFF_DUR_MAX = 0.9f;
    private static final float FIRE_PUFF_RAMP_FRAC = 0.15f;     // matches detonation nebula
    private static final float FIRE_PUFF_FADE_FRAC = 0.55f;

    private void spawnFireBurst(CombatEngineAPI engine, ShipAPI ship, float charge, boolean isRobot) {
        Vector2f muzzle = muzzleOrShip(ship);
        Vector2f shipVel = ship.getVelocity();
        float facing = ship.getFacing();

        Color inner = isRobot ? BETA_INNER : ALPHA_INNER;
        Color outer = isRobot ? BETA_OUTER : ALPHA_OUTER;
        Color streak = isRobot ? BETA_STREAK : ALPHA_STREAK;

        // core flash — oversized hot slug at the muzzle, layered like the charge core
        float coreSize = (CORE_SIZE_MAX * FIRE_CORE_SIZE_MULT) * charge;
        engine.addHitParticle(muzzle, shipVel, coreSize * 1.8f, 0.5f, FIRE_CORE_DURATION * 1.5f, outer);
        engine.addHitParticle(muzzle, shipVel, coreSize, 1f, FIRE_CORE_DURATION, inner);

        // shockwave ring — proportional start/grow like the detonation ring
        float ringStart = coreSize * FIRE_RING_START_MULT;
        float ringGrow = coreSize * FIRE_RING_GROW_MULT;
        MagicRender.battlespace(
                Global.getSettings().getSprite("misc", "armaa_sfxpulse"),
                new Vector2f(muzzle),
                new Vector2f(shipVel),
                new Vector2f(ringStart, ringStart),
                new Vector2f(ringGrow, ringGrow),
                facing,
                0f,
                new Color(outer.getRed() / 255f, outer.getGreen() / 255f,
                        outer.getBlue() / 255f, FIRE_RING_ALPHA * charge),
                true,
                0f,
                0f,
                FIRE_RING_FADE_OUT);

        // forward streak cone — inverse of the charge streaks
        int coneCount = (int) (charge * FIRE_CONE_COUNT_MAX);
        for (int i = 0; i < coneCount; i++) {
            float angle = facing + MathUtils.getRandomNumberInRange(-FIRE_CONE_SPREAD, FIRE_CONE_SPREAD);
            float speed = MathUtils.getRandomNumberInRange(FIRE_CONE_SPEED_MIN, FIRE_CONE_SPEED_MAX) * charge;
            Vector2f vel = MathUtils.getPointOnCircumference(ZERO, speed, angle);
            Vector2f.add(vel, shipVel, vel);

            SpriteAPI sprite = Global.getSettings().getSprite("misc", "armaa_streak");
            MagicRender.battlespace(
                    sprite,
                    new Vector2f(muzzle),
                    vel,
                    new Vector2f((sprite.getWidth() / 1.5f) * charge, (sprite.getHeight() / 1.5f) * charge),
                    new Vector2f(0f, 0f),
                    angle + 90f,
                    0f,
                    new Color(streak.getRed() / 255f, streak.getGreen() / 255f,
                            streak.getBlue() / 255f, FIRE_CONE_ALPHA * charge),
                    true,
                    0f,
                    0.25f * charge,
                    0.25f * charge);
        }
        Color glow = isRobot ? BETA_GLOW : ALPHA_GLOW;
        int puffCount = (int) (charge * FIRE_PUFF_COUNT_MAX);
        for (int i = 0; i < puffCount; i++) {
            float angle = facing + MathUtils.getRandomNumberInRange(-FIRE_PUFF_SPREAD, FIRE_PUFF_SPREAD);
            float speed = MathUtils.getRandomNumberInRange(FIRE_PUFF_SPEED_MIN, FIRE_PUFF_SPEED_MAX) * charge;
            Vector2f vel = MathUtils.getPointOnCircumference(ZERO, speed, angle);
            Vector2f.add(vel, shipVel, vel);

            float size = FIRE_PUFF_SIZE * charge * (0.7f + (float) Math.random() * 0.6f);
            float endSizeMult = FIRE_PUFF_END_SIZE_MULT_MIN
                    + (float) Math.random() * (FIRE_PUFF_END_SIZE_MULT_MAX - FIRE_PUFF_END_SIZE_MULT_MIN);
            float duration = MathUtils.getRandomNumberInRange(FIRE_PUFF_DUR_MIN, FIRE_PUFF_DUR_MAX);

            engine.addNebulaParticle(new Vector2f(muzzle), vel, size, endSizeMult,
                    FIRE_PUFF_RAMP_FRAC, FIRE_PUFF_FADE_FRAC, duration, glow);
        }
    }

    private static final float ALPHA_FIRE_PITCH_MIN = 0.8f;
    private static final float ALPHA_FIRE_PITCH_MAX = 1.3f;
    private static final float ALPHA_FIRE_VOL_MIN = 0.8f;
    private static final float ALPHA_FIRE_VOL_MAX = 1.4f;

    private void fireAlphaBolt(ShipAPI ship, CombatEngineAPI engine, float damage) {
        Vector2f origin = muzzleOrShip(ship);
        String wep = "armaa_waveCannon_alpha" + "_t" + tierForCharge(chargeTime);
        int tier = tierForCharge(chargeTime);

        // AI aim-assist with target leading (ALPHA travels). Spawn first, read the projectile's
        // real speed, then point it at the lead angle (so we don't hardcode per-tier speeds).
        DamagingProjectileAPI proj
                = (DamagingProjectileAPI) engine.spawnProjectile(ship, null, wep, origin, ship.getFacing(), ship.getVelocity());
        float projSpeed = (proj != null) ? proj.getVelocity().length() : 0f;
        float facing = computeFireAngle(ship, origin, true, projSpeed);
        if (proj != null) {
            // re-point the projectile along the assisted angle, preserving its speed
            float spd = proj.getVelocity().length();
            Vector2f newVel = MathUtils.getPointOnCircumference(ZERO, spd, facing);
            proj.getVelocity().set(newVel.x, newVel.y);
            proj.setFacing(facing);
        }
        float t = (tier - 1) / (float) (TIER_DAMAGE.length - 1); // 0, .33, .67, 1
        float pitch = ALPHA_FIRE_PITCH_MIN + (ALPHA_FIRE_PITCH_MAX - ALPHA_FIRE_PITCH_MIN) * t;
        float volume = ALPHA_FIRE_VOL_MIN + (ALPHA_FIRE_VOL_MAX - ALPHA_FIRE_VOL_MIN) * t;
        Global.getSoundPlayer().playSound("kinetic_blaster_fire", pitch, volume, origin, ship.getVelocity());

        //if (proj instanceof DamagingProjectileAPI) {
        //    ((DamagingProjectileAPI) proj).getDamage().setDamage(damage);
        //}
    }

    private void fireBetaHitscan(ShipAPI ship, CombatEngineAPI engine, float damage, int tier) {
        Vector2f origin = muzzleOrShip(ship);
        float facing = computeFireAngle(ship, origin, false, 0f); // BETA is instant -> aim at current pos, no lead
        Vector2f rayEnd = MathUtils.getPointOnCircumference(origin, 1000000f, facing); // long ray for reliable test
        Vector2f maxImpact = MathUtils.getPointOnCircumference(origin, BETA_HITSCAN_RANGE, facing);

        // ---- find the impact point: first ship the ray crosses (circle test + bounds refine) ----
        // We only need WHERE the beam stops; the explosion handles who's caught, so small
        // positional error is forgiven by the blast radius.
        Vector2f impact = maxImpact;
        float bestDist = BETA_HITSCAN_RANGE;

        for (CombatEntityAPI e : CombatUtils.getEntitiesWithinRange(origin, BETA_HITSCAN_RANGE)) {
            if (!(e instanceof ShipAPI) && !(e instanceof MissileAPI)) {
                continue;
            }
            if (e.getOwner() == ship.getOwner() || e.getOwner() == 100) {
                continue;
            }

            if (e.getCollisionClass() == CollisionClass.NONE) {
                continue;
            }
            float radius = e.getCollisionRadius();

            // 1) does the ray even cross this ship's bounding circle? (cheap reject + guaranteed fallback)
            if (!CollisionUtils.getCollides(origin, rayEnd, e.getLocation(), radius)) {
                continue;
            }

            // 2) try precise bounds intersection; fall back to circle-entry if bounds unusable/missed
            Vector2f hit = CollisionUtils.getCollisionPoint(origin, rayEnd, e);
            if (hit == null) {
                // bounds returned nothing (no bounds, or finicky) -> use circle entry point.
                // bounds are guaranteed inside the collision radius, so the circle is a valid outer hit.
                float centerAlong = projectOntoRay(origin, rayEnd, e.getLocation());
                float perp = perpDistToRay(origin, rayEnd, e.getLocation());
                float half = (float) Math.sqrt(Math.max(0f, radius * radius - perp * perp));
                float along = Math.max(0f, centerAlong - half);
                hit = MathUtils.getPointOnCircumference(origin, along, facing);
            }

            float dist = MathUtils.getDistance(origin, hit);
            if (dist > BETA_HITSCAN_RANGE) {
                continue; // crossing is beyond our actual range
            }
            if (dist < bestDist) {
                bestDist = dist;
                impact = hit;
            }
        }

        // ---- AoE HE explosion with falloff (engine resolves who's caught) ----
        float blastRadius = BETA_BLAST_RADIUS_BASE + BETA_BLAST_RADIUS_PER_TIER * (tier - 1);
        float coreRadius = blastRadius * BETA_BLAST_CORE_FRAC;
        DamagingExplosionSpec spec = new DamagingExplosionSpec(
                0.3f, // duration
                blastRadius, // radius (outer, edge damage)
                coreRadius, // core radius (full damage)
                damage, // maxDamage (center)
                damage * BETA_BLAST_MIN_DAMAGE_FRAC, // minDamage (edge)
                CollisionClass.PROJECTILE_FF, // collisionClass
                CollisionClass.PROJECTILE_FIGHTER, // collisionClassByFighter
                0f, 0f, 0f, 0, // particle size/range/dur/count (0 -> our own VFX)
                new Color(0, 0, 0, 0), // particle color (unused)
                new Color(0, 0, 0, 0));                           // explosion color
        spec.setDamageType(BETA_DAMAGE_TYPE);          // HIGH_EXPLOSIVE
        engine.spawnDamagingExplosion(spec, ship, new Vector2f(impact), false); // canDamageSource=false

        // ---- EMP-heavy disable splash to surrounding ships (scales with tier) ----
        float empRadius = BETA_EMP_RADIUS_BASE + BETA_EMP_RADIUS_PER_TIER * (tier - 1);
        float empDamage = BETA_EMP_PER_TIER * tier;
        float empEnergy = empDamage * BETA_EMP_ENERGY_FRAC;
        int empArcs = BETA_EMP_ARCS_BASE + BETA_EMP_ARCS_PER_TIER * (tier - 1);
        for (ShipAPI e : CombatUtils.getShipsWithinRange(impact, empRadius)) {
            if (e.getOwner() == ship.getOwner() || e.getOwner() == 100) {
                continue;
            }
            if (e.getCollisionClass() == CollisionClass.NONE) {
                continue;
            }
            for (int a = 0; a < empArcs; a++) {
                if (Math.random() > 0.50f) {
                    engine.spawnEmpArc(ship, new Vector2f(impact), e, e,
                            DamageType.ENERGY, empEnergy, empDamage, empRadius,
                            "tachyon_lance_emp_impact", 5f, BETA_OUTER, BETA_INNER);
                }
            }
        }

        spawnBetaDetonation(ship, engine, impact, tier);
    }

    private void spawnBetaDetonation(ShipAPI ship, CombatEngineAPI engine, Vector2f origin, int tier) {
        float scale = 0.6f + 0.25f * tier;            // tier 1 ~0.85x ... tier 4 ~1.6x
        float coreSize = BETA_EXPLOSION_RADIUS * scale;

        for (int i = 0; i < 3; i++) {
            engine.spawnEmpArcVisual(muzzleOrShip(ship), ship, origin, null, 5f, BETA_INNER, BETA_OUTER);
        }
        String slotId = MUZZLE_SLOT_BETA;
        WeaponAPI w = findWeapon(ship, slotId);
        float facing = w.getCurrAngle();
        /*
        MagicFakeBeam.spawnFakeBeam(
                engine,
                muzzleOrShip(ship),
                BETA_HITSCAN_RANGE,
                facing,
                coreSize / 4f,
                .11f,
                .1f,
                coreSize / 4f,
                BETA_INNER,
                BETA_OUTER,
                0f,
                DamageType.OTHER,
                0f,
                ship);
         */
        // expanding shockwave ring
        float ringStart = coreSize * 0.6f;
        float ringGrow = coreSize * 1.5f;
        MagicRender.battlespace(
                Global.getSettings().getSprite("misc", "armaa_sfxpulse"),
                new Vector2f(origin),
                new Vector2f(),
                new Vector2f(ringStart, ringStart),
                new Vector2f(ringGrow, ringGrow),
                0f,
                0f,
                DETO_RING_COLOR,
                true,
                0.04f,
                0.0f,
                0.35f * scale
        );

        // core flash
        engine.spawnExplosion(new Vector2f(origin), new Vector2f(), DETO_CORE_COLOR, coreSize / 2f, 0.45f);
        //engine.addHitParticle(new Vector2f(origin), ZERO, coreSize * 0.8f, 1f, 0.1f, DETO_FLASH_COLOR);
        //float endSizeMult = 1.8f + (float) Math.random() * 0.8f; // grows as it fades

        //engine.addNebulaParticle(origin, new Vector2f(), coreSize/2f, endSizeMult,
        //        0.15f, // ramp-up fraction
        //        0.55f, // fade fraction
        //        1f,
        //        DETO_CORE_COLOR);
        // secondary pops in a ring
        int secondaryCount = 1 + tier * 2;
        for (int i = 0; i < secondaryCount; i++) {
            float angle = (360f / secondaryCount) * i + (float) Math.random() * (360f / secondaryCount);
            float dist = coreSize * (0.4f + (float) Math.random() * 0.6f);
            Vector2f pos = MathUtils.getPointOnCircumference(origin, dist, angle);
            float size = coreSize * (0.25f + (float) Math.random() * 0.35f);

            //engine.spawnExplosion(pos, vel, DETO_SECONDARY_COLOR, size, dur);
            MagicLensFlare.createSharpFlare(
                    Global.getCombatEngine(),
                    ship,
                    pos,
                    5,
                    size,
                    angle,
                    BETA_OUTER,
                    BETA_INNER
            );
            if (Math.random() < 0.75f) {
                engine.spawnEmpArcVisual(origin, null, pos, null, 2f, BETA_INNER, BETA_OUTER);
            }

        }

        // radial spark burst
        int sparkCount = 5 + tier * 4;
        for (int i = 0; i < sparkCount; i++) {
            float angle = (float) Math.random() * 360f;
            float speed = (120f + (float) Math.random() * 380f) * scale;
            Vector2f vel = MathUtils.getPointOnCircumference(ZERO, speed, angle);
            float size = (3f + (float) Math.random() * 7f);
            engine.addHitParticle(new Vector2f(origin), vel, size, 1f,
                    0.5f + (float) Math.random() * 0.5f, DETO_SPARK_COLOR);
        }

        // nebula bloom (outward + swirl) — the charged-gas residue
        //spawnNebulaBloom(engine, origin, scale, tier);
        Global.getSoundPlayer().playSound("armaa_buster_hit", 0.8f, 1f, origin, ZERO);
        // Global.getSoundPlayer().playSound("explosion_from_damage", 1f, 1f, origin, ZERO);
    }

    /**
     * Soft nebula bloom for the energy residue: particles blow outward from the
     * impact (radial velocity) with a tangential component for swirl, and grow
     * over their life via endSizeMult. Larger, slower, longer-lived than the
     * sparks so they read as a charged cloud lingering after the strike.
     */
    private void spawnNebulaBloom(CombatEngineAPI engine, Vector2f origin, float scale, int tier) {
        int count = 8 + tier * 4;
        float baseSize = (BETA_EXPLOSION_RADIUS / 2f) * 0.45f * scale;
        for (int i = 0; i < count; i++) {
            float angle = (float) Math.random() * 360f;
            float outSpeed = (40f + (float) Math.random() * 90f) * scale;
            // radial (outward) component
            Vector2f vel = MathUtils.getPointOnCircumference(ZERO, outSpeed, angle);
            // tangential (swirl) component: perpendicular to outward, random direction
            float swirlDir = Math.random() < 0.5f ? 90f : -90f;
            float swirlSpeed = (20f + (float) Math.random() * 50f) * scale;
            Vector2f swirl = MathUtils.getPointOnCircumference(ZERO, swirlSpeed, angle + swirlDir);
            Vector2f.add(vel, swirl, vel);

            // spawn slightly off-center so the cloud isn't a perfect point
            Vector2f pos = MathUtils.getPointOnCircumference(origin,
                    (float) Math.random() * baseSize * 0.5f, angle);

            float size = baseSize * (0.6f + (float) Math.random() * 0.7f);
            float endSizeMult = 1.8f + (float) Math.random() * 0.8f; // grows as it fades
            float duration = 0.8f + (float) Math.random() * 0.7f;

            engine.addNebulaParticle(pos, vel, size, endSizeMult,
                    0.15f, // ramp-up fraction
                    0.55f, // fade fraction
                    duration,
                    DETO_NEBULA_COLOR);
        }
    }

    // ---- VFX --------------------------------------------------------------------------------
    private void spawnChargeUpFX(CombatEngineAPI engine, ShipAPI ship, float charge, boolean isRobot) {
        Vector2f muzzle = muzzleOrShip(ship);
        Vector2f shipVel = ship.getVelocity();

        Color inner = isRobot ? BETA_INNER : ALPHA_INNER;
        Color outer = isRobot ? BETA_OUTER : ALPHA_OUTER;
        Color streak = isRobot ? BETA_STREAK : ALPHA_STREAK;
        Color glow = isRobot ? BETA_GLOW : ALPHA_GLOW;

        if (Math.random() < CORE_FLICKER_CHANCE) {
            float coreSize = (CORE_SIZE_MIN + (CORE_SIZE_MAX - CORE_SIZE_MIN) * charge)
                    * (0.75f + (float) Math.random() * 0.5f);
            float brightness = CORE_BRIGHTNESS_MIN + (CORE_BRIGHTNESS_MAX - CORE_BRIGHTNESS_MIN) * charge;
            engine.addHitParticle(muzzle, shipVel, coreSize * 1.8f, brightness * 0.35f, CORE_DURATION * 2f, outer);
            engine.addHitParticle(muzzle, shipVel, coreSize, brightness, CORE_DURATION, inner);
        }
        if (Math.random() < GLOW_CHANCE * charge) {
            float glowRadius = MathUtils.getRandomNumberInRange(5f, CORE_SIZE_MAX * 0.6f * charge);
            Vector2f glowPos = MathUtils.getRandomPointInCircle(muzzle, glowRadius);
            engine.addSmoothParticle(glowPos, shipVel,
                    MathUtils.getRandomNumberInRange(GLOW_SIZE_MIN, GLOW_SIZE_MAX),
                    charge,
                    MathUtils.getRandomNumberInRange(GLOW_DURATION_MIN, GLOW_DURATION_MAX),
                    glow);
        }
        streakInterval.advance(engine.getElapsedInLastFrame());
        if (!streakInterval.intervalElapsed()) {
            return;
        }
        int streakCount = (int) (charge * STREAK_COUNT_MAX);
        //if (charge > 0.9f) {
        //    streakCount += 3;
        //}
        //come back
        for (int i = 0; i < streakCount; i++) {
            muzzle = muzzleOrShip(ship);
            float spawnRadius = MathUtils.getRandomNumberInRange(
                    STREAK_SPAWN_RADIUS_MIN + (1f - charge) * STREAK_RADIUS_CHARGE_BIAS,
                    STREAK_SPAWN_RADIUS_MAX);
            float spawnAngle = MathUtils.getRandomNumberInRange(0f, 360f);
            Vector2f spawnPos = MathUtils.getPointOnCircumference(muzzle, spawnRadius, spawnAngle);

            // travel inward toward the muzzle
            Vector2f toCenter = Vector2f.sub(muzzle, spawnPos, new Vector2f());
            float dist = toCenter.length();
            if (dist < 1f) {
                continue;
            }
            toCenter.scale(1f / dist);

            float speed = MathUtils.getRandomNumberInRange(STREAK_SPEED_MIN, STREAK_SPEED_MAX) * charge;
            Vector2f vel = new Vector2f(toCenter.x * speed, toCenter.y * speed);
            Vector2f.add(vel, shipVel, vel);

            float alpha = STREAK_ALPHA * charge;
            float angle = VectorUtils.getAngle(spawnPos, muzzle) + 90f;

            SpriteAPI sprite = Global.getSettings().getSprite("misc", "armaa_streak");
            MagicRender.battlespace(
                    sprite,
                    new Vector2f(spawnPos),
                    vel, // real inward motion + ship tracking
                    new Vector2f((sprite.getWidth() / 2f) * charge, (sprite.getHeight() / 2f) * charge),
                    new Vector2f(0f, 0f), // no growth
                    angle,
                    0f, // no spin
                    new Color(streak.getRed() / 255f, streak.getGreen() / 255f,
                            streak.getBlue() / 255f, alpha),
                    true, // additive
                    0f, // fade in
                    STREAK_FULL_DURATION,
                    STREAK_FADE_OUT
            );
        }
    }

    private final IntervalUtil maxPulseInterval = new IntervalUtil(MAX_PULSE_INTERVAL, MAX_PULSE_INTERVAL);
// ---- VFX: max-charge sustain ------------------------------------------------------------
    private static final int MAX_SPRAY_COUNT = 2;
    private static final float MAX_SPRAY_SPEED_MIN = 80f;
    private static final float MAX_SPRAY_SPEED_MAX = 220f;
    private static final float MAX_SPRAY_SIZE_MIN = 3f;
    private static final float MAX_SPRAY_SIZE_MAX = 8f;
    private static final float MAX_SPRAY_DUR_MIN = 0.2f;
    private static final float MAX_SPRAY_DUR_MAX = 0.5f;
    private static final float MAX_JITTER_CHANCE = 0.4f;
    private static final float MAX_PULSE_INTERVAL = 0.25f;
    private static final float MAX_RING_START_MULT = 0.8f;
    private static final float MAX_RING_GROW_MULT = 4.0f;
    private static final float MAX_RING_ALPHA = 0.5f;
    private static final float MAX_RING_FADE_OUT = 0.4f;

    private void spawnMaxSpray(CombatEngineAPI engine, ShipAPI ship, boolean isRobot) {
        Vector2f muzzle = muzzleOrShip(ship);
        Vector2f shipVel = ship.getVelocity();
        Color inner = isRobot ? BETA_INNER : ALPHA_INNER;
        Color outer = isRobot ? BETA_OUTER : ALPHA_OUTER;
        Color streak = isRobot ? BETA_STREAK : ALPHA_STREAK;

        // fast spark spray — energy venting outward, runs every sprayInterval tick
        for (int i = 0; i < MAX_SPRAY_COUNT; i++) {
            float angle = MathUtils.getRandomNumberInRange(0f, 360f);
            float speed = MathUtils.getRandomNumberInRange(MAX_SPRAY_SPEED_MIN, MAX_SPRAY_SPEED_MAX);
            Vector2f vel = MathUtils.getPointOnCircumference(ZERO, speed, angle);
            Vector2f.add(vel, shipVel, vel);
            float size = MathUtils.getRandomNumberInRange(MAX_SPRAY_SIZE_MIN, MAX_SPRAY_SIZE_MAX);
            engine.addHitParticle(new Vector2f(muzzle), vel, size, 1f,
                    MathUtils.getRandomNumberInRange(MAX_SPRAY_DUR_MIN, MAX_SPRAY_DUR_MAX), streak);
        }

        // unstable core jitter — bright flicker so the orb reads as "straining"
        if (Math.random() < MAX_JITTER_CHANCE) {
            engine.addHitParticle(muzzle, shipVel, CORE_SIZE_MAX * 1.6f, 1f, 0.05f, outer);
            engine.addHitParticle(muzzle, shipVel, CORE_SIZE_MAX, 1f, 0.05f, inner);
        }

        // slower containment-ring pulse — the rhythmic "it's full" heartbeat
        maxPulseInterval.advance(engine.getElapsedInLastFrame());
        if (maxPulseInterval.intervalElapsed()) {
            float ringStart = CORE_SIZE_MAX * MAX_RING_START_MULT;
            float ringGrow = CORE_SIZE_MAX * MAX_RING_GROW_MULT;
            MagicRender.battlespace(
                    Global.getSettings().getSprite("misc", "armaa_sfxpulse"),
                    new Vector2f(muzzle),
                    new Vector2f(shipVel),
                    new Vector2f(ringStart, ringStart),
                    new Vector2f(ringGrow, ringGrow),
                    ship.getFacing(),
                    0f,
                    new Color(outer.getRed() / 255f, outer.getGreen() / 255f,
                            outer.getBlue() / 255f, MAX_RING_ALPHA),
                    true, 0f, 0f, MAX_RING_FADE_OUT);
        }
    }

    /**
     * One-shot tell fired on each tier-up crossing (0->1, 1->2, 2->3, 3->4).
     * Flash, snap ring, and ping all scale with the tier just reached, so each
     * step up is a discrete, louder/higher cue. Tier 4 is the loudest case.
     */
    private void fireTierTell(ShipAPI ship, CombatEngineAPI engine, int tier) {
        Vector2f muzzle = muzzleOrShip(ship);
        Vector2f shipVel = ship.getVelocity();
        boolean isRobot = readIsRobot(ship, engine);
        Color inner = isRobot ? BETA_INNER : ALPHA_INNER;
        Color outer = isRobot ? BETA_OUTER : ALPHA_OUTER;

        float t = tier / 4f; // 0.25, 0.5, 0.75, 1.0

        // flash scales with tier
        engine.addHitParticle(muzzle, shipVel, CORE_SIZE_MAX * (1f + 1.5f * t), 1f, 0.15f, inner);
        engine.addHitParticle(muzzle, shipVel, CORE_SIZE_MAX * (0.6f + 0.8f * t), 1f, 0.1f,
                new Color(255, 255, 255, 255));

        // snap ring scales with tier
        float ringStart = CORE_SIZE_MAX * (0.3f + 0.4f * t);
        float ringGrow = CORE_SIZE_MAX * (2f + 4f * t);
        MagicRender.battlespace(
                Global.getSettings().getSprite("misc", "armaa_sfxpulse"),
                new Vector2f(muzzle), new Vector2f(shipVel),
                new Vector2f(ringStart, ringStart),
                new Vector2f(ringGrow, ringGrow),
                ship.getFacing(), 0f,
                new Color(outer.getRed() / 255f, outer.getGreen() / 255f, outer.getBlue() / 255f, 0.9f),
                true, 0f, 0f, 0.35f);

        // pitch climbs per tier so each "ping" is audibly a step up
        float pitch = 0.85f + 0.25f * t;
        Global.getSoundPlayer().playSound("ui_industry_install_any_item", pitch, 0.8f + 0.6f * t,
                muzzle, ship.getVelocity());
    }

    // ---- muzzle / geometry helpers ----------------------------------------------------------
    /**
     * Muzzle position of the mode-appropriate slot, or ship location if not
     * found.
     */
    private Vector2f muzzleOrShip(ShipAPI ship) {
        boolean isRobot = readIsRobot(ship, Global.getCombatEngine());
        String slotId = isRobot ? MUZZLE_SLOT_BETA : MUZZLE_SLOT_ALPHA;
        WeaponAPI w = findWeapon(ship, slotId);
        if (w == null) {
            return new Vector2f(ship.getLocation());
        }
        return getMuzzlePos(w);
    }

    private WeaponAPI findWeapon(ShipAPI ship, String slotId) {
        for (WeaponAPI w : ship.getAllWeapons()) {
            if (w.getSlot() != null && slotId.equals(w.getSlot().getId())) {
                return w;
            }
        }
        return null;
    }

    private float computeFireAngle(ShipAPI ship, Vector2f muzzle, boolean lead, float projSpeed) {
        boolean isRobot = readIsRobot(ship, Global.getCombatEngine());
        String slotId = isRobot ? MUZZLE_SLOT_BETA : MUZZLE_SLOT_ALPHA;
        WeaponAPI w = findWeapon(ship, slotId);
        float base = w.getCurrAngle();

        ShipAPI target = pickTarget(ship, muzzle, base);
        if (target == null) {
            return base;
        }

        float toTarget = VectorUtils.getAngle(muzzle, target.getLocation());
        float off = MathUtils.getShortestRotation(base, toTarget);
        off = MathUtils.clamp(off, -AIM_ASSIST_MAX_DEVIATION, AIM_ASSIST_MAX_DEVIATION);
        return base + off;
    }

    private ShipAPI pickTarget(ShipAPI ship, Vector2f muzzle, float base) {
        boolean player = ship.getShipAI() == null;
        ShipAPI best = null;
        float bestScore = Float.MAX_VALUE;
        Vector2f cursor = player ? ship.getMouseTarget() : null;

        // AI: ship target first, if valid
        if (!player) {
            ShipAPI st = ship.getShipTarget();
            if (st != null && st.isAlive() && st.getOwner() != ship.getOwner()
                    && MathUtils.getDistance(muzzle, st.getLocation()) <= BETA_HITSCAN_RANGE
                    && Math.abs(MathUtils.getShortestRotation(base,
                            VectorUtils.getAngle(muzzle, st.getLocation()))) <= AIM_ASSIST_MAX_DEVIATION) {
                return st;
            }
        }

        for (ShipAPI e : CombatUtils.getShipsWithinRange(muzzle, BETA_HITSCAN_RANGE)) {
            if (e.getOwner() == ship.getOwner() || e.getOwner() == 100 || !e.isAlive()) {
                continue;
            }
            if (e.getCollisionClass() == CollisionClass.NONE) {
                continue; // skips phased ships too
            }
            float ang = VectorUtils.getAngle(muzzle, e.getLocation());
            float off = Math.abs(MathUtils.getShortestRotation(base, ang));
            if (off > AIM_ASSIST_MAX_DEVIATION) {
                continue;
            }
            // player: prefer proximity to cursor; both: tiebreak by angular offset
            float score = (cursor != null) ? MathUtils.getDistance(cursor, e.getLocation()) : off;
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

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

    /**
     * Perpendicular distance from point to the infinite line origin->end.
     */
    private float perpDistToRay(Vector2f origin, Vector2f end, Vector2f point) {
        float dx = end.x - origin.x;
        float dy = end.y - origin.y;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len <= 0f) {
            return MathUtils.getDistance(origin, point);
        }
        float ux = dx / len;
        float uy = dy / len;
        float px = point.x - origin.x;
        float py = point.y - origin.y;
        return Math.abs(px * uy - py * ux);
    }

    private float projectOntoRay(Vector2f origin, Vector2f end, Vector2f point) {
        float dx = end.x - origin.x;
        float dy = end.y - origin.y;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len <= 0f) {
            return 0f;
        }
        float ux = dx / len;
        float uy = dy / len;
        float px = point.x - origin.x;
        float py = point.y - origin.y;
        return px * ux + py * uy;
    }

    // ---- misc helpers -----------------------------------------------------------------------
    private int tierForCharge(float t) {
        if (t >= TIER_4_TIME) {
            return 4;
        }
        if (t >= TIER_3_TIME) {
            return 3;
        }
        if (t >= TIER_2_TIME) {
            return 2;
        }
        if (t >= TIER_1_TIME) {
            return 1;
        }
        return 0;
    }

    private float mix(float a, float b, float t) {
        if (t < 0f) {
            t = 0f;
        }
        if (t > 1f) {
            t = 1f;
        }
        return a + (b - a) * t;
    }

    private boolean readIsRobot(ShipAPI ship, CombatEngineAPI engine) {
        Object v = engine.getCustomData().get("armaa_tranformState_" + ship.getId());
        return v instanceof Boolean ? (Boolean) v : true;
    }

    private void registerListener(ShipAPI ship) {
        if (listener == null) {
            listener = new DegradeListener();
        }
        ship.addListener(listener);
    }

    private void unregisterListener(ShipAPI ship) {
        if (listener != null) {
            ship.removeListener(listener);
        }
    }

    private class DegradeListener implements DamageListener {

        @Override
        public void reportDamageApplied(Object source, CombatEntityAPI target, ApplyDamageResultAPI result) {
            if (result != null) {
                float raw = result.getDamageToHull()
                        + result.getTotalDamageToArmor()
                        + result.getDamageToShields();
                chargeTime = Math.max(0f, chargeTime - raw * DEGRADE_SECONDS_PER_DAMAGE);
            }
        }
    }
}
