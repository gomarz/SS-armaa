package data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.util.Misc.Token;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import java.util.Map;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import data.scripts.campaign.armaa_valkazardListener;
import java.util.List;

public class armaa_valkazardCMD extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        String action = params.get(0).getString(memoryMap);
        CampaignFleetAPI pf = Global.getSector().getPlayerFleet();
        MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
        if (memory == null) {
            return false; // should not be possible unless there are other big problems already
        }
        if ("addTracker".equals(action)) {
            Global.getSector().addListener(new armaa_valkazardListener());
            return true;
        }
        return false;
    }
}
