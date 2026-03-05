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
public class armaa_valkazardKaiWeaponSwap extends BaseHullMod {


    public Map<Integer, String> LEFT_SELECTOR = new HashMap<>();

    {
        LEFT_SELECTOR.put(0, "armaa_valkazard_harpoon");
        LEFT_SELECTOR.put(4, "armaa_valkazard_rcl_left");
        LEFT_SELECTOR.put(5, "armaa_valkazard_blade_lv2");
        if(1==1)
        {
            LEFT_SELECTOR.put(2, "armaa_valkazard_pulse_rifle_left_lv2");
            LEFT_SELECTOR.put(1, "armaa_valkazard_shotgun_left_lv2");
            LEFT_SELECTOR.put(3, "armaa_valkazard_machinegun_left_lv2");
        }
    }

    public Map<Integer, String> RIGHT_SELECTOR = new HashMap<>();

    {
        RIGHT_SELECTOR.put(0, "armaa_valkazard_rcl");
        RIGHT_SELECTOR.put(3, "armaa_valkazard_machinegun_right_lv2");

        if(1==1)
        {
            RIGHT_SELECTOR.put(1, "armaa_valkazard_chaingun_lv2");
            RIGHT_SELECTOR.put(2, "armaa_valkazard_pulse_rifle_right_lv2");
            RIGHT_SELECTOR.put(4, "armaa_valkazard_shotgun_right_lv2");
        }
    }

    private final Map<String, Integer> SWITCH_TO_LEFT = new HashMap<>();

    {
        SWITCH_TO_LEFT.put("armaa_valkazard_harpoon", 5);
        SWITCH_TO_LEFT.put("armaa_valkazard_blade_lv2", 4);
        SWITCH_TO_LEFT.put("armaa_valkazard_rcl_left", 3);

        if(1==1)
        {
            SWITCH_TO_LEFT.put("armaa_valkazard_shotgun_left_lv2", 0);
            SWITCH_TO_LEFT.put("armaa_valkazard_pulse_rifle_left_lv2", 1);
            SWITCH_TO_LEFT.put("armaa_valkazard_machinegun_left_lv2", 2);
        }
    }

    private final Map<String, Integer> SWITCH_TO_RIGHT = new HashMap<>();
    {
        if(1==1)
        {
            SWITCH_TO_RIGHT.put("armaa_valkazard_chaingun_lv2", 0);
            SWITCH_TO_RIGHT.put("armaa_valkazard_shotgun_right_lv2", 3);
            SWITCH_TO_RIGHT.put("armaa_valkazard_pulse_rifle_right_lv2", 1);
        }
        SWITCH_TO_RIGHT.put("armaa_valkazard_rcl", 4);
        SWITCH_TO_RIGHT.put("armaa_valkazard_machinegun_right_lv2", 2);


    }

    private final Map<Integer, String> RIGHTSWITCH = new HashMap<>();

    {
        RIGHTSWITCH.put(0, "armaa_selector_rcl");
        RIGHTSWITCH.put(1, "armaa_selector_chaingun");
        RIGHTSWITCH.put(2, "armaa_selector_pulse_rifle_right");
        RIGHTSWITCH.put(3, "armaa_selector_machinegun_right");
        RIGHTSWITCH.put(4, "armaa_selector_shotgun_right");

    }
    private final Map<Integer, String> LEFTSWITCH = new HashMap<>();

    {
        LEFTSWITCH.put(0, "armaa_selector_harpoon");
        LEFTSWITCH.put(1, "armaa_selector_shotgun_left");
        LEFTSWITCH.put(2, "armaa_selector_pulse_rifle_left");
        LEFTSWITCH.put(3, "armaa_selector_machinegun_left");
        LEFTSWITCH.put(4, "armaa_selector_rcl_left");
        LEFTSWITCH.put(5, "armaa_selector_blade_VAL");
    }

    public Map<Integer, String> CORE_SELECTOR = new HashMap<>();

    {
        CORE_SELECTOR.put(0, "armaa_valkazard_torso_lv2");
        CORE_SELECTOR.put(1, "armaa_valkazard_torso_shield_lv2");

        if(1==1)
        {
            CORE_SELECTOR.put(2, "armaa_valkazard_torso_boson_lv2");
            CORE_SELECTOR.put(3, "armaa_valkazard_torso_chaosburst_lv2");
            CORE_SELECTOR.put(4, "armaa_valkazard_torso_ac_lv2");
        }
    }

    private final Map<String, Integer> SWITCH_TO_CORE = new HashMap<>();

    {
        SWITCH_TO_CORE.put("armaa_valkazard_torso_lv2", 4);
        SWITCH_TO_CORE.put("armaa_valkazard_torso_shield_lv2", 0);

        if(1==1)
        {
            SWITCH_TO_CORE.put("armaa_valkazard_torso_boson_lv2",1);
            SWITCH_TO_CORE.put("armaa_valkazard_torso_chaosburst_lv2",2);
            SWITCH_TO_CORE.put("armaa_valkazard_torso_ac_lv2",3);
        }

    }

    private final Map<Integer, String> CORESWITCH = new HashMap<>();

    {
        CORESWITCH.put(0, "armaa_selector_blunderbuss");
        CORESWITCH.put(1, "armaa_selector_counter_shield");
        CORESWITCH.put(2, "armaa_selector_boson");
        CORESWITCH.put(3, "armaa_selector_chaos");
        CORESWITCH.put(4, "armaa_selector_ac20");
    }

    public Map<Integer, String> HEAD_SELECTOR = new HashMap<>();

    {
        HEAD_SELECTOR.put(0, "armaa_valkazard_head_lv2"); //4
        HEAD_SELECTOR.put(1, "armaa_valkazard_head_assault"); //0
        HEAD_SELECTOR.put(2, "armaa_valkazard_head_pd"); //1
        HEAD_SELECTOR.put(3, "armaa_valkazard_head_lance"); //2
        //HEAD_SELECTOR.put(4, "armaa_valkazard_torso_ac_lv2"); //3 ?
    }

    private final Map<String, Integer> SWITCH_TO_HEAD = new HashMap<>();

    {
        SWITCH_TO_HEAD.put("armaa_valkazard_head_assault", 0);
        SWITCH_TO_HEAD.put("armaa_valkazard_head_pd",1);
        SWITCH_TO_HEAD.put("armaa_valkazard_head_lance",2);
        SWITCH_TO_HEAD.put("armaa_valkazard_head_lv2",3);
    }

    private final Map<Integer, String> HEADSWITCH = new HashMap<>();

    {
        HEADSWITCH.put(0, "armaa_selector_command");
        HEADSWITCH.put(1, "armaa_selector_assault");
        HEADSWITCH.put(2, "armaa_selector_pd");
        HEADSWITCH.put(3, "armaa_selector_lance");
    }

    private final String leftslotID = "C_ARML";
    private final String rightslotID = "A_GUN";
    private final String coreslotID = "B_TORSO";
    private final String headslotID = "E_HEAD";
    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {

        //trigger a weapon switch if none of the selector hullmods are present
        boolean toSwitchLeft = true;
        boolean toSwitchRight = true;
        boolean toSwitchCore = true;
        boolean toSwitchHead = true;
        int numMods = 0;
        for (int i = 0; i < SWITCH_TO_LEFT.size(); i++) {
            if (stats.getVariant().getHullMods().contains(LEFTSWITCH.get(i))) {
                toSwitchLeft = false;
                numMods++;
            }
        }
        for (int i = 0; i < SWITCH_TO_RIGHT.size(); i++) {
            if (stats.getVariant().getHullMods().contains(RIGHTSWITCH.get(i))) {
                toSwitchRight = false;
                numMods++;
            }
        }
        for (int i = 0; i < CORESWITCH.size(); i++) {
            if (stats.getVariant().getHullMods().contains(CORESWITCH.get(i)) && stats.getVariant().getWeaponSpec(coreslotID) != null) {
                toSwitchCore = false;
                numMods++;
            }
        }
        for (int i = 0; i < HEADSWITCH.size(); i++) {
            if (stats.getVariant().getHullMods().contains(HEADSWITCH.get(i)) && stats.getVariant().getWeaponSpec(headslotID) != null) {
                toSwitchHead = false;
                numMods++;
            }
        }
        //If there isn't both a hullmod for the left AND right arm, we need to switch something
        //remove the weapons to change and swap the hullmod for the next fire mode
        if (toSwitchLeft) {
            //select new fire mode
            int selected;
            boolean random = false;
            if (stats.getVariant().getWeaponSpec(leftslotID) != null && SWITCH_TO_LEFT.containsKey(stats.getVariant().getWeaponSpec(leftslotID).getWeaponId())) {
                selected = SWITCH_TO_LEFT.get(stats.getVariant().getWeaponSpec(leftslotID).getWeaponId());
                //stake

            } else {
                selected = MathUtils.getRandomNumberInRange(0, SWITCH_TO_LEFT.size() - 1);
                random = true;
            }

            //add the proper hullmod
            stats.getVariant().addMod(LEFTSWITCH.get(selected));

            //clear the weapons to replace
            //if(ship.getHullmods.contains(SWITCH))
            //{
            stats.getVariant().clearSlot(leftslotID);
            Global.getLogger(this.getClass()).info(selected);
            String toInstallLeft = LEFT_SELECTOR.get(selected);
            stats.getVariant().addWeapon(leftslotID, toInstallLeft);
            //	}
            if (random) {
                stats.getVariant().autoGenerateWeaponGroups();
            }
        }

        if (toSwitchRight) {
            //select new fire mode
            int selected;
            boolean random = false;
            if (stats.getVariant().getWeaponSpec(rightslotID) != null) {
                selected = SWITCH_TO_RIGHT.get(stats.getVariant().getWeaponSpec(rightslotID).getWeaponId());

            } else {
                selected = MathUtils.getRandomNumberInRange(0, SWITCH_TO_RIGHT.size() - 1);
                random = true;
            }

            //add the proper hullmod
            stats.getVariant().addMod(RIGHTSWITCH.get(selected));

            //clear the weapons to replace
            //if(ship.getHullmods.contains(SWITCH)
            //{
            stats.getVariant().clearSlot(rightslotID);
            //select and place the proper weapon
            Global.getLogger(this.getClass()).info(selected);
            String toInstallRight = RIGHT_SELECTOR.get(selected);
            stats.getVariant().addWeapon(rightslotID, toInstallRight);
            //}

            if (random) {
                stats.getVariant().autoGenerateWeaponGroups();
            }
        }

        if (toSwitchCore) {
            //select new fire mode
            int selected;
            boolean random = false;
            if (stats.getVariant().getWeaponSpec(coreslotID) != null) {
                selected = SWITCH_TO_CORE.get(stats.getVariant().getWeaponSpec(coreslotID).getWeaponId());
            } else {
                selected = MathUtils.getRandomNumberInRange(0, CORESWITCH.size() - 1);
                String nullMod = "";
                for (String mod : stats.getVariant().getHullMods()) {
                    if (CORESWITCH.containsValue(mod)) {
                        nullMod = mod;
                    }
                }
                stats.getVariant().removeMod(nullMod);
                random = true;
            }

            //add the proper hullmod
            stats.getVariant().addMod(CORESWITCH.get(selected));

            //clear the weapons to replace
            //if(ship.getHullmods.contains(SWITCH)
            //{
            stats.getVariant().clearSlot(coreslotID);
            //select and place the proper weapon
            String toInstallCore = CORE_SELECTOR.get(selected);
            stats.getVariant().addWeapon(coreslotID, toInstallCore);
            //}

            if (random) {
                stats.getVariant().autoGenerateWeaponGroups();
            }
        }

        if (toSwitchHead) {
            //select new fire mode
            int selected;
            boolean random = false;
            if (stats.getVariant().getWeaponSpec(headslotID) != null) {
                selected = SWITCH_TO_HEAD.get(stats.getVariant().getWeaponSpec(headslotID).getWeaponId());
            } else {
                selected = MathUtils.getRandomNumberInRange(0, HEADSWITCH.size() - 1);
                String nullMod = "";
                for (String mod : stats.getVariant().getHullMods()) {
                    if (HEADSWITCH.containsValue(mod)) {
                        nullMod = mod;
                    }
                }
                stats.getVariant().removeMod(nullMod);
                random = true;
            }

            //add the proper hullmod
            stats.getVariant().addMod(HEADSWITCH.get(selected));

            //clear the weapons to replace
            //if(ship.getHullmods.contains(SWITCH)
            //{
            stats.getVariant().clearSlot(headslotID);
            //select and place the proper weapon
            String toInstallHead = HEAD_SELECTOR.get(selected);
            stats.getVariant().addWeapon(headslotID, toInstallHead);
            //}

            if (random) {
                stats.getVariant().autoGenerateWeaponGroups();
            }
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
                    if (s.isWeaponStack() && (LEFT_SELECTOR.containsValue(s.getWeaponSpecIfWeapon().getWeaponId())
                            || RIGHT_SELECTOR.containsValue(s.getWeaponSpecIfWeapon().getWeaponId())
                            || CORE_SELECTOR.containsValue(s.getWeaponSpecIfWeapon().getWeaponId()))) {
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
        // Allows any ship with a diableavionics hull id
        return (ship.getHullSpec().getHullId().startsWith("armaa_"));
    }
}