package data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin;
import java.awt.Color;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import java.util.List;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;
// this script isnt currently used by anything
public class armaa_localFCSStats extends BaseShipSystemScript {

    public static final float DAMAGE_BONUS_PERCENT = 0f;
    public static float SPEED_BONUS = 50f;
    public static float TURN_BONUS = 100f;
    public float bonusPercent = 0f;
    //private Color color = new Color(100,255,100,255);
    private Color color = new Color(255, 0, 115, 255);
    public static Color TEXT_COLOR = new Color(255, 55, 55, 255);
    public static Object KEY_SHIP = new Object();
    public static Object KEY_TARGET = new Object();
    public static Color JITTER_COLOR = new Color(255, 50, 50, 75);
    public static Color JITTER_UNDER_COLOR = new Color(255, 100, 100, 155);

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

    /*
        We want a reason why a small Cataphract can meaningfully support ships from fighter-size up to battleships
        without sounding like:
        "It projects a magic aura!" (bad)

        "It has infinite reactor output!" (unbelievable)

        The key is: The mech is not providing raw power.
        It is providing information, targeting correction, and threat interpretation.
        (Like JTAC/FAC...Helldivers? LOL)
        Which scales perfectly to ships of all sizes.
     */
    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {

        //I guess we want to have this trigger on target
        //for AI we'll stelp through some logic to determine who we want to target
        //Apply everyframeeffect to the target that lasts x duration based on flux level
        // at the time of application?
        // and give system user benefits based on their flux level?
        //
        ShipAPI ship = (ShipAPI) stats.getEntity();
        final String targetDataKey = ship.getId() + "_armaa_localFCS_target_data";
        Object targetDataObj = Global.getCombatEngine().getCustomData().get(targetDataKey);
        ship.getMutableStats().getAcceleration().modifyMult(id, 1.20f);
        ship.getMutableStats().getDeceleration().modifyMult(id, 1.20f);
        ship.getMutableStats().getTurnAcceleration().modifyMult(id, 1.25f);
        ship.getMutableStats().getMaxTurnRate().modifyMult(id, 1.15f);
        if (state == State.IN && targetDataObj == null) {
            ShipAPI target = findTarget(ship);
            Global.getCombatEngine().getCustomData().put(targetDataKey, new TargetData(ship, target));
            if (target != null) {
                if (target.getFluxTracker().showFloaty()
                        || ship == Global.getCombatEngine().getPlayerShip()
                        || target == Global.getCombatEngine().getPlayerShip()) {
                    target.getFluxTracker().showOverloadFloatyIfNeeded("Telemetry provided!", TEXT_COLOR, 4f, true);
                }
            }
        } else if (state == State.IDLE && targetDataObj != null) {
            Global.getCombatEngine().getCustomData().remove(targetDataKey);
            ((TargetData) targetDataObj).currDamMult = 1f;
            targetDataObj = null;
        }
        if (targetDataObj == null || ((TargetData) targetDataObj).target == null) {
            return;
        }
        final TargetData targetData = (TargetData) targetDataObj;
        float scale = effectLevel;
        targetData.currDamMult = effectLevel;
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
                        Global.getCombatEngine().maintainStatusForPlayerShip(KEY_TARGET,
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
                            targetData.target.addAfterimage(color, 0f, 0f, 0f, 0f, 5f, 0f, 1f, 1f, true, true, true);
                            //org.magiclib.util.MagicFakeBeam.spawnFakeBeam(Global.getCombatEngine(), targetData.ship.getLocation(), 10f, weapon.getCurrAngle(), 20f, amount, 0.15f*chargeLevel, 20f, new Color(.9f,1f,.9f,1f*chargeLevel), new Color(0f,1f,0.75f,.9f*chargeLevel), 0f, DamageType.ENERGY, 0f, ship);
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
                                    new Color(21, 215, 16, 255),
                                    true,
                                    .2f,
                                    1f,
                                    .3f,
                                    true
                            );
                        }
                    }

                    if (!targetData.ship.isAlive() || !targetData.ship.getSystem().isActive() || targetData.elaspedAfterInState > targetData.ship.getSystem().getChargeActiveDur()) {
                        targetData.target.getMutableStats().getProjectileSpeedMult().unmodify(id);
                        targetData.target.getMutableStats().getRecoilPerShotMult().unmodify(id);
                        targetData.target.getMutableStats().getMissileGuidance().unmodify(id);
                        targetData.target.getMutableStats().getMissileTurnAccelerationBonus().unmodify(id);
                        targetData.target.getMutableStats().getAutofireAimAccuracy().unmodify(id);
                        targetData.target.getMutableStats().getArmorDamageTakenMult().unmodify(id);
                        targetData.target.getMutableStats().getHullDamageTakenMult().unmodify(id);
                        Global.getCombatEngine().getCustomData().remove(targetDataKey);
                        Global.getCombatEngine().removePlugin(targetData.targetEffectPlugin);
                    } else {
                        targetData.target.getMutableStats().getProjectileSpeedMult().modifyMult(id, 1.25f);
                        targetData.target.getMutableStats().getRecoilPerShotMult().modifyMult(id, 0.80f);
                        targetData.target.getMutableStats().getMissileGuidance().modifyPercent(id, 50f);
                        targetData.target.getMutableStats().getMissileTurnAccelerationBonus().modifyPercent(id, 50f);
                        targetData.target.getMutableStats().getAutofireAimAccuracy().modifyPercent(id, 100f);
                        targetData.target.getMutableStats().getArmorDamageTakenMult().modifyMult(id, 0.80f);
                        targetData.target.getMutableStats().getHullDamageTakenMult().modifyMult(id, 0.80f);
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

            float maxRangeBonus = 50f;
            float jitterRangeBonus = shipJitterLevel * maxRangeBonus;

            if (shipJitterLevel > 0) {
                ship.setJitterUnder(KEY_SHIP, JITTER_UNDER_COLOR, shipJitterLevel, 21, 0f, 3f + jitterRangeBonus);
                // ship.setJitter(KEY_SHIP, color, shipJitterLevel, 4, 0f, 0 + jitterRangeBonus * 1f);
            }

            if (targetJitterLevel > 0) {
                //target.setJitterUnder(KEY_TARGET, JITTER_UNDER_COLOR, targetJitterLevel, 5, 0f, 15f);
                //targetData.target.setJitter(KEY_TARGET, color, targetJitterLevel, 3, 0f, 5f);
            }
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        stats.getAcceleration().unmodify();
        stats.getDeceleration().unmodify();
        stats.getTurnAcceleration().unmodify();
        stats.getMaxTurnRate().unmodify();
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        //float bonusPercent = DAMAGE_BONUS_PERCENT * effectLevel;
        if (index == 0) {
            return new StatusData("Improved maneuverability, +" + (int) bonusPercent + "% DMG", false);
        }
        return null;
    }

    @Override
    public boolean isUsable(ShipSystemAPI system, ShipAPI ship) {
        //if (true) return true;
        ShipAPI target = findTarget(ship);
        return target != null && target != ship;
    }

    protected ShipAPI findTarget(ShipAPI ship) {
        float range = 1500f;
        ShipAPI target = ship.getShipTarget();
        if (target != null && target.getOwner() == ship.getOwner()) {
            float dist = Misc.getDistance(ship.getLocation(), target.getLocation());
            float radSum = ship.getCollisionRadius() + target.getCollisionRadius();
            if (dist > range + radSum || target.getOwner() != ship.getOwner() || target == ship) {
                target = null;
            }
        } else {
            if (target == null) {
                for (ShipAPI potTarget : AIUtils.getNearbyAllies(ship, range)) {
                    potTarget = target;
                }
            }
        }
        return target;
    }
}
