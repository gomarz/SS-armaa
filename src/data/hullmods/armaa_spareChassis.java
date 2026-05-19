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
import java.util.List;
import org.lazywizard.lazylib.combat.CombatUtils;

public class armaa_spareChassis extends BaseHullMod {

    private static final String GLOBAL_POOL_KEY = "armaa_globalSpareChassisCount";
    private static final String GLOBAL_POOL_KEY_0 = "armaa_globalSpareChassisCount_ally";
    private static final String GLOBAL_POOL_KEY_1 = "armaa_globalSpareChassisCount_enemy";
    private static final String POOL_INITIALIZED_KEY = "armaa_globalSpareChassisInitialized";

    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getDynamic().getMod(Stats.DMOD_ACQUIRE_PROB_MOD)
                .modifyMult(id, 0f);
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        if (ship.getOwner() < 0) {
            return;
        }
        if (!ship.hasListenerOfClass(armaa_chassisReplacementTracker.class)) {
            ship.addListener(new armaa_chassisReplacementTracker(ship));
        }
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        return null;
    }

    static void initPoolIfNeeded(ShipAPI ship) {
        CombatEngineAPI engine = Global.getCombatEngine();
        boolean ai = ship.getOriginalOwner() == 1 || ship.getOriginalOwner() == 0 && ship.isAlly();
        if (!ai && engine.getCustomData().containsKey(POOL_INITIALIZED_KEY)) {
            return;
        } else if (engine.getCustomData().containsKey(POOL_INITIALIZED_KEY + ship.getOriginalOwner())) {
            return;
        }

        int fleetTotal = 0;
        if (!ai) {
            // Full fleet count including reserves
            for (FleetMemberAPI member : Global.getSector().getPlayerFleet()
                    .getFleetData().getMembersListCopy()) {
                if (member.getVariant() == null) {
                    continue;
                }
                if (!member.getVariant().hasHullMod("armaa_spare_chassis_storage")) {
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
        } else {
            for (ShipAPI s : engine.getShips()) {
                if (!s.isHulk() && s.getOriginalOwner() == ship.getOriginalOwner()
                        && s.getVariant() != null
                        && s.hasLaunchBays()) {
                    switch (s.getHullSize()) {
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
        if (ship.getOwner() == 0 && !ship.isAlly()) {
            engine.getCustomData().put(GLOBAL_POOL_KEY, fleetTotal);
        } else {
            engine.getCustomData().put(GLOBAL_POOL_KEY + "_" + ship.getOriginalOwner(), fleetTotal);
        }
        if (!ai) {
            engine.getCustomData().put(POOL_INITIALIZED_KEY, true);
        } else {
            engine.getCustomData().put(POOL_INITIALIZED_KEY + ship.getOriginalOwner(), true);
        }
        if (!ai) {
            engine.getCombatUI().addMessage(1, Global.getSettings().getBrightPlayerColor(),fleetTotal + " spare chassis available.");
        } else if (ship.getOriginalOwner() == 1) {
            // Optional — remove this if you don't want the player to see enemy pool info
            engine.getCombatUI().addMessage(1,Misc.getNegativeHighlightColor(), "Enemy spare chassis available: " + fleetTotal);
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
            Global.getCombatEngine().getCombatUI().addMessage(1, "All spare chassis have been expended.");
        } else if (newVal <= 0 && ship.getOriginalOwner() == 1) {
            Global.getCombatEngine().getCombatUI().addMessage(1, "All enemy spare chassis have been expended.");
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
                if (target.getVariant().hasHullMod("armaa_spare_chassis_storage") || !isPlayerShip) {
                    Global.getLogger(this.getClass()).info("Found carrier for: " + ship.getName() + " " + owner + " vs " + ship.getOriginalOwner());
                    carrier = target;
                    break;
                }
            }
            return carrier;
        }

        @Override
        public void advance(float amount) {
            if (Global.getCombatEngine().isPaused()) {
                return;
            }

            initPoolIfNeeded(ship);

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

            if (member == null && ship.getCaptain() != null) {
                member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, ship.getVariant().clone());
                PersonAPI commander = ship.getCaptain().getFleet() == null
                        ? Global.getCombatEngine().getFleetManager(ship.getOriginalOwner()).getFleetCommander()
                        : ship.getCaptain().getFleet().getCommander();
                if (commander.getFleet() != null) {
                    member.setFleetCommanderForStats(commander, commander.getFleet().getFlagship().getFleetDataForStats());
                }
                member.setCaptain(ship.getCaptain());
                member.setOwner(ship.getOriginalOwner());
                member.getCrewComposition().setCrew(member.getMinCrew());
                member.getRepairTracker().setCR(Math.min(1f, ship.getCRAtDeployment()));
                member.getRepairTracker().performRepairsFraction(1f);
                member.updateStats();
            }

            Global.getCombatEngine().getFleetManager(ship.getOriginalOwner()).addToReserves(member);
            Global.getCombatEngine().addPlugin(new armaa_spareChassisSpawner(member, carrier.getLocation(), ship.getFacing(), ship.getOriginalOwner(), isPlayerShip));
            deductFromPool(ship);
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

        public armaa_spareChassisSpawner(FleetMemberAPI member, Vector2f location, float facing, int owner, boolean isPlayerShip) {
            this.member = member;
            this.location = location;
            this.facing = facing;
        }

        @Override
        public void advance(float f, List<InputEventAPI> list) {
            if (spawnedShip) {
                return;
            }
            if (location != null) {
                ShipAPI replacement = Global.getCombatEngine().getFleetManager(member.getOwner()).spawnFleetMember(member, location, facing, 0f);
                replacement.setAnimatedLaunch();
                replacement.setName(replacement.getName() + " [spare]");

                spawnedShip = true;
            }
            Global.getCombatEngine().removePlugin(this);
        }
    }
}
