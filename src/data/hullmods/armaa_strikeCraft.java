package data.hullmods;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.characters.*;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.combat.listeners.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;

import org.lazywizard.lazylib.MathUtils;
import org.magiclib.util.MagicUI;
import data.scripts.ai.armaa_combat_docking_AI;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;
import java.util.*;
import java.awt.Color;
import data.scripts.util.armaa_utils;
import data.scripts.util.armaa_strikeCraftRepairTracker;
import data.scripts.MechaModPlugin;
import org.magiclib.util.MagicRender;
import org.magiclib.util.MagicIncompatibleHullmods;

public class armaa_strikeCraft extends BaseHullMod {

    private float DEGRADE_INCREASE_PERCENT = 50f;
    public float CORONA_EFFECT_REDUCTION = 0.00001f;

    public MagicUI ui;
    private static final float RETREAT_AREA_SIZE = 2100f;
    private IntervalUtil textInterval = new IntervalUtil(.5f, .5f);
    private IntervalUtil repairInterval = new IntervalUtil(5f, 5f);
    private IntervalUtil refitInterval = new IntervalUtil(.01f, .05f);
    private IntervalUtil carrierCacheInterval = new IntervalUtil(0.5f, 0.5f);

    private float MISSILE_DAMAGE_THRESHOLD = 750f;
    public final ArrayList<String> landingLines_Good = new ArrayList<>();
    public final ArrayList<String> landingLines_Fair = new ArrayList<>();
    public final ArrayList<String> landingLines_Critical = new ArrayList<>();
    public final ArrayList<String> landingLines_notPossible = new ArrayList<>();
    private static final Set<String> BLOCKED_HULLMODS = new HashSet<>();

    enum RefitMode {
        REFIT_WHEN_SAFE("armaa_strikecraft_refit_outCombat"),
        REFIT_ASAP("armaa_strikecraft_refit_standard"),
        REFIT_NEVER("armaa_strikecraft_refit_never");
        public final String value;

        private RefitMode(String value) {
            this.value = value;
        }
    }

    static {
        BLOCKED_HULLMODS.add("converted_fighterbay");
        BLOCKED_HULLMODS.add("roider_fighterClamps");
    }

    {
        landingLines_Good.add("\"Welcome aboard, $hisorher.\"");
        landingLines_Good.add("\"Looks like you got away pretty clean!\"");
        landingLines_Good.add("\"Hopefully, you bag a few more kills today.\"");
        landingLines_Good.add("\"All conditions good. Fuselage solid.\"");

        landingLines_Critical.add("\"Glad you made it back alive, $hisorher.\"");
        landingLines_Critical.add("\"Looks like this may be a tough battle, $hisorher.\"");
        landingLines_Critical.add("\"Be careful flying while severely damaged. Even anti-air fire could bring you down.\"");

        landingLines_notPossible.add("\"Dammit, nowhere to land!\"");
        landingLines_notPossible.add("\"Could really use somewhere to land right now..\"");
        landingLines_notPossible.add("\"Zilch on available carriers. Aborting refit!\"");
        landingLines_notPossible.add("\"There's nowhere for me to resupply.\"");
    }

    public static Map DAMAGE_MAP = new HashMap();

    static {
        DAMAGE_MAP.put(HullSize.FIGHTER, 1f);
        DAMAGE_MAP.put(HullSize.FRIGATE, 1.20f);
        DAMAGE_MAP.put(HullSize.DESTROYER, 1.10f);
        DAMAGE_MAP.put(HullSize.CRUISER, 0.90f);
        DAMAGE_MAP.put(HullSize.CAPITAL_SHIP, 0.80f);
    }

    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        if (stats.getFleetMember() != null) {
            // UAF compat
            if (!stats.getFleetMember().getCaptain().isAICore()) {
                armaa_utils.applyFlatMod(stats.getZeroFluxMinimumFluxLevel(), id, -114514f);
                armaa_utils.applyMultMod(stats.getAllowZeroFluxAtAnyLevel(), id, -114514f);
            }
        }
        stats.getCRLossPerSecondPercent().modifyPercent(id, DEGRADE_INCREASE_PERCENT);
        stats.getSensorProfile().modifyMult(id, 0.5f);
        stats.getSensorStrength().modifyMult(id, 0.5f);

        if (stats.getFleetMember() != null && stats.getFleetMember().getFleetData() != null) {
            FleetDataAPI fleet = stats.getFleetMember().getFleetData();
            boolean carrierBonus = false;
            boolean independent = stats.getVariant().getHullSpec().getTags().contains("independent_of_carrier");
            boolean hasSupportingShip = false;
            //fleet.syncIfNeeded();
            for (FleetMemberAPI ship : fleet.getMembersListCopy()) {
                if (ship == stats.getFleetMember()) {
                    continue;
                }
                if (independent && carrierBonus) {
                    break;
                }
                if (ship.getVariant().hasHullMod("armaa_strikeCraft")) {
                    continue;
                }
                hasSupportingShip = true;
                if (ship.isFrigate()) {
                    continue;
                }
                if (ship.getNumFlightDecks() < 1) {
                    continue;
                }
                carrierBonus = true;
            }

            if (!hasSupportingShip && !independent) {
                stats.getMaxBurnLevel().modifyMult("armaa_carrierStorage", 0f);
            } else {
                stats.getMaxBurnLevel().unmodify("armaa_carrierStorage");
            }

            if (carrierBonus) {
                stats.getDynamic().getStat(Stats.CORONA_EFFECT_MULT).modifyMult("armaa_carrierStorageHyper", CORONA_EFFECT_REDUCTION);
            }
        }

        if (Global.getCombatEngine() != null || (Global.getSector() != null && Global.getSector().getCampaignUI() != null && Global.getSector().getCampaignUI().isShowingDialog())) {
            stats.getDynamic().getStat(Stats.CORONA_EFFECT_MULT).unmodify("armaa_carrierStorageHyper");
        }
        if (stats.getVariant().getHullSpec().hasTag("armaa_launches_from_ships")) {
            stats.getVariant().getHullSpec().setTravelDriveId("armaa_traveldrive");
        }
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        for (String tmp : BLOCKED_HULLMODS) {
            if (ship.getVariant().getHullMods().contains(tmp)) {
                MagicIncompatibleHullmods.removeHullmodWithWarning(ship.getVariant(), tmp, "cataphract");
            }
        }
        if (!ship.hasListenerOfClass(armaa_strikeCraft.StrikeCraftDeathMod.class)) {
            ship.addListener(new StrikeCraftDeathMod(ship));
        }
    }

    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) {
            return "activating autopilot. You can also target a specific carrier to land on it.";
        }
        return null;
    }

    private final Color HL = Global.getSettings().getColor("hColor");
    private final Color F = Global.getSettings().getColor("textFriendColor");
    private final Color E = Global.getSettings().getColor("textEnemyColor");

    public String getRefitMode(ShipAPI ship) {
        if (ship != null) {
            Collection<String> tags = ship.getHullSpec().getTags();
            if (tags.contains(RefitMode.REFIT_WHEN_SAFE.value)) {
                return "refit when no enemies are nearby, or completely out of ammo";
            } else if (tags.contains(RefitMode.REFIT_ASAP.value)) {
                return "refit as soon as a weapon runs out of ammo.";
            } else {
                return "only refit when CR or Hull is low.";
            }
        }
        return "";
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float pad = 10f;
        float padS = 2f;
        Color[] arr = {Misc.getHighlightColor(), F};
        Color[] arr2 = {Misc.getHighlightColor(), E};
        Color[] arr3 = {Misc.getHighlightColor(), Misc.getHighlightColor(), E};
        String size = "";
        if (ship != null) {
            if (ship.getHullSpec().hasTag("strikecraft_medium")) {
                size = "can only land on ships larger than destroyers";
            } else if (ship.getHullSpec().hasTag("strikecraft_large")) {
                size = "can only land on capital ships";
            }
        }

        tooltip.addSectionHeading("Details", Alignment.MID, 10);
        tooltip.addPara("%s " + "Can %s.", pad, Misc.getHighlightColor(), "\u2022", "maneuver over asteroids and other vessels, unless flamed out");
        tooltip.addPara("%s " + "Most weapons %s.", padS, Misc.getHighlightColor(), "\u2022", "fire over allied ships");
        tooltip.addPara("%s " + "No %s.", padS, Misc.getHighlightColor(), "\u2022", "zero-flux speed bonus");
        tooltip.addPara("%s " + "Combat Readiness decreases %s faster.", padS, arr2, "\u2022", (int) DEGRADE_INCREASE_PERCENT + "%");
        if (ship != null) {
            tooltip.addPara("%s " + "Can dock at carriers to resupply %s times", padS, Misc.getHighlightColor(), "\u2022", "" + (armaa_strikeCraftRepairTracker.getRepairPool(ship) / ship.getFleetMember().getDeploymentPointsCost()));
        }
        tooltip.addPara("%s " + "Docking replenishes PPT, armor, hull, and ammo.", padS, Misc.getHighlightColor(), "\u2022");
        tooltip.addPara("%s " + "Benefits from all bonuses that affect frigates.", padS, Misc.getHighlightColor(), "\u2022", "frigates");
        tooltip.addSectionHeading("Point Defense Vulnerability", Alignment.MID, 10);
        TooltipMakerAPI pdWarning = tooltip.beginImageWithText("graphics/armaa/icons/hullsys/armaa_pdWarning.png", 64);
        pdWarning.addPara("%s " + "Receives extra damage ships/weapons would deal to fighters, up %s.", padS, Misc.getHighlightColor(), "\u2022", "25%");
        UIPanelAPI temp = tooltip.addImageWithText(10f);

        tooltip.addSectionHeading("Refit Mode", Alignment.MID, 10);
        tooltip.addPara("Ship will " + getRefitMode(ship), pad, arr, "\u2022");
        tooltip.addSectionHeading("Carrier Bonuses", Alignment.MID, 10);
        if (ship != null) {
            if (ship.getFleetMember() != null && ship.getFleetMember().getFleetData() != null) {
                for (FleetMemberAPI member : ship.getFleetMember().getFleetData().getMembersListCopy()) {
                    if (member.isCarrier()) {
                        tooltip.addPara("%s " + "Friendly carriers present for storage during travel: Receives %s from hyperspace storms and coronas outside of combat.", pad, arr, "\u2022", "no damage");
                        break;
                    }
                }
            }
        }

        tooltip.addSectionHeading("Refit Penalties", Alignment.MID, 10);
        if (ship != null && ship.getVariant() != null) {
            if (size.length() > 1) {
                tooltip.addPara("%s " + "Large Strikecraft: %s", pad, arr2, "\u2022", size);
            }
            getRefitRate(ship);
            Map<String, Float> MALUSES = (Map) Global.getCombatEngine().getCustomData().get("armaa_strikecraftMalus_" + ship.getId());
            if (MALUSES == null || MALUSES.isEmpty()) {
                tooltip.addPara("No refit penalty.", 10, F);
            } else {
                float total = (float) Global.getCombatEngine().getCustomData().get("armaa_strikecraftTotalMalus" + ship.getId());
                tooltip.addPara("%s " + "Refitting at carriers is %s slower.", pad, arr2, "\u2022", (int) (total * 100) + "%");
                tooltip.addSectionHeading("Modifiers", Alignment.MID, 10);
                tooltip.addPara("", 0f);
                for (Map.Entry<String, Float> entry : MALUSES.entrySet()) {
                    float value = entry.getValue();
                    String weapon = entry.getKey();
                    tooltip.addPara("%s " + "%s: +%s.", padS, arr3, "\u2022", weapon, (int) (value * 100) + "%");
                }
            }
        }
    }

    private void getRefitRate(ShipAPI target) {
        float totalRate = 0f;
        String wepName = "";
        List<WeaponAPI> weapons = target.getAllWeapons();
        Map<String, Float> MALUSES = new HashMap<>();
        for (WeaponAPI w : weapons) {
            float adjustedRate = 0f;
            if (MechaModPlugin.MISSILE_REFIT_MALUS.get(w.getId()) != null) {
                adjustedRate = MechaModPlugin.MISSILE_REFIT_MALUS.get(w.getId());
                totalRate += adjustedRate;
                if (MALUSES.containsKey(w.getDisplayName())) {
                    MALUSES.put(w.getDisplayName(), MALUSES.get(w.getDisplayName()) + adjustedRate);
                } else {
                    MALUSES.put(w.getDisplayName(), adjustedRate);
                }
            } else if (w.getType() == WeaponType.MISSILE) {
                float damage = w.getDerivedStats().getDamagePerShot();
                if (damage > MISSILE_DAMAGE_THRESHOLD) {
                    float penalty = damage / MISSILE_DAMAGE_THRESHOLD;
                    if (penalty < 1) {
                        continue;
                    }
                    penalty = penalty / 10f;
                    float newRate = (float) Math.min(.5f, penalty);
                    wepName = w.getDisplayName();
                    adjustedRate = newRate;
                    totalRate += adjustedRate;
                    if (MALUSES.containsKey(wepName)) {
                        MALUSES.put(wepName, MALUSES.get(wepName) + adjustedRate);
                    } else {
                        MALUSES.put(wepName, adjustedRate);
                    }
                }
            }
        }
        for (HullModSpecAPI spec : Global.getSettings().getAllHullModSpecs()) {
            if (MechaModPlugin.HULLMOD_REFIT_MALUS.get(spec.getId()) == null) {
                continue;
            }
            if (!target.getVariant().getHullMods().contains(spec.getId())) {
                continue;
            }
            String hullmod = spec.getId();
            String name = spec.getDisplayName();
            float adjustedRate = MechaModPlugin.HULLMOD_REFIT_MALUS.get(hullmod);
            totalRate += adjustedRate;
            if (MALUSES.containsKey(name)) {
                MALUSES.put(name, MALUSES.get(name) + adjustedRate);
            } else {
                MALUSES.put(name, adjustedRate);
            }
        }
        Global.getCombatEngine().getCustomData().put("armaa_strikecraftTotalMalus" + target.getId(), totalRate);
        Global.getCombatEngine().getCustomData().put("armaa_strikecraftMalus" + "_" + target.getId(), MALUSES);
    }

    private boolean needsReload(ShipAPI target) {
        List<WeaponAPI> weapons = target.getAllWeapons();
        int loadedWeps = 0;
        int dryWeps = 0;

        for (ShipAPI module : target.getChildModulesCopy()) {
            String key = "moduleRepair_isDestroyed" + "_" + module.getId();
            if (Global.getCombatEngine().getCustomData().containsKey(key)) {
                return true;
            }
        }

        boolean check = false;
        int side = target.getOwner() == 1 ? 0 : 1;
        ShipHullSpecAPI spec = target.getVariant().getHullSpec();
        if (target.getVariant().getHullSpec().isDHull() && target.getHullSpec().getDParentHull() != null) {
            spec = target.getHullSpec().getDParentHull();
        }
        if (spec.getTags().contains(RefitMode.REFIT_WHEN_SAFE.value)) {
            if (target.areAnyEnemiesInRange() || CombatUtils.isVisibleToSide(target, side)) {
                return check;
            }
        } else if (spec.getTags().contains("armaa_strikecraft_refit_never")) {
            return check;
        }

        for (WeaponAPI w : weapons) {
            if (w.hasAIHint(WeaponAPI.AIHints.PD) || w.hasAIHint(WeaponAPI.AIHints.PD_ALSO) || w.getMaxAmmo() <= 0) {
                continue;
            }
            if (w.getId().equals("armaa_leynosBoostKnuckle") && Global.getCombatEngine().getPlayerShip() != target) {
                return false;
            }
            if (!w.getSlot().isDecorative() && !w.getId().equals("armaa_landingBeacon")) {
                if (w.usesAmmo() && (w.getAmmo() < 1 && w.getAmmoPerSecond() == 0)) {
                    dryWeps++;
                    return true;
                } else if ((w.usesAmmo() && w.getAmmo() >= 1 && w.getAmmoPerSecond() >= 0) || !w.usesAmmo()) {
                    loadedWeps++;
                }
            }
        }
        if (dryWeps > 0) {
            if (dryWeps >= loadedWeps) {
                return true;
            } else if ((Global.getCombatEngine().getPlayerShip() != target && (target.getShipTarget() == null || target.getShipTarget().isHulk()))
                    || (Global.getCombatEngine().getPlayerShip() == target && !Global.getCombatEngine().isUIAutopilotOn() && target.getShipTarget() == null)
                    || Global.getCombatEngine().getPlayerShip() == target) {
                return true;
            }
        }
        return false;
    }

    private boolean canRefit(ShipAPI ship) {
        for (ShipAPI module : ship.getChildModulesCopy()) {
            if (module.getVariant().hasTag("no_wingcom_docking")) {
                return false;
            }
        }
        for (ShipAPI carrier : CombatUtils.getShipsWithinRange(ship.getLocation(), 10000.0F)) {
            if (carrier.getOwner() != ship.getOwner() || carrier.isFighter() || carrier.isFrigate()) {
                continue;
            }
            if (carrier.isHulk()) {
                continue;
            }
            if (ship.getHullSpec().hasTag("strikecraft_medium") && carrier.isDestroyer()) {
                continue;
            }
            if (ship.getHullSpec().hasTag("strikecraft_large") && (carrier.isCruiser() || carrier.isDestroyer())) {
                continue;
            }
            if (carrier.getNumFighterBays() > 0) {
                return true;
            }
        }
        return false;
    }

    private ShipAPI getNearestCarrier(ShipAPI ship) {
        ShipAPI potCarrier = null;
        float distance = 99999f;
        for (ShipAPI carrier : CombatUtils.getShipsWithinRange(ship.getLocation(), 100000f)) {
            if (carrier == ship) {
                continue;
            }
            if (carrier.getVariant().hasHullMod("strikeCraft")) {
                continue;
            }
            if (carrier.getHullSpec().hasTag("no_wingcom_docking")) {
                continue;
            }
            if (carrier.getOwner() != ship.getOwner()) {
                continue;
            }
            if (!carrier.getHullSpec().hasTag("strikecraft_with_bays") && (carrier.isFighter() || carrier.isFrigate()) && !carrier.isStationModule()) {
                continue;
            }
            if (carrier == ship) {
                continue;
            }
            if (carrier.isHulk() || !carrier.isAlive() || !Global.getCombatEngine().isEntityInPlay(carrier)) {
                continue;
            }
            if (carrier.getNumFighterBays() > 0) {
                float dist = MathUtils.getDistance(ship, carrier);
                if (dist < distance) {
                    distance = dist;
                    potCarrier = carrier;
                }
            }
        }
        return potCarrier;
    }

    private void retreatToRefit(ShipAPI ship, ShipAPI carrier) {
        if (ship.isRetreating()) {
            return;
        }
        if (ship == null || ship.getLocation() == null || carrier == null) {
            return;
        }
        ShipwideAIFlags flags = ship.getAIFlags();
        Global.getCombatEngine().headInDirectionWithoutTurning(
                ship, VectorUtils.getAngle(ship.getLocation(), carrier.getLocation()), ship.getMaxSpeed());
        flags.setFlag(ShipwideAIFlags.AIFlags.BACK_OFF, 1f);
        flags.setFlag(ShipwideAIFlags.AIFlags.MOVEMENT_DEST, 1f, carrier.getLocation());
    }

    public void adjustHullSize(ShipAPI ship, HullSize size) {
        if (ship.isRetreating() && ship.getHullSize() != HullSize.FRIGATE && !ship.isFinishedLanding()) {
            size = HullSize.FRIGATE;
        }
        if (ship.getHullSize().equals(size)) {
            return;
        }
        ship.setHullSize(size);
        if (size.equals(HullSize.FRIGATE)) {
            ship.resetDefaultAI();
        }
    }

    public void checkRefitStatus(ShipAPI ship, float amount) {
        boolean player = ship == Global.getCombatEngine().getPlayerShip();
        if (!ship.isRetreating() && ((!player && canRefit(ship)) || (player && !Global.getCombatEngine().isUIAutopilotOn()))) {
            armaa_combat_docking_AI DockingAI = null;
            ShipAPI nearestCarrier = getNearestCarrier(ship);
            if (!ship.isLanding() && !ship.isFinishedLanding() && nearestCarrier != null && MathUtils.getDistance(ship, nearestCarrier) < 500f) {
                DockingAI = new armaa_combat_docking_AI(ship);
                if (!Global.getCombatEngine().getCustomData().containsKey("armaa_strikecraftisLanding" + ship.getId())) {
                    ship.setShipAI(DockingAI);
                    DockingAI.init();
                    Global.getCombatEngine().getCustomData().put("armaa_strikecraftisLanding" + ship.getId(), true);
                }
            } else {
                if (Global.getCombatEngine().getCustomData().get("armaa_strikecraftLanded" + ship.getId()) instanceof Boolean) {
                    boolean hasLanded = (boolean) Global.getCombatEngine().getCustomData().get("armaa_strikecraftLanded" + ship.getId());
                    if (player && !hasLanded && ship.isFinishedLanding()) {
                        Random rand = new Random();
                        String txt = landingLines_Critical.get(rand.nextInt(landingLines_Critical.size()));
                        if (ship.getHullLevel() > 0.45f) {
                            txt = landingLines_Good.get(rand.nextInt(landingLines_Good.size()));
                        }
                        Vector2f texLoc;
                        if (txt.contains("$hisorher")) {
                            FullName.Gender gender = ship.getCaptain().getGender();
                            String title;
                            switch (gender) {
                                case FEMALE:
                                    title = "ma'am";
                                    break;
                                case MALE:
                                    title = "sir";
                                    break;
                                default:
                                    title = "sir";
                                    break;
                            }
                            txt = txt.replace("$hisorher", title);
                        }
                        if (DockingAI != null && DockingAI.getCarrier() != null) {
                            texLoc = DockingAI.getCarrier().getLocation();
                        } else {
                            texLoc = ship.getLocation();
                        }
                        if (Math.random() <= 0.35f) {
                            ship.getFluxTracker().showOverloadFloatyIfNeeded(txt, Color.white, 2f, true);
                            Global.getSoundPlayer().playSound("ui_noise_static", 1f, 1f, texLoc, new Vector2f());
                        }
                        Global.getCombatEngine().getCustomData().put("armaa_strikecraftLanded" + ship.getId(), true);
                    }
                }
                if (DockingAI != null) {
                    Global.getCombatEngine().getCustomData().put("armaa_strikecraftRefitTime" + ship.getId(), DockingAI.getCarrierRefitRate());
                }
            }
        }
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (!ship.isPhased() && (ship.getEngineController().isFlamedOut() || ship.getHitpoints() <= 0 || ship.isHulk())) {
            ship.setCollisionClass(CollisionClass.SHIP);
        } else if (ship.getCollisionClass() == CollisionClass.SHIP) {
            ship.setCollisionClass(CollisionClass.FIGHTER);
        }

        if (!ship.isAlive()) {
            return;
        }
        if (Global.getCombatEngine().isPaused()) {
            return;
        }
        if(ship.isStationModule())
            return;
        if (ship.getShipAI() == null && Global.getCombatEngine().getPlayerShip() == ship) {
            Global.getCombatEngine().getCustomData().remove("armaa_strikecraftisLanding" + ship.getId());
        }

        if (!(Global.getCombatEngine().getCustomData().get("armaa_strikecraftLanded" + ship.getId()) instanceof Boolean)) {
            Global.getCombatEngine().getCustomData().put("armaa_strikecraftLanded" + ship.getId(), false);
        }

        ShipAPI target = null;
        boolean selectedCarrier = false;
        if (Global.getCombatEngine().getPlayerShip() == ship) {
            if (ship.getShipTarget() != null) {
                target = ship.getShipTarget();
            }
            if (!ship.isFinishedLanding() && target != null
                    && (target.isAlly() || target.getOwner() == ship.getOwner())
                    && (!target.isFrigate() || target.isStationModule())
                    && target.getNumFighterBays() > 0) {
                selectedCarrier = true;
            }
        }

        carrierCacheInterval.advance(amount);
        if (carrierCacheInterval.intervalElapsed()) {
            ShipAPI freshCarrier = getNearestCarrier(ship);
            boolean freshCanRefit = canRefit(ship);
            float peakDuration = ship.getPeakTimeRemaining();
            boolean lowPPT = peakDuration < 50;
            int side = ship.getOwner() == 1 ? 0 : 1;
            boolean safeToRetreat = !ship.areAnyEnemiesInRange() || !CombatUtils.isVisibleToSide(ship, side);
            boolean needsPPTRefit = lowPPT && safeToRetreat;
            boolean poolCanRepairHullCR = armaa_strikeCraftRepairTracker.canRepairHullCR(ship);

            // Gate needs-refit on pool state so AI stops cycling when pool is exhausted
            boolean freshNeedsRefit = (armaa_utils.canRestoreHPOrCR(ship) && poolCanRepairHullCR)
                    || (needsReload(ship))
                    || needsPPTRefit && poolCanRepairHullCR
                    || selectedCarrier;

            Global.getCombatEngine().getCustomData().put("armaa_cachedCarrier_" + ship.getId(), freshCarrier);
            Global.getCombatEngine().getCustomData().put("armaa_cachedCanRefit_" + ship.getId(), freshCanRefit);
            Global.getCombatEngine().getCustomData().put("armaa_cachedNeedsRefit_" + ship.getId(), freshNeedsRefit);
        }

        // Read cached values
        ShipAPI cachedCarrier = null;
        Object cachedCarrierObj = Global.getCombatEngine().getCustomData().get("armaa_cachedCarrier_" + ship.getId());
        if (cachedCarrierObj instanceof ShipAPI) {
            cachedCarrier = (ShipAPI) cachedCarrierObj;
        }

        boolean cachedCanRefit = Boolean.TRUE.equals(Global.getCombatEngine().getCustomData().get("armaa_cachedCanRefit_" + ship.getId()));
        boolean cachedNeedsRefit = Boolean.TRUE.equals(Global.getCombatEngine().getCustomData().get("armaa_cachedNeedsRefit_" + ship.getId()));

        // Validate cached carrier is still alive
        if (cachedCarrier != null && (cachedCarrier.isHulk() || !cachedCarrier.isAlive() || !Global.getCombatEngine().isEntityInPlay(cachedCarrier))) {
            cachedCarrier = null;
            Global.getCombatEngine().getCustomData().put("armaa_cachedCarrier_" + ship.getId(), null);
        }

        // retreatToRefit runs every frame using cached carrier - smooth AI movement, no scan
        if (cachedNeedsRefit && cachedCanRefit && cachedCarrier != null) {
            if (ship.getAI() != null) {
                retreatToRefit(ship, cachedCarrier);
            }
        }

        // Refit interval - actual docking logic
        refitInterval.advance(amount);
        if (refitInterval.intervalElapsed()) {
            if (cachedCanRefit && cachedNeedsRefit) {
                if (!ship.isStationModule() || ship.getStationSlot() == null) {
                    checkRefitStatus(ship, amount);
                }
            }
        }

        repairInterval.advance(amount);
        if (cachedNeedsRefit && cachedCanRefit) {
            if (repairInterval.intervalElapsed()) {
                if (Global.getCombatEngine().getPlayerShip() == ship && !ship.isFinishedLanding()) {

                    String repairCapStr = getRepairCapacityString(ship);
                    Global.getCombatEngine().maintainStatusForPlayerShip("AceSystem1",
                            "graphics/ui/icons/icon_repair_refit.png",
                            "/ - WARNING - /",
                            repairCapStr + " (PRESS " + Global.getSettings().getControlStringForEnumName("C2_TOGGLE_AUTOPILOT") + ")",
                            true);
                }
            }
        } else {
            if (Global.getCombatEngine().getPlayerShip() == ship && !ship.isFinishedLanding()) {
                String repairCapStr = getRepairCapacityString(ship);
                Global.getCombatEngine().maintainStatusForPlayerShip("AceSystem1",
                        "graphics/ui/icons/icon_repair_refit.png",
                        "/ - STATUS - /",
                        repairCapStr,
                        false);
            }
        }

        if (ship.getFluxTracker().isOverloaded()) {
            textInterval.advance(amount);
        }

        // Repair sprite render - only show for player-side ships that still have pool
        if (ship.getAI() != null && cachedCanRefit && cachedNeedsRefit && !ship.isFinishedLanding()
                && repairInterval.intervalElapsed() && ship.getOwner() == 0) {
            SpriteAPI repairSprite = Global.getSettings().getSprite("ui", "icon_repair_refit");
            MagicRender.objectspace(
                    repairSprite, ship,
                    new Vector2f(0f, ship.getCollisionRadius()), new Vector2f(),
                    new Vector2f(30, 30), new Vector2f(-1f, -1f),
                    0f, 90f, false,
                    Global.getSettings().getBasePlayerColor(),
                    true, 0f, 2f, 2f, true
            );
        }

    }

    private String getRepairCapacityString(ShipAPI ship) {
        int repairsLeft = armaa_strikeCraftRepairTracker.getRepairsRemaining(ship);
        float pool = armaa_strikeCraftRepairTracker.getRepairPool(ship);
        if (repairsLeft > 0) {
            return repairsLeft + " REPAIR(S) REMAINING";
        } else if (pool > 0) {
            return "PARTIAL REPAIR AVAILABLE";
        } else {
            return "HULL/CR REPAIRS EXHAUSTED";
        }
    }

    public static class StrikeCraftDeathMod implements DamageTakenModifier, AdvanceableListener {

        protected ShipAPI ship;

        public StrikeCraftDeathMod(ShipAPI ship) {
            this.ship = ship;
        }

        @Override
        public void advance(float amount) {
            if (!ship.controlsLocked() && ship.getMutableStats().getHullDamageTakenMult().getPercentMods().containsKey("armaa_invincible")) {
                ship.getMutableStats().getHullDamageTakenMult().unmodify("armaa_invincible");
                ship.getMutableStats().getArmorDamageTakenMult().unmodify("armaa_invincible");
            }
        }

        @Override
        public String modifyDamageTaken(Object param, CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean shieldHit) {
            if (!(target instanceof ShipAPI)) {
                return null;
            }
            if (shieldHit) {
                return null;
            }

            String id = "strikecraft_death";
            if (damage.getStats() != null) {
                if (ship.getVariant().hasHullMod("armaa_targetingDisruptor")) {
                    Float hullSizeMult = (float) DAMAGE_MAP.get(damage.getStats().getVariant().getHullSize());
                    if (hullSizeMult != null) {
                        damage.getModifier().modifyMult(id, hullSizeMult);
                    }
                } else {
                    float mult = Math.max(1f, Math.min(1.25f, damage.getStats().getDamageToFighters().getModifiedValue()));
                    if (mult > 1.05f && Math.random() < 0.05f) {
                        Color textColor = mult >= 1.25f ? Color.RED : (mult >= 1.15f ? Color.ORANGE : Color.YELLOW);
                        Global.getCombatEngine().addFloatingText(
                                ship.getLocation(),
                                "PD-Optimized strike! +" + mult + "x dmg",
                                16f, textColor, ship, 0.5f, 1.0f
                        );
                    }
                    damage.getModifier().modifyMult(id, mult);
                }
            }
            return id;
        }
    }
}
