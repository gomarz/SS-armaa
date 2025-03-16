package data.scripts.campaign.notifications;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;

	//yoinked for svc
	abstract class armaa_NotificationBase {
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
		
	private Object notif = Global.getSector().getPersistentData().get("$" + "armaa_" + notificationId + "wasNotificationShown");
    private boolean hasNotificationBeenShown =  notif == null ? false : (boolean)notif;
    public boolean shouldBeShownOnce = false;
    public boolean shouldBeShownRepeatable = false;
	
	public boolean getNotifStatus()
	{
		if(notif == null)
		{
			notif = Global.getSector().getPersistentData().put("$" + "armaa_" + notificationId + "wasNotificationShown",false);
			notif = Global.getSector().getPersistentData().get("$" + "armaa_" + notificationId + "wasNotificationShown");
		}
		 hasNotificationBeenShown = (boolean)notif;
		 return hasNotificationBeenShown;
	}

    private void showNotification() {
        InteractionDialogPlugin notification = create();
        Global.getSector().getCampaignUI().showInteractionDialog(notification, interactionTarget);
        hasNotificationBeenShown = true;
        shouldBeShownOnce = false;
        shouldBeShownRepeatable = false;
    }

    public void showIfRequested() 
	{
		boolean hasNotificationBeenShown = getNotifStatus();
        if ((shouldBeShownOnce || showAutomaticallyIf()) && !hasNotificationBeenShown) {
            showNotification();
        }
        if (shouldBeShownRepeatable) {
            showNotification();
        }
    }

    public boolean showAutomaticallyIf() {
        return false;
    }
}