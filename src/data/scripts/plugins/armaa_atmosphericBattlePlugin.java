package data.scripts.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import java.util.*;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;
import java.awt.Color;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.MathUtils;
import lunalib.lunaSettings.LunaSettings;
import com.fs.starfarer.api.graphics.SpriteAPI;
import data.scripts.missions.armaa_ReentryEffect;
import data.scripts.missions.armaa_titleSplash;
import data.scripts.util.armaa_utils;

public class armaa_atmosphericBattlePlugin extends BaseEveryFrameCombatPlugin {
    // for atmo battle

    boolean warning = true;
    protected CombatEngineAPI engine;
    private IntervalUtil interval = new IntervalUtil(.025f, .05f);
    private IntervalUtil interval3 = new IntervalUtil(2f, 2f);
    private IntervalUtil bgInterval = new IntervalUtil(0.05f, 0.1f);
    private IntervalUtil attackInterval = new IntervalUtil(2f, 2f);
    private IntervalUtil cutsceneTextInterval = new IntervalUtil(2f, 2f);
    private IntervalUtil cutsceneInterval = new IntervalUtil(3f, 3f);
    private final IntervalUtil spinInterval = new IntervalUtil(0.05f, 0.05f);
    armaa_ReentryEffect fx;
    private boolean runOnce = false;
    private float spin = 0f, ratioMod = 0f;
    private boolean playedMusic = false;
    private boolean perfMode = false;
    private boolean showedTitle = false;
    private boolean startedFade = false;
    boolean cutsceneOver = false;
    boolean hidCutscene = false;
    private float bossStage = 1f, ratio = 0f, bgStage = 0f;
    private float battleStr = -99f;
    private boolean bossLine1, bossLine2, bossLine3, firstContact = false, stationAlive = true;
    private boolean spawnedBoss = false;
    private int diagLine = 0;
    public static Map MANUVER_MALUS = new HashMap();
    FleetMemberAPI largest = null;

    static {
        /*mass_mult.put(HullSize.FRIGATE, 3f);
        mass_mult.put(HullSize.DESTROYER, 3f);
        mass_mult.put(HullSize.CRUISER, 2f);
        mass_mult.put(HullSize.CAPITAL_SHIP, 2f); massy*/
        MANUVER_MALUS.put(HullSize.FIGHTER, 0.90f);
        MANUVER_MALUS.put(HullSize.FRIGATE, 0.80f);
        MANUVER_MALUS.put(HullSize.DESTROYER, 0.70f);
        MANUVER_MALUS.put(HullSize.CRUISER, 0.60f);
        MANUVER_MALUS.put(HullSize.CAPITAL_SHIP, 0.50f);
    }

    public Color shiftColor(Color start, Color end, float ratio) {
        Color intermediateColor = Color.WHITE;
        // Number of steps in the transition
        // Duration of the transition in milliseconds
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
        if (!playedMusic && cutsceneOver) {
            if (Global.getSettings().getModManager().isModEnabled("lunalib")) {
                perfMode = LunaSettings.getBoolean("armaa", "armaa_performanceMode");
            }
            engine.setRenderStarfield(false);
            Global.getSoundPlayer().playCustomMusic(1, 1, "music_armaa_ax_bounty", true);
            playedMusic = true;
            engine.setDoNotEndCombat(true);
        }
        if (!runOnce) {
            engine.getFleetManager(0).setSuppressDeploymentMessages(true);
            engine.getCombatUI().hideShipInfo();

            Global.getSoundPlayer().playCustomMusic(1, 1, "music_encounter_mysterious_non_aggressive", true);
            runOnce = true;

            //let's get the largest ship in player fleet, that can't be deployed
            for (FleetMemberAPI member : Global.getSector().getPlayerFleet().getFleetData().getMembersInPriorityOrder()) {
                if (largest == null && (member.isCruiser() || member.isCapital()) && !member.isCivilian()) {
                    largest = member;
                } else if (member.isCapital() && (largest != null && !largest.isCapital())) {
                    largest = member;
                }
            }
        }
        if (engine == null) {
            return;
        }
        Color startColor = new Color(75, 0, 0, 255);

        Color endColor = new Color(15, 30, 75, 100);

        Color endColorBG = new Color(100, 100, 100, 255);

        if (diagLine < 3 && !engine.isPaused()) {
            cutsceneTextInterval.advance(amount);
            for (ShipAPI ship : engine.getAllShips()) {
                ship.blockCommandForOneFrame(ShipCommand.ACCELERATE);
               // ship.blockCommandForOneFrame(ShipCommand.DECELERATE);
               // ship.blockCommandForOneFrame(ShipCommand.TURN_LEFT);
               // ship.blockCommandForOneFrame(ShipCommand.TURN_RIGHT);
                ship.getVelocity().set(0, 0);
            }
            if (engine.getPlayerShip() != null && Math.random() < 0.10f) {

            }
        }
        if (cutsceneTextInterval.intervalElapsed() && diagLine < 5 && !engine.isPaused()) {
            playAtmosphericEntryLine(engine, diagLine);
            diagLine++;

            if (diagLine == 3 && !showedTitle) {
                /*
                SpriteAPI spr = Global.getSettings().getSprite("mission_splash", "armaa_acoc_splash");
                MagicRender.screenspace(
                        spr,
                        MagicRender.positioning.CENTER,
                        new Vector2f(0, 0),
                        new Vector2f(0, 0),
                        new Vector2f((spr.getWidth()), (spr.getHeight())),
                        new Vector2f(0, 0),
                        0f,
                        0f, //spin
                        new Color(1f, 1f, 1f, 0.9f),
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
                showedTitle = true;
                */
            String minuteStr = armaa_utils.getMinuteString();
            Global.getCombatEngine().addLayeredRenderingPlugin(
                    new armaa_titleSplash("JENIUS - STAGING POINT A  " + Global.getSector().getClock().getHour() + ":" + minuteStr + " | " + Global.getSector().getClock().getDateString(),
                            " Automated defense perimeter"));                
            }
        }
        float viewMult = engine.getViewport().getViewMult();
        float inv = 1f / viewMult;

        float w = Global.getSettings().getScreenWidth();
        float h = Global.getSettings().getScreenHeight();

        float diag = (float) Math.sqrt(w * w + h * h);
        float minCover = diag * 1.05f;

        float base = w * (1.2f + 3f * Math.min(2f,ratio));

        // actual size with zoom response
        float s1 = base * inv;

        s1 = Math.max(s1, minCover);

        Vector2f bgsize = new Vector2f(s1, s1);
        if (ratio >= 0.1f) {
            SpriteAPI spr = Global.getSettings().getSprite("misc", "armaa_atmo2");
            MagicRender.screenspace(
                    spr,
                    MagicRender.positioning.CENTER,
                    new Vector2f(0, 0),
                    new Vector2f(0, 0),
                    bgsize,
                    new Vector2f(0, 0),
                    spin,
                    0f, //spin
                    shiftColor(new Color(50, 50, 50, 255), endColorBG, bgStage),
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
        int a = Math.max(0, 255 - (int) (ratio * 255f));
        Color bg2Color = new Color(200, 155, 155, a);
        if (ratio < 0.45f && hidCutscene) {
            SpriteAPI spr = Global.getSettings().getSprite("misc", "armaa_atmo");
            MagicRender.screenspace(
                    spr,
                    MagicRender.positioning.CENTER,
                    new Vector2f(0, 0),
                    new Vector2f(0, 0),
                    bgsize,
                    new Vector2f(0, 0),
                    spin,
                    0f, //spin
                    shiftColor(new Color(155, 100, 100, 255), bg2Color, ratio),
                    false,
                    0f,
                    0,
                    0f,
                    0f,
                    0f,
                    0f,
                    -1,
                    0f,
                    CombatEngineLayers.CLOUD_LAYER
            );
        }

        if (!hidCutscene) {
            float level = cutsceneInterval.getElapsed() / cutsceneInterval.getIntervalDuration();
            SpriteAPI spr = Global.getSettings().getSprite("misc", "armaa_atmo");
            MagicRender.screenspace(
                    spr,
                    MagicRender.positioning.CENTER,
                    new Vector2f(0, 0),
                    new Vector2f(0, 0),
                    bgsize,
                    new Vector2f(0, 0),
                    spin,
                    0f, //spin
                    shiftColor(new Color(255, 255, 255, 255), new Color(155, 100, 100, 255), level),
                    false,
                    0f,
                    0,
                    0f,
                    0f,
                    0f,
                    0f,
                    -1,
                    0f,
                    CombatEngineLayers.CLOUD_LAYER
            );
            // im using 'spin' for gradual movement
            // no idea why past me thought to use this var but, it works
            // this gets jacked up massively by level as the cutscene starts to end
            // this way, there shouldnt be any bg ships by the time combat actually starts
            float rawInv = 1f / viewMult;
            // background size
            float bgSize = Math.max(base * rawInv, minCover);
            // THIS is the key value
            float effectiveInv = bgSize / base;
            float mult = effectiveInv;
            spr = Global.getSettings().getSprite(Global.getSettings().getHullSpec("valkyrie").getSpriteName());
            MagicRender.screenspace(
                    spr,
                    MagicRender.positioning.CENTER,
                    new Vector2f(300 * mult, 200 + spin * 30 + 1000 * level * mult),
                    new Vector2f(0, 0),
                    new Vector2f((spr.getWidth() / 4f) * mult, (spr.getHeight() / 4f) * mult),
                    new Vector2f(0, 0),
                    0f,
                    0f, //spin
                    new Color(0.5f, 0.5f, 0.75f, 1f),
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

            spr = Global.getSettings().getSprite(Global.getSettings().getHullSpec("valkyrie").getSpriteName());
            MagicRender.screenspace(
                    spr,
                    MagicRender.positioning.CENTER,
                    new Vector2f(-300 * mult, 200 + spin * 30 + 1000 * level * mult),
                    new Vector2f(0, 0),
                    new Vector2f((spr.getWidth() / 4f) * mult, (spr.getHeight() / 4f) * mult),
                    new Vector2f(0, 0),
                    0f,
                    0f, //spin
                    new Color(0.5f, 0.5f, 0.75f, 1f),
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

            spr = Global.getSettings().getSprite(Global.getSettings().getHullSpec("dram").getSpriteName());
            MagicRender.screenspace(
                    spr,
                    MagicRender.positioning.CENTER,
                    new Vector2f(-200 * mult, -250 + spin * 50 + 1500 * mult * level),
                    new Vector2f(0, 0),
                    new Vector2f((spr.getWidth() / 4f) * mult, (spr.getHeight() / 4f) * mult),
                    new Vector2f(0, 0),
                    0f,
                    0f, //spin
                    new Color(0.5f, 0.5f, 0.75f, 1f),
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
            spr = Global.getSettings().getSprite(Global.getSettings().getHullSpec("phaeton").getSpriteName());
            MagicRender.screenspace(
                    spr,
                    MagicRender.positioning.CENTER,
                    new Vector2f(0 * mult, 100 + spin * 25 + 1500 * level * mult),
                    new Vector2f(0, 0),
                    new Vector2f((spr.getWidth() / 4f) * mult, (spr.getHeight() / 4f) * mult),
                    new Vector2f(0, 0),
                    0f,
                    0f, //spin
                    new Color(0.5f, 0.5f, 0.75f, 1f),
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
            spr = Global.getSettings().getSprite(Global.getSettings().getHullSpec("dram").getSpriteName());
            MagicRender.screenspace(
                    spr,
                    MagicRender.positioning.CENTER,
                    new Vector2f(200 * mult, -250 + spin * 50 + 1000 * level * mult),
                    new Vector2f(0, 0),
                    new Vector2f((spr.getWidth() / 4f) * mult, (spr.getHeight() / 4f) * mult),
                    new Vector2f(0, 0),
                    0f,
                    0f, //spin
                    new Color(0.5f, 0.5f, 0.75f, 1f),
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

            spr = Global.getSettings().getSprite(Global.getSettings().getHullSpec("falcon").getSpriteName());
            MagicRender.screenspace(
                    spr,
                    MagicRender.positioning.CENTER,
                    new Vector2f(-600 * mult, -200 - spin * 55 - 1000 * level * mult),
                    new Vector2f(0, 0),
                    new Vector2f((spr.getWidth() / 4f) * mult, (spr.getHeight() / 4f) * mult),
                    new Vector2f(0, 0),
                    0f,
                    0f, //spin
                    new Color(0.5f, 0.5f, 0.75f, 1f),
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
            spr = Global.getSettings().getSprite(Global.getSettings().getHullSpec("falcon").getSpriteName());
            MagicRender.screenspace(
                    spr,
                    MagicRender.positioning.CENTER,
                    new Vector2f(600 * mult, -200 - spin * 50 - 1000 * level * mult),
                    new Vector2f(0, 0),
                    new Vector2f((spr.getWidth() / 4f) * (mult), (spr.getHeight() / 4f) * mult),
                    new Vector2f(0, 0),
                    0f,
                    0f, //spin
                    new Color(0.5f, 0.5f, 0.75f, 1f),
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
        if (engine.isFleetsInContact()) {
            cutsceneOver = true;
        }
        if (cutsceneOver && hidCutscene == false && cutsceneInterval.intervalElapsed()) {
            engine.getCombatUI().reFanOutShipInfo();
            hidCutscene = true;
        }
        if (!cutsceneInterval.intervalElapsed()) {
            if (!engine.isPaused() && engine.isFleetsInContact() && !hidCutscene) {
                cutsceneInterval.advance(amount);
            }
            float level = cutsceneInterval.getElapsed() / cutsceneInterval.getIntervalDuration();
            MagicRender.screenspace(
                    Global.getSettings().getSprite("misc", "armaa_cutscene"),
                    MagicRender.positioning.LOW_LEFT,
                    new Vector2f(0, 0),
                    new Vector2f(0, 0),
                    new Vector2f(Global.getSettings().getScreenWidth() * 4f, Global.getSettings().getScreenHeight() / 4f - (Global.getSettings().getScreenHeight() / 4) * level),
                    new Vector2f(0, 0),
                    0f,
                    0f, //spin
                    new Color(0f, 0f, 0f, Math.max(0f, 1f - 1f * level)),
                    false,
                    0f,
                    0f,
                    0f,
                    0f,
                    0f,
                    0f,
                    -1,
                    0f,
                    CombatEngineLayers.ABOVE_PARTICLES
            );
            MagicRender.screenspace(
                    Global.getSettings().getSprite("misc", "armaa_cutscene"),
                    MagicRender.positioning.UP_LEFT,
                    new Vector2f(0, Global.getSettings().getScreenHeight()),
                    new Vector2f(0, 0),
                    new Vector2f(Global.getSettings().getScreenWidth() * 4f, -Global.getSettings().getScreenHeight() / 4 + (Global.getSettings().getScreenHeight() / 4) * level),
                    new Vector2f(0, 0),
                    0f,
                    0f, //spin
                    new Color(0f, 0f, 0f, Math.max(0f, 1f - 1f * level)),
                    false,
                    0f,
                    0f,
                    0f,
                    0f,
                    0f,
                    0f,
                    -1,
                    0f,
                    CombatEngineLayers.ABOVE_PARTICLES
            );
        }
        if (bgStage > 0.50f) {
            if (!startedFade) {
                fx.beginFadeOut();
                startedFade = true;
            }
            viewMult = engine.getViewport().getViewMult();
            float rawInv = 1f / viewMult;
            // background size
            // THIS is the key value
            float size = Math.min(1f, (bgStage) - 0.50f);
            SpriteAPI spr = Global.getSettings().getSprite(Global.getSettings().getHullSpec("station_derelict_survey_mothership").getSpriteName());
            MagicRender.screenspace(
                    spr,
                    MagicRender.positioning.CENTER,
                    new Vector2f(500 * rawInv - 500 * size * rawInv, 0),
                    new Vector2f(0, 0),
                    new Vector2f(spr.getWidth() * 2f * size * rawInv, spr.getHeight() * 2f * size * rawInv),
                    new Vector2f(0, 0),
                    -spin * 3,
                    0f,
                    new Color(0.6f * size, 0.6f * size, 0.6f * size, 1f),
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
            float elapsed = attackInterval.getElapsed();
            float maxinterval = attackInterval.getMaxInterval();
            float rate = Math.min(1f, elapsed / maxinterval);
            SpriteAPI enemySpr = Global.getSettings().getSprite(Global.getSettings().getHullSpec("warden").getSpriteName());
            if (bgStage < 0.60f) {
                MagicRender.screenspace(
                        enemySpr,
                        MagicRender.positioning.CENTER,
                        new Vector2f(500 - (500 * size) - (1000 * rate), 0),
                        new Vector2f(0, 0),
                        new Vector2f((spr.getWidth() / 6), (spr.getHeight() / 6)),
                        new Vector2f(0, 0),
                        0 + (45f * size),
                        0f,
                        new Color(0.6f * size, 0.6f * size, 0.6f * size, 1f),
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
        }
        if (!engine.isPaused()) {

            ratio = (float) Math.min(5f, (engine.getElapsedInContactWithEnemy() / 100));

            if (!stationAlive) {
                if (ratioMod >= 0f) {
                    ratioMod = -ratio; // Set `ratioMod` initially when station is no longer alive
                }
                ratio += ratioMod; // Decrease `ratio` by `ratioMod` when `stationAlive` is false
            } else {
                ratio = 0f;       // Reset `ratio` if the station is alive
                ratioMod = 0f;    // Reset `ratioMod` to prevent locking when station is alive
            }
            bgStage = ratio;
            float level = cutsceneInterval.getElapsed() / cutsceneInterval.getIntervalDuration();
            if (hidCutscene) {
                engine.setBackgroundGlowColor(shiftColor(startColor, endColor, ratio));
            } else {
                engine.setBackgroundGlowColor(shiftColor(new Color(0, 0, 0, 0), startColor, level));
            }
            interval3.advance(amount);
            bgInterval.advance(amount);
            spinInterval.advance(amount);
            float mult = engine.getViewport().getViewMult();
            //Global.getCombatEngine().maintainStatusForPlayerShip("AceSystem3", "graphics/ui/icons/icon_repair_refit.png", "mult",""+ ratio,false);
            float minX = engine.getViewport().getLLX(); // Leftmost X-coordinate of the viewport
            float maxX = engine.getViewport().getLLX() + engine.getViewport().getVisibleWidth(); // Rightmost X-coordinate of the viewport
            float minY = engine.getViewport().getLLY(); // Leftmost X-coordinate of the viewport
            float maxY = engine.getViewport().getLLY() + engine.getViewport().getVisibleHeight(); // Rightmost X-coordinate of the viewport
            if(spinInterval.intervalElapsed())
            {
                spin += 0.02f;
            }
            ShipAPI bossEne = null;
            if (engine.getCustomData().containsKey("armaa_atmo_boss")) {
                bossEne = (ShipAPI) engine.getCustomData().get("armaa_atmo_boss");
            }
            if (bossEne != null && (bgStage > 0.60f && battleStr >= 0 && battleStr / 2 > engine.getFleetManager(0).getCurrStrength() || (!bossEne.isAlive() && (bossEne.getCurrentCR() < 0.20f) && !bossEne.isRetreating()))) {
                if (bossEne.getFluxTracker().isOverloaded()) {
                    return;
                }
                battleStr = -999;
                engine.getFleetManager(1).getTaskManager(false).orderFullRetreat();
                for (ShipAPI ship : engine.getAllShips()) {
                    if (ship.getOwner() == 1) {
                        ship.setRetreating(true, true);
                    }
                    engine.getFleetManager(1).getTaskManager(false).orderRetreat(engine.getFleetManager(1).getDeployedFleetMember(ship), true, false);
                }
                if (bossEne != null && bossEne.isAlive()) {
                    if (bossEne.getHullLevel() > 0.50f || bossEne.getCurrentCR() > 0.20f) {
                        engine.getCombatUI().addMessage(1, bossEne, Color.cyan, bossEne.getName(), Color.white, ":", Color.cyan, "More inbound. We've done enough here, pull back for now.");
                        Global.getSector().getMemoryWithoutUpdate().set("$armaa_imeldaSavedPlayer", true);
                    } else {
                        if (bossEne.isAlive()) {
                            engine.getCombatUI().addMessage(1, bossEne, Color.cyan, bossEne.getName(), Color.white, ":", Color.cyan, "These ones aren't half bad. Pull back. We'll relay this development to IK.");
                        }
                        Global.getSector().getMemoryWithoutUpdate().set("$armaa_imeldaSavedPlayerDidSignificantDamage", true);
                    }
                    engine.getFleetManager(0).spawnShipOrWing("brawler_pather_Raider", new Vector2f(-100, -10000), 0f, 10f);
                    engine.getFleetManager(0).spawnShipOrWing("brawler_pather_Raider", new Vector2f(0, -10000), 0f, 10f);
                    engine.getFleetManager(0).spawnShipOrWing("brawler_pather_Raider", new Vector2f(100, -10000), 0f, 10f);
                }
            }
            if ((bgStage >= 1.2f || engine.getFleetManager(1).getCurrStrength() <= 0f) && !spawnedBoss && !stationAlive) {
                // Apparently this can be the case
                //if (Misc.getAICoreOfficerPlugin(ITEM) != null) {
                //	return;
                //}
                engine.setDoNotEndCombat(false);
                //Global.getSoundPlayer().pauseCustomMusic();
                battleStr = engine.getFleetManager(0).getCurrStrength();
                PersonAPI pilot = Misc.getAICoreOfficerPlugin(Commodities.ALPHA_CORE).createPerson(Commodities.ALPHA_CORE, "remnant", new Random());
                String bossStr = "hyperion_Attack";
                String chaffStr = "armaa_morganamp_standard";
                if (engine.getPlayerShip() != null) {
                    engine.addFloatingText(engine.getPlayerShip().getLocation(), "Reinforcements approaching from the north and soutj!", 36f, Color.yellow, engine.getPlayerShip(), 1f, 1f);
                }
                ShipAPI boss = engine.getFleetManager(1).spawnShipOrWing(bossStr, new Vector2f(0, 10000), 270f, 0f);
                engine.getFleetManager(1).spawnShipOrWing("armaa_morgana_wing", new Vector2f(-100, 10000), 270f, 5f);
                engine.getFleetManager(1).spawnShipOrWing("armaa_morgana_wing", new Vector2f(100, 10000), 270f, 5f);
                boss.setCaptain(pilot);
                Global.getSoundPlayer().playUISound("cr_playership_critical", 0.67f, 10f);
                engine.getCombatUI().addMessage(1, boss, Color.red, boss.getName(), Color.white, ":", Color.cyan, "Unknown IFF detected; offworld origin. You've entered restricted space. Power down your weapons and surrender immediately, or face swift destruction. This is your only warning.");
                spawnedBoss = true;
                engine.getCustomData().put("armaa_atmo_boss", boss);
            }
            ShipAPI boss = null;
            if (engine.getCustomData().containsKey("armaa_atmo_boss")) {
                boss = (ShipAPI) engine.getCustomData().get("armaa_atmo_boss");
            }

            if (boss != null && boss.isAlive() && (!bossLine1 || !bossLine2 || !bossLine3 || !firstContact)) {
                if (!firstContact && boss.areAnyEnemiesInRange()) {
                    //Global.getSoundPlayer().playCustomMusic(0,0,"music_armaa_pirate_encounter_hostile",true);
                    engine.getCombatUI().addMessage(1, boss, Color.red, boss.getName(), Color.white, ":", Color.white, "Engaging intercept protocols. Let's see if you spacers have the guts to die for your arrogance.");
                    firstContact = true;
                }
                bossStage = boss.getHullLevel();
                if (bossStage <= 0.25f && !bossLine3) {
                    engine.getCombatUI().addMessage(1, boss, Color.red, boss.getName(), Color.white, ":", Color.white, "Impossible...!");
                    bossLine3 = true;
                    bossLine2 = true;
                    bossLine1 = true;
                } else if (bossStage <= 0.50f && !bossLine2) {
                    engine.getCombatUI().addMessage(1, boss, Color.red, boss.getName(), Color.white, ":", Color.white, "You're not walking away from this. Let's end it.");
                    bossLine2 = true;
                    bossLine1 = true;
                } else if (bossStage <= 0.75f && !bossLine1) {
                    engine.getCombatUI().addMessage(1, boss, Color.red, boss.getName(), Color.white, ":", Color.white, "I'll give you credit-you're lasting longer than expected. But pushing your luck won't change the outcome.");
                    bossLine1 = true;
                }

            }
            if (((!perfMode && interval3.intervalElapsed()) || perfMode && bgInterval.intervalElapsed()) && ratio < 0.55f) {
                for (int i = 0; i < Math.random() * 4 * mult; i++) {
                    float xVel = (float) (MathUtils.getRandomNumberInRange(-100, 100));
                    float cloudSize = (float) (MathUtils.getRandomNumberInRange(1200, 1800) * (Math.random() * 2));
                    MagicRender.battlespace(
                            Global.getSettings().getSprite("misc", "armaa_atmo_cloud"),
                            new Vector2f(MathUtils.getRandomNumberInRange(minX, maxX), MathUtils.getRandomNumberInRange(minY, maxY)),
                            new Vector2f(xVel, -MathUtils.getRandomNumberInRange(600, 1800)),
                            new Vector2f(cloudSize, cloudSize),
                            new Vector2f(50f, 50f),
                            90f,
                            0f,
                            shiftColor(new Color(75, 50, 50, 100), endColor, bgStage),
                            false,
                            0f,
                            0f,
                            0f,
                            0f,
                            0f,
                            0.4f * mult,
                            2f * mult,
                            1f * mult,
                            CombatEngineLayers.BELOW_SHIPS_LAYER
                    );
                }

            } else if (interval3.intervalElapsed() && ratio > 0.60f) {
                for (int i = 0; i < Math.random() * 2 * mult; i++) {
                    float xVel = (float) (MathUtils.getRandomNumberInRange(-250, 250));
                    float cloudSize = (float) (MathUtils.getRandomNumberInRange(1000, 1500) * (Math.random() * 2));
                    MagicRender.battlespace(
                            Global.getSettings().getSprite("misc", "armaa_atmo_cloud"),
                            new Vector2f(MathUtils.getRandomNumberInRange(minX, maxX), engine.getViewport().getCenter().y + engine.getViewport().getVisibleHeight()),
                            new Vector2f(xVel, -MathUtils.getRandomNumberInRange(100, 300) * mult),
                            new Vector2f(MathUtils.getRandomNumberInRange(cloudSize * 0.75f, cloudSize * 1.25f), MathUtils.getRandomNumberInRange(cloudSize * 0.75f, cloudSize * 1.25f)),
                            new Vector2f(50f, 50f),
                            0f,
                            0f,
                            new Color(150, 150, 150, 225),
                            true,
                            0f,
                            0f,
                            0f,
                            0f,
                            0f,
                            0.4f * mult,
                            2f * mult,
                            1f * mult,
                            CombatEngineLayers.BELOW_SHIPS_LAYER
                    );
                }

            }
            if (ratio <= 1f && Math.random() < 0.33) {

                float xVel = (float) (MathUtils.getRandomNumberInRange(-1000, 1000));
                float cloudSize = (float) (MathUtils.getRandomNumberInRange(1000, 2000) * (Math.random() * 2));

                if (Math.random() <= 0.40 && ratio >= 0.20f && ratio < 0.45f) {
                    MagicRender.battlespace(
                            Global.getSettings().getSprite("misc", "armaa_atmo_cloud"),
                            new Vector2f(MathUtils.getRandomNumberInRange(minX, maxX), engine.getViewport().getCenter().y + engine.getViewport().getVisibleHeight()),
                            new Vector2f(xVel, -(float) Math.random() * MathUtils.getRandomNumberInRange(1500, 2000) * mult),
                            new Vector2f(MathUtils.getRandomNumberInRange(cloudSize * 0.75f, cloudSize * 1.25f), MathUtils.getRandomNumberInRange(cloudSize * 0.75f, cloudSize * 1.25f)),
                            new Vector2f(10f, 10f),
                            0f,
                            0f,
                            shiftColor(getRandomTopCloudColor(ratio), new Color(0.4f, 0.3f, 0.3f, 1f - ratio), bgStage),
                            true,
                            0f,
                            0f,
                            0f,
                            0f,
                            0f,
                            (0.2f) * mult,
                            (0.5f + 0.80f - ratio) * mult,
                            (0.5f + 0.80f - ratio) * mult,
                            CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER
                    );
                }
                int random = MathUtils.getRandomNumberInRange(0, 3);
                if (random == 1) {
                    random = 0;
                }

                if (Math.random() <= 0.30 && ratio < 0.40f) {
                    MagicRender.battlespace(
                            Global.getSettings().getSprite("backgrounds", "star2"),
                            new Vector2f(MathUtils.getRandomNumberInRange(minX, maxX), engine.getViewport().getCenter().y + engine.getViewport().getVisibleHeight()),
                            new Vector2f(xVel, -MathUtils.getRandomNumberInRange(100, 1000) * mult),
                            new Vector2f(30, 30),
                            new Vector2f(2f, 2f),
                            0f,
                            0f,
                            shiftColor(new Color(255, 133, 6, 255), new Color(255, 133, 6, 0), bgStage),
                            true,
                            0f,
                            0f,
                            0f,
                            0f,
                            0f,
                            0.5f * mult,
                            2f * mult,
                            1f * mult,
                            CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER
                    );
                }
                if (Math.random() <= 0.50 && ratio > 0.10f && ratio < 0.80f) {
                    MagicRender.battlespace(
                            Global.getSettings().getSprite("misc", "armaa_atmo_cloud2"),
                            new Vector2f(MathUtils.getRandomNumberInRange(minX, maxX), engine.getViewport().getCenter().y + engine.getViewport().getVisibleHeight()),
                            new Vector2f(xVel, -MathUtils.getRandomNumberInRange(800, 1000) * mult),
                            new Vector2f(MathUtils.getRandomNumberInRange(cloudSize * 1.25f, cloudSize * 3f), MathUtils.getRandomNumberInRange(cloudSize * 1.25f, cloudSize * 3f)),
                            new Vector2f(50f, 50f),
                            0f,
                            0f,
                            shiftColor(getRandomBottomCloudColor(ratio), new Color(0.5f, 0.5f, 0.5f, 1f - ratio), bgStage),
                            false,
                            0f,
                            0f,
                            0f,
                            0f,
                            0f,
                            (0.2f) * mult,
                            (0.5f + 0.80f - ratio) * mult,
                            (0.5f + 0.80f - ratio) * mult,
                            CombatEngineLayers.BELOW_SHIPS_LAYER
                    );
                }
            }
        }
        Vector2f vec = (Vector2f) engine.getCustomData().get("armaa_atmoWarningLoc" + 0);

        if (vec != null && !warning && bgStage < 0.60f) {

            float elapsed = attackInterval.getElapsed();
            float maxinterval = attackInterval.getMaxInterval();
            float rate = Math.min(1f, elapsed / maxinterval);
            SpriteAPI enemySpr = Global.getSettings().getSprite(Global.getSettings().getHullSpec("warden").getSpriteName());
            MagicRender.battlespace(
                    enemySpr,
                    vec,
                    new Vector2f(0, 0),
                    new Vector2f(enemySpr.getWidth() * rate, enemySpr.getWidth() * rate),
                    new Vector2f(0, 0),
                    0f,
                    0f,
                    new Color(0.5f * rate, 0.5f * rate, 0.5f * rate, 1f),
                    false,
                    0f,
                    0f,
                    0f,
                    0f,
                    0f,
                    0f,
                    amount,
                    0f,
                    CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER
            );
        }
        if (!engine.isPaused()) {
            interval.advance(amount);
            attackInterval.advance(amount);

            if (attackInterval.intervalElapsed() && bgStage > 0.10f) { // Leftmost X-coordinate of the viewport
                // Leftmost X-coordinate of the viewport
                Vector2f altVector = new Vector2f(MathUtils.getRandomNumberInRange(-10000, 10000), MathUtils.getRandomNumberInRange(-10000, 10000));
                for (int i = 0; i < 1; i++) {
                    if (warning && bgStage < 0.60f) {
                        vec = altVector;
                        MagicRender.battlespace(
                                Global.getSettings().getSprite("ceylon", "armaa_ceylontarget"),
                                vec,
                                new Vector2f(0, 0),
                                new Vector2f(1050, 1050),
                                new Vector2f(-250f, -250f),
                                0f,
                                10f,
                                new Color(255, 75, 0, 150),
                                true,
                                0f,
                                0f,
                                0f,
                                0f,
                                0f,
                                0.2f,
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
                                true,
                                0f,
                                0f,
                                0f,
                                0f,
                                0f,
                                0.2f,
                                1f,
                                0.5f,
                                CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER
                        );
                        warning = false;
                        engine.getCustomData().put("armaa_atmoWarningLoc" + i, vec);
                    } else if (!warning && bgStage < 0.60f) {

                        for (ShipAPI ship : engine.getShips()) {
                            if (ship.getOwner() != 1 || stationAlive) {
                                continue;
                            }
                            if (!engine.getCustomData().containsKey("armaa_atmoWarningLoc" + i)) {
                                continue;
                            }
                            vec = (Vector2f) engine.getCustomData().get("armaa_atmoWarningLoc" + i);
                            engine.getFleetManager(1).spawnShipOrWing("warden_Defense", vec, 270f, 0f);
                            engine.spawnExplosion(vec, new Vector2f(), shiftColor(new Color(0.29f, .1f, .1f, 0.50f), new Color(1f, 1f, 1f, 0.50f), bgStage), 250f, 1f);
                            //engine.spawnProjectile(ship,ship.getAllWeapons().get(0),"armaa_curvyLaser",vec,ship.getFacing(),ship.getVelocity());
                            break;
                        }
                        warning = true;
                    }
                }

            }
            if (bgInterval.intervalElapsed()) {
                for (CombatEntityAPI asteroid : engine.getAsteroids()) {
                    if (ratio > 0.3) {
                        engine.removeEntity(asteroid);
                    }
                }
            }
            for (ShipAPI ship : engine.getShips()) {
                if (stationAlive && ship.getName() != null && ship.getName().equals("Damaged Guardian") && ship.isCapital() && ship.getHullLevel() > 0.50f) {
                    ship.setHitpoints(ship.getHitpoints() * 0.65f);
                    ship.setCurrentCR(0.50f);
                }

                if (stationAlive && ship.getName() != null && ship.getName().equals("Damaged Guardian")) {
                    if (!ship.isAlive() || ship.isHulk() || !engine.isEntityInPlay(ship) || Global.getSector().getMemoryWithoutUpdate().get("$armaa_killedJeniusGuardian") != null) {
                        stationAlive = false;
                        Global.getSector().getMemoryWithoutUpdate().set("$armaa_killedJeniusGuardian", true);
                        Global.getSoundPlayer().playUISound("cr_allied_critical", 0.77f, 10f);
                        fx = new armaa_ReentryEffect();
                        Global.getCombatEngine().addLayeredRenderingPlugin(fx);
                        engine.getCombatUI().addMessage(1, ship, Color.white, "Obstacle eliminated. Proceeding into lower orbit...");
                        if (!ship.isAlive()) {
                            engine.removeEntity(ship);
                        }
                        if (engine.getPlayerShip() != null) {
                            engine.addFloatingText(engine.getPlayerShip().getLocation(), "Reinforcements approaching from below us! South!", 36f, Color.yellow, engine.getPlayerShip(), 1f, 1f);
                        }
                        for (int i = 0; i < 6; i++) {
                            float randomPosX = MathUtils.getRandomNumberInRange(-10000f, 10000f);
                            float randomPosY = MathUtils.getRandomNumberInRange(-10000f, 10000f);
                            ShipAPI s = engine.getFleetManager(1).spawnShipOrWing("warden_Defense", new Vector2f(randomPosX, randomPosY), 270f, 0f);
                            s.setAnimatedLaunch();
                            s = engine.getFleetManager(1).spawnShipOrWing("broadsword_wing", new Vector2f(randomPosX, randomPosY), 270f, 0f);
                            s.setAnimatedLaunch();
                            s = engine.getFleetManager(1).spawnShipOrWing("armaa_morgana_wing", new Vector2f(randomPosX, randomPosY), 270f, 0f);
                            s.setAnimatedLaunch();
                            s = engine.getFleetManager(1).spawnShipOrWing("talon_wing", new Vector2f(randomPosX, randomPosY), 270f, 0f);
                            s.setAnimatedLaunch();

                        }

                    }
                }
                // early out aggressively
                if (!ship.isAlive() || ship.isHulk() || !engine.isEntityInPlay(ship)) {
                    engine.removeEntity(ship);
                    continue;
                }
                if (ship.getStationSlot() != null) {
                    continue;
                }
                float velX = ship.getVelocity().getX();
                float velY = ship.getVelocity().getY();
                float id = 0;
                float id2 = 0;
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
                Vector2f rightDir = Misc.getUnitVectorAtDegreeAngle(ship.getFacing() + 90f); // use -90f if mirrored

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
                    if (ratio > 0.45f) {
                        MagicTrailPlugin.AddTrailMemberSimple(ship, id, Global.getSettings().getSprite("fx", "beam_trail_cel"), leftPos, speed, ship.getFacing() + 180f + 12f, 1f, 10f, new Color(150, 150, 150, 225), 0.8f, 0.1f, 1f, 0.6f, true);
                        MagicTrailPlugin.AddTrailMemberSimple(ship, id + 1, Global.getSettings().getSprite("fx", "beam_trail_cel"), rightPos, speed, ship.getFacing() + 180f - 12f, 1f, 10f, new Color(150, 150, 150, 225), 0.8f, 0.1f, 1f, 0.6f, true);
                    }

                    float facingRad = (float) Math.toRadians(ship.getFacing());
                    float cosF = (float) Math.cos(facingRad);
                    float sinF = (float) Math.sin(facingRad);


                    Vector2f nosePos = new Vector2f(
                            ship.getLocation().x + cosF * r * 0.6f,
                            ship.getLocation().y + sinF * r * 0.6f
                    );


                    float spawnOffset = MathUtils.getRandomNumberInRange(0f, r * 0.4f);
                    Vector2f fireSpawn = new Vector2f(
                            nosePos.x - cosF * spawnOffset,
                            nosePos.y - sinF * spawnOffset
                    );


                    float trailSpeed = MathUtils.getRandomNumberInRange(600, 900);
                    float spread = (float) (Math.random() - 0.5f) * r * 3f;
                    float fireVx = -cosF * trailSpeed + sinF * spread + velX;
                    float fireVy = -sinF * trailSpeed - cosF * spread + velY;


                    float smokeOffset = MathUtils.getRandomNumberInRange(r * 0.3f, r * 1.5f);
                    Vector2f smokeSpawn = new Vector2f(
                            nosePos.x - cosF * smokeOffset,
                            nosePos.y - sinF * smokeOffset
                    );
                    float smokeSpeed = MathUtils.getRandomNumberInRange(200, 500);
                    float smokeVx = -cosF * smokeSpeed + velX;
                    float smokeVy = -sinF * smokeSpeed + velY;


                    if (Math.random() <= 1f - ratio && ratio >= 0.20f && ratio <= 0.60f) {
                        MagicRender.battlespace(
                                Global.getSettings().getSprite("misc", "armaa_atmo_cloud"),
                                fireSpawn,
                                new Vector2f(fireVx, fireVy),
                                new Vector2f(
                                        MathUtils.getRandomNumberInRange(ship.getCollisionRadius() * 2f, ship.getCollisionRadius() * 3f),
                                        MathUtils.getRandomNumberInRange(ship.getCollisionRadius() * 3f, ship.getCollisionRadius() * 4f)
                                ),
                                new Vector2f(ship.getCollisionRadius() * 0.3f, ship.getCollisionRadius() * 0.3f),
                                0f,
                                0f,
                                shiftColor(getBrighterReentryColor(0.3f), new Color(0.5f, 0.5f, 0.5f, 1f - ratio), bgStage + 0.40f),
                                true,
                                0f, 0f, 0f, 0f, 0f,
                                0.05f,
                                Math.max(0.1f, (1f - ratio) - 0.4f),
                                0.1f,
                                CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER
                        );
                    }


                    if (Math.random() <= 1f - ratio-0.15f && ratio >= 0.15f && ratio-0.15f <= 0.70f) {
                        MagicRender.battlespace(
                                Global.getSettings().getSprite("misc", "armaa_atmo_cloud"),
                                smokeSpawn,
                                new Vector2f(smokeVx, smokeVy),
                                new Vector2f(
                                        MathUtils.getRandomNumberInRange(ship.getCollisionRadius(), ship.getCollisionRadius() * 3f),
                                        MathUtils.getRandomNumberInRange(ship.getCollisionRadius() * 2f, ship.getCollisionRadius() * 4f)
                                ),
                                new Vector2f(ship.getCollisionRadius() * 1.5f, ship.getCollisionRadius() * 1.5f),
                                0f,
                                0f,
                                shiftColor(getRandomBottomCloudColor(0.1f), new Color(0.5f, 0.5f, 0.5f, 1f - ratio), bgStage + 0.20f),
                                false,
                                0f, 0f, 0f, 0f, 0f,
                                0.1f,
                                Math.max(0.1f, (1f - ratio) - 0.3f),
                                0.2f,
                                CombatEngineLayers.BELOW_SHIPS_LAYER
                        );
                    }

                    if (ratio >= 0.20f && ratio <= 0.60f && interval.intervalElapsed()) {
                        float minParticleSize = 8f; // never smaller than this regardless of ship size    
                        float particleSpeed = MathUtils.getRandomNumberInRange(800f, 1400f);
                        spread = (float) (Math.random() - 0.5f) * r * 0.4f;

                        // Tight bright core particles — white/yellow, small, fast
                        for (int i = 0; i < 3; i++) {
                            float s = (float) (Math.random() - 0.5f) * r * 0.3f;
                            Global.getCombatEngine().addSmoothParticle(
                                    new Vector2f(nosePos.x + sinF * s, nosePos.y - cosF * s), // slight lateral spread
                                    new Vector2f(
                                            -cosF * (particleSpeed + MathUtils.getRandomNumberInRange(-200f, 200f)) + velX,
                                            -sinF * (particleSpeed + MathUtils.getRandomNumberInRange(-200f, 200f)) + velY
                                    ),
                                    MathUtils.getRandomNumberInRange(
                                            Math.max(minParticleSize, r * 0.05f),
                                            Math.max(minParticleSize * 2f, r * 0.15f)
                                    ),
                                    MathUtils.getRandomNumberInRange(0.6f, 1.0f), // brightness
                                    MathUtils.getRandomNumberInRange(0.2f, 0.5f), // lifetime
                                    shiftColor(new Color(1.0f, 0.8f + (float) Math.random() * 0.2f, 0.3f, 1f), new Color(0.5f, 0.5f, 0.5f, 1f - ratio), bgStage) // white-yellow
                            );
                        }

                        // Outer plasma particles — orange/red, larger, slightly slower
                        for (int i = 0; i < 2; i++) {
                            float s = (float) (Math.random() - 0.5f) * r * 0.8f;
                            Global.getCombatEngine().addSmoothParticle(
                                    new Vector2f(nosePos.x + sinF * s, nosePos.y - cosF * s),
                                    new Vector2f(
                                            -cosF * (particleSpeed * 0.6f + MathUtils.getRandomNumberInRange(-100f, 100f)) + velX,
                                            -sinF * (particleSpeed * 0.6f + MathUtils.getRandomNumberInRange(-100f, 100f)) + velY
                                    ),
                                    MathUtils.getRandomNumberInRange(
                                            Math.max(minParticleSize * 1.5f, r * 0.1f),
                                            Math.max(minParticleSize * 3f, r * 0.25f)
                                    ),
                                    MathUtils.getRandomNumberInRange(0.4f, 0.8f),
                                    MathUtils.getRandomNumberInRange(0.3f, 0.7f),
                                    new Color(1.0f, 0.3f + (float) Math.random() * 0.2f, 0.0f, 1f)
                            );
                        }
                    }
                }

                String key = ship.getId() + "_atmo";
                if (!ship.getMutableStats().getMaxSpeed().getMultMods().containsKey(key)) {
                    float malus = (float) MANUVER_MALUS.get(ship.getHullSize());
                    ship.getMutableStats().getMaxSpeed().modifyMult(key, malus);
                    ship.getMutableStats().getMaxTurnRate().modifyMult(key, malus);
                    ship.getMutableStats().getAcceleration().modifyMult(key, malus);
                    ship.getMutableStats().getTurnAcceleration().modifyMult(key, malus);
                }
                Global.getCombatEngine().maintainStatusForPlayerShip("atmo", "graphics/ui/icons/icon_repair_refit.png", "In Atmoshpere", "Manuverability reduced", true);
            }
        }
    }

    // Method to generate a random color for the top (lighter) cloud layer
    private Color getRandomTopCloudColor(float ratio) {
        Random rand = new Random();
        // Define the range for the top layer colors (bright yellow-orange shades)
        float r = rand.nextFloat() * 0.30f + 0.30f; // Random red value from 0 to 1
        float g = rand.nextFloat() * 0.1f + 0.10f; // Random green value from 0.35 to 1.0 for yellow-orange range
        float b = rand.nextFloat() * 0.1f; // Random blue value from 0 to 0.5 for warmer, less cool tones

        // Return the random color
        return new Color(r, g, b, 0.7f - ratio);
    }

    // Method to generate a random color for the bottom (darker) cloud layer
    private Color getRandomBottomCloudColor(float ratio) {
        Random rand = new Random();
        float r = rand.nextFloat() * 0.25f + 0.1f; // Random red value from 0 to 1
        float g = rand.nextFloat() * 0.05f; // Random green value from 0.35 to 1.0 for yellow-orange range
        float b = rand.nextFloat() * 0.05f; // Random blue value from 0 to 0.5 for warmer, less cool tones

        // Return the random color
        return new Color(r * ratio, g * ratio, b * ratio, 0.9f - ratio);
    }

    // Method to generate a random color for a brighter, more vibrant reentry effect
    private Color getBrighterReentryColor(float ratio) {
        Random rand = new Random();
        // Define the range for fiery red-orange shades
        float r = 0.85f + rand.nextFloat() * 0.15f; // Bright red, close to the upper range
        float g = 0.3f + rand.nextFloat() * 0.3f;  // Moderate green for orange hues
        float b = 0.0f + rand.nextFloat() * 0.1f;  // Minimal blue to keep it warm

        // Return the color with adjusted alpha based on the ratio
        return new Color(r, g, b, 1.0f - ratio);
    }

    public static void playAtmosphericEntryLine(CombatEngineAPI engine, int id) {
        /*
        switch (id) {
            case 0:
                engine.getCombatUI().addMessage(
                        1, Global.getSettings().getColor("yellowTextColor"), "Strike Wing Sigma-3: ", Color.white,
                        "Sigma-3 on approach. Moving ahead to the target bombardment area."
                );
                break;

            case 1:
                engine.getCombatUI().addMessage(
                        1, Global.getSettings().getColor("yellowTextColor"), "Kade: ", Color.white,
                        "Stay on auto-burn. " + Global.getSector().getPlayerPerson().getName().getLast() + " will handle anything headed our way"
                );
                break;

            case 2:
                engine.getCombatUI().addMessage(
                        1, Global.getSettings().getColor("yellowTextColor"), "Strike Wing Sigma-3: ", Color.white,
                        "Copy. Drones are tightening the noose... something big behi-"
                );
                break;

            case 3:
                engine.getCombatUI().addMessage(
                        1, Global.getSettings().getColor("yellowTextColor"), "Strike Wing Sigma-3: ", Color.white,
                        "[SIGNAL LOST]"
                );
                break;

            case 4:
                engine.getCombatUI().addMessage(
                        1, Global.getSettings().getColor("yellowTextColor"), "Kade: ", Color.white,
                        "Other task groups are in contact. Stick to the drop, Captain " + Global.getSector().getPlayerPerson().getName().getLast() + "."
                );
                break;

            default:
                // Fallback line if ID isn't valid
                engine.getCombatUI().addMessage(
                        1, "OPSDIR:", Color.white,
                        "Hold steady."
                );
                break;
            */
        //}
    }

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
    }
}
