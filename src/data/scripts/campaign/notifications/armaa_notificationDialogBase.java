package data.scripts.campaign.notifications;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.InteractionDialogImageVisual;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.input.Keyboard;
import java.util.HashMap;
import java.util.Map;

public abstract class armaa_notificationDialogBase implements InteractionDialogPlugin {
    protected String text;
    protected String title;
    protected String spriteName;
    protected InteractionDialogAPI dialog;

    public static final float PANEL_WIDTH = 1200f;

    public armaa_notificationDialogBase(String text, String title, String spriteName) {
        this.text = text;
        this.title = title;
        this.spriteName = spriteName;
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        if (spriteName != null) {
            float imgWidth = Global.getSettings().getSprite(spriteName).getWidth();
            float imgHeight = Global.getSettings().getSprite(spriteName).getHeight();
            dialog.getVisualPanel().showImageVisual(new InteractionDialogImageVisual(spriteName, imgWidth, imgHeight));
        }
        dialog.getTextPanel().addParagraph(title,Misc.getHighlightColor());
        dialog.getTextPanel().addParagraph(text);
        if (dialog.getOptionPanel() != null) {
            addOptions(dialog.getOptionPanel());
        }
    }

    public abstract void addOptions(OptionPanelAPI options);

    @Override
    public void optionMousedOver(String optionText, Object optionData) {}

    @Override
    public void advance(float amount) {}

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {}

    @Override
    public Object getContext() {
        return null;
    }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() {
        return null;
    }
}