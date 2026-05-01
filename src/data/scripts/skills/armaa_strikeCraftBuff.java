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
import com.fs.starfarer.api.impl.campaign.ids.Stats;

public class armaa_strikeCraftBuff {

    public static final float SPEED_BONUS = 25f;
    private static final float BEAM_RESISTANCE = 25f;
    public static final float OVERLOAD_MULT = 0.25f;
    public static final float VENT_MULT = 0.25f;
    private static final float NO_DMOD_CHANCE_MULT = 0.50f;

    public static boolean isStrikecraft(MutableShipStatsAPI stats) {
        if (stats.getEntity() instanceof ShipAPI) {
            ShipAPI ship = (ShipAPI) stats.getEntity();
            return ship.getVariant().hasHullMod("strikeCraft");

        } else {
            FleetMemberAPI member = stats.getFleetMember();
            if (member == null) {
                return false;
            }
            return member.getVariant().hasHullMod("strikeCraft");

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
            return "- -" + (int) (NO_DMOD_CHANCE_MULT * 100) + "% chance to acquire DMods\n"
                    + "- -" + (int) (BEAM_RESISTANCE)+ "% damage received from beam weapons\n"
                    + "- As hull level decreases (reaching full effect at or below 45% hull):\n"
                    + "- up to +" + (int) SPEED_BONUS + "% max speed\n"
                    + "- up to +" + (int) (OVERLOAD_MULT * 100) + "% shorter overload time, and " + (int) (VENT_MULT * 100) + "% faster active vent rate";
        }

        @Override
        public ScopeDescription getScopeDescription() {
            return ScopeDescription.ALL_SHIPS;
        }
    }

    public static class Level2 implements ShipSkillEffect {

        @Override
        public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {
            stats.getBeamDamageTakenMult().modifyMult(id, 1f - (BEAM_RESISTANCE / 100f));
            stats.getBeamShieldDamageTakenMult().modifyMult(id, 1f - (BEAM_RESISTANCE / 100f));
        }

        @Override
        public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
            stats.getBeamDamageTakenMult().unmodify(id);
            stats.getBeamShieldDamageTakenMult().unmodify(id);
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
        public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {

            // D-mod chance reduction: -50%
            if (isStrikecraft(stats)) {
                stats.getDynamic().getMod(Stats.DMOD_ACQUIRE_PROB_MOD)
                        .modifyMult(id, NO_DMOD_CHANCE_MULT);
            }
        }

        @Override
        public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
            if (isStrikecraft(stats)) {
                stats.getDynamic().getMod(Stats.DMOD_ACQUIRE_PROB_MOD)
                        .unmodify(id);
            }

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

        @Override
        public void applyEffectsAfterShipCreation(ShipAPI ship, String string) {
            if (isStrikecraft(ship.getMutableStats()) == false) {
                return;
            }
            ship.addListener(new armaa_strikeCraftBuffListener(ship));

        }

        @Override
        public void unapplyEffectsAfterShipCreation(ShipAPI ship, String string) {
            if (isStrikecraft(ship.getMutableStats()) == false) {
                return;
            }
            ship.removeListenerOfClass(armaa_strikeCraftBuffListener.class);
        }
    }

    public static class armaa_strikeCraftBuffListener implements AdvanceableListener {

        public String modId;
        public ShipAPI ship;

        public armaa_strikeCraftBuffListener(ShipAPI ship) {
            this.ship = ship;
            // Use a stable id that won't collide with other ship systems
            this.modId = "armaa_strikeCraftBuffListener_" + ship.getId();
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
            float t = (1f - ship.getHullLevel()) / (1f - 0.45f); // 0 at full HP, 1 at 45% HP
            t = Math.min(t, 1f); // cap at 1 below 45%
            float speedBonus = t * SPEED_BONUS;
            ship.getMutableStats().getMaxSpeed().modifyPercent(modId, speedBonus);
            // t goes 0->1 as hull drops, so bonus scales up with damage
            float overloadMult = 1f - (t * OVERLOAD_MULT);  // 1.0 at full HP (no change), 0.75 at low HP (half duration)
            float ventMult = 1f + (t * VENT_MULT);      // 1.0 at full HP (no change), 1.25 at low HP (50% faster)

            ship.getMutableStats().getOverloadTimeMod().modifyMult(modId, overloadMult);
            ship.getMutableStats().getVentRateMult().modifyMult(modId, ventMult);
            if (engine.getPlayerShip() == ship) {
                String label = "+" + speedBonus + "% top speed";
                Global.getCombatEngine().maintainStatusForPlayerShip(
                        modId,
                        "graphics/icons/tactical/engine_boost2.png",
                        "Reactive Motion Sheath",
                        label,
                        false
                );
            }
        }
    }
}
