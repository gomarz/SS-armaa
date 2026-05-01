package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;

public class armaa_weaponsPack implements EveryFrameWeaponEffectPlugin {

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
    {

        // not used anywhere..but somehow is affecting the bakraids...wtf just delete it 

    }
}
