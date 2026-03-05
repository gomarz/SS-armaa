package data.hullmods;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
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
import data.scripts.util.armaa_pilotTracker;
import data.scripts.util.armaa_pilotTrackerNP;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import data.scripts.MechaModPlugin;
import org.lwjgl.input.Keyboard;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import com.fs.starfarer.api.impl.campaign.ids.Voices;

import org.magiclib.util.MagicIncompatibleHullmods;

import com.fs.starfarer.api.util.IntervalUtil;

public class armaa_wingCommander extends BaseHullMod {

    private Object KEY_JITTER = new Object();
    private Color COLOR = new Color(0, 106, 0, 50);
    private static final float RETREAT_AREA_SIZE = 2000f;
    private boolean hasLanded;
    private Vector2f landingLoc = new Vector2f();
    //public List<String> squadNames = new ArrayList<>();
    private static final Color JITTER_UNDER_COLOR = new Color(50, 125, 50, 50);
    private static final float MAX_TIME_MULT = 1.1f;
    private static final Map<HullSize, Float> ENGAGEMENT_REDUCTION = new HashMap<>();
    private static final int BOMBER_COST_MOD = 10000;
    private static final float FIGHTER_REPLACEMENT_TIME_MULT = .70f;
    private static final float FIGHTER_RATE = 1.25f;
    private static final float CREW_LOSS_MULT = 0.25f;
    private float deadWingmen = 0;
    private IntervalUtil tracker = new IntervalUtil(0.5f, 1.0f);

    private static String st = "st";
    private static String nd = "nd";
    private static String rd = "rd";
    private static String th = "th";

    //private final ArrayList<String> squadChatter = new ArrayList<>();
    private final ArrayList<String> squadChatter_villain = new ArrayList<>();
    private final ArrayList<String> squadChatter_aristo = new ArrayList<>();
    private final ArrayList<String> squadChatter_business = new ArrayList<>();
    private final ArrayList<String> squadChatter_faithful = new ArrayList<>();
    private final ArrayList<String> squadChatter_official = new ArrayList<>();
    private final ArrayList<String> squadChatter_pather = new ArrayList<>();
    private final ArrayList<String> squadChatter_scientist = new ArrayList<>();
    private final ArrayList<String> squadChatter_soldier = new ArrayList<>();
    private final ArrayList<String> squadChatter_spacer = new ArrayList<>();
    private final WeightedRandomPicker<String> voices = new WeightedRandomPicker<>();
    private static final Set<String> BLOCKED_HULLMODS = new HashSet<>();

    static {
        // These hullmods will automatically be removed
        // This prevents unexplained hullmod blocking
    }

    {
        voices.add(Voices.SOLDIER, 5);
        voices.add(Voices.SPACER, 10);
        voices.add(Voices.FAITHFUL, 3);
        voices.add(Voices.VILLAIN, 4);
    }

    static {
        ENGAGEMENT_REDUCTION.put(HullSize.FIGHTER, 0.7f);
        ENGAGEMENT_REDUCTION.put(HullSize.FRIGATE, 0.7f);
        ENGAGEMENT_REDUCTION.put(HullSize.DESTROYER, 0.4f);
        ENGAGEMENT_REDUCTION.put(HullSize.CRUISER, 0.3f);
        ENGAGEMENT_REDUCTION.put(HullSize.CAPITAL_SHIP, 0.2f);
    }

    //possible refactor of the way pilot data is stored. it'd be more efficient but I dont think there's much benefit otherwise
    //shunted to TODO list
    //All of these access data stored in global
    //WingCommander
    //WingcommanderPromote
    //PromoteWingMan
    //PilotTracker
    // private final Map<String,Object>PLT_DATA = new HashMap<>();
    //  {
    //PLT_DATA.add("Pilot",PersonAPI);
    //PLT_DATA.add("Callsign",String);
    //PLT_DATA.add("
//	}
    private final Map<String, List<String>> VOICE_DIALG = new HashMap<>();

    {
        //vanilla lpcs
        // VOICE_DIALG.put(Voices.ARISTO, "\"Finally, someone with class. Hello, Commander!\"");
        //VOICE_DIALG.put(Voices.ARISTO, "\"I like your style.\"");
        //VOICE_DIALG.put(Voices.BUSINESS, "\"Even though they fly like bricks, Warthogs are hardy and pack a punch. We never know what we could run into out here, so i'm a little more at ease with that knowledge.\"");

        VOICE_DIALG.put(Voices.SOLDIER, MechaModPlugin.squadChatter_soldier);
        VOICE_DIALG.put(Voices.VILLAIN, MechaModPlugin.squadChatter_villain);
        //"OK, that's enough work for today. Why don't we all go out drinking?"

    }

    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        int numFighters = 0;
        int extraCrew = 0;
        if (stats.getVariant().getHullSpec().getFighterBays() == 0) {
            for (int i = 0; i < stats.getVariant().getWings().size(); i++) {
                if (stats.getVariant().getWing(i) != null) {
                    extraCrew += stats.getVariant().getWing(i).getVariant().getHullSpec().getMinCrew() * stats.getVariant().getWing(i).getNumFighters();
                }
            }
            stats.getMinCrewMod().modifyFlat(id, extraCrew);
            stats.getDynamic().getMod(Stats.BOMBER_COST_MOD).modifyFlat(id, BOMBER_COST_MOD);
            stats.getDynamic().getMod(Stats.BOMBER_COST_MOD).modifyFlat(id, BOMBER_COST_MOD);
            if(stats.getNumFighterBays().isUnmodified())
                stats.getNumFighterBays().modifyFlat(id, 1f);
            stats.getDynamic().getStat(Stats.REPLACEMENT_RATE_DECREASE_MULT).modifyMult(id, 1f + FIGHTER_REPLACEMENT_TIME_MULT);
        }

        stats.getFighterWingRange().modifyMult(id, 1f - ENGAGEMENT_REDUCTION.get(hullSize));
        stats.getDynamic().getStat(Stats.REPLACEMENT_RATE_DECREASE_MULT).modifyMult(id, 1f + FIGHTER_REPLACEMENT_TIME_MULT);
        stats.getFighterRefitTimeMult().modifyMult(id, FIGHTER_RATE);
        stats.getDynamic().getStat(Stats.FIGHTER_CREW_LOSS_MULT).modifyMult(id, CREW_LOSS_MULT);

    }

    //@Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, java.lang.String id) {
        for (String tmp : BLOCKED_HULLMODS) {
            if (ship.getVariant().getHullMods().contains(tmp)) {
                MagicIncompatibleHullmods.removeHullmodWithWarning(ship.getVariant(), tmp, "cataphract");
            }
        }

        if (ship.getCaptain() != null) {
            if (!ship.getCaptain().isDefault() && getWingSize(ship) > 0) {
                if (hasSquad(ship.getCaptain(), true)) {
                    createPilots(ship.getCaptain(), ship, true);
                }
            }
        }
    }

    @Override
    public boolean affectsOPCosts() {
        return true;
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return (!ship.isStationModule() && ship.getVariant().hasHullMod("strikeCraft") && ship.getHullSpec().getFighterBays() == 0) || ship.getMutableStats().getNumFighterBays().isPositive();
    }

    public String getUnapplicableReason(ShipAPI ship) {
        if (ship == null) {
            return "Can not be assigned";
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
        tooltip.addPara("%s " + "Assigning an officer to this vessel %s.", pad, arr, "\u2022", "establishes a squadron that gains skills over time");
        tooltip.addPara("%s " + "Fighter crew losses are reduced by %s.", pad, arrB, "\u2022", (int) ((1f - CREW_LOSS_MULT) * 100f) + "%");
        tooltip.addPara("%s " + "Only applicable with %s fighters.", pad, arrB, "\u2022", "crewed");

        if (ship == null) {
            tooltip.addPara("%s " + "Fighter engagement range decreased by %s.", pad, arr2, "\u2022", "70/60/50/40" + " percent");
        } else {
            tooltip.addPara("%s " + "Fighter engagement range decreased by %s.", pad, arr2, "\u2022", (int) (ENGAGEMENT_REDUCTION.get(ship.getVariant().getHullSize()) * 100f) + "%");
            if (ship.getVariant().getHullSpec().getFighterBays() == 0) {
                tooltip.addPara("%s " + "If frigate, or no built-in bays: Replacement rate consumption increased by %s.", padS, arr2, "\u2022", (int) Math.round(FIGHTER_REPLACEMENT_TIME_MULT * 100f) + "%");
                tooltip.addPara("%s " + "Refit time increased by %s.", padS, arr2, "\u2022", (FIGHTER_RATE - 1f) * 100 + "%");
                tooltip.addPara("%s " + "Strikecraft %s.", padS, Misc.getHighlightColor(), "\u2022", "enter combat from deployment zone, or carrier landed at for refit");
                tooltip.addPara("%s " + "If no carriers are present, %s.", padS, Misc.getHighlightColor(), "\u2022", "fighters in need of refit will attempt to exit the combat zone");
            }
        }
        if (ship == null) {
            return;
        }

        tooltip.addSectionHeading("=== S Q U A D R O N   I N F O ===", Alignment.MID, 10);

        FighterWingSpecAPI wing = ship.getVariant().getWing(0);
        int wingSize = getWingSize(ship);

        if (ship != null && ship.getVariant() != null || Global.getSector().getPlayerFleet().getCargo().getCrew() - 1 <= wingSize) {
            //boolean crewedWing = false;
            if (wing == null) {
                tooltip.addPara(
                        "No wing assigned.",
                         10,
                         Misc.getHighlightColor()
                );

            } else if (wingSize == 0) {
                tooltip.addPara(
                        "Wing is automated. No pilots assigned.",
                         10,
                         Misc.getHighlightColor()
                );
            } else if (Global.getCombatEngine().isInCampaign() && Global.getSector().getPlayerFleet().getCargo().getCrew() - 1 <= wingSize) {
                tooltip.addPara(
                        "No crew can be spared to assign to this wing.",
                         10,
                         Misc.getHighlightColor()
                );
            } else if (ship.getCaptain().isDefault()) {
                tooltip.addPara(
                        "The wing lead by this unit is of no real note. Assign an officer to establish a squadron.",
                         10,
                         Misc.getHighlightColor()
                );

            } else {
                boolean expand = Keyboard.isKeyDown(Keyboard.getKeyIndex("F1"));

                String captain = ship.getCaptain().getNameString();
                //Only create wingmen for named captains and non auto fighters
                if (!ship.getCaptain().isDefault() && wingSize > 0) {
                    boolean hasSpecialString = false;
                    String str = "";
                    ArrayList<String> squadChatterCopy = new ArrayList<String>();
                    for (String s : MechaModPlugin.squadChatter) {
                        squadChatterCopy.add(s);
                    }
                    ArrayList<String> soldierChatterCopy = new ArrayList<String>(squadChatter_soldier);
                    ArrayList<String> villainChatterCopy = new ArrayList<String>(squadChatter_villain);
                    String key = ship.getVariant().getWing(0).getVariant().getHullSpec().getHullId();

                    if (MechaModPlugin.SPECIAL_WING_DIALG.get(key) == null) {
                        key = ship.getVariant().getWing(0).getVariant().getHullSpec().getBaseHullId();
                    }

                    if (MechaModPlugin.SPECIAL_WING_DIALG.get(key) != null) {
                        hasSpecialString = true;
                        str = MechaModPlugin.SPECIAL_WING_DIALG.get(key);
                    }
                    String squadName = "";

                    if (hasSquad(ship.getCaptain(), true)) {
                        squadName = (String) Global.getSector().getPersistentData().get("armaa_wingCommander_squadronName_" + ship.getCaptain().getId());
                    } else {
                        createSquad(ship.getCaptain());
                    }

                    tooltip.addPara(
                            "The "
                            + squadName
                            + " has been established under the command of "
                            + ship.getCaptain().getNameString()
                            + ". If this officer is assigned to another unit with WINGCOM, they will follow.",
                             10,
                             HL,
                             squadName,
                             ship.getCaptain().getNameString()
                    );

                    float squadLevel = 0;
                    for (int i = 0; i < wingSize; i++) {
                        PersonAPI pilot = null;
                        if (Global.getSector().getPersistentData().get("armaa_wingCommander_wingman_" + i + "_" + ship.getCaptain().getId()) instanceof PersonAPI) {
                            pilot = (PersonAPI) Global.getSector().getPersistentData().get("armaa_wingCommander_wingman_" + i + "_" + ship.getCaptain().getId());
                            squadLevel += pilot.getRelToPlayer().getRel();
                        }
                    }
                    squadLevel /= wingSize;

                    //Global
                    tooltip.addPara("Unit solidarity is at %s, increasing fighter defensive capabilities by %s and offensive by %s. ", pad, F, (int) (squadLevel * 100) + "%", (int) (Math.min((squadLevel * 100) * 0.30, 0.30 * 100)) + "%", (int) (Math.min(squadLevel * 100 * 0.30, 0.30 * 100)) + "%");
                    if (wingSize > 4) {
                        if (expand) {
                            tooltip.addPara("Press F1 to show less information.", 10, E, "F1");
                        } else {
                            tooltip.addPara("Hold F1 to show more information.", 10, E, "F1");
                            return;
                        }
                    }

                    createPilots(ship.getCaptain(), ship, true);

                    for (int i = 0; i < wingSize; i++) {
                        Random rand = new Random();
                        PersonAPI pilot = null;
                        String callsign = "";
                        if (Global.getSector().getPersistentData().get("armaa_wingCommander_wingman_" + i + "_" + ship.getCaptain().getId()) instanceof PersonAPI) {
                            pilot = (PersonAPI) Global.getSector().getPersistentData().get("armaa_wingCommander_wingman_" + i + "_" + ship.getCaptain().getId());
                            callsign = (String) Global.getSector().getPersistentData().get("armaa_wingCommander_wingman_" + i + "_" + "callsign_" + ship.getCaptain().getId());
                        }
                        if (pilot == null) {
                            continue;
                        }
                        if (hasSpecialString) {
                            squadChatterCopy.add(str);
                            soldierChatterCopy.add(str);
                            villainChatterCopy.add(str);

                        }
                        if (squadChatterCopy.size() <= 0) {
                            squadChatterCopy.add("...");
                        }
                        Collections.shuffle(squadChatterCopy, new Random());
                        Collections.shuffle(soldierChatterCopy, new Random());
                        Collections.shuffle(villainChatterCopy, new Random());
                        int size = rand.nextInt(squadChatterCopy.size());
                        String chatter = squadChatterCopy.get(size);
                        if (VOICE_DIALG.get(pilot.getVoice()) != null) {
                            ArrayList<String> lines = new ArrayList<String>(VOICE_DIALG.get(pilot.getVoice()));
                            int rnd = rand.nextInt(lines.size());
                            chatter = lines.get(rnd);
                            String p = pilot.getVoice();
                            switch (pilot.getVoice()) {
                                case "soldier":
                                    soldierChatterCopy.remove(chatter);
                                    break;
                                case "villain":
                                    villainChatterCopy.remove(chatter);
                                    break;
                                default:
                                    break;
                            }
                        } else {
                            squadChatterCopy.remove(chatter);
                        }

                        tooltip.addSectionHeading(pilot.getName().getFirst() + " \"" + callsign + "\" " + pilot.getName().getLast(), Alignment.MID, 10);

                        String personality = pilot.getVoice().substring(0, 1).toUpperCase() + pilot.getVoice().substring(1);

                        tooltip.addSectionHeading("Persona: " + personality, HL, TT, Alignment.MID, 0f);
                        tooltip.beginImageWithText(pilot.getPortraitSprite(), 100).addPara(chatter, 3, def, chatter);
                        UIPanelAPI temp = tooltip.addImageWithText(8);
                        tooltip.addRelationshipBar(pilot, 5f);
                    }
                }
            }
        }

    }

    @Override
    public void advanceInCampaign(FleetMemberAPI member, float amount) {
        if (member.getFleetData() == null || member.getFleetData().getFleet() == null) {
            return;
        }

        if (!member.getFleetData().getFleet().isPlayerFleet()) {
            return;
        }
        if (member.getCaptain() != null) {
            if (!member.getCaptain().isDefault() && getWingSize(member.getVariant()) > 0) {
                if (!hasSquad(member.getCaptain(), true)) {
                    createSquad(member.getCaptain());
                }
            }
        }
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship.getLaunchBaysCopy().isEmpty()) {
            return;
        }
        //if you inherently have fighterbays, it's probably ok to use default fighter behavior
        if (ship.isStationModule() && ship.getStationSlot() != null) {
            return;
        }
        if ((ship.getHullSpec().getFighterBays() > 0 && !ship.isFrigate() && !ship.isFighter()) || ship.getHullSpec().hasTag("strikecraft_with_bays")) {
            return;
        }
        if (ship.isStationModule()) {
            if (ship.getCaptain() != null) {
                if (!ship.getCaptain().isDefault() && getWingSize(ship) > 0) {
                    if (hasSquad(ship.getCaptain(), true)) {
                        createPilots(ship.getCaptain(), ship, true);
                    }
                }
            }
        }
        FighterLaunchBayAPI bay = ship.getLaunchBaysCopy().get(0);
        if (ship.isLanding()) {
            ShipAPI defaultCarrier = getCarrier(ship);
            if (defaultCarrier != null) {
                Global.getCombatEngine().getCustomData().put("armaa_wingCommander_landingLocation_default" + ship.getId(), defaultCarrier);
            }
        }
        //Global.getCombatEngine().addFloatingText(ship.getLocation(), "asdf", 10f, Color.white, ship, 0f, 0f);
        if (bay.getWing() != null) {
            if (!bay.getWing().getWingMembers().isEmpty()) {
                for (int i = 0; i < bay.getWing().getWingMembers().size(); i++) {
                    ShipAPI fighter = bay.getWing().getWingMembers().get(i);
                    if (fighter == null || fighter.isHulk()) {
                        continue;
                    }
                    if (fighter != null) {
                        Object b = Global.getCombatEngine().getCustomData().get("armaa_wingCommander_landingLocation_" + fighter.getId() + "_set");
                        boolean done = b instanceof Boolean ? (Boolean) b : false;
                        if (fighter.isLiftingOff() && !done) {
                            float mapWidth = Global.getCombatEngine().getMapWidth() / 2f, mapHeight = Global.getCombatEngine().getMapHeight() / 2f;
                            Vector2f rawLL = new Vector2f(-mapWidth, -mapHeight), rawUR = new Vector2f(mapWidth, mapHeight);
                            Vector2f landingLoc = null;

                            //set launch point to the motherships by default
                            ShipAPI potentialLaunchPoint = (ShipAPI) Global.getCombatEngine().getCustomData().get("armaa_wingCommander_landingLocation_default" + ship.getId());

                            //Is there a carrier this index remembers landing at?
                            if (Global.getCombatEngine().getCustomData().get("armaa_wingCommander_landingLocation_" + ship.getId() + "_" + i) instanceof ShipAPI) {
                                //if so, use as launch point
                                potentialLaunchPoint = (ShipAPI) Global.getCombatEngine().getCustomData().get("armaa_wingCommander_landingLocation_" + ship.getId() + "_" + i);

                            }

                            if (potentialLaunchPoint != null && (!potentialLaunchPoint.isAlive() || potentialLaunchPoint.isHulk())) {
                                potentialLaunchPoint = null;
                            }
                            if (potentialLaunchPoint == null) {
                                potentialLaunchPoint = getCarrier(fighter);
                            }
                            if (Global.getCombatEngine().isEntityInPlay(potentialLaunchPoint) && potentialLaunchPoint.isAlive()) {
                                for (FighterLaunchBayAPI wep : potentialLaunchPoint.getLaunchBaysCopy()) {
                                    if (wep.getWeaponSlot() != null) {
                                        WeaponSlotAPI w = wep.getWeaponSlot();
                                        landingLoc = new Vector2f(potentialLaunchPoint.getLocation().x + w.getLocation().y, potentialLaunchPoint.getLocation().y + w.getLocation().x);
                                        if (Math.random() <= .50f) {
                                            break;
                                        }
                                    }
                                }
                            }

                            //if we are here, we still have nowhere to land
                            if (landingLoc == null) {
                                if (potentialLaunchPoint != null && potentialLaunchPoint.getLocation() != null && potentialLaunchPoint.isAlive()) {
                                    landingLoc = potentialLaunchPoint.getLocation();
                                } else {
                                    if (fighter.getOwner() == 0) {
                                        if (ship.getLocation().getY() > rawLL.getY() - RETREAT_AREA_SIZE) {
                                            landingLoc = new Vector2f((ship.getLocation().getX()), ship.getLocation().getY() - 2000);
                                        } else {
                                            landingLoc = new Vector2f((rawLL.x + rawUR.x) / 2, rawLL.y);
                                        }
                                    } else {
                                        landingLoc = new Vector2f((rawLL.x + rawUR.x) / 2, rawUR.y);
                                    }
                                }
                            }
                            armaa_utils.setLocation(fighter, landingLoc);
                            Global.getCombatEngine().getCustomData().put("armaa_wingCommander_landingLocation_" + fighter.getId() + "_set", true);

                        }
                    }
                }
            }
            tracker.advance(amount);
            if (tracker.intervalElapsed()) {
                if (!bay.getWing().getReturning().isEmpty()) {
                    doLanding(bay, ship);
                }
            }
        }
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
                crewedWing = w.getVariant().getHullSpec().getMinCrew() > 0;

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
                crewedWing = w.getVariant().getHullSpec().getMinCrew() > 0;

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

    public void createPilots(PersonAPI commander, ShipAPI ship, boolean persistent) {
        int size = getWingSize(ship);
        int currentSize = size;
        if (Global.getSector().getPersistentData().get("armaa_wingCommander_squadSize_" + commander.getId()) instanceof Integer) {
            currentSize = (int) Global.getSector().getPersistentData().get("armaa_wingCommander_squadSize_" + commander.getId());
            if (size == (int) Global.getSector().getPersistentData().get("armaa_wingCommander_squadSize_" + commander.getId()) || size == 0) {
                return;
            }
            //quick fix for bug with drone wings
            if (currentSize > 100) {
                currentSize = 0;
            }
        }

        for (int i = 0; i < size; i++) {
            Random rand = new Random();
            PersonAPI pilot = null;
            String callsign;

            if (persistent) {
                if (Global.getSector().getPersistentData().get("armaa_wingCommander_wingman_" + i + "_" + commander.getId()) instanceof PersonAPI) {
                    pilot = (PersonAPI) Global.getSector().getPersistentData().get("armaa_wingCommander_wingman_" + i + "_" + commander.getId());
                    callsign = (String) Global.getSector().getPersistentData().get("armaa_wingCommander_wingman_" + i + "_" + "callsign_" + commander.getId());
                }
            } else {
                if (Global.getCombatEngine().getCustomData().get("armaa_wingCommander_wingman_" + i + "_" + commander.getId()) instanceof PersonAPI) {
                    pilot = (PersonAPI) Global.getCombatEngine().getCustomData().get("armaa_wingCommander_wingman_" + i + "_" + commander.getId());
                    callsign = (String) Global.getCombatEngine().getCustomData().get("armaa_wingCommander_wingman_" + i + "_" + "callsign_" + commander.getId());
                }
            }
            if (pilot == null) {
                int level = MathUtils.getRandomNumberInRange(0, 3);
                if (level > 1 && (float) Math.random() > .25f) {
                    level--;
                }
                if (level >= 2 && (float) Math.random() > .75f) {
                    level = 1;
                }
                if (!persistent) {
                    level = MathUtils.getRandomNumberInRange(0, commander.getStats().getLevel());
                }
                pilot = OfficerManagerEvent.createOfficer(commander.getFaction(), level, true);
                callsign = OfficerManagerEvent.createOfficer(commander.getFaction(), 1, true).getName().getLast();
                pilot.setVoice(voices.pick());
                if ((float) Math.random() < .20f) {
                    pilot.addTag("armaa_latentTalent");
                }
                if (persistent) {
                    Global.getSector().getPersistentData().put("armaa_wingCommander_wingman_" + i + "_" + commander.getId(), pilot);
                    Global.getSector().getPersistentData().put("armaa_wingCommander_wingman_" + i + "_" + "callsign_" + commander.getId(), callsign);
                } else {
                    Global.getCombatEngine().getCustomData().put("armaa_wingCommander_wingman_" + i + "_" + commander.getId(), pilot);
                    Global.getCombatEngine().getCustomData().put("armaa_wingCommander_wingman_" + i + "_" + "callsign_" + commander.getId(), callsign);
                }
            }
        }
        if (persistent) {
            Global.getSector().getPersistentData().put("armaa_wingCommander_squadSize_" + commander.getId(), currentSize + Math.abs(currentSize - size));
        } else {
            Global.getCombatEngine().getCustomData().put("armaa_wingCommander_squadSize_" + commander.getId(), size);
        }

    }

    public void createSquad(PersonAPI commander) {
        String squadName = getSquadName();
        Global.getSector().getPersistentData().put("armaa_wingCommander_squadronName_" + commander.getId(), squadName);
    }

    public boolean hasSquad(PersonAPI commander, boolean persistent) {
        if (persistent) {
            return Global.getSector().getPersistentData().get("armaa_wingCommander_squadronName_" + commander.getId()) instanceof String;
        }

        return Global.getCombatEngine().getCustomData().get("armaa_wingCommander_squadSize_" + commander.getId()) instanceof Integer;

    }

    @Override
    public void applyEffectsToFighterSpawnedByShip(ShipAPI fighter, ShipAPI ship, java.lang.String id) {
        //this is on an individual basis so we need to find a way to know what pilots already are on the field so they aren't assigned twice
        //Just taking the pilots relation to player

        if (ship.getCaptain() == null || ship.getCaptain().isDefault()) {
            return;
        }
        int count = 0;

        if (fighter.getMutableStats().getMinCrewMod().computeEffective(fighter.getHullSpec().getMinCrew()) <= 0) {
            return;
        }

        //get named pilots on the squadron
        if (Global.getSector().getPersistentData().get("armaa_wingCommander_squadSize_" + ship.getCaptain().getId()) instanceof Integer) {
            count = (Integer) Global.getSector().getPersistentData().get("armaa_wingCommander_squadSize_" + ship.getCaptain().getId());
            assignPilotToFighters(count, fighter, ship, true);
        } else {
            if (ship.isAlly() || ship.getOwner() == 1) {
                if (!hasSquad(ship.getCaptain(), false)) {
                    createPilots(ship.getCaptain(), ship, false);
                }
                count = (Integer) Global.getCombatEngine().getCustomData().get("armaa_wingCommander_squadSize_" + ship.getCaptain().getId());
                assignPilotToFighters(count, fighter, ship, false);
            }
        }

    }

    public void assignTempFighters() {

    }

    public void assignPilotToFighters(int count, ShipAPI fighter, ShipAPI ship, boolean persistent) {
        float squadLevel = 0;
        PersonAPI pilot = null;
        if (persistent) {
            for (int j = 0; j < count; j++) {
                boolean wasAssigned = false;
                if (Global.getSector().getPersistentData().get("armaa_wingCommander_wingman_" + j + "_" + ship.getCaptain().getId()) instanceof PersonAPI && fighter.getWing().getSpec().getVariant().getHullSpec().getMinCrew() > 0) {
                    if (Global.getCombatEngine().getCustomData().get("armaa_wingCommander_wingman_" + j + "_wasAssigned_" + ship.getCaptain().getId()) instanceof Boolean) {
                        wasAssigned = (Boolean) Global.getCombatEngine().getCustomData().get("armaa_wingCommander_wingman_" + j + "_wasAssigned_" + ship.getCaptain().getId());
                    }
                    pilot = (PersonAPI) Global.getSector().getPersistentData().get("armaa_wingCommander_wingman_" + j + "_" + ship.getCaptain().getId());
                    squadLevel += pilot.getRelToPlayer().getRel();
                    if (!wasAssigned) {
                        String callsign = (String) Global.getSector().getPersistentData().get("armaa_wingCommander_wingman_" + j + "_" + "callsign_" + ship.getCaptain().getId());
                        fighter.setCaptain(pilot);
                        Global.getCombatEngine().getCustomData().put("armaa_wingCommander_wingman_" + j + "_wasAssigned_" + ship.getCaptain().getId(), true);
                        Global.getCombatEngine().addPlugin(new armaa_pilotTracker(fighter, callsign, j));
                        break;
                    }
                }
            }
        } else {
            for (int j = 0; j < count; j++) {
                boolean wasAssigned = false;
                if (Global.getCombatEngine().getCustomData().get("armaa_wingCommander_wingman_" + j + "_" + ship.getCaptain().getId()) instanceof PersonAPI && fighter.getWing().getSpec().getVariant().getHullSpec().getMinCrew() > 0) {
                    if (Global.getCombatEngine().getCustomData().get("armaa_wingCommander_wingman_" + j + "_wasAssigned_" + ship.getCaptain().getId()) instanceof Boolean) {
                        wasAssigned = (Boolean) Global.getCombatEngine().getCustomData().get("armaa_wingCommander_wingman_" + j + "_wasAssigned_" + ship.getCaptain().getId());
                    }
                    pilot = (PersonAPI) Global.getCombatEngine().getCustomData().get("armaa_wingCommander_wingman_" + j + "_" + ship.getCaptain().getId());
                    if (persistent) {
                        squadLevel += pilot.getRelToPlayer().getRel();
                    } else {
                        squadLevel += (pilot.getStats().getLevel() / ship.getCaptain().getStats().getLevel()) * .3f;
                    }
                    if (!wasAssigned) {
                        String callsign = (String) Global.getCombatEngine().getCustomData().get("armaa_wingCommander_wingman_" + j + "_" + "callsign_" + ship.getCaptain().getId());
                        fighter.setCaptain(pilot);
                        Global.getCombatEngine().getCustomData().put("armaa_wingCommander_wingman_" + j + "_wasAssigned_" + ship.getCaptain().getId(), true);
                        Global.getCombatEngine().addPlugin(new armaa_pilotTrackerNP(fighter, callsign, j));
                        break;
                    }
                }
            }
        }

        if (fighter.getCaptain() == null || fighter.getCaptain().isDefault()) {
            return;
        }
        //Why are you iteratng through the entire wing BAKA
        squadLevel /= fighter.getWing().getSpec().getNumFighters();
        float mult = Math.min(squadLevel, 0.3f);
        MutableShipStatsAPI stats = fighter.getMutableStats();
        stats.getHullDamageTakenMult().modifyMult("armaa_wingCommander", (1f - mult));
        stats.getArmorDamageTakenMult().modifyMult("armaa_wingCommander", (1f - mult));
        stats.getEmpDamageTakenMult().modifyMult("armaa_wingCommander", (1f - mult));
        stats.getShieldDamageTakenMult().modifyMult("armaa_wingCommander", (1f - mult));
        //stats.getBallisticWeaponDamageMult().modifyMult("armaa_wingCommander",  1f+mult);
        //stats.getEnergyWeaponDamageMult().modifyMult("armaa_wingCommander", 1f+mult);

        stats.getEnergyAmmoBonus().modifyPercent("armaa_wingCommander", (mult * 100));
        stats.getBallisticAmmoBonus().modifyPercent("armaa_wingCommander", (mult * 100));

        stats.getMaxSpeed().modifyPercent("armaa_wingCommander", mult * 100);

        for (WeaponAPI weapon : fighter.getAllWeapons()) {
            float ammo = weapon.getAmmo() * (1 + (mult) / 2);
            weapon.setAmmo((int) ammo);
        }
        stats.getSystemCooldownBonus().modifyPercent("armaa_wingCommander", (mult * 100));
        stats.getSystemUsesBonus().modifyPercent("armaa_wingCommander", (mult * 100));

        stats.getBallisticRoFMult().modifyMult("armaa_wingCommander", 1f + mult);
        stats.getEnergyRoFMult().modifyMult("armaa_wingCommander", 1f + mult);

        stats.getFluxCapacity().modifyMult("armaa_wingCommander", 1f + mult);
        stats.getFluxDissipation().modifyMult("armaa_wingCommander", 1f + mult);
    }

    public String getDescriptionParam(int index, HullSize hullSize) {
        return null;
    }

    public ShipAPI getCarrier(ShipAPI ship) {
        ShipAPI potentialCarrier = null;
        boolean isFakeFighter = ship.getVariant().getHullSize() == HullSize.FRIGATE ? true : false;
        float distance = 99999f;
        for (ShipAPI carrier : CombatUtils.getShipsWithinRange(ship.getLocation(), 10000f)) {
            if (carrier.getOwner() != ship.getOwner()) {
                continue;
            }
            if (!carrier.isAlive() || carrier.isHulk()) {
                continue;
            }

            if (carrier.isFrigate() && carrier.getVariant().getNonBuiltInWings().size() <= 1) {
                continue;
            }

            if ((carrier.isFighter() && !carrier.getHullSpec().hasTag("strikecraft_with_bays")) || carrier.getHullSpec().getFighterBays() < 1) {
                continue;
            }

            //If this is a normal fighter we want to get the nearest carrier to the source ship
            if (!isFakeFighter && ship.getWing() != null && ship.getWing().getSourceShip() != null) {
                ShipAPI source = ship.getWing().getSourceShip();
                if (distance > MathUtils.getDistance(carrier, source)) {
                    distance = MathUtils.getDistance(carrier, source);
                    potentialCarrier = carrier;
                    continue;
                }

            } else if ((!carrier.isFrigate() || carrier.isStationModule())) {
                if (carrier.hasLaunchBays()) {
                    //carriersExist = true;
                    potentialCarrier = carrier;
                    return potentialCarrier;
                }
            }
        }

        return potentialCarrier;

    }

    public static String getSuffix(int num) {
        if (num == 11 || num == 12 || num == 13) {
            return th;
        }

        int lastDigit = num % 10;
        switch (lastDigit) {
            case 1:
                return st;
            case 2:
                return nd;
            case 3:
                return rd;
            default:
                return th;
        }
    }

    public String getSquadName() {
        int num = MathUtils.getRandomNumberInRange(1, 100);
        String suffix = getSuffix(num);
        Random rand = new Random();
        int size = rand.nextInt(MechaModPlugin.squadNames.size());

        return num + suffix + " " + MechaModPlugin.squadNames.get(size);
    }

    public void doLanding(FighterLaunchBayAPI bay, ShipAPI ship) {
        for (int i = 0; i < bay.getWing().getReturning().size(); i++) {
            ShipAPI fighter = bay.getWing().getReturning().get(i).fighter;
            if (fighter == null) {
                continue;
            }
            if (fighter != null) {
                if (bay.getWing().isReturning(fighter)) {
                    boolean carriersExist = false;
                    ShipAPI posCarrier = getCarrier(fighter);
                    if (posCarrier != null) {
                        armaa_combat_docking_AI_fighter DockingAI = new armaa_combat_docking_AI_fighter(fighter);
                        if (fighter.getShipAI() != DockingAI) {
                            fighter.setShipAI(DockingAI);
                            DockingAI.init();
                        }
                    } else {
                        boolean alreadyHasAI = false;
                        if (fighter.getCustomData().get("armaa_wingCommander_fighterRetreat_" + fighter.getId()) instanceof Boolean) {
                            alreadyHasAI = (Boolean) fighter.getCustomData().get("armaa_wingCommander_fighterRetreat_" + fighter.getId());
                        }
                        if (!alreadyHasAI) {
                            armaa_combat_retreat_AI_fighter RetreatAI = new armaa_combat_retreat_AI_fighter(fighter);
                            if (fighter.getShipAI() != RetreatAI);
                            {
                                fighter.setShipAI(RetreatAI);
                                fighter.getCustomData().put("armaa_wingCommander_fighterRetreat_" + fighter.getId(), true);
                                //DockingAI.init();
                            }
                        }

                    }
                }

                if (fighter.isLanding()) {
                    ShipAPI carrierLandingOn;
                    for (ShipAPI carrier : CombatUtils.getShipsWithinRange(fighter.getLocation(), 100f)) {
                        if (carrier.getOwner() != fighter.getOwner()) {
                            continue;
                        }
                        if (carrier.isFighter()) {
                            continue;
                        }
                        if ((!carrier.isFrigate() || carrier.isStationModule())) {
                            if (carrier.hasLaunchBays()) {
                                Global.getCombatEngine().getCustomData().put("armaa_wingCommander_landingLocation_" + ship.getId() + "_" + i, carrier);
                                break;
                            }
                        }
                    }
                }

                if (fighter.isFinishedLanding()) {
                    bay.land(fighter);
                    Global.getCombatEngine().removeEntity(fighter);
                }

            }
        }
    }
}
