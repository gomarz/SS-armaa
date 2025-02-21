package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.ShipCommand;


public class armaa_linkedSystems implements EveryFrameWeaponEffectPlugin{    


	
    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon)
	{
		ShipAPI ship = weapon.getShip();
		
		if(ship.getParentStation() != null)
		{
			ShipAPI parent = ship.getParentStation();
			ship.setCollisionClass(parent.getCollisionClass());
			if(parent.getPhaseCloak().isActive() && !ship.getPhaseCloak().isActive())
				ship.giveCommand(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK,null,0);
			else if(ship.getPhaseCloak().isActive() && !parent.getPhaseCloak().isActive())
			{
				ship.getPhaseCloak().deactivate();
				ship.blockCommandForOneFrame(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK);
				ship.blockCommandForOneFrame(ShipCommand.USE_SYSTEM);
			}
			else
			{
				ship.blockCommandForOneFrame(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK);
			}
			
		}
  


    }
}

