package data.scripts.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier;
import com.fs.starfarer.api.input.InputEventAPI;
import java.awt.Color;

import org.lwjgl.util.vector.Vector2f;

import java.util.List;
/**
 * Post-eject survival window for bakraid core units.
 *
 * Fired once, immediately after the core is swapped in for the destroyed shell:
 *
 *     Global.getCombatEngine().addPlugin(new armaa_CoreEjectEffect(core));
 *
 * Timeline (all durations in REAL seconds, not dilated seconds):
 *
 *   PHASE 1 - GLOBAL SLOW
 *       Engine-wide time mult drops. Everything slows equally, so the player
 *       gains information but no mechanical advantage. Player ships only;
 *       AI ejects skip straight to phase 2 so a six-bakraid engagement does
 *       not turn into a strobing slideshow.
 *
 *   CROSSFADE
 *       Engine mult ramps back to 1.0 while the ship's own mult ramps up.
 *       Overlapping the two sells it as one continuous beat instead of two
 *       effects fired back to back.
 *
 *   PHASE 2 - RELATIVE DILATION
 *       Ship mult decays to 1.0. This is the actual escape window.
 *
 * Invulnerability ends partway through phase 2 by design: the last stretch of
 * fast-but-mortal is what keeps the mechanic honest. Weapons stay blocked for
 * the whole invulnerable stretch so the window can never be used offensively.
 */
public class armaa_CoreEjectEffect extends BaseEveryFrameCombatPlugin {

    // ---------------------------------------------------------------- tuning

    /** Engine-wide time mult during phase 1. Lower = heavier slow. */
    private static final float GLOBAL_SLOW_MULT = 0.10f;

    /** Phase 1 length, real seconds. At 0.30 mult this is ~2.3s of dilated time. */
    private static final float PHASE_1_DUR = 0.70f;

    /** Handoff from engine slow to ship dilation, real seconds. */
    private static final float CROSSFADE_DUR = 0.25f;

    /** Peak ship-relative time mult at the start of phase 2. */
    private static final float RELATIVE_PEAK_MULT = 1.25f;

    /** Phase 2 length, real seconds. Ship mult eases from peak back to 1.0. */
    private static final float PHASE_2_DUR = 2.00f;

    /**
     * How long after the crossfade ends the core stays invulnerable, real
     * seconds. Must be shorter than PHASE_2_DUR or the window becomes a
     * guarantee rather than a chance.
     */
    private static final float INVULN_TAIL = 0.75f;

    /** Also block the ship system, so the window cannot be chained into another. */
    private static final boolean BLOCK_SHIP_SYSTEM = true;

    // ------------------------------------------------------------- constants

    private static final String STAT_ID = "armaa_coreEject";
    private static final String ENGINE_SLOW_ID = "armaa_coreEjectGlobal";

    private static final float PHASE_2_START = PHASE_1_DUR + CROSSFADE_DUR;
    private static final float INVULN_DUR = PHASE_2_START + INVULN_TAIL;
    private static final float TOTAL_DUR = PHASE_2_START + PHASE_2_DUR;

    // ------------------------------------------------------- global arbiter
    // A stuck engine-wide time mult is the worst bug this design can produce:
    // it persists for the rest of the battle and is miserable to trace. One
    // shared modifier key, and only the current owner is allowed to clear it.
    // Engine identity is tracked too, because statics survive across battles.

    private static armaa_CoreEjectEffect slowOwner = null;
    private static CombatEngineAPI slowOwnerEngine = null;

    private static void claimGlobalSlow(armaa_CoreEjectEffect who, CombatEngineAPI engine) {
        slowOwner = who;
        slowOwnerEngine = engine;
    }

    private static boolean ownsGlobalSlow(armaa_CoreEjectEffect who, CombatEngineAPI engine) {
        return slowOwner == who && slowOwnerEngine == engine;
    }

    private static void releaseGlobalSlow(armaa_CoreEjectEffect who, CombatEngineAPI engine) {
        if (engine != null) {
            engine.getTimeMult().unmodify(ENGINE_SLOW_ID);
        }
        if (slowOwner == who) {
            slowOwner = null;
            slowOwnerEngine = null;
        }
    }

    // ---------------------------------------------------------------- state

    private final ShipAPI core;
    private final boolean usesGlobalSlow;

    private float elapsed = 0f;
    private boolean invulnActive = false;
    private boolean finished = false;

    private final InvulnListener invulnListener = new InvulnListener();

    public armaa_CoreEjectEffect(ShipAPI core) {
        this.core = core;
        this.usesGlobalSlow = core != null && core == Global.getCombatEngine().getPlayerShip();
    }

    // --------------------------------------------------------------- update

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;
        if (finished) return;

        // Watchdog. Unconditionally tear down if the owning ship is gone, so a
        // core that dies mid-window can never leave the engine mult hanging.
        if (core == null || !core.isAlive() || !engine.isEntityInPlay(core)) {
            cleanup(engine);
            return;
        }

        // advance()'s amount is already scaled by the engine time mult, and so
        // is getElapsedInLastFrame(). Divide it back out so every duration
        // above means what it says.
        float engineMult = engine.getTimeMult().getModifiedValue();
        if (engineMult <= 0.0001f) engineMult = 1f;
        float real = amount / engineMult;

        elapsed += real;

        if (elapsed >= TOTAL_DUR) {
            cleanup(engine);
            return;
        }

        applyTimeMults(engine);
        applyInvuln(engine);
        applyCommandBlocks();
    }

    private void applyTimeMults(CombatEngineAPI engine) {
        MutableShipStatsAPI stats = core.getMutableStats();
            ShipAPI ship = (ShipAPI)stats.getEntity();
            float effectLevel;
        if (elapsed < PHASE_1_DUR) {
            // Phase 1: everything slows equally. Ship mult stays at 1.0, so the
            // core is slowed right along with the rest of the field. Perceptual
            // only, no mechanical edge.
            effectLevel = (elapsed/PHASE_1_DUR);
            if (usesGlobalSlow) {
                claimGlobalSlow(this, engine);
                engine.getTimeMult().modifyMult(ENGINE_SLOW_ID, GLOBAL_SLOW_MULT);
            }
            float sMult = 1f + (RELATIVE_PEAK_MULT - 1f) * (1f-effectLevel);
            stats.getTimeMult().modifyMult(STAT_ID, sMult);
            
            ship.setJitterUnder(ship, Color.red, 1f-(effectLevel), (int)(5*(1f-effectLevel)), 10f*(1f-effectLevel));

        } else if (elapsed < PHASE_2_START) {
            // Crossfade: engine mult back toward 1.0, ship mult up toward peak.
            float t = (elapsed - PHASE_1_DUR) / CROSSFADE_DUR;
            t = clamp01(t);
            effectLevel = (elapsed/PHASE_1_DUR);
            if (usesGlobalSlow && ownsGlobalSlow(this, engine)) {
                float eMult = GLOBAL_SLOW_MULT + (1f - GLOBAL_SLOW_MULT) * t;
                engine.getTimeMult().modifyMult(ENGINE_SLOW_ID, eMult);
            }

            float sMult = 1f + (RELATIVE_PEAK_MULT - 1f) * t;
            stats.getTimeMult().modifyMult(STAT_ID, sMult);
            ship.setJitterUnder(ship, Color.red, 1f-(effectLevel), (int)(5*(1f-effectLevel)), 10f*(1f-effectLevel));

        } else {
            // Phase 2: engine back to normal, ship dilation bleeding off.
            if (usesGlobalSlow && ownsGlobalSlow(this, engine)) {
                releaseGlobalSlow(this, engine);
            }

            float t = (elapsed - PHASE_2_START) / PHASE_2_DUR;
            t = clamp01(t);
            effectLevel = ((elapsed - PHASE_2_START)/PHASE_2_DUR);
            // Ease-out: holds the speed a moment, then drops off. Reads as
            // momentum bleeding away rather than a switch flipping.
            float eased = 1f - (1f - t) * (1f - t);
            float sMult = RELATIVE_PEAK_MULT + (1f - RELATIVE_PEAK_MULT) * eased;
            stats.getTimeMult().modifyMult(STAT_ID, sMult);
            ship.setJitterUnder(ship, Color.red, 1f-(effectLevel), (int)(5*(1f-effectLevel)), 10f*(1f-effectLevel));
        }
    }

    private void applyInvuln(CombatEngineAPI engine) {
        boolean shouldBeInvuln = elapsed < INVULN_DUR;
        if (shouldBeInvuln == invulnActive) return;

        MutableShipStatsAPI stats = core.getMutableStats();
        if (shouldBeInvuln) {
            core.addListener(invulnListener);
            // Zeroed damage does not stop flameout. Without this the window
            // buys you a fast, invulnerable, non-responsive frigate.
            stats.getEmpDamageTakenMult().modifyMult(STAT_ID, 0f);
        } else {
            core.removeListener(invulnListener);
            stats.getEmpDamageTakenMult().unmodify(STAT_ID);
        }
        invulnActive = shouldBeInvuln;
    }

    private void applyCommandBlocks() {
        // Invulnerable + dilated + armed is the one combination that turns a
        // save into a weapon. Blocked for the whole invulnerable stretch.
        if (!invulnActive) return;
        core.blockCommandForOneFrame(ShipCommand.FIRE);
        if (BLOCK_SHIP_SYSTEM) {
            core.blockCommandForOneFrame(ShipCommand.USE_SYSTEM);
        }
    }

    // -------------------------------------------------------------- cleanup

    private void cleanup(CombatEngineAPI engine) {
        if (finished) return;
        finished = true;

        if (ownsGlobalSlow(this, engine)) {
            releaseGlobalSlow(this, engine);
        } else if (engine != null && slowOwner == null) {
            // Belt and suspenders: if nobody owns it, make sure it is gone.
            engine.getTimeMult().unmodify(ENGINE_SLOW_ID);
        }

        if (core != null) {
            MutableShipStatsAPI stats = core.getMutableStats();
            stats.getTimeMult().unmodify(STAT_ID);
            stats.getEmpDamageTakenMult().unmodify(STAT_ID);
            if (invulnActive) {
                core.removeListener(invulnListener);
                invulnActive = false;
            }
        }

        if (engine != null) {
            engine.removePlugin(this);
        }
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    // ------------------------------------------------------------- listener

    private static class InvulnListener implements DamageTakenModifier {
        @Override
        public String modifyDamageTaken(Object param, CombatEntityAPI target, DamageAPI damage,
                                        Vector2f point, boolean shieldHit) {
            damage.getModifier().modifyMult(STAT_ID, 0f);
            return STAT_ID;
        }
    }
}