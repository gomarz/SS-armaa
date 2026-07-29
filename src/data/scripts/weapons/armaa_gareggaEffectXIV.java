package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import java.util.List;
import org.lazywizard.lazylib.MathUtils;

public class armaa_gareggaEffectXIV implements EveryFrameWeaponEffectPlugin {

    private boolean runOnce = false;
    private ShipAPI ship;
    private WeaponAPI armL, armR, shoulderR, shoulderL, turretL, decoL;
    private float originalShoulderPos = 0;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (!runOnce) {
            runOnce = true;
            ship = weapon.getShip();
            if (!ship.getHullSpec().getBaseHullId().contains("garegga")) {
                return;
            }
            for (WeaponAPI w : ship.getAllWeapons()) {
                switch (w.getSlot().getId()) {
                    case "A_GUN":
                        if (armL == null) {
                            armL = w;
                        }
                        break;
                    case "MAIN_GUN":
                        if (armL == null) {
                            armL = w;
                        }
                        break;
                    case "A_GUN2":
                        if (armR == null) {
                            armR = w;
                        }
                        break;
                    case "E_RSHOULDER":
                        shoulderR = w;
                        originalShoulderPos = shoulderR.getSprite().getCenterY();
                        break;
                    case "E_LSHOULDER":
                        shoulderL = w;
                        shoulderL.getSprite().getCenterY();
                        break;
                    case "WS0003":
                        break;

                    case "WS0001":
                        if (turretL == null) {
                            turretL = w;
                        }
                        break;
                    case "E_DECO":
                        if (decoL == null) {
                            decoL = w;
                        }
                        break;
                    case "E_DECO_R":
                        break;
                    //break;					//break;
                }
            }
        }

        if (armL == null) {
            return;
        }

        float sineA = 0, sinceB = 0;

        float global = ship.getFacing();
        float aim = MathUtils.getShortestRotation(global, weapon.getCurrAngle());
        //*End animation math stuff

        //Right arm + shoulder
        if (!armL.getSlot().getId().equals("MAIN_GUN")) {
            weapon.setCurrAngle(ship.getFacing() + MathUtils.getShortestRotation(ship.getFacing(), armL.getCurrAngle()) * 0.7f);
            weapon.getSprite().setCenterY(originalShoulderPos + (2 * armL.getCooldownRemaining() / armL.getCooldown()));
        }
        //turretL.setCurrAngle(ship.getFacing() + MathUtils.getShortestRotation(ship.getFacing(),armL.getCurrAngle())*0.7f);
        if (armR != null) {
            shoulderR.setCurrAngle(ship.getFacing() + MathUtils.getShortestRotation(ship.getFacing(), armR.getCurrAngle()) * 0.7f);
            shoulderR.getSprite().setCenterY(originalShoulderPos + (2 * armR.getCooldownRemaining() / armR.getCooldown()));
        }
        ship.syncWeaponDecalsWithArmorDamage();
        List<ShipAPI> children = ship.getChildModulesCopy();
        if (children != null) {
            for (ShipAPI module : children) {
                module.ensureClonedStationSlotSpec();

                if (module.getStationSlot() != null) {
                    module.blockCommandForOneFrame(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK);
                    if (ship.getFluxTracker().isOverloaded() && !module.getFluxTracker().isOverloaded()) {
                        module.getFluxTracker().beginOverloadWithTotalBaseDuration(ship.getFluxTracker().getOverloadTimeRemaining());
                    }
                }
            }
        }
    }
}
