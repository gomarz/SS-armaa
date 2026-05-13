package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.combat.listeners.HullDamageAboutToBeTakenListener;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import data.scripts.plugins.armaa_comboUnitControlPlugin;
import org.lwjgl.util.vector.Vector2f;

public class armaa_comboUnit extends BaseHullMod 
{
    
    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        if(stats.getVariant().getModuleVariant("MODULE") != null)
        {
            ShipVariantAPI var = stats.getVariant().getModuleVariant("MODULE").clone();
            var.addPermaMod("armaa_dpReduction");
            stats.getVariant().setModuleVariant("MODULE", var);
            stats.getDynamic().getMod(Stats.DEPLOYMENT_POINTS_MOD).modifyFlat(id, var.getHullSpec().getFleetPoints());
        }
        
    }
    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        if (!ship.hasListenerOfClass(armaa_comboUnit.armaa_comboUnitDeathListener.class)) {
            ship.addListener(new armaa_comboUnitDeathListener(ship));
        }
    }
    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) {
            return "X";
        }
        return null;
    }
    private class armaa_comboUnitDeathListener implements AdvanceableListener, HullDamageAboutToBeTakenListener {

        private final ShipAPI ship;

        armaa_comboUnitDeathListener(ShipAPI ship) {
            this.ship = ship;
        }

        @Override
        public void advance(float f) {

        }

        @Override
        public boolean notifyAboutToTakeHullDamage(Object param, ShipAPI ship, Vector2f point, float damageAmount) {
            float hull = ship.getHitpoints();
            if (damageAmount >= hull) {
                ShipAPI module = ship.getChildModulesCopy().get(0);
                //armaa_comboUnitControlPlugin.createShipFromModule(ship, module, Global.getCombatEngine());
                //armaa_comboUnitControlPlugin.
                Global.getCombatEngine().getCustomData().put("armaa_autoEject_"+ship.getId(), true);
                ship.setHitpoints(damageAmount+1f);
                ship.removeListener(this);
                return true;
            }
            return false;
        }
    }
}
