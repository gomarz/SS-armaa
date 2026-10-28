package data.scripts.campaign.stealth;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The hints the drone projects: what IRON KING says, when, and what he looks at. A hint is one line of text, so adding one is adding a line:
 * <pre>id | scope | trigger | target | priority | text</pre>
 * <ul>
 * <li><b>scope</b>: any, tutorial (the tutorial rooms), start (the first cell, with its vent), cell (the cell block), hub (the hub and its walkway).</li>
 * <li><b>trigger</b>: <code>room:LESSON</code> (the player is in that tutorial room), <code>time:SECONDS</code> (that long after the section started), <code>event:NAME</code> (the sim reports it with {@link Director#event}), <code>zone:col,row,r</code> (the player is
 * within r units of a tile), <code>near:TARGET,r</code> (within r units of a target), <code>see:guard:key|key2|any[,r]</code> (that guard is in
 * the player's view), <code>state:NAME</code> (a condition the sim answers, see {@link armaa_stealthSim#hintState}).</li>
 * <li><b>target</b>: what he looks at and rings: <code>lock:X</code>, <code>guard:key</code>, <code>guard:key2</code>, <code>interact:TYPE</code>,
 * <code>pickup:TYPE</code>, <code>exit</code>, <code>point:col,row</code>, <code>evt</code> (where the event happened), or <code>none</code>.
 * Resolved every frame, so his eye follows a moving guard.</li>
 * <li><b>priority</b>: 1 is quiet (held back while guards are hunting the player), 2 or more comes through anyway.</li>
 * </ul>
 * Each hint is shown once. Pure Java, no game classes, so the headless tests can drive it.
 */
public class armaa_stealthHints {

    public static class Hint {
        public final String id, scope, trigger, target, text;
        public final int priority;
        /** Its place in the list: of the hints waiting, the earlier one is shown first. */
        public int order;

        public Hint(String id, String scope, String trigger, String target, int priority, String text) {
            this.id = id;
            this.scope = scope;
            this.trigger = trigger;
            this.target = target;
            this.priority = priority;
            this.text = text;
        }
    }

    /** What the panel draws: the line, where he is looking (if the player can see it), and how far through the hint is. */
    public static class View {
        public String id, text;
        public float tx, ty;
        public boolean hasTarget;
        public float age, life;

        /** 0 to 1 and back: the projection fades in and out at the ends. */
        public float fade() {
            return Math.max(0f, Math.min(1f, Math.min(age / 0.35f, (life - age) / 0.35f)));
        }
    }

    /** The default hints. The text is plain: the stealth section's strings will move to their own file later. */
    public static final String[] DEFAULTS = {
        "watched|start|event:watched|evt|2|... you're going to try escaping while he's looking directly at you?",
        "vent|cell|state:ventReady|interact:vent|1|The vent. Open it.",
        "guard_card|any|see:guard:key|guard:key|1|This one has a card.",
        "door|any|event:lockedDoor|evt|1|You will need to find a card for this.",
        "sergeant|hub|see:guard:key2|guard:key2|1|This one has a card to the shuttle.",
        "exit|hub|state:exitUnsealed|exit|2|It's open. Go.",
        "ik_start|start|time:14|lock:V|2|There should be something to open the access panel on the southern wall. Find it.",
        "panel|start|state:ventReady|lock:V|1|You know what to do. Open the panel.",
        "fork|start|zone:41,21,50|point:44,15|1|Head north if you want some pointers from me (tutorial). Otherwise, continue south. The shuttles are that way.",
        "t_welcome|tutorial|time:3|none|1|Nobody in here can raise the alarm or hurt you. Go through the rooms and try things.",
        "t_door|tutorial|event:sealedDoor|evt|1|I will open the door once you've followed my instructions.",
        "t_inv|tutorial|room:inventory|none|1|That junk on the floor? Go pick it up.",
        "t_got|tutorial:inventory|event:gotThrowable|none|1|Press E to throw it. By my estimate, you can carry four things at once. Use Tab or the number keys change your active item.",
        "t_dist|tutorial|room:distract|none|1|This guard... draw him away by throwing something at the far wall.",
        "t_lured|tutorial:distract|event:lured|evt|1|... good, good.",
        "t_lights|tutorial|room:lights|none|1|Knocking out those lights will make you harder to detect.",
        "t_lightb|tutorial:lights|event:lightBroken|evt|1|Good. They'll have a harder time seeing, but so will you. Luckily, you've got me.",
        "t_sneak|tutorial|room:sneak|none|1|They're all watching the aisle. use Shift to sneak by undetected.",
        "t_spot|tutorial:sneak|event:spotted|evt|2|Hmm..shoddy work. Return to the entrance and sneak past with Shift.",
        "t_grab|tutorial|room:grab|none|1|Sneak up behind him and press Space to grab him. Interrogate him by holding Space.",
        "t_q|tutorial:grab|event:questioned|none|1|I can use what he says to give you better intel on the enemies position. Be mindful that the enemy can break free.",
        "t_ko|tutorial|room:ko|none|1|While grabbed, you can use E to knock the target out ... i'd reccommend simply killing him, however.",
        "t_drag|tutorial:ko|event:dragged|none|1|You can hide the body in one of the nooks, or press Space again to drop it. Mind that this affects your own mobility.",
        "t_hidden|tutorial:ko|event:bodyHidden|evt|1|Good. A body left in the open will arouse suspicion.",
        "t_combat|tutorial|room:combat|none|1|I'll open the next door once these two are taken care of. Take the baton or the sidearm. I'm sure you know what to do with them.",
        "t_blood|tutorial|room:blood|none|1|Mind the mess your handiwork makes. Guards notice it. Try to avoid stepping in blood - it can be used to track your movements.",
        "t_bloodf|tutorial:blood|event:blood|evt|1|See how that guard went to investigate? You can use these moments to your advantage.",
        "t_out|tutorial|zone:177,7,60|exit|1|Satisfactory. Let's continue.",
        "prisoners|hub|state:cagesOpen|point:33,5|1|Your underlings are being held here. Free them if you wish.",
        "swap|any|state:twoWeapons|none|1|Another weapon? Press Tab to swap between them.",        
    };

    /**
     * The mechanics hints (cones, noise, blood, weapons, the fight) that were cut to keep IRON KING quiet outside the tutorial: he now points at
     * objectives and little else. They are kept so they can be brought back: set {@link #chatty}.
     */
    public static final String[] CHATTER = {
        "bracket|cell|near:interact:bunk,130|interact:bunk|1|That bracket will work the vent.",
        "cones|any|see:guard:any,220|guard:any|1|Stay out of the light cone. Creeping with Shift is quieter.",
        "noise|any|event:noise|evt|1|He heard that. Creep with Shift near them.",
        "ko|any|event:ko|evt|1|He will wake. Press Space to take hold of him and drag him out of sight.",
        "blood|any|event:blood|evt|1|Guards notice blood.",
        "prisoners|hub|state:cagesOpen|point:33,5|1|The prisoners will fight for you. Freeing them is optional.",
        "armory|hub|state:armoryOpen|interact:armory|1|Weapons in here. You can carry four things.",
        "swap|any|state:twoWeapons|none|1|Tab changes weapon.",
        "block|any|event:blocked|evt|1|He blocks from the front. Two blocked blows break his guard, or go round the back.",
        "fight|hub|event:fightStarted|evt|2|They come from the far end. Use the crates.",
        "cover|hub|event:fightWound|evt|2|Crates stop bullets. Lean out to fire.",
        "bunk|start|near:interact:bunk,60|interact:bunk|1|Something has worked loose from that bunk frame.",
        "watched|start|event:watched|evt|2|He saw that. Wait until he has moved on."
    };

    /** Whether the cut hints in {@link #CHATTER} are used as well. */
    public static boolean chatty = false;

    /** The hint lines in use. */
    public static String[] lines() {
        if (!chatty) {
            return DEFAULTS;
        }
        String[] all = new String[DEFAULTS.length + CHATTER.length];
        System.arraycopy(DEFAULTS, 0, all, 0, DEFAULTS.length);
        System.arraycopy(CHATTER, 0, all, DEFAULTS.length, CHATTER.length);
        return all;
    }

    /**
     * The tutorial's lessons, one per room: {id, the events that complete it (any of them, separated by /), the goal shown while you are in the
     * room, what is said when it is done}. The door at the end of the room (named 1 to 8 on the map) unlocks when it is done. The objective line follows the room the player is in (see armaa_stealthSim.objective).
     */
    public static final String[] LESSONS = {
        "inventory|gotThrowable|Pick up the object on the floor|Press E to throw.",
        "distract|lured/ko/kill|Throw objects near guards to draw their attention where you want it.|Slip past while he is distracted",
        "lights|lightBroken|Destroy a light.|Good. Enemies cannot see far in the dark.",
        "sneak|passed|Sneak past the enemy without being seen or heard: hold Shift|Sneaking reduces the sound emit, and makes you harder to see",
        "grab|questioned/ko/kill|Grab the guard from behind with Space, then hold Space to question him|Q to release, E to knock out",
        "ko|bodyHidden|Take a guard down, and stow him in a nook|Hide bodies to avoid detection",
        "combat|sparDone|Eliminate both guards|Done",
        "blood|blood/bloodStep|Wait for the guard to notice the blood, or step in it|Done"
    };

    public static List<String[]> lessons() {
        List<String[]> out = new ArrayList<String[]>();
        for (String l : LESSONS) {
            out.add(l.split("\\|", 4));
        }
        return out;
    }

    public static List<Hint> parse(String[] lines) {
        List<Hint> out = new ArrayList<Hint>();
        for (String line : lines) {
            if (line == null || line.trim().isEmpty() || line.trim().startsWith("#")) {
                continue;
            }
            String[] p = line.split("\\|", 6);
            if (p.length < 6) {
                continue;
            }
            int prio = 1;
            try {
                prio = Integer.parseInt(p[4].trim());
            } catch (NumberFormatException e) {
                prio = 1;
            }
            Hint h = new Hint(p[0].trim(), p[1].trim(), p[2].trim(), p[3].trim(), prio, p[5].trim());
            h.order = out.size();
            out.add(h);
        }
        return out;
    }

    /** Decides which hint is shown when, and keeps what has been shown. */
    public static class Director {
        /** After one hint ends the next waits at least this long. */
        public static final float GAP = 5f;
        /** A hint set off by an event that could not be shown (a fight on, another hint up) is dropped after this long. */
        public static final float STALE = 20f;
        private static final float CHECK = 0.25f;
        /**
         * A hint about an objective (a state, a guard in view, being near something) is only said once the player has been in that
         * situation this long without getting past it: IRON KING points, but does not tell them what to do at every step. Time, zone, room and event hints are not
         * delayed.
         */
        public static float objectiveDelay = 20f;
        private final java.util.Map<String, Float> held = new java.util.HashMap<String, Float>();

        private final List<Hint> hints;
        private final Set<String> seen = new LinkedHashSet<String>();
        private final List<Object[]> queue = new ArrayList<Object[]>();   // {Hint, fired time, event x, event y}
        private final java.util.Map<String, float[]> events = new java.util.HashMap<String, float[]>();
        private Hint active;
        private float evtX, evtY;
        private float clock, age, life, gap, checkIn;
        private final View view = new View();
        public boolean enabled = true;

        public Director(List<Hint> hints) {
            this.hints = hints;
        }

        public Director() {
            this(parse(lines()));
        }

        public void markSeen(String csv) {
            if (csv == null) {
                return;
            }
            for (String s : csv.split(",")) {
                if (!s.trim().isEmpty()) {
                    seen.add(s.trim());
                }
            }
        }

        public String seenString() {
            StringBuilder sb = new StringBuilder();
            for (String s : seen) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(s);
            }
            return sb.toString();
        }

        /** Forget that a hint was shown, so that its trigger can bring it up again. */
        public void repeat(String id) {
            seen.remove(id);
        }

        public boolean hasSeen(String id) {
            return seen.contains(id);
        }

        public Hint active() {
            return active;
        }

        public int queued() {
            return queue.size();
        }

        private boolean queuedAlready(Hint h) {
            for (Object[] q : queue) {
                if (q[0] == h) {
                    return true;
                }
            }
            return false;
        }

        private void enqueue(Hint h, float x, float y) {
            if (!seen.contains(h.id) && h != active && !queuedAlready(h)) {
                queue.add(new Object[]{h, clock, x, y});
            }
        }

        /** The sim reports that something happened, at (x, y). Hints whose trigger is that event are queued. */
        public void event(String name, float x, float y, armaa_stealthSim sim) {
            sim.lessonEvent(name);
            if (!enabled) {
                return;
            }
            events.put(name, new float[]{x, y});
            for (Hint h : hints) {
                if (h.trigger.equals("event:" + name) && sim.hintScope(h.scope)) {
                    enqueue(h, x, y);
                }
            }
        }

        /** Checks the other triggers a few times a second, then shows the best queued hint when it is time. */
        public void update(armaa_stealthSim sim, float dt) {
            if (!enabled) {
                return;
            }
            clock += dt;
            gap = Math.max(0f, gap - dt);
            checkIn -= dt;
            if (checkIn <= 0f) {
                checkIn = CHECK;
                for (Hint h : hints) {
                    if (seen.contains(h.id) || h == active || queuedAlready(h) || h.trigger.startsWith("event:") || !sim.hintScope(h.scope)) {
                        continue;
                    }
                    boolean now = triggered(h, sim);
                    if (delayed(h)) {
                        Float before = held.get(h.id);
                        float hs = now ? (before == null ? 0f : before) + CHECK : 0f;
                        held.put(h.id, hs);
                        now = hs >= objectiveDelay;
                    }
                    if (now) {
                        enqueue(h, Float.NaN, Float.NaN);
                    }
                }
            }
            if (active != null && (!sim.hintScope(active.scope) || (active.trigger.startsWith("room:") && !triggered(active, sim)))) {
                active = null;                                  // about a room the player has left: it ends at once, and the next room's hint need not wait
                gap = 0f;
                checkIn = 0f;
            }
            if (active != null) {
                age += dt;
                if (age >= life) {
                    active = null;
                    gap = GAP;
                }
            }
            for (int i = queue.size() - 1; i >= 0; i--) {
                Hint qh = (Hint) queue.get(i)[0];
                boolean event = qh.trigger.startsWith("event:");
                if (!sim.hintScope(qh.scope) || (event ? clock - (Float) queue.get(i)[1] > STALE : !triggered(qh, sim))) {
                    queue.remove(i);                            // an event's moment has passed; or the player has left what the hint was about
                }
            }
            if (active == null && gap <= 0f && !queue.isEmpty()) {
                int best = -1;
                for (int i = 0; i < queue.size(); i++) {
                    Hint h = (Hint) queue.get(i)[0];
                    if (h.priority < 2 && sim.hintHunted()) {
                        continue;                               // keep quiet while they are after the player
                    }
                    Hint b = best < 0 ? null : (Hint) queue.get(best)[0];
                    if (b == null || h.priority > b.priority || (h.priority == b.priority && h.order < b.order)) {
                        best = i;                               // the more urgent first, then in the order the hints are written
                    }
                }
                if (best >= 0) {
                    Object[] q = queue.remove(best);
                    active = (Hint) q[0];
                    evtX = (Float) q[2];
                    evtY = (Float) q[3];
                    age = 0f;
                    life = Math.max(4f, Math.min(9f, 3f + 0.07f * active.text.length()));
                    seen.add(active.id);
                }
            }
        }

        private static boolean delayed(Hint h) {
            return !h.scope.startsWith("tutorial") && (h.trigger.startsWith("state:") || h.trigger.startsWith("see:") || h.trigger.startsWith("near:"));
        }

        private boolean triggered(Hint h, armaa_stealthSim sim) {
            String t = h.trigger;
            if (t.startsWith("time:")) {
                return sim.hintTime() >= Float.parseFloat(t.substring(5));
            }
            if (t.startsWith("room:")) {
                return sim.hintInRoom(t.substring(5));
            }
            if (t.startsWith("state:")) {
                return sim.hintState(t.substring(6));
            }
            if (t.startsWith("zone:")) {
                String[] a = t.substring(5).split(",");
                float[] at = sim.hintTarget("point:" + a[0] + "," + a[1]);       // a tile of this level (row 0 at the top)
                return at != null && Math.hypot(sim.px - at[0], sim.py - at[1]) <= Float.parseFloat(a[2]);
            }
            if (t.startsWith("near:")) {
                int c = t.lastIndexOf(',');
                float[] at = sim.hintTarget(t.substring(5, c));
                return at != null && Math.hypot(sim.px - at[0], sim.py - at[1]) <= Float.parseFloat(t.substring(c + 1));
            }
            if (t.startsWith("see:")) {
                String spec = t.substring(4);
                float range = 400f;
                int c = spec.indexOf(',');
                if (c >= 0) {
                    range = Float.parseFloat(spec.substring(c + 1));
                    spec = spec.substring(0, c);
                }
                float[] at = sim.hintTarget(spec);
                return at != null && Math.hypot(sim.px - at[0], sim.py - at[1]) <= range && sim.canSee(at[0], at[1]);
            }
            return false;
        }

        /** The hint being shown, with where he is looking; null when none. The target is only given if the player can see it now. */
        public View view(armaa_stealthSim sim) {
            if (active == null) {
                return null;
            }
            view.id = active.id;
            view.text = active.text;
            view.age = age;
            view.life = life;
            float[] at = null;
            if ("evt".equals(active.target)) {
                if (!Float.isNaN(evtX)) {
                    at = new float[]{evtX, evtY};
                }
            } else {
                at = sim.hintTarget(active.target);
            }
            view.hasTarget = at != null && sim.canSee(at[0], at[1]);
            if (view.hasTarget) {
                view.tx = at[0];
                view.ty = at[1];
            }
            return view;
        }
    }
}
