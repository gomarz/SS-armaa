package data.scripts.campaign;

import com.fs.starfarer.api.campaign.BaseCampaignEventListenerAndScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.ReputationActionResponsePlugin.ReputationAdjustmentResult;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.characters.PersonAPI;
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
    public void reportEconomyTick(int iterindex)
    {
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
    }

    @Override
    public void reportEconomyMonthEnd() {
        boolean hasDawn = Global.getSector().getImportantPeople().getPerson("armaa_dawn").getFleet() != null && Global.getSector().getImportantPeople().getPerson("armaa_dawn").getFleet() == Global.getSector().getPlayerFleet();
        if(!hasDawn)
            return;
        PersonAPI person = Global.getSector().getImportantPeople().getPerson("armaa_dawn");
        CustomRepImpact impact = new CustomRepImpact();
        impact.limit = RepLevel.WELCOMING;
        impact.delta = 1 * 0.01f;
        ReputationAdjustmentResult result = Global.getSector().adjustPlayerReputation(
                new RepActionEnvelope(RepActions.CUSTOM, impact,
                        null, null, false, true), person);
        //return result.delta != 0;

        //Global.getSector().removeListener(this);
    }
}
