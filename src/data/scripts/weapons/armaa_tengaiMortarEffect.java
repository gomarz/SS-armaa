package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;

/**
 * Kicks the right arm sprite back when the mortar fires; a smaller
 * share of the force travels to the right shoulder. The barrel recoil
 * is handled separately by visualRecoil in the .wpn.
 */
public class armaa_tengaiMortarEffect implements EveryFrameWeaponEffectPlugin {

    private static final float ARM_RECOIL = 5f;
    private static final float SHOULDER_RECOIL = 1.5f;

    private boolean runOnce = false;
    private WeaponAPI pauldronR;
    private float originalArmPos = 0f, originalShoulderPos = 0f;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        ShipAPI ship = weapon.getShip();
        if (ship == null) {
            return;
        }
        if (!runOnce) {
            runOnce = true;
            originalArmPos = weapon.getSprite().getCenterY();
            for (WeaponAPI w : ship.getAllWeapons()) {
                if ("D_PAULDRONR".equals(w.getSlot().getId())) {
                    pauldronR = w;
                    originalShoulderPos = w.getSprite().getCenterY();
                    break;
                }
            }
        }
        if (engine.isPaused()) {
            return;
        }

        float t = 0f;
        if (weapon.getCooldown() > 0f) {
            t = weapon.getCooldownRemaining() / weapon.getCooldown();
        }
        // t*t eases both back out
        weapon.getSprite().setCenterY(originalArmPos + ARM_RECOIL * t * t);
        if (pauldronR != null) {
            pauldronR.getSprite().setCenterY(originalShoulderPos + SHOULDER_RECOIL * t * t);
        }
    }
}
