package data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShieldAPI.ShieldType;

public class armaa_selector_counter_shield extends BaseHullMod {

	/* A Ship of the 14th Domain Battlegroup
	 * well-maintained survivor of the original battlegroup which founded the Hegemony
	 * Sterling example of the Domain Navy's traditional "decisive battle" doctrine
	 * focus on superior armour and firepower on ships of the line to destroy the enemy
	 * - slightly better flux handling
	 * - slightly better armour
	 * - slightly worse speed/maneuver
	 * - 
	 */
	
	private static final float ARMOR_BONUS_MULT = 1.1f;
	private static final float SHIELD_MALUS = -75f;
	private static final float CAPACITY_MULT = 1.2f;
	private static final float DISSIPATION_MULT = 1.2f;
	private static final float HANDLING_MULT = 1.2f;
	
	/*private static Map mag = new HashMap();
	static {
		mag.put(HullSize.FIGHTER, 0.0f);
		mag.put(HullSize.FRIGATE, 0.25f);
		mag.put(HullSize.DESTROYER, 0.15f);
		mag.put(HullSize.CRUISER, 0.10f);
		mag.put(HullSize.CAPITAL_SHIP, 0.05f);
	}*/	
	
	public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) 
	{
		stats.getShieldArcBonus().modifyFlat(id, SHIELD_MALUS);
		
	}
	
	public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
		ShieldAPI shield = ship.getShield();
		if (shield != null) {
			shield.setType(ShieldType.OMNI);
		}
	}
	
	public String getDescriptionParam(int index, HullSize hullSize) {
//		if (index == 0) return "" + (int) ((Float) mag.get(HullSize.FRIGATE) * 100f);
//		if (index == 1) return "" + (int) ((Float) mag.get(HullSize.DESTROYER) * 100f);
//		if (index == 2) return "" + (int) ((Float) mag.get(HullSize.CRUISER) * 100f);
//		if (index == 3) return "" + (int) ((Float) mag.get(HullSize.CAPITAL_SHIP) * 100f);
		//if (index == 0) return Misc.getRoundedValue((Float) mag.get(HullSize.FRIGATE) + 1f);
		//if (index == 1) return Misc.getRoundedValue((Float) mag.get(HullSize.DESTROYER) + 1f);
		//if (index == 2) return Misc.getRoundedValue((Float) mag.get(HullSize.CRUISER) + 1f);
		//if (index == 3) return Misc.getRoundedValue((Float) mag.get(HullSize.CAPITAL_SHIP) + 1f);
		//if (index == 0) return Misc.getRoundedValue(ARMOR_BONUS);
	//	if (index == 1) return "" + (int) ((HANDLING_MULT - 1f) * 100f) + "%"; // + Strings.X;
	//	if (index == 2) return "" + (int) ((CAPACITY_MULT - 1f) * 100f) + "%"; 
		return null;
	}

	/*public String getDescriptionParam(int index, HullSize hullSize) {
		if (index == 0) return "" + ((Float) mag.get(HullSize.FRIGATE)).intValue();
		if (index == 1) return "" + ((Float) mag.get(Hullize.DESTROYER)).intValue();
		if (index == 2) return "" + ((Float) mag.get(HullSize.CRUISER)).intValue();
		if (index == 3) return "" + ((Float) mag.get(HullSize.CAPITAL_SHIP)).intValue();
		if (index == 4) return "" + (int) ACCELERATION_BONUS;
		//if (index == 5) return "four times";
		if (index == 5) return "4" + Strings.X;
		if (index == 6) return "" + BURN_LEVEL_BONUS;
		return null;
	}*/

}
