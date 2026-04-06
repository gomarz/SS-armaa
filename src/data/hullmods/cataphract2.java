package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipEngineControllerAPI.ShipEngineAPI;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.impl.hullmods.CompromisedStructure;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;

import org.magiclib.util.MagicIncompatibleHullmods;
import java.awt.Color;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import com.fs.starfarer.api.ui.Alignment;
import data.scripts.util.armaa_utils;

public class cataphract2 extends BaseHullMod {

    private static final float PARA_PAD = 10f;
    private static final Set<String> BLOCKED_HULLMODS = new HashSet<>();
    public static final Map<String, Float> GROUND_BONUS = new HashMap<>();


    private final float EMP_RESIST = 33f;
    private final float DISABLE_RESIST = 66f;
    private static final float CR_PENALTY = 0.10f;


    public static float SMOD_BONUS = 2f;
    public static float CRUISER_DAMAGE_BONUS = 10f;
    public static float CAPITAL_DAMAGE_BONUS = 20f;
    //  S-mod no-officer bonuses 
    private static final float NO_OFFICER_DP_REDUCTION_PCT = 0.20f;  // 20%
    private static final float NO_OFFICER_DP_REDUCTION_MAX = 10f;    // cap at 10 pts
    private static final float NO_OFFICER_DMOD_CHANCE_MULT = 0.50f;  // 50% reduction
    private static final float NO_OFFICER_CR_BONUS = 0.10f;  // +10% max CR

    //  Panic burst
    private static final float PANIC_HULL_THRESHOLD = 0.35f;   // triggers at 25% hull
    private static final float PANIC_SPEED_BONUS = 25f;     // % speed boost
    private static final float PANIC_MANEUVER_BONUS = 25f;     // % turn/accel boost
    private static final float PANIC_FLUX_COST_MULT = 1.25f;   // +25% weapon flux cost
    private static final float PANIC_DURATION = 5f;      // seconds active
    private static final float BACK_OFF_DURATION = 10f;     // seconds of BACK_OFF hint

    static {
        BLOCKED_HULLMODS.add("converted_hangar");
        BLOCKED_HULLMODS.add("cargo_expansion");
        BLOCKED_HULLMODS.add("additional_crew_quarters");
        BLOCKED_HULLMODS.add("fuel_expansion");
        BLOCKED_HULLMODS.add("additional_berthing");
        BLOCKED_HULLMODS.add("auxiliary_fuel_tanks");
        BLOCKED_HULLMODS.add("expanded_cargo_holds");
        BLOCKED_HULLMODS.add("surveying_equipment");
        BLOCKED_HULLMODS.add("recovery_shuttles");
        BLOCKED_HULLMODS.add("operations_center");
        BLOCKED_HULLMODS.add("expanded_deck_crew");
        BLOCKED_HULLMODS.add("combat_docking_module");
        BLOCKED_HULLMODS.add("roider_fighterClamps");
    }

    public static float getCRPenalty(ShipVariantAPI variant) {
        float scale = 1f;
        for (String hullMod : variant.getHullMods()) {
            HullModSpecAPI modSpec = Global.getSettings().getHullModSpec(hullMod);
            if (modSpec.hasTag(Tags.HULLMOD_DMOD)) {
                scale /= CompromisedStructure.DEPLOYMENT_COST_MULT;
            }
        }
        return scale * CR_PENALTY;
    }

    public static String getNonDHullId(ShipHullSpecAPI spec) {
        if (spec == null) {
            return null;
        }
        if (spec.getDParentHullId() != null && !spec.getDParentHullId().isEmpty()) {
            return spec.getDParentHullId();
        }
        return spec.getHullId();
    }

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        // Always-on resistances
        stats.getEmpDamageTakenMult().modifyMult(id, (100f - EMP_RESIST) / 100f);
        stats.getEngineDamageTakenMult().modifyMult(id, (100f - DISABLE_RESIST) / 100f);
        stats.getWeaponDamageTakenMult().modifyMult(id, (100f - DISABLE_RESIST) / 100f);

        boolean sMod = stats.getVariant().getSModdedBuiltIns().contains("cataphract2") || isSMod(stats);
        if (!sMod) {
            return;
        }
        if (stats.getFleetMember() == null) {
            return;
        }

        boolean hasOfficer = !stats.getFleetMember().getCaptain().isDefault();

        if (hasOfficer) {

            float level = Math.min(15, stats.getFleetMember().getCaptain().getStats().getLevel());
            float capitalBonus = (level / 15f) * CAPITAL_DAMAGE_BONUS;   // scales 0-20% with level
            float cruiserBonus = (level / 15f) * CRUISER_DAMAGE_BONUS;   // scales 0-10% with level            
            int dpCost = (int) stats.getFleetMember().getDeploymentPointsCost();

            stats.getDamageToCapital().modifyPercent(id, capitalBonus);
            stats.getDamageToCruisers().modifyPercent(id, cruiserBonus);
            stats.getFluxDissipation().modifyMult(id, 1f + SMOD_BONUS * (level * 0.01f));
            stats.getAutofireAimAccuracy().modifyMult(id, SMOD_BONUS * (level * 0.01f));
            stats.getMaxTurnRate().modifyMult(id, 1f + SMOD_BONUS * (level * 0.01f));
            stats.getTurnAcceleration().modifyMult(id, 1f + SMOD_BONUS * (level * 0.01f));
            stats.getDynamic().getMod(Stats.DEPLOYMENT_POINTS_MOD)
                    .modifyFlat(id, dpCost * (SMOD_BONUS * (level * 0.01f)));

        } else {

            // DP reduction: 20% or 10 points, whichever is less
            float baseCost = stats.getSuppliesToRecover().getBaseValue();
            float reduction = Math.min(NO_OFFICER_DP_REDUCTION_MAX, baseCost * NO_OFFICER_DP_REDUCTION_PCT);
            stats.getDynamic().getMod(Stats.DEPLOYMENT_POINTS_MOD).modifyFlat(id, -reduction);

            // D-mod chance reduction: -50%
            stats.getDynamic().getMod(Stats.DMOD_ACQUIRE_PROB_MOD)
                    .modifyMult(id, NO_OFFICER_DMOD_CHANCE_MULT);

            // +10% max combat readiness
            stats.getMaxCombatReadiness().modifyFlat(id, NO_OFFICER_CR_BONUS, "Combat Customization");
        }
    }


    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        // Remove incompatible hullmods
        for (String tmp : BLOCKED_HULLMODS) {
            if (ship.getVariant().getNonBuiltInHullmods().contains(tmp)) {
                MagicIncompatibleHullmods.removeHullmodWithWarning(ship.getVariant(), tmp, "cataphract");
            }
        }

        // Attach panic-burst listener whenever the s-mod is active
        boolean sMod = ship.getVariant().getSModdedBuiltIns().contains("cataphract2")
                || isSMod(ship.getMutableStats());
        if (sMod && !ship.hasListenerOfClass(PanicBurstListener.class)) {
            ship.addListener(new PanicBurstListener(ship));
        }
    }

    @Override
    public void advanceInCampaign(FleetMemberAPI member, float amount) {
        if (member.getFleetData() != null
                && member.getFleetData().getFleet() != null
                && member.getFleetData().getFleet().getViewForMember(member) != null) {
            member.getFleetData().getFleet().getViewForMember(member)
                    .getContrailColor().setBase(new Color(0f, 0f, 0f, 0f));
            member.getFleetData().getFleet().getViewForMember(member)
                    .getEngineColor().setBase(new Color(0f, 0f, 0f, 0f));
            member.setSpriteOverride("");
        }
        // we track the ground bonuses in data.scripts.campaign.armaa_cataphractGBListener
    }

    public static class PanicBurstListener implements AdvanceableListener {

        private final ShipAPI ship;
        private final String modId;

        private boolean triggered = false;   // fired this combat?
        private boolean burstActive = false;
        private boolean backOffActive = false;
        private float burstTimer = 0f;
        private float backOffTimer = 0f;

        public PanicBurstListener(ShipAPI ship) {
            this.ship = ship;
            this.modId = "armaa_panicburst_" + ship.getId();
        }

        @Override
        public void advance(float amount) {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null || ship == null || !ship.isAlive() || ship.isHulk()) {
                return;
            }

            MutableShipStatsAPI stats = ship.getMutableStats();


            if (burstActive) {
                burstTimer -= amount;
                armaa_utils.makeAfterImages(ship, 0.35f, Global.getCombatEngine().getElapsedInLastFrame()/ engine.getTimeMult().getModifiedValue(), new Color(100, 200, 255, 185)); // cyan

                if (burstTimer <= 0f) {
                    endBurst(stats);
                }
            }


            if (backOffActive) {
                backOffTimer -= amount;
                if (backOffTimer <= 0f) {
                    backOffActive = false;
                }
            }


            if (triggered && ship.getHullLevel() > PANIC_HULL_THRESHOLD) {
                triggered = false;
            }


            if (!triggered && !burstActive && ship.getHullLevel() <= PANIC_HULL_THRESHOLD) {
                triggered = true;
                startBurst(stats, engine);
            }

            // ── Player HUD status ─────────────────────────────────────────────
            if (burstActive && engine.getPlayerShip() == ship) {
                engine.maintainStatusForPlayerShip(
                        modId,
                        "graphics/icons/tactical/engine_boost2.png",
                        "Combat Customization",
                        "Emergency Egress: " + (int) Math.ceil(burstTimer) + "s",
                        false
                );
            }
        }

        private void startBurst(MutableShipStatsAPI stats, CombatEngineAPI engine) {
            burstActive = true;
            burstTimer = PANIC_DURATION;
            backOffActive = true;
            backOffTimer = BACK_OFF_DURATION;

            // Mobility boost
            stats.getMaxSpeed().modifyPercent(modId, PANIC_SPEED_BONUS);
            stats.getMaxTurnRate().modifyPercent(modId, PANIC_MANEUVER_BONUS);
            stats.getTurnAcceleration().modifyPercent(modId, PANIC_MANEUVER_BONUS);
            stats.getAcceleration().modifyPercent(modId, PANIC_MANEUVER_BONUS);
            stats.getDeceleration().modifyPercent(modId, PANIC_MANEUVER_BONUS);

            // Weapon flux cost increase (discourage sticking to fight)
            stats.getBallisticWeaponFluxCostMod().modifyMult(modId, PANIC_FLUX_COST_MULT);
            stats.getEnergyWeaponFluxCostMod().modifyMult(modId, PANIC_FLUX_COST_MULT);

            // AI back-off hint
            if (ship.getOwner() != 0 || engine.getPlayerShip() != ship) {
                ship.getAIFlags().setFlag(AIFlags.BACK_OFF,
                        BACK_OFF_DURATION);
            }

            for (ShipEngineAPI sEngine : ship.getEngineController().getShipEngines()) {
                if (sEngine.isDisabled()) {
                    sEngine.repair();
                }
            }

            // Screen flash for player
            if (engine.getPlayerShip() == ship) {
                engine.addFloatingText(ship.getLocation(),
                        "Emergency Egress!", 24f, Color.CYAN, ship, 1f, 2f);
                // Replace with your preferred screen effect hook here, e.g.:
                // Global.getSoundPlayer().playUISound("armaa_egress_sound", 1f, 1f);
            }
        }

        private void endBurst(MutableShipStatsAPI stats) {
            burstActive = false;
            stats.getMaxSpeed().unmodify(modId);
            stats.getMaxTurnRate().unmodify(modId);
            stats.getTurnAcceleration().unmodify(modId);
            stats.getAcceleration().unmodify(modId);
            stats.getDeceleration().unmodify(modId);
            stats.getBallisticWeaponFluxCostMod().unmodify(modId);
            stats.getEnergyWeaponFluxCostMod().unmodify(modId);
        }
    }

    // ── Tooltip / description ─────────────────────────────────────────────────
    @Override
    public boolean isSModEffectAPenalty() {
        return false;
    }

    public String getSModDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) {
            return "" + (int) SMOD_BONUS + "%";
        }
        return null;
    }

    private final Color E = Global.getSettings().getColor("textEnemyColor");
    private final Color dark = Global.getSettings().getColor("textGrayColor");
    private final Color flavor = new Color(110, 110, 110, 255);

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize,
            ShipAPI ship, float width, boolean isForModSpec) {
        float pad = 10f;
        float padS = 2f;
        float level = 0f;
        if (ship != null) {
            level = ship.getCaptain().isDefault() ? 0f : ship.getCaptain().getStats().getLevel();
        }

        float n2 = ship.getFleetMember() != null ? ship.getFleetMember().getDeploymentPointsCost() : 15f;
        if (ship != null && GROUND_BONUS.get(ship.getHullSpec().getHullId()) != null) {
            n2 = GROUND_BONUS.get(ship.getHullSpec().getHullId());
        }
        int n = (int) (n2 * Math.max(1, level));

        tooltip.addSectionHeading("Details", Alignment.MID, 10);
        tooltip.addPara("%s EMP Resistance increased by %s.", pad,
                Misc.getHighlightColor(), "\u2022", (int) EMP_RESIST + "%");
        tooltip.addPara("%s Weapons and engines are %s less likely to be disabled.", padS,
                Misc.getHighlightColor(), "\u2022", (int) DISABLE_RESIST + "%");
        tooltip.addPara("%s While marines are present, increases effective strength of ground ops by Level*Deploy Cost: %s.", padS,
                Misc.getHighlightColor(), "\u2022", Integer.toString(n));
        if (ship != null) {
            tooltip.addPara("%s Ground deployments consume %s CR.", padS,
                    Misc.getHighlightColor(), "\u2022",
                    (int) Math.round(getCRPenalty(ship.getVariant()) * 100f) + "%");
        }
        tooltip.addPara("%s Large modifications cannot be made, %s.", padS,
                Misc.getHighlightColor(), "\u2022", "precluding the addition of several hullmods");
        tooltip.addPara("%s", 6f, flavor, new String[]{
            "\"Morning! How's it feel to be strapped to a walking metal coffin at six AM? "
            + "Bet you wish you studied harder at the academy! Hit the throttle. "
            + "We're going to be killing people before breakfast today.\""}).italicize();
        tooltip.addPara("%s", 1f, flavor, new String[]{"         \u2014 Savarin Johnson, c. 192"});

        // Incompatibilities
        tooltip.addSectionHeading("Incompatibilities", Alignment.MID, 10);
        String str = "";
        int size = BLOCKED_HULLMODS.size();
        int counter = 0;
        Color[] arr2 = {Misc.getHighlightColor(), E};
        for (HullModSpecAPI spec : Global.getSettings().getAllHullModSpecs()) {
            for (String tmp : BLOCKED_HULLMODS) {
                if (spec.getId().equals(tmp)) {
                    counter++;
                    str += spec.getDisplayName();
                    if (counter != size - 1) {
                        str += ", ";
                    }
                }
            }
        }
        if (ship == null) {
            return;
        }
        str = str.substring(0, str.length() - 2);
        tooltip.addPara("%s Incompatible with %s.", pad, arr2, "\u2022", "" + str);

        if (getCRPenalty(ship.getVariant()) > CR_PENALTY) {
            float penaltyScalePct = 100f * ((getCRPenalty(ship.getVariant()) / CR_PENALTY) - 1f);
            tooltip.addPara("CR Deployment cost is penalized by %s due to hull defects.",
                    PARA_PAD, Misc.getNegativeHighlightColor(),
                    "" + Math.round(penaltyScalePct) + "%");
        }

        float CR = ship.getCurrentCR();
        if (ship.getFleetMember() != null) {
            CR = ship.getFleetMember().getRepairTracker().getBaseCR();
        }
        if (CR < getCRPenalty(ship.getVariant())) {
            tooltip.addPara("Insufficient CR for deployment!", Misc.getNegativeHighlightColor(), PARA_PAD);
        }
    }

    @Override
    public void addSModEffectSection(TooltipMakerAPI tooltip, HullSize hullSize,
            ShipAPI ship, float width,
            boolean isForModSpec, boolean isForBuildInList) {
        float pad = 10f;
        Color color = flavor;
        boolean sMod = ship != null && (ship.getVariant().getSModdedBuiltIns().contains("cataphract2")
                || isSMod(ship.getMutableStats()));
        if (sMod) {
            color = Misc.getStoryBrightColor();
        }

        float level = 0f;
        boolean hasOfficer = false;
        if (ship != null) {
            hasOfficer = !ship.getCaptain().isDefault();
            level = hasOfficer ? ship.getCaptain().getStats().getLevel() : 0f;
        }

        float value = SMOD_BONUS;

        // Always-active s-mod effect (panic burst)
        tooltip.addPara("%s When hull drops below %s, gain +%s speed and maneuverability"
                + " for %s. Weapon flux cost is increased by %s.",
                pad, color, Misc.getHighlightColor(),
                "\u2022",
                (int) (PANIC_HULL_THRESHOLD * 100f) + "%",
                (int) PANIC_SPEED_BONUS + "%",
                (int) PANIC_DURATION + "s",
                (int) ((PANIC_FLUX_COST_MULT - 1f) * 100f) + "%");
        tooltip.addSectionHeading("Pilot Synergy", Misc.getStoryBrightColor(), Misc.getStoryDarkColor(), Alignment.MID, 10);
        tooltip.addPara("Assigning an officer increases combat performance, scaling with pilot level, at the cost of higher deployment points. Without an officer, deployment costs are reduced and the unit is easier to maintain.",
                pad, color);
        if (hasOfficer || ship == null) {
            // Officer bonuses
            tooltip.addPara("%s Officer assigned: Increases flux dissipation, turning rate, and autofire accuracy"
                    + " by %s per pilot level. Current increase: %s.",
                    pad, color, Misc.getHighlightColor(),
                    "\u2022", (int) value + "%", (int) (value * level) + "%");
            tooltip.addPara("%s Increases damage dealt to cruisers by up to %s and capitals by up to %s, scaling with pilot level. Current bonus: %s / %s.",
                    pad, color, Misc.getHighlightColor(),
                    "\u2022", "10%", "20%",
                    (int) ((level / 15f) * 10f) + "%",
                    (int) ((level / 15f) * 20f) + "%");
            tooltip.addPara("%s Increases deployment cost by %s.",
                    pad, color, Misc.getHighlightColor(),
                    "\u2022", (int) (value * level) + "%");
        } else {
            // No-officer bonuses
            tooltip.addPara("%s No assigned officer: Deployment point cost reduced by %s"
                    + " (up to %s points).",
                    pad, color, Misc.getHighlightColor(),
                    "\u2022",
                    (int) (NO_OFFICER_DP_REDUCTION_PCT * 100f) + "%",
                    (int) NO_OFFICER_DP_REDUCTION_MAX + "");
            tooltip.addPara("%s %s reduced chance of acquiring D-mods when recovered.",
                    pad, color, Misc.getHighlightColor(),
                    "\u2022", (int) (NO_OFFICER_DMOD_CHANCE_MULT * 100f) + "%");
            tooltip.addPara("%s +%s maximum combat readiness.",
                    pad, color, Misc.getHighlightColor(),
                    "\u2022", (int) (NO_OFFICER_CR_BONUS * 100f) + "%");
        }
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return false;
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) {
            return "A";
        }
        if (index == 1) {
            return "B";
        }
        if (index == 2) {
            return "C";
        }
        if (index == 3) {
            return "D";
        }
        return null;
    }
}
