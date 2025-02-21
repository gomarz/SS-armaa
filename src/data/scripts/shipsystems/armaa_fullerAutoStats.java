package data.scripts.shipsystems;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;

public class armaa_fullerAutoStats extends BaseShipSystemScript {

	public static final float FLUX_REDUCTION = 15f;
	public static final float MAX_TIME_MULT = 1.25f;
	public static final float MIN_TIME_MULT = 0.1f;
	
	public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {

		ShipAPI ship = null;
		boolean player = false;
		if (stats.getEntity() instanceof ShipAPI) {
			ship = (ShipAPI) stats.getEntity();
			player = ship == Global.getCombatEngine().getPlayerShip();
			id = id + "_" + ship.getId();
		} else {
			return;
		}
		
		float shipTimeMult = 1f + (MAX_TIME_MULT - 1f) * effectLevel;

		stats.getEnergyWeaponFluxCostMod().modifyPercent(id, -FLUX_REDUCTION);
		stats.getBallisticWeaponFluxCostMod().modifyPercent(id, -FLUX_REDUCTION);
		stats.getTimeMult().modifyMult(id, shipTimeMult);
		if (player) {
			Global.getCombatEngine().getTimeMult().modifyMult(id, 1f / shipTimeMult);

		} else {
			Global.getCombatEngine().getTimeMult().unmodify(id);
		}
	}
	public void unapply(MutableShipStatsAPI stats, String id) {
		
		ShipAPI ship = null;
		boolean player = false;
		if (stats.getEntity() instanceof ShipAPI) {
			ship = (ShipAPI) stats.getEntity();
			player = ship == Global.getCombatEngine().getPlayerShip();
			id = id + "_" + ship.getId();
		} else {
			return;
		}
		
		stats.getBallisticWeaponFluxCostMod().unmodify(id);
		stats.getEnergyWeaponFluxCostMod().unmodify(id);
		Global.getCombatEngine().getTimeMult().unmodify(id);
		stats.getTimeMult().unmodify(id);
	}
	
	public StatusData getStatusData(int index, State state, float effectLevel) {
		float bonusDilation = ((MAX_TIME_MULT-1)*100)*effectLevel;
		if (index == 0) {
			return new StatusData("WPN FLUX USE -" + (int) FLUX_REDUCTION + "%" , false);
		}
		if (index == 1) {
			return new StatusData("Reaction time +" + (int)bonusDilation + "%", false);
		}
		return null;
	}
}
