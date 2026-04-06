package data.scripts.plugins;
 
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;
import data.scripts.util.armaa_utils;
import org.lwjgl.util.vector.Vector2f;
 
import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.lazywizard.lazylib.combat.CombatUtils;
 
/**
 * Manages staggered carrier launches for armaa strikecraft.
 *
 * Ships are queued here by armaa_TravelDriveStats when they first deploy.
 * While waiting, ships are tucked into their carrier (landing animation,
 * CollisionClass.NONE). When their delay expires the manager fires the
 * full launch sequence independently of the travel drive.
 *
 * Register once per combat via:
 *   if (!engine.hasPlugin(armaa_CarrierLaunchManager.class))
 *       engine.addPlugin(new armaa_CarrierLaunchManager());
 */
public class armaa_CarrierLaunchManager extends BaseEveryFrameCombatPlugin {
 
    // --- Tunables ---
    /** Base delay before the first ship launches (seconds) */
    private static final float BASE_DELAY    = 0.3f;
    /** Additional random stagger per ship (seconds) */
    private static final float STAGGER_RANGE = 2.5f;
 
    // ---- Internal entry ----
    private static class LaunchEntry {
        ShipAPI ship;
        ShipAPI carrier;
        float   delay;      // seconds remaining
 
        LaunchEntry(ShipAPI ship, ShipAPI carrier, float delay) {
            this.ship    = ship;
            this.carrier = carrier;
            this.delay   = delay;
        }
    }
 
    private final List<LaunchEntry> queue = new ArrayList<>();
 
    // ---- Public API ----
 
    /**
     * Queue a ship for a staggered launch.
     * Call from armaa_TravelDriveStats before doing anything else.
     * The ship will be tucked into the carrier immediately.
     */
    public void queueLaunch(ShipAPI ship, ShipAPI carrier) {
        float delay = BASE_DELAY + (float)(Math.random() * STAGGER_RANGE);
        if(Global.getCombatEngine().getPlayerShip() == ship)
            delay = 0.5f;
        queue.add(new LaunchEntry(ship, carrier, delay));
        Global.getLogger(armaa_CarrierLaunchManager.class)
            .info("queueLaunch called for " + ship.getHullSpec().getHullId()); 
        // Tuck the ship away immediately
        //ship.setControlsLocked(true);        
        ship.setAnimatedLaunch();
        //ship.setCollisionClass(CollisionClass.NONE);
    }
 
    // ---- EveryFrameCombatPlugin ----
 
    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (Global.getCombatEngine().isPaused()) return;
        if (queue.isEmpty()) return;
 
        Iterator<LaunchEntry> it = queue.iterator();
        while (it.hasNext()) {
            LaunchEntry entry = it.next();
            entry.ship.setAnimatedLaunch();
            // Sanity check — carrier or ship may have died while waiting
            if (!Global.getCombatEngine().isEntityInPlay(entry.ship)
                    || !Global.getCombatEngine().isEntityInPlay(entry.carrier)
                    || entry.carrier.isHulk()) {
                it.remove();
                continue;
            }
 
            if (entry.delay > 0f) {
                // Still waiting — keep ship parked on carrier
                armaa_utils.setLocation(entry.ship, entry.carrier.getLocation());
                entry.ship.getVelocity().set(entry.carrier.getVelocity());
                            entry.delay -= amount;
                continue;
            }
 
            // Delay expired fire launch sequence
            fireLaunch(entry.ship, entry.carrier);
            it.remove();
        }
    }
 
    private void fireLaunch(ShipAPI ship, ShipAPI carrier) {
        // Restore collision
        ship.setControlsLocked(false);      
 
        // Find a free launch bay slot on the carrier
        Vector2f takeOffLoc = null;
        for (com.fs.starfarer.api.loading.WeaponSlotAPI wep : carrier.getHullSpec().getAllWeaponSlotsCopy()) {
            if (Global.getCombatEngine().getCustomData()
                    .get("armaa_launchSlots" + carrier.getId() + "_" + wep.getId()) != null) {
                continue;
            }
            if (wep.getWeaponType() == com.fs.starfarer.api.combat.WeaponAPI.WeaponType.LAUNCH_BAY) {
                if (Global.getCombatEngine().getPlayerShip() == ship) {
                    Global.getSoundPlayer().playSound("ui_noise_static", 1f, 1f,
                            carrier.getLocation(), new Vector2f());
                    carrier.getFluxTracker().showOverloadFloatyIfNeeded(
                            "Good luck out there!", Color.white, 2f, true);
                }
                ship.setFacing(carrier.getFacing() + wep.getAngle());
                takeOffLoc = new Vector2f(wep.computePosition(carrier));
                Global.getCombatEngine().getCustomData()
                        .put("armaa_launchSlots" + carrier.getId() + "_" + wep.getId(), "-");
                break;
            }
        }
 
        if (takeOffLoc == null) {
            takeOffLoc = new Vector2f(carrier.getLocation());
        }
 
        ship.setLaunchingShip(carrier);
        armaa_utils.setLocation(ship, takeOffLoc);
        ship.setAnimatedLaunch();
        Global.getSoundPlayer().playSound("fighter_takeoff", 1f, 1f, ship.getLocation(), new Vector2f());
        CombatUtils.applyForce(
                ship, ship.getFacing(),
                (float) Math.random() * carrier.getMaxSpeed() * 0.50f);
    }
}