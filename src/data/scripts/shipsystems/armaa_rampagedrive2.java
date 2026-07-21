package data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import org.lazywizard.lazylib.FastTrig;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import org.magiclib.util.MagicFakeBeam;
import static org.magiclib.util.MagicFakeBeam.getShipCollisionPoint;
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier;
import com.fs.starfarer.api.loading.ProjectileSpawnType;
import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import org.magiclib.util.MagicRender;
import java.util.Map;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.input.InputEventAPI;

public class armaa_rampagedrive2 extends BaseShipSystemScript {

    //massy private static Map mass_mult = new HashMap();
    public static Map bugs = new HashMap();
    private static Map wide = new HashMap();
    private static Map damage = new HashMap();
    private static Map SPEED_BOOST = new HashMap();
    private static Map DAMAGE_MULT = new HashMap();
    private static float AFTERIMAGE_THRESHOLD = 0.09f;
    private static final float CONE_ANGLE = 150f;
    // one half of the angle. used internally, don't mess with thos
    private static final float A_2 = CONE_ANGLE / 2;
    private static final Color MUZZLE_FLASH_COLOR = new Color(250, 146, 0, 255);
    private static final Color MUZZLE_FLASH_COLOR_ALT = new Color(255, 255, 255, 100);
    private static final Color MUZZLE_FLASH_COLOR_GLOW = new Color(255, 0, 0, 100);
    private static final float MUZZLE_FLASH_DURATION = 1f;
    private static final float MUZZLE_FLASH_SIZE = 50.0f;

    static {
        bugs.put(HullSize.FIGHTER, 80f);
        bugs.put(HullSize.FRIGATE, 80f);
        bugs.put(HullSize.DESTROYER, 100f);
        bugs.put(HullSize.CRUISER, 160f);
        bugs.put(HullSize.CAPITAL_SHIP, 305f);
        wide.put(HullSize.FIGHTER, 40f);
        wide.put(HullSize.FRIGATE, 80f);
        wide.put(HullSize.DESTROYER, 136f);
        wide.put(HullSize.CRUISER, 177f);
        wide.put(HullSize.CAPITAL_SHIP, 201f);
        damage.put(HullSize.FIGHTER, 10f);
        damage.put(HullSize.FRIGATE, 500f);
        damage.put(HullSize.DESTROYER, 1500f);
        damage.put(HullSize.CRUISER, 2500f);
        damage.put(HullSize.CAPITAL_SHIP, 3500f);
        SPEED_BOOST.put(HullSize.FIGHTER, 100f);
        SPEED_BOOST.put(HullSize.FRIGATE, 200f);
        SPEED_BOOST.put(HullSize.DESTROYER, 275f);
        SPEED_BOOST.put(HullSize.CRUISER, 300f);
        SPEED_BOOST.put(HullSize.CAPITAL_SHIP, 300f);
        DAMAGE_MULT.put(HullSize.FIGHTER, 0.50f);
        DAMAGE_MULT.put(HullSize.FRIGATE, 0.33f);
        DAMAGE_MULT.put(HullSize.DESTROYER, 0.33f);
        DAMAGE_MULT.put(HullSize.CRUISER, 0.5f);
        DAMAGE_MULT.put(HullSize.CAPITAL_SHIP, 0.5f);
    }
    private static final Color color = new Color(255, 135, 240, 0);
    public static final float MASS_MULT = 1.2f;
    //public static final float RANGE = 600f;
    public static final float ROF_MULT = 0.5f;
    private static String poopystinky = "Engines and Armor boosted";
    private static String poopystinky2 = "Reduced weapons rate of fire";
    private static String poopystinky3 = "READY";

    private boolean reset = true;
    //private float activeTime = 0f;
    //private float jitterLevel;
    private boolean DidRam = false;

    private Float mass = null;

    // === MID-DASH BAILOUT TUNABLES ==========================================
    // While a dash is active (AI ships only), re-scan nearby enemy threat and
    // cut the dash short via deactivate(). Attack dashes bail when threat is too
    // high; escape dashes end once threat drops low enough. The gap between the
    // two thresholds is hysteresis so the AI's re-dash logic doesn't flap.
    private final IntervalUtil bailoutCheck = new IntervalUtil(0.1f, 0.1f);
    private static final float BAILOUT_SCAN_RADIUS = 1400f; // threat scan radius while dashing
    private static final float BAILOUT_MAX_THREAT  = 7.0f;  // attack dash bails if threat > this
    private static final float ESCAPE_SAFE_THREAT  = 2.5f;  // escape dash ends once threat < this
    // ========================================================================

    public static class TargetData {

        public ShipAPI ship;
        public ShipAPI target;
        public EveryFrameCombatPlugin targetEffectPlugin;
        public float currDamMult;
        public float elaspedAfterInState;

        public TargetData(ShipAPI ship, ShipAPI target) {
            this.ship = ship;
            this.target = target;
        }
    }

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        CombatEngineAPI engine = Global.getCombatEngine();
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (engine.isPaused() || ship == null) {
            return;
        }

        if (mass == null) {
            mass = ship.getMass();
        }

        if (reset) {
            reset = false;
            //activeTime = 0f;
            //jitterLevel = 0f;
            DidRam = false;
        }
        if (!ship.hasListenerOfClass(RampageDriveListener.class)) {
            ship.addListener(new RampageDriveListener());
        }

        ShipAPI target = ship.getShipTarget();
        for (ShipAPI module : ship.getChildModulesCopy()) {
            module.setJitter(new Object(), new Color(255, 165, 90, 155), effectLevel, 2, 0, 5);
            module.setJitterUnder(new Object(), new Color(155, 25, 3, 55), effectLevel, 25, 0, 7);
        }
        float turnrate = ship.getMaxTurnRate() * 1.6f;
        if (ship.getShield() != null && ship.getShield().isOn()) {
            ship.getShield().toggleOff();
        }
        if (target != null) {
            final String targetDataKey = ship.getId() + "_execution_target_data";
            Object targetDataObj = Global.getCombatEngine().getCustomData().get(targetDataKey);
            if (state == State.IN && targetDataObj == null) {
                Global.getCombatEngine().getCustomData().put(targetDataKey, new TargetData(ship, target));
                if (target != null) {
                    if (target.getFluxTracker().showFloaty()
                            || ship == Global.getCombatEngine().getPlayerShip()
                            || target == Global.getCombatEngine().getPlayerShip()) {
                        target.getFluxTracker().showOverloadFloatyIfNeeded("Marked!", Color.WHITE, 4f, true);
                    }
                }
            } else if (state == State.IDLE && targetDataObj != null) {
                Global.getCombatEngine().getCustomData().remove(targetDataKey);
                ((TargetData) targetDataObj).currDamMult = 1f;
                targetDataObj = null;
            }
            if (targetDataObj != null && ((TargetData) targetDataObj).target != null && ((TargetData) targetDataObj).target.isAlive()) {
                final TargetData targetData = (TargetData) targetDataObj;
                float scale = 1f;
                targetData.currDamMult = 1f + (0.25f - 1f) * effectLevel;
                if (targetData.targetEffectPlugin == null) {
                    targetData.targetEffectPlugin = new BaseEveryFrameCombatPlugin() {
                        boolean hasSprite = false;
                        IntervalUtil interval = new IntervalUtil(1.5f, 1.5f);

                        @Override
                        public void advance(float amount, List<InputEventAPI> events) {
                            if (Global.getCombatEngine().isPaused()) {
                                return;
                            }

                            if (targetData.target == Global.getCombatEngine().getPlayerShip()) {
                                Global.getCombatEngine().maintainStatusForPlayerShip("targeted",
                                        targetData.ship.getSystem().getSpecAPI().getIconSpriteName(),
                                        targetData.ship.getSystem().getDisplayName(),
                                        "" + (int) ((targetData.currDamMult - 1f) * 100f) + "% more damage taken", true);
                            }

                            interval.advance(amount);
                            SpriteAPI waveSprite = Global.getSettings().getSprite("ceylon", "armaa_ceylontarget");

                            //why the FUCK is this not working?
                            //fallback to plan B: copy IS
                            if (waveSprite != null) {
                                float radius = targetData.target.getCollisionRadius() * 3;
                                if (interval.intervalElapsed()) {
                                    MagicRender.objectspace(
                                            waveSprite,
                                            targetData.target,
                                            new Vector2f(),
                                            new Vector2f(),
                                            new Vector2f(radius, radius),
                                            new Vector2f(-100f, -100f),
                                            0f,
                                            25f,
                                            true,
                                            new Color(215, 21, 16, 255),
                                            true,
                                            .2f,
                                            1f,
                                            .3f,
                                            true
                                    );
                                }
                            }

                            if (!targetData.ship.isAlive() || !targetData.ship.getSystem().isActive() || !targetData.target.isAlive() || targetData.target.isHulk()) {
                                if (!targetData.target.isAlive() || targetData.target.isHulk()) {
                                    targetData.target.getFluxTracker().showOverloadFloatyIfNeeded("Vanquished!", Color.WHITE, 8f, true);
                                    if (Global.getCombatEngine().getCustomData().get("armaa_" + targetData.ship.getId() + "_combo") == null) {
                                        Global.getCombatEngine().getCustomData().put("armaa_" + targetData.ship.getId() + "_combo", "-");
                                    }
                                    targetData.ship.getSystem().deactivate();
                                    targetData.ship.getSystem().setCooldownRemaining(1);
                                    //unapply(stats,ship.getId());
                                }
                                Global.getCombatEngine().getCustomData().remove(targetDataKey);
                                Global.getCombatEngine().removePlugin(targetData.targetEffectPlugin);
                            }
                        }
                    };
                    Global.getCombatEngine().addPlugin(targetData.targetEffectPlugin);
                }

                if (effectLevel > 0) {
                    if (state != State.IN) {
                        targetData.elaspedAfterInState += Global.getCombatEngine().getElapsedInLastFrame();
                    }
                    float shipJitterLevel = 0;
                    if (state == State.IN) {
                        shipJitterLevel = effectLevel;
                    } else {
                        float durOut = 0.5f;
                        shipJitterLevel = Math.max(0, durOut - targetData.elaspedAfterInState) / durOut;
                    }
                    float targetJitterLevel = effectLevel;

                    if (targetJitterLevel > 0) {
                        //target.setJitterUnder(KEY_TARGET, JITTER_UNDER_COLOR, targetJitterLevel, 5, 0f, 15f);
                        targetData.target.setJitter(new Object(), color, targetJitterLevel, 3, 0f, 5f);
                    }
                }
            }
        }

        if (state == State.OUT) {
            stats.getMaxSpeed().unmodify(id); // to slow down ship to its regular top speed while powering drive down
            stats.getBallisticRoFMult().unmodify(id);
            stats.getEnergyRoFMult().unmodify(id);
            ship.setMass(mass);
            DidRam = false;
            ship.getCustomData().remove("armaa_rampageHeading"); // clear escape heading
            ship.getCustomData().remove("armaa_isEscaping");
            ship.getCustomData().remove("armaa_rampageFlankOffset");
        } else {
            // --- Mid-dash bailout (AI ships only) ---------------------------
            // Re-scan threat while dashing and cut it short. Attack dashes bail
            // if we blundered into too much fire; escape dashes end once we're
            // clear (no point flying away for the full duration). Player ships
            // are never auto-cut.
            if (!ship.isAlive() || ship != Global.getCombatEngine().getPlayerShip()) {
                bailoutCheck.advance(Global.getCombatEngine().getElapsedInLastFrame());
                if (bailoutCheck.intervalElapsed() && ship.getSystem() != null
                        && ship.getSystem().isActive() && !ship.isDirectRetreat()) {
                    boolean escaping = ship.getCustomData().get("armaa_isEscaping") != null;
                    float threat = enemyThreatNear(ship.getLocation(), BAILOUT_SCAN_RADIUS, ship.getOwner());
                    if (escaping) {
                        if (threat < ESCAPE_SAFE_THREAT) {
                            ship.getSystem().deactivate();
                            return;
                        }
                    } else if (!DidRam && threat > BAILOUT_MAX_THREAT) {
                        ship.getSystem().deactivate();
                        return;
                    }
                }
            }
            if (ship.getMass() == mass) {
                ship.setMass(mass * MASS_MULT);
            }
            float bonus = 1f;
            if (Global.getCombatEngine().getCustomData().get("armaa_" + ship.getId() + "_combo") != null) {
                bonus = 1.25f;
            }
            stats.getMaxSpeed().modifyFlat(id, (Float) SPEED_BOOST.get(ship.getHullSize()) * bonus);
            stats.getAcceleration().modifyFlat(id, (Float) SPEED_BOOST.get(ship.getHullSize()) * 4);
            stats.getEmpDamageTakenMult().modifyMult(id, (Float) DAMAGE_MULT.get(ship.getHullSize()));
            stats.getArmorDamageTakenMult().modifyMult(id, (Float) DAMAGE_MULT.get(ship.getHullSize()));
            stats.getHullDamageTakenMult().modifyMult(id, (Float) DAMAGE_MULT.get(ship.getHullSize()));
            stats.getMaxTurnRate().modifyMult(id, 0.50f);
            stats.getFluxDissipation().modifyPercent(id, (Float) 1.25f);
            stats.getHardFluxDissipationFraction().modifyPercent(id, (Float) 1.25f);
            stats.getBallisticRoFMult().modifyMult(id, ROF_MULT);
            stats.getEnergyRoFMult().modifyMult(id, ROF_MULT);
            for (ShipAPI module : ship.getChildModulesCopy()) {

                module.getMutableStats().getMaxSpeed().modifyFlat(id, (Float) SPEED_BOOST.get(ship.getHullSize()) * bonus);
                module.getMutableStats().getAcceleration().modifyFlat(id, (Float) SPEED_BOOST.get(ship.getHullSize()) * 4);
                module.getMutableStats().getEmpDamageTakenMult().modifyMult(id, (Float) DAMAGE_MULT.get(ship.getHullSize()));
                module.getMutableStats().getArmorDamageTakenMult().modifyMult(id, (Float) DAMAGE_MULT.get(ship.getHullSize()));
                module.getMutableStats().getHullDamageTakenMult().modifyMult(id, (Float) DAMAGE_MULT.get(ship.getHullSize()));
                module.getMutableStats().getMaxTurnRate().modifyMult(id, 0.50f);
                module.getMutableStats().getFluxDissipation().modifyPercent(id, (Float) 1.25f);
                module.getMutableStats().getHardFluxDissipationFraction().modifyPercent(id, (Float) 1.25f);
                module.getMutableStats().getBallisticRoFMult().modifyMult(id, ROF_MULT);
                module.getMutableStats().getEnergyRoFMult().modifyMult(id, ROF_MULT);
            }
            if (!DidRam) {
                Vector2f from = ship.getLocation();
                float angle = ship.getFacing();
                Vector2f end = MathUtils.getPoint(from, (Float) bugs.get(ship.getHullSize()), angle);
                List<CombatEntityAPI> entity = CombatUtils.getEntitiesWithinRange(ship.getLocation(), (Float) bugs.get(ship.getHullSize()) + 25f);
                if (!entity.isEmpty()) {
                    for (CombatEntityAPI e : entity) {
                        if (e.getCollisionClass() == CollisionClass.NONE) {
                            continue;
                        }
                        if (e.getOwner() == ship.getOwner()) {
                            continue;
                        }
                        Vector2f col = new Vector2f(1000000, 1000000);
                        if (e instanceof ShipAPI) {
                            if (e != ship && ((ShipAPI) e).getParentStation() != e && (e.getCollisionClass() != CollisionClass.NONE) && CollisionUtils.getCollides(ship.getLocation(), end, e.getLocation(), e.getCollisionRadius())) {
                                ShipAPI s = (ShipAPI) e;
                                Vector2f hitPoint = (Vector2f) getShipCollisionPoint(from, end, s, angle);
                                if (hitPoint != null) {
                                    col = hitPoint;
                                }
                            }
                            if (col.x != 1000000 && MathUtils.getDistanceSquared(from, col) < MathUtils.getDistanceSquared(from, end)) {
                                DidRam = true;
                                MagicFakeBeam.spawnFakeBeam(engine, ship.getLocation(), (Float) bugs.get(ship.getHullSize()), ship.getFacing(), (Float) wide.get(ship.getHullSize()), 0.1f, 0.1f, 25, color, color, (Float) damage.get(ship.getHullSize()), DamageType.KINETIC, 0, ship);
                                float angleAB = VectorUtils.getAngle(ship.getLocation(), e.getLocation());
                                float ramMass = ship.getMass();
                                float tgtMass = e.getMass();

                                // calculate how much target is flung relative to ram
                                float pushTarget = ramMass / tgtMass;  // bigger ram -> bigger push
                                float pushRammer = tgtMass / ramMass;  // smaller tgt -> ram bounces more

                                if (MagicRender.screenCheck(0.2f, col)) {
                                    if (Math.random() > 0.75) {
                                        engine.spawnExplosion(col, new Vector2f(), MUZZLE_FLASH_COLOR, MUZZLE_FLASH_SIZE * 0.10f, MUZZLE_FLASH_DURATION);
                                    } else {
                                        engine.spawnExplosion(col, new Vector2f(), MUZZLE_FLASH_COLOR, MUZZLE_FLASH_SIZE, MUZZLE_FLASH_DURATION);
                                    }
                                    engine.addSmoothParticle(col, new Vector2f(), MUZZLE_FLASH_SIZE * 3f, 1f, MUZZLE_FLASH_DURATION * 2f, MUZZLE_FLASH_COLOR_GLOW);
                                    engine.addSmoothParticle(col, new Vector2f(0, 0), 300, 2, 0.15f, Color.white);
                                    engine.spawnExplosion(col, new Vector2f(0, 0), Color.DARK_GRAY, 125, 2);
                                    engine.spawnExplosion(col, new Vector2f(0, 0), Color.BLACK, 60 * 5, 3);

                                    for (int x = 0; x < 15; x++) {
                                        engine.addHitParticle(col,
                                                MathUtils.getPointOnCircumference(null, MathUtils.getRandomNumberInRange(150f, 325f), (float) Math.random() * 360f),
                                                6f, 1f, MathUtils.getRandomNumberInRange(0.6f, 1f), Color.white);
                                    }

                                }

                                // clamp extremes so nothing becomes ridiculous
                                pushTarget = Math.min(pushTarget, 4f);   // yeet small ships but not to space
                                if(!ship.isFrigate() && !ship.isFighter() )
                                CombatUtils.applyForce(ship, angleAB + 180f, 3f * ship.getMass() * pushRammer);
                                CombatUtils.applyForce(e, angleAB, 3f * ship.getMass() * pushTarget);
                                Global.getSoundPlayer().playSound("collision_ships", 1f, 0.5f, ship.getLocation(), ship.getVelocity());
                                ship.getSystem().deactivate();
                                //engine.addFloatingText(ship.getLocation(), "Yamete!", 25f, Color.WHITE, ship, 1f, 0.5f);
                            }
                        }
                    }
                }
            }
            if (ship.isDirectRetreat() && ship.getSystem().isActive()) {
                ship.setAngularVelocity(Math.min(turnrate, Math.max(-turnrate, MathUtils.getShortestRotation(ship.getFacing(), ship.getOwner() == 0 ? Global.getCombatEngine().getFleetManager(ship.getOwner()).getGoal() == FleetGoal.ESCAPE ? 90f : 270f : Global.getCombatEngine().getFleetManager(ship.getOwner()).getGoal() == FleetGoal.ESCAPE ? 270f : 90f) * 2)));
            } else if (ship.getCustomData().get("armaa_rampageHeading") != null && ship.getSystem().isActive()) {
                // Escape mode: AI set a heading to flee along. Steer toward it
                // directly rather than toward a ship target.
                float wantHeading = (Float) ship.getCustomData().get("armaa_rampageHeading");
                float facing = MathUtils.getShortestRotation(ship.getFacing(), wantHeading);
                ship.setAngularVelocity(Math.min(turnrate, Math.max(-turnrate, facing * 5)));
            } else if ((target != null && target.isAlive()) && ship.getSystem().isActive()) {
                float facing = ship.getFacing();
                if ((target.isFighter() || target.isDrone()) && target.getWing() != null && target.getWing().getSourceShip() != null && target.getWing().getSourceShip().isAlive()) {
                    facing = MathUtils.getShortestRotation(facing, VectorUtils.getAngle(ship.getLocation(), target.getWing().getSourceShip().getLocation()));
                } else {
                    //if ((target.isFighter() || target.isDrone()) && ship.getAIFlags().getCustom(AIFlags.MANEUVER_TARGET) instanceof ShipAPI) {engine.addFloatingText(ship.getLocation(), "My Life For Ava!", 15f, Color.WHITE, ship, 1f, 0.5f);ship.setShipTarget((ShipAPI) ship.getAIFlags().getCustom(AIFlags.MANEUVER_TARGET));}
                    float aimAngle = VectorUtils.getAngle(ship.getLocation(), target.getLocation());
                    // Apply the AI's flank offset, faded out as we close so the
                    // ram lands straight on. Offset is 0 once within straightenDist.
                    Object offObj = ship.getCustomData().get("armaa_rampageFlankOffset");
                    if (offObj != null && !target.isFighter() && !target.isDrone()) {
                        float offset = (Float) offObj;
                        float dist = MathUtils.getDistance(ship.getLocation(), target.getLocation());
                        float straightenDist = target.getCollisionRadius() + 150f;
                        float fadeRange = (Float) bugs.get(ship.getHullSize());
                        // 0 at/below straightenDist, ramps to 1 by straightenDist+fadeRange
                        float t = (dist - straightenDist) / Math.max(1f, fadeRange);
                        t = Math.max(0f, Math.min(1f, t));
                        aimAngle = aimAngle + offset * t;
                    }
                    facing = MathUtils.getShortestRotation(facing, aimAngle);
                }
                if (!target.isFighter() && !target.isDrone()) {
                    ship.setAngularVelocity(Math.min(turnrate, Math.max(-turnrate, facing * 5)));
                } else {
                    ship.setAngularVelocity(Math.min(turnrate, Math.max(-turnrate, facing * 2)));
                }
            }
            float amount = Global.getCombatEngine().getElapsedInLastFrame();
            //afterimage shit from tahlan
            ship.getMutableStats().getDynamic().getStat("armaa_NNAfterimageTracker").modifyFlat("armaa_NNAfterimageTrackerNullerID", -1);
            ship.getMutableStats().getDynamic().getStat("armaa_NNAfterimageTracker").modifyFlat("armaa_NNAfterimageTrackerID",
                    ship.getMutableStats().getDynamic().getStat("armaa_NNAfterimageTracker").getModifiedValue() + amount);

            if (ship.getMutableStats().getDynamic().getStat("armaa_NNAfterimageTracker").getModifiedValue() > AFTERIMAGE_THRESHOLD) {

                // Sprite offset fuckery - Don't you love trigonometry?
                SpriteAPI sprite = ship.getSpriteAPI();
                float offsetX = sprite.getWidth() / 2 - sprite.getCenterX();
                float offsetY = sprite.getHeight() / 2 - sprite.getCenterY();

                float trueOffsetX = (float) FastTrig.cos(Math.toRadians(ship.getFacing() - 90f)) * offsetX - (float) FastTrig.sin(Math.toRadians(ship.getFacing() - 90f)) * offsetY;
                float trueOffsetY = (float) FastTrig.sin(Math.toRadians(ship.getFacing() - 90f)) * offsetX + (float) FastTrig.cos(Math.toRadians(ship.getFacing() - 90f)) * offsetY;

                if (!ship.isHulk()) {
                    for (WeaponAPI w : ship.getAllWeapons()) {
                        if (!w.getSlot().isBuiltIn() && !w.getSlot().isDecorative()) {
                            continue;
                        }
                        //armaa_valkazardEffect;
                        if (w.getSpec().getType() == WeaponAPI.WeaponType.MISSILE && w.getAmmo() < 0) {
                            continue;
                        }

                        if (w.getSprite() == null) {
                            continue;
                        }
                        Color sysColo = ship.getSystem().getSpecAPI().getJitterEffectColor();
                        if (!w.getSlot().getId().equals("F_LEGS")) {
                            MagicRender.battlespace(
                                    Global.getSettings().getSprite(w.getSpec().getTurretSpriteName()),
                                    new Vector2f(w.getLocation().getX() + trueOffsetX, w.getLocation().getY() + trueOffsetY),
                                    new Vector2f(0, 0),
                                    new Vector2f(w.getSprite().getWidth(), w.getSprite().getHeight()),
                                    new Vector2f(0, 0),
                                    ship.getFacing() - 90f,
                                    0f,
                                    sysColo,
                                    true,
                                    0f,
                                    0f,
                                    0f,
                                    0f,
                                    0f,
                                    0.1f,
                                    0.1f,
                                    0.3f,
                                    CombatEngineLayers.BELOW_SHIPS_LAYER);
                        } else {
                            int frame = w.getAnimation().getFrame();
                            SpriteAPI spr = Global.getSettings().getSprite("graphics/armaa/ships/valkazard/armaa_valkazard_legs0" + frame + ".png");

                            if (frame >= 10) {
                                spr = Global.getSettings().getSprite("graphics/armaa/ships/valkazard/armaa_valkazard_legs" + frame + ".png");
                            }

                            MagicRender.battlespace(
                                    spr,
                                    new Vector2f(w.getLocation().getX() + trueOffsetX, w.getLocation().getY() + trueOffsetY),
                                    new Vector2f(0, 0),
                                    new Vector2f(w.getSprite().getWidth(), w.getSprite().getHeight()),
                                    new Vector2f(0, 0),
                                    ship.getFacing() - 90f,
                                    0f,
                                    sysColo,
                                    true,
                                    0f,
                                    0f,
                                    0f,
                                    0f,
                                    0f,
                                    0.1f,
                                    0.1f,
                                    0.3f,
                                    CombatEngineLayers.BELOW_SHIPS_LAYER);
                        }

                        if (w.getBarrelSpriteAPI() != null) {
                            if (!w.getSlot().getId().equals("C_ARML") && (!w.getSlot().getId().equals("A_GUN"))) {
                                continue;
                            }

                            //Explicitly for Valkazard compat, since apparently can't access barrelsprite id through api and using the api itself also modifies the color of the original
                            SpriteAPI spr = Global.getSettings().getSprite(w.getSpec().getHardpointSpriteName());

                            MagicRender.battlespace(
                                    spr,
                                    new Vector2f(w.getLocation().getX() + trueOffsetX, w.getLocation().getY() + trueOffsetY),
                                    new Vector2f(0, 0),
                                    new Vector2f(w.getBarrelSpriteAPI().getWidth(), w.getBarrelSpriteAPI().getHeight()),
                                    new Vector2f(0, 0),
                                    w.getCurrAngle() - 90f,
                                    0f,
                                    sysColo,
                                    true,
                                    0f,
                                    0f,
                                    0f,
                                    0f,
                                    0f,
                                    0.1f,
                                    0.1f,
                                    0.3f,
                                    CombatEngineLayers.BELOW_SHIPS_LAYER);
                        }
                    }
                }
                ship.getMutableStats().getDynamic().getStat("armaa_NNAfterimageTracker").modifyFlat("armaa_NNAfterimageTrackerID",
                        ship.getMutableStats().getDynamic().getStat("armaa_NNAfterimageTracker").getModifiedValue() - AFTERIMAGE_THRESHOLD);
            }
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        reset = true;
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null) {
            return;
        }

        if (mass == null) {
            mass = ship.getMass();
        }
        /*
        if (ship.hasListenerOfClass(RampageDriveListener.class))
		{
			ship.removeListenerOfClass(RampageDriveListener.class);
		}
         */
        if (ship.getMass() != mass) {
            ship.setMass(mass);
        }
        for (ShipAPI module : ship.getChildModulesCopy()) {
            module.getMutableStats().getMaxSpeed().unmodify(id);
            module.getMutableStats().getAcceleration().unmodify(id);
            module.getMutableStats().getEmpDamageTakenMult().unmodify(id);
            module.getMutableStats().getHullDamageTakenMult().unmodify(id);
            module.getMutableStats().getArmorDamageTakenMult().unmodify(id);
            module.getMutableStats().getBallisticRoFMult().unmodify(id);
            module.getMutableStats().getEnergyRoFMult().unmodify(id);
            module.getMutableStats().getFluxDissipation().unmodify(id);
            module.getMutableStats().getHardFluxDissipationFraction().unmodify(id);
            module.getMutableStats().getMaxTurnRate().unmodify(id);
        }
        stats.getMaxSpeed().unmodify(id);
        stats.getAcceleration().unmodify(id);
        stats.getEmpDamageTakenMult().unmodify(id);
        stats.getHullDamageTakenMult().unmodify(id);
        stats.getArmorDamageTakenMult().unmodify(id);
        stats.getBallisticRoFMult().unmodify(id);
        stats.getEnergyRoFMult().unmodify(id);
        stats.getFluxDissipation().unmodify(id);
        stats.getHardFluxDissipationFraction().unmodify(id);
        stats.getMaxTurnRate().unmodify(id);
    }

    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) {
            return new StatusData(poopystinky, false);
        } else if (index == 1) {
            return new StatusData(poopystinky2, true);
        }
        return null;
    }

    @Override
    public String getInfoText(ShipSystemAPI system, ShipAPI ship) {
        if (system.isOutOfAmmo()) {
            return null;
        }
        if (system.getState() != ShipSystemAPI.SystemState.IDLE) {
            return null;
        }

        return poopystinky3;
    }

    /**
     * Sums ONLY enemy threat weight near a point (allies excluded). Mirrors the
     * AI script's threat scoring so the mid-dash bailout uses the same scale.
     */
    private float enemyThreatNear(Vector2f point, float radius, int selfOwner) {
        float danger = 0f;
        for (ShipAPI s : CombatUtils.getShipsWithinRange(point, radius)) {
            if (s == null || !s.isAlive() || s.isHulk() || s.isFighter()) {
                continue;
            }
            if (s.getOwner() == selfOwner) {
                continue; // skip allies and self
            }
            danger += threatWeightOf(s);
        }
        return danger;
    }

    private float threatWeightOf(ShipAPI s) {
        float w;
        if (s.isFrigate()) {
            w = 1f;
        } else if (s.isDestroyer()) {
            w = 2f;
        } else if (s.isCruiser()) {
            w = 3f;
        } else if (s.isCapital()) {
            w = 4f;
        } else {
            w = 0.25f;
        }
        if (s.getFluxTracker().isOverloaded() || s.getFluxTracker().isVenting()) {
            w *= 0.5f;
        }
        if (s.getEngineController().isFlamedOut()) {
            w *= 0.7f;
        }
        return w;
    }

    public static class RampageDriveListener implements DamageTakenModifier {

        @Override
        public String modifyDamageTaken(Object param, CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean shieldHit) {
            // checking for ship explosions
            if (param instanceof DamagingProjectileAPI) {
                DamagingProjectileAPI proj = (DamagingProjectileAPI) param;

                if (proj.getDamageType().equals(DamageType.HIGH_EXPLOSIVE)
                        && proj.getProjectileSpecId() == null
                        && proj.getSource() != null
                        && !proj.getSource().isAlive()
                        && proj.getSpawnType().equals(ProjectileSpawnType.OTHER)
                        && MathUtils.getDistance(proj.getSpawnLocation(), proj.getSource().getLocation()) < 150f) {

                    damage.getModifier().modifyMult(this.getClass().getName(), 0.05f);

                }
            }
            return null;
        }
    }
    /*protected ShipAPI findTarget(ShipAPI ship) {
        ShipAPI target = ship.getShipTarget();
        if(
                target!=null
                        &&
                        (!target.isDrone()||!target.isFighter())
                        &&
                        MathUtils.isWithinRange(ship, target, RANGE)
                        &&
                        target.getOwner()!=ship.getOwner()
                ){
            return target;
        } else {
            return null;
        }
    }*/
}
