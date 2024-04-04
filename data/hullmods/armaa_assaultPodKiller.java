package data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.Global;

public class armaa_assaultPodKiller extends BaseHullMod {


	public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {

	}
	
	public String getDescriptionParam(int index, HullSize hullSize) 
	{
		return "";
	}

    //Built-in only
    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return false;
    }

	@Override
	public boolean affectsOPCosts() {
		return true;
	}

    @Override
    public void advanceInCombat(ShipAPI ship, float amount)
    {
		ship.setHitpoints(ship.getHitpoints() - amount*2);
		if(Global.getCombatEngine().isCombatOver() || Global.getCombatEngine().getFleetManager(0).getTaskManager(false).isInFullRetreat() || Global.getCombatEngine().isEnemyInFullRetreat())
		{
			ship.setHitpoints(ship.getHitpoints() - amount*100);
		}
    }	

}
