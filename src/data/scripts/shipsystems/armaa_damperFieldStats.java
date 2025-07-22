package data.scripts.shipsystems;

import java.util.HashMap;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;

public class armaa_damperFieldStats extends BaseShipSystemScript {

	private static Map mag = new HashMap();
	static {
		mag.put(HullSize.FIGHTER, 0.2f);
		mag.put(HullSize.FRIGATE, 0.2f);
		mag.put(HullSize.DESTROYER, 0.5f);
		mag.put(HullSize.CRUISER, 0.5f);
		mag.put(HullSize.CAPITAL_SHIP, 0.5f);
	}
	
	protected Object STATUSKEY1 = new Object();
	//public static final float MAX_TIME_MULT = 1.2f;
	//public static final float MIN_TIME_MULT = 0.1f;
	
	//public static final float INCOMING_DAMAGE_MULT = 0.25f;
	public static final float INCOMING_DAMAGE_CAPITAL = 0.5f;
	/*
	public static float getMaxTimeMult(MutableShipStatsAPI stats) {
		return 1f + (MAX_TIME_MULT - 1f);
	}
	*/
	public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
		effectLevel = 1f;
		
		float mult = (Float) mag.get(HullSize.CRUISER);
		if (stats.getVariant() != null) {
			mult = (Float) mag.get(stats.getVariant().getHullSize());
		}
		stats.getHullDamageTakenMult().modifyMult(id, 1f - (1f - mult) * effectLevel);
		stats.getArmorDamageTakenMult().modifyMult(id, 1f - (1f - mult) * effectLevel);
		stats.getEmpDamageTakenMult().modifyMult(id, 1f - (1f - mult) * effectLevel);
	//	float shipTimeMult = 1f + (getMaxTimeMult(stats) - 1f) * effectLevel;
	//	stats.getTimeMult().modifyMult(id, shipTimeMult);
			
		ShipAPI ship = null;
		boolean player = false;
		if (stats.getEntity() instanceof ShipAPI) {
			ship = (ShipAPI) stats.getEntity();
			player = ship == Global.getCombatEngine().getPlayerShip();
		}
		if(player) 
		{
			ShipSystemAPI system = ship.getPhaseCloak();
			if (system != null) 
			{
				//Global.getCombatEngine().getTimeMult().modifyMult(id, 1f / shipTimeMult);
				float percent = (1f - mult) * effectLevel * 100;
				Global.getCombatEngine().maintainStatusForPlayerShip(STATUSKEY1,
					system.getSpecAPI().getIconSpriteName(), system.getDisplayName(),
					(int) Math.round(percent) + "% less damage taken", false);
			}
		}
		
		/*else 
		{
			Global.getCombatEngine().getTimeMult().unmodify(id);
		}	
		*/
	}
	public void unapply(MutableShipStatsAPI stats, String id) {
		stats.getHullDamageTakenMult().unmodify(id);
		stats.getArmorDamageTakenMult().unmodify(id);
		stats.getEmpDamageTakenMult().unmodify(id);
	//	Global.getCombatEngine().getTimeMult().unmodify(id);
	//	stats.getTimeMult().unmodify(id);
	}
	
}
