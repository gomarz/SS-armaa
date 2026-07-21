package data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FleetMemberPickerListener;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class armaa_SPAtmoShipPicker extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
            List<Token> params, Map<String, MemoryAPI> memoryMap) {

        String action = params.get(0).getString(memoryMap);
        MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
        if ("hasCruiser+".equals(action)) {
            for (FleetMemberAPI member : Global.getSector().getPlayerFleet()
                    .getFleetData().getMembersListCopy()) {
                if (member.isCruiser()) {
                    return true;
                }
            }
            return false;
        }
        if ("pickLargeUnit".equals(action)) {
            // Build core unit pool
            List<FleetMemberAPI> unitPool = new ArrayList<>();
            for (FleetMemberAPI member : Global.getSector().getPlayerFleet()
                    .getFleetData().getMembersListCopy()) {
                if ((member.isCruiser())) {
                    unitPool.add(member);
                }
            }

            if (unitPool.isEmpty()) {
                dialog.getTextPanel().addParagraph("No eligible units found.");
                return true;
            }

            // First picker - select core unit
            dialog.showFleetMemberPickerDialog(
                    "Select Unit",
                    "Confirm", "Cancel",
                    4, 6, 58f,
                    false, false,
                    unitPool,
                    new FleetMemberPickerListener() {
                @Override
                public void pickedFleetMembers(List<FleetMemberAPI> members) {
                    if (members == null || members.isEmpty()) {
                        return;
                    }
                    members.get(0).getRepairTracker().setMothballed(false);
                    
                    dialog.getTextPanel().addParagraph(
                            members.get(0).getShipName() + " has been selected as the core unit.");
                    Global.getSector().getPlayerMemoryWithoutUpdate().set("$armaa_atmoBattleSparedHull", members.get(0).getId(),15);
                    dialog.getOptionPanel().clearOptions();
                    String rule = "armaa_WFRendevous6Begin";
                    if(params.get(1) != null)
                    {
                        rule = params.get(1).getString(memoryMap);
                    }
                    dialog.getOptionPanel().addOption("Proceed", rule);
                    dialog.getPlugin().optionSelected("Proceed", rule);
                }

                @Override
                public void cancelledFleetMemberPicking() {
                    dialog.getOptionPanel().clearOptions();
                    dialog.getOptionPanel().addOption("Cut Comm Link", "cutCommLink");
                    dialog.getPlugin().optionSelected("Cut Comm Link", "cutCommLink");
                    Global.getSector().getPlayerStats().addStoryPoints(1);
                }
            }
            );
            return true;
        }

        return false;
    }
}
