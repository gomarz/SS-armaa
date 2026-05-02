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
import com.fs.starfarer.api.input.InputEventAPI;
import java.util.List;
import org.lazywizard.lazylib.combat.CombatUtils;

public class armaa_spareChassis extends BaseHullMod {

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, java.lang.String id) {
        if (ship.getOwner() < 0) {
            return;
        }
        if (!ship.hasListenerOfClass(armaa_chassisReplacementTracker.class)) {
            ship.addListener(new armaa_chassisReplacementTracker(ship, 2));
        }
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        return null;
    }

    public static class armaa_chassisReplacementTracker implements HullDamageAboutToBeTakenListener, AdvanceableListener {

        protected ShipAPI ship;
        protected int owner;
        protected IntervalUtil track = new IntervalUtil(10f, 10f);
        protected boolean startReplacement = false;
        protected boolean forceSync = false;
        protected FleetMemberAPI member;
        protected int spares = -999;
        protected ShipAPI newShip;

        private ShipAPI getCarrier(ShipAPI ship) {
            ShipAPI carrier = null;

            for (ShipAPI target : CombatUtils.getShipsWithinRange(ship.getLocation(), 100000)) {
                if (owner != target.getOwner()) {
                    continue;
                }
                if (!target.isAlive()) {
                    continue;
                }
                if(ship.getOwner() == 1 || ship.getOwner() == 0 && ship.isAlly())
                {
                    if(carrier == null)
                        carrier = target;
                }
                if (target.getVariant().hasHullMod("armaa_spare_chassis_storage")) {
                    carrier = target;
                }
            }
            return carrier;
        }

        public armaa_chassisReplacementTracker(ShipAPI ship, int spares) {
            this.ship = ship;
            this.owner = ship.getOwner();
            this.spares = spares;
        }

        @Override
        public void advance(float amount) {
            if (Global.getCombatEngine().isPaused()) {
                return;
            }
            Object cd = ship.getCustomData().get("armaa_spareChassis");
            // If not set yet, don't force-set it here; just wait a frame.
            if (cd == null) {
                ship.getCustomData().put("armaa_spareChassis", spares);
                return;
            }

            // Always keep local 'spares' in sync (or at least when it's uninitialized)
            if (spares == -999) {
                spares = (Integer) cd;
            } else {
                int cdVal = (Integer) cd;
                if (cdVal != spares) {
                    spares = cdVal; // simplest sync
                    ship.getCustomData().put("armaa_spareChassis", spares);
                }
            }
            if (startReplacement && !track.intervalElapsed()) {
                track.advance(amount);
            }
            if (track.intervalElapsed()) {
                ShipAPI carrier = getCarrier(ship);
                if (spares <= 0 || carrier == null) {
                    ship.removeListener(this);
                    return;
                }
                spares--;
                // sigh
                if (member == null && ship.getCaptain() != null) {
                    member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, ship.getVariant().clone());
                    PersonAPI commander = ship.getCaptain().getFleet() == null ? Global.getCombatEngine().getFleetManager(owner).getFleetCommander() : ship.getCaptain().getFleet().getCommander();
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
                Global.getCombatEngine().addPlugin(new armaa_spareChassisSpawner(member, carrier.getLocation(), ship.getFacing(), 5f, owner,spares));
                ship.removeListener(this);
            }
        }

        @Override
        public boolean notifyAboutToTakeHullDamage(Object param, ShipAPI ship, Vector2f point, float damageAmount) {
            if (ship.getCustomData().get("armaa_spareChassis") == null) {
                return false;
            }

            float hull = ship.getHitpoints();
            if (damageAmount >= hull) {
                startReplacement = true;
                return false;
            }
            return false;
        }
    }

    private static class armaa_spareChassisSpawner extends BaseEveryFrameCombatPlugin {

        private final FleetMemberAPI member;
        private final Vector2f location;
        private final float facing;
        private final int owner;
        private final int spares;
        private boolean spawnedShip = false;
        private ShipAPI ship;
        private ShipAPI replacement;

        public armaa_spareChassisSpawner(FleetMemberAPI member, Vector2f location, float facing, float f, int owner, int spares) {
            this.member = member;
            this.location = location;
            this.facing = facing;
            this.owner = owner;
            this.spares = spares;
        }

        @Override
        public void advance(float f, List<InputEventAPI> list) 
        {                
            if (!spawnedShip) {
                ship = Global.getCombatEngine().getFleetManager(owner).spawnFleetMember(member, location, facing, 0f);
                ship.setAnimatedLaunch();
                replacement = ship;
                replacement.setName(spares+"");
                spawnedShip = true;
            }
            int ogVal = spares;
            int newVal = 999;
            if (!spawnedShip) {
                return;
            }
            if (replacement.getCustomData().get("armaa_spareChassis") != null) {
                newVal = (Integer) replacement.getCustomData().get("armaa_spareChassis");
            }

            if (ogVal != newVal) {
                replacement.getCustomData().put("armaa_spareChassis", spares);
            } else {
                Global.getCombatEngine().removePlugin(this);
                return;
            }
        }
    }
}
