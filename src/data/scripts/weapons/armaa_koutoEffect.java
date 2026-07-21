package data.scripts.weapons;

import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;

/**
 *
 * @author Tartiflette
 */
public class armaa_koutoEffect implements EveryFrameWeaponEffectPlugin {

    private ShipAPI ship;
    private WeaponAPI head, armL, armR, pauldronL, pauldronR, torso;
    private float sinceB = 0f;
    //private int maxFrame, frame;
    //sliver cannon charging fx
    //private int lastWeaponAmmo = 0;
    public float TURRET_OFFSET = 30f;
    private int limbInit = 0;

    private float overlap = 0;
    private final float RIGHT_ARM_OFFSET = -25, MAX_OVERLAP = 10;

    public void init() {
        ship.setCollisionClass(CollisionClass.FIGHTER);
        for (WeaponAPI w : ship.getAllWeapons()) {
            switch (w.getSlot().getId()) {
                case "B_TORSO":
                    if (torso == null) {
                        torso = w;
                        limbInit++;
                    }
                    break;
                case "C_ARML":
                    if (armL == null) {
                        armL = w;
                        limbInit++;
                    }
                    break;
                case "C_ARMR":
                    if (armR == null) {
                        armR = w;
                        limbInit++;
                    }
                    break;
                case "D_PAULDRONL":
                    if (pauldronL == null) {
                        pauldronL = w;
                        limbInit++;
                    }
                    break;
                case "D_PAULDRONR":
                    if (pauldronR == null) {
                        pauldronR = w;
                        limbInit++;
                    }
                    break;
                case "E_HEAD":
                    if (head == null) {
                        head = w;
                        limbInit++;
                    }
                    break;
                case "H_GLOW":
                    break;
            }
        }

    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        ship = weapon.getShip();
        if (!Global.getCombatEngine().isEntityInPlay(ship)) {
            return;
        }
        ship.getSpriteAPI();
        ship.getSystem();
        weapon.getAnimation();

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

        if (ship.areAnyEnemiesInRange()) {
            sinceB += amount;
        } else {
            sinceB -= amount;
        }
        if (sinceB < 0) {
            sinceB = 0;
        } else if (sinceB > 1) {
            sinceB = 1;
        }

        if (torso != null) {
            float desired = weapon.getCurrAngle() * 0.95f;
            float current = torso.getCurrAngle();

            float delta = MathUtils.getShortestRotation(current, desired);

            // control strength of turn
            float turnRate = 0.3f;

            torso.setCurrAngle(current + delta * turnRate);
        }

        if (armR != null) {
            float armDesired = weapon.getCurrAngle() + RIGHT_ARM_OFFSET;
            float armCurrent = armR.getCurrAngle();
            float armDelta = MathUtils.getShortestRotation(armCurrent, armDesired);

            armR.setCurrAngle(armCurrent + armDelta * 0.4f); // adjustable smoothness
        }

        if (pauldronR != null) {
            float pauldronR_desired = torso.getCurrAngle()
                    + MathUtils.getShortestRotation(torso.getCurrAngle(), armR.getCurrAngle()) * 0.6f;

            float pauldronR_current = pauldronR.getCurrAngle();
            float pauldronR_delta = MathUtils.getShortestRotation(pauldronR_current, pauldronR_desired);

            pauldronR.setCurrAngle(pauldronR_current + pauldronR_delta * 0.4f);
        }
        if (torso.isDisabled()) {
        }

        Vector2f origin = new Vector2f(weapon.getLocation());
        Vector2f offset = new Vector2f(TURRET_OFFSET, -15f);
        VectorUtils.rotate(offset, weapon.getCurrAngle(), offset);
        Vector2f.add(offset, origin, origin);

        if (pauldronL != null) {
            float pauldronL_desired = torso.getCurrAngle()
                    + MathUtils.getShortestRotation(torso.getCurrAngle(), armL.getCurrAngle()) * 0.6f;

            float pauldronL_current = pauldronL.getCurrAngle();
            float pauldronL_delta = MathUtils.getShortestRotation(pauldronL_current, pauldronL_desired);

            pauldronL.setCurrAngle(pauldronL_current + pauldronL_delta * 0.4f);
        }

    }
}
