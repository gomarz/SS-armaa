package data.scripts.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.awt.Color;
import java.util.Collections;
import org.magiclib.util.MagicRender;

public class armaa_armorPodAI implements MissileAIPlugin, GuidedMissileAI {

    private static final Logger LOG = Global.getLogger(armaa_armorPodAI.class);

    // -- tunables ---------------------------------------------------------
    /**
     * How far past the contact point the pod sinks in.
     */
    private static final float EMBED_DEPTH = 3f;
    /**
     * How close to the target's bounds the pod latches, past its own radius.
     */
    private static final float ATTACH_RANGE = 1f;
    /**
     * How long the board runs while the pod is present and amplifying.
     */
    private static final float BOARD_DURATION = 10f;

    private static final float RESIDUAL_DURATION = 2.5f;
    /**
     * Seconds between boarding effect ticks.
     */
    private static final float TICK_INTERVAL = 0.8f;

    /**
     * Sabotage gate while the pod is present (amplified).
     */
    private static final float SABOTAGE_CHANCE = 0.35f;

    private static final float SABOTAGE_CHANCE_RESIDUAL = 0.15f;
    private static final float SABOTAGE_DAMAGE = 25f;

    private static final float SABOTAGE_EMP = 300f;
    private static final float BREACH_EMP = 1000f;
    private static final float BREACH_DAMAGE = 200f;

    private static final float WEAPON_MALF_BUMP = 0.01f;

    private static final float ENGINE_MALF_BUMP = 0.03f;

    private static final float WEAPON_MALF_CAP = 0.03f;
    private static final float ENGINE_MALF_CAP = 0.10f;

    private static final int MAX_PODS_PER_TARGET = 3;

    private static final float ACQUIRE_RANGE = 1500f;

    private static final HullSize MIN_BOARD_SIZE = HullSize.FRIGATE;
    /**
     * Turn rate cap during pursuit, degrees/sec.
     */
    private static float TURN_RATE = 90f;
    /**
     * Aim scatter, as a fraction of target collision radius. Salvos spread
     * across the hull instead of stacking on one contact point.
     */
    private static final float SCATTER_MIN = 0.35f;
    private static final float SCATTER_MAX = 0.8f;

    private static final String MALF_KEY = "armaa_boardingPod_malf";

    private static final String REAPER_KEY = "armaa_boardingPod_reaper";
    private static final String BOARDINGS_KEY = "armaa_boardingPod_boardings";

    // -- state ------------------------------------------------------------
    private static int podCounter = 0;

    private final MissileAPI missile;
    private CombatEngineAPI engine;
    private ShipAPI target;

    private final String tag;

    private boolean handedOff = false;

    private boolean freeDeployed = false;

    private final float scatterAngle = (float) (Math.random() * 360f);
    private final float scatterFrac
            = SCATTER_MIN + (float) Math.random() * (SCATTER_MAX - SCATTER_MIN);

    public armaa_armorPodAI(MissileAPI missile, ShipAPI launchingShip) {
        this.missile = missile;
        this.engine = Global.getCombatEngine();
        this.tag = "[armaaPod-" + (podCounter++) + "]";
        this.TURN_RATE = missile.getTurnAcceleration();

        // Prefer the launching ship's deliberate target if it's boardable.
        if (launchingShip != null && launchingShip.getShipTarget() != null
                && isValidTarget(launchingShip.getShipTarget())) {
            target = launchingShip.getShipTarget();
        }
    }

    @Override
    public void advance(float amount) {
        if (engine == null || engine.isPaused()) {
            return;
        }
        if (handedOff) {
            return;
        }
        if (missile.isFading() || missile.isFizzling() || missile.getFlightTime() >= missile.getMaxFlightTime() - 0.25f) {
            if (!freeDeployed) {
                freeDeployed = true;
                deployAssaultPod(missile.getLocation(), missile.getFacing(),
                        missile.getVelocity(), false);
                engine.removeEntity(missile);
            }
            return;
        }
        advanceSeeking(amount);
    }

    private void advanceSeeking(float amount) {
        if (!isValidTarget(target)) {
            target = acquireTarget();
            if (target == null) {
                return; // drift dumbfire until something shows up
            }
        }

        // Steer: lead pursuit, clamped turn rate.
        Vector2f aim = intercept(missile, target);
        if (MathUtils.getDistance(missile, target) > missile.getCollisionRadius() + ATTACH_RANGE * 4f) {
            Vector2f scatter = MathUtils.getPointOnCircumference(new Vector2f(),
                    target.getCollisionRadius() * scatterFrac,
                    target.getFacing() + scatterAngle);
            Vector2f.add(aim, scatter, aim);
        }

        float desired = VectorUtils.getAngle(missile.getLocation(), aim);
        float delta = MathUtils.getShortestRotation(missile.getFacing(), desired);
        float maxTurn = TURN_RATE * amount;
        missile.setFacing(missile.getFacing()
                + Math.max(-maxTurn, Math.min(maxTurn, delta)));
        missile.giveCommand(ShipCommand.ACCELERATE);

        if (MathUtils.getDistance(missile, target) < missile.getCollisionRadius() + ATTACH_RANGE) {
            Vector2f nearest = CollisionUtils.getNearestPointOnBounds(missile.getLocation(), target);
            if (MathUtils.getDistance(missile.getLocation(), nearest)
                    <= missile.getCollisionRadius() + ATTACH_RANGE) {
                attach();
            }
        }
    }

    private void attach() {
        Vector2f contact = CollisionUtils.getNearestPointOnBounds(missile.getLocation(), target);
        Vector2f inward = Vector2f.sub(contact, missile.getLocation(), null);
        float inwardAngle = inward.lengthSquared() > 0f
                ? VectorUtils.getFacing(inward)
                : missile.getFacing();

        missile.getLocation().set(
                MathUtils.getPointOnCircumference(contact, EMBED_DEPTH, inwardAngle));
        missile.setFacing(inwardAngle); // nose-in, like the spike

        // Record where we are in the target's local frame.
        Vector2f localOffset = new Vector2f();
        Vector2f rel = Vector2f.sub(missile.getLocation(), target.getLocation(), null);
        VectorUtils.rotate(rel, -target.getFacing(), localOffset);
        float facingOffset = missile.getFacing() - target.getFacing();

        missile.setCollisionClass(CollisionClass.FIGHTER);

        BoardingRecord rec = new BoardingRecord(
                target, missile.getSource(), missile,
                localOffset, facingOffset);
        getBoardings(engine).add(rec);
        handedOff = true;

        ensureReaper(engine);

        rec.breachStrike(engine);

        updateMalfBump(engine, target);

        engine.spawnEmpArcVisual(missile.getLocation(), missile,
                target.getLocation(), target, 10f,
                new Color(120, 200, 255, 255), new Color(255, 255, 255, 255));
    }

    private static void deployAssaultPod(Vector2f loc, float facing,
            Vector2f vel, boolean fromSource, ShipAPI source) {
        CombatEngineAPI engine = Global.getCombatEngine();
        CombatFleetManagerAPI cfm = engine.getFleetManager(1);
        cfm.setSuppressDeploymentMessages(true);
        ShipAPI pod = cfm.spawnShipOrWing("armaa_assaultPod_hull_pa", loc, 0f);
        if (pod != null) {
            pod.setFacing(facing);
            pod.getVelocity().set(vel);
            int owner = (fromSource && source != null)
                    ? source.getOriginalOwner() : 1;
            pod.setOwner(owner);
            pod.setOriginalOwner(owner);
            pod.getMutableStats().getFighterRefitTimeMult()
                    .modifyPercent(pod.getId(), 9999f);
        }
        cfm.setSuppressDeploymentMessages(false);
        engine.spawnExplosion(loc, vel,
                new Color(150, 150, 160, 160), 30f, 0.3f);
    }

    private void deployAssaultPod(Vector2f loc, float facing, Vector2f vel,
            boolean fromSource) {
        deployAssaultPod(loc, facing, vel, true, missile.getSource());
    }

    public static class BoardingRecord {

        private final ShipAPI target;
        private final ShipAPI source;

        private MissileAPI pod;

        private final Vector2f localOffset;
        private final float facingOffset;

        private final Vector2f lastKnownLoc = new Vector2f();

        private float boardTimer = 0f;
        private float residualTimer = 0f;
        private boolean residual = false; // pod gone, degraded window running
        private boolean finished = false;
        private boolean fighterDeployed = false;

        private final IntervalUtil tick
                = new IntervalUtil(TICK_INTERVAL, TICK_INTERVAL);

        BoardingRecord(ShipAPI target, ShipAPI source, MissileAPI pod,
                Vector2f localOffset, float facingOffset) {
            this.target = target;
            this.source = source;
            this.pod = pod;
            this.localOffset = localOffset;
            this.facingOffset = facingOffset;
            this.lastKnownLoc.set(pod.getLocation());
        }

        boolean isFinished() {
            return finished;
        }

        ShipAPI getTarget() {
            return target;
        }

        boolean isPodAlive(CombatEngineAPI engine) {
            return pod != null
                    && engine.isEntityInPlay(pod)
                    && !pod.isFading()
                    && !pod.isFizzling();
        }

        void advance(CombatEngineAPI engine, float amount) {
            if (finished) {
                return;
            }

            if (target == null || !engine.isEntityInPlay(target)
                    || !target.isAlive() || target.isHulk()) {
                if (!finished) {
                    engine.spawnExplosion(new Vector2f(lastKnownLoc),
                            new Vector2f(),
                            new Color(255, 180, 100, 200), 60f, 0.4f);
                }
                finish(engine, false);
                return;
            }
            if (target.isPhased()) {
                finish(engine, isPodAlive(engine));
                return;
            }

            boolean podAlive = isPodAlive(engine);

            if (podAlive) {
                // Keep the pod glued to the hull and remember where it is.
                Vector2f world = new Vector2f(localOffset);
                VectorUtils.rotate(world, target.getFacing(), world);
                Vector2f.add(world, target.getLocation(), world);
                pod.getLocation().set(world);
                pod.getVelocity().set(target.getVelocity());
                pod.setFacing(target.getFacing() + facingOffset);
                pod.setFlightTime(0f);

                // Render the submerged assault-pod sprite over the missile.
                MagicRender.singleframe(
                        Global.getSettings().getSprite("armaa_assaultPod", "submerged"),
                        new Vector2f(pod.getLocation()),
                        new Vector2f(pod.getSpriteAPI().getWidth(),
                                pod.getSpriteAPI().getHeight()),
                        pod.getFacing() - 90f,
                        new Color(1f, 1f, 1f), false);
                pod.getSpriteAPI().setColor(new Color(0, 0, 0, 0));

                lastKnownLoc.set(world);

                boardTimer += amount;
                if (boardTimer >= BOARD_DURATION) {
                    finish(engine, true); // full board: pod survived, deploy
                    return;
                }
            } else {
                // Pod is gone. First frame here: enter the residual window.
                if (!residual) {
                    residual = true;
                    residualTimer = 0f;
                    if (pod != null) {
                        // Snapshot the pod's final resting spot before we drop
                        // the reference; it's about to be removed by the engine.
                        if (engine.isEntityInPlay(pod)) {
                            lastKnownLoc.set(pod.getLocation());
                        }
                    }
                    pod = null;
                }
                residualTimer += amount;
                if (residualTimer >= RESIDUAL_DURATION) {
                    // Pod was destroyed: breach + residual damage already
                    // landed, but no fighter. Denying the pod denies the pod.
                    finish(engine, false);
                    return;
                }
            }

            // Sabotage ticks. Gate depends on amplified vs degraded.
            tick.advance(amount);
            if (tick.intervalElapsed()) {
                doTick(engine, podAlive);
            }
        }

        private void doTick(CombatEngineAPI engine, boolean podAlive) {
            float gate = podAlive ? SABOTAGE_CHANCE : SABOTAGE_CHANCE_RESIDUAL;
            Vector2f origin = podAlive ? pod.getLocation() : lastKnownLoc;

            if (Math.random() >= gate) {
                internalExplosion(engine, origin, 15f);
                return;
            }
            Vector2f loc = findNearestLiveComponent(origin);
            if (loc == null) {
                internalExplosion(engine, origin, 20f);
                return;
            }
            strike(engine, loc, SABOTAGE_DAMAGE, SABOTAGE_EMP);
        }
        private static final float BREACH_ARC_RANGE = 400f; // reach from insertion point
        private static final int BREACH_ARC_COUNT = 3;
        private static final float BREACH_ARC_DMG = 10f;
        private static final float BREACH_ARC_EMP = 400f; // ion cannon is 200 for reference

        void breachStrike(CombatEngineAPI engine) {
            Vector2f loc = findNearestLiveComponent(lastKnownLoc);
            if (loc == null) {
                loc = lastKnownLoc;
            }
            strike(engine, loc, BREACH_DAMAGE, BREACH_EMP);

            // Insertion surge: arc to up to 3 nearby weapons, PD first.
            List<WeaponAPI> pd = new ArrayList<>();
            List<WeaponAPI> other = new ArrayList<>();
            for (WeaponAPI w : target.getAllWeapons()) {
                if (w.isDecorative() || w.isDisabled() || w.isPermanentlyDisabled()) {
                    continue;
                }
                if (MathUtils.getDistance(w.getLocation(), lastKnownLoc) > BREACH_ARC_RANGE) {
                    continue;
                }
                (w.hasAIHint(WeaponAPI.AIHints.PD) ? pd : other).add(w);
            }
            Collections.shuffle(pd);
            Collections.shuffle(other);
            pd.addAll(other); // random within tier, PD prioritized

            int arcs = Math.min(BREACH_ARC_COUNT, pd.size());
            for (int i = 0; i < arcs; i++) {
                Vector2f wLoc = pd.get(i).getLocation();
                engine.spawnEmpArcVisual(lastKnownLoc, pod, wLoc, target,
                        10f, new Color(120, 200, 255, 255), Color.white);
                engine.applyDamage(target, wLoc, BREACH_ARC_DMG, DamageType.ENERGY,
                        BREACH_ARC_EMP, true, false, source, false);
            }
        }

        private void strike(CombatEngineAPI engine, Vector2f loc,
                float dmg, float emp) {
            engine.applyDamage(
                    target, loc, dmg, DamageType.ENERGY, emp,
                    true, // bypass shields
                    false, // hard flux
                    source,
                    false);
            internalExplosion(engine, loc, 22f);
        }

        private Vector2f findNearestLiveComponent(Vector2f origin) {
            Vector2f best = null;
            float bestDist = Float.MAX_VALUE;

            for (WeaponAPI w : target.getAllWeapons()) {
                if (w.isDecorative() || w.isDisabled() || w.isPermanentlyDisabled()) {
                    continue;
                }
                float d = MathUtils.getDistance(w.getLocation(), origin);
                if (d < bestDist) {
                    bestDist = d;
                    best = w.getLocation();
                }
            }
            for (ShipEngineControllerAPI.ShipEngineAPI e
                    : target.getEngineController().getShipEngines()) {
                if (e.isDisabled() || e.isPermanentlyDisabled()) {
                    continue;
                }
                float d = MathUtils.getDistance(e.getLocation(), origin);
                if (d < bestDist) {
                    bestDist = d;
                    best = e.getLocation();
                }
            }
            return best;
        }

        private void internalExplosion(CombatEngineAPI engine, Vector2f loc,
                float size) {
            engine.spawnExplosion(loc, target.getVelocity(),
                    new Color(255, 160, 90, 180), size, 0.4f);
            engine.addSmokeParticle(loc, new Vector2f(target.getVelocity()),
                    size * 1.4f, 0.6f, 1.5f, new Color(70, 70, 70, 160));
            Global.getSoundPlayer().playSound("explosion_from_damage",
                    1f, 0.7f, loc, target.getVelocity());
        }

        private void finish(CombatEngineAPI engine, boolean deployFighter) {
            if (finished) {
                return;
            }
            finished = true;

            // Grab the deploy transform before cleanupPod nulls the pod.
            float facing = (pod != null && engine.isEntityInPlay(pod))
                    ? pod.getFacing() : target.getFacing();
            Vector2f deployLoc = new Vector2f(lastKnownLoc);

            cleanupPod(engine);

            if (deployFighter && !fighterDeployed) {
                fighterDeployed = true;
                deployAssaultPod(deployLoc, facing,
                        target.getVelocity(), true, source);
            }

            if (target != null && engine.isEntityInPlay(target)) {
                updateMalfBump(engine, target);
            }
        }

        private void cleanupPod(CombatEngineAPI engine) {
            if (pod != null && engine.isEntityInPlay(pod)) {
                engine.removeEntity(pod);
            }
            pod = null;
        }
    }

    public static class PodReaper extends BaseEveryFrameCombatPlugin {

        private final IntervalUtil malfSweep = new IntervalUtil(0.5f, 0.5f);

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null || engine.isPaused()) {
                return;
            }

            List<BoardingRecord> boardings = getBoardings(engine);

            // Advance every active board every frame (ticks use IntervalUtil).
            for (Iterator<BoardingRecord> it = boardings.iterator(); it.hasNext();) {
                BoardingRecord rec = it.next();
                rec.advance(engine, amount);
                if (rec.isFinished()) {
                    it.remove();
                }
            }

            // Recompute the passive malf bumps periodically, per live target.
            malfSweep.advance(amount);
            if (malfSweep.intervalElapsed()) {
                // Gather distinct live targets still being boarded.
                List<ShipAPI> seen = new ArrayList<>();
                for (BoardingRecord rec : boardings) {
                    ShipAPI t = rec.getTarget();
                    if (t != null && engine.isEntityInPlay(t)
                            && t.isAlive() && !seen.contains(t)) {
                        seen.add(t);
                        updateMalfBump(engine, t);
                    }
                }
            }
        }
    }

    // -- registries -------------------------------------------------------
    @SuppressWarnings("unchecked")
    private static List<BoardingRecord> getBoardings(CombatEngineAPI engine) {
        Object o = engine.getCustomData().get(BOARDINGS_KEY);
        if (o instanceof List) {
            return (List<BoardingRecord>) o;
        }
        List<BoardingRecord> list = new ArrayList<>();
        engine.getCustomData().put(BOARDINGS_KEY, list);
        return list;
    }

    private static void ensureReaper(CombatEngineAPI engine) {
        if (!engine.getCustomData().containsKey(REAPER_KEY)) {
            engine.getCustomData().put(REAPER_KEY, Boolean.TRUE);
            engine.addPlugin(new PodReaper());
        }
    }

    private static void updateMalfBump(CombatEngineAPI engine, ShipAPI ship) {
        int count = 0;
        for (BoardingRecord rec : getBoardings(engine)) {
            if (rec.getTarget() == ship && !rec.isFinished()
                    && rec.isPodAlive(engine)) {
                count++;
            }
        }

        MutableShipStatsAPI stats = ship.getMutableStats();
        float weaponBump = Math.min(WEAPON_MALF_CAP, count * WEAPON_MALF_BUMP);

        MutableStat.StatMod existing
                = stats.getWeaponMalfunctionChance().getFlatStatMod(MALF_KEY);
        float current = (existing == null) ? 0f : existing.getValue();
        if (weaponBump == current) {
            return; // nothing changed since last sweep
        }

        if (count <= 0) {
            stats.getWeaponMalfunctionChance().unmodify(MALF_KEY);
            stats.getEngineMalfunctionChance().unmodify(MALF_KEY);
        } else {
            float engineBump = Math.min(ENGINE_MALF_CAP, count * ENGINE_MALF_BUMP);
            stats.getWeaponMalfunctionChance().modifyFlat(MALF_KEY, weaponBump);
            stats.getEngineMalfunctionChance().modifyFlat(MALF_KEY, engineBump);
        }
    }

    // -- targeting --------------------------------------------------------
    private boolean isValidTarget(ShipAPI ship) {
        if (ship == null || !ship.isAlive() || ship.isHulk()) {
            return false;
        }
        if (ship.getOwner() == missile.getOwner()) {
            return false;
        }
        if (ship.getHullSize().ordinal() < MIN_BOARD_SIZE.ordinal()) {
            return false;
        }
        if (countBoardings(engine, ship) >= MAX_PODS_PER_TARGET) {
            return false;
        }
        return true;
    }

    private static int countBoardings(CombatEngineAPI engine, ShipAPI ship) {
        if (engine == null) {
            return 0;
        }
        int n = 0;
        for (BoardingRecord rec : getBoardings(engine)) {
            if (rec.getTarget() == ship && !rec.isFinished()) {
                n++;
            }
        }
        return n;
    }

    private ShipAPI acquireTarget() {
        ShipAPI best = null;
        float bestDist = ACQUIRE_RANGE;
        for (ShipAPI ship : AIUtils.getEnemiesOnMap(missile)) {
            if (!isValidTarget(ship)) {
                continue;
            }
            float d = MathUtils.getDistance(missile, ship);
            if (d < bestDist) {
                bestDist = d;
                best = ship;
            }
        }
        return best;
    }

    private Vector2f intercept(MissileAPI m, ShipAPI t) {
        float speed = Math.max(1f, m.getVelocity().length());
        float time = MathUtils.getDistance(m, t) / speed;
        time = Math.min(time, 2f); // don't lead absurdly at long range
        Vector2f lead = new Vector2f(t.getVelocity());
        lead.scale(time);
        return Vector2f.add(t.getLocation(), lead, null);
    }

    @Override
    public CombatEntityAPI getTarget() {
        return target;
    }

    @Override
    public void setTarget(CombatEntityAPI target) {
        if (target instanceof ShipAPI && isValidTarget((ShipAPI) target)) {
            this.target = (ShipAPI) target;
        }
    }
}
