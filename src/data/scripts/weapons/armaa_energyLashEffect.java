package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import org.lwjgl.util.vector.Vector2f;

/**
 * Energy Lash OnHit effect (self-contained; diverged from armaa_harpoonEffect).
 *
 * On a hull hit: spawn the embedded secondary projectile (currently reusing
 * "armaa_harpoonSecondary"), attach the diverged energyLash AI, and HAND OFF the
 * trailing rope (spawned at fire time by the thrower) from the primary to this secondary.
 *
 * The rope was already following the primary in flight; handoff swaps its far-end to the
 * embedded secondary so the tether is continuous from fire -> flight -> embed. If the shot
 * had missed, no handoff happens and the rope retracts on its own.
 */
public class armaa_energyLashEffect implements OnHitEffectPlugin {

    private final String SECONDARY_ID = "armaa_harpoonSecondary"; // TODO: replace with energyLash secondary when ready

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point,
                      boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        if (projectile.isFading()) {
            return;
        }
        if (shieldHit) {
            return; // shields don't embed
        }

        armaa_energyLashThrowerEffect thrower =
                (armaa_energyLashThrowerEffect) projectile.getWeapon().getEffectPlugin();

        // relay the target to the AI's pickup list
        thrower.putHIT(target);

        // spawn the embedded secondary
        DamagingProjectileAPI secondary = (DamagingProjectileAPI) engine.spawnProjectile(
                projectile.getSource(),
                projectile.getWeapon(),
                SECONDARY_ID,
                point,
                projectile.getFacing(),
                target.getVelocity()
        );

        // attach the diverged AI (stick + fixed-duration + looser tear-off)
        armaa_energyLashProjectileScript rope = thrower.ropeForPrimary(projectile);
        if (secondary instanceof MissileAPI) {
            MissileAPI missile = (MissileAPI) secondary;
            missile.setMissileAI(new data.scripts.ai.armaa_energyLashAI(
                    missile, (ShipAPI) projectile.getSource()));

            // hand the trailing rope (which had been following THIS primary) off to the embedded missile
            if (rope != null) {
                rope.handoff(missile, target);
            }
        }
    }
}