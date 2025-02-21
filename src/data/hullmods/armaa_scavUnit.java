package data.hullmods;

import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import data.scripts.ai.armaa_repairdrone_AI;
public class armaa_scavUnit extends BaseHullMod 
{
	private boolean runOnce = false;
    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
 		if (index == 0) return "X";
        return null;    
    }

    public void advanceInCombat(ShipAPI ship, float amount)
    {
		if(!runOnce)
		{
			ship.setShipAI(new armaa_repairdrone_AI(ship));
			runOnce = true;
		}
	}		
}
