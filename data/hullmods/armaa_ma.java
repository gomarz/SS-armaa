package data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

public class armaa_ma extends BaseHullMod {
	// nothing to do, just a marker hullmod - actual changes to hull made in the .skin file


	public String getDescriptionParam(int index, HullSize hullSize) {
		if (index == 0) return "" + "Low-Grade EMP-Emitter";
		if (index == 1) return "" + "a pair of hunter-killer drones";
		if (index == 2) return "" + "vent flux and overload alongside the core ship";
		if (index == 3) return "" + "Destruction of the backpack module will result in the destruction of both leg modules";
		if (index == 4) return "" + "All modules gain the hullmods applied to the core ship";
		return null;
	}
}

