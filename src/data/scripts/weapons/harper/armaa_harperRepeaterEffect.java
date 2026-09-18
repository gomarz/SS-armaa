package data.scripts.weapons.harper;


import org.lazywizard.lazylib.MathUtils;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponGroupAPI;

public class armaa_harperRepeaterEffect implements EveryFrameWeaponEffectPlugin {

    public static final boolean DEBUG = armaa_harperLanceEffect.DEBUG;
    private static final float LANCE_SLAVE_TURN_RATE = 70f;

    private ShipAPI ship;
    private armaa_harperLanceEffect lance;
    private boolean runOnce = false;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {

        if (engine.isPaused() || weapon == null) {
            return;
        }
        ship = weapon.getShip();
        if (ship == null) {
            return;
        }

        if (!runOnce) {
            runOnce = true;
        }

        if (!findLance(engine)) {
            return;
        }

        WeaponAPI stab = lance.getWeapon();

        // The lance is the mount that takes hits; when it goes, so does the
        // repeater bolted to it.
        if (stab != null && stab.isDisabled()) {
            weapon.setForceNoFireOneFrame(true);
            return;
        }

        if (stab != null) {
            arbitrateAim(amount, engine, weapon, stab);
        }
    }

    // ------------------------------------------------------------------
    // aim
    // ------------------------------------------------------------------

    /**
     * Decide which of the two mounts is steering the assembly this frame, then
     * point the other one at it.
     */
    private void arbitrateAim(float amount, CombatEngineAPI engine,
            WeaponAPI weapon, WeaponAPI stab) {

        if (!weapon.isFiring() && lanceLeads(engine, stab)) {
            weapon.setCurrAngle(stab.getCurrAngle());
        } else {
            slaveLanceTo(stab, weapon.getCurrAngle(), amount);
        }
    }

    private boolean lanceLeads(CombatEngineAPI engine, WeaponAPI stab) {

        // Mid-strike the assembly is committed to where the lunge started.
        if (lance.isThrusting()) {
            return true;
        }

        // Player has the lance group selected: their aim wins outright.
        if (ship == engine.getPlayerShip()) {
            WeaponGroupAPI group = ship.getWeaponGroupFor(stab);
            if (group != null && !group.isAutofiring()) {
                return true;
            }
        }

        // The lance has something it could actually hit.
        return lance.hasTargetInReach(stab);
    }

    /** Swing the lance onto the repeater's line, at the lance's own turn rate. */
    private void slaveLanceTo(WeaponAPI stab, float target, float amount) {
        float delta = MathUtils.getShortestRotation(stab.getCurrAngle(), target);
        float step = LANCE_SLAVE_TURN_RATE * amount;
        if (Math.abs(delta) > step) {
            delta = delta > 0 ? step : -step;
        }
        stab.setCurrAngle(MathUtils.clampAngle(stab.getCurrAngle() + delta));
    }

    // ------------------------------------------------------------------
    // firing
    // ------------------------------------------------------------------

    /**
     * A shot shows up as cooldown jumping up, or as ammo dropping. Both are
     * checked: a weapon with negligible cooldown still spends ammo, and an
     * unlimited-ammo weapon still gains cooldown.
     */

    private boolean findLance(CombatEngineAPI engine) {
        if (lance != null) {
            return true;
        }
        Object o = engine.getCustomData()
                .get(armaa_harperLanceEffect.customDataKey(ship));
        if (o instanceof armaa_harperLanceEffect) {
            lance = (armaa_harperLanceEffect) o;
            return true;
        }
        // The lance registers on its own first frame; this one may run before
        // it. Harmless to miss a frame and pick it up on the next.
        return false;
    }
}