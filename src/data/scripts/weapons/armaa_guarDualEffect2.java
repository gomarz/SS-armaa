package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import data.scripts.util.armaa_utils;
import org.magiclib.util.MagicAnim;
import java.awt.Color;
import java.util.*;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.util.IntervalUtil;
import org.magiclib.util.MagicRender;

// this is disgusting
// TODO: REFACTOR
public class armaa_guarDualEffect2 implements EveryFrameWeaponEffectPlugin {

    private boolean runOnce = false, cleared = false;
    private ShipAPI ship;
    private float sineD = 0f;
    // 1 = Plane, 0 = Robot
    private float transformLevel = 0f, originalRArmPos = 0f;
    private boolean isRobot = true, transforming = false, forcedToMech, transformBlock = false;
    ;
private float transformCooldown = 0f;
    private static final float TRANSFORM_COOLDOWN_TIME = 1f; // commitment window; tune 0.75-1.0
    private WeaponAPI head, armL, wingR, wingL, pauldronL, pauldronR, gun, gunF, realGun, lMissile, rMissile, broadLMissile, broadRMissile;
    private float overlap = 0;
    private IntervalUtil interval2 = new IntervalUtil(1f, 1f);
    private IntervalUtil forceTransformTimer = new IntervalUtil(0.5f, 5f);
    private final IntervalUtil animUpdateInterval = new IntervalUtil(0.033f, 0.05f);
    private Vector2f ogPosL, ogRArmSize, ogPosR, ogPosRArm, ogPosLArm, ogPosLWing, ogPosRWing, ogPosGunF, ogPosLMissile, ogPosRMissile;
    private final float TORSO_OFFSET = -150, LEFT_ARM_OFFSET = -90, RIGHT_ARM_OFFSET = -25, MAX_OVERLAP = 10;
    private Vector2f ogGunFSize;

    public void init() {
        runOnce = true;
        for (WeaponAPI w : ship.getAllWeapons()) {
            switch (w.getSlot().getId()) {
                case "C_ARML":
                    if (armL == null) {
                        armL = w;
                        ogPosLArm = new Vector2f(armL.getSprite().getCenterX(), armL.getSprite().getCenterY());
                        new Vector2f(armL.getSprite().getWidth(), armL.getSprite().getHeight());
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
                        pauldronL.getSprite().getCenterY();
                        ogPosL = new Vector2f(pauldronL.getSprite().getCenterX(), pauldronL.getSprite().getCenterY());
                    }
                    break;
                case "D_PAULDRONR":
                    if (pauldronR == null) {
                        pauldronR = w;
                        pauldronL.getSprite().getCenterY();
                        ogPosR = new Vector2f(pauldronR.getSprite().getCenterX(), pauldronR.getSprite().getCenterY());
                    }
                    break;
                case "E_HEAD":
                    if (head == null) {
                        head = w;
                        if (ship.getOwner() == -1) {
                            head.getSprite().setColor(new Color(0, 0, 0, 0));
                        }
                    }
                    break;
                case "A_GUN":
                    if (gun == null) {
                        gun = w;
                        originalRArmPos = w.getSprite().getCenterY();
                        ogPosRArm = new Vector2f(gun.getSprite().getCenterX(), gun.getSprite().getCenterY());
                        ogRArmSize = new Vector2f(gun.getSprite().getWidth(), gun.getSprite().getHeight());
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
                        if (gunF.getAnimation() != null) {
                            gunF.getAnimation().setFrame(2);
                        }

                        ogPosGunF = new Vector2f(gunF.getSprite().getCenterX(), gunF.getSprite().getCenterY());
                        ogGunFSize = new Vector2f(gunF.getSprite().getWidth(), gunF.getSprite().getHeight());
                    }
                    break;
                case "WS0002":
                    if (lMissile == null) {
                        lMissile = w;
                        ogPosLMissile = new Vector2f(lMissile.getSprite().getCenterX(), lMissile.getSprite().getCenterY());
                    }
                    break;
                case "WS0004":
                    if (rMissile == null) {
                        rMissile = w;
                        ogPosRMissile = new Vector2f(rMissile.getSprite().getCenterX(), rMissile.getSprite().getCenterY());
                    }
                    break;
                case "WS0003":
                    if (broadLMissile == null) {
                        broadLMissile = w;
                    }
                    break;
                case "WS0005":
                    if (broadRMissile == null) {
                        broadRMissile = w;
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
        if (!runOnce) {
            if (ship.getShield() != null) {
                engine.getCustomData().put("armaa_transformState_sArc_" + ship.getId(), ship.getShield().getArc());
            }
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
        if (!Global.getCombatEngine().isEntityInPlay(ship) || engine.isPaused()) {
            return;
        }

        if (ship == engine.getPlayerShip() && transformCooldown > 0f) {
            engine.maintainStatusForPlayerShip(
                    "armaa_transformCD",
                    null, // or a transform icon path
                    "Transform Recharging",
                    String.format("Ready in %.1fs", transformCooldown),
                    true); // true = isDebuff styling (greyed/negative), signals "can't yet"
        }
        boolean transformNow = engine.getCustomData().get("armaa_transformNow_" + ship.getId()) != null
                ? true : false;
        interval2.advance(amount);
        if (interval2.intervalElapsed()) {
            ship.syncWeaponDecalsWithArmorDamage();
        }
        if (transformCooldown > 0f) {
            transformCooldown -= amount;
        }
        if (transformBlock) {
            forceTransformTimer.advance(amount);
        }
        if (forceTransformTimer.intervalElapsed() && transformBlock) {
            transformBlock = false;
            engine.getCustomData().remove("armaa_transformNow_" + ship.getId());
        }
        if (!transformBlock && ship.isAlive()) {
            if (!ship.getFluxTracker().isOverloaded() && !transforming) {

                // --- forced transform
                if (transformNow && ship.getShipAI() != null) {
                    transformNow = false;
                    transformBlock = true;
                    transforming = true;
                }

                // --- consume the AI decision script's request
                if (!transforming && ship.getShipAI() != null) {
                    Object req = engine.getCustomData().get("armaa_transformAI_request_" + ship.getId());
                    if (req instanceof Boolean && (Boolean) req) {
                        transforming = true;
                    }
                }
                // clear the request so it's one-shot (consumed or not)
                engine.getCustomData().remove("armaa_transformAI_request_" + ship.getId());
                boolean playerInput = (armaa_utils.isKeyDoubleTapped(ship, engine) || armaa_utils.isMiddleMouseClicked(ship, engine))
                        && transformCooldown <= 0f;
                // --- player-input control
                if (!ship.getFluxTracker().isVenting() && (playerInput)
                        || (ship.getFluxTracker().isVenting() && ship.getFluxLevel() != 0) && !isRobot && !transforming) {
                    if (ship.getFluxTracker().isVenting() && !isRobot) {
                        forcedToMech = true;
                    }

                    transforming = true;
                    if (playerInput) {
                        transformCooldown = TRANSFORM_COOLDOWN_TIME; // start the commitment window on deliberate flicks only
                    }
                } else if (forcedToMech && isRobot && !ship.getFluxTracker().isVenting() && ship.getFluxLevel() == 0) {
                    transforming = true;
                    forcedToMech = false;
                }

                if (transforming) {
                    Global.getSoundPlayer().playSound("mechmoveRev", MathUtils.getRandomNumberInRange(1.1f, 1.25f), 1f, ship.getLocation(), ship.getVelocity());
                }
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
                //if (ship.getShield() != null) {
                //    engine.getCustomData().put("armaa_transformState_sArc_" + ship.getId(), ship.getShield().getArc());
                //}
            }
        }
        engine.getCustomData().put("armaa_tranformState_" + ship.getId(), isRobot);
        engine.getCustomData().put("armaa_tranformLevel_" + ship.getId(), transformLevel);
        engine.getCustomData().put("armaa_tranforming_" + ship.getId(), transforming);
        String id = "armaa_transformationBonus_" + ship.getId();
        MutableShipStatsAPI stats = ship.getMutableStats();

        // Pull this hull's tunables (no more hardcoded 50f). Defined once in the variable-unit
        // hullmod; tooltip reads the same source so display and applied stats can't drift.
        data.hullmods.armaa_variableUnit.Bonuses b
                = data.hullmods.armaa_variableUnit.getBonuses(ship.getHullSpec().getBaseHullId());

        // top speed: full at fighter (transformLevel 1), reduced toward mech (transformLevel 0)
        stats.getMaxSpeed().modifyMult(id, 1f - ((1f - b.speedMalusAtMech) * (1f - transformLevel)));

        // gradient stats: scale with transformLevel (full toward fighter, zero at mech)
        ship.getMutableStats().getBallisticRoFMult().modifyPercent(id, b.rofPct * transformLevel);
        ship.getMutableStats().getEnergyRoFMult().modifyPercent(id, b.rofPct * transformLevel);
        ship.getMutableStats().getShieldDamageTakenMult().modifyMult(id, 1f + (b.shieldDamageTakenBonus * transformLevel));
        if (ship.getShield() != null) {
            float sArc = (float) engine.getCustomData().get("armaa_transformState_sArc_" + ship.getId());
            ship.getShield().setArc(sArc - sArc * b.shieldArcReductionMult * transformLevel);
        }
        if (isRobot) {
            // BETA / mech: full at transformLevel 0, so scale by mult = (1 - transformLevel)
            float mult = (1f - transformLevel);
            ship.getMutableStats().getMaxTurnRate().modifyPercent(id, b.betaTurnRatePct * mult);
            ship.getMutableStats().getBallisticWeaponDamageMult().modifyPercent(id, b.betaWeaponDamagePct * mult);
            ship.getMutableStats().getEnergyWeaponDamageMult().modifyPercent(id, b.betaWeaponDamagePct * mult);
            if (lMissile != null) {
                lMissile.setForceNoFireOneFrame(true);
            }
            if (rMissile != null) {
                rMissile.setForceNoFireOneFrame(true);
            }
            if (armL != null && !armL.isDecorative()) {
                if (armL.isDisabled()) {
                    armL.repair();
                }
            }

            // tankiness: lerp the damage-taken mult from 1.0 (no bonus) toward the configured mult
            stats.getArmorDamageTakenMult().modifyMult(id, 1f - ((1f - b.betaArmorDamageTakenMult) * mult));
            stats.getEngineDamageTakenMult().modifyMult(id, 1f - ((1f - b.betaEngineDamageTakenMult) * mult));

            stats.getAcceleration().modifyMult(id, 1f - ((1f - b.betaAccelMult) * mult)); // mech sluggish accel
            stats.getDeceleration().modifyPercent(id, b.betaDecelPct * mult);
            stats.getTurnAcceleration().modifyPercent(id, b.betaTurnAccelPct * mult);
        } else {
            if (broadLMissile != null) {
                broadLMissile.setForceNoFireOneFrame(true);
            }
            if (broadRMissile != null) {
                broadRMissile.setForceNoFireOneFrame(true);
            }
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
            if (armL != null && !armL.isDecorative()) {
                armL.setForceNoFireOneFrame(true);
            }
            // ALPHA / fighter: full at transformLevel 1, so scale by mult = transformLevel
            float mult = transformLevel;
            ship.getMutableStats().getProjectileSpeedMult().modifyPercent(id, b.alphaProjectileSpeedPct * mult);
            ship.getMutableStats().getMaxTurnRate().modifyMult(id, 1f - ((1f - b.alphaTurnRateMult) * mult));
            ship.getMutableStats().getBallisticWeaponDamageMult().unmodify(id);
            ship.getMutableStats().getEnergyWeaponDamageMult().unmodify(id);
            stats.getArmorDamageTakenMult().unmodify(id);
            //stats.getHullDamageTakenMult().unmodify(id);
            stats.getEngineDamageTakenMult().unmodify(id);
            //stats.getFluxDissipation().unmodify(id);

            stats.getAcceleration().modifyPercent(id, b.alphaAccelPct); // fighter nimble accel
            stats.getDeceleration().modifyMult(id, 1f - ((1f - b.alphaDecelMult) * mult));
            stats.getTurnAcceleration().modifyMult(id, 1f - ((1f - b.alphaTurnAccelMult) * mult));
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
        if (!MagicRender.screenCheck(0.1f, weapon.getLocation())) {
            return;
        }
        Color col = armL.getSprite().getColor();
        float red = col.getRed() / 255f;
        float green = col.getGreen() / 255f;
        float blue = col.getBlue() / 255f;
        if (sineC > 0 && lMissile != null && rMissile != null) {

            //literally dont know any other way to do this
            WeaponAPI lWep, rWep;
            if (engine.getCustomData().get("armaa_lfakeWep_" + ship.getId()) instanceof WeaponAPI weaponAPI) {
                lWep = weaponAPI;
            } else {
                lWep = engine.createFakeWeapon(ship, lMissile.getSpec().getWeaponId());
                engine.getCustomData().put("armaa_lfakeWep_" + ship.getId(), lWep);
            }
            if (engine.getCustomData().get("armaa_rfakeWep_" + ship.getId()) instanceof WeaponAPI weaponAPI) {
                rWep = weaponAPI;
            } else {
                rWep = engine.createFakeWeapon(ship, rMissile.getSpec().getWeaponId());
                engine.getCustomData().put("armaa_rfakeWep_" + ship.getId(), rWep);
            }
            Color wepCol = new Color((red) * (transformLevel), green * transformLevel, blue * transformLevel, sineC);
            // Offset vector in missile local space (e.g., shift 4 units forward)
            float offsetAmount = 0f - 8f * (1f - sineC);
            float offsetAmountR = 0f + 8f * (1f - sineC);
            Vector2f offset = new Vector2f(6f, offsetAmount);
            Vector2f offsetR = new Vector2f(6f, offsetAmountR);
            offset = VectorUtils.rotate(offset, ship.getFacing());
            offsetR = VectorUtils.rotate(offsetR, ship.getFacing());
            Vector2f renderPosL = Vector2f.add(lMissile.getLocation(), offset, null);
            Vector2f renderPosR = Vector2f.add(rMissile.getLocation(), offsetR, null);
            if (lMissile.getAmmo() > 0 && lWep.getMissileRenderData() != null) {
                MagicRender.singleframe(
                        lWep.getMissileRenderData().get(0).getSprite(),
                        renderPosL,
                        new Vector2f(lWep.getMissileRenderData().get(0).getSprite().getWidth(), lWep.getMissileRenderData().get(0).getSprite().getHeight()),
                        ship.getFacing() - 90f,
                        new Color(wepCol.getRed() / 255f, wepCol.getBlue() / 255f, wepCol.getGreen() / 255f, (wepCol.getAlpha() / 255f) * (1 - lMissile.getCooldownRemaining() / lMissile.getCooldown())),
                        false,
                        CombatEngineLayers.FRIGATES_LAYER
                );
            }
            if (rMissile.getAmmo() > 0 && rWep.getMissileRenderData() != null) {
                MagicRender.singleframe(
                        rWep.getMissileRenderData().get(0).getSprite(),
                        renderPosR,
                        new Vector2f(rWep.getMissileRenderData().get(0).getSprite().getWidth(), rWep.getMissileRenderData().get(0).getSprite().getHeight()),
                        ship.getFacing() - 90f,
                        new Color(wepCol.getRed() / 255f, wepCol.getBlue() / 255f, wepCol.getGreen() / 255f, (wepCol.getAlpha() / 255f) * (1 - rMissile.getCooldownRemaining() / rMissile.getCooldown())),
                        false,
                        CombatEngineLayers.FRIGATES_LAYER
                );
            }
        }
        animUpdateInterval.advance(amount);
        if (!animUpdateInterval.intervalElapsed()) {
            return;
        }
        if (gun != null) {
            gun.getSprite().setCenterY(ogPosRArm.getY() - 24 * sineC);
            gun.getSprite().setCenterX(ogPosRArm.getX() - 25 * sineC);
            gun.getSprite().setSize(ogRArmSize.x * (1f - sineC), ogRArmSize.y * (1f - sineC));
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
        sinceB = MagicAnim.smoothNormalizeRange(armL.getChargeLevel(), 0.3f, 1f);
        if (armL != null) {
            if (armL.isDecorative()) {
                armL.setCurrAngle(
                        global
                        + ((aim + LEFT_ARM_OFFSET) * sineD) * (1f - transformLevel)
                        + ((overlap + aim * 0.25f) * (1 - sineD)) * (1f - transformLevel)
                );
                float recoilOffset = sineD >= 1f ? recoil : 0f;
                armL.getSprite().setCenterY(ogPosLArm.getY() + 15f * sineC + recoilOffset);
                armL.getSprite().setCenterX(ogPosLArm.getX() - 5 * sineC);
            } else {
                //armL.setCurrAngle(armL.getCurrAngle() * (1f - transformLevel));

                armL.getSprite().setCenterY((ogPosLArm.getY() - (10 * sinceB)) + 15f * sineC);
                armL.getSprite().setCenterX(ogPosLArm.getX() - 5 * sineC);
            }
            float normalizedAlpha = 1f;
            float newAlpha = Math.max(0f, Math.min(1f, normalizedAlpha * (1f - transformLevel)));
            Color newCol = new Color(red, green, blue, newAlpha);
            armL.getSprite().setColor(newCol);
            armL.getSprite().setAlphaMult((1f - sineC));
            head.getSprite().setColor(new Color((red) * (1f - transformLevel), (green) * (1f - transformLevel), (blue) * (1f - transformLevel), newAlpha));
            wingR.getSprite().setColor(new Color((red) * (transformLevel), (green) * (transformLevel), (blue) * (transformLevel), transformLevel));
            wingL.getSprite().setColor(new Color((red) * (transformLevel), (green) * (transformLevel), (blue) * (transformLevel), transformLevel));
            gun.getSprite().setColor(new Color((red) * (1f - transformLevel), (green) * (1f - transformLevel), (blue) * (1f - transformLevel), newAlpha));
            gunF.getSprite().setColor(new Color((red) * (transformLevel), green * transformLevel, blue * transformLevel, sineC));
            //gunF.getSprite().setCenterY(ogPosGunF.getY()-4*sineC);
            gunF.getSprite().setCenterX(ogPosGunF.getX() - 27 * (1f - sineC));
            gunF.getSprite().setCenterY(ogPosGunF.getY() - 42 * (1f - sineC));
            gunF.getSprite().setSize(ogGunFSize.x * sineC, ogGunFSize.y * sineC);

        }

        if (pauldronL != null) {
            pauldronL.setCurrAngle(global + MathUtils.getShortestRotation(ship.getFacing(), armL.getCurrAngle()) * (1f - transformLevel) * 0.6f + (sineA + sineC) * 90f);
            pauldronL.getSprite().setCenterY((ogPosL.getY() - (8 * sinceB)) - 6 * sineC);
            pauldronL.getSprite().setCenterX(ogPosL.getX() - 4 * sineC);
            wingL.setCurrAngle(pauldronL.getCurrAngle());
            wingL.getSprite().setCenterY(ogPosLWing.getY() - 14 * sineC);
            wingL.getSprite().setCenterX(ogPosLWing.getX() - 8 * sineC);
        }
        String spriteString = "guardual";
        if (ship.getHullSpec().getHullId().contains("bs")) {
            spriteString += "_bs";
        }
        else if (ship.getHullSpec().getHullId().contains("_s")) {
            spriteString += "_s";
        }
        int frame = (int) Math.round(6 * (1f - transformLevel));

// Check if a paintjob is active (the id we stored in combat custom data when we stripped the tag)
        Object pjObj = Global.getCombatEngine().getCustomData().get("armaa_guarDualPaintjob_" + ship.getId());
        String spriteKey;
        if (pjObj instanceof String) {
            String paintjobId = (String) pjObj;
            // painted frame key, e.g. "armaa_guardual_{paintjobId}_3"
            spriteString += "_" + paintjobId;
        }
        // no paintjob: base frames
        spriteKey = "armaa_guardual" + frame;
        //Global.getLogger(this.getClass()).info(spriteKey + spriteString);
        ship.setSprite(spriteString, spriteKey);
        //Global.getLogger(this.getClass()).info(spriteKey + spriteString);
        if (lMissile != null && lMissile.getMissileRenderData() != null) {
            for (int i = 0; i < lMissile.getMissileRenderData().size(); i++) {
                lMissile.getMissileRenderData().get(i).getSprite().setCenterY(4 + ogPosLMissile.getY() - 8 * (1f - sineC));
                lMissile.getMissileRenderData().get(i).getSprite().setCenterX((ogPosLMissile.getX()) * (1f - sineC));
                lMissile.getMissileRenderData().get(i).getSprite().setColor(new Color((red) * (transformLevel), green * transformLevel, blue * transformLevel, 0f));
            }
        }
        if (rMissile != null && rMissile.getMissileRenderData() != null) {
            for (int i = 0; i < rMissile.getMissileRenderData().size(); i++) {
                rMissile.getMissileRenderData().get(i).getSprite().setCenterY(4 + ogPosRMissile.getY() - 8 * (1f - sineC));
                rMissile.getMissileRenderData().get(i).getSprite().setCenterX((ogPosRMissile.getX() - ogPosRMissile.getX()) * (1f - sineC));
                rMissile.getMissileRenderData().get(i).getSprite().setColor(new Color((red) * (transformLevel), green * transformLevel, blue * transformLevel, 0f));
            }
        }
        List<String> ids = new ArrayList<String>();
        ids.add("WS0002");
        ids.add("WS0003");
        ids.add("WS0005");
        ids.add("WS0004");
        for (WeaponAPI w : ship.getAllWeapons()) {
            if (ids.contains(w.getSlot().getId())) {
                Color invis = new Color(0f, 0f, 0f, 0f);
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
                        } else if (w.getSlot().getId().equals("WS0004")) {
                            w.getSpec().getHardpointFireOffsets().add(ogSpec.get(size - 1));
                            w.getSpec().getHardpointAngleOffsets().add(ogAngle.get(size - 1));
                        }
                    }
                }
                if (w.getSprite() != null) {
                    w.getSprite().setSize(1f, 1f);
                    w.getSprite().setColor(invis);
                }
                if (w.getUnderSpriteAPI() != null) {
                    w.getUnderSpriteAPI().setSize(1f, 1f);
                    w.getUnderSpriteAPI().setColor(invis);
                }
                if (w.getBarrelSpriteAPI() != null) {
                    w.getBarrelSpriteAPI().setSize(1f, 1f);
                    w.getBarrelSpriteAPI().setColor(invis);
                }
                if (w.getGlowSpriteAPI() != null) {
                    w.getGlowSpriteAPI().setSize(1f, 1f);
                    w.getGlowSpriteAPI().setColor(invis);
                }
                if (w.getSlot().getId().equals("WS0003") || w.getSlot().getId().equals("WS0005")) {
                    if (w.getMissileRenderData() != null) {
                        for (int i = 0; i < w.getMissileRenderData().size(); i++) {
                            w.getMissileRenderData().get(i).getSprite().setSize(1f, 1f);
                        }
                    }
                }
            }
        }
        cleared = true;
        ship.syncWeaponDecalsWithArmorDamage();
    }
}
