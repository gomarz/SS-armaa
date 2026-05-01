package data.hullmods;
 
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.weapons.armaa_linkedSystems;
import java.awt.Color;
 
public class armaa_reactiveCarapace extends BaseHullMod {
 
    private final Color flavor = new Color(110, 110, 110, 255);
 
    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        // No stats applied here.  effects are handled by the shield module's weapon script
    }
 
    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return false;
    }
 
    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize,
            ShipAPI ship, float width, boolean isForModSpec) {
        float pad  = 10f;
        float padS = 2f;
 
        tooltip.addSectionHeading("Details", Alignment.MID, 10);
 
        tooltip.addPara("%s This unit is equipped with a detachable ablative shield module"
                + " that absorbs incoming fire in place of the main hull.",
                pad, Misc.getHighlightColor(), "\u2022");
 
        tooltip.addPara("%s While the shield module is active, speed, turn rate,"
                + " and weapon traverse are reduced by %s.",
                padS, Misc.getHighlightColor(),
                "\u2022", (int)(armaa_linkedSystems.MANUEVER_MALUS * 100f) + "%");
 
        tooltip.addPara("%s When the shield module is destroyed, the maneuverability"
                + " penalty is removed.",
                padS, Misc.getHighlightColor(), "\u2022");
 
        tooltip.addSectionHeading("Reactive Repair", Alignment.MID, 10);
 
        tooltip.addPara("%s The shield module gradually regenerates hull and armor"
                + " at %s per second when not under fire.",
                pad, Misc.getHighlightColor(),
                "\u2022", (int)(armaa_linkedSystems.REGEN_HP_PERCENT_PER_SECOND * 100f) + "%");
 
        tooltip.addPara("%s Regeneration is suppressed for %s seconds after taking damage"
                + " and does not occur while the unit is phased.",
                padS, Misc.getHighlightColor(),
                "\u2022", (int) armaa_linkedSystems.REGEN_PAUSE_DURATION + "s");
 
        tooltip.addPara("%s Regeneration only activates if the module has sustained"
                + " at least %s damage to hull or armor.",
                padS, Misc.getHighlightColor(),
                "\u2022", (int)(armaa_linkedSystems.REGEN_MIN_THRESHOLD * 100f) + "%");
 
        tooltip.addPara("%s", 12f, flavor, new String[]{
            "\"The carapace isn't armor in any traditional sense."
            + " It's a sacrifice. You're not protecting the pilot —"
            + " you're giving the enemy something cheaper to kill first.\""
        }).italicize();
        tooltip.addPara("%s", 1f, flavor,
                new String[]{"         \u2014 Field Notes on Mech Combat Doctrine, author unknown"});
    }
}