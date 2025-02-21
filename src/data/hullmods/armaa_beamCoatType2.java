package data.hullmods;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.combat.BaseHullMod;
import org.magiclib.util.MagicIncompatibleHullmods;
import com.fs.starfarer.api.combat.ShipAPI;
import java.util.HashSet;
import java.util.Set;

public class armaa_beamCoatType2 extends BaseHullMod {

	public static final float BEAM_DAMAGE_REDUCTION = 0.40f;
	private static final Set<String> BLOCKED_HULLMODS = new HashSet<>();
	static{
        // These hullmods will automatically be removed
        // This prevents unexplained hullmod blocking
        BLOCKED_HULLMODS.add("heavyarmor");
    }
	
	public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
		stats.getBeamDamageTakenMult().modifyMult(id, BEAM_DAMAGE_REDUCTION);
	}
	
    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id)
    {
        for (String tmp : BLOCKED_HULLMODS)
        {
			if(ship.getVariant().getHullMods().contains(tmp))
			{
   				MagicIncompatibleHullmods.removeHullmodWithWarning(ship.getVariant(), tmp, "cataphract");
			}
        }
    }
	
	public String getDescriptionParam(int index, HullSize hullSize) {
		if (index == 0) return "" + (int) Math.round((1f - BEAM_DAMAGE_REDUCTION) * 100f) + "%";
		if(index == 1) return "Heavy Armor";
		return null;
	}


}
