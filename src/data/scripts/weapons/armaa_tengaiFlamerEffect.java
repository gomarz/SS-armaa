package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;

/**
 * Flamer visuals from armaa_flamerEffect, plus a per-shot kick on the left arm
 * and left shoulder so they shudder while the flamer runs. The shoulder is posed
 * by armaa_leynosEffect, which reads RECOIL_KEY.
 */
public class armaa_tengaiFlamerEffect extends armaa_flamerEffect {

    public static final String RECOIL_KEY = "armaa_tengai_flamerRecoil";
    private static final float ARM_RECOIL = 2.5f;
    private static final float KICK_PER_SHOT = 0.6f;
    // kick falls from full to zero in about 0.1 s, so each shot reads as its own pulse
    private static final float DECAY_RATE = 10f;

    private boolean runOnce = false;
    private float originalArmPos = 0f;
    private float recoil = 0f;

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        super.onFire(projectile, weapon, engine);
        recoil = Math.min(1f, recoil + KICK_PER_SHOT);
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        super.advance(amount, engine, weapon);

        ShipAPI ship = weapon.getShip();
        if (ship == null) {
            return;
        }
        if (!runOnce) {
            runOnce = true;
            originalArmPos = weapon.getSprite().getCenterY();
        }
        if (engine.isPaused()) {
            return;
        }

        recoil = Math.max(0f, recoil - DECAY_RATE * amount);
        ship.setCustomData(RECOIL_KEY, recoil);
        weapon.getSprite().setCenterY(originalArmPos + ARM_RECOIL * recoil);
    }
}
