package data.scripts.weapons;

import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import org.magiclib.util.MagicAnim;
import com.fs.starfarer.api.Global;
import java.awt.Color;
import java.util.Map;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lazywizard.lazylib.CollisionUtils;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.combat.DamageType;

public class armaa_paEffect implements EveryFrameWeaponEffectPlugin {

    private boolean isBoarding = false;
    private float swingLevelR = 0f;
    private boolean swingingR = false;
    private boolean cooldownR = false;
    private IntervalUtil animInterval = new IntervalUtil(0.012f, 0.012f);
    private IntervalUtil boardingInterval = new IntervalUtil(1f, 3f);
    private IntervalUtil sabotageInterval = new IntervalUtil(5f, 10f);
    private IntervalUtil abscondInterval = new IntervalUtil(0.5f, 0.5f);
    private final float TORSO_OFFSET = -45;
    private int boardingAttacks = 3;
    private IntervalUtil boardingRefresh = new IntervalUtil(5f, 5f);

    public static Vector2f lerp(Vector2f start, Vector2f end, float alpha) {
        if (alpha < 0.0f) {
            alpha = 0.0f;
        }
        if (alpha > 1.0f) {
            alpha = 1.0f;
        }

        float x = start.x + alpha * (end.x - start.x);
        float y = start.y + alpha * (end.y - start.y);

        return new Vector2f(x, y);
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        ShipAPI ship = weapon.getShip();
        if (engine.isPaused()) {
            return;
        }

        if (!ship.isAlive()) {
            return;
        }

        boardingInterval.advance(amount);
        if (boardingInterval.intervalElapsed() && !isBoarding && boardingAttacks > 0) {
            ShipAPI boardingTarget = null;
            for (ShipAPI target : CombatUtils.getShipsWithinRange(ship.getLocation(), 25f)) {
                if (!target.isAlive()) {
                    continue;
                }
                if (target.isHulk()) {
                    continue;
                }
                if (target == ship) {
                    continue;
                }
                if (target.getOwner() != 0 && target.getOwner() != 1) {
                    continue;
                }
                if (target.getMutableStats().getMinCrewMod().computeEffective(target.getHullSpec().getMinCrew()) <= 0) {
                    continue;
                }
                if (target.getOwner() == ship.getOwner()) {
                    continue;
                }
                boolean canBoard = false;
                if (target.getShield() == null) {
                    canBoard = true;
                } else {
                    if (target.getShield().isOff()) {
                        canBoard = true;
                    } else {
                        if (!target.getShield().isWithinArc(ship.getLocation())) {
                            canBoard = true;
                        }
                    }
                }

                if (canBoard) {
                    boardingTarget = target;
                    engine.getCustomData().put("armaa_boardingTarget_" + ship.getId(), target);
                    break;
                }

            }

            if (boardingTarget != null) {
                if (!engine.hasAttachedFloaty(ship) && Math.random() < 0.10f) {
                }
                ship.setShipAI(null);
                ship.beginLandingAnimation(boardingTarget);
                ship.setCollisionClass(CollisionClass.NONE);
                ship.getMutableStats().getHullDamageTakenMult().modifyMult("invincible", 0f);
                ship.getMutableStats().getArmorDamageTakenMult().modifyMult("invincible", 0f);
                ship.setControlsLocked(true);
                Map<String, Object> customData = engine.getCustomData();
                int numBoarders = customData.get("armaa_boardingTarget_" + boardingTarget.getId() + "_numBoarders") == null ? 0 : (int) customData.get("armaa_boardingTarget_" + boardingTarget.getId() + "_numBoarders");
                engine.getCustomData().put("armaa_boardingTarget_" + boardingTarget.getId() + "_numBoarders", 5 + numBoarders);
                isBoarding = true;
            }
        }

        if (isBoarding) {
            ShipAPI boardingTarget = (ShipAPI) engine.getCustomData().get("armaa_boardingTarget_" + ship.getId());
            Map<String, Object> customData = engine.getCustomData();
            int numBoarders = customData.get("armaa_boardingTarget_" + boardingTarget.getId() + "_numBoarders") == null ? 0 : (int) customData.get("armaa_boardingTarget_" + boardingTarget.getId() + "_numBoarders");

            abscondInterval.advance(amount);
            sabotageInterval.advance(amount);

            Vector2f dest = new Vector2f(CollisionUtils.getNearestPointOnBounds(ship.getLocation(), boardingTarget));
            float distToTarget = (float) (MathUtils.getDistanceSquared(dest, ship.getLocation()) / Math.pow(boardingTarget.getCollisionRadius(), 2));
            if (!ship.isFinishedLanding()) {
                float f = 1.0f - Math.min(1, distToTarget);
                ship.getLocation().x = (dest.x * (f * 0.1f) + ship.getLocation().x * (2f - f * 0.1f)) / 2;
                ship.getLocation().y = (dest.y * (f * 0.1f) + ship.getLocation().y * (2f - f * 0.1f)) / 2;

            } else {
                ship.getLocation().set(boardingTarget.getLocation());
            }

            if (sabotageInterval.intervalElapsed()) {
                float minCrew = boardingTarget.getMutableStats().getMinCrewMod().computeEffective(boardingTarget.getHullSpec().getMinCrew() * boardingTarget.getHullLevel());
                float defMult = boardingTarget.getShield() != null && boardingTarget.getShield().isOn() ? 1.5f : 1f;
                float mult = boardingTarget.getOwner() == ship.getOwner() ? 2f : 1f;
                numBoarders = customData.get("armaa_boardingTarget_" + boardingTarget.getId() + "_numBoarders") == null ? 0 : (int) customData.get("armaa_boardingTarget_" + boardingTarget.getId() + "_numBoarders");

                if (ship.getHitpoints() - Math.max(10, 10 * (minCrew / numBoarders) * mult) <= 0) {
                    engine.removeEntity(ship);
                }
                if (boardingAttacks <= 0 || (boardingTarget == null || !boardingTarget.isAlive() || boardingTarget.isHulk())) {
                    ship.setControlsLocked(false);
                    ship.getMutableStats().getHullDamageTakenMult().unmodify("invincible");
                    ship.getMutableStats().getArmorDamageTakenMult().unmodify("invincible");
                    ship.setCollisionClass(CollisionClass.FIGHTER);
                    ship.setAnimatedLaunch();
                    Global.getSoundPlayer().playSound("fighter_takeoff", 1f, 1f, ship.getLocation(), new Vector2f());
                    ship.resetDefaultAI();
                    isBoarding = false;
                    return;
                }
                ship.setHitpoints(ship.getHitpoints() - Math.max(10, 10 * (minCrew / numBoarders) * mult));
                if (!engine.hasAttachedFloaty(boardingTarget)) {
                    engine.addFloatingText(boardingTarget.getLocation(), "Boarded! Defender Strength: " + minCrew + " vs " + numBoarders, 15f, Color.white, boardingTarget, 1f, 5f);
                }
                if (1==1) {

                    Vector2f loc = CollisionUtils.getNearestPointOnBounds(MathUtils.getRandomPointOnCircumference(boardingTarget.getLocation(), boardingTarget.getCollisionRadius() * 0.70f), boardingTarget);
                    engine.applyDamage(boardingTarget, loc, 150f, DamageType.ENERGY, 500f, true, false, ship);
                    engine.addHitParticle(loc, new Vector2f(), 50f, 2f, Color.green);
                    boardingAttacks--;
                }
            }

        }

        float sineC = 0, sinceD = 0;

        if (weapon != null) {
            if (weapon.getChargeLevel() < 1) {
                sineC = MagicAnim.smoothNormalizeRange(weapon.getChargeLevel(), 0.3f, 0.9f);
                sinceD = MagicAnim.smoothNormalizeRange(weapon.getChargeLevel(), 0.3f, 1f);
            } else {
                sineC = 1;
                sinceD = 1;
            }

            if (weapon.getCooldownRemaining() <= 0f && !weapon.isFiring()) {
                cooldownR = false;
            }

            if (!swingingR && !cooldownR && weapon.getChargeLevel() > 0f) {
                weapon.setCurrAngle(weapon.getCurrAngle() + (sineC * -(TORSO_OFFSET) * 0.30f) * weapon.getChargeLevel());
            }
            if (weapon.getChargeLevel() >= 1f) {
                swingingR = true;
            }

            if (swingingR && weapon.getCurrAngle() != weapon.getShip().getFacing() - 90f) {
                animInterval.advance(amount);
                weapon.setCurrAngle((float) Math.max(weapon.getCurrAngle() - swingLevelR, weapon.getCurrAngle() - weapon.getArc() / 2));
            }

            if (swingingR == true && (weapon.getChargeLevel() <= 0f)) {
                swingingR = false;
                swingLevelR = 0f;
                cooldownR = true;
            }

            if (animInterval.intervalElapsed()) {
                swingLevelR += 0.5;
            }

            if (swingLevelR > 9) {
                swingLevelR = 9;
            }
            if (swingingR == false) {
                swingLevelR = 0f;
            }

        }
    }
}
