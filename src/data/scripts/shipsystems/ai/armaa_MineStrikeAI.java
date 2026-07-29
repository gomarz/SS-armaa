package data.scripts.shipsystems.ai;


import com.fs.starfarer.api.combat.ArmorGridAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import data.scripts.shipsystems.armaa_MineStrikeStats;
import org.lwjgl.util.vector.Vector2f;

public class armaa_MineStrikeAI implements ShipSystemAIScript {

    private ShipAPI ship;
    private ShipSystemAPI system;
    private ShipwideAIFlags flags;
    private CombatEngineAPI engine;
    private final IntervalUtil tracker = new IntervalUtil(0.05f, 0.05f);

    // must match reality: fuse delay (1s) + windup (0.25) + fade-in (0.5)
    private static final float TIME_TO_LIVE_FIELD = 1.75f;
    private static final float FIELD_RADIUS = 200f;   // rough footprint of the ring
    private static final float MIN_SELF_DIST = 250f;  // don't mine our own doorstep

    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship; this.system = system; this.flags = flags; this.engine = engine;
    }

    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        if (engine.isPaused() || ship.getShipAI() == null) return;
        tracker.advance(amount);
        if (!tracker.intervalElapsed()) return;

        if (system.isOutOfAmmo() || system.isActive() || system.getCooldownRemaining() > 0) return;

        float range = armaa_MineStrikeStats.getRange(ship) + ship.getCollisionRadius();

        Vector2f best = null;
        float bestScore = 0f;

        // --- score fighter clusters ---
        for (ShipAPI f : engine.getShips()) {
            if (!f.isFighter() || f.getOwner() == ship.getOwner() || !f.isAlive()) continue;
            if (Misc.getDistance(ship.getLocation(), f.getLocation()) > range) continue;

            float score = 0f;
            for (ShipAPI g : engine.getShips()) {
                if (!g.isFighter() || g.getOwner() == ship.getOwner() || !g.isAlive()) continue;
                if (Misc.getDistance(f.getLocation(), g.getLocation()) < FIELD_RADIUS) score += 1f;
            }
            if (score > bestScore) {
                bestScore = score;
                best = leadPoint(f);
            }
        }

        // --- score missile salvos ---
        for (MissileAPI m : engine.getMissiles()) {
            if (m.isMine() || m.getOwner() == ship.getOwner() || m.isFizzling()) continue;
            if (Misc.getDistance(ship.getLocation(), m.getLocation()) > range) continue;

            float score = 0f;
            for (MissileAPI n : engine.getMissiles()) {
                if (n.isMine() || n.getOwner() == ship.getOwner() || n.isFizzling()) continue;
                if (Misc.getDistance(m.getLocation(), n.getLocation()) < FIELD_RADIUS) score += 0.5f;
            }
            if (score > bestScore) {
                bestScore = score;
                best = leadPoint(m);
            }
        }

        // --- ships: small always eligible, big only when cracked open ---
        for (ShipAPI s : engine.getShips()) {
            if (s.isFighter() || s.getOwner() == ship.getOwner() || !s.isAlive()) continue;
            if (Misc.getDistance(ship.getLocation(), s.getLocation()) > range) continue;

            float score = 0f;
            if (s.isFrigate() || s.isDrone()) score = 2.5f;
            else if (s.isDestroyer()) score = 2f;
            else if (isArmorStripped(s)) score = 3f;   // cruiser/cap, but soft underneath
            // vulnerable states sweeten any of the above
            if (score > 0 && (s.getFluxTracker().isOverloadedOrVenting())) score += 1.5f;

            if (score > bestScore) {
                bestScore = score;
                best = new Vector2f(s.getLocation());
            }
        }

        if (best != null && bestScore >= 3f
                && Misc.getDistance(ship.getLocation(), best) > MIN_SELF_DIST) {
            flags.setFlag(AIFlags.SYSTEM_TARGET_COORDS, 1f, best);
            ship.useSystem();
        }
    }

    private Vector2f leadPoint(CombatEntityAPI e) {
        Vector2f lead = new Vector2f(e.getVelocity());
        lead.scale(TIME_TO_LIVE_FIELD);
        return Vector2f.add(new Vector2f(e.getLocation()), lead, lead);
    }

    private boolean isArmorStripped(ShipAPI s) {
        ArmorGridAPI grid = s.getArmorGrid();
        float max = grid.getMaxArmorInCell();
        if (max <= 0) return true;
        float[][] cells = grid.getGrid();
        int stripped = 0, total = 0;
        for (float[] col : cells) {
            for (float v : col) {
                total++;
                if (v < max * 0.25f) stripped++;
            }
        }
        return total > 0 && (float) stripped / total > 0.35f; // >35% of cells cracked
    }
}