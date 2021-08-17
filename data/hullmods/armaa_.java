package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class HMI_Monjeau_Armour extends BaseHullMod {

    public static final float ARMOR_STRENGTH_MULT = 20.0f;
    public static final float ARMOR_MAX_REDUCTION_BONUS = 4.0f;
    public static final float SUBSYSTEM_HEALTH_BONUS = 300f;
    public static final float EMP_TAKEN_MULT = 0.5f;

    private static final Set<String> BLOCKED_HULLMODS = new HashSet<>(1);

    static {
        BLOCKED_HULLMODS.add("advancedshieldemitter");
        BLOCKED_HULLMODS.add("extendedshieldemitter");
        BLOCKED_HULLMODS.add("frontshield");
		BLOCKED_HULLMODS.add("hardenedshieldemitter");
		BLOCKED_HULLMODS.add("adaptiveshields");
        BLOCKED_HULLMODS.add("stabilizedshieldemitter");
		BLOCKED_HULLMODS.add("shield_shunt");
        BLOCKED_HULLMODS.add("swp_shieldbypass");
        BLOCKED_HULLMODS.add("heavyarmor");
    }
	
	private float check=0;
    private String id, ERROR="IncompatibleHullmodWarning";

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getEffectiveArmorBonus().modifyMult(id, ARMOR_STRENGTH_MULT);
        stats.getMaxArmorDamageReduction().modifyFlat(id, ARMOR_MAX_REDUCTION_BONUS);
        stats.getEmpDamageTakenMult().modifyMult(id, EMP_TAKEN_MULT);
        stats.getWeaponHealthBonus().modifyPercent(id, SUBSYSTEM_HEALTH_BONUS);
        stats.getEngineHealthBonus().modifyPercent(id, SUBSYSTEM_HEALTH_BONUS);
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id){

        if (check>0) {
            check-=1;
            if (check<1){
                ship.getVariant().removeMod(ERROR);
            }
        }

        for (String tmp : BLOCKED_HULLMODS) {
            if (ship.getVariant().getHullMods().contains(tmp)) {
                ship.getVariant().removeMod(tmp);
                ship.getVariant().addMod(ERROR);
                check=3;
            }
        }
    }

    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) return "" + (int)ARMOR_STRENGTH_MULT;
        if (index == 1) return "" + (int)ARMOR_MAX_REDUCTION_BONUS + "%";
        if (index == 2) return "" + "beam weapons";
        if (index == 3) return "" + "50%";
        if (index == 4) return "" + "300";
        if (index == 5) return "" + "no room to install a shield generator, and the installation of heavy armour in inhibited";
        return null;
    }
}
