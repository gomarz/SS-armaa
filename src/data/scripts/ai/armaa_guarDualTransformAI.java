package data.scripts.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;

import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.MathUtils;

public class armaa_guarDualTransformAI implements AdvanceableListener {

    private final ShipAPI ship;

    private final IntervalUtil transformInterval = new IntervalUtil(2f, 3f);

    // tunables mirrored from the effect script's current values
    private static final float ESCAPE_FLUX = 0.45f;       // mech flee / path-2 re-tank threshold
    private static final float COMMIT_FLUX_MAX = 0.5f;    // max own hard-flux to commit to a kill in mech
    private static final float COMMIT_CONFIRM_TIME = 0.3f;
    private float commitHeldFor = 0f;
    // STAGE-1 debug: latch the last "wants" reason so a momentary trigger stays readable
    // instead of being overwritten next frame. Lets us SEE which trigger is flickering.
    private String lastWantReason = "none";
    private float lastWantHold = 0f;
    private static final float WANT_REASON_HOLD = 1.0f;

    // commit lockout: after a commit fires (fighter->mech to punish), give it a protected
    // window before the ESCAPE trigger can pull it back out otherwise closing in to punish
    // raises flux/danger, escape fires, and commit<->escape strobe the mode. During the lockout
    // the NORMAL escape (>= ESCAPE_FLUX) is suppressed, but a genuine emergency
    // (hard flux >= ESCAPE_EMERGENCY_FLUX) still lets it bail so it can't die mid-commit.
    private float commitLockout = 0f;
    private static final float COMMIT_LOCKOUT_TIME = 2f;
    private static final float ESCAPE_EMERGENCY_FLUX = 0.7f;
    private float blockedHeldFor = 0f;                       // accumulator: how long the blocked-missile condition has held
    private static final float BLOCKED_CONFIRM_TIME = 0.8f;  // must hold this long before the nudge fires

    public armaa_guarDualTransformAI(ShipAPI ship) {
        this.ship = ship;
    }

    @Override
    public void advance(float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || ship == null) {
            return;
        }
        if (!engine.isEntityInPlay(ship) || engine.isPaused() || !ship.isAlive()) {
            return;
        }
        // AI-only; manual control has no getShipAI()
        if (ship.getShipAI() == null) {
            return;
        }
        if (ship.getFluxTracker().isOverloadedOrVenting()) {
            return;
        }
        //boolean transforming = (Boolean)engine.getCustomData().get("armaa_tranforming_" + ship.getId());
        boolean transforming = readBool(engine, "armaa_tranforming_" + ship.getId(), false);
        if (transforming) {
            return;
        }
        transformInterval.advance(amount);
        if (commitLockout > 0f) {
            commitLockout -= amount;
            return;
        }

        // read published transform state (effect script owns/publishes these)
        boolean isRobot = readBool(engine, "armaa_tranformState_" + ship.getId(), true);
        int cannonTier = readInt(engine, "armaa_chargeCannonTier_" + ship.getId(), 0);

        ShipwideAIFlags flags = ship.getAIFlags();
        if (flags == null) {
            return;
        }
        boolean inDanger = flags.hasFlag(AIFlags.HAS_INCOMING_DAMAGE);
        for (BeamAPI beam : engine.getBeams()) {
            if (beam.getDamageTarget() == ship) {
                inDanger = true;
                break;
            }
        }

        boolean wantsTransform = false;
        String reason = "none";

        boolean intervalReady = transformInterval.intervalElapsed();
        float flux = ship.getFluxLevel();

        // 0) PROBABILISTIC: the original chance-based transform block. Higher flux -> higher
        // chance; danger reduces it. Gated by the interval and by flag conditions per mode.
        if (intervalReady) {
            float baseChance = inDanger ? flux : 1f;
            float dangerPenalty = inDanger ? (1f - 0.5f * flux) : 1f;
            float transformChance = baseChance * dangerPenalty;
            if (Math.random() < transformChance) {
                if (!isRobot && Math.random() < 0.80f) {
                    if (flags.hasFlag(AIFlags.TURN_QUICKLY)) {
                        wantsTransform = true;
                        reason = "prob_turn";
                    }
                } else if (isRobot) {
                    if (flags.hasFlag(AIFlags.BACK_OFF) || flags.hasFlag(AIFlags.BACKING_OFF)
                            || flags.hasFlag(AIFlags.HARASS_MOVE_IN) || flags.hasFlag(AIFlags.NEEDS_HELP) || ship.isRetreating()) {
                        wantsTransform = true;
                        reason = "prob_backoff";
                    }
                    if (flags.hasFlag(AIFlags.MANEUVER_TARGET) || flags.hasFlag(AIFlags.MOVEMENT_DEST)
                            || flags.hasFlag(AIFlags.PURSUING) || flags.hasFlag(AIFlags.RUN_QUICKLY)
                            || ship.getHardFluxLevel() >= 0.65f) {
                        wantsTransform = true;
                        reason = "prob_maneuver";
                    }
                }
            }
        }
        float hf = ship.getHardFluxLevel();
        boolean emergency = hf >= ESCAPE_EMERGENCY_FLUX;

        if (!wantsTransform && isRobot && inDanger && (intervalReady || emergency)) {
            boolean chargingCannon = cannonTier > 0;
            boolean lockedOut = commitLockout > 0f && !emergency;
            boolean fluxUnsustainable = hf >= ESCAPE_FLUX;
            boolean wantsOut = flags.hasFlag(AIFlags.NEEDS_HELP)
                    || flags.hasFlag(AIFlags.BACK_OFF)
                    || flags.hasFlag(AIFlags.BACKING_OFF);
            if (emergency || !chargingCannon && !lockedOut && (fluxUnsustainable || wantsOut)) {
                wantsTransform = true;
                reason = "escape";
            }
        }
        // in fighter, in danger, flux recovered -> may return to mech
        if (!wantsTransform && !isRobot && inDanger && intervalReady) {
            if (ship.getHardFluxLevel() < ESCAPE_FLUX && Math.random() > 0.55f) {
                wantsTransform = true;
                reason = "retank";
            }
        }
        // WEAPON-BLOCKED nudge: the AI's actively selected weapon is a missile the current
        // mode suppresses. Good-faith assumption: selected = wants to use.
        boolean blockedMissileSelected = false;
        if (ship.getSelectedGroupAPI() != null) {
            WeaponAPI active = ship.getSelectedGroupAPI().getActiveWeapon();
            if (active != null
                    && active.getType() == WeaponAPI.WeaponType.MISSILE
                    && (!active.usesAmmo() || active.getAmmo() > 0)
                    && active.isForceNoFireOneFrame()) {
                blockedMissileSelected = true;
            }
        }
        if (blockedMissileSelected && !emergency) {
            blockedHeldFor += amount;
        } else {
            blockedHeldFor = 0f;
        }
        if (!wantsTransform && intervalReady && blockedHeldFor >= BLOCKED_CONFIRM_TIME) {
            wantsTransform = true;
            reason = "weapon_blocked";
            blockedHeldFor = 0f;
        }
        // in fighter, target helpless, safe -> flip to mech to punish.
        boolean commitConditionRaw = false;
        if (!isRobot) {
            ShipAPI tgt = ship.getShipTarget();
            boolean targetHelpless = tgt != null && tgt.isAlive()
                    && tgt.getFluxTracker() != null
                    && (tgt.getFluxTracker().isOverloadedOrVenting() || tgt.getFluxTracker().getHardFlux() > 0.85f || tgt.getShield() == null || tgt.isFighter());

            boolean safeToCommit
                    = tgt != null
                    && ship.getHardFluxLevel() < COMMIT_FLUX_MAX
                    && !inDanger
                    && !flags.hasFlag(AIFlags.BACK_OFF)
                    && !flags.hasFlag(AIFlags.BACKING_OFF)
                    && !flags.hasFlag(AIFlags.NEEDS_HELP)
                    && MathUtils.getDistance(ship, tgt) <= 600f;
            commitConditionRaw = targetHelpless && safeToCommit;
        }
        if (commitConditionRaw && !emergency) {
            commitHeldFor += amount;
        } else {
            commitHeldFor = 0f;   // any drop resets the timer — must be CONTINUOUS hold
        }
        if (!wantsTransform && commitConditionRaw && !emergency && commitHeldFor >= COMMIT_CONFIRM_TIME) {
            wantsTransform = true;
            reason = "commit";
            commitLockout = COMMIT_LOCKOUT_TIME;
            commitHeldFor = 0f;   // require a fresh hold for the next commit
        }

        // The effect script
        // consumes this (sets transforming=true, clears the flag) and remains the sole owner of
        // transforming/transformLevel/isRobot. We never write those here. Keep the latched reason
        // key for debug visibility.
        if (wantsTransform) {
            lastWantReason = reason;
            lastWantHold = WANT_REASON_HOLD;
            engine.getCustomData().put("armaa_transformAI_request_" + ship.getId(), Boolean.TRUE);
        } else if (lastWantHold > 0f) {
            lastWantHold -= amount;
        }
        String shownReason = lastWantHold > 0f ? lastWantReason : "none";

        engine.getCustomData().put("armaa_transformAI_wants_" + ship.getId(), wantsTransform);
        engine.getCustomData().put("armaa_transformAI_reason_" + ship.getId(), shownReason);
    }

    private boolean readBool(CombatEngineAPI engine, String key, boolean def) {
        Object v = engine.getCustomData().get(key);
        return (v instanceof Boolean) ? (Boolean) v : def;
    }

    private int readInt(CombatEngineAPI engine, String key, int def) {
        Object v = engine.getCustomData().get(key);
        return (v instanceof Integer) ? (Integer) v : def;
    }
}
