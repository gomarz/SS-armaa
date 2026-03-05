package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import org.lazywizard.lazylib.MathUtils;

public class armaa_kshatriyaSpearMeleeEffect implements EveryFrameWeaponEffectPlugin {

    private float ogSpikePos = 0f;
    private float wepRecoilMax = 30f;
    private float recoil = 0f;
    private boolean runOnce = false;
    private ShipAPI ship;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine.isPaused()) {
            return;
        }
        if (!runOnce) {
            ogSpikePos = weapon.getBarrelSpriteAPI().getCenterY();
            runOnce = true;
        }
        ship = weapon.getShip();
        if (engine.getCustomData().get("armaa_drillHit_" + ship.getId()) != null) {
            engine.getCustomData().remove("armaa_drillHit_" + ship.getId());
            recoil = Math.max(0, recoil - 10);
        } else if (recoil < wepRecoilMax) {
            recoil++;
        }

        weapon.getBarrelSpriteAPI().setCenterY(ogSpikePos - (recoil * weapon.getCooldownRemaining() / weapon.getCooldown()));

        if (ship.getShipTarget() != null && MathUtils.getDistance(ship, ship.getShipTarget()) < 500 && !weapon.isDisabled()) {
            if (ship.getAI() != null) {
                ship.blockCommandForOneFrame(ShipCommand.DECELERATE);
                engine.headInDirectionWithoutTurning(ship, weapon.getCurrAngle(), ship.getMaxSpeed());
            }
        }
    }
}
