package data.scripts.plugins;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;

import data.scripts.campaign.missions.plugins.armaa_DustField3D;
import data.scripts.campaign.missions.plugins.armaa_SkySphereRenderer;
import data.scripts.campaign.missions.plugins.armaa_titleSplash;
import data.scripts.util.armaa_utils;

/**
 * Combat plugin for the Starfall ambush at the Pather installation.
 *
 * Attached by armaa_EinhanderHaxPlugin when the other fleet carries
 * $inSfoAmbushBattle, which armaa_starfallMission.spawnAmbush() sets.
 *
 * Shows the title card, and lets the ambush fleet's ships eject wings of
 * armaa_crew (fanatics in vacuum suits) as they take damage and when they die.
 * Draws a tumbling sky behind the fight (armaa_SkySphereRenderer). Music is stubbed out below.
 *
 * Ejections per ship, by hull size: frigate 1, destroyer 2, cruiser 3, capital 4.
 * All but the last go out at evenly spaced hull thresholds; the last goes out
 * when the ship dies. A frigate therefore only ejects on death. Wings are
 * queued rather than spawned at once, so the alive and per-battle caps hold.
 */
public class armaa_sfoAmbushBattlePlugin extends BaseEveryFrameCombatPlugin {

    public static final String CREW_WING = "armaa_crew_wing";
    public static final String CREW_HULL = "armaa_crew";
    public static final int CREW_PER_WING = 6;

    /** Crew alive at once. Wings past this wait in the queue. */
    public static final int MAX_ALIVE_CREW = 100;
    /** Crew spawned over the whole battle (42 = seven wings). */
    public static final int MAX_TOTAL_CREW = 999;

    /**
     * The sky's tumble is tuned for a fast frigate. Anything at or above this
     * top speed gets the full swing; slower or larger ships only get calmer,
     * never wilder, so a capital doesn't feel like it's spinning in a hurricane.
     */
    private static final float SKY_ZIPPY_SPEED = 200f;
    private static final float SKY_MIN_INTENSITY = 0.35f;

    protected CombatEngineAPI engine;
    private boolean started = false;
    private armaa_SkySphereRenderer sky;

    private final IntervalUtil scan = new IntervalUtil(0.25f, 0.25f);
    private final Map<ShipAPI, Ejector> tracked = new HashMap<ShipAPI, Ejector>();
    private final List<Vector2f> queue = new ArrayList<Vector2f>();
    private int crewSpawned = 0;

    /** What one ship has done so far, and where it last was. */
    private static class Ejector {

        int total;          // wings this ship will eject in all
        int ejected = 0;
        float[] thresholds; // hull levels at which the non-final wings go out
        Vector2f lastLoc = new Vector2f();
        float radius;
    }

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null || engine.isPaused()) {
            return;
        }

        // The city plugin does its setup on the first advance, not in init(),
        // so the same is done here.
        if (!started) {
            started = true;

            // Global.getSoundPlayer().playCustomMusic(1, 1, "music_armaa_sfoambush", true);

            // A tumbling sky behind the fight. armaa_sfo_sky is a 2:1 panorama; until
            // that image exists the renderer falls back to armaa_gravion.
            sky = new armaa_SkySphereRenderer("armaa_sfo_sky", "armaa_gravion");
            sky.setIntensity(skyIntensityForPlayerShip());
            engine.addLayeredRenderingPlugin(sky);

            // Two dust fields, as real 3D points seen through the sky's own camera. The first
            // slides with the player's ship and streaks a little at speed; the second is
            // fainter and slides with the view, for parallax when it moves. Both are white,
            // like vanilla's specks.
            // Size falls off with distance (sizePx / distance), clamped to the min and max. Most specks
            // sit 3 to 6 units out, so the min is what most of them are drawn at.
            engine.addLayeredRenderingPlugin(new armaa_DustField3D(sky, false, 900, 6f, 600f,
                    6f, 1.1f, 2.6f, 0.6f, 0.02f, 1f, Color.white, 1L));
            engine.addLayeredRenderingPlugin(new armaa_DustField3D(sky, true, 500, 9f, 1500f,
                    7f, 0.8f, 1.8f, 0.4f, 0f, 1f, Color.white, 2L));

            String minuteStr = armaa_utils.getMinuteString();
            engine.addLayeredRenderingPlugin(new armaa_titleSplash(
                    "PATHER INSTALLATION | " + Global.getSector().getClock().getHour() + ":" + minuteStr
                    + " | " + Global.getSector().getClock().getDateString(),
                    " UNIDENTIFIED INSTALLATION"));
        }

        scan.advance(amount);
        if (scan.intervalElapsed()) {
            trackShips();
            checkEjections();
            spawnQueued();
        }
    }

    /** 1 at or above SKY_ZIPPY_SPEED, backing off toward SKY_MIN_INTENSITY for slower ships. */
    private float skyIntensityForPlayerShip() {
        ShipAPI player = engine.getPlayerShip();
        if (player == null) {
            return 1f;
        }
        float speed = player.getMutableStats().getMaxSpeed().getModifiedValue();
        return Math.max(SKY_MIN_INTENSITY, Math.min(1f, speed / SKY_ZIPPY_SPEED));
    }

    /** Wings a ship of this size ejects in total. */
    private static int wingsFor(ShipAPI.HullSize size) {
        switch (size) {
            case FRIGATE:
                return 1;
            case DESTROYER:
                return 2;
            case CRUISER:
                return 3;
            case CAPITAL_SHIP:
                return 4;
            default:
                return 0;
        }
    }

    /** total-1 evenly spaced hull levels: 2 -> 0.5; 3 -> 0.66, 0.33; 4 -> 0.75, 0.5, 0.25. */
    private static float[] thresholdsFor(int total) {
        float[] t = new float[Math.max(0, total - 1)];
        for (int i = 0; i < t.length; i++) {
            t[i] = 1f - (i + 1f) / total;
        }
        return t;
    }

    private void trackShips() {
        for (ShipAPI ship : engine.getShips()) {
            if (ship.getOwner() != 1 || tracked.containsKey(ship)) {
                continue;
            }
            if (ship.isFighter() || ship.isDrone() || ship.isShuttlePod() || ship.isStationModule()
                    || CREW_HULL.equals(ship.getHullSpec().getHullId())) {
                continue;
            }
            int total = wingsFor(ship.getHullSize());
            if (total <= 0) {
                continue;
            }
            Ejector e = new Ejector();
            e.total = total;
            e.thresholds = thresholdsFor(total);
            e.radius = ship.getCollisionRadius();
            e.lastLoc.set(ship.getLocation());
            tracked.put(ship, e);
        }
    }

    private void checkEjections() {
        Iterator<Map.Entry<ShipAPI, Ejector>> it = tracked.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ShipAPI, Ejector> entry = it.next();
            ShipAPI ship = entry.getKey();
            Ejector e = entry.getValue();

            boolean dead = !ship.isAlive() || ship.isHulk();
            if (dead) {
                // whatever the thresholds did not release goes out with the ship
                while (e.ejected < e.total) {
                    queueWing(e.lastLoc, e.radius);
                    e.ejected++;
                }
                it.remove();
                continue;
            }
            if (!engine.isEntityInPlay(ship)) {
                it.remove();   // left the battle alive; nothing to eject
                continue;
            }

            e.lastLoc.set(ship.getLocation());
            float hull = ship.getHullLevel();
            while (e.ejected < e.thresholds.length && hull <= e.thresholds[e.ejected]) {
                queueWing(ship.getLocation(), e.radius);
                e.ejected++;
            }
        }
    }

    /** Picks a point just off the hull, so the crew visibly leave the ship. */
    private void queueWing(Vector2f shipLoc, float radius) {
        float angle = MathUtils.getRandomNumberInRange(0f, 360f);
        queue.add(MathUtils.getPointOnCircumference(shipLoc, radius * 0.6f, angle));
    }

    private int aliveCrew() {
        int n = 0;
        for (ShipAPI ship : engine.getShips()) {
            if (ship.getOwner() == 1 && ship.isAlive() && CREW_HULL.equals(ship.getHullSpec().getHullId())) {
                n++;
            }
        }
        return n;
    }

    private void spawnQueued() {
        if (queue.isEmpty()) {
            return;
        }
        CombatFleetManagerAPI fm = engine.getFleetManager(1);
        boolean quiet = fm.isSuppressDeploymentMessages();
        fm.setSuppressDeploymentMessages(true);
        int alive = aliveCrew();
        while (!queue.isEmpty()
                && alive + CREW_PER_WING <= MAX_ALIVE_CREW
                && crewSpawned + CREW_PER_WING <= MAX_TOTAL_CREW) {
            Vector2f loc = queue.remove(0);
            float facing = MathUtils.getRandomNumberInRange(0f, 360f);
            ShipAPI wing = fm.spawnShipOrWing(CREW_WING, loc, facing, 1f);
            if (wing != null) {
                wing.setAnimatedLaunch();
            }
            engine.spawnExplosion(loc, new Vector2f(), new Color(255, 170, 110, 255), 60f, 0.4f);
            alive += CREW_PER_WING;
            crewSpawned += CREW_PER_WING;
        }
        fm.setSuppressDeploymentMessages(quiet);
        // once the total cap is reached nothing more can spawn, so drop the queue
        if (crewSpawned + CREW_PER_WING > MAX_TOTAL_CREW) {
            queue.clear();
        }
    }
}
