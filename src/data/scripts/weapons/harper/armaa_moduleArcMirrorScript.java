package data.scripts.weapons.harper;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import java.util.List;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.input.InputEventAPI;

public class armaa_moduleArcMirrorScript extends BaseEveryFrameCombatPlugin {

    private String wepSlot;
    private WeaponAPI weapon;
    private ShipAPI module;
    private ShipAPI ship;
    float sinceB = 0f;
    public armaa_moduleArcMirrorScript(ShipAPI ship, String weaponSlot) {
        this.wepSlot = weaponSlot;
        this.ship = ship;
        for (WeaponAPI wep : ship.getAllWeapons()) {
            if (wep.getSlot().getId().equals(wepSlot)) {
                weapon = wep;
                break;
            }
        }

        List<ShipAPI> children = ship.getChildModulesCopy();
        if (children != null) {
            for (ShipAPI m : children) {
                if (m.getStationSlot() == null) {
                    continue;
                }
                m.ensureClonedStationSlotSpec();
                m.setHullSize(ShipAPI.HullSize.FIGHTER);
                if (m.getStationSlot().getId().equals("MODULE")) {
                    module = m;
                    m.setFacing(weapon.getCurrAngle());
                }
            }
        }

    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (module == null) {
            List<ShipAPI> children = ship.getChildModulesCopy();
            if (children != null) {
                for (ShipAPI m : children) {
                    if (m.getStationSlot() == null) {
                        continue;
                    }
                    m.ensureClonedStationSlotSpec();
                    m.setHullSize(ShipAPI.HullSize.FIGHTER);
                    m.setLayer(CombatEngineLayers.FIGHTERS_LAYER);
                    if (m.getStationSlot().getId().equals("MODULE")) {
                        module = m;
                        m.setFacing(weapon.getCurrAngle());
                    }
                }
            }
            return;
        }
        if (module.getStationSlot() != null && module.isAlive()) {
            float global = ship.getFacing();
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
            float LEFT_ARM_OFFSET = -70;
            //float aim = MathUtils.getShortestRotation(global, gun.getCurrAngle());
            weapon.setCurrAngle(global - ((LEFT_ARM_OFFSET) * sinceB) + ((0f * 0.25f) * (1 - sinceB)));
            module.setHullSize(ShipAPI.HullSize.FIGHTER);
            module.setCollisionClass(CollisionClass.FIGHTER);
            module.setLayer(CombatEngineLayers.FIGHTERS_LAYER);
            module.setFacing(weapon.getCurrAngle());
        } else {
            Global.getCombatEngine().removePlugin(this);
        }

    }
}
