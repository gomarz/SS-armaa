package data.scripts.shipsystems.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.weapons.armaa_AWACS;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

public class armaa_prioritymark_ai implements ShipSystemAIScript {

    private ShipAPI ship;
    private CombatEngineAPI engine;
    private ShipwideAIFlags flags;

    private final IntervalUtil tracker = new IntervalUtil(0.25f, 0.5f);


    private static final float MAX_RANGE = 2000f;

    private static final float MARK_DURATION = 7f;

    private static final float ALLY_SUPPORT_RADIUS = 1200f;
    private static final float ENEMY_CLUMP_RADIUS = 700f;

    private static final Map<ShipAPI.HullSize, Float> SIZE_WEIGHT = new HashMap<ShipAPI.HullSize, Float>();
    static {
        SIZE_WEIGHT.put(ShipAPI.HullSize.FIGHTER, 0.2f);
        SIZE_WEIGHT.put(ShipAPI.HullSize.FRIGATE, 1.0f);
        SIZE_WEIGHT.put(ShipAPI.HullSize.DESTROYER, 1.6f);
        SIZE_WEIGHT.put(ShipAPI.HullSize.CRUISER, 2.3f);
        SIZE_WEIGHT.put(ShipAPI.HullSize.CAPITAL_SHIP, 3.2f);
    }

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        if (engine == null || ship == null) return;
        if (engine.isPaused()) return;

        tracker.advance(amount);
        if (!tracker.intervalElapsed()) return;

        if (!AIUtils.canUseSystemThisFrame(ship)) return;
        if (ship.isRetreating() || ship.isDirectRetreat()) return;

        // don’t spam while mark should still be active
        if (!isMarkActive() && AIUtils.canUseSystemThisFrame(ship)) 
        {           
            flags.setFlag(AIFlags.DO_NOT_BACK_OFF, 2.5f);
            flags.setFlag(AIFlags.PURSUING, 1.5f);
        }
        else if (isMarkActive())
            return;

        float range = getDynamicRange();

        ShipAPI best = pickBestTarget(range);
        if (best == null) return;

        ship.setShipTarget(best);
        ship.useSystem();

    }

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.flags = flags;
        this.engine = engine;
    }

    // ------------------------
    // Target selection
    // ------------------------

    private ShipAPI pickBestTarget(float range) {
        List<ShipAPI> enemies = CombatUtils.getShipsWithinRange(ship.getLocation(), range);

        ShipAPI best = null;
        float bestScore = -999999f;

        if (enemies == null || enemies.isEmpty()) return null;

        for (ShipAPI e : enemies) {
            if (!isValidEnemy(e)) continue;

            float score = scoreTarget(e, range);
            if (score > bestScore) {
                bestScore = score;
                best = e;
            }
        }

        // small threshold so we don’t fire on trash picks
        if (bestScore < 1.25f) return null;
        return best;
    }

    private boolean isValidEnemy(ShipAPI e) {
        if (e == null) return false;
        if (!e.isAlive()) return false;
        if (e.getOwner() == ship.getOwner()) return false;

        if (e.isFighter() || e.isDrone()) return false;
        if (e.isStation() || e.isStationModule()) return false;
        if (e.isPhased()) return false;

        if (e.getHullLevel() < 0.15f) return false;

        return true;
    }

    private float scoreTarget(ShipAPI e, float maxRange) {
        float dist = MathUtils.getDistance(ship, e);
        float distScore = 1f - Math.min(dist / maxRange, 1f);  // 0..1
        distScore *= 1.2f;

        float size = 1f;
        Float w = SIZE_WEIGHT.get(e.getHullSize());
        if (w != null) size = w;

        float flux = e.getFluxLevel();
        float hardFlux = e.getHardFluxLevel();
        float vuln = 1f + (0.8f * flux) + (0.6f * hardFlux);
        if (e.getFluxTracker().isOverloadedOrVenting()) vuln += 1.5f;

        float hullFinish = 1f + (0.8f * (1f - e.getHullLevel()));

        int enemyCount = 0;
        for (ShipAPI other : CombatUtils.getShipsWithinRange(e.getLocation(), ENEMY_CLUMP_RADIUS)) {
            if (other == null || other == e) continue;
            if (!other.isAlive()) continue;
            if (other.getOwner() == ship.getOwner()) continue;
            if (other.isFighter() || other.isDrone() || other.isStation() || other.isStationModule()) continue;
            enemyCount++;
        }
        float clump = 1f + Math.min(enemyCount, 5) * 0.12f;

        return (size * vuln * hullFinish *  clump) + distScore;
    }

    // ------------------------
    // Range scaling via deployed drones
    // ------------------------

    private float getDynamicRange() {
        ShipAPI droneBase = ship.getChildModulesCopy() != null  && ship.getChildModulesCopy().get(0) != null ? ship.getChildModulesCopy().get(0) : ship;
        float r = MAX_RANGE * armaa_AWACS.getEffectLevel(droneBase);
        //Global.getLogger(this.getClass()).info(r);
        return Math.min(r, MAX_RANGE);
    }

    private boolean isMarkActive() {
       return ship.getSystem().isActive();
    }
}
