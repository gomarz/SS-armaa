package data.scripts.campaign;

import com.fs.starfarer.api.campaign.BaseCampaignEventListenerAndScript;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.util.IntervalUtil;

public class armaa_hyperSpaceImmunity extends BaseCampaignEventListenerAndScript {
    //Logger log = Logger.getLogger(armaa_hyperSpaceImmunity.class.getName());

    private IntervalUtil interval = new IntervalUtil(1f, 1f);

    @Override
    public boolean runWhilePaused() {
        return true;
    }

    @Override
    public void advance(float amount) {
        if (1==1) {
            return;
        }
        if (Global.getSector() != null && Global.getSector().getCampaignUI() != null && Global.getSector().getCampaignUI().getCurrentInteractionDialog() != null) {
            if(Global.getSector().getCampaignUI().getCurrentInteractionDialog() != null)
            if (Global.getSector().getCampaignUI().getCurrentInteractionDialog().getInteractionTarget() instanceof CampaignFleetAPI) {
                if (Global.getSector().getPlayerFleet() != null && Global.getSector().getPlayerFleet().getFleetData() != null) {
                    FleetDataAPI fleet = Global.getSector().getPlayerFleet().getFleetData();

                    for (FleetMemberAPI ship : fleet.getMembersInPriorityOrder()) {
                        if (!ship.getVariant().hasHullMod("strikeCraft")) {
                            continue;
                        }
                        Global.getLogger(this.getClass()).info("In dialog, unapplying corona effect. Interaction Target: "+ Global.getSector().getCampaignUI().getCurrentInteractionDialog().getInteractionTarget().getFullName() );
                        ship.getStats().getDynamic().getStat(Stats.CORONA_EFFECT_MULT).unmodify("armaa_carrierStorageHyper");
                    }
                    return;
                }
            }
        }
        //if(Globa.)
        interval.advance(amount);
        if (interval.intervalElapsed()) {
            if (Global.getSector().getPlayerFleet() != null && Global.getSector().getPlayerFleet().getFleetData() != null) {
                FleetDataAPI fleet = Global.getSector().getPlayerFleet().getFleetData();

                for (FleetMemberAPI ship : fleet.getMembersInPriorityOrder()) {
                    if (!ship.getVariant().hasHullMod("strikeCraft")) {
                        continue;
                    }
                    ship.getStats().getDynamic().getStat(Stats.CORONA_EFFECT_MULT).modifyMult("armaa_carrierStorageHyper", 0.0f);
                }

            }
        }
    }
}
