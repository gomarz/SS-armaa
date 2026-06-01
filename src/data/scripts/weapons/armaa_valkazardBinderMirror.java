package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import data.scripts.util.MagicRender;
import java.awt.Color;

public class armaa_valkazardBinderMirror implements EveryFrameWeaponEffectPlugin {

    private boolean runOnce = false;
    private WeaponAPI stationWep;
    private WeaponAPI shipWep;

    public void init(WeaponAPI weapon) {
        // get the station weapon
        String ourVernier = weapon.getShip().getHullSpec().getHullId().contains("_l") ? "WS0001" : "WS0002";
        for (WeaponAPI w : weapon.getShip().getParentStation().getAllWeapons()) {
            if (w.getSlot().getId().equals(ourVernier) && stationWep == null) {
                stationWep = w;
                break;
            }

        }
        if(stationWep == null)
            return;
        for (WeaponAPI w : weapon.getShip().getAllWeapons()) {
            if (w.getId() == stationWep.getId()) {
                shipWep = w;
                break;
            }
        }
                runOnce = true;
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if(weapon.getShip().getParentStation() == null)
            return;
        if(weapon.getShip().getCollisionClass() != weapon.getShip().getParentStation().getCollisionClass())
            weapon.getShip().setCollisionClass( weapon.getShip().getParentStation().getCollisionClass());

    }
}
