package data.hullmods;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import java.awt.Color;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.Alignment;
import org.lazywizard.lazylib.combat.CombatUtils;
import com.fs.starfarer.api.combat.CombatEntityAPI;

import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

public class armaa_variableUnit extends BaseHullMod {

    private static final float MIN_TIME_MULT = 0.1f;
    private static final Set<String> BLOCKED_HULLMODS = new HashSet<>();
    public static final Map<String, Float> GROUND_BONUS = new HashMap<>();

    public static class Bonuses {

        public float betaWeaponDamagePct, betaTurnRatePct, betaTurnAccelPct, betaDecelPct;
        public float betaAccelMult;           
        public float betaArmorDamageTakenMult; 
        public float betaEngineDamageTakenMult;
        public float alphaProjectileSpeedPct, alphaAccelPct;
        public float alphaTurnRateMult, alphaDecelMult, alphaTurnAccelMult; // 0.5 = -50%
        // gradient 
        public float rofPct, shieldDamageTakenBonus, shieldArcReductionMult, speedMalusAtMech;
        // shared
        public float maxTimeMult, groundBonus;
    }

    private static final Map<String, Bonuses> REGISTRY = new HashMap<>();

    public static Bonuses getBonuses(String hullId) {
        Bonuses b = REGISTRY.get(hullId);
        return (b != null) ? b : DEFAULT;
    }

    /**
     * Base guarDUAL values
     */
    public static final Bonuses DEFAULT = makeBase();

    private static Bonuses makeBase() {
        Bonuses b = new Bonuses();
        b.betaWeaponDamagePct = 50f;
        b.betaTurnRatePct = 50f;
        b.betaTurnAccelPct = 50f;
        b.betaDecelPct = 100f;
        b.betaAccelMult = 0.5f;            // -50% accel in mech
        b.betaArmorDamageTakenMult = 0.5f; // -50% armor damage taken
        b.betaEngineDamageTakenMult = 0.5f;
        b.alphaProjectileSpeedPct = 50f;
        b.alphaAccelPct = 50f;             // +50% accel in fighter
        b.alphaTurnRateMult = 0.5f;        // -50% turn rate
        b.alphaDecelMult = 0.5f;
        b.alphaTurnAccelMult = 0.5f;
        b.rofPct = 50f;
        b.shieldDamageTakenBonus = 0.25f;
        b.shieldArcReductionMult = 0.5f;
        b.speedMalusAtMech = 0.5f;         // -50% top speed in mech
        b.maxTimeMult = 1.15f;
        b.groundBonus = 40f;
        return b;
    }

    /**
     * Zwei
     */
    private static Bonuses makeHeavy() {
        Bonuses b = makeBase();
        b.betaWeaponDamagePct = 50f; // 
        b.rofPct = 10f;              // ALPHA RoF trimmed
        b.maxTimeMult = 1.05f;
        b.shieldArcReductionMult = 0.75f;
        b.speedMalusAtMech = 0.55f;         // -45% top speed in mech
        b.shieldDamageTakenBonus = 0.43f;
        return b;
    }

    static {
        REGISTRY.put("armaa_guardual", makeBase());
        REGISTRY.put("armaa_guardual_bs", makeHeavy());
    }

    static {
        // These hullmods will automatically be removed
        // This prevents unexplained hullmod blocking
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

    static {
        //GROUND_BONUS.put("armaa_guardual", 40f);
        //GROUND_BONUS.put("armaa_guardual", 40f);
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float HEIGHT = 64f;
        float PAD = 10f;
        Color YELLOW = new Color(241, 199, 0);
        String fighterIcon = "graphics/armaa/icons/hullsys/armaa_fighter_icon.png";
        String mechIcon = "graphics/armaa/icons/hullsys/armaa_soldier_icon.png";

        // Pull this hull's actual numbers so the tooltip can never drift from the applied stats.
        Bonuses b =  ship != null ? getBonuses(ship.getHullSpec().getBaseHullId()) : makeBase();

        float pad = 2f;
        Color POS = Misc.getPositiveHighlightColor();
        Color NEG = Misc.getNegativeHighlightColor();
        Color HL = Misc.getHighlightColor();

        tooltip.addSectionHeading("Details", Alignment.MID, 5);

        // ---- Mode ALPHA (fighter) ----
        TooltipMakerAPI alpha = tooltip.beginImageWithText(fighterIcon, HEIGHT);
        alpha.addPara("Fighter Mode", pad, YELLOW, "Fighter Mode");
        alpha.addPara("\u2022 +%s projectile speed. Disables hybrid slot.", pad,
                new Color[]{POS, NEG}, (int) b.alphaProjectileSpeedPct + "%", "Disables hybrid slot");
        alpha.addPara("\u2022 +%s acceleration", pad, POS, (int) b.alphaAccelPct + "%");
        alpha.addPara("\u2022 Turn rate reduced by %s", pad, NEG, (int) ((1f - b.alphaTurnRateMult) * 100f) + "%");
        alpha.addPara("\u2022 +%s rate of fire", pad, POS, (int) b.rofPct + "%");
        alpha.addPara("\u2022 Shield damage taken increased by %s, arc reduced by %s", pad,
                new Color[]{NEG, NEG},
                (int) (b.shieldDamageTakenBonus * 100f) + "%", (int) (b.shieldArcReductionMult * 100f) + "%");
        alpha.addPara("\u2022 +%s time dilation when near enemies or enemy projectiles", pad, POS,
                (int) ((b.maxTimeMult - 1f) * 100f) + "%");
        tooltip.addImageWithText(PAD);

        // ---- Mode BETA (mech) ----
        TooltipMakerAPI beta = tooltip.beginImageWithText(mechIcon, HEIGHT);
        beta.addPara("Soldier Mode", pad, YELLOW, "Soldier Mode");
        beta.addPara("\u2022 +%s ballistic & energy damage. Disables missile slots.", pad,
                new Color[]{POS, NEG}, (int) b.betaWeaponDamagePct + "%", "Disables missile slots");
        beta.addPara("\u2022 Armor & engine damage taken reduced by %s", pad, POS,
                (int) ((1f - b.betaArmorDamageTakenMult) * 100f) + "%");
        beta.addPara("\u2022 +%s turn rate, +%s turn acceleration", pad,
                new Color[]{POS, POS, POS},
                (int) b.betaTurnRatePct + "%", (int) b.betaTurnAccelPct + "%");
        beta.addPara("\u2022 +%s deceleration", pad, POS, (int) b.betaDecelPct + "%");
        beta.addPara("\u2022 Acceleration reduced by %s, top speed reduced by %s", pad,
                new Color[]{NEG, NEG},
                (int) ((1f - b.betaAccelMult) * 100f) + "%", (int) ((1f - b.speedMalusAtMech) * 100f) + "%");
        tooltip.addImageWithText(PAD);

        // ---- ground support ----
        float level = 0;
        if (ship != null && ship.getFleetMember() != null) {
             level = ship.getCaptain().isDefault() ? 1f
                        : ship.getCaptain().getStats().getLevel();
                Float groundVal = ship.getFleetMember().getDeploymentPointsCost();
                float bonus = (groundVal != null) ? groundVal : ship.getFleetMember().getDeploymentPointsCost();


        tooltip.addPara("%s " + "Increases effective strength of ground ops by %s", pad * 2, HL, "\u2022", level*bonus+"");
        }
        tooltip.addPara("%s", 6f, Misc.getGrayColor(), new String[]{"\"Keeping this thing running is like patching up three LSMs at once. A miracle when it's flight-ready, and a nightmare when it's not. Makes you understand why they kept production numbers low.\""}).italicize();
        tooltip.addPara("%s", 1f, Misc.getGrayColor(), new String[]{"         \u2014 Chief Mechanic"});

    }

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
    }

    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        if (!ship.hasListenerOfClass(data.scripts.ai.armaa_guarDualTransformAI.class)) {
            ship.addListener(new data.scripts.ai.armaa_guarDualTransformAI(ship));

        }
    }

    @Override
    public void advanceInCampaign(FleetMemberAPI member, float amount) {
        if (member.getFleetData() != null && member.getFleetData().getFleet() != null && member.getFleetData().getFleet().getViewForMember(member) != null) {
            member.getFleetData().getFleet().getViewForMember(member).getContrailColor().setBase(new Color(0f, 0f, 0f, 0f));
            member.getFleetData().getFleet().getViewForMember(member).getEngineColor().setBase(new Color(0f, 0f, 0f, 0f));
            member.setSpriteOverride("graphics/fx/empty.png");
        }
        // magicpaintjob hack
        if (member.getVariant() != null) {
            String storedId = (String) Global.getSector().getPersistentData()
                    .get("armaa_guarDualPaintjob_" + member.getId());
            if (storedId != null) {
                boolean hasTag = false;
                for (String tag : member.getVariant().getTags()) {
                    if (tag.startsWith("ML_paintjob-")) {
                        hasTag = true;
                        break;
                    }
                }
                if (!hasTag) {
                    member.getVariant().addTag("ML_paintjob-" + storedId);
                }
            }
        }
        Global.getSector().getPersistentData().remove("armaa_guarDualPaintjob_" + member.getId());
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        boolean enemiesInRange = false;
        boolean player = ship == Global.getCombatEngine().getPlayerShip();
        float maxTimeMult = getBonuses(ship.getHullSpec().getBaseHullId()).maxTimeMult;
        float transformLevel = Global.getCombatEngine().getCustomData().get("armaa_tranformLevel_" + ship.getId()) != null
                ? (Float) Global.getCombatEngine().getCustomData().get("armaa_tranformLevel_" + ship.getId()) : 0f;
        if (Global.getCurrentState() == GameState.COMBAT) {

            if (ship.getVariant() != null) {
                String pjTag = null;
                for (String tag : ship.getVariant().getTags()) {
                    if (tag.startsWith("ML_paintjob-")) {
                        pjTag = tag;
                        break;
                    }
                }
                //Global.getLogger(this.getClass()).info(pjTag);
                if (pjTag != null) {
                    String fullId = pjTag.substring("ML_paintjob-".length());   // "armaa_guardual_bs_gunmetal"
                    String hullId = ship.getHullSpec().getBaseHullId();
                    String variant = fullId.startsWith(hullId + "_")
                            ? fullId.substring(hullId.length() + 1) // "gunmetal"
                            : fullId;
                    Global.getCombatEngine().getCustomData().put("armaa_guarDualPaintjob_" + ship.getId(), variant);
                    if (ship.getFleetMember() != null) {
                        Global.getSector().getPersistentData().put(
                                "armaa_guarDualPaintjob_" + ship.getFleetMember().getId(), fullId);
                    }
                    ship.getVariant().removeTag(pjTag);
                }
            }
        }
        if (ship.isAlive()) {

            for (CombatEntityAPI entity : CombatUtils.getEntitiesWithinRange(ship.getLocation(), 700f)) {
                //if(entity.
                if (entity.getOwner() != ship.getOwner() && entity.getOwner() != 100) {
                    enemiesInRange = true;
                    if (!ship.isLanding() && !ship.isFinishedLanding() && player) {
                        Global.getCombatEngine().maintainStatusForPlayerShip("timeflow", "graphics/icons/hullsys/temporal_shell.png", "Heightened Reaction", "Timeflow at " + (int) (100f + ((maxTimeMult * transformLevel) - 1f) * 100f) + "%", true);
                        break;
                    }
                }
            }
            if (enemiesInRange && !ship.isLanding() && !ship.isFinishedLanding()) {
                ship.getMutableStats().getTimeMult().modifyMult(ship.getId() + "_armaa_fighterBoost", Math.max(1f, maxTimeMult * transformLevel));
                if (player) {
                    float mult = 1f / (maxTimeMult);
                    float timeMult = transformLevel * mult + (1 - transformLevel) * 1f;
                    Global.getCombatEngine().getTimeMult().modifyMult(ship.getId() + "_armaa_fighterBoost", timeMult);
                }
            }
        } else {
            Global.getCombatEngine().getTimeMult().unmodify(ship.getId() + "_armaa_fighterBoost");
            ship.getMutableStats().getTimeMult().unmodify(ship.getId() + "_armaa_fighterBoost");
        }
        if (player && !ship.isLanding() && !ship.isFinishedLanding()) {
            // transformLevel ~1 = fighter, ~0 = mech (you compute this already)
            boolean fighterMode = transformLevel > 0.5f;
            if (fighterMode) {
                Global.getCombatEngine().maintainStatusForPlayerShip(
                        "armaa_missilemode",
                        "graphics/.../some_icon.png",
                        "Fighter Configuration",
                        "Forward missiles online \u2014 broadside mounts offline",
                        false);  // false = not a debuff/positive coloring as appropriate
            } else {
                Global.getCombatEngine().maintainStatusForPlayerShip(
                        "armaa_missilemode",
                        "graphics/.../some_icon.png",
                        "Soldier Configuration",
                        "Broadside missiles online \u2014 forward mounts offline",
                        false);
            }
        }
    }

    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) {
            return "X";
        }
        if (index == 1) {
            return "incompatible with Safety Overrides.";
        }
        if (index == 2) {
            return "additional large scale modifications cannot be made to the hull";
        }
        return null;
    }

}
