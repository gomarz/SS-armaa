package data.hullmods;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.impl.campaign.ids.HullMods;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;

public class armaa_noVent extends BaseHullMod {

    private ShipwideAIFlags flags;

	private static Map speed = new HashMap();
	static {
		speed.put(HullSize.FRIGATE, 50f);
		speed.put(HullSize.DESTROYER, 30f);
		speed.put(HullSize.CRUISER, 20f);
		speed.put(HullSize.CAPITAL_SHIP, 10f);
	}
	
//	private static Map flux = new HashMap();
//	static {
//		flux.put(HullSize.FRIGATE, 100f);
//		flux.put(HullSize.DESTROYER, 150f);
//		flux.put(HullSize.CRUISER, 200f);
//		flux.put(HullSize.CAPITAL_SHIP, 300f);
//	}
	
	//private static final float PEAK_MULT = 0.3333f;
	private static final float PEAK_MULT = 0.33f;
	//private static final float CR_DEG_MULT = 2f;
	//private static final float OVERLOAD_DUR = 50f;
	private static final float FLUX_DISSIPATION_MULT = 2f;
	//private static final float FLUX_CAPACITY_MULT = 1f;
	
	private static final float RANGE_THRESHOLD = 450f;
	private static final float RANGE_MULT = 0.25f;
	
	//private static final float RECOIL_MULT = 2f;
	//private static final float MALFUNCTION_PROB = 0.05f;
	
	public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {

		//stats.getVentRateMult().modifyMult(id, 0f);
	}
	
	public String getDescriptionParam(int index, HullSize hullSize) {
		
		
		return null;
	}

	@Override
	public boolean isApplicableToShip(ShipAPI ship) {
//		return !ship.getVariant().getHullMods().contains("unstable_injector") &&
//			   !ship.getVariant().getHullMods().contains("augmented_engines");
		if (ship.getVariant().getHullSize() == HullSize.CAPITAL_SHIP) return false;
		if (ship.getVariant().hasHullMod(HullMods.CIVGRADE) && !ship.getVariant().hasHullMod(HullMods.MILITARIZED_SUBSYSTEMS)) return false;
		
		
		return true;
	}
	
	public String getUnapplicableReason(ShipAPI ship) {
		if (ship.getVariant().getHullSize() == HullSize.CAPITAL_SHIP) {
			return "Can not be installed on capital ships";
		}
		if (ship.getVariant().hasHullMod(HullMods.CIVGRADE) && !ship.getVariant().hasHullMod(HullMods.MILITARIZED_SUBSYSTEMS)) {
			return "Can not be installed on civilian ships";
		}
		
		return null;
	}
	

	private Color color = new Color(255,100,255,255);
	@Override
	public void advanceInCombat(ShipAPI ship, float amount) 
	{
		if(flags == null || flags != ship.getAIFlags())
		flags = ship.getAIFlags();

		else
		flags.setFlag(AIFlags.DO_NOT_VENT, 10f);

	}

	

}
