package data.scripts.campaign.notifications;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl;

/**
 * Rule dialog for notifications. Every notification rule in rules.csv clears its
 * $player.armaa_..._event_id flag when it fires, so if the flag is still set after
 * PopulateOptions, no rule matched and vanilla has put up its blank failsafe
 * ("Leave" and nothing else). Close that instead of showing it, and consume the
 * notification so it isn't retried into the same empty dialog.
 */
public class armaa_notificationRuleDialog extends RuleBasedInteractionDialogPluginImpl {

    private final String triggerKey;
    private InteractionDialogAPI dialog;
    private boolean noRuleMatched = false;

    public armaa_notificationRuleDialog(String triggerKey) {
        super("PopulateOptions");
        this.triggerKey = triggerKey;
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        super.init(dialog);

        MemoryAPI mem = Global.getSector().getPlayerMemoryWithoutUpdate();
        if (mem.getBoolean(triggerKey)) {
            noRuleMatched = true;
            // clearing the flag lets armaa_NotificationBase treat this as done
            mem.set(triggerKey, false);
            Global.getLogger(armaa_notificationRuleDialog.class).warn(
                    "No rules.csv rule matched notification " + triggerKey + ", closing the empty dialog");
        }
    }

    @Override
    public void advance(float amount) {
        // dismissed here rather than in init, once the dialog is fully up
        if (noRuleMatched) {
            noRuleMatched = false;
            dialog.dismiss();
            return;
        }
        super.advance(amount);
    }
}
