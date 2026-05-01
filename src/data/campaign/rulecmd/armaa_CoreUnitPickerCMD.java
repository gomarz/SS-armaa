package data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FleetMemberPickerListener;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.util.Misc.Token;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class armaa_CoreUnitPickerCMD extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
            List<Token> params, Map<String, MemoryAPI> memoryMap) {

        String action = params.get(0).getString(memoryMap);
        MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
        if ("pickCoreUnit".equals(action)) {
            // Build core unit pool - strikecraft, not flagship
            List<FleetMemberAPI> coreUnitPool = new ArrayList<>();
            for (FleetMemberAPI member : Global.getSector().getPlayerFleet()
                    .getFleetData().getMembersListCopy()) {
                if (member.getVariant().hasHullMod("strikeCraft") && !member.isFlagship()) {
                    coreUnitPool.add(member);
                }
            }

            if (coreUnitPool.isEmpty()) {
                dialog.getTextPanel().addParagraph("No eligible strikecraft found.");
                return true;
            }

            // First picker - select core unit
            dialog.showFleetMemberPickerDialog(
                    "Select Core Unit",
                    "Confirm", "Cancel",
                    4, 6, 58f,
                    false, false,
                    coreUnitPool,
                    new FleetMemberPickerListener() {
                @Override
                public void pickedFleetMembers(List<FleetMemberAPI> members) {
                    if (members == null || members.isEmpty()) {
                        return;
                    }
                    memory.set("$coreUnit", members.get(0), 1);
                    dialog.getTextPanel().addParagraph(
                            members.get(0).getShipName() + " has been selected as the core unit.");
                }

                @Override
                public void cancelledFleetMemberPicking() {
                    dialog.getOptionPanel().clearOptions();
                    dialog.getOptionPanel().addOption("Cut Comm Link", "cutCommLink");
                    dialog.getPlugin().optionSelected("Cut Comm Link", "cutCommLink");
                }
            }
            );
            return true;
        }
        if ("pickMobileArmor".equals(action)) {

            // Build mobile armor pool
            List<FleetMemberAPI> mobileArmorPool = new ArrayList<>();
            for (FleetMemberAPI member : Global.getSector().getPlayerFleet()
                    .getFleetData().getMembersListCopy()) {
                if (member.getVariant().hasHullMod("armaa_comboUnit")) {
                    mobileArmorPool.add(member);
                }
            }

            if (mobileArmorPool.isEmpty()) {
                dialog.getTextPanel().addParagraph("No Mobile Armor found.");
                return false;
            }

            dialog.showFleetMemberPickerDialog(
                    "Select Mobile Armor",
                    "Confirm", "Cancel",
                    4, 6, 58f,
                    false, false,
                    mobileArmorPool,
                    new FleetMemberPickerListener() {
                @Override
                public void pickedFleetMembers(List<FleetMemberAPI> members) {
                    if (members == null || members.isEmpty()) {
                        return;
                    }
                    FleetMemberAPI mobileArmor = members.get(0);
                    FleetMemberAPI coreUnit = (FleetMemberAPI) memory.get("$coreUnit");
                    performSwap(coreUnit, mobileArmor, dialog);
                }

                @Override
                public void cancelledFleetMemberPicking() {
                    dialog.getOptionPanel().clearOptions();
                    dialog.getOptionPanel().addOption("Cut Comm Link", "cutCommLink");
                    dialog.getPlugin().optionSelected("Cut Comm Link", "cutCommLink");

                }
            }
            );
        }
        return false;
    }

    private void performSwap(FleetMemberAPI coreUnit, FleetMemberAPI mobileArmor,
            InteractionDialogAPI dialog) {
        // Get the module slot id - you'll need to know what slot the core goes in
        String moduleSlotId = "MODULE"; // replace with your actual module slot id

        PersonAPI corePilot = coreUnit.getCaptain();
        PersonAPI maPilot = mobileArmor.getCaptain();
        // Save the current module variant from the bakraid
        ShipVariantAPI oldModuleVariant = mobileArmor.getVariant()
                .getModuleVariant(moduleSlotId);

        // Set the bakraid's module to the chosen core unit's variant
        ShipVariantAPI newModuleVariant = coreUnit.getVariant().clone();
        newModuleVariant.setSource(VariantSource.REFIT);
        mobileArmor.getVariant().setModuleVariant(moduleSlotId, newModuleVariant);
        mobileArmor.setCaptain(corePilot);
        // Add old module back to fleet as a standalone ship (if it existed)
        if (oldModuleVariant != null) {
            oldModuleVariant.removePermaMod("armaa_dpReduction");
            FleetMemberAPI returnedShip = Global.getFactory()
                    .createFleetMember(FleetMemberType.SHIP, oldModuleVariant);
            Global.getSector().getPlayerFleet().getFleetData()
                    .addFleetMember(returnedShip);
            returnedShip.setCaptain(maPilot);
        }

        // Remove the chosen core unit from fleet
        Global.getSector().getPlayerFleet().getFleetData()
                .removeFleetMember(coreUnit);

        dialog.getTextPanel().addParagraph(
                coreUnit.getShipName() + " has been integrated as the core unit of "
                + mobileArmor.getShipName() + ".");
        Global.getSoundPlayer().playUISound("ui_refit_slot_filled_ballistic_large", 1f, 1f);
        mobileArmor.updateStats();
    }
}
