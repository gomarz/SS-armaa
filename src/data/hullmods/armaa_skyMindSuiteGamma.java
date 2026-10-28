package data.hullmods;

import com.fs.starfarer.api.combat.*;
import data.scripts.util.armaa_wingLandingUtil;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import org.lazywizard.lazylib.MathUtils;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import data.scripts.ai.armaa_combat_docking_AI_fighter;
import data.scripts.ai.armaa_combat_retreat_AI_fighter;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;
import java.util.*;
import java.awt.Color;
import java.util.Random;
import com.fs.starfarer.api.ui.Alignment;
import data.scripts.util.armaa_utils;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemType;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.HullModItemManager;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import data.scripts.MechaModPlugin;
import org.lwjgl.input.Keyboard;

import org.magiclib.util.MagicIncompatibleHullmods;

import com.fs.starfarer.api.util.IntervalUtil;

public class armaa_skyMindSuiteGamma extends BaseHullMod {

    public static final String DATA_PREFIX = "gamma_core_skymind_check_";
    public static final String coreType = "GAMMA CORE";
    private Color COLOR = new Color(0, 106, 0, 50);
    private boolean hasLanded;
    public static final String ITEM = Commodities.GAMMA_CORE;
    private Vector2f landingLoc = new Vector2f();
    private static final Color JITTER_UNDER_COLOR = new Color(50, 125, 50, 50);
    private static final float MAX_TIME_MULT = 1.1f;
    private static final Map<HullSize, Float> ENGAGEMENT_REDUCTION = new HashMap<>();
    private static final int BOMBER_COST_MOD = 10000;
    private static final float FIGHTER_REPLACEMENT_TIME_MULT = .70f;
    private static final float FIGHTER_RATE = 1.25f;
    private static final float CREW_LOSS_MULT = 0.25f;
    private IntervalUtil tracker2 = new IntervalUtil(0.05f, 0.05f);
    private final List<String> squadChatter_gamma = MechaModPlugin.squadChatter_gamma;
    private static final Set<String> BLOCKED_HULLMODS = new HashSet<>();

    static {
        BLOCKED_HULLMODS.add("armaa_skyMindAlpha");
        BLOCKED_HULLMODS.add("armaa_skyMindBeta");
    }

    static {
        ENGAGEMENT_REDUCTION.put(HullSize.FIGHTER, 0.5f);
        ENGAGEMENT_REDUCTION.put(HullSize.FRIGATE, 0.5f);
        ENGAGEMENT_REDUCTION.put(HullSize.DESTROYER, 0.4f);
        ENGAGEMENT_REDUCTION.put(HullSize.CRUISER, 0.3f);
        ENGAGEMENT_REDUCTION.put(HullSize.CAPITAL_SHIP, 0.2f);
    }

    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        int numFighters = 0;
        int extraCrew = 0;
    }

    //@Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, java.lang.String id) {
        for (String tmp : BLOCKED_HULLMODS) {
            if (ship.getVariant().getHullMods().contains(tmp)) {
                MagicIncompatibleHullmods.removeHullmodWithWarning(ship.getVariant(), tmp, "armaa_skyMindGamma");
            }
        }

    }

    @Override
    public boolean affectsOPCosts() {
        return false;
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        final CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        boolean hasItem = true;
        if (playerFleet != null) {
            if (Global.getSector().getPlayerFleet().getCargo().getCommodityQuantity(ITEM) > 0) {
                hasItem = true;
            } else {
                hasItem = false;
            }
        }

        return hasItem && ((!ship.isStationModule() && ship.getVariant().hasHullMod("strikeCraft") && ship.getHullSpec().getFighterBays() == 0) || ship.getMutableStats().getNumFighterBays().getModifiedValue() > 0);
    }

    public String getUnapplicableReason(ShipAPI ship) {
        if (ship == null) {
            return "Can not be assigned";
        }

        final CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        if (playerFleet != null) {
            final String coreKey = "armaa_skyMind_" + ITEM + "_" + ship.getFleetMemberId();
            final Map<String, Object> data = Global.getSector().getPersistentData();
            if (Global.getSector().getPlayerFleet().getCargo().getCommodityQuantity(ITEM) <= 0 && !data.containsKey(coreKey)) {
                return "Installation requires 1 GAMMA Core";
            }
        }

        return "Only installable on strikecraft or carriers larger than frigates";
    }

    private final Color HL = Global.getSettings().getColor("hColor");
    private final Color TT = Global.getSettings().getColor("buttonBgDark");
    private final Color F = Global.getSettings().getColor("textFriendColor");
    private final Color E = Global.getSettings().getColor("textEnemyColor");
    private final Color def = Global.getSettings().getColor("standardTextColor");

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float pad = 10f;
        float padS = 2f;
        Color[] arr = {Misc.getHighlightColor(), F};
        Color[] arrB = {Misc.getHighlightColor(), F, F};
        Color[] arr2 = {Misc.getHighlightColor(), E};
        //title

        tooltip.addSectionHeading("Details", Alignment.MID, 10);
        tooltip.addPara("%s " + "Assigning an AI Core to this vessel %s.", pad, arr, "\u2022", "will grant it's wing all the skills the core possesses");
        tooltip.addPara("%s " + "Only applicable with %s fighters.", pad, arrB, "\u2022", "automated");

        if (ship == null) {
            return;
        }

        tooltip.addSectionHeading("=== AI CORE INFO ===", Alignment.MID, 10);

        FighterWingSpecAPI wing = ship.getVariant().getWing(0);
        int wingSize = getWingSize(ship);

        if (ship != null && ship.getVariant() != null) {
            //boolean crewedWing = false;
            if (wing == null) {
                tooltip.addPara(
                        "No wing assigned.",
                        10,
                        Misc.getHighlightColor()
                );

            } else {
                boolean expand = Keyboard.isKeyDown(Keyboard.getKeyIndex("F1"));

                String captain = ship.getCaptain().getNameString();
                boolean hasSpecialString = false;
                String str = "";
                ArrayList<String> squadChatterCopy = new ArrayList<String>();
                for (String s : MechaModPlugin.squadChatter) {
                    squadChatterCopy.add(s);
                }

                Random rand = new Random();

                int size = rand.nextInt(squadChatter_gamma.size());
                String chatter = squadChatter_gamma.get(size);;
                PersonAPI pilot = ship.getCaptain();

                final String coreKey = "armaa_skyMind_" + ITEM + "_" + ship.getFleetMemberId();
                final Map<String, Object> data = Global.getSector().getPersistentData();
                if (data.containsKey(coreKey)) {
                    pilot = (PersonAPI) data.get(coreKey);
                } else {
                    return;
                }
                tooltip.addSectionHeading(pilot.getId().toUpperCase(), Alignment.MID, 10);

                String personality = coreType;

                tooltip.addSectionHeading("Persona: " + personality, HL, TT, Alignment.MID, 0f);
                tooltip.beginImageWithText(pilot.getPortraitSprite(), 88).addPara(chatter, 3, def, chatter);
                tooltip.addImageWithText(8);
                tooltip.addRelationshipBar(ship.getCaptain(), 5f);
            }
        }
    }

    @Override
    public CargoStackAPI getRequiredItem() {
        return Global.getSettings().createCargoStack(CargoItemType.RESOURCES, ITEM, null);
    }

    @Override
    public String getCanNotBeInstalledNowReason(ShipAPI ship, MarketAPI marketOrNull, CampaignUIAPI.CoreUITradeMode mode) {

        return super.getCanNotBeInstalledNowReason(ship, marketOrNull, mode);

    }

    @Override
    public void addRequiredItemSection(TooltipMakerAPI tooltip, FleetMemberAPI member, ShipVariantAPI currentVariant, MarketAPI dockedAt, float width, boolean isForModSpec) {
        CargoStackAPI req = getRequiredItem();
        if (req != null) {
            float opad = 2f;
            if (isForModSpec || Global.CODEX_TOOLTIP_MODE) {
                Color color = Misc.getBasePlayerColor();
                if (isForModSpec) {
                    color = Misc.getHighlightColor();
                }
                String name = req.getDisplayName();
                String aOrAn = Misc.getAOrAnFor(name);
                //tooltip.addPara("Requires " + aOrAn + " %s to install.", opad, color, name);
                final TooltipMakerAPI text = tooltip.beginImageWithText(Global.getSettings().getCommoditySpec(ITEM).getIconName(), 20f);
                text.addPara("Requires " + aOrAn + " %s to install.", opad, color, name);
                tooltip.addImageWithText(5f);
            } else if (currentVariant != null && member != null) {
                if (currentVariant.hasHullMod(spec.getId())) {
                    if (!currentVariant.getHullSpec().getBuiltInMods().contains(spec.getId())) {
                        Color color = Misc.getPositiveHighlightColor();
                        //tooltip.addPara("Using item: " + req.getDisplayName(), color, opad);
                        final TooltipMakerAPI text2 = tooltip.beginImageWithText(Global.getSettings().getCommoditySpec(ITEM).getIconName(), 20f);
                        text2.addPara("Using item: " + req.getDisplayName(), color, opad);
                        tooltip.addImageWithText(5f);
                    }
                } else {
                    int available = HullModItemManager.getInstance().getNumAvailableMinusUnconfirmed(req, member, currentVariant, dockedAt);
                    Color color = Misc.getPositiveHighlightColor();
                    if (available < 1) {
                        color = Misc.getNegativeHighlightColor();
                    }
                    if (available < 0) {
                        available = 0;
                    }
                    final TooltipMakerAPI text3 = tooltip.beginImageWithText(Global.getSettings().getCommoditySpec(ITEM).getIconName(), 20f);
                    text3.addPara("Requires item: " + req.getDisplayName() + " (" + available + " available)", color, opad);
                    tooltip.addImageWithText(5f);
//                        tooltip.addPara("Requires item: " + req.getDisplayName() + " (" + available + " available)",
//                                                                color, opad);
                }
            }
        }
    }

    @Override
    public void advanceInCampaign(FleetMemberAPI member, float amount) {
        final String coreKey = "armaa_skyMind_" + ITEM + "_" + member.getId();
        final Map<String, Object> data = Global.getSector().getPersistentData();
        if (!data.containsKey(coreKey)) {
            // Apparently this can be the case
            if (Misc.getAICoreOfficerPlugin(ITEM) == null) {
                return;
            }

            PersonAPI core = Misc.getAICoreOfficerPlugin(ITEM).createPerson(ITEM, member.getCaptain().getFaction().getId(), Misc.random);
            core.setAICoreId(ITEM);
            data.put(coreKey, core);
        }
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship.getLaunchBaysCopy().isEmpty()) {
            return;
        }
        //if you inherently have fighterbays, it's probably ok to use default fighter behavior
        if (ship.isStationModule()) {
            return;
        }
        if ((ship.getHullSpec().getFighterBays() > 0 && !ship.isFrigate() && !ship.isFighter()) || ship.getHullSpec().hasTag("strikecraft_with_bays")) {
            return;
        }

        armaa_wingLandingUtil.advanceLanding(ship, ship.getLaunchBaysCopy().get(0), amount);
    }

    public int getWingSize(ShipAPI ship) {
        FighterWingSpecAPI wing = ship.getVariant().getWing(0);
        int wingSize = 0;
        boolean crewedWing = false;
        if (wing != null) {
            for (int i = 0; i < ship.getVariant().getWings().size(); i++) {
                FighterWingSpecAPI w = ship.getVariant().getWing(i);
                if (w == null) {
                    continue;
                }
                crewedWing = w.getVariant().getHullSpec().getMinCrew() == 0;

                if (crewedWing) {
                    wingSize += w.getNumFighters();
                }
            }
        }

        for (String slot : ship.getVariant().getModuleSlots()) {
            if (!ship.getVariant().getModuleVariant(slot).hasHullMod("armaa_wingCommander")) {
                continue;
            }
            for (int i = 0; i < ship.getVariant().getModuleVariant(slot).getWings().size(); i++) {
                FighterWingSpecAPI w = ship.getVariant().getModuleVariant(slot).getWing(i);
                if (w == null) {
                    continue;
                }
                crewedWing = w.getVariant().getHullSpec().getMinCrew() == 0;

                if (crewedWing) {
                    wingSize += w.getNumFighters();
                }
            }
        }

        return wingSize;
    }

    public int getWingSize(ShipVariantAPI ship) {
        FighterWingSpecAPI wing = ship.getWing(0);
        int wingSize = 0;
        boolean crewedWing = false;
        if (wing != null) {
            for (int i = 0; i < ship.getWings().size(); i++) {
                FighterWingSpecAPI w = ship.getWing(i);
                if (w == null) {
                    continue;
                }
                crewedWing = w.getVariant().getHullSpec().getMinCrew() > 0;

                if (crewedWing) {
                    wingSize += w.getNumFighters();
                }
            }
        }

        return wingSize;
    }

    @Override
    public void applyEffectsToFighterSpawnedByShip(ShipAPI fighter, ShipAPI ship, java.lang.String id) {
        if (fighter.getHullSpec().getMinCrew() > 0) {
            return;
        }

        final String coreKey = "armaa_skyMind_" + ITEM + "_" + ship.getFleetMemberId();
        final Map<String, Object> data = Global.getSector().getPersistentData();
        if (data.containsKey(coreKey)) {
            PersonAPI core = (PersonAPI) data.get(coreKey);
            fighter.setCaptain(core);
        }
    }

    public String getDescriptionParam(int index, HullSize hullSize) {
        return null;
    }
}
