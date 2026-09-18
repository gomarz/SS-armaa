package data.scripts.weapons.harper;

import java.awt.Color;

import org.lazywizard.lazylib.MathUtils;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;

public class armaa_harperAnimEffect implements EveryFrameWeaponEffectPlugin {

    // ---- wind-back: arm drawn in while the lance charges ----
    /**
     * Sprite units the arm pulls back at full wind.
     *
     * SIGN: on this axis POSITIVE is backward and NEGATIVE is forward - the
     * original leynos line reads originalArmPos - (16 * sinceB) + (8 * sinceG)
     * where sinceG is the early-charge wind-back (positive) and sinceB the
     * late-charge thrust (negative).
     */
    private static final float WIND_PULL = 8f;
    private static final float WIND_RISE_PER_SEC = 2.2f;
    private static final float WIND_FALL_PER_SEC = 1.1f;
    /**
     * Wind is released MUCH faster than it decays idle. Without this the arm
     * still has wind left when the punch has already collapsed, and the
     * leftover draw reads as a second wind-up happening after the thrust.
     */
    private static final float WIND_RELEASE_PER_SEC = 9f;

    // ---- punch: arm driven out during the strike ----
    /**
     * Sprite units the arm pushes forward at full punch. Larger than WIND_PULL,
     * matching the original: the draw is short, the thrust is long.
     */
    private static final float PUNCH_PUSH = 16f;
    private static final float PUNCH_RISE_PER_SEC = 9f;
    private static final float PUNCH_FALL_PER_SEC = 2.5f;

    // ---- torso lean ----
    private static final float MAX_OVERLAP = 10f;
    private static final float MAX_LEG_ROTATE = 22.5f;
    private static final float TORSO_OFFSET = -45f;
    private static final float RIGHT_ARM_OFFSET = -25f;
    /**
     * How much of the wind-back reaches the torso and shoulders. The arm itself
     * only translates; the lean is what actually reads as a wind-up.
     */
    private static final float TORSO_WIND_SCALE = 1f;

    private boolean runOnce = false;
    private ShipAPI ship;
    private armaa_harperLanceEffect lance;

    private WeaponAPI head, armL, armR, pauldronL, pauldronR, headGlow;

    private float originalArmPos = 0f;
    private float originalShoulderPos = 0f;

    private float wind = 0f;
    private float punch = 0f;
    /**
     * How far the arm sprite currently sits from its rest position, in sprite
     * units. Positive is backward.
     *
     * Published because anything anchored to the arm has to follow it:
     * setCenterY moves the SPRITE only, so the weapon's fire point does not
     * move, and an effect hung off that fire point stays in midair while the
     * arm slides out from under it.
     */
    private float armOffset = 0f;

    private float overlap = 0f;
    private float currentRotateL = 0f;
    private float currentRotateR = 0f;

    public static String customDataKey(ShipAPI ship) {
        return "armaa_harperAnim_" + ship.getId();
    }

    /**
     * Arm displacement from rest this frame, sprite units, positive backward.
     */
    public float getArmOffset() {
        return armOffset;
    }

    private void init() {
        runOnce = true;
        for (WeaponAPI w : ship.getAllWeapons()) {
            String slot = w.getSlot().getId();
            if ("C_ARML".equals(slot) && armL == null) {
                armL = w;
                originalArmPos = armL.getSprite().getCenterY();
            } else if ("A_GUN".equals(slot) && armR == null) {
                armR = w;
            } else if ("D_PAULDRONL".equals(slot) && pauldronL == null) {
                pauldronL = w;
                originalShoulderPos = pauldronL.getSprite().getCenterY();
            } else if ("D_PAULDRONR".equals(slot) && pauldronR == null) {
                pauldronR = w;
            } else if ("E_HEAD".equals(slot) && head == null) {
                head = w;
            } else if ("H_GLOW".equals(slot) && headGlow == null) {
                headGlow = w;
            }
        }
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {

        ship = weapon.getShip();
        if (ship == null) {
            return;
        }
        if (!runOnce) {
            init();
            engine.getCustomData().put(customDataKey(ship), this);
        }
        if (armL == null) {
            return;
        }

        if (ship.isHulk()) {
            shedLimbs();
            return;
        }
        if (engine.isPaused()) {
            return;
        }

        findLance(engine);
        updateDrivers(amount);
        updateLean(amount);

        float global = ship.getFacing();
        float aimL = MathUtils.getShortestRotation(global, armL.getCurrAngle());
        float aimR = armR != null
                ? MathUtils.getShortestRotation(global, armR.getCurrAngle())
                : 0f;

        // Torso carries the wind-up, since the arm itself may not be rotated.
        // Sense matches the original: the late thrust term carried TORSO_OFFSET
        // and the early wind-back term carried its negation.
        float lean = (punch - wind) * TORSO_OFFSET * TORSO_WIND_SCALE;
        weapon.setCurrAngle(global + lean + aimR * 0.3f + currentRotateR);

        // Arm and shoulder translate: back on wind, forward on punch.
        armOffset = WIND_PULL * wind - PUNCH_PUSH * punch;
        float armPos = originalArmPos + armOffset;
        armL.getSprite().setCenterY(armPos);
        if (armL.getMissileRenderData() != null && !armL.getMissileRenderData().isEmpty()) {
            armL.getMissileRenderData().get(0).getSprite().setCenterY(armPos);
        }

        if (pauldronL != null) {
            pauldronL.setCurrAngle(global + lean + aimL * 0.75f
                    - RIGHT_ARM_OFFSET * 0.5f + currentRotateL * 0.75f);
            pauldronL.getSprite().setCenterY(originalShoulderPos - PUNCH_PUSH * 0.5f * punch);
        }
        if (pauldronR != null) {
            pauldronR.setCurrAngle(global + lean * 0.5f + aimR * 0.75f
                    + RIGHT_ARM_OFFSET * 0.5f + currentRotateR * 0.75f);
        }
        if (headGlow != null && head != null) {
            headGlow.setCurrAngle(head.getCurrAngle());
        }
    }

    // ------------------------------------------------------------------
    // drivers
    // ------------------------------------------------------------------
    /**
     * Wind rises while the lance is charging and falls otherwise; punch rises
     * during the strike and decays after. Two separate integrators rather than
     * one curve read in both directions, so an early contact strike retreats
     * from wherever the arm happened to be instead of completing a thrust it
     * never made.
     */
    private void updateDrivers(float amount) {

        boolean charging = lance != null && lance.isLunging();
        boolean striking = lance != null && lance.isThrusting();

        float windRate;
        if (charging) {
            windRate = WIND_RISE_PER_SEC;
        } else if (striking) {
            // the draw is spent the instant the strike begins
            windRate = WIND_RELEASE_PER_SEC;
        } else {
            windRate = WIND_FALL_PER_SEC;
        }

        wind = approach(wind, charging ? 1f : 0f, windRate, amount);
        punch = approach(punch, striking ? 1f : 0f,
                striking ? PUNCH_RISE_PER_SEC : PUNCH_FALL_PER_SEC, amount);
    }

    private static float approach(float value, float target, float ratePerSec, float amount) {
        float step = ratePerSec * amount;
        float d = target - value;
        if (d > step) {
            d = step;
        } else if (d < -step) {
            d = -step;
        }
        float out = value + d;
        return out < 0f ? 0f : (out > 1f ? 1f : out);
    }

    /**
     * Body lean under acceleration and leg splay while turning.
     */
    private void updateLean(float amount) {

        if (ship.getEngineController().isAccelerating()) {
            overlap = Math.min(MAX_OVERLAP, overlap + ((MAX_OVERLAP - overlap) * amount * 5f));
        } else if (ship.getEngineController().isDecelerating()
                || ship.getEngineController().isAcceleratingBackwards()) {
            overlap = Math.max(-MAX_OVERLAP, overlap + ((-MAX_OVERLAP + overlap) * amount * 5f));
        } else if (Math.abs(overlap) < 0.1f) {
            overlap = 0f;
        } else {
            overlap -= (overlap / 2f) * amount * 3f;
        }

        float target = 0f;
        if (ship.getEngineController().isTurningLeft()) {
            target = -MAX_LEG_ROTATE / 2f;
        } else if (ship.getEngineController().isTurningRight()) {
            target = MAX_LEG_ROTATE / 2f;
        }
        currentRotateL = stepToward(currentRotateL, target);
        currentRotateR = stepToward(currentRotateR, target);
    }

    private static float stepToward(float current, float target) {
        float d = MathUtils.getShortestRotation(current, target);
        if (Math.abs(d) < 0.5f) {
            return target;
        }
        return current + (d > 0 ? 0.4f : -0.4f);
    }

    /**
     * Randomly drop limbs off the wreck, as the leynos script does.
     */
    private void shedLimbs() {
        Color clear = new Color(0, 0, 0, 0);
        if (head != null && Math.random() > 0.5f) {
            head.getSprite().setColor(clear);
        }
        if (Math.random() > 0.5f) {
            if (pauldronL != null) {
                pauldronL.getSprite().setColor(clear);
            }
            armL.getSprite().setColor(clear);
        }
        if (Math.random() > 0.5f) {
            if (pauldronR != null) {
                pauldronR.getSprite().setColor(clear);
            }
            if (armR != null) {
                armR.getSprite().setColor(clear);
            }
        }
    }

    private void findLance(CombatEngineAPI engine) {
        if (lance != null) {
            return;
        }
        Object o = engine.getCustomData()
                .get(armaa_harperLanceEffect.customDataKey(ship));
        if (o instanceof armaa_harperLanceEffect) {
            lance = (armaa_harperLanceEffect) o;
        }
        // The lance registers on its own first frame; missing it costs nothing.
    }
}
