package data.scripts.campaign.notifications;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import org.lwjgl.input.Keyboard;
import data.scripts.campaign.notifications.armaa_notificationDialogBase;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.magiclib.util.MagicSettings;
public class armaa_approachingPlanetNotification extends armaa_notificationDialogBase {

private String entity;
private Map<String, String> options = new HashMap<>();

    public armaa_approachingPlanetNotification(String entity) {
        super(
            Global.getSettings().getString("armaa_notifications", "armaa_planet_approaching_text_"+entity),
            Global.getSettings().getString("armaa_notifications", "armaa_planet_approaching_title_"+entity),
            Global.getSettings().getSpriteName("backgrounds", "defaultSpaceBackground")
        );
		this.entity = entity;
		//String spriteName;		
    }

	
    @Override
    public void addOptions(OptionPanelAPI options) {
		this.dialog.getVisualPanel().showPersonInfo(Global.getSector().getImportantPeople().getPerson("armaa_dawn"));		
		options.clearOptions();
		List<String> OPTIONS = MagicSettings.getList("armaarmatura_notifications",entity);
		int i = 0;
		for(String option : OPTIONS)
		{
			options.addOption(option,i);
			i++;
		}
        options.addOption("Continue", "Cut comm-link");
        options.setShortcut("Cut comm-link", Keyboard.KEY_ESCAPE, false, false, false, false);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        //if ("Cut comm-link".equals(optionData)) {
            this.dialog.dismiss();
        //}
    }
}