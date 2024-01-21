package data.hullmods;

import java.util.Iterator;
import java.util.List;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import org.magiclib.util.MagicIncompatibleHullmods;

import java.util.HashSet;
import java.util.Set;
public class armaa_SeraphTargetingSoftware extends BaseHullMod {

	public static final float DAMAGE_BONUS = 50f;
	
		private static final Set<String> BLOCKED_HULLMODS = new HashSet<>();
		static{
			// These hullmods will automatically be removed
			// This prevents unexplained hullmod blocking
			BLOCKED_HULLMODS.add("converted_hangar");
			BLOCKED_HULLMODS.add("cargo_expansion");
			BLOCKED_HULLMODS.add("additional_crew_quarters");
			BLOCKED_HULLMODS.add("fuel_expansion");
			BLOCKED_HULLMODS.add("additional_berthing");
			BLOCKED_HULLMODS.add("auxiliary_fuel_tanks");
			BLOCKED_HULLMODS.add("expanded_cargo_holds");
			BLOCKED_HULLMODS.add("surveying_equipment");
			BLOCKED_HULLMODS.add("recovery_shuttles");
			BLOCKED_HULLMODS.add("operations_center");
			BLOCKED_HULLMODS.add("pointdefenseai");
			BLOCKED_HULLMODS.add("safetyoverrides");
		}
	
	@Override
	public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {

		stats.getDynamic().getMod(Stats.PD_IGNORES_FLARES).modifyFlat(id, 1f);
		stats.getDynamic().getMod(Stats.PD_BEST_TARGET_LEADING).modifyFlat(id, 1f);
        	stats.getAutofireAimAccuracy().modifyMult(id, 1.5f);
		stats.getDamageToMissiles().modifyPercent(id, DAMAGE_BONUS);
		//fuck fighters
		stats.getDamageToFighters().modifyPercent(id, DAMAGE_BONUS);
		
	}

	public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
		List weapons = ship.getAllWeapons();
		Iterator iter = weapons.iterator();
		while (iter.hasNext()) {
			WeaponAPI weapon = (WeaponAPI)iter.next();
//			if (weapon.hasAIHint(AIHints.PD)) {
//				weapon.get
//			}
			boolean sizeMatches = weapon.getSize() == WeaponSize.SMALL;
			//sizeMatches |= weapon.getSize() == WeaponSize.MEDIUM;
			
			if (sizeMatches && weapon.getType() != WeaponType.MISSILE) {
				weapon.setPD(true);
			}
		}
		
		for (String tmp : BLOCKED_HULLMODS)
        {
			if(ship.getVariant().getHullMods().contains(tmp))
			{
   				MagicIncompatibleHullmods.removeHullmodWithWarning(ship.getVariant(), tmp, "armaa_SeraphTargetingSoftware");
			}
        }
	}
	
	public String getDescriptionParam(int index, HullSize hullSize) {
		if (index == 0) return "" + (int)Math.round(DAMAGE_BONUS) + "%";
		if (index == 1) return "incompatible with Safety Overrides.";
		if (index == 2) return "additional large scale modifications cannot be made to the hull";
		return null;
	}


}
