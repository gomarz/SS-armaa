package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import org.lazywizard.lazylib.MathUtils;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.combat.ai.BasicShipAI;
import data.scripts.ai.armaa_combat_docking_AI;
import java.awt.Color;
import java.util.List;
import org.magiclib.util.MagicAnim;
    


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

