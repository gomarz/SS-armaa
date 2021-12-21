package data.hullmods;

import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.BaseHullMod;

public class armaa_selector_grenade extends BaseHullMod {    

		@Override
		public int getDisplaySortOrder() 
		{
			return 2000;
		}    
    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
		if (index == 0) return "FH29 Dispersal Grenade";
		if (index == 1) return "Remove this hullmod to cycle between weapons.";
        return null;
    }
}
