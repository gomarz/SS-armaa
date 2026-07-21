package data.campaign.rulecmd;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.VisualPanelAPI;
import com.fs.starfarer.api.util.Misc.Token;
import java.util.Map;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI.SkillLevelAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.impl.campaign.rulecmd.missions.BarCMD;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import data.scripts.campaign.armaa_dawnListener;
import data.scripts.util.armaa_utils;
import java.util.ArrayList;
import java.util.List;
import lunalib.lunaSettings.LunaSettings;

public class armaa_dawnCMD extends BaseCommandPlugin {

    /**
     * Soft cap on how many "Invite X" options the picker offers. The option
     * panel doesn't scroll, and the portrait roster in the text panel grows
     * with it - tune to taste.
     */
    private static final int MAX_PICKER_OPTIONS = 8;

    private static final String GUEST_PICK_PREFIX = "armaa_dawnGuestPick_";

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        String action = params.get(0).getString(memoryMap);
        MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
        if (memory == null) {
            return false; // should not be possible unless there are other big problems already
        }

        if ("setHireDate".equals(action)) {
            if (Global.getSector().getMemoryWithoutUpdate().get("$armaa_dawnHireDate") != null) {
                return false;
            }
            Global.getLogger(this.getClass()).info("Adding Dawn Listener..");
            Global.getSector().getListenerManager().addListener(new armaa_dawnListener());
            long timestamp = Global.getSector().getClock().getTimestamp();
            Global.getSector().getMemoryWithoutUpdate().set("$armaa_dawnHireDate", timestamp);
            // give her a mek
            String variantId = "armaa_guardual_bs_Hull";
            ShipVariantAPI variant = Global.getSettings().getVariant(variantId).clone();
            FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);
            Global.getSector().getPlayerFleet().getFleetData().addFleetMember(member);
            member.setCaptain(Global.getSector().getImportantPeople().getPerson("armaa_dawn"));
            return true;
        }
        if ("setInteractionTimestamp".equals(action)) {
            long timestamp = Global.getSector().getClock().getTimestamp();
            Global.getSector().getMemoryWithoutUpdate().set("$armaa_dawnBarTimestamp", timestamp);
            return true;
        } else if ("checkDaysElapsed".equals(action)) {
            String startDate = null;
            if (params.size() > 2 && params.get(2) != null) {
                startDate = params.get(2).getString(memoryMap);
            }
            float days = params.get(1).getFloat(memoryMap);
            long timestamp = startDate == null ? (Long) Global.getSector().getMemoryWithoutUpdate().get("$armaa_dawnHireDate") : (Long) Global.getSector().getMemoryWithoutUpdate().get("$" + startDate);
            Global.getSector().getMemoryWithoutUpdate().set("$armaa_dawnElapsedDays", Global.getSector().getClock().getElapsedDaysSince(timestamp));
            if (Global.getSector().getClock().getElapsedDaysSince(timestamp) >= days) {
                return true;
            }
            return false;
        } else if ("dawnCaresAboutHeresy".equals(action)) {
            boolean cares = true;
            if (Global.getSettings().getModManager().isModEnabled("lunalib") == false) {
                cares = true;
                return true;
            }
            cares = LunaSettings.getBoolean("armaa", "armaa_dawnCaresAboutHeresy");
            memory.set("$armaa_dawnTrueToCharacter", cares, 1);
            return cares;

        } else if ("setGuestOfficer".equals(action)) {
            List<PersonAPI> people = new ArrayList<>();
            for (OfficerDataAPI officer : Global.getSector().getPlayerFleet().getFleetData().getOfficersCopy()) {
                PersonAPI p = officer.getPerson();
                if (!isEligibleGuest(p)) {
                    continue;
                }
                people.add(p);
            }
            if (people.isEmpty()) {
                return false; // no valid guest; guards the random pick below against an empty list
            }
            PersonAPI randomPerson = people.get(
                    (int) (Math.random() * people.size())
            );
            applyGuest(randomPerson, memory);
            return true;

        } // ShowSecondPerson only checks for important people, so..
        else if ("showGuestOfficer".equals(action)) {
            PersonAPI person = (PersonAPI) memory.get("$armaa_dawnGuestPerson");
            dialog.getVisualPanel().showSecondPerson(person);
        } else if ("supressBarSupression".equals(action)) {
            //lol
            EveryFrameScript barscript = null;
            for (EveryFrameScript script : Global.getSector().getTransientScripts()) {
                if (script instanceof BarCMD.BarAmbiencePlayer) {
                    barscript = script;
                }
            }
            if (barscript != null) {
                Global.getSector().removeTransientScript(barscript);
            }
            Global.getSector().getCampaignUI().suppressMusic(0f);
            return true;

        } else if ("initDawn".equals(action)) {
            Global.getSector().getEconomy().getMarket("armaa_meshanii_market").getCommDirectory().getEntryForPerson(Global.getSector().getImportantPeople().getPerson("armaa_dawn")).setHidden(false);
            //runcode
            OfficerManagerEvent event = armaa_utils.getOfficerManagerEvent();
            PersonAPI dawn = Global.getSector().getImportantPeople().getPerson("armaa_dawn");
            OfficerManagerEvent.AvailableOfficer f = new OfficerManagerEvent.AvailableOfficer(dawn, "armaa_meshanii_market", 4000, 1000);
            f.timeRemaining = 9999999999999999999999999999999999999f;
            dawn.getMemoryWithoutUpdate().set("$ome_hireable", true);
            dawn.getMemoryWithoutUpdate().set("$ome_eventRef", event);
            dawn.getMemoryWithoutUpdate().set("$ome_hiringBonus", Misc.getWithDGS(f.hiringBonus));
            dawn.getMemoryWithoutUpdate().set("$ome_salary", Misc.getWithDGS(f.salary));
            event.addAvailable(f);

            return true;
        } else if ("hasOfficerForBarEvent".equals(action)) {
            if (Misc.getNumNonMercOfficers(Global.getSector().getPlayerFleet()) > 1) {
                return true;
            }
            return false;
        } else if ("hasMoreCombatSkillsThanDawn".equals(action)) {

            PersonAPI dawn = Global.getSector().getImportantPeople().getPerson("armaa_dawn");
            int playerCombatLevel = 0;

            int dawnCombatLevel = 0;
            for (SkillLevelAPI sl : dawn.getStats().getSkillsCopy()) {
                if (sl.getLevel() <= 0f) {
                    continue;
                }
                if (Skills.APT_COMBAT.equals(sl.getSkill().getGoverningAptitudeId())) {
                    dawnCombatLevel += sl.getLevel();
                }
            }
            for (SkillLevelAPI sl : Global.getSector().getPlayerStats().getSkillsCopy()) {
                if (sl.getLevel() <= 0f) {
                    continue;
                }
                if (Skills.APT_COMBAT.equals(sl.getSkill().getGoverningAptitudeId())) {
                    playerCombatLevel += sl.getLevel();
                }
            }
            Global.getLogger(this.getClass()).info(playerCombatLevel + " vs " + dawnCombatLevel);
            return playerCombatLevel > dawnCombatLevel;

        } else if ("increaseLevelCap".equals(action)) {

            PersonAPI dawn = Global.getSector().getImportantPeople().getPerson("armaa_dawn");
            dawn.getMemoryWithoutUpdate().set(MemFlags.OFFICER_MAX_LEVEL, 8);

        } else if ("guestPickSelected".equals(action)) {
            // CONDITIONS-column command: must be a pure check, no side effects.
            // It gets evaluated during rule matching, potentially more than once.
            String opt = memory.getString("$option");
            Global.getLogger(this.getClass()).info(opt);
            return opt != null && opt.startsWith(GUEST_PICK_PREFIX);
        } else if ("applyPickedGuest".equals(action)) {
            String opt = memory.getString("$option");
            if (opt == null || !opt.startsWith(GUEST_PICK_PREFIX)) {
                return false;
            }
            String pid = opt.substring(GUEST_PICK_PREFIX.length());
            return setGuestOfficerById(pid, memory);
        } else if ("populateGuestPicker".equals(action)) {
            // Gather eligible officers first so we can order them and cap the list.
            if (params.size() > 1) {
                String option = params.get(1).getString(memoryMap);
                memory.set("$armaa_dawnOption", option, 5);
            }
            List<PersonAPI> eligible = new ArrayList<>();
            for (OfficerDataAPI od : Global.getSector().getPlayerFleet().getFleetData().getOfficersCopy()) {
                PersonAPI p = od.getPerson();
                if (!isEligibleGuest(p)) {
                    continue;
                }
                // Named/persistent characters (the rangers, etc.) go to the front;
                // generated officers follow in fleet order.
                if (Global.getSector().getImportantPeople().getPerson(p.getId()) != null) {
                    eligible.add(0, p);
                } else {
                    eligible.add(p);
                }
            }
            if (eligible.isEmpty()) {
                return false; // nothing to offer; the column-defined Continue option still works
            }
            if (eligible.size() > MAX_PICKER_OPTIONS) {
                eligible = eligible.subList(0, MAX_PICKER_OPTIONS);
            }

            VisualPanelAPI vp = dialog.getVisualPanel();
            int cols = 3;
            float cardW = 92f, cardH = 92f, pad = 6f;
            int rows = (eligible.size() + cols - 1) / cols;
            float w = cols * (cardW + pad) + pad;
            float h = rows * (cardH + pad) + pad;

            CustomPanelAPI roster = vp.showCustomPanel(w, h, null);

            CustomPanelAPI rowAnchor = null;   // first card of the previous row
            CustomPanelAPI prev = null;        // card to the left
            int num = 2;
            int i = 0;
            for (PersonAPI p : eligible) {
                CustomPanelAPI card = roster.createCustomPanel(cardW, cardH, null);
                TooltipMakerAPI ct = card.createUIElement(cardW, cardH, false);
                ct.addImage(p.getPortraitSprite(), cardW, 64f, 0f);
                ct.addPara(p.getName().getFirst(), Misc.getHighlightColor(), 3f);
                card.addUIElement(ct).inTL(0f, 0f);

                PositionAPI pos = roster.addComponent(card);
                if (i % cols == 0) {
                    if (rowAnchor == null) {
                        pos.inTL(pad, pad);
                    } else {
                        pos.belowLeft(rowAnchor, pad);
                    }
                    rowAnchor = card;
                } else {
                    pos.rightOfTop(prev, pad);
                }
                prev = card;

                dialog.getOptionPanel().addOption(p.getNameString(), GUEST_PICK_PREFIX + p.getId());
                num++;
                i++;
            }
            return true;
        } else if ("getAICoreForDawnEvent".equals(action)) {
            List<FleetMemberAPI> aiShips = new ArrayList<>();

            for (FleetMemberAPI member : Global.getSector().getPlayerFleet()
                    .getFleetData().getMembersListCopy()) {
                PersonAPI captain = member.getCaptain();
                if (captain == null || captain.isDefault()) {
                    continue;
                }
                if (!captain.isAICore()) {
                    continue;
                }
                aiShips.add(member);
            }

            if (aiShips.isEmpty()) {
                return false; // no AI-core ship -> event shouldn't fire
            }

            WeightedRandomPicker<FleetMemberAPI> picker
                    = new WeightedRandomPicker<>();
            for (FleetMemberAPI ship : aiShips) {
                int level = ship.getCaptain().getStats().getLevel();
                float weight = (level + 1) * (level + 1);
                picker.add(ship, weight);
            }

            FleetMemberAPI chosen = picker.pick();
            PersonAPI core = chosen.getCaptain();

            // Store ship name and core name for the dialogue
            MemoryAPI mem = dialog.getInteractionTarget().getMemoryWithoutUpdate();
            mem.set("$armaa_dawnEventShipName", chosen.getShipName(), 5f);
            mem.set("$armaa_dawnEventCoreName", core.getNameString(), 5f);

            return true;
        }
        return false;
    }

    /**
     * important people are only eligible if they're ArmaA's own characters and
     * not Dawn herself; generated officers are eligible unless they're AI
     * cores.
     */
    private boolean isEligibleGuest(PersonAPI p) {
        if (p == null) {
            return false;
        }
        String id = p.getId();
        if (Global.getSector().getImportantPeople().getPerson(id) != null) {
            return id.contains("armaa_") && !id.contains("armaa_dawn");
        }
        return !p.isAICore();
    }

    private void applyGuest(PersonAPI p, MemoryAPI memory) {
        memory.set("$armaa_dawnGuestName", p.getName().getFirst(), 1);
        memory.set("$armaa_dawnGuest", p.getId(), 1);
        memory.set("$armaa_dawnGuestId", p.getId(), 1);
        memory.set("$armaa_dawnGuestPerson", p, 1);
        memory.set("$armaa_dawnGuestPersonality", p.getPersonalityAPI().getId(), 1);
    }

    private boolean setGuestOfficerById(String pid, MemoryAPI memory) {
        for (OfficerDataAPI od : Global.getSector().getPlayerFleet().getFleetData().getOfficersCopy()) {
            PersonAPI p = od.getPerson();
            if (!p.getId().equals(pid)) {
                continue;
            }
            if (!isEligibleGuest(p)) {
                return false;
            }
            applyGuest(p, memory);
            return true;
        }
        return false;
    }
}
