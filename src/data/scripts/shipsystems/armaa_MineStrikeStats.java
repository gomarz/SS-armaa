package data.scripts.shipsystems;

import java.awt.Color;
import java.util.List;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI.SystemState;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.impl.combat.MineStrikeStatsAIInfoProvider;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import java.util.ArrayList;
import java.util.Collections;

public class armaa_MineStrikeStats extends BaseShipSystemScript implements MineStrikeStatsAIInfoProvider {

    protected static float MINE_RANGE = 1000f;
    protected static final int MINE_COUNT = 5;
    protected static final float RING_RADIUS = 100f;   // ~coreRadius, tiles 100su cores
    protected static final float RING_JITTER = 30f;
    public static final float MIN_SPAWN_DIST = 50f;
    public static final float MIN_SPAWN_DIST_FRIGATE = 100f;

    public static final float LIVE_TIME = 5f;

    public static final Color JITTER_COLOR = new Color(155, 255, 255, 75);
    public static final Color JITTER_UNDER_COLOR = new Color(155, 255, 255, 155);

    public static float getRange(ShipAPI ship) {
        if (ship == null) {
            return MINE_RANGE;
        }
        return ship.getMutableStats().getSystemRangeBonus().computeEffective(MINE_RANGE);
    }

    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = null;
        //boolean player = false;
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
        } else {
            return;
        }

        float jitterLevel = effectLevel;
        if (state == State.OUT) {
            jitterLevel *= jitterLevel;
        }
        float maxRangeBonus = 25f;
        float jitterRangeBonus = jitterLevel * maxRangeBonus;
        if (state == State.OUT) {
        }

        ship.setJitterUnder(this, JITTER_UNDER_COLOR, jitterLevel, 11, 0f, 3f + jitterRangeBonus);
        ship.setJitter(this, JITTER_COLOR, jitterLevel, 4, 0f, 0 + jitterRangeBonus);

        if (state == State.IN) {
        } else if (effectLevel >= 1) {
            Vector2f target = ship.getMouseTarget();
            if (ship.getShipAI() != null && ship.getAIFlags().hasFlag(AIFlags.SYSTEM_TARGET_COORDS)) {
                target = (Vector2f) ship.getAIFlags().getCustom(AIFlags.SYSTEM_TARGET_COORDS);
            }
            if (target != null) {
                float dist = Misc.getDistance(ship.getLocation(), target);
                float max = getMaxRange(ship) + ship.getCollisionRadius();
                if (dist > max) {
                    float dir = Misc.getAngleInDegrees(ship.getLocation(), target);
                    target = Misc.getUnitVectorAtDegreeAngle(dir);
                    target.scale(max);
                    Vector2f.add(target, ship.getLocation(), target);
                }

                target = findClearLocation(ship, target);

                if (target != null) {
                    float startAngle = (float) Math.random() * 360f;
                    for (int i = 0; i < MINE_COUNT; i++) {
                        float angle = startAngle + i * (360f / MINE_COUNT)
                                + ((float) Math.random() - 0.5f) * 20f;
                        Vector2f offset = Misc.getUnitVectorAtDegreeAngle(angle);
                        offset.scale(RING_RADIUS + ((float) Math.random() - 0.5f) * 2f * RING_JITTER);
                        Vector2f loc = Vector2f.add(target, offset, new Vector2f());
                        spawnMine(ship, loc);
                    }
                }
            }

        } else if (state == State.OUT) {
        }
    }

    public void unapply(MutableShipStatsAPI stats, String id) {
    }

    public void spawnMine(ShipAPI source, Vector2f mineLoc) {
        CombatEngineAPI engine = Global.getCombatEngine();
        Vector2f currLoc = new Vector2f(mineLoc); // trust the ring position first
        float start = (float) Math.random() * 360f;
        for (float angle = start; angle < start + 390f; angle += 30f) {
            if (angle != start) {
                Vector2f loc = Misc.getUnitVectorAtDegreeAngle(angle);
                loc.scale(60f + (float) Math.random() * 30f); // nudge, not relocate
                currLoc = Vector2f.add(mineLoc, loc, new Vector2f());
            }
            boolean blocked = false;
            for (MissileAPI other : engine.getMissiles()) {
                if (!other.isMine()) {
                    continue;
                }
                if (Misc.getDistance(currLoc, other.getLocation())
                        < other.getCollisionRadius() + 40f) {
                    blocked = true;
                    break;
                }
            }
            if (!blocked) {
                break;
            }
        }

        //Vector2f currLoc = mineLoc;
        MissileAPI mine = (MissileAPI) engine.spawnProjectile(source, null,
                "armaa_mine",
                currLoc,
                (float) Math.random() * 360f, null);
        if (source != null) {
            Global.getCombatEngine().applyDamageModifiersToSpawnedProjectileWithNullWeapon(
                    source, WeaponType.MISSILE, false, mine.getDamage());
//			float extraDamageMult = source.getMutableStats().getMissileWeaponDamageMult().getModifiedValue();
//			mine.getDamage().setMultiplier(mine.getDamage().getMultiplier() * extraDamageMult);
        }

        float fadeInTime = 0.5f;
        mine.getVelocity().scale(0);
        mine.fadeOutThenIn(fadeInTime);

        Global.getCombatEngine().addPlugin(createMissileJitterPlugin(mine, fadeInTime));
        Global.getCombatEngine().addPlugin(createEmpBurstPlugin(mine, source));
        //mine.setFlightTime((float) Math.random());
        float liveTime = LIVE_TIME;
        //liveTime = 0.01f;
        mine.setFlightTime(mine.getMaxFlightTime() - liveTime);

        Global.getSoundPlayer().playSound("mine_teleport", 1f, 1f, mine.getLocation(), mine.getVelocity());
    }

    protected EveryFrameCombatPlugin createMissileJitterPlugin(final MissileAPI mine, final float fadeInTime) {
        return new BaseEveryFrameCombatPlugin() {
            float elapsed = 0f;

            @Override
            public void advance(float amount, List<InputEventAPI> events) {
                if (Global.getCombatEngine().isPaused()) {
                    return;
                }

                elapsed += amount;

                float jitterLevel = mine.getCurrentBaseAlpha();
                if (jitterLevel < 0.5f) {
                    jitterLevel *= 2f;
                } else {
                    jitterLevel = (1f - jitterLevel) * 2f;
                }

                float jitterRange = 1f - mine.getCurrentBaseAlpha();
                //jitterRange = (float) Math.sqrt(jitterRange);
                float maxRangeBonus = 50f;
                float jitterRangeBonus = jitterRange * maxRangeBonus;
                Color c = JITTER_UNDER_COLOR;
                c = Misc.setAlpha(c, 70);
                //mine.setJitter(this, c, jitterLevel, 15, jitterRangeBonus * 0.1f, jitterRangeBonus);
                mine.setJitter(this, c, jitterLevel, 15, jitterRangeBonus * 0, jitterRangeBonus);

                if (jitterLevel >= 1 || elapsed > fadeInTime) {
                    Global.getCombatEngine().removePlugin(this);
                }
            }
        };
    }

    protected float getMaxRange(ShipAPI ship) {
        return getMineRange(ship);
    }

    @Override
    public String getInfoText(ShipSystemAPI system, ShipAPI ship) {
        if (system.isOutOfAmmo()) {
            return null;
        }
        if (system.getState() != SystemState.IDLE) {
            return null;
        }

        Vector2f target = ship.getMouseTarget();
        if (target != null) {
            float dist = Misc.getDistance(ship.getLocation(), target);
            float max = getMaxRange(ship) + ship.getCollisionRadius();
            if (dist > max) {
                return "OUT OF RANGE";
            } else {
                return "READY";
            }
        }
        return null;
    }

    @Override
    public boolean isUsable(ShipSystemAPI system, ShipAPI ship) {
        return ship.getMouseTarget() != null;
    }

    private Vector2f findClearLocation(ShipAPI ship, Vector2f dest) {
        if (isLocationClear(dest)) {
            return dest;
        }

        float incr = 50f;

        WeightedRandomPicker<Vector2f> tested = new WeightedRandomPicker<Vector2f>();
        for (float distIndex = 1; distIndex <= 32f; distIndex *= 2f) {
            float start = (float) Math.random() * 360f;
            for (float angle = start; angle < start + 360; angle += 60f) {
                Vector2f loc = Misc.getUnitVectorAtDegreeAngle(angle);
                loc.scale(incr * distIndex);
                Vector2f.add(dest, loc, loc);
                tested.add(loc);
                if (isLocationClear(loc)) {
                    return loc;
                }
            }
        }

        if (tested.isEmpty()) {
            return dest; // shouldn't happen
        }
        return tested.pick();
    }

    private boolean isLocationClear(Vector2f loc) {
        for (ShipAPI other : Global.getCombatEngine().getShips()) {
            if (other.isShuttlePod()) {
                continue;
            }
            if (other.isFighter()) {
                continue;
            }

//			Vector2f otherLoc = other.getLocation();
//			float otherR = other.getCollisionRadius();
//			if (other.isPiece()) {
//				System.out.println("ewfewfewfwe");
//			}
            Vector2f otherLoc = other.getShieldCenterEvenIfNoShield();
            float otherR = other.getShieldRadiusEvenIfNoShield();
            if (other.isPiece()) {
                otherLoc = other.getLocation();
                otherR = other.getCollisionRadius();
            }

//			float dist = Misc.getDistance(loc, other.getLocation());
//			float r = other.getCollisionRadius();
            float dist = Misc.getDistance(loc, otherLoc);
            float r = otherR;
            //r = Math.min(r, Misc.getTargetingRadius(loc, other, false) + r * 0.25f);
            float checkDist = MIN_SPAWN_DIST + RING_RADIUS + RING_JITTER;

            if (other.isFrigate()) {
                checkDist = MIN_SPAWN_DIST_FRIGATE;
            }
            if (dist < r + checkDist) {
                return false;
            }
        }
        for (CombatEntityAPI other : Global.getCombatEngine().getAsteroids()) {
            float dist = Misc.getDistance(loc, other.getLocation());
            if (dist < other.getCollisionRadius() + MIN_SPAWN_DIST) {
                return false;
            }
        }

        return true;
    }

    public float getFuseTime() {
        return 1f;
    }

    public float getMineRange(ShipAPI ship) {
        return getRange(ship);
        //return MINE_RANGE;
    }

    protected static final int EMP_ARCS_MAX = 3;
    protected static final float EMP_ARC_RANGE = 200f;
    protected static final float EMP_PER_ARC = 200f;
    protected static final float DMG_PER_ARC = 50f;
    protected static final Color ARC_FRINGE = new Color(0, 100, 255, 255);
    protected static final Color ARC_CORE = new Color(160, 220, 255, 255);

    protected EveryFrameCombatPlugin createEmpBurstPlugin(final MissileAPI mine, final ShipAPI source) {
        return new BaseEveryFrameCombatPlugin() {
            final Vector2f lastLoc = new Vector2f(mine.getLocation());

            @Override
            public void advance(float amount, List<InputEventAPI> events) {
                CombatEngineAPI engine = Global.getCombatEngine();
                if (engine.isPaused()) {
                    return;
                }

                if (engine.isEntityInPlay(mine)) {
                    lastLoc.set(mine.getLocation());
                    return;
                }

                engine.removePlugin(this);

                if (mine.getHitpoints() <= 0f) {
                    return;                       // shot down by PD
                }
                if (mine.getFlightTime() >= mine.getMaxFlightTime()) {
                    return; // expired unspent
                }
                // it detonated
                List<CombatEntityAPI> candidates = new ArrayList<>();
                for (ShipAPI s : engine.getShips()) {
                    if (s.getOwner() == mine.getOwner() || !s.isAlive() || s.isHulk()) {
                        continue;
                    }
                    if (Misc.getDistance(lastLoc, s.getLocation())
                            < EMP_ARC_RANGE + s.getCollisionRadius()) {
                        candidates.add(s);
                    }
                }
                for (MissileAPI m : engine.getMissiles()) {
                    if (m.getOwner() == mine.getOwner() || m.isMine() || m.isFizzling()) {
                        continue;
                    }
                    if (Misc.getDistance(lastLoc, m.getLocation()) < EMP_ARC_RANGE) {
                        candidates.add(m);
                    }
                }
                if (candidates.isEmpty()) {
                    return;
                }

                Collections.shuffle(candidates);
                int arcs = Math.min(EMP_ARCS_MAX, candidates.size());
                for (int i = 0; i < arcs; i++) {
                    engine.spawnEmpArc(source, lastLoc, null, candidates.get(i),
                            DamageType.ENERGY,
                            DMG_PER_ARC,
                            EMP_PER_ARC,
                            EMP_ARC_RANGE,
                            "tachyon_lance_emp_impact",
                            5f,
                            ARC_FRINGE,
                            ARC_CORE);
                }
            }
        };
    }

}
