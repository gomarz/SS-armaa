package data.scripts.campaign.notifications;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemType;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import data.scripts.campaign.notifications.armaa_NotificationBase;
import data.scripts.campaign.notifications.armaa_approachingPlanetNotification;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import org.lazywizard.lazylib.campaign.CampaignUtils;

public class armaa_notificationShower implements EveryFrameScript {
	public IntervalUtil timer = new IntervalUtil(1f,1f);
    private final Map<String, armaa_NotificationBase> notifications = new HashMap<>();
	private static final List<String> validEntities = new ArrayList<String>();
	private static final List<String> validKeys = new ArrayList<String>();	
	static
	{
		//TODO: externalize this
		//validEntities.add("jangala");
		validEntities.add("station_galatia_academy");
		validEntities.add("mazalot");
		//validEntities.add("umbra");
		validEntities.add("volturn");
		
		validKeys.add("$anh_inProgress");
		//validKeys.add("$foundOneslaught");
		validKeys.add("$encounteredDweller");
		validKeys.add("$pk_recovered");		
		//validKeys.add("$defeatedLuddicChurchExpedition");		
	}

	//This script adds all the possible notifications
	//When a notif can be shown/should be shown, shownotifactions is caleld
	//this tells the notifcation that it should  be shown
	//ostensibly, this then allows it to proc.
	//I need to be able to create a notification that populates text/dialogue based on the entity
	//I don't think this is possible unless I follow a strict pattern, specifically with same # of options
	//could create a map/set of options? Iterate through and add key/value pair.
	
    public void showNotificationOnce(String id) {
        armaa_NotificationBase notification = notifications.get(id);
        if (notification != null) {
            notification.shouldBeShownOnce = true;
        }
    }

    public void showNotificationRepeatable(String id) {
        armaa_NotificationBase notification = notifications.get(id);
        if (notification != null) {
            notification.shouldBeShownRepeatable = true;
        }
    }

    @Override
    public boolean isDone() {
        return false;
    }
	
    public void addNotification(final String entity) 
	{
		
        notifications.put("armaa_"+entity+"_event_id", new armaa_NotificationBase("armaa_"+entity+"_event_id") {
            @Override
            public InteractionDialogPlugin create() {
                return new armaa_approachingPlanetNotification(entity);
            }
        });
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    public boolean dawnIsPresent() 
	{
		List<OfficerDataAPI> data = Global.getSector().getPlayerFleet().getFleetData().getOfficersCopy();
		for(OfficerDataAPI officer : data)
		{
			if(officer.getPerson().getId().equals("armaa_dawn"))
				return true;
		}
	
        return false;
    }
    @Override
    public void advance(float amount) 
	{
        if (Global.getSector().isInNewGameAdvance() || Global.getSector().getCampaignUI().isShowingDialog() ||
           Global.getSector().getCampaignUI().isShowingMenu()  || Global.getCurrentState() == GameState.TITLE) {
            return;
        }
		timer.advance(amount);
		if(timer.intervalElapsed())		
		{
			CampaignFleetAPI flt = Global.getSector().getPlayerFleet();
			
			if(flt == null)
				return;
			List<SectorEntityToken> entities = CampaignUtils.getNearbyEntitiesWithTag((SectorEntityToken)flt,1000f,Tags.STATION);
			entities.addAll(CampaignUtils.getNearbyEntitiesWithTag(flt,1000f,Tags.PLANET));
			entities.addAll(CampaignUtils.getNearbyEntitiesWithTag(flt,1000f,Tags.CRYOSLEEPER));


			//sleeper start notification
			if(notifications.get("armaa_valkHunter_event_id") == null)
				if(Global.getSector().getMemoryWithoutUpdate().contains("$armaa_engagedValkHunters"))
				{
					addNotification("valkHunter");
					showNotificationOnce("armaa_valkHunter_event_id");
					notifications.get("armaa_valkHunter_event_id").showIfRequested();				
				}
					

			
			//Dawn Notifications -- maybe should be its own class?
			if(dawnIsPresent())
			{
				//check if we're near anything we can talk about
				for(SectorEntityToken entity : entities)
				{
					if(entity.hasTag(Tags.CRYOSLEEPER) && notifications.get("armaa_"+Tags.CRYOSLEEPER+"_event_id") == null)
					{
						addNotification(Tags.CRYOSLEEPER);
						showNotificationOnce("armaa_"+Tags.CRYOSLEEPER+"_event_id");			
					}
					if(validEntities.contains(entity.getId()) && notifications.get("armaa_"+entity.getId()+"_event_id") == null)
					{
						addNotification(entity.getId());
						showNotificationOnce("armaa_"+entity.getId()+"_event_id");
						//Global.getSector().getCampaignUI().addMessage("Near " + entity.getId(),Misc.getHighlightColor());					
					}	
				}
				
				//check for shrouded hullmods
				if(notifications.get("armaa_shroudmod_event_id") == null)
					for (FleetMemberAPI member : flt.getFleetData().getMembersListCopy()) {
						for (String modId : member.getVariant().getHullMods()) {
							HullModSpecAPI spec = Global.getSettings().getHullModSpec(modId);
							if (spec.hasTag(Tags.SHROUDED))
							{
								addNotification("shroudmod");
								showNotificationOnce("armaa_shroudmod_event_id");
							}
						}
					}
				// we have to check if player has PK for the pk event, else it can be flagged to trigger
				// and nothing will actually happen since the rule has condition of having it also
				if(notifications.get("armaa_pk_event_id") == null)
					if(Global.getSector().getPlayerFleet().getCargo().getQuantity(CargoItemType.SPECIAL, new SpecialItemData("planetkiller", null)) > 0)
					{						
						addNotification("pk");
						showNotificationOnce("armaa_pk_event_id");	
					}						
				//check if there are any triggers we can talk about
				for(String key : Global.getSector().getMemoryWithoutUpdate().getKeys())
				{						
					if(validKeys.contains(key) && notifications.get("armaa_"+key+"_event_id") == null)
					{
						//Misc.showRuleDialog(flt, "PopulateOptions");
						addNotification(key);
						showNotificationOnce("armaa_"+key+"_event_id");								
					}
				}
				for(String key : Global.getSector().getPlayerMemoryWithoutUpdate().getKeys())
				{						
					if(validKeys.contains(key) && notifications.get("armaa_"+key+"_event_id") == null)
					{
						//Misc.showRuleDialog(flt, "PopulateOptions");
						addNotification(key);
						showNotificationOnce("armaa_"+key+"_event_id");								
					}
				}
			}
			
			// Other Notifications - caused by flags being set or something I guess
		}		
    }
}