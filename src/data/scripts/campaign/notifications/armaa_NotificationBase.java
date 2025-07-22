package data.scripts.campaign.notifications;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.util.Misc;

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
		
	private Object notif = Global.getSector().getPersistentData().get("$" +notificationId + "wasNotificationShown");
    public boolean shouldBeShownOnce = false;
    public boolean shouldBeShownRepeatable = false;
	
	public boolean getNotifStatus()
	{
		if(Global.getSector().getPersistentData().get("$" + notificationId + "wasNotificationShown") != null)
		{
			notif = Global.getSector().getPersistentData().get("$" + notificationId + "wasNotificationShown");
			return (boolean)notif;
		}
		else
		{
			Global.getSector().getPersistentData().put("$" + notificationId + "wasNotificationShown",false);
			return false;
		}
	}

    private void showNotification() {
		Global.getSector().getPlayerMemoryWithoutUpdate().set("$"+notificationId,true);//$armaa_mazalot_event_id
		//Global.getSector().getCampaignUI().addMessage((String)Global.getSector().getPlayerMemoryWithoutUpdate().get(notificationId));
		//Global.getSector().getCampaignUI().addMessage(interactionTarget.getId());		
        InteractionDialogPlugin notification = create();
		Misc.showRuleDialog(interactionTarget, "PopulateOptions");
        //Global.getSector().getCampaignUI().showInteractionDialog(notification, interactionTarget);
        shouldBeShownOnce = false;
        shouldBeShownRepeatable = false;
		//Global.getSector().getPlayerMemoryWithoutUpdate().unset("$"+notificationId);//$armaa_mazalot_event_id
		Global.getSector().getPersistentData().put("$" + notificationId + "wasNotificationShown",true);
    }

    public void showIfRequested() 
	{
        if ((shouldBeShownOnce || showAutomaticallyIf()) && !getNotifStatus()) {
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