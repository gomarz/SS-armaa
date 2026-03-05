package data.scripts.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

import java.util.*;
import java.awt.Color;

import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.combat.dweller.WarpingSpriteRendererUtilV2;
import com.fs.starfarer.api.mission.FleetSide;
import org.lazywizard.lazylib.MathUtils;

public class armaa_cityBattlePlugin extends BaseEveryFrameCombatPlugin {

    protected CombatEngineAPI engine;

    private final IntervalUtil interval = new IntervalUtil(.05f, .05f);
    private final IntervalUtil cloudInterval = new IntervalUtil(3f, 5f);
    private final IntervalUtil cloudInterval2 = new IntervalUtil(1f, 1f);
    private final IntervalUtil interval2 = new IntervalUtil(1f, 3f);
    private final IntervalUtil interval3 = new IntervalUtil(5f, 5f);
    private final IntervalUtil spinInterval = new IntervalUtil(0.08f, 0.08f);

    private float ratio = 0f;
    private float spin = 0f;

    public final static Map MANUVER_MALUS = new HashMap();
    boolean reinforcementsTriggered = false, wave2Triggered = false, timeUp = false;
    ShipAPI ceylon;

    //TODO refactor this and create helper functions for what we can reuse here
    // in other plugins
    // ---------------- BG STATE MACHINE (REWRITTEN) ----------------
    private enum BgPhase {
        FOREST_SCROLL,
        FOREST_OCEAN_TRANS,
        OCEAN_SCROLL,
        CITY_DROP,
        CITY_HOLD
    }

    private BgPhase bgPhase = BgPhase.FOREST_SCROLL;
    private float phaseElapsed = 0f;
// battlespace conveyor sizing
    // Ratio triggers (ratio = elapsedInContactWithEnemy / 100)
    private static final float RATIO_START_TRANS = 0.35f;
    private static final float RATIO_START_CITY = 1f;
    // Durations

    private static final float DUR_CITY_DROP = 2f;

    // City spin
    private float citySpin = 0f;
    private float citySpinVel = 0f;
    private static final float CITY_SPIN_ACCEL = 0.20f;
    private static final float CITY_SPIN_MAX_VEL = 0.35f;
    // --- scrolling state (tile conveyor) ---
    private String bgCurr;
    private String bgNext;
// Scroll timing: one full tile passes in this many seconds
    private static final float SECONDS_PER_TILE = 10f;
    private SpriteAPI oceanSpr;
// 0..1 progress through the current tile (fractional scroll)
    private float bgScrollFrac = 0f;
// one-shot transition control
    private boolean transConsumed = false;
    private boolean transArmed = false;       // condition met, we want to begin transition sequence
    private boolean oceanCommitted = false;   // TRANS fully passed and we are truly in ocean
    // Sprites (SET THESE TO YOUR REAL KEYS)
    private static final String SPR_FOREST = "armaa_atmo3";          // phase 1 scrolling
    private static final String SPR_TRANS = "armaa_atmo4";          // single transition tile (forest->ocean)
    private static final String SPR_OCEAN = "armaa_atmo5";          // phase 2 scrolling
    private static final String SPR_CITY = "armaa_city2";          // city

    // --------------------------------------------------------------
    static {
        MANUVER_MALUS.put(HullSize.FIGHTER, 0.90f);
        MANUVER_MALUS.put(HullSize.FRIGATE, 0.80f);
        MANUVER_MALUS.put(HullSize.DESTROYER, 0.60f);
        MANUVER_MALUS.put(HullSize.CRUISER, 0.50f);
        MANUVER_MALUS.put(HullSize.CAPITAL_SHIP, 0.40f);
    }
    private WarpingSpriteRendererUtilV2 ocean;

    public Color shiftColor(Color start, Color end) {
        int steps = 100;
        float r = (float) engine.getElapsedInContactWithEnemy() / steps;
        if (r >= 1f) {
            return end;
        }

        int red = (int) (start.getRed() * (1 - r) + end.getRed() * r);
        int green = (int) (start.getGreen() * (1 - r) + end.getGreen() * r);
        int blue = (int) (start.getBlue() * (1 - r) + end.getBlue() * r);
        int alpha = (int) (start.getAlpha() * (1 - r) + end.getAlpha() * r);
        return new Color(red, green, blue, alpha);
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null) {
            return;
        }

        if (!reinforcementsTriggered) {
            engine.setHyperspaceMode();
            oceanSpr = Global.getSettings().getSprite("misc", SPR_OCEAN);
            ocean = new WarpingSpriteRendererUtilV2(oceanSpr, 10, 10, 10f, 15f, 1f);
            engine.setBackgroundGlowColor(shiftColor(new Color(50, 50, 50, 80), new Color(75, 50, 0, 25)));
            engine.getFleetManager(0).setSuppressDeploymentMessages(true);
            Global.getSoundPlayer().playCustomMusic(1, 1, "music_armaa_citybattle", true);
            SpriteAPI spr = Global.getSettings().getSprite("mission_splash", "armaa_at_splash");
            MagicRender.screenspace(
                    spr,
                    MagicRender.positioning.CENTER,
                    new Vector2f(0, 0),
                    new Vector2f(0, 5),
                    new Vector2f((spr.getWidth() * 0.70f), (spr.getHeight() * 0.70f)),
                    new Vector2f(50f, 50f),
                    0f,
                    0f, //spin
                    Global.getSettings().getColor("textFriendColor"),
                    true,
                    0f,
                    0f,
                    0f,
                    0f,
                    0f,
                    1f,
                    5f,
                    1f,
                    CombatEngineLayers.ABOVE_PARTICLES
            );
            PersonAPI pilot3 = Global.getSector().getImportantPeople().getPerson("armaa_redeye");
            ShipAPI f;

            f = engine.getFleetManager(0).spawnShipOrWing("armaa_xyphos_standard", new Vector2f(-500, 0), 270f, 0f);
            f.setOwner(0);
            f.setOriginalOwner(0);
            f.setAlly(true);

            f = engine.getFleetManager(0).spawnShipOrWing("hyperion_Attack", new Vector2f(-100, 0), 90f, 0f);
            f.setAlly(true);

            f = engine.getFleetManager(0).spawnShipOrWing("armaa_einhander_boss", new Vector2f(0, 0), 270f, 0f);
            f.setCaptain(pilot3);
            f.setAlly(true);

            engine.getCombatUI().addMessage(
                    1, f,
                    Misc.getBasePlayerColor(), "Deadeye",
                    Color.white, ":",
                    Color.white,
                    "" + Global.getSector().getPlayerPerson().getRank() + " "
                    + Global.getSector().getPlayerPerson().getName().getLast()
                    + "?! They're throwing everything they have at us! We'll follow your lead!"
            );

            f = engine.getFleetManager(0).spawnShipOrWing("tempest_Attack", new Vector2f(-100, 0), 90f, 6000f);
            f.setAlly(true);

            f = engine.getFleetManager(0).spawnShipOrWing("heron_Attack2", new Vector2f(-500, 0), 80f, 3f);
            f.setAlly(true);

            f = engine.getFleetManager(0).spawnShipOrWing("armaa_leynos_jeniusCity", new Vector2f(-500, 1050), 90f, 0f);
            f.setAlly(true);
            engine.getFleetManager(0).setSuppressDeploymentMessages(false);
            reinforcementsTriggered = true;
        }

        if (!engine.isPaused()) {
            ratio = (float) engine.getElapsedInContactWithEnemy() / 100f;

            interval3.advance(amount);
            interval2.advance(amount);

            // NEW background flow
            advanceBackground(amount, ratio);

            float mult = engine.getViewport().getViewMult();
            float minX = engine.getViewport().getLLX();
            float maxX = engine.getViewport().getLLX() + engine.getViewport().getVisibleWidth();

            if ((ratio > 3f && !timeUp) || (ceylon != null && !ceylon.isAlive() && !timeUp)) {
                if (ceylon != null && ceylon.isAlive()) {
                    engine.getCombatUI().addMessage(1, ceylon, Color.red, ceylon.getName(), Color.white, ":", Color.white,
                            "...the city is lost. All remaining units, pull back.");
                    ceylon.setRetreating(true, true);
                    engine.getFleetManager(1).getTaskManager(false)
                            .orderRetreat(engine.getFleetManager(1).getDeployedFleetMember(ceylon), true, false);
                    ceylon = null;
                }
                timeUp = true;
            }

            if (!wave2Triggered && ((ratio > 0.1f && engine.getFleetManager(1).getCurrStrength() < 25)
                    || ratio >= 2f || engine.getFleetManager(1).getCurrStrength() < 25)) {

                boolean losing = engine.getFleetManager(1).getCurrStrength() < 25;
                PersonAPI pilot = OfficerManagerEvent.createOfficer(engine.getFleetManager(0).getDefaultCommander().getFaction(), 5, true);

                ShipAPI f = engine.getFleetManager(1).spawnShipOrWing("armaa_ceylon_boss", new Vector2f(0, 10000), 270f, 30f);
                engine.getFleetManager(1).spawnShipOrWing("armaa_bassline_standard", new Vector2f(-200, 10000), 270f, 45f);
                engine.getFleetManager(1).spawnShipOrWing("armaa_bassline_standard", new Vector2f(400, 10000), 270f, 45f);
                engine.getFleetManager(1).spawnShipOrWing("armaa_bassline_standard", new Vector2f(-400, 10000), 270f, 45f);

                f.setName("DAS Ceylon");
                f.setCaptain(pilot);

                if (losing) {
                    engine.getCombatUI().addMessage(1, f, Color.red, f.getName(), Color.white, ":", Color.white,
                            "Didn't expect you to carve through the vanguard like that. This was supposed to be a trap. All remaining units, close in and engage!");
                } else {
                    engine.getCombatUI().addMessage(1, f, Color.red, f.getName(), Color.white, ":", Color.white,
                            "DAS Ceylon inbound. Scum in the outskirts put up a fight.. pointless, of course. Still more trash to clear. Beginning approach.");
                }

                pilot = OfficerManagerEvent.createOfficer(engine.getFleetManager(0).getDefaultCommander().getFaction(), 10, true);
                for (int i = 0; i < 12; i++) {
                    f = engine.getFleetManager(1).spawnShipOrWing("armaa_morganamp_standard",
                            new Vector2f(MathUtils.getRandomNumberInRange(-2000, 2000),
                                    MathUtils.getRandomNumberInRange(-10000, 10000)),
                            270f, 3f);
                    f.setAnimatedLaunch();
                    f = engine.getFleetManager(1).spawnShipOrWing("sentry_FS",
                            new Vector2f(MathUtils.getRandomNumberInRange(-2000, 2000),
                                    MathUtils.getRandomNumberInRange(-10000, 10000)),
                            270f, 3f);
                    f.setAnimatedLaunch();
                }
                f.setCaptain(pilot);
                wave2Triggered = true;
            }

            cloudInterval.advance(amount);
            cloudInterval2.advance(amount);
            spinInterval.advance(amount);
            if (spinInterval.intervalElapsed()) {
                spin++;
            }

            // Clouds
            if (cloudInterval.intervalElapsed() && (ratio >= .01f && ratio < 0.80f || ratio > 1f)) {
                float xVel = (float) (MathUtils.getRandomNumberInRange(-100, 100));
                if (1 == 1) {
                    float cloudSize = (float) (MathUtils.getRandomNumberInRange(900, 1100));
                    MagicRender.battlespace(
                            Global.getSettings().getSprite("misc", "armaa_atmo_cloud2"),
                            new Vector2f(MathUtils.getRandomNumberInRange(minX, maxX),
                                    engine.getViewport().getCenter().y + engine.getViewport().getVisibleHeight()),
                            new Vector2f(xVel / 6, (-MathUtils.getRandomNumberInRange(100, 200) * mult) / 2),
                            new Vector2f(MathUtils.getRandomNumberInRange(cloudSize * 1.05f, cloudSize),
                                    MathUtils.getRandomNumberInRange(cloudSize * 1.05f, cloudSize)),
                            new Vector2f(0f, 0f),
                            MathUtils.getRandomNumberInRange(-15f, 15f), 0f,
                            new Color(155, 175, 175, 255),
                            false,
                            0f, 0f, 0f, 0f, 0f,
                            0.1f, 10f, 0.5f,
                            CombatEngineLayers.BELOW_SHIPS_LAYER
                    );
                    cloudSize = (float) (MathUtils.getRandomNumberInRange(1200, 1400));
                    MagicRender.battlespace(
                            Global.getSettings().getSprite("misc", "armaa_atmo_cloud2"),
                            new Vector2f(MathUtils.getRandomNumberInRange(minX, maxX),
                                    engine.getViewport().getCenter().y + engine.getViewport().getVisibleHeight()),
                            new Vector2f(xVel / 4, (-MathUtils.getRandomNumberInRange(400, 600) * mult) / 2),
                            new Vector2f(MathUtils.getRandomNumberInRange(cloudSize * 1.05f, cloudSize),
                                    MathUtils.getRandomNumberInRange(cloudSize * 1.05f, cloudSize)),
                            new Vector2f(10f, 10f),
                            0f, 0f,
                            new Color(240, 255, 255, 200),
                            false,
                            0f, 0f, 0f, 0f, 0f,
                            0.1f, 5f, 5f,
                            CombatEngineLayers.ABOVE_SHIPS_LAYER
                    );
                }
            } else if (ratio >= .8f && ratio <= 1f) {
                float r = ratio;
                // start introducing clouds earlier
                float start = 0.80f;
                float full = 0.90f;

                // 0 at 0.80, 1 at 0.90+
                float t = Math.max(0f, Math.min(1f, (r - start) / (full - start)));

                // ease so it stays low early, ramps hard near the end
                t = t * t * (3f - 2f * t); // smoothstep

                // probability per tick (tuned numbers)
                float pMin = 0.05f;  // at 0.80
                float pMax = 0.85f;  // at 0.90+
                float p = pMin + (pMax - pMin) * t;

                if (cloudInterval2.intervalElapsed()) {
                    if ((float) Math.random() < p) {
                        float cloudSize = (float) (MathUtils.getRandomNumberInRange(1200, 1800)) * t;
                        MagicRender.battlespace(
                                Global.getSettings().getSprite("misc", "armaa_atmo_cloud"),
                                new Vector2f(MathUtils.getRandomNumberInRange(minX, maxX),
                                        engine.getViewport().getCenter().y + engine.getViewport().getVisibleHeight()),
                                new Vector2f(0, ratio * (-MathUtils.getRandomNumberInRange(800, 1000) * mult) / 2),
                                new Vector2f(MathUtils.getRandomNumberInRange(cloudSize * 1.25f * ratio, cloudSize * ratio),
                                        MathUtils.getRandomNumberInRange(cloudSize * 1.25f * ratio, cloudSize * ratio)),
                                new Vector2f(25f, 25f),
                                0f, 0f,
                                new Color(200, 255, 255, 150),
                                false,
                                0f, 0f, 0f, 0f, 0f,
                                0.1f, 5f, 5f,
                                CombatEngineLayers.ABOVE_SHIPS_LAYER
                        );
                        cloudSize = 6000f * t;
                        Color cloudColor = ratio > 0.90f ? new Color(200f / 255f, 1f, 1f, 1f * t) : new Color(115f / 255f, 157f / 255f, 240f / 255f, 1f * t);
                        MagicRender.battlespace(
                                Global.getSettings().getSprite("misc", "armaa_atmo_cloud2"),
                                new Vector2f(MathUtils.getRandomNumberInRange(minX, maxX),
                                        engine.getViewport().getCenter().y + engine.getViewport().getVisibleHeight()),
                                new Vector2f(0, -MathUtils.getRandomNumberInRange(400, 800)),
                                new Vector2f(MathUtils.getRandomNumberInRange(cloudSize * 1.25f, cloudSize),
                                        MathUtils.getRandomNumberInRange(cloudSize * 1.25f, cloudSize)),
                                new Vector2f(25f, 25f),
                                0f, 0f,
                                cloudColor,
                                false,
                                0f, 0f, 0f, 0f, 0f,
                                1f, 5f, 1f,
                                CombatEngineLayers.BELOW_SHIPS_LAYER
                        );
                    }
                }

            }

        } else {
            // still render bg while paused

            advanceBackground(0f, ratio);
        }

        // cleanup + atmosphere stat malus section
        if (!engine.isPaused()) {
            if (interval2.intervalElapsed() && Global.getSector().getMemoryWithoutUpdate().get("$armaa_ceylonEscaped") == null) {
                for (FleetMemberAPI member : engine.getFleetManager(1).getRetreatedCopy()) {
                    if (member.getHullSpec().getBaseHullId().equals("armaa_ceylon")) {
                        Global.getSector().getMemoryWithoutUpdate().set("$armaa_ceylonEscaped", true);
                    }
                }
            }

            if (interval2.intervalElapsed()) {
                for (CombatEntityAPI asteroid : engine.getAsteroids()) {
                    engine.removeEntity(asteroid);
                }
            }

            interval.advance(amount);
            for (ShipAPI ship : engine.getShips()) {
                if (!ship.isAlive() || ship.isHulk() || !engine.isEntityInPlay(ship)) {
                    SpriteAPI spr = Global.getSettings().getSprite(ship.getHullSpec().getSpriteName());
                    MagicRender.battlespace(
                            spr,
                            ship.getLocation(),
                            new Vector2f(ship.getVelocity().x, ship.getVelocity().y - MathUtils.getRandomNumberInRange(200, 300)),
                            new Vector2f(spr.getWidth(), spr.getHeight()),
                            new Vector2f(-10f, -10f),
                            ship.getFacing() - 90f,
                            MathUtils.getRandomNumberInRange(-25, 25),
                            new Color(100, 75, 75, 255),
                            false,
                            0f,
                            0f,
                            0f,
                            0f,
                            0f,
                            0f,
                            3f,
                            5f,
                            CombatEngineLayers.BELOW_SHIPS_LAYER
                    );
                    engine.removeEntity(ship);
                    continue;
                }

                float id;
                float id2;

                if (ship.getCustomData().get("trail_id") == null) {
                    id = MagicTrailPlugin.getUniqueID();
                    ship.getCustomData().put("trail_id", id);
                    id2 = MagicTrailPlugin.getUniqueID();
                    ship.getCustomData().put("trail_id2", id2);
                } else {
                    id = (Float) ship.getCustomData().get("trail_id");
                    id2 = (Float) ship.getCustomData().get("trail_id2");
                }

                float r = ship.getCollisionRadius();
                float rearDist = r * 0.7f;
                float sideDist = r * 0.55f;

                Vector2f rearDir = Misc.getUnitVectorAtDegreeAngle(ship.getFacing() + 180f);
                Vector2f rightDir = Misc.getUnitVectorAtDegreeAngle(ship.getFacing() + 90f);

                Vector2f leftPos = new Vector2f(
                        ship.getLocation().x + rearDir.x * rearDist - rightDir.x * sideDist,
                        ship.getLocation().y + rearDir.y * rearDist - rightDir.y * sideDist
                );
                Vector2f rightPos = new Vector2f(
                        ship.getLocation().x + rearDir.x * rearDist + rightDir.x * sideDist,
                        ship.getLocation().y + rearDir.y * rearDist + rightDir.y * sideDist
                );

                float speed = ship.getVelocity().length();

                if (MagicRender.screenCheck(0.05f, ship.getLocation()) && interval.intervalElapsed() && !ship.isFighter()) {
                    MagicTrailPlugin.AddTrailMemberSimple(ship, id, Global.getSettings().getSprite("fx", "beam_trail_cel"),
                            leftPos, speed, ship.getFacing() + 180f + 12f, 10f, 10f,
                            new Color(150, 150, 150, 225),
                            0.8f, 0.1f, 1f, 0.6f, true);

                    MagicTrailPlugin.AddTrailMemberSimple(ship, id + 1, Global.getSettings().getSprite("fx", "beam_trail_cel"),
                            rightPos, speed, ship.getFacing() + 180f - 12f, 10f, 10f,
                            new Color(150, 150, 150, 225),
                            0.8f, 0.1f, 1f, 0.6f, true);

                    if (ceylon == null && wave2Triggered) {
                        if (ship.getHullSpec().getBaseHullId().contains("armaa_ceylon") && ship.getOwner() == 1) {
                            ceylon = ship;
                        }
                    }
                }

                // atmosphere malus
                String key = ship.getId() + "_atmo";
                if (!ship.getMutableStats().getMaxSpeed().getMultMods().containsKey(key)) {
                    float malus = (float) MANUVER_MALUS.get(ship.getHullSize());
                    ship.getMutableStats().getMaxSpeed().modifyMult(key, malus);
                    ship.getMutableStats().getMaxTurnRate().modifyMult(key, malus);
                    ship.getMutableStats().getAcceleration().modifyMult(key, malus);
                    ship.getMutableStats().getTurnAcceleration().modifyMult(key, malus);
                }

                Global.getCombatEngine().maintainStatusForPlayerShip(
                        "atmo",
                        "graphics/ui/icons/icon_repair_refit.png",
                        "In Atmoshpere",
                        "Manuverability reduced",
                        true
                );
            }
        }
    }

    // ---------------- BG HELPERS ----------------
    private void setPhase(BgPhase next) {
        bgPhase = next;
        phaseElapsed = 0f;

        if (next == BgPhase.FOREST_SCROLL) {
            bgCurr = SPR_FOREST;
            bgNext = SPR_FOREST;
            bgScrollFrac = 0f;     // (good hygiene)
            transConsumed = false;
            transArmed = false;
            oceanCommitted = false;
        }

        if (next == BgPhase.FOREST_OCEAN_TRANS) {
            //transConsumed = false;
            bgNext = SPR_FOREST;  // guarantee next tile is still forest, then TRANS appears once at boundary
        }

        if (next == BgPhase.OCEAN_SCROLL) {
            // guarantee we don't ever return TRANS once we commit to ocean
            bgNext = SPR_OCEAN;
            // optional safety:
            // transConsumed = true;
        }
    }

    private float clamp01(float t) {
        return Math.max(0f, Math.min(1f, t));
    }

    private float easeOutCubic(float t) {
        t = clamp01(t);
        return 1f - (float) Math.pow(1f - t, 3);
    }

    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private float getSquareBgSize(float mult) {
        float w = Global.getSettings().getScreenWidth();
        float h = Global.getSettings().getScreenHeight();
        return Math.max(w, h) * mult;
    }

    private void renderScreenspaceSquarePaused(String spriteKey, float centerY, float sizeMult, float angle, Color color) {
        float viewMult = engine.getViewport().getViewMult();
        float inv = 1f / viewMult;

        float w = Global.getSettings().getScreenWidth();
        float h = Global.getSettings().getScreenHeight();

        float diag = (float) Math.sqrt(w * w + h * h);
        float minCover = diag * 1.05f;

        float base = w * (1.2f);

        // actual size with zoom response
        float s1 = base * inv;

        s1 = Math.max(s1, minCover);

        Vector2f bgsize = new Vector2f(s1, s1);
        float tileSize = getBgTileSizeWorld(1.0f, ratio);
        float y0 = -bgScrollFrac * tileSize;
        float y1 = y0 + tileSize;
        MagicRender.screenspace(
                Global.getSettings().getSprite("misc", spriteKey),
                MagicRender.positioning.CENTER,
                new Vector2f(0f, y0),
                new Vector2f(0f, 0f),
                bgsize,
                new Vector2f(0f, 0f),
                angle,
                0f,
                color,
                false,
                0f, 0f, 0f, 0f, 0f, 0f,
                -1,
                0f,
                CombatEngineLayers.CLOUD_LAYER
        );
        MagicRender.screenspace(
                Global.getSettings().getSprite("misc", spriteKey),
                MagicRender.positioning.CENTER,
                new Vector2f(0f, y1),
                new Vector2f(0f, 0f),
                bgsize,
                new Vector2f(0f, 0f),
                angle,
                0f,
                color,
                false,
                0f, 0f, 0f, 0f, 0f, 0f,
                -1,
                0f,
                CombatEngineLayers.CLOUD_LAYER
        );
    }

    private void renderScreenspaceSquare(String spriteKey, float centerY, float sizeMult, float angle, Color color) {
        float viewMult = engine.getViewport().getViewMult();
        float inv = 1f / viewMult;

        float w = Global.getSettings().getScreenWidth();
        float h = Global.getSettings().getScreenHeight();

        float diag = (float) Math.sqrt(w * w + h * h);
        float minCover = diag * 1.05f;

        float base = w * (1.2f);

        // actual size with zoom response
        float s1 = base * inv;

        s1 = Math.max(s1, minCover);

        Vector2f bgsize = new Vector2f(s1, s1);
        WarpingSpriteRendererUtilV2 ocean = new WarpingSpriteRendererUtilV2(Global.getSettings().getSprite("misc", spriteKey), 500, 500, 10f, 500f, 1f);
        ocean.renderAtCenter(engine.getViewport().getCenter().x, engine.getViewport().getCenter().y);

        MagicRender.screenspace(
                Global.getSettings().getSprite("misc", spriteKey),
                MagicRender.positioning.CENTER,
                new Vector2f(0f, 0f + centerY),
                new Vector2f(0f, 0f),
                bgsize,
                new Vector2f(0f, 0f),
                angle,
                0f,
                color,
                false,
                0f, 0f, 0f, 0f, 0f, 0f,
                -1,
                0f,
                CombatEngineLayers.CLOUD_LAYER
        );
    }

    private void advanceBackground(float amount, float ratio) {

        // ratio-driven phase switching
        // ratio/time-driven phase switching (but DO NOT cut a tile mid-scroll)
        if (!transArmed && bgPhase == BgPhase.FOREST_SCROLL && ratio >= RATIO_START_TRANS) {
            transArmed = true;
            setPhase(BgPhase.FOREST_OCEAN_TRANS);
        }

        // Don't commit to ocean until AFTER the TRANS tile has fully passed (tile boundary)
        if (bgPhase == BgPhase.FOREST_OCEAN_TRANS && oceanCommitted) {
            setPhase(BgPhase.OCEAN_SCROLL);
        }
        if (bgPhase == BgPhase.OCEAN_SCROLL && ratio >= RATIO_START_CITY) {
            citySpin = 0f;
            citySpinVel = 0f;
            setPhase(BgPhase.CITY_DROP);
        }

        phaseElapsed += amount;

        Color cForest = new Color(100, 100, 100, 255);
        Color cTrans = new Color(120, 120, 120, 255);
        Color cOcean = new Color(150, 150, 150, 255);
        Color cCity = new Color(175, 175, 175, 255);

        switch (bgPhase) {
            case FOREST_SCROLL, FOREST_OCEAN_TRANS, OCEAN_SCROLL -> {
                Color tCurr = tintForSprite(bgCurr, cForest, cTrans, cOcean);
                Color tNext = tintForSprite(bgNext, cForest, cTrans, cOcean);
                if (!engine.isPaused()) {
                    renderScrollConveyor(amount, tCurr, tNext);
                } else {
                    renderScreenspaceSquarePaused(bgCurr, 0f, 1.2f, 0f, tCurr);
                }

            }
            case CITY_DROP -> {
                float t = easeOutCubic(phaseElapsed / DUR_CITY_DROP);

                float size = getSquareBgSize(1.2f);
                float screenH = Global.getSettings().getScreenHeight();
                float yStart = (screenH * 0.5f + size * 0.5f);
                float y = lerp(yStart, 0f, t);

                renderScreenspaceSquare(SPR_CITY, y, 1.2f, 0f, cCity);

                if (t >= 1f) {
                    setPhase(BgPhase.CITY_HOLD);
                }
            }
            case CITY_HOLD -> {
                citySpinVel += CITY_SPIN_ACCEL * amount;
                if (citySpinVel > CITY_SPIN_MAX_VEL) {
                    citySpinVel = CITY_SPIN_MAX_VEL;
                }
                citySpin += citySpinVel * amount;
                renderScreenspaceSquare(SPR_CITY, 0f, 1.2f, citySpin, cCity);
            }
        }
    }

    private void renderScrollConveyor(float amount, Color cCurr, Color cNext) {

        // 1) Compute tile size in WORLD units so it naturally zooms with viewport
        float tileSize = getBgTileSizeWorld(1.0f, ratio); // WORLD units, zoom-aware

        // advance in TILE FRACTION, not world units
        bgScrollFrac += amount / SECONDS_PER_TILE;

        if (bgScrollFrac >= 1f) {
            bgScrollFrac -= 1f;

            bgCurr = bgNext;
            bgNext = chooseNextBgSprite();

            // If we have already consumed TRANS and we just stepped onto an OCEAN tile,
            // that means TRANS has fully completed and we can safely commit to ocean phase.
            if (transConsumed && SPR_OCEAN.equals(bgCurr)) {
                oceanCommitted = true;
            }
        }

        float y0 = -bgScrollFrac * tileSize;
        float y1 = y0 + tileSize;

        Vector2f vpCenter = engine.getViewport().getCenter();

        renderBattlespaceSquare(bgCurr, vpCenter, y0, tileSize, 0f, cCurr);
        renderBattlespaceSquare(bgNext, vpCenter, y1, tileSize, 0f, cNext);

        // IMPORTANT:
        // any effects that used scrollSpeed should now use (tileSize / SECONDS_PER_TILE)
        //float scrollSpeedWorld = tileSize / SECONDS_PER_TILE;
        // slipstream overlays can choose which phases
        if (bgCurr == SPR_OCEAN) {

            float viewMult = engine.getViewport().getViewMult();
            float inv = 1f / viewMult;

            float w = Global.getSettings().getScreenWidth();
            float h = Global.getSettings().getScreenHeight();

            float diag = (float) Math.sqrt(w * w + h * h);
            float minCover = diag * 1.05f;

            float base = w * (1.2f);

            // actual size with zoom response
            float s1 = base;

            s1 = Math.max(s1, minCover);

            Vector2f bgsize = new Vector2f(s1, s1);
            MagicRender.screenspace(
                    Global.getSettings().getSprite("misc", "slipstream_edge33"),
                    MagicRender.positioning.CENTER,
                    new Vector2f((-bgScrollFrac * s1) / 2f, -bgScrollFrac * s1),
                    new Vector2f(0f, 0f),
                    bgsize,
                    new Vector2f(0f, 0f),
                    0f, 0f,
                    new Color(200, 255, 255, 100),
                    false,
                    0f, 0f, 0f, 0f, 0f, 0f,
                    -1, 0f,
                    CombatEngineLayers.CLOUD_LAYER
            );
            if (Math.random() <= 0.10) {
                MagicRender.battlespace(
                        Global.getSettings().getSprite("backgrounds", "star2"),
                        new Vector2f(MathUtils.getRandomNumberInRange(0, w), MathUtils.getRandomNumberInRange(0, h)),
                        new Vector2f(0, 0),
                        new Vector2f(15f, 15f),
                        new Vector2f(0f, 0f),
                        0f,
                        0f,
                        new Color(200, 255, 255, 225),
                        true,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        0.1f,
                        0.15f,
                        0.15f,
                        CombatEngineLayers.BELOW_SHIPS_LAYER
                );
            }
            MagicRender.screenspace(
                    Global.getSettings().getSprite("misc", "slipstream_edge33"),
                    MagicRender.positioning.CENTER,
                    new Vector2f((-bgScrollFrac * s1 + s1) / 2f, -bgScrollFrac * s1 + s1),
                    new Vector2f(0f, 0f),
                    bgsize,
                    new Vector2f(0f, 0f),
                    0f, 0f,
                    new Color(200, 255, 255, 100),
                    false,
                    0f, 0f, 0f, 0f, 0f, 0f,
                    -1, 0f,
                    CombatEngineLayers.CLOUD_LAYER
            );
            MagicRender.screenspace(
                    Global.getSettings().getSprite("misc", "slipstream_edge2"),
                    MagicRender.positioning.CENTER,
                    new Vector2f(10f, -bgScrollFrac * s1),
                    new Vector2f(0f, 0f),
                    bgsize,
                    new Vector2f(0f, 0f),
                    0f, 0f,
                    new Color(200, 255, 255, 100),
                    true,
                    0f, 0f, 0f, 0f, 0f, 0f,
                    -1, 0f,
                    CombatEngineLayers.CLOUD_LAYER
            );
            MagicRender.screenspace(
                    Global.getSettings().getSprite("misc", "slipstream_edge2"),
                    MagicRender.positioning.CENTER,
                    new Vector2f(10f, -bgScrollFrac * s1 + s1),
                    new Vector2f(0f, 0f),
                    bgsize,
                    new Vector2f(0f, 0f),
                    0f, 0f,
                    new Color(200, 255, 255, 100),
                    true,
                    0f, 0f, 0f, 0f, 0f, 0f,
                    -1, 0f,
                    CombatEngineLayers.CLOUD_LAYER
            );
        }
    }

    private String chooseNextBgSprite() {
        switch (bgPhase) {
            case FOREST_SCROLL -> {
                return SPR_FOREST;
            }

            case FOREST_OCEAN_TRANS -> {
                // One-shot: forest repeats, then exactly ONE trans tile, then ocean repeats.
                if (!transConsumed) {
                    transConsumed = true;
                    return SPR_TRANS;   // armaa_atmo4 exactly once
                }
                return SPR_OCEAN;      // after the trans tile, next tiles are ocean
            }
            case OCEAN_SCROLL -> {
                return SPR_OCEAN;
            }

            default -> {
                return SPR_OCEAN;
            }
        }
    }

    private void renderBattlespaceSquare(String spriteKey, Vector2f center, float yOffset, float size, float angle, Color color) {
        Vector2f loc = new Vector2f(center.x, center.y + yOffset);
        //Vector2f bgsize = new Vector2f(s1, s1);
        //ocean.renderAtCenter(engine.getViewport().getCenter().x, engine.getViewport().getCenter().y);

        SpriteAPI spr = Global.getSettings().getSprite("misc", spriteKey);
        MagicRender.battlespace(
                oceanSpr,
                loc,
                new Vector2f(0f, 0f), // vel (we control motion via yOffset/bgScroll)
                new Vector2f(size, size), // size in WORLD units
                new Vector2f(0f, 0f), // growth
                angle,
                0f, // spin handled externally if needed
                color,
                false,
                0f, 0f, 0f, 0f, 0f,
                0f, // fadein
                engine.getElapsedInLastFrame(), // full (-1 means "forever" in MagicRender)
                0f, // fadeout
                CombatEngineLayers.CLOUD_LAYER
        );
        ocean.advance(engine.getElapsedInLastFrame());
        //ocean.renderAtCenter(engine.getViewport().getCenter().x, engine.getViewport().getCenter().y);
    }

    private Color tintForSprite(String spr, Color cForest, Color cTrans, Color cOcean) {
        if (SPR_TRANS.equals(spr)) {
            return cTrans;
        }
        if (SPR_OCEAN.equals(spr)) {
            return cOcean;
        }
        return cForest;
    }

    private float getBgTileSizeWorld(float baseMult, float ratio) {
        float vw = engine.getViewport().getVisibleWidth();
        float vh = engine.getViewport().getVisibleHeight();

        float diag = (float) Math.sqrt(vw * vw + vh * vh);
        float minCover = diag * 1.05f;

        float base = vw * baseMult; // NO ratio term
        return Math.max(base, minCover);
    }

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        bgPhase = BgPhase.FOREST_SCROLL;
        bgCurr = SPR_FOREST;
        bgNext = SPR_FOREST;
        transArmed = false;
        oceanCommitted = false;
        bgScrollFrac = 0f;
    }
}
