package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import java.util.ArrayList;
import java.util.List;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;

/**
 * Torso-as-pivot for Tengai. The torso (B_TORSO) turns toward where the arms are aiming, and the arms,
 * head and pauldrons take the torso's facing as their focal point instead of the ship's.
 *
 * The engine only knows arcs relative to ship facing, so the slot arcs in the .ship files are widened to cover
 * every angle the torso can reach. The real arcs are the HALF_ARC constants below and are enforced here by
 * clamping each part's currAngle to torso facing + slot angle +/- half arc.
 */
public class armaa_tengaiTorsoEffect extends armaa_leynosEffect {

    private static final float TORSO_MAX = 60f;         // max torso swing either side of ship facing
    private static final float TORSO_TURN_RATE = 120f;  // deg/sec
    private static final float DEADZONE = 2f;           // don't chase tiny errors
    private static final float PAULDRON_FOLLOW = 0.75f; // share of the arm's offset from the torso the pauldron takes on

    private static final float ARM_HALF_ARC = 45f;      // C_ARML / A_GUN, originally arc 90
    private static final float TRUE_GUN_HALF_ARC = 50f; // originally arc 100
    private static final float HEAD_HALF_ARC = 135f;    // originally arc 270

    private boolean pivotInit = false;
    private WeaponAPI armR, armL, trueGun, head, pauldronL, pauldronR;
    private float torsoRel = 0f; // torso angle relative to ship facing

    private void initPivot(ShipAPI ship) {
        pivotInit = true;
        for (WeaponAPI w : ship.getAllWeapons()) {
            switch (w.getSlot().getId()) {
                case "A_GUN": armR = w; break;
                case "C_ARML": armL = w; break;
                case "TRUE_GUN": trueGun = w; break;
                case "E_HEAD": head = w; break;
                case "D_PAULDRONL": pauldronL = w; break;
                case "D_PAULDRONR": pauldronR = w; break;
            }
        }
    }

    // angle the weapon is aiming at, relative to ship facing and with its slot offset removed
    private float aimRel(WeaponAPI w, float global) {
        return MathUtils.getShortestRotation(global, w.getCurrAngle()) - w.getSlot().getAngle();
    }

    private void clampTo(WeaponAPI w, float torsoAngle, float halfArc) {
        if (w == null) {
            return;
        }
        float center = torsoAngle + w.getSlot().getAngle();
        float off = MathUtils.getShortestRotation(center, w.getCurrAngle());
        if (off > halfArc) {
            w.setCurrAngle(center + halfArc);
        } else if (off < -halfArc) {
            w.setCurrAngle(center - halfArc);
        }
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        // runs the Leynos hulk fade etc. and sets a first-pass torso/pauldron angle, overwritten below
        super.advance(amount, engine, weapon);

        ShipAPI ship = weapon.getShip();
        if (engine.isPaused() || ship == null || ship.isHulk()) {
            return;
        }
        if (!pivotInit) {
            initPivot(ship);
        }

        float global = ship.getFacing();

        // what the torso should face. Player: the mouse aim. Averaging the arms is wrong there, since an arm in an
        // unselected weapon group rests near 0 and drags the torso short of the target, pinning the aiming arm.
        // AI: the arms' aim, preferring whichever arms are firing.
        Float desiredRel = null;
        if (engine.getPlayerShip() == ship && ship.getShipAI() == null && ship.getMouseTarget() != null) {
            desiredRel = MathUtils.getShortestRotation(global,
                    VectorUtils.getAngle(ship.getLocation(), ship.getMouseTarget()));
        } else {
            List<WeaponAPI> arms = new ArrayList<>();
            List<WeaponAPI> firing = new ArrayList<>();
            for (WeaponAPI w : new WeaponAPI[]{armR, armL, trueGun}) {
                if (w == null) {
                    continue;
                }
                arms.add(w);
                if (w.isFiring()) {
                    firing.add(w);
                }
            }
            List<WeaponAPI> drivers = firing.isEmpty() ? arms : firing;
            if (!drivers.isEmpty()) {
                float sum = 0f;
                for (WeaponAPI w : drivers) {
                    sum += aimRel(w, global);
                }
                desiredRel = sum / drivers.size();
            }
        }
        if (desiredRel != null) {
            float desired = MathUtils.clamp(desiredRel, -TORSO_MAX, TORSO_MAX);
            float err = desired - torsoRel;
            if (Math.abs(err) > DEADZONE) {
                float step = TORSO_TURN_RATE * amount;
                torsoRel += MathUtils.clamp(err, -step, step);
            }
        }
        float torsoAngle = global + torsoRel;
        weapon.setCurrAngle(torsoAngle);

        // everything else orbits the torso
        clampTo(armR, torsoAngle, ARM_HALF_ARC);
        clampTo(armL, torsoAngle, ARM_HALF_ARC);
        clampTo(trueGun, torsoAngle, TRUE_GUN_HALF_ARC);
        clampTo(head, torsoAngle, HEAD_HALF_ARC);

        if (pauldronR != null && armR != null) {
            float o = MathUtils.getShortestRotation(torsoAngle + armR.getSlot().getAngle(), armR.getCurrAngle());
            pauldronR.setCurrAngle(torsoAngle + o * PAULDRON_FOLLOW);
        }
        if (pauldronL != null && armL != null) {
            float o = MathUtils.getShortestRotation(torsoAngle + armL.getSlot().getAngle(), armL.getCurrAngle());
            pauldronL.setCurrAngle(torsoAngle + o * PAULDRON_FOLLOW);
        }
    }
}
