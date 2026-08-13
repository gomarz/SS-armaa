package data.scripts.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.GuidedMissileAI;
import com.fs.starfarer.api.combat.MissileAIPlugin;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.weapons.basilisk.armaa_BasiliskDetonation;
import data.scripts.weapons.basilisk.armaa_BasiliskTracker;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;

/**
 * CRUISE -> HOLD -> CHARGE -> DETONATE
 *
 * Charge is continuous, not a gate. Yield scales with how long the warhead
 * survived charging, so point defense reduces the blast rather than negating
 * it. If it dies before firing, BasiliskTracker detonates it at its last known
 * position with its last charge level.
 */
public class armaa_BasiliskMissileAI implements MissileAIPlugin, GuidedMissileAI {

    private static final float PING_MAX = 1.5f;  // starting interval
    private static final float PING_MIN = 0.1f;
    /* Distance from the target at which it halts and starts charging.
     */
    public static final float STANDOFF_RANGE = 260f;
    /**
     * Time to full yield. This is the real balance lever, not damage.
     */
    public static final float CHARGE_TIME = 4f;
    /**
     * Failsafe: fire rather than fly forever.
     */
    public static final float MAX_LIFETIME = 10f;
    /**
     * Degrees per second. Deliberately poor -- it is a big dumb object.
     */
    public static final float TURN_RATE = 28f;
    /**
     * Velocity damping per second once holding.
     */
    public static final float BRAKE_RATE = 2.0f;
    public static final float RETARGET_TIME = 0.5f;
    /**
     * How often to refresh enemy panic flags, seconds.
     */
    public static final float PANIC_INTERVAL = 0.25f;
    private IntervalUtil blink = new IntervalUtil(1f, 1f);

    private enum State {
        CRUISE, HOLD, CHARGE, DONE
    }

    private final MissileAPI missile;
    private final ShipAPI source;

    private CombatEntityAPI target;
    private State state = State.CRUISE;
    private float chargeTimer = 0f;
    private float lifetime = 0f;
    private float retargetTimer = 0f;
    private float panicTimer = 0f;
    private boolean detonated = false;
    private boolean registered = false;

    public armaa_BasiliskMissileAI(MissileAPI missile, ShipAPI source) {
        this.missile = missile;
        this.source = source;
    }

    /**
     * 0..1 charge level. Read by the tracker when the missile is shot down.
     */
    public float getCharge() {
        return Math.min(1f, chargeTimer / CHARGE_TIME);
    }

    public boolean hasDetonated() {
        return detonated;
    }

    @Override
    public void advance(float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused() || state == State.DONE) {
            return;
        }

        if (!registered) {
            registered = true;
            armaa_BasiliskTracker t = armaa_BasiliskTracker.get(engine);
            if (t != null) {
                t.register(missile, this, source);
            }
        }

        lifetime += amount;
        if (lifetime > MAX_LIFETIME) {
            fire(engine);
            return;
        }

        retargetTimer -= amount;
        if (target == null || !isValid(target)) {
            target = findTarget(engine);
        } else if (retargetTimer <= 0f) {
            retargetTimer = RETARGET_TIME;
        }

        if (target == null) {
            missile.giveCommand(ShipCommand.ACCELERATE);
            return;
        }

        float dist = armaa_BasiliskDetonation.distance(missile.getLocation(), target.getLocation())
                - target.getCollisionRadius();

        switch (state) {
            case CRUISE:
                turnToward(target.getLocation(), amount);
                missile.giveCommand(ShipCommand.ACCELERATE);
                if (dist < 1500) {
                    chargeTimer += amount;
                    doChargeVisual(engine, getCharge());
                    panicTimer -= amount;
                    if (panicTimer <= 0f) {
                        panicTimer = PANIC_INTERVAL;
                        scareEnemies(engine);
                    }
                }

                if (chargeTimer >= CHARGE_TIME) {
                    fire(engine);
                }

                if (dist <= STANDOFF_RANGE) {
                    state = State.HOLD;
                }
                break;

            case HOLD:
                turnToward(target.getLocation(), amount);
                brake(amount);
                panicTimer -= amount;
                if (panicTimer <= 0f) {
                    panicTimer = PANIC_INTERVAL;
                    scareEnemies(engine);
                }
                chargeTimer += amount;
                if (dist > STANDOFF_RANGE * 1.6f) {
                    state = State.CRUISE;
                }
                doChargeVisual(engine, getCharge());

                if (chargeTimer >= CHARGE_TIME) {
                    fire(engine);
                }
                break;

            case CHARGE:
                turnToward(target.getLocation(), amount);
                brake(amount);

                panicTimer -= amount;
                if (panicTimer <= 0f) {
                    panicTimer = PANIC_INTERVAL;
                    scareEnemies(engine);
                }
                chargeTimer += amount;
                doChargeVisual(engine, getCharge());
                if (chargeTimer >= CHARGE_TIME) {
                    fire(engine);
                }
                break;
            default:
                break;
        }
    }

    // --------------------------------------------------------------------
    private void fire(CombatEngineAPI engine) {
        if (detonated) {
            return;
        }
        detonated = true;
        state = State.DONE;

        armaa_BasiliskDetonation.detonate(engine,
                new Vector2f(missile.getLocation()),
                source,
                getCharge());

        engine.removeEntity(missile);
    }

    /**
     * Titan-style panic. Enemies inside the projected blast are told to run.
     * This is most of what makes the weapon feel dangerous -- the fleet visibly
     * scatters instead of standing there.
     */
    private void scareEnemies(CombatEngineAPI engine) {
        float danger = armaa_BasiliskDetonation.getDangerRadius(1f);

        for (ShipAPI ship : engine.getShips()) {
            if (!ship.isAlive() || ship.isHulk() || ship.isShuttlePod()) {
                continue;
            }
            if (ship.isDrone() || ship.isFighter()) {
                continue;
            }
            if (ship.isStation() || ship.isStationModule()) {
                continue;
            }
            if (source != null && ship.getOwner() == source.getOwner()) {
                continue;
            }
            if (ship.getAIFlags() == null) {
                continue;
            }

            float d = armaa_BasiliskDetonation.distance(missile.getLocation(), ship.getLocation());
            if (d > danger) {
                continue;
            }

            ship.getAIFlags().setFlag(AIFlags.RUN_QUICKLY, 1f);
            //ship.getAIFlags().setFlag(AIFlags.MANEUVER_TARGET, 1f, missile.getLocation());
            ship.getAIFlags().setFlag(AIFlags.HAS_INCOMING_DAMAGE, 1f);
            ship.getAIFlags().setFlag(AIFlags.IN_CRITICAL_DPS_DANGER, 1f);
            ship.getAIFlags().setFlag(AIFlags.DO_NOT_PURSUE, 1f);
            ship.getAIFlags().setFlag(AIFlags.BACK_OFF, 1f);
            ship.getAIFlags().unsetFlag(AIFlags.HARASS_MOVE_IN);
            ship.getAIFlags().unsetFlag(AIFlags.MAINTAINING_STRIKE_RANGE);
            ship.getAIFlags().unsetFlag(AIFlags.PURSUING);
            ship.getAIFlags().unsetFlag(AIFlags.DO_NOT_BACK_OFF);
            ship.getAIFlags().unsetFlag(AIFlags.SAFE_FROM_DANGER_TIME);
        }
    }

    private void turnToward(Vector2f point, float amount) {
        float desired = armaa_BasiliskDetonation.angleTo(missile.getLocation(), point);
        float diff = armaa_BasiliskDetonation.angleDiff(missile.getFacing(), desired);
        float step = TURN_RATE * amount;
        if (Math.abs(diff) <= step) {
            missile.setFacing(desired);
        } else {
            missile.setFacing(missile.getFacing() + Math.signum(diff) * step);
        }
    }

    private void brake(float amount) {
        Vector2f v = missile.getVelocity();
        float f = Math.max(0f, 1f - BRAKE_RATE * amount);
        v.set(v.x * f, v.y * f);
    }

    private float speed() {
        Vector2f v = missile.getVelocity();
        return (float) Math.sqrt(v.x * v.x + v.y * v.y);
    }

    /**
     * Charge must be legible -- the enemy needs to see the yield building.
     */
    private void doChargeVisual(CombatEngineAPI engine, float t) {
        int n = (int) chargeTimer;
        Color color = new Color(0, 0, 0);
        switch (n) {
            case 0:
                color = Color.blue;
                break;
            case 1:
                color = Color.green;
                break;
            case 2: // code to be executed if n = 2;
                color = Color.yellow;
                break;
            case 3: // code to be executed if n = 2;
                color = Color.orange;
                break;
            case 4: // code to be executed if n = 2;
                color = Color.red;
                break;
            default: // code to be executed if n doesn't match any cases
        }
        blink.advance(engine.getElapsedInLastFrame());
        if (blink.intervalElapsed()) {
            float next = Math.max(PING_MIN, blink.getMinInterval() * 0.66f);

            float pitch = 0.5f + t * 0.3f;   // 0.9 -> 1.5
            float volume = 0.5f + t * 0.3f;   // 0.6 -> 1.2

            Global.getSoundPlayer().playSound("ui_survey_found_1", pitch, volume,
                    missile.getLocation(), missile.getVelocity());

            blink.setInterval(next, next);
            engine.addHitParticle(missile.getLocation(), missile.getVelocity(), 200 * (1 + pitch), 0.4f, 0.1f, color);

        }

    }

    // --------------------------------------------------------------------
    private boolean isValid(CombatEntityAPI e) {
        if (e == null) {
            return false;
        }
        if (e instanceof ShipAPI) {
            ShipAPI s = (ShipAPI) e;
            if (s.isHulk() || !s.isAlive()) {
                return false;
            }
        }
        return Global.getCombatEngine().isEntityInPlay(e);
    }

    private CombatEntityAPI findTarget(CombatEngineAPI engine) {
        if (source != null && source.getShipTarget() != null && isValid(source.getShipTarget())) {
            return source.getShipTarget();
        }
        CombatEntityAPI best = null;
        float bestDist = Float.MAX_VALUE;
        for (ShipAPI ship : engine.getShips()) {
            if (ship.isHulk() || ship.isShuttlePod() || ship.isFighter()) {
                continue;
            }
            if (ship.getOwner() == missile.getOwner()) {
                continue;
            }
            float d = armaa_BasiliskDetonation.distance(missile.getLocation(), ship.getLocation());
            if (d < bestDist) {
                bestDist = d;
                best = ship;
            }
        }
        return best;
    }

    @Override
    public CombatEntityAPI getTarget() {
        return target;
    }

    @Override
    public void setTarget(CombatEntityAPI target) {
        this.target = target;
    }
}
