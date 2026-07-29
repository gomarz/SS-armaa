package data.hullmods.selector;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.util.Misc;
import java.awt.Color;

public class armaa_selector_head_flak extends BaseHullMod {

    public static final float MISSILE_DAMAGE_BONUS = 50f;  // % bonus damage to missiles
    public static final float FIGHTER_DAMAGE_BONUS = 25f;  // % bonus damage to fighters
    public static final float ARMOR_BONUS          = 100f; // flat armor
    public static final float ENERGY_DAMAGE_TAKEN = 0.75f; // multiplier (25% reduction to hull)

    @Override
    public int getDisplaySortOrder() {
        return 2000;
    }

    @Override
    public int getDisplayCategoryIndex() {
        return 3;
    }

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getDamageToMissiles().modifyPercent(id, MISSILE_DAMAGE_BONUS);
        stats.getDamageToFighters().modifyPercent(id, FIGHTER_DAMAGE_BONUS);
        stats.getArmorBonus().modifyFlat(id, ARMOR_BONUS);
        stats.getHullDamageTakenMult().modifyMult(id, ENERGY_DAMAGE_TAKEN);
    }

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return "Assault Flak Head";
        if (index == 1) return "Remove this hullmod to cycle between heads.";
        return null;
    }

    private final Color F  = Global.getSettings().getColor("textFriendColor");

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize,
            ShipAPI ship, float width, boolean isForModSpec) {
        float pad  = 10f;
        float padS = 2f;
        Color[] pos = { Misc.getHighlightColor(), F };

        tooltip.addSectionHeading("Area Suppression Suite", Alignment.MID, pad);
        tooltip.addPara(
            "Replaces the standard head with a reinforced close-assault configuration",
            padS
        );
        tooltip.addPara("%s The mounting hardware for the Assault Flak configuration " + 
                "is significantly heavier than the standard head, with reinforced connection points and blast shielding " + 
                "that incidentally adds meaningful protection to the upper hull section.",
                padS, pos, "-");        

        tooltip.addSectionHeading("Bonuses", Alignment.MID, pad);
        tooltip.addPara("%s Damage to missiles increased by %s.",
                padS, pos, "-", (int) MISSILE_DAMAGE_BONUS + "%");
        tooltip.addPara("%s Damage to fighters increased by %s.",
                padS, pos, "-", (int) FIGHTER_DAMAGE_BONUS + "%");
        tooltip.addPara("%s Armor increased by %s.",
                padS, pos, "-", (int) ARMOR_BONUS + "");
        tooltip.addPara("%s Incoming hull damage reduced by %s.",
                padS, pos, "-", (int)((1f - ENERGY_DAMAGE_TAKEN) * 100f) + "%");

        tooltip.addSectionHeading("Notes", Alignment.MID, pad);
        tooltip.addPara(
            "Best suited for extra survivability, or engagements against carrier-heavy / missile-heavy opponents.",
            padS
        );
    }
}