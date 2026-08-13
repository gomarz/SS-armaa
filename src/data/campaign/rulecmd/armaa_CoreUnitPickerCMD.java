package data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import starship_legends.RepRecord;

public class armaa_CoreUnitPickerCMD extends BaseCommandPlugin {

    /**
     * Persistent-data key prefix for parked core unit ids. One entry per
     * (mobile armor, module slot) pair.
     */
    private static final String CORE_ID_KEY_PREFIX = "$armaa_installedCoreId_";

    private static String coreIdKey(FleetMemberAPI armor, String slotId) {
        return CORE_ID_KEY_PREFIX + armor.getId() + ":" + slotId;
    }

    private static boolean isIdInUse(FleetDataAPI data, String id) {
        for (FleetMemberAPI m : data.getMembersListCopy()) {
            if (id.equals(m.getId())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
            List<Token> params, Map<String, MemoryAPI> memoryMap) {

        String action = params.get(0).getString(memoryMap);
        MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
        if ("hasSwappableUnit".equals(action)) {
            for (FleetMemberAPI member : Global.getSector().getPlayerFleet()
                    .getFleetData().getMembersListCopy()) {
                if (member.getVariant().hasHullMod("armaa_comboUnit")) {
                    return true;
                }
            }
            return false;
        }
        if ("pickCoreUnit".equals(action)) {
            // Build core unit pool
            List<FleetMemberAPI> coreUnitPool = new ArrayList<>();
            for (FleetMemberAPI member : Global.getSector().getPlayerFleet()
                    .getFleetData().getMembersListCopy()) {
                if (member.getVariant().hasHullMod("strikeCraft")) {
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
                    if (members.get(0) == null) {
                        dialog.getTextPanel().addParagraph("No Core unit found.");
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
                    if (coreUnit == null) {
                        dialog.getTextPanel().addParagraph("No core unit selected.");
                        return;
                    }
                    performSwap(coreUnit, mobileArmor, dialog);
                    memory.unset("$coreUnit");
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

    /**
     * Swaps a new core unit into a mobile armor, ejecting the previous core back
     * into the fleet.
     *
     * The mobile armor keeps its own fleet member id for its entire life - it is
     * never given a core's id. Instead the installed core's id is parked in
     * persistent data for as long as it is inside the armor, and handed back to the
     * ship carrying that core's variant when it is ejected.
     *
     * A core's id is therefore absent from the fleet while it is installed, which
     * every mod already has to cope with (players sell and lose ships constantly),
     * rather than present on a hull of the wrong class and size, which many mods do
     * not - see the upgrade-path CTD that motivated this.
     */
    private void performSwap(FleetMemberAPI coreUnit, FleetMemberAPI mobileArmor,
            InteractionDialogAPI dialog) {

        String moduleSlotId = "MODULE"; // replace with your actual module slot id

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        FleetDataAPI fleetData = playerFleet.getFleetData();
        Map<String, Object> persist = Global.getSector().getPersistentData();

        final String key = coreIdKey(mobileArmor, moduleSlotId);
        // id of the core currently installed, if we parked one on a previous swap
        final String parkedCoreId = (String) persist.get(key);
        final String incomingCoreId = coreUnit.getId();

        PersonAPI corePilot = coreUnit.getCaptain();
        PersonAPI maPilot = mobileArmor.getCaptain();

        // Save the current module variant from the armor before we overwrite it
        ShipVariantAPI oldModuleVariant = mobileArmor.getVariant()
                .getModuleVariant(moduleSlotId);

        // Set the armor's module to the chosen core unit's variant
        ShipVariantAPI newModuleVariant = coreUnit.getVariant().clone();
        newModuleVariant.setSource(VariantSource.REFIT);
        mobileArmor.getVariant().setModuleVariant(moduleSlotId, newModuleVariant);
        mobileArmor.setCaptain(corePilot);

        boolean ssl = Global.getSettings().getModManager()
                .isModEnabled("sun_starship_legends");

        // Add old module back to fleet as a standalone ship (if it existed)
        FleetMemberAPI returnedShip = null;
        if (oldModuleVariant != null) {
            oldModuleVariant.removePermaMod("armaa_dpReduction");
            returnedShip = Global.getFactory()
                    .createFleetMember(FleetMemberType.SHIP, oldModuleVariant);

            if (parkedCoreId != null) {
                if (isIdInUse(fleetData, parkedCoreId)) {
                    // Should not happen: the id was vacated when this core was
                    // installed. If it fires, something else minted it in the
                    // meantime, so leave the factory id alone rather than create
                    // a duplicate.
                    Global.getLogger(this.getClass()).warn(
                            "armaa: parked core id " + parkedCoreId
                            + " is already in use; ejected core keeps its factory id");
                } else {
                    // set BEFORE it enters fleet data, so nothing indexes it under
                    // the factory-generated id
                    returnedShip.setId(parkedCoreId);
                }
            }
            // No parked id means this is the factory-default core, which never
            // existed as a fleet member. It keeps its factory id - correct, since
            // there is no prior identity to restore.

            returnedShip.setCaptain(maPilot);
            fleetData.addFleetMember(returnedShip);
        } else {
            Global.getLogger(this.getClass()).warn(
                    "armaa: no module variant in slot " + moduleSlotId + " on "
                    + mobileArmor.getShipName() + "; parked core id " + parkedCoreId
                    + " has nothing to return to");
        }

        // Starship Legends is keyed on fleet member id, and ids no longer rotate,
        // so the explicit transfers are needed again. Order matters: these must run
        // AFTER returnedShip.setId(), or the record lands on the factory id and is
        // orphaned the moment we overwrite it.
        if (ssl) {
            if (returnedShip != null) {
                // armor's accumulated rep goes out with the core being ejected
                RepRecord.transfer(mobileArmor, returnedShip);
            }
            // incoming core's rep comes in with it
            RepRecord.transfer(coreUnit, mobileArmor);
            newModuleVariant.removePermaMod("sun_sl_notable_1");
        }

        // Park the incoming core's id so the next eject can hand it back
        persist.put(key, incomingCoreId);

        fleetData.removeFleetMember(coreUnit);
        mobileArmor.updateStats();
        fleetData.syncIfNeeded();

        dialog.getTextPanel().addParagraph(
                coreUnit.getShipName() + " has been integrated as the core unit of "
                + mobileArmor.getShipName() + ".");
        Global.getSoundPlayer().playUISound("ui_refit_slot_filled_ballistic_large", 1f, 1f);
    }
}