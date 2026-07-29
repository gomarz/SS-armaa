package data.scripts.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

import java.util.*;
import java.awt.Color;

import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.combat.dweller.WarpingSpriteRendererUtilV2;
import data.campaign.rulecmd.armaa_jeniusCityBattle;
import data.scripts.missions.armaa_titleSplash;
import data.scripts.util.armaa_utils;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.magiclib.util.MagicCampaign;

public class armaa_cityBattlePlugin extends BaseEveryFrameCombatPlugin {

    protected CombatEngineAPI engine;

    private final IntervalUtil interval = new IntervalUtil(.05f, .05f);
    private final IntervalUtil cloudInterval = new IntervalUtil(3f, 5f);
    private final IntervalUtil cloudInterval2 = new IntervalUtil(1f, 1f);
    private final IntervalUtil interval2 = new IntervalUtil(1f, 3f);
    private final IntervalUtil interval3 = new IntervalUtil(5f, 5f);

    private float ratio = 0f;

    public final static Map MANUVER_MALUS = new HashMap();
    boolean reinforcementsTriggered = false, wave2Triggered = false, timeUp = false;
    ShipAPI ceylon;

    //TODO refactor this and create helper functions for what we can reuse here
    // in other plugins
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
    private static final float RATIO_START_TRANS = 0.5f;
    private static final float RATIO_START_CITY = 1.5f;
    // Durations

    private static final float DUR_CITY_DROP = 2f;

    // City spin
    private float citySpin = 0f;
    private float citySpinVel = 0f;
    private static final float CITY_SPIN_ACCEL = 0.10f;
    private static final float CITY_SPIN_MAX_VEL = 0.15f;
    // --- scrolling state (tile conveyor) ---
    private String bgCurr;
    private String bgNext;
// Scroll timing: one full tile passes in this many seconds
    private static final float SECONDS_PER_TILE = 15f;
// 0..1 progress through the current tile (fractional scroll)
    private float bgScrollFrac = 0f;
// one-shot transition control
    private boolean transConsumed = false;
    private boolean transArmed = false;
    private boolean oceanCommitted = false;
    private static final String SPR_FOREST = Global.getSettings().getString("armaa_missionBGs", "armaa_atmo3");          // phase 1 scrolling
    private static final String SPR_TRANS = Global.getSettings().getString("armaa_missionBGs", "armaa_atmo4");         // single transition tile (forest->ocean)
    private static final String SPR_OCEAN = Global.getSettings().getString("armaa_missionBGs", "armaa_atmo5");       // phase 2 scrolling
    private static final String SPR_CITY = Global.getSettings().getString("armaa_missionBGs", "armaa_city");         // city

    // --------------------------------------------------------------
    static {
        MANUVER_MALUS.put(HullSize.FIGHTER, 0.90f);
        MANUVER_MALUS.put(HullSize.FRIGATE, 0.80f);
        MANUVER_MALUS.put(HullSize.DESTROYER, 0.60f);
        MANUVER_MALUS.put(HullSize.CRUISER, 0.50f);
        MANUVER_MALUS.put(HullSize.CAPITAL_SHIP, 0.40f);
    }
    private WarpingSpriteRendererUtilV2 ocean;
    private OceanWarpRenderer foam;

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
            SpriteAPI oceanSpr = Global.getSettings().getSprite(SPR_OCEAN);
            oceanSpr.setSize(oceanSpr.getWidth(), oceanSpr.getHeight());
            oceanSpr.setCenter(oceanSpr.getWidth() / 2f, oceanSpr.getHeight() / 2f);

            SpriteAPI foamSpr = Global.getSettings().getSprite("misc", "slipstream_edge33");
            foamSpr.setSize(foamSpr.getWidth(), foamSpr.getHeight());
            foamSpr.setCenter(foamSpr.getWidth() / 2f, foamSpr.getHeight() / 2f);
            SpriteAPI depthSpr = Global.getSettings().getSprite("misc", "slipstream2");
            CombatEntityAPI e = Global.getCombatEngine().addLayeredRenderingPlugin(new OceanBackgroundPlugin(oceanSpr, foamSpr, depthSpr));
            e.getLocation().set(engine.getViewport().getCenter());
            oceanSpr = Global.getSettings().getSprite(SPR_OCEAN);
            float width = oceanSpr.getWidth();   // or * some scale factor
            float height = oceanSpr.getHeight();
            float warpAmt = width * 0.04f;       // 4% of width, same as the source
            oceanSpr.setCenter(width / 2f, height / 2f);  // ← THE MISSING PIECE
            //ocean = new WarpingSpriteRendererUtilV2(oceanSpr, 10, 10, warpAmt, warpAmt * 1.4f, 1f);
            engine.setBackgroundGlowColor(shiftColor(new Color(50, 50, 50, 80), new Color(75, 50, 0, 25)));
            engine.getFleetManager(0).setSuppressDeploymentMessages(true);
            Global.getSoundPlayer().playCustomMusic(1, 1, "music_armaa_citybattle", true);
            /*
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
             */
            String minuteStr = armaa_utils.getMinuteString();
            Global.getCombatEngine().addLayeredRenderingPlugin(
                    new armaa_titleSplash("NIKOPOLIS - CITY APPROACH | " + Global.getSector().getClock().getHour() + ":" + minuteStr + " | " + Global.getSector().getClock().getDateString(),
                            " THE COASTAL CAPITAL"));
            PersonAPI pilot3 = Global.getSector().getImportantPeople().getPerson("armaa_redeye");
            ShipAPI f;

            f = engine.getFleetManager(0).spawnShipOrWing("armaa_garegga_tt_carrier_aleste", new Vector2f(-500, 0), 270f, 0f);
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

            f = engine.getFleetManager(0).spawnShipOrWing("armaa_monitor_standard_xiv", new Vector2f(-500, 1050), 90f, 0f);
            f.setAlly(true);
            engine.getFleetManager(0).setSuppressDeploymentMessages(false);
            reinforcementsTriggered = true;
        }

        if (!engine.isPaused()) {
            ratio = (float) engine.getElapsedInContactWithEnemy() / 100f;

            interval3.advance(amount);
            interval2.advance(amount);

            // Background state update (rendering happens in BackgroundRendererPlugin,
            // which runs every frame — paused included — so no paused branch is needed)
            updateBackground(amount, ratio);

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

            if (!wave2Triggered && ((Global.getCombatEngine().getTotalElapsedTime(false) > 20f && engine.getFleetManager(1).getCurrStrength() < 25)
                    || ratio >= 2f)) {

                boolean losing = engine.getFleetManager(1).getCurrStrength() < 25;
                PersonAPI pilot = (PersonAPI) MagicCampaign.createCaptainBuilder(Factions.MERCENARY)
                        .setFirstName("David")
                        .setLastName("Rengel")
                        .setGender(FullName.Gender.MALE)
                        .create();

                ShipAPI f = engine.getFleetManager(1).spawnShipOrWing("armaa_ceylon_boss", new Vector2f(0, 10000), 270f, 30f);
                f.setName("DAS Ceylon");
                f.setCaptain(pilot);

                if (losing) {
                    engine.getCombatUI().addMessage(1, f, Color.red, f.getName(), Color.white, ":", Color.white,
                            "Didn't expect you to carve through the vanguard like that. This was supposed to be a trap. All remaining units, close in and engage!");
                } else {
                    engine.getCombatUI().addMessage(1, f, Color.red, f.getName(), Color.white, ":", Color.white,
                            "DAS Ceylon inbound. Scum in the outskirts put up a fight.. pointless, of course. Still more trash to clear. Beginning approach.");
                }

                for (int i = 0; i < 4; i++) {
                    f = engine.getFleetManager(1).spawnShipOrWing("armaa_morgana_wing",
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
            } else if (ratio >= 1.2f && ratio <= 1.5f) {
                float r = ratio;
                // start introducing clouds earlier
                float start = 1.2f;
                float full = 1.3f;

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
                        float cloudX = (float) Global.getSettings().getScreenWidth() * 2f * ratio;// * t;                        
                        float cloudY = (float) Global.getSettings().getScreenHeight() * 2f * ratio;// * t;
                        MagicRender.battlespace(
                                Global.getSettings().getSprite("misc", "armaa_atmo_cloud"),
                                new Vector2f(MathUtils.getRandomNumberInRange(minX, maxX),
                                        engine.getViewport().getCenter().y + engine.getViewport().getVisibleHeight()),
                                new Vector2f(0, ratio * (-MathUtils.getRandomNumberInRange(800, 1000) * mult) / 2),
                                new Vector2f(cloudX, cloudY),
                                new Vector2f(25f, 25f),
                                0f, 0f,
                                new Color(200, 255, 255, 150),
                                false,
                                0f, 0f, 0f, 0f, 0f,
                                0.1f, 5f, 5f,
                                CombatEngineLayers.ABOVE_SHIPS_LAYER
                        );
                        Color cloudColor = ratio > 1.5f ? new Color(200f / 255f, 1f, 1f, 1f * t) : new Color((115f / 255f), (157f / 255f), 240f / 255f, 1f * t);
                        MagicRender.battlespace(
                                Global.getSettings().getSprite("misc", "armaa_atmo_cloud2"),
                                new Vector2f(MathUtils.getRandomNumberInRange(minX, maxX),
                                        engine.getViewport().getCenter().y + engine.getViewport().getVisibleHeight()),
                                new Vector2f(0, -MathUtils.getRandomNumberInRange(400, 800)),
                                new Vector2f(cloudX, cloudY),
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

        }

        // cleanup + atmosphere stat malus section
        if (!engine.isPaused()) {
            if (interval2.intervalElapsed() && Global.getSector().getMemoryWithoutUpdate().get("$armaa_ceylonEscaped") == null && Global.getSector().getMemoryWithoutUpdate().get("$armaa_ceylonDestroyedEarly") == null) {
                for (FleetMemberAPI member : engine.getFleetManager(1).getRetreatedCopy()) {
                    if (member.getHullSpec().getBaseHullId().equals("armaa_ceylon")) {
                        Global.getSector().getMemoryWithoutUpdate().set("$armaa_ceylonEscaped", true);
                    }
                }
                for (FleetMemberAPI member : engine.getFleetManager(1).getDestroyedCopy()) {
                    if (member.getHullSpec().getBaseHullId().equals("armaa_ceylon")) {
                        if(ratio <= 1.5f)
                        {
                            Global.getSector().getMemoryWithoutUpdate().set("$armaa_ceylonDestroyedEarly", true);
                            
                        }
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

    // ---------------------------------------------------------------------------
    // State-only update. Called from advance() inside the !isPaused() block.
    // NO rendering in here — all drawing lives in BackgroundRendererPlugin.render(),
    // which runs every frame (paused included) against the live viewport.
    // ---------------------------------------------------------------------------
    private void updateBackground(float amount, float ratio) {

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

        switch (bgPhase) {
            case FOREST_SCROLL, FOREST_OCEAN_TRANS, OCEAN_SCROLL -> {
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

                // transient FX only — fine to skip while paused
                spawnOceanFX();
            }
            case CITY_DROP -> {
                if (easeOutCubic(phaseElapsed / DUR_CITY_DROP) >= 1f) {
                    setPhase(BgPhase.CITY_HOLD);
                }
            }
            case CITY_HOLD -> {
                citySpinVel += CITY_SPIN_ACCEL * amount;
                if (citySpinVel > CITY_SPIN_MAX_VEL) {
                    citySpinVel = CITY_SPIN_MAX_VEL;
                }
                citySpin += citySpinVel * amount;
            }
        }
    }

    // Transient ocean-phase effects (star flecks + slipstream seam overlays).
    // Was the bottom half of the old renderScrollConveyor.
    private void spawnOceanFX() {
        if (!SPR_OCEAN.equals(bgCurr)) {
            return;
        }

        float w = Global.getSettings().getScreenWidth();
        float h = Global.getSettings().getScreenHeight();

        float diag = (float) Math.sqrt(w * w + h * h);
        float minCover = diag * 1.05f;

        float base = w * (1.2f);
        float s1 = Math.max(base, minCover);

        Vector2f bgsize = new Vector2f(s1, s1);

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

    private Color tintForSprite(String spr, Color cForest, Color cTrans, Color cOcean) {
        if (SPR_TRANS.equals(spr)) {
            return cTrans;
        }
        if (SPR_OCEAN.equals(spr)) {
            return cOcean;
        }
        return cForest;
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
        armaa_utils.loadMissionTextures(armaa_jeniusCityBattle.MISSION_TEXTURES_CITY);

        // Persistent background renderer, draws every frame (paused included)
        CombatEntityAPI e = engine.addLayeredRenderingPlugin(new BackgroundRendererPlugin());
        e.getLocation().set(0f, 0f); // irrelevant with MAX_VALUE render radius
    }

    // ---------------------------------------------------------------------------
    // The renderer. render() runs every frame regardless of pause and reads the
    // CURRENT viewport, so zoom/pan while paused always stays covered. Immediate-
    // mode SpriteAPI draw = exactly one render per frame, no sprite accumulation.
    // ---------------------------------------------------------------------------
    public class BackgroundRendererPlugin extends BaseCombatLayeredRenderingPlugin {

        private static final Color C_FOREST = new Color(100, 100, 100, 255);
        private static final Color C_TRANS = new Color(100, 100, 100, 255);
        private static final Color C_OCEAN = new Color(110, 110, 110, 255);
        private static final Color C_CITY = new Color(175, 175, 175, 255);

        @Override
        public float getRenderRadius() {
            return Float.MAX_VALUE; // same trick as the ocean plugin: never culled
        }

        @Override
        public EnumSet<CombatEngineLayers> getActiveLayers() {
            // tiles on ABOVE_PLANETS (matches old battlespace calls),
            // city on CLOUD_LAYER (matches old screenspace calls)
            return EnumSet.of(CombatEngineLayers.ABOVE_PLANETS, CombatEngineLayers.CLOUD_LAYER);
        }

        @Override
        public boolean isExpired() {
            return false; // lives for the whole battle
        }

        @Override
        public void render(CombatEngineLayers layer, ViewportAPI viewport) {
            switch (bgPhase) {
                case FOREST_SCROLL, FOREST_OCEAN_TRANS, OCEAN_SCROLL -> {
                    if (layer != CombatEngineLayers.ABOVE_PLANETS) {
                        return;
                    }

                    // size from the LIVE viewport, every frame — this is the fix
                    float tileSize = coverSize(viewport, 1.0f);
                    float y0 = -bgScrollFrac * tileSize;
                    Vector2f c = viewport.getCenter();

                    drawTile(bgCurr, c, y0, tileSize, 0f,
                            tintForSprite(bgCurr, C_FOREST, C_TRANS, C_OCEAN));
                    drawTile(bgNext, c, y0 + tileSize, tileSize, 0f,
                            tintForSprite(bgNext, C_FOREST, C_TRANS, C_OCEAN));
                }
                case CITY_DROP -> {
                    if (layer != CombatEngineLayers.CLOUD_LAYER) {
                        return;
                    }

                    float size = coverSize(viewport, 1.2f);
                    float t = easeOutCubic(phaseElapsed / DUR_CITY_DROP);
                    float yStart = viewport.getVisibleHeight() * 0.5f + size * 0.5f;
                    float y = lerp(yStart, 0f, t);

                    drawSized(SPR_CITY, viewport.getCenter(), y, size, 0f, C_CITY);
                }
                case CITY_HOLD -> {
                    if (layer != CombatEngineLayers.CLOUD_LAYER) {
                        return;
                    }
                    float size = coverSize(viewport, 1.2f);
                    drawSized(SPR_CITY, viewport.getCenter(), 0f, size, citySpin, C_CITY);
                }
            }
        }

        // guaranteed screen coverage at any zoom: max(width-based, diagonal * 1.05)
        private float coverSize(ViewportAPI viewport, float baseMult) {
            float vw = viewport.getVisibleWidth();
            float vh = viewport.getVisibleHeight();
            float diag = (float) Math.sqrt(vw * vw + vh * vh);
            return Math.max(vw * baseMult, diag * 1.05f);
        }

        private void drawTile(String spriteKey, Vector2f center, float yOffset,
                float size, float angle, Color color) {
            // preserve the ocean substitute hack from the old renderBattlespaceSquare
            if (SPR_OCEAN.equals(bgCurr)) {
                spriteKey = "graphics/armaa/backgrounds/armaa_cutscene.jpg";
            }
            drawSized(spriteKey, center, yOffset, size, angle, color);
        }

        private void drawSized(String spriteKey, Vector2f center, float yOffset,
                float size, float angle, Color color) {
            SpriteAPI spr = Global.getSettings().getSprite(spriteKey);
            spr.setSize(size, size);
            spr.setCenter(size / 2f, size / 2f);
            spr.setColor(color);
            spr.setAngle(angle);
            spr.setAlphaMult(1f);
            spr.renderAtCenter(center.x, center.y + yOffset);
        }
    }

    public class OceanBackgroundPlugin extends BaseCombatLayeredRenderingPlugin {

        private OceanWarpRenderer ocean;
        private OceanWarpRenderer foam;
        private OceanWarpRenderer depth;
        private float oceanAlpha = 0f;
        private boolean oceanFadeStarted = false;
        private float oceanFadeElapsed = 0f;
        private static final float OCEAN_FADE_IN_DURATION = 8f; // seconds to fully fade in        

        public OceanBackgroundPlugin(SpriteAPI oceanSpr, SpriteAPI foamSpr, SpriteAPI depthSpr) {
            ocean = new OceanWarpRenderer(oceanSpr);
            ocean.setWaveParams(100f, 800f, 60f, 20f, 450f, 50f);
            oceanSpr.setColor(new Color(150, 150, 150, 255));
            depthSpr.setSize(oceanSpr.getWidth(), oceanSpr.getWidth());
            depthSpr.setColor(new Color(120, 150, 155, 50)); // blue tint, more visible

            //depthSpr.setAlphaMult(2f); // punch it up
            depth = new OceanWarpRenderer(depthSpr);
            depth.setWaveParams(20f, 1200f, 15f, 10f, 800f, 80f);
            depth.setScrollSpeed(0.02f, 0.04f);
            foamSpr.setSize(oceanSpr.getWidth(), oceanSpr.getWidth());
            foamSpr.setColor(new Color(40, 40, 40, 80)); // dark blue, low opacity
            foam = new OceanWarpRenderer(foamSpr);
            foam.setScrollSpeed(0.04f, 0.07f); // barely moves
            foam.setWaveParams(100f, 800f, 60f, 20f, 450f, 50f);
            //foam.setWaveParams(20f, 1200f, 15f, 10f, 800f, 20f); // very slow, very large
        }

        @Override
        public EnumSet<CombatEngineLayers> getActiveLayers() {
            return EnumSet.of(CombatEngineLayers.CLOUD_LAYER);
        }

        @Override
        public float getRenderRadius() {
            return Float.MAX_VALUE; // always render
        }

        @Override
        public boolean isExpired() {
            return bgPhase == BgPhase.CITY_DROP;
        }

        @Override
        public void advance(float amount) {
            if (Global.getCombatEngine().isPaused()) {
                return;
            }
            ocean.advance(amount);
            depth.advance(amount);
            foam.advance(amount);
        }

        @Override
        public void render(CombatEngineLayers layer, ViewportAPI viewport) {
            boolean oceanOnScreen = SPR_OCEAN.equals(bgCurr)
                    || (SPR_OCEAN.equals(bgNext) && bgScrollFrac > 0.6f);
            if (!oceanOnScreen) {
                return;
            }

            if (layer == CombatEngineLayers.CLOUD_LAYER) {
                // Fade in from 0 once ocean becomes current
                if (!oceanFadeStarted) {
                    oceanFadeStarted = true;
                    oceanFadeElapsed = 0f;
                }
                if (!Global.getCombatEngine().isPaused()) {
                    oceanFadeElapsed += Global.getCombatEngine().getElapsedInLastFrame();
                }
                oceanAlpha = Math.min(1f, oceanFadeElapsed / OCEAN_FADE_IN_DURATION);
                Vector2f center = viewport.getCenter(); // locked to camera             
                //ocean.renderAtCenter(center.x, center.y, oceanAlpha);
                // Render ship reflections

                // Set additive blend DIRECTLY via GL before foam renders           
                GL11.glEnable(GL11.GL_BLEND);
                GL14.glBlendEquation(GL14.GL_FUNC_ADD);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                ocean.renderAtCenter(center.x, center.y, oceanAlpha);
                depth.renderAtCenter(center.x, center.y, oceanAlpha);
                foam.renderAtCenter(center.x, center.y, oceanAlpha);
                // Restore normal blend after
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL14.glBlendEquation(GL14.GL_FUNC_ADD);
                for (ShipAPI ship : Global.getCombatEngine().getShips()) {
                    if (ship.isHulk()) {
                        continue;
                    }
                    if (!MagicRender.screenCheck(0.1f, ship.getLocation())) {
                        continue;
                    }

                    renderShipReflection(ship);
                }
            }
        }
    }

    private void renderShipReflection(ShipAPI ship) {
        // Higher altitude feel - reflection is offset and slightly larger
        float altitude = 200f; // how high above water the ship "is"
        float sunAngle = 45f;  // light direction in degrees
        Vector2f sunDir = Misc.getUnitVectorAtDegreeAngle(sunAngle);

        float reflX = ship.getLocation().x + sunDir.x * altitude;
        float reflY = ship.getLocation().y - sunDir.y * altitude;
        SpriteAPI reflectionSpr = Global.getSettings().getSprite(ship.getHullSpec().getSpriteName());
        reflectionSpr.setWidth(ship.getSpriteAPI().getWidth() * 0.70f);
        reflectionSpr.setHeight(ship.getSpriteAPI().getHeight() * 0.70f);
        reflectionSpr.setColor(new Color(0, 0, 25, 75)); // cool blue-white
        reflectionSpr.setAngle(ship.getFacing() - 90f);
        reflectionSpr.renderAtCenter(reflX, reflY);

    }

    public class OceanWarpRenderer {

        private SpriteAPI sprite;
        private float elapsed = 0f;

        // Wave parameters
        private int cols = 20;  // mesh resolution
        private int rows = 20;
        private float waveHeight = 15f;   // vertical displacement
        private float waveLength = 400f;  // horizontal wave period
        private float waveSpeed = 80f;    // how fast waves scroll
        private float waveHeight2 = 8f;   // second wave layer
        private float waveLength2 = 250f; // different frequency
        private float waveSpeed2 = 120f;  // different speed
        private float scrollU = 0f;  // horizontal scroll
        private float scrollV = 0f;  // vertical scroll
        private float scrollSpeedU = 0.02f;  // slight right
        private float scrollSpeedV = 0.06f;  // upward    

        public OceanWarpRenderer(SpriteAPI sprite) {
            this.sprite = sprite;
        }

        public void advance(float amount) {
            elapsed += amount;
            scrollU += scrollSpeedU * amount;
            scrollV += scrollSpeedV * amount;
            // Wrap to avoid float precision issues over long battles
            if (scrollU > 1f) {
                scrollU -= 1f;
            }
            if (scrollV > 1f) {
                scrollV -= 1f;
            }
        }

        public void setScrollSpeed(float u, float v) {
            scrollSpeedU = u;
            scrollSpeedV = v;
        }

        public void setWaveParams(float h1, float l1, float s1, float h2, float l2, float s2) {
            waveHeight = h1;
            waveLength = l1;
            waveSpeed = s1;
            waveHeight2 = h2;
            waveLength2 = l2;
            waveSpeed2 = s2;
        }

        public SpriteAPI getSprite() {
            return sprite;
        }

        public void renderAtCenter(float cx, float cy, float alphaMult) {
            CombatEngineAPI engine = Global.getCombatEngine();
            float vw = engine.getViewport().getVisibleWidth();
            float vh = engine.getViewport().getVisibleHeight();
            float diag = (float) Math.sqrt(vw * vw + vh * vh);
            float size = diag * 1.05f; // slightly oversized to prevent edge gaps
            float w = size;
            float h = size;
            float left = cx - w / 2f;
            float top = cy - h / 2f;
            float texW = 1f / cols;
            float texH = 1f / rows;

            sprite.bindTexture();
            GL11.glBegin(GL11.GL_QUADS);

            float[] color = sprite.getColor().getRGBComponents(null);
            GL11.glColor4f(color[0], color[1], color[2], sprite.getAlphaMult() * alphaMult);

            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    // Four corners of this quad
                    float[] xs = {
                        left + (col) * (w / cols),
                        left + (col + 1) * (w / cols),
                        left + (col + 1) * (w / cols),
                        left + (col) * (w / cols)
                    };
                    float[] ys = {
                        top + (row) * (h / rows),
                        top + (row) * (h / rows),
                        top + (row + 1) * (h / rows),
                        top + (row + 1) * (h / rows)
                    };

                    // Apply sine displacement to each corner
                    for (int i = 0; i < 4; i++) {
                        float wave1 = (float) Math.sin((xs[i] / waveLength) + elapsed * waveSpeed * 0.01f) * waveHeight;
                        float wave2 = (float) Math.sin((xs[i] / waveLength2) + elapsed * waveSpeed2 * 0.01f + 1.3f) * waveHeight2;
                        ys[i] += wave1 + wave2;

                        // Add lateral displacement using Y position as input instead of X
                        float lateral1 = (float) Math.sin((ys[i] / waveLength) + elapsed * waveSpeed * 0.008f + 0.7f) * waveHeight * 0.4f;
                        float lateral2 = (float) Math.sin((ys[i] / waveLength2) + elapsed * waveSpeed2 * 0.008f + 2.1f) * waveHeight2 * 0.4f;
                        xs[i] += lateral1 + lateral2;
                    }

                    // Emit quad
                    GL11.glTexCoord2f((col) * texW + scrollU, (row) * texH + scrollV);
                    GL11.glVertex2f(xs[0], ys[0]);
                    GL11.glTexCoord2f((col + 1) * texW + scrollU, (row) * texH + scrollV);
                    GL11.glVertex2f(xs[1], ys[1]);
                    GL11.glTexCoord2f((col + 1) * texW + scrollU, (row + 1) * texH + scrollV);
                    GL11.glVertex2f(xs[2], ys[2]);
                    GL11.glTexCoord2f((col) * texW + scrollU, (row + 1) * texH + scrollV);
                    GL11.glVertex2f(xs[3], ys[3]);

                }
            }

            GL11.glEnd();
        }
    }
}