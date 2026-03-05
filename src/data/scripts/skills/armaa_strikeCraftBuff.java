package data.scripts.skills;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.skills.*;
import com.fs.starfarer.api.characters.FleetTotalItem;
import com.fs.starfarer.api.characters.FleetTotalSource;
import com.fs.starfarer.api.characters.ShipSkillEffect;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.characters.AfterShipCreationSkillEffect;
import com.fs.starfarer.api.combat.CombatEngineAPI;

public class armaa_strikeCraftBuff {

        public static final float LOW_FLUX_MAX = 0.40f;
        public static final float HIGH_FLUX_MIN = 0.80f;

        // Low band
        public static final float LOW_SPEED_BONUS = 10f;
        public static final float LOW_FLUXCOST_REDUCTION = 10f;

        // Mid band
        public static final float MID_DISSIPATION_BONUS = 10f;
        public static final float MID_TURN_BONUS = 10f; // optional

        // High band
        public static final float HIGH_SPEED_BONUS = 15f;
        public static final float HIGH_SHIELD_DMG_REDUCTION = 10f; // % reduction to upkeep (optional)
        public static final float HIGH_PHASE_UPKEEP_REDUCTION = 10f; // % reduction to upkeep (optional)
        public static final float HIGH_DISSIPATION_BONUS = 15f;

    public static boolean isStrikecraftAndOfficer(MutableShipStatsAPI stats) {
        if (stats.getEntity() instanceof ShipAPI) {
            ShipAPI ship = (ShipAPI) stats.getEntity();
            if (!ship.getVariant().hasHullMod("strikeCraft")) {
                return false;
            }
            return !ship.getCaptain().isDefault();
        } else {
            FleetMemberAPI member = stats.getFleetMember();
            if (member == null) {
                return false;
            }
            if (!member.getVariant().hasHullMod("strikeCraft")) {
                return false;
            }
            return !member.getCaptain().isDefault();
        }
    }

    public static class Level1 implements ShipSkillEffect {

        @Override
        public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {

        }

        @Override
        public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {

        }

        @Override
        public String getEffectPerLevelDescription() {
            return null;
        }

        @Override
        public String getEffectDescription(float level) {
            return ""
                + "- <=40% flux: +" + (int)LOW_SPEED_BONUS + "% max speed, -" + (int)LOW_FLUXCOST_REDUCTION + "% weapon flux cost\n"
                + "- 40–80% flux: +" + (int)MID_DISSIPATION_BONUS + "% flux dissipation, +" + (int)MID_TURN_BONUS + "% turn performance\n"
                + "- >=80% flux: +" + (int)HIGH_SPEED_BONUS + "% max speed, +" + (int)HIGH_DISSIPATION_BONUS + "% flux dissipation, "
                + "-" + (int)HIGH_SHIELD_DMG_REDUCTION + "% shield damage taken (if shielded) / -" + (int)HIGH_PHASE_UPKEEP_REDUCTION + "% phase upkeep (if phased), "
                + "+10% hull damage resistance";        
        }

        @Override
        public ScopeDescription getScopeDescription() {
            return ScopeDescription.ALL_SHIPS;
        }
    }

    public static class Level2 implements ShipSkillEffect {

        @Override
        public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {

        }

        @Override
        public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {

        }

        @Override
        public String getEffectDescription(float level) {
            return "";
        }

        @Override
        public String getEffectPerLevelDescription() {
            return null;
        }

        @Override
        public ScopeDescription getScopeDescription() {
            return ScopeDescription.ALL_SHIPS;
        }
    }

    public static class Level3 extends BaseSkillEffectDescription implements ShipSkillEffect, FleetTotalSource, AfterShipCreationSkillEffect {

        @Override
        public FleetTotalItem getFleetTotalItem() {
            return getPhaseOPTotal();
        }

        @Override
        public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
            if (isStrikecraftAndOfficer(ship.getMutableStats())) {
                ship.addListener(new FluxDisciplineListener(ship));
            }
        }

        @Override
        public void unapplyEffectsAfterShipCreation(ShipAPI ship, String id) {
            ship.removeListenerOfClass(FluxDisciplineListener.class);
        }

        @Override
        public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {
        }

        @Override
        public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {

        }

        @Override
        public String getEffectDescription(float level) {
            return "";
        }

        //public void createCustomDescription(MutableCharacterStatsAPI stats, SkillSpecAPI skill, 
        //									TooltipMakerAPI info, float width) {
        //
        //}
        @Override
        public ScopeDescription getScopeDescription() {
            return ScopeDescription.ALL_SHIPS;
        }
    }

    public static class FluxDisciplineListener implements AdvanceableListener {

        private final ShipAPI ship;
        private final String modId; // unique per ship + skill instance
        private int lastBand = -1;
        public static final float LOW_FLUX_MAX = 0.40f;
        public static final float HIGH_FLUX_MIN = 0.80f;

        // Low band
        public static final float LOW_SPEED_BONUS = 10f;
        public static final float LOW_FLUXCOST_REDUCTION = 10f;

        // Mid band
        public static final float MID_DISSIPATION_BONUS = 10f;
        public static final float MID_TURN_BONUS = 10f; // optional

        // High band
        public static final float HIGH_SPEED_BONUS = 15f;
        public static final float HIGH_SHIELD_DMG_REDUCTION = 10f; // % reduction to upkeep (optional)
        public static final float HIGH_PHASE_UPKEEP_REDUCTION = 10f; // % reduction to upkeep (optional)
        public static final float HIGH_DISSIPATION_BONUS = 15f;
        // Bands: 0 = low, 1 = mid, 2 = high

        private int getBand(float flux) {
            if (flux <= LOW_FLUX_MAX) {
                return 0;
            }
            if (flux >= HIGH_FLUX_MIN) {
                return 2;
            }
            return 1;
        }

        public FluxDisciplineListener(ShipAPI ship) {
            this.ship = ship;
            // Use a stable id that won't collide with other ship systems
            this.modId = "armaa_fluxdisc_" + ship.getId();
        }

        private void clearMods(MutableShipStatsAPI stats) {
            stats.getMaxSpeed().unmodify(modId);

            stats.getFluxDissipation().unmodify(modId);
            stats.getMaxTurnRate().unmodify(modId);

            stats.getShieldDamageTakenMult().unmodify(modId);
            stats.getBallisticWeaponFluxCostMod().unmodify(modId);
            stats.getEnergyWeaponFluxCostMod().unmodify(modId);

            stats.getTurnAcceleration().unmodify(modId);
            stats.getDeceleration().unmodify(modId);

            stats.getHullDamageTakenMult().unmodify(modId);

            stats.getPhaseCloakUpkeepCostBonus().unmodify(modId);            
        }

        @Override
        public void advance(float amount) {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null) {
                return;
            }
            if (ship == null || !ship.isAlive() || ship.isHulk()) {
                return;
            }

            MutableShipStatsAPI stats = ship.getMutableStats();
            float flux = ship.getFluxLevel();

            int band = getBand(flux);
            if (band != lastBand) {
                // Only clear/reapply when band changes (cheap + avoids jitter)
                clearMods(stats);

                if (band == 0) {
                    // ≤40%: precision window
                    float costMult = 1f - (LOW_FLUXCOST_REDUCTION / 100f);                    
                    stats.getMaxSpeed().modifyPercent(modId, LOW_SPEED_BONUS);
                    stats.getBallisticWeaponFluxCostMod().modifyMult(modId,costMult);
                    stats.getEnergyWeaponFluxCostMod().modifyMult(modId,costMult);

                } else if (band == 1) {
                    // 40–80%: working hot
                    stats.getFluxDissipation().modifyPercent(modId, MID_DISSIPATION_BONUS);
                    stats.getMaxTurnRate().modifyPercent(modId, 10f); // optional
                    stats.getTurnAcceleration().modifyPercent(modId, 10f);  // if available
                    stats.getDeceleration().modifyPercent(modId, 10f);      // if available                    

                } else {
                    // ≥80%: emergency egress
                    stats.getHullDamageTakenMult().modifyMult(modId,0.9f);
                    stats.getArmorDamageTakenMult().modifyMult(modId,0.9f);
                    stats.getMaxSpeed().modifyPercent(modId, HIGH_SPEED_BONUS);
                    stats.getFluxDissipation().modifyPercent(modId, HIGH_DISSIPATION_BONUS);

                    // Only meaningful if the ship actually has a shield
                    if (ship.getShield() != null) {
                        float upkeepMult = 1f - (HIGH_SHIELD_DMG_REDUCTION / 100f); // 0.90
                        stats.getShieldDamageTakenMult().modifyMult(modId, upkeepMult);
                    }
                    else if(ship.getPhaseCloak() != null)
                    {
                        float upkeepMult = 1f - (HIGH_PHASE_UPKEEP_REDUCTION / 100f);
                        stats.getPhaseCloakUpkeepCostBonus().modifyMult(modId, upkeepMult);
                    }
                }

                lastBand = band;
            }

            // Optional: player feedback
            if (engine.getPlayerShip() == ship) {
                String label = (band == 0) ? "Precision" : (band == 1) ? "Working Hot" : "Egress";
                Global.getCombatEngine().maintainStatusForPlayerShip(
                        modId,
                        "graphics/icons/tactical/engine_boost2.png",
                        "Flux Discipline",
                        label,
                        false
                );
            }
        }
    }
}
