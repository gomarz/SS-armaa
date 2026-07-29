package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import data.scripts.util.armaa_utils;
import org.lazywizard.lazylib.MathUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class armaa_aegisPlugin extends BaseEveryFrameCombatPlugin {

    private static final float SCALE_EPS = 0.02f; // re-apply only if scale moved this much

    // Cached list of live Aegis hosts, read by the damage listener each hit.
    public static final List<ShipAPI> AEGIS_HOSTS = new ArrayList<>();

    // ship -> last zero-flux scale we applied to it. Presence = currently buffed.
    private final Map<ShipAPI, Float> buffedScale = new HashMap<>();

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) {
            return;
        }

        List<ShipAPI> ships = engine.getShips();

        // --- pass 1: rebuild host cache + attach renderers -----------------
        AEGIS_HOSTS.clear();
        for (int i = 0, n = ships.size(); i < n; i++) {
            ShipAPI ship = ships.get(i);
            if (!armaa_aegisConfig.isAegisHost(ship)) {
                continue;
            }
            AEGIS_HOSTS.add(ship);

            if (!ship.getCustomData().containsKey("armaa_aegis_ring")) {
                ship.getCustomData().put("armaa_aegis_ring", Boolean.TRUE);
                //engine.addLayeredRenderingPlugin(new armaa_aegisRenderer(ship));
            }
        }

        // --- pass 2: zero-flux mobility boost on covered ships -------------
        // Track who's covered this frame so we can remove the boost from
        // anyone who dropped out.
        List<ShipAPI> toRemove = new ArrayList<>();

        if (!AEGIS_HOSTS.isEmpty()) {
            for (int i = 0, n = ships.size(); i < n; i++) {
                ShipAPI ship = ships.get(i);
                if (ship.isHulk() || !ship.isAlive()) {
                    continue;
                }

                ShipAPI cover = coveringHostFor(ship);
                if (cover == null) {
                    continue;
                }

                float scale = armaa_aegisConfig.currentReduction(cover)
                        / armaa_aegisConfig.MAX_REDUCTION; // 1 fresh -> 0 at cutoff

                Float last = buffedScale.get(ship);
                // only touch stats on enter, or when the scale moved noticeably
                if (last == null || Math.abs(last - scale) > SCALE_EPS) {
                    applyZeroFluxBoost(ship, scale);
                    buffedScale.put(ship, scale);
                }
            }
        }

        // remove the boost from ships that were buffed but aren't covered now
        for (Map.Entry<ShipAPI, Float> e : buffedScale.entrySet()) {
            ShipAPI ship = e.getKey();
            boolean stillCovered = !ship.isHulk() && ship.isAlive()
                    && !AEGIS_HOSTS.isEmpty() && coveringHostFor(ship) != null;
            if (!stillCovered) {
                if (ship.isAlive() && !ship.isHulk()) {
                    removeZeroFluxBoost(ship);
                }
                toRemove.add(ship);
            }
        }
        for (int i = 0; i < toRemove.size(); i++) {
            buffedScale.remove(toRemove.get(i));
        }
    }

    /**
     * Returns a live host covering this ship, or null.
     */
    private static ShipAPI coveringHostFor(ShipAPI ship) {
        return armaa_aegisConfig.bestCoveringHost(AEGIS_HOSTS, ship);
    }

    private static void applyZeroFluxBoost(ShipAPI ship, float scale) {
        MutableShipStatsAPI stats = ship.getMutableStats();
        // permission: allow the boost regardless of the ship's own flux level
        if (ship.getVariant().hasHullMod("strikeCraft")) {
            stats.getZeroFluxMinimumFluxLevel().unmodify("strikeCraft");
            stats.getAllowZeroFluxAtAnyLevel().unmodify("strikeCraft");
        }
        // magnitude: scale the boost with the Bellator's remaining strength
        stats.getZeroFluxSpeedBoost().modifyMult("armaa_aegis", scale);
        float threshold = 0.7f * scale;  // fresh bubble = boost up to 70% flux; fades with the Bellator
        stats.getZeroFluxMinimumFluxLevel().modifyFlat("armaa_aegis", threshold);
    }

    private static void removeZeroFluxBoost(ShipAPI ship) {
        MutableShipStatsAPI stats = ship.getMutableStats();
        stats.getZeroFluxSpeedBoost().unmodify("armaa_aegis");
        // restore the baseline suppression the mod normally applies to strikecraft
        if (ship.getVariant().hasHullMod("strikeCraft")) {
            armaa_utils.applyFlatMod(stats.getZeroFluxMinimumFluxLevel(), "strikeCraft", -114514f);
            armaa_utils.applyMultMod(stats.getAllowZeroFluxAtAnyLevel(), "strikeCraft", -114514f);
        }
    }
}
