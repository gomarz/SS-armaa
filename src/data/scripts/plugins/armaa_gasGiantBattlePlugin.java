package data.scripts.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import java.util.*;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;
import java.awt.Color;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.MathUtils;

import com.fs.starfarer.api.graphics.SpriteAPI;
import data.scripts.missions.armaa_TransitionExplosion;
import data.scripts.missions.armaa_WarningMessage;
import data.scripts.missions.armaa_titleSplash;
import data.scripts.util.armaa_utils;

public class armaa_gasGiantBattlePlugin extends BaseEveryFrameCombatPlugin {
    // for atmo battle

    protected CombatEngineAPI engine;
    private IntervalUtil interval = new IntervalUtil(20f, 20f);
    private IntervalUtil collapseInterval = new IntervalUtil(8f, 8f);
    private IntervalUtil collapseAftermathInterval = new IntervalUtil(8f, 8f);
    private IntervalUtil attackInterval = new IntervalUtil(2f, 2f);
    private IntervalUtil bossInterval = new IntervalUtil(10f, 10f);
    //try to improve perf a bit here
    private IntervalUtil bgInterval = new IntervalUtil(0.1f, 0.5f);
    private IntervalUtil asteroidInterval = new IntervalUtil(1f, 5f);
    private float spin = 0f;
    // 1 - 100% HP
    private boolean playedMusic, playedMusicSt2 = false;
    private boolean bossSpawn = false;
    private float initialStr = 0f;
    private float currentStr = 99999f;
    private boolean collapsing, collapseBegan, collapsed, collapsedAftermath = false;

    public Color shiftColor(Color start, Color end, float ratio) {
        Color intermediateColor = Color.WHITE;
        int steps = 100; // Number of steps in the transition
        long duration = 1500; // Duration of the transition in milliseconds		
        if (ratio >= 1) {
            return end;
        }

        int red = (int) (start.getRed() * (1 - ratio) + end.getRed() * ratio);
        int green = (int) (start.getGreen() * (1 - ratio) + end.getGreen() * ratio);
        int blue = (int) (start.getBlue() * (1 - ratio) + end.getBlue() * ratio);
        int alpha = (int) (start.getAlpha() * (1 - ratio) + end.getAlpha() * ratio);
        intermediateColor = new Color(red, green, blue, alpha);

        return intermediateColor;

    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (!playedMusic) {
            if (Global.getSettings().getModManager().isModEnabled("lunalib")) {
                //perfMode = LunaSettings.getBoolean("armaa", "armaa_performanceMode");
            }
            Global.getSoundPlayer().playCustomMusic(1, 1, "music_encounter_mysterious", true);
            playedMusic = true;
            engine.setDoNotEndCombat(true);
            String minuteStr = armaa_utils.getMinuteString();
            Global.getCombatEngine().addLayeredRenderingPlugin(
                    new armaa_titleSplash("GRAVION - HIGH ORBIT | " + Global.getSector().getClock().getHour() + ":" + minuteStr + " | " + Global.getSector().getClock().getDateString(),
                            " NO DATA AVAILABLE"));            
            engine.getCombatUI().addMessage(1, "", Color.red, "=INTERCEPTED TRANSMISSION=", Color.cyan, ":", Color.cyan, "" + Global.getSector().getPlayerPerson().getRank() + " " + Global.getSector().getPlayerPerson().getName().getLast() + "? Here? Lock down the station - full lockdown! We're compromised!");
        } else if (!playedMusicSt2) {
            float effectLevel = collapseInterval.getElapsed() / collapseInterval.getIntervalDuration();
            if (effectLevel > 0.95f) {
                try {
                    //a faint melody begins to rise like a broken music box echoing across time and space
                    //Global.getSoundPlayer().playCustomMusic(0, 0, null, true);
                    //Global.getSoundPlayer().
                    Global.getCombatEngine().addLayeredRenderingPlugin(
                            new armaa_titleSplash("????? | " + "??" + ":" + "??" + " | " + Global.getSector().getClock().getDateString(),
                                    " a faint melody begins to rise, echoing across time and space"));                           
                    Global.getSoundPlayer().playCustomMusic(0, 1, "music_armaa_gravionbattle", true);
                } catch (Exception e) {

                }
                playedMusicSt2 = true;
            } else if (effectLevel > 0f && effectLevel < 0.80f) {
                if (Math.random() < 0.20f) {
                    float minX = engine.getViewport().getLLX(); // Leftmost X-coordinate of the viewport
                    float maxX = engine.getViewport().getLLX() + engine.getViewport().getVisibleWidth(); // Rightmost X-coordinate of the viewport	
                    float minY = engine.getViewport().getLLY(); // Leftmost X-coordinate of the viewport
                    float maxY = engine.getViewport().getLLY() + engine.getViewport().getVisibleHeight(); // Rightmost X-coordinate of the viewport				
                    Vector2f loc = new Vector2f(MathUtils.getRandomNumberInRange(minX, maxX), MathUtils.getRandomNumberInRange(minY, maxY));
                    armaa_TransitionExplosion.spawn(MathUtils.getRandomPointInCircle(loc, 50f));

                    Global.getSoundPlayer().playUISound("gate_explosion", MathUtils.getRandomNumberInRange(0.5f, 1.5f), 10f);
                }
            }
        }
        if (engine == null) {
            return;
        }
        Color startColor = new Color(75, 30, 0, 255); // Starting color: (200, 0, 0)
        Color endColor = new Color(15, 30, 75, 100);   // Ending color: (0, 0, 200)
        Color startColorBG = new Color(150, 150, 150, 255); // Starting color: (200, 0, 0)
        Color endColorBG = new Color(175, 175, 175, 255);   // Ending color: (0, 0, 200)
        Color endColorBG2 = new Color(175, 175, 175, 0);   // Ending color: (0, 0, 200)		
        engine.setBackgroundColor(new Color(150, 125, 0, 255));
        float anomalySize = collapsing ? Math.min(1f, (engine.getTotalElapsedTime(false) / 100) - 0.50f) : 0;
        float mapMult = (engine.getTotalElapsedTime(false) / 100);
        if(engine.getFleetManager(1).getCurrStrength() < 10f && collapsing)
            anomalySize = 1f;
        if (!collapsedAftermath) {
            MagicRender.screenspace(
                    Global.getSettings().getSprite("misc", "armaa_gravion"),
                    MagicRender.positioning.CENTER,
                    new Vector2f(0 + MathUtils.getRandomNumberInRange(-1, 1) * 3f * (anomalySize), 0 + MathUtils.getRandomNumberInRange(-1, 1) * 3f * (anomalySize)),
                    new Vector2f(0, 0),
                    new Vector2f(Global.getSettings().getScreenWidth() * (1.2f + mapMult), Global.getSettings().getScreenWidth() * (1.2f + mapMult)),
                    new Vector2f(0, 0),
                    spin,
                    0f, //spin 
                    shiftColor(new Color(0f, 0f, 0f, 1f), startColorBG, (engine.getTotalElapsedTime(false) / 100) + 0.1f),
                    false,
                    0f,
                    0f,
                    0f,
                    0f,
                    0f,
                    0f,
                    -1,
                    0f,
                    CombatEngineLayers.CLOUD_LAYER
            );
        } else {
            MagicRender.screenspace(
                    Global.getSettings().getSprite("misc", "armaa_gravion2"),
                    MagicRender.positioning.CENTER,
                    new Vector2f(0, 0),
                    new Vector2f(0, 0),
                    new Vector2f(Global.getSettings().getScreenWidth() * (1.2f), Global.getSettings().getScreenWidth() * (1.2f)),
                    new Vector2f(0, 0),
                    spin,
                    0f, //spin 
                    new Color(0.33f, 0.33f, 0.33f, 1f),
                    false,
                    0f,
                    0f,
                    0f,
                    0f,
                    0f,
                    0f,
                    -1,
                    0f,
                    CombatEngineLayers.CLOUD_LAYER
            );
        }
        if (!engine.isPaused()) {

            engine.setBackgroundGlowColor(shiftColor(startColor, endColor, Math.min(1f, collapseAftermathInterval.getElapsed() / collapseAftermathInterval.getIntervalDuration())));
            float mult = engine.getViewport().getViewMult();
            float minX = engine.getViewport().getLLX(); // Leftmost X-coordinate of the viewport
            float maxX = engine.getViewport().getLLX() + engine.getViewport().getVisibleWidth(); // Rightmost X-coordinate of the viewport	
            float minY = engine.getViewport().getLLY(); // Leftmost X-coordinate of the viewport
            float maxY = engine.getViewport().getLLY() + engine.getViewport().getVisibleHeight(); // Rightmost X-coordinate of the viewport	
            float size = Math.max(1f, 1f * (float) (engine.getTotalElapsedTime(false) / 100));
            if ((engine.getTotalElapsedTime(false) / 100) > 0.15 && initialStr == 0) {
                initialStr = engine.getFleetManager(1).getCurrStrength();
                currentStr = initialStr;
            }
            bgInterval.advance(amount);
            asteroidInterval.advance(amount);
            float effectLevel = collapseInterval.getElapsed() / collapseInterval.getIntervalDuration() - collapseAftermathInterval.getElapsed() / collapseAftermathInterval.getIntervalDuration();
            if (collapsed && !collapseAftermathInterval.intervalElapsed()) {
                if (!collapseInterval.intervalElapsed()) {
                    collapseInterval.advance(amount);
                } else {
                    if (!collapsedAftermath) {
                        // had an overload here, but removed since it could cause some cheesy things
                    }
                    collapsedAftermath = true;
                }

                float explosionSize = collapseInterval.getElapsed() / collapseInterval.getIntervalDuration();
                MagicRender.screenspace(
                        Global.getSettings().getSprite("systemMap", "radar_circle"),
                        MagicRender.positioning.CENTER,
                        new Vector2f(0, 0),
                        new Vector2f(0, 0),
                        new Vector2f(Global.getSettings().getScreenWidth() * (2f + mapMult), Global.getSettings().getScreenWidth() * (2f + mapMult)),
                        new Vector2f(0, 0),
                        spin,
                        0f, //spin 
                        new Color(0.8f, 0.8f, 0.8f, Math.min(1f, (1f * explosionSize) - collapseAftermathInterval.getElapsed() / collapseAftermathInterval.getIntervalDuration())),
                        false,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        amount,
                        0f,
                        CombatEngineLayers.CLOUD_LAYER
                );
            }
            spin += amount / 2;
            SpriteAPI spr = Global.getSettings().getSprite(Global.getSettings().getHullSpec("station_small").getSpriteName());

            if (!collapsedAftermath) {
                float explosionSize = collapseInterval.getElapsed() / collapseInterval.getIntervalDuration();

                size = Math.min(1f, (engine.getTotalElapsedTime(false) / 100));
                spr = Global.getSettings().getSprite(Global.getSettings().getHullSpec("station_small").getSpriteName());
                float col = Math.max(0f, 0.5f - explosionSize);
                if (Math.random() * anomalySize > 0.66) {
                    MagicRender.screenspace(
                            Global.getSettings().getSprite("misc", "armaa_sfxpulse"),
                            MagicRender.positioning.CENTER,
                            new Vector2f(0 + MathUtils.getRandomNumberInRange(-1, 1) * 3f * (anomalySize), 0 + MathUtils.getRandomNumberInRange(-1, 1) * 3f * (anomalySize)),
                            new Vector2f(0, 0),
                            new Vector2f(spr.getWidth() * size, spr.getHeight() * size),
                            new Vector2f(800 * anomalySize, 800 * anomalySize),
                            -spin * 3,
                            0f,
                            Color.red,
                            true,
                            0f,
                            0f,
                            0f,
                            0f,
                            0f,
                            anomalySize / 2f,
                            anomalySize * 2f,
                            anomalySize * 2f,
                            CombatEngineLayers.CLOUD_LAYER
                    );
                }
                MagicRender.screenspace(
                        spr,
                        MagicRender.positioning.CENTER,
                        new Vector2f(0 + MathUtils.getRandomNumberInRange(-1, 1) * 3f * (anomalySize), 0 + MathUtils.getRandomNumberInRange(-1, 1) * 3f * (anomalySize)),
                        new Vector2f(0, 0),
                        new Vector2f(spr.getWidth() * size, spr.getHeight() * size),
                        new Vector2f(0, 0),
                        -spin * 3,
                        0f,
                        new Color(col, col, col, Math.max(0f, 0.8f - explosionSize)),
                        false,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        -1,
                        0f,
                        CombatEngineLayers.CLOUD_LAYER
                );

            }
            if (collapsing && !collapsed) {
                if (!collapsed && anomalySize >= 1f) {
                    // at this point size >= 1f
                    Global.getSoundPlayer().playUISound("cr_playership_critical", 0.67f, 10f);
                    engine.getCombatUI().addMessage(1, "", Color.red, "=INTERCEPTED TRANSMISSION=", Color.cyan, ":", Color.cyan, "No, no - kill the drive! We're not ready! It's going to-");
                    Global.getSoundPlayer().playUISound("gate_explosion_windup", 0.50f, 10f);
                    Global.getSoundPlayer().pauseCustomMusic();
                    collapsed = true;
                }
            }
            if (collapsedAftermath) {
                if (!collapseAftermathInterval.intervalElapsed()) {
                    collapseAftermathInterval.advance(amount);
                }

                float maxColor = Math.min(0.80f, collapseAftermathInterval.getElapsed() / collapseAftermathInterval.getIntervalDuration());
                if (Math.random() < 0.33) {
                    float xVel = (float) (MathUtils.getRandomNumberInRange(-800, 800));
                    float cloudSize = (float) (MathUtils.getRandomNumberInRange(1500, 2000) * (Math.random() * 2));
                    if (!collapseAftermathInterval.intervalElapsed()) {
                        MagicRender.battlespace(
                                Global.getSettings().getSprite("misc", "armaa_atmo_cloud"),
                                new Vector2f(MathUtils.getRandomNumberInRange(minX, maxX), engine.getViewport().getCenter().y + engine.getViewport().getVisibleHeight()),
                                new Vector2f(xVel, -MathUtils.getRandomNumberInRange(400, 1000) * mult),
                                new Vector2f(MathUtils.getRandomNumberInRange(cloudSize * 0.7f, cloudSize * 1.25f), MathUtils.getRandomNumberInRange(cloudSize * 0.70f, cloudSize * 1.25f)),
                                new Vector2f(0f, 0f),
                                0f,
                                0f,
                                new Color(70f / 255f, 50f / 255f, 50f / 255f, (Math.max(0f, 1f - collapseAftermathInterval.getElapsed() / collapseAftermathInterval.getIntervalDuration()))),
                                true,
                                0f,
                                0f,
                                0f,
                                0f,
                                0f,
                                (0.2f) * mult,
                                (0.5f + 0.80f) * mult,
                                (1f + 0.80f) * mult,
                                CombatEngineLayers.BELOW_SHIPS_LAYER
                        );
                    }

                    if ((!collapseAftermathInterval.intervalElapsed() && Math.random() < 0.04 || (collapsedAftermath && attackInterval.intervalElapsed()))) {
                        engine.spawnAsteroid(MathUtils.getRandomNumberInRange(1, 3), MathUtils.getRandomNumberInRange(-10000, 10000),
                                10000,
                                MathUtils.getRandomNumberInRange(-500, 500),
                                -MathUtils.getRandomNumberInRange(500, 600));
                        String asteroidSpr = "armaa_asteroid_big" + MathUtils.getRandomNumberInRange(1, 3);
                        MagicRender.battlespace(
                                Global.getSettings().getSprite("misc", asteroidSpr),
                                new Vector2f(MathUtils.getRandomNumberInRange(minX, maxX), engine.getViewport().getCenter().y + engine.getViewport().getVisibleHeight()),
                                new Vector2f(MathUtils.getRandomNumberInRange(-200, 200), -MathUtils.getRandomNumberInRange(100, 200)),
                                new Vector2f(Global.getSettings().getSprite("misc", asteroidSpr).getWidth() * 2f, Global.getSettings().getSprite("misc", asteroidSpr).getHeight() * 2f),
                                new Vector2f(0f, 0f),
                                MathUtils.getRandomNumberInRange(0f, 360f),
                                MathUtils.getRandomNumberInRange(-10f, 10f),
                                new Color((1f - effectLevel) * 0.20f, (1f - effectLevel) * 0.20f, (1f - effectLevel) * 0.20f, 1f),
                                false,
                                0f,
                                0f,
                                0f,
                                0f,
                                0f,
                                0.25f,
                                7f,
                                2f,
                                CombatEngineLayers.BELOW_SHIPS_LAYER
                        );
                    }
                }
                if (Math.random() < 0.15) {
                    float xVel = (float) (MathUtils.getRandomNumberInRange(-800, 800));
                    float cloudSize = (float) (MathUtils.getRandomNumberInRange(1500, 2000) * (Math.random() * 2));
                    if (!collapseAftermathInterval.intervalElapsed()) {
                        MagicRender.battlespace(
                                Global.getSettings().getSprite("misc", "armaa_atmo_cloud2"),
                                new Vector2f(MathUtils.getRandomNumberInRange(minX, maxX), engine.getViewport().getCenter().y + engine.getViewport().getVisibleHeight()),
                                new Vector2f(xVel, -MathUtils.getRandomNumberInRange(800, 1000) * mult),
                                new Vector2f(MathUtils.getRandomNumberInRange(cloudSize * 0.7f, cloudSize * 1.25f), MathUtils.getRandomNumberInRange(cloudSize * 0.70f, cloudSize * 1.25f)),
                                new Vector2f(0f, 0f),
                                0f,
                                0f,
                                new Color(100f / 255f, 70f / 255f, 70f / 255f, (Math.max(0f, 0.8f - collapseAftermathInterval.getElapsed() / collapseAftermathInterval.getIntervalDuration()))),
                                true,
                                0f,
                                0f,
                                0f,
                                0f,
                                0f,
                                (0.2f) * mult,
                                (0.5f + 0.80f) * mult,
                                (0.5f + 0.80f) * mult,
                                CombatEngineLayers.BELOW_SHIPS_LAYER
                        );
                    }
                    if (!collapseAftermathInterval.intervalElapsed()) {
                        MagicRender.battlespace(
                                Global.getSettings().getSprite("misc", "armaa_atmo_cloud"),
                                new Vector2f(MathUtils.getRandomNumberInRange(minX, maxX), engine.getViewport().getCenter().y + engine.getViewport().getVisibleHeight()),
                                new Vector2f(xVel, -MathUtils.getRandomNumberInRange(800, 1000) * mult),
                                new Vector2f(MathUtils.getRandomNumberInRange(cloudSize * 0.7f, cloudSize * 1.25f), MathUtils.getRandomNumberInRange(cloudSize * 0.70f, cloudSize * 1.25f)),
                                new Vector2f(0f, 0f),
                                0f,
                                0f,
                                new Color(100f / 255f, 70f / 255f, 70f / 255f, (Math.max(0f, 0.5f - collapseAftermathInterval.getElapsed() / collapseAftermathInterval.getIntervalDuration()))),
                                false,
                                0f,
                                0f,
                                0f,
                                0f,
                                0f,
                                (0.2f) * mult,
                                (0.5f + 0.80f) * mult,
                                (0.5f + 0.80f) * mult,
                                CombatEngineLayers.ABOVE_SHIPS_LAYER
                        );
                    }
                }
                if (!interval.intervalElapsed()) {
                    interval.advance(amount);
                }
                float gateRatio = interval.getElapsed() / interval.getIntervalDuration();
                float bossRatio = bossInterval.getElapsed() / bossInterval.getIntervalDuration();
                if (gateRatio > 0) {
                    spr = Global.getSettings().getSprite("gates", "glow_rays");
                    MagicRender.screenspace(
                            spr,
                            MagicRender.positioning.CENTER,
                            new Vector2f(0, 0),
                            new Vector2f(0, 0),
                            new Vector2f(spr.getWidth() * 2f * (1f - gateRatio), spr.getHeight() * 2f * (1f - gateRatio)),
                            new Vector2f(0, 0),
                            0f,
                            0f,
                            new Color(1f, 0.80f, 0.80f, 1f - gateRatio),
                            true,
                            0f,
                            0f,
                            0f,
                            0f,
                            0f,
                            0f,
                            -1,
                            0f,
                            CombatEngineLayers.CLOUD_LAYER
                    );
                    spr = Global.getSettings().getSprite("gates", "glow_whirl2");
                    MagicRender.screenspace(
                            spr,
                            MagicRender.positioning.CENTER,
                            new Vector2f(0, 0),
                            new Vector2f(0, 0),
                            new Vector2f(spr.getWidth() * 2f * (1f - gateRatio), spr.getHeight() * 2f * (1f - gateRatio)),
                            new Vector2f(0, 0),
                            -spin * 3,
                            0f,
                            new Color(1f, 0.80f, 0.80f, 1f - gateRatio),
                            true,
                            0f,
                            0f,
                            0f,
                            0f,
                            0f,
                            0f,
                            -1,
                            0f,
                            CombatEngineLayers.CLOUD_LAYER
                    );
                }
                if (!bossSpawn) {
                    engine.setDoNotEndCombat(true);
                    spr = Global.getSettings().getSprite(Global.getSettings().getHullSpec("tesseract").getSpriteName());
                    MagicRender.screenspace(
                            spr,
                            MagicRender.positioning.CENTER,
                            new Vector2f(0, 0 - 500 * bossRatio - (400 * bossRatio * 2)),
                            new Vector2f(0, 0),
                            new Vector2f(Math.max(spr.getWidth() * 0.25f, spr.getWidth() * bossRatio), Math.max(spr.getHeight() * 0.25f, spr.getHeight() * bossRatio)),
                            new Vector2f(0, 0),
                            spin * 6 + 30 * bossRatio,
                            0f,
                            new Color(Math.max(0.1f, 1f * bossRatio), Math.max(0.1f, 1f * bossRatio), Math.max(0.1f, 1f * bossRatio), 1f),
                            false,
                            0f,
                            0f,
                            0f,
                            0f,
                            0f,
                            0f,
                            -1,
                            0f,
                            CombatEngineLayers.CLOUD_LAYER
                    );
                    MagicRender.screenspace(
                            Global.getSettings().getSprite("backgrounds", "star2"),
                            MagicRender.positioning.CENTER,
                            new Vector2f(0, 0 - 500 * bossRatio - (200 * bossRatio * 2)),
                            new Vector2f(0, 0),
                            new Vector2f(spr.getWidth() * (1f - bossRatio), spr.getHeight() * (1f - bossRatio)),
                            new Vector2f(0, 0),
                            0f,
                            0f,
                            new Color(1f, 0f, 0f, 1f * (1f - bossRatio)),
                            true,
                            0f,
                            0f,
                            0f,
                            0f,
                            0f,
                            0f,
                            -1,
                            0f,
                            CombatEngineLayers.CLOUD_LAYER
                    );
                }
                if (!bossSpawn && engine.getFleetManager(1).getCurrStrength() <= 0) {
                    //start moving tessie to the foreground
                    bossInterval.advance(amount);
                    if (bossInterval.intervalElapsed() || bossRatio >= 0.90f) {
                        bossSpawn = true;
                        // Apparently this can be the case
                        spawnBoss();
                    }

                } else {
                    if (engine.getFleetManager(1).getCurrStrength() > 0 && !bossSpawn && collapsedAftermath) {
                        boolean noEnemies = true;
                        for (int i = 0; i < engine.getAllShips().size(); i++) {
                            if (engine.getAllShips().get(i).getOwner() == 1) {
                                noEnemies = false;
                            }
                        }
                        if (noEnemies) {
                            //engine.setDoNotEndCombat(true); 
                            bossInterval.advance(amount);
                            if (bossInterval.intervalElapsed() || bossRatio >= 0.90f) {
                                bossSpawn = true;
                                // Apparently this can be the case
                                spawnBoss();
                            }
                        }

                    }
                }
            }
            ShipAPI bossEne = null;
            if (engine.getCustomData().containsKey("armaa_atmo_boss")) {
                bossEne = (ShipAPI) engine.getCustomData().get("armaa_atmo_boss");
            }
            if (!collapseBegan && ((engine.getTotalElapsedTime(false) / 100) > 0.50f ||engine.getFleetManager(1).getCurrStrength() < 50f && engine.getTotalElapsedTime(false) > 20f)) {
                Global.getSoundPlayer().playUISound("cr_allied_critical", 0.77f, 10f);
                engine.getCombatUI().addMessage(1, "", Color.red, "=INTERCEPTED TRANSMISSION=", Color.cyan, ":", Color.cyan,
                        "Forget it! Activate the device. We can't let our progress go to waste.");
                collapseBegan = true;
                collapsing = true;
            }

            if (!collapsed && bgInterval.intervalElapsed() && Math.random() < 0.10f) {
                MagicRender.battlespace(
                        Global.getSettings().getSprite("terrain", "aurora"),
                        new Vector2f(maxX, engine.getViewport().getCenter().y),
                        new Vector2f(-MathUtils.getRandomNumberInRange(500, 1000) * mult, MathUtils.getRandomNumberInRange(-100, 100)),
                        new Vector2f(Global.getSettings().getScreenWidth() * 1.3f, Global.getSettings().getScreenWidth() * 1.3f),
                        new Vector2f(20f, 20f),
                        0f,
                        0f,
                        new Color(90, 60, 50, 150),
                        true,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        (0.2f) * mult,
                        (0.5f + 0.80f) * mult * (float) Math.random(),
                        (0.5f + 0.80f) * mult,
                        CombatEngineLayers.BELOW_SHIPS_LAYER
                );
            }
            float xVel = (float) (MathUtils.getRandomNumberInRange(-800, 800));
            float cloudSize = (float) (MathUtils.getRandomNumberInRange(1500, 2000) * (Math.random() * 2));
            xVel = (float) (MathUtils.getRandomNumberInRange(-1000, 1000));
            cloudSize = (float) (MathUtils.getRandomNumberInRange(500, 1000));
        }
        if (!engine.isPaused()) {
            attackInterval.advance(amount);

            for (CombatEntityAPI asteroid : engine.getAsteroids()) {
                if ((engine.getTotalElapsedTime(false) / 100) < 1 && !collapsed) {
                    engine.removeEntity(asteroid);
                }

            }

            float effectLevel = collapseInterval.getElapsed() / collapseInterval.getIntervalDuration() - collapseAftermathInterval.getElapsed() / collapseAftermathInterval.getIntervalDuration();
            if (collapsed && !collapsedAftermath && effectLevel > 0.60f && bgInterval.intervalElapsed() && Math.random() > 0.50f) {
                String asteroidStr = "asteroid_" + MathUtils.getRandomNumberInRange(1, 4);
                float minX = engine.getViewport().getLLX(); // Leftmost X-coordinate of the viewport
                float maxX = engine.getViewport().getLLX() + engine.getViewport().getVisibleWidth(); // Rightmost X-coordinate of the viewport
                MagicRender.battlespace(
                        Global.getSettings().getSprite("terrain", asteroidStr),
                        new Vector2f(MathUtils.getRandomNumberInRange(minX, maxX), engine.getViewport().getCenter().y + engine.getViewport().getVisibleHeight()),
                        new Vector2f(MathUtils.getRandomNumberInRange(-200, 200), -MathUtils.getRandomNumberInRange(800, 1000)),
                        new Vector2f(Global.getSettings().getSprite("terrain", asteroidStr).getWidth() * MathUtils.getRandomNumberInRange(1, 2), Global.getSettings().getSprite("terrain", asteroidStr).getHeight() * MathUtils.getRandomNumberInRange(1, 2)),
                        new Vector2f(0f, 0f),
                        MathUtils.getRandomNumberInRange(0f, 360f),
                        MathUtils.getRandomNumberInRange(-10f, 10f),
                        new Color(1f - effectLevel, 1f - effectLevel, 1f - effectLevel, 1f),
                        false,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        0.2f,
                        1f,
                        1f,
                        CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER
                );
            }
            /*
			if((collapsedAftermath && Math.random() < 0.40f) && bgInterval.intervalElapsed())
			{
				float minX = engine.getViewport().getLLX(); // Leftmost X-coordinate of the viewport
				float maxX = engine.getViewport().getLLX() + engine.getViewport().getVisibleWidth(); // Rightmost X-coordinate of the viewport
				if(Math.random() > 0.50f)
				engine.spawnAsteroid(MathUtils.getRandomNumberInRange(1,3),MathUtils.getRandomNumberInRange(-10000,10000),
				10000,
				MathUtils.getRandomNumberInRange(-500,500),
				-MathUtils.getRandomNumberInRange(500,600));				
			}
             */
            if ((collapsedAftermath && asteroidInterval.intervalElapsed())) {
                float minX = engine.getViewport().getLLX(); // Leftmost X-coordinate of the viewport
                float maxX = engine.getViewport().getLLX() + engine.getViewport().getVisibleWidth(); // Rightmost X-coordinate of the viewport		
                float minY = engine.getViewport().getLLY(); // Leftmost X-coordinate of the viewport
                float maxY = engine.getViewport().getLLY() + engine.getViewport().getVisibleHeight(); // Rightmost X-coordinate of the viewport				
                String asteroidStr = "asteroid_" + MathUtils.getRandomNumberInRange(1, 4);
                MagicRender.battlespace(
                        Global.getSettings().getSprite("terrain", asteroidStr),
                        new Vector2f(MathUtils.getRandomNumberInRange(minX, maxX), engine.getViewport().getCenter().y + engine.getViewport().getVisibleHeight()),
                        new Vector2f(MathUtils.getRandomNumberInRange(-200, 200), -MathUtils.getRandomNumberInRange(-300, 400)),
                        new Vector2f(Global.getSettings().getSprite("terrain", asteroidStr).getWidth() * 0.50f, Global.getSettings().getSprite("terrain", asteroidStr).getHeight() * 0.50f),
                        new Vector2f(0f, 0f),
                        MathUtils.getRandomNumberInRange(0f, 360f),
                        MathUtils.getRandomNumberInRange(-15f, 15f),
                        new Color(127, 100, 100, 255),
                        false,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        0.5f,
                        4f,
                        2f,
                        CombatEngineLayers.BELOW_SHIPS_LAYER
                );

            }
            for (ShipAPI ship : engine.getShips()) {
                float velX = ship.getVelocity().getX();
                float velY = ship.getVelocity().getY();
                if (collapsed || collapsing) {
                    if (!collapseAftermathInterval.intervalElapsed()) {
                        ship.fadeToColor(null, Color.black, 0.1f, 0.1f, effectLevel);
                    }
                }
                if (!collapsing) {
                    if (!ship.isPhased() || ship.isFighter() && ship.getEngineController().isFlamedOut() || !ship.isFinishedLanding() && !ship.isLanding()) {
                        CombatUtils.applyForce(ship, 180f, 1f);
                    }
                }
                if (attackInterval.intervalElapsed() && !collapsed) {
                    if (Math.random() < 0.10f) {
                        engine.spawnEmpArcVisual(new Vector2f(10000, -10000 * ship.getOwner()), null, ship.getLocation(), null, (float) Math.random() * 100f, generateLightningColor(), Color.white);
                        if (Math.random() > 0.50f) {
                            break;
                        }

                    }
                }

                if (!collapsedAftermath) {
                    if (!ship.isAlive() || ship.isHulk() || !engine.isEntityInPlay(ship)) {
                        engine.removeEntity(ship);
                    }
                }
            }
        }
    }
    // Method to generate a random color for a brighter, more vibrant reentry effect

    private Color generateLightningColor() {
        float hue;
        float saturation = 1.0f; // Slight variation in saturation
        float brightness = 1.0f; // Slight variation in brightness
        Random random = new Random();
        int type = random.nextInt(2); // Pick a random lightning type

        switch (type) {
            case 0: // Classic Purple Lightning
                hue = randomInRange(250, 270) / 360f;
                break;
            case 1: // Deep Violet or Magenta
                hue = randomInRange(270, 290) / 360f;
                break;
            default:
                hue = 0.75f; // Fallback to a strong purple
        }

        Color baseColor = Color.getHSBColor(hue, saturation, brightness);

        // Add transparency
        int alpha = randomInRange(120, 180); // Adjust for desired transparency
        return new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), alpha);
    }

    private int randomInRange(int min, int max) {
        Random random = new Random();
        return min + random.nextInt(max - min + 1);
    }

    private void spawnBoss() 
    {
        if (Misc.getAICoreOfficerPlugin(Commodities.OMEGA_CORE) == null) {
            return;
        }
        PersonAPI pilot = Misc.getAICoreOfficerPlugin(Commodities.OMEGA_CORE).createPerson(Commodities.OMEGA_CORE, "remnant", new Random());
        ShipAPI ship = engine.getFleetManager(1).spawnShipOrWing("armaa_facet_boss", new Vector2f(-500, -10000), 90f, 5f);
        ship.setCaptain(pilot);
        ship = engine.getFleetManager(1).spawnShipOrWing("armaa_tesseract_boss", new Vector2f(0, -10000), 90f, 15f);
        ship.setCaptain(pilot);
            armaa_WarningMessage warningMessage = new armaa_WarningMessage("W A R N I N G", "UNKNOWN ENEMY APPROACHING", null);
            Global.getSoundPlayer().playUISound("armaa_boss_warning", 0.85f, 1.1f);
            Global.getCombatEngine().addLayeredRenderingPlugin(warningMessage);            
        if (!Global.getSector().getMemoryWithoutUpdate().contains("$armaa_engagedValkHunters")) {
            ShipAPI valkazard = engine.getFleetManager(1).spawnShipOrWing("armaa_valkazard_boss", new Vector2f(500, -10000), 90f, 2f);
            valkazard.setCaptain(Global.getSector().getImportantPeople().getPerson("armaa_redeye"));
        } else {
            ship = engine.getFleetManager(1).spawnShipOrWing("armaa_facet_boss", new Vector2f(500, -10000), 90f, 2f);
            ship.setCaptain(pilot);
        }
        engine.setDoNotEndCombat(false);
    }

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
    }
}
