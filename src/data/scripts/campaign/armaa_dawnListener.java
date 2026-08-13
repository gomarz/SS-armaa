package data.scripts.campaign;

import com.fs.starfarer.api.campaign.BaseCampaignEventListenerAndScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PersonImportance;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.ReputationActionResponsePlugin.ReputationAdjustmentResult;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyInteractionListener;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.CustomRepImpact;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.intel.contacts.ContactIntel;
import data.scripts.util.armaa_utils;

public class armaa_dawnListener extends BaseCampaignEventListenerAndScript implements ColonyInteractionListener {

    public armaa_dawnListener() {
        //this.days = Global.getSector().getClock().getTimestamp();

    }

    @Override
    public void reportEconomyTick(int iterindex) {
        long timestamp = (Long) Global.getSector().getMemoryWithoutUpdate().get("$armaa_dawnHireDate");
        Global.getSector().getMemoryWithoutUpdate().set("$armaa_dawnElapsedDays", Global.getSector().getClock().getElapsedDaysSince(timestamp));
        PersonAPI dawn = Global.getSector().getImportantPeople().getPerson("armaa_dawn");
        ContactIntel existing = ContactIntel.getContactIntel(dawn);          // null if none

        if (existing == null) {
            ContactIntel intel = new ContactIntel(dawn, Global.getSector().getEconomy().getMarket("armaa_meshanii_market"));
            Global.getSector().getIntelManager().addIntel(intel, false);  // true = no notification ping
            intel.develop(null);  // → NON_PRIORITY state, adds her to comm directory, market people list, and gives her a BaseMissionHub
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
    public void reportPlayerOpenedMarket(MarketAPI market) {
        if (market.isHidden()) {
            return;
        }

        if (!market.hasSpaceport()) {
            return;
        }
        boolean notHostile = true;
        if (market.getFaction().isHostileTo(Global.getSector().getPlayerFaction())) {
            notHostile = false;
        }
        PersonAPI dawn = Global.getSector().getImportantPeople().getPerson("armaa_dawn");
        if (dawn == null) {
            return;
        }
        if (armaa_utils.hasActiveMissionsFrom(dawn)) {
            return;
        }
        ContactIntel intel = ContactIntel.getContactIntel(dawn);
        if (intel == null || market == dawn.getMarket()) {
            return;
        }
        if (Global.getSector().getPlayerFleet().getFleetData().getOfficerData(dawn) == null) {
            return;
        }
        PersonImportance imp = dawn.getImportance();
        intel.relocateToMarket(market, notHostile);
        dawn.setImportance(imp);
        Global.getSector().getMemoryWithoutUpdate().set("$armaa_dawnLastMarket", market.getId());
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
