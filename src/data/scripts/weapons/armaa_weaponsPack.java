package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
    


public class armaa_weaponsPack implements EveryFrameWeaponEffectPlugin{    

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {

		ShipAPI ship = weapon.getShip();
		
		if(ship.getParentStation() == null)
			return;
		
		ShipAPI parent = ship.getParentStation();		
		if(!ship.isAlive())
		{
			parent.getMutableStats().getMaxSpeed().unmodify(ship.getId()+"_weaponsPack");
			parent.getMutableStats().getMaxTurnRate().unmodify(ship.getId()+"_weaponsPack");
			parent.getMutableStats().getTurnAcceleration().unmodify(ship.getId()+"_weaponsPack");				
			return;
		}
		if(parent.getPhaseCloak() != null)
		parent.getPhaseCloak().setCooldownRemaining(1f);
		parent.getSystem().setCooldownRemaining(1f);
		parent.getMutableStats().getMaxSpeed().modifyMult(ship.getId()+"_weaponsPack",0.6f);
		parent.getMutableStats().getMaxTurnRate().modifyMult(ship.getId()+"_weaponsPack",0.6f);		
		parent.getMutableStats().getTurnAcceleration().modifyMult(ship.getId()+"_weaponsPack", 0.6f);

	
	}
}

