package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import java.awt.Color;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;

public class armaa_PlasmaFlameEveryFrameEffect implements EveryFrameWeaponEffectPlugin {

    private static final Color MUZZLE_FLASH_COLOR = new Color(255, 200, 100, 50);
    private static final Color MUZZLE_FLASH_COLOR_ALT = new Color(100, 100, 255, 100);
    private static final Color MUZZLE_FLASH_COLOR_GLOW = new Color(255, 75, 0, 50);
    private static final float MUZZLE_FLASH_DURATION = 0.15f;
    private static final float MUZZLE_FLASH_SIZE = 25.0f;
    private static final Vector2f MUZZLE_OFFSET_HARDPOINT = new Vector2f(37.0f, -4.5f);
    private static final Vector2f MUZZLE_OFFSET_TURRET = new Vector2f(35.0f, -4.5f);

    private int lastWeaponAmmo = 0;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine.isPaused()) {
            return;
        }

        int weaponAmmo = weapon.getAmmo();

        if (weaponAmmo < lastWeaponAmmo) {
            Vector2f weaponLocation = weapon.getLocation();
            ShipAPI ship = weapon.getShip();
            float weaponFacing = weapon.getCurrAngle();
            Vector2f muzzleLocation = new Vector2f(weapon.getSlot().isHardpoint() ? MUZZLE_OFFSET_HARDPOINT : MUZZLE_OFFSET_TURRET);
            VectorUtils.rotate(muzzleLocation, weaponFacing, muzzleLocation);
            Vector2f.add(muzzleLocation, weaponLocation, muzzleLocation);

            if (weaponAmmo < lastWeaponAmmo) {
                Vector2f shipVelocity = MathUtils.getPointOnCircumference(ship.getVelocity(), (float) Math.random() * 20f, weaponFacing + 90f
                        - (float) Math.random()
                        * 180f);
                if (Math.random() > 0.75) {
                    engine.spawnExplosion(muzzleLocation, shipVelocity, MUZZLE_FLASH_COLOR_ALT, MUZZLE_FLASH_SIZE * 0.5f, MUZZLE_FLASH_DURATION);
                } else {
                    engine.spawnExplosion(muzzleLocation, shipVelocity, MUZZLE_FLASH_COLOR, MUZZLE_FLASH_SIZE, MUZZLE_FLASH_DURATION);
                }
                engine.addSmoothParticle(muzzleLocation, shipVelocity, MUZZLE_FLASH_SIZE * 3f, 1f, MUZZLE_FLASH_DURATION * 2f, MUZZLE_FLASH_COLOR_GLOW);
            }
        }

        lastWeaponAmmo = weaponAmmo;
    }
}
