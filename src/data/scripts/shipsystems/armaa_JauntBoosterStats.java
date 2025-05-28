package data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipEngineControllerAPI.ShipEngineAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import data.scripts.util.armaa_utils;
import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.lazywizard.lazylib.MathUtils;
import org.magiclib.util.MagicLensFlare;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.entities.AnchoredEntity;
import org.lwjgl.util.vector.Vector2f;

public class armaa_JauntBoosterStats extends BaseShipSystemScript {

    public static final float MAX_TURN_BONUS = 50f;
    public static final float TURN_ACCEL_BONUS = 50f;
    public static final float INSTANT_BOOST_FLAT = 300f;
    public static final float INSTANT_BOOST_MULT = 5f;
    public static final Map<HullSize, Float> SPEED_FALLOFF_PER_SEC = new HashMap<>();
    public static final Map<HullSize, Float> BASELINE_MULT = new HashMap<>();
    public static final Map<HullSize, Float> FORWARD_PENALTY = new HashMap<>(); // from baseline mult
    public static final Map<HullSize, Float> REVERSE_PENALTY = new HashMap<>(); // from baseline mult
    public static final Map<HullSize, Float> IN_OVERRIDE = new HashMap<>();
    public static final Map<HullSize, Float> ACTIVE_OVERRIDE = new HashMap<>();
    public static final Map<HullSize, Float> OUT_OVERRIDE = new HashMap<>();
    public static final Map<HullSize, Integer> USES_OVERRIDE = new HashMap<>();
    public static final Map<HullSize, Float> REGEN_OVERRIDE = new HashMap<>();
    public static final float ARMOR_ENGINE_RESIST_MULT = 0.25f;
    public static final float TARGETING_TIME_MULT = 3f;
    public static final float TARGETING_FLUX_PER_USE = 0.1f;
    public static final int ELITE_CHARGE_MULT = 2;
    public static final float ELITE_COOLDOWN_MULT = 0f;
    public static final float FIGHTER_COOLDOWN_MULT = 0.5f;
    public static final Map<HullSize, Float> ELITE_REGEN_MULT = new HashMap<>();

    private static final Map<HullSize, Float> EXTEND_TIME = new HashMap<>();
    private static final Map<HullSize, Float> MAX_FRAC_OUT = new HashMap<>();
    private static final Map<HullSize, Float> BOOST_MULT = new HashMap<>();

    static {
        SPEED_FALLOFF_PER_SEC.put(HullSize.FIGHTER, 0.75f); // base boost distance 386-580, time 2.07-3.15 (carnifex 175 speed, 175 accel, 115 decel)
        SPEED_FALLOFF_PER_SEC.put(HullSize.FRIGATE, 0.55f); // base boost distance 438-577, time 2.67-3.65 (decurion 135 speed, 110 accel, 80 decel)
        SPEED_FALLOFF_PER_SEC.put(HullSize.DESTROYER, 0.45f); // base boost distance 465-528, time 3.47-4.12 (interrex 80 speed, 60 accel, 50 decel)
        SPEED_FALLOFF_PER_SEC.put(HullSize.CRUISER, 0.25f); // base boost distance 361-420, time 3.32-4.30 (sebastos 55 speed, 40 accel, 30 decel)
        SPEED_FALLOFF_PER_SEC.put(HullSize.CAPITAL_SHIP, 0.225f); // base boost distance 357-413, time 4.55-5.92 (barrus 40 speed, 20 accel, 15 decel)

        BASELINE_MULT.put(HullSize.FIGHTER, 0.975f);
        BASELINE_MULT.put(HullSize.FRIGATE, 0.85f);
        BASELINE_MULT.put(HullSize.DESTROYER, 0.80f);
        BASELINE_MULT.put(HullSize.CRUISER, 0.75f);
        BASELINE_MULT.put(HullSize.CAPITAL_SHIP, 0.75f);

        FORWARD_PENALTY.put(HullSize.FIGHTER, 0.675f); // boost distance 263-393, time 1.70-2.58
        FORWARD_PENALTY.put(HullSize.FRIGATE, 0.35f); // boost distance 305-397, time 2.20-2.98
        FORWARD_PENALTY.put(HullSize.DESTROYER, 0.3f); // boost distance 214-238, time 2.18-2.57
        FORWARD_PENALTY.put(HullSize.CRUISER, 0.2f); // boost distance 140-153, time 1.67-2.10
        FORWARD_PENALTY.put(HullSize.CAPITAL_SHIP, 0.2f); // boost distance 126-135, time 2.02-2.53

        REVERSE_PENALTY.put(HullSize.FIGHTER, 0.725f); // boost distance 181-269, time 1.40-2.13
        REVERSE_PENALTY.put(HullSize.FRIGATE, 0.45f); // boost distance 168-215, time 1.58-2.13
        REVERSE_PENALTY.put(HullSize.DESTROYER, 0.4f); // boost distance 110-118, time 1.38-1.60
        REVERSE_PENALTY.put(HullSize.CRUISER, 0.35f); // boost distance 85-89, time 1.05-1.28
        REVERSE_PENALTY.put(HullSize.CAPITAL_SHIP, 0.4f); // boost distance 64-65, time 0.97-1.03

        IN_OVERRIDE.put(HullSize.FIGHTER, 0.2f);
        IN_OVERRIDE.put(HullSize.FRIGATE, 0.1f);
        IN_OVERRIDE.put(HullSize.DESTROYER, 0.1f);
        IN_OVERRIDE.put(HullSize.CRUISER, 0.2f);
        IN_OVERRIDE.put(HullSize.CAPITAL_SHIP, 0.2f);

        ACTIVE_OVERRIDE.put(HullSize.FIGHTER, 0.2f);
        ACTIVE_OVERRIDE.put(HullSize.FRIGATE, 0.3f);
        ACTIVE_OVERRIDE.put(HullSize.DESTROYER, 0.3f);
        ACTIVE_OVERRIDE.put(HullSize.CRUISER, 0.2f);
        ACTIVE_OVERRIDE.put(HullSize.CAPITAL_SHIP, 0.2f);

        OUT_OVERRIDE.put(HullSize.FIGHTER, 0.4f);
        OUT_OVERRIDE.put(HullSize.FRIGATE, 0.6f);
        OUT_OVERRIDE.put(HullSize.DESTROYER, 0.8f);
        OUT_OVERRIDE.put(HullSize.CRUISER, 0.9f);
        OUT_OVERRIDE.put(HullSize.CAPITAL_SHIP, 1f);

        USES_OVERRIDE.put(HullSize.FIGHTER, 3);
        USES_OVERRIDE.put(HullSize.FRIGATE, 4);
        USES_OVERRIDE.put(HullSize.DESTROYER, 4);
        USES_OVERRIDE.put(HullSize.CRUISER, 3);
        USES_OVERRIDE.put(HullSize.CAPITAL_SHIP, 3);

        REGEN_OVERRIDE.put(HullSize.FIGHTER, 0.2f);
        REGEN_OVERRIDE.put(HullSize.FRIGATE, 0.2f);
        REGEN_OVERRIDE.put(HullSize.DESTROYER, 0.2f);
        REGEN_OVERRIDE.put(HullSize.CRUISER, 0.1f);
        REGEN_OVERRIDE.put(HullSize.CAPITAL_SHIP, 0.075f);

        ELITE_REGEN_MULT.put(HullSize.FIGHTER, 1f);
        ELITE_REGEN_MULT.put(HullSize.FRIGATE, 1.5f);
        ELITE_REGEN_MULT.put(HullSize.DESTROYER, 1.6f);
        ELITE_REGEN_MULT.put(HullSize.CRUISER, 1.75f);
        ELITE_REGEN_MULT.put(HullSize.CAPITAL_SHIP, 2f);

        EXTEND_TIME.put(HullSize.FIGHTER, 0.1f);
        EXTEND_TIME.put(HullSize.FRIGATE, 0.1f);
        EXTEND_TIME.put(HullSize.DESTROYER, 0.1f);
        EXTEND_TIME.put(HullSize.CRUISER, 0.1f);
        EXTEND_TIME.put(HullSize.CAPITAL_SHIP, 0.1f);

        MAX_FRAC_OUT.put(HullSize.FIGHTER, 0.15f / OUT_OVERRIDE.get(HullSize.FIGHTER));
        MAX_FRAC_OUT.put(HullSize.FRIGATE, 0.15f / OUT_OVERRIDE.get(HullSize.FRIGATE));
        MAX_FRAC_OUT.put(HullSize.DESTROYER, 0.15f / OUT_OVERRIDE.get(HullSize.DESTROYER));
        MAX_FRAC_OUT.put(HullSize.CRUISER, 0.15f / OUT_OVERRIDE.get(HullSize.CRUISER));
        MAX_FRAC_OUT.put(HullSize.CAPITAL_SHIP, 0.15f / OUT_OVERRIDE.get(HullSize.CAPITAL_SHIP));

        BOOST_MULT.put(HullSize.FIGHTER, 0.5f);
        BOOST_MULT.put(HullSize.FRIGATE, 1f);
        BOOST_MULT.put(HullSize.DESTROYER, 2f);
        BOOST_MULT.put(HullSize.CRUISER, 3f);
        BOOST_MULT.put(HullSize.CAPITAL_SHIP, 4f);
    }

    private Color ENGINE_COLOR_STANDARD = new Color(255, 145, 75);
    private static final Color CONTRAIL_COLOR_STANDARD = new Color(100, 100, 100, 25);
    private static final Color BOOST_COLOR_STANDARD = new Color(255, 175, 175, 200);

    private static final Vector2f ZERO = new Vector2f();
    private final Object STATUSKEY1 = new Object();
    private final Object ENGINEKEY1 = new Object();
    private final Object ENGINEKEY2 = new Object();

    private final Map<Integer, Float> engState = new HashMap<>();

    private boolean started = false;
    private boolean ended = false;
    private final IntervalUtil interval = new IntervalUtil(0.015f, 0.015f);
    private float boostScale = 0.75f;
    private float boostVisualDir = 0f;
    private boolean boostForward = false;

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null) {
            return;
        }
		ENGINE_COLOR_STANDARD = ship.getEngineController().getShipEngines().get(0).getEngineColor();
        float shipRadius = armaa_utils.effectiveRadius(ship);
        float amount = Global.getCombatEngine().getElapsedInLastFrame();
        float objectiveAmount = amount * Global.getCombatEngine().getTimeMult().getModifiedValue();
        if (Global.getCombatEngine().isPaused()) {
            amount = 0f;
            objectiveAmount = 0f;
        }

        Color ENGINE_COLOR = ENGINE_COLOR_STANDARD;
        Color CONTRAIL_COLOR = CONTRAIL_COLOR_STANDARD;
        Color BOOST_COLOR = BOOST_COLOR_STANDARD;
        float afterimageIntensity = 1f;

        ship.getEngineController().fadeToOtherColor(ENGINEKEY1, ENGINE_COLOR, CONTRAIL_COLOR, effectLevel, 1f);
        ship.getEngineController().extendFlame(ENGINEKEY2, 0f, 1f * effectLevel, 3f * effectLevel);

        if (!ended) {
            /* Unweighted direction calculation for visual purposes - 0 degrees is forward */
            Vector2f direction = new Vector2f();
            if (ship.getEngineController().isAccelerating()) {
                direction.y += 1f;
            } else if (ship.getEngineController().isAcceleratingBackwards() || ship.getEngineController().isDecelerating()) {
                direction.y -= 1f;
            }
            if (ship.getEngineController().isStrafingLeft()) {
                direction.x -= 1f;
            } else if (ship.getEngineController().isStrafingRight()) {
                direction.x += 1f;
            }
            if (direction.length() <= 0f) {
                direction.y = 1f;
            }
            boostVisualDir = MathUtils.clampAngle(VectorUtils.getFacing(direction) - 90f);
        }

        if (state == State.IN) {
            if (!started) {
                if (ship.isFighter()) {
                    Global.getSoundPlayer().playSound("system_emp_emitter_activate", 0.78f, 0.6f, ship.getLocation(), ZERO);
                } else {
                    Global.getSoundPlayer().playSound("system_emp_emitter_activate", 0.8f, 0.6f, ship.getLocation(), ZERO);
                }

                started = true;
            }

            List<ShipEngineAPI> engList = ship.getEngineController().getShipEngines();
            for (int i = 0; i < engList.size(); i++) {
                ShipEngineAPI eng = engList.get(i);
                if (eng.isSystemActivated()) {
                    float targetLevel = getSystemEngineScale(eng, boostVisualDir) * 0.4f;
                    Float currLevel = engState.get(i);
                    if (currLevel == null) {
                        currLevel = 0f;
                    }
                    if (currLevel > targetLevel) {
                        currLevel = Math.max(targetLevel, currLevel - (objectiveAmount / EXTEND_TIME.get(ship.getHullSize())));
                    } else {
                        currLevel = Math.min(targetLevel, currLevel + (objectiveAmount / EXTEND_TIME.get(ship.getHullSize())));
                    }
                    engState.put(i, currLevel);
                    ship.getEngineController().setFlameLevel(eng.getEngineSlot(), currLevel);
                }
            }
        }

        if (state == State.OUT) {
            /* Black magic to counteract the effects of maneuvering penalties/bonuses on the effectiveness of this system */
            float decelMult = Math.max(0.5f, Math.min(2f, stats.getDeceleration().getModifiedValue() / stats.getDeceleration().getBaseValue()));
            float adjFalloffPerSec = SPEED_FALLOFF_PER_SEC.get(ship.getHullSize()) * (float) Math.pow(decelMult, 0.5);
            float maxDecelPenalty = 1f / decelMult;

            stats.getMaxTurnRate().unmodify(id);
            stats.getDeceleration().modifyMult(id, armaa_utils.lerp(1f, maxDecelPenalty, effectLevel));
            stats.getTurnAcceleration().modifyPercent(id, TURN_ACCEL_BONUS * effectLevel);

            if (boostForward) {
                ship.giveCommand(ShipCommand.ACCELERATE, null, 0);
                ship.blockCommandForOneFrame(ShipCommand.ACCELERATE_BACKWARDS);
                ship.blockCommandForOneFrame(ShipCommand.DECELERATE);
            } else {
                ship.blockCommandForOneFrame(ShipCommand.ACCELERATE);
            }

            /* Quickly unapply the instant repair buff */
            stats.getCombatEngineRepairTimeMult().unmodify(id);

            if (objectiveAmount > 0f) {
                ship.getVelocity().scale((float) Math.pow(adjFalloffPerSec, objectiveAmount * stats.getTimeMult().getModifiedValue()));
            }

            interval.advance(amount);
            if (interval.intervalElapsed()) {
                float randRange = (float) Math.sqrt(shipRadius) * 0.5f * afterimageIntensity * boostScale;
                Vector2f randLoc = MathUtils.getRandomPointInCircle(ZERO, randRange);
                Color afterimageColor = new Color(CONTRAIL_COLOR.getRed(), CONTRAIL_COLOR.getGreen(), CONTRAIL_COLOR.getBlue(),
                        armaa_utils.clamp255(Math.round(0.15f * afterimageIntensity * CONTRAIL_COLOR.getAlpha())));
                ship.addAfterimage(afterimageColor, randLoc.x, randLoc.y, -ship.getVelocity().x, -ship.getVelocity().y,
                        randRange, 0f, 0.1f, 0.5f, true, false, false);
						
            }

            List<ShipEngineAPI> engList = ship.getEngineController().getShipEngines();
            for (int i = 0; i < engList.size(); i++) {
                ShipEngineAPI eng = engList.get(i);
                if (eng.isSystemActivated()) {
                    float targetLevel = getSystemEngineScale(eng, boostVisualDir) * effectLevel;
                    if (targetLevel >= (1f - MAX_FRAC_OUT.get(ship.getHullSize()))) {
                        targetLevel = 1f;
                    } else {
                        targetLevel = targetLevel / (1f - MAX_FRAC_OUT.get(ship.getHullSize()));
                    }
                    engState.put(i, targetLevel);
                    ship.getEngineController().setFlameLevel(eng.getEngineSlot(), targetLevel);
                }
            }
        } else if (state == State.ACTIVE) {
            stats.getMaxTurnRate().modifyPercent(id, MAX_TURN_BONUS);
            stats.getTurnAcceleration().modifyPercent(id, TURN_ACCEL_BONUS * effectLevel);

            ship.getEngineController().getExtendLengthFraction().advance(objectiveAmount * 2f);
            ship.getEngineController().getExtendWidthFraction().advance(objectiveAmount * 2f);
            ship.getEngineController().getExtendGlowFraction().advance(objectiveAmount * 2f);
            List<ShipEngineAPI> engList = ship.getEngineController().getShipEngines();
            for (int i = 0; i < engList.size(); i++) {
                ShipEngineAPI eng = engList.get(i);
                if (eng.isSystemActivated()) {
                    float targetLevel = getSystemEngineScale(eng, boostVisualDir);
                    Float currLevel = engState.get(i);
                    if (currLevel == null) {
                        currLevel = 0f;
                    }
                    if (currLevel > targetLevel) {
                        currLevel = Math.max(targetLevel, currLevel - (objectiveAmount / EXTEND_TIME.get(ship.getHullSize())));
                    } else {
                        currLevel = Math.min(targetLevel, currLevel + (objectiveAmount / EXTEND_TIME.get(ship.getHullSize())));
                    }
                    engState.put(i, currLevel);
                    ship.getEngineController().setFlameLevel(eng.getEngineSlot(), currLevel);
                }
            }
        }

        if (state == State.OUT) {
            if (!ended) {
                Vector2f direction = new Vector2f();
                boostForward = false;
                boostScale = BASELINE_MULT.get(ship.getHullSize());
                if (ship.getEngineController().isAccelerating()) {
                    direction.y += BASELINE_MULT.get(ship.getHullSize()) - FORWARD_PENALTY.get(ship.getHullSize());
                    boostScale -= FORWARD_PENALTY.get(ship.getHullSize());
                    boostForward = true;
                } else if (ship.getEngineController().isAcceleratingBackwards() || ship.getEngineController().isDecelerating()) {
                    direction.y -= BASELINE_MULT.get(ship.getHullSize()) - REVERSE_PENALTY.get(ship.getHullSize());
                    boostScale -= REVERSE_PENALTY.get(ship.getHullSize());
                }
                if (ship.getEngineController().isStrafingLeft()) {
                    direction.x -= 1f;
                    boostScale += 1f - BASELINE_MULT.get(ship.getHullSize());
                    boostForward = false;
                } else if (ship.getEngineController().isStrafingRight()) {
                    direction.x += 1f;
                    boostScale += 1f - BASELINE_MULT.get(ship.getHullSize());
                    boostForward = false;
                }
                if (direction.length() <= 0f) {
                    direction.y = BASELINE_MULT.get(ship.getHullSize()) - FORWARD_PENALTY.get(ship.getHullSize());
                    boostScale -= FORWARD_PENALTY.get(ship.getHullSize());
                }
                Misc.normalise(direction);
                VectorUtils.rotate(direction, ship.getFacing() - 90f, direction);
                direction.scale(((ship.getMaxSpeedWithoutBoost() * INSTANT_BOOST_MULT) + INSTANT_BOOST_FLAT) * boostScale);
                Vector2f.add(ship.getVelocity(), direction, ship.getVelocity());
                ended = true;

                float duration = (float) Math.sqrt(shipRadius) / 25f;
                ship.getEngineController().getExtendLengthFraction().advance(1f);
                ship.getEngineController().getExtendWidthFraction().advance(1f);
                ship.getEngineController().getExtendGlowFraction().advance(1f);
                for (ShipEngineAPI eng : ship.getEngineController().getShipEngines()) {
                    float level = 1f;
                    if (eng.isSystemActivated()) {
                        level = getSystemEngineScale(eng, boostVisualDir);
                    }
                    if ((eng.isActive() || eng.isSystemActivated()) && (level > 0f)) {
                        Color bigBoostColor = new Color(
                                armaa_utils.clamp255(Math.round(0.1f * ENGINE_COLOR.getRed())),
                                armaa_utils.clamp255(Math.round(0.1f * ENGINE_COLOR.getGreen())),
                                armaa_utils.clamp255(Math.round(0.1f * ENGINE_COLOR.getBlue())),
                                armaa_utils.clamp255(Math.round(0.3f * ENGINE_COLOR.getAlpha() * level)));
                        Color boostColor = new Color(BOOST_COLOR.getRed(), BOOST_COLOR.getGreen(), BOOST_COLOR.getBlue(),
                                armaa_utils.clamp255(Math.round(BOOST_COLOR.getAlpha() * level)));
                        Global.getCombatEngine().spawnExplosion(eng.getLocation(), ZERO, bigBoostColor,
                                BOOST_MULT.get(ship.getHullSize()) * 4f * boostScale * eng.getEngineSlot().getWidth(), duration);
                        Global.getCombatEngine().spawnExplosion(eng.getLocation(), ZERO, boostColor,
                                BOOST_MULT.get(ship.getHullSize()) * 2f * boostScale * eng.getEngineSlot().getWidth(), 0.15f);
								
						float VEL_MIN = 0.2f;
						// constant that effects the upper end of the particle velocity
						float CONE_ANGLE = 200f;
						float A_2 = CONE_ANGLE / 2;
						float VEL_MAX = 0.4f;		
						float speed = 450f;
						float facing = ship.getFacing();
						float angle = MathUtils.getRandomNumberInRange(facing - A_2,
								facing + A_2);
						float vel = MathUtils.getRandomNumberInRange(speed * -VEL_MIN,
								speed * -VEL_MAX);
						Vector2f vector = MathUtils.getPointOnCircumference(null,
								vel,
								angle);						
						Vector2f origin = new Vector2f(eng.getLocation());
						Vector2f offset = new Vector2f(-5f, -0f);
						VectorUtils.rotate(offset, ship.getFacing(), offset);
						Vector2f.add(offset, origin, origin);								

					   Global.getCombatEngine().addHitParticle(
								origin,
								vector,
								MathUtils.getRandomNumberInRange(2, 5),
								1f*effectLevel,
								MathUtils.getRandomNumberInRange(0.4f, 0.8f),
								BOOST_COLOR);
                    }
                }

                float soundScale = (float) Math.sqrt(boostScale);
                switch (ship.getHullSize()) {
                    case FIGHTER:
                        Global.getSoundPlayer().playSound("mechmove", 1.1f, 0.6f * soundScale, ship.getLocation(), ZERO);
                        break;
                    case FRIGATE:
                        Global.getSoundPlayer().playSound("mechmove", .8f, 1.1f * soundScale, ship.getLocation(), ZERO);
                        break;
                    default:
                    case DESTROYER:
                        Global.getSoundPlayer().playSound("mechmove", 0.9f, 1.1f * soundScale, ship.getLocation(), ZERO);
                        break;
                    case CRUISER:
                        Global.getSoundPlayer().playSound("mechmove", 0.8f, 1.2f * soundScale, ship.getLocation(), ZERO);
                        break;
                    case CAPITAL_SHIP:
                        Global.getSoundPlayer().playSound("mechmove", 0.7f, 1.3f * soundScale, ship.getLocation(), ZERO);
                        break;
                }
            }
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null) {
            return;
        }

        started = false;
        ended = false;
        boostScale = 0.75f;
        boostVisualDir = 0f;
        boostForward = false;
        engState.clear();

        stats.getCombatEngineRepairTimeMult().unmodify(id);
        stats.getEngineDamageTakenMult().unmodify(id);

        stats.getTimeMult().unmodify(id);
        String globalId = id + "_" + ship.getId();
        Global.getCombatEngine().getTimeMult().unmodify(globalId);

        stats.getMaxTurnRate().unmodify(id);
        stats.getDeceleration().unmodify(id);
        stats.getTurnAcceleration().unmodify(id);

        ship.setJitter(this, ENGINE_COLOR_STANDARD, 0f, 5, 0f, 13f);
        ship.setJitterUnder(this, ENGINE_COLOR_STANDARD, 0f, 25, 0f, 17f);
    }

    @Override
    public boolean isUsable(ShipSystemAPI system, ShipAPI ship) {
        if ((ship != null) && (system != null) && ship.isFighter()) {
            system.setCooldown(1f * FIGHTER_COOLDOWN_MULT);
        }
        return true;
    }

    @Override
    public String getInfoText(ShipSystemAPI system, ShipAPI ship) {
        if (ship != null) {
			if (ship.getEngineController().isFlamedOut()) {
                return "FLAMED OUT";
            }
        }
        return null;
    }

    @Override
    public float getInOverride(ShipAPI ship) {
        if (ship != null) {
            return IN_OVERRIDE.get(ship.getHullSize());
        }
        return -1;
    }

    @Override
    public float getActiveOverride(ShipAPI ship) {
        if (ship != null) {
            return ACTIVE_OVERRIDE.get(ship.getHullSize());
        }
        return -1;
    }

    @Override
    public float getOutOverride(ShipAPI ship) {
        if (ship != null) {
            return OUT_OVERRIDE.get(ship.getHullSize());
        }
        return -1;
    }

    @Override
    public int getUsesOverride(ShipAPI ship) {
        if (ship != null) 
                return USES_OVERRIDE.get(ship.getHullSize());
        return -1;
    }

    @Override
    public float getRegenOverride(ShipAPI ship) {
        if (ship != null) {
                return REGEN_OVERRIDE.get(ship.getHullSize());
        }
        return -1;
    }

    private static float getSystemEngineScale(ShipEngineAPI engine, float direction) {
        float engAngle = engine.getEngineSlot().getAngle();
        if (Math.abs(MathUtils.getShortestRotation(engAngle, direction)) > 100f) {
            return 1f;
        } else {
            return 0f;
        }
    }
}
