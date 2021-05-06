package data.hullmods;

import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.BaseHullMod;

public class armaa_selector_blade extends BaseHullMod {    
    
    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
		if (index == 0) return "MOONLIGHT";
		if (index == 1) return "WARNING: Pilots will attempt to close to minimal range with this weapon!";
		if (index == 2) return "Remove this hullmod to cycle between weapons.";
        return null;
    }
}
