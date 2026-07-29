package data.scripts.campaign.notifications;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;

//yoinked for svc
abstract class armaa_NotificationBase {

    // Game-seconds to wait for rules.csv to flip $player.<id> back to false
    // (proof the dialog actually displayed) before assuming it was eaten and retrying
    private static final float CONFIRM_TIMEOUT = 5f;
    // Hard cap on display attempts so a rule that forgets to clear its flag
    // can't cause an infinite retry loop
    private static final int MAX_SHOW_ATTEMPTS = 3;

    private String notificationId = "";
    private CampaignFleetAPI interactionTarget;

    public armaa_NotificationBase(String notificationId) {
        this(notificationId, Global.getSector().getPlayerFleet());
    }

    public armaa_NotificationBase(String notificationId, CampaignFleetAPI interactionTarget) {
        this.notificationId = notificationId;
        this.interactionTarget = interactionTarget;
    }

    public abstract InteractionDialogPlugin create();

    private Object notif = Global.getSector().getPersistentData().get("$" + notificationId + "wasNotificationShown");
    public boolean shouldBeShownOnce = false;
    public boolean shouldBeShownRepeatable = false;

    // Gating: if true, the shower holds this at show time until Dawn is in the fleet.
    // Detection-time checks aren't enough once retries / recovery can delay a show
    // arbitrarily long past the frame the condition was last verified.
    public boolean requiresDawn = false;

    // True between firing showRuleDialog() and rules.csv proving the dialog displayed
    private boolean awaitingConfirmation = false;
    private float confirmTimer = 0f;
    private int showAttempts = 0;

    // $player.armaa_..._event_id in rules.csv: the rule's trigger condition (== true),
    // which the rule's script sets back to false once it actually fires
    private String getTriggerKey() {
        return "$" + notificationId;
    }

    public boolean getNotifStatus() {
        if (Global.getSector().getPersistentData().get("$" + notificationId + "wasNotificationShown") != null) {
            notif = Global.getSector().getPersistentData().get("$" + notificationId + "wasNotificationShown");
            return (boolean) notif;
        } else {
            Global.getSector().getPersistentData().put("$" + notificationId + "wasNotificationShown", false);
            return false;
        }
    }

    private void showNotification() {
        MemoryAPI mem = Global.getSector().getPlayerMemoryWithoutUpdate();
        mem.set(getTriggerKey(), true);//$armaa_mazalot_event_id

        showAttempts++;
        Misc.showRuleDialog(interactionTarget, "PopulateOptions");

        shouldBeShownOnce = false;
        shouldBeShownRepeatable = false;

        // wasNotificationShown is no longer committed here -- that happens in
        // advanceConfirmation() once rules.csv has flipped the trigger flag back
        // to false, proving the dialog actually reached the screen
        awaitingConfirmation = true;
        confirmTimer = 0f;
    }

    public void showIfRequested() {
        if (awaitingConfirmation) {
            return; // attempt already in flight, wait for confirm/timeout
        }
        if ((shouldBeShownOnce || showAutomaticallyIf()) && !getNotifStatus()) {
            showNotification();
        }
        if (shouldBeShownRepeatable) {
            showNotification();
        }
    }

    /**
     * Called every frame by armaa_notificationShower. Only ticks while no dialog
     * is up (the shower early-outs on isShowingDialog), so time spent reading the
     * notification doesn't count against the timeout.
     */
    public void advanceConfirmation(float amount) {
        if (!awaitingConfirmation) {
            return;
        }

        MemoryAPI mem = Global.getSector().getPlayerMemoryWithoutUpdate();

        if (!mem.getBoolean(getTriggerKey())) {
            // rules.csv set it false -> the player actually saw it. NOW it counts as shown.
            awaitingConfirmation = false;
            showAttempts = 0;
            Global.getSector().getPersistentData().put("$" + notificationId + "wasNotificationShown", true);
            return;
        }

        confirmTimer += amount;
        if (confirmTimer < CONFIRM_TIMEOUT) {
            return;
        }

        // Timed out with the flag still true: the dialog got eaten somewhere
        awaitingConfirmation = false;
        confirmTimer = 0f;

        if (showAttempts < MAX_SHOW_ATTEMPTS) {
            // Shower re-queues this on its next interval pass
            shouldBeShownOnce = true;
            Global.getLogger(armaa_NotificationBase.class).warn(
                    "Notification " + notificationId + " never confirmed shown, re-queueing (attempt "
                    + showAttempts + "/" + MAX_SHOW_ATTEMPTS + ")");
        } else {
            // Rule is broken or UI is perma-blocked; consume so we don't spin forever.
            // Clear the flag ourselves so the recovery sweep doesn't resurrect it next session.
            mem.set(getTriggerKey(), false);
            Global.getSector().getPersistentData().put("$" + notificationId + "wasNotificationShown", true);
            Global.getLogger(armaa_NotificationBase.class).error(
                    "Notification " + notificationId + " failed to display after " + MAX_SHOW_ATTEMPTS
                    + " attempts, marking as shown. Check that its rules.csv script sets $player."
                    + notificationId + " = false");
        }
    }

    public boolean showAutomaticallyIf() {
        return false;
    }
}
