package data.scripts.shipsystems;


import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.impl.campaign.ids.Stats;

public class MPhaseDamperStats extends BaseShipSystemScript {

	public static final float MAX_TIME_MULT = 1.30f;
	public static final float MIN_TIME_MULT = 0.1f;

	// reduce all damage by 20%
	public static final float DEF_MULT = 0.80f;
	
	// reduce beam damage an additional 40%. 100 beam damage -> 18 damage
	// if I did my math right
	public static final float DEF_BEAM_MULT = 0.60f;
	public static final float DAM_MULT = 0.1f;
	
	protected Object STATUSKEY1 = new Object();
	
	public static float getMaxTimeMult(MutableShipStatsAPI stats) {
		return 1f + (MAX_TIME_MULT - 1f) * stats.getDynamic().getValue(Stats.PHASE_TIME_BONUS_MULT);
	}


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
		
		//if(!player) ship.setPhased(true);
		float jitterLevel = effectLevel;
		float jitterRangeBonus = 0;
		float maxRangeBonus = 10f;

		effectLevel *= effectLevel;

		float shipTimeMult = 1f + (getMaxTimeMult(stats) - 1f) * effectLevel;
		stats.getTimeMult().modifyMult(id, shipTimeMult);

		stats.getHullDamageTakenMult().modifyMult(id, 1f - (1f - DEF_MULT) * effectLevel);
		stats.getArmorDamageTakenMult().modifyMult(id, 1f - (1f - DEF_MULT) * effectLevel);
		stats.getEmpDamageTakenMult().modifyMult(id, 1f - (1f - DEF_MULT) * effectLevel);

		stats.getBeamDamageTakenMult().modifyMult(id, 1f - (1f - DEF_BEAM_MULT) * effectLevel);

		if (player) {
			Global.getCombatEngine().getTimeMult().modifyMult(id, 1f / shipTimeMult);
//			if (ship.areAnyEnemiesInRange()) {
//				Global.getCombatEngine().getTimeMult().modifyMult(id, 1f / shipTimeMult);
//			} else {
//				Global.getCombatEngine().getTimeMult().modifyMult(id, 2f / shipTimeMult);
//			}
		} else {
			Global.getCombatEngine().getTimeMult().unmodify(id);
		}

if (player) {
			ShipSystemAPI system = ship.getPhaseCloak();
			if (system != null) {
				float percent = (1f - DEF_MULT) * effectLevel * 100;
				float percent2 = (1f - shipTimeMult) * effectLevel * 100 *-1;
				Global.getCombatEngine().maintainStatusForPlayerShip(STATUSKEY1,
					system.getSpecAPI().getIconSpriteName(), system.getDisplayName(),
					(int) Math.round(percent) + "% DMG REDUCTION / " + (int) Math.round(percent2) + "% TIME-FLOW ALTERATION", false);
			}
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
		Global.getCombatEngine().getTimeMult().unmodify(id);
		stats.getTimeMult().unmodify(id);
		//if(!player) ship.setPhased(false);
		
		stats.getHullDamageTakenMult().unmodify(id);
		stats.getArmorDamageTakenMult().unmodify(id);
		stats.getEmpDamageTakenMult().unmodify(id);
		stats.getBeamDamageTakenMult().unmodify(id);
	}
	
	
//	public StatusData getStatusData(int index, State state, float effectLevel) {
//		float mult = (Float) mag.get(HullSize.CRUISER);
//		if (stats.getVariant() != null) {
//			mult = (Float) mag.get(stats.getVariant().getHullSize());
//		}
//		effectLevel = 1f;
//		float percent = (1f - INCOMING_DAMAGE_MULT) * effectLevel * 100;
//		if (index == 0) {
//			return new StatusData((int) percent + "% less damage taken", false);
//		}
//		return null;
//	}
}
