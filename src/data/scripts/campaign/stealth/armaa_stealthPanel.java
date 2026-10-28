package data.scripts.campaign.stealth;

import java.awt.Color;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import data.campaign.rulecmd.armaa_starfallPeopleCMD;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.Misc;

import data.scripts.campaign.stealth.armaa_stealthLevel.Pickup;
import data.scripts.campaign.stealth.armaa_stealthSim.Guard;
import data.scripts.campaign.stealth.armaa_stealthSim.GuardState;
import data.scripts.campaign.stealth.armaa_stealthSim.Rect;
import data.scripts.campaign.stealth.armaa_stealthSim.Ripple;
import data.scripts.campaign.stealth.armaa_stealthSim.Shot;
import data.scripts.campaign.stealth.armaa_stealthSim.Status;

/**
 * Draws and drives armaa_stealthSim inside the interaction dialog's visual
 * panel. All rules live in the sim; this class only turns keys into movement,
 * draws the state, and reports the outcome back to the rules.
 *
 * Movement: WASD or the arrow keys. Hold Shift to sneak. Press E to throw a
 * distraction at the cursor (a few per run); a faint marker shows where it
 * would land. Once the sidearm is picked up, left click (or F) fires at the
 * cursor. A shot kills the first guard in its path and is heard a long way off.
 *
 * The level is bigger than the panel. The view follows the player
 * (armaa_stealthCamera) and everything is clipped to the panel. World
 * drawing is offset by the camera; screen effects (sweep, flash, scanlines,
 * counters) are not.
 *
 * The look is a deliberate old-school radar screen: a green field with a faint
 * grid, a slow sweep line, glowing outlined walls, vision cones that fade with
 * distance, rings of sound that expand and fade, and scanlines over the top.
 * Its colours are fixed here on purpose rather than pulled from the faction
 * palette.
 *
 * Outcome, written to global memory when the run ends:
 *   $armaa_sfo_stealthResult  clean / seen / caught
 *   $armaa_sfo_aloneHeat      +1 for seen, +3 for caught (the alone escape's
 *                             existing tally, read by the exit screen)
 * then the dialog's options are replaced by one Continue whose id is
 * armaa_sfo_boardAloneStealthDone. Kills have no effect on the outcome.
 */
public class armaa_stealthPanel extends BaseCustomUIPanelPlugin {

    public static final String DONE_OPTION = "armaa_sfo_boardAloneStealthDone";
    public static final String RESULT_KEY = "$armaa_sfo_stealthResult";
    /** Which of IRON KING's hints have been shown (comma separated ids), so a retry does not repeat them. */
    public static final String HINTS_SEEN_KEY = "$armaa_sfo_ikHintsSeen";
    public static final String HEAT_KEY = "$armaa_sfo_aloneHeat";
    /** Added to the heat the quest passes in, because the section played too easily at the quest's own values. */
    public static final int DIFFICULTY_BONUS = 1;

    // the interface colours: the combat interface's green, built into a theme by applyTheme (these are the fallback if the setting cannot be read)
    private static final Color FIELD_GREEN = new Color(0, 32, 20);
    private static final Color GRID_GREEN = new Color(90, 170, 80);
    private static final Color LINE_GREEN = new Color(170, 255, 120);
    private static final Color RING_GREEN = new Color(200, 255, 170);
    private static Color FIELD = FIELD_GREEN;
    private static Color GRID = GRID_GREEN;
    private static Color LINE = LINE_GREEN;
    private static final Color CONE = new Color(0, 200, 170);
    private static final Color ALERT = new Color(255, 80, 40);
    private static final Color SEARCH = new Color(255, 190, 50);
    private static final Color GUARD = new Color(255, 0, 0);
    private static final Color DEAD = new Color(110, 130, 110);
    private static final Color PLAYER = Color.white;
    private static Color RING_PLAYER = RING_GREEN;
    private static final Color RING_GUARD = new Color(255, 120, 90);
    private static final Color RING_THROWN = new Color(255, 235, 160);
    private static final Color RING_SHOT = new Color(255, 190, 120);
    private static final Color TRACER = new Color(255, 250, 220);

    /** Seconds for the sweep line to cross the view. */
    private static final float SWEEP_PERIOD = 7.2f;

    private final InteractionDialogAPI dialog;
    private armaa_stealthSim sim;
    private final int difficulty;
    private final armaa_stealthCamera cam = new armaa_stealthCamera();
    private final Set<Integer> held = new HashSet<Integer>();

    private PositionAPI pos;
    private boolean reported;
    private float clock;
    /** Cursor in view units (relative to the panel's corner), and whether it is over the panel. */
    private float mouseX, mouseY;
    private final armaa_stealthHologram holo = new armaa_stealthHologram();
    /** How much smaller than its design size the window is drawn, to fit a screen with the interface scaled up. */
    private final float fit = fit();
    /** Line widths are physical pixels: this makes them follow the interface scale and the fit. */
    private static float lineScale = 1f;

    /** The share of the design size the window gets on this screen (see armaa_stealthSim.fitFor). */
    public static float fit() {
        return armaa_stealthSim.fitFor(Global.getSettings().getScreenWidth(), Global.getSettings().getScreenHeight());
    }
    private boolean mouseIn;
    /** Which way the player last moved, for throwing or firing with no cursor over the panel. */
    private float lastDirX = 1f, lastDirY = 0f;
    private float frameDt = 0.016f;
    private static final float TOAST_LIFE = 3.4f;
    /** The wedges that flash over a swing (the lunge and the dust carry it now); the guard's building red wedge stays as the warning. */
    private static final boolean SHOW_SWING_ARC = false;
    private static final Color DUST = new Color(176, 158, 130);
    private static final Color SPARK_BLOCK = new Color(255, 225, 120);
    private static final Color HOLO_BEAM = new Color(127, 216, 255);
    private final java.util.List<Object[]> toastLines = new java.util.ArrayList<Object[]>();

    private final armaa_stealthSounds sounds = new armaa_stealthSounds();

    // ---- going down: how long a guard or prisoner has been down (panel time), so that he falls over a moment instead of changing at once
    private final java.util.Map<Object, Float> downSince = new java.util.IdentityHashMap<Object, Float>();
    /** A knocked-out guard takes this long to drop to the sleeping look, a killed one this long to turn grey; seconds. */
    private static final float FALL_TIME = 0.45f;
    private static final float KILL_TIME = 0.55f;
    /** How far a body slides the way the blow went. */
    private static final float DOWN_SLIDE = 12f;

    private float downAge(Object o) {
        Float t = downSince.get(o);
        return t == null ? 99f : clock - t;
    }

    private void trackDowns() {
        for (Guard g : sim.guards) {
            if (g.state == GuardState.DEAD) {
                if (!downSince.containsKey(g)) {
                    downSince.put(g, clock);
                }
            } else {
                downSince.remove(g);
            }
        }
        for (armaa_stealthSim.Ally al : sim.allies) {
            if (al.dead) {
                if (!downSince.containsKey(al)) {
                    downSince.put(al, clock);
                }
            } else {
                downSince.remove(al);
            }
        }
    }

    // ---- the way from one stage to the next: the player walks out of sight, the picture breaks into blocks and goes black, a title card, and it
    // comes back the same way while the player can not yet move
    private int trans;                                          // 0 none, 1 walking out, 2 the title card, 3 coming back
    private float transT;
    private float walkDx;
    private float walkDy;
    private String[] transTitle = {"", ""};
    private static final float TRANS_OUT = 1.1f;
    private static final float TRANS_CARD = 2.8f;
    private static final float TRANS_IN = 1.2f;
    /** The player gets control back this long after the new stage starts to show. */
    private static final float TRANS_LOCK = 0.7f;
    private static final String GARBLE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789@#$%&*!?^~";
    private final java.util.Map<Object, float[]> weaponAim = new java.util.HashMap<Object, float[]>();
    /** Screen shake, in pixels, from the sim's 0..1 amplitude. */
    private static final float SHAKE_PIXELS = 5f;
    /** How far a blow throws the view along itself, at full power. */
    private static final float KICK_PIXELS = 6f;
    private float shakeX, shakeY;

    // who is who: faction colours as rims, portraits for the people we know
    private static final float PORTRAIT_RADIUS = 15f;
    /** Fraction of the portrait shown across the circle's radius, and how far above its centre the face sits. */
    private static final float FACE_SPAN = 0.36f;
    private static final float FACE_LIFT = 0.08f;
    /** -1 if portraits come out upside down. */
    private static final float FLIP = 1f;
    /**
     * The settings.json colour the stealth interface is built from: the friendly colour of the combat interface (iconFriendColor, a pure green; the
     * text version, textFriendColor, is a yellower green and is what Misc.getPositiveHighlightColor gives).
     */
    private static final String COMBAT_UI_COLOR = "iconFriendColor";
    private Color playerRim = Color.white;
    private Color guardRim = SEARCH;
    private SpriteAPI playerFace;
    private SpriteAPI rolandFace;
    private final java.util.Map<String, SpriteAPI> allyFaces = new java.util.HashMap<String, SpriteAPI>();
    private org.lazywizard.lazylib.ui.LazyFont speechFont;
    /** What an enemy says is in the game's enemy text colour; Roland and the rest keep the plain colour. */
    private Color enemyText = new Color(255, 100, 0);
    private static final Color PLAIN_TEXT = new Color(235, 245, 235);
    /** Size of the speech text in pixels; the box and wrapping follow it. */
    private static final float SPEECH_FONT_SIZE = 18f;

    public armaa_stealthPanel(InteractionDialogAPI dialog, int difficulty) {
        this(dialog, difficulty, "cellblock");
    }

    /** Roland is in the holding block (the stage after the first cell and its vent), so it is dropped there too if he is not along. */
    private boolean withRoland = true;

    public armaa_stealthPanel(InteractionDialogAPI dialog, int difficulty, boolean withRoland) {
        this(dialog, difficulty, "startcell");
        this.withRoland = withRoland;
        if (!withRoland) {
            sim.dropRoland();
        }
    }

    /** Who is in the cages of the hub: "named", "bodyguards", "marines" (count of them) or "none". */
    private String occupants = "named";
    private int occupantCount;

    public armaa_stealthPanel(InteractionDialogAPI dialog, int difficulty, boolean withRoland, String occupants, int occupantCount) {
        this(dialog, difficulty, withRoland);
        this.occupants = occupants;
        this.occupantCount = occupantCount;
    }

    public armaa_stealthPanel(InteractionDialogAPI dialog, int difficulty, String levelName) {
        this.dialog = dialog;
        this.difficulty = difficulty + DIFFICULTY_BONUS;
        Global.getLogger(armaa_stealthPanel.class).info("Stealth section started at difficulty " + this.difficulty
                + " (heat " + difficulty + " + bonus " + DIFFICULTY_BONUS + ")");
        this.sim = new armaa_stealthSim(armaa_stealthLevels.byName(levelName), this.difficulty);
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        this.sim.hints.markSeen(mem.contains(HINTS_SEEN_KEY) ? mem.getString(HINTS_SEEN_KEY) : null);
        this.cam.snap(sim);
        sim.reflexSlowMo = true;
        loadLooks();
    }

    /** Faction colours and portraits. Anything that cannot be found just leaves the plain discs. */
    private void loadLooks() {
        try {
            // the combat interface's friendly green, read from the game's own settings rather than written in here; everything that follows the
            // interface colour (the field, grid, lines, the steel tint of the armoured marines, the player's rim) is built from it
            Color base = Global.getSettings().getColor(COMBAT_UI_COLOR);
            playerRim = bright(base);
            applyTheme(base);
        } catch (Throwable t) {
            applyTheme(null);
        }
        try {
            guardRim = bright(Global.getSector().getFaction("luddic_path").getBaseUIColor());
        } catch (Throwable t) {
            // keep the default
        }
        try {
            enemyText = Global.getSettings().getColor("textEnemyColor");
        } catch (Throwable t) {
            // keep the default orange
        }
        try {
            speechFont = org.lazywizard.lazylib.ui.LazyFont.loadFont("graphics/fonts/victor14.fnt");
        } catch (Throwable t) {
            try {
                speechFont = org.lazywizard.lazylib.ui.LazyFont.loadFont("graphics/fonts/insignia15LTaa.fnt");
            } catch (Throwable t2) {
                speechFont = null;                              // no font: the words still go to the dialog log
            }
        }
        try {
            playerFace = face(Global.getSector().getPlayerPerson().getPortraitSprite());
        } catch (Throwable t) {
            playerFace = null;
        }
        try {
            rolandFace = face(Global.getSector().getImportantPeople().getPerson("armaa_sfo_roland").getPortraitSprite());
        } catch (Throwable t) {
            rolandFace = null;
        }
        bindAllies();
    }

    /** Looks each prisoner up in ImportantPeople by their id: their portrait for the radar, and their name for the text. */
    private void bindAllies() {
        for (armaa_stealthSim.Ally a : sim.allies) {
            try {
                PersonAPI p = Global.getSector().getImportantPeople().getPerson(a.id);
                if (p == null) {
                    armaa_starfallPeopleCMD.ensurePeople();          // registers them if the quest has not yet
                    p = Global.getSector().getImportantPeople().getPerson(a.id);
                }
                if (p != null) {
                    a.name = p.getName().getLast();
                    allyFaces.put(a.id, face(p.getPortraitSprite()));
                }
            } catch (Throwable t) {
                // no portrait: a plain disc with their initial
            }
        }
    }

    /**
     * Tints the interface from the player faction's colour: the field is a very dark version,
     * the lines and grid lighter ones. Reds, oranges and yellows (and greys, which have no hue)
     * would be lost against the amber and red of the guards' warnings, so those fall back to the
     * radar green. A null colour is the green too.
     */
    private static void applyTheme(Color base) {
        armaa_stealthTheme t = armaa_stealthTheme.of(base);
        if (t != null) {
            FIELD = t.field;
            GRID = t.grid;
            LINE = t.line;
            RING_PLAYER = t.ring;
            return;
        }
        FIELD = FIELD_GREEN;
        GRID = GRID_GREEN;
        LINE = LINE_GREEN;
        RING_PLAYER = RING_GREEN;
    }

    private static SpriteAPI face(String path) {
        return path == null ? null : Global.getSettings().getSprite(path);
    }

    /** Dark faction colours vanish into the field, so lift them to a minimum brightness. */
    private static Color bright(Color c) {
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        hsb[2] = Math.max(hsb[2], 0.8f);
        hsb[1] = Math.min(hsb[1], 0.9f);
        return Color.getHSBColor(hsb[0], hsb[1], hsb[2]);
    }

    @Override
    public void positionChanged(PositionAPI position) {
        this.pos = position;
    }

    @Override
    public void advance(float amount) {
        if (pos == null) {
            return;
        }
        clock += amount;
        frameDt = Math.min(amount, 0.05f);
        sounds.listener(sim);
        if (trans == 1 || trans == 2) {
            advanceTransition(amount);
            return;
        }
        if (trans == 3) {
            transT += frameDt;
            if (transT >= TRANS_IN) {
                trans = 0;
            }
        }
        if (sim.status != Status.RUNNING) {
            if (sim.nextStage != null && sim.status != Status.CAUGHT) {
                beginTransition();
                return;
            }
            report();
            return;
        }
        // a long frame (alt-tab, load hitch) must not teleport anybody
        float dt = Math.min(amount, 0.05f);
        float mx = 0f;
        float my = 0f;
        if (held.contains(Keyboard.KEY_A) || held.contains(Keyboard.KEY_LEFT)) {
            mx -= 1f;
        }
        if (held.contains(Keyboard.KEY_D) || held.contains(Keyboard.KEY_RIGHT)) {
            mx += 1f;
        }
        if (held.contains(Keyboard.KEY_S) || held.contains(Keyboard.KEY_DOWN)) {
            my -= 1f;
        }
        if (held.contains(Keyboard.KEY_W) || held.contains(Keyboard.KEY_UP)) {
            my += 1f;
        }
        boolean sneak = held.contains(Keyboard.KEY_LSHIFT) || held.contains(Keyboard.KEY_RSHIFT);
        if (trans == 3 && transT < TRANS_LOCK) {
            mx = 0f;
            my = 0f;                                            // still coming into the new place
        }
        if (mx != 0f || my != 0f) {
            lastDirX = mx;
            lastDirY = my;
        }
        if (sim.hitStop > 0f) {
            sim.hitStop = Math.max(0f, sim.hitStop - dt);      // a blow landed: the picture holds for a beat
        } else {
            sim.step(dt, mx, my, sneak);
        }
        sounds.frame(sim);
        for (String n : sim.drainNotices()) {
            Global.getLogger(armaa_stealthPanel.class).info("stealth: " + n);
        }
        trackDowns();
        for (String t : sim.drainToasts()) {
            toastLines.add(new Object[]{t, 0f});
            while (toastLines.size() > 3) {
                toastLines.remove(0);
            }
        }
        for (Object[] tl : toastLines) {
            tl[1] = (Float) tl[1] + dt;
        }
        toastLines.removeIf(tl -> (Float) tl[1] > TOAST_LIFE);
        cam.follow(sim, dt);
        shakeX = (float) (Math.random() - 0.5) * 2f * sim.shake * SHAKE_PIXELS + sim.kickX * KICK_PIXELS;
        shakeY = (float) (Math.random() - 0.5) * 2f * sim.shake * SHAKE_PIXELS + sim.kickY * KICK_PIXELS;
        for (String line : sim.drainDialog()) {
            dialog.getTextPanel().addPara(line, Misc.getGrayColor());
        }
    }

    /** The player has reached the way out: they keep walking the way they were going, out of the picture. */
    private void beginTransition() {
        trans = 1;
        transT = 0f;
        Rect r = sim.exit;
        if (sim.exit2 != null && sim.exit2.overlapsCircle(sim.px, sim.py, armaa_stealthSim.PLAYER_RADIUS)) {
            r = sim.exit2;
        }
        float dx = r.x + r.w / 2f - sim.px;
        float dy = r.y + r.h / 2f - sim.py;
        float len = (float) Math.hypot(dx, dy);
        if (len < 3f) {
            dx = lastDirX;
            dy = lastDirY;
            len = (float) Math.hypot(dx, dy);
        }
        walkDx = len > 0.01f ? dx / len : 0f;
        walkDy = len > 0.01f ? dy / len : 1f;
        transTitle = armaa_stealthLevels.titleFor(sim.nextStage);
        sounds.play(sim, "transOut", Float.NaN, 0f);
    }

    private void advanceTransition(float amount) {
        float dt = Math.min(amount, 0.05f);
        transT += dt;
        if (trans == 1) {
            sim.px += walkDx * armaa_stealthSim.WALK_SPEED * 0.7f * dt;
            sim.py += walkDy * armaa_stealthSim.WALK_SPEED * 0.7f * dt;
            if (transT >= TRANS_OUT) {
                armaa_stealthSim next = new armaa_stealthSim(armaa_stealthLevels.byName(sim.nextStage, occupants, occupantCount), difficulty);
                next.carryFrom(sim);
                if (!withRoland) {
                    next.dropRoland();
                }
                sim = next;
                downSince.clear();
                bindAllies();
                cam.snap(sim);
                trans = 2;
                transT = 0f;
            }
        } else if (transT >= TRANS_CARD) {
            trans = 3;
            transT = 0f;
            sounds.play(sim, "transIn", Float.NaN, 0f);
        }
    }

    /** The cursor as a point in the level. */
    private float aimX() {
        return mouseIn ? mouseX + cam.x - shakeX : sim.px + lastDirX * 130f;
    }

    private float aimY() {
        return mouseIn ? mouseY + cam.y - shakeY : sim.py + lastDirY * 130f;
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        for (InputEventAPI e : events) {
            if (e.isConsumed()) {
                continue;
            }
            if (e.isMouseMoveEvent()) {
                if (pos != null && pos.containsEvent(e)) {
                    mouseIn = true;
                    mouseX = (e.getX() - pos.getX()) / fit;
                    mouseY = (e.getY() - pos.getY()) / fit;
                } else {
                    mouseIn = false;
                }
                continue;                                  // never consume mouse moves
            }
            if (trans == 1 || trans == 2 || (trans == 3 && transT < TRANS_LOCK)) {
                // no control while walking out, on the card and for a moment after: keys are still tracked, and Space or Enter skips the card
                if (e.isKeyDownEvent() || e.isKeyUpEvent()) {
                    int code = e.getEventValue();
                    if (isControlKey(code)) {
                        if (e.isKeyDownEvent()) {
                            held.add(code);
                        } else {
                            held.remove(code);
                        }
                        e.consume();
                    } else if (trans == 2 && e.isKeyDownEvent() && !e.isRepeat() && (code == Keyboard.KEY_SPACE || code == Keyboard.KEY_RETURN) && transT > 0.9f) {
                        transT = Math.max(transT, TRANS_CARD - 0.3f);
                        e.consume();
                    } else if (code == Keyboard.KEY_V || code == Keyboard.KEY_TAB || code == Keyboard.KEY_G || code == Keyboard.KEY_Q || code == Keyboard.KEY_SPACE
                            || code == Keyboard.KEY_E || code == Keyboard.KEY_F || (code >= Keyboard.KEY_1 && code <= Keyboard.KEY_4)) {
                        e.consume();
                    }
                } else if ((e.isLMBDownEvent() || e.isRMBDownEvent() || e.isMouseScrollEvent()) && pos != null && pos.containsEvent(e)) {
                    e.consume();
                }
                continue;
            }
            if (e.isRMBDownEvent() && pos != null && pos.containsEvent(e)) {
                sim.playerMelee(aimX(), aimY());
                e.consume();
                continue;
            }
            if (e.isKeyDownEvent() && e.getEventValue() == Keyboard.KEY_V && !e.isRepeat()) {
                sim.playerMelee(aimX(), aimY());
                e.consume();
                continue;
            }
            if (e.isMouseScrollEvent() && pos != null && pos.containsEvent(e)) {
                sim.cycleWeapon();
                e.consume();
                continue;
            }
            if (e.isKeyDownEvent() && e.getEventValue() == Keyboard.KEY_TAB && !e.isRepeat()) {
                sim.cycleWeapon();
                e.consume();
                continue;
            }
            if (e.isKeyDownEvent() && e.getEventValue() >= Keyboard.KEY_1 && e.getEventValue() <= Keyboard.KEY_4 && !e.isRepeat()) {
                sim.selectSlot(e.getEventValue() - Keyboard.KEY_1);
                e.consume();
                continue;
            }
            if (e.isKeyDownEvent() && e.getEventValue() == Keyboard.KEY_G && !e.isRepeat()) {
                sim.dropHeld();
                e.consume();
                continue;
            }
            if (e.isLMBDownEvent() && pos != null && pos.containsEvent(e)) {
                sim.fire(aimX(), aimY());
                e.consume();
                continue;
            }
            if (e.isKeyDownEvent() && e.getEventValue() == Keyboard.KEY_Q && !e.isRepeat()) {
                sim.releaseGrab();
                e.consume();
                continue;
            }
            if (e.isKeyDownEvent() && e.getEventValue() == Keyboard.KEY_SPACE && !e.isRepeat()) {
                sim.questioning = true;
                sim.interact();
                e.consume();
                continue;
            }
            if (e.isKeyUpEvent() && e.getEventValue() == Keyboard.KEY_SPACE) {
                sim.questioning = false;
                e.consume();
                continue;
            }
            if (e.isKeyDownEvent() && e.getEventValue() == Keyboard.KEY_E && !e.isRepeat()) {
                sim.throwDistraction(aimX(), aimY());
                e.consume();
                continue;
            }
            if (e.isKeyDownEvent() && e.getEventValue() == Keyboard.KEY_F && !e.isRepeat()) {
                sim.fire(aimX(), aimY());
                e.consume();
                continue;
            }
            if (e.isKeyDownEvent()) {
                int code = e.getEventValue();
                if (isControlKey(code)) {
                    held.add(code);
                    e.consume();
                }
            } else if (e.isKeyUpEvent()) {
                int code = e.getEventValue();
                if (isControlKey(code)) {
                    held.remove(code);
                    e.consume();
                }
            }
        }
    }

    private static boolean isControlKey(int code) {
        return code == Keyboard.KEY_W || code == Keyboard.KEY_A || code == Keyboard.KEY_S
                || code == Keyboard.KEY_D || code == Keyboard.KEY_UP || code == Keyboard.KEY_DOWN
                || code == Keyboard.KEY_LEFT || code == Keyboard.KEY_RIGHT
                || code == Keyboard.KEY_LSHIFT || code == Keyboard.KEY_RSHIFT;
    }

    /** Writes the outcome and hands control back to the rules, once. */
    private void report() {
        if (reported) {
            return;
        }
        reported = true;
        MemoryAPI m = Global.getSector().getMemoryWithoutUpdate();
        String result;
        int heat = m.contains(HEAT_KEY) ? m.getInt(HEAT_KEY) : 0;
        switch (sim.status) {
            case CLEAN:
                result = "clean";
                break;
            case SEEN:
                result = "seen";
                heat += 1;
                break;
            default:
                result = "caught";
                heat += 3;
                break;
        }
        m.set(RESULT_KEY, result);
        m.set(HINTS_SEEN_KEY, sim.hints.seenString());
        m.set("$armaa_sfo_rolandAbandoned", sim.abandonedRoland);
        m.set("$armaa_sfo_stealthAlliesFreed", sim.alliesFreed());
        m.set("$armaa_sfo_stealthAlliesAlive", sim.alliesAlive());
        m.set(HEAT_KEY, heat);
        dialog.getOptionPanel().clearOptions();
        dialog.getOptionPanel().addOption("Continue", DONE_OPTION);
    }

    // ------------------------------------------------------------------
    // drawing
    // ------------------------------------------------------------------
    /** Clips everything that follows to the panel. Scissor is in physical pixels, hence the scale. */
    private void clipOn() {
        float s = Global.getSettings().getScreenScaleMult();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) (pos.getX() * s), (int) (pos.getY() * s),
                (int) (armaa_stealthSim.VIEW_W * fit * s) + 1, (int) (armaa_stealthSim.VIEW_H * fit * s) + 1);
        lineScale = s * fit;
        GL11.glPushMatrix();                                    // drawn at design size, shrunk about the panel's corner when it has to fit
        GL11.glTranslatef(pos.getX(), pos.getY(), 0f);
        GL11.glScalef(fit, fit, 1f);
        GL11.glTranslatef(-pos.getX(), -pos.getY(), 0f);
    }

    private void clipOff() {
        GL11.glPopMatrix();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    /** Where the sweep line is, in view units from the panel's left edge. */
    private float sweepX() {
        return (clock % SWEEP_PERIOD) / SWEEP_PERIOD * armaa_stealthSim.VIEW_W;
    }

    /** The sweep fades in from the left edge and out toward the right: 0 at both ends, 1 midway. */
    private float sweepEnv() {
        float p = (clock % SWEEP_PERIOD) / SWEEP_PERIOD;
        return (float) Math.sin(Math.PI * p);
    }

    /** 0..1 glow at a world x from the sweep: a hot edge at the line with a long fading trail behind it. */
    private float sweepGlow(float worldX) {
        float d = sweepX() - (worldX - cam.x);
        float env = sweepEnv();
        if (d >= 0f) {
            return env * Math.max(0f, 1f - d / 150f);
        }
        return env * Math.max(0f, 1f + d / 18f);
    }

    @Override
    public void renderBelow(float alphaMult) {
        if (pos == null) {
            return;
        }
        clipOn();
        float vx = pos.getX();
        float vy = pos.getY();
        float ox = vx - cam.x + shakeX;
        float oy = vy - cam.y + shakeY;
        float vw = armaa_stealthSim.VIEW_W;
        float vh = armaa_stealthSim.VIEW_H;
        fill(vx, vy, vw, vh, FIELD, alphaMult);

        // faint grid, fixed to the level so it slides as the view moves
        lineWidth(1f);
        prep();
        color(GRID, 0.07f * alphaMult);
        GL11.glBegin(GL11.GL_LINES);
        for (float gx = (float) Math.floor(cam.x / 50f) * 50f; gx < cam.x + vw + 50f; gx += 50f) {
            GL11.glVertex2f(ox + gx, vy);
            GL11.glVertex2f(ox + gx, vy + vh);
        }
        for (float gy = (float) Math.floor(cam.y / 50f) * 50f; gy < cam.y + vh + 50f; gy += 50f) {
            GL11.glVertex2f(vx, oy + gy);
            GL11.glVertex2f(vx + vw, oy + gy);
        }
        GL11.glEnd();
        endLines();

        // dark zones: a dimmer patch of floor
        for (Rect dz : sim.darks) {
            fill(vx + dz.x - cam.x + shakeX, vy + dz.y - cam.y + shakeY, dz.w, dz.h, Color.black, 0.42f * alphaMult);
        }
        bloodBelow(vx, vy, alphaMult);
        lightsBelow(vx, vy, alphaMult);

        // the exit, pulsing slowly
        Rect ex = sim.exit;
        float pulse = 0.75f + 0.25f * (float) Math.sin(clock * 3f);
        fill(ox + ex.x + ex.w / 2f - 12f, oy + ex.y + ex.h / 2f - 12f, 24f, 24f, sim.exitSealed() ? SEARCH : LINE, pulse * alphaMult);
        clipOff();
    }

    @Override
    public void render(float alphaMult) {
        if (pos == null) {
            return;
        }
        clipOn();
        float vx = pos.getX();
        float vy = pos.getY();
        float ox = vx - cam.x + shakeX;
        float oy = vy - cam.y + shakeY;

        for (Guard g : sim.guards) {
            if (g.state != GuardState.DEAD && g.shown > 0.02f) {
                cone(ox, oy, g, alphaMult * g.shown);
            }
        }

        if (!sim.fogOn()) {
            for (Ripple r : sim.ripples) {
                ring(ox, oy, r, alphaMult);
            }
        }

        // walls: a soft wide pass for the glow, then the crisp line; the sweep lights them as it passes
        interactables(ox, oy, alphaMult);
        for (Rect w : sim.walls) {
            if (w.x > cam.x + armaa_stealthSim.VIEW_W + 10f || w.x + w.w < cam.x - 10f
                    || w.y > cam.y + armaa_stealthSim.VIEW_H + 10f || w.y + w.h < cam.y - 10f) {
                continue;                                   // off screen
            }
            float glow = sweepGlow(w.x + w.w / 2f);
            lineWidth(6f);
            outline(ox + w.x, oy + w.y, w.w, w.h, LINE, (0.10f + 0.16f * glow) * alphaMult);
            lineWidth(2f);
            outline(ox + w.x, oy + w.y, w.w, w.h, LINE, (0.62f + 0.38f * glow) * alphaMult);
        }
        for (Rect b : sim.bars) {
            if (b.x > cam.x + armaa_stealthSim.VIEW_W + 10f || b.x + b.w < cam.x - 10f
                    || b.y > cam.y + armaa_stealthSim.VIEW_H + 10f || b.y + b.h < cam.y - 10f) {
                continue;
            }
            // bars: a thin rail along each side and uprights between them, so the view through them is plain
            fill(ox + b.x, oy + b.y, b.w, b.h, FIELD, 0.25f * alphaMult);
            lineWidth(1.5f);
            prep();
            color(LINE, 0.7f * alphaMult);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2f(ox + b.x, oy + b.y + b.h * 0.15f);
            GL11.glVertex2f(ox + b.x + b.w, oy + b.y + b.h * 0.15f);
            GL11.glVertex2f(ox + b.x, oy + b.y + b.h * 0.85f);
            GL11.glVertex2f(ox + b.x + b.w, oy + b.y + b.h * 0.85f);
            for (float bx = b.x + 3f; bx < b.x + b.w; bx += 6f) {
                GL11.glVertex2f(ox + bx, oy + b.y);
                GL11.glVertex2f(ox + bx, oy + b.y + b.h);
            }
            GL11.glEnd();
            endLines();
        }

        pickups(ox, oy, alphaMult);
        locksAndRoland(ox, oy, alphaMult);

        puffs(ox, oy, alphaMult);
        for (Guard g : sim.guards) {
            if (g.shown < 0.02f) {
                continue;                                   // in the dark
            }
            float am = alphaMult * g.shown;
            if (g.state == GuardState.DEAD) {
                if (!g.hidden) {
                    if (g.ko) {
                        knockedOut(ox + g.x, oy + g.y, g, am);
                    } else {
                        body(ox + g.x, oy + g.y, am, downAge(g), g.hitAngle);
                    }
                }
                continue;
            }
            float glow = sweepGlow(g.x);
            float[] an = animate(g, g.x, g.y);
            float gr = armaa_stealthSim.GUARD_RADIUS * an[1];
            float gyy = oy + g.y + an[0];
            String gw = guardWeapon(g);
            float gaim = "baton".equals(gw) ? aimBlend(g, false) : aimBlend(g, g.state == GuardState.ALERT || g.state == GuardState.SUSPICIOUS);
            float[] gl = guardLunge(g);
            GL11.glPushMatrix();
            GL11.glTranslatef(gl[0], gl[1], 0f);
            if (weaponBehind(g.facing)) {
                holdWeapon(gw, ox + g.x, gyy, gr, g.facing, gaim, g.flash, 1f, am);
            }
            disc(ox + g.x, gyy, gr + 3f, FIELD, am);
            disc(ox + g.x, gyy, gr + 2f, GUARD, (0.10f + 0.2f * glow) * am);
            disc(ox + g.x, gyy, gr, GUARD, am);
            circleLine(ox + g.x, gyy, gr + 1f, guardRim, am, 2f);
            if (!weaponBehind(g.facing)) {
                holdWeapon(gw, ox + g.x, gyy, gr, g.facing, gaim, g.flash, 1f, am);
            }
            if (g.hitTime > 0f) {
                // a hit: the guard flashes white and sparks fly from the side the blow came from
                float ht = g.hitTime / armaa_stealthSim.HIT_FLASH;
                disc(ox + g.x, gyy, gr, Color.white, 0.65f * ht * am);
                lineWidth(2f);
                prep();
                color(Color.white, 0.9f * ht * am);
                GL11.glBegin(GL11.GL_LINES);
                for (int i = -1; i <= 1; i++) {
                    double sa = g.hitAngle + i * 0.45;
                    float r0 = gr + 1f;
                    float r1 = gr + 5f + 7f * (1f - ht);
                    GL11.glVertex2f(ox + g.x + (float) Math.cos(sa) * r0, gyy + (float) Math.sin(sa) * r0);
                    GL11.glVertex2f(ox + g.x + (float) Math.cos(sa) * r1, gyy + (float) Math.sin(sa) * r1);
                }
                GL11.glEnd();
                endLines();
            }
            if (g.brokenTime > 0f) {
                // his guard is broken: two stars circle his head until he recovers
                for (int i = 0; i < 2; i++) {
                    double sa = clock * 4.0 + i * Math.PI;
                    star(ox + g.x + (float) Math.cos(sa) * 9f, gyy + gr + 6f + (float) Math.sin(sa) * 3f, 3.2f, new Color(255, 225, 60), Math.min(1f, g.brokenTime * 3f) * am);
                }
            }
            if (g.blockTime > 0f) {
                // a blow turned aside: a bright arc in front of him
                float bt = g.blockTime / 0.25f;
                double bf = Math.toRadians(g.facing);
                lineWidth(3f);
                prep();
                color(Color.white, 0.9f * bt * am);
                GL11.glBegin(GL11.GL_LINE_STRIP);
                for (int i = -4; i <= 4; i++) {
                    double ba = bf + i * 0.18;
                    GL11.glVertex2f(ox + g.x + (float) Math.cos(ba) * (gr + 6f), gyy + (float) Math.sin(ba) * (gr + 6f));
                }
                GL11.glEnd();
                endLines();
            }
            GL11.glPopMatrix();
            if (g.hasKey || g.hasKey2) {
                // the keycard at his belt: a small bright card, offset from the body
                float kx = ox + g.x + gr * 0.6f;
                float ky = gyy - gr - 5f;
                float glint = 0.75f + 0.25f * (float) Math.sin(clock * 5f);
                fill(kx, ky, 9f, 6f, g.hasKey2 ? SEARCH : CONE, glint * am);
                lineWidth(1f);
                outline(kx, ky, 9f, 6f, PLAYER, 0.95f * am);
                fill(kx + 1.5f, ky + 2f, 3f, 2f, PLAYER, 0.9f * am);
            }
            if (g.hp < armaa_stealthSim.MAX_HP) {
                hpBar(ox + g.x - 13f, oy + g.y - armaa_stealthSim.GUARD_RADIUS - 11f, 26f, 5f, (float) g.hp / armaa_stealthSim.MAX_HP, 0, am);
            }
            float rp = sim.radioProgress(g);
            if (rp >= 0f) {
                // about to radio: a ring that fills clockwise from the top; when it closes, he speaks (the reflex window)
                float ringR = gr + 8f;
                circleLine(ox + g.x, gyy, ringR, PLAYER, 0.22f * am, 1f);
                lineWidth(2.5f);
                prep();
                color(PLAYER, 0.95f * am);
                GL11.glBegin(GL11.GL_LINE_STRIP);
                int segs = Math.max(2, (int) (36 * rp));
                for (int i = 0; i <= segs; i++) {
                    double a = Math.PI / 2.0 - Math.PI * 2.0 * rp * i / segs;
                    GL11.glVertex2f(ox + g.x + (float) Math.cos(a) * ringR, gyy + (float) Math.sin(a) * ringR);
                }
                GL11.glEnd();
                endLines();
            }
            if (g.radioState == 3) {
                // speaking into the radio: rings that pulse outward
                float t = (clock * 2.5f) % 1f;
                circleLine(ox + g.x, oy + g.y, armaa_stealthSim.GUARD_RADIUS + 4f + 14f * t, ALERT, (1f - t) * am, 2f);
            }
            float markY = oy + g.y + armaa_stealthSim.GUARD_RADIUS + 6f;
            float pop = pop(g.markAge);
            if (g.state == GuardState.ALERT) {
                exclaim(ox + g.x, markY, ALERT, pop, am);
            } else if (g.bodyAlarm && g.state != GuardState.PATROL) {
                exclaim(ox + g.x, markY, g.witness ? ALERT : SEARCH, pop, am);   // found a body: amber "!", red if he saw it happen
            } else if (g.state != GuardState.PATROL) {
                question(ox + g.x, markY, SEARCH, pop, am);
            }
        }

        for (Guard sg : sim.guards) {
            if (sg.meleeTime > 0f && sg.state == GuardState.ALERT) {
                // a swing building: a red wedge toward the player that fills in until the blow
                float t = Math.min(1f, sg.meleeTime / armaa_stealthSim.MELEE_WINDUP);
                prep();
                GL11.glBegin(GL11.GL_TRIANGLE_FAN);
                color(PLAYER, (0.10f + 0.5f * t) * alphaMult);
                GL11.glVertex2f(ox + sg.x, oy + sg.y);
                color(PLAYER, (0.05f + 0.35f * t) * alphaMult);
                for (int i = -4; i <= 4; i++) {
                    double a = Math.toRadians(sg.facing + i * 9f);
                    GL11.glVertex2f(ox + sg.x + (float) Math.cos(a) * armaa_stealthSim.MELEE_REACH,
                            oy + sg.y + (float) Math.sin(a) * armaa_stealthSim.MELEE_REACH);
                }
                GL11.glEnd();
                GL11.glEnable(GL11.GL_TEXTURE_2D);
            }
        }
        for (Guard ag : sim.guards) {
            if (ag.aimTime > 0f && ag.state == GuardState.ALERT) {
                float t = Math.min(1f, ag.aimTime / ag.aimNeeded);
                lineWidth(1f + 1.5f * t);
                prep();
                GL11.glBegin(GL11.GL_LINES);
                color(ALERT, 0.15f * alphaMult);
                GL11.glVertex2f(ox + ag.x, oy + ag.y);
                color(ALERT, (0.35f + 0.65f * t) * alphaMult);
                GL11.glVertex2f(ox + ag.aimX, oy + ag.aimY);
                GL11.glEnd();
                endLines();
            }
        }
        if (SHOW_SWING_ARC && sim.swingTime > 0f) {
            float t = sim.swingTime / armaa_stealthSim.PLAYER_SWING_SHOW;
            prep();
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            color(PLAYER, 0.15f * t * alphaMult);
            GL11.glVertex2f(ox + sim.px, oy + sim.py);
            color(PLAYER, 0.8f * t * alphaMult);
            for (int i = -5; i <= 5; i++) {
                double a = Math.toRadians(sim.swingAngle + i * (armaa_stealthSim.PLAYER_MELEE_ARC_DEG / 5f));
                GL11.glVertex2f(ox + sim.px + (float) Math.cos(a) * armaa_stealthSim.PLAYER_MELEE_REACH,
                        oy + sim.py + (float) Math.sin(a) * armaa_stealthSim.PLAYER_MELEE_REACH);
            }
            GL11.glEnd();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        }
        Guard tk = sim.grabTarget();
        if (tk != null) {
            circleLine(ox + tk.x, oy + tk.y, armaa_stealthSim.GUARD_RADIUS + 6f + 1.5f * (float) Math.sin(clock * 8f), PLAYER, 0.9f * alphaMult, 2f);
        }
        grabHud(ox, oy, alphaMult);
        aimAndThrown(ox, oy, alphaMult);
        tracers(ox, oy, alphaMult);

        fog(ox, oy, alphaMult);
        if (sim.fogOn()) {
            // sound is the radar's other sense: rings close enough to hear show through the dark
            for (Ripple r : sim.ripples) {
                if (r.fromPlayer || sim.heard(r.x, r.y)) {
                    ring(ox, oy, r, alphaMult);
                }
            }
        }

        // the player, with a faint halo
        disc(ox + sim.px, oy + sim.py, armaa_stealthSim.PLAYER_RADIUS + 5f, PLAYER,
                (0.10f + 0.05f * (float) Math.sin(clock * 4f)) * alphaMult);
        float[] pan = animate(sim, sim.px, sim.py);
        float pAimDeg = (float) Math.toDegrees(Math.atan2(aimY() - sim.py, aimX() - sim.px));
        boolean pArmed = sim.hasSidearm && sim.status == Status.RUNNING;
        float pr = playerFace != null ? PORTRAIT_RADIUS : armaa_stealthSim.PLAYER_RADIUS;
        float pAim = aimBlend(this, mouseIn);
        float pl = sim.swingTime > 0f ? armaa_stealthSim.strikeOffset(1f - sim.swingTime / armaa_stealthSim.PLAYER_SWING_SHOW, 0f, armaa_stealthSim.LUNGE_REACH) : 0f;
        GL11.glPushMatrix();
        GL11.glTranslatef((float) Math.cos(Math.toRadians(sim.swingAngle)) * pl, (float) Math.sin(Math.toRadians(sim.swingAngle)) * pl, 0f);
        if (pArmed && weaponBehind(pAimDeg)) {
            holdWeapon("pistol", ox + sim.px, oy + sim.py, pr, pAimDeg, pAim, sim.playerFlash, 1f, alphaMult);
        }
        person(ox + sim.px, oy + sim.py + pan[0], playerFace, playerRim, alphaMult, pan[1]);
        if (pArmed && !weaponBehind(pAimDeg)) {
            holdWeapon("pistol", ox + sim.px, oy + sim.py, pr, pAimDeg, pAim, sim.playerFlash, 1f, alphaMult);
        }
        GL11.glPopMatrix();

        sweep(vx, vy, alphaMult);
        alertFlash(vx, vy, alphaMult);
        speechBubbles(vx, vy, alphaMult);
        noticeMarkers(vx, vy, alphaMult);
        cautionBar(vx, vy, alphaMult);
        weaponsFree(vx, vy, alphaMult);
        counters(vx, vy, alphaMult);
        droneAndHint(ox, oy, alphaMult);
        sparks(ox, oy, alphaMult);
        popups(ox, oy, alphaMult);
        hud(vx, vy, alphaMult);
        jamStatic(vx, vy, alphaMult);
        slowMoVignette(vx, vy, alphaMult);
        scanlines(vx, vy, alphaMult);
        transitionOverlay(vx, vy, alphaMult);
        clipOff();
    }

    /** The picture breaking up into blocks and going black on the way out, the title card, and the blocks clearing on the way in. */
    private void transitionOverlay(float vx, float vy, float alphaMult) {
        if (trans == 0) {
            return;
        }
        float w = armaa_stealthSim.VIEW_W;
        float h = armaa_stealthSim.VIEW_H;
        if (trans == 1) {
            dissolve(vx, vy, w, h, Math.min(1f, transT / (TRANS_OUT * 0.9f)), alphaMult);
        } else if (trans == 2) {
            fill(vx, vy, w, h, Color.black, alphaMult);
            titleCard(vx, vy, w, h, alphaMult);
        } else {
            float p = 1f - transT / TRANS_IN;
            if (p > 0f) {
                dissolve(vx, vy, w, h, p, alphaMult);
            }
        }
    }

    /** Black blocks over the picture, p (0 to 1) of them; the blocks are small at first and coarser as it goes black (and finer as it clears). */
    private void dissolve(float vx, float vy, float w, float h, float p, float alphaMult) {
        if (p >= 0.995f) {
            fill(vx, vy, w, h, Color.black, alphaMult);
            return;
        }
        float cell = p < 0.34f ? 6f : (p < 0.67f ? 12f : 20f);
        int stage = (int) cell;
        int cols = (int) Math.ceil(w / cell);
        int rows = (int) Math.ceil(h / cell);
        prep();
        GL11.glBegin(GL11.GL_QUADS);
        color(Color.black, alphaMult);
        for (int j = 0; j < rows; j++) {
            for (int i = 0; i < cols; i++) {
                int hash = (i * 73856093) ^ (j * 19349663) ^ (stage * 83492791);
                hash ^= hash >>> 15;
                float thr = (hash & 1023) / 1023f;
                if (thr < p) {
                    float x0 = vx + i * cell;
                    float y0 = vy + j * cell;
                    GL11.glVertex2f(x0, y0);
                    GL11.glVertex2f(x0 + cell, y0);
                    GL11.glVertex2f(x0 + cell, y0 + cell);
                    GL11.glVertex2f(x0, y0 + cell);
                }
            }
        }
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    /** The name of the place: two lines draw out from the middle, the headline resolves out of noise letter by letter, and the subtitle follows. */
    private void titleCard(float vx, float vy, float w, float h, float alphaMult) {
        float t = transT;
        float a = Math.min(1f, t / 0.25f) * Math.min(1f, (TRANS_CARD - t) / 0.35f) * alphaMult;
        float cx = vx + w / 2f;
        float cy = vy + h / 2f;
        float grow = Math.min(1f, t / 0.5f);
        float half = w * 0.3f * grow;
        lineWidth(1.5f);
        prep();
        GL11.glBegin(GL11.GL_LINES);
        color(LINE, 0.7f * a);
        GL11.glVertex2f(cx - half, cy + 30f);
        GL11.glVertex2f(cx + half, cy + 30f);
        GL11.glVertex2f(cx - half, cy - 26f);
        GL11.glVertex2f(cx + half, cy - 26f);
        GL11.glEnd();
        endLines();
        String head = transTitle[0].toUpperCase();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < head.length(); i++) {
            char c = head.charAt(i);
            boolean resolved = c == ' ' || t >= 0.3f + i * 0.05f + 0.15f;
            sb.append(resolved ? c : GARBLE.charAt(((int) (clock * 22f) + i * 7) % GARBLE.length()));
        }
        hudText(sb.toString(), cx, cy + 4f, 28f, PLAIN_TEXT, a, org.lazywizard.lazylib.ui.LazyFont.TextAnchor.CENTER);
        float sub = Math.min(1f, Math.max(0f, (t - 0.9f) / 0.4f));
        if (sub > 0f) {
            hudText(transTitle[1].toUpperCase(), cx, cy - 13f, 16f, LINE, a * sub, org.lazywizard.lazylib.ui.LazyFont.TextAnchor.CENTER);
        }
    }

    /**
     * When a guard you cannot see has noticed you: a "?" (or "!" once he is alert) on the edge of the view pointing to him, or over his
     * spot if he is on screen but hidden in the dark. It lasts a few seconds and pulses.
     */
    private void noticeMarkers(float vx, float vy, float alphaMult) {
        for (armaa_stealthSim.Ping p : sim.pings) {
            Guard g = p.who;
            if (g.shown >= 0.3f || sim.canSee(g.x, g.y)) {
                continue;                                       // in sight: his own mark says it
            }
            float[] m = armaa_stealthSim.edgeMarker(g.x, g.y, cam.x, cam.y, armaa_stealthSim.VIEW_W, armaa_stealthSim.VIEW_H, 22f);
            float fade = Math.min(1f, (armaa_stealthSim.PING_LIFE - p.age) / 0.6f) * (0.65f + 0.35f * (float) Math.sin(clock * 10f));
            Color c = p.alert ? ALERT : SEARCH;
            float x = vx + m[0];
            float y = vy + m[1];
            if (m[3] > 0.5f) {
                // a chevron on the edge, pointing the way to him
                double a = Math.toRadians(m[2]);
                float ux = (float) Math.cos(a);
                float uy = (float) Math.sin(a);
                prep();
                color(c, 0.9f * fade * alphaMult);
                GL11.glBegin(GL11.GL_TRIANGLES);
                GL11.glVertex2f(x + ux * 10f, y + uy * 10f);
                GL11.glVertex2f(x - ux * 6f - uy * 8f, y - uy * 6f + ux * 8f);
                GL11.glVertex2f(x - ux * 6f + uy * 8f, y - uy * 6f - ux * 8f);
                GL11.glEnd();
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                // the mark sits just inside the chevron
                float mx = x - ux * 22f;
                float my = y - uy * 22f - 6f;
                if (p.alert) {
                    exclaim(mx, my, c, 1f, fade * alphaMult);
                } else {
                    question(mx, my, c, 1f, fade * alphaMult);
                }
            } else {
                circleLine(x, y, 11f + 3f * (float) Math.sin(clock * 10f), c, 0.8f * fade * alphaMult, 2f);
                if (p.alert) {
                    exclaim(x, y + 12f, c, 1f, fade * alphaMult);
                } else {
                    question(x, y + 12f, c, 1f, fade * alphaMult);
                }
            }
        }
    }

    /** The held guard's ring, the question gauge over him, and a mark on whoever has the keycard once he has talked. */
    private void grabHud(float ox, float oy, float alphaMult) {
        Guard g = sim.grabbed;
        if (g != null) {
            float t = Math.min(1f, g.shown + 0.01f);
            circleLine(ox + g.x, oy + g.y, armaa_stealthSim.GUARD_RADIUS + 5f, PLAYER, 0.9f * alphaMult, 2.5f);
            float hold = sim.grabSilent ? armaa_stealthSim.GRAB_HOLD_BEHIND : armaa_stealthSim.GRAB_HOLD_FRONT;
            float left = Math.max(0f, 1f - sim.grabTime / hold);
            fill(ox + g.x - 14f, oy + g.y + armaa_stealthSim.GUARD_RADIUS + 8f, 28f, 3f, Color.black, 0.6f * alphaMult);
            fill(ox + g.x - 14f, oy + g.y + armaa_stealthSim.GUARD_RADIUS + 8f, 28f * left, 3f, left < 0.3f ? SEARCH : PLAYER, 0.9f * alphaMult);
            if (!sim.grabQuestioned && sim.questionProgress > 0f) {
                float q = Math.min(1f, sim.questionProgress / armaa_stealthSim.QUESTION_TIME);
                fill(ox + g.x - 14f, oy + g.y + armaa_stealthSim.GUARD_RADIUS + 12f, 28f, 3f, Color.black, 0.6f * alphaMult);
                fill(ox + g.x - 14f, oy + g.y + armaa_stealthSim.GUARD_RADIUS + 12f, 28f * q, 3f, Color.white, 0.9f * alphaMult);
            }
        }
        if (sim.revealLeft > 0f) {
            float fade = Math.min(1f, sim.revealLeft / 2f);
            for (Guard k : sim.guards) {
                if ((k.hasKey || k.hasKey2) && k.state != GuardState.DEAD) {
                    circleLine(ox + k.x, oy + k.y, armaa_stealthSim.GUARD_RADIUS + 7f + 2f * (float) Math.sin(clock * 6f), SEARCH, 0.95f * fade * alphaMult, 2f);
                }
            }
        }
    }

    /** Blood on the floor: irregular dark-red blobs that spread slowly and then dry darker. Drawn under the lights, so the dark dims it, and under the fog. */
    private void bloodBelow(float vx, float vy, float alphaMult) {
        if (sim.blood.isEmpty() && sim.droplets.isEmpty()) {
            return;
        }
        float offX = vx - cam.x + shakeX;
        float offY = vy - cam.y + shakeY;
        // drops in flight, as small bright streaks
        for (armaa_stealthSim.Droplet d : sim.droplets) {
            lineWidth(2f);
            prep();
            color(new Color(200, 28, 34), 0.85f * alphaMult);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2f(offX + d.x, offY + d.y);
            GL11.glVertex2f(offX + d.x - d.vx * 0.03f, offY + d.y - d.vy * 0.03f);
            GL11.glEnd();
            endLines();
        }
        for (armaa_stealthSim.Blood b : sim.blood) {
            float reach = b.size * Math.max(1.3f, b.stretch) * 1.3f;
            if (b.x < cam.x - reach || b.x > cam.x + armaa_stealthSim.VIEW_W + reach || b.y < cam.y - reach || b.y > cam.y + armaa_stealthSim.VIEW_H + reach) {
                continue;
            }
            Color c = new Color(Math.round(132 - 62 * b.dry), Math.round(16 - 6 * b.dry), Math.round(22 - 8 * b.dry));
            float a = 0.6f * (1f - 0.35f * b.dry) * b.fade * alphaMult;
            double cr = Math.cos(b.rot);
            double sr = Math.sin(b.rot);
            if (b.kind == 4) {
                // a footprint: the sole, and the heel behind it
                float k = b.size / 3.4f;
                bloodEllipse(offX + b.x + (float) cr * 1.8f * k, offY + b.y + (float) sr * 1.8f * k, 4.2f * k, 2.2f * k, b.rot, c, a);
                bloodEllipse(offX + b.x - (float) cr * 3.6f * k, offY + b.y - (float) sr * 3.6f * k, 1.7f * k, 1.6f * k, b.rot, c, a);
                continue;
            }
            prep();
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            color(c, a);
            GL11.glVertex2f(offX + b.x, offY + b.y);
            for (int i = 0; i <= armaa_stealthSim.BLOOD_LOBES; i++) {
                int k = i % armaa_stealthSim.BLOOD_LOBES;
                double ang = Math.PI * 2.0 * k / armaa_stealthSim.BLOOD_LOBES;
                double lx = Math.cos(ang) * b.size * b.lobes[k] * b.stretch;
                double ly = Math.sin(ang) * b.size * b.lobes[k];
                color(c, a * 0.8f);
                GL11.glVertex2f(offX + b.x + (float) (lx * cr - ly * sr), offY + b.y + (float) (lx * sr + ly * cr));
            }
            GL11.glEnd();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        }
    }

    // ---- held weapons ----
    /** 0 held low, 1 raised to aim, easing between. */
    private float aimBlend(Object who, boolean aiming) {
        float[] v = weaponAim.get(who);
        if (v == null) {
            v = new float[]{aiming ? 1f : 0f};
            weaponAim.put(who, v);
        }
        v[0] = armaa_stealthSim.approach(v[0], aiming ? 1f : 0f, 5f * frameDt);
        return v[0];
    }

    private void gunPart(float hx, float hy, float ca, float sa, float flip, float x0, float y0, float w, float h, Color c, float al) {
        float[][] pts = {{x0, y0}, {x0 + w, y0}, {x0 + w, y0 + h}, {x0, y0 + h}};
        prep();
        color(c, al);
        GL11.glBegin(GL11.GL_QUADS);
        for (float[] pt : pts) {
            float lx = pt[0];
            float ly = -pt[1] * flip;
            GL11.glVertex2f(hx + lx * ca - ly * sa, hy + lx * sa + ly * ca);
        }
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    /**
     * A weapon in a character's hands, drawn from a few flat shapes along its own length: a rifle, a small pistol, a heavy gun, a baton.
     * It is raised along the facing when aiming and held low across the body otherwise, kicks back a little on a shot and flashes at the
     * muzzle. `scale` sizes it; the stripe takes the faction colour and the steel is tinted like the helmet's.
     */
    private void holdWeapon(String kind, float cx, float cy, float r, float facingDeg, float aim, float flash, float scale, float alphaMult) {
        double f = Math.toRadians(facingDeg);
        double lowDir = f - Math.toRadians(80.0);
        float off = 0.38f * r * aim + 0.15f * r * (1f - aim);
        float hx = cx + (float) Math.cos(f) * off + (1f - aim) * (float) Math.cos(lowDir) * r * 0.55f;
        float hy = cy + (float) Math.sin(f) * off + (1f - aim) * (float) Math.sin(lowDir) * r * 0.55f;
        double ang = f - Math.toRadians(65.0) * (1.0 - aim);
        float ca = (float) Math.cos(ang);
        float sa = (float) Math.sin(ang);
        float rec = "baton".equals(kind) ? 0f : Math.max(0f, flash) * 3.2f * scale;
        hx -= ca * rec;
        hy -= sa * rec;
        float flip = ca < 0f ? -1f : 1f;
        Color dark = new Color(38, 43, 47);
        Color mid = tintedSteel(80, 86, 92);
        Color stripe = new Color(Math.min(255, (int) (LINE.getRed() * 0.78f)), Math.min(255, (int) (LINE.getGreen() * 0.78f)), Math.min(255, (int) (LINE.getBlue() * 0.78f)));
        float k = scale;
        float length;
        if ("rifle".equals(kind)) {
            gunPart(hx, hy, ca, sa, flip, -9f * k, -1.7f * k, 8f * k, 3.4f * k, dark, alphaMult);
            gunPart(hx, hy, ca, sa, flip, -1f * k, -2.6f * k, 9f * k, 5.2f * k, mid, alphaMult);
            gunPart(hx, hy, ca, sa, flip, 8f * k, -2.2f * k, 9f * k, 4.4f * k, stripe, alphaMult);
            gunPart(hx, hy, ca, sa, flip, 17f * k, -1f * k, 8f * k, 2f * k, dark, alphaMult);
            gunPart(hx, hy, ca, sa, flip, 3f * k, 2.4f * k, 3.2f * k, 5.4f * k, dark, alphaMult);
            length = 26f * k;
        } else if ("pistol".equals(kind)) {
            gunPart(hx, hy, ca, sa, flip, -1.2f * k, 0f, 3.2f * k, 5.4f * k, dark, alphaMult);
            gunPart(hx, hy, ca, sa, flip, -2.4f * k, -2f * k, 10f * k, 3.6f * k, mid, alphaMult);
            gunPart(hx, hy, ca, sa, flip, 7.6f * k, -1f * k, 3f * k, 1.6f * k, dark, alphaMult);
            gunPart(hx, hy, ca, sa, flip, -1f * k, -2f * k, 5f * k, 1f * k, stripe, alphaMult);
            length = 11f * k;
        } else if ("heavy".equals(kind)) {
            gunPart(hx, hy, ca, sa, flip, -12f * k, -3.2f * k, 10f * k, 6.4f * k, dark, alphaMult);
            gunPart(hx, hy, ca, sa, flip, -2f * k, -4.2f * k, 16f * k, 8.4f * k, mid, alphaMult);
            gunPart(hx, hy, ca, sa, flip, 14f * k, -3.2f * k, 11f * k, 6.4f * k, stripe, alphaMult);
            gunPart(hx, hy, ca, sa, flip, 25f * k, -1.5f * k, 17f * k, 3f * k, dark, alphaMult);
            gunPart(hx, hy, ca, sa, flip, 0f, 3.5f * k, 8f * k, 6.5f * k, dark, alphaMult);              // the drum
            length = 44f * k;
        } else {
            gunPart(hx, hy, ca, sa, flip, -3f * k, -1.2f * k, 5f * k, 2.4f * k, dark, alphaMult);
            gunPart(hx, hy, ca, sa, flip, 2f * k, -1f * k, 13f * k, 2f * k, new Color(205, 211, 215), alphaMult);
            length = 15f * k;
        }
        float fl = Math.max(0f, flash - 0.6f) / 0.4f;
        if (fl > 0f && !"baton".equals(kind)) {
            float mx = hx + ca * length;
            float my = hy + sa * length;
            prep();
            GL11.glBegin(GL11.GL_TRIANGLES);
            color(new Color(255, 233, 168), fl * alphaMult);
            GL11.glVertex2f(mx, my);
            GL11.glVertex2f(mx + ca * 5f - sa * 3.2f * scale, my + sa * 5f + ca * 3.2f * scale);
            GL11.glVertex2f(mx + ca * 11f * scale, my + sa * 11f * scale);
            GL11.glVertex2f(mx, my);
            GL11.glVertex2f(mx + ca * 5f + sa * 3.2f * scale, my + sa * 5f - ca * 3.2f * scale);
            GL11.glVertex2f(mx + ca * 11f * scale, my + sa * 11f * scale);
            GL11.glEnd();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        }
    }

    /** True when a character faces up the screen, so the weapon is behind the body. */
    private static boolean weaponBehind(float facingDeg) {
        return Math.sin(Math.toRadians(facingDeg)) > 0.35;
    }

    /** What a guard holds: a rifle once he is a gunner (weapons free, or in the flood), a baton before. */
    private String guardWeapon(Guard g) {
        return (g.flood || (sim.lethalKnown && g.role == armaa_stealthSim.ROLE_GUN)) ? "rifle" : "baton";
    }

    /** Half the helmet's width on screen: about the size of a prisoner's portrait. */
    private static final float ARMOR_ICON_H = 16f;

    /** A steel grey blended about 55% toward the interface colour (the faction's, when there is one): metal that belongs to the radar. */
    private static Color tintedSteel(int r, int g, int b) {
        float k = 0.55f;
        float[] in = {r, g, b};
        float[] t = {LINE.getRed() / 255f, LINE.getGreen() / 255f, LINE.getBlue() / 255f};
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) {
            out[i] = Math.min(255, Math.round(in[i] * (1f - k) + in[i] * t[i] * k * 1.15f));
        }
        return new Color(out[0], out[1], out[2]);
    }

    private void fanPoly(float cx, float cy, float[] pts, Color c, float a) {
        prep();
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        color(c, a);
        GL11.glVertex2f(cx, cy);
        for (int i = 0; i <= pts.length; i += 2) {
            int k = i % pts.length;
            GL11.glVertex2f(pts[k], pts[k + 1]);
        }
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private void polyLine(float[] pts, Color c, float a) {
        lineWidth(1.5f);
        prep();
        color(c, a);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < pts.length; i += 2) {
            GL11.glVertex2f(pts[i], pts[i + 1]);
        }
        GL11.glEnd();
        endLines();
    }

    private static float[] roundRect(float x0, float y0, float x1, float y1, float r) {
        r = Math.max(0f, Math.min(r, Math.min((x1 - x0) / 2f, (y1 - y0) / 2f)));
        java.util.List<Float> v = new java.util.ArrayList<Float>();
        float[][] corners = {{x1 - r, y1 - r, 0f}, {x0 + r, y1 - r, 90f}, {x0 + r, y0 + r, 180f}, {x1 - r, y0 + r, 270f}};
        for (float[] c : corners) {
            for (int i = 0; i <= 5; i++) {
                double ang = Math.toRadians(c[2] + 90.0 * i / 5.0);
                v.add(c[0] + (float) Math.cos(ang) * r);
                v.add(c[1] + (float) Math.sin(ang) * r);
            }
        }
        float[] out = new float[v.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = v.get(i);
        }
        return out;
    }

    /**
     * The power-armor marine: a domed helmet that turns with the way they face (front on when facing down the screen, a side view with
     * two eyes when facing sideways, the back of the head when facing up), a visor plate that slides across the dome, two prongs on top,
     * and four lens eyes that fade in and out as they turn toward you.
     */
    private void armoredHelmet(float cx, float cy, float h, float facingDeg, float alphaMult) {
        double a = Math.toRadians(facingDeg);
        double th = Math.atan2(Math.cos(a), -Math.sin(a));
        float c = (float) Math.cos(th);
        float s = (float) Math.sin(th);
        float d = 0.85f * h;
        float ac = Math.abs(c);
        float as = Math.abs(s);
        float hy = cy;
        float W = h * (1.0f * ac + 0.88f * as) + 0.04f * h;
        float topY = hy + 0.95f * h;
        Color steel = tintedSteel(86, 92, 98);
        // a shadow under it, so it sits on the floor
        disc(cx, hy - 0.95f * h - 0.1f * h, (0.9f * ac + 0.8f * as) * h * 0.9f, Color.black, 0.3f * alphaMult);
        for (int k = -1; k <= 1; k += 2) {
            float px = cx + k * 0.55f * h * c;
            float[] prong = roundRect(px - 0.17f * h, topY - 0.15f * h, px + 0.17f * h, topY + 0.5f * h, 0.14f * h);
            fanPoly(px, topY + 0.18f * h, prong, tintedSteel(89, 96, 103), alphaMult);
            polyLine(prong, LINE, alphaMult);
        }
        float[] dome = new float[96];
        for (int i = 0; i < 48; i++) {
            double t = i / 48.0 * Math.PI * 2.0;
            double ct = Math.cos(t);
            double st = Math.sin(t);
            double n = st > 0 ? 2.3 : 4.2;
            dome[i * 2] = cx + W * (float) (Math.signum(ct) * Math.pow(Math.abs(ct), 2.0 / n));
            dome[i * 2 + 1] = hy + 0.95f * h * (float) (Math.signum(st) * Math.pow(Math.abs(st), 2.0 / n));
        }
        fanPoly(cx, hy, dome, steel, alphaMult);
        float[][] corners = {{-h, d}, {h, d}, {h, -d}, {-h, -d}};
        float[] px = new float[4];
        for (int i = 0; i < 4; i++) {
            px[i] = cx + corners[i][0] * c + corners[i][1] * s;
        }
        float[] normal = {c, -s, -c, s};
        Color[] shade = {tintedSteel(154, 160, 166), tintedSteel(118, 124, 130), tintedSteel(79, 85, 90), tintedSteel(104, 110, 116)};
        // the faces turned toward you, nearest last
        Integer[] order = {0, 1, 2, 3};
        java.util.Arrays.sort(order, (p, q) -> Float.compare(normal[p], normal[q]));
        for (int idx : order) {
            if (normal[idx] <= 0.001f || idx == 2) {
                continue;
            }
            float x0 = Math.min(px[idx], px[(idx + 1) % 4]) + 0.06f * h;
            float x1 = Math.max(px[idx], px[(idx + 1) % 4]) - 0.06f * h;
            x0 = Math.max(x0, cx - W * 0.94f);
            x1 = Math.min(x1, cx + W * 0.94f);
            if (x1 - x0 < 0.5f) {
                continue;
            }
            float[] plate = roundRect(x0, hy - 0.74f * h, x1, hy + 0.58f * h, 0.38f * h);
            fanPoly((x0 + x1) / 2f, hy - 0.08f * h, plate, shade[idx], alphaMult);
            polyLine(plate, Color.black, 0.3f * alphaMult);
        }
        if (normal[2] > 0.001f) {
            fanPoly(cx, hy, dome, shade[2], alphaMult * 0.55f);                 // the back of the head
        }
        float[] crown = new float[24];
        for (int i = 0; i < 12; i++) {
            double t = Math.PI * 2.0 * i / 12.0;
            crown[i * 2] = cx - 0.15f * h * s + (float) Math.cos(t) * W * 0.7f;
            crown[i * 2 + 1] = hy + 0.65f * h + (float) Math.sin(t) * 0.2f * h;
        }
        fanPoly(cx - 0.15f * h * s, hy + 0.65f * h, crown, LINE, 0.22f * alphaMult);
        polyLine(dome, LINE, alphaMult);
        float[][] eyes = {{-0.46f, -0.30f}, {0.46f, -0.30f}, {-0.46f, 0.42f}, {0.46f, 0.42f}};
        for (float[] e : eyes) {
            float ex = cx + e[0] * h * c + d * s;
            float ez = -e[0] * h * s + d * c;
            float t = Math.max(0f, Math.min(1f, ez / (0.4f * h)));
            float al = t * t * (3f - 2f * t);
            if (al <= 0.01f) {
                continue;
            }
            float ey = hy - e[1] * h;
            disc(ex, ey, 0.13f * h * 1.9f, new Color(255, 64, 64), 0.3f * al * alphaMult);
            disc(ex, ey, 0.13f * h * 1.45f, new Color(35, 39, 43), al * alphaMult);
            circleLine(ex, ey, 0.13f * h * 1.45f, new Color(111, 118, 124), al * alphaMult, 1f);
            disc(ex, ey, 0.13f * h, new Color(255, 48, 48), al * alphaMult);
            disc(ex - 0.13f * h * 0.35f, ey + 0.13f * h * 0.35f, 0.13f * h * 0.28f, new Color(255, 210, 210), 0.8f * al * alphaMult);
        }
    }

    private void bloodEllipse(float cx, float cy, float rx, float ry, float rot, Color c, float a) {
        double cr = Math.cos(rot);
        double sr = Math.sin(rot);
        prep();
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        color(c, a);
        GL11.glVertex2f(cx, cy);
        for (int i = 0; i <= 12; i++) {
            double ang = Math.PI * 2.0 * i / 12.0;
            double lx = Math.cos(ang) * rx;
            double ly = Math.sin(ang) * ry;
            color(c, a * 0.85f);
            GL11.glVertex2f(cx + (float) (lx * cr - ly * sr), cy + (float) (lx * sr + ly * cr));
        }
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    /** Light fixtures: a faint pool of light on the floor they light, the dim floor where one is broken, and the fixtures themselves. */
    private void lightsBelow(float vx, float vy, float alphaMult) {
        if (sim.lights.isEmpty()) {
            return;
        }
        float fc = armaa_stealthSim.FOG_CELL;
        float offX = vx - cam.x + shakeX;
        float offY = vy - cam.y + shakeY;
        float vw = armaa_stealthSim.VIEW_W;
        float vh = armaa_stealthSim.VIEW_H;
        for (armaa_stealthSim.Light l : sim.lights) {
            if (l.glow < 0.01f || l.x < cam.x - l.radius || l.x > cam.x + vw + l.radius || l.y < cam.y - l.radius || l.y > cam.y + vh + l.radius) {
                continue;
            }
            for (int k = 0; k < l.cells.length; k++) {
                int c = l.cells[k] % sim.fogCols;
                int r = l.cells[k] / sim.fogCols;
                fill(offX + c * fc, offY + r * fc, fc, fc, Color.white, 0.07f * l.weights[k] * l.glow * alphaMult);
            }
        }
        int c0 = Math.max(0, (int) (cam.x / fc));
        int c1 = Math.min(sim.fogCols - 1, (int) ((cam.x + vw) / fc));
        int r0 = Math.max(0, (int) (cam.y / fc));
        int r1 = Math.min(sim.fogRows - 1, (int) ((cam.y + vh) / fc));
        for (int r = r0; r <= r1; r++) {
            int c = c0;
            while (c <= c1) {
                float a = sim.darkness(c, r);
                if (a < 0.02f) {
                    c++;
                    continue;
                }
                int e = c;
                while (e + 1 <= c1 && Math.abs(sim.darkness(e + 1, r) - a) < 0.04f) {
                    e++;
                }
                fill(offX + c * fc, offY + r * fc, (e - c + 1) * fc, fc, Color.black, 0.42f * a * alphaMult);
                c = e + 1;
            }
        }
        for (armaa_stealthSim.Light l : sim.lights) {
            float x = offX + l.x;
            float y = offY + l.y;
            if (l.on) {
                prep();
                GL11.glBegin(GL11.GL_TRIANGLE_FAN);
                color(Color.white, 0.35f * alphaMult);
                GL11.glVertex2f(x, y);
                color(Color.white, 0f);
                for (int i = 0; i <= 16; i++) {
                    double a = i * Math.PI / 8.0;
                    GL11.glVertex2f(x + (float) Math.cos(a) * 16f, y + (float) Math.sin(a) * 16f);
                }
                GL11.glEnd();
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                fill(x - 2.5f, y - 2.5f, 5f, 5f, Color.white, alphaMult);
            } else {
                fill(x - 2.5f, y - 2.5f, 5f, 5f, new Color(90, 90, 90), 0.7f * alphaMult);
                if (l.breakAge < 0.35f) {
                    float t = l.breakAge / 0.35f;
                    lineWidth(1.5f);
                    prep();
                    GL11.glBegin(GL11.GL_LINES);
                    color(Color.white, (1f - t) * alphaMult);
                    for (int i = 0; i < 7; i++) {
                        double a = Math.toRadians(i * 51f + l.x * 7f);
                        GL11.glVertex2f(x + (float) Math.cos(a) * (3f + 5f * t), y + (float) Math.sin(a) * (3f + 5f * t));
                        GL11.glVertex2f(x + (float) Math.cos(a) * (6f + 30f * t), y + (float) Math.sin(a) * (6f + 30f * t));
                    }
                    GL11.glEnd();
                    endLines();
                }
            }
        }
    }

    /** During the reflex window: a bright border and a faint wash, so the slowed time is obvious. */
    private void slowMoVignette(float vx, float vy, float alphaMult) {
        float amt = sim.slowMoAmount();
        if (amt <= 0.02f) {
            return;
        }
        float w = armaa_stealthSim.VIEW_W;
        float h = armaa_stealthSim.VIEW_H;
        float t = 14f * amt;
        float a = 0.35f * amt * alphaMult;
        fill(vx, vy, w, t, PLAYER, a);
        fill(vx, vy + h - t, w, t, PLAYER, a);
        fill(vx, vy, t, h, PLAYER, a);
        fill(vx + w - t, vy, t, h, PLAYER, a);
        fill(vx, vy, w, h, PLAYER, 0.05f * amt * alphaMult);
    }

    /** Radar jamming while any guard is alert: flickering streaks of static across the screen, thicker the worse it is. */
    private void jamStatic(float vx, float vy, float alphaMult) {
        if (sim.jam <= 0.02f) {
            return;
        }
        java.util.Random rnd = new java.util.Random((long) (clock * 18f));       // changes about 18 times a second
        int streaks = 6 + (int) (22 * sim.jam);
        float w = armaa_stealthSim.VIEW_W;
        float h = armaa_stealthSim.VIEW_H;
        for (int i = 0; i < streaks; i++) {
            float y = vy + rnd.nextFloat() * h;
            float x0 = vx + rnd.nextFloat() * w * 0.7f;
            float len = (0.1f + rnd.nextFloat() * 0.5f) * w;
            float a = (0.06f + 0.16f * rnd.nextFloat()) * sim.jam * alphaMult;
            fill(x0, y, Math.min(len, vx + w - x0), 1f + 2f * rnd.nextFloat(), LINE, a);
        }
        // and a faint wash over the whole screen
        fill(vx, vy, w, h, LINE, 0.04f * sim.jam * alphaMult);
    }

    /** Darkens what the player cannot see now: black where never seen, dimmed where only remembered. */
    private void fog(float ox, float oy, float alphaMult) {
        if (!sim.fogOn()) {
            return;
        }
        int c0 = Math.max(0, (int) (cam.x / armaa_stealthSim.FOG_CELL) - 1);
        int c1 = Math.min(sim.fogCols - 1, (int) ((cam.x + armaa_stealthSim.VIEW_W) / armaa_stealthSim.FOG_CELL) + 1);
        int r0 = Math.max(0, (int) (cam.y / armaa_stealthSim.FOG_CELL) - 1);
        int r1 = Math.min(sim.fogRows - 1, (int) ((cam.y + armaa_stealthSim.VIEW_H) / armaa_stealthSim.FOG_CELL) + 1);
        float cell = armaa_stealthSim.FOG_CELL;
        prep();
        GL11.glBegin(GL11.GL_QUADS);
        for (int r = r0; r <= r1; r++) {
            for (int c = c0; c <= c1; c++) {
                float seen = sim.cellShown(c, r);
                float dark = sim.cellExplored(c, r) ? 0.62f : 1f;
                float a = (1f - seen) * dark;
                if (a < 0.02f) {
                    continue;
                }
                color(FIELD, a * alphaMult);
                float x = ox + c * cell;
                float y = oy + r * cell;
                GL11.glVertex2f(x, y);
                GL11.glVertex2f(x + cell, y);
                GL11.glVertex2f(x + cell, y + cell);
                GL11.glVertex2f(x, y + cell);
            }
        }
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    /** The "!" or "?" overshoots a little as it appears, then settles. */
    private static float pop(float age) {
        float grow = Math.min(1f, age / 0.12f);
        float overshoot = Math.max(0f, 1f - age / 0.25f) * 0.3f;
        return grow * (1f + overshoot);
    }

    // ---- level furniture ----
    /** Things lying about: a pulsing diamond with a faint halo. */
    private void pickups(float ox, float oy, float alphaMult) {
        for (Pickup pk : sim.pickups) {
            float pulse = 0.6f + 0.4f * (float) Math.sin(clock * 4f);
            Color tint = "key".equals(pk.type) ? CONE : SEARCH;
            disc(ox + pk.x, oy + pk.y, 9f, tint, 0.10f * pulse * alphaMult);
            if ("sidearm".equals(pk.type) || "rifle".equals(pk.type) || "baton".equals(pk.type)) {
                // a weapon lying on the floor, tilted
                String kind = "sidearm".equals(pk.type) ? "pistol" : pk.type;
                float off = "rifle".equals(kind) ? 8f : ("pistol".equals(kind) ? 4f : 6f);
                holdWeapon(kind, ox + pk.x - off * 0.9f, oy + pk.y + off * 0.4f, 0f, 25f, 1f, 0f, 0.9f, alphaMult);
                continue;
            }
            prep();
            color(tint, alphaMult);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2f(ox + pk.x, oy + pk.y + 5f);
            GL11.glVertex2f(ox + pk.x - 5f, oy + pk.y);
            GL11.glVertex2f(ox + pk.x, oy + pk.y - 5f);
            GL11.glVertex2f(ox + pk.x + 5f, oy + pk.y);
            GL11.glEnd();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        }
    }

    /** Small brackets on things that can be examined, and the grate the vent came out of. */
    private void interactables(float ox, float oy, float alphaMult) {
        for (armaa_stealthLevel.Interact it : sim.interacts) {
            if ("roland".equals(it.type) && sim.rolandFree) {
                continue;
            }
            float dx = it.x - sim.px;
            float dy = it.y - sim.py;
            boolean near = dx * dx + dy * dy < armaa_stealthSim.INTERACT_RANGE * armaa_stealthSim.INTERACT_RANGE;
            float a = (near ? 0.9f : 0.35f) * alphaMult;
            outline(ox + it.x - 5f, oy + it.y - 5f, 10f, 10f, LINE, a);
        }
        if (sim.suitSpot != null && sim.suitAvailable()) {
            // the suit of power armor: a larger bracket with two red lenses showing
            float sx = ox + sim.suitSpot[0];
            float sy = oy + sim.suitSpot[1];
            float near = Math.min(1f, 1.4f - (float) Math.hypot(sim.suitSpot[0] - sim.px, sim.suitSpot[1] - sim.py) / 140f);
            float sa = (0.45f + 0.4f * Math.max(0f, near)) * alphaMult;
            lineWidth(2f);
            outline(sx - 13f, sy - 13f, 26f, 26f, LINE, sa);
            disc(sx - 4.5f, sy + 1.5f, 1.8f, new Color(255, 48, 48), sa);
            disc(sx + 4.5f, sy + 1.5f, 1.8f, new Color(255, 48, 48), sa);
            disc(sx - 4.5f, sy - 3.5f, 1.8f, new Color(255, 48, 48), sa);
            disc(sx + 4.5f, sy - 3.5f, 1.8f, new Color(255, 48, 48), sa);
        }
        for (float[] h : sim.hides) {
            float dx = h[0] - sim.px;
            float dy = h[1] - sim.py;
            boolean near = sim.carrying != null && dx * dx + dy * dy < armaa_stealthSim.HIDE_RANGE * armaa_stealthSim.HIDE_RANGE;
            float a = (near ? 0.95f : 0.4f) * alphaMult;
            outline(ox + h[0] - 7f, oy + h[1] - 7f, 14f, 14f, LINE, a);
            lineWidth(1f);
            prep();
            color(LINE, a * 0.7f);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2f(ox + h[0] - 7f, oy + h[1] - 7f);
            GL11.glVertex2f(ox + h[0] + 7f, oy + h[1] + 7f);
            GL11.glVertex2f(ox + h[0] - 7f, oy + h[1] + 7f);
            GL11.glVertex2f(ox + h[0] + 7f, oy + h[1] - 7f);
            GL11.glEnd();
            endLines();
        }
        if (sim.ventOpen && sim.ventExit != null) {
            outline(ox + sim.ventExit[0] - 8f, oy + sim.ventExit[1] - 8f, 16f, 16f, LINE, 0.5f * alphaMult);
        }
    }

    /** Locked doors in amber over the walls they are part of, and Roland. */
    private void locksAndRoland(float ox, float oy, float alphaMult) {
        for (armaa_stealthSim.Lock l : sim.locks) {
            if (l.open && !l.auto) {
                continue;
            }
            float x0 = ox + l.rect.x;
            float y0 = oy + l.rect.y;
            if (l.open && l.slide > 0.01f) {
                // an opening door: two leaves that slide apart into the wall
                float leaf = (l.slidesAlongY ? l.rect.h : l.rect.w) / 2f * (1f - l.slide);
                if (leaf < 0.5f) {
                    continue;
                }
                float lw = l.slidesAlongY ? l.rect.w : leaf;
                float lh = l.slidesAlongY ? leaf : l.rect.h;
                float bx = l.slidesAlongY ? x0 : x0 + l.rect.w - leaf;
                float by = l.slidesAlongY ? y0 + l.rect.h - leaf : y0;
                fill(x0, y0, lw, lh, SEARCH, 0.35f * alphaMult);
                lineWidth(2f);
                outline(x0, y0, lw, lh, SEARCH, alphaMult);
                fill(bx, by, lw, lh, SEARCH, 0.35f * alphaMult);
                outline(bx, by, lw, lh, SEARCH, alphaMult);
                continue;
            }
            fill(x0, y0, l.rect.w, l.rect.h, SEARCH, 0.35f * alphaMult);
            lineWidth(2f);
            outline(x0, y0, l.rect.w, l.rect.h, SEARCH, alphaMult);
        }
        if (sim.overrideLock != null && sim.overrideProgress > 0f) {
            armaa_stealthSim.Lock ol = sim.overrideLock;
            float frac = Math.min(1f, sim.overrideProgress / sim.overrideNeed());
            float bx = ox + ol.rect.x + ol.rect.w / 2f - 16f;
            float by = oy + ol.rect.y - 10f;
            fill(bx, by, 32f, 4f, Color.black, 0.7f * alphaMult);
            fill(bx, by, 32f * frac, 4f, PLAYER, 0.95f * alphaMult);
        }
        for (armaa_stealthSim.Ally al : sim.allies) {
            if (al.isRoland || !sim.canSee(al.x, al.y)) {
                continue;
            }
            if (al.dead) {
                body(ox + al.x, oy + al.y, alphaMult, downAge(al), al.hitAngle);
                continue;
            }
            float a = (al.freed ? 1f : 0.55f) * alphaMult;
            float[] ll = allyLunge(al);
            GL11.glPushMatrix();
            GL11.glTranslatef(ll[0], ll[1], 0f);
            try {
            SpriteAPI portrait = allyFaces.get(al.id);
            boolean raised = al.flash > 0f || al.burstLeft > 0 || al.aimTime > 0f || al.meleeTime >= 0f || sim.fightStarted;
            float waim = aimBlend(al, raised);
            float wr = al.armored ? ARMOR_ICON_H : (portrait != null ? PORTRAIT_RADIUS : 10f);
            String wk = al.armored ? "heavy" : "rifle";
            float wsc = al.armored ? 1f : 1.15f;
            if (al.armed && weaponBehind(al.facing)) {
                holdWeapon(wk, ox + al.x, oy + al.y, wr, al.facing, waim, al.flash, wsc, a);
            }
            if (al.armored) {
                float[] an = animate(al, al.x, al.y);
                armoredHelmet(ox + al.x, oy + al.y + an[0] * 1.3f, ARMOR_ICON_H, al.facing, a);
                if (al.armed && !weaponBehind(al.facing)) {
                    holdWeapon(wk, ox + al.x, oy + al.y, wr, al.facing, waim, al.flash, wsc, a);
                }
                if (SHOW_SWING_ARC && al.swingTime > 0f) {
                    // the backhand: a quick wedge over the arc it hit
                    float t = al.swingTime / armaa_stealthSim.SUIT_SWING_SHOW;
                    prep();
                    GL11.glBegin(GL11.GL_TRIANGLE_FAN);
                    color(LINE, 0.15f * t * alphaMult);
                    GL11.glVertex2f(ox + al.x, oy + al.y);
                    color(LINE, 0.8f * t * alphaMult);
                    for (int i = -6; i <= 6; i++) {
                        double sa = Math.toRadians(al.swingAngle + i * (armaa_stealthSim.SUIT_ARC_DEG / 6f));
                        GL11.glVertex2f(ox + al.x + (float) Math.cos(sa) * armaa_stealthSim.SUIT_REACH, oy + al.y + (float) Math.sin(sa) * armaa_stealthSim.SUIT_REACH);
                    }
                    GL11.glEnd();
                    GL11.glEnable(GL11.GL_TEXTURE_2D);
                }
                continue;
            }
            if (portrait != null) {
                person(ox + al.x, oy + al.y, portrait, CONE, a, 1f);
                if (al.armed && !weaponBehind(al.facing)) {
                    holdWeapon(wk, ox + al.x, oy + al.y, wr, al.facing, waim, al.flash, wsc, a);
                }
                continue;
            }
            float gr = al.type == 0 ? armaa_stealthSim.PLAYER_RADIUS : 10f;                 // a marine or bodyguard in plain kit is a little bigger
            disc(ox + al.x, oy + al.y, gr + 3f, FIELD, a);
            disc(ox + al.x, oy + al.y, gr, CONE, 0.55f * a);
            circleLine(ox + al.x, oy + al.y, gr + 1f, CONE, a, 2f);
            if (speechFont != null) {
                org.lazywizard.lazylib.ui.LazyFont.DrawableString ls = speechFont.createText(al.name.substring(0, 1), PLAIN_TEXT, 14f);
                ls.setAnchor(org.lazywizard.lazylib.ui.LazyFont.TextAnchor.CENTER);
                ls.setBaseColor(new Color(PLAIN_TEXT.getRed(), PLAIN_TEXT.getGreen(), PLAIN_TEXT.getBlue(), (int) (255f * a)));
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                ls.draw(ox + al.x, oy + al.y);
            }
            if (al.armed && !weaponBehind(al.facing)) {
                holdWeapon(wk, ox + al.x, oy + al.y, wr, al.facing, waim, al.flash, wsc, a);
            }
            } finally {
                GL11.glPopMatrix();
            }
        }
        allyFx(ox, oy, alphaMult);
        if (sim.hasRoland) {
            float[] ran = animate(rolandFace == null ? CONE : rolandFace, sim.rolandX, sim.rolandY);
            armaa_stealthSim.Ally ra = sim.rolandAlly;
            boolean rArmed = ra != null && ra.armed;
            float rAim = ra == null ? 0f : aimBlend(ra, ra.flash > 0f || ra.burstLeft > 0 || ra.aimTime > 0f || sim.fightStarted);
            if (rArmed && weaponBehind(ra.facing)) {
                holdWeapon("rifle", ox + sim.rolandX, oy + sim.rolandY, PORTRAIT_RADIUS, ra.facing, rAim, ra.flash, 1.15f, alphaMult);
            }
            person(ox + sim.rolandX, oy + sim.rolandY + ran[0], rolandFace, CONE, alphaMult, ran[1]);
            if (rArmed && !weaponBehind(ra.facing)) {
                holdWeapon("rifle", ox + sim.rolandX, oy + sim.rolandY, PORTRAIT_RADIUS, ra.facing, rAim, ra.flash, 1.15f, alphaMult);
            }
        }
    }

    private final java.util.Map<Object, float[]> lastSeen = new java.util.HashMap<Object, float[]>();

    /** {vertical offset, size factor} for an actor: breathing while still, a small bob while moving. */
    private float[] animate(Object who, float x, float y) {
        float[] prev = lastSeen.get(who);
        boolean moving = prev != null && (Math.abs(prev[0] - x) + Math.abs(prev[1] - y)) > 0.05f;
        lastSeen.put(who, new float[]{x, y});
        float phase = (who.hashCode() & 0xff) * 0.1f;
        if (moving) {
            return new float[]{(float) Math.sin(clock * 12f + phase) * 1.2f, 1f};
        }
        return new float[]{0f, 1f + 0.06f * (float) Math.sin(clock * 2.4f + phase)};
    }

    private static Color mix(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    /**
     * A person: their portrait in a circle if we have one, else a plain disc,
     * with a ring in their colour. The portrait is a square sprite pushed down a
     * little so the face sits in the middle, and the corners round the circle
     * are painted over with the field colour.
     */
    private void person(float cx, float cy, SpriteAPI face, Color rim, float alphaMult, float scale) {
        float r = (face == null ? armaa_stealthSim.PLAYER_RADIUS : PORTRAIT_RADIUS) * scale;
        disc(cx, cy, r + 3f, rim, 0.25f * alphaMult);
        if (face == null) {
            disc(cx, cy, r, PLAYER, alphaMult);
            circleLine(cx, cy, r + 1f, rim, alphaMult, 2f);
            return;
        }
        // a textured disc: the portrait's centre region mapped onto a triangle fan, so nothing
        // outside the circle is drawn. The face sits a little above the image's centre.
        float tx = face.getTexX();
        float ty = face.getTexY();
        float tw = face.getTexWidth();
        float th = face.getTexHeight();
        float faceV = 0.5f + FACE_LIFT;
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        face.bindTexture();
        boolean shaded = beginTint(alphaMult);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glTexCoord2f(tx + tw * 0.5f, ty + th * faceV);
        GL11.glVertex2f(cx, cy);
        int steps = 36;
        for (int i = 0; i <= steps; i++) {
            double a = Math.PI * 2.0 * i / steps;
            float c = (float) Math.cos(a);
            float sn = (float) Math.sin(a);
            GL11.glTexCoord2f(tx + tw * (0.5f + FACE_SPAN * c), ty + th * (faceV + FACE_SPAN * sn * FLIP));
            GL11.glVertex2f(cx + c * r, cy + sn * r);
        }
        GL11.glEnd();
        if (shaded) {
            GL20.glUseProgram(0);
        }
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        circleLine(cx, cy, r, rim, alphaMult, 2.5f);
    }

    // ---- the radar tint for portraits ----
    private static final String TINT_VERTEX =
            "void main() { gl_Position = ftransform(); gl_TexCoord[0] = gl_MultiTexCoord0; }";
    /** Luminance of the portrait, mapped from the field colour up to the line colour. */
    private static final String TINT_FRAGMENT =
            "uniform sampler2D tex;\n"
            + "uniform vec3 lo;\n"
            + "uniform vec3 hi;\n"
            + "uniform float alpha;\n"
            + "void main() {\n"
            + "  vec4 t = texture2D(tex, gl_TexCoord[0].st);\n"
            + "  float l = dot(t.rgb, vec3(0.299, 0.587, 0.114));\n"
            + "  l = clamp((l - 0.05) * 1.25, 0.0, 1.0);\n"
            + "  gl_FragColor = vec4(mix(lo, hi, l), t.a * alpha);\n"
            + "}\n";
    /** 0 = not tried yet, -1 = failed (use the plain tint), else the program id. */
    private static int tintProgram = 0;

    /**
     * Binds the tint shader and sets it up for one draw. Returns false if the
     * shader is unavailable, in which case a flat green modulation is used instead.
     */
    private static boolean beginTint(float alpha) {
        if (tintProgram == 0) {
            tintProgram = buildTint();
        }
        if (tintProgram < 0) {
            GL11.glColor4f(0.55f, 1f, 0.45f, alpha);
            return false;
        }
        GL20.glUseProgram(tintProgram);
        GL20.glUniform1i(GL20.glGetUniformLocation(tintProgram, "tex"), 0);
        GL20.glUniform3f(GL20.glGetUniformLocation(tintProgram, "lo"), FIELD.getRed() / 255f, FIELD.getGreen() / 255f, FIELD.getBlue() / 255f);
        GL20.glUniform3f(GL20.glGetUniformLocation(tintProgram, "hi"), LINE.getRed() / 255f, LINE.getGreen() / 255f, LINE.getBlue() / 255f);
        GL20.glUniform1f(GL20.glGetUniformLocation(tintProgram, "alpha"), alpha);
        return true;
    }

    private static int buildTint() {
        try {
            int vs = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
            GL20.glShaderSource(vs, TINT_VERTEX);
            GL20.glCompileShader(vs);
            int fs = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
            GL20.glShaderSource(fs, TINT_FRAGMENT);
            GL20.glCompileShader(fs);
            int prog = GL20.glCreateProgram();
            GL20.glAttachShader(prog, vs);
            GL20.glAttachShader(prog, fs);
            GL20.glLinkProgram(prog);
            if (GL20.glGetProgrami(prog, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                Global.getLogger(armaa_stealthPanel.class).warn("portrait tint shader failed: "
                        + GL20.glGetProgramInfoLog(prog, 1024));
                return -1;
            }
            return prog;
        } catch (Throwable t) {
            Global.getLogger(armaa_stealthPanel.class).warn("portrait tint shader unavailable", t);
            return -1;
        }
    }

    private static void circleLine(float cx, float cy, float r, Color c, float alpha, float width) {
        lineWidth(width);
        prep();
        color(c, alpha);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < 28; i++) {
            double a = Math.PI * 2.0 * i / 28;
            GL11.glVertex2f(cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r);
        }
        GL11.glEnd();
        endLines();
    }

    /**
     * A knocked-out guard: a dulled, slightly smaller version of his own icon, with yellow stars circling over his head.
     * There is one star for each fifth of the time left before he wakes, and the last one fades out as it goes.
     * On going down he drops to that size over FALL_TIME (a dip as he lands), the red draining out of him, sliding a little the way the blow went.
     */
    private void knockedOut(float cx, float cy, Guard g, float alphaMult) {
        float u = Math.min(1f, downAge(g) / FALL_TIME);
        float e = 1f - (float) Math.pow(1f - u, 3.0);
        float r = armaa_stealthSim.GUARD_RADIUS * (0.8f + 0.2f * (1f - e) - 0.1f * (float) Math.sin(Math.PI * u));
        float sx = cx - (float) Math.cos(g.hitAngle) * DOWN_SLIDE * e;
        float sy = cy - (float) Math.sin(g.hitAngle) * DOWN_SLIDE * e;
        disc(sx, sy, r + 2f, FIELD, (1f - 0.4f * e) * alphaMult);
        disc(sx, sy, r, GUARD, (1f - 0.5f * e) * alphaMult);
        circleLine(sx, sy, r + 1f, guardRim, (1f - 0.6f * e) * alphaMult, 1.5f);
        float starIn = Math.max(0f, Math.min(1f, (u - 0.6f) / 0.4f));
        float left = Math.max(0f, Math.min(1f, g.koTimer / armaa_stealthSim.KO_WAKE_TIME)) * 5f;
        int stars = (int) Math.ceil(left);
        float lastFade = left - (float) Math.floor(left);          // the newest-to-go star dims as its fifth runs out
        for (int i = 0; i < stars; i++) {
            double an = clock * 2.2 + i * (Math.PI * 2.0 / 5.0);
            float stx = sx + (float) Math.cos(an) * 9f;
            float sty = sy + armaa_stealthSim.GUARD_RADIUS + 5f + (float) Math.sin(an) * 3.5f;
            float alpha = (i == stars - 1 && lastFade > 0f) ? lastFade : 1f;
            star(stx, sty, 3.2f, new Color(255, 225, 60), alpha * starIn * alphaMult);
        }
    }

    /** A small five-pointed star. */
    private static void star(float cx, float cy, float r, Color c, float alpha) {
        prep();
        color(c, alpha);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(cx, cy);
        for (int i = 0; i <= 10; i++) {
            double a = Math.PI / 2.0 + i * Math.PI / 5.0;
            float rr = (i % 2 == 0) ? r : r * 0.45f;
            GL11.glVertex2f(cx + (float) Math.cos(a) * rr, cy + (float) Math.sin(a) * rr);
        }
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    /**
     * A guard that has been shot: a dim disc with a cross through it. He goes down over KILL_TIME: a flash, the red fading to grey as he shrinks and
     * slides a little the way the blow went, and the cross drawn in stroke by stroke at the end.
     */
    private void body(float cx, float cy, float alphaMult, float age, float hitAngle) {
        float u = Math.min(1f, age / KILL_TIME);
        float e = 1f - (float) Math.pow(1f - u, 3.0);
        float sx = cx - (float) Math.cos(hitAngle) * DOWN_SLIDE * e;
        float sy = cy - (float) Math.sin(hitAngle) * DOWN_SLIDE * e;
        float r = armaa_stealthSim.GUARD_RADIUS * (1f - 0.1f * e);
        Color c = new Color((int) (GUARD.getRed() + (DEAD.getRed() - GUARD.getRed()) * e), (int) (GUARD.getGreen() + (DEAD.getGreen() - GUARD.getGreen()) * e),
                (int) (GUARD.getBlue() + (DEAD.getBlue() - GUARD.getBlue()) * e));
        disc(sx, sy, r, c, (1f - 0.45f * e) * alphaMult);
        if (age < 0.07f) {
            disc(sx, sy, armaa_stealthSim.GUARD_RADIUS, Color.white, 0.8f * (1f - age / 0.07f) * alphaMult);
        }
        float xu = Math.max(0f, Math.min(1f, (age - KILL_TIME * 0.55f) / (KILL_TIME * 0.6f)));
        if (xu > 0f) {
            float s1 = Math.min(1f, xu * 2f);
            float s2 = Math.max(0f, xu * 2f - 1f);
            lineWidth(2f);
            prep();
            color(FIELD, 0.9f * alphaMult);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2f(sx - 4f, sy - 4f);
            GL11.glVertex2f(sx - 4f + 8f * s1, sy - 4f + 8f * s1);
            if (s2 > 0f) {
                GL11.glVertex2f(sx - 4f, sy + 4f);
                GL11.glVertex2f(sx - 4f + 8f * s2, sy + 4f - 8f * s2);
            }
            GL11.glEnd();
            endLines();
        }
    }

    /** The line of a shot that has just been fired, and a flash where it stopped. */
    private void tracers(float ox, float oy, float alphaMult) {
        for (Shot sh : sim.shots) {
            float a = 1f - sh.age / armaa_stealthSim.SHOT_LIFE;
            lineWidth(2f);
            prep();
            GL11.glBegin(GL11.GL_LINES);
            color(TRACER, 0.9f * a * alphaMult);
            GL11.glVertex2f(ox + sh.x0, oy + sh.y0);
            color(TRACER, 0.25f * a * alphaMult);
            GL11.glVertex2f(ox + sh.x1, oy + sh.y1);
            GL11.glEnd();
            endLines();
            disc(ox + sh.x1, oy + sh.y1, sh.hitGuard ? 6f : 3f, sh.hitGuard ? ALERT : TRACER, 0.8f * a * alphaMult);
        }
        for (armaa_stealthSim.Bullet bl : sim.bullets) {
            lineWidth(2f);
            prep();
            GL11.glBegin(GL11.GL_LINES);
            Color bc = bl.fromPlayer ? TRACER : ALERT;
            color(bc, 0.1f * alphaMult);
            GL11.glVertex2f(ox + bl.x - bl.dx * 22f, oy + bl.y - bl.dy * 22f);
            color(bc, alphaMult);
            GL11.glVertex2f(ox + bl.x, oy + bl.y);
            GL11.glEnd();
            endLines();
            disc(ox + bl.x, oy + bl.y, 2.5f, bl.fromPlayer ? TRACER : ALERT, alphaMult);
        }
    }

    // ---- screen effects (not moved by the camera) ----
    /** The radar sweep: a trail fading out behind a bright line, drawn over everything in the room. */
    private void sweep(float vx, float vy, float alphaMult) {
        float h = armaa_stealthSim.VIEW_H;
        float sx = sweepX();
        float env = sweepEnv();
        float trail = 150f;
        float left = Math.max(0f, sx - trail);
        prep();
        GL11.glBegin(GL11.GL_QUADS);
        color(LINE, 0f);
        GL11.glVertex2f(vx + left, vy);
        GL11.glVertex2f(vx + left, vy + h);
        color(LINE, 0.10f * env * alphaMult);
        GL11.glVertex2f(vx + sx, vy + h);
        GL11.glVertex2f(vx + sx, vy);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        lineWidth(2f);
        prep();
        color(LINE, 0.45f * env * alphaMult);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(vx + sx, vy);
        GL11.glVertex2f(vx + sx, vy + h);
        GL11.glEnd();
        endLines();
    }

    /** Once the guards know the player kills: a pulsing red warning triangle with a bang, top right. */
    private void weaponsFree(float vx, float vy, float alphaMult) {
        if (!sim.lethalKnown) {
            return;
        }
        float pulse = 0.65f + 0.35f * (float) Math.sin(clock * 6f);
        float cx = vx + armaa_stealthSim.VIEW_W - 24f;
        float cy = vy + armaa_stealthSim.VIEW_H - 26f;
        prep();
        GL11.glBegin(GL11.GL_TRIANGLES);
        color(ALERT, 0.25f * pulse * alphaMult);
        GL11.glVertex2f(cx - 14f, cy - 11f);
        GL11.glVertex2f(cx + 14f, cy - 11f);
        GL11.glVertex2f(cx, cy + 13f);
        GL11.glEnd();
        lineWidth(2f);
        prep();
        color(ALERT, pulse * alphaMult);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2f(cx - 14f, cy - 11f);
        GL11.glVertex2f(cx + 14f, cy - 11f);
        GL11.glVertex2f(cx, cy + 13f);
        GL11.glEnd();
        endLines();
        exclaim(cx, cy - 8f, ALERT, 0.8f, pulse * alphaMult);
    }

    /**
     * What characters have just said, in small boxes over their heads. Boxes never sit on top of each other: they are placed
     * oldest first, and a newer one that would overlap is lifted above the ones already there. A lifted box is tied to its speaker
     * by a faint wedge, so it is still clear who is talking.
     */
    private void speechBubbles(float vx, float vy, float alphaMult) {
        if (speechFont == null || sim.speeches.isEmpty()) {
            return;
        }
        float ox = vx - cam.x + shakeX;
        float oy = vy - cam.y + shakeY;
        java.util.List<float[]> placed = new java.util.ArrayList<float[]>();          // x, y, w, h of boxes already down
        if (ikBox != null) {
            placed.add(ikBox);                                  // his words come first: anyone whose box would overlap sits on top, with the wedge to the speaker
        }
        for (armaa_stealthSim.Speech sp : sim.speeches) {
            float x, headY;
            float vis = 1f;
            if (sp.ally != null) {
                x = sp.ally.x;
                headY = sp.ally.y + PORTRAIT_RADIUS + 4f;
            } else if (sp.who == null) {
                x = sim.rolandX;
                headY = sim.rolandY + armaa_stealthSim.PLAYER_RADIUS + 4f;
            } else {
                x = sp.who.x;
                headY = sp.who.y + armaa_stealthSim.GUARD_RADIUS + 3f;
                vis = sp.who.shown;
            }
            String heard = sim.heardText(sp);                  // what can be made out from here; null if nothing
            if (heard == null) {
                continue;
            }
            // a speaker out of sight but within earshot still shows (his words fade in with the distance); one in sight fades with the fog
            float fade = Math.min(1f, Math.min(sp.age / 0.12f, (sp.life - sp.age) / 0.4f)) * (sim.canSee(x, headY) ? Math.max(vis, 0.3f) : 1f);
            org.lazywizard.lazylib.ui.LazyFont.DrawableString ds = speechFont.createText(heard, sp.who == null ? PLAIN_TEXT : enemyText, SPEECH_FONT_SIZE, 300f);
            ds.setAnchor(org.lazywizard.lazylib.ui.LazyFont.TextAnchor.BOTTOM_CENTER);
            float w = ds.getWidth() + 8f;
            float h = ds.getHeight() + 6f;
            float bx = x - w / 2f;
            float by = headY + 26f;                             // clear of the "!" or "?" over the head
            for (int guard = 0; guard < 12; guard++) {
                boolean moved = false;
                for (float[] r : placed) {
                    if (bx < r[0] + r[2] + 2f && bx + w > r[0] - 2f && by < r[1] + r[3] + 3f && by + h > r[1] - 3f) {
                        by = r[1] + r[3] + 4f;                  // sit on top of the one in the way
                        moved = true;
                    }
                }
                if (!moved) {
                    break;
                }
            }
            placed.add(new float[]{bx, by, w, h});
            float a = fade * alphaMult;
            if (by > headY + 30f) {
                // lifted: a faint wedge from the speaker's head up to the foot of his box
                float tipX = ox + x;
                float tipY = oy + headY + 18f;
                float footX = Math.max(bx + 6f, Math.min(bx + w - 6f, x));
                prep();
                GL11.glBegin(GL11.GL_TRIANGLES);
                color(LINE, 0.22f * a);
                GL11.glVertex2f(tipX, tipY);
                GL11.glVertex2f(ox + footX - 6f, oy + by);
                GL11.glVertex2f(ox + footX + 6f, oy + by);
                GL11.glEnd();
                lineWidth(1f);
                prep();
                GL11.glBegin(GL11.GL_LINES);
                color(LINE, 0.55f * a);
                GL11.glVertex2f(tipX, tipY);
                GL11.glVertex2f(ox + footX, oy + by);
                GL11.glEnd();
                endLines();
            }
            fill(ox + bx, oy + by, w, h, FIELD, 0.85f * a);
            lineWidth(1f);
            outline(ox + bx, oy + by, w, h, LINE, 0.6f * a);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            Color tc = sp.who == null ? PLAIN_TEXT : enemyText;
            ds.setBaseColor(new Color(tc.getRed(), tc.getGreen(), tc.getBlue(), Math.max(0, Math.min(255, (int) (255f * a)))));
            ds.draw(ox + x, oy + by + 3f);
        }
    }

    /** While the station is on caution: a faint amber edge and a bar along the top that runs down with the timer. */
    private void cautionBar(float vx, float vy, float alphaMult) {
        float b = sim.cautionBlend;
        if (b <= 0.01f) {
            return;
        }
        float w = armaa_stealthSim.VIEW_W;
        float h = armaa_stealthSim.VIEW_H;
        float pulse = 0.7f + 0.3f * (float) Math.sin(clock * (sim.cautionHeld ? 14f : 5f));   // quicker while it is being held
        float frac = sim.cautionMax > 0f ? Math.min(1f, sim.cautionLeft / sim.cautionMax) : 0f;
        fill(vx, vy + h - 4f, w * frac, 4f, SEARCH, 0.85f * pulse * b * alphaMult);
        fill(vx, vy, 3f, h, SEARCH, 0.25f * b * alphaMult);
        fill(vx + w - 3f, vy, 3f, h, SEARCH, 0.25f * b * alphaMult);
        fill(vx, vy, w, 3f, SEARCH, 0.25f * b * alphaMult);
    }

    private void alertFlash(float vx, float vy, float alphaMult) {
        float f = sim.alertFlash;
        if (f <= 0.01f) {
            return;
        }
        float w = armaa_stealthSim.VIEW_W;
        float h = armaa_stealthSim.VIEW_H;
        fill(vx, vy, w, h, ALERT, 0.16f * f * alphaMult);
        // a few bars of static
        for (int i = 0; i < 6; i++) {
            float y = vy + (float) Math.random() * h;
            float bh = 1f + (float) Math.random() * 3f;
            fill(vx, y, w, bh, LINE, (0.10f + 0.12f * (float) Math.random()) * f * alphaMult);
        }
    }

    /** One small square per distraction left, and (once you have the sidearm) a red one per round. */
    /** Where a guard is drawn through a swing: leaning back as he winds up, then forward along his facing. */
    private float[] guardLunge(Guard g) {
        float off = 0f;
        if (g.lungeTime > 0f) {
            off = armaa_stealthSim.strikeOffset(1f - g.lungeTime / armaa_stealthSim.LUNGE_TIME, armaa_stealthSim.LUNGE_LEAN, armaa_stealthSim.LUNGE_REACH);
        } else if (g.state == GuardState.ALERT && g.meleeTime > 0f) {
            off = armaa_stealthSim.windupOffset(g.meleeTime / armaa_stealthSim.MELEE_WINDUP, armaa_stealthSim.LUNGE_LEAN);
        } else if (g.allyMeleeTime >= 0f) {
            off = armaa_stealthSim.windupOffset(1f - g.allyMeleeTime / armaa_stealthSim.MELEE_WINDUP, armaa_stealthSim.LUNGE_LEAN);
        }
        double f = Math.toRadians(g.facing);
        return new float[]{(float) Math.cos(f) * off, (float) Math.sin(f) * off};
    }

    private float[] allyLunge(armaa_stealthSim.Ally al) {
        float lean = al.armored ? armaa_stealthSim.LUNGE_LEAN_SUIT : armaa_stealthSim.LUNGE_LEAN_FIST;
        float reach = al.armored ? armaa_stealthSim.LUNGE_REACH_SUIT : armaa_stealthSim.LUNGE_REACH;
        float off = 0f;
        double dir = Math.toRadians(al.facing);
        if (al.swingTime > 0f) {
            float show = al.armored ? armaa_stealthSim.SUIT_SWING_SHOW : armaa_stealthSim.ALLY_SWING_SHOW;
            off = armaa_stealthSim.strikeOffset(1f - al.swingTime / show, lean, reach);
            dir = Math.toRadians(al.swingAngle);
        } else if (al.meleeTime >= 0f) {
            float total = al.armored ? armaa_stealthSim.SUIT_WINDUP : armaa_stealthSim.ALLY_MELEE_WINDUP;
            off = armaa_stealthSim.windupOffset(1f - al.meleeTime / total, lean);
        }
        return new float[]{(float) Math.cos(dir) * off, (float) Math.sin(dir) * off};
    }

    /** Dust kicked up by blows and skids, under the characters. */
    private void puffs(float ox, float oy, float alphaMult) {
        for (armaa_stealthSim.Puff p : sim.puffs) {
            if (!sim.canSee(p.x, p.y)) {
                continue;
            }
            float u = Math.min(1f, p.age / p.life);
            disc(ox + p.x, oy + p.y, p.r0 + (p.r1 - p.r0) * u, DUST, p.alpha * (1f - u) * (1f - u) * alphaMult);
        }
    }

    /** The short lines a blow throws off: white for a hit, yellow for one turned aside. */
    private void sparks(float ox, float oy, float alphaMult) {
        for (armaa_stealthSim.Spark k : sim.sparks) {
            if (!sim.canSee(k.x, k.y)) {
                continue;
            }
            float u = Math.min(1f, k.age / k.life);
            float r0 = 1f + 4f * u;
            float r1 = r0 + k.len * 0.5f;
            lineWidth(2f);
            prep();
            color(k.block ? SPARK_BLOCK : Color.white, (1f - u) * alphaMult);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2f(ox + k.x + (float) Math.cos(k.angle) * r0, oy + k.y + (float) Math.sin(k.angle) * r0);
            GL11.glVertex2f(ox + k.x + (float) Math.cos(k.angle) * r1, oy + k.y + (float) Math.sin(k.angle) * r1);
            GL11.glEnd();
            endLines();
        }
    }

    /** The text cut into lines no wider than maxW. */
    private java.util.List<String> wrap(String text, float maxW) {
        java.util.List<String> lines = new java.util.ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String tryLine = cur.length() == 0 ? word : cur + " " + word;
            if (cur.length() > 0 && hudTextWidth(tryLine, SPEECH_FONT_SIZE) > maxW) {
                lines.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(tryLine);
            }
        }
        if (cur.length() > 0) {
            lines.add(cur.toString());
        }
        return lines;
    }

    /** A circle drawn as dashes. */
    private static void dashedCircle(float cx, float cy, float r, Color c, float alpha, float width) {
        lineWidth(width);
        prep();
        color(c, alpha);
        GL11.glBegin(GL11.GL_LINES);
        int dashes = 14;
        for (int i = 0; i < dashes; i++) {
            double a0 = i * Math.PI * 2.0 / dashes;
            double a1 = a0 + Math.PI * 2.0 / dashes * 0.55;
            GL11.glVertex2f(cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r);
            GL11.glVertex2f(cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r);
        }
        GL11.glEnd();
        endLines();
    }

    /**
     * The drone at the player's shoulder and, while a hint is up, IRON KING's emblem projected over it: his eye turns to what he is talking
     * about (only if the player can see it), a ring marks it, and the words sit above him.
     */
    /** Where IRON KING's box was this frame (x, y, w, h in world units), so other speakers' boxes are lifted clear of it; null when he is silent. */
    private float[] ikBox;

    private void droneAndHint(float ox, float oy, float alphaMult) {
        armaa_stealthSim.Drone d = sim.drone;
        ikBox = null;
        if (!d.placed || sim.status != Status.RUNNING) {
            return;
        }
        armaa_stealthHints.View hv = sim.hints.view(sim);
        float lookX = 0f;
        float lookY = 0f;
        if (hv != null && hv.hasTarget) {
            lookX = hv.tx - d.x;
            lookY = hv.ty - d.y;
        }
        armaa_stealthHologram.lineScale = lineScale;
        holo.drone(ox, oy, d, clock, alphaMult, lookX, lookY, hv != null);
        if (hv == null) {
            return;
        }
        holo.update(frameDt);
        if (holo.takeBurst()) {
            sounds.play(sim, "ikstatic", Float.NaN, 0f);        // the static plays with the glitch, not with every line
        }
        float fade = hv.fade() * alphaMult;
        float R = armaa_stealthHologram.EMBLEM_R;
        float hx = Math.max(cam.x + 110f, Math.min(cam.x + armaa_stealthSim.VIEW_W - 110f, d.x));
        float hy = Math.min(d.y + 14f + 3.3f * R, cam.y + armaa_stealthSim.VIEW_H - 2.5f * R - 62f);       // above the drone (66 at the original radius of 16)
        float ex = (float) Math.cos(Math.toRadians(d.face)) * 0.4f;
        float ey = (float) Math.sin(Math.toRadians(d.face)) * 0.4f;
        if (hv.hasTarget) {
            float dx = hv.tx - hx;
            float dy = hv.ty - hy;
            float len = Math.max(1f, (float) Math.hypot(dx, dy));
            ex = dx / len;
            ey = dy / len;
        }
        float p = clock % 4.2f;
        float open = p < 0.2f ? Math.abs(p - 0.1f) / 0.1f * 0.85f + 0.15f : 1f;
        // the beam: a cone from the drone's lens up to the emblem, filled and fading to nothing, flickering, with bands rising through it
        float bx0 = ox + d.x;
        float by0 = oy + d.y + 5f;
        float bx1 = ox + hx;
        float by1 = oy + hy - 1.3f * R;
        float flick = 0.8f + 0.2f * (float) (Math.sin(clock * 37.0) * Math.sin(clock * 11.3));
        if (((int) (clock * 20f) * 2654435761L >>> 7) % 23 == 0) {
            flick *= 0.4f;
        }
        float bw0 = 3f;
        float bw1 = 14f;
        prep();
        GL11.glBegin(GL11.GL_QUADS);
        color(HOLO_BEAM, 0.5f * flick * fade);
        GL11.glVertex2f(bx0 - bw0, by0);
        GL11.glVertex2f(bx0 + bw0, by0);
        color(HOLO_BEAM, 0f);
        GL11.glVertex2f(bx1 + bw1, by1);
        GL11.glVertex2f(bx1 - bw1, by1);
        GL11.glEnd();
        lineWidth(1f);
        GL11.glBegin(GL11.GL_LINES);
        color(HOLO_BEAM, 0.7f * flick * fade);
        GL11.glVertex2f(bx0 - bw0, by0);
        color(HOLO_BEAM, 0f);
        GL11.glVertex2f(bx1 - bw1, by1);
        color(HOLO_BEAM, 0.7f * flick * fade);
        GL11.glVertex2f(bx0 + bw0, by0);
        color(HOLO_BEAM, 0f);
        GL11.glVertex2f(bx1 + bw1, by1);
        for (int k = 0; k < 4; k++) {
            float u = (clock * 0.5f + k / 4f) % 1f;
            float cxk = bx0 + (bx1 - bx0) * u;
            float cyk = by0 + (by1 - by0) * u;
            float hw = bw0 + (bw1 - bw0) * u;
            color(HOLO_BEAM, 0.35f * (1f - u) * flick * fade);
            GL11.glVertex2f(cxk - hw, cyk);
            GL11.glVertex2f(cxk + hw, cyk);
        }
        GL11.glEnd();
        endLines();
        holo.hologram(ox + hx, oy + hy, R, fade, ex, ey, open);
        Color highlight = Misc.getHighlightColor();
        java.util.List<String> lines = wrap(hv.text, 300f);
        float lineH = SPEECH_FONT_SIZE + 4f;
        float boxW = 16f;
        for (String ln : lines) {
            boxW = Math.max(boxW, hudTextWidth(ln, SPEECH_FONT_SIZE) + 16f);
        }
        boxW = Math.min(boxW, armaa_stealthSim.VIEW_W - 16f);
        float boxH = lines.size() * lineH + 8f;
        float bx = Math.max(cam.x + 8f, Math.min(cam.x + armaa_stealthSim.VIEW_W - boxW - 8f, hx - boxW / 2f));
        float by = hy + 2.6f * R + 6f;
        float top = cam.y + armaa_stealthSim.VIEW_H - 8f;
        if (by + boxH > top) {
            // no room above him: under the drone if there is room there, else as high as the window allows
            float below = d.y - 16f - boxH;
            by = below >= cam.y + 8f ? below : Math.max(cam.y + 8f, top - boxH);
        }
        ikBox = new float[]{bx, by, boxW, boxH};
        fill(ox + bx, oy + by, boxW, boxH, FIELD, 0.88f * fade);
        lineWidth(1f);
        outline(ox + bx, oy + by, boxW, boxH, LINE, 0.6f * fade);            // the same box as everyone else's
        int typed = armaa_stealthSounds.typed(hv);                                // his words come out as typed, with the box already its full size
        for (int i = 0; i < lines.size(); i++) {
            String ln = lines.get(i);
            int shown = Math.min(ln.length(), typed);
            typed -= shown;
            if (shown > 0) {
                hudText(ln.substring(0, shown), ox + bx + 8f, oy + by + boxH - 4f - lineH / 2f - i * lineH, SPEECH_FONT_SIZE, PLAIN_TEXT, fade, org.lazywizard.lazylib.ui.LazyFont.TextAnchor.CENTER_LEFT);
            }
        }
        if (hv.hasTarget) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(clock * 5f);
            dashedCircle(ox + hv.tx, oy + hv.ty, 18f + 3f * pulse, highlight, (0.5f + 0.4f * pulse) * fade, 2f);
        }
    }

    /** Floating words over a blow: damage in red, "Blocked" plain, "Broken!" in the highlight colour, "No effect" grey. */
    private void popups(float ox, float oy, float alphaMult) {
        for (armaa_stealthSim.Popup p : sim.popups) {
            if (!sim.canSee(p.x, p.y)) {
                continue;
            }
            float t = Math.min(1f, p.age / armaa_stealthSim.POP_LIFE);
            float rise = 26f * (1f - (1f - t) * (1f - t));
            float fade = Math.min(1f, (1f - t) / 0.35f);
            float size = SPEECH_FONT_SIZE * (t < 0.12f ? 1f + (0.12f - t) * 4f : 1f);
            Color c;
            switch (p.kind) {
                case armaa_stealthSim.POP_BLOCK:
                    c = PLAIN_TEXT;
                    break;
                case armaa_stealthSim.POP_BREAK:
                    c = LINE;
                    break;
                case armaa_stealthSim.POP_IMMUNE:
                    c = new Color(170, 180, 190);
                    break;
                case armaa_stealthSim.POP_KO:
                    c = SEARCH;
                    break;
                default:
                    c = ALERT;
                    break;
            }
            hudText(p.text, ox + p.x + p.dx, oy + p.y + 16f + rise, size, c, fade * alphaMult, org.lazywizard.lazylib.ui.LazyFont.TextAnchor.BOTTOM_CENTER);
        }
    }

    /** A freed prisoner's blow: the same wedge as the player's; and the flash of a hit and the stars of a broken guard over them. */
    private void allyFx(float ox, float oy, float alphaMult) {
        for (armaa_stealthSim.Ally al : sim.allies) {
            if (al.dead || al.isRoland && !al.freed || !sim.canSee(al.x, al.y)) {
                continue;
            }
            float r = sim.allyRadius(al);
            if (SHOW_SWING_ARC && al.swingTime > 0f && !al.armored) {
                float t = al.swingTime / armaa_stealthSim.ALLY_SWING_SHOW;
                prep();
                GL11.glBegin(GL11.GL_TRIANGLE_FAN);
                color(CONE, 0.15f * t * alphaMult);
                GL11.glVertex2f(ox + al.x, oy + al.y);
                color(CONE, 0.8f * t * alphaMult);
                for (int i = -5; i <= 5; i++) {
                    double sa = Math.toRadians(al.swingAngle + i * (armaa_stealthSim.PLAYER_MELEE_ARC_DEG / 5f));
                    GL11.glVertex2f(ox + al.x + (float) Math.cos(sa) * armaa_stealthSim.ALLY_MELEE_REACH, oy + al.y + (float) Math.sin(sa) * armaa_stealthSim.ALLY_MELEE_REACH);
                }
                GL11.glEnd();
                GL11.glEnable(GL11.GL_TEXTURE_2D);
            }
            if (al.hitTime > 0f) {
                float ht = al.hitTime / armaa_stealthSim.HIT_FLASH;
                disc(ox + al.x, oy + al.y, r, Color.white, 0.65f * ht * alphaMult);
                lineWidth(2f);
                prep();
                color(Color.white, 0.9f * ht * alphaMult);
                GL11.glBegin(GL11.GL_LINES);
                for (int i = -1; i <= 1; i++) {
                    double sa = al.hitAngle + i * 0.45;
                    GL11.glVertex2f(ox + al.x + (float) Math.cos(sa) * (r + 1f), oy + al.y + (float) Math.sin(sa) * (r + 1f));
                    GL11.glVertex2f(ox + al.x + (float) Math.cos(sa) * (r + 5f + 7f * (1f - ht)), oy + al.y + (float) Math.sin(sa) * (r + 5f + 7f * (1f - ht)));
                }
                GL11.glEnd();
                endLines();
            }
            if (al.brokenTime > 0f) {
                for (int i = 0; i < 2; i++) {
                    double sa = clock * 4.0 + i * Math.PI;
                    star(ox + al.x + (float) Math.cos(sa) * 9f, oy + al.y + r + 6f + (float) Math.sin(sa) * 3f, 3.2f, new Color(255, 225, 60), Math.min(1f, al.brokenTime * 3f) * alphaMult);
                }
            }
            int allyMax = al.armored ? armaa_stealthSim.ARMOR_HP : armaa_stealthSim.ALLY_HP;
            if (al.hp < allyMax) {
                hpBar(ox + al.x - 11f, oy + al.y + r + 5f, 22f, 4f, (float) al.hp / allyMax, 0, alphaMult);
            }
        }
    }

    private void hudText(String text, float x, float y, float size, Color c, float a, org.lazywizard.lazylib.ui.LazyFont.TextAnchor anchor) {
        if (speechFont == null) {
            return;
        }
        org.lazywizard.lazylib.ui.LazyFont.DrawableString ds = speechFont.createText(text, c, size);
        ds.setAnchor(anchor);
        ds.setBaseColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, (int) (255f * a)))));
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        ds.draw(x, y);
    }

    private float hudTextWidth(String text, float size) {
        return speechFont == null ? 0f : speechFont.createText(text, PLAIN_TEXT, size).getWidth();
    }

    /** The objective at the top left, the pickup notices at the bottom, the inventory at the bottom right. */
    private void hud(float vx, float vy, float alphaMult) {
        float W = armaa_stealthSim.VIEW_W;
        float H = armaa_stealthSim.VIEW_H;
        // objective
        String obj = sim.objective();
        if (obj != null) {
            Color highlight = Misc.getHighlightColor();                 // the game's yellowish highlight
            float w = hudTextWidth(obj, SPEECH_FONT_SIZE);
            float x = vx + 14f;
            float y = vy + H - 38f;
            fill(x - 7f, y - 4f, w + 14f, 28f, FIELD, 0.72f * alphaMult);
            lineWidth(1f);
            outline(x - 7f, y - 4f, w + 14f, 28f, LINE, 0.5f * alphaMult);
            hudText(obj, x, y + 10f, SPEECH_FONT_SIZE, highlight, alphaMult, org.lazywizard.lazylib.ui.LazyFont.TextAnchor.CENTER_LEFT);      // centred on the box (its middle is y + 10)
            String side = sim.sideObjective();
            if (side != null) {
                hudText(side, x, y - 16f, SPEECH_FONT_SIZE, highlight, 0.7f * alphaMult, org.lazywizard.lazylib.ui.LazyFont.TextAnchor.CENTER_LEFT);
            }
        }
        // pickup notices, newest at the bottom, fading at the end
        for (int i = 0; i < toastLines.size(); i++) {
            Object[] tl = toastLines.get(toastLines.size() - 1 - i);
            String text = (String) tl[0];
            float age = (Float) tl[1];
            float fade = Math.min(1f, Math.min(age / 0.12f, (TOAST_LIFE - age) / 0.6f));
            float w = hudTextWidth(text, SPEECH_FONT_SIZE);
            float cx = vx + W / 2f;
            float y = vy + 70f + i * 32f;
            fill(cx - w / 2f - 10f, y - 4f, w + 20f, 28f, FIELD, 0.82f * fade * alphaMult);
            lineWidth(1f);
            outline(cx - w / 2f - 10f, y - 4f, w + 20f, 28f, LINE, 0.55f * fade * alphaMult);
            hudText(text, cx, y + 10f, SPEECH_FONT_SIZE, Color.white, fade * alphaMult, org.lazywizard.lazylib.ui.LazyFont.TextAnchor.CENTER);
        }
        // the inventory: four slots, the held weapon lit, rounds under a gun
        java.util.List<String> items = sim.slotItems();
        String active = sim.activeWeapon();
        float bx0 = vx + W - 12f - (armaa_stealthSim.INVENTORY_SLOTS * 36f + (armaa_stealthSim.INVENTORY_SLOTS - 1) * 4f) + 36f * 0f;
        for (int i = 0; i < armaa_stealthSim.INVENTORY_SLOTS; i++) {
            float bx = bx0 + i * 40f;
            float by = vy + 12f;
            String it = i < items.size() ? items.get(i) : null;
            boolean on = it != null && it.equals(active) && sim.owns(it);
            fill(bx, by, 36f, 36f, FIELD, 0.72f * alphaMult);
            lineWidth(on ? 2.5f : 1f);
            outline(bx, by, 36f, 36f, LINE, (on ? 1f : (it != null ? 0.55f : 0.25f)) * alphaMult);
            hudText(Integer.toString(i + 1), bx + 3f, by + 25f, 11f, LINE, 0.6f * alphaMult, org.lazywizard.lazylib.ui.LazyFont.TextAnchor.BOTTOM_LEFT);
            if (it == null) {
                continue;
            }
            float cx = bx + 18f;
            float cy = by + 18f;
            if ("pistol".equals(it)) {
                holdWeapon("pistol", cx - 3.5f, cy, 0f, 0f, 1f, 0f, 1.1f, alphaMult);
            } else if ("rifle".equals(it)) {
                holdWeapon("rifle", cx - 7f, cy, 0f, 0f, 1f, 0f, 0.8f, alphaMult);
            } else if ("baton".equals(it)) {
                holdWeapon("baton", cx - 6f, cy, 0f, 0f, 1f, 0f, 1.1f, alphaMult);
            } else if ("tool".equals(it)) {
                fill(cx - 8f, cy - 7f, 4f, 14f, PLAIN_TEXT, 0.85f * alphaMult);
                fill(cx - 8f, cy - 7f, 14f, 4f, PLAIN_TEXT, 0.85f * alphaMult);
            } else if ("distract".equals(it)) {
                disc(cx, cy + 2f, 6f, RING_THROWN, 0.9f * alphaMult);
                hudText(Integer.toString(sim.distractionsLeft), bx + 33f, by + 3f, 12f, PLAIN_TEXT, alphaMult, org.lazywizard.lazylib.ui.LazyFont.TextAnchor.BOTTOM_RIGHT);
            }
            if ("pistol".equals(it) || "rifle".equals(it)) {
                int rounds = "rifle".equals(it) ? sim.rifleAmmo : sim.ammo;
                hudText(Integer.toString(rounds), bx + 33f, by + 3f, 12f, rounds == 0 ? ALERT : PLAIN_TEXT, alphaMult, org.lazywizard.lazylib.ui.LazyFont.TextAnchor.BOTTOM_RIGHT);
            }
        }
        // the cards they hold, above the slots
        float cardsX = bx0;
        float cardsY = vy + 54f;
        if (sim.hasKey) {
            fill(cardsX, cardsY, 22f, 14f, CONE, 0.9f * alphaMult);
            lineWidth(1f);
            outline(cardsX, cardsY, 22f, 14f, PLAYER, 0.95f * alphaMult);
            cardsX += 28f;
        }
        if (sim.hasKey2) {
            fill(cardsX, cardsY, 22f, 14f, SEARCH, 0.9f * alphaMult);
            lineWidth(1f);
            outline(cardsX, cardsY, 22f, 14f, PLAYER, 0.95f * alphaMult);
        }
    }

    /**
     * A bar of hit points: dark behind, a fill that goes from teal to amber to red as it drains, a bright edge so it can be seen, and (if notches
     * is more than 1) a notch for each hit it takes to empty.
     */
    private void hpBar(float x, float y, float w, float h, float frac, int notches, float alphaMult) {
        frac = Math.max(0f, Math.min(1f, frac));
        fill(x, y, w, h, Color.black, 0.75f * alphaMult);
        if (frac > 0f) {
            Color c = frac > 0.6f ? CONE : (frac > 0.3f ? SEARCH : ALERT);
            fill(x + 1f, y + 1f, (w - 2f) * frac, h - 2f, c, 0.95f * alphaMult);
        }
        lineWidth(1f);
        outline(x, y, w, h, Color.white, 0.75f * alphaMult);
        if (notches > 1) {
            prep();
            color(Color.black, 0.8f * alphaMult);
            GL11.glBegin(GL11.GL_LINES);
            for (int i = 1; i < notches; i++) {
                float nx = x + w * i / notches;
                GL11.glVertex2f(nx, y + 1f);
                GL11.glVertex2f(nx, y + h - 1f);
            }
            GL11.glEnd();
            endLines();
        }
    }

    private void counters(float vx, float vy, float alphaMult) {
        // the player's hit points, bottom-left over the other counters: a bar that drains, a notch for each hit it takes to end the run
        float hpFrac = Math.max(0f, (float) (armaa_stealthSim.MAX_WOUNDS - sim.wounds) / armaa_stealthSim.MAX_WOUNDS);
        hpBar(vx + 16f, vy + 44f, 70f, 9f, hpFrac, armaa_stealthSim.MAX_WOUNDS / armaa_stealthSim.WOUND_DAMAGE, alphaMult);
        hudText("HP", vx + 92f, vy + 49f, 17f, PLAIN_TEXT, 0.9f * alphaMult, org.lazywizard.lazylib.ui.LazyFont.TextAnchor.CENTER_LEFT);
        for (int i = 0; i < armaa_stealthSim.START_DISTRACTIONS; i++) {
            float x = vx + 16f + i * 14f;
            float y = vy + 16f;
            if (i < sim.distractionsLeft) {
                fill(x, y, 8f, 8f, RING_THROWN, 0.9f * alphaMult);
            }
            lineWidth(1f);
            outline(x, y, 8f, 8f, RING_THROWN, 0.6f * alphaMult);
        }
        if (sim.heldAmmoMax() > 0) {
            for (int i = 0; i < sim.heldAmmoMax(); i++) {
                float x = vx + 16f + i * 14f;
                float y = vy + 30f;
                if (i < sim.heldAmmo()) {
                    fill(x, y, 8f, 8f, ALERT, 0.9f * alphaMult);
                }
                lineWidth(1f);
                outline(x, y, 8f, 8f, ALERT, 0.6f * alphaMult);
            }
        }
    }

    /** Thin dark lines over everything, with a slight flicker. */
    private void scanlines(float vx, float vy, float alphaMult) {
        float w = armaa_stealthSim.VIEW_W;
        float h = armaa_stealthSim.VIEW_H;
        float flicker = 0.92f + 0.08f * (float) Math.sin(clock * 41f);
        lineWidth(1f);
        prep();
        color(Color.black, 0.22f * flicker * alphaMult);
        GL11.glBegin(GL11.GL_LINES);
        for (float y = 1f; y < h; y += 3f) {
            GL11.glVertex2f(vx, vy + y);
            GL11.glVertex2f(vx + w, vy + y);
        }
        GL11.glEnd();
        endLines();
    }

    // ---- drawing helpers. Each sets the state it needs and restores texturing. ----
    private static void prep() {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private static void lineWidth(float w) {
        GL11.glLineWidth(Math.max(1f, w * lineScale));
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
    }

    private static void endLines() {
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(1f);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private static void color(Color c, float alpha) {
        GL11.glColor4ub((byte) c.getRed(), (byte) c.getGreen(), (byte) c.getBlue(),
                (byte) (255 * Math.max(0f, Math.min(1f, alpha))));
    }

    private static void fill(float x, float y, float w, float h, Color c, float alpha) {
        prep();
        color(c, alpha);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x, y + h);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x + w, y);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private static void outline(float x, float y, float w, float h, Color c, float alpha) {
        prep();
        color(c, alpha);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x, y + h);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x + w, y);
        GL11.glEnd();
        endLines();
    }

    private static void disc(float cx, float cy, float r, Color c, float alpha) {
        prep();
        color(c, alpha);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(cx, cy);
        for (int i = 0; i <= 20; i++) {
            double a = i / 20.0 * Math.PI * 2.0;
            GL11.glVertex2f(cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r);
        }
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    /** A bar and a dot, the exclamation mark over an alert guard. */
    private static void exclaim(float cx, float baseY, Color c, float scale, float alpha) {
        fill(cx - 2f * scale, baseY + 6f * scale, 4f * scale, 11f * scale, c, alpha);
        fill(cx - 2f * scale, baseY, 4f * scale, 4f * scale, c, alpha);
    }

    /** A question mark over a guard that is suspicious or searching. */
    private static void question(float cx, float baseY, Color c, float scale, float alpha) {
        lineWidth(2f);
        prep();
        color(c, alpha);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        float hookY = baseY + 12f * scale;
        for (int i = 0; i <= 8; i++) {
            double a = Math.toRadians(160.0 - i * 30.0);          // an arc from upper left, over the top, to the right
            GL11.glVertex2f(cx + (float) Math.cos(a) * 4.5f * scale, hookY + (float) Math.sin(a) * 4.5f * scale);
        }
        GL11.glVertex2f(cx, baseY + 8f * scale);                  // down into the stem
        GL11.glVertex2f(cx, baseY + 6f * scale);
        GL11.glEnd();
        endLines();
        fill(cx - 1.5f * scale, baseY, 3f * scale, 3f * scale, c, alpha);
    }

    /** The landing marker while aiming, and any distraction in the air. */
    private void aimAndThrown(float ox, float oy, float alphaMult) {
        if (sim.status == Status.RUNNING && sim.distractionsLeft > 0 && mouseIn) {
            float[] land = sim.landingFor(aimX(), aimY());
            lineWidth(1f);
            prep();
            color(RING_THROWN, 0.20f * alphaMult);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2f(ox + sim.px, oy + sim.py);
            GL11.glVertex2f(ox + land[0], oy + land[1]);
            GL11.glEnd();
            // the cross where it will land, and a faint circle for how far the noise carries
            color(RING_THROWN, 0.55f * alphaMult);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2f(ox + land[0] - 5f, oy + land[1]);
            GL11.glVertex2f(ox + land[0] + 5f, oy + land[1]);
            GL11.glVertex2f(ox + land[0], oy + land[1] - 5f);
            GL11.glVertex2f(ox + land[0], oy + land[1] + 5f);
            GL11.glEnd();
            color(RING_THROWN, 0.10f * alphaMult);
            GL11.glBegin(GL11.GL_LINE_LOOP);
            for (int i = 0; i < 40; i++) {
                double a = i / 40.0 * Math.PI * 2.0;
                GL11.glVertex2f(ox + land[0] + (float) Math.cos(a) * armaa_stealthSim.DISTRACTION_RADIUS,
                        oy + land[1] + (float) Math.sin(a) * armaa_stealthSim.DISTRACTION_RADIUS);
            }
            GL11.glEnd();
            endLines();
        }
        for (armaa_stealthSim.Thrown th : sim.thrownItems) {
            // a small bright dot with a short fading trail behind it
            for (int i = 3; i >= 0; i--) {
                float back = Math.max(0f, th.t - i * 0.025f);
                float f = Math.min(1f, back / th.flight);
                float x = th.x0 + (th.x1 - th.x0) * f;
                float y = th.y0 + (th.y1 - th.y0) * f;
                disc(ox + x, oy + y, 3.2f - i * 0.6f, RING_THROWN, (1f - i * 0.25f) * alphaMult);
            }
        }
    }

    /**
     * A vision cone cut off at walls, so it shows what the guard can really
     * see, and fading out with distance like a radar sweep. Turns red while the
     * guard is alert and amber while it is suspicious or searching. Faint lines
     * run down the edges and around the far arc.
     */
    private void cone(float ox, float oy, Guard g, float alphaMult) {
        Color c = (g.state == GuardState.ALERT || g.witness) ? ALERT : (g.state == GuardState.PATROL ? mix(CONE, SEARCH, sim.cautionBlend) : SEARCH);
        float range = sim.rangeOf(g);
        float half = sim.halfAngleOf(g);
        int steps = 28;
        float[] px = new float[steps + 1];
        float[] py = new float[steps + 1];
        for (int i = 0; i <= steps; i++) {
            float ang = g.facing - half + (2f * half) * i / steps;
            float d = sim.visionRay(g.x, g.y, ang, range);
            double rad = Math.toRadians(ang);
            px[i] = ox + g.x + (float) Math.cos(rad) * d;
            py[i] = oy + g.y + (float) Math.sin(rad) * d;
        }
        prep();
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        color(c, 0.6f * alphaMult);
        GL11.glVertex2f(ox + g.x, oy + g.y);
        for (int i = 0; i <= steps; i++) {
            color(c, 0.04f * alphaMult);
            GL11.glVertex2f(px[i], py[i]);
        }
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);

        lineWidth(1f);
        prep();
        GL11.glBegin(GL11.GL_LINE_STRIP);
        color(c, 0.35f * alphaMult);
        GL11.glVertex2f(ox + g.x, oy + g.y);
        color(c, 0.08f * alphaMult);
        GL11.glVertex2f(px[0], py[0]);
        GL11.glEnd();
        GL11.glBegin(GL11.GL_LINE_STRIP);
        color(c, 0.35f * alphaMult);
        GL11.glVertex2f(ox + g.x, oy + g.y);
        color(c, 0.08f * alphaMult);
        GL11.glVertex2f(px[steps], py[steps]);
        GL11.glEnd();
        GL11.glBegin(GL11.GL_LINE_STRIP);
        color(c, 0.14f * alphaMult);
        for (int i = 0; i <= steps; i++) {
            GL11.glVertex2f(px[i], py[i]);
        }
        GL11.glEnd();
        endLines();
    }

    /**
     * One ring of sound. It is drawn as a wavefront that stops at walls rather
     * than a perfect circle, with a dimmer echo just behind it.
     */
    private void ring(float ox, float oy, Ripple r, float alphaMult) {
        float radius = r.radius();
        if (radius < 1f) {
            return;
        }
        Color c = r.shot ? RING_SHOT : (r.thrown ? RING_THROWN : (r.fromPlayer ? RING_PLAYER : RING_GUARD));
        float a = r.intensity();
        wavefront(ox, oy, r, radius * 0.82f, c, 0.35f * a * alphaMult, 1f);
        wavefront(ox, oy, r, radius, c, 0.9f * a * alphaMult, 2f);
    }

    private void wavefront(float ox, float oy, Ripple r, float radius, Color c, float alpha, float width) {
        int n = 56;
        lineWidth(width);
        prep();
        color(c, alpha);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int i = 0; i <= n; i++) {
            float ang = 360f * i / n;
            float d = r.shot ? sim.hearDistance(r.x, r.y, ang, radius)
                    : Math.min(radius, sim.rayDistance(r.x, r.y, ang, radius));
            double rad = Math.toRadians(ang);
            GL11.glVertex2f(ox + r.x + (float) Math.cos(rad) * d, oy + r.y + (float) Math.sin(rad) * d);
        }
        GL11.glEnd();
        endLines();
    }
}
