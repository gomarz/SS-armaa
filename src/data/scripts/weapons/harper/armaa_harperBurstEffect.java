package data.scripts.weapons.harper;

import java.awt.Color;

import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicLensFlare;
import org.magiclib.util.MagicRender;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;

/**
 * The Harper's burst tip - the spike is launched off the lance and detonates,
 * trading a limited charge for a high-explosive strike.
 *
 * WHY THIS IS A SEPARATE MOUNT. The whole point of the tips being finite is the
 * decision of when to spend one - saving them for a cruiser instead of burning
 * them on a frigate. Folding the burst into charge level would delete that
 * decision, because the player could never choose to be fully charged and NOT
 * spend a tip. A discrete trigger keeps the choice in the player's hands, and
 * the AI script decides it separately.
 *
 * SLAVED, NOT INDEPENDENT. The lance owns the lunge, the couch, the barrel
 * offset and the sweep. This plugin finds the lance through the engine's custom
 * data, mirrors its angle, and asks it to strike with an HE payload -
 * requestBurstStrike routes through the normal strike path, so the burst
 * inherits the sweep, the arrest and the cooldown handling for free.
 *
 * FIRING THE TIP TAKES THE SPIKE AWAY. While the tip is gone the lance cannot
 * stab, and the spike is parked inside the shroud until the mount reloads. That
 * is not a penalty bolted on - it falls out of the fiction, and it gives the
 * repeater a window where it is the only thing the Harper has.
 */
public class armaa_harperBurstEffect implements EveryFrameWeaponEffectPlugin {

    public static final boolean DEBUG = armaa_harperLanceEffect.DEBUG;

    /**
     * Damage delivered by the burst strike, before the couch multiplier. Sits
     * above the lance's own kinetic figure because it is finite and lands as
     * HIGH_EXPLOSIVE.
     */
    private static final float BURST_DAMAGE = 900f;

    private ShipAPI ship;
    private armaa_harperLanceEffect lance;

    private float prevCooldown = 0f;
    private int prevAmmo = -999;
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
            prevCooldown = weapon.getCooldownRemaining();
            prevAmmo = weapon.getAmmo();
            runOnce = true;
        }

        if (!findLance(engine)) {
            return;
        }

        WeaponAPI stab = lance.getWeapon();
        if (stab != null) {
            weapon.setCurrAngle(stab.getCurrAngle());
            if (stab.isDisabled()) {
                weapon.setForceNoFireOneFrame(true);
                return;
            }
        }

        // ---- no tip on the lance until this mount reloads ----
        boolean tipGone = weapon.getCooldownRemaining() > 0f
                || (weapon.usesAmmo() && weapon.getAmmo() <= 0);
        lance.setTipPresent(!tipGone);
        if (tipGone && stab != null) {
            // Suppressed every frame - setForceNoFireOneFrame lasts exactly one,
            // and a single missed frame is a free stab with no spike on it.
            stab.setForceNoFireOneFrame(true);
        }

        // Do not launch into empty space, and do not interrupt a strike already
        // in progress.
        if (lance.isBusy()) {
            weapon.setForceNoFireOneFrame(true);
            return;
        }

        if (detectFire(weapon)) {
            fireTip(engine, weapon);
        }
    }

    private void fireTip(CombatEngineAPI engine, WeaponAPI weapon) {

        if (!lance.requestBurstStrike(engine, BURST_DAMAGE)) {
            return;
        }
        lance.setTipPresent(false);

        Vector2f p = weapon.getFirePoint(0);
        float angle = weapon.getCurrAngle();

        Global.getSoundPlayer().playSound("armaa_polearm", 0.75f, 1.2f,
                p, new Vector2f());

        if (DEBUG) {
            engine.addFloatingText(ship.getLocation(),
                    "BURST TIP  ammo=" + weapon.getAmmo(),
                    20f, Color.orange, ship, 1f, 1f);
        }
    }

    private boolean detectFire(WeaponAPI weapon) {
        float cd = weapon.getCooldownRemaining();
        int ammo = weapon.getAmmo();

        boolean jumped = cd > prevCooldown + 0.0001f;
        boolean dropped = prevAmmo != -999 && ammo < prevAmmo;

        prevCooldown = cd;
        prevAmmo = ammo;
        return jumped || dropped;
    }

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
        return false;
    }
}
