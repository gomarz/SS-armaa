package data.hullmods;

import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.BaseHullMod;

public class armaa_selector_shotgun extends BaseHullMod {    
    
    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
 		if (index == 0) return "PLMT-07 Shotgun";
		if (index == 1) return "Remove this hullmod to cycle between weapons.";
        return null;    
    }
}
