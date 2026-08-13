package data.scripts.weapons.basilisk;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import org.lazywizard.lazylib.CollisionUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;

import java.awt.Color;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Basilisk warhead. Two-phase:
 *
 *   1. Native DamagingExplosion (graphic suppressed) -- the engine handles
 *      falloff, armour cells, shields and hit strength. Our own VFX go on top.
 *   2. Manual expanding wave, hitting each ship ONCE as it passes.
 *
 * Yield scales by 'attenuate' (0..1), the missile's charge when it went off.
 * Damage scales on sqrt, radius scales linearly.
 *
 * The shockwave is a single MagicRender sprite spawned once at detonation with
 * growth matched to EXPANSION_RATE, so it stays in lockstep with the damage
 * without being re-rendered per frame.
 */
public class armaa_BasiliskDetonation {

    // ---- yield ---------------------------------------------------------
    public static final float INITIAL_BLAST_DAMAGE = 8000f;
    public static final float INITIAL_BLAST_RADIUS = 400f;
    /** Fraction of radius that takes full damage. */
    public static final float CORE_FRACTION = 0.25f;
    /** Damage multiplier at the outer edge. */
    public static final float EDGE_FRACTION = 0.2f;

    /** ONE hit per ship as the wave passes. Not DPS. */
    public static final float EXPANSION_HIT_DAMAGE = 800f;
    public static final float EXPANSION_HIT_EMP = 0f;

    public static final float EXPANSION_RATE = 180f;   // su/sec
    public static final float EXPANSION_TIME = 5f;     // sec

    public static final DamageType DAMAGE_TYPE = DamageType.HIGH_EXPLOSIVE;

    /** Floor so an instantly-killed missile still pops. */
    public static final float MIN_ATTENUATE = 0.15f;

    /** Fixed evaluation interval for the wave. */
    public static final float ACCUM_INTERVAL = 0.05f;

    // ---- visuals -------------------------------------------------------
    /** How far behind the leading edge the trailing smoke sits. */
    public static final float RING_VISUAL_WIDTH = 90f;

    public static final String PULSE_SPRITE_CATEGORY = "misc";
    public static final String PULSE_SPRITE_ID = "armaa_sfxpulse";

    /**
     * Where the bright ring sits within the sprite, as a fraction of its half
     * width. 1.0 means the ring is at the very edge. Lower this if the visible
     * front lands inside where damage triggers.
     */
    public static final float PULSE_RING_PEAK = 1.0f;

    public static final Color CORE_COLOR = new Color(255, 100, 0, 175);
    public static final Color MID_COLOR = new Color(255, 75, 0, 125);
    public static final Color FRINGE_COLOR = new Color(255, 140, 50, 255);
    public static final Color SMOKE_COLOR = new Color(90, 90, 95, 170);
    public static final Color RING_COLOR = new Color(255, 180, 90, 220);

    /** Transparent, since setShowGraphic(false) suppresses the native VFX anyway. */
    private static final Color NO_COLOR = new Color(0, 0, 0, 0);

    public static final String SOUND_ID = "explosion_ship";

    private static final Random RNG = new Random();
    private static final Vector2f ZERO = new Vector2f();

    // --------------------------------------------------------------------
    public static void detonate(CombatEngineAPI engine, Vector2f loc, ShipAPI source, float attenuate) {
        if (engine == null || loc == null) {
            return;
        }
        float atten = clamp(attenuate, MIN_ATTENUATE, 1f);
        Vector2f at = new Vector2f(loc);

        float blastDamage = INITIAL_BLAST_DAMAGE * (float) Math.sqrt(atten);
        float blastRadius = INITIAL_BLAST_RADIUS * atten;

        // Build the exclusion set BEFORE the explosion, so the wave skips
        // anything phase 1 already covered. No damage applied here.
        Set<ShipAPI> caught = shipsInRadius(engine, at, blastRadius, source);

        // ---- phase 1: native explosion, our graphics ----
        DamagingExplosionSpec spec = new DamagingExplosionSpec(
                0.1f,                            // duration
                blastRadius,                     // radius
                blastRadius * CORE_FRACTION,     // core radius: full damage inside this
                blastDamage,                     // max damage (core)
                blastDamage * EDGE_FRACTION,     // min damage (edge)
                CollisionClass.MISSILE_FF,
                CollisionClass.MISSILE_FF,
                0f,                              // particle size min
                0f,                              // particle size range
                0f,                              // particle duration
                0,                               // particle count
                NO_COLOR,
                NO_COLOR);
        spec.setDamageType(DAMAGE_TYPE);
        spec.setShowGraphic(false);
        spec.setUseDetailedExplosion(false);

        DamagingProjectileAPI blastProj =
                engine.spawnDamagingExplosion(spec, source, at, false);

        doInitialVisuals(engine, at, blastRadius, atten);
        spawnShockwaveSprite(at, blastRadius, atten);

        Global.getSoundPlayer().playSound(SOUND_ID, 0.5f + 0.3f * atten, 1.2f + 0.6f * atten, at, ZERO);

        // ---- phase 2: manual wave ----
        // Attribute to the explosion projectile so both phases report as one source.
        Object attribution = (blastProj != null) ? blastProj : source;

        armaa_BasiliskTracker tracker = armaa_BasiliskTracker.get(engine);
        if (tracker != null) {
            ExpandingBlast blast = new ExpandingBlast(at, atten, source, attribution);
            blast.alreadyHit.addAll(caught);
            tracker.addBlast(blast);
        }
    }

    /**
     * One sprite, spawned once, growing at the same rate the damage wave
     * expands. Growth and lifetime MUST mirror ExpandingBlast or the visible
     * front will drift out of sync with the hits.
     */
    private static void spawnShockwaveSprite(Vector2f at, float blastRadius, float atten) {
        SpriteAPI waveSprite = Global.getSettings().getSprite(PULSE_SPRITE_CATEGORY, PULSE_SPRITE_ID);
        if (waveSprite == null) {
            return;
        }

        float rate = EXPANSION_RATE * (float) Math.sqrt(atten);   // matches ExpandingBlast.rate
        float life = EXPANSION_TIME * atten;                      // matches ExpandingBlast.duration

        // Size is a diameter; radius grows at `rate`, so diameter grows at 2x.
        float startD = (blastRadius * 2f) / PULSE_RING_PEAK;
        float growD = (rate * 2f) / PULSE_RING_PEAK;

        float fadeIn = 0.05f;
        float fadeOut = 0.5f;
        float full = Math.max(0.1f, life - fadeIn - fadeOut);

        MagicRender.battlespace(
                waveSprite,
                new Vector2f(at),                       // location
                new Vector2f(),                         // velocity
                new Vector2f(startD, startD),           // size
                new Vector2f(growD, growD),             // growth per second
                0f,                                     // angle
                0f,                                     // spin
                new Color(RING_COLOR.getRed(), RING_COLOR.getGreen(),
                        RING_COLOR.getBlue(), 100),
                true,                                   // additive
                fadeIn,
                0f,
                full+fadeOut);
    }

    /** Ships whose hull falls inside the radius. No damage -- exclusion set only. */
    private static Set<ShipAPI> shipsInRadius(CombatEngineAPI engine, Vector2f at,
                                              float radius, ShipAPI source) {
        Set<ShipAPI> hit = new HashSet<ShipAPI>();
        for (ShipAPI ship : engine.getShips()) {
            if (!isValidTarget(ship, source)) {
                continue;
            }
            if (distance(at, ship.getLocation()) - ship.getCollisionRadius() <= radius) {
                hit.add(ship);
            }
        }
        return hit;
    }

    /**
     * Where the wave actually connects.
     *
     * A point on the HULL sits inside the shield bubble, so the engine resolves
     * it straight to armour and a raised shield never gets a chance to eat it.
     * Test the shield first and put the point on its perimeter instead -- that
     * makes shield facing matter, same as any projectile.
     */
    private static Vector2f hitPoint(Vector2f from, ShipAPI ship) {
        ShieldAPI shield = ship.getShield();
        if (shield != null && shield.isOn()) {
            Vector2f centre = shield.getLocation();
            float ang = angleTo(centre, from);
            Vector2f onShield = Vector2f.add(centre, fromAngle(ang, shield.getRadius()), null);
            if (shield.isWithinArc(onShield)) {
                return onShield;   // shield eats it
            }
        }
        // No shield, shield down, or the wave arrived outside the arc.
        Vector2f p = CollisionUtils.getCollisionPoint(from, ship.getLocation(), ship);
        return (p != null) ? p : new Vector2f(ship.getLocation());
    }

    /**
     * Distance at which the wave should register a hit. Shield perimeter when
     * raised, hull centre otherwise, so a wide shield is not struck late.
     */
private static float triggerDistance(Vector2f from, ShipAPI ship) {
    ShieldAPI shield = ship.getShield();
    if (shield != null && shield.isOn()) {
        Vector2f centre = shield.getLocation();
        float ang = angleTo(centre, from);
        Vector2f onShield = Vector2f.add(centre, fromAngle(ang, shield.getRadius()), null);
        if (shield.isWithinArc(onShield)) {
            return shield.getRadius();
        }
    }
    return 0f;
}

    // --------------------------------------------------------------------
    // phase 2: expanding wave
    // --------------------------------------------------------------------
    public static class ExpandingBlast {

        public final Vector2f loc = new Vector2f();
        public final float atten;
        public final ShipAPI source;
        /** Damage source for analytics. Fixed at detonation time. */
        public final Object attribution;
        public final float duration;
        public final float rate;
        public float t = 0f;
        public float accum = 0f;
        public float radius;

        /** Each ship is hit exactly once, when the wave first reaches it. */
        final Set<ShipAPI> alreadyHit = new HashSet<ShipAPI>();

        public ExpandingBlast(Vector2f loc, float atten, ShipAPI source, Object attribution) {
            this.loc.set(loc);
            this.atten = atten;
            this.source = source;
            this.attribution = attribution;
            this.duration = EXPANSION_TIME * atten;
            this.rate = EXPANSION_RATE * (float) Math.sqrt(atten);
            this.radius = INITIAL_BLAST_RADIUS * atten;
        }

        public boolean isDone() {
            return t >= duration;
        }

        /** Returns true when finished. */
        public boolean advance(CombatEngineAPI engine, float amount) {
            t += amount;
            radius += rate * amount;

            accum += amount;
            while (accum >= ACCUM_INTERVAL) {
                accum -= ACCUM_INTERVAL;
                tick(engine);
            }
            if (engine.getViewport().isNearViewport(loc, radius + 500f)) {
                doRingVisuals(engine, loc, radius, atten, t / Math.max(0.001f, duration));
            }
            return isDone();
        }

        private void tick(CombatEngineAPI engine) {
            for (ShipAPI ship : engine.getShips()) {
                if (!isValidTarget(ship, source)) {
                    continue;
                }
                if (alreadyHit.contains(ship)) {
                    continue;
                }

                // Centre distance, less the shield radius when one is raised.
                // Triggering off collision radius fires when the wave reaches a
                // capital's bounding circle, well outside its sprite.
                if (distance(loc, ship.getLocation()) - triggerDistance(loc,ship) > radius) {
                    continue;
                }

                alreadyHit.add(ship);

                float dmg = EXPANSION_HIT_DAMAGE * (float) Math.sqrt(atten);
                float emp = EXPANSION_HIT_EMP * (float) Math.sqrt(atten);

                engine.applyDamage(ship, hitPoint(loc, ship), dmg, DAMAGE_TYPE, emp,
                        false,          // bypassShields -- shield facing decides
                        false,          // no extra fighter multiplier
                        attribution,    // damage source
                        true);
            }
        }
    }

    private static boolean isValidTarget(ShipAPI ship, ShipAPI source) {
        if (ship == null || !ship.isAlive()) {
            return false;
        }
        if (ship.isHulk() || ship.isShuttlePod()) {
            return false;
        }
        if (source != null && ship.getOwner() == source.getOwner()) {
            return false;
        }
        return true;
    }

    // --------------------------------------------------------------------
    // visuals
    // --------------------------------------------------------------------
    private static void doInitialVisuals(CombatEngineAPI engine, Vector2f at, float radius, float atten) {
        float scale = 0.5f + 0.5f * atten;

        engine.spawnExplosion(at, ZERO, CORE_COLOR, radius * 5f, 4f);
        engine.addHitParticle(at, ZERO, radius * 2.2f, 1f, 0.4f, CORE_COLOR);
        engine.addSmoothParticle(at, new Vector2f(0,0), radius * 5f, 2, 0.1f, Color.white);
        int ringCount = (int) (42 * scale);
        for (int i = 0; i < ringCount; i++) {
            float a = (360f / ringCount) * i;
            Vector2f vel = fromAngle(a, 700f + RNG.nextFloat() * 500f);
            engine.addSmoothParticle(new Vector2f(at), vel, 26f * scale, 1f, 2f, MID_COLOR);
            vel = fromAngle(a, 700f / 4f + RNG.nextFloat() * 500f / 2f);
            engine.spawnExplosion(at, vel, CORE_COLOR, radius, 1f);
        }

        for (int i = 0; i < ringCount / 2; i++) {
            float a = RNG.nextFloat() * 360f;
            Vector2f vel = fromAngle(a, 200f + RNG.nextFloat() * 300f);
            engine.addSmoothParticle(new Vector2f(at), vel, 45f * scale, 0.8f, 2f, FRINGE_COLOR);
        }

        int secondaries = (int) (7 * scale);
        for (int i = 0; i < secondaries; i++) {
            float a = RNG.nextFloat() * 360f;
            float r = RNG.nextFloat() * radius * 0.7f;
            Vector2f p = Vector2f.add(at, fromAngle(a, r), null);
            engine.spawnExplosion(p, ZERO, MID_COLOR,
                    radius * (0.25f + RNG.nextFloat() * 0.3f),
                    0.4f + RNG.nextFloat() * 0.4f);
        }

        int smoke = (int) (30 * scale);
        for (int i = 0; i < smoke; i++) {
            float a = RNG.nextFloat() * 360f;
            Vector2f vel = fromAngle(a, 60f + RNG.nextFloat() * 180f);
            engine.addNebulaSmokeParticle(
                    new Vector2f(at),
                    vel,
                    60f + RNG.nextFloat() * 60f, // start size
                    2.2f,                        // end size multiplier
                    0.2f,                        // ramp-up fraction
                    0.35f,                       // full-brightness fraction
                    2.5f + RNG.nextFloat() * 1.5f,
                    SMOKE_COLOR);
        }
    }

    /**
     * Trailing smoke only. The leading edge is the MagicRender sprite spawned
     * at detonation -- a circle of particles reads as confetti, not a front.
     */
    private static void doRingVisuals(CombatEngineAPI engine, Vector2f center, float radius,
                                      float atten, float progress) {
        float fade = 1f - progress;
        if (fade <= 0f) {
            return;
        }

        int count = (int) ((5 + 5 * atten) * (radius / INITIAL_BLAST_RADIUS));
        for (int i = 0; i < count; i++) {
            float a = RNG.nextFloat() * 360f;
            Vector2f p = Vector2f.add(center, fromAngle(a, radius - RING_VISUAL_WIDTH), null);
            engine.addNebulaSmokeParticle(p, fromAngle(a, 40f), 50f, 1.8f, 0.25f, 0.4f, 2f,
                    new Color(CORE_COLOR.getRed(), CORE_COLOR.getGreen(),
                            CORE_COLOR.getBlue(), (int) (100f * fade)));
        }
    }

    // --------------------------------------------------------------------
    // math helpers
    // --------------------------------------------------------------------
    public static Vector2f fromAngle(float degrees, float length) {
        double r = Math.toRadians(degrees);
        return new Vector2f((float) Math.cos(r) * length, (float) Math.sin(r) * length);
    }

    public static float angleTo(Vector2f from, Vector2f to) {
        return (float) Math.toDegrees(Math.atan2(to.y - from.y, to.x - from.x));
    }

    public static float angleDiff(float a, float b) {
        float d = (b - a) % 360f;
        if (d > 180f) {
            d -= 360f;
        }
        if (d < -180f) {
            d += 360f;
        }
        return d;
    }

    public static float distance(Vector2f a, Vector2f b) {
        float dx = b.x - a.x, dy = b.y - a.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /** Projected danger radius at a given charge. Used for AI panic flags. */
    public static float getDangerRadius(float atten) {
        float a = clamp(atten, MIN_ATTENUATE, 1f);
        return (INITIAL_BLAST_RADIUS * a)
                + (EXPANSION_RATE * (float) Math.sqrt(a) * EXPANSION_TIME * a);
    }
}