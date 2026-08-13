package data.scripts.ui;

import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.VisualPanelAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;

/**
 * Renders a large character portrait alongside faction info in the visual panel
 * of a Starsector interaction dialog, similar to a visual novel / JRPG style.
 *
 * Usage from any InteractionDialogPlugin, bar event, or rules.csv command:
 *   armaa_LargePortraitRenderer.show(dialog, person);
 *
 * The layout matches the mockup:
 *   - Large portrait image (right side, ~280px) with vanilla-style box border
 *   - Faction crest + Name / Rank / Title below the portrait
 *   - The left-side TextPanelAPI remains vanilla and handles dialogue as normal
 */
public class armaa_LargePortraitRenderer {

    // ---- Layout constants ---- tweak these to match desired proportions

    public static float PANEL_WIDTH  = 400f;
    public static float PANEL_HEIGHT = 500f;

    public static float PORTRAIT_SIZE  = 280f;
    public static float PORTRAIT_PAD_X = 50f;   // horizontal inset from panel edge
    public static float PORTRAIT_PAD_Y = 20f;   // top inset

    public static float BORDER_PADDING = 3f;    // gap between portrait image and box border

    public static float CREST_SIZE = 48f;
    public static float INFO_WIDTH = 300f;
    public static float CREST_TEXT_GAP = 15f;    // horizontal gap between faction crest and name/rank text

    public static Color BORDER_COLOR = new Color(0, 180, 255, 200);   // cyan box border
    public Color NAME_COLOR   = new Color(255, 200, 50, 255);  // gold for name
    public static Color LABEL_COLOR  = new Color(150, 150, 150, 255); // gray for "Name:", "Rank:"
    public Color VALUE_COLOR  = Misc.getTextColor();

    // ---- Public API ----

    /**
     * Show the large portrait panel for the given person.
     */
    public static void show(InteractionDialogAPI dialog, PersonAPI person) {
        show(dialog, person, BORDER_COLOR);
    }

    /**
     * Show with a custom border color (e.g. red for IRON KING, faction color, etc).
     */
    public static void show(InteractionDialogAPI dialog, PersonAPI person, Color borderColor) {
        if (dialog == null || person == null) return;

        VisualPanelAPI visualPanel = dialog.getVisualPanel();

        CustomPanelAPI panel = visualPanel.showCustomPanel(
                PANEL_WIDTH,
                PANEL_HEIGHT,
                new NoOpPlugin()
        );

        String portraitSprite = person.getPortraitSprite();
        String factionCrest  = person.getFaction().getCrest();
        buildPortraitLayout(panel, person, portraitSprite, factionCrest, borderColor);
    }

    /**
     * Show with explicit sprite overrides for portrait and/or faction crest.
     */
    public static void show(InteractionDialogAPI dialog, PersonAPI person,
                            String portraitSprite, String factionCrestSprite) {
        show(dialog, person, portraitSprite, factionCrestSprite, BORDER_COLOR);
    }

    /**
     * Full control: custom sprites + custom border color.
     */
    public static void show(InteractionDialogAPI dialog, PersonAPI person,
                            String portraitSprite, String factionCrestSprite, Color borderColor) {
        if (dialog == null || person == null) return;

        VisualPanelAPI visualPanel = dialog.getVisualPanel();

        CustomPanelAPI panel = visualPanel.showCustomPanel(
                PANEL_WIDTH,
                PANEL_HEIGHT,
                new NoOpPlugin()
        );

        buildPortraitLayout(panel, person, portraitSprite, factionCrestSprite, borderColor);
    }

    // ---- Layout construction ----

    private static void buildPortraitLayout(CustomPanelAPI panel, PersonAPI person,
                                            String portraitSprite, String factionCrest,
                                            Color borderColor) {
        float pad = 5f;

        // ============================================================
        // 1) LARGE PORTRAIT with vanilla box border
        // ============================================================
        float bp = BORDER_PADDING;

        // Border frame — vanilla pattern: create with height 0, add content,
        // updateUIElementSizeAndMakeItProcessInput, THEN wrap with box
        TooltipMakerAPI borderSpacer = panel.createUIElement(PORTRAIT_SIZE+10f, 0, false);
        borderSpacer.addImage(portraitSprite, PORTRAIT_SIZE, PORTRAIT_SIZE, 0f);
        panel.updateUIElementSizeAndMakeItProcessInput(borderSpacer);
        UIPanelAPI borderBox = panel.wrapTooltipWithBox(borderSpacer, bp, bp, bp, bp, borderColor);
        panel.addComponent(borderBox).inTR(PORTRAIT_PAD_X, PORTRAIT_PAD_Y);

        // No separate portrait element needed — the image is inside the box now

        // ============================================================
        // 2) FACTION CREST + CHARACTER INFO — below the portrait,
        //    wrapped in a border box
        // ============================================================
        float borderedPortraitSize = PORTRAIT_SIZE + (bp * 2f);
        float infoY = PORTRAIT_PAD_Y + borderedPortraitSize + 10f;

        // Use same width as portrait tooltip so info box centers beneath it
        float infoWidth = PORTRAIT_SIZE + 10f;
        TooltipMakerAPI infoHolder = panel.createUIElement(infoWidth, 0, false);

        // beginImageWithText puts the crest on the left with text to its right.
        TooltipMakerAPI imageText = infoHolder.beginImageWithText(factionCrest, CREST_SIZE);

        // Faction name
        imageText.addRelationshipBar(person, 2f);
        String factionName = person.getFaction().getDisplayName();
        imageText.addPara(factionName, pad, person.getFaction().getBaseUIColor(), factionName);

        // Name line
        String name = person.getName().getFullName();
        imageText.addPara("Name:   %s", pad, LABEL_COLOR, person.getFaction().getBaseUIColor(), name);

        // Rank line
        String rank = person.getRankId();
        if (rank != null && !rank.isEmpty()) {
            String rankDisplay = Misc.ucFirst(person.getRank().toLowerCase());
            imageText.addPara("Rank:    %s", 2f, LABEL_COLOR, person.getFaction().getBaseUIColor(), rankDisplay);
        }

        // Post/title line (e.g. "Fleet Commander", "Officer")
        String post = person.getPostId();
        if (post != null && !post.isEmpty()) {
            String postDisplay = Misc.ucFirst(person.getPost().toLowerCase());
            imageText.addPara(postDisplay, LABEL_COLOR, 2f);
        }

        infoHolder.addImageWithText(CREST_TEXT_GAP);

        // Wrap info in a border box, same pattern as portrait
        panel.updateUIElementSizeAndMakeItProcessInput(infoHolder);
        UIPanelAPI infoBox = panel.wrapTooltipWithBox(infoHolder, bp, bp, bp, bp, borderColor);
        panel.addComponent(infoBox).inTR(PORTRAIT_PAD_X, infoY);
    }

    // ---- No-op plugin (static display, no input handling needed) ----

    private static class NoOpPlugin implements CustomUIPanelPlugin {
        @Override public void positionChanged(PositionAPI pos) {}
        @Override public void renderBelow(float alpha) {}
        @Override public void render(float alpha) {}
        @Override public void advance(float amount) {}
        @Override public void processInput(java.util.List<com.fs.starfarer.api.input.InputEventAPI> events) {}
        @Override public void buttonPressed(Object buttonId) {}
    }
}