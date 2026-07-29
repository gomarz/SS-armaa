package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

public class armaa_prodromosEffect implements EveryFrameWeaponEffectPlugin, OnFireEffectPlugin {

    private ShipAPI ship;
    private WeaponAPI armL, torso;
    public static final int SHOTS = 4;
    public static final float SPREAD = 10f;

    private float overlap = 0;
    private final float TORSO_OFFSET = -45, MAX_OVERLAP = 10;

    public void init() {
        for (WeaponAPI w : ship.getAllWeapons()) {
            switch (w.getSlot().getId()) {
                case "B_TORSO":
                    if (torso == null) {
                        torso = w;
                    }
                    break;

                case "C_ARML":
                    if (armL == null) {
                        armL = w;
                    }
                    break;
            }
        }

    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {

        ship = weapon.getShip();
        init();

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

        float sineA = 0, sinceB = 0;

        float global = ship.getFacing();
        float aim = MathUtils.getShortestRotation(global, weapon.getCurrAngle());

        if (torso != null) {
            torso.setCurrAngle(global + sineA * TORSO_OFFSET + aim * 0.3f);
        }

        ship.syncWeaponDecalsWithArmorDamage();

    }

    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {

        for (int f = 0; f < SHOTS; f++) {
            float offset = ((float) Math.random() * SPREAD) - ((float) Math.random() * SPREAD * 0.5f);
            float angle = weapon.getCurrAngle() + offset;
            String weaponId = "armaa_prodromos_leftArmTrue";
            Vector2f muzzleLocation = weapon.getSpec().getTurretFireOffsets().get(0);
            DamagingProjectileAPI proj = (DamagingProjectileAPI) engine.spawnProjectile(weapon.getShip(), weapon, weaponId, projectile.getLocation(), angle, weapon.getShip().getVelocity());
            float randomSpeed = (float) Math.random() * 0.4f + 0.8f;
            proj.getVelocity().scale(randomSpeed);
        }

        engine.removeEntity(projectile);

    }
}
