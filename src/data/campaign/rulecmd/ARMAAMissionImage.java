package data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import java.util.List;
import java.util.Map;
/**
 *
 * @author Mayu
 */
public class ARMAAMissionImage extends BaseCommandPlugin {
    
    @Override
    public boolean execute(final String ruleId, final InteractionDialogAPI dialog, final List<Misc.Token> params, final Map<String, MemoryAPI> memoryMap) {
	if (dialog == null) {
            return false;
	}
        final String img = params.get(0).getString((Map)memoryMap);
        final String imgtwo = params.get(1).getString((Map)memoryMap);
        final TextPanelAPI text = dialog.getTextPanel();
        text.addImage(Global.getSettings().getSpriteName(img, imgtwo));
        return true;
    }
    
}