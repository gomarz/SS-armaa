package data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.IntervalUtil;

public class armaa_autoModuleRepair extends BaseHullMod {

    private IntervalUtil hullCheck = new IntervalUtil(0.5f, 1f);

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {

    }

    @Override
    public void advanceInCampaign(FleetMemberAPI member, float amount) {
        hullCheck.advance(amount);
        if (hullCheck.intervalElapsed()) {
            for (int i = 0; i < member.getVariant().getModuleSlots().size(); i++) {
                member.getStatus().setDetached(i + 1, false);
                member.getStatus().setHullFraction(i+1,1f);
                //member.getVariant().getModuleVariant("foo").
            }
        }
    }
}
