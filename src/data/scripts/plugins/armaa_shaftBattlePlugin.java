package data.scripts.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.FaderUtil;
import java.util.*;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;
import java.awt.Color;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;

public class armaa_shaftBattlePlugin extends BaseEveryFrameCombatPlugin {

    protected CombatEngineAPI engine;
    private final IntervalUtil effectInterval = new IntervalUtil(0.05f, 1.5f);
    private final IntervalUtil interval3 = new IntervalUtil(2f, 2f);
    private final IntervalUtil ascentInterval = new IntervalUtil(15f, 15f);
    private final IntervalUtil lightInterval = new IntervalUtil(2f, 2f);
    private final IntervalUtil reverseInterval = new IntervalUtil(1f, 1f);
    private final IntervalUtil attackInterval2 = new IntervalUtil(5f, 5f);
    private boolean playSecondPhase, playedSecondPhase = false, ascended = false;
    private boolean reversing = false, showLights = false;
    private final IntervalUtil attackInterval = new IntervalUtil(5f, 5f);
    private float spin = 0f;
    private final FaderUtil redLevel = new FaderUtil(1f, 2f, 2f);
    boolean warning = true;
    private boolean startedMusic = false;
    boolean reinforcementsTriggered = false;
    private float ascentRatio = 0;
    // Smooth scroll speed control (0 = stopped, 1 = full speed)
    private float scrollSpeed = 1f;
    private float targetScrollSpeed = 1f;

    // Tuning: bigger = faster easing
    private static final float SCROLL_DECEL_RATE = 1.25f; // slows down on warning
    private static final float SCROLL_ACCEL_RATE = 1.75f; // ramps up when reversing

    public Color shiftColor(Color start, Color end) {
        Color intermediateColor = Color.WHITE;
        int steps = 100; // Number of steps in the transition
        float ratio = (float) engine.getElapsedInContactWithEnemy() / steps;
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
        if (engine == null) {
            return;
        }
        float rate = (targetScrollSpeed < scrollSpeed) ? SCROLL_DECEL_RATE : SCROLL_ACCEL_RATE;
        scrollSpeed = approachExp(scrollSpeed, targetScrollSpeed, rate, amount);
        scrollSpeed = clamp01(scrollSpeed);
        if (playSecondPhase && !reversing) {
            reverseInterval.advance(amount);
        }
        if (reverseInterval.intervalElapsed() && !reversing) {
            Global.getSoundPlayer().playCustomMusic(0, 0, "music_armaa_pirate_encounter_hostile", true);
            reversing = true;
            targetScrollSpeed = 1f; // ramp back up once reverse/red phase begins
        }
        if (reversing && !ascended) {
            attackInterval2.advance(amount);
            ascentInterval.advance(amount);
            // no idea how these work
            redLevel.advance(amount);
            if (redLevel.getBrightness() >= 1f) {
                redLevel.fadeOut();
            } else if (redLevel.getBrightness() <= 0f) {
                redLevel.fadeIn();
            }
        }
        if (ascentInterval.intervalElapsed() && !ascended) {
            ascended = true;
        }
        if (ascentRatio >= 1 && !reinforcementsTriggered) {
            reinforcementsTriggered = true;
            Vector2f spawnPoint = new Vector2f(engine.getViewport().getCenter());
            spawnPoint.setY(spawnPoint.getY() + 1000f);
            ShipAPI boss = engine.getFleetManager(1).spawnShipOrWing("armaa_bellator_boss", spawnPoint, 270f, 0f);
            PersonAPI pilot = Misc.getAICoreOfficerPlugin("alpha_core").createPerson("alpha_core", "armaarmatura", new Random());
            pilot.setPortraitSprite("graphics/armaa/portraits/armaa_ironking.png");
            boss.setCaptain(pilot);
            engine.setDoNotEndCombat(false);
        }
        if (showLights && !playSecondPhase) {
            lightInterval.advance(amount);
        }
        if (lightInterval.intervalElapsed()) {
            playSecondPhase = true;
        }
        if (engine.getFleetManager(1).getCurrStrength() < 50f && !showLights && engine.getElapsedInContactWithEnemy() > 10f) {
            engine.setDoNotEndCombat(true);
            showLights = true;
            Global.getSoundPlayer().pauseCustomMusic();
            engine.getCombatUI().addMessage(0, engine.getPlayerShip(), "Test:", "WARNING - MASSIVE POWER SIGNATURE DETECTED");
            Global.getSoundPlayer().playUISound("armaa_boss_warning", 1f, 1f);
            targetScrollSpeed = 0f; // start easing to a stop as soon as warning happens
        }
        if (!startedMusic) {
            startedMusic = true;
            Global.getSoundPlayer().playCustomMusic(0, 0, "music_armaa_mission_descent", true);
        }
        if (reversing) {
            attackInterval.advance(amount);
        }
        if (attackInterval.intervalElapsed() && !playedSecondPhase) {
            playedSecondPhase = true;
        }
        float sprRatio = attackInterval.getElapsed() / attackInterval.getIntervalDuration();
        float lightRatio = lightInterval.getElapsed() / lightInterval.getIntervalDuration();
        float reverseRatio = ascentInterval.getElapsed() / ascentInterval.getIntervalDuration();
        if (lightRatio > 1) {
            lightRatio = 1;
        }
        Color endColor = new Color(0.02f, 0.02f, 0.02f, 1f);
        Color endColor3 = new Color(0, 0, 0, 0);
        Color bgColor = reversing && !ascended ? new Color(.3f * redLevel.getBrightness(), 0f, 0f, 1f) : endColor;
        engine.setBackgroundGlowColor(bgColor);
        engine.setBackgroundColor(bgColor);

        String str = "armaa_shaft";
        float initialWidth = Global.getSettings().getScreenWidth();
        float initialHeight = Global.getSettings().getScreenWidth(); // was width

        float currentWidth = initialWidth;
        float currentHeight = initialHeight;

        final float ZOOM_STEP = 1.015f; // old rate

        Float val = (Float) engine.getCustomData().get("armaa_bgHeight");
        if (val == null) {
            val = initialWidth;
        }
        float minVal = initialWidth;
        float maxVal = initialWidth * 6.2f;  // "deepest" point before looping

        // When scrollSpeed=0 -> step=1 (no movement). When scrollSpeed=1 -> step=ZOOM_STEP (full movement).
        float step = 1f + (ZOOM_STEP - 1f) * scrollSpeed;

        if (!reversing) {
            val *= step;
            if (val > maxVal) {
                val = minVal;
            }
        } else {
            val /= step;
            if (val < minVal) {
                val = maxVal;
            }
        }

        currentWidth = val;
        currentHeight = val;           // keep square

        engine.getCustomData().put("armaa_bgHeight", val);
        if (!reversing) {
            effectInterval.advance(amount);
        }
        float cloudSize = (float) (MathUtils.getRandomNumberInRange(300, 600));
        if (!ascended) {
            Color col = reversing ? new Color(.3f * redLevel.getBrightness(), 0f, 0f, 1f) : shiftColor(new Color(.2f, .2f, .2f, 1f), endColor);

            MagicRender.screenspace(
                    Global.getSettings().getSprite("misc", str),
                    MagicRender.positioning.CENTER,
                    new Vector2f(0, 0),
                    new Vector2f(0, 0),
                    new Vector2f((currentWidth / 12), (currentHeight / 12)),
                    new Vector2f(0, 0),
                    0f,
                    spin, //spin 
                    col,
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
                    Global.getSettings().getSprite("misc", str),
                    MagicRender.positioning.CENTER,
                    new Vector2f(0, 0),
                    new Vector2f(0, 0),
                    new Vector2f((currentWidth / 6), (currentHeight / 6)),
                    new Vector2f(0, 0),
                    0f,
                    spin, //spin 
                    col,
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
                    Global.getSettings().getSprite("misc", str + "_lights"),
                    MagicRender.positioning.CENTER,
                    new Vector2f(0, 0),
                    new Vector2f(0, 0),
                    new Vector2f((currentWidth / 6), (currentHeight / 6)),
                    new Vector2f(0, 0),
                    0f,
                    spin, //spin 
                    shiftColor(endColor3, new Color(1f, 1f, 0.7f, 1f * 1f - sprRatio)),
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
                    Global.getSettings().getSprite("misc", str),
                    MagicRender.positioning.CENTER,
                    new Vector2f(0, 0),
                    new Vector2f(0, 0),
                    new Vector2f(currentWidth, currentHeight),
                    new Vector2f(0, 0),
                    0f,
                    spin, //spin 
                    col,
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
                    Global.getSettings().getSprite("misc", str + "_lights"),
                    MagicRender.positioning.CENTER,
                    new Vector2f(0, 0),
                    new Vector2f(0, 0),
                    new Vector2f(currentWidth, currentHeight),
                    new Vector2f(0, 0),
                    0f,
                    spin, //spin 
                    shiftColor(endColor3, new Color(1f, 1f, 0.7f, 1f * 1f - sprRatio)),
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
            if (reversing && !ascended && !warning) {
                Vector2f vec = (Vector2f) engine.getCustomData().get("armaa_atmoWarningLoc" + 0);
                float elapsed = attackInterval2.getElapsed();
                float maxinterval = attackInterval2.getMaxInterval();
                float ratio = Math.min(1f, elapsed / maxinterval);
                SpriteAPI enemySpr = Global.getSettings().getSprite(Global.getSettings().getHullSpec("armaa_viator").getSpriteName());
                if(vec != null)
                MagicRender.battlespace(
                        enemySpr,
                        vec,
                        new Vector2f(0, 0),
                        new Vector2f(enemySpr.getWidth() * ratio, enemySpr.getHeight() * ratio),
                        new Vector2f(0, 0),
                        0f,
                        0f,
                        new Color(0.5f * ratio, 0.5f * ratio, 0.5f * ratio, 1f),
                        false,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        0.5f,
                        1f,
                        0.5f,
                        CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER
                );
            }
            if (attackInterval2.intervalElapsed() && reversing) {
                for (int i = 0; i < 1; i++) {
                    Vector2f vec = (Vector2f) engine.getCustomData().get("armaa_atmoWarningLoc" + i);
                    if (warning) {
                        float minX = engine.getViewport().getLLX(); // Leftmost X-coordinate of the viewport
                        float maxX = engine.getViewport().getLLX() + engine.getViewport().getVisibleWidth(); // Rightmost X-coordinate of the viewport	
                        float minY = engine.getViewport().getLLY(); // Leftmost X-coordinate of the viewport
                        float maxY = engine.getViewport().getLLY() + engine.getViewport().getVisibleHeight(); // Rightmost X-coordinate of the viewport

                        vec = new Vector2f(MathUtils.getRandomNumberInRange(minX, maxX), MathUtils.getRandomNumberInRange(minY, maxY));
                        MagicRender.battlespace(
                                Global.getSettings().getSprite("ceylon", "armaa_ceylontarget"),
                                vec,
                                new Vector2f(0, 0),
                                new Vector2f(1050, 1050),
                                new Vector2f(-250f, -250f),
                                0f,
                                10f,
                                new Color(255, 75, 0, 150),
                                false,
                                0f,
                                0f,
                                0f,
                                0f,
                                0f,
                                0.5f,
                                1f,
                                0.5f,
                                CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER
                        );
                        MagicRender.battlespace(
                                Global.getSettings().getSprite("ceylon", "armaa_ceylontarget"),
                                vec,
                                new Vector2f(0, 0),
                                new Vector2f(1150, 1150),
                                new Vector2f(-250f, -250f),
                                0f,
                                -10f,
                                new Color(255, 150, 0, 150),
                                false,
                                0f,
                                0f,
                                0f,
                                0f,
                                0f,
                                0.5f,
                                1f,
                                0.5f,
                                CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER
                        );
                        warning = false;
                        engine.getCustomData().put("armaa_atmoWarningLoc" + i, vec);
                    } else {
                        for (ShipAPI ship : engine.getShips()) {
                            if (ship.getOwner() != 1) {
                                continue;
                            }
                            if (!engine.getCustomData().containsKey("armaa_atmoWarningLoc" + i)) {
                                continue;
                            }
                            vec = (Vector2f) engine.getCustomData().get("armaa_atmoWarningLoc" + i);
                            engine.spawnProjectile(ship, engine.createFakeWeapon(ship, "minelayer1"), "minelayer1", vec, 0f, new Vector2f());
                            engine.getFleetManager(1).spawnShipOrWing("armaa_viator_variant", vec, 270f, 0f);
                            break;
                        }
                        warning = true;
                    }
                }
            }
        } else {
            float startScale = 4f;
            float endScale = 1f;

            ascentRatio += 0.15f * engine.getElapsedInLastFrame(); // speed: tweak 0.05-0.3
            if (ascentRatio > 1f) {
                ascentRatio = 1f;
            }

            float scale = startScale + (endScale - startScale) * ascentRatio;
            SpriteAPI spr = Global.getSettings().getSprite("misc", "armaa_shaft_top");
            MagicRender.screenspace(
                    spr,
                    MagicRender.positioning.CENTER,
                    new Vector2f(0, 0),
                    new Vector2f(0, 0),
                    new Vector2f(spr.getWidth() * scale, spr.getHeight() * scale),
                    new Vector2f(0, 0),
                    0f,
                    spin, //spin 
                    new Color(0.25f * ascentRatio, 0.25f * ascentRatio, 0.25f * ascentRatio, 1f),
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
        float vel = MathUtils.getRandomNumberInRange(-200, 200);

        if (!engine.isPaused()) {
            if (reversing && !ascended) {
                SpriteAPI spr = Global.getSettings().getSprite("misc", "armaa_bellator_lights");
                MagicRender.screenspace(
                        spr,
                        MagicRender.positioning.CENTER,
                        new Vector2f(0, 0),
                        new Vector2f(0, 0),
                        new Vector2f(spr.getWidth() * reverseRatio / 2f, spr.getHeight() * reverseRatio / 2f),
                        new Vector2f(0f, 0f),
                        180f,
                        spin / 5f,
                        new Color(1f * reverseRatio, 1f * reverseRatio, 1f * reverseRatio, 1f * reverseRatio),
                        true,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        -1f,
                        0f,
                        CombatEngineLayers.BELOW_SHIPS_LAYER
                );
                spr = Global.getSettings().getSprite(Global.getSettings().getHullSpec("armaa_bellator").getSpriteName());
                MagicRender.screenspace(
                        spr,
                        MagicRender.positioning.CENTER,
                        new Vector2f(0, 0),
                        new Vector2f(0, 0),
                        new Vector2f(spr.getWidth() * (reverseRatio / 2f), spr.getHeight() * (reverseRatio / 2f)),
                        new Vector2f(0f, 0f),
                        180f,
                        spin / 5f,
                        new Color(1f, 1f, 1f, 1f * (reverseRatio / 2f)),
                        false,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        -1f,
                        0f,
                        CombatEngineLayers.BELOW_SHIPS_LAYER
                );
            } else if (ascentRatio < 1) {
                SpriteAPI spr = Global.getSettings().getSprite("misc", "armaa_bellator_lights");
                MagicRender.screenspace(
                        spr,
                        MagicRender.positioning.CENTER,
                        new Vector2f(0, 1000 * ascentRatio),
                        new Vector2f(0, 0),
                        new Vector2f((spr.getWidth() / 2f) * (2f * ascentRatio), (spr.getHeight() / 2f) * (2f * ascentRatio)),
                        new Vector2f(0f, 0f),
                        180f,
                        spin / 5f,
                        new Color(1f * ascentRatio * 0.50f, 1f * ascentRatio * 0.50f, 1f * ascentRatio * 0.50f, 1f),
                        true,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        -1f,
                        0f,
                        CombatEngineLayers.BELOW_SHIPS_LAYER
                );
                spr = Global.getSettings().getSprite(Global.getSettings().getHullSpec("armaa_bellator").getSpriteName());
                MagicRender.screenspace(
                        spr,
                        MagicRender.positioning.CENTER,
                        new Vector2f(0, 0 + 1000 * ascentRatio),
                        new Vector2f(0, 0),
                        new Vector2f((spr.getWidth() / 2f) * (2f * ascentRatio), (spr.getHeight() / 2f) * (2f * ascentRatio)),
                        new Vector2f(0f, 0f),
                        180f,
                        spin / 5f,
                        new Color(1f * ascentRatio * 0.50f, 1f * ascentRatio * 0.50f, 1f * ascentRatio * 0.50f, 1f),
                        false,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        -1f,
                        0f,
                        CombatEngineLayers.BELOW_SHIPS_LAYER
                );
            }
            interval3.advance(amount);
            spin += amount;
            if (!ascended) {
                MagicRender.screenspace(
                        Global.getSettings().getSprite("misc", "armaa_atmo_cloud"),
                        MagicRender.positioning.CENTER,
                        new Vector2f(0, 0),
                        new Vector2f(vel, vel),
                        new Vector2f(cloudSize, cloudSize),
                        new Vector2f(100f, 100f),
                        0f,
                        spin / 5f,
                        shiftColor(new Color(0, 10, 20, 255), new Color(0, 0, 0, 255)),
                        false,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        .2f,
                        .2f,
                        .3f,
                        CombatEngineLayers.BELOW_SHIPS_LAYER
                );
            }
        }

        if (!engine.isPaused()) {

            for (CombatEntityAPI asteroid : engine.getAsteroids()) {
                engine.removeEntity(asteroid);
            }
            for (ShipAPI ship : engine.getShips()) {
                Global.getCombatEngine().maintainStatusForPlayerShip("atmo", "graphics/ui/icons/icon_repair_refit.png", "EM Interference", "Sensor range reduced", true);
                if (!ship.isAlive() || ship.isHulk() || !engine.isEntityInPlay(ship)) {
                    engine.removeEntity(ship);
                }
            }
        }
    }

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
    }

    private float clamp01(float x) {
        return Math.max(0f, Math.min(1f, x));
    }

    private float approachExp(float current, float target, float rate, float amount) {
        // amount is seconds; rate ~ "per second"
        float t = 1f - (float) Math.exp(-rate * amount);
        return current + (target - current) * t;
    }
}
