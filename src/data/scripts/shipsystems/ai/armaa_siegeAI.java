package data.scripts.shipsystems.ai;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.util.armaa_utils;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

/**
 * Siege system AI: - Activates only when there is at least one valuable target
 * in the "siege band": outside normal large-weapon range, but inside boosted
 * range, AND with clear LoF (no friendly body blocking). - Deactivates
 * aggressively when pressured, brawling, or LoF/value disappears.
 *
 */
public class armaa_siegeAI implements ShipSystemAIScript {

    private ShipAPI ship;
    private CombatEngineAPI engine;
    private ShipwideAIFlags flags;
    private ShipSystemAPI system;

    private final IntervalUtil tracker = new IntervalUtil(0.35f, 0.60f);

    // --- Tuning knobs ---
    private static final float BONUS_INACTIVE = 1.8f; // should match  system's effective range boost (or close)
    private static final float BONUS_ACTIVE = 1.0f;   // while active, we can just evaluate boosted range directly via ship stats

    private static final float HYSTERESIS_ON = 2.5f;   // score needed to activate
    private static final float HYSTERESIS_OFF = 0.1f;  // score below which to deactivate (when active)

    private static final float BASE_RANGE_IGNORE_FRAC = 0.90f; // if target is within 90% base range, siege is not worth it
    private static final float BOOSTED_RANGE_FRAC = 0.98f;      // consider targets up to 98% of boosted range (avoid edge chasing)

    private static final float ENEMY_TOO_CLOSE = 900f;     // if hostiles this close, siege is usually bad
    private static final float ALLY_LOF_FUDGE = 25f;       // extra radius to be conservative with friendly bodies
    private static final float MIN_TARGET_RADIUS_BONUS = 80f; // mild preference for bigger ships (optional)

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.flags = flags;
        this.engine = engine;
        this.system = ship != null ? ship.getSystem() : system;
    }

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        if (engine == null || ship == null) {
            return;
        }
        if (engine.isPaused()) {
            return;
        }

        tracker.advance(amount);
        if (!tracker.intervalElapsed()) {
            return;
        }

        // --------- Abort/pressure checks (used for both activate and deactivate) ---------
        boolean underPressure = false;

        if (flags != null && flags.hasFlag(ShipwideAIFlags.AIFlags.HAS_INCOMING_DAMAGE)) {
            float incoming = armaa_utils.estimateIncomingDamage(ship);
            float beams = armaa_utils.estimateIncomingBeamDamage(ship, 5f);
            if (incoming > 100f || beams >= 10f) {
                underPressure = true;
                // Encourage backing off while we drop/avoid siege lock-in
                flags.setFlag(ShipwideAIFlags.AIFlags.BACK_OFF, 1.5f);
            }
        }

        // If enemies are very close, we likely need mobility/turning, not siege mode
        boolean enemyTooClose = hasHostileWithin(ENEMY_TOO_CLOSE);

        // --------- Evaluate siege value score ---------
        SiegeEval eval = evaluateSiegeValue();

        // --------- Deactivate logic (FIXED: reachable) ---------
        if (system != null && system.isActive()) {
            if (enemyTooClose) {
                system.deactivate();
            }
            return; // when active we don't also try to activate again
        }

        // --------- Activate logic ---------
        if (system == null || system.isActive()) {
            return;
        }

        // Never activate while pressured or brawling
        if (underPressure || enemyTooClose) {
            return;
        }

        // Only activate if we found a meaningful, clear-LoF siege opportunity
        if (eval.bestScore >= HYSTERESIS_ON) {
            ship.useSystem();
        }
    }

    private boolean hasHostileWithin(float range) {
        List<ShipAPI> nearby = CombatUtils.getShipsWithinRange(ship.getLocation(), range);
        for (ShipAPI other : nearby) {
            if (other == ship) {
                continue;
            }
            if (!isValidCombatShip(other)) {
                continue;
            }
            if (other.getOwner() == ship.getOwner()) {
                continue;
            }
            return true;
        }
        return false;
    }

    private static boolean isValidCombatShip(ShipAPI s) {
        if (s == null) {
            return false;
        }
        if (!s.isAlive() || s.isHulk()) {
            return false;
        }
        if (s.isFighter()) {
            return false;
        }
        return true;
    }

    private static boolean isLargeNonDecor(WeaponAPI w) {
        return w != null
                && !w.isDecorative()
                && w.getSize() == WeaponAPI.WeaponSize.LARGE
                && w.getShip() != null
                && !w.isDisabled();
    }

    private static final class SiegeEval {

        float bestScore = 0f;
        WeaponAPI bestWeapon = null;
        ShipAPI bestTarget = null;
    }

    private SiegeEval evaluateSiegeValue() {
        SiegeEval out = new SiegeEval();

        // Gather large weapons once
        List<WeaponAPI> largeWeapons = new ArrayList<>();
        float maxBaseRange = 0f;

        for (WeaponAPI w : ship.getAllWeapons()) {
            if (!isLargeNonDecor(w)) {
                continue;
            }
            largeWeapons.add(w);
            maxBaseRange = Math.max(maxBaseRange, w.getRange());
        }

        if (largeWeapons.isEmpty()) {
            return out;
        }

        // Compute a "search radius" for candidates. We use the ship stat system range bonus, since it's already in your code.
        // This is an approximation of boosted range while inactive; good enough for target gathering.
        float bonus = system != null && system.isActive() ? BONUS_ACTIVE : BONUS_INACTIVE;

        // Use the max base range among large weapons to find candidate ships.
        float effectiveMaxRange = ship.getMutableStats().getSystemRangeBonus().computeEffective(maxBaseRange * bonus);
        float searchRange = effectiveMaxRange + 400f; // padding so we don't miss edge candidates

        List<ShipAPI> nearby = CombatUtils.getShipsWithinRange(ship.getLocation(), searchRange);

        List<ShipAPI> friendlies = new ArrayList<>(nearby.size());
        List<ShipAPI> enemies = new ArrayList<>(nearby.size());

        for (ShipAPI s : nearby) {
            if (s == ship) {
                continue;
            }
            if (!isValidCombatShip(s)) {
                continue;
            }

            if (s.getOwner() == ship.getOwner()) {
                friendlies.add(s);
            } else {
                enemies.add(s);
            }
        }

        if (enemies.isEmpty()) {
            return out;
        }

        // Score each (weapon, enemy) pair; pick best.
        for (WeaponAPI w : largeWeapons) {
            float baseRange = w.getRange();

            // Compute boosted range for THIS weapon (approx).
            float boostedRange = ship.getMutableStats().getSystemRangeBonus().computeEffective(baseRange * bonus);

            for (ShipAPI enemy : enemies) {
                float d = MathUtils.getDistance(ship, enemy);

                // Need enemy to be in the "siege band": outside base range (mostly), but within boosted range.
                if (d <= baseRange * BASE_RANGE_IGNORE_FRAC) {
                    continue;
                }
                if (d >= boostedRange * BOOSTED_RANGE_FRAC) {
                    continue;
                }

                // Must be in arc (your original used distanceFromArc == 0)
                if (w.distanceFromArc(enemy.getLocation()) > 0f) {
                    continue;
                }

                // Must have clear LoF (no friendly ship body intersecting segment)
                if (!hasClearLoF(w, enemy, friendlies)) {
                    continue;
                }

                // --- Scoring ---
                float score = 0f;

                // Primary: being in the band is good. Prefer farther in band (more "need" for siege).
                float bandT = (d - baseRange) / Math.max(1f, (boostedRange - baseRange));
                bandT = clamp01(bandT);
                score += 2.0f + 2.0f * bandT; // 2..4

                // Prefer bigger collision radius slightly (avoid wasting siege on tiny frigates)
                float r = enemy.getCollisionRadius();
                score += Math.min(1.0f, Math.max(0f, (r - MIN_TARGET_RADIUS_BONUS) / 200f)); // 0..1

                // If enemy is very close anyway, reduce value (safety net)
                if (d < 1000f) {
                    score -= 1.0f;
                }

                if (score > out.bestScore) {
                    out.bestScore = score;
                    out.bestWeapon = w;
                    out.bestTarget = enemy;
                }
            }
        }

        return out;
    }

    private boolean hasClearLoF(WeaponAPI weapon, ShipAPI enemy, List<ShipAPI> friendlies) {
        Vector2f from = weapon.getLocation();
        Vector2f to = enemy.getLocation();

        for (ShipAPI ally : friendlies) {
            if (ally == ship) {
                continue;
            }
            if (!ally.isAlive() || ally.isHulk()) {
                continue;
            }

            float r = ally.getCollisionRadius() + ALLY_LOF_FUDGE;
            if (CollisionUtils.getCollides(from, to, ally.getLocation(), r)) {
                return false;
            }
        }
        return true;
    }

    private static float clamp01(float x) {
        if (x < 0f) {
            return 0f;
        }
        if (x > 1f) {
            return 1f;
        }
        return x;
    }
}
