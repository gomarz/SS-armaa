package data.scripts.campaign;

import com.fs.starfarer.api.campaign.BaseCampaignEventListenerAndScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.ReputationActionResponsePlugin.ReputationAdjustmentResult;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.CustomRepImpact;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.intel.contacts.ContactIntel;

public class armaa_dawnListener extends BaseCampaignEventListenerAndScript {

    private static float REP_CAP_1 = 0.30f;

    public armaa_dawnListener() {
        //this.days = Global.getSector().getClock().getTimestamp();

    }

    @Override
    public void reportEconomyTick(int iterindex) {
        long timestamp = (Long) Global.getSector().getMemoryWithoutUpdate().get("$armaa_dawnHireDate");
        Global.getSector().getMemoryWithoutUpdate().set("$armaa_dawnElapsedDays", Global.getSector().getClock().getElapsedDaysSince(timestamp));
        for (IntelInfoPlugin curr : Global.getSector().getIntelManager().getIntel(ContactIntel.class)) {
            ContactIntel intel = (ContactIntel) curr;
            if (intel.isEnding() || intel.isEnded() || intel.getState() == ContactIntel.ContactState.POTENTIAL) {
                continue;
            }

            if (intel.getPerson().getId().equals("armaa_dawn")) {
                intel.loseContact(null);
            }
        }
        boolean feltOutgrown = Global.getSector().getPlayerMemoryWithoutUpdate().contains("$dawnConfidedOutpaced");

        if (Global.getSector().getPlayerMemoryWithoutUpdate().contains("$metDawnBar2Q3") && !feltOutgrown) {
            Global.getSector().getPlayerMemoryWithoutUpdate().set("$dawnOutpaced", true);
            //Global.getLogger(this.getClass()).info("DawnOutpaced check: bar2q3="
            //       + Global.getSector().getPlayerMemoryWithoutUpdate().contains("$metDawnBar2Q3")
            //       + "mem" + Global.getSector().getPlayerMemoryWithoutUpdate().toString());
            /*
            int highestLevel = 0;
            int numSuperiorOfficers = 0;
            int dawnLevel = Global.getSector().getImportantPeople().getPerson("armaa_dawn").getStats().getLevel();
            if(Global.getSector().getPlayerStats().getLevel() <= dawnLevel) 
                return;
            for (OfficerDataAPI officer : Global.getSector().getPlayerFleet().getFleetData().getOfficersCopy()) {
                if (officer.getPerson().getId().equals("armaa_dawn")) {
                    continue;
                }
                if (officer.getPerson().getStats().getLevel() > dawnLevel)
                    numSuperiorOfficers++;
                if (officer.getPerson().getStats().getLevel() > highestLevel) {
                    highestLevel = officer.getPerson().getStats().getLevel();
                }
            }
            if(numSuperiorOfficers >= 2)
            {
                // set some flag here to trigger the event
                
            }*/
        }
    }

    @Override
    public void reportEconomyMonthEnd() {
        boolean hasDawn = Global.getSector().getImportantPeople().getPerson("armaa_dawn").getFleet() != null && Global.getSector().getImportantPeople().getPerson("armaa_dawn").getFleet() == Global.getSector().getPlayerFleet();
        if (!hasDawn) {
            return;
        }
        PersonAPI person = Global.getSector().getImportantPeople().getPerson("armaa_dawn");
        CustomRepImpact impact = new CustomRepImpact();
        impact.limit = RepLevel.FAVORABLE;
        impact.delta = 1 * 0.01f;
        ReputationAdjustmentResult result = Global.getSector().adjustPlayerReputation(
                new RepActionEnvelope(RepActions.CUSTOM, impact,
                        null, null, false, true), person);
        //return result.delta != 0;

        //Global.getSector().removeListener(this);
    }

    @Override
    public void reportPlayerEngagement(EngagementResultAPI result) {
        boolean noticedAI = Global.getSector().getPlayerMemoryWithoutUpdate().contains("$dawnNoticedAI");
        if (!noticedAI) {
            for (FleetMemberAPI member : result.getBattle().getPlayerCombined().getFleetData().getMembersListCopy()) {
                if (member.getCaptain() != null && member.getCaptain().isAICore()) {
                    Global.getSector().getPlayerMemoryWithoutUpdate().set("$dawnNoticedAI", true);
                }
            }
        }
    }
}
