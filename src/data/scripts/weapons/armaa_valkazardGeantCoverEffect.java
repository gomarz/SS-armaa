package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;

public class armaa_valkazardGeantCoverEffect implements EveryFrameWeaponEffectPlugin {

    private boolean runOnce = false;
    private WeaponAPI chestWeapon, torso;
    private float originalPos;
    private float retractLevel = 12f;
    private float currRetract = 0f;

    public void init(WeaponAPI weapon) {
        runOnce = true;
        //need to grab another weapon so some effects are properly rendered, like lighting from glib
        for (WeaponAPI w : weapon.getShip().getAllWeapons()) {
            switch (w.getSlot().getId()) {
                case "CORE":
                    if (chestWeapon == null) {
                        chestWeapon = w;
                    }
                    break;
                case "CHEST_DECO":
                    originalPos = w.getSprite().getCenterY();
                    break;
                case "B_TORSO":
                    if (torso == null) {
                        torso = w;
                    }
                    break;
            }
        }
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (weapon.getShip().getOwner() == -1 || !weapon.getShip().isAlive()) {
            return;
        }
        if (engine.isPaused()) {
            return;
        }
        if (!runOnce) {
            init(weapon);
        }
        if (chestWeapon == null) {
            return;
        }

        if (chestWeapon.isFiring() || engine.getShipPlayerIsTransferringCommandTo() == weapon.getShip()) {
            if (currRetract < retractLevel) {
                currRetract += 0.5f;
            } else if (currRetract > retractLevel) {
                currRetract = retractLevel;
            }
        } else {
            if (currRetract > 0) {
                currRetract -= 0.5f;
            }
        }
        weapon.getSprite().setCenterY(originalPos + (currRetract));
    }
}
