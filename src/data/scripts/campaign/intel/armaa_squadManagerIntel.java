package data.scripts.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.characters.FullName.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.impl.campaign.ids.Voices;
import data.scripts.MechaModPlugin;

import data.scripts.campaign.intel.armaa_promoteWingman;

import org.apache.log4j.Logger;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import lunalib.lunaSettings.LunaSettings;

public class armaa_squadManagerIntel extends BaseIntelPlugin {
    public static Logger log = Global.getLogger(armaa_squadManagerIntel.class);

    public static final boolean DISPLAY_ONLY = false;
    public static final Tab BUTTON_FLEET   = Tab.FLEET;
    public static final Tab BUTTON_PORTRAIT = Tab.PORTRAIT;
    public static final Tab BUTTON_HELP    = Tab.HELP;
    public static final String BUTTON_INSURE_ALL = "btn_insureAll";

    // Prefix constants — keeps all the string keys in one place so they
    // can't silently diverge between createSquadView and buttonPressConfirmed
    private static final String ACT_PORTRAIT  = "portrait";
    private static final String ACT_SKILL_SWAP = "skillSwap";
    private static final String ACT_PROMOTE   = "promote";

    protected transient Tab currentTab;
    protected PersonAPI member;
    protected int pilot;

    // Tracks which skill slot is being replaced when Tab.SKILL is open.
    // Null when not in a skill-swap flow.
    protected String skillBeingReplaced = null;

    protected IntervalUtil updateInterval = new IntervalUtil(0.25f, 0.25f);

    public armaa_squadManagerIntel() {
        Global.getSector().getIntelManager().addIntel(this);
        Global.getSector().addScript(this);
        this.setImportant(true);
    }

    public List<FleetMemberAPI> getAllShips() {
        return Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy();
    }

    public float getCredits() { return 1; }

    @Override
    public String getSmallDescriptionTitle() { return "getName"; }

    protected String getName() {
        if (listInfoParam != null && listInfoParam instanceof List)
            return "titleV2Expire";
        return "WINGCOM Management";
    }

    @Override public boolean hasSmallDescription()  { return false; }
    @Override public boolean hasLargeDescription()  { return true; }
    @Override public boolean isPlayerVisible()       { return true; }

    public static int TAB_BUTTON_HEIGHT   = 20;
    public static int TAB_BUTTON_WIDTH    = 180;
    public static int ENTRY_HEIGHT        = 80;
    public static int ENTRY_WIDTH         = 300;
    public static int IMAGE_WIDTH         = 80;
    public static int MANAGE_BUTTON_WIDTH = 120;
    public static int IMAGE_DESC_GAP      = 12;

    // -------------------------------------------------------------------------
    // Image helpers
    // -------------------------------------------------------------------------

    public TooltipMakerAPI createPersonImageForPanel(CustomPanelAPI panel,
            PersonAPI person, float width, float height) {
        TooltipMakerAPI image = panel.createUIElement(width, height, false);
        image.addImage(person.getPortraitSprite(), height, 0);
        return image;
    }

    public TooltipMakerAPI createFleetMemberImageForPanel(CustomPanelAPI panel,
            FleetMemberAPI member, float width, float height) {
        TooltipMakerAPI image = panel.createUIElement(width, height, false);
        return image;
    }

    protected TooltipMakerAPI generateImage(CustomPanelAPI panel, FleetMemberAPI member,
            OfficerDataAPI officer, PersonAPI person) {
        if (member == null && officer == null) {
            return createPersonImageForPanel(panel, person, IMAGE_WIDTH, ENTRY_HEIGHT);
        }
        if (member != null) {
            return createFleetMemberImageForPanel(panel, member, IMAGE_WIDTH, ENTRY_HEIGHT);
        } else {
            return createPersonImageForPanel(panel, officer.getPerson(), IMAGE_WIDTH, ENTRY_HEIGHT);
        }
    }

    // -------------------------------------------------------------------------
    // Valid skill list — mirrors the one in armaa_wingmanPromotion so both
    // the auto-learn system and the manual swap UI offer the same pool.
    // -------------------------------------------------------------------------
    private static List<String> getValidSkillList() {
        List<String> skills = new ArrayList<String>();
        skills.add(Skills.COMBAT_ENDURANCE);
        skills.add(Skills.HELMSMANSHIP);
        skills.add(Skills.ENERGY_WEAPON_MASTERY);
        skills.add(Skills.BALLISTIC_MASTERY);
        skills.add(Skills.FIELD_MODULATION);
        skills.add(Skills.TARGET_ANALYSIS);
        skills.add(Skills.IMPACT_MITIGATION);
        skills.add(Skills.DAMAGE_CONTROL);
        skills.add(Skills.POLARIZED_ARMOR);
        skills.add(Skills.POINT_DEFENSE);
        skills.add(Skills.MISSILE_SPECIALIZATION);
        skills.add(Skills.SYSTEMS_EXPERTISE);
        return skills;
    }

    // -------------------------------------------------------------------------
    // FLEET VIEW — list of commanders with established squadrons
    // -------------------------------------------------------------------------
    protected void createFleetView(CustomPanelAPI panel, TooltipMakerAPI info, float width) {
        float pad  = 3;
        float opad = 10;
        Color h = Misc.getHighlightColor();
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return;

        PersonAPI playerChar = player.getFleetData().getCommander();
        boolean addPlayer = Global.getSector().getPersistentData()
                .get("armaa_wingCommander_squadronName_" + playerChar.getId()) instanceof String;

        List<PersonAPI> officers = new ArrayList<PersonAPI>();
        for (OfficerDataAPI od : player.getFleetData().getOfficersCopy()) {
            if (!(Global.getSector().getPersistentData()
                    .get("armaa_wingCommander_squadronName_" + od.getPerson().getId()) instanceof String))
                continue;
            if (player.getFleetData().getMemberWithCaptain(od.getPerson()) == null) continue;
            FleetMemberAPI assignedShip = player.getFleetData().getMemberWithCaptain(od.getPerson());
            if (!assignedShip.getVariant().hasHullMod("armaa_wingCommander")) continue;
            officers.add(od.getPerson());
        }
        if (addPlayer) officers.add(0, playerChar);

        float heightPerItem = ENTRY_HEIGHT + opad;
        float itemPanelHeight = heightPerItem * Math.max(officers.size(), 1);
        CustomPanelAPI itemPanel = panel.createCustomPanel(ENTRY_WIDTH, itemPanelHeight, null);
        float yPos = opad;

        boolean noSquads = true;
        for (PersonAPI commander : officers) {
            TooltipMakerAPI image = generateImage(itemPanel, null, null, commander);
            itemPanel.addUIElement(image).inTL(4, yPos);

            TooltipMakerAPI entry = itemPanel.createUIElement(
                    width - IMAGE_WIDTH - IMAGE_DESC_GAP, ENTRY_HEIGHT, true);
            entry.addPara("Name: " + commander.getName().getFullName(), opad, h,
                    commander.getName().getFullName());
            entry.addPara("Rank: " + commander.getRank(), pad, h, commander.getRank());

            boolean hasSquadron = Global.getSector().getPersistentData()
                    .get("armaa_wingCommander_squadronName_" + commander.getId()) instanceof String;

            if (hasSquadron) {
                noSquads = false;
                String amount = (String) Global.getSector().getPersistentData()
                        .get("armaa_wingCommander_squadronName_" + commander.getId());
                entry.addPara("Commanding: " + amount, pad, h, amount);
                itemPanel.addUIElement(entry).rightOfTop(image, IMAGE_DESC_GAP);

                TooltipMakerAPI buttonHolder = itemPanel.createUIElement(MANAGE_BUTTON_WIDTH, 16, false);
                buttonHolder.addButton("Manage", commander, 120, 16, 0);
                itemPanel.addUIElement(buttonHolder)
                        .inTL(4 + IMAGE_WIDTH + IMAGE_DESC_GAP + ENTRY_WIDTH, yPos + ENTRY_HEIGHT / 4);

                yPos += ENTRY_HEIGHT + 16 + opad;
            }
        }

        if (noSquads) {
            TooltipMakerAPI entry = itemPanel.createUIElement(
                    width - IMAGE_WIDTH - IMAGE_DESC_GAP, ENTRY_HEIGHT, true);
            TooltipMakerAPI image2 = generateImage(itemPanel, null, null, playerChar);
            itemPanel.addUIElement(image2).inTL(4, opad);
            entry.addPara("No squadrons of any merit has been established.", pad, h);
            entry.addPara("Once officers have been %s, their squadrons can be managed from here.",
                    pad, h, "assigned to a unit with WINGCOM installed");
            itemPanel.addUIElement(entry).rightOfTop(image2, IMAGE_DESC_GAP);
        }

        info.addCustom(itemPanel, 0);
        member = null;
    }

    // -------------------------------------------------------------------------
    // SQUAD VIEW — pilots in a commander's squadron
    // -------------------------------------------------------------------------
    protected void createSquadView(CustomPanelAPI panel, TooltipMakerAPI info,
            float width, PersonAPI squadLeader) {
        float pad  = 3;
        float opad = 10;
        Color h = Misc.getHighlightColor();
        if (squadLeader == null || squadLeader.getId() == null) return;

        int numItems = 0;
        if (Global.getSector().getPersistentData()
                .get("armaa_wingCommander_squadSize_" + squadLeader.getId()) instanceof Integer) {
            numItems = (int) Global.getSector().getPersistentData()
                    .get("armaa_wingCommander_squadSize_" + squadLeader.getId());
        }

        MutableCharacterStatsAPI playerStats = Global.getSector().getPlayerFleet()
                .getFleetData().getCommander().getStats();
        int maxElite = (int) Global.getSettings().getInt("officerMaxEliteSkills")
                + (int) playerStats.getDynamic().getMod(Stats.OFFICER_MAX_ELITE_SKILLS_MOD).computeEffective(0);
        int maxLevel = 2;
        if(Global.getSettings().getModManager().isModEnabled("lunalib"))
        {
           maxLevel = LunaSettings.getInt("armaa", "armaa_wingcomMaxLevel");
        }
        // Squadron rename row - field and button both flow into info so they
        // stay visually adjacent rather than on separate containers.
        List<Object> squadParams = new ArrayList<Object>();
        TextFieldAPI squadName = info.addTextField(200f, pad);
        squadParams.add(squadLeader);
        squadParams.add(squadName);
        info.addButton("Rename Squadron", squadParams, 160, 20, pad);

        // Row height: portrait height + rel bar + callsign row + padding + 10px extra gap.
        float heightPerItem = ENTRY_HEIGHT + 82; // extra 22px for callsign field below button
        float itemPanelHeight = heightPerItem * numItems + opad;
        CustomPanelAPI itemPanel = panel.createCustomPanel(width, itemPanelHeight, null);
        float yPos = opad;

        for (int i = 0; i < numItems; i++) {
            PersonAPI pilot = (PersonAPI) Global.getSector().getPersistentData()
                    .get("armaa_wingCommander_wingman_" + i + "_" + squadLeader.getId());
            if (pilot == null) continue;

            String callsign = (String) Global.getSector().getPersistentData()
                    .get("armaa_wingCommander_wingman_" + i + "_callsign_" + squadLeader.getId());

            // --- Portrait ---
            // Button is added FIRST so the engine registers it for hit-testing, then the
            // image is added on top so the portrait is fully visible with no button label
            // obscuring it. Starsector renders later-added elements on top, but hit-tests
            // the first matching element at a position, so this gives us a clickable portrait
            // with no visible button chrome.
            // Button data: Integer pilot index → buttonPressConfirmed routes to Tab.PORTRAIT.
            TooltipMakerAPI portraitBtn = itemPanel.createUIElement(IMAGE_WIDTH, ENTRY_HEIGHT, false);
            portraitBtn.addButton("", i, IMAGE_WIDTH, ENTRY_HEIGHT, 0);
            itemPanel.addUIElement(portraitBtn).inTL(4, yPos);

            TooltipMakerAPI image = itemPanel.createUIElement(IMAGE_WIDTH, ENTRY_HEIGHT, false);
            image.addImage(pilot.getPortraitSprite(), ENTRY_HEIGHT, 0);
            itemPanel.addUIElement(image).inTL(4, yPos);

            // 'Change Portrait' button centered beneath the portrait.
            // Same width as the portrait so it aligns cleanly.
            // Button carries the pilot index — same routing as the portrait overlay above.
            TooltipMakerAPI portraitLabelBtn = itemPanel.createUIElement(IMAGE_WIDTH, 20, false);
            portraitLabelBtn.addButton("Portrait", i, IMAGE_WIDTH, 20, 0);
            itemPanel.addUIElement(portraitLabelBtn).inTL(4, yPos + ENTRY_HEIGHT + 1);

            // --- Info column (name / callsign / level / rel bar) ---
            TooltipMakerAPI entry = itemPanel.createUIElement(
                    ENTRY_WIDTH - IMAGE_WIDTH - IMAGE_DESC_GAP, heightPerItem - opad, true);
            entry.addPara("Name: " + pilot.getName().getFullName(), pad, h,
                    pilot.getName().getFullName());
            entry.addPara("Callsign: " + callsign, pad, h, callsign);

            int pilotLevel = pilot.getStats().getLevel();
            if (maxLevel < pilotLevel) maxLevel = pilotLevel;
            entry.addPara("Level: " + pilotLevel + " / " + maxLevel, pad, h, String.valueOf(pilotLevel));
            entry.addRelationshipBar(pilot, 5f);
            itemPanel.addUIElement(entry).rightOfTop(image, IMAGE_DESC_GAP);

            // --- Skills column ---
            // Each known skill is rendered as a clickable button labelled with the skill name
            // (+ suffix if elite).  Clicking opens the skill-picker (Tab.SKILL) for that slot.
            // Skills the pilot doesn't have yet show as greyed-out "empty slot" buttons so the
            // player can see how many they have left and fill them manually.
            MutableCharacterStatsAPI stats = pilot.getStats();
            List<MutableCharacterStatsAPI.SkillLevelAPI> skillList =
                    new ArrayList<MutableCharacterStatsAPI.SkillLevelAPI>(stats.getSkillsCopy());

            float skillColX = 4 + IMAGE_WIDTH + IMAGE_DESC_GAP + ENTRY_WIDTH / 2 + opad;
            TooltipMakerAPI skillsHeader = itemPanel.createUIElement(MANAGE_BUTTON_WIDTH + opad * 2, 16, false);
            skillsHeader.addPara("Skills: " + Misc.getNumEliteSkills(pilot) + " / " + maxElite + " Elite",
                    pad, h, String.valueOf(Misc.getNumEliteSkills(pilot)));
            itemPanel.addUIElement(skillsHeader).inTL(skillColX, yPos);

            float skillBtnY = yPos + 20;
            for (MutableCharacterStatsAPI.SkillLevelAPI skill : skillList) {
                if (skill.getLevel() <= 0) continue;

                String skillLabel = skill.getSkill().getName();
                if (skill.getLevel() >= 2) skillLabel += " +";

                // Params for skill swap: [commander PersonAPI, pilot index int, skill id String]
                List<Object> swapParams = new ArrayList<Object>();
                swapParams.add(squadLeader);
                swapParams.add(i);
                swapParams.add(skill.getSkill().getId());
                swapParams.add(ACT_SKILL_SWAP);

                TooltipMakerAPI skillBtn = itemPanel.createUIElement(MANAGE_BUTTON_WIDTH + opad * 2, 20, false);
                skillBtn.addButton(skillLabel, swapParams, MANAGE_BUTTON_WIDTH + opad * 2, 20, 0);
                itemPanel.addUIElement(skillBtn).inTL(skillColX, skillBtnY);
                skillBtnY += 22;
            }

            // --- Right column: dialogue only ---
            // setParaFont with a raw path crashes because Starsector expects a registered
            // font ID, not a file path. Use setParaFontInsignia which is a known safe call
            // that bumps the size up slightly (~20px equivalent), then reset with Default.
            // Color: textFriendColor = the same green used in the combat HUD.
            // Nudge the text down 25% of the row height for a visually centered feel.
            float renameX = skillColX + MANAGE_BUTTON_WIDTH + opad * 4;
            float rightColW = Math.max(200f, width - renameX - opad);
            String chatter = getChatterForPilot(pilot, i);
            Color combatGreen = Global.getSettings().getColor("textFriendColor");
            float dialogueNudge = (heightPerItem - opad) * 0.25f;
            TooltipMakerAPI dialogueCol = itemPanel.createUIElement(rightColW, heightPerItem - opad, false);
            dialogueCol.addSpacer(dialogueNudge);
            dialogueCol.setParaFont(Global.getSettings().getString("armaa_wingCommander", "dialogueFont"));
            dialogueCol.addPara(chatter, pad, combatGreen, chatter);
            dialogueCol.setParaFontDefault();
            itemPanel.addUIElement(dialogueCol).inTL(renameX, yPos);

            // --- Callsign button + field stacked beneath the skill buttons ---
            // Button first, text field directly below it — both share the same X
            // so they're always visually aligned regardless of skill count.
            float callsignBtnW = 128f;
            float skillColW = MANAGE_BUTTON_WIDTH + opad * 2; // 140
            float callsignBtnX = skillColX + (skillColW - callsignBtnW) / 2f;
            float callsignY = skillBtnY + pad;

            // Text field sits directly below the button, same X and width
            TooltipMakerAPI fieldHolder = itemPanel.createUIElement(callsignBtnW, 20, false);
            TextFieldAPI nameChange = fieldHolder.addTextField(callsignBtnW, 0);
            itemPanel.addUIElement(fieldHolder).inTL(callsignBtnX, callsignY + 22);

            List<Object> renameParams = new ArrayList<Object>();
            renameParams.add(squadLeader);
            renameParams.add(i);
            renameParams.add(nameChange);
            TooltipMakerAPI callsignBtn = itemPanel.createUIElement(callsignBtnW, 20, false);
            callsignBtn.addButton("Change Callsign", renameParams, (int) callsignBtnW, 20, 0);
            itemPanel.addUIElement(callsignBtn).inTL(callsignBtnX, callsignY);

            // --- Promote button (only when eligible, not already queued) ---
            if (pilot.getRelToPlayer().getRel() >= .70f) {
                boolean alreadyConsidered = false;
                if (Global.getSector().getIntelManager().getIntel(armaa_promoteWingman.class) != null) {
                    int size = Global.getSector().getIntelManager()
                            .getIntel(armaa_promoteWingman.class).size();
                    for (int j = 0; j < size; j++) {
                        armaa_promoteWingman pastIntel = (armaa_promoteWingman) Global.getSector()
                                .getIntelManager().getIntel(armaa_promoteWingman.class).get(j);
                        if (pastIntel.getCandidate().getId().equals(pilot.getId())) {
                            alreadyConsidered = true;
                        }
                    }
                }
                if (!alreadyConsidered) {
                    List<Object> promoteParams = new ArrayList<Object>();
                    promoteParams.add(squadLeader);
                    promoteParams.add(i);
                    promoteParams.add(ACT_PROMOTE);
                    TooltipMakerAPI promoteBtn = itemPanel.createUIElement(120, 20, false);
                    promoteBtn.addButton("Promote", promoteParams, 120, 20, 0);
                    itemPanel.addUIElement(promoteBtn).inTL(callsignBtnX + callsignBtnW + opad, callsignY);
                }
            }

            yPos += heightPerItem;
        }

        info.addCustom(itemPanel, 0);
    }

    // -------------------------------------------------------------------------
    // SKILL VIEW — pick a replacement skill for one slot
    // -------------------------------------------------------------------------
    protected void createSkillView(CustomPanelAPI panel, TooltipMakerAPI info,
            float width, PersonAPI squadLeader, int pilotIndex, String replacingSkillId) {
        float pad  = 3;
        float opad = 10;
        Color h = Misc.getHighlightColor();

        PersonAPI pilot = (PersonAPI) Global.getSector().getPersistentData()
                .get("armaa_wingCommander_wingman_" + pilotIndex + "_" + squadLeader.getId());
        if (pilot == null) return;

        String callsign = (String) Global.getSector().getPersistentData()
                .get("armaa_wingCommander_wingman_" + pilotIndex + "_callsign_" + squadLeader.getId());

        String replacingName = Global.getSettings().getSkillSpec(replacingSkillId).getName();
        info.addPara("Replace %s for pilot \"" + callsign + "\"", opad, h, replacingName);
        info.addPara("Select a new skill below. The old skill will be removed.", pad,
                Misc.getGrayColor());

        List<String> validSkills = getValidSkillList();

        // Two-column grid of skill buttons.
        // Skills the pilot already has are shown greyed-out (can't pick the same skill twice).
        int cols = 2;
        int rows = (int) Math.ceil(validSkills.size() / (float) cols);
        float btnW = (width - opad * (cols + 1)) / cols;
        float btnH = 26;
        float itemPanelHeight = rows * (btnH + pad) + opad * 2;
        CustomPanelAPI skillPanel = panel.createCustomPanel(width, itemPanelHeight, null);

        int idx = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (idx >= validSkills.size()) break;
                String skillId = validSkills.get(idx++);
                SkillSpecAPI spec = Global.getSettings().getSkillSpec(skillId);
                boolean alreadyHas = pilot.getStats().hasSkill(skillId)
                        && !skillId.equals(replacingSkillId);
                String label = spec.getName() + (alreadyHas ? " (known)" : "");

                // Params: [commander, pilotIndex, oldSkillId, newSkillId, ACT_SKILL_SWAP+"confirm"]
                List<Object> confirmParams = new ArrayList<Object>();
                confirmParams.add(squadLeader);
                confirmParams.add(pilotIndex);
                confirmParams.add(replacingSkillId);
                confirmParams.add(skillId);
                confirmParams.add(ACT_SKILL_SWAP + "_confirm");

                TooltipMakerAPI btn = skillPanel.createUIElement(btnW, btnH, false);
                ButtonAPI b = btn.addButton(label, alreadyHas ? null : confirmParams,
                        (int) btnW, (int) btnH, 0);
                if (alreadyHas) b.setEnabled(false);

                float xPos = opad + c * (btnW + opad);
                float yPos = opad + r * (btnH + pad);
                skillPanel.addUIElement(btn).inTL(xPos, yPos);
            }
        }

        info.addCustom(skillPanel, opad);

        // Cancel button — returns to squad view without changing anything
        List<Object> cancelParams = new ArrayList<Object>();
        cancelParams.add(squadLeader);
        cancelParams.add(pilotIndex);
        cancelParams.add("cancel_skill");
        TooltipMakerAPI cancelHolder = panel.createUIElement(120, 20, false);
        cancelHolder.addButton("Cancel", cancelParams, 120, 20, 0);
        // Place below the skill grid
        info.addCustom(cancelHolder, pad);
    }

    // -------------------------------------------------------------------------
    // PORTRAIT VIEW — grid of portraits to pick from
    // -------------------------------------------------------------------------
    protected void createPortraitView(CustomPanelAPI panel, TooltipMakerAPI info,
            float width, PersonAPI squadLeader, int squadMember) {
        float pad  = 3;
        float opad = 10;

        PersonAPI pilot = (PersonAPI) Global.getSector().getPersistentData()
                .get("armaa_wingCommander_wingman_" + squadMember + "_" + squadLeader.getId());
        if (pilot == null) return;

        List<String> portraits = new ArrayList<String>();
        for (Gender gender : Gender.values()) {
            List<String> portraitList = new ArrayList<String>();
            if (Misc.getCommissionFaction() != null) {
                portraitList.addAll(Misc.getCommissionFaction().getPortraits(gender).getItems());
            }
            portraitList.addAll(Global.getSector().getPlayerFaction().getPortraits(gender).getItems());
            for (String p : portraitList) {
                if (!portraits.contains(p)) portraits.add(p);
            }
        }

        int cols = Global.getSettings().getScreenWidth() < 1920 ? 9 : 12;
        int rows = (int) Math.ceil(portraits.size() / (float) cols);
        // Extra 16px per row for the 'Select' label beneath each portrait
        float itemPanelHeight = rows * (ENTRY_HEIGHT + 16) + opad * rows;
        CustomPanelAPI itemPanel = panel.createCustomPanel(ENTRY_WIDTH * cols, itemPanelHeight, null);

        float yPos = opad;
        int li = 0;
        for (int r = 0; r < rows; r++) {
            float xPos = opad;
            for (int c = 0; c < cols; c++) {
                if (li > portraits.size() - 1) break;
                String portraitPath = portraits.get(li++);

                // Button FIRST (hit-test registration), image SECOND (renders on top).
                // This makes the portrait fully visible while still being clickable.
                List<Object> selectParams = new ArrayList<Object>();
                selectParams.add(squadLeader);
                selectParams.add(squadMember);
                selectParams.add(portraitPath);

                TooltipMakerAPI selectBtn = itemPanel.createUIElement(IMAGE_WIDTH, ENTRY_HEIGHT, false);
                selectBtn.addButton("", selectParams, IMAGE_WIDTH, ENTRY_HEIGHT, 0);
                itemPanel.addUIElement(selectBtn).inTL(4 + xPos, yPos);

                TooltipMakerAPI image = itemPanel.createUIElement(IMAGE_WIDTH, ENTRY_HEIGHT, false);
                image.addImage(portraitPath, ENTRY_HEIGHT, 0);
                itemPanel.addUIElement(image).inTL(4 + xPos, yPos);

                // Small label so the player knows portraits are selectable
                TooltipMakerAPI label = itemPanel.createUIElement(IMAGE_WIDTH, 14, false);
                label.addPara("Select", 0, Misc.getHighlightColor());
                itemPanel.addUIElement(label).inTL(4 + xPos, yPos + ENTRY_HEIGHT + 1);

                xPos += IMAGE_WIDTH + opad * 2;
            }
            yPos += ENTRY_HEIGHT + 16 + opad;
        }

        info.addCustom(itemPanel, 0);
    }

    // -------------------------------------------------------------------------
    // HELP VIEW
    // -------------------------------------------------------------------------
    protected void createHelpView(TooltipMakerAPI info) {
        float opad = 15;
        float pad  = 3;
        Color h = Misc.getHighlightColor();

        TooltipMakerAPI help = info.beginImageWithText("graphics/icons/skills/best_of_the_best.png", 52f);
        help.setParaInsigniaLarge();
        help.addPara("About WINGCOM", opad);
        help.setParaFontDefault();
        help.setBulletedListMode(BaseIntelPlugin.BULLET);
        help.addPara("When an officer is assigned to a unit with WINGCOM, the crew drafted for the "
                + "squadron can be reviewed here.", pad);
        help.addPara("Squadron Members are generally drafted from the fleets best pilots, often %s "
                + "than their compatriots.", pad, h, "possessing greater skills");
        help.addPara("By engaging in many battles, these squad members can eventually be %s.", pad, h,
                "promoted to full fledged officers");
        help.setBulletedListMode(BaseIntelPlugin.BULLET);
        UIPanelAPI temp = info.addImageWithText(pad);
        unindent(info);

        TooltipMakerAPI help2 = info.beginImageWithText("graphics/icons/skills/crew_training.png", 82f);
        help2.setParaInsigniaLarge();
        help2.addPara("Squad Enhancement", opad);
        help2.setParaFontDefault();
        help2.setBulletedListMode(BaseIntelPlugin.BULLET);
        help2.addPara("Squadrons combat performance will fluctuate based on two simple factors.", pad);
        help2.addPara("Winning battles will generally increase the squadrons performance by a random amount.", pad, h);
        help2.addPara("Once a pilot reaches their level cap, there is a small chance they can exceed it by one.", pad, h);
        help2.addPara("If the squadron leader is shot down or forced to retreat, performance will suffer.", pad);
        help2.setBulletedListMode(BaseIntelPlugin.BULLET);
        help2.setParaInsigniaLarge();
        help2.addPara("Promoting Pilots", opad);
        help2.setParaFontDefault();
        help2.setBulletedListMode(BaseIntelPlugin.BULLET);
        help2.addPara("Squad members can be promoted once their relationship exceeds %s points.", pad, h, "70");
        help2.addPara("If a squad member is promoted, a new pilot will fill in their vacant position.", pad);
        help2.addPara("You can also simply %s for promotion.", pad, h, "pass them over");
        help2.setBulletedListMode(BaseIntelPlugin.BULLET);
        unindent(info);
        temp = info.addImageWithText(pad);

        TooltipMakerAPI help3 = info.beginImageWithText("graphics/icons/skills/tactical_drills.png", 52f);
        help3.setParaInsigniaLarge();
        help3.addPara("Managing Skills", opad);
        help3.setParaFontDefault();
        help3.setBulletedListMode(BaseIntelPlugin.BULLET);
        help3.addPara("Click any skill button next to a pilot to replace it with a different one.", pad);
        help3.addPara("Click a pilot's portrait to change their appearance.", pad);
        help3.setBulletedListMode(BaseIntelPlugin.BULLET);
        unindent(info);
        temp = info.addImageWithText(pad);
    }

    // -------------------------------------------------------------------------
    // Tab buttons
    // -------------------------------------------------------------------------
    public TooltipMakerAPI generateTabButton(CustomPanelAPI buttonRow, String nameId, Tab tab,
            Color base, Color bg, Color bright, TooltipMakerAPI rightOf) {
        TooltipMakerAPI holder = buttonRow.createUIElement(TAB_BUTTON_WIDTH, TAB_BUTTON_HEIGHT, false);
        ButtonAPI button = holder.addAreaCheckbox(nameId, tab, base, bg, bright,
                TAB_BUTTON_WIDTH, TAB_BUTTON_HEIGHT, 0);
        button.setChecked(tab == this.currentTab);
        if (rightOf != null) {
            buttonRow.addUIElement(holder).rightOfTop(rightOf, 4);
        } else {
            buttonRow.addUIElement(holder).inTL(0, 3);
        }
        return holder;
    }

    protected TooltipMakerAPI addTabButtons(TooltipMakerAPI tm, CustomPanelAPI panel, float width) {
        FactionAPI fc = getFactionForUIColors();
        Color base = fc.getBaseUIColor(), bg = fc.getDarkUIColor(), bright = fc.getBrightUIColor();
        TooltipMakerAPI btnHolder1 = generateTabButton(panel, "Squadrons", BUTTON_FLEET, base, bg, bright, null);
        TooltipMakerAPI btnHolder3 = generateTabButton(panel, "Info",       BUTTON_HELP, base, bg, bright, btnHolder1);
        return btnHolder1;
    }

    // -------------------------------------------------------------------------
    // createLargeDescription — routes to the correct view
    // -------------------------------------------------------------------------
    @Override
    public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
        float opad = 10;
        float pad  = 3;
        Color h = Misc.getHighlightColor();

        if (currentTab == null) currentTab = Tab.FLEET;

        TooltipMakerAPI info = panel.createUIElement(width, height - TAB_BUTTON_HEIGHT - 4, true);
        FactionAPI faction = Global.getSector().getPlayerFaction();

        TooltipMakerAPI buttonHolder = addTabButtons(info, panel, width);

        info.addSectionHeading("Squadron Management", faction.getBaseUIColor(),
                faction.getDarkUIColor(), com.fs.starfarer.api.ui.Alignment.MID, opad);

        switch (currentTab) {
            case FLEET:
                createFleetView(panel, info, width);
                break;
            case HELP:
                createHelpView(info);
                break;
            case SQUAD:
                String squadName = (String) Global.getSector().getPersistentData()
                        .get("armaa_wingCommander_squadronName_" + member.getId());
                info.addPara("Squadron Details: " + squadName, opad, h, squadName);
                createSquadView(panel, info, width, member);
                break;
            case PORTRAIT:
                info.addPara("Click a portrait to change appearance", opad);
                createPortraitView(panel, info, width, member, pilot);
                break;
            case SKILL:
                String beingReplaced = skillBeingReplaced != null ? skillBeingReplaced : "";
                createSkillView(panel, info, width, member, pilot, beingReplaced);
                break;
        }

        panel.addUIElement(info).belowLeft(buttonHolder, pad);
    }

    // -------------------------------------------------------------------------
    // Button routing
    // -------------------------------------------------------------------------
    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {

        // Tab switch buttons (the top nav bar)
        if (buttonId instanceof Tab) {
            currentTab = (Tab) buttonId;
            ui.updateUIForItem(this);
            return;
        }

        if (buttonId == BUTTON_INSURE_ALL) {
            ui.updateUIForItem(this);
            return;
        }

        // Fleet view: clicking "Manage" next to a commander's entry
        if (buttonId instanceof PersonAPI) {
            member = (PersonAPI) buttonId;
            currentTab = Tab.SQUAD;
            ui.updateUIForItem(this);
            return;
        }

        // Squad view: clicking the transparent portrait overlay button (Integer = pilot index)
        // Only enter portrait flow when we are already in the squad view.
        if (buttonId instanceof Integer && currentTab == Tab.SQUAD) {
            pilot = (int) buttonId;
            currentTab = Tab.PORTRAIT;
            ui.updateUIForItem(this);
            return;
        }

        // All List-based button params
        if (buttonId instanceof List) {
            List<Object> params = (List<Object>) buttonId;
            if (params.isEmpty()) return;

            // --- Skill swap: cancel ---
            if (params.size() == 3 && ACT_SKILL_SWAP.equals(params.get(2))) {
                // Actually this is the swap initiation (size 4 below), fall through.
                // "cancel_skill" is a size-3 list with literal "cancel_skill" string.
            }
            if (params.size() == 3 && "cancel_skill".equals(params.get(2))) {
                currentTab = Tab.SQUAD;
                skillBeingReplaced = null;
                ui.updateUIForItem(this);
                return;
            }

            // --- Skill swap: initiation (open the skill picker) ---
            // params: [PersonAPI squadLeader, Integer pilotIndex, String skillId, "skillSwap"]
            if (params.size() == 4 && ACT_SKILL_SWAP.equals(params.get(3))) {
                member           = (PersonAPI) params.get(0);
                pilot            = (int) params.get(1);
                skillBeingReplaced = (String) params.get(2);
                currentTab       = Tab.SKILL;
                ui.updateUIForItem(this);
                return;
            }

            // --- Skill swap: confirmation (a replacement was chosen) ---
            // params: [PersonAPI squadLeader, Integer pilotIndex, String oldSkillId,
            //          String newSkillId, "skillSwap_confirm"]
            if (params.size() == 5 && (ACT_SKILL_SWAP + "_confirm").equals(params.get(4))) {
                PersonAPI squadLeader = (PersonAPI) params.get(0);
                int       pilotIdx   = (int) params.get(1);
                String    oldSkill   = (String) params.get(2);
                String    newSkill   = (String) params.get(3);

                PersonAPI p = (PersonAPI) Global.getSector().getPersistentData()
                        .get("armaa_wingCommander_wingman_" + pilotIdx + "_" + squadLeader.getId());
                if (p != null) {
                    MutableCharacterStatsAPI stats = p.getStats();
                    // Remove old skill: set level to 0 by reducing until gone
                    // increaseSkill has no removeSkill counterpart in the public API,
                    // so we use the level setter workaround.
                    int oldLevel = (int) stats.getSkillLevel(oldSkill);
                    for (int k = 0; k < oldLevel; k++) {
                        stats.setSkillLevel(oldSkill, 0);
                    }
                    // Add the new skill at level 1
                    stats.increaseSkill(newSkill);
                    // Adjust pilot level to stay consistent (level = number of skills)
                    // We don't change level here — the skill count is what matters mechanically.
                    Global.getSector().getPersistentData().put(
                            "armaa_wingCommander_wingman_" + pilotIdx + "_" + squadLeader.getId(), p);
                }
                skillBeingReplaced = null;
                currentTab = Tab.SQUAD;
                ui.updateUIForItem(this);
                return;
            }

            // --- Promote ---
            // params: [PersonAPI squadLeader, Integer pilotIndex, "promote"]
            if (params.size() == 3 && ACT_PROMOTE.equals(params.get(2))) {
                PersonAPI squadLeader = (PersonAPI) params.get(0);
                int pilotIdx = (int) params.get(1);
                CampaignFleetAPI player = Global.getSector().getPlayerFleet();
                FleetMemberAPI lead = player.getFleetData().getMemberWithCaptain(squadLeader);
                armaa_promoteWingman intel = new armaa_promoteWingman(null, lead, pilotIdx);
                Global.getSector().getIntelManager().addIntel(intel);
                ui.updateIntelList();
                return;
            }

            // --- Squadron rename ---
            // params: [PersonAPI squadLeader, TextFieldAPI nameField]  (size == 2)
            if (params.size() == 2) {
                PersonAPI squadLeader = (PersonAPI) params.get(0);
                TextFieldAPI newName  = (TextFieldAPI) params.get(1);
                Global.getSector().getPersistentData().put(
                        "armaa_wingCommander_squadronName_" + squadLeader.getId(), newName.getText());
                currentTab = Tab.SQUAD;
                ui.updateUIForItem(this);
                return;
            }

            // --- Portrait select (from portrait picker) ---
            // params: [PersonAPI squadLeader, Integer pilotIndex, String portraitPath]  (size == 3,
            //         third element is a path string, not a keyword)
            if (params.size() == 3 && params.get(2) instanceof String
                    && !((String) params.get(2)).startsWith("cancel")) {
                PersonAPI squadLeader = (PersonAPI) params.get(0);
                int pilotIdx          = (int) params.get(1);
                String portraitPath   = (String) params.get(2);
                PersonAPI p = (PersonAPI) Global.getSector().getPersistentData()
                        .get("armaa_wingCommander_wingman_" + pilotIdx + "_" + squadLeader.getId());
                if (p != null) {
                    p.setPortraitSprite(portraitPath);
                    Global.getSector().getPersistentData().put(
                            "armaa_wingCommander_wingman_" + pilotIdx + "_" + squadLeader.getId(), p);
                }
                currentTab = Tab.SQUAD;
                ui.updateUIForItem(this);
                return;
            }

            // --- Callsign rename ---
            // params: [PersonAPI squadLeader, Integer pilotIndex, TextFieldAPI nameField]
            if (params.size() == 3 && params.get(2) instanceof TextFieldAPI) {
                PersonAPI squadLeader = (PersonAPI) params.get(0);
                int pilotIdx          = (int) params.get(1);
                TextFieldAPI text     = (TextFieldAPI) params.get(2);
                Global.getSector().getPersistentData().put(
                        "armaa_wingCommander_wingman_" + pilotIdx + "_callsign_" + squadLeader.getId(),
                        text.getText());
                currentTab = Tab.SQUAD;
                ui.updateUIForItem(this);
                return;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Misc overrides
    // -------------------------------------------------------------------------
    @Override
    public void createConfirmationPrompt(Object buttonId, TooltipMakerAPI prompt) {
        if (buttonId == BUTTON_INSURE_ALL) { }
        if (buttonId instanceof FleetMemberAPI) { }
    }

    @Override
    public boolean doesButtonHaveConfirmDialog(Object buttonId) {
        if (buttonId == BUTTON_INSURE_ALL) return true;
        if (buttonId instanceof FleetMemberAPI) return true;
        return false;
    }

    @Override public String getConfirmText(Object buttonId) { return super.getConfirmText(buttonId); }
    @Override public String getCancelText(Object buttonId)  { return super.getCancelText(buttonId); }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add("Squadron Manager");
        return tags;
    }

    @Override public String getIcon()     { return "graphics/icons/markets/headquarters.png"; }
    @Override public boolean isHidden()   { return false; }

    @Override
    protected void notifyEnded() {
        Global.getSector().getIntelManager().removeIntel(this);
        Global.getSector().removeScript(this);
    }

    protected static String getString(String id) { return "GetString"; }

    // -------------------------------------------------------------------------
    // Pilot dialogue helper
    // Priority order:
    //   1. special_wing_lines entry for the pilot's assigned wing hull ID
    //   2. Voice-specific pool matching pilot.getVoice()
    //   3. General pool (squadChatter) as final fallback
    //
    // Seeded from pilot ID hash + index so the line is stable across repaints
    // without needing to shuffle on every render.
    // -------------------------------------------------------------------------
    private String getChatterForPilot(PersonAPI pilot, int index) {
        // Rotate dialogue every few days by folding the current day interval
        // into the seed. Changing CHATTER_ROTATION_DAYS adjusts the frequency.
        final int CHATTER_ROTATION_DAYS = 5;
        long period = (long) Global.getSector().getClock().getDay() / CHATTER_ROTATION_DAYS;
        Random rand = new Random(pilot.getId().hashCode() + index + period);

        // --- Step 1: build the voice-appropriate base pool ---
        // Each voice maps to its own pool. FAITHFUL and SPACER have no
        // dedicated pool so they fall through to the general pool.
        String voice = pilot.getVoice();
        List<String> basePool = null;

        if (Voices.SOLDIER.equals(voice)) {
            basePool = MechaModPlugin.squadChatter_soldier;
        } else if (Voices.VILLAIN.equals(voice)) {
            basePool = MechaModPlugin.squadChatter_villain;
        } else if ("alpha".equals(voice)) {
            basePool = MechaModPlugin.squadChatter_alpha;
        } else if ("beta".equals(voice)) {
            basePool = MechaModPlugin.squadChatter_beta;
        } else if ("gamma".equals(voice)) {
            basePool = MechaModPlugin.squadChatter_gamma;
        }

        // Fall back to general pool if voice has no dedicated pool or pool is empty
        if (basePool == null || basePool.isEmpty()) {
            basePool = MechaModPlugin.squadChatter;
        }

        // Absolute last resort
        if (basePool == null || basePool.isEmpty()) {
            return "...";
        }

        // Work on a copy so we never mutate the source list
        List<String> pool = new ArrayList<String>(basePool);

        // --- Step 2: resolve special wing line for this pilot's wing ---
        // Walk crewed wings in order accumulating fighter counts, mirroring
        // the sequential assignment in createPilots. Non-crewed wings are
        // skipped with the same guard as getWingSize.
        // If found, add the special line into the pool with extra weight (3x)
        // so it comes up more often than a regular line but isn't guaranteed.
        if (member != null) {
            FleetMemberAPI assignedShip = Global.getSector().getPlayerFleet()
                    .getFleetData().getMemberWithCaptain(member);
            if (assignedShip != null) {
                ShipVariantAPI variant = assignedShip.getVariant();
                int accumulated = 0;
                String resolvedHullId = null;
                FighterWingSpecAPI resolvedWing = null;
                for (int w = 0; w < variant.getWings().size(); w++) {
                    FighterWingSpecAPI wingSpec = variant.getWing(w);
                    if (wingSpec == null) continue;
                    if (wingSpec.getVariant().getHullSpec().getMinCrew() <= 0) continue;
                    int fighters = wingSpec.getNumFighters();
                    if (index < accumulated + fighters) {
                        resolvedHullId = wingSpec.getVariant().getHullSpec().getHullId();
                        resolvedWing   = wingSpec;
                        break;
                    }
                    accumulated += fighters;
                }
                if (resolvedHullId != null) {
                    String specialLine = MechaModPlugin.SPECIAL_WING_DIALG.get(resolvedHullId);
                    if (specialLine == null && resolvedWing != null) {
                        String baseId = resolvedWing.getVariant().getHullSpec().getBaseHullId();
                        specialLine = MechaModPlugin.SPECIAL_WING_DIALG.get(baseId);
                    }
                    if (specialLine != null && !specialLine.isEmpty()) {
                        // Add 3 times for ~3x likelihood vs a single regular line
                        pool.add(specialLine);
                        pool.add(specialLine);
                        pool.add(specialLine);
                    }
                }
            }
        }

        // --- Step 3: pick from the combined pool ---
        return pool.get(rand.nextInt(pool.size()));
    }

    public static enum Tab {
        FLEET, PORTRAIT, HELP, SQUAD, SKILL
    }
}