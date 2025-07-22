package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.ShipEngineControllerAPI.ShipEngineAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import data.scripts.util.armaa_utils;
import org.magiclib.util.MagicAnim;
import java.awt.Color;
import java.util.*;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import org.magiclib.util.MagicRender;

public class armaa_guarDualEffect implements EveryFrameWeaponEffectPlugin {

    private boolean runOnce = false, cleared = false;
    private ShipAPI ship;
    private float sineD = 0f;
    // 1 = Plane, 0 = Robot
    private float transformLevel = 0f, originalRArmPos = 0f, originalShoulderPos = 0f, originalRShoulderPos = 0f;
    private boolean isRobot = true, transforming = false, forcedToMech, transformBlock = false;
    ;
    private SpriteAPI sprite;
    private WeaponAPI head, armL, wingR, wingL, pauldronL, pauldronR, gun, gunF, realGun, lMissile, rMissile;
    private float overlap = 0;
    private IntervalUtil interval2 = new IntervalUtil(1f, 1f);
    private IntervalUtil transformInterval = new IntervalUtil(3f, 5f);
    private IntervalUtil forceTransformTimer = new IntervalUtil(1.5f, 5f);
    private final IntervalUtil animUpdateInterval = new IntervalUtil(0.033f,0.05f);
    private Vector2f ogPosL, ogPosR, ogPosRArm, ogPosLArm, ogPosLWing, ogPosRWing, ogPosGunF, ogPosLMissile, ogPosRMissile;
    private final float TORSO_OFFSET = -150, LEFT_ARM_OFFSET = -65, RIGHT_ARM_OFFSET = -25, MAX_OVERLAP = 10;

    public void init() {
        runOnce = true;
        for (WeaponAPI w : ship.getAllWeapons()) {
            switch (w.getSlot().getId()) {
                case "C_ARML":
                    if (armL == null) {
                        armL = w;
                        ogPosLArm = new Vector2f(armL.getSprite().getCenterX(), armL.getSprite().getCenterY());
                    }
                    break;
                case "WINGR":
                    if (wingR == null) {
                        wingR = w;
                        ogPosRWing = new Vector2f(wingR.getSprite().getCenterX(), wingR.getSprite().getCenterY());
                    }
                    break;
                case "WINGL":
                    if (wingL == null) {
                        wingL = w;
                        ogPosLWing = new Vector2f(wingL.getSprite().getCenterX(), wingL.getSprite().getCenterY());
                    }
                    break;
                case "D_PAULDRONL":
                    if (pauldronL == null) {
                        pauldronL = w;
                        originalShoulderPos = pauldronL.getSprite().getCenterY();
                        ogPosL = new Vector2f(pauldronL.getSprite().getCenterX(), pauldronL.getSprite().getCenterY());
                    }
                    break;
                case "D_PAULDRONR":
                    if (pauldronR == null) {
                        pauldronR = w;
                        originalRShoulderPos = pauldronL.getSprite().getCenterY();
                        ogPosR = new Vector2f(pauldronR.getSprite().getCenterX(), pauldronR.getSprite().getCenterY());
                    }
                    break;
                case "E_HEAD":
                    if (head == null) {
                        if (ship.getOwner() == -1) {
                            head.getSprite().setColor(new Color(0, 0, 0, 0));
                        }
                        head = w;
                    }
                    break;
                case "A_GUN":
                    if (gun == null) {
                        gun = w;
                        originalRArmPos = w.getSprite().getCenterY();
                        ogPosRArm = new Vector2f(gun.getSprite().getCenterX(), gun.getSprite().getCenterY());
                    }
                    break;
                case "TRUE_GUN":
                    if (realGun == null) {
                        realGun = w;
                    }
                    break;
                case "F_GPOD":
                    if (gunF == null) {
                        gunF = w;
                        if(gunF.getAnimation() != null)
                            if(!ship.isFighter())
                                gunF.getAnimation().setFrame(1);
                            else
                                gunF.getAnimation().setFrame(2);
                            
                        ogPosGunF = new Vector2f(gunF.getSprite().getCenterX(), gunF.getSprite().getCenterY());
                    }
                    break;
                case "WS0002":
                    if (lMissile == null) {
                        lMissile = w;
                        if (w.getMissileRenderData() != null && w.getMissileRenderData().size() > 0) {
                            int index = 0;
                            if (w.getMissileRenderData().size() / 2 > 0) {
                                index = w.getMissileRenderData().size() / 2;
                            }
                            ogPosLMissile = new Vector2f(lMissile.getMissileRenderData().get(index).getSprite().getCenterX(), lMissile.getMissileRenderData().get(index).getSprite().getCenterY());
                        }
                    }
                    break;
                case "WS0004":
                    if (rMissile == null) {
                        rMissile = w;
                        if (w.getMissileRenderData() != null && w.getMissileRenderData().size() > 0) {
                            int index = 0;
                            if (w.getMissileRenderData().size() / 2 > 0) {
                                index = w.getMissileRenderData().size() / 2;
                            }
                            ogPosRMissile = new Vector2f(rMissile.getMissileRenderData().get(index).getSprite().getCenterX(), rMissile.getMissileRenderData().get(index).getSprite().getCenterY());
                        }
                    }
                    break;
            }
        }
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (ship == null) {
            ship = weapon.getShip();
        }
        if(!runOnce)
        {
            if(ship.getShield() != null)
                engine.getCustomData().put("armaa_transformState_sArc_" + ship.getId(),ship.getShield().getArc());
            if(!ship.isFighter())
                transforming = true;
                isRobot = false;

            init();
        }
        // refit screen
        if (ship.getOwner() == -1 || !ship.isAlive()) {
            pauldronL.setCurrAngle(ship.getFacing() + MathUtils.getShortestRotation(ship.getFacing(), 0f + 1f * 90f));
            pauldronL.getSprite().setCenterY(ogPosL.getY() - 6 * 1);
            pauldronL.getSprite().setCenterX(ogPosL.getX() - 4 * 1);
            wingL.setCurrAngle(pauldronL.getCurrAngle());
            wingL.getSprite().setCenterY(ogPosLWing.getY() - 14 * 1);
            wingL.getSprite().setCenterX(ogPosLWing.getX() - 8 * 1);
            head.getSprite().setColor(new Color(0, 0, 0, 0));
            armL.getSprite().setColor(new Color(0, 0, 0, 0));
            gun.getSprite().setColor(new Color(0, 0, 0, 0));
            pauldronR.setCurrAngle(ship.getFacing() + (1f) * TORSO_OFFSET * 0.5f * 0.75f + RIGHT_ARM_OFFSET * 0.5f);
            pauldronR.getSprite().setCenterY(ogPosR.getY() - 6 * 1);
            pauldronR.getSprite().setCenterX(ogPosR.getX() + 4 * 1);
            wingR.setCurrAngle(pauldronR.getCurrAngle());
            wingR.getSprite().setCenterY(ogPosRWing.getY() - 14 * 1);
            wingR.getSprite().setCenterX(ogPosRWing.getX() + 8 * 1);
        }
        if (!Global.getCombatEngine().isEntityInPlay(ship)) {
            return;
        }
        boolean transformNow = engine.getCustomData().get("armaa_transformNow_" + ship.getId()) != null
                ? true : false;
        interval2.advance(amount);
        transformInterval.advance(amount);
        if (transformBlock) {
            forceTransformTimer.advance(amount);
        }
        if (forceTransformTimer.intervalElapsed() && transformBlock) {
            transformBlock = false;
            engine.getCustomData().remove("armaa_transformNow_" + ship.getId());
        }
        if (!transformBlock && ship.isAlive() && !ship.isFighter()) {
            if ((!ship.getFluxTracker().isOverloaded() && !transforming) || !ship.getFluxTracker().isOverloaded() && transformNow && !transforming) {
                //AI stuff
                ShipwideAIFlags flags = ship.getAIFlags();
                boolean inDanger = flags.hasFlag(ShipwideAIFlags.AIFlags.HAS_INCOMING_DAMAGE);
                for (BeamAPI beam : engine.getBeams()) {
                    if (beam.getDamageTarget() == ship) {
                        inDanger = true;
                        break;
                    }
                }
                if ((!inDanger) && (transformNow || transformInterval.intervalElapsed()) && ship.getShipAI() != null) {
                    if (transformNow) {
                        transformNow = false;
                        transformBlock = true;
                        transforming = true;
                    } else if (!isRobot && Math.random() < 0.10f) {
                        if (flags.hasFlag(AIFlags.TURN_QUICKLY)) {
                            transforming = true;
                        }
                    }
                    else if (isRobot) {
                        if (flags.hasFlag(AIFlags.BACK_OFF) || flags.hasFlag(AIFlags.BACKING_OFF)
                                || flags.hasFlag(AIFlags.BACKING_OFF) || flags.hasFlag(AIFlags.HARASS_MOVE_IN)) {
                            transforming = true;
                        }
                        if (flags.hasFlag(AIFlags.MANEUVER_TARGET) || flags.hasFlag(AIFlags.MOVEMENT_DEST)
                                || flags.hasFlag(AIFlags.PURSUING) || flags.hasFlag(AIFlags.RUN_QUICKLY)) {
                            transforming = true;
                        }
                    }
                }
                if(!isRobot && inDanger && ship.getShipAI() != null) {
                    if(Math.random() > 0.50f)
                        transforming = true;
                }                
                if (!ship.getFluxTracker().isVenting() && (armaa_utils.isKeyDoubleTapped(ship, engine) || armaa_utils.isMiddleMouseClicked(ship, engine))
                        || (ship.getFluxTracker().isVenting() && ship.getFluxLevel() != 0) && !isRobot && !transforming) {
                    if (ship.getFluxTracker().isVenting() && !isRobot) {
                        forcedToMech = true;
                    }
                    transforming = true;
                } // auto transform back if was forced
                else if (forcedToMech && isRobot && !ship.getFluxTracker().isVenting() && ship.getFluxLevel() == 0) {
                    transforming = true;
                    forcedToMech = false;
                }
                else if(ship.isFighter() && Math.random() < 0.40f && transformInterval.intervalElapsed())
                {
                    transforming = true;
                }
                if (transforming) {
                    Global.getSoundPlayer().playSound("mechmoveRev", MathUtils.getRandomNumberInRange(1.1f, 1.25f), 1f, ship.getLocation(), ship.getVelocity());
                }
            }
        }
        if(ship.isFighter())
        {
            if(Math.random() < 0.40f && transformInterval.intervalElapsed())
            {
                transforming = true;
            }
        }
        if (transforming) {
            // transforming to fighter
            if (transformLevel > 0f && !isRobot) {
                transformLevel -= amount * 2;
            } // transforming to mech
            else if (transformLevel < 1f && isRobot) {
                transformLevel += amount * 2;
            }
            if (transformLevel >= 1f) {
                transformLevel = 1f;
                transforming = false;
                isRobot = false;
            }
            if (transformLevel <= 0f) {
                transformLevel = 0f;
                isRobot = true;
                transforming = false;
                if(ship.getShield() != null)
                    engine.getCustomData().put("armaa_transformState_sArc_" + ship.getId(),ship.getShield().getArc());
            }
        }
        engine.getCustomData().put("armaa_tranformState_" + ship.getId(), isRobot);
        engine.getCustomData().put("armaa_tranformLevel_" + ship.getId(), transformLevel);
        String id = "armaa_transformationBonus_" + ship.getId();
        MutableShipStatsAPI stats = ship.getMutableStats();
        stats.getMaxSpeed().modifyMult(id, 1f - (0.50f * (1f - transformLevel)));
        // if (transformLevel >= 1f && !ship.isFighter()) {
            // if (ship.getShield() != null && ship.getShield().isOn()) {
            //    ship.getShield().toggleOff();
            // }
        // }
        // Engines
        for (ShipEngineAPI eng : ship.getEngineController().getShipEngines()) {
            //IDK If there's a better way, seems there's no engine slot ID
            if (eng != ship.getEngineController().getShipEngines().get(0)) {
                Color engCol = eng.getEngineSlot().getColor();
                if (engine.getCustomData().get("armaa_guarDualEngineCol_" + ship.getId()) != null) {
                    engCol = (Color) engine.getCustomData().get("armaa_guarDualEngineCol_" + ship.getId());
                } else {
                    engCol = new Color(engCol.getRed(), engCol.getGreen(), engCol.getBlue(), engCol.getAlpha());
                    engine.getCustomData().put("armaa_guarDualEngineCol_" + ship.getId(), engCol);
                }

                eng.getEngineSlot().setColor(new Color(engCol.getRed() / 255f, engCol.getGreen() / 255f, engCol.getBlue() / 255f, (engCol.getAlpha() / 255f) * transformLevel));
            }
        }
        ship.getMutableStats().getBallisticRoFMult().modifyPercent(id, 50f * transformLevel);
        ship.getMutableStats().getEnergyRoFMult().modifyPercent(id, 50f * transformLevel);
        ship.getMutableStats().getShieldDamageTakenMult().modifyMult(id,1f+(0.25f * transformLevel));
        if(ship.getShield() != null)
        {
            float sArc = (float)engine.getCustomData().get("armaa_transformState_sArc_" + ship.getId());            
            ship.getShield().setArc(sArc-sArc*.50f*transformLevel);
        }
        if (isRobot) {
            float mult = (1f - transformLevel);
            int level = ship.getCaptain() != null ? Math.min(15, ship.getCaptain().getStats().getLevel()) : 1;
            ship.getMutableStats().getMaxTurnRate().modifyPercent(id, 50f * mult);
            ship.getMutableStats().getBallisticWeaponDamageMult().modifyPercent(id, 50f * mult);
            ship.getMutableStats().getEnergyWeaponDamageMult().modifyPercent(id, 50f * mult);
            if (lMissile != null) {
                lMissile.setForceNoFireOneFrame(true);
            }
            if (rMissile != null) {
                rMissile.setForceNoFireOneFrame(true);
            }
            stats.getArmorDamageTakenMult().modifyMult(id, 1f - (0.50f * mult));
            stats.getEngineDamageTakenMult().modifyMult(id, 1f - (0.50f * mult));
            //stats.getFluxDissipation().modifyPercent(id,50f*mult);

            stats.getAcceleration().modifyMult(id, 1f - (0.50f * mult));
            stats.getDeceleration().modifyPercent(id, 50f * mult);
            stats.getTurnAcceleration().modifyPercent(id, 50f * mult);
        } else {
            if (lMissile != null) {
                if (lMissile.isDisabled()) {
                    lMissile.repair();
                }
            }
            if (rMissile != null) {
                if (rMissile.isDisabled()) {
                    rMissile.repair();
                }
            }

            float mult = transformLevel;
            ship.getMutableStats().getProjectileSpeedMult().modifyPercent(id, 50f * mult);
            ship.getMutableStats().getMaxTurnRate().modifyMult(id, 1f - (0.50f * mult));
            ship.getMutableStats().getBallisticWeaponDamageMult().unmodify(id);
            ship.getMutableStats().getEnergyWeaponDamageMult().unmodify(id);
            stats.getArmorDamageTakenMult().unmodify(id);
            //stats.getHullDamageTakenMult().unmodify(id);
            stats.getEngineDamageTakenMult().unmodify(id);
            //stats.getFluxDissipation().unmodify(id);

            stats.getAcceleration().modifyPercent(id, 50f);
            stats.getDeceleration().modifyMult(id, 1f - (0.50f * mult));
            stats.getTurnAcceleration().modifyMult(id, 1f - (0.50f * mult));
            ship.getEngineController().extendFlame(null, 1.5f * mult, 0.70f * mult, 1f);
        }
        if (ship.getEngineController().isAccelerating()) {
            if (overlap > (MAX_OVERLAP - 0.1f)) {
                overlap = MAX_OVERLAP;
            } else {
                overlap = Math.min(MAX_OVERLAP, overlap + ((MAX_OVERLAP - overlap) * amount * 5));
            }
        } else if (ship.getEngineController().isDecelerating() || ship.getEngineController().isAcceleratingBackwards()) {
            if (overlap < -(MAX_OVERLAP - 0.1f)) {
                overlap = -MAX_OVERLAP;
            } else {
                overlap = Math.max(-MAX_OVERLAP, overlap + ((-MAX_OVERLAP + overlap) * amount * 5));
            }
        } else {
            if (Math.abs(overlap) < 0.1f) {
                overlap = 0;
            } else {
                overlap -= (overlap / 2) * amount * 3;
            }
        }

        float sineA = 0;
        float sineC = MagicAnim.smoothNormalizeRange(transformLevel, 0.0f, 1f);
        float sinceB = MagicAnim.smoothNormalizeRange(sineC, 0.8f, 1f);
        if (isRobot) {
            sinceB = MagicAnim.smoothNormalizeRange(transformLevel, 0.7f, 0.1f);
        }
        float global = ship.getFacing();
        float aim = MathUtils.getShortestRotation(global, gun.getCurrAngle());
        if (ship.areAnyEnemiesInRange()) {
            sineD += amount;
        } else {
            sineD -= amount;
        }
        if (sineD < 0f) {
            sineD = 0f;
        } else if (sineD > 1f) {
            sineD = 1f;
        }
        if(!MagicRender.screenCheck(0.1f, weapon.getLocation()))
        {
            return;
        }
        animUpdateInterval.advance(amount);
        if(!animUpdateInterval.intervalElapsed())
            return;
        if (gun != null) {
            gun.getSprite().setCenterY(ogPosRArm.getY() + 6 * sineC);
            gun.getSprite().setCenterX(ogPosRArm.getX() + 4 * sineC);
            if (realGun != null) {
                if (!isRobot) {
                    if (realGun.getCurrAngle() < global - 5) {
                        realGun.setCurrAngle(global - 5);
                    } else if (realGun.getCurrAngle() > global + 5) {
                        realGun.setCurrAngle(global + 5);
                    }
                } else {
                    if (realGun.getCooldown() > 0) {
                        gun.getSprite().setCenterY(originalRArmPos + (2 * realGun.getCooldownRemaining() / realGun.getCooldown()));
                    }
                }
            }
        }
        float recoil = realGun != null && realGun.getCooldown() > 0 ? (2 * (realGun.getCooldownRemaining() / realGun.getCooldown()) * (1f - sineC)) : 1 * 0;        
        if (pauldronR != null) {
            pauldronR.setCurrAngle(global + (sineA + sineC) * TORSO_OFFSET * 0.5f + (aim * (1f - transformLevel)) * 0.75f + RIGHT_ARM_OFFSET * 0.5f);
            pauldronR.getSprite().setCenterY(ogPosR.getY() - 6 * sineC + recoil);
            pauldronR.getSprite().setCenterX(ogPosR.getX() + (4 * sineC));
            wingR.setCurrAngle(pauldronR.getCurrAngle());
            wingR.getSprite().setCenterY(ogPosRWing.getY() - 14 * sineC);
            wingR.getSprite().setCenterX(ogPosRWing.getX() + 8 * sineC);
        }

        if (armL != null) {
            armL.setCurrAngle(
                    global
                    + ((aim + LEFT_ARM_OFFSET) * sineD) * (1f - transformLevel)
                    + ((overlap + aim * 0.25f) * (1 - sineD)) * (1f - transformLevel)
            );
            float recoilOffset = sineD >= 1f ? recoil : 0f;
            armL.getSprite().setCenterY(ogPosLArm.getY() + 15f * sineC + recoilOffset);
            armL.getSprite().setCenterX(ogPosLArm.getX() - 5 * sineC);
            Color col = armL.getSprite().getColor();
            float red = col.getRed() / 255f;
            float green = col.getGreen() / 255f;
            float blue = col.getBlue() / 255f;
            float normalizedAlpha = 1f;
            float newAlpha = Math.max(0f, Math.min(1f, normalizedAlpha * (1f - transformLevel)));
            Color newCol = new Color(red, green, blue, newAlpha);
            armL.getSprite().setColor(newCol);
            head.getSprite().setColor(new Color((red) * (1f - transformLevel), (green) * (1f - transformLevel), (blue) * (1f - transformLevel), newAlpha));
            wingR.getSprite().setColor(new Color((red) * (transformLevel), (green) * (transformLevel), (blue) * (transformLevel), transformLevel));
            wingL.getSprite().setColor(new Color((red) * (transformLevel), (green) * (transformLevel), (blue) * (transformLevel), transformLevel));
            gun.getSprite().setColor(new Color((red) * (1f - transformLevel), (green) * (1f - transformLevel), (blue) * (1f - transformLevel), newAlpha));
            gunF.getSprite().setColor(new Color((red) * (transformLevel), green * transformLevel, blue * transformLevel, sineC));
            //gunF.getSprite().setCenterY(ogPosGunF.getY()-4*sineC);
            gunF.getSprite().setCenterX(ogPosGunF.getX() - 6 * (1f - sineC));
            gunF.getSprite().setCenterY(ogPosGunF.getY() + 0 * (1f - sineC));
        }

        if (pauldronL != null) {
            pauldronL.setCurrAngle(global + MathUtils.getShortestRotation(ship.getFacing(), armL.getCurrAngle()) * (1f - transformLevel) * 0.6f + (sineA + sineC) * 90f);
            float recoilOffset = sineD >= 1f ? recoil : 0f;            
            pauldronL.getSprite().setCenterY(ogPosL.getY() - 6 * sineC + recoilOffset);
            pauldronL.getSprite().setCenterX(ogPosL.getX() - 4 * sineC);
            wingL.setCurrAngle(pauldronL.getCurrAngle());
            wingL.getSprite().setCenterY(ogPosLWing.getY() - 14 * sineC);
            wingL.getSprite().setCenterX(ogPosLWing.getX() - 8 * sineC);
        }

        ship.setSprite("guardual", "armaa_guardual" + (int) Math.round(6 * (1f - transformLevel)));
        if (lMissile != null && lMissile.getMissileRenderData() != null) {
            Color col = armL.getSprite().getColor();
            float red = col.getRed() / 255f;
            float green = col.getGreen() / 255f;
            float blue = col.getBlue() / 255f;
            for (int i = 0; i < lMissile.getMissileRenderData().size(); i++) {
                lMissile.getMissileRenderData().get(i).getSprite().setCenterY(4 + ogPosLMissile.getY() - 8 * (1f - sineC));
                lMissile.getMissileRenderData().get(i).getSprite().setCenterX(ogPosLMissile.getX() - 2 * (1f - sineC));
                lMissile.getMissileRenderData().get(i).getSprite().setColor(new Color((red) * (transformLevel), green * transformLevel, blue * transformLevel, sineC));
            }
        }
        if (rMissile != null && rMissile.getMissileRenderData() != null) {
            Color col = armL.getSprite().getColor();
            float red = col.getRed() / 255f;
            float green = col.getGreen() / 255f;
            float blue = col.getBlue() / 255f;
            for (int i = 0; i < rMissile.getMissileRenderData().size(); i++) {
                rMissile.getMissileRenderData().get(i).getSprite().setCenterY(4 + ogPosRMissile.getY() - 8 * (1f - sineC));
                rMissile.getMissileRenderData().get(i).getSprite().setCenterX(ogPosRMissile.getX() + 2 * (1f - sineC));
                rMissile.getMissileRenderData().get(i).getSprite().setColor(new Color((red) * (transformLevel), green * transformLevel, blue * transformLevel, sineC));
            }
        }
        for (WeaponAPI w : ship.getAllWeapons()) {
            List<String> ids = new ArrayList<String>();
            ids.add("WS0002");
            ids.add("WS0004");
            if (ids.contains(w.getSlot().getId())) {
                Color invis = new Color(0f, 0f, 0f, 0f);
                WeaponAPI trueWeapon = w.getSlot().getId().equals("WS0002") ? wingL : wingR;
                w.ensureClonedSpec();
                List<Vector2f> ogSpec = new ArrayList<Vector2f>(w.getSpec().getTurretFireOffsets());
                List<Float> ogAngle = new ArrayList<Float>(w.getSpec().getTurretAngleOffsets());
                int size = w.getSpec().getTurretFireOffsets().size();
                int posL = size > 2 ? size - 1 : 0;
                if (size > 1 && !cleared) {
                    w.getSpec().getHardpointFireOffsets().clear();
                    w.getSpec().getHardpointAngleOffsets().clear();
                    for (int i = 0; i < size; i++) {
                        if (w.getSlot().getId().equals("WS0002")) {
                            w.getSpec().getHardpointFireOffsets().add(ogSpec.get(posL));
                            w.getSpec().getHardpointAngleOffsets().add(ogAngle.get(posL));
                        } else {
                            w.getSpec().getHardpointFireOffsets().add(ogSpec.get(size - 1));
                            w.getSpec().getHardpointAngleOffsets().add(ogAngle.get(size - 1));
                        }
                    }
                }
                if (w.getSprite() != null) {
                    w.getSprite().setColor(invis);
                }
                if (w.getUnderSpriteAPI() != null) {
                    w.getUnderSpriteAPI().setColor(invis);
                }
                if (w.getBarrelSpriteAPI() != null) {
                    w.getBarrelSpriteAPI().setColor(invis);
                }
                if (w.getGlowSpriteAPI() != null) {
                    w.getGlowSpriteAPI().setColor(invis);
                }
            }
        }
        cleared = true;
        ship.syncWeaponDecalsWithArmorDamage();
    }
}
