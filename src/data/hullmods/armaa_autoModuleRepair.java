package data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.util.IntervalUtil;

public class armaa_autoModuleRepair extends BaseHullMod {

    private IntervalUtil hullCheck = new IntervalUtil(0.5f, 1f);

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        for (WeaponSlotAPI slot : stats.getVariant().getHullSpec().getAllWeaponSlotsCopy()) {
            if (stats.getVariant().getModuleVariant(slot.getId()) != null) {
                ShipVariantAPI var = stats.getVariant().getModuleVariant(slot.getId()).clone();
                var.addPermaMod("armaa_dpReduction");
                stats.getVariant().setModuleVariant(slot.getId(), var);
                stats.getDynamic().getMod(Stats.DEPLOYMENT_POINTS_MOD).modifyFlat(id, var.getHullSpec().getFleetPoints());
            }
        }
    }

    @Override
    public void advanceInCampaign(FleetMemberAPI member, float amount) {
        hullCheck.advance(amount);
        if (hullCheck.intervalElapsed()) {
            for (int i = 0; i < member.getVariant().getModuleSlots().size(); i++) {
                member.getStatus().setDetached(i + 1, false);
                member.getStatus().setHullFraction(i + 1, 1f);
                //member.getVariant().getModuleVariant("foo").
            }
        }
    }
}
