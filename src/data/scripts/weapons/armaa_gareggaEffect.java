package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import org.lazywizard.lazylib.MathUtils;

public class armaa_gareggaEffect implements EveryFrameWeaponEffectPlugin {

    private boolean runOnce = false;
    private ShipAPI ship;
    private WeaponAPI armL, armR, shoulderR, head, headGlow;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {

        if (!runOnce) {
            runOnce = true;
            ship = weapon.getShip();
            ship.getAIFlags();
            for (WeaponAPI w : ship.getAllWeapons()) {
                switch (w.getSlot().getId()) {
                    case "E_LARM":
                        if (armL == null) {
                            armL = w;
                        }
                        break;
                    case "E_RARM":
                        if (armR == null) {
                            armR = w;
                        }
                        break;
                    case "E_RSHOULDER":
                        shoulderR = w;
                        break;
                    case "E_LSHOULDER":
                        break;

                    case "E_HEAD_GLOW":
                        if (headGlow == null) {
                            headGlow = w;
                        }
                        break;

                    case "E_HEAD":
                        if (head == null) {
                            head = w;
                        }
                        break;

                    case "WS 005":
                        if (head == null) {
                        }
                        break;

                    case "WS 006":
                        if (head == null) {
                        }
                        break;
                    //break;
                }
            }
        }

        if (armL == null) {
            return;
        }
        //-----
        //-----

        float global = ship.getFacing();
        //*End animation math stuff

        //Right arm + shoulder
        weapon.setCurrAngle(ship.getFacing() + MathUtils.getShortestRotation(ship.getFacing(), armL.getCurrAngle()) * 0.7f);
        //turretL.setCurrAngle(ship.getFacing() + MathUtils.getShortestRotation(ship.getFacing(),armL.getCurrAngle())*0.7f);
        if (armR != null) {
            shoulderR.setCurrAngle(ship.getFacing() + MathUtils.getShortestRotation(ship.getFacing(), armR.getCurrAngle()) * 0.7f);
            //turretR.setCurrAngle(ship.getFacing() + MathUtils.getShortestRotation(ship.getFacing(),armR.getCurrAngle())*0.7f);
            shoulderR.getSprite().setCenterY(armR.getBarrelSpriteAPI().getCenterY());
        }

        weapon.getSprite().setCenterY(armL.getBarrelSpriteAPI().getCenterY());

        ship.syncWeaponDecalsWithArmorDamage();

    }
}
