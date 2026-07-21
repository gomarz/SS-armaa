package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import java.util.HashMap;
import java.util.Map;
import org.lazywizard.lazylib.MathUtils;

//Original code by Tartiflette
//Left-arm-only version: right arm is now a modular slot, so only C_ARML cycles.
public class armaa_alesteMeleeSwap extends BaseHullMod {

    private static final String LEFT_SLOT_ID = "C_ARML";

    //index -> weapon installed for that fire mode
    public final Map<Integer, String> LEFT_SELECTOR = new HashMap<>();

    {
        LEFT_SELECTOR.put(0, "armaa_koutoBazooka");
        LEFT_SELECTOR.put(1, "armaa_alesteLeftArm");
        LEFT_SELECTOR.put(2, "armaa_aleste_rifle_left");
        LEFT_SELECTOR.put(3, "armaa_aleste_grenade_left");
        LEFT_SELECTOR.put(4, "armaa_aleste_blade_LeftArm");
        LEFT_SELECTOR.put(5, "armaa_alestePilebunkerLeft");
    }

    //current weapon -> index of the NEXT fire mode in the cycle
    //bazooka -> grenade -> rifle -> arm -> pilebunker -> blade -> bazooka
    private final Map<String, Integer> SWITCH_TO_LEFT = new HashMap<>();

    {
        SWITCH_TO_LEFT.put("armaa_koutoBazooka", 3);
        SWITCH_TO_LEFT.put("armaa_aleste_grenade_left", 2);
        SWITCH_TO_LEFT.put("armaa_aleste_rifle_left", 1);
        SWITCH_TO_LEFT.put("armaa_alesteLeftArm", 5);
        SWITCH_TO_LEFT.put("armaa_alestePilebunkerLeft", 4);
        SWITCH_TO_LEFT.put("armaa_aleste_blade_LeftArm", 0);
    }

    //index -> selector hullmod marking that fire mode as active
    private final Map<Integer, String> LEFTSWITCH = new HashMap<>();

    {
        LEFTSWITCH.put(0, "armaa_selector_bazooka");
        LEFTSWITCH.put(1, "armaa_selector_stake");
        LEFTSWITCH.put(2, "armaa_selector_lrifle_left");
        LEFTSWITCH.put(3, "armaa_selector_grenade");
        LEFTSWITCH.put(4, "armaa_selector_blade");
        LEFTSWITCH.put(5, "armaa_alestePilebunker");
    }

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {

        //trigger a weapon switch if none of the selector hullmods are present
        boolean toSwitchLeft = true;
        for (String selectorMod : LEFTSWITCH.values()) {
            if (stats.getVariant().getHullMods().contains(selectorMod)) {
                toSwitchLeft = false;
                break;
            }
        }

        if (!toSwitchLeft) {
            return;
        }

        //select new fire mode
        int selected;
        boolean random = false;
        if (stats.getVariant().getWeaponSpec(LEFT_SLOT_ID) != null
                && SWITCH_TO_LEFT.get(stats.getVariant().getWeaponSpec(LEFT_SLOT_ID).getWeaponId()) != null) {
            selected = SWITCH_TO_LEFT.get(stats.getVariant().getWeaponSpec(LEFT_SLOT_ID).getWeaponId());
        } else {
            selected = MathUtils.getRandomNumberInRange(0, LEFT_SELECTOR.size() - 1);
            random = true;
        }

        //add the proper hullmod, then swap in the matching weapon
        stats.getVariant().addMod(LEFTSWITCH.get(selected));
        stats.getVariant().clearSlot(LEFT_SLOT_ID);
        stats.getVariant().addWeapon(LEFT_SLOT_ID, LEFT_SELECTOR.get(selected));

        if (random) {
            stats.getVariant().autoGenerateWeaponGroups();
        }
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {

        if (ship.getOriginalOwner() < 0) {
            //undo fix for harvests put in cargo
            if (Global.getSector() != null
                    && Global.getSector().getPlayerFleet() != null
                    && Global.getSector().getPlayerFleet().getCargo() != null
                    && Global.getSector().getPlayerFleet().getCargo().getStacksCopy() != null
                    && !Global.getSector().getPlayerFleet().getCargo().getStacksCopy().isEmpty()) {
                for (CargoStackAPI s : Global.getSector().getPlayerFleet().getCargo().getStacksCopy()) {
                    if (s.isWeaponStack() && LEFT_SELECTOR.containsValue(s.getWeaponSpecIfWeapon().getWeaponId())) {
                        Global.getSector().getPlayerFleet().getCargo().removeStack(s);
                    }
                }
            }
        }
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) {
            return "A";
        }
        if (index == 1) {
            return "B";
        }
        if (index == 2) {
            return "C";
        }
        if (index == 3) {
            return "D";
        }
        return null;
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return (ship.getHullSpec().getHullId().startsWith("armaa_"));
    }
}