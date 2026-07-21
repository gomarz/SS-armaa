package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier;
import com.fs.starfarer.api.combat.listeners.HullDamageAboutToBeTakenListener;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import java.awt.Color;
import org.lwjgl.util.vector.Vector2f;

public class armaa_comboUnit extends BaseHullMod {

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        if (stats.getVariant().getModuleVariant("MODULE") != null) {
            ShipVariantAPI var = stats.getVariant().getModuleVariant("MODULE").clone();
            var.addPermaMod("armaa_dpReduction");
            stats.getVariant().setModuleVariant("MODULE", var);
            stats.getDynamic().getMod(Stats.DEPLOYMENT_POINTS_MOD).modifyFlat(id, var.getHullSpec().getFleetPoints());
        }

    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        if (ship.getOwner() < 0) {
            return;
        }
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

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize,
            ShipAPI ship, float width, boolean isForModSpec) {
        Color h = Misc.getHighlightColor();
        float pad = 10f;

        tooltip.addSectionHeading("Core Unit", Alignment.MID, pad);

        tooltip.addPara("This vessel operates as a paired system: a core unit integrated into a "
                + "larger frame. The core unit is any strikecraft-class hull, and determines the "
                + "combined unit's identity for reputation and progression.", pad);

        tooltip.addPara("If the frame's hull is depleted, the core unit automatically ejects."
                + "If the frame does not require the core unit to function, it will continue to fight even when manually ejecting."+"Dependent frames instead lock "
                + "controls and go inert if lost.", pad);

        tooltip.addPara("The core unit can be swapped at %s or at any player colony.", pad,
                h, "New Meshan");

        tooltip.addPara("Swapping preserves reputation history: the outgoing core inherits the "
                + "combined unit's standing, and the incoming core's standing transfers to the frame.", pad);
    }

    private class armaa_comboUnitDeathListener implements AdvanceableListener, HullDamageAboutToBeTakenListener {

        private final ShipAPI ship;
        private ShipAPI module;
        private boolean initModule = false;

        armaa_comboUnitDeathListener(ShipAPI ship) {
            this.ship = ship;
        }

        @Override
        public void advance(float f) {
            if (ship.getOwner() < 0) {
                return;
            }
            if (module == null && ship.getChildModulesCopy().size() > 0) {
                initModule = true;
                module = ship.getChildModulesCopy().get(0);
            }
            if (!initModule) {
                return;
            }
            if (!module.isAlive() || module.isHulk()) {
                return;
            }
            if (!module.hasListenerOfClass(armaa_comboUnit.armaa_comboUnitModuleListener.class)) {
                module.addListener(new armaa_comboUnitModuleListener(module));
            }
        }

        @Override
        public boolean notifyAboutToTakeHullDamage(Object param, ShipAPI ship, Vector2f point, float damageAmount) {
            float hull = ship.getHitpoints();
            if (damageAmount >= hull) {
                if (!ship.getChildModulesCopy().isEmpty()) {
                    ShipAPI module = ship.getChildModulesCopy().get(0);
                    //armaa_comboUnitControlPlugin.createShipFromModule(ship, module, Global.getCombatEngine());
                    //armaa_comboUnitControlPlugin.
                    Global.getCombatEngine().getCustomData().put("armaa_autoEject_" + ship.getId(), true);
                    ship.setHitpoints(damageAmount + 1f);
                    ship.removeListener(this);
                    return true;
                }
            }
            return false;
        }
    }

    private class armaa_comboUnitModuleListener implements AdvanceableListener, DamageTakenModifier {

        private final ShipAPI ship;

        armaa_comboUnitModuleListener(ShipAPI ship) {
            this.ship = ship;
        }

        @Override
        public void advance(float f) {
            if (!ship.isAlive() || ship.getLocation().getY() == -1000000f) {
                ShipAPI parent = ship.getParentStation();
                parent.setControlsLocked(true);
                boolean independent = ship.getVariant().hasTag("armaa_core_independent") || ship.getHullSpec().hasTag("armaa_core_independent");
                if (independent) {
                    ship.setControlsLocked(true);
                    ship.getFluxTracker().showOverloadFloatyIfNeeded("Core unit lost! Controls locked!", Color.red, 10f, true);

                }
                ship.setStationSlot(null);
                ship.removeListener(this);
            }

        }

        @Override
        public String modifyDamageTaken(Object param, CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean shieldHit) {
            ShipAPI parent = (ShipAPI) target;
            parent = parent.getParentStation();
            if (parent.getShield() != null && parent.getShield().isOn()) {
                damage.setDamage(0f);
                return "armaa_shieldprotection";

            }
            return "";
        }
    }
}
