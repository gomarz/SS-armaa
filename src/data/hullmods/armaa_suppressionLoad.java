package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipVariantAPI;

import java.util.List;
import java.util.Map;

public class armaa_suppressionLoad extends BaseHullMod 
{

    /** Shield efficiency multiplier applied when a PHASE-defense core is docked. */
    public static final float SHIELD_DAMAGE_TAKEN_MULT = 1.3f;

    private static final String STAT_ID = "armaa_discordantScreen";
    private static final String DATA_KEY = "armaa_discordantScreen_applied";

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        if (stats == null) return;
        if (!variantHasPhaseCore(stats.getVariant())) return;
        applyPenalty(stats);
    }

    // ------------------------------------------------------------------
    // Combat path. Reconciles each frame against the *live* module list, so
    // runtime core swaps (armaa_CoreUnitPickerCMD) are handled correctly and
    // the penalty lifts if the core is destroyed or ejected while the shell
    // is still alive.
    // ------------------------------------------------------------------

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null || ship.getMutableStats() == null) return;

        boolean shouldApply = shipHasPhaseCore(ship);

        Object cached = ship.getCustomData().get(DATA_KEY);
        boolean currentlyApplied = (cached instanceof Boolean) && (Boolean) cached;

        // Dirty check -- avoid churning the stat mod every frame.
        if (shouldApply == currentlyApplied) return;

        MutableShipStatsAPI stats = ship.getMutableStats();
        if (shouldApply) {
            applyPenalty(stats);
        } else {
            removePenalty(stats);
        }
        ship.setCustomData(DATA_KEY, shouldApply);
    }

    private void applyPenalty(MutableShipStatsAPI stats) {
        stats.getShieldDamageTakenMult().modifyMult(STAT_ID, SHIELD_DAMAGE_TAKEN_MULT);

    }

    private void removePenalty(MutableShipStatsAPI stats) {
        stats.getShieldDamageTakenMult().unmodifyMult(STAT_ID);
    }

    // ------------------------------------------------------------------
    // Detection
    // ------------------------------------------------------------------

    /** Live combat check: scan attached child modules. */
    public static boolean shipHasPhaseCore(ShipAPI ship) {
        if (ship == null) return false;
        List<ShipAPI> modules = ship.getChildModulesCopy();
        if (modules == null || modules.isEmpty()) return false;

        for (ShipAPI module : modules) {
            if (module == null || !module.isAlive()) continue;
            if (module.getHullSpec() == null) continue;
            if(module.isLiftingOff()) continue;
            if (module.getHullSpec().getDefenseType() == ShieldAPI.ShieldType.PHASE) {
                return true;
            }
        }
        return false;
    }

    /** Refit / non-combat check: inspect the variant's module slots. */
    public static boolean variantHasPhaseCore(ShipVariantAPI variant) {
        if (variant == null) return false;

        List<String> slots = variant.getModuleSlots();
        if (slots != null) {
            for (String slotId : slots) {
                ShipVariantAPI moduleVariant = variant.getModuleVariant(slotId);
                if (isPhaseVariant(moduleVariant)) return true;
            }
        }

        // Fallback for variants where module variants are stored by id only.
        Map<String, String> stationModules = variant.getStationModules();
        if (stationModules != null) {
            for (String variantId : stationModules.values()) {
                if (variantId == null) continue;
                ShipVariantAPI moduleVariant = Global.getSettings().getVariant(variantId);
                if (isPhaseVariant(moduleVariant)) return true;
            }
        }

        return false;
    }

    private static boolean isPhaseVariant(ShipVariantAPI variant) {
        return variant != null
                && variant.getHullSpec() != null
                && variant.getHullSpec().getDefenseType() == ShieldAPI.ShieldType.PHASE;
    }

    // ------------------------------------------------------------------
    // Tooltip
    // ------------------------------------------------------------------

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
                if (index == 1) return "PHASE";
        if (index == 0) return Math.round((SHIELD_DAMAGE_TAKEN_MULT - 1f) * 100f) + "%";
        return null;
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return true;
    }

    @Override
    public boolean affectsOPCosts() {
        return false;
    }
}