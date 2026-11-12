package data.scripts.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.input.InputEventAPI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.apache.log4j.Logger;

/**
 * Safety net for scripted story battles that suppress or delay the normal end of combat.
 * If no enemy ship is left inside the map for long enough, it logs what the engine still
 * thinks is on the field and ends the battle as a player victory, so the player is not
 * left with no way out of the battle.
 */
public class armaa_stuckBattleWatchdog extends BaseEveryFrameCombatPlugin {

    private static final float CHECK_INTERVAL = 1f;
    private static final float LOG_AFTER = 5f;
    private static final float MAP_MARGIN = 4000f;

    private final Logger log = Global.getLogger(armaa_stuckBattleWatchdog.class);
    private final String tag;
    private final float graceSeconds;
    private final float pendingGraceSeconds;
    private final BooleanSupplier scriptedPhasePending;
    private final boolean ignoreOffMapShips;
    private CombatEngineAPI engine;
    private float sinceCheck = 0f;
    private float emptyFor = 0f;
    private boolean logged = false;

    /**
     * @param tag label used in log lines
     * @param graceSeconds seconds with no enemies left before combat is ended
     * @param pendingGraceSeconds the same, used while the battle script still has a phase to run
     * @param scriptedPhasePending true while the script is still expected to spawn more enemies, may be null
     * @param ignoreOffMapShips treat enemies far outside the map as gone; leave false if a script parks
     *                          a live enemy off-map on purpose (the Rajanya boss does)
     */
    public armaa_stuckBattleWatchdog(String tag, float graceSeconds, float pendingGraceSeconds,
            BooleanSupplier scriptedPhasePending, boolean ignoreOffMapShips) {
        this.tag = tag;
        this.graceSeconds = graceSeconds;
        this.pendingGraceSeconds = pendingGraceSeconds;
        this.scriptedPhasePending = scriptedPhasePending;
        this.ignoreOffMapShips = ignoreOffMapShips;
    }

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null || engine.isPaused() || engine.isCombatOver()) {
            return;
        }
        sinceCheck += amount;
        if (sinceCheck < CHECK_INTERVAL) {
            return;
        }
        float dt = sinceCheck;
        sinceCheck = 0f;

        if (!findRemainingEnemies().isEmpty()) {
            emptyFor = 0f;
            logged = false;
            return;
        }

        emptyFor += dt;
        boolean pending = scriptedPhasePending != null && scriptedPhasePending.getAsBoolean();
        float grace = pending ? pendingGraceSeconds : graceSeconds;

        if (!logged && emptyFor >= LOG_AFTER) {
            logged = true;
            logState("no enemy ships left on the map, waiting " + grace + "s before forcing the end", pending);
        }
        if (emptyFor >= grace) {
            logState("battle did not end on its own, ending it as a player victory", pending);
            engine.setDoNotEndCombat(false);
            engine.endCombat(2f, FleetSide.PLAYER);
            engine.removePlugin(this);
        }
    }

    // Mirrors what blocks vanilla from ending combat: living, non-fighter enemy ships.
    // With ignoreOffMapShips, ships far outside the map are skipped since the player cannot reach them.
    private List<ShipAPI> findRemainingEnemies() {
        List<ShipAPI> remaining = new ArrayList<ShipAPI>();
        for (ShipAPI ship : engine.getShips()) {
            if (ship.getOwner() != 1 || !ship.isAlive() || ship.isHulk() || ship.isFighter()
                    || ship.isShuttlePod() || ship.isPiece() || ship.getParentStation() != null) {
                continue;
            }
            if (ignoreOffMapShips && isOutsideMap(ship)) {
                continue;
            }
            remaining.add(ship);
        }
        return remaining;
    }

    private boolean isOutsideMap(ShipAPI ship) {
        float halfW = engine.getMapWidth() / 2f + MAP_MARGIN;
        float halfH = engine.getMapHeight() / 2f + MAP_MARGIN;
        return Math.abs(ship.getLocation().x) > halfW || Math.abs(ship.getLocation().y) > halfH;
    }

    private void logState(String reason, boolean scriptPending) {
        CombatFleetManagerAPI enemy = engine.getFleetManager(1);
        StringBuilder sb = new StringBuilder("[" + tag + "] " + reason);
        sb.append(" | elapsed=").append((int) engine.getTotalElapsedTime(false)).append("s");
        sb.append(" emptyFor=").append((int) emptyFor).append("s");
        sb.append(" scriptPending=").append(scriptPending);
        sb.append(" enemyFullRetreat=").append(engine.isEnemyInFullRetreat());
        if (enemy != null) {
            sb.append(" enemyDeployed=").append(enemy.getDeployedCopy().size());
            sb.append(" enemyReserves=").append(enemy.getReservesCopy().size());
            for (FleetMemberAPI member : enemy.getReservesCopy()) {
                sb.append(" reserve:").append(member.getHullId());
            }
        }
        for (ShipAPI ship : engine.getShips()) {
            if (ship.getOwner() != 1 || !ship.isAlive() || ship.isHulk()) {
                continue;
            }
            sb.append(" | alive ").append(ship.getHullSpec().getHullId())
                    .append(" at (").append((int) ship.getLocation().x).append(",").append((int) ship.getLocation().y).append(")")
                    .append(ship.isFighter() ? " fighter" : "")
                    .append(ship.isRetreating() ? " retreating" : "")
                    .append(isOutsideMap(ship) ? " outsideMap" : "");
        }
        log.info(sb.toString());
    }
}
