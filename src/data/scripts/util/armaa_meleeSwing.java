package data.scripts.util;

import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.util.IntervalUtil;

public class armaa_meleeSwing {

    /** Ramp tick. Every existing weapon used 0.012s; kept as the default. */
    public static final float DEFAULT_TICK = 0.012f;
    public static final float DEFAULT_RAMP_PER_TICK = 0.5f;
    public static final float DEFAULT_PEAK = 9f;
    public static final float DEFAULT_TAPER_PER_TICK = 0.25f;
    /**
     * A swing that has not tapered out by this long is force-ended. Lifted from
     * guarDualBlade, where it exists because  stalled sweep could otherwise
     * wedge the state machine with the blade live.
     */
    public static final float DEFAULT_MAX_DURATION = 1.5f;
    /**
     * Level below which the taper stops counting as a damage window. Weapons
     * that want damage for the whole taper pass 0.
     */
    public static final float DEFAULT_DAMAGE_FLOOR = 5f;

    private final float rampPerTick;
    private final float peak;
    private final float taperPerTick;
    private final float maxDuration;
    private final float damageFloor;

    private final IntervalUtil tick;

    private boolean windingBack = false;
    private boolean swinging = false;
    private boolean pastPeak = false;
    private boolean onCooldown = false;
    private boolean endedThisFrame = false;

    private float level = 0f;
    private float elapsed = 0f;
    private float prevChargeLevel = 0f;

    public armaa_meleeSwing() {
        this(DEFAULT_TICK, DEFAULT_RAMP_PER_TICK, DEFAULT_PEAK,
                DEFAULT_TAPER_PER_TICK, DEFAULT_MAX_DURATION, DEFAULT_DAMAGE_FLOOR);
    }

    public armaa_meleeSwing(float tickSeconds, float rampPerTick, float peak,
            float taperPerTick, float maxDuration, float damageFloor) {
        this.tick = new IntervalUtil(tickSeconds, tickSeconds);
        this.rampPerTick = rampPerTick;
        this.peak = peak;
        this.taperPerTick = taperPerTick;
        this.maxDuration = maxDuration;
        this.damageFloor = damageFloor;
    }

    // ------------------------------------------------------------------
    // queries
    // ------------------------------------------------------------------

    /** Charge is rising and the blade should be moving to its start angle. */
    public boolean isWindingBack() {
        return windingBack;
    }

    /** The blade is sweeping. Step it by getLevel() degrees per tick. */
    public boolean isSwinging() {
        return swinging;
    }

    /** How fast the blade is moving right now, in the ramp's own units. */
    public float getLevel() {
        return level;
    }

    /** Past the peak and tapering out. */
    public boolean isPastPeak() {
        return pastPeak;
    }

    /**
     * Is the blade live? Active from the first frame of sweep until the
     * post-peak taper falls below the damage floor - anchored to the swing
     * itself rather than to isFiring(), which stays true through the whole
     * charge-up and would make the blade live during the windup.
     */
    public boolean isDamaging() {
        return swinging && level > 0f && (!pastPeak || level >= damageFloor);
    }

    /**
     * True on the single frame a swing finished. Reading it clears it, so this
     * is where a weapon clears its per-swing hit set.
     */
    public boolean consumeEnded() {
        boolean was = endedThisFrame;
        endedThisFrame = false;
        return was;
    }

    /**
     * The blade has swung and is waiting for the weapon to recycle. Cleared
     * once cooldown is done and the trigger is released.
     */
    public boolean isOnCooldown() {
        return onCooldown;
    }

    // ------------------------------------------------------------------
    // drive
    // ------------------------------------------------------------------

    /**
     * Advance the ramp. Call once per frame, before reading anything.
     *
     * The weapon decides WHEN a swing may begin by calling beginSwing(); this
     * only owns what happens once it has.
     */
    public void advance(float amount, WeaponAPI weapon) {

        endedThisFrame = false;

        float charge = weapon.getChargeLevel();

        if (weapon.getCooldownRemaining() <= 0f && !weapon.isFiring()) {
            onCooldown = false;
        }

        // Rising charge with no swing underway means the blade is winding back.
        windingBack = !swinging && !onCooldown && charge > 0f;

        if (swinging) {
            elapsed += amount;
            tick.advance(amount);

            if (tick.intervalElapsed()) {
                if (pastPeak) {
                    level = Math.max(0f, level - taperPerTick);
                } else {
                    level = Math.min(peak, level + rampPerTick);
                }
            }

            if (level >= peak) {
                pastPeak = true;
            }

            // Ended: tapered out, the charge collapsed under us, or the safety
            // valve tripped on a sweep that never completed.
            boolean tapered = pastPeak && level <= 0f;
            boolean chargeGone = charge <= 0f;
            boolean stalled = maxDuration > 0f && elapsed >= maxDuration;

            if (tapered || chargeGone || stalled) {
                endSwing();
            }
        } else {
            level = 0f;
        }

        prevChargeLevel = charge;
    }

    /**
     * True on the frame a fresh charge cycle starts. Weapons use this to clear
     * per-cycle latches; without it a stale latch from a cycle that never
     * produced a swing starts the next one in the wrong phase.
     */
    public boolean isNewChargeCycle(WeaponAPI weapon) {
        return prevChargeLevel <= 0f && weapon.getChargeLevel() > 0f && !swinging;
    }

    /** Called by the weapon once its own conditions for starting are met. */
    public void beginSwing() {
        if (swinging) {
            return;
        }
        swinging = true;
        windingBack = false;
        pastPeak = false;
        level = 0f;
        elapsed = 0f;
    }

    /** Force a swing to end early. */
    public void endSwing() {
        if (!swinging) {
            return;
        }
        swinging = false;
        pastPeak = false;
        level = 0f;
        elapsed = 0f;
        onCooldown = true;
        endedThisFrame = true;
    }

    /** Full reset, for a disabled or destroyed weapon. */
    public void reset() {
        swinging = false;
        windingBack = false;
        pastPeak = false;
        onCooldown = false;
        endedThisFrame = false;
        level = 0f;
        elapsed = 0f;
    }
}
