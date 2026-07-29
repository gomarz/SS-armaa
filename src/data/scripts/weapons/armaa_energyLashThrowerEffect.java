package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Energy Lash weapon coordinator (self-contained; diverged from armaa_harpoonThrowerEffect).
 *
 * Roles:
 *  - OnFire: spawn a rope (armaa_energyLashProjectileScript) trailing the PRIMARY projectile,
 *    and register it keyed by that primary so multiple in-flight shots each get their own rope.
 *  - EveryFrame: relay the projectile->missile handoff via the hit/detonation maps (same pattern
 *    as the original), and prune stale rope-map entries.
 *  - Provides ropeForPrimary(...) so the OnHit effect can find the right rope to hand off to.
 *
 * The rope self-manages its own lifetime (it retracts and removePlugin's itself); this map is
 * purely for handoff lookup.
 */
public class armaa_energyLashThrowerEffect implements EveryFrameWeaponEffectPlugin, OnFireEffectPlugin {

    private boolean runOnce = false, empty = false;

    private final Map<CombatEntityAPI, Boolean> detonation = new HashMap<>(); // synch detonations
    private final List<CombatEntityAPI> hit = new ArrayList<>();              // projectile -> missile target relay

    // primary projectile -> its rope script (for fired-time spawn + handoff lookup)
    private final Map<DamagingProjectileAPI, armaa_energyLashProjectileScript> ropes = new HashMap<>();

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (!runOnce) {
            runOnce = true;
            detonation.clear();
            hit.clear();
            ropes.clear();
        }
        if (engine.isPaused()) {
            return;
        }

        // prune resolved detonations
        if (!detonation.isEmpty()) {
            for (Iterator<CombatEntityAPI> iter = detonation.keySet().iterator(); iter.hasNext(); ) {
                CombatEntityAPI entry = iter.next();
                if (detonation.get(entry)) {
                    iter.remove();
                }
            }
        }

        // prune rope entries whose primary is gone (rope handles its own retract/removal)
        if (!ropes.isEmpty()) {
            for (Iterator<DamagingProjectileAPI> iter = ropes.keySet().iterator(); iter.hasNext(); ) {
                DamagingProjectileAPI p = iter.next();
                if (p == null || !engine.isEntityInPlay(p)) {
                    iter.remove();
                }
            }
        }

        // empty-ammo click
        if (weapon.getAmmo() == 0) {
            if (!empty) {
                empty = true;
                Global.getSoundPlayer().playSound("armaa_stake_empty", 1, 2,
                        weapon.getLocation(), weapon.getShip().getVelocity());
            }
        } else if (empty) {
            empty = false;
        }

    }

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) 
    {
            for (CombatEntityAPI anchored : new ArrayList<>(detonation.keySet())) {
        applyDetonation(anchored); // one live tether per weapon
    }
        // Spawn the rope trailing the freshly-fired PRIMARY projectile.
        armaa_energyLashProjectileScript rope =
                new armaa_energyLashProjectileScript(projectile, weapon.getShip(), null);
        engine.addPlugin(rope);
        ropes.put(projectile, rope);
    }

    /** OnHit effect calls this with the PRIMARY to find the rope that should take the handoff. */
    public armaa_energyLashProjectileScript ropeForPrimary(DamagingProjectileAPI primary) {
        return ropes.get(primary);
    }

    // --- projectile -> missile relay (same contract the AI expects) ---
    public void putHIT(CombatEntityAPI target) {
        hit.add(target);
    }

    public List<CombatEntityAPI> getHITS() {
        return hit;
    }

    public void setDetonation(CombatEntityAPI target) {
        if (hit.contains(target)) {
            hit.remove(target);
        }
        if (!detonation.containsKey(target)) {
            detonation.put(target, false);
        }
    }

    public boolean getDetonation(CombatEntityAPI target) {
        if (detonation.containsKey(target)) {
            return detonation.get(target);
        } else {
            return true;
        }
    }

    public void applyDetonation(CombatEntityAPI target) {
        if (detonation.containsKey(target)) {
            detonation.put(target, true);
        }
    }
}
