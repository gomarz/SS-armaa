package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

public class beamCoat extends BaseHullMod {

	// lower = more res
	public static final float BEAM_DAMAGE_REDUCTION_MAX = 0.40f;
	public static final float BEAM_DAMAGE_REDUCTION_MIN = 0.70f;
	

	public String getDescriptionParam(int index, HullSize hullSize) {
		if (index == 0) return "" + (int) Math.round((1f - BEAM_DAMAGE_REDUCTION_MAX) * 100f) + "%";
		if(index == 1) return "" + (int)70+"%";
		return null;
	}
	
    public void advanceInCombat(ShipAPI ship, float amount)
    {
		
		ship.getMutableStats().getBeamDamageTakenMult().modifyMult("armaa_beamRes_"+ship.getId(), BEAM_DAMAGE_REDUCTION_MIN-(BEAM_DAMAGE_REDUCTION_MAX*ship.getFluxLevel()));	
		if(Global.getCombatEngine().getPlayerShip() == ship)
			Global.getCombatEngine().maintainStatusForPlayerShip("AceSystem2", "graphics/ui/icons/icon_repair_refit.png","Beam Coating Eff %", String.valueOf((1f-(BEAM_DAMAGE_REDUCTION_MIN)+(BEAM_DAMAGE_REDUCTION_MAX*ship.getFluxLevel()))*100f)+"%" ,false);
	}


}
