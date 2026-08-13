package data.campaign.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import data.scripts.ui.armaa_LargePortraitRenderer;

import java.util.List;
import java.util.Map;

/**
 * Rules.csv command to show the large portrait panel for the current
 * interaction's person.
 *
 * Usage in rules.csv:
 *   armaa_ShowLargePortrait
 *
 * With optional custom sprite overrides:
 *   armaa_ShowLargePortrait graphics/portraits/armaa_dawn.png graphics/factions/armaa_crest.png
 */
public class armaa_ShowLargePortrait extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;

        PersonAPI person = dialog.getInteractionTarget().getActivePerson();
        if (person == null) return false;

        if (params != null && params.size() >= 2) {
            String portrait = params.get(0).getString(memoryMap);
            String crest = params.get(1).getString(memoryMap);
            armaa_LargePortraitRenderer.show(dialog, person, portrait, person.getFaction().getCrest());
        } else {
            armaa_LargePortraitRenderer.show(dialog, person);
        }

        return true;
    }
}