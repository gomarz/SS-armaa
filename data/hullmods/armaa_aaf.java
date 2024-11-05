package data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

public class armaa_aaf extends BaseHullMod {

	public static final float ROF_BONUS = 1f;
	public static final float FLUX_REDUCTION = 50f;
	
	public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {

		stats.getBallisticRoFMult().modifyMult(id, 2f);
		stats.getEnergyRoFMult().modifyMult(id, 2f);
		stats.getBallisticWeaponFluxCostMod().modifyMult(id, 1f - (FLUX_REDUCTION * 0.01f));
		stats.getEnergyWeaponFluxCostMod().modifyMult(id, 1f - (FLUX_REDUCTION * 0.01f));
	}
	
	public String getDescriptionParam(int index, HullSize hullSize) {
		return null;
	}
	
	public String getSModDescriptionParam(int index, HullSize hullSize) {
		return null;
	}
	

}
