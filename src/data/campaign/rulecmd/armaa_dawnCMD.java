package data.campaign.rulecmd;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.util.Misc.Token;
import java.util.Map;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.rulecmd.missions.BarCMD;
import com.fs.starfarer.api.util.Misc;
import data.scripts.campaign.armaa_dawnListener;
import data.scripts.util.armaa_utils;
import java.util.ArrayList;
import java.util.List;
import lunalib.lunaSettings.LunaSettings;

public class armaa_dawnCMD extends BaseCommandPlugin {

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
            long timestamp = startDate == null ? (Long) Global.getSector().getMemoryWithoutUpdate().get("$armaa_dawnHireDate") : (Long) Global.getSector().getMemoryWithoutUpdate().get("$"+startDate);
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
                if (Global.getSector().getImportantPeople().getPerson(officer.getPerson().getId()) != null) {
                    //guard against getting other mod characters...?
                    if (!officer.getPerson().getId().contains("armaa_") || officer.getPerson().getId().contains("armaa_dawn")) {
                        continue;
                    }
                    people.add(officer.getPerson());
                } else {
                    if (officer.getPerson().isAICore()) {
                        continue;
                    }

                    people.add(officer.getPerson());
                }
            }
            PersonAPI randomPerson = people.get(
                    (int) (Math.random() * people.size())
            );
            memory.set("$armaa_dawnGuestName", randomPerson.getName().getFirst(), 1);
            memory.set("$armaa_dawnGuest", randomPerson.getId(), 1);
            memory.set("$armaa_dawnGuestPerson", randomPerson, 1);
            memory.set("$armaa_dawnGuestPersonality", randomPerson.getPersonalityAPI().getId(), 1);

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

        }
        else if ("initDawn".equals(action))
        {
            Global.getSector().getEconomy().getMarket("armaa_meshanii_market").getCommDirectory().getEntryForPerson(Global.getSector().getImportantPeople().getPerson("armaa_dawn")).setHidden(false);
                OfficerManagerEvent event = armaa_utils.getOfficerManagerEvent();
                PersonAPI dawn = Global.getSector().getImportantPeople().getPerson("armaa_dawn");
                OfficerManagerEvent.AvailableOfficer f = new OfficerManagerEvent.AvailableOfficer(dawn, "armaa_meshanii_market", 4000, 1000);
                dawn.getMemoryWithoutUpdate().set("$ome_hireable", true);
                dawn.getMemoryWithoutUpdate().set("$ome_eventRef", event);
                dawn.getMemoryWithoutUpdate().set("$ome_hiringBonus", Misc.getWithDGS(f.hiringBonus));
                dawn.getMemoryWithoutUpdate().set("$ome_salary", Misc.getWithDGS(f.salary));
                event.addAvailable(f);

                return true;
        }
        return false;
    }
}
