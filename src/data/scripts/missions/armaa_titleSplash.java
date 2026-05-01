package data.scripts.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.awt.Color;
import java.util.EnumSet;
import java.util.Random;

public class armaa_titleSplash extends BaseCombatLayeredRenderingPlugin {

    private static final float SLAM_DURATION = 0.40f;
    private static final float GARBLE_RATE = 0.045f;

    // How long after the effect begins before the first orange streak appears
    private static final float ORANGE_START_DELAY = 0.15f;
    // How long each orange streak takes to ramp up to full brightness
    private static final float ORANGE_SLAM_DUR = 0.25f;
    // Stagger delay between each character's streak starting left-to-right
    private static final float ORANGE_PER_CHAR = 0.05f;
    // How long the orange letter holds at 1.45x size before fading out
    private static final float ORANGE_HOLD_DUR = 0.3f;
    // How long the orange letter takes to fade out after the hold phase
    private static final float ORANGE_FADE_DUR = 0.3f;

    private static final float SUBTITLE_RESOLVE_FRAC = 0.80f;
    private static final float HOLD_DURATION = 2.0f;

    // How long each outro orange streak lasts per char
    private static final float OUTRO_STREAK_DUR = 0.20f;
    // Subtitle starts fading this many seconds after streaks finish
    private static final float OUTRO_SUBTITLE_DELAY = 0.08f;
    // How long subtitle takes to expand and fade out
    private static final float OUTRO_SUBTITLE_DUR = 0.35f;
    // How long the bg band and rings take to fade out (starts after streaks complete)
    private static final float OUTRO_BG_DUR = 0.40f;

    private static final float BAND_H_FRAC = 0.12f;
    private static final float BAND_CY_FRAC = 0.50f;
    private static final float BAND_ALPHA = 0.45f;
    private static final float LINE_THICKNESS = 3f;
    private static final float PULSE_DUR = 1f;
    private static final float PULSE_RANGE = 40f;
    private static final float PULSE_INTERVAL = 0.5f;
    private static final float SLAM_SCALE_START = 7.0f;

    private static final float HEADLINE_SIZE = 32f;
    private static final float SUBTITLE_SIZE = 24f;

    private static final Color COL_YELLOW = Global.getSettings().getBrightPlayerColor(); //Global.getSettings().getColor("yellowTextColor");
    private static final Color COL_LINE = new Color(0.90f, 0.08f, 0.00f, 0.90f);
    private static final Color COL_LINE_STATIC = new Color(0.90f, 0.08f, 0.00f, 0.90f);
    private static final String GARBLE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789@#$%&*!?^~";

    private final String headline;
    private final String subtitle;

    private float elapsed = 0f;
    private boolean expired = false;
    private boolean fontLoaded = false;

    private LazyFont headlineFont = null;
    private LazyFont subtitleFont = null;

    private final char[] headlineGarble;
    private final float[] headlineGarbleTimer;
    private final char[] subtitleGarble;
    private final float[] subtitleGarbleTimer;

    private final Random rng = new Random();

    private final float[] ringSpawnTimes = new float[12];
    private int ringCount = 0;
    private float nextPulseTime = 0f;

    private float[] headlineCharWidths = null;
    private float headlineTotalWidth = 0f;

    private int lastSoundChar = -1;
    private float startDelay = 2.0f; // seconds before effect begins
    private float delayElapsed = 0f;
    private boolean delayDone = false;

    public armaa_titleSplash(String headline, String subtitle) {
        this.headline = headline != null ? headline.toUpperCase() : "";
        this.subtitle = subtitle != null ? subtitle.toUpperCase() : "";
        headlineGarble = new char[this.headline.length()];
        headlineGarbleTimer = new float[this.headline.length()];
        subtitleGarble = new char[this.subtitle.length()];
        subtitleGarbleTimer = new float[this.subtitle.length()];
        for (int i = 0; i < headlineGarble.length; i++) {
            headlineGarble[i] = randomGarble();
        }
        for (int i = 0; i < subtitleGarble.length; i++) {
            subtitleGarble[i] = randomGarble();
        }
    }

    @Override
    public boolean isExpired() {
        return expired;
    }

    @Override
    public float getRenderRadius() {
        return Float.MAX_VALUE;
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(CombatEngineLayers.JUST_BELOW_WIDGETS);
    }

    private boolean shouldSkipChar(char c) {
        return c == ' ';
    }

    private float outroStartTime() {
        return orangeTotalDuration() + HOLD_DURATION;
    }

    private float outroDuration() {
        return headline.length() * ORANGE_PER_CHAR
                + OUTRO_STREAK_DUR * 1.5f
                + OUTRO_BG_DUR;
    }

    private float orangeTotalDuration() {
        return ORANGE_START_DELAY + headline.length() * ORANGE_PER_CHAR
                + ORANGE_SLAM_DUR + ORANGE_HOLD_DUR + ORANGE_FADE_DUR;
    }

    @Override
    public void advance(float amount) {
        if (Global.getCombatEngine().isPaused()) {
            return;
        }
        if (!delayDone) {
            delayElapsed += amount;
            if (delayElapsed >= startDelay) {
                delayDone = true;
            }
            return;
        }
        elapsed += amount;
        for (int i = 0; i < headlineGarbleTimer.length; i++) {
            headlineGarbleTimer[i] += amount;
            if (headlineGarbleTimer[i] >= GARBLE_RATE) {
                headlineGarbleTimer[i] = 0f;
                headlineGarble[i] = randomGarble();
            }
        }
        for (int i = 0; i < subtitleGarbleTimer.length; i++) {
            subtitleGarbleTimer[i] += amount;
            if (subtitleGarbleTimer[i] >= GARBLE_RATE) {
                subtitleGarbleTimer[i] = 0f;
                subtitleGarble[i] = randomGarble();
            }
        }
        if (elapsed >= nextPulseTime && ringCount < ringSpawnTimes.length) {
            ringSpawnTimes[ringCount++] = elapsed;
            nextPulseTime = elapsed + PULSE_INTERVAL;
        }
        if (elapsed > outroStartTime() + outroDuration()) {
            expired = true;
            cleanup();
        }

        float orangeElapsedAdv = elapsed - ORANGE_START_DELAY;
        float outroTAdv = Math.max(0f, elapsed - outroStartTime());
        boolean inOutroAdv = outroTAdv > 0f;

        // Sound for intro streaks
        if (orangeElapsedAdv > 0f && !inOutroAdv) {
            for (int i = 0; i < headline.length(); i++) {
                if (shouldSkipChar(headline.charAt(i))) {
                    continue;
                }
                if (i > lastSoundChar && orangeElapsedAdv >= i * ORANGE_PER_CHAR) {
                    lastSoundChar = i;
                    Global.getSoundPlayer().playUISound("ui_hold_fire_on", 0.75f, 0.3f);
                }
            }
        }
        /*
        // Sound for outro streaks
        if (inOutroAdv) {
            for (int i = 0; i < headline.length(); i++) {
                if (shouldSkipChar(headline.charAt(i))) continue;
                if (i > lastSoundCharOutro && outroTAdv >= i * ORANGE_PER_CHAR) {
                    lastSoundCharOutro = i;
                    Global.getSoundPlayer().playUISound("ui_hold_fire_on", 1f, 0.5f);
                }
            }
        }
         */
    }

    private void ensureFonts() {
        if (fontLoaded) {
            return;
        }
        fontLoaded = true;
        try {
            headlineFont = LazyFont.loadFont("graphics/armaa/fonts/orbitron48aa.fnt");
        } catch (Exception e) {
            Global.getLogger(armaa_titleSplash.class).error("font", e);
        }
        try {
            subtitleFont = LazyFont.loadFont("graphics/fonts/orbitron24aa.fnt");
        } catch (Exception e) {
            Global.getLogger(armaa_titleSplash.class).error("font", e);
        }
    }

    private void ensureCharWidths() {
        if (headlineCharWidths != null || headlineFont == null) {
            return;
        }
        headlineCharWidths = new float[headline.length()];
        headlineTotalWidth = 0f;
        for (int i = 0; i < headline.length(); i++) {
            char c = headline.charAt(i);
            LazyFont.DrawableString ds = headlineFont.createText(c == ' ' ? "A" : String.valueOf(c), Color.WHITE, HEADLINE_SIZE);
            headlineCharWidths[i] = c == ' ' ? ds.getWidth() * 0.6f : ds.getWidth();
            headlineTotalWidth += headlineCharWidths[i];
        }
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (layer != CombatEngineLayers.JUST_BELOW_WIDGETS) {
            return;
        }
        if (expired) {
            return;
        }
        if (!delayDone) {
            return;
        }
        ensureFonts();
        ensureCharWidths();

        float sw = Global.getSettings().getScreenWidth();
        float sh = Global.getSettings().getScreenHeight();

        float outroT = Math.max(0f, elapsed - outroStartTime());
        boolean inOutro = outroT > 0f;

        // BG fades only after all outro streaks have completed
        float bgFadeStart = headline.length() * ORANGE_PER_CHAR + OUTRO_STREAK_DUR * 1.5f;
        float bgAlpha = (!inOutro) ? 1f
                : (outroT < bgFadeStart) ? 1f
                        : Math.max(0f, 1f - (outroT - bgFadeStart) / OUTRO_BG_DUR);

        float bandH = sh * BAND_H_FRAC;
        float bandCY = sh * BAND_CY_FRAC;
        float bandBot = bandCY - bandH * 0.5f;
        float bandTop = bandCY + bandH * 0.5f;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, sw, 0, sh, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendEquation(GL14.GL_FUNC_ADD);
        GL11.glDisable(GL11.GL_TEXTURE_2D);

        //  BG band
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(0f, 0f, 0f, BAND_ALPHA * 0.8f * bgAlpha);
        GL11.glVertex2f(0, bandTop);
        GL11.glVertex2f(sw, bandTop);
        GL11.glColor4f(0f, 0f, 0f, BAND_ALPHA * 0.1f * bgAlpha);
        GL11.glVertex2f(sw, bandBot);
        GL11.glVertex2f(0, bandBot);
        GL11.glEnd();

        // Pulse rings 
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        for (int r = 0; r < ringCount; r++) {
            float ringAge = elapsed - ringSpawnTimes[r];
            if (ringAge < 0f || ringAge > PULSE_DUR) {
                continue;
            }
            float t = ringAge / PULSE_DUR;
            float dist = t * PULSE_RANGE;
            float alpha = Math.max(0f, (0.8f - t) * COL_LINE.getAlpha() / 255f * bgAlpha);
            GL11.glColor4f(COL_LINE.getRed() / 255f, COL_LINE.getGreen() / 255f, COL_LINE.getBlue() / 255f, alpha);
            fillRect(0, bandTop + dist, sw, LINE_THICKNESS);
            fillRect(0, bandBot - dist - LINE_THICKNESS, sw, LINE_THICKNESS);
        }

        // ---- Static line between title and subtitle ----
        GL11.glColor4f(COL_LINE_STATIC.getRed() / 255f, COL_LINE_STATIC.getGreen() / 255f, COL_LINE_STATIC.getBlue() / 255f, 0.5f * bgAlpha);
        LazyFont.DrawableString subRef = subtitleFont != null
                ? subtitleFont.createText("A", COL_YELLOW, SUBTITLE_SIZE) : null;
        float subLineY = bandBot + bandH * 0.30f + (subRef != null ? subRef.getHeight() * 0.2f : 8f);
        fillRect(0, subLineY, sw, LINE_THICKNESS);

        if (headlineFont == null || headlineCharWidths == null) {
            popGL();
            return;
        }

        float startX = sw * 0.5f - headlineTotalWidth * 0.5f;
        LazyFont.DrawableString refStr = headlineFont.createText("A", Color.WHITE, HEADLINE_SIZE);
        float charH = refStr.getHeight();
        float baseY = bandCY + charH * 0.35f;

        float orangeElapsed = elapsed - ORANGE_START_DELAY;

        int totalOrangeChars = 0;
        for (int i = 0; i < headline.length(); i++) {
            if (!shouldSkipChar(headline.charAt(i))) {
                totalOrangeChars++;
            }
        }

        boolean[] orangeResolved = new boolean[headline.length()];
        int orangeResolvedCount = 0;
        if (orangeElapsed > 0f) {
            for (int i = 0; i < headline.length(); i++) {
                if (shouldSkipChar(headline.charAt(i))) {
                    continue;
                }
                if (orangeElapsed >= i * ORANGE_PER_CHAR + ORANGE_SLAM_DUR) {
                    orangeResolved[i] = true;
                    orangeResolvedCount++;
                }
            }
        }
        float orangeDoneFrac = totalOrangeChars > 0 ? (float) orangeResolvedCount / totalOrangeChars : 0f;

        // ---- superimposed letter ----
        float cx = startX;
        if (orangeElapsed > 0f && !inOutro) {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            cx = startX;
            for (int i = 0; i < headline.length(); i++) {
                char realChar = headline.charAt(i);
                float charW = headlineCharWidths[i];
                if (!shouldSkipChar(realChar)) {
                    float charLocalTime = orangeElapsed - i * ORANGE_PER_CHAR;
                    if (charLocalTime > 0f) {
                        float totalLetterDur = ORANGE_SLAM_DUR + ORANGE_HOLD_DUR + ORANGE_FADE_DUR * 3f;
                        float letterAlpha;
                        if (charLocalTime >= totalLetterDur) {
                            letterAlpha = 0f;
                        } else if (charLocalTime > ORANGE_SLAM_DUR + ORANGE_HOLD_DUR) {
                            float fadeT = (charLocalTime - ORANGE_SLAM_DUR - ORANGE_HOLD_DUR) / ORANGE_FADE_DUR;
                            letterAlpha = Math.max(0f, 1f - fadeT);
                        } else {
                            letterAlpha = 1f;
                        }
                        letterAlpha *= bgAlpha;
                        if (letterAlpha > 0f) {
                            char displayChar = orangeResolved[i] ? realChar : headlineGarble[i];
                            float startScale = sh / charH;
                            float letterScale;
                            if (charLocalTime < ORANGE_SLAM_DUR) {
                                float shrinkT = easeOut(charLocalTime / ORANGE_SLAM_DUR);
                                letterScale = startScale + (1.45f - startScale) * shrinkT;
                            } else {
                                letterScale = 1.45f;
                            }
                            LazyFont.DrawableString ds = headlineFont.createText(
                                    String.valueOf(displayChar),
                                    new Color(255, 100, 10, (int) (letterAlpha * 255)),
                                    HEADLINE_SIZE);
                            float charMidY = baseY - charH * 0.5f;
                            GL11.glPushMatrix();
                            GL11.glTranslatef(cx + charW * 0.5f, charMidY, 0f);
                            GL11.glScalef(1f, letterScale, 1f);
                            GL11.glTranslatef(-(cx + charW * 0.5f), -charMidY, 0f);
                            ds.draw(cx, baseY);
                            GL11.glPopMatrix();
                        }
                    }
                }
                cx += charW;
            }
        }

        // ---- headline char erase during outro ----
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        cx = startX;
        for (int i = 0; i < headline.length(); i++) {
            char realChar = headline.charAt(i);
            float charW = headlineCharWidths[i];
            if (!shouldSkipChar(realChar)) {
                float slamT = easeOut(Math.min(1f, elapsed / SLAM_DURATION));
                float vScale = SLAM_SCALE_START + (1f - SLAM_SCALE_START) * slamT;

                float charAlpha = 1f;
                if (inOutro) {
                    float charOutroLocal = outroT - i * ORANGE_PER_CHAR;
                    if (charOutroLocal >= OUTRO_STREAK_DUR * 0.7f) {
                        float fadeT = (charOutroLocal - OUTRO_STREAK_DUR * 0.7f) / OUTRO_STREAK_DUR;
                        charAlpha = Math.max(0f, 1f - fadeT);
                    } else if (charOutroLocal > 0f) {
                        charAlpha = 1f;
                    }
                }
                if (charAlpha <= 0f) {
                    cx += charW;
                    continue;
                }

                char displayChar = orangeResolved[i] ? realChar : headlineGarble[i];
                LazyFont.DrawableString ds = headlineFont.createText(
                        String.valueOf(displayChar),
                        new Color(255, 255, 255, (int) (charAlpha * 255)),
                        HEADLINE_SIZE);
                float charMidY = baseY - charH * 0.5f;
                GL11.glPushMatrix();
                GL11.glTranslatef(cx + charW * 0.5f, charMidY, 0f);
                GL11.glScalef(1f, vScale, 1f);
                GL11.glTranslatef(-(cx + charW * 0.5f), -charMidY, 0f);
                ds.draw(cx, baseY);
                GL11.glPopMatrix();
            }
            cx += charW;
        }

        // Orange streaks
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        // Intro streaks
        if (orangeElapsed > 0f && !inOutro) {
            cx = startX;
            for (int i = 0; i < headline.length(); i++) {
                char realChar = headline.charAt(i);
                float charW = headlineCharWidths[i];
                float streakW = charW * 1.2f;
                if (!shouldSkipChar(realChar)) {
                    float charLocalTime = orangeElapsed - i * ORANGE_PER_CHAR;
                    if (charLocalTime > 0f) {
                        float slamEased = easeOut(Math.min(1f, charLocalTime / ORANGE_SLAM_DUR));
                        float streakAlpha;
                        if (charLocalTime < ORANGE_SLAM_DUR) {
                            streakAlpha = slamEased;
                        } else {
                            float fadeT = (charLocalTime - ORANGE_SLAM_DUR) / ORANGE_FADE_DUR;
                            streakAlpha = Math.max(0f, 1f - fadeT);
                        }
                        streakAlpha *= bgAlpha;
                        if (streakAlpha > 0f) {
                            float charMidX = cx + charW * 0.5f;
                            float streakBot = bandCY - sh * 0.5f;
                            GL11.glColor4f(1f, 0.35f, 0.05f, streakAlpha * 0.8f);
                            fillRect(charMidX - streakW * 0.5f, streakBot, streakW, sh);
                        }
                    }
                }
                cx += charW;
            }
        }

        // subtitlee
        if (subtitleFont != null && subtitle.length() > 0) {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            boolean subResolved = orangeDoneFrac >= SUBTITLE_RESOLVE_FRAC;

            float subAlpha;
            float subScale = 1f;
            float subEraseFrac = 0f;
            if (inOutro) {
                float streaksSweepDone = headline.length() * ORANGE_PER_CHAR + OUTRO_STREAK_DUR;
                float subOutroT = Math.max(0f, outroT - streaksSweepDone + OUTRO_SUBTITLE_DELAY);
                float subProgress = Math.min(1f, subOutroT / OUTRO_SUBTITLE_DUR);
                subAlpha = Math.max(0f, 1f - subProgress);
                subScale = 1f + 0.4f * easeIn(subProgress);
                subEraseFrac = subProgress;
            } else {
                subAlpha = bgAlpha;
            }

            if (subAlpha > 0f || subEraseFrac < 1f) {
                float subTotalW = 0f;
                for (int i = 0; i < subtitle.length(); i++) {
                    char c = subtitle.charAt(i);
                    LazyFont.DrawableString ds = subtitleFont.createText(c == ' ' ? "A" : String.valueOf(c), COL_YELLOW, SUBTITLE_SIZE);
                    subTotalW += c == ' ' ? ds.getWidth() * 0.6f : ds.getWidth();
                }
                float subStartX = sw * 0.5f - subTotalW * 0.5f;
                float subY = bandBot + bandH * 0.30f;
                float subCX = subStartX;
                int charsErased = (int) (subEraseFrac * subtitle.length());

                for (int i = 0; i < subtitle.length(); i++) {
                    char realChar = subtitle.charAt(i);
                    char displayChar = subResolved ? realChar : subtitleGarble[i];
                    LazyFont.DrawableString ds = subtitleFont.createText(
                            realChar == ' ' ? "A" : String.valueOf(displayChar),
                            new Color(COL_YELLOW.getRed(), COL_YELLOW.getGreen(), COL_YELLOW.getBlue(),
                                    (int) (COL_YELLOW.getAlpha() * (i < charsErased ? 0f : subAlpha))),
                            SUBTITLE_SIZE);
                    float w = realChar == ' ' ? ds.getWidth() * 0.6f : ds.getWidth();
                    if (realChar != ' ' && i >= charsErased) {
                        if (inOutro && subScale != 1f) {
                            float charMidX = subCX + w * 0.5f;
                            float charMidY2 = subY - (subRef != null ? subRef.getHeight() * 0.5f : 8f);
                            GL11.glPushMatrix();
                            GL11.glTranslatef(charMidX, charMidY2, 0f);
                            GL11.glScalef(subScale, subScale, 1f);
                            GL11.glTranslatef(-charMidX, -charMidY2, 0f);
                            ds.draw(subCX, subY);
                            GL11.glPopMatrix();
                        } else {
                            ds.draw(subCX, subY);
                        }
                    }
                    subCX += w;
                }
            }
        }

        popGL();
    }

    private void popGL() {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    private void fillRect(float x, float y, float w, float h) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + w, y);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x, y + h);
        GL11.glEnd();
    }

    private char randomGarble() {
        return GARBLE_CHARS.charAt(rng.nextInt(GARBLE_CHARS.length()));
    }

    private static float easeOut(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    private static float easeIn(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * t;
    }
}
