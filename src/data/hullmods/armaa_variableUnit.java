package data.hullmods;

import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.Misc;
import java.awt.Color;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.Alignment;
import org.lazywizard.lazylib.combat.CombatUtils;
import com.fs.starfarer.api.combat.CombatEntityAPI;

import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

public class armaa_variableUnit extends BaseHullMod {

    private static final float DAMAGE_BONUS = 50f;
    private static final float ROF_MALUS = 40f;
    private static final float SPEED_MALUS = 50f;
    private static final float MAX_TIME_MULT = 1.10f;
    private static final float MIN_TIME_MULT = 0.1f;
    private static final Set<String> BLOCKED_HULLMODS = new HashSet<>();
    public static final Map<String, Float> GROUND_BONUS = new HashMap<>();

    static {
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
        BLOCKED_HULLMODS.add("expanded_deck_crew");
        BLOCKED_HULLMODS.add("combat_docking_module");
        BLOCKED_HULLMODS.add("roider_fighterClamps");
    }

    static {
        GROUND_BONUS.put("armaa_guardual", 40f);
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float HEIGHT = 64f;
        float PAD = 10f;
        Color YELLOW = new Color(241, 199, 0);
        String fighterIcon = "graphics/armaa/icons/hullsys/armaa_fighter_icon.png";
        String mechIcon = "graphics/armaa/icons/hullsys/armaa_soldier_icon.png";
        String DNITitle = "Mode ALPHA";
        String DNIText1 = "\u2022 +%s projectile speed. Disables hybrid slot.";
        String DNIText2 = "\u2022 Manuverability reduced by %s";
        String DNIText3 = "\u2022 Shield efficiency reduced by %s";
        String DNIText4 = "\u2022 +%s time dilation when near enemies or enemy projectiles";

        String PDTitle = "Mode BETA";
        String PDText1 = "\u2022 +%s ballistic & energy damage. Disables missile slots.";
        String PDText2 = "\u2022 +%s armor strength";
        String PDText3 = "\u2022 -%s top speed";
        String PDText4 = "\u2022 +%s manuverability";

        float pad = 2f;
        Color[] arr = {Misc.getPositiveHighlightColor(), Misc.getHighlightColor(), Misc.getNegativeHighlightColor()};
        Color[] arr2 = {Misc.getPositiveHighlightColor(), Misc.getNegativeHighlightColor()};
        tooltip.addSectionHeading("Details", Alignment.MID, 5);
        TooltipMakerAPI transformInfo = tooltip.beginImageWithText(fighterIcon, HEIGHT);
        transformInfo.addPara(DNITitle, pad, YELLOW, DNITitle);
        transformInfo.addPara(DNIText1, pad, arr2, SPEED_MALUS + "%", "Disables hybrid slot");
        transformInfo.addPara(DNIText2, pad, Misc.getNegativeHighlightColor(), SPEED_MALUS + "%");
        transformInfo.addPara(DNIText3, pad, Misc.getHighlightColor(), SPEED_MALUS + "%");
        transformInfo.addPara(DNIText4, pad, Misc.getPositiveHighlightColor(), (int) ((MAX_TIME_MULT - 1f) * 100f) + "%");
        UIPanelAPI temp = tooltip.addImageWithText(PAD);
        Color[] disabletext = {Global.getSettings().getColor("textGrayColor"), Misc.getNegativeHighlightColor()};
        if (hullSize == ShipAPI.HullSize.FRIGATE) {
            TooltipMakerAPI pd = tooltip.beginImageWithText(mechIcon, HEIGHT);
            pd.addPara(PDTitle, pad, YELLOW, PDTitle);
            pd.addPara(PDText1, pad, arr, (int) DAMAGE_BONUS + "%", "Disables missile slots");
            pd.addPara(PDText2, pad, Misc.getPositiveHighlightColor(), (int) DAMAGE_BONUS + "%");
            pd.addPara(PDText3, pad, Misc.getNegativeHighlightColor(), SPEED_MALUS + "%");
            pd.addPara(PDText4, pad, Misc.getPositiveHighlightColor(), (int) (DAMAGE_BONUS) + "%");
            temp = tooltip.addImageWithText(PAD);
        }
        float level = 0;
        if (ship != null) {
            level = ship.getCaptain().isDefault() ? 0 : ship.getCaptain().getStats().getLevel() * 2f;
        }
        float n2 = GROUND_BONUS.get(ship.getHullSpec().getHullId()) != null ? GROUND_BONUS.get(ship.getHullSpec().getHullId()) : 25f;
        int n = (int) (n2 + level);
        tooltip.addPara("%s " + "Increases effective strength of ground ops by %s, (up to number of marines in the fleet)", pad * 2, Misc.getHighlightColor(), "\u2022", Integer.toString(n));
        tooltip.addPara("%s", 6f, Misc.getGrayColor(), new String[]{"\"Keeping this thing running is like patching up three LSMs at once. A miracle when it's flight-ready, and a nightmare when it's not. Makes you understand why they kept production numbers low.\""}).italicize();
        tooltip.addPara("%s", 1f, Misc.getGrayColor(), new String[]{"         \u2014 Chief Mechanic"});

    }

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
    }

    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {

    }

    @Override
    public void advanceInCampaign(FleetMemberAPI member, float amount) {
        if (member.getFleetData() != null && member.getFleetData().getFleet() != null && member.getFleetData().getFleet().getViewForMember(member) != null) {
            member.getFleetData().getFleet().getViewForMember(member).getContrailColor().setBase(new Color(0f, 0f, 0f, 0f));
            member.getFleetData().getFleet().getViewForMember(member).getEngineColor().setBase(new Color(0f, 0f, 0f, 0f));
            member.setSpriteOverride("");
        }
        float level = member.getCaptain().isDefault() ? 0 : member.getCaptain().getStats().getLevel() * 2f;

        if (GROUND_BONUS.get(member.getHullSpec().getBaseHullId()) != null) {
            member.getStats().getDynamic().getMod(Stats.FLEET_GROUND_SUPPORT).modifyFlat("id", GROUND_BONUS.get(member.getHullSpec().getBaseHullId()) + level);
        } else {
            member.getStats().getDynamic().getMod(Stats.FLEET_GROUND_SUPPORT).unmodify("id");
        }

    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        boolean enemiesInRange = false;
        boolean player = ship == Global.getCombatEngine().getPlayerShip();
        boolean isRobot = Global.getCombatEngine().getCustomData().get("armaa_tranformState_" + ship.getId()) != null
                ? (Boolean) Global.getCombatEngine().getCustomData().get("armaa_tranformState_" + ship.getId()) : false;
        float transformLevel = Global.getCombatEngine().getCustomData().get("armaa_tranformLevel_" + ship.getId()) != null
                ? (Float) Global.getCombatEngine().getCustomData().get("armaa_tranformLevel_" + ship.getId()) : 0f;
        if (ship.isAlive()) {

            for (CombatEntityAPI entity : CombatUtils.getEntitiesWithinRange(ship.getLocation(), 700f)) {
                //if(entity.
                if (entity.getOwner() != ship.getOwner() && entity.getOwner() != 100) {
                    enemiesInRange = true;
                    if (!ship.isLanding() && !ship.isFinishedLanding() && player) {
                        Global.getCombatEngine().maintainStatusForPlayerShip("timeflow", "graphics/icons/hullsys/temporal_shell.png", "Heightened Reaction", "Timeflow at " + (int) (100f + ((MAX_TIME_MULT * transformLevel) - 1f) * 100f) + "%", true);
                        break;
                    }
                }
            }
            if (enemiesInRange && !ship.isLanding() && !ship.isFinishedLanding()) {
                ship.getMutableStats().getTimeMult().modifyMult(ship.getId(), Math.max(1f, MAX_TIME_MULT * transformLevel));
                if (player) {
                    float mult = 1f / (MAX_TIME_MULT);
                    float timeMult = transformLevel * mult + (1 - transformLevel) * 1f;
                    Global.getCombatEngine().getTimeMult().modifyMult(ship.getId() + "_fighterBoost", timeMult);
                }
            }
        } else {
            Global.getCombatEngine().getTimeMult().unmodify(ship.getId() + "_fighterBoost");
            ship.getMutableStats().getTimeMult().unmodify(ship.getId() + "_fighterBoost");
        }
    }

    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) {
            return "X";
        }
        if (index == 1) {
            return "incompatible with Safety Overrides.";
        }
        if (index == 2) {
            return "additional large scale modifications cannot be made to the hull";
        }
        return null;
    }

}
