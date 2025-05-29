package data.hullmods;


import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

public class armaa_crampedFiring extends BaseHullMod {

	public static final float DAMAGE_BONUS = 30f;
	
	@Override
	public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) 
	{
		stats.getBallisticRoFMult().modifyMult(id,0.70f); 
	}
	
	public String getDescriptionParam(int index, HullSize hullSize) {
		if (index == 0) return "" + (int)Math.round(DAMAGE_BONUS) + "%";
		return null;
	}


}
