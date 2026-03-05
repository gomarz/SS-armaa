package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.ShipCommand;

public class armaa_linkedSystems implements EveryFrameWeaponEffectPlugin {

    private final String SHIELD_KEY = "armaa_shieldDebuff";
    private boolean init = false;
    private final float MANUEVER_MALUS = 0.25f;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        ShipAPI ship = weapon.getShip();
        if (ship.getParentStation() == null) {
            return;
        }
        ShipAPI parent = ship.getParentStation();
        if (!init) {
            parent.getMutableStats().getMaxSpeed().modifyMult(SHIELD_KEY, 1f - MANUEVER_MALUS);
            parent.getMutableStats().getMaxTurnRate().modifyMult(SHIELD_KEY, 1f - MANUEVER_MALUS);
            parent.getMutableStats().getWeaponTurnRateBonus().modifyMult(SHIELD_KEY, 1f - MANUEVER_MALUS);
            init = true;
        }
        // spazzes out for some reason
        //if (Global.getCombatEngine().getPlayerShip() == parent) {
        //    Global.getCombatEngine().maintainStatusForPlayerShip(new Object(), "graphics/icons/hullsys/fortress_shield.png", "Unwieldy Shield", "Manuverability decreased by 20%", true);
        //}
        if (ship.isHulk() || !ship.isAlive()) {
            parent.getMutableStats().getMaxSpeed().unmodifyMult(SHIELD_KEY);
            parent.getMutableStats().getMaxTurnRate().unmodifyMult(SHIELD_KEY);
            parent.getMutableStats().getWeaponTurnRateBonus().unmodifyMult(SHIELD_KEY);
        }
        if(ship.getCollisionClass() != parent.getCollisionClass())
            ship.setCollisionClass(parent.getCollisionClass());
        if(ship.getPhaseCloak() != null)
        {
            if (parent.getPhaseCloak().isActive() && !ship.getPhaseCloak().isActive()) {
                ship.giveCommand(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK, null, 0);
            } else if (ship.getPhaseCloak().isActive() && !parent.getPhaseCloak().isActive()) {
                ship.getPhaseCloak().deactivate();
                ship.blockCommandForOneFrame(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK);
                ship.blockCommandForOneFrame(ShipCommand.USE_SYSTEM);
            } else {
                ship.blockCommandForOneFrame(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK);
            }
        }

    }
}
