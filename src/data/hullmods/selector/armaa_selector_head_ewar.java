package data.hullmods.selector;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.util.Misc;
import data.scripts.weapons.armaa_valkTargetingLaserEffect;
import java.awt.Color;

public class armaa_selector_head_ewar extends BaseHullMod {
    public static final float EW_BONUS = 3f;      
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
        stats.getDynamic().getMod(Stats.ELECTRONIC_WARFARE_FLAT)
                .modifyFlat(id, EW_BONUS);
    }

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return "Targeting Head";
        if (index == 1) return "Remove this hullmod to cycle between heads.";
        return null;
    }

    private final Color HL = Global.getSettings().getColor("hColor");
    private final Color F  = Global.getSettings().getColor("textFriendColor");
    private final Color E  = Global.getSettings().getColor("textEnemyColor");

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize,
            ShipAPI ship, float width, boolean isForModSpec) {
        float pad  = 10f;
        float padS = 2f;
        Color[] pos = { Misc.getHighlightColor(), F };
        Color[] neg = { Misc.getHighlightColor(), E };

        tooltip.addSectionHeading("Targeting Head - Electronic Warfare Suite", Alignment.MID, pad);
        tooltip.addPara(
            "Replaces the standard head with a focused EWAR laser system designed to "
            + "cripple and disable enemy combat effectiveness. ",
            padS
        );

        tooltip.addSectionHeading("Weapon Effects", Alignment.MID, pad);
        tooltip.addPara("%s Reduces target autofire accuracy by %s while painted.",
                padS, pos, "-", (int) armaa_valkTargetingLaserEffect.AUTOAIM_DEBUFF + "%");
        tooltip.addPara("%s Increases damage taken by target weapons by %s.",
                padS, pos, "-", (int)((armaa_valkTargetingLaserEffect.WEAPON_DAMAGE_MULT - 1f) * 100f) + "%");
        tooltip.addPara("%s Increases damage taken by target engines by %s.",
                padS, pos, "-", (int)((armaa_valkTargetingLaserEffect.ENGINE_DAMAGE_MULT - 1f) * 100f) + "%");
        tooltip.addPara("%s At high flux levels, the beam has up to a %s chance per interval to arc EMP directly through target shields.",
                padS, pos, "-", (int)(armaa_valkTargetingLaserEffect.EMP_PIERCE_CHANCE_MAX * 100f) + "%");
        tooltip.addPara("%s Electronic warfare rating increased by %s",
                padS, pos, "-", String.valueOf((int) EW_BONUS)+"%");
        tooltip.addSectionHeading("Notes", Alignment.MID, pad);
        tooltip.addPara(
            "Middleground between support and offense. "
            + "Pairs well with high-volume allied fire to exploit reduced accuracy, weapon and engine damage bonuses.",
            padS
        );
    }
}