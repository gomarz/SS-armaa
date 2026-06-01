package data.hullmods.selector;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.util.armaa_utils;
import java.awt.Color;

public class armaa_selector_head_lonewolf extends BaseHullMod {

    public static final float DAMAGE_BONUS_PER_THREAT = 0.03f; // 3% per weighted threat
    public static final float DAMAGE_BONUS_MAX = 0.40f;        // cap at 40%
    public static final float SYSTEM_REGEN_BONUS = 10f;        // % faster system regen

    public static final float WEIGHT_FIGHTER = 0.25f;
    public static final float WEIGHT_FRIGATE = 0.5f;
    public static final float WEIGHT_DESTROYER = 1.0f;
    public static final float WEIGHT_CRUISER = 1.5f;
    public static final float WEIGHT_CAPITAL = 2.0f;

    public static final float THREAT_RANGE = 2000f;

    private static final String MOD_ID = "armaa_head_lonewolf";
    private final IntervalUtil timer = new IntervalUtil(0.05f, 0.05f);

    @Override
    public int getDisplaySortOrder() {
        return 2000;
    }

    @Override
    public int getDisplayCategoryIndex() {
        return 3;
    }

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        armaa_utils.applyFlatMod(stats.getZeroFluxMinimumFluxLevel(), id, 114514f);
        armaa_utils.applyMultMod(stats.getAllowZeroFluxAtAnyLevel(), id, 114514f);
        stats.getSystemRegenBonus().modifyPercent(id, SYSTEM_REGEN_BONUS);
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) {
            return;
        }

        timer.advance(amount);
        if (!timer.intervalElapsed()) {
            return;
        }

        float weightedEnemyCount = 0f;
        float weightedFriendlyCount = 0f;

        for (ShipAPI other : engine.getShips()) {
            if (!other.isAlive() || other.isHulk()) {
                continue;
            }
            if (other == ship) {
                continue;
            }

            float dist = Misc.getDistance(ship.getLocation(), other.getLocation());
            if (dist > THREAT_RANGE) {
                continue;
            }

            float weight = getWeight(other.getHullSize());

            if (other.getOwner() == ship.getOwner()) {
                weightedFriendlyCount += weight;
            } else {
                weightedEnemyCount += weight;
            }
        }

        float netThreat = Math.max(0f, weightedEnemyCount - weightedFriendlyCount);
        float damageBonus = Math.min(netThreat * DAMAGE_BONUS_PER_THREAT, DAMAGE_BONUS_MAX);

        if (damageBonus > 0) {
            ship.getMutableStats().getBallisticWeaponDamageMult()
                    .modifyPercent(MOD_ID, damageBonus * 100f);
            ship.getMutableStats().getEnergyWeaponDamageMult()
                    .modifyPercent(MOD_ID, damageBonus * 100f);
        } else {
            ship.getMutableStats().getBallisticWeaponDamageMult().unmodify(MOD_ID);
            ship.getMutableStats().getEnergyWeaponDamageMult().unmodify(MOD_ID);
        }

        if (ship == engine.getPlayerShip()) {
            String statusText;
            if (damageBonus <= 0) {
                statusText = "No bonus - allied support negates threat weight";
            } else {
                statusText = "Weapon damage: +"
                        + (int) (damageBonus * 100f) + "%"
                        + "  (threat: " + String.format("%.1f", weightedEnemyCount)
                        + "  support: " + String.format("%.1f", weightedFriendlyCount)
                        + "  net: " + String.format("%.1f", netThreat) + ")";
            }
            engine.maintainStatusForPlayerShip(
                    MOD_ID + "_status",
                    "graphics/icons/hullsys/missile_autoforge.png",
                    "Adversity",
                    statusText,
                    damageBonus <= 0
            );
        }
    }

    private float getWeight(HullSize size) {
        switch (size) {
            case FIGHTER:
                return WEIGHT_FIGHTER;
            case FRIGATE:
                return WEIGHT_FRIGATE;
            case DESTROYER:
                return WEIGHT_DESTROYER;
            case CRUISER:
                return WEIGHT_CRUISER;
            case CAPITAL_SHIP:
                return WEIGHT_CAPITAL;
            default:
                return 0f;
        }
    }

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) {
            return "Lone Wolf Head";
        }
        if (index == 1) {
            return "Remove this hullmod to cycle between heads.";
        }
        return null;
    }

    private final Color HL = Global.getSettings().getColor("hColor");
    private final Color F = Global.getSettings().getColor("textFriendColor");
    private final Color E = Global.getSettings().getColor("textEnemyColor");

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize,
            ShipAPI ship, float width, boolean isForModSpec) {
        float pad = 10f;
        float padS = 2f;
        Color[] pos = {Misc.getHighlightColor(), F};
        Color[] neg = {Misc.getHighlightColor(), E};

        tooltip.addSectionHeading("Adversity Systems", Alignment.MID, pad);
        tooltip.addPara(
                "Replaces the standard head with a combat escalation suite tuned for solo engagements. "
                + "The more outnumbered it is, the more dangerous it becomes, but allied support "
                + "diminishes the effect.",
                padS
        );

        tooltip.addSectionHeading("Bonuses", Alignment.MID, pad);
        tooltip.addPara(
                "%s Enables zero-flux speed boost.",
                padS, pos, "-"
        );
        tooltip.addPara(
                "%s Ballistic and energy weapon damage increases by %s per net threat weight within %s su, "
                + "up to %s. Nearby allied ships reduce net threat weight by their own hull size weight, "
                + "negating the bonus if sufficiently supported.",
                padS, pos, "-",
                (int) (DAMAGE_BONUS_PER_THREAT * 100f) + "%",
                (int) THREAT_RANGE + "",
                (int) (DAMAGE_BONUS_MAX * 100f) + "%"
        );
        tooltip.addPara(
                "%s Ship system recharges %s faster.",
                padS, pos, "-",
                (int) SYSTEM_REGEN_BONUS + "%"
        );

        tooltip.addSectionHeading("Hull Size Weights", Alignment.MID, pad);
        tooltip.addPara(
                "Fighter: %s  |  Frigate: %s  |  Destroyer: %s  |  Cruiser: %s  |  Capital: %s",
                padS, new Color[]{Misc.getHighlightColor(), Misc.getHighlightColor(),
                    Misc.getHighlightColor(), Misc.getHighlightColor(), Misc.getHighlightColor()},
                String.valueOf(WEIGHT_FIGHTER),
                String.valueOf(WEIGHT_FRIGATE),
                String.valueOf(WEIGHT_DESTROYER),
                String.valueOf(WEIGHT_CRUISER),
                String.valueOf(WEIGHT_CAPITAL)
        );

        tooltip.addSectionHeading("Notes", Alignment.MID, pad);
        tooltip.addPara(
                "Best suited for aggressive forward play where the unit intentionally operates "
                + "ahead of the fleet. Rewards isolation.",
                padS
        );
    }
}
