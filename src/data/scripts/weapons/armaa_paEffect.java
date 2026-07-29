package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import org.magiclib.util.MagicAnim;
import org.lwjgl.util.vector.Vector2f;

public class armaa_paEffect implements EveryFrameWeaponEffectPlugin {

    private float swingLevelR = 0f;
    private boolean swingingR = false;
    private boolean cooldownR = false;
    private IntervalUtil animInterval = new IntervalUtil(0.012f, 0.012f);
    private final float TORSO_OFFSET = -45;

    public static Vector2f lerp(Vector2f start, Vector2f end, float alpha) {
        if (alpha < 0.0f) {
            alpha = 0.0f;
        }
        if (alpha > 1.0f) {
            alpha = 1.0f;
        }

        float x = start.x + alpha * (end.x - start.x);
        float y = start.y + alpha * (end.y - start.y);

        return new Vector2f(x, y);
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        ShipAPI ship = weapon.getShip();
        if (engine.isPaused()) {
            return;
        }

        if (!ship.isAlive()) {
            return;
        }


        float sineC = 0, sinceD = 0;

        if (weapon != null) {
            if (weapon.getChargeLevel() < 1) {
                sineC = MagicAnim.smoothNormalizeRange(weapon.getChargeLevel(), 0.3f, 0.9f);
                sinceD = MagicAnim.smoothNormalizeRange(weapon.getChargeLevel(), 0.3f, 1f);
            } else {
                sineC = 1;
                sinceD = 1;
            }

            if (weapon.getCooldownRemaining() <= 0f && !weapon.isFiring()) {
                cooldownR = false;
            }

            if (!swingingR && !cooldownR && weapon.getChargeLevel() > 0f) {
                weapon.setCurrAngle(weapon.getCurrAngle() + (sineC * -(TORSO_OFFSET) * 0.30f) * weapon.getChargeLevel());
            }
            if (weapon.getChargeLevel() >= 1f) {
                swingingR = true;
            }

            if (swingingR && weapon.getCurrAngle() != weapon.getShip().getFacing() - 90f) {
                animInterval.advance(amount);
                weapon.setCurrAngle((float) Math.max(weapon.getCurrAngle() - swingLevelR, weapon.getCurrAngle() - weapon.getArc() / 2));
            }

            if (swingingR == true && (weapon.getChargeLevel() <= 0f)) {
                swingingR = false;
                swingLevelR = 0f;
                cooldownR = true;
            }

            if (animInterval.intervalElapsed()) {
                swingLevelR += 0.5;
            }

            if (swingLevelR > 9) {
                swingLevelR = 9;
            }
            if (swingingR == false) {
                swingLevelR = 0f;
            }

        }
    }
}
