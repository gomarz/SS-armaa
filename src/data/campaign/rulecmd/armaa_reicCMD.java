package data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.util.Misc.Token;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import java.util.Map;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.FullName.Gender;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import data.scripts.campaign.armaa_lingfengListener;
import java.util.List;

public class armaa_reicCMD extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        String action = params.get(0).getString(memoryMap);
        MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
        if (memory == null) {
            return false; // should not be possible unless there are other big problems already
        }
        if ("nameFelixNGira".equals(action)) {
            FullName felixName = new FullName("Felix", "Felner", Gender.MALE);
            Global.getSector().getImportantPeople().getPerson("armaa_felix").setName(felixName);
            FullName giraName = new FullName("Gira", "Loma Prieta", Gender.FEMALE);
            Global.getSector().getImportantPeople().getPerson("armaa_gira").setName(giraName);
            //memory.set($hireCost);
            return true;
        } else if ("addFelixNGira".equals(action)) {
            PersonAPI felix = Global.getSector().getImportantPeople().getPerson("armaa_felix");
            PersonAPI gira = Global.getSector().getImportantPeople().getPerson("armaa_gira");
            Misc.setMercenary(gira, true);
            Misc.setMercenary(felix, true);
            Misc.setMercHiredNow(gira);
            Misc.setMercHiredNow(felix);
            FleetDataAPI playerFleet = Global.getSector().getPlayerFleet().getFleetData();
            FleetMemberAPI fm = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "armaa_ashura_frig_standard");
            fm.setId("armaa_gira");
            Global.getSector().getPlayerFleet().getFleetData().addOfficer(felix);
            Global.getSector().getPlayerFleet().getFleetData().addOfficer(gira);
            fm.setCaptain(gira);
            playerFleet.addFleetMember(fm);
            fm = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "armaa_kouto_frig_standard");
            fm.setCaptain(felix);
            fm.setId("armaa_felix");
            playerFleet.addFleetMember(fm);
            Misc.setUnremovable(gira, true);
            Misc.setUnremovable(felix, true);

            return true;
        } else if ("removeFelixNGira".equals(action)) {
            PersonAPI felix = Global.getSector().getImportantPeople().getPerson("armaa_felix");
            PersonAPI gira = Global.getSector().getImportantPeople().getPerson("armaa_gira");
            for (FleetMemberAPI member : Global.getSector().getPlayerFleet().getMembersWithFightersCopy()) {
                if (member.getCaptain() == felix || member.getCaptain() == gira) {
                    Global.getSector().getPlayerFleet().getFleetData().removeFleetMember(member);
                }
            }
            Global.getSector().getPlayerFleet().getFleetData().removeOfficer(felix);
            Global.getSector().getPlayerFleet().getFleetData().removeOfficer(gira);
            return true;
        } else if ("CheckMercStatus".equals(action)) {
            boolean hasGira = false;
            boolean hasFelix = false;
            for (OfficerDataAPI officer : Global.getSector().getPlayerFleet().getFleetData().getOfficersCopy()) {
                if (officer.getPerson().getId().equals("armaa_gira")) {
                    hasGira = true;
                } else if (officer.getPerson().getId().equals("armaa_felix")) {
                    hasFelix = true;
                }

            }
            if (!hasFelix && hasGira) {
                memory.set("$armaa_reicOnlyGira", true, 1);
            } else if (hasFelix && !hasGira) {
                memory.set("$armaa_reicOnlyFelix", true, 1);
            }
            return true;
        } else if ("getRaidDiffREIC".equals(action)) {

            if (params.size() > 1) {
                String initStr = params.get(1).getString(memoryMap);
                int str = Global.getSector().getMemoryWithoutUpdate().getInt("$armaa_reic_heat");
                int finalValue = str * Integer.valueOf(initStr);
                memory.set("$armaa_reicRaidDif", finalValue);

            }

        } else if ("setQMMood".equals(action)) {

            if (memory.contains("$armaa_reicQMMood")) {
                return true;   // one-time
            }
            WeightedRandomPicker<String> p = new WeightedRandomPicker<>();
            p.add("friendly", 25f);
            p.add("sus", 40f);
            p.add("broke", 35f);
            memory.set("$armaa_reicQMMood", p.pick());
            return true;
        } else if ("addTracker".equals(action)) {
            Global.getSector().addListener(new armaa_lingfengListener());
            return true;
        }
        return false;
    }
}
