package data.scripts.weapons.basilisk;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import data.scripts.ai.armaa_BasiliskMissileAI;

/**
 * Attaches BasiliskMissileAI to each launched missile and registers it with
 * the death-watcher. Referenced from armaa_basilisk.wpn via "onFireEffect".
 */
public class armaa_BasiliskOnFire implements EveryFrameWeaponEffectPlugin, OnFireEffectPlugin {

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        if (!(projectile instanceof MissileAPI)) return;

        MissileAPI missile = (MissileAPI) projectile;
        ShipAPI source = weapon.getShip();

        armaa_BasiliskMissileAI ai = new armaa_BasiliskMissileAI(missile, source);
        missile.setMissileAI(ai);

        armaa_BasiliskTracker.get(engine).register(missile, ai, source);
    }

    @Override
    public void advance(float f, CombatEngineAPI ceapi, WeaponAPI wapi) {
        return;
    }
}
