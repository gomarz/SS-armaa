/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.impl.campaign.ids.Stats;

public class armaa_barbicanArray extends BaseHullMod {

    public static final float SMALL_PD_OP_REDUCTION  = 3f;
    public static final float MEDIUM_PD_OP_REDUCTION = 5f;
    public static final float PD_RANGE_BONUS         = 20f;  // percent
    public static final float ANTI_MISSILE_BONUS     = 15f;  // percent

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getDynamic().getMod(Stats.SMALL_PD_MOD).modifyFlat(id, -SMALL_PD_OP_REDUCTION);
        stats.getDynamic().getMod(Stats.MEDIUM_PD_MOD).modifyFlat(id, -MEDIUM_PD_OP_REDUCTION);

        stats.getBeamPDWeaponRangeBonus().modifyPercent(id, PD_RANGE_BONUS);
        stats.getNonBeamPDWeaponRangeBonus().modifyPercent(id, PD_RANGE_BONUS);

        stats.getDamageToMissiles().modifyPercent(id, ANTI_MISSILE_BONUS);
        stats.getDamageToFighters().modifyPercent(id, ANTI_MISSILE_BONUS);
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) return "" + (int) SMALL_PD_OP_REDUCTION;
        if (index == 1) return "" + (int) MEDIUM_PD_OP_REDUCTION;
        if (index == 2) return "" + (int) PD_RANGE_BONUS + "%";
        if (index == 3) return "" + (int) ANTI_MISSILE_BONUS + "%";
        return null;
    }

    @Override
    public boolean affectsOPCosts() { return true; }
}


