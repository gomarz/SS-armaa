package data.scripts.campaign.stealth;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SoundPlayerAPI;
import java.util.HashMap;
import java.util.Map;
import org.lwjgl.util.vector.Vector2f;

/**
 * Every sound the stealth section makes. The sim only says what happened (a key and where, see {@link armaa_stealthSim#drainSounds}); this plays
 * it: quieter with distance, a little different in pitch each time, and not twice in a row too fast. The table in {@link #spec} is the one place
 * a sound is chosen. Sound ids are the game's own (vanilla ones, apart from the swing and the shot, which are the mod's, see sounds.json).
 * Mono files (blows, shots) go through the world player with the listener put at the player; the UI player is for stereo files.
 */
public class armaa_stealthSounds {

    /** Master volume of everything here. */
    public static final float VOLUME = 0.8f;
    /** A sound this far from the player (level units) is not heard. */
    public static final float RANGE = 700f;
    /** How fast IRON KING's words are typed out, in characters a second (0 shows them whole), and the little wait before it starts. */
    public static final float IK_TYPE_CPS = 45f;
    public static final float IK_TYPE_DELAY = 0.25f;

    private final Map<String, Float> last = new HashMap<String, Float>();
    private String hintSounded;
    private int typedSounded;
    private int ticks;

    /** How many characters of a hint are showing at its age. */
    public static int typed(armaa_stealthHints.View hv) {
        if (IK_TYPE_CPS <= 0f) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.max(0f, (hv.age - IK_TYPE_DELAY) * IK_TYPE_CPS);
    }

    /** {game sound id, volume, pitch variation, least seconds between two, and (if present) true for a mono world sound}. */
    private static Object[] spec(String key) {
        switch (key) {
            case "hit": return new Object[]{"hit_solid", 0.7f, 0.08f, 0.05f, true};
            case "block": return new Object[]{"hit_shield_light_gun", 0.8f, 0.06f, 0.05f, true};
            case "suit": return new Object[]{"hit_hull_heavy", 0.8f, 0.05f, 0.1f, true};
            case "break": return new Object[]{"hit_shield_heavy_gun", 1f, 0.04f, 0.2f, true};
            case "swing": return new Object[]{"armaa_polearm", 0.6f, 0.1f, 0.08f, true};
            case "shot": return new Object[]{"armaa_rifle_shot", 0.8f, 0.07f, 0.05f, true};
            case "dry": return new Object[]{"gun_out_of_ammo", 0.8f, 0.03f, 0.3f, true};
            case "alert": return new Object[]{"detected_player", 0.9f, 0.04f, 0.4f};
            case "lost": return new Object[]{"lost_sight_of_player", 0.8f, 0.04f, 0.4f};
            case "talk": return new Object[]{"ui_noise_static", 1.8f, 0.06f, 0.2f};
            case "ik": return new Object[]{"ui_channel_comm_secure", 2f, 0.02f, 0.5f};
            case "ikstatic": return new Object[]{"ui_noise_static", 0.6f, 0.05f, 0.5f};
            case "type": return new Object[]{"ui_typer_type", 1.2f, 0.12f, 0.03f};
            case "pick": return new Object[]{"ui_item_pickup", 1f, 0.05f, 0.1f};
            case "pickGun": return new Object[]{"ui_cargo_handweapons", 1.2f, 0.03f, 0.1f};
            case "drop": return new Object[]{"ui_cargo_handweapons_drop", 1.2f, 0.03f, 0.1f};
            case "land": return new Object[]{"ui_cargo_default_drop", 1f, 0.08f, 0.1f};
            case "unlock": return new Object[]{"ui_refit_slot_filled_ballistic_large", 0.9f, 0.03f, 0.2f};
            case "deny": return new Object[]{"ui_smallaction_no", 1f, 0.03f, 0.3f};
            case "lesson": return new Object[]{"ui_objective_constructed", 0.9f, 0f, 0.5f};
            case "transOut": return new Object[]{"ui_neural_transfer_begin", 0.9f, 0f, 1f};
            case "transIn": return new Object[]{"ui_neural_transfer_complete", 0.9f, 0f, 1f};
            default: return null;
        }
    }

    /**
     * Call every frame. A positioned sound is dropped if it is too far from the listener, and the game takes the listener's place from the camera
     * (here, somewhere in the campaign) when its sound system next updates, so the override has to be set every frame, not when a sound plays:
     * the sounds of the next frame are then measured from the player.
     */
    public void listener(armaa_stealthSim sim) {
        try {
            Global.getSoundPlayer().setListenerPosOverrideOneFrame(new Vector2f(sim.px, sim.py));
        } catch (Throwable t) {
            // no sound system
        }
    }

    /** Plays a sound from (x, y) in the sim's level (NaN: right here, at full volume). */
    public void play(armaa_stealthSim sim, String key, float x, float y) {
        Object[] spec = spec(key);
        if (spec == null) {
            return;
        }
        float f = 1f;
        if (!Float.isNaN(x)) {
            float d = (float) Math.hypot(x - sim.px, y - sim.py);
            f = Math.max(0f, 1f - d / RANGE);
            f *= f;
            if (d > 40f && !sim.canSee(x, y)) {
                f *= 0.6f;                                      // round a corner or through a wall
            }
        }
        if (f < 0.04f) {
            return;
        }
        float now = System.nanoTime() / 1e9f;
        Float before = last.get(key);
        if (before != null && now - before < (Float) spec[3]) {
            return;
        }
        last.put(key, now);
        float pitch = 1f + ((float) Math.random() * 2f - 1f) * (Float) spec[2];
        float volume = (Float) spec[1] * f * VOLUME;
        try {
            SoundPlayerAPI sp = Global.getSoundPlayer();
            if (spec.length > 4) {
                float lx = Float.isNaN(x) ? sim.px : sim.px + (x - sim.px) * 0.15f;
                float ly = Float.isNaN(x) ? sim.py : sim.py + (y - sim.py) * 0.15f;
                sp.playSound((String) spec[0], pitch, volume, new Vector2f(lx, ly), new Vector2f());
            } else {
                sp.playUISound((String) spec[0], pitch, volume);
            }
        } catch (Throwable t) {
            // a missing sound id must not stop the section
        }
    }

    /** Every frame: plays what the sim reported, the opening of a line from IRON KING, and a tick for every second letter as it is typed. */
    public void frame(armaa_stealthSim sim) {
        for (armaa_stealthSim.Sfx s : sim.drainSounds()) {
            play(sim, s.key, s.x, s.y);
        }
        armaa_stealthHints.View hv = sim.hints.view(sim);
        String id = hv == null ? null : hv.id;
        if (id != null && !id.equals(hintSounded)) {
            play(sim, "ik", Float.NaN, 0f);
        }
        if (id == null || !id.equals(hintSounded)) {
            typedSounded = 0;
            ticks = 0;
        }
        hintSounded = id;
        if (hv != null && IK_TYPE_CPS > 0f) {
            int n = Math.min(typed(hv), hv.text.length());
            for (int i = typedSounded; i < n; i++) {
                if (hv.text.charAt(i) != ' ' && (++ticks % 2) == 0) {
                    play(sim, "type", Float.NaN, 0f);
                }
            }
            typedSounded = Math.max(typedSounded, n);
        }
    }
}
