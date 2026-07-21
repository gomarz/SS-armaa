package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import static com.fs.starfarer.api.combat.ShipAPI.HullSize.CAPITAL_SHIP;
import static com.fs.starfarer.api.combat.ShipAPI.HullSize.CRUISER;
import static com.fs.starfarer.api.combat.ShipAPI.HullSize.DESTROYER;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.util.IntervalUtil;

import com.fs.starfarer.api.combat.listeners.*;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.Misc;
import java.awt.Color;
import java.util.List;
import org.lazywizard.lazylib.combat.CombatUtils;

public class armaa_spareChassis extends BaseHullMod {

    private static final String GLOBAL_POOL_KEY = "armaa_globalSpareChassisCount";
    private static final String POOL_INITIALIZED_KEY = "armaa_globalSpareChassisInitialized";

    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getDynamic().getMod(Stats.DMOD_ACQUIRE_PROB_MOD)
                .modifyMult(id, 0f);
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (Global.getCombatEngine() == null || Global.getCombatEngine().isPaused()) {
            return;
        }
        if(!ship.isAlive() || ship.isHulk())
            return;
        //Global.getCombatEngine().getCombatUI().addMessage(0, Global.getCombatEngine().getCustomData().containsKey("armaa_globalSpareChassisInitialized"));
        if (ship.getOriginalOwner() < 0) {
            return;
        }
        if (Global.getCombatEngine().getTotalElapsedTime(false) > 1) {
            if (!ship.isStationModule() && !ship.hasListenerOfClass(armaa_chassisReplacementTracker.class)) {
                if (!ship.getCustomData().containsKey("armaa_addedReplacementTracker")) {
                    ship.addListener(new armaa_chassisReplacementTracker(ship));
                    initPoolIfNeeded(ship);
                    ship.getCustomData().put("armaa_addedReplacementTracker", "-");
                }
            }
        }
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        return null;
    }

    static void initPoolIfNeeded(ShipAPI ship) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) {
            return;
        }
        boolean ai = ship.getOriginalOwner() == 1 || ship.getOriginalOwner() == 0 && ship.isAlly();
        if (!ai && engine.getCustomData().containsKey(POOL_INITIALIZED_KEY)) {
            return;
        } else if (ai && engine.getCustomData().containsKey(POOL_INITIALIZED_KEY + ship.getOriginalOwner())) {
            return;
        }

        int fleetTotal = 0;
        if (!ai) {
            if (Global.getSector().getPlayerFleet() != null) {
                for (FleetMemberAPI member : Global.getSector().getPlayerFleet()
                        .getFleetData().getMembersListCopy()) {
                    if (member.getVariant() == null) {
                        continue;
                    }
                    if (!member.getVariant().getHullMods().contains("armaa_spare_chassis_storage")) {
                        continue;
                    }
                    switch (member.getHullSpec().getHullSize()) {
                        case DESTROYER:
                            fleetTotal += 1;
                            break;
                        case CRUISER:
                            fleetTotal += 2;
                            break;
                        case CAPITAL_SHIP:
                            fleetTotal += 3;
                            break;
                        default:
                            break;
                    }
                }
            }
        } else {
            for (FleetMemberAPI member : engine.getFleetManager(ship.getOriginalOwner()).getReservesCopy()) {
                if (member.isCarrier()) {
                    switch (member.getHullSpec().getHullSize()) {
                        case DESTROYER:
                            fleetTotal += 1;
                            break;
                        case CRUISER:
                            fleetTotal += 2;
                            break;
                        case CAPITAL_SHIP:
                            fleetTotal += 3;
                            break;
                        default:
                            break;
                    }
                }
            }
        }
        if (ship.getOriginalOwner() == 0 && !ship.isAlly()) {
            engine.getCustomData().put(GLOBAL_POOL_KEY, fleetTotal);
        } else {
            if (fleetTotal > 10) {
                fleetTotal = 10;
            }
            engine.getCustomData().put(GLOBAL_POOL_KEY + "_" + ship.getOriginalOwner(), fleetTotal);
        }
        if (!ai) {
            engine.getCustomData().put(POOL_INITIALIZED_KEY, true);
        } else {

            engine.getCustomData().put(POOL_INITIALIZED_KEY + ship.getOriginalOwner(), true);
        }

        if (!ai) {
            engine.getCombatUI().addMessage(1, Global.getSettings().getBrightPlayerColor(), fleetTotal + " spare chassis available.");
        } else if (ship.getOriginalOwner() == 1) {
            engine.getCombatUI().addMessage(1, Misc.getNegativeHighlightColor(), "Enemy spare chassis available: " + fleetTotal);
        } else if (ship.isAlly()) {
            engine.getCombatUI().addMessage(1, Misc.getHighlightColor(), "Ally spare chassis available: " + fleetTotal);
        }

    }

    static int getPool(ShipAPI ship) {
        Object val = Global.getCombatEngine().getCustomData().get(GLOBAL_POOL_KEY);
        if (ship.getOriginalOwner() == 0 && ship.isAlly() || ship.getOriginalOwner() == 1) {
            val = Global.getCombatEngine().getCustomData().get(GLOBAL_POOL_KEY + "_" + ship.getOriginalOwner());
        }
        if (val == null) {
            return 0;
        }
        return (Integer) val;
    }

    //int message = (String)Global.getCombatEngine().getCustomData().get("armaa_globalSpareChassisCount" + "_" + 1);
    //Global.getCombatEngine().getCombatUI().addMessage(0,message+"");
    static void deductFromPool(ShipAPI ship) {
        int newVal = Math.max(0, getPool(ship) - 1);
        if (ship.getOriginalOwner() == 0 && ship.isAlly() || ship.getOriginalOwner() == 1) {
            Global.getCombatEngine().getCustomData().put(GLOBAL_POOL_KEY + "_" + ship.getOriginalOwner(), newVal);
        } else {
            Global.getCombatEngine().getCustomData().put(GLOBAL_POOL_KEY, newVal);
        }
        if (newVal <= 0 && ship.getOriginalOwner() == 0 && !ship.isAlly()) {
            Global.getCombatEngine().getCombatUI().addMessage(1,Color.red, "All spare chassis have been expended.");
        } else if (newVal <= 0 && ship.getOriginalOwner() == 1) {
            Global.getCombatEngine().getCombatUI().addMessage(1,Misc.getPositiveHighlightColor(), "All enemy spare chassis have been expended.");
        }
    }

    public static class armaa_chassisReplacementTracker implements HullDamageAboutToBeTakenListener, AdvanceableListener {

        protected ShipAPI ship;
        protected int owner;
        protected boolean isPlayerShip;
        protected IntervalUtil respawnTrack = new IntervalUtil(10f, 10f);
        protected IntervalUtil poolCheckTrack = new IntervalUtil(2.5f, 2.5f);
        protected boolean startReplacement = false;
        protected boolean hasRespawned = false; // for AI/ally one-shot
        protected FleetMemberAPI member;

        public armaa_chassisReplacementTracker(ShipAPI ship) {
            this.ship = ship;
            this.owner = ship.getOriginalOwner();
            this.isPlayerShip = (ship.getOriginalOwner() == 0 && !ship.isAlly());
        }

        private ShipAPI getCarrier() {
            ShipAPI carrier = null;
            for (ShipAPI target : CombatUtils.getShipsWithinRange(ship.getLocation(), 100000)) {
                if (ship.getOriginalOwner() != target.getOwner()) {
                    continue;
                }
                if (target.isFrigate()) {
                    continue;
                }
                if (!target.isAlive()) {
                    continue;
                }
                if (carrier == null) {
                    carrier = target;
                }
                if (carrier == null) {
                    continue;
                }
                // Why does this matter? If they have enough spares, just spawn it from any carrier
                if (target.hasLaunchBays()) {
                    //Global.getLogger(this.getClass()).info("Found carrier for: " + ship.getName() + " " + owner + " vs " + ship.getOriginalOwner());
                    carrier = target;
                    break;
                }
            }
            return carrier;
        }

        @Override
        public void advance(float amount) {
            if (Global.getCombatEngine() == null || Global.getCombatEngine().isPaused()) {
                return;
            }
            if (hasRespawned) {
                ship.removeListener(this);                
                return;
            }
            poolCheckTrack.advance(amount);
            if (poolCheckTrack.intervalElapsed() && getPool(ship) <= 0) {
                ship.removeListener(this);
                return;
            }

            if (!startReplacement) {
                return;
            }

            respawnTrack.advance(amount);
            if (!respawnTrack.intervalElapsed()) {
                return;
            }

            // Pool check at spawn time for player ships
            if (getPool(ship) <= 0) {
                ship.removeListener(this);
                return;
            }

            ShipAPI carrier = getCarrier();
            if (carrier == null) {
                ship.removeListener(this);
                return;
            }

            if (member == null) {
                member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, ship.getVariant().clone());
                if (ship.getCaptain() != null) {
                    PersonAPI commander = ship.getCaptain().getFleet() == null
                            ? Global.getCombatEngine().getFleetManager(ship.getOriginalOwner()).getFleetCommander()
                            : ship.getCaptain().getFleet().getCommander();
                    if (commander.getFleet() != null) {
                        member.setFleetCommanderForStats(commander, commander.getFleet().getFlagship().getFleetDataForStats());
                    }
                    member.setCaptain(ship.getCaptain());
                }
                member.setOwner(ship.getOriginalOwner());
                member.getCrewComposition().setCrew(member.getMinCrew());
                member.getRepairTracker().setCR(Math.min(1f, ship.getCRAtDeployment()));
                member.getRepairTracker().performRepairsFraction(1f);
                member.updateStats();
                member.setAlly(ship.isAlly());
            }

            Global.getCombatEngine().getFleetManager(ship.getOriginalOwner()).addToReserves(member);
            Global.getCombatEngine().addPlugin(new armaa_spareChassisSpawner(member, carrier.getLocation(), ship.getFacing(), ship.getOriginalOwner(), member.isAlly()));
            deductFromPool(ship);
            if(ship.getOriginalOwner() == 0 && !ship.isAlly())
            Global.getCombatEngine().getCombatUI().addMessage(0,ship,Global.getSettings().getBrightPlayerColor(), "Consumed spare chassis for " + ship.getHullSpec().getHullNameWithDashClass()+", " + getPool(ship) + " remain.");

            hasRespawned = true;
            ship.removeListener(this);
        }

        @Override
        public boolean notifyAboutToTakeHullDamage(Object param, ShipAPI ship, Vector2f point, float damageAmount) {

            float hull = ship.getHitpoints();
            if (damageAmount >= hull) {
                startReplacement = true;
            }
            return false;
        }
    }

    private static class armaa_spareChassisSpawner extends BaseEveryFrameCombatPlugin {

        private final FleetMemberAPI member;
        private final Vector2f location;
        private final float facing;
        private boolean spawnedShip = false;
        private boolean isAlly;

        public armaa_spareChassisSpawner(FleetMemberAPI member, Vector2f location, float facing, int owner, boolean isAlly) {
            this.member = member;
            this.location = location;
            this.facing = facing;
            this.isAlly = isAlly;
        }

        @Override
        public void advance(float f, List<InputEventAPI> list) {
            if (spawnedShip) {
                return;
            }
            if (location != null) {
                ShipAPI replacement = Global.getCombatEngine().getFleetManager(member.getOwner()).spawnFleetMember(member, location, facing, 0f);
                replacement.setAnimatedLaunch();
                replacement.setAlly(isAlly);
                replacement.setName(replacement.getName() + "-" + member.getOwner() + " [spare]");

                spawnedShip = true;
            }
            Global.getCombatEngine().removePlugin(this);
        }
    }
}
