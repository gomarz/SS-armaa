package data.scripts.weapons.basilisk;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import data.scripts.ai.armaa_BasiliskMissileAI;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Two jobs:
 *   1. Detonate Basilisks that die before firing, at their last known
 *      position and charge level.
 *   2. Advance expanding blast waves after detonation.
 *
 * Register in data/config/settings.json:
 *   "plugins":{ "armaa_basilisk_tracker":"data.scripts.weapons.basilisk.BasiliskTracker" }
 */
public class armaa_BasiliskTracker extends BaseEveryFrameCombatPlugin {

    public static final String KEY = "armaa_basilisk_tracker";

    private static class Entry {
        final MissileAPI missile;
        final armaa_BasiliskMissileAI ai;
        final ShipAPI source;
        final Vector2f lastLoc;
        float lastCharge;

        Entry(MissileAPI m, armaa_BasiliskMissileAI ai, ShipAPI source) {
            this.missile    = m;
            this.ai         = ai;
            this.source     = source;
            this.lastLoc    = new Vector2f(m.getLocation());
            this.lastCharge = 0f;
        }
    }

    private final List<Entry> tracked = new ArrayList<Entry>();
    private final List<armaa_BasiliskDetonation.ExpandingBlast> blasts =
            new ArrayList<armaa_BasiliskDetonation.ExpandingBlast>();

    /** Returns null if the plugin is not registered -- always null-check. */
public static armaa_BasiliskTracker get(CombatEngineAPI engine) {
    Object o = engine.getCustomData().get(KEY);
    if (o instanceof armaa_BasiliskTracker) return (armaa_BasiliskTracker) o;

    armaa_BasiliskTracker t = new armaa_BasiliskTracker();
    engine.getCustomData().put(KEY, t);
    engine.addPlugin(t);
    return t;
}

    public void register(MissileAPI missile, armaa_BasiliskMissileAI ai, ShipAPI source) {
        tracked.add(new Entry(missile, ai, source));
    }

    public void addBlast(armaa_BasiliskDetonation.ExpandingBlast blast) {
        blasts.add(blast);
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;

        advanceMissiles(engine, amount);
        advanceBlasts(engine, amount);
    }

    private void advanceMissiles(CombatEngineAPI engine, float amount) {
        if (tracked.isEmpty()) return;
        List<MissileAPI> live = engine.getMissiles();

        for (Iterator<Entry> it = tracked.iterator(); it.hasNext(); ) {
            Entry e = it.next();

            // Fired normally -- the AI already handled it.
            if (e.ai != null && e.ai.hasDetonated()) {
                it.remove();
                continue;
            }

            boolean gone = !live.contains(e.missile)
                        || e.missile.getHitpoints() <= 0f
                        || !engine.isEntityInPlay(e.missile);

            if (gone) {
                // Shot down mid-charge. Yield scales with how far it got.
                armaa_BasiliskDetonation.detonate(engine, e.lastLoc, e.source, e.lastCharge);
                it.remove();
                continue;
            }

            e.lastLoc.set(e.missile.getLocation());
            e.lastCharge = (e.ai != null) ? e.ai.getCharge() : 0f;
        }
    }

    private void advanceBlasts(CombatEngineAPI engine, float amount) {
        if (blasts.isEmpty()) return;
        for (Iterator<armaa_BasiliskDetonation.ExpandingBlast> it = blasts.iterator(); it.hasNext(); ) {
            if (it.next().advance(engine, amount)) it.remove();
        }
    }

    @Override
    public void init(CombatEngineAPI engine) {
        tracked.clear();
        blasts.clear();
        engine.getCustomData().put(KEY, this);
    }
}