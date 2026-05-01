package data.scripts.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;

public class armaa_rajanyaBossPlugin extends BaseEveryFrameCombatPlugin {

    // -----------------------------------------------------------------------
    // Config
    // -----------------------------------------------------------------------
    public static class Config {

        /**
         * Hull fraction (0-1) at which the first retreat triggers.
         */
        public float phase1Threshold = 0.75f;
        /**
         * Number of barrages fired during the first bg phase.
         */
        public int phase1Attacks = 3;

        /**
         * Hull fraction (0-1) at which the second retreat triggers.
         */
        public float phase2Threshold = 0.40f;
        /**
         * Number of barrages fired during the second background phase.
         */
        public int phase2Attacks = 6;

        /**
         * Seconds the ghost takes to shrink/grow in battlespace.
         */
        public float shrinkDuration = 5.0f;
        /**
         * Seconds the ghost takes to drift to/from screen center in
         * screenspace.
         */
        public float driftDuration = 5.0f;

        public float transitionDuration = 3f;
        /**
         * Safety cap: if the background phase runs longer than this (seconds),
         * force a return regardless of remaining attack count.
         */
        public float maxForegroundTime = 50f;

        /**
         * Tint applied to the ghost sprite. Color.WHITE = no tint.
         */
        public Color ghostTint = Color.WHITE;

        /**
         * Where the boss is teleported when it retreats off-screen. Should be
         * well outside the battle area bounds.
         */
        public Vector2f offscreenPosition = new Vector2f(100000f, 100000f);

        /**
         * Where the boss re-enters the map when restoring. Set this before
         * restore() is called.
         */
        public Vector2f reEntryPosition = new Vector2f(0f, 0f);
    }

    // -----------------------------------------------------------------------
    // Phase state machine
    // -----------------------------------------------------------------------
    private enum Phase {
        WAITING_FOR_BOSS,
        COMBAT,
        TRANSITIONING_OUT,
        FOREGROUND,
        TRANSITIONING_IN,
        DONE
    }

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------
    private final ShipAPI boss;
    private final Config config;
    private final armaa_BossRetreatTransitionPlugin transition;

    private Phase phase = Phase.WAITING_FOR_BOSS;

    private int retreatCount = 0;
    private int attacksFired = 0;
    private float backgroundTimer = 0f;

    private final IntervalUtil barrageCooldown = new IntervalUtil(2.0f, 3.5f);
    private armaa_RajanyaLaserAttack attack;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------
    public armaa_rajanyaBossPlugin(ShipAPI boss, Config config) {
        this.boss = boss;
        this.config = config;
        this.transition = new armaa_BossRetreatTransitionPlugin(
                boss,
                config.transitionDuration,
                config.ghostTint
        );
    }

    // -----------------------------------------------------------------------
    // Plugin init
    // -----------------------------------------------------------------------
    @Override
    public void init(CombatEngineAPI engine) {
        engine.addPlugin(transition);
    }

    // -----------------------------------------------------------------------
    // Main advance loop
    // -----------------------------------------------------------------------
    @Override
    public void advance(float amount, java.util.List events) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine.isPaused()) {
            return;
        }
        if (boss == null || !boss.isAlive()) {
            return;
        }

        switch (phase) {
            case WAITING_FOR_BOSS:
                advanceWaitingForBoss();
                break;
            case COMBAT:
                advanceCombat();
                break;
            case TRANSITIONING_OUT:
                break; // transition plugin drives this
            case FOREGROUND:
                advanceForeground(amount);
                break;
            case TRANSITIONING_IN:
                break; // transition plugin drives this
            case DONE:
                break;
        }
    }

    // -----------------------------------------------------------------------
    // Phase handlers
    // -----------------------------------------------------------------------
    private void advanceWaitingForBoss() {
        phase = Phase.COMBAT;
    }

    private void advanceCombat() {
        ShipAPI player = Global.getCombatEngine().getPlayerShip();
        boolean playerPresent = player != null && player.isAlive() && !player.isHulk();

        float hull = boss.getHullLevel();
        if (retreatCount == 0 && hull <= config.phase1Threshold) {
            if (playerPresent) {
                triggerRetreat();
                boss.getFluxTracker().ventFlux();
            }
        } else if (retreatCount == 1 && hull <= config.phase2Threshold) {
            if (playerPresent) {
                triggerRetreat();
                boss.getFluxTracker().ventFlux();
            }
        }
    }

    private void advanceForeground(float amount) {
        backgroundTimer += amount;

        int attackQuota = retreatCount == 1 ? config.phase1Attacks : config.phase2Attacks;
        boolean quotaMet = attacksFired >= attackQuota;
        boolean timedOut = backgroundTimer >= config.maxForegroundTime;
        ShipAPI player = Global.getCombatEngine().getPlayerShip();
        boolean playerPresent = player != null && player.isAlive() && !player.isHulk();
        if (!quotaMet && !timedOut) {
            barrageCooldown.advance(amount);
            if (attacksFired == 0) {
                fireForegroundBarrage();
                attacksFired++;
            } else if (barrageCooldown.intervalElapsed()) {
                fireForegroundBarrage();
                attacksFired++;
            }
        } else if (attack == null || attack.isExpired() || !playerPresent) {
            triggerRestore();
        }
    }

    // -----------------------------------------------------------------------
    // Retreat / restore triggers
    // -----------------------------------------------------------------------
    private void triggerRetreat() {
        phase = Phase.TRANSITIONING_OUT;
        retreatCount++;
        attacksFired = 0;
        backgroundTimer = 0f;

        Vector2f retreatPos = (Vector2f) Global.getCombatEngine().getCustomData().get("armaa_bossRetreatPos");
        if (retreatPos == null) {
            Global.getCombatEngine().getCustomData().put("armaa_bossRetreatPos", new Vector2f(boss.getLocation()));
            retreatPos = new Vector2f(boss.getLocation());
        }
        boss.getLocation().set(config.offscreenPosition);
        //armaa_TransitionExplosion.spawn(retreatPos);
        transition.retreat(retreatPos, () -> {
            boss.getLocation().set(config.offscreenPosition);
            phase = Phase.FOREGROUND;
            onForegroundPhaseBegin();
        });
    }

    private void triggerRestore() {
        phase = Phase.TRANSITIONING_IN;

        Vector2f reEntryPos = getReEntryPosition();

        transition.restore(reEntryPos, () -> {
            boss.getLocation().set(reEntryPos);
            phase = retreatCount >= 2 ? Phase.DONE : Phase.COMBAT;
            Global.getCombatEngine().getCustomData().remove("armaa_bossRetreatPos");
            onReturnToCombat();
        });
    }

    // -----------------------------------------------------------------------
    // Re-entry position — falls back gracefully if player is absent
    // -----------------------------------------------------------------------
    /**
     * Returns the best world position for the boss to re-enter at.
     *
     * Priority: 1. Player ship is alive and piloting — use viewport center 2.
     * No player — nearest living enemy ship to map center 3. No enemies at all
     * — map center (0,0)
     */
    private Vector2f getReEntryPosition() {
        CombatEngineAPI engine = Global.getCombatEngine();

        // 1. Player is alive and piloting
        ShipAPI player = engine.getPlayerShip();
        if (player != null && player.isAlive() && !player.isHulk()) {
            return new Vector2f(engine.getViewport().getCenter().x, engine.getViewport().getCenter().y - 1000);
        }

        // 2. No player find nearest living enemy to map center
        Vector2f mapCenter = new Vector2f(0f, 0f);
        ShipAPI nearest = null;
        float nearestDist = Float.MAX_VALUE;
        for (ShipAPI ship : engine.getShips()) {
            if (ship.getOwner() != boss.getOwner() && ship.isAlive() && !ship.isHulk()
                    && ship.getHullSize() != ShipAPI.HullSize.FIGHTER) {
                float dist = MathUtils.getDistance(ship.getLocation(), mapCenter);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = ship;
                }
            }
        }
        if (nearest != null) {
            return new Vector2f(nearest.getLocation());
        }

        // 3. Nothing — map center
        return mapCenter;
    }

    // -----------------------------------------------------------------------
    // Stubs
    // -----------------------------------------------------------------------
    private void onForegroundPhaseBegin() {
        // TODO: play transition sound

    }

    private void fireForegroundBarrage() {
        attack = new armaa_RajanyaLaserAttack(1, 10 * Math.max(1, attacksFired), new Vector2f(), boss, 0f);
        int moduleIndex = 1;
        Global.getCombatEngine().addLayeredRenderingPlugin(attack);
        for (ShipAPI module : boss.getChildModulesCopy()) {
            if (module.isHulk() || !module.isAlive()) {
                continue;
            } else {
                for (WeaponAPI wep : module.getAllWeapons()) {
                    Vector2f offset = wep.getSlot().getLocation();
                    float delay = moduleIndex * 0.8f; // 0.4 seconds between each module
                    Global.getCombatEngine().addLayeredRenderingPlugin(new armaa_RajanyaModuleAttack(1, Math.max(1, attacksFired), offset,
                            boss, delay, 1f));
                }
            }

        }
        // attacksFired gives the current wave index (0-based)
    }

    private void onReturnToCombat() {
        // TODO: play return sound
        // enrage / hardmode?
        //if(retreatCount == 2)
        //{
        Global.getCombatEngine().getFleetManager(1).spawnShipOrWing("armaa_exilium_custom", new Vector2f(0, 10000), 270f, 30f);
        //}
    }
}
