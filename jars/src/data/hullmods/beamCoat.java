package data.hullmods;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.combat.BaseHullMod;

public class beamCoat extends BaseHullMod {

	public static final float BEAM_DAMAGE_REDUCTION = 0.60f;
	
	public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
		stats.getBeamDamageTakenMult().modifyMult(id, BEAM_DAMAGE_REDUCTION);
	}
	
	public String getDescriptionParam(int index, HullSize hullSize) {
		if (index == 0) return "" + (int) Math.round((1f - BEAM_DAMAGE_REDUCTION) * 100f) + "%";
		if(index == 1) return "" + (int)70+"%";
		return null;
	}


}
