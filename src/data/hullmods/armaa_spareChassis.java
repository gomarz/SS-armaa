package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.util.IntervalUtil;

import com.fs.starfarer.api.combat.listeners.*;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.input.InputEventAPI;
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
        if (engine.getCustomData().containsKey(POOL_INITIALIZED_KEY)) {
            return;
        }

        int fleetTotal = 0;
        for (ShipAPI s : engine.getShips()) {
            if (s.getOwner() == ship.getOwner()
                    && !s.isHulk()
                    && s.getVariant() != null
                    && s.getVariant().hasHullMod("armaa_spare_chassis_storage")) {
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

        engine.getCustomData().put(GLOBAL_POOL_KEY, fleetTotal);
        engine.getCustomData().put(POOL_INITIALIZED_KEY, true);
    }

    static int getPool() {
        Object val = Global.getCombatEngine().getCustomData().get(GLOBAL_POOL_KEY);
        if (val == null) {
            return 0;
        }
        return (Integer) val;
    }

    static void deductFromPool() {
        int newVal = Math.max(0, getPool() - 1);
        Global.getCombatEngine().getCustomData().put(GLOBAL_POOL_KEY, newVal);
        if (newVal <= 0) {
            Global.getCombatEngine().getCombatUI().addMessage(1, "All spare chassis have been expended.");
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
            this.owner = ship.getOwner();
            this.isPlayerShip = (ship.getOwner() == 0 && !ship.isAlly());
        }

        private ShipAPI getCarrier() {
            ShipAPI carrier = null;
            for (ShipAPI target : CombatUtils.getShipsWithinRange(ship.getLocation(), 100000)) {
                if (owner != target.getOwner()) {
                    continue;
                }
                if (!target.isAlive()) {
                    continue;
                }
                if (carrier == null) {
                    carrier = target;
                }
                if (target.getVariant().hasHullMod("armaa_spare_chassis_storage")) {
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

            if (isPlayerShip) {
                initPoolIfNeeded(ship);

                poolCheckTrack.advance(amount);
                if (poolCheckTrack.intervalElapsed() && getPool() <= 0) {
                    ship.removeListener(this);
                    return;
                }
            } else {
                // AI/ally: already used their one respawn
                if (hasRespawned) {
                    ship.removeListener(this);
                    return;
                }
            }

            if (!startReplacement) {
                return;
            }

            respawnTrack.advance(amount);
            if (!respawnTrack.intervalElapsed()) {
                return;
            }

            // Pool check at spawn time for player ships
            if (isPlayerShip && getPool() <= 0) {
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
                        ? Global.getCombatEngine().getFleetManager(owner).getFleetCommander()
                        : ship.getCaptain().getFleet().getCommander();
                if (commander.getFleet() != null) {
                    member.setFleetCommanderForStats(commander, commander.getFleet().getFlagship().getFleetDataForStats());
                }
                member.setCaptain(ship.getCaptain());
                member.setOwner(owner);
                member.getCrewComposition().setCrew(member.getMinCrew());
                member.getRepairTracker().setCR(Math.min(1f, ship.getCRAtDeployment()));
                member.getRepairTracker().performRepairsFraction(1f);
                member.updateStats();
            }

            Global.getCombatEngine().getFleetManager(owner).addToReserves(member);
            Global.getCombatEngine().addPlugin(new armaa_spareChassisSpawner(member, carrier.getLocation(), ship.getFacing(), owner, isPlayerShip));

            if (isPlayerShip) {
                deductFromPool();
            } else {
                hasRespawned = true;
            }

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
        private final int owner;
        private final boolean isPlayerShip;
        private boolean spawnedShip = false;

        public armaa_spareChassisSpawner(FleetMemberAPI member, Vector2f location, float facing, int owner, boolean isPlayerShip) {
            this.member = member;
            this.location = location;
            this.facing = facing;
            this.owner = owner;
            this.isPlayerShip = isPlayerShip;
        }

        @Override
        public void advance(float f, List<InputEventAPI> list) {
            if (spawnedShip) {
                return;
            }

            ShipAPI replacement = Global.getCombatEngine().getFleetManager(owner).spawnFleetMember(member, location, facing, 0f);
            replacement.setAnimatedLaunch();
            replacement.setName(replacement.getName() + " [spare]");

            if (!replacement.hasListenerOfClass(armaa_chassisReplacementTracker.class)) {
                armaa_chassisReplacementTracker tracker = new armaa_chassisReplacementTracker(replacement);
                tracker.isPlayerShip = isPlayerShip; // carry through
                replacement.addListener(tracker);
            }

            spawnedShip = true;
            Global.getCombatEngine().removePlugin(this);
        }
    }
}
