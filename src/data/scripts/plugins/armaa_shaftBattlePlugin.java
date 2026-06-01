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
import data.campaign.rulecmd.armaa_jeniusShaftBattle;
import data.scripts.missions.armaa_HulkRenderer;
import data.scripts.missions.armaa_RajanyaLaserAttack;
import data.scripts.missions.armaa_ShaftCylinderRenderer;
import data.scripts.missions.armaa_ShaftDescentRenderer;
import data.scripts.missions.armaa_WarningMessage;
import data.scripts.missions.armaa_rajanyaBossPlugin;
import data.scripts.missions.armaa_titleSplash;
import data.scripts.util.armaa_utils;
import org.lazywizard.lazylib.MathUtils;

public class armaa_shaftBattlePlugin extends BaseEveryFrameCombatPlugin {

    protected CombatEngineAPI engine;
    private final IntervalUtil effectInterval = new IntervalUtil(0.05f, 1.5f);
    private final IntervalUtil interval3 = new IntervalUtil(2f, 2f);
    private final IntervalUtil ascentInterval = new IntervalUtil(45f, 45f);
    private final IntervalUtil lightInterval = new IntervalUtil(2f, 2f);
    private final IntervalUtil reverseInterval = new IntervalUtil(3f, 3f);
    private final IntervalUtil attackInterval2 = new IntervalUtil(3.2f, 3.2f);
    private final IntervalUtil hulkCheckInterval = new IntervalUtil(0.5f, 0.5f);
    private boolean playSecondPhase, playedSecondPhase = false, ascended = false;
    private boolean reversing = false, showLights = false;
    private final IntervalUtil attackInterval = new IntervalUtil(5f, 5f);
    private float spin = 0f;
    private final FaderUtil redLevel = new FaderUtil(1f, 2f, 2f);
    boolean warning = true;
    private boolean startedMusic = false;
    boolean reinforcementsTriggered = false;
    private float ascentRatio = 0;
    private float scrollSpeed = 1f;
    private float targetScrollSpeed = 1f;
    armaa_RajanyaLaserAttack attack;
    armaa_WarningMessage warningMessage;
    armaa_HulkRenderer hulkRenderer;
    private armaa_ShaftCylinderRenderer cylinderRenderer = null;
    private armaa_ShaftDescentRenderer particleRenderer = null;
    // Reset before reaching dark center - tune this (0.4-0.8)

    private static final float SCROLL_DECEL_RATE = 1.25f;
    private static final float SCROLL_ACCEL_RATE = 1.75f;

    public Color shiftColor(Color start, Color end) {
        int steps = 100;
        float ratio = (float) engine.getElapsedInContactWithEnemy() / steps;
        if (ratio >= 1) {
            return end;
        }
        int red = (int) (start.getRed() * (1 - ratio) + end.getRed() * ratio);
        int green = (int) (start.getGreen() * (1 - ratio) + end.getGreen() * ratio);
        int blue = (int) (start.getBlue() * (1 - ratio) + end.getBlue() * ratio);
        int alpha = (int) (start.getAlpha() * (1 - ratio) + end.getAlpha() * ratio);
        return new Color(red, green, blue, alpha);
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null) {
            return;
        }
        Global.getCombatEngine().maintainStatusForPlayerShip(
                "atmo", "graphics/ui/icons/icon_repair_refit.png",
                "EM Interference", "Sensor range reduced", true);
        if (ascended) {
            // Always-present black fill
            float startScale = 4f;
            float endScale = 1f;
            ascentRatio += 0.15f * engine.getElapsedInLastFrame();
            if (ascentRatio > 1f) {
                ascentRatio = 1f;
            }
            float scale = startScale + (endScale - startScale) * ascentRatio;
            String spriteName = Global.getSettings().getString("armaa_missionBGs", "armaa_shaft_top");
            SpriteAPI spr = Global.getSettings().getSprite(spriteName);
            MagicRender.screenspace(
                    spr, MagicRender.positioning.CENTER,
                    new Vector2f(0, 0), new Vector2f(0, 0),
                    new Vector2f(Global.getSettings().getScreenWidth() * scale, Global.getSettings().getScreenWidth() * scale),
                    new Vector2f(0, 0), 0f, spin,
                    new Color(0.25f * ascentRatio, 0.25f * ascentRatio, 0.25f * ascentRatio, 1f),
                    false, 0f, 0f, 0f, 0f, 0f, 0f, -1, 0f,
                    CombatEngineLayers.CLOUD_LAYER
            );
        } else {
            float initialWidth = Global.getSettings().getScreenWidth();
            MagicRender.screenspace(
                    Global.getSettings().getSprite("misc", "armaa_cutscene"),
                    MagicRender.positioning.CENTER,
                    new Vector2f(0, 0), new Vector2f(0, 0),
                    new Vector2f(initialWidth * 2, initialWidth * 2),
                    new Vector2f(0, 0), 0f, 0f,
                    new Color(0, 0, 0, 255),
                    false, 0f, 0f, 0f, 0f, 0f, 0f, -1, 0f,
                    CombatEngineLayers.CLOUD_LAYER
            );
        }
        if (engine.isPaused()) {
            return;
        }
        hulkCheckInterval.advance(amount);
        float rate = (targetScrollSpeed < scrollSpeed) ? SCROLL_DECEL_RATE : SCROLL_ACCEL_RATE;
        scrollSpeed = approachExp(scrollSpeed, targetScrollSpeed, rate, amount);
        scrollSpeed = clamp01(scrollSpeed);

        if (playSecondPhase && !reversing) {
            if (warningMessage == null || warningMessage.isExpired()) {
                if (attack == null) {
                    attack = new armaa_RajanyaLaserAttack(1, 36, new Vector2f(), null, 0f);
                    Global.getCombatEngine().addLayeredRenderingPlugin(attack);
                }

                reverseInterval.advance(amount);
            }
        }
        if (reverseInterval.intervalElapsed() && !reversing) {
            Global.getSoundPlayer().playCustomMusic(0, 0, "music_armaa_rajanya_boss", true);
            reversing = true;
            targetScrollSpeed = 1f;
            if (cylinderRenderer != null) {
                cylinderRenderer.setSpeed(-1f);
            }
            if (particleRenderer != null) {
                particleRenderer.expire();
            }
        }
        if (reversing && !ascended) {
            attackInterval2.advance(amount);
            ascentInterval.advance(amount);
            redLevel.advance(amount);
            if (redLevel.getBrightness() >= 1f) {
                redLevel.fadeOut();
            } else if (redLevel.getBrightness() <= 0f) {
                redLevel.fadeIn();
            }
        }
        if (ascentInterval.intervalElapsed() && !ascended) {

            ascended = true;
            if (cylinderRenderer != null) {
                cylinderRenderer.expire();
            }
        }

        // Drive wall color from redLevel when reversing
        if (reversing && !ascended && cylinderRenderer != null) {
            float rb = redLevel.getBrightness();
            cylinderRenderer.setWallColor(new Color(rb, rb * 0.05f, rb * 0.05f, 1f));
        }

        if (ascentRatio >= 1 && !reinforcementsTriggered) {
            reinforcementsTriggered = true;
            Vector2f spawnPoint = new Vector2f(engine.getViewport().getCenter());
            spawnPoint.setY(spawnPoint.getY() + 5000f);
            ShipAPI boss = engine.getFleetManager(1).spawnShipOrWing("armaa_kshatriya_boss", spawnPoint, 270f, 0f);
            PersonAPI pilot = Misc.getAICoreOfficerPlugin("alpha_core").createPerson("alpha_core", "armaarmatura", new Random());
            pilot.setPortraitSprite("graphics/armaa/portraits/armaa_ironking.png");
            boss.setCaptain(pilot);
            boss.setName("Unit S3-1K13");
            for (ShipAPI module : boss.getChildModulesCopy()) {
                module.setCaptain(pilot);
            }
            Global.getCombatEngine().addLayeredRenderingPlugin(new armaa_WarningMessage("A@#%TFC!", "Registry: ERROR - Threat assessment: UNRESOLVED", null));
            Global.getSoundPlayer().playUISound("armaa_boss_warning", 0.85f, 1.1f);
            armaa_rajanyaBossPlugin.Config cfg = new armaa_rajanyaBossPlugin.Config();
            cfg.phase1Threshold = 0.75f;
            cfg.phase1Attacks = 3;
            cfg.phase2Threshold = 0.40f;
            cfg.phase2Attacks = 5;
            cfg.shrinkDuration = 2f;
            cfg.driftDuration = 5f;
            cfg.maxForegroundTime = 15f;
            cfg.ghostTint = Color.WHITE;

            engine.addPlugin(new armaa_rajanyaBossPlugin(boss, cfg));
            engine.setDoNotEndCombat(false);
        }

        if (showLights && !playSecondPhase) {
            lightInterval.advance(amount);
        }
        if (lightInterval.intervalElapsed()) {
            playSecondPhase = true;
        }

        if (engine.getFleetManager(1).getCurrStrength() < 50f && !showLights && engine.getTotalElapsedTime(false) > 20f) {
            showLights = true;
            Global.getSoundPlayer().pauseCustomMusic();
            warningMessage = new armaa_WarningMessage("W A R N I N G", "UNKNOWN ENEMY APPROACHING. PREPARE FOR PRE-EMPTIVE ATTACK", null);
            Global.getSoundPlayer().playUISound("armaa_boss_warning", 0.85f, 1.1f);
            Global.getCombatEngine().addLayeredRenderingPlugin(warningMessage);
            // fires 60 homing projectiles over an interval of 0.15s that rise from above then fire at target's location
            // not quite accurate and should be easily evaded
            Global.getCombatEngine().getFleetManager(1).spawnShipOrWing("hyperion_Attack", new Vector2f(0, -10000), 270f, 30f);
            Global.getCombatEngine().getFleetManager(1).spawnShipOrWing("hyperion_Attack", new Vector2f(-500, -10000), 270f, 30f);
            targetScrollSpeed = 0f;
            if (cylinderRenderer != null) {
                cylinderRenderer.setSpeed(0f);
            }
        }

        if (!startedMusic) {
            startedMusic = true;
            engine.setDoNotEndCombat(true);
            Global.getSoundPlayer().playCustomMusic(0, 0, "music_armaa_mission_descent", true);
            particleRenderer = new armaa_ShaftDescentRenderer();
            Global.getCombatEngine().addLayeredRenderingPlugin(particleRenderer);
            cylinderRenderer = new armaa_ShaftCylinderRenderer();
            Global.getCombatEngine().addLayeredRenderingPlugin(cylinderRenderer);
            hulkRenderer = new armaa_HulkRenderer();
            Global.getCombatEngine().addLayeredRenderingPlugin(hulkRenderer);
            int randomMinute = (int) (Math.random() * 60);

            int currentMinute = Global.getSector().getClock().getCal().get(java.util.Calendar.MINUTE);
            String minuteStr = currentMinute < 10 ? "0" + currentMinute : "" + currentMinute;

            Global.getCombatEngine().addLayeredRenderingPlugin(
                    new armaa_titleSplash("\"FORT ARDENT\" - " + Global.getSector().getClock().getHour() + ":" + minuteStr + " | " + Global.getSector().getClock().getDateString(),
                            " Fallen research facility"));
        }

        if (reversing) {
            attackInterval.advance(amount);
        }
        if (attackInterval.intervalElapsed() && !playedSecondPhase) {
            playedSecondPhase = true;
        }

        float lightRatio = lightInterval.getElapsed() / lightInterval.getIntervalDuration();
        float reverseRatio = ascentInterval.getElapsed() / ascentInterval.getIntervalDuration();
        if (lightRatio > 1) {
            lightRatio = 1;
        }

        Color endColor = new Color(0.15f, 0.02f, 0.02f, 1f);
        Color bgColor = reversing && !ascended
                ? new Color(.3f * redLevel.getBrightness(), 0f, 0f, 1f)
                : endColor;
        engine.setBackgroundGlowColor(bgColor);
        engine.setBackgroundColor(bgColor);

        if (!reversing || ascended) {
            effectInterval.advance(amount);
        }
        float cloudSize = MathUtils.getRandomNumberInRange(300, 600);

        if (!ascended) {

            if (reversing && !ascended && !warning) {
                warning = true;
                // do something here to warn about barrage?
            }

            if (attackInterval2.intervalElapsed() && reversing && warning) {
                if (attack == null || attack.isExpired()) {
                    attack = new armaa_RajanyaLaserAttack(1, 20, new Vector2f(), null, 0f);
                    Global.getCombatEngine().addLayeredRenderingPlugin(attack);
                }
                warning = false;
            }

        } else {
            float startScale = 4f;
            float endScale = 1f;
            ascentRatio += 0.15f * engine.getElapsedInLastFrame();
            if (ascentRatio > 1f) {
                ascentRatio = 1f;
            }
            float scale = startScale + (endScale - startScale) * ascentRatio;
            String spriteName = Global.getSettings().getString("armaa_missionBGs", "armaa_shaft_top");
            SpriteAPI spr = Global.getSettings().getSprite(spriteName);

            MagicRender.screenspace(
                    spr, MagicRender.positioning.CENTER,
                    new Vector2f(0, 0), new Vector2f(0, 0),
                    new Vector2f(Global.getSettings().getScreenWidth() * scale, Global.getSettings().getScreenWidth() * scale),
                    new Vector2f(0, 0), 0f, spin,
                    new Color(0.25f * ascentRatio, 0.25f * ascentRatio, 0.25f * ascentRatio, 1f),
                    false, 0f, 0f, 0f, 0f, 0f, 0f, -1, 0f,
                    CombatEngineLayers.CLOUD_LAYER
            );
        }

        if (!engine.isPaused()) {
            if (reversing && !ascended) {
                SpriteAPI spr = Global.getSettings().getSprite("misc", "armaa_bellator_lights");
                MagicRender.screenspace(
                        spr, MagicRender.positioning.CENTER,
                        new Vector2f(0, 0), new Vector2f(0, 0),
                        new Vector2f(spr.getWidth() * reverseRatio / 2f, spr.getHeight() * reverseRatio / 2f),
                        new Vector2f(0f, 0f), 180f, spin / 5f,
                        new Color(1f * reverseRatio, 1f * reverseRatio, 1f * reverseRatio, 1f * reverseRatio),
                        true, 0f, 0f, 0f, 0f, 0f, 0f, -1f, 0f,
                        CombatEngineLayers.BELOW_SHIPS_LAYER
                );
                spr = Global.getSettings().getSprite(Global.getSettings().getHullSpec("armaa_bellator").getSpriteName());
                float rb = redLevel.getBrightness();
                MagicRender.screenspace(
                        spr, MagicRender.positioning.CENTER,
                        new Vector2f(0, 0), new Vector2f(0, 0),
                        new Vector2f(spr.getWidth() * (reverseRatio / 2f), spr.getHeight() * (reverseRatio / 2f)),
                        new Vector2f(0f, 0f), 180f, spin / 5f,
                        new Color(rb * (reverseRatio / 2f), rb * (reverseRatio / 2f) * 0.05f, rb * (reverseRatio / 2f) * 0.05f, 1f * (reverseRatio / 2f)),
                        false, 0f, 0f, 0f, 0f, 0f, 0f, -1f, 0f,
                        CombatEngineLayers.BELOW_SHIPS_LAYER
                );
            } else if (ascentRatio < 1) {
                SpriteAPI spr = Global.getSettings().getSprite("misc", "armaa_bellator_lights");
                MagicRender.screenspace(
                        spr, MagicRender.positioning.CENTER,
                        new Vector2f(0, 1000 * ascentRatio), new Vector2f(0, 0),
                        new Vector2f((spr.getWidth() / 2f) * (2f * ascentRatio), (spr.getHeight() / 2f) * (2f * ascentRatio)),
                        new Vector2f(0f, 0f), 180f, spin / 5f,
                        new Color(1f * ascentRatio * 0.50f, 1f * ascentRatio * 0.50f, 1f * ascentRatio * 0.50f, 1f),
                        true, 0f, 0f, 0f, 0f, 0f, 0f, -1f, 0f,
                        CombatEngineLayers.BELOW_SHIPS_LAYER
                );
                spr = Global.getSettings().getSprite(Global.getSettings().getHullSpec("armaa_bellator").getSpriteName());
                MagicRender.screenspace(
                        spr, MagicRender.positioning.CENTER,
                        new Vector2f(0, 0 + 1000 * ascentRatio), new Vector2f(0, 0),
                        new Vector2f((spr.getWidth() / 2f) * (2f * ascentRatio), (spr.getHeight() / 2f) * (2f * ascentRatio)),
                        new Vector2f(0f, 0f), 180f, spin / 5f,
                        new Color(1f * ascentRatio * 0.50f, 1f * ascentRatio * 0.50f, 1f * ascentRatio * 0.50f, 1f),
                        false, 0f, 0f, 0f, 0f, 0f, 0f, -1f, 0f,
                        CombatEngineLayers.BELOW_SHIPS_LAYER
                );
            }

            interval3.advance(amount);
            spin += amount;

            if (!ascended && effectInterval.intervalElapsed()) {
                float vel = MathUtils.getRandomNumberInRange(-100, 100);
                MagicRender.screenspace(
                        Global.getSettings().getSprite("misc", "armaa_atmo_cloud2"),
                        MagicRender.positioning.CENTER,
                        new Vector2f(0 + MathUtils.getRandomNumberInRange(-25, 25), 0), new Vector2f(vel, vel),
                        new Vector2f(cloudSize * 4f, cloudSize * 4f), new Vector2f(100f, 100f),
                        0f, spin / 5f,
                        shiftColor(new Color(25, 15, 5, 150), new Color(0, 0, 0, 100)),
                        true, 0f, 0f, 0f, 0f, 0f, .5f, 1f, 1f,
                        CombatEngineLayers.ABOVE_SHIPS_LAYER
                );
            } else if (ascended && effectInterval.intervalElapsed()) {
                /*MagicRender.screenspace(
                        Global.getSettings().getSprite("misc", "armaa_atmo_cloud2"),
                        MagicRender.positioning.CENTER,
                        new Vector2f(0 + MathUtils.getRandomNumberInRange(-100, 100), 0), new Vector2f(0, 0),
                        new Vector2f(cloudSize * 8f, cloudSize * 8f), new Vector2f(100f, 100f),
                        0f, spin / 5f,
                        shiftColor(new Color(25, 15, 5, 150), new Color(0, 0, 0, 100)),
                        false, 0f, 0f, 0f, 0f, 0f, .5f, 3f, 1f,
                        CombatEngineLayers.BELOW_SHIPS_LAYER
                );*/
            }
        }

        if (!engine.isPaused()) {
            if (hulkCheckInterval.intervalElapsed()) {
                for (CombatEntityAPI asteroid : engine.getAsteroids()) {
                    engine.removeEntity(asteroid);
                }
            }
            for (ShipAPI ship : engine.getShips()) {
                if (reversing && !ascended) {
                    float rb = redLevel.getBrightness();
                    ship.fadeToColor(ship, new Color(rb, rb, rb, 1f), redLevel.getDurationIn(), redLevel.getDurationOut(), 0.9f);
                }
            }
            if (hulkCheckInterval.intervalElapsed()) {
                for (ShipAPI ship : engine.getShips()) {
                    if (!ship.isAlive() || ship.isHulk() || !engine.isEntityInPlay(ship)) {
                        boolean ascending = Math.random() < 0.30f;
                        if (hulkRenderer != null) {
                            hulkRenderer.addHulk(ship, ascending);
                        }
                        engine.removeEntity(ship);
                        continue;
                    }
                }
            }
        }
    }

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        armaa_utils.loadMissionTextures(armaa_jeniusShaftBattle.MISSION_TEXTURES_SHAFT);

    }

    private float clamp01(float x) {
        return Math.max(0f, Math.min(1f, x));
    }

    private float approachExp(float current, float target, float rate, float amount) {
        float t = 1f - (float) Math.exp(-rate * amount);
        return current + (target - current) * t;
    }
}
