package data.scripts.campaign.stealth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

import data.scripts.campaign.stealth.armaa_stealthLevel.GuardSpec;
import data.scripts.campaign.stealth.armaa_stealthLevel.Pickup;

/**
 * Rules of the Starfall stealth section. No game classes in here on purpose:
 * the same simulation runs in the campaign panel (armaa_stealthPanel) and in
 * the standalone harness under JavaApplication1/test, so it can be tuned and
 * checked without launching Starsector.
 *
 * Coordinates are panel units with the origin at the bottom-left, y up, which
 * is how the campaign UI lays out. A level is width x height world units, bigger
 * than the VIEW_W x VIEW_H window onto it (see armaa_stealthCamera); levels come
 * from armaa_stealthLevel.
 *
 * Guards have four states.
 *
 *   PATROL      walking a loop of waypoints.
 *   SUSPICIOUS  something caught the guard's attention: the player inside its
 *               cone with a clear line, or a footstep close by. It stops
 *               walking for NOTICE_PAUSE (a "?" pops up and it says something)
 *               but keeps turning to face the spot, following the player if
 *               they are in view, then walks there and looks into it. If the player is
 *               within ALERT_RADIUS and the guard can SEE them (in its cone,
 *               clear line), it goes ALERT. A guard that first sees the player
 *               already inside that radius skips the pause and goes straight to
 *               ALERT. Being close is not enough: a player standing behind a
 *               guard has not been seen, and is only heard if they make noise.
 *   ALERT       it has you. It hunts you down at ALERT_SPEED, radios for
 *               backup on the harder settings, and an alert guard within
 *               MELEE_START with a clear line swings at you (see guardMelee).
 *   SEARCH      it reached the spot and found nobody. It looks around for a few
 *               seconds, then goes back to its route.
 *
 * There is no visible meter. Being noticed is a guard changing state, and the
 * only measure of "seen" the rules read is whether any guard ever went ALERT.
 *
 * Sneaking slows the player, makes no noise, and shrinks how far a cone
 * reaches. The cone shrinks and grows gradually, and it does not shrink for a
 * guard that is already alert: crouching in front of someone who is chasing you
 * gains nothing. The radius at which a suspicious guard turns alert shrinks
 * with it, so a crouching player can stay put while a guard searches nearby.
 *
 * Sound is shown as expanding rings. A walking player sends out a ring that
 * grows to NOISE_RANGE, which is exactly how far a footstep can be heard, so
 * the picture and the rule agree. Sneaking sends out only a faint, tiny ring
 * that nobody hears. Guards leave smaller rings of their own. Rings are
 * purely for display (and for the player to read), and the views draw them
 * stopped by walls, like the cones.
 *
 * The player can throw a limited number of distractions. One flies to the
 * chosen spot (stopping short of any wall in the way, and no further than
 * THROW_RANGE), lands with a loud ring, and every patrolling or searching guard
 * within DISTRACTION_RADIUS that has a clear line to the landing spot turns
 * suspicious and goes to look. Alert guards, and guards already suspicious,
 * ignore it. It never makes anyone alert by itself.
 *
 * The player can find a sidearm. Firing it is a hitscan along the aim line that
 * stops at walls and kills the first guard in the way, and it is loud: a big
 * ring that turns every living guard in earshot (clear line to the shooter)
 * suspicious of where the shot came from. Four rounds will not clear a room,
 * and it never makes anyone alert by itself.
 *
 * Guards walk around walls rather than into them: when a straight line to
 * where they are going is blocked, they follow an A* path over a grid of the
 * room (cells too close to a wall for a guard to fit are closed). A guard that
 * cannot make progress for a second and a half gives up and searches.
 *
 * Backup, by difficulty. From 1 up, a guard that goes alert radios the guards
 * within BACKUP_RADIUS, who go alert and head for the same spot. From 3 up,
 * reinforcements also arrive from the exit a few seconds after the first
 * alert (one at difficulty 3, two at 4).
 */
public class armaa_stealthSim {

    /** The window onto the level, in world units. The panel is exactly this size. */
    public static final float VIEW_W = 810f;
    public static final float VIEW_H = 400f;
    /**
     * With the interface scaled up the screen has fewer virtual pixels (physical divided by the scale), and the window, with the 500 wide text pane
     * beside it, no longer fits. The window is then drawn smaller, by the share this returns (1 when it fits, never below 0.5).
     */
    public static final float FIT_SIDE = 580f;
    public static final float FIT_BELOW = 330f;

    public static float fitFor(float screenW, float screenH) {
        float w = (screenW - FIT_SIDE) / VIEW_W;
        float h = (screenH - FIT_BELOW) / VIEW_H;
        return Math.max(0.5f, Math.min(1f, Math.min(w, h)));
    }

    public static final float PLAYER_RADIUS = 7f;
    public static final float GUARD_RADIUS = 9f;

    public static final float WALK_SPEED = 95f;
    public static final float SNEAK_SPEED = 45f;
    /** How quickly the player gets up to speed, and how quickly they stop (units per second per second). */
    public static final float PLAYER_ACCEL = 280f;
    public static final float PLAYER_DECEL = 400f;
    /** Below this speed the player is treated as standing still: no footstep noise. */
    public static final float MOVING_SPEED = 4f;

    public static final float CONE_HALF_DEG = 32f;
    public static final float CONE_RANGE = 150f;
    /** Sneaking shrinks how far a cone reaches to this fraction. */
    public static final float SNEAK_RANGE_FACTOR = 0.55f;
    /** How fast a cone's reach changes, as a fraction of full range per second. */
    public static final float SNEAK_BLEND_RATE = 0.8f;

    /** Rings: how long one lasts, and the gap between footstep rings while walking / sneaking. */
    public static final float RIPPLE_LIFE = 1.1f;
    public static final float STEP_INTERVAL_WALK = 0.5f;
    public static final float STEP_INTERVAL_SNEAK = 0.55f;
    /** The sidearm: rounds, reach, time between shots, how far a shot is heard, how close to pick one up. */
    public static final int SIDEARM_AMMO = 8;                   // two rounds to a guard now, so twice the rounds
    public static final float SIDEARM_RANGE = 420f;
    public static final float FIRE_COOLDOWN = 0.4f;
    // caution: the whole station knows someone is about
    public static final float CAUTION_BASE_TIME = 30f;
    public static final float CAUTION_TIME_PER_DIFFICULTY = 6f;
    public static final float CAUTION_SPEED = 1.45f;
    public static final float CAUTION_CONE_RANGE = 0.15f;
    public static final float CAUTION_CONE_DEG = 4f;
    /** Extra reach and width of the cone for a guard who is alert or in the modified alert. */
    public static final float HUNTER_FOV_BONUS = 1.15f;
    public static final float CAUTION_HEARING = 1.4f;
    public static final float CAUTION_PAUSE = 0.9f;
    public static final float CAUTION_BLEND_RATE = 1.5f;
    // radioing it in: an alert guard either keeps after you and calls it in a moment later,
    // or breaks off to somewhere out of your sight to do it. Once he is speaking, killing him is too late.
    public static final float RADIO_DELAY_MIN = 1.4f;
    public static final float RADIO_DELAY_MAX = 2.6f;
    public static final float RADIO_SPEECH = 1.3f;
    /** A witness to a killing calls it in almost at once. */
    public static final float WITNESS_RADIO_DELAY_MIN = 0.3f;
    public static final float WITNESS_RADIO_DELAY_MAX = 0.8f;
    /** A witness searches this many times as long as an ordinary guard, and looks further to each side. */
    public static final float WITNESS_SEARCH_FACTOR = 2f;
    public static final float BREAK_OFF_CHANCE = 0.5f;
    public static final float BREAK_OFF_RANGE = 220f;
    public static final float SHOT_NOISE_RADIUS = 420f;
    /** Bullets are real projectiles. They carry on until a wall, a guard, or SIDEARM_RANGE. */
    public static final float BULLET_SPEED = 520f;
    /** The thud of a bullet hitting something: heard like a shot, but nearer. */
    public static final float IMPACT_NOISE_RADIUS = 200f;
    // silent takedown
    /** How far a takedown reaches: the player lunges the rest of the way, so a guard walking away can be caught even while creeping. */
    public static final float TAKEDOWN_RANGE = 35f;
    public static final float TAKEDOWN_COOLDOWN = 0.8f;
    /** Beyond this angle off a guard's facing counts as behind him. */
    public static final float TAKEDOWN_BEHIND_DEG = 70f;
    public static final float KO_WAKE_TIME = 50f;
    /** Screen shake kick when firing, 0..1; the panel scales it to pixels. */
    public static final float SHAKE_FIRE = 0.6f;
    /** Screen shake when a melee blow lands, and when it puts a guard down. */
    public static final float SHAKE_MELEE_HIT = 0.45f;
    public static final float SHAKE_MELEE_KO = 0.7f;
    // reflex window: the first time a guard spots the player, time slows for a moment
    public static final float SLOWMO_SECONDS = 2.5f;            // real seconds
    public static final float SLOWMO_SCALE = 0.25f;
    public static final float SLOWMO_RAMP = 0.2f;
    // fog of war
    public static final float FOG_CELL = 10f;
    /** Each unit of a guard's sight line that passes through a dark zone counts as this many units of his sight range. */
    public static final float DARK_SIGHT_COST = 2.2f;
    /** Below this visibility a point counts as out of sight. */
    public static final float FOG_SEEN = 0.3f;
    /** Sound rings and guards' footsteps are heard (and shown) this far, fog or not. */
    public static final float HEARING_REVEAL = 300f;
    /** How fast actors and the fog itself fade, per second (5 is about a fifth of a second to change). */
    public static final float FOG_FADE_RATE = 5f;
    // moving bodies
    public static final float BODY_GRAB_RANGE = 24f;
    public static final float DRAG_SPEED_FACTOR = 0.6f;
    public static final float HIDE_RANGE = 28f;
    // guards shooting back, once they know the player kills
    public static final float GUARD_FIRE_RANGE = 260f;
    /** Seconds from raising the weapon to firing; the aim stops following the player for the last part. */
    public static final float GUARD_AIM_TIME = 0.35f;
    public static final float GUARD_AIM_LOCK = 0.2f;
    /** Leaning out from cover he takes a moment longer. */
    public static final float GUARD_PEEK_AIM = 0.5f;
    /**
     * Nobody shoots dead on: each round strays by a spread (standard deviation, degrees) and a kick that builds with each round fired and
     * dies away in a moment, so the second of a pair strays more than the first.
     */
    public static final float GUARD_SPREAD_DEG = 2f;
    public static final float ALLY_SPREAD_DEG = 1.5f;
    public static final float RECOIL_DEG = 2.5f;
    public static final float RECOIL_DECAY = 3f;
    /** Shots per volley, and the gap between them. After the first, each shot is aimed at where the player is then. */
    public static final int GUARD_BURST = 2;
    public static final float GUARD_BURST_GAP = 0.14f;
    public static final float GUARD_BULLET_SPEED = 360f;
    public static final float GUARD_FIRE_COOLDOWN = 1.0f;
    /** The player's hit points: what the wounds taken add up to before the run ends as caught (a guard's round or blow takes WOUND_DAMAGE). */
    public static final int MAX_WOUNDS = 6;
    public static final int WOUND_DAMAGE = 2;
    /** Each wall between a shot and a listener counts as this much extra distance, so sound leaks through walls but weakly. */
    public static final float SHOT_WALL_MUFFLE = 110f;
    public static final float PICKUP_RADIUS = 14f;
    public static final float SHOT_LIFE = 0.16f;
    public static final float GUARD_HIT_RADIUS = 11f;

    /** Thrown distractions: how many, how far, how fast, and how far the landing is heard. */
    public static final int START_DISTRACTIONS = 3;
    public static final float THROW_RANGE = 220f;
    public static final float THROW_SPEED = 380f;
    public static final float DISTRACTION_RADIUS = 120f;

    /** Size of the faint ring a sneaking player leaves. Nobody hears it. */
    public static final float SNEAK_RIPPLE_RADIUS = 16f;

    /** Walking this close to a guard (clear line) can be heard. */
    public static final float NOISE_RANGE = 70f;
    /** A suspicious guard this close to the player (clear line) goes alert. Scaled by the cone's reach. */
    public static final float ALERT_RADIUS = 90f;
    /** A suspicious guard that keeps you in view this long goes alert, however far off you are. */
    public static final float SIGHT_ALERT_TIME = 0.6f;

    /** A guard that goes alert radios guards this close (difficulty 1 and up). */
    public static final float BACKUP_RADIUS = 260f;
    /** How many guards a radio call brings: 1 + difficulty, at most 4. The rest only go on caution. */
    public static final int BACKUP_MAX_BASE = 1;
    public static final int BACKUP_MAX = 4;
    /** Responders stop this far from the spot, spread round it, rather than piling on the one point. */
    public static final float RESPONDER_SPREAD = 90f;
    /** Seconds from the first alert to reinforcements arriving (difficulty 3 and up). */
    public static final float REINFORCE_DELAY = 4f;

    /** How long a guard stops walking after something catches its attention. It keeps turning toward it. */
    public static final float NOTICE_PAUSE = 0.5f;

    public static final float PATROL_TURN_RATE = 110f;   // degrees per second
    public static final float CHASE_TURN_RATE = 260f;
    public static final float SUSPICIOUS_SPEED = 60f;
    public static final float ALERT_SPEED = 85f;
    /** Walking speed of a suspicious guard who heard a gunshot: in more of a hurry than one who heard a footstep. */
    public static final float HURRY_SPEED = 75f;
    public static final float SEARCH_TIME = 4.5f;
    /** How fast a searching guard turns its head from one side to the other, degrees per second. */
    public static final float SEARCH_TURN_RATE = 105f;
    public static final float WAYPOINT_PAUSE = 1.2f;

    /** An alert guard this close, with a clear line, has the player. */
    /** A guard this close to the player, with a clear line, starts a swing. */
    public static final float MELEE_START = 30f;
    /** The swing lands if the player is within this when it finishes. */
    public static final float MELEE_REACH = 36f;
    /** Seconds from raising the arm to the blow, so there is time to step out of reach. */
    public static final float MELEE_WINDUP = 0.6f;
    public static final float MELEE_COOLDOWN = 1.0f;
    /** After any wound the player cannot be wounded again for this long. */
    public static final float WOUND_GRACE = 1.0f;
    public static final float KNOCKBACK = 26f;
    /** Everyone, the player and each guard, has this many hit points. A blow is one. A bullet from the sidearm kills. */
    public static final int MAX_HP = 6;
    /**
     * Damage, against hit points that are doubled from what they were so that weapons can differ: a pistol round 3 (two to put a guard down), a
     * rifle round 6 (one), a guard's or an ally's round 2 (three to put down a player, a guard or a prisoner), a fist 2 and a baton 4. A round that hits
     * an unaware guard in the back counts double.
     */
    public static final int PISTOL_DAMAGE = 3;
    public static final int RIFLE_DAMAGE = 6;
    public static final int GUARD_BULLET_DAMAGE = 2;
    public static final int ALLY_BULLET_DAMAGE = 2;
    public static final int FIST_DAMAGE = 2;
    public static final int ALLY_MELEE_DAMAGE = 2;
    /** What a blow from a guard (a round or a swing) takes from a prisoner. */
    public static final int ALLY_HIT_DAMAGE = 2;
    /** A round this far round to a guard's back from the way he faces, and he unaware, counts double. */
    public static final float BACKSHOT_ARC_DEG = 75f;
    /** For the tests of other things: every round from the player or a prisoner kills outright, as they all did before damage was split by weapon. */
    public static boolean SHOTS_KILL = false;
    // the player's own melee
    public static final float PLAYER_MELEE_REACH = 34f;
    public static final float PLAYER_MELEE_ARC_DEG = 50f;
    public static final float PLAYER_MELEE_COOLDOWN = 0.55f;
    public static final float PLAYER_SWING_SHOW = 0.18f;
    /** The thump of a blow: heard nearby. */
    public static final float MELEE_NOISE_RADIUS = 110f;
    public static final float STUN_TIME = 0.45f;
    public static final float HIT_PUSH = 14f;
    // how hunting guards divide the work
    public static final int ROLE_NONE = 0;
    public static final int ROLE_MELEE = 1;
    public static final int ROLE_GUN = 2;
    public static final int ROLE_HOLD = 3;
    public static final float ROLE_REFRESH = 0.3f;
    /** Guards standing off circle the player at this rate, degrees per second. */
    public static final float HOLD_ORBIT_DEG_PER_SEC = 22f;
    public static final float GUN_SLOT_RADIUS = 135f;
    public static final float HOLD_SLOT_RADIUS = 110f;
    public static final float MELEE_SLOT_RADIUS = 16f;

    public enum Status { RUNNING, CLEAN, SEEN, CAUGHT }

    public enum GuardState { PATROL, SUSPICIOUS, ALERT, SEARCH, DEAD }

    /** An expanding ring of sound. Display only. */
    public static class Ripple {
        public final float x, y;
        public final float maxRadius;
        public final float strength;
        public final boolean fromPlayer;
        /** A landed distraction, drawn in its own colour. */
        public boolean thrown;
        /** A gunshot, drawn in its own colour. */
        public boolean shot;
        public float age;

        public Ripple(float x, float y, float maxRadius, float strength, boolean fromPlayer) {
            this.x = x;
            this.y = y;
            this.maxRadius = maxRadius;
            this.strength = strength;
            this.fromPlayer = fromPlayer;
        }

        /** 0 at birth, 1 when it has faded out. */
        public float progress() {
            return Math.min(1f, age / RIPPLE_LIFE);
        }

        /** Radius now: quick at first, slowing down, like a pulse losing energy. */
        public float radius() {
            float t = progress();
            return maxRadius * (1f - (1f - t) * (1f - t));
        }

        /** 1 at birth, 0 when gone. */
        public float intensity() {
            float t = 1f - progress();
            return strength * t * t;
        }
    }

    /** A shot just fired, for drawing the tracer: from (x0,y0) to where it stopped. */
    public static class Shot {
        public final float x0, y0, x1, y1;
        public final boolean hitGuard;
        public float age;

        public Shot(float x0, float y0, float x1, float y1, boolean hitGuard) {
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
            this.hitGuard = hitGuard;
        }
    }

    /** The small movements of two fighters in reach of each other: a step round the other, or back, now and then. */
    public static class Duel {
        public float time, cool, hold;
        /** 1 circling, 2 a step back. */
        public int kind;
        public float side = 1f;
    }

    /** The producer's camera drone, which follows the player (IRON KING speaks through it): where it hovers and how it is moving. */
    public static class Drone {
        public float x, y, vx, vy, face;
        public boolean placed;
        /** Woken up (it starts lying on the floor in the first cell), and how far off the floor it is (0 to 1). */
        public boolean awake;
        public float lift = 1f;
    }

    /** A puff of dust kicked up by a blow or a skid: grows from r0 to r1 and fades over its life. */
    public static class Puff {
        public float x, y, vx, vy, r0, r1, age, life, alpha;
    }

    /** A short bright line thrown off a blow; yellow when it was turned aside. */
    public static class Spark {
        public float x, y, angle, len, age, life;
        public boolean block;
    }

    /** How far forward (negative: back) a character is drawn through a swing. `w` 0..1 is the windup, `u` 0..1 the strike and recovery. */
    public static float windupOffset(float w, float lean) {
        float t = Math.max(0f, Math.min(1f, w));
        return -lean * (1f - (1f - t) * (1f - t));
    }

    public static float strikeOffset(float u, float lean, float reach) {
        float t = Math.max(0f, Math.min(1f, u));
        if (t < 0.3f) {
            float k = t / 0.3f;
            return -lean + (reach + lean) * (1f - (1f - k) * (1f - k));
        }
        if (t < 0.45f) {
            return reach;
        }
        float k = (t - 0.45f) / 0.55f;
        return reach * (1f - (1f - (1f - k) * (1f - k)));
    }

    /** A word or number that floats up from where a blow landed. */
    public static class Popup {
        public final float x, y;
        public final String text;
        public final int kind;
        /** A sideways offset so two at once do not sit on each other. */
        public final float dx;
        public float age;

        public Popup(float x, float y, String text, int kind, float dx) {
            this.x = x;
            this.y = y;
            this.text = text;
            this.kind = kind;
            this.dx = dx;
        }
    }

    /** Something a character just said, shown over their head for a moment. The same words also go to the dialog log. */
    public static class Speech {
        /** The speaker, or null for Roland or an ally (see ally). */
        public final Guard who;
        /** Set when a freed or caged prisoner is speaking: the bubble sits over them, not over Roland. */
        public final Ally ally;
        public final String text;
        public float age;
        public final float life;
        /** Decides which letters go missing when it is only half heard; fixed so they do not flicker. */
        public final int seed;
        /** The last sound distance worked out for this line, and when, so it is not recomputed every frame. */
        public float distCache = -1f;
        public float distCacheTime = -10f;

        public Speech(Guard who, String text, int seed) {
            this(who, null, text, seed);
        }

        public Speech(Guard who, Ally ally, String text, int seed) {
            this.who = who;
            this.ally = ally;
            this.text = text;
            this.seed = seed;
            this.life = Math.min(3.6f, 1.1f + 0.045f * text.length());
        }
    }

    /** A guard has noticed the player (suspicious or alert). If he is out of sight, the views point to him for a moment. */
    public static class Ping {
        public final Guard who;
        public boolean alert;
        public float age;

        public Ping(Guard who, boolean alert) {
            this.who = who;
            this.alert = alert;
        }
    }

    public static final float PING_LIFE = 3f;

    /** A bullet in flight. */
    public static class Bullet {
        /** Fired by an ally of the player's: it counts as the player's side (it hurts guards), but is not the player's kill. */
        public boolean fromAlly;
        /** Hit points it takes off whom it hits. */
        public int damage = GUARD_BULLET_DAMAGE;
        public float x, y;
        public final float angle;
        public final float dx, dy;
        public float travelled;
        public final boolean fromPlayer;
        public final float speed;
        public final float range;
        /** The guard who fired it: it cannot hit him. Null for the player's. */
        public Guard owner;

        public Bullet(float x, float y, float angle, boolean fromPlayer) {
            this(x, y, angle, fromPlayer, BULLET_SPEED, SIDEARM_RANGE);
        }

        public Bullet(float x, float y, float angle, boolean fromPlayer, float speed, float range) {
            this.speed = speed;
            this.range = range;
            this.x = x;
            this.y = y;
            this.angle = angle;
            this.dx = (float) Math.cos(Math.toRadians(angle));
            this.dy = (float) Math.sin(Math.toRadians(angle));
            this.fromPlayer = fromPlayer;
        }
    }

    /**
     * Blood on the floor: an irregular blob that grows slowly to its full size and then dries (darkens and fades).
     * Kind 0 is a pool, 1 a spatter, 2 a drip left by something wounded on the move, 3 a smear from a dragged body.
     * `seq` numbers them in the order they were made, so a trail can be followed.
     */
    public static class Blood {
        public float x, y;
        public float size, maxSize, growTime;
        public float rot, stretch = 1f;
        public float[] lobes;
        public float dry;
        public float age;
        public int kind;
        public int seq;
        /** Kind 4 is a footprint, 5 a stain on a wall. */
        public boolean noticed;
        /** Who made it (a guard's own drips and prints are not news to him). */
        public Object owner;
        /** 1 for a full print, less as the blood on the foot runs out. */
        public float fade = 1f;
    }

    /** A drop of blood in flight from an impact: it lands on the floor or stains the wall it hits. */
    public static class Droplet {
        public float x, y, vx, vy;
        public float left;
        public final float angle;
        public final float max;
        public final boolean wall;

        Droplet(float x, float y, float angle, float speed, float dist, float max, boolean wall) {
            this.x = x;
            this.y = y;
            this.angle = angle;
            this.vx = (float) Math.cos(angle) * speed;
            this.vy = (float) Math.sin(angle) * speed;
            this.left = dist / speed;
            this.max = max;
            this.wall = wall;
        }
    }

    /** Blood on a foot: how many more prints it will leave, and where it was last. */
    public static class Wet {
        public int left;
        public float dist;
        public int side = 1;
        public float lastX = Float.NaN, lastY;
    }

    public static final int WET_PRINTS = 10;
    public static final float STRIDE = 15f;
    public static final float BLOOD_NOTICE_RANGE = 90f;
    private static final String[] BLOOD_LINES = {"That is blood.", "Blood on the floor. Fresh.", "Is that blood?"};

    public static final int MAX_BLOOD = 160;
    public static final int BLOOD_LOBES = 10;
    public static final float BLOOD_DRY_TIME = 60f;

    /** A light fixture. While it is on it lights the floor it can see; once broken that floor is dark. */
    public static class Light {
        public float x, y;
        public final float radius;
        public boolean on = true;
        /** 1 while lit, falling to 0 after it breaks. */
        public float glow = 1f;
        public float breakAge = 99f;
        public boolean noticed;
        /** Set into a wall rather than standing on the floor. */
        public boolean mounted;
        /** Fog-grid cells it lights (row * fogCols + col), and how strongly (1 at the fixture, 0 at the edge). */
        public int[] cells = new int[0];
        public float[] weights = new float[0];

        public Light(float x, float y, float radius) {
            this.x = x;
            this.y = y;
            this.radius = radius;
        }
    }

    public static final float LIGHT_HIT_RADIUS = 9f;
    public static final float LIGHT_NOISE_RADIUS = 160f;
    public static final float LIGHT_FADE_PER_SEC = 3f;
    public static final float DARK_FADE_PER_SEC = 2.5f;
    private static final String[] LIGHT_LINES = {"(guard) That light is out.", "(guard) Who broke the light?", "(guard) Something smashed that fixture."};

    /** A distraction in flight from (x0,y0) to (x1,y1). */
    public static class Thrown {
        public final float x0, y0, x1, y1, flight;
        public float t;

        public Thrown(float x0, float y0, float x1, float y1, float flight) {
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
            this.flight = flight;
        }

        public float x() {
            return x0 + (x1 - x0) * Math.min(1f, t / flight);
        }

        public float y() {
            return y0 + (y1 - y0) * Math.min(1f, t / flight);
        }
    }

    public static class Rect {
        public final float x, y, w, h;

        public Rect(float x, float y, float w, float h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        public boolean overlapsCircle(float cx, float cy, float r) {
            float nx = Math.max(x, Math.min(cx, x + w));
            float ny = Math.max(y, Math.min(cy, y + h));
            float dx = cx - nx;
            float dy = cy - ny;
            return dx * dx + dy * dy < r * r;
        }

        /** True if the segment (x1,y1)-(x2,y2) passes through this rectangle. */
        public boolean blocks(float x1, float y1, float x2, float y2) {
            float t0 = 0f;
            float t1 = 1f;
            float dx = x2 - x1;
            float dy = y2 - y1;
            float[] p = {-dx, dx, -dy, dy};
            float[] q = {x1 - x, x + w - x1, y1 - y, y + h - y1};
            for (int i = 0; i < 4; i++) {
                if (p[i] == 0f) {
                    if (q[i] < 0f) {
                        return false;
                    }
                } else {
                    float t = q[i] / p[i];
                    if (p[i] < 0f) {
                        if (t > t1) {
                            return false;
                        }
                        if (t > t0) {
                            t0 = t;
                        }
                    } else {
                        if (t < t0) {
                            return false;
                        }
                        if (t < t1) {
                            t1 = t;
                        }
                    }
                }
            }
            return true;
        }
    }

    public static class Guard {
        public float x, y;
        /** Degrees, 0 = +x. Turns gradually toward whatever the guard wants to face. */
        public float facing;
        public final float speed;
        public final float[][] route;
        public int next = 1;
        public GuardState state = GuardState.PATROL;
        /** Pause at a waypoint. */
        public float pause;
        public float sweepBase;
        public float sweepClock;
        /** Where a suspicious or alert guard is headed. */
        public float targetX, targetY;
        /** Search time left. */
        public float searchLeft;
        /** Seconds a suspicious or alert guard has been unable to move, for walls in the way. */
        public float stuck;
        /** Test hook: a frozen guard neither walks, turns nor reacts. */
        public boolean frozen;
        /** Fraction of full cone range in effect; drifts toward its target, see rangeScaleTarget. */
        public float rangeScale = 1f;
        /** Current detour around walls, or null when the way is straight. */
        public List<float[]> path;
        /** Time left standing still after noticing something. */
        public float noticeLeft;
        /** Seconds spent walking to the current spot; reset when the guard takes an interest. */
        public float chaseClock;
        /** Seconds the guard's "?" or "!" has been showing, for the pop-in. */
        public float markAge;
        /** For a dead guard: some other guard has already found the body. */
        public boolean found;
        /** Spots this guard is sweeping while the station is on caution, first one next. */
        public final List<float[]> hunt = new ArrayList<float[]>();
        /** Set when the guard went alert, so losing the player raises caution. */
        public boolean wasAlert;
        /** One of the guards flooding in on the walkway. */
        public boolean flood;
        /** Which wave of the flood he came in with. */
        public int wave;
        public boolean ammoDropped;
        public boolean gearDropped;
        /** Has bled: a body of his leaves a smear when dragged, and he drips while hurt and moving. */
        public boolean bloody;
        public float bleedDist;
        public float flash;
        /** How long he has been waiting for backup before taking someone on alone, and how long his guard is up after a blocked blow. */
        public float waitTime, blockTime;
        /** The time left of the lunge after a swing (the view steps the guard forward along his facing), and the skid-puff timer. */
        public float lungeTime, skidT;
        /** A watcher stops at his look-in waypoints and looks into the player's cell, turning to follow them; this is how long he has left. */
        public boolean watch;
        /** Holds a post: stands at the first point of the route facing the second. */
        public boolean post;
        public boolean[] lookSpots;
        public float watching, strikeCool;
        /** A sparring guard of the tutorial: he really fights (the others only notice). */
        public boolean spar;
        /** A sparring guard of the tutorial who has been put down (knocked out or killed) at some point. */
        public boolean beaten;
        /** The kick of the rounds he has just fired, in degrees: it adds to the stray of the next, and dies away. */
        public float recoilDeg;
        /** How long the shot he is lining up takes in all (longer from cover): the aim line fills over this. */
        public float aimNeeded = GUARD_AIM_TIME;
        /** How much longer he remembers who hit him last: a prisoner (foeAlly) or the player. Whoever hit him more recently is who he deals with. */
        public float attackedByAlly, attackedByPlayer;
        public final Duel duel = new Duel();
        /** Blocked blows in a row, and how long before they are forgotten; the time left of a broken guard. */
        public int blockHits;
        public float blockDecay, brokenTime;
        /** A knockback in progress (units a second, easing out), and the flash of a hit: how long it lasts and which way the blow came from. */
        public float kbVx, kbVy, hitTime, hitAngle;
        public final Wet wet = new Wet();
        /** 0 normally, rising to 1 while he is studying something (a body, a broken light, a man on the floor, the guard he is talking to): his view thins and shortens. */
        public float focus;
        /** Something he has been drawn to look at, and how long he keeps looking. */
        public float lookLeft, lookX, lookY;
        /** In the fight: 0 running to cover, 1 waiting behind it, 2 stepped out to fire. */
        public int fmode;
        public float[] fcover, fpeek;
        public float fwait, fage, fstall;
        /** Seconds a suspicious guard has had the player in view since its notice pause ended. */
        public float seenTime;
        /** Found a body: shows "!" and calls it in, but is not hunting the player. Cleared when the guard returns to patrol or goes alert. */
        public boolean bodyAlarm;
        /** Heard a gunshot: walks to the spot at HURRY_SPEED rather than SUSPICIOUS_SPEED. */
        public boolean hurry;
        /** Sweeps round its own patrol area under caution instead of the disturbance, so the map is not emptied. */
        public boolean homeSweep;
        /** Dead guards: killed with a bullet (true) or knocked out (false). Only a killing counts as "they kill". */
        public boolean lethal;
        /** Knocked out: counts down, then wakes up. */
        public boolean ko;
        public float koTimer;
        /** Has seen what the player did, and will tell the others when the call goes out. */
        public boolean knowsLethal;
        /** Seconds this guard has had a weapon raised on the player, and where it is pointing. */
        public float aimTime;
        public float aimX, aimY;
        public float shotCooldown;
        public int burstLeft;
        public float burstTimer;
        /** Carries the keycard; drops it when killed or knocked out. */
        public boolean hasKey;
        public boolean hasKey2;
        /** Standing talking with a partner, not patrolling, until the scene is over. */
        public boolean huddle;
        /** A knocked-out guard this one has found and is walking up to, to wake him. */
        public Guard chastise;
        /** Time left standing over a knocked-out guard before waking him; -1 when not started. */
        public float chastiseWait = -1f;
        /** A loose prisoner he is hunting, and how long he has not had a line to them. */
        public Ally foeAlly;
        public float foeLost;
        public float allyMeleeTime = -1f;
        /** Standing in a short exchange with another guard, not patrolling, until it is over. */
        public boolean chat;
        public Guard chatWith;
        /** Seconds into a melee swing (0 = not swinging), and the wait before the next one. */
        public float meleeTime;
        public float meleeCooldown;
        public int hp = MAX_HP;
        /** Reeling from a blow: does nothing until it runs out. */
        public float stun;
        /** What this hunting guard is doing (ROLE_*), and the spot on the ring round the player he is heading for. */
        public int role;
        public float slotX, slotY;
        /** Seconds left in which this alert guard still has the player marked, after last seeing them. */
        public float trackTime;
        /** Stowed in a hiding place: out of sight, and not found by walking past. */
        public boolean hidden;
        /** The modified alert, for a guard who saw a killing or found a body: moves at alert speed, calls it in at once, and searches harder. Does not know where the player is. */
        public boolean witness;
        /** 0..1: how much of this guard is shown through the fog. It eases toward 1 in sight and 0 out of it, rather than switching. */
        public float shown;
        /** Which way the player was last seen heading (degrees), or NaN. A searching guard looks that way longer. */
        public float lastHeading = Float.NaN;
        /** The current search: {offset from the base facing, seconds to hold it} per look, and where it has got to. */
        public float[][] look;
        public int lookIdx;
        public float lookHold;
        /** 0 not calling, 1 pursuing and about to call, 2 running somewhere out of sight to call, 3 speaking, 4 call made. */
        public int radioState;
        public float radioTimer;
        /** What radioTimer started at, so the views can show how close the call is. */
        public float radioTimerMax = 1f;
        public float radioX, radioY;
        public GuardState markState = GuardState.PATROL;
        public float stepClock;
        public int pathIdx;
        public float pathGoalX, pathGoalY;
        public float repathTimer;

        public Guard(float speed, float[][] route) {
            this.speed = speed;
            this.route = route;
            this.x = route[0][0];
            this.y = route[0][1];
            this.facing = angleTo(route[0][0], route[0][1], route[1][0], route[1][1]);
            this.sweepBase = facing;
        }
    }

    /** The size of the level in world units. */
    public final float width, height;
    public final List<Rect> walls = new ArrayList<Rect>();
    public final List<Guard> guards = new ArrayList<Guard>();
    public Rect exit;

    public float px, py;
    /** The player's velocity. It eases toward the speed asked for rather than snapping to it. */
    public float vx, vy;
    /** True once any guard has gone ALERT. Mere suspicion does not count. */
    public boolean everSeen;
    public boolean sneaking;
    public final List<Ripple> ripples = new ArrayList<Ripple>();
    /** Distractions in the air. Display and timing only; see throwDistraction. */
    public final List<Thrown> thrownItems = new ArrayList<Thrown>();
    public int distractionsLeft = START_DISTRACTIONS;
    // ---- inventory ----
    /** Items the player can carry at once: each gun, the bracket, and the distractions (all together) take one. */
    public static final int INVENTORY_SLOTS = 4;
    public static final int RIFLE_AMMO_MAX = 8;
    public static final float RIFLE_COOLDOWN = 0.55f;
    public static final int BATON_DAMAGE = 4;
    public boolean hasRifle;
    public int rifleAmmo;
    public boolean hasBaton;
    /** The weapon the player has chosen to hold ("pistol", "rifle", "baton"), or null for the best they have. */
    public String held;
    private final List<String> toasts = new ArrayList<String>();
    private String lastObjective;
    /** The sidearm: picked up from a level's pickups; every shot is loud. */
    public boolean hasSidearm;
    public int ammo;
    public int kills;
    public final List<Shot> shots = new ArrayList<Shot>();
    public final List<Bullet> bullets = new ArrayList<Bullet>();
    public final List<Speech> speeches = new ArrayList<Speech>();
    public final List<Ping> pings = new ArrayList<Ping>();
    /** True once a call has gone out that someone has seen the player kill. From then on the guards use lethal force. Per level. */
    public boolean lethalKnown;
    /** 0..1 screen-shake amplitude, decaying. The views turn it into pixels. */
    public float shake;
    /** A blow that lands near the player freezes the picture for a moment (the panel pauses the sim while this runs down; seconds). */
    public float hitStop;
    private float hitStopCool;
    /** And jolts the view along the blow, 0 to 1, falling away quickly. */
    public float kickX, kickY;
    /** A blow this close to the player is felt in the view. */
    public static final float FEEL_RANGE = 120f;
    public static final float HIT_STOP_HIT = 0.05f;
    public static final float HIT_STOP_BLOCK = 0.03f;
    public static final float HIT_STOP_SUIT = 0.07f;
    public static final float HIT_STOP_BREAK = 0.09f;
    private float takedownCooldown;

    // ---- grab and question ----
    public static final float GRAB_RANGE = 35f;
    /** How long a guard grabbed from behind can be held before he breaks free. */
    public static final float GRAB_HOLD_BEHIND = 4f;
    /** A guard grabbed face to face shouts after a moment and breaks free sooner. */
    public static final float GRAB_HOLD_FRONT = 1.6f;
    public static final float GRAB_SHOUT = 0.5f;
    public static final int GRAB_KILL_PRESSES = 4;
    public static final float QUESTION_TIME = 1.8f;
    public static final float REVEAL_TIME = 12f;
    public static final float GRAB_SHOUT_RADIUS = 260f;

    /** The guard in the player's grip, or null. */
    public Guard grabbed;
    public boolean grabSilent;
    public float grabTime;
    public int grabHits;
    public boolean grabShouted;
    public boolean grabQuestioned;
    /** Set by the view while the question key is held. */
    public boolean questioning;
    public float questionProgress;
    /** While above zero, the radar shows every guard and his cone, and whoever has the keycard is marked. */
    public float revealLeft;
    private float grabOffX;
    private float grabOffY;

    /** A guard waking a knocked-out one: the kick, the grumbling, and back to work. */
    private static class Exchange {
        Guard finder;
        Guard sleeper;
        int idx;
        float timer;
    }

    private final List<Exchange> exchanges = new ArrayList<Exchange>();
    public static final String[] CHASTISE_LINES = {"Hey! Sleeping on the job?", "Oi! Is that a nap?", "Are you asleep? Get up!"};
    public static final String[] KICK_LINES = {"Up! On your feet!", "Wake up, you idiot!"};
    public static final String[] SLEEPER_LINES = {"Ugh... I was not asleep.", "Five more minutes...", "What? I was resting my eyes."};
    public static final String[] WARY_LINES = {"Something is off. I am checking the area.", "My head... someone was here."};
    public static final String[] SCOLD_LINES = {"Sure you were. Back to your post.", "Resting your eyes. Move.", "Back to your route. Now."};

    // the keycard scene: two guards talking in the hallway, until the player comes near or something disturbs them
    private Guard carrier;
    private Guard partner;
    private float huddleX, huddleY;
    private boolean sceneActive;
    private int sceneIdx;
    private float sceneTimer;
    public static final float HUDDLE_TRIGGER = 230f;
    public static final float SCENE_LINE_GAP = 2.4f;
    private static final String[] SCENE_LINES = {
        "(guard) You still have the cell card?",
        "(guard) Right here. Nobody opens those cages without it.",
        "(guard) Then don't lose it.",
        "(guard) Back to your post.",
    };

    // fog of war: how well each cell is seen right now (0..1), and whether it has ever been seen
    public float fogRadius;
    /** Dark zones. */
    public final List<Rect> darks = new ArrayList<Rect>();
    public final List<Light> lights = new ArrayList<Light>();
    public final List<Blood> blood = new ArrayList<Blood>();
    /** The player's own muzzle flash, as `flash` for the guards and allies. */
    public float playerFlash;
    private final java.util.Random bloodRng = new java.util.Random();
    private int bloodSeq;
    public final List<Droplet> droplets = new ArrayList<Droplet>();
    private final Wet playerWet = new Wet();
    /** Which way the last hit was travelling, in radians, so the spray follows it; NaN when there is no direction. */
    private float bloodDir = Float.NaN;
    private float bleedLeft;
    private float dripX, dripY;
    private float smearX, smearY;
    private final int[] lightCover;
    private final int[] lightOnCount;
    private final float[] darkAmt;
    private int brokenLights;
    private boolean darkAnimating;
    /** 0..1: how jammed the radar is. It rises while any guard is alert and eases off once none is. */
    public float jam;
    public static final float JAM_SHRINK = 0.2f;
    /** How far the player sees lit floor, in line of sight, whatever the fog radius or the jamming. */
    public static final float LIT_VIEW = 800f;
    public static final float JAM_RISE_PER_SEC = 1.5f;
    public static final float JAM_FALL_PER_SEC = 0.5f;
    public final int fogCols, fogRows;
    private final float[] fogVis;
    private final boolean[] fogExplored;
    /** The fog as drawn: eases toward fogVis so the edge of sight does not pop. */
    private final float[] fogShow;
    /** The body being dragged, or null. */
    public Guard carrying;
    /** Places a body can be stowed. */
    public final List<float[]> hides = new ArrayList<float[]>();
    /** Hits taken from guards' bullets; MAX_WOUNDS ends the run as caught. */
    public int wounds;
    /** 1 the moment the player is hit, fading. */
    public float woundFlash;
    /** Seconds left in which the player cannot be wounded again. */
    public float woundGrace;
    private float playerMeleeCooldown;
    /** Seconds left of the swing being shown, and which way it went. */
    public float swingTime;
    public float swingAngle;
    private float roleTimer;
    /** The reflex window is off unless the view turns it on (the panel and harness do; tests leave it off). */
    public boolean reflexSlowMo;
    private boolean slowMoUsed;
    private float slowMoLeft;
    /** Seconds left in which some alert guard has the player in view, which the others know by radio. */
    private float squadTrack;
    /** Pickups still lying about. */
    public final List<Pickup> pickups = new ArrayList<Pickup>();
    private float fireCooldown;
    /** 1 the moment a guard goes alert, fading to 0. The views flash the screen with it. */
    public float alertFlash;
    private float playerStepClock;
    public Status status = Status.RUNNING;
    public float time;

    // ---- the reflex window ----
    /** How fast game time runs right now: 1 normally, SLOWMO_SCALE in the middle of the reflex window, easing in and out. */
    public float timeScale() {
        if (slowMoLeft <= 0f) {
            return 1f;
        }
        float elapsed = SLOWMO_SECONDS - slowMoLeft;
        float in = Math.min(1f, elapsed / SLOWMO_RAMP);
        float out = Math.min(1f, slowMoLeft / SLOWMO_RAMP);
        float depth = Math.min(in, out);
        return 1f - (1f - SLOWMO_SCALE) * depth;
    }

    /** 0 outside the window, 1 at its deepest: the views use it for a vignette. */
    public float slowMoAmount() {
        return (1f - timeScale()) / (1f - SLOWMO_SCALE);
    }

    /** How far through a guard's about-to-radio countdown he is, 0..1, or -1 if he is not about to call. */
    public float radioProgress(Guard g) {
        if ((g.radioState != 1 && g.radioState != 2) || g.state == GuardState.DEAD) {
            return -1f;
        }
        return Math.max(0f, Math.min(1f, 1f - g.radioTimer / Math.max(0.01f, g.radioTimerMax)));
    }

    // ---- fog of war ----
    public boolean fogOn() {
        return fogRadius > 0f;
    }

    /**
     * Works out what the player can see: a fan of rays out to fogRadius, each stopped by
     * the first wall, marking the cells along it (fading toward the edge of sight) as
     * seen now and as explored for good.
     */
    private void updateFog() {
        if (fogRadius <= 0f) {
            return;
        }
        final float reach = effectiveFogRadius();
        java.util.Arrays.fill(fogVis, 0f);
        for (float deg = 0f; deg < 360f; deg += 1.5f) {
            float d = rayDistance(px, py, deg, reach);
            double rad = Math.toRadians(deg);
            float cx = (float) Math.cos(rad);
            float cy = (float) Math.sin(rad);
            float end = Math.min(reach, d + FOG_CELL);                  // the wall face itself counts as seen
            for (float t = 0f; t <= end; t += FOG_CELL * 0.5f) {
                int c = (int) ((px + cx * t) / FOG_CELL);
                int r = (int) ((py + cy * t) / FOG_CELL);
                if (c < 0 || r < 0 || c >= fogCols || r >= fogRows) {
                    break;
                }
                float f = t <= 0.7f * reach ? 1f : Math.max(0f, 1f - (t - 0.7f * reach) / (0.3f * reach));
                int i = r * fogCols + c;
                if (f > fogVis[i]) {
                    fogVis[i] = f;
                }
                if (f >= 0.5f) {
                    fogExplored[i] = true;
                }
            }
        }
        if (!lights.isEmpty()) {
            // lit floor is seen however far off it is, as long as nothing is in the way; a finer sweep, because far cells are sparse to the coarse one
            for (float deg = 0f; deg < 360f; deg += 0.5f) {
                double rad = Math.toRadians(deg);
                float cx = (float) Math.cos(rad);
                float cy = (float) Math.sin(rad);
                float far = rayDistance(px, py, deg, LIT_VIEW);
                float farEnd = Math.min(LIT_VIEW, far + FOG_CELL);
                for (float t = reach * 0.7f; t <= farEnd; t += FOG_CELL * 0.5f) {
                    int c = (int) ((px + cx * t) / FOG_CELL);
                    int r = (int) ((py + cy * t) / FOG_CELL);
                    if (c < 0 || r < 0 || c >= fogCols || r >= fogRows) {
                        break;
                    }
                    int i = r * fogCols + c;
                    if (lightOnCount[i] > 0) {
                        fogVis[i] = 1f;
                        fogExplored[i] = true;
                    }
                }
            }
        }
    }

    private void updateFade(float dt) {
        float step = FOG_FADE_RATE * dt;
        if (fogRadius > 0f) {
            for (int i = 0; i < fogShow.length; i++) {
                fogShow[i] = approach(fogShow[i], fogVis[i], step);
            }
        }
        for (Guard g : guards) {
            boolean shown = canSee(g.x, g.y) || (revealLeft > 0f && g.state != GuardState.DEAD);
            g.shown = approach(g.shown, shown ? 1f : 0f, step);
        }
    }

    /** How far the player sees right now: the fog radius, shrunk by the radar jamming. */
    public float effectiveFogRadius() {
        return fogRadius * (1f - JAM_SHRINK * jam);
    }

    private void updateJam(float dt) {
        boolean anyAlert = false;
        for (Guard g : guards) {
            if (g.state == GuardState.ALERT && !g.frozen) {
                anyAlert = true;
                break;
            }
        }
        anyAlert &= !fightStarted;                      // no jamming in the walkway fight: they need to see
        jam = approach(jam, anyAlert ? 1f : 0f, (anyAlert ? JAM_RISE_PER_SEC : JAM_FALL_PER_SEC) * dt);
    }

    /** How well a world point is seen right now, 0..1. Always 1 with no fog. */
    public float visibility(float x, float y) {
        if (fogRadius <= 0f) {
            return 1f;
        }
        int c = (int) (x / FOG_CELL);
        int r = (int) (y / FOG_CELL);
        if (c < 0 || r < 0 || c >= fogCols || r >= fogRows) {
            return 0f;
        }
        return fogVis[r * fogCols + c];
    }

    public boolean canSee(float x, float y) {
        return visibility(x, y) >= FOG_SEEN;
    }

    public float cellVisibility(int c, int r) {
        return fogVis[r * fogCols + c];
    }

    /** The fog as it should be drawn this frame: eased, so it never pops. */
    public float cellShown(int c, int r) {
        return fogShow[r * fogCols + c];
    }

    public boolean cellExplored(int c, int r) {
        return fogExplored[r * fogCols + c];
    }

    /** Whether a sound at (x, y) is close enough to the player to be shown. */
    public boolean heard(float x, float y) {
        float dx = x - px;
        float dy = y - py;
        return dx * dx + dy * dy <= HEARING_REVEAL * HEARING_REVEAL;
    }

    // ---- the cell: examining things, locks, the vent, the key, Roland ----
    public static final float INTERACT_RANGE = 30f;
    public static final float ROLAND_FOLLOW_DISTANCE = 26f;
    public static final float ROLAND_SPEED = 100f;

    /** A locked door. It is a wall until the key opens it. */
    public static class Lock {
        public final String name;
        public final Rect rect;
        /** Unlocked. A door then slides open for anyone who comes near and closes again once they have gone. */
        public boolean open;
        /** 0 shut, 1 fully open. */
        public float slide;
        public float holdLeft;
        /** The rect is in the wall list while the door is (nearly) shut. */
        public boolean wallIn = true;
        /** An opening door: slides on its own once unlocked. Cage doors, overridden by hand, stay open. */
        public boolean auto = true;
        /** The door slides along y (it sits in a wall that runs up and down the map). */
        public boolean slidesAlongY;
        public boolean hintShown;

        public Lock(String name, Rect rect) {
            this.name = name;
            this.rect = rect;
        }
    }

    /** A prisoner in a cage, who can be freed and will then hold where they are. */
    public static class Ally {
        /** The person's id in ImportantPeople; the view looks up their portrait and real name from it. */
        public final String id;
        /** What they are called in the text. Starts as a guess from the id, and the view replaces it with the person's name. */
        public String name;
        public float x, y;
        public final String lock;
        public boolean freed;
        public boolean noticed;
        /** Where they wait in their room once out of the cage. */
        public float holdX, holdY;
        public float meleeTime = -1f, meleeCooldown;
        public final Wet wet = new Wet();
        public boolean isRoland;
        public float armTime;
        /** 0 a named officer, 1 a marine, 2 a bodyguard. */
        public int type;
        /** In the suit of power armor: tougher, heavier, louder and bigger. */
        public boolean armored;
        public float stepDist;
        /** 1 at the moment of a shot, falling to 0: the view kicks the weapon back and flashes the muzzle. */
        public float flash;
        /** While above zero he follows the nav route instead of cutting across; set when he has been stuck. */
        public float forcePath, blockedTime;
        /** The suit's swing: how much of the flash is left, and which way it went. */
        public float swingTime, swingAngle;
        /** Blows are shared with the guards: stun, a blocked-blow count, a guard that can be broken, a knockback and the flash of a hit. */
        public float stun, blockTime, blockDecay, brokenTime, kbVx, kbVy, hitTime, hitAngle, skidT, recoilDeg;
        /** The guard who last struck them, and how long they remember it: they deal with him first. */
        public Guard attacker;
        public float attackedTime;
        public final Duel duel = new Duel();
        public int blockHits;
        /** Out of the cage and with the player (set when the east door is opened). */
        public boolean joined;
        public boolean armed;
        public boolean dead;
        public int hp = ALLY_HP;
        public float facing;
        public float aimTime, shotCooldown, burstTimer, aimX, aimY;
        public int burstLeft;
        public java.util.List<float[]> path;
        public int pathIdx;
        public float pathAge;
        /** In the fight: 0 waiting in cover, 1 stepping out to a firing spot. */
        public int mode;
        public float modeTime, coverWait, coverAge;
        public float[] cover, peek;

        public Ally(String id, float x, float y, String lock) {
            this.id = id;
            String tail = id.substring(id.lastIndexOf('_') + 1);
            this.name = tail.isEmpty() ? id : Character.toUpperCase(tail.charAt(0)) + tail.substring(1);
            this.type = "marine".equals(id) ? 1 : ("bodyguard".equals(id) ? 2 : 0);
            this.x = x;
            this.y = y;
            this.lock = lock;
        }
    }

    public static final int ALLY_HP = 6;
    public static final float ALLY_SPEED = 72f;
    public static final float ALLY_RANGE = 240f;
    public static final float ALLY_AIM = 0.35f;
    public static final float ALLY_COOLDOWN = 1f;
    /** The next wave comes in once this share of the current wave is down. */
    public static final float FLOOD_NEXT_FRACTION = 0.6f;
    public static final float ALLY_PEEK_AIM = 0.5f;
    /** A second knocked-out guard found within this long of the first is an alarm, not a guard asleep on the job. */
    public static final float SLEEPER_MEMORY = 90f;
    /** A blow from the front is blocked by a guard who is on his guard and facing the player within this half-angle. */
    public static final float BLOCK_ARC_DEG = 60f;
    public static final float BLOCK_STAGGER = 0.25f;
    /** Blocked blows in a row that break a guard's guard, and how long he is open when they do. */
    public static final int BLOCK_BREAK_HITS = 2;
    public static final float BREAK_STUN = 1.8f;
    public static final float BLOCK_MEMORY = 2.5f;
    /** Knockback eases out at this rate: a push of d units goes a total of d, mostly in the first fifth of a second. */
    public static final float KNOCK_RATE = 9f;
    public static final float HIT_FLASH = 0.18f;
    public static final float ALLY_SWING_SHOW = 0.18f;
    /** After a swing the character lunges forward and back over this long; how far (the view only: the sim does not move). */
    public static final float LUNGE_TIME = 0.2f;
    public static final float LUNGE_REACH = 8f;
    public static final float LUNGE_REACH_SUIT = 12f;
    public static final float LUNGE_LEAN = 5f;
    public static final float LUNGE_LEAN_SUIT = 4f;
    public static final float LUNGE_LEAN_FIST = 3f;
    /** Floating notices over a blow: a damage number, "Blocked", "Broken!", "No effect". */
    public static final int POP_DAMAGE = 0, POP_BLOCK = 1, POP_BREAK = 2, POP_IMMUNE = 3, POP_KO = 4;
    public static final float POP_LIFE = 0.9f;
    /** A lone hunter keeps this far off (backing away inside the lower distance) and waits at most this long for company. */
    public static final float LONE_BACK_OFF = 130f;
    public static final float LONE_COME_ON = 200f;
    public static final float LONE_WAIT_MAX = 7f;
    /** A lone hunter only waits for company if some other guard is within this far to come. */
    public static final float LONE_BACKUP_RANGE = 700f;
    /** Who hit a character is dealt with first for this long. */
    public static final float ATTACK_MEMORY = 4f;
    /** In reach of each other, fighters now and then circle or step back: how fast, and how near counts as a fight. */
    public static final float DUEL_SPEED = 40f;
    public static final float DUEL_RANGE = 1.1f;
    /** A guard only circles a player who is standing his ground: one who is running gets chased, not given time. */
    public static final float DUEL_PLAYER_SPEED = 10f;
    /**
     * Whether a guard also circles the player between blows. Off: with it on, the probe bot (a cautious sneaker) is caught about half as often
     * even though a guard's wounds on a player who stands still are unchanged, so it is left for a deliberate decision. Guards and prisoners
     * circle each other regardless.
     */
    public static final boolean DUEL_VS_PLAYER = false;
    public static final float LONE_MATE_RANGE = 260f;
    /** A guard studying something sees only this share of his usual width and length, at full focus. */
    public static final float FOCUS_ANGLE = 0.5f;
    public static final float FOCUS_RANGE = 0.65f;
    public static final float FOCUS_RATE = 3f;
    /** How long a guard stands and looks at a knocked-out guard before waking him. */
    public static final float CHASTISE_LOOK = 1f;
    public static final float ALLY_MELEE_REACH = 30f;
    public static final float ALLY_MELEE_WINDUP = 0.5f;
    public static final float ALLY_MELEE_COOLDOWN = 0.9f;
    public static final float ALLY_SIGHT = 200f;
    public static final float COVER_WAIT_MIN = 0.9f;
    public static final float COVER_WAIT_MAX = 1.7f;
    public static final float ADVANCE_STEP = 160f;
    public static final int FLOOD_WAVE_SIZE = 4;
    public static final float DOOR_TRIGGER = 44f;
    public static final float DOOR_TIME = 0.3f;
    public static final float DOOR_HOLD = 2f;
    public static final float OVERRIDE_TIME = 2f;
    public final List<Ally> allies = new ArrayList<Ally>();
    public final List<Popup> popups = new ArrayList<Popup>();
    public final List<Puff> puffs = new ArrayList<Puff>();
    /** What IRON KING projects through the drone, when and at what (see armaa_stealthHints). */
    public final armaa_stealthHints.Director hints = new armaa_stealthHints.Director();
    public final Drone drone = new Drone();
    /** Bars stop a body but not sight; where the player crawls (the vent shaft); the player's cell; the second exit and where it leads. */
    public final List<Rect> bars = new ArrayList<Rect>();
    public final List<Rect> crawls = new ArrayList<Rect>();
    public Rect cellRect;
    public Rect exit2;
    public String nextStage2;
    public boolean droneDown;
    /** The tutorial: guards that would raise the alarm only notice, and nothing wounds the player. */
    public boolean tutorialMode;
    public final java.util.Set<String> lessonsDone = new java.util.LinkedHashSet<String>();
    private final List<Object[]> lessonZones = new ArrayList<Object[]>();
    private final List<Object[]> trips = new ArrayList<Object[]>();         // {name, float[]{x, y, r}, fired}
    private float lastSpot = -99f;
    private boolean sparDone;
    /** The tutorial gives the player nothing to throw until they pick the first throwable up; more lie in a corner of every room. */
    public boolean throwablesFound;
    private float nudgeClock;
    private int nudgeRoom = -1;
    /** How long a player can stand about in a tutorial room before IK says again what to do. */
    public static final float NUDGE_TIME = 10f;
    /** The hint that says what to do in each room, in the order of the lessons. */
    private static final String[] ROOM_HINTS = {"t_inv", "t_dist", "t_lights", "t_sneak", "t_grab", "t_ko", "t_combat", "t_blood"};
    /** The guards of a tutorial room stand still until the player comes into it. */
    private boolean[] roomWoken = new boolean[1];
    private final float[] roomTime = new float[16];
    /** A tutorial room that has not given up its lesson opens its door after this long. */
    public static final float ROOM_PATIENCE = 90f;
    /** How many times the guard looking in has caught the player at the vent. */
    public int watchStrikes;
    /** Crawling in the shaft is this share of a creep's speed. */
    public static final float CRAWL_FACTOR = 0.55f;
    /** Holding the interact key this long works the vent grate loose. */
    public static final float VENT_TIME = 3f;
    /** How long the guard stops to look into the cell, and how long after the start the drone wakes. */
    public static final float LOOK_IN_TIME = 8f;
    public static final float DRONE_WAKE_TIME = 8f;
    public final List<Spark> sparks = new ArrayList<Spark>();
    private final java.util.Random fxRng = new java.util.Random();
    private final java.util.Random duelRng = new java.util.Random(31);
    private final java.util.Random aimRng = new java.util.Random(53);

    /** The angle, in degrees, a round strays from its aim: a spread plus the kick of the rounds fired a moment ago (in a random direction). */
    private float stray(float spreadDeg, float recoilDeg) {
        float g = (float) Math.max(-2.2, Math.min(2.2, aimRng.nextGaussian()));
        float kick = recoilDeg * (aimRng.nextBoolean() ? 1f : -1f) * aimRng.nextFloat();
        return g * spreadDeg + kick;
    }
    private int popupCount;
    private final List<float[]> armSpots = new ArrayList<float[]>();
    public float[] suitSpot;
    /** The marine who has taken the suit (and is putting it on, or wearing it). */
    public Ally suitClaimant;
    /** Roland, once a prisoner is free: he becomes one of the party (and cannot be killed: at 1 hp he is only out of the fight). */
    public Ally rolandAlly;
    public static final float ARM_TIME = 2.5f;
    /** Putting on the suit takes longer than picking up a rifle. */
    public static final float DON_TIME = 4f;
    public static final int ARMOR_HP = 16;
    public static final float ARMOR_SPEED = 0.7f;
    public static final float ARMOR_RADIUS = 10f;
    public static final float ARMOR_COOLDOWN = 0.7f;
    public static final float ARMOR_NOISE = 90f;
    public static final float ARMOR_STEP = 24f;
    /** Degrees a second an ally can turn while walking, and a suit more slowly. */
    public static final float ALLY_TURN_RATE = 300f;
    /** The suit's backhand: it reaches this far, hits everything within this half-angle of where it faces, and throws them back. */
    public static final float SUIT_REACH = 46f;
    public static final float SUIT_ARC_DEG = 45f;
    public static final float SUIT_WINDUP = 0.4f;
    public static final float SUIT_COOLDOWN = 1.1f;
    public static final float SUIT_KNOCK = 60f;
    public static final float SUIT_STUN = 1f;
    public static final int SUIT_DAMAGE = 4;
    public static final float SUIT_SWING_SHOW = 0.22f;
    public static final float ARMOR_TURN_RATE = 140f;
    public static final float ALLY_MELEE_SIGHT = 120f;
    /** The east door has been opened: freed allies join the player. */
    public boolean allyBreakout;
    /** The walkway fight is on. */
    public boolean fightStarted;
    public float fightTime;
    public int floodWaves;
    private boolean sealWarned;
    /** How many of the flood may still be on their feet when the shuttle lock opens. */
    public static final int FIGHT_CLEAR_LEFT = 2;
    private float fightX = -1f;
    public boolean escalates;
    /** When the last knocked-out guard was found (and passed off as asleep), or -1. */
    private float lastSleeperFound = -1f;
    private float rallyX = -1f, rallyY;
    private java.util.List<float[]> rolandPath;
    private int rolandPathIdx;
    private float rolandPathAge;
    private float floodX, floodY;
    public float overrideProgress;
    public Lock overrideLock;
    private final List<Rect> navIgnore = new ArrayList<Rect>();

    public final List<Lock> locks = new ArrayList<Lock>();
    public final List<armaa_stealthLevel.Interact> interacts = new ArrayList<armaa_stealthLevel.Interact>();
    public boolean hasKey;
    /** The second card, which the east door of the hub wants. */
    public boolean hasKey2;
    public boolean hasTool;
    public boolean ventOpen;
    /** Where the vent comes out, or null if this level has none. */
    public float[] ventExit;
    public boolean hasRoland;
    public boolean rolandFree;
    /** True once the player has opened the way out without freeing Roland. */
    public boolean abandonedRoland;
    private boolean warnedAboutRoland;
    public float rolandX, rolandY;
    /** Name of the level to load when the exit is reached, or null if this is the last. */
    public String nextStage;

    /** Navigation grid cell size. */
    private static final float CELL = 10f;
    private final int navCols;
    private final int navRows;
    private boolean[] navBlocked;
    /** Walls grown by a guard's radius, for line-of-travel checks. */
    private final List<Rect> grown = new ArrayList<Rect>();

    private final float coneRange;
    private final float coneHalf;
    private final int difficulty;
    private final Random rng = new Random();
    private final List<String> barks = new ArrayList<String>();
    private int reinforcementsLeft;
    private float reinforceTimer = -1f;

    /** A radio call already under way: it goes out even if the caller is shot before it ends. */
    private static class PendingCall {
        final Guard caller;
        float left;

        PendingCall(Guard caller, float left) {
            this.caller = caller;
            this.left = left;
        }
    }

    private final List<PendingCall> pendingCalls = new ArrayList<PendingCall>();

    /** Seconds of caution left (0 = none), how long it was set for, where it started, and a 0..1 blend for the visuals and senses. */
    public float cautionLeft;
    public float cautionMax;
    public float cautionBlend;
    private float cautionX, cautionY;
    private float lastAlertX, lastAlertY;

    /** The small test room (see armaa_stealthLevel.classic), at the given difficulty. */
    public armaa_stealthSim(int difficulty) {
        this(armaa_stealthLevel.classic(), difficulty);
    }

    /**
     * @param difficulty 0 is the base level. Each point beyond that widens the
     * cones a little, and a level's guards with a higher MIN_DIFFICULTY join in.
     * Heat from the escape that led here maps straight onto this.
     */
    public armaa_stealthSim(armaa_stealthLevel level, int difficulty) {
        difficulty = Math.max(0, difficulty);
        this.difficulty = difficulty;
        this.width = level.width;
        this.height = level.height;
        navCols = (int) (width / CELL);
        navRows = (int) (height / CELL);
        fogCols = (int) Math.ceil(width / FOG_CELL);
        fogRows = (int) Math.ceil(height / FOG_CELL);
        fogVis = new float[fogCols * fogRows];
        fogExplored = new boolean[fogCols * fogRows];
        fogShow = new float[fogCols * fogRows];
        lightCover = new int[fogCols * fogRows];
        lightOnCount = new int[fogCols * fogRows];
        darkAmt = new float[fogCols * fogRows];
        reinforcementsLeft = level.tutorial ? 0 : (difficulty >= 4 ? 2 : (difficulty >= 3 ? 1 : 0));
        coneRange = CONE_RANGE + (level.tutorial ? 0f : 12f * Math.min(difficulty, 4));
        coneHalf = CONE_HALF_DEG + (level.tutorial ? 0f : 3f * Math.min(difficulty, 4));
        walls.addAll(level.walls);
        bars.addAll(level.bars);
        crawls.addAll(level.crawls);
        cellRect = level.cell;
        exit2 = level.exit2;
        nextStage2 = level.next2;
        droneDown = level.droneDown;
        tutorialMode = level.tutorial;
        if (tutorialMode) {
            distractionsLeft = 0;                               // nothing to throw until the first room gives them something
        }
        lessonZones.addAll(level.lessons);
        roomWoken = new boolean[Math.max(1, level.lessons.size())];
        for (Object[] tr : level.trips) {
            trips.add(new Object[]{tr[0], tr[1], Boolean.FALSE});
        }
        fogRadius = level.fogRadius;
        darks.addAll(level.darks);
        px = level.startX;
        py = level.startY;
        exit = level.exit;
        buildNav();
        for (float[] ls : level.lights) {
            Light lamp = new Light(ls[0], ls[1], ls[2]);
            if (ls.length > 3 && ls[3] > 0f) {
                mountLight(lamp);
            }
            lights.add(lamp);
        }
        rebuildLights(true);
        Guard keyCarrier = null;
        Guard keyPartner = null;
        for (GuardSpec spec : level.guards) {
            if (difficulty >= spec.minDifficulty) {
                Guard g = new Guard(spec.speed, spec.route);
                g.watch = spec.watch;
                g.spar = spec.spar;
                g.post = spec.post;
                g.lookSpots = spec.lookSpots;
                if (spec.carriesKey2) {
                    g.hasKey2 = true;
                } else if (spec.carriesKey) {
                    g.hasKey = true;
                    keyCarrier = g;
                } else if (keyPartner == null && spec.minDifficulty == 0) {
                    keyPartner = g;
                }
                guards.add(g);
            }
        }
        pickups.addAll(level.pickups);
        for (float[] bl : level.bloods) {
            presetBlood(bl[0], bl[1], bl[2]);
        }
        if (level.tutorial) {
            for (Guard tg : guards) {
                for (Object[] z : level.lessons) {
                    Rect zr = (Rect) z[1];
                    if (tg.x >= zr.x && tg.x <= zr.x + zr.w && tg.y >= zr.y && tg.y <= zr.y + zr.h) {
                        tg.frozen = true;                       // until the player comes into his room
                    }
                }
            }
        }
        updateFog();

        nextStage = level.next;
        hides.addAll(level.hides);
        interacts.addAll(level.interacts);
        for (java.util.Map.Entry<String, Rect> e : level.locks.entrySet()) {
            locks.add(new Lock(e.getKey(), e.getValue()));
            walls.add(e.getValue());
        }
        for (Lock lk : locks) {
            lk.auto = !isCageName(lk.name) && !"V".equals(lk.name) && !isLessonDoor(lk.name);           // a tutorial door, once unlocked, stays open
            float mx = lk.rect.x + lk.rect.w / 2f;
            float my = lk.rect.y + lk.rect.h / 2f;
            boolean wallsLeftRight = solidAt(lk.rect.x - 4f, my) && solidAt(lk.rect.x + lk.rect.w + 4f, my);
            lk.slidesAlongY = !wallsLeftRight;
        }
        fightX = level.fightX;
        escalates = level.escalates;
        armSpots.addAll(level.armSpots);
        suitSpot = level.suitSpot;
        rallyX = level.rallyX;
        rallyY = level.rallyY;
        floodX = level.floodX;
        floodY = level.floodY;
        for (armaa_stealthLevel.Prisoner pr : level.prisoners) {
            allies.add(new Ally(pr.id, pr.x, pr.y, pr.lock));
        }
        if (!level.locks.isEmpty()) {
            rebuildNav();
        }
        if (!level.ventExits.isEmpty()) {
            ventExit = level.ventExits.get(rng.nextInt(level.ventExits.size()));
        }
        if (keyCarrier != null && keyPartner != null && ventExit != null && ventExit.length >= 4) {
            // the carrier and a partner are standing talking in the hallway outside wherever the vent comes out
            carrier = keyCarrier;
            partner = keyPartner;
            huddleX = ventExit[2];
            huddleY = ventExit[3];
            carrier.x = huddleX - 14f;
            carrier.y = huddleY;
            carrier.facing = 0f;
            partner.x = huddleX + 14f;
            partner.y = huddleY;
            partner.facing = 180f;
            carrier.huddle = true;
            partner.huddle = true;
        }
        if (!level.keySpots.isEmpty() && keyCarrier == null) {
            float[] k = level.keySpots.get(rng.nextInt(level.keySpots.size()));
            pickups.add(new Pickup("key", k[0], k[1]));
        }
        if (level.rolandAt != null) {
            hasRoland = true;
            rolandX = level.rolandAt[0];
            rolandY = level.rolandAt[1];
        }
        updateFog();                                        // again, now the locked doors are walls
        System.arraycopy(fogVis, 0, fogShow, 0, fogVis.length);
        for (Guard g : guards) {
            g.shown = canSee(g.x, g.y) ? 1f : 0f;
        }
    }

    /** Carries what the player has done into the next stage. */
    public void carryFrom(armaa_stealthSim prev) {
        everSeen = prev.everSeen;
        abandonedRoland = prev.abandonedRoland;
        kills = prev.kills;
        distractionsLeft = tutorialMode ? 0 : prev.distractionsLeft;
        hasSidearm = prev.hasSidearm;
        ammo = prev.ammo;
        hasRifle = prev.hasRifle;
        rifleAmmo = prev.rifleAmmo;
        hasBaton = prev.hasBaton;
        held = prev.held;
        hints.markSeen(prev.hints.seenString());
        if (prev.rolandFree) {
            hasRoland = true;
            rolandFree = true;
            // beside the player, on whichever side is clear (the arrival may be against a wall)
            float[][] sides = {{-1f, 0f}, {1f, 0f}, {0f, 1f}, {0f, -1f}, {-1f, 1f}, {-1f, -1f}, {1f, 1f}, {1f, -1f}};
            rolandX = px;
            rolandY = py;
            for (float dist : new float[]{ROLAND_FOLLOW_DISTANCE, ROLAND_FOLLOW_DISTANCE * 0.6f}) {
                boolean placed = false;
                for (float[] sd : sides) {
                    float len = (float) Math.sqrt(sd[0] * sd[0] + sd[1] * sd[1]);
                    float tx = px + sd[0] / len * dist;
                    float ty = py + sd[1] / len * dist;
                    if (!hitsWall(tx, ty, PLAYER_RADIUS + 1f)) {
                        rolandX = tx;
                        rolandY = ty;
                        placed = true;
                        break;
                    }
                }
                if (placed) {
                    break;
                }
            }
        }
    }

    /** The run without Roland: no one in the cage across, no lock on it, and nothing to wait for. */
    public void dropRoland() {
        hasRoland = false;
        rolandFree = false;
        for (int i = interacts.size() - 1; i >= 0; i--) {
            if ("roland".equals(interacts.get(i).type)) {
                interacts.remove(i);
            }
        }
        Lock cage = lockNamed("R");
        if (cage != null) {
            locks.remove(cage);
            walls.remove(cage.rect);
            rebuildNav();
        }
    }

    private void rebuildNav() {
        grown.clear();
        buildNav();
        rebuildLights(false);                           // an opened door lets the light through
    }

    /** Works out which floor each fixture lights (what it can see, within its radius) and which cells have gone dark. */
    private void rebuildLights(boolean snap) {
        java.util.Arrays.fill(lightCover, 0);
        java.util.Arrays.fill(lightOnCount, 0);
        for (Light l : lights) {
            int c0 = Math.max(0, (int) ((l.x - l.radius) / FOG_CELL));
            int c1 = Math.min(fogCols - 1, (int) ((l.x + l.radius) / FOG_CELL));
            int r0 = Math.max(0, (int) ((l.y - l.radius) / FOG_CELL));
            int r1 = Math.min(fogRows - 1, (int) ((l.y + l.radius) / FOG_CELL));
            int[] cells = new int[(c1 - c0 + 1) * (r1 - r0 + 1)];
            float[] weights = new float[cells.length];
            int n = 0;
            for (int r = r0; r <= r1; r++) {
                for (int c = c0; c <= c1; c++) {
                    float cx = (c + 0.5f) * FOG_CELL;
                    float cy = (r + 0.5f) * FOG_CELL;
                    float d = (float) Math.sqrt((cx - l.x) * (cx - l.x) + (cy - l.y) * (cy - l.y));
                    if (d > l.radius) {
                        continue;
                    }
                    boolean blocked = false;
                    for (Rect w : walls) {
                        if (w.blocks(l.x, l.y, cx, cy)) {
                            blocked = true;
                            break;
                        }
                    }
                    if (blocked) {
                        continue;
                    }
                    cells[n] = r * fogCols + c;
                    weights[n] = 1f - d / l.radius;
                    n++;
                }
            }
            l.cells = java.util.Arrays.copyOf(cells, n);
            l.weights = java.util.Arrays.copyOf(weights, n);
            for (int i = 0; i < n; i++) {
                lightCover[l.cells[i]]++;
                if (l.on) {
                    lightOnCount[l.cells[i]]++;
                }
            }
        }
        if (snap) {
            for (int i = 0; i < darkAmt.length; i++) {
                darkAmt[i] = lightDark(i) ? 1f : 0f;
            }
        } else {
            darkAnimating = true;
        }
    }

    /** Moves a fixture from its tile to the face of the nearest wall (within a tile), so it sits flush on it. */
    private void mountLight(Light l) {
        float best = 24f;
        float fx = 0f;
        float fy = 0f;
        boolean found = false;
        for (Rect w : walls) {
            float cx = Math.max(w.x, Math.min(l.x, w.x + w.w));
            float cy = Math.max(w.y, Math.min(l.y, w.y + w.h));
            float d = (float) Math.sqrt((cx - l.x) * (cx - l.x) + (cy - l.y) * (cy - l.y));
            if (d < best) {
                best = d;
                fx = cx;
                fy = cy;
                found = true;
            }
        }
        if (!found || best < 0.001f) {
            return;
        }
        float nx = (l.x - fx) / best;
        float ny = (l.y - fy) / best;
        l.x = fx + nx * 3f;                             // the face of the wall, a hair out
        l.y = fy + ny * 3f;
        l.mounted = true;
    }

    /** True if a light that is on lights this point. */
    public boolean lit(float x, float y) {
        int c = (int) (x / FOG_CELL);
        int r = (int) (y / FOG_CELL);
        return c >= 0 && r >= 0 && c < fogCols && r < fogRows && lightOnCount[r * fogCols + c] > 0;
    }

    private boolean lightDark(int i) {
        return lightCover[i] > 0 && lightOnCount[i] == 0;
    }

    /** Adds a fixture (tests and tools). */
    public Light addLight(float x, float y, float radius) {
        Light l = new Light(x, y, radius);
        lights.add(l);
        rebuildLights(true);
        return l;
    }

    // ---- blood ----
    private Blood blob(float x, float y, int kind, float max, float growTime, float stretch, float rot) {
        Blood b = new Blood();
        b.x = x;
        b.y = y;
        b.kind = kind;
        b.maxSize = max * (0.85f + 0.3f * bloodRng.nextFloat());
        b.size = b.maxSize * 0.3f;
        b.growTime = growTime;
        b.stretch = stretch;
        b.rot = Float.isNaN(rot) ? bloodRng.nextFloat() * 6.2832f : rot;
        b.seq = bloodSeq++;
        float[] raw = new float[BLOOD_LOBES];
        for (int i = 0; i < BLOOD_LOBES; i++) {
            raw[i] = 0.62f + 0.62f * bloodRng.nextFloat();
        }
        b.lobes = new float[BLOOD_LOBES];
        for (int i = 0; i < BLOOD_LOBES; i++) {
            b.lobes[i] = (raw[(i + BLOOD_LOBES - 1) % BLOOD_LOBES] + 2f * raw[i] + raw[(i + 1) % BLOOD_LOBES]) / 4f;   // smoothed: lumpy, not spiky
        }
        float lo = 9f;
        float hi = 0f;
        float mean = 0f;
        for (float l : b.lobes) {
            lo = Math.min(lo, l);
            hi = Math.max(hi, l);
            mean += l / BLOOD_LOBES;
        }
        if (hi / lo < 1.35f) {
            for (int i = 0; i < BLOOD_LOBES; i++) {
                b.lobes[i] = Math.max(0.4f, mean + (b.lobes[i] - mean) * 1.7f);     // never a near-circle
            }
        }
        blood.add(b);
        if (blood.size() > MAX_BLOOD) {
            blood.remove(0);
        }
        return b;
    }

    /** Test and tool helper: a full-grown blob of blood at a point. */
    public Blood addBlood(float x, float y, int kind, float size) {
        Blood b = blob(x, y, kind, size, 0.1f, 1f, Float.NaN);
        b.size = b.maxSize;
        return b;
    }

    /** A pool under someone who has been badly hurt, with a few drops round it. */
    private void bloodPool(float x, float y, float max) {
        blob(x, y, 0, max, 16f, 1f + 0.4f * bloodRng.nextFloat(), Float.NaN);
        spatter(x, y, 7, 55f, 3.8f);
    }

    /** A few small drops about a point. */
    private void spatter(float x, float y, int count, float spread, float max) {
        for (int i = 0; i < count; i++) {
            double a = bloodRng.nextDouble() * Math.PI * 2.0;
            if (!Float.isNaN(bloodDir) && bloodRng.nextFloat() < 0.75f) {
                a = bloodDir + (bloodRng.nextDouble() - 0.5) * 2.0 * Math.toRadians(55.0);   // most of it goes the way the blow went
            }
            float range = spread * (0.5f + 1.1f * bloodRng.nextFloat());
            float speed = 150f + 130f * bloodRng.nextFloat();
            float wallAt = rayDistance(x, y, (float) Math.toDegrees(a), range);
            boolean hitWall = wallAt < range - 0.5f;
            float dist = hitWall ? Math.max(0f, wallAt - 2f) : range;
            if (dist < 1f) {
                continue;
            }
            droplets.add(new Droplet(x, y, (float) a, speed, dist, max * (0.8f + 0.5f * bloodRng.nextFloat()), hitWall));
        }
    }

    /** A step in fresh blood: whatever has it on its feet leaves prints for a few strides, fainter each time. */
    private void trackBlood(Wet w, float x, float y, Object owner) {
        if (Float.isNaN(w.lastX)) {
            w.lastX = x;
            w.lastY = y;
            return;
        }
        float dx = x - w.lastX;
        float dy = y - w.lastY;
        float moved = (float) Math.sqrt(dx * dx + dy * dy);
        w.lastX = x;
        w.lastY = y;
        if (w.left <= 0 && blood.isEmpty()) {
            return;
        }
        if (inFreshBlood(x, y)) {
            w.left = WET_PRINTS;
        }
        if (w.left <= 0 || moved < 0.01f) {
            return;
        }
        w.dist += moved;
        float heading = (float) Math.atan2(dy, dx);
        while (w.dist >= STRIDE && w.left > 0) {
            w.dist -= STRIDE;
            w.side = -w.side;
            float ox = (float) -Math.sin(heading) * 3.5f * w.side;
            float oy = (float) Math.cos(heading) * 3.5f * w.side;
            Blood print = blob(x + ox, y + oy, 4, 3.4f, 0.1f, 1f, heading);
            print.size = print.maxSize;
            print.fade = (float) w.left / WET_PRINTS;
            print.owner = owner;
            w.left--;
        }
    }

    private boolean inFreshBlood(float x, float y) {
        for (Blood b : blood) {
            if (b.kind == 4 || b.kind == 5 || b.dry >= 0.7f || b.size < 2.5f) {
                continue;
            }
            float dx = x - b.x;
            float dy = y - b.y;
            if (dx * dx + dy * dy <= b.size * b.size * 0.72f) {
                return true;
            }
        }
        return false;
    }

    /** A patrolling or wary guard whose view takes in fresh blood goes into the modified alert, as if he had found a body. */
    private void updateBloodNotice() {
        for (Guard g : guards) {
            if (g.frozen || g.state == GuardState.DEAD || g == grabbed || (g.state != GuardState.PATROL && g.state != GuardState.SUSPICIOUS)) {
                continue;
            }
            for (Blood b : blood) {
                if (b.noticed || b.owner == g || b.dry >= 0.6f || b.size < 2.2f || b.fade < 0.2f) {
                    continue;
                }
                if (Math.hypot(b.x - g.x, b.y - g.y) > BLOOD_NOTICE_RANGE || !seesPoint(g, b.x, b.y)) {
                    continue;
                }
                for (Blood n : blood) {
                    if (Math.hypot(n.x - b.x, n.y - b.y) < 60f) {
                        n.noticed = true;                          // one find covers the stain round it
                    }
                }
                g.lookLeft = 3f;
                g.lookX = b.x;
                g.lookY = b.y;
                speak(g, BLOOD_LINES[rng.nextInt(BLOOD_LINES.length)]);
                becomeSuspicious(g, b.x, b.y, false);
                g.hurry = false;
                g.bodyAlarm = true;
                g.witness = true;
                g.noticeLeft = Math.min(g.noticeLeft, 0.3f);
                notice(g, false);
                hints.event("blood", b.x, b.y, this);
                beginRadio(g, b.x, b.y);
                break;
            }
        }
    }

    private void updateDroplets(float dt) {
        for (int i = droplets.size() - 1; i >= 0; i--) {
            Droplet d = droplets.get(i);
            d.x += d.vx * dt;
            d.y += d.vy * dt;
            d.left -= dt;
            if (d.left <= 0f) {
                droplets.remove(i);
                if (d.wall) {
                    blob(d.x, d.y, 5, d.max, 4f, 1.9f, d.angle + 1.5708f);       // a stain across the wall it hit
                } else {
                    blob(d.x, d.y, 1, d.max, 4f, 1f + 0.8f * bloodRng.nextFloat(), d.angle);
                }
            }
        }
    }

    private void drip(float x, float y) {
        blob(x + (bloodRng.nextFloat() - 0.5f) * 4f, y + (bloodRng.nextFloat() - 0.5f) * 4f, 2, 3.2f, 2f, 1f, Float.NaN);
    }

    private void updateBlood(float dt) {
        updateDroplets(dt);
        for (Blood b : blood) {
            b.age += dt;
            if (b.size < b.maxSize) {
                b.size = Math.min(b.maxSize, b.size + b.maxSize * 0.7f / b.growTime * dt);
            } else if (b.dry < 1f) {
                b.dry = Math.min(1f, b.dry + dt / BLOOD_DRY_TIME);
            }
        }
        // a wounded player on the move leaves drops behind
        if (bleedLeft > 0f) {
            bleedLeft = Math.max(0f, bleedLeft - dt);
            if (Math.hypot(px - dripX, py - dripY) >= 26f) {
                drip(px, py);
                dripX = px;
                dripY = py;
            }
        }
    }

    /** Smashes a fixture: the floor it lit goes dark, and the noise carries. False if it was already out. */
    public boolean breakLight(Light l, boolean byPlayer) {
        if (!l.on) {
            return false;
        }
        l.on = false;
        l.breakAge = 0f;
        brokenLights++;
        for (int idx : l.cells) {
            lightOnCount[idx]--;
        }
        darkAnimating = true;
        ripples.add(new Ripple(l.x, l.y, LIGHT_NOISE_RADIUS, 0.7f, byPlayer));
        makeNoise(l.x, l.y, LIGHT_NOISE_RADIUS, false);
        hints.event("lightBroken", l.x, l.y, this);
        return true;
    }

    /** 0 (lit or never lit) to 1 (dark) for a fog-grid cell: eased, for drawing. */
    public float darkness(int c, int r) {
        if (c < 0 || r < 0 || c >= fogCols || r >= fogRows) {
            return 0f;
        }
        return darkAmt[r * fogCols + c];
    }

    private boolean noDarkness() {
        return darks.isEmpty() && brokenLights == 0;
    }

    /** A guard studying something narrows and shortens his view, as if his eyes were on it (and sometimes up or down). */
    private void updateFocus(float dt) {
        for (Guard g : guards) {
            if (g.frozen || g.state == GuardState.DEAD) {
                g.focus = approach(g.focus, 0f, FOCUS_RATE * dt);
                continue;
            }
            g.lookLeft = Math.max(0f, g.lookLeft - dt);
            if (g.lookLeft > 0f && Math.hypot(g.lookX - g.x, g.lookY - g.y) < 60f) {
                g.lookLeft = Math.max(g.lookLeft, 1.2f);        // at the thing: he keeps looking
            }
            boolean on = g.huddle || g.chat || g.chastise != null || g.lookLeft > 0f;
            for (int i = 0; i < exchanges.size() && !on; i++) {
                on = exchanges.get(i).finder == g || exchanges.get(i).sleeper == g;
            }
            if (!on && g.bodyAlarm && g.state == GuardState.SUSPICIOUS) {
                // on his way to a body, and nearly there
                for (Guard b : guards) {
                    if (b.state == GuardState.DEAD && !b.hidden && Math.hypot(b.x - g.targetX, b.y - g.targetY) < 40f
                            && Math.hypot(b.x - g.x, b.y - g.y) < 110f) {
                        on = true;
                        break;
                    }
                }
            }
            g.focus = approach(g.focus, on ? 1f : 0f, FOCUS_RATE * dt);
        }
    }

    private void updateLights(float dt) {
        for (Light l : lights) {
            if (!l.on) {
                l.breakAge += dt;
                l.glow = approach(l.glow, 0f, LIGHT_FADE_PER_SEC * dt);
            }
        }
        if (darkAnimating) {
            boolean moving = false;
            float stepAmt = DARK_FADE_PER_SEC * dt;
            for (int i = 0; i < darkAmt.length; i++) {
                float t = lightDark(i) ? 1f : 0f;
                if (darkAmt[i] != t) {
                    darkAmt[i] = approach(darkAmt[i], t, stepAmt);
                    moving = true;
                }
            }
            darkAnimating = moving;
        }
        // a patrolling guard who looks at a broken light stops to see to it
        for (Light l : lights) {
            if (l.on || l.noticed) {
                continue;
            }
            for (Guard g : guards) {
                if (g.frozen || g.state != GuardState.PATROL) {
                    continue;
                }
                if (seesPoint(g, l.x, l.y)) {
                    l.noticed = true;
                    g.lookLeft = 3f;
                    g.lookX = l.x;
                    g.lookY = l.y;
                    becomeSuspicious(g, l.x, l.y, false);
                    speak(g, LIGHT_LINES[rng.nextInt(LIGHT_LINES.length)]);
                    break;
                }
            }
        }
    }

    private Lock lockNamed(String name) {
        for (Lock l : locks) {
            if (l.name.equals(name)) {
                return l;
            }
        }
        return null;
    }

    private void open(Lock l) {
        l.open = true;
        sound("unlock", l.rect.x + l.rect.w / 2f, l.rect.y + l.rect.h / 2f);
        if ("X".equals(l.name)) {
            startBreakout();
        }
        if (!l.auto) {
            l.wallIn = false;
            walls.remove(l.rect);
        } else {
            l.slide = 0f;
            l.holdLeft = DOOR_HOLD;
            navIgnore.add(l.rect);                      // guards plan their routes through it; it opens when they get there
        }
        rebuildNav();
    }

    private static boolean isLessonDoor(String n) {
        return n.length() == 1 && n.charAt(0) >= '1' && n.charAt(0) <= '8';
    }

    private static boolean isCageName(String n) {
        return n.length() == 1 && n.charAt(0) >= 'a' && n.charAt(0) <= 'z';
    }

    /** A wall (not a door) at a point. */
    private boolean solidAt(float x, float y) {
        for (Rect w : walls) {
            boolean door = false;
            for (Lock lk : locks) {
                door |= lk.rect == w;
            }
            if (!door && x >= w.x && x <= w.x + w.w && y >= w.y && y <= w.y + w.h) {
                return true;
            }
        }
        return false;
    }

    private static float rectDist(Rect r, float x, float y) {
        float dx = Math.max(Math.max(r.x - x, 0f), x - (r.x + r.w));
        float dy = Math.max(Math.max(r.y - y, 0f), y - (r.y + r.h));
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /** Whoever opens a door by coming near: the player, Roland once he is free, any guard on his feet. */
    private boolean actorNear(Rect r, float reach) {
        if (rectDist(r, px, py) <= reach) {
            return true;
        }
        if (hasRoland && rolandFree && rectDist(r, rolandX, rolandY) <= reach) {
            return true;
        }
        for (Ally a : allies) {
            if (a.freed && !a.dead && rectDist(r, a.x, a.y) <= reach) {
                return true;
            }
        }
        for (Guard g : guards) {
            if (!g.frozen && g.state != GuardState.DEAD && rectDist(r, g.x, g.y) <= reach) {
                return true;
            }
        }
        return false;
    }

    /** Anyone, or any body, standing in the doorway. */
    private boolean doorwayBlocked(Rect r) {
        if (r.overlapsCircle(px, py, PLAYER_RADIUS + 3f)) {
            return true;
        }
        if (hasRoland && r.overlapsCircle(rolandX, rolandY, PLAYER_RADIUS + 3f)) {
            return true;
        }
        for (Ally a : allies) {
            if (a.freed && !a.dead && r.overlapsCircle(a.x, a.y, allyRadius(a) + 3f)) {
                return true;
            }
        }
        for (Guard g : guards) {
            if (g.state == GuardState.DEAD && g.hidden) {
                continue;
            }
            if (r.overlapsCircle(g.x, g.y, GUARD_RADIUS + 3f)) {
                return true;
            }
        }
        return false;
    }

    private void updateDoors(float dt) {
        for (Lock l : locks) {
            if (!l.open || !l.auto) {
                continue;
            }
            boolean blocked = doorwayBlocked(l.rect);
            if (blocked || actorNear(l.rect, DOOR_TRIGGER)) {
                l.holdLeft = DOOR_HOLD;
                l.slide = Math.min(1f, l.slide + dt / DOOR_TIME);
            } else if (l.slide > 0f) {
                l.holdLeft -= dt;
                if (l.holdLeft <= 0f) {
                    l.slide = Math.max(0f, l.slide - dt / DOOR_TIME);
                }
            }
            boolean wantWall = l.slide < 0.9f;
            if (wantWall != l.wallIn) {
                l.wallIn = wantWall;
                if (wantWall) {
                    walls.add(l.rect);
                } else {
                    walls.remove(l.rect);
                }
                rebuildNav();
            }
        }
    }

    /** Holding the interact key beside a cage door works its latch loose. */
    private void updateOverride(float dt) {
        Lock target = null;
        if (questioning && grabbed == null && carrying == null && status == Status.RUNNING) {
            for (Lock l : locks) {
                if (!l.open && (isCageName(l.name) || ("V".equals(l.name) && hasTool)) && rectDist(l.rect, px, py) <= INTERACT_RANGE) {
                    target = l;
                    break;
                }
            }
        }
        if (target != null && "V".equals(target.name) && beingWatched()) {
            strikeWatch();                                      // he sees the player at the grate
            target = null;
        }
        if (target == null) {
            overrideProgress = Math.max(0f, overrideProgress - 2f * dt);
            if (overrideProgress == 0f) {
                overrideLock = null;
            }
            return;
        }
        if (overrideLock != target) {
            overrideLock = target;
            overrideProgress = 0f;
        }
        overrideProgress += dt;
        if (overrideProgress >= overrideNeed()) {
            overrideProgress = 0f;
            overrideLock = null;
            open(target);
            if ("V".equals(target.name)) {
                barks.add("The last screw drops into your palm. The grate comes away, and behind it is a duct, just wide enough to crawl along.");
                toast("Vent open");
            }
            boolean newlyFreed = false;
            for (Ally a : allies) {
                if (target.name.equals(a.lock) && !a.freed) {
                    a.freed = true;
                    barks.add("The latch gives. " + a.name + " steps out of the cage.");
                    int slotIdx = allies.indexOf(a);
                    // two rows if there are many, kept well back from the door so it can shut
                    a.holdX = rallyX >= 0f ? rallyX + ((slotIdx % 6) - 2.5f) * 40f : a.x;
                    a.holdY = rallyX >= 0f ? rallyY + (slotIdx / 6) * 28f : a.y;
                    newlyFreed = true;
                    if (allyBreakout) {
                        joinAlly(a);
                    }
                }
            }
            if (newlyFreed) {
                ensureRolandAlly();                             // after the loop: it adds to the list
            }
        }
    }

    public int alliesAlive() {
        int n = 0;
        for (Ally a : allies) {
            n += a.freed && !a.dead && !a.isRoland ? 1 : 0;
        }
        return n;
    }

    // ---- the breakout ----
    /** The east door is open: every freed prisoner comes out and falls in behind the player. */
    private void startBreakout() {
        allyBreakout = true;
        ensureRolandAlly();
        for (Ally a : allies) {
            if (a.freed && !a.dead) {
                joinAlly(a);
            }
        }
    }

    private void joinAlly(Ally a) {
        if (a.joined) {
            return;
        }
        a.joined = true;
        speakAlly(a, a.armored ? "The suit is sealed. I will go first." : (a.armed ? "I have a weapon. Go ahead, I will cover you." : "I have nothing to shoot with. I will stay behind you."));
    }

    /** Roland, if he is with the player, becomes one of the party: his own place in the room, an errand to the armory, a gun, a share of the fight. */
    public void ensureRolandAlly() {
        if (rolandAlly != null || !hasRoland || !rolandFree) {
            return;
        }
        Ally r = new Ally("armaa_sfo_roland", rolandX, rolandY, null);
        r.name = "Roland";
        r.isRoland = true;
        r.freed = true;
        r.holdX = rallyX >= 0f ? rallyX : rolandX;
        r.holdY = rallyX >= 0f ? rallyY - 26f : rolandY;
        rolandAlly = r;
        allies.add(r);
        if (allyBreakout) {
            joinAlly(r);
        }
    }

    // ---- the drone and its hints ----
    /** Which part of the game this is: "any", "cell" (the cell block, with its door D) or "hub" (the hub, with its east door X). */
    public boolean hintScope(String scope) {
        if (tutorialMode && !scope.startsWith("tutorial")) {
            return false;                                       // in the tutorial IK says only what is about the room the player is in
        }
        if (scope.startsWith("tutorial:")) {
            return tutorialMode && hintInRoom(scope.substring(9));
        }
        if ("cell".equals(scope)) {
            return lockNamed("D") != null;
        }
        if ("hub".equals(scope)) {
            return lockNamed("X") != null;
        }
        if ("start".equals(scope)) {
            return lockNamed("V") != null;
        }
        if ("tutorial".equals(scope)) {
            return tutorialMode;
        }
        return true;
    }

    /** A guard is hunting the player, so a quiet hint waits. */
    public boolean hintHunted() {
        for (Guard g : guards) {
            if (g.state == GuardState.ALERT && !g.frozen) {
                return true;
            }
        }
        return false;
    }

    /** The named conditions the hints can wait for. */
    public boolean hintState(String name) {
        if ("ventReady".equals(name)) {
            Lock v = lockNamed("V");
            if (v != null) {
                return hasTool && !v.open;
            }
            return ventExit != null && hasTool && !ventOpen;
        }
        if ("inShaft".equals(name)) {
            return crawling();
        }
        if ("cagesOpen".equals(name)) {
            Lock n = lockNamed("N");
            return n != null && n.open;
        }
        if ("armoryOpen".equals(name)) {
            Lock a = lockNamed("S");
            return a != null && a.open;
        }
        if ("twoWeapons".equals(name)) {
            return (hasSidearm ? 1 : 0) + (hasRifle ? 1 : 0) + (hasBaton ? 1 : 0) >= 2;
        }
        if ("exitUnsealed".equals(name)) {
            return fightStarted && !exitSealed();
        }
        return false;
    }

    /** Where a hint target is now ({x, y}), or null: lock:X, guard:key|key2|any, interact:TYPE, pickup:TYPE, exit, point:col,row. */
    public float[] hintTarget(String spec) {
        if (spec == null || spec.isEmpty()) {
            return null;
        }
        if (spec.startsWith("lock:")) {
            Lock l = lockNamed(spec.substring(5));
            return l == null ? null : new float[]{l.rect.x + l.rect.w / 2f, l.rect.y + l.rect.h / 2f};
        }
        if (spec.startsWith("guard:")) {
            String kind = spec.substring(6);
            Guard best = null;
            float bd = Float.MAX_VALUE;
            for (Guard g : guards) {
                if (g.state == GuardState.DEAD || g.frozen) {
                    continue;
                }
                boolean ok = "key".equals(kind) ? g.hasKey : ("key2".equals(kind) ? g.hasKey2 : g.state == GuardState.PATROL);
                float d = (float) Math.hypot(g.x - px, g.y - py);
                if (ok && d < bd) {
                    bd = d;
                    best = g;
                }
            }
            return best == null ? null : new float[]{best.x, best.y};
        }
        if (spec.startsWith("interact:")) {
            String type = spec.substring(9);
            float[] best = null;
            float bd = Float.MAX_VALUE;
            for (armaa_stealthLevel.Interact it : interacts) {
                float d = (float) Math.hypot(it.x - px, it.y - py);
                if (type.equals(it.type) && d < bd) {
                    bd = d;
                    best = new float[]{it.x, it.y};
                }
            }
            return best;
        }
        if (spec.startsWith("pickup:")) {
            String type = spec.substring(7);
            float[] best = null;
            float bd = Float.MAX_VALUE;
            for (armaa_stealthLevel.Pickup pk : pickups) {
                float d = (float) Math.hypot(pk.x - px, pk.y - py);
                if (type.equals(pk.type) && d < bd) {
                    bd = d;
                    best = new float[]{pk.x, pk.y};
                }
            }
            return best;
        }
        if ("exit".equals(spec)) {
            return exit == null ? null : new float[]{exit.x + exit.w / 2f, exit.y + exit.h / 2f};
        }
        if (spec.startsWith("point:")) {
            String[] a = spec.substring(6).split(",");
            float rows = height / armaa_stealthLevel.TILE;                   // row 0 is the top of the map
            return new float[]{Float.parseFloat(a[0]) * armaa_stealthLevel.TILE + armaa_stealthLevel.TILE / 2f,
                (rows - 1f - Float.parseFloat(a[1])) * armaa_stealthLevel.TILE + armaa_stealthLevel.TILE / 2f};
        }
        return null;
    }

    /** The centre of the cover nearest the player, or null if the level has none. */
    private float[] nearestCover() {
        float[] best = null;
        float bd = Float.MAX_VALUE;
        for (Rect c : coverRects()) {
            float cx = c.x + c.w / 2f;
            float cy = c.y + c.h / 2f;
            float d = (float) Math.hypot(cx - px, cy - py);
            if (d < bd) {
                bd = d;
                best = new float[]{cx, cy};
            }
        }
        return best;
    }

    /**
     * The drone hovers at the player's shoulder: a spring toward a spot beside them, on whichever side is clear, kept out of walls. It looks
     * at the thing it is talking about while a hint is up.
     */
    private void updateDrone(float dt) {
        if (droneDown && !drone.awake) {
            if (!drone.placed) {
                drone.x = px + 24f;
                drone.y = py - 8f;
                if (hitsWall(drone.x, drone.y, 4f)) {
                    drone.x = px - 24f;
                }
                drone.placed = true;
            }
            if (time < DRONE_WAKE_TIME) {
                drone.lift = 0f;
                return;                                         // lying on the floor, dark
            }
            drone.awake = true;
        }
        drone.lift = Math.min(1f, drone.lift + 1.1f * dt);
        float[][] sides = {{-13f, -11f}, {13f, -11f}, {-13f, 11f}, {13f, 11f}, {0f, -16f}, {0f, 0f}};
        float wx = px;
        float wy = py;
        for (float[] sd : sides) {
            if (!hitsWall(px + sd[0], py + sd[1], 4f)) {
                wx = px + sd[0];
                wy = py + sd[1];
                break;
            }
        }
        if (!drone.placed) {
            drone.x = wx;
            drone.y = wy;
            drone.placed = true;
        }
        drone.vx += ((wx - drone.x) * 16f - drone.vx * 6.5f) * dt;
        drone.vy += ((wy - drone.y) * 16f - drone.vy * 6.5f) * dt;
        drone.x += drone.vx * dt;
        drone.y += drone.vy * dt;
        if (hitsWall(drone.x, drone.y, 3f)) {
            drone.x += (px - drone.x) * Math.min(1f, 8f * dt);       // never lingers in a wall
            drone.y += (py - drone.y) * Math.min(1f, 8f * dt);
            drone.vx = 0f;
            drone.vy = 0f;
        }
        armaa_stealthHints.View hv = hints.view(this);
        float want = drone.face;
        if (hv != null && hv.hasTarget) {
            want = angleTo(drone.x, drone.y, hv.tx, hv.ty);
        } else if (Math.hypot(drone.vx, drone.vy) > 12f) {
            want = angleTo(0f, 0f, drone.vx, drone.vy);
        }
        drone.face = turnToward(drone.face, want, 420f * dt);
    }

    /** Seconds since the section started, for hints that wait. */
    public float hintTime() {
        return time;
    }

    /** The player is in the vent shaft: slower, quiet, nothing to fight with. */
    public boolean crawling() {
        for (Rect c : crawls) {
            if (px >= c.x && px <= c.x + c.w && py >= c.y && py <= c.y + c.h) {
                return true;
            }
        }
        return false;
    }

    /** The player is in his cell, where the guards pay him no mind. */
    public boolean playerConfined() {
        return cellRect != null && px >= cellRect.x && px <= cellRect.x + cellRect.w && py >= cellRect.y && py <= cellRect.y + cellRect.h;
    }

    /** How long the hold on whatever the player is working on takes. */
    public float overrideNeed() {
        return overrideLock != null && "V".equals(overrideLock.name) ? VENT_TIME : OVERRIDE_TIME;
    }

    /** The guard stopped at the cell front has the player in his view. */
    private boolean watchSees(Guard g) {
        return playerConfined() && distToPlayer(g) <= 300f && Math.abs(angleDiff(g.facing, angleTo(g.x, g.y, px, py))) <= 50f && clearLine(g);
    }

    private boolean beingWatched() {
        for (Guard g : guards) {
            if (g.watching > 0f && g.state == GuardState.PATROL && watchSees(g)) {
                return true;
            }
        }
        return false;
    }

    /** He catches the player at the grate: the hold is lost, he tells them off and stays a while. No alarm: the cell is where he thinks they are. */
    private void strikeWatch() {
        for (Guard g : guards) {
            if (g.watching > 0f && g.strikeCool <= 0f && g.state == GuardState.PATROL && watchSees(g)) {
                g.strikeCool = 3f;
                g.watching = Math.max(g.watching, 3f);
                overrideProgress = 0f;
                overrideLock = null;
                watchStrikes++;
                speak(g, "(guard) Hey. Get away from there.");
                hints.event("watched", g.x, g.y, this);
                shake = Math.max(shake, 0.3f);
                return;
            }
        }
    }

    private void updateWatch(float dt) {
        for (Guard g : guards) {
            g.strikeCool = Math.max(0f, g.strikeCool - dt);
        }
    }

    // ---- the tutorial ----
    /** A guard who would have sounded the alarm only notices: he turns to look, and the player is told what that would have meant. */
    private void tutorialSpot(Guard g) {
        if (g.state != GuardState.ALERT) {
            g.state = GuardState.SUSPICIOUS;
            g.noticeLeft = 1.2f;
            g.targetX = px;
            g.targetY = py;
            g.path = null;
        }
        notice(g, false);
        if (time - lastSpot > 5f) {
            lastSpot = time;
            speak(g, "(guard) Hm? Who is there?");
            toast("Spotted");
            hints.event("spotted", g.x, g.y, this);
        }
    }

    /** What the player is trying to do in the room they are in, and what they have learned. */
    private String tutorialObjective() {
        for (Object[] z : lessonZones) {
            Rect r = (Rect) z[1];
            if (px >= r.x && px <= r.x + r.w && py >= r.y && py <= r.y + r.h) {
                for (String[] l : armaa_stealthHints.lessons()) {
                    if (l[0].equals(z[0])) {
                        return lessonsDone.contains(l[0]) ? "Done: go on to the next room" : l[2];
                    }
                }
            }
        }
        return lessonsDone.size() >= lessonZones.size() ? "Head for the way out" : "Go on to the next room";
    }

    /** Called when something happens that a lesson waits for (and by the hints, for every event). */
    public void lessonEvent(String name) {
        if (!tutorialMode) {
            return;
        }
        List<String[]> ls = armaa_stealthHints.lessons();
        for (int i = 0; i < ls.size(); i++) {
            if (java.util.Arrays.asList(ls.get(i)[1].split("/")).contains(name) && hintInRoom(ls.get(i)[0])) {
                completeLesson(i, false);                       // only what is done in the lesson's own room counts
            }
        }
    }

    /** A lesson is done: the player is told, and the door at the end of its room (named for its number) unlocks. */
    private void completeLesson(int index, boolean forced) {
        String[] l = armaa_stealthHints.lessons().get(index);
        if (!lessonsDone.add(l[0])) {
            return;
        }
        toast(forced ? "The door unlocks anyway" : l[3]);
        sound("lesson", px, py);
        Lock d = lockNamed(String.valueOf(index + 1));
        if (d != null && !d.open) {
            open(d);
            barks.add("The lock on the door at the end of the room clicks open.");
        }
    }

    /** The player is in the tutorial room of this lesson. */
    public boolean hintInRoom(String id) {
        for (Object[] z : lessonZones) {
            Rect r = (Rect) z[1];
            if (z[0].equals(id) && px >= r.x && px <= r.x + r.w && py >= r.y && py <= r.y + r.h) {
                return true;
            }
        }
        return false;
    }

    private void updateTutorial(float dt) {
        if (!tutorialMode) {
            return;
        }
        for (Object[] tr : trips) {
            float[] t = (float[]) tr[1];
            if (tr[2] == Boolean.FALSE && Math.hypot(px - t[0], py - t[1]) <= t[2]) {
                tr[2] = Boolean.TRUE;
                hints.event((String) tr[0], t[0], t[1], this);
            }
        }
        if (!sparDone) {
            int spars = 0;
            int down = 0;
            for (Guard g : guards) {
                if (g.spar) {
                    spars++;
                    g.beaten |= g.state == GuardState.DEAD;     // knocked out or killed, once: waking up again does not undo it
                    down += g.beaten ? 1 : 0;
                }
            }
            if (spars > 0 && down == spars && hintInRoom("combat")) {       // counted only in the room, so it is not lost if the player is out of it
                sparDone = true;
                hints.event("sparDone", px, py, this);
            }
        }
        // the guards of a room wake when the player comes into it
        for (int z = 0; z < lessonZones.size() && z < roomWoken.length; z++) {
            Object[] zone = lessonZones.get(z);
            if (!roomWoken[z] && hintInRoom((String) zone[0])) {
                roomWoken[z] = true;
                Rect zr = (Rect) zone[1];
                for (Guard g : guards) {
                    if (g.frozen && g.x >= zr.x && g.x <= zr.x + zr.w && g.y >= zr.y && g.y <= zr.y + zr.h) {
                        g.frozen = false;
                    }
                }
            }
        }
        // a room that will not give up its lesson lets the player on after a long time in it; and IK says what to do again if they are slow
        List<String[]> ls = armaa_stealthHints.lessons();
        int here = -1;
        for (int i = 0; i < ls.size(); i++) {
            if (!lessonsDone.contains(ls.get(i)[0]) && hintInRoom(ls.get(i)[0])) {
                here = i;
                roomTime[i] += dt;
                if (roomTime[i] >= ROOM_PATIENCE) {
                    completeLesson(i, true);
                }
            }
        }
        if (here != nudgeRoom || hints.active() != null || hints.queued() > 0) {
            nudgeRoom = here;
            nudgeClock = 0f;
        } else if (here >= 0 && here < ROOM_HINTS.length) {
            nudgeClock += dt;
            if (nudgeClock >= NUDGE_TIME) {
                nudgeClock = 0f;
                hints.repeat(ROOM_HINTS[here]);
            }
        }
        if (!lessonsDone.contains("blood") && hintInRoom("blood")) {          // only counts in the blood room (blood stepped in before, in another room, used to spend it)
            for (Blood b : blood) {
                if (b.kind == 0 && b.size > 6f && Math.hypot(px - b.x, py - b.y) <= b.size * 0.7f) {
                    hints.event("bloodStep", px, py, this);
                    break;
                }
            }
        }
    }

    /** A pool of blood already on the floor, full size. */
    private void presetBlood(float x, float y, float size) {
        Blood b = blob(x, y, 0, size, 16f, 1.1f, Float.NaN);
        b.size = b.maxSize;
        b.age = 20f;
    }

    private boolean wallBetween(float x1, float y1, float x2, float y2) {
        for (Rect w : walls) {
            if (w.blocks(x1, y1, x2, y2)) {
                return true;
            }
        }
        return false;
    }

    /** True while a prisoner who hit this guard is the one he should deal with (he was hit by him more recently than by the player). */
    private boolean allyTakesPriority(Guard g) {
        return g.foeAlly != null && !g.foeAlly.dead && g.foeAlly.freed && g.attackedByAlly > 0f && g.attackedByAlly >= g.attackedByPlayer;
    }

    /**
     * One step of a fight in reach: now and then, between blows, the fighter circles the other a little or steps back. Returns the new position or
     * null (staying put). Keeps its distance while circling, and gives up the move at a wall.
     */
    private float[] duelStep(Duel du, float x, float y, float tx, float ty, float rad, float dt) {
        float dx = tx - x;
        float dy = ty - y;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d < 1f) {
            return null;
        }
        float ux = dx / d;
        float uy = dy / d;
        if (du.time > 0f) {
            du.time -= dt;
            float mx;
            float my;
            if (du.kind == 2) {
                mx = -ux;
                my = -uy;
            } else {
                float pull = Math.max(-0.5f, Math.min(0.5f, (d - du.hold) * 0.08f));
                mx = -uy * du.side + ux * pull;
                my = ux * du.side + uy * pull;
                float ml = (float) Math.sqrt(mx * mx + my * my);
                mx /= ml;
                my /= ml;
            }
            float nx = x + mx * DUEL_SPEED * dt;
            float ny = y + my * DUEL_SPEED * dt;
            if (hitsWall(nx, ny, rad)) {
                du.time = 0f;
                return null;
            }
            return new float[]{nx, ny};
        }
        du.cool -= dt;
        if (du.cool > 0f) {
            return null;
        }
        du.cool = 0.7f + duelRng.nextFloat() * 0.9f;
        float r = duelRng.nextFloat();
        if (r < 0.5f) {
            du.kind = 1;
            du.side = duelRng.nextBoolean() ? 1f : -1f;
            du.hold = d;
            du.time = 0.35f + duelRng.nextFloat() * 0.25f;
        } else if (r < 0.75f) {
            du.kind = 2;
            du.time = 0.2f;
        }
        return null;
    }

    private void duelGuard(Guard g, float tx, float ty, float dt) {
        float[] n = duelStep(g.duel, g.x, g.y, tx, ty, GUARD_RADIUS, dt);
        if (n != null) {
            g.x = n[0];
            g.y = n[1];
        }
        g.facing = turnToward(g.facing, angleTo(g.x, g.y, tx, ty), CHASE_TURN_RATE * dt);
    }

    private void duelAlly(Ally a, float tx, float ty, float dt) {
        float[] n = duelStep(a.duel, a.x, a.y, tx, ty, allyRadius(a), dt);
        if (n != null) {
            a.x = n[0];
            a.y = n[1];
        }
        a.facing = turnToward(a.facing, angleTo(a.x, a.y, tx, ty), CHASE_TURN_RATE * dt);
    }

    /** The nearest guard hunting, within range and in the clear, for an armed ally to shoot at. */
    private Guard allyFoe(Ally a) {
        if (a.attackedTime > 0f && a.attacker != null && a.attacker.state != GuardState.DEAD && !a.attacker.frozen
                && Math.hypot(a.attacker.x - a.x, a.attacker.y - a.y) <= ALLY_RANGE && !wallBetween(a.x, a.y, a.attacker.x, a.attacker.y)) {
            return a.attacker;
        }
        Guard best = null;
        float bestD = ALLY_RANGE;
        for (Guard g : guards) {
            if (g.state == GuardState.DEAD || g.frozen || g == grabbed) {
                continue;
            }
            float d = (float) Math.hypot(g.x - a.x, g.y - a.y);
            if (d > bestD) {
                continue;
            }
            boolean blocked = false;
            for (Rect w : walls) {
                if (w.blocks(a.x, a.y, g.x, g.y)) {
                    blocked = true;
                    break;
                }
            }
            if (!blocked) {
                best = g;
                bestD = d;
            }
        }
        return best;
    }

    private void fireAllyBullet(Ally a) {
        a.flash = 1f;
        sound("shot", a.x, a.y);
        float angle = angleTo(a.x, a.y, a.aimX, a.aimY) + stray(ALLY_SPREAD_DEG, a.recoilDeg);
        a.recoilDeg += RECOIL_DEG;
        Bullet b = new Bullet(a.x, a.y, angle, true, GUARD_BULLET_SPEED, ALLY_RANGE + 80f);
        b.fromAlly = true;
        b.damage = ALLY_BULLET_DAMAGE;
        b.x += b.dx * 12f;
        b.y += b.dy * 12f;
        bullets.add(b);
        shots.add(new Shot(a.x, a.y, a.x + b.dx * 16f, a.y + b.dy * 16f, true));
        Ripple boom = new Ripple(a.x, a.y, SHOT_NOISE_RADIUS, 1f, true);
        boom.shot = true;
        ripples.add(boom);
    }

    /** How big an ally is: the suit is bigger than a person. */
    public float allyRadius(Ally a) {
        return a.armored ? ARMOR_RADIUS : PLAYER_RADIUS;
    }

    /** The suit is still on its rack. */
    public boolean suitAvailable() {
        return suitSpot != null && suitClaimant == null;
    }

    private void hurtAlly(Ally a) {
        hurtAlly(a, ALLY_HIT_DAMAGE);
    }

    private void hurtAlly(Ally a, int damage) {
        if (a.isRoland && a.hp <= ALLY_HIT_DAMAGE) {
            return;                                             // he is only knocked about: out of the fight, not dead
        }
        a.hp -= damage;
        popup(a.x, a.y, a.hp <= 0 ? "Down" : "-" + damage, POP_DAMAGE);
        shots.add(new Shot(a.x, a.y, a.x, a.y, true));
        spatter(a.x, a.y, 3, 10f, 4f);
        if (a.hp <= 0 && !a.dead) {
            if (!a.isRoland) {
                bloodPool(a.x, a.y, 22f);
            }
            a.dead = true;
            a.burstLeft = 0;
            barks.add(a.name + " is down.");
        }
    }

    /** Walks an ally toward a point, round the walls if need be; the same collision as the player. */
    /** True if a body of this radius can walk the line without touching a wall (unlocked doors count as open). */
    private boolean lineFreeR(float x1, float y1, float x2, float y2, float r) {
        if (r <= GUARD_RADIUS) {
            return lineFree(x1, y1, x2, y2);
        }
        for (Rect w : walls) {
            if (navIgnore.contains(w)) {
                continue;
            }
            if (new Rect(w.x - r, w.y - r, w.w + 2 * r, w.h + 2 * r).blocks(x1, y1, x2, y2)) {
                return false;
            }
        }
        return true;
    }

    private void moveAlly(Ally a, float tx, float ty, float dt) {
        float gx = tx;
        float gy = ty;
        a.forcePath = Math.max(0f, a.forcePath - dt);
        if (a.forcePath > 0f || !lineFreeR(a.x, a.y, tx, ty, allyRadius(a) + 1f)) {
            a.pathAge += dt;
            if (a.path == null || a.pathAge > 0.5f) {
                a.path = findPath(a.x, a.y, tx, ty);
                a.pathAge = 0f;
                a.pathIdx = 0;
            }
            if (a.path != null) {
                while (a.pathIdx < a.path.size() && Math.hypot(a.path.get(a.pathIdx)[0] - a.x, a.path.get(a.pathIdx)[1] - a.y) < 8f) {
                    a.pathIdx++;
                }
                if (a.pathIdx < a.path.size()) {
                    gx = a.path.get(a.pathIdx)[0];
                    gy = a.path.get(a.pathIdx)[1];
                }
            }
        } else {
            a.path = null;
        }
        float dx = gx - a.x;
        float dy = gy - a.y;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5f) {
            return;
        }
        float step = Math.min(ALLY_SPEED * (a.armored ? ARMOR_SPEED : 1f) * dt, len);
        float nx = a.x + dx / len * step;
        float ny = a.y + dy / len * step;
        float rad = allyRadius(a);
        float ox = a.x;
        float oy = a.y;
        if (hitsWall(nx, ny, rad)) {
            // blocked: try a few headings either side, so a wide body slides round a corner instead of sticking on it
            double base = Math.atan2(dy, dx);
            boolean moved = false;
            for (double off : new double[]{0.6, -0.6, 1.2, -1.2}) {
                float cx = a.x + (float) Math.cos(base + off) * step;
                float cy = a.y + (float) Math.sin(base + off) * step;
                if (!hitsWall(cx, cy, rad)) {
                    a.x = cx;
                    a.y = cy;
                    moved = true;
                    break;
                }
            }
            if (!moved) {
                if (!hitsWall(nx, a.y, rad)) {
                    a.x = nx;
                }
                if (!hitsWall(a.x, ny, rad)) {
                    a.y = ny;
                }
            }
        } else {
            a.x = nx;
            a.y = ny;
        }
        // he turns toward where he is going, not instantly
        a.facing = turnToward(a.facing, angleTo(a.x, a.y, gx, gy), (a.armored ? ARMOR_TURN_RATE : ALLY_TURN_RATE) * dt);
        if (Math.hypot(a.x - ox, a.y - oy) < 0.25f * step) {
            a.blockedTime += dt;                                // not getting anywhere: stop cutting corners and follow the route
            if (a.blockedTime > 0.6f) {
                a.blockedTime = 0f;
                a.forcePath = 2f;
                a.path = null;
                a.pathAge = 1f;
            }
        } else {
            a.blockedTime = 0f;
        }
        if (a.armored) {
            a.stepDist += (float) Math.hypot(a.x - ox, a.y - oy);
            if (a.stepDist >= ARMOR_STEP) {
                a.stepDist = 0f;
                ripples.add(new Ripple(a.x, a.y, ARMOR_NOISE, 0.8f, false));
                makeNoise(a.x, a.y, ARMOR_NOISE, false);        // the suit is heavy and it rings on the deck
            }
        }
    }

    /** Allies who have joined: an armed one stands and shoots a guard in range; otherwise they keep close behind the player. */
    private void updateAllyUnits(float dt) {
        int slot = 0;
        for (Ally a : allies) {
            if (a.dead) {
                continue;
            }
            if (a.stun > 0f) {
                a.stun -= dt;                                   // reeling from a blow: he does nothing
                a.meleeTime = -1f;
                a.aimTime = 0f;
                a.burstLeft = 0;
                slot++;
                continue;
            }
            if (a.freed && !a.joined) {
                holdAllyStep(a, dt, false);
                continue;
            }
            if (!a.joined) {
                continue;
            }
            if (a.armored && suitMeleeStep(a, dt)) {
                slot++;
                continue;
            }
            a.shotCooldown = Math.max(0f, a.shotCooldown - dt);
            if (a.burstLeft > 0) {
                a.burstTimer -= dt;
                if (a.burstTimer <= 0f) {
                    a.burstLeft--;
                    a.burstTimer = GUARD_BURST_GAP;
                    fireAllyBullet(a);
                }
                a.facing = turnToward(a.facing, angleTo(a.x, a.y, a.aimX, a.aimY), CHASE_TURN_RATE * dt);
                slot++;
                continue;
            }
            float dPlayer = (float) Math.hypot(px - a.x, py - a.y);
            if (fightStarted && allyFightStep(a, slot, dt)) {
                slot++;
                continue;
            }
            if (fightStarted && a.armed) {
                // (no cover to be had) in the fight the armed ones go ahead of the player, spread across the walkway, and stand and shoot there
                float ax = Math.min(px + 80f + 12f * slot, exit.x - 20f);
                float ay = floodY + (slot - 1) * 30f;
                Guard foe2 = allyFoe(a);
                if (foe2 != null && (Math.hypot(ax - a.x, ay - a.y) < 70f || Math.hypot(foe2.x - a.x, foe2.y - a.y) < 90f)) {      // or someone has caught up with him
                    a.aimX = foe2.x;
                    a.aimY = foe2.y;
                    a.facing = turnToward(a.facing, angleTo(a.x, a.y, foe2.x, foe2.y), CHASE_TURN_RATE * dt);
                    if (a.shotCooldown <= 0f) {
                        a.aimTime += dt;
                        if (a.aimTime >= ALLY_AIM) {
                            fireAllyBullet(a);
                            a.aimTime = 0f;
                            a.burstLeft = a.armored ? 2 : 1;
                            a.burstTimer = GUARD_BURST_GAP;
                            a.shotCooldown = a.armored ? ARMOR_COOLDOWN : ALLY_COOLDOWN;
                        }
                    }
                } else if (Math.hypot(ax - a.x, ay - a.y) > 8f) {
                    moveAlly(a, ax, ay, dt);
                }
                slot++;
                continue;
            }
            if (!a.armed && holdAllyStep(a, dt, true)) {
                slot++;                                         // not armed: hits back at whoever attacks him, and goes to the armory if it is open
                continue;
            }
            Guard foe = a.armed && dPlayer < 170f ? allyFoe(a) : null;
            if (foe != null) {
                a.aimX = foe.x;
                a.aimY = foe.y;
                a.facing = turnToward(a.facing, angleTo(a.x, a.y, foe.x, foe.y), CHASE_TURN_RATE * dt);
                if (a.shotCooldown <= 0f) {
                    a.aimTime += dt;
                    if (a.aimTime >= ALLY_AIM) {
                        fireAllyBullet(a);
                        a.aimTime = 0f;
                        a.burstLeft = a.armored ? 2 : 1;
                        a.burstTimer = GUARD_BURST_GAP;
                        a.shotCooldown = a.armored ? ARMOR_COOLDOWN : ALLY_COOLDOWN;
                    }
                }
                slot++;
                continue;
            }
            a.aimTime = 0f;
            float want = (a.armed ? 48f : 72f) + 14f * slot;
            if (dPlayer > want) {
                moveAlly(a, px, py, dt);
            }
            slot++;
        }
    }

    // ---- freed prisoners before the breakout ----
    /** The nearest guard a loose prisoner can see, near their post. */
    private Guard allyHostile(Ally a) {
        if (a.attackedTime > 0f && a.attacker != null && a.attacker.state != GuardState.DEAD && !a.attacker.frozen
                && Math.hypot(a.attacker.x - a.x, a.attacker.y - a.y) <= ALLY_SIGHT && !wallBetween(a.x, a.y, a.attacker.x, a.attacker.y)) {
            return a.attacker;
        }
        Guard best = null;
        float bd = ALLY_SIGHT;
        for (Guard g : guards) {
            if (g.state == GuardState.DEAD || g.frozen || g == grabbed) {
                continue;
            }
            float d = (float) Math.hypot(g.x - a.x, g.y - a.y);
            if (d >= bd) {
                continue;
            }
            boolean blocked = false;
            for (Rect w : walls) {
                if (w.blocks(a.x, a.y, g.x, g.y)) {
                    blocked = true;
                    break;
                }
            }
            if (!blocked) {
                best = g;
                bd = d;
            }
        }
        return best;
    }

    /**
     * A prisoner's blow: the same swing as the player's (an arc in front of them, which a guard on his guard can block and which a second blocked
     * blow breaks) but it kills, not knocks out: at zero hit points a guard is dead.
     */
    public void allyStrike(Ally a) {
        a.swingTime = ALLY_SWING_SHOW;
        a.swingAngle = a.facing;
        sound("swing", a.x, a.y);
        ripples.add(new Ripple(a.x, a.y, MELEE_NOISE_RADIUS, 0.6f, false));
        List<Guard> struck = new ArrayList<Guard>();
        for (Guard g : guards) {
            if (g.state == GuardState.DEAD || g.frozen || g == grabbed) {
                continue;
            }
            float d = (float) Math.hypot(g.x - a.x, g.y - a.y);
            if (d > ALLY_MELEE_REACH + GUARD_RADIUS || Math.abs(angleDiff(a.facing, angleTo(a.x, a.y, g.x, g.y))) > PLAYER_MELEE_ARC_DEG) {
                continue;
            }
            boolean walled = false;
            for (Rect w : walls) {
                if (w.blocks(a.x, a.y, g.x, g.y)) {
                    walled = true;
                    break;
                }
            }
            if (!walled) {
                struck.add(g);
            }
        }
        List<Guard> guarding = new ArrayList<Guard>();
        for (Guard g : struck) {
            if (blocksBlowFrom(g, a.x, a.y)) {
                guarding.add(g);
            }
        }
        if (struck.isEmpty()) {
            return;
        }
        makeNoise(a.x, a.y, MELEE_NOISE_RADIUS, false);
        for (Guard g : struck) {
            g.foeAlly = a;                                      // he knows who is hitting him, and deals with them before anyone else
            g.attackedByAlly = ATTACK_MEMORY;
            if (guarding.contains(g)) {
                blockBlow(g, a.x, a.y, false);
                continue;
            }
            bloodDir = (float) Math.toRadians(angleTo(a.x, a.y, g.x, g.y));
            impactFx((a.x + g.x) / 2f, (a.y + g.y) / 2f, bloodDir, 0);
            g.hp -= ALLY_MELEE_DAMAGE;
            g.bloody = true;
            shots.add(new Shot(g.x, g.y, g.x, g.y, true));
            spatter(g.x, g.y, 3, 12f, 4f);
            if (g.hp <= 0) {
                killGuard(g);
                kills--;                                        // not the player's
                witnessKill(g);
                continue;
            }
            popup(g.x, g.y, "-" + ALLY_MELEE_DAMAGE, POP_DAMAGE);
            knock(g, bloodDir, HIT_PUSH);
            hitMark(g, bloodDir + Math.PI);
            g.stun = Math.max(STUN_TIME, g.brokenTime);
            g.meleeTime = 0f;
            g.aimTime = 0f;
            g.burstLeft = 0;
            if (g.state != GuardState.ALERT) {
                g.foeAlly = a;
                becomeAlert(g);
                g.targetX = a.x;
                g.targetY = a.y;
            }
        }
    }

    /** A freed prisoner on his guard, facing the blow, turns it; a suit needs no guard. */
    private boolean allyBlocks(Ally a, float fx, float fy) {
        return a.freed && !a.dead && !a.armored && a.stun <= 0f && a.brokenTime <= 0f && a.meleeTime < 0f
                && Math.abs(angleDiff(a.facing, angleTo(a.x, a.y, fx, fy))) <= BLOCK_ARC_DEG;
    }

    /** A guard's blow reaches a prisoner: nothing against a suit, blocked or breaking the guard of one who is ready, otherwise a hit point and a shove. */
    private void allyTakesBlow(Ally a, float fx, float fy) {
        double away = Math.atan2(a.y - fy, a.x - fx);
        if (a.armored) {
            popup(a.x, a.y, "No effect", POP_IMMUNE);
            impactFx((a.x + fx) / 2f, (a.y + fy) / 2f, away, 1);
            return;
        }
        if (allyBlocks(a, fx, fy)) {
            a.blockTime = 0.25f;
            a.stun = Math.max(a.stun, BLOCK_STAGGER);
            a.blockHits++;
            a.blockDecay = BLOCK_MEMORY;
            impactFx((a.x + fx) / 2f, (a.y + fy) / 2f, away, 1);
            if (a.blockHits >= BLOCK_BREAK_HITS) {
                a.blockHits = 0;
                a.stun = BREAK_STUN;
                a.brokenTime = BREAK_STUN;
                sound("break", a.x, a.y);
                feel(a.x, a.y, Math.atan2(a.y - py, a.x - px), HIT_STOP_BREAK, 1f, 0.7f);
                knock(a, away, 24f);
                popup(a.x, a.y, "Broken!", POP_BREAK);
            } else {
                knock(a, away, 7f);
                popup(a.x, a.y, "Blocked", POP_BLOCK);
            }
            hitMark(a, away + Math.PI);
            return;
        }
        impactFx((a.x + fx) / 2f, (a.y + fy) / 2f, away, 0);
        knock(a, away, HIT_PUSH);
        hitMark(a, away + Math.PI);
        a.stun = Math.max(STUN_TIME, a.brokenTime);
        hurtAlly(a);
    }

    /** A freed prisoner waits at their place in the room, and goes for any guard they see, bare-handed. */
    // ---- the suit's melee ----
    /** The nearest guard within reach of the suit and in the clear. */
    private Guard suitTarget(Ally a) {
        Guard best = null;
        float bd = SUIT_REACH;
        for (Guard g : guards) {
            if (g.state == GuardState.DEAD || g == grabbed) {
                continue;
            }
            float d = (float) Math.hypot(g.x - a.x, g.y - a.y);
            if (d >= bd) {
                continue;
            }
            boolean blocked = false;
            for (Rect w : walls) {
                if (w.blocks(a.x, a.y, g.x, g.y)) {
                    blocked = true;
                    break;
                }
            }
            if (!blocked) {
                best = g;
                bd = d;
            }
        }
        return best;
    }

    /**
     * An armored ally with a guard within reach turns to him, winds up, and backhands: every guard within SUIT_REACH and
     * SUIT_ARC_DEG of where he faces takes SUIT_DAMAGE and is thrown back (killed outright if that finishes him). True while he is
     * turning, winding up or recovering, so he stands his ground and nothing else he would do this step.
     */
    private boolean suitMeleeStep(Ally a, float dt) {
        a.swingTime = Math.max(0f, a.swingTime - dt);
        a.meleeCooldown = Math.max(0f, a.meleeCooldown - dt);
        if (a.meleeTime >= 0f) {
            a.meleeTime -= dt;
            if (a.meleeTime < 0f) {
                suitStrike(a);
                a.meleeCooldown = SUIT_COOLDOWN;
            }
            return true;
        }
        Guard foe = suitTarget(a);
        if (foe == null) {
            return false;
        }
        float want = angleTo(a.x, a.y, foe.x, foe.y);
        a.facing = turnToward(a.facing, want, CHASE_TURN_RATE * dt);
        if (a.meleeCooldown <= 0f && Math.abs(angleDiff(a.facing, want)) <= 40f) {
            a.meleeTime = SUIT_WINDUP;
        }
        return true;
    }

    private void suitStrike(Ally a) {
        a.swingTime = SUIT_SWING_SHOW;
        a.swingAngle = a.facing;
        ripples.add(new Ripple(a.x, a.y, MELEE_NOISE_RADIUS, 0.7f, false));
        makeNoise(a.x, a.y, MELEE_NOISE_RADIUS, false);
        shake = Math.max(shake, 0.25f);
        for (int i = 0; i < guards.size(); i++) {
            Guard g = guards.get(i);
            if (g.state == GuardState.DEAD || g == grabbed) {
                continue;
            }
            float d = (float) Math.hypot(g.x - a.x, g.y - a.y);
            float ang = angleTo(a.x, a.y, g.x, g.y);
            if (d > SUIT_REACH || Math.abs(angleDiff(a.facing, ang)) > SUIT_ARC_DEG) {
                continue;
            }
            boolean blocked = false;
            for (Rect w : walls) {
                if (w.blocks(a.x, a.y, g.x, g.y)) {
                    blocked = true;
                    break;
                }
            }
            if (blocked) {
                continue;
            }
            bloodDir = (float) Math.toRadians(ang);
            impactFx((a.x + g.x) / 2f, (a.y + g.y) / 2f, bloodDir, 2);
            g.hp -= SUIT_DAMAGE;
            g.bloody = true;
            if (g.hp > 0) {
                popup(g.x, g.y, "-" + SUIT_DAMAGE, POP_DAMAGE);
            }
            shots.add(new Shot(g.x, g.y, g.x, g.y, true));
            spatter(g.x, g.y, 4, 30f, 3.5f);
            if (g.hp <= 0) {
                killGuard(g);
                kills--;                                        // not the player's
                witnessKill(g);
                continue;
            }
            // thrown back along the line of the blow, stopping at a wall
            knock(g, Math.toRadians(ang), SUIT_KNOCK);
            hitMark(g, Math.toRadians(ang) + Math.PI);
            g.stun = SUIT_STUN;
            g.meleeTime = 0f;
            g.aimTime = 0f;
            g.burstLeft = 0;
            g.path = null;
            if (g.state != GuardState.ALERT) {
                g.foeAlly = a;
                becomeAlert(g);
                g.targetX = a.x;
                g.targetY = a.y;
            }
        }
    }

    /**
     * A freed prisoner's own doings: fighting back at a guard who attacks, shooting one if armed, and (the armory open, and no weapon yet) going to
     * arm. True if he did one of them this step. Before the breakout that is all he does and then he goes back to hold his place; once he has joined
     * (`joined`) he goes on with the party instead, so one that was not yet armed when the east door opened still gets his weapon, and
     * one that is attacked still hits back.
     */
    private boolean holdAllyStep(Ally a, float dt, boolean joined) {
        if (a.armored && suitMeleeStep(a, dt)) {
            return true;
        }
        a.meleeCooldown = Math.max(0f, a.meleeCooldown - dt);
        a.shotCooldown = Math.max(0f, a.shotCooldown - dt);
        if (a.burstLeft > 0) {
            a.burstTimer -= dt;
            if (a.burstTimer <= 0f) {
                a.burstLeft--;
                a.burstTimer = GUARD_BURST_GAP;
                fireAllyBullet(a);
            }
            a.facing = turnToward(a.facing, angleTo(a.x, a.y, a.aimX, a.aimY), CHASE_TURN_RATE * dt);
            return true;
        }
        if (a.meleeTime >= 0f) {
            a.meleeTime -= dt;                                  // winding up: stands and swings
            if (a.meleeTime < 0f) {
                allyStrike(a);
                a.meleeCooldown = ALLY_MELEE_COOLDOWN;
            }
            return true;
        }
        Guard foe = allyHostile(a);
        if (foe != null && a.armed) {
            // armed, he shoots any guard he sees
            a.aimX = foe.x;
            a.aimY = foe.y;
            a.facing = turnToward(a.facing, angleTo(a.x, a.y, foe.x, foe.y), CHASE_TURN_RATE * dt);
            if (a.shotCooldown <= 0f) {
                a.aimTime += dt;
                if (a.aimTime >= ALLY_AIM) {
                    fireAllyBullet(a);
                    a.aimTime = 0f;
                    a.burstLeft = a.armored ? 2 : 1;
                    a.burstTimer = GUARD_BURST_GAP;
                    a.shotCooldown = a.armored ? ARMOR_COOLDOWN : ALLY_COOLDOWN;
                }
            }
            return true;
        }
        if (foe != null && Math.hypot(foe.x - a.x, foe.y - a.y) <= ALLY_MELEE_SIGHT) {
            float d = (float) Math.hypot(foe.x - a.x, foe.y - a.y);
            a.facing = turnToward(a.facing, angleTo(a.x, a.y, foe.x, foe.y), CHASE_TURN_RATE * dt);
            if (d <= ALLY_MELEE_REACH * DUEL_RANGE) {
                if (a.meleeCooldown <= 0f && d <= ALLY_MELEE_REACH) {
                    a.meleeTime = ALLY_MELEE_WINDUP;
                } else if (a.meleeCooldown > 0f) {
                    duelAlly(a, foe.x, foe.y, dt);
                }
            } else {
                moveAlly(a, foe.x, foe.y, dt);
            }
            return true;
        }
        a.aimTime = 0f;
        Lock armory = lockNamed("S");
        if (!a.armed && armory != null && armory.open && !armSpots.isEmpty()) {
            // the armory is open: go and arm, waiting out any guard's gaze on the way
            float[] spot = errandSpot(a);
            if (Math.hypot(spot[0] - a.x, spot[1] - a.y) > 8f) {
                a.armTime = 0f;
                if (!seenByGuard(a)) {
                    moveAlly(a, spot[0], spot[1], dt);
                }
                return true;
            }
            if (a.type == 1 && suitSpot != null && suitClaimant == null) {
                suitClaimant = a;                               // the first marine there takes the suit
            }
            boolean suit = suitClaimant == a;
            a.armTime += dt;
            a.facing = turnToward(a.facing, 270f, CHASE_TURN_RATE * dt);
            if (a.armTime >= (suit ? DON_TIME : ARM_TIME)) {
                a.armed = true;
                if (suit) {
                    a.armored = true;
                    a.hp = ARMOR_HP;
                    speakAlly(a, "Suit is sound. Powering up.");
                } else {
                    speakAlly(a, "I have a weapon now.");
                }
            }
            return true;
        }
        if (joined) {
            return false;
        }
        float[] home = a.armored && suitSpot != null ? suitSpot : (a.armed && !armSpots.isEmpty() ? armSpot(a) : new float[]{a.holdX, a.holdY});
        float hx = home[0];
        float hy = home[1];
        if (Math.hypot(hx - a.x, hy - a.y) > 8f) {
            if (!seenByGuard(a)) {
                moveAlly(a, hx, hy, dt);
            }
        }
        return true;
    }

    /** Where a prisoner goes to arm: the suit for the first marine, otherwise a rifle spot. */
    private float[] errandSpot(Ally a) {
        if (a.type == 1 && suitSpot != null && (suitClaimant == null || suitClaimant == a)) {
            return suitSpot;
        }
        return armSpot(a);
    }

    /** Where this prisoner stands to arm and then wait: their own spot in the armory, shifted for the ones beyond the spots there are. */
    private float[] armSpot(Ally a) {
        int idx = allies.indexOf(a);
        float[] base = armSpots.get(idx % armSpots.size());
        return new float[]{base[0] + (idx / armSpots.size()) * 26f, base[1]};
    }

    /** True while any guard's view covers the ally: they stay put and let him look away. */
    private boolean seenByGuard(Ally a) {
        for (Guard g : guards) {
            if (!g.frozen && g.state != GuardState.DEAD && g != grabbed && seesPoint(g, a.x, a.y)) {
                return true;
            }
        }
        return false;
    }

    // ---- allies in cover ----
    private java.util.List<Rect> coverRectsCache;

    /** The crates on the walkway: small walls past the fight line. */
    private java.util.List<Rect> coverRects() {
        if (coverRectsCache == null) {
            coverRectsCache = new ArrayList<Rect>();
            if (fightX >= 0f) {
                for (Rect w : walls) {
                    boolean door = false;
                    for (Lock lk : locks) {
                        door |= lk.rect == w;
                    }
                    if (!door && w.w <= 60f && w.h <= 60f && w.x >= fightX - 20f) {
                        coverRectsCache.add(w);
                    }
                }
            }
        }
        return coverRectsCache;
    }

    /** The nearest hunting guard within reach of the fight, seen or not. */
    private Guard allyThreat(Ally a) {
        Guard best = null;
        float bd = 360f;
        for (Guard g : guards) {
            if (g.state != GuardState.ALERT || g.frozen) {
                continue;
            }
            float d = (float) Math.hypot(g.x - a.x, g.y - a.y);
            if (d < bd) {
                bd = d;
                best = g;
            }
        }
        return best;
    }

    /**
     * The spot behind a crate, hidden from the threat, that suits an ally best. An armed one stays near where he is while
     * guards are about and moves up from crate to crate when none are, never far ahead of the player; an unarmed one keeps behind the
     * player. Two allies do not take the same spot.
     */
    private float[] pickCover(Ally a, float tx, float ty, boolean threatened) {
        float desiredX = threatened ? a.x : Math.min(Math.max(a.x, px) + ADVANCE_STEP, exit.x - 40f);
        float limitX = a.armed ? px + 170f : px - 10f;
        float[] best = null;
        float bestScore = Float.MAX_VALUE;
        for (Rect c : coverRects()) {
            float cx = c.x + c.w / 2f;
            float cy = c.y + c.h / 2f;
            float hx = c.w / 2f + 16f;
            float hy = c.h / 2f + 16f;
            float[][] offs = {{-hx, 0f}, {hx, 0f}, {0f, -hy}, {0f, hy}, {-hx, -hy}, {hx, -hy}, {-hx, hy}, {hx, hy}};
            for (float[] o : offs) {
                float x = cx + o[0];
                float y = cy + o[1];
                if (x > limitX || Math.abs(y - floodY) > 190f || hitsWall(x, y, PLAYER_RADIUS + 1f)) {
                    continue;
                }
                boolean covered = false;
                for (Rect w : walls) {
                    if (w.blocks(x, y, tx, ty)) {
                        covered = true;
                        break;
                    }
                }
                if (!covered) {
                    continue;
                }
                float d = (float) Math.hypot(x - a.x, y - a.y);
                float score = d * 0.5f + Math.abs(x - (a.armed ? desiredX : px - 60f)) * 0.6f;
                for (Ally o2 : allies) {
                    if (o2 != a && o2.joined && !o2.dead && o2.cover != null && Math.hypot(o2.cover[0] - x, o2.cover[1] - y) < 30f) {
                        score += 200f;
                    }
                }
                if (score < bestScore) {
                    bestScore = score;
                    best = new float[]{x, y};
                }
            }
        }
        return best;
    }

    /** From behind cover, the nearest spot to the side with a clear line to the threat: the least he has to show. */
    private float[] peekSpot(Ally a, float tx, float ty) {
        return peekFrom(a.cover[0], a.cover[1], tx, ty, a.x, a.y);
    }

    /** The nearest spot beside the cover point (cx, cy), nearer to (refX, refY) on a tie, from which (tx, ty) is in the clear. */
    private float[] peekFrom(float cx, float cy, float tx, float ty, float refX, float refY) {
        float dx = tx - cx;
        float dy = ty - cy;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1f) {
            return null;
        }
        float perpX = -dy / len;
        float perpY = dx / len;
        for (float off : new float[]{30f, 40f, 52f, 64f}) {
            float[] pick = null;
            float pickD = Float.MAX_VALUE;
            for (int side = -1; side <= 1; side += 2) {
                float x = cx + perpX * side * off;
                float y = cy + perpY * side * off;
                if (hitsWall(x, y, PLAYER_RADIUS + 1f)) {
                    continue;
                }
                boolean seen = true;
                for (Rect w : walls) {
                    // clear from the spot and from a little either side of it, so standing a step short of it still sees
                    if (w.blocks(x, y, tx, ty) || w.blocks(x + perpX * 6f, y + perpY * 6f, tx, ty) || w.blocks(x - perpX * 6f, y - perpY * 6f, tx, ty)) {
                        seen = false;
                        break;
                    }
                }
                float d = (float) Math.hypot(x - refX, y - refY);
                if (seen && d < pickD) {
                    pickD = d;
                    pick = new float[]{x, y};
                }
            }
            if (pick != null) {
                return pick;
            }
        }
        return null;
    }

    private float coverWait() {
        return COVER_WAIT_MIN + rng.nextFloat() * (COVER_WAIT_MAX - COVER_WAIT_MIN);
    }

    /**
     * An ally in the fight works from cover: he sits behind a crate hidden from the guards, and an armed one steps out to the side
     * every second or two to fire a burst, then back. A wounded one stays behind the crate. When no guards are about they move up to
     * the next crate. False if there is no cover on the level, so the caller falls back to the plain advance.
     */
    private boolean allyFightStep(Ally a, int slot, float dt) {
        if (coverRects().isEmpty()) {
            return false;
        }
        if (a.armored && a.hp > ARMOR_HP / 2) {
            return false;                                       // the suit goes first: advances and fires from the open until it is hurt
        }
        Guard threat = allyThreat(a);
        float tx = threat != null ? threat.x : floodX;
        float ty = threat != null ? threat.y : floodY;
        a.coverAge += dt;
        if (a.cover == null || a.coverAge > 0.9f) {
            a.cover = pickCover(a, tx, ty, threat != null);
            a.coverAge = 0f;
            if (a.cover == null) {
                return false;
            }
            if (a.coverWait == 0f) {
                a.coverWait = coverWait();
            }
        }
        if (a.mode == 1 && a.peek != null) {
            if (Math.hypot(a.peek[0] - a.x, a.peek[1] - a.y) > 5f) {
                moveAlly(a, a.peek[0], a.peek[1], dt);
                a.modeTime += dt;
                if (a.modeTime > 1.2f) {
                    a.mode = 0;
                    a.modeTime = 0f;
                }
                return true;
            }
            Guard foe = allyFoe(a);
            if (foe == null) {
                a.modeTime += dt;
                if (a.modeTime > 0.8f) {
                    a.mode = 0;
                    a.modeTime = 0f;
                    a.coverWait = coverWait();
                }
                return true;
            }
            a.aimX = foe.x;
            a.aimY = foe.y;
            a.facing = turnToward(a.facing, angleTo(a.x, a.y, foe.x, foe.y), CHASE_TURN_RATE * dt);
            if (a.shotCooldown <= 0f) {
                a.aimTime += dt;
                if (a.aimTime >= ALLY_PEEK_AIM) {
                    fireAllyBullet(a);
                    a.aimTime = 0f;
                    a.burstLeft = a.armored ? 2 : 1;
                    a.burstTimer = GUARD_BURST_GAP;
                    a.shotCooldown = a.armored ? ARMOR_COOLDOWN : ALLY_COOLDOWN;
                    a.mode = 0;                                 // back behind the crate once the burst is out
                    a.modeTime = 0f;
                    a.coverWait = coverWait();
                }
            }
            return true;
        }
        a.mode = 0;
        if (Math.hypot(a.cover[0] - a.x, a.cover[1] - a.y) > 6f) {
            moveAlly(a, a.cover[0], a.cover[1], dt);
            return true;
        }
        a.modeTime += dt;
        a.facing = turnToward(a.facing, angleTo(a.x, a.y, tx, ty), CHASE_TURN_RATE * dt);
        if (a.armed && threat != null && a.hp > ALLY_HIT_DAMAGE && a.shotCooldown <= 0f && a.modeTime >= a.coverWait) {
            a.peek = peekSpot(a, tx, ty);
            a.modeTime = 0f;
            if (a.peek != null) {
                a.mode = 1;
            }
        }
        return true;
    }

    // ---- guards in cover ----
    /**
     * Where the guards of the fight think the player is: where any of them last had a clear line to him within firing range, from the start
     * (the radio call) until then. If he stays unseen for KNOWN_DRIFT_AFTER seconds the squad's idea of it creeps toward him, so hiding gives
     * him a while but not for ever.
     */
    private float knownX, knownY, knownAge;
    public static final float KNOWN_DRIFT_AFTER = 10f;
    public static final float KNOWN_DRIFT_SPEED = 25f;

    /** Where the fight's guards think the player is, and how many seconds ago they last saw him: {x, y, age}. */
    public float[] knownSpot() {
        return new float[]{knownX, knownY, knownAge};
    }

    private void updateKnown(float dt) {
        boolean spotted = false;
        for (Guard g : guards) {
            if (g.frozen || g.state == GuardState.DEAD || g == grabbed) {
                continue;
            }
            if (Math.hypot(px - g.x, py - g.y) <= GUARD_FIRE_RANGE && clearTo(g, px, py)) {
                spotted = true;
                break;
            }
        }
        if (spotted) {
            knownX = px;
            knownY = py;
            knownAge = 0f;
            return;
        }
        knownAge += dt;
        if (knownAge > KNOWN_DRIFT_AFTER) {
            float dx = px - knownX;
            float dy = py - knownY;
            float d = (float) Math.hypot(dx, dy);
            float step = Math.min(d, KNOWN_DRIFT_SPEED * dt);
            if (d > 0.001f) {
                knownX += dx / d * step;
                knownY += dy / d * step;
            }
        }
    }

    /** The nearest of the player (where he is thought to be) and the joined allies who are still up and in view: {x, y, distance}. */
    private float[] nearestEnemy(Guard g) {
        // the player is where the squad last saw him (see updateKnown), not where he is: hiding behind a crate works
        float[] best = {knownX, knownY, (float) Math.hypot(knownX - g.x, knownY - g.y)};
        for (Ally a : allies) {
            if (!a.joined || a.dead || !clearTo(g, a.x, a.y)) {
                continue;
            }
            float d = (float) Math.hypot(a.x - g.x, a.y - g.y);
            if (d < best[2]) {
                best = new float[]{a.x, a.y, d};
            }
        }
        return best;
    }

    /**
     * The next crate for a guard to work up to: hidden from the nearest enemy, a short run from where he is, standing off about
     * 150 units from the enemy (never closer than he is already, nor wildly farther), preferably with a spot beside it from which he can
     * shoot. Stalled (nowhere to shoot from last time), he looks for a crate off to the side instead, to come at them from another angle.
     */
    private float[] pickFloodCover(Guard g, float[] foe, boolean stalled) {
        float[] best = null;
        float bestScore = Float.MAX_VALUE;
        for (int pass = 0; pass < 2 && best == null; pass++) {
            float reach = pass == 0 ? 230f : 2000f;
            for (Rect c : coverRects()) {
                float cx = c.x + c.w / 2f;
                float cy = c.y + c.h / 2f;
                float hx = c.w / 2f + 16f;
                float hy = c.h / 2f + 16f;
                float[][] offs = {{-hx, 0f}, {hx, 0f}, {0f, -hy}, {0f, hy}, {-hx, -hy}, {hx, -hy}, {-hx, hy}, {hx, hy}};
                for (float[] o : offs) {
                    float x = cx + o[0];
                    float y = cy + o[1];
                    if (Math.abs(y - floodY) > 190f || hitsWall(x, y, GUARD_RADIUS + 1f)) {
                        continue;
                    }
                    float dGuard = (float) Math.hypot(x - g.x, y - g.y);
                    if (dGuard > reach || dGuard < 12f) {
                        continue;
                    }
                    boolean hidden = false;
                    for (Rect w : walls) {
                        if (w.blocks(x, y, foe[0], foe[1])) {
                            hidden = true;
                            break;
                        }
                    }
                    if (!hidden) {
                        continue;
                    }
                    float dFoe = (float) Math.hypot(x - foe[0], y - foe[1]);
                    float score = dGuard * 0.4f + Math.abs(dFoe - 150f) * 0.8f;
                    if (dFoe > foe[2] + 40f) {
                        score += 120f;                          // no falling back
                    }
                    if (peekFrom(x, y, foe[0], foe[1], g.x, g.y) != null) {
                        score -= 60f;
                    } else {
                        score += 80f;
                    }
                    if (stalled) {
                        score -= Math.abs(y - foe[1]) * 0.5f;       // come at them from the side
                        if (g.fcover != null && Math.hypot(g.fcover[0] - x, g.fcover[1] - y) < 60f) {
                            score += 200f;
                        }
                    }
                    for (Guard mate : guards) {
                        if (mate != g && mate.flood && mate.state != GuardState.DEAD && mate.fcover != null
                                && Math.hypot(mate.fcover[0] - x, mate.fcover[1] - y) < 36f) {
                            score += 150f;
                        }
                    }
                    if (score < bestScore) {
                        bestScore = score;
                        best = new float[]{x, y};
                    }
                }
            }
        }
        return best;
    }

    private float fwait() {
        return 0.8f + rng.nextFloat() * 1.0f;
    }

    /**
     * A flood guard fights from cover: he runs from crate to crate (only to ones hidden from the enemy), waits behind one, steps out to the
     * side to fire a volley and goes back. If he has nothing to shoot at from beside his crate he moves on to another, off to the side if
     * he keeps being stalled. False if the level has no cover, so he charges as other guards do.
     */
    private boolean floodGuardStep(Guard g, float dt) {
        if (coverRects().isEmpty()) {
            return false;
        }
        float[] foe = nearestEnemy(g);
        if (g.fmode != 2) {
            g.shotCooldown = Math.max(0f, g.shotCooldown - dt);
        }
        if (g.burstLeft > 0) {
            guardShoot(g, dt);                                  // finish the volley where he stands
            return true;
        }
        g.fage += dt;
        switch (g.fmode) {
            case 0: {
                if (g.fcover == null) {
                    g.fcover = pickFloodCover(g, foe, g.fstall >= 1f);
                    if (g.fcover == null) {
                        return false;
                    }
                }
                float step = ALERT_SPEED * 1.15f * dt;
                float moved = moveToward(g, g.fcover[0], g.fcover[1], ALERT_SPEED * 1.15f, CHASE_TURN_RATE, dt, false);
                g.stuck = moved < 0.3f * step ? g.stuck + dt : 0f;
                if (g.stuck > 1f) {
                    g.stuck = 0f;
                    g.fcover = null;                            // blocked: look for another
                    return true;
                }
                if (Math.hypot(g.fcover[0] - g.x, g.fcover[1] - g.y) < 6f) {
                    g.fmode = 1;
                    g.fage = 0f;
                    g.fwait = fwait();
                }
                return true;
            }
            case 1: {
                g.fwait -= dt;
                g.facing = turnToward(g.facing, angleTo(g.x, g.y, foe[0], foe[1]), CHASE_TURN_RATE * dt);
                if (g.fwait <= 0f) {
                    float[] pk = peekFrom(g.fcover[0], g.fcover[1], foe[0], foe[1], g.x, g.y);
                    if (pk != null && g.shotCooldown <= 0f && foe[2] <= GUARD_FIRE_RANGE) {
                        g.fpeek = pk;
                        g.fmode = 2;
                        g.fage = 0f;
                    } else if (pk == null || foe[2] > GUARD_FIRE_RANGE) {
                        g.fstall += 1f;                         // nothing to shoot at from here: move up, or round the side
                        g.fcover = pickFloodCover(g, foe, g.fstall >= 1f);
                        g.fmode = 0;
                        g.stuck = 0f;
                        if (g.fcover == null) {
                            return false;
                        }
                    } else {
                        g.fwait = 0.3f;                         // still cycling his weapon
                    }
                }
                return true;
            }
            default: {
                if (Math.hypot(g.fpeek[0] - g.x, g.fpeek[1] - g.y) > 5f) {
                    moveToward(g, g.fpeek[0], g.fpeek[1], ALERT_SPEED * 1.15f, CHASE_TURN_RATE, dt, false);
                    if (g.fage > 1.2f) {
                        g.fmode = 1;
                        g.fwait = fwait();
                    }
                    return true;
                }
                if (guardShoot(g, dt, GUARD_PEEK_AIM)) {
                    g.fstall = 0f;
                    return true;
                }
                g.fmode = 1;                                    // out of cooldown or out of sight: back behind the crate
                g.fwait = fwait();
                return true;
            }
        }
    }

    /** Who a guard shoots at: the nearest of the player and the allies he has a clear line to, the player if none. */
    private float[] shootTarget(Guard g) {
        if (allyTakesPriority(g) && Math.hypot(g.foeAlly.x - g.x, g.foeAlly.y - g.y) <= GUARD_FIRE_RANGE && clearTo(g, g.foeAlly.x, g.foeAlly.y)) {
            return new float[]{g.foeAlly.x, g.foeAlly.y};
        }
        float bestX = px;
        float bestY = py;
        if (!fightStarted && g.foeAlly == null) {
            return new float[]{bestX, bestY};
        }
        float bestD = (float) Math.hypot(px - g.x, py - g.y);
        if (!clearTo(g, px, py)) {
            bestD = Float.MAX_VALUE;
        }
        for (Ally a : allies) {
            if ((!a.joined && a != g.foeAlly) || a.dead) {
                continue;
            }
            float d = (float) Math.hypot(a.x - g.x, a.y - g.y);
            if (d < bestD && d <= GUARD_FIRE_RANGE && clearTo(g, a.x, a.y)) {
                bestD = d;
                bestX = a.x;
                bestY = a.y;
            }
        }
        return new float[]{bestX, bestY};
    }

    /** In the fight a guard leads a moving player: he aims where the player will be when the bullet arrives. */
    private float[] lead(Guard g, float[] tgt) {
        if (!fightStarted || tgt[0] != px || tgt[1] != py) {
            return tgt;
        }
        float t = (float) Math.hypot(tgt[0] - g.x, tgt[1] - g.y) / GUARD_BULLET_SPEED;
        return new float[]{px + vx * t, py + vy * t};
    }

    private boolean clearTo(Guard g, float tx, float ty) {
        for (Rect w : walls) {
            if (w.blocks(g.x, g.y, tx, ty)) {
                return false;
            }
        }
        return true;
    }

    /** The walkway fight: crossing the line starts it, and guards pour in from the far end, alert, with weapons free. */
    private void updateFight(float dt) {
        if (fightX < 0f || status != Status.RUNNING) {
            return;
        }
        if (!fightStarted) {
            Lock x = lockNamed("X");
            if (px >= fightX && (x == null || x.open)) {
                fightStarted = true;
                fightTime = 0f;
                lethalKnown = true;
                everSeen = true;
                barks.add("(radio) Breach on the east walkway! All units, weapons free!");
                knownX = px;                                    // the call tells them where
                knownY = py;
                knownAge = 0f;
                raiseCaution(px, py);
                spawnFlood();
                hints.event("fightStarted", floodX, floodY, this);
            }
            return;
        }
        fightTime += dt;
        updateKnown(dt);
        if (floodWaves < floodTotal()) {
            int size = 0;
            int down = 0;
            for (Guard g : guards) {
                if (g.flood && g.wave == floodWaves) {
                    size++;
                    down += g.state == GuardState.DEAD ? 1 : 0;
                }
            }
            if (size > 0 && down >= Math.ceil(size * FLOOD_NEXT_FRACTION)) {
                spawnFlood();                                   // enough of this wave is down: the next comes in
            }
        }
    }

    private int floodTotal() {
        return 3 + (difficulty >= 3 ? 1 : 0);
    }

    private int floodAlive() {
        int n = 0;
        for (Guard g : guards) {
            n += g.flood && g.state != GuardState.DEAD ? 1 : 0;
        }
        return n;
    }

    /** The shuttle lock stays shut while the walkway fight is on, until all the guards have come and nearly all are down. */
    public boolean exitSealed() {
        return fightStarted && (floodWaves < floodTotal() || floodAlive() > FIGHT_CLEAR_LEFT);
    }

    private void spawnFlood() {
        floodWaves++;
        int n = FLOOD_WAVE_SIZE + (difficulty >= 2 ? 1 : 0);
        for (int i = 0; i < n; i++) {
            float y = floodY + (i - (n - 1) / 2f) * 28f;
            Guard g = new Guard(58f, new float[][]{{floodX, y}, {floodX - 60f, y}});
            g.x = floodX;
            g.y = y;
            g.facing = 180f;
            g.state = GuardState.ALERT;
            g.radioState = 4;                              // the call has gone out already
            g.wasAlert = true;
            g.flood = true;
            g.role = ROLE_GUN;
            g.wave = floodWaves;
            g.targetX = px;
            g.targetY = py;
            guards.add(g);
        }
    }

    public int alliesFreed() {
        int n = 0;
        for (Ally a : allies) {
            n += a.freed && !a.isRoland ? 1 : 0;
        }
        return n;
    }

    /** A patrolling guard who looks into a cage that has been opened goes to raise the alarm. */
    private void updateAllies() {
        for (Ally a : allies) {
            if (!a.freed || a.dead || a.isRoland) {
                continue;
            }
            for (Guard g : guards) {
                if (g.frozen || g.state == GuardState.DEAD || g == grabbed || (g.foeAlly != null && g.foeAlly != a)) {
                    continue;
                }
                if (g.foeAlly == a && g.state == GuardState.ALERT) {
                    continue;
                }
                if (!seesPoint(g, a.x, a.y) && Math.hypot(a.x - g.x, a.y - g.y) > 40f) {
                    continue;
                }
                // a prisoner loose: he is after them, and tells the others
                boolean first = g.foeAlly == null;
                g.foeAlly = a;
                g.foeLost = 0f;
                a.noticed = true;
                if (g.state != GuardState.ALERT) {
                    becomeAlert(g);
                }
                g.targetX = a.x;
                g.targetY = a.y;
                if (first) {
                    speak(g, "(guard) The prisoner is out! Stop them!");
                }
            }
        }
    }

    /**
     * A guard after a loose prisoner: closes with them and swings (a fatal blow), or shoots if he has been told to. The player takes priority
     * when nearer and in view. False when he has no one to deal with, so his normal hunting carries on.
     */
    private boolean guardVsAlly(Guard g, float dt) {
        Ally a = g.foeAlly;
        if (a.dead || !a.freed) {
            g.foeAlly = null;
            g.allyMeleeTime = -1f;
            return false;
        }
        float d = (float) Math.hypot(a.x - g.x, a.y - g.y);
        if (g.allyMeleeTime >= 0f) {
            g.facing = turnToward(g.facing, angleTo(g.x, g.y, a.x, a.y), CHASE_TURN_RATE * dt);
            g.allyMeleeTime -= dt;
            if (g.allyMeleeTime < 0f) {
                g.meleeCooldown = MELEE_COOLDOWN;
                g.lungeTime = LUNGE_TIME;
                sound("swing", g.x, g.y);
                if (d <= MELEE_REACH + 6f && Math.abs(angleDiff(g.facing, angleTo(g.x, g.y, a.x, a.y))) <= PLAYER_MELEE_ARC_DEG) {
                    allyTakesBlow(a, g.x, g.y);                 // a hit point, a shove; blockable; nothing against a suit
                    a.attacker = g;
                    a.attackedTime = ATTACK_MEMORY;
                }
            }
            return true;
        }
        if (clearLine(g) && distToPlayer(g) < d && !allyTakesPriority(g)) {
            return false;
        }
        if (clearTo(g, a.x, a.y)) {
            g.foeLost = 0f;
        } else {
            g.foeLost += dt;
            if (g.foeLost > 6f) {
                g.foeAlly = null;
                return false;
            }
        }
        g.targetX = a.x;
        g.targetY = a.y;
        if (d <= MELEE_START) {
            if (g.meleeCooldown <= 0f) {
                g.allyMeleeTime = MELEE_WINDUP;
                g.facing = turnToward(g.facing, angleTo(g.x, g.y, a.x, a.y), CHASE_TURN_RATE * dt);
            } else {
                duelGuard(g, a.x, a.y, dt);                     // between blows he moves round a little
            }
            return true;
        }
        if (lethalKnown && g.role == ROLE_GUN && guardShoot(g, dt)) {
            return true;
        }
        moveToward(g, a.x, a.y, ALERT_SPEED, CHASE_TURN_RATE, dt, false);
        return true;
    }

    /**
     * Examines whatever is nearest within reach: a piece of the cell, a locked
     * door, Roland. What there is to read goes out as barks. False if nothing is
     * close enough.
     */
    public boolean interact() {
        if (status != Status.RUNNING) {
            return false;
        }
        if (carrying != null) {
            return putDownBody();
        }
        if (grabbed != null) {
            return true;                                    // the view's held Space questions him
        }
        Guard grip = grabTarget();
        if (grip != null) {
            return grab(grip);
        }
        String type = null;
        float best = INTERACT_RANGE * INTERACT_RANGE;
        for (armaa_stealthLevel.Interact it : interacts) {
            float d = (it.x - px) * (it.x - px) + (it.y - py) * (it.y - py);
            if (d < best) {
                best = d;
                type = it.type;
            }
        }
        for (Lock l : locks) {
            if (l.open) {
                continue;
            }
            float cx = Math.max(l.rect.x, Math.min(px, l.rect.x + l.rect.w));
            float cy = Math.max(l.rect.y, Math.min(py, l.rect.y + l.rect.h));
            float d = (cx - px) * (cx - px) + (cy - py) * (cy - py);
            if (d < best) {
                best = d;
                type = "lock" + l.name;
            }
        }
        Ally talk = null;
        for (Ally a : allies) {
            if (a.isRoland) {
                continue;
            }
            float d = (a.x - px) * (a.x - px) + (a.y - py) * (a.y - py);
            if (d < best) {
                best = d;
                type = "ally";
                talk = a;
            }
        }
        Guard body = nearestBody();
        if (body != null && (type == null
                || (body.x - px) * (body.x - px) + (body.y - py) * (body.y - py) < best)) {
            carrying = body;
            hints.event("dragged", body.x, body.y, this);
            barks.add("(you) You take hold of the body.");
            return true;
        }
        if (type == null) {
            return false;
        }
        if ("bunk".equals(type)) {
            if (hasTool) {
                barks.add("There is nothing else under the bunk.");
            } else if (slotsUsed() >= INVENTORY_SLOTS) {
                toast("Inventory full: press G to drop something");
                hints.event("bagFull", px, py, this);
            } else {
                hasTool = true;
                toast("Bracket");
                barks.add("A bracket has worked loose from the bunk frame. The edge is flat and narrow enough to turn a screw. You take it.");
            }
        } else if ("ally".equals(type)) {
            speakAlly(talk, talk.freed
                    ? "I'll hold here until you open the way."
                    : "The panel on the door. Hold on it and the latch will give.");
        } else if ("armory".equals(type)) {
            barks.add("Weapon racks, mostly bare. What is left is bolted down.");
        } else if ("toilet".equals(type)) {
            barks.add("A steel toilet bolted to the floor. There is nothing in it but water.");
        } else if ("bars".equals(type)) {
            barks.add("Cold steel bars. You can see the whole corridor through them, and anyone in it can see you.");
        } else if ("scratches".equals(type)) {
            barks.add("Scratches in the wall: tallies, a few names, a date that someone gouged out.");
        } else if ("sink".equals(type)) {
            barks.add("A dry basin. Someone has scratched a tally into the wall above it. Forty-one marks.");
        } else if ("vent".equals(type)) {
            if (ventOpen) {
                barks.add("The grate hangs open. The duct beyond is too narrow to turn around in.");
            } else if (!hasTool) {
                barks.add("A grate low in the wall, held by four screws. You would need something to turn them.");
            } else if (ventExit == null) {
                barks.add("The grate comes away, but the duct beyond is blocked.");
            } else {
                ventOpen = true;
                px = ventExit[0];
                py = ventExit[1];
                vx = 0f;
                vy = 0f;
                barks.add("The screws come out one at a time. The duct is tight and runs a long way in the dark. You come out through a grate in some other part of the station.");
            }
        } else if ("roland".equals(type)) {
            if (!rolandFree) {
                speak(null, hasKey
                        ? "Roland nods at the card reader on his cage."
                        : "Roland keeps his voice low. \"One of the guards on the main corridor carries the card. I watched him pocket it. You will have to take it off him.\"");
            }
        } else if (type.startsWith("lock")) {
            Lock l = lockNamed(type.substring(4));
            boolean needsOfficer = "X".equals(l.name);
            if (isLessonDoor(l.name)) {
                sound("deny", px, py);
                hints.event("sealedDoor", l.rect.x + l.rect.w / 2f, l.rect.y + l.rect.h / 2f, this);
                barks.add("The door is sealed. It unlocks when you have done what this room is for.");
            } else if ("V".equals(l.name)) {
                barks.add(hasTool
                        ? "The screws are stiff. Hold Space on the grate and the bracket will turn them."
                        : "A grate low in the wall, held by four screws. You would need something to turn them.");
            } else if (isCageName(l.name)) {
                barks.add("The latch panel on the cage door is dead. Holding Space on it should force the latch.");
            } else if (needsOfficer ? !hasKey2 : !hasKey) {
                sound("deny", px, py);
                hints.event("lockedDoor", l.rect.x + l.rect.w / 2f, l.rect.y + l.rect.h / 2f, this);
                barks.add(needsOfficer && hasKey
                        ? "The reader flashes red at your card. This door wants an officer's."
                        : "A card reader, blinking red. You need a keycard.");
            } else if ("D".equals(l.name) && hasRoland && !rolandFree && !warnedAboutRoland) {
                warnedAboutRoland = true;
                barks.add("You are not leaving without Roland. If you open the door again, you are choosing to.");
            } else {
                open(l);
                if ("D".equals(l.name) && hasRoland && !rolandFree) {
                    abandonedRoland = true;
                }
                if ("R".equals(l.name)) {
                    rolandFree = true;
                    barks.add("The card reader accepts the key and the cage door unlatches. Roland steps out with one hand pressed to his ribs.");
                } else if ("N".equals(l.name)) {
                    barks.add("The door slides back on a row of cages along the north wall.");
                } else if ("S".equals(l.name)) {
                    barks.add("The door slides back on racks and shelving. Most of it is bare.");
                } else if ("X".equals(l.name)) {
                    barks.add("The heavy door grinds aside. A long walkway runs east, and at the far end of it is the shuttle lock.");
                } else if ("D".equals(l.name)) {
                    barks.add(abandonedRoland
                            ? "The door unlocks. Roland is still in his cage behind you."
                            : "The door unlocks. Beyond it is the facility, and the shuttle lock at the far end of it.");
                } else {
                    barks.add("The card reader accepts the key and the door unlatches.");
                }
            }
        }
        return true;
    }

    private Guard nearestBody() {
        Guard best = null;
        float bd = BODY_GRAB_RANGE * BODY_GRAB_RANGE;
        for (Guard g : guards) {
            if (g.state != GuardState.DEAD || g.hidden) {
                continue;
            }
            float d = (g.x - px) * (g.x - px) + (g.y - py) * (g.y - py);
            if (d < bd) {
                bd = d;
                best = g;
            }
        }
        return best;
    }

    /** The dragged body follows just behind the player. */
    private void moveCarried() {
        if (carrying == null) {
            return;
        }
        float sp = (float) Math.sqrt(vx * vx + vy * vy);
        float bx = px;
        float by = py;
        if (sp > 1f) {
            bx -= vx / sp * 12f;
            by -= vy / sp * 12f;
        }
        if (!hitsWall(bx, by, 4f)) {
            carrying.x = bx;
            carrying.y = by;
            if (carrying.bloody && Math.hypot(bx - smearX, by - smearY) >= 16f) {
                float heading = (float) Math.atan2(by - smearY, bx - smearX);
                if (Math.hypot(smearX, smearY) > 0.5f && Math.hypot(bx - smearX, by - smearY) < 40f) {
                    blob(bx, by, 3, 6f, 3f, 2.4f, heading);
                }
                smearX = bx;
                smearY = by;
            }
        }
    }

    /** Drops the body, or stows it if a hiding place is within reach. */
    private boolean putDownBody() {
        Guard body = carrying;
        carrying = null;
        for (float[] h : hides) {
            if ((h[0] - px) * (h[0] - px) + (h[1] - py) * (h[1] - py) <= HIDE_RANGE * HIDE_RANGE) {
                body.hidden = true;
                body.x = h[0];
                body.y = h[1];
                hints.event("bodyHidden", h[0], h[1], this);
                barks.add("(you) You stow the body out of sight.");
                return true;
            }
        }
        body.x = px;
        body.y = py;
        barks.add("(you) You let the body down.");
        return true;
    }

    private void followRoland(float dt) {
        if (!hasRoland || !rolandFree || rolandAlly != null) {
            return;                                             // once he is in the party the ally logic moves him
        }
        float gx = px;
        float gy = py;
        float stop = ROLAND_FOLLOW_DISTANCE;
        if (rallyX >= 0f && alliesFreed() >= 1 && !allyBreakout) {
            gx = rallyX;                                    // once one of them is free he stays with them, in their room
            gy = rallyY - 26f;
            stop = 6f;
        }
        float dx = gx - rolandX;
        float dy = gy - rolandY;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d <= stop) {
            return;
        }
        float tx = gx;
        float ty = gy;
        if (!lineFree(rolandX, rolandY, gx, gy)) {
            rolandPathAge += dt;
            if (rolandPath == null || rolandPathAge > 0.5f) {
                rolandPath = findPath(rolandX, rolandY, gx, gy);
                rolandPathAge = 0f;
                rolandPathIdx = 0;
            }
            if (rolandPath != null) {
                while (rolandPathIdx < rolandPath.size()
                        && Math.hypot(rolandPath.get(rolandPathIdx)[0] - rolandX, rolandPath.get(rolandPathIdx)[1] - rolandY) < 8f) {
                    rolandPathIdx++;
                }
                if (rolandPathIdx < rolandPath.size()) {
                    tx = rolandPath.get(rolandPathIdx)[0];
                    ty = rolandPath.get(rolandPathIdx)[1];
                }
            }
        } else {
            rolandPath = null;
        }
        dx = tx - rolandX;
        dy = ty - rolandY;
        float seg = (float) Math.sqrt(dx * dx + dy * dy);
        if (seg < 0.5f) {
            return;
        }
        float move = Math.min(ROLAND_SPEED * dt, Math.max(0f, d - stop));
        float nx = rolandX + dx / seg * move;
        float ny = rolandY + dy / seg * move;
        if (!hitsWall(nx, rolandY, PLAYER_RADIUS)) {
            rolandX = nx;
        }
        if (!hitsWall(rolandX, ny, PLAYER_RADIUS)) {
            rolandY = ny;
        }
    }

    /**
     * Advances the simulation.
     *
     * @param dt seconds
     * @param moveX -1, 0 or 1
     * @param moveY -1, 0 or 1
     * @param sneak whether the player is sneaking
     */
    public void step(float dt, float moveX, float moveY, boolean sneak) {
        if (status != Status.RUNNING) {
            return;
        }
        if (slowMoLeft > 0f) {
            float real = dt;
            dt *= timeScale();
            slowMoLeft = Math.max(0f, slowMoLeft - real);
        }
        time += dt;
        bloodDir = Float.NaN;
        sneaking = sneak;
        boolean moved = movePlayer(dt, moveX, moveY, sneak);
        boolean quiet = (sneak || crawling()) && carrying == null;       // dragging a body is never quiet
        moveCarried();
        updateFog();
        updateFade(dt);
        fireCooldown = Math.max(0f, fireCooldown - dt);
        updatePickups();
        followRoland(dt);
        for (int i = shots.size() - 1; i >= 0; i--) {
            shots.get(i).age += dt;
            if (shots.get(i).age >= SHOT_LIFE) {
                shots.remove(i);
            }
        }

        emitPlayerRipples(dt, moved, quiet);
        alertFlash = Math.max(0f, alertFlash - 2.2f * dt);
        for (int r = ripples.size() - 1; r >= 0; r--) {
            Ripple rp = ripples.get(r);
            rp.age += dt;
            if (rp.age >= RIPPLE_LIFE) {
                ripples.remove(r);
            }
        }
        updateThrown(dt);
        for (Guard g : guards) {
            g.rangeScale = approach(g.rangeScale, rangeScaleTarget(g), SNEAK_BLEND_RATE * dt);
        }
        updateReinforcements(dt);
        updateCaution(dt);
        updatePendingCalls(dt);
        updateScene(dt);
        updateExchanges(dt);
        updateJam(dt);
        updateSpeeches(dt);
        updatePings(dt);
        updateLights(dt);
        updateFocus(dt);
        updateBlood(dt);
        updateKnock(dt);
        updateObjective();
        updateDrone(dt);
        hints.update(this, dt);
        updateWatch(dt);
        updateTutorial(dt);
        playerFlash = Math.max(0f, playerFlash - 2.5f * dt);
        for (Guard fg : guards) {
            fg.flash = Math.max(0f, fg.flash - 2.5f * dt);
            fg.blockTime = Math.max(0f, fg.blockTime - dt);
            fg.lungeTime = Math.max(0f, fg.lungeTime - dt);
            fg.recoilDeg *= (float) Math.exp(-RECOIL_DECAY * dt);
            fg.attackedByAlly = Math.max(0f, fg.attackedByAlly - dt);
            fg.attackedByPlayer = Math.max(0f, fg.attackedByPlayer - dt);
        }
        for (Ally fa : allies) {
            fa.flash = Math.max(0f, fa.flash - 2.5f * dt);
            fa.attackedTime = Math.max(0f, fa.attackedTime - dt);
            fa.recoilDeg *= (float) Math.exp(-RECOIL_DECAY * dt);
        }
        updateBloodNotice();
        updateDoors(dt);
        updateOverride(dt);
        updateAllies();
        updateFight(dt);
        updateAllyUnits(dt);
        for (Ally ba : allies) {
            if (ba.freed && !ba.dead && !ba.isRoland) {
                trackBlood(ba.wet, ba.x, ba.y, ba);
            }
        }
        trackBlood(playerWet, px, py, null);
        if (rolandAlly != null) {
            rolandX = rolandAlly.x;
            rolandY = rolandAlly.y;
        }
        updateBullets(dt);
        updateKnockedOut(dt);
        updateGrab(dt);
        revealLeft = Math.max(0f, revealLeft - dt);
        shake = Math.max(0f, shake - 2.5f * dt);
        kickX *= Math.max(0f, 1f - 14f * dt);
        kickY *= Math.max(0f, 1f - 14f * dt);
        hitStopCool = Math.max(0f, hitStopCool - dt);
        woundFlash = Math.max(0f, woundFlash - 3f * dt);
        woundGrace = Math.max(0f, woundGrace - dt);
        playerMeleeCooldown = Math.max(0f, playerMeleeCooldown - dt);
        swingTime = Math.max(0f, swingTime - dt);
        takedownCooldown = Math.max(0f, takedownCooldown - dt);

        squadTrack = Math.max(0f, squadTrack - dt);
        roleTimer -= dt;
        if (roleTimer <= 0f) {
            roleTimer = ROLE_REFRESH;
            assignRoles();
        }

        // index loop: reinforcements can be added while iterating
        for (int i = 0; i < guards.size(); i++) {
            Guard g = guards.get(i);
            if (g.frozen || g.state == GuardState.DEAD || g == grabbed) {
                continue;
            }
            if (g.stun > 0f) {
                g.stun -= dt;
                continue;
            }
            float gx0 = g.x;
            float gy0 = g.y;
            react(g, moved && !quiet, dt);
            switch (g.state) {
                case PATROL:
                    if (g.chat) {
                        chatStep(g, dt);
                    } else if (g.huddle) {
                        huddleStep(g, dt);
                    } else {
                        patrol(g, dt);
                    }
                    break;
                case SUSPICIOUS:
                    if (g.chastise != null && chastiseStep(g, dt)) {
                        break;
                    }
                    if (g.noticeLeft > 0f) {
                        // stops walking, "?", but keeps its eyes on the spot (or the player, if still in view)
                        g.noticeLeft -= dt;
                        g.facing = turnToward(g.facing, angleTo(g.x, g.y, g.targetX, g.targetY), CHASE_TURN_RATE * dt);
                    } else if (!(g.bodyAlarm && radioBusy(g, dt))) {
                        chase(g, dt, g.witness ? ALERT_SPEED : (g.hurry ? HURRY_SPEED : SUSPICIOUS_SPEED), true);
                    }
                    break;
                case ALERT:
                    if (g.radioState == 0) {
                        beginRadio(g);                  // he held back because another call was out; if it never finished, he makes it
                    }
                    if (!radioBusy(g, dt) && !guardMelee(g, dt)) {
                        if (g.foeAlly != null && guardVsAlly(g, dt)) {
                            break;
                        }
                        if (loneWait(g, dt)) {
                            break;
                        }
                        if (g.flood && fightStarted && floodGuardStep(g, dt)) {
                            break;
                        }
                        boolean engaged = g.role != ROLE_NONE && distToPlayer(g) < 360f && clearLine(g);
                        if (!engaged && fightStarted && g.role != ROLE_NONE) {
                            float[] et = shootTarget(g);                    // in the fight he stands and shoots an ally he can see too
                            engaged = Math.hypot(et[0] - g.x, et[1] - g.y) < 360f && clearTo(g, et[0], et[1]);
                        }
                        if (engaged && g.role == ROLE_GUN) {
                            if (!guardShoot(g, dt)) {
                                holdAt(g, dt);
                            }
                        } else if (engaged && g.role == ROLE_HOLD) {
                            holdAt(g, dt);
                        } else {
                            if (engaged && g.role == ROLE_MELEE) {
                                g.targetX = g.slotX;                       // come in from his own side, not down everyone else's line
                                g.targetY = g.slotY;
                            }
                            chase(g, dt, ALERT_SPEED, false);
                        }
                    }
                    break;
                default:
                    search(g, dt);
                    break;
            }
            trackMark(g, dt);
            emitGuardRipples(g, dt, gx0, gy0);
            trackBlood(g.wet, g.x, g.y, g);
            if (g.bloody && g.hp < MAX_HP && g.state != GuardState.DEAD) {
                g.bleedDist += (float) Math.hypot(g.x - gx0, g.y - gy0);
                if (g.bleedDist >= 28f) {
                    g.bleedDist = 0f;
                    drip(g.x, g.y);
                }
            }
        }

        separateGuards();
        if (exit2 != null && exit2.overlapsCircle(px, py, PLAYER_RADIUS)) {
            nextStage = nextStage2;                             // the other way out
            status = everSeen ? Status.SEEN : Status.CLEAN;
        } else if (exit.overlapsCircle(px, py, PLAYER_RADIUS) && exitSealed()) {
            if (!sealWarned) {
                sealWarned = true;
                barks.add("(you) The shuttle lock is sealed while the security detail is still on its feet.");
            }
        } else if (exit.overlapsCircle(px, py, PLAYER_RADIUS)) {
            status = everSeen ? Status.SEEN : Status.CLEAN;
        }
    }

    // ---- player ----
    /**
     * Eases the velocity toward what the keys ask for (quick to start, a bit
     * quicker to stop), slides along walls one axis at a time, and reports
     * whether the player is moving fast enough to be heard.
     */
    private boolean movePlayer(float dt, float mx, float my, boolean sneak) {
        float len = (float) Math.sqrt(mx * mx + my * my);
        float tvx = 0f;
        float tvy = 0f;
        if (len > 0f) {
            float speed = (sneak ? SNEAK_SPEED : WALK_SPEED) * (carrying != null ? DRAG_SPEED_FACTOR : 1f) * (crawling() ? CRAWL_FACTOR : 1f);
            tvx = mx / len * speed;
            tvy = my / len * speed;
        }
        float rate = (len > 0f ? PLAYER_ACCEL : PLAYER_DECEL) * dt;
        vx = approach(vx, tvx, rate);
        vy = approach(vy, tvy, rate);

        float dx = vx * dt;
        float dy = vy * dt;
        if (dx != 0f) {
            if (!hitsWall(px + dx, py, PLAYER_RADIUS)) {
                px += dx;
            } else {
                vx = 0f;                                   // pressed against a wall: stop pushing into it
            }
        }
        if (dy != 0f) {
            if (!hitsWall(px, py + dy, PLAYER_RADIUS)) {
                py += dy;
            } else {
                vy = 0f;
            }
        }
        return (float) Math.sqrt(vx * vx + vy * vy) > MOVING_SPEED;
    }

    private boolean hitsWall(float cx, float cy, float r) {
        for (Rect w : walls) {
            if (w.overlapsCircle(cx, cy, r)) {
                return true;
            }
        }
        for (Rect b : bars) {
            if (b.overlapsCircle(cx, cy, r)) {
                return true;
            }
        }
        return false;
    }

    // ---- guards ----
    /**
     * What a guard notices this step. Alert guards just keep tracking. Others:
     * a suspicious guard close enough to the player goes alert; seeing the
     * player makes a guard suspicious; a footstep nearby does too.
     */
    private void react(Guard g, boolean playerMadeNoise, float dt) {
        if (playerConfined()) {
            return;                                             // he is in his cell: that is where he is meant to be
        }
        if (g.state == GuardState.ALERT) {
            if (sees(g)) {
                g.trackTime = 1.5f;
                squadTrack = 1.5f;
                g.targetX = px;
                g.targetY = py;
                lastAlertX = px;
                lastAlertY = py;
            } else {
                g.trackTime = Math.max(0f, g.trackTime - dt);
            }
            return;
        }
        boolean saw = sees(g);
        for (Guard body : guards) {
            if (body.state == GuardState.DEAD && !body.hidden && !body.found && seesPoint(g, body.x, body.y)) {
                discoverBody(g, body);
                return;
            }
        }
        // keep the player in view long enough and the guard is sure, wherever they stand
        if (g.state == GuardState.SUSPICIOUS && g.noticeLeft <= 0f) {
            if (saw) {
                g.seenTime += dt;
                if (g.seenTime >= SIGHT_ALERT_TIME) {
                    becomeAlert(g);
                    return;
                }
            } else {
                g.seenTime = Math.max(0f, g.seenTime - 0.5f * dt);
            }
        } else if (g.state != GuardState.SUSPICIOUS) {
            g.seenTime = 0f;
        }
        // close enough to have you, but only once the guard actually has you in view
        if (saw && distToPlayer(g) <= ALERT_RADIUS * g.rangeScale) {
            becomeAlert(g);
            return;
        }
        boolean heard = !saw && (g.state == GuardState.PATROL || g.state == GuardState.SEARCH)
                && playerMadeNoise && distToPlayer(g) <= NOISE_RANGE * (1f + (CAUTION_HEARING - 1f) * cautionBlend) && clearLine(g);
        if (saw && vx * vx + vy * vy > 25f) {
            g.lastHeading = angleTo(0f, 0f, vx, vy);
        }
        if (saw || heard) {
            boolean fresh = g.state != GuardState.SUSPICIOUS;
            becomeSuspicious(g, px, py, heard);
            if (fresh) {
                notice(g, false);
                if (heard) {
                    hints.event("noise", g.x, g.y, this);
                }
            }
        }
    }

    /** Records that a guard has noticed the player, or that he has now gone alert. */
    private void notice(Guard g, boolean alert) {
        for (Ping p : pings) {
            if (p.who == g) {
                p.alert |= alert;
                p.age = 0f;
                return;
            }
        }
        pings.add(new Ping(g, alert));
    }

    private void updatePings(float dt) {
        for (int i = pings.size() - 1; i >= 0; i--) {
            Ping p = pings.get(i);
            p.age += dt;
            if (p.age >= PING_LIFE || p.who.state == GuardState.DEAD) {
                pings.remove(i);
            }
        }
    }

    /**
     * Where to draw a marker for something at world point (gx, gy), given the view's corner and size. On screen it is the point itself;
     * off screen it sits on the edge of the view, inset, and points outward. Returns {x, y, angleDeg, offscreen (1 or 0)}, x and y in view
     * pixels from the view's bottom-left.
     */
    public static float[] edgeMarker(float gx, float gy, float camX, float camY, float w, float h, float inset) {
        float sx = gx - camX;
        float sy = gy - camY;
        float cx = w / 2f;
        float cy = h / 2f;
        float angle = (float) Math.toDegrees(Math.atan2(sy - cy, sx - cx));
        boolean on = sx >= inset && sx <= w - inset && sy >= inset && sy <= h - inset;
        if (on) {
            return new float[]{sx, sy, angle, 0f};
        }
        float dx = sx - cx;
        float dy = sy - cy;
        float halfW = w / 2f - inset;
        float halfH = h / 2f - inset;
        float t = Math.min(dx == 0f ? Float.MAX_VALUE : halfW / Math.abs(dx), dy == 0f ? Float.MAX_VALUE : halfH / Math.abs(dy));
        return new float[]{cx + dx * t, cy + dy * t, angle, 1f};
    }

    /** A guard sees a body: it drops whatever it was following, shows the alarm, goes to the body and calls it in. */
    private void discoverBody(Guard g, Guard body) {
        if (body.ko && !body.lethal) {
            if (escalates && lastSleeperFound >= 0f && time - lastSleeperFound < SLEEPER_MEMORY) {
                body.found = true;                          // a second one down so soon is no accident
                lastSleeperFound = time;
                raiseViolenceAlarm(g, body.x, body.y, "Another one down! Someone is knocking us out!");
                return;
            }
            lastSleeperFound = time;
            discoverSleeper(g, body);                       // not a killing: he assumes the man is slacking
            return;
        }
        body.found = true;
        g.knowsLethal |= body.lethal;
        speak(g, BODY_LINES[rng.nextInt(BODY_LINES.length)]);
        becomeSuspicious(g, body.x, body.y, false);
        g.hurry = false;
        g.bodyAlarm = true;
        g.noticeLeft = Math.min(g.noticeLeft, 0.2f);
        // a guard who finds a body goes into the modified alert at once (alert speed, red cone, an urgent call,
        // a harder search). The station goes on caution when his call goes out, not before: shoot him first and it does not.
        g.witness = true;
        beginRadio(g, body.x, body.y);
    }

    /**
     * A guard comes on a knocked-out guard. He does not raise the alarm: he thinks the man is asleep on the job,
     * scolds him, walks up (still only suspicious) and kicks him awake. See updateExchanges.
     */
    private void discoverSleeper(Guard g, Guard body) {
        body.found = true;
        becomeSuspicious(g, body.x, body.y, false);
        g.hurry = false;
        g.bodyAlarm = false;
        g.witness = false;
        g.chastise = body;
        g.chastiseWait = -1f;
        g.noticeLeft = Math.min(g.noticeLeft, 0.3f);
        speak(g, CHASTISE_LINES[rng.nextInt(CHASTISE_LINES.length)]);
    }

    /** Called each step for a suspicious guard on his way to a sleeper. True once he has arrived and the exchange has begun. */
    private boolean chastiseStep(Guard g, float dt) {
        Guard body = g.chastise;
        if (body.state != GuardState.DEAD || !body.ko) {
            g.chastise = null;                              // he woke by himself, or was carried off: nothing to wake
            return false;
        }
        if (Math.hypot(body.x - g.x, body.y - g.y) >= 24f) {
            g.chastiseWait = -1f;
            return false;
        }
        if (g.chastiseWait < 0f) {
            g.chastiseWait = CHASTISE_LOOK;                 // he stops and looks at the man first
        }
        g.facing = turnToward(g.facing, angleTo(g.x, g.y, body.x, body.y), CHASE_TURN_RATE * dt);
        g.chastiseWait -= dt;
        if (g.chastiseWait > 0f) {
            return true;
        }
        g.chastiseWait = -1f;
        startExchange(g, body);
        return true;
    }

    private void startExchange(Guard finder, Guard sleeper) {
        finder.chastise = null;
        finder.state = GuardState.PATROL;
        finder.pause = 0f;
        finder.chat = true;
        finder.chatWith = sleeper;
        if (carrying == sleeper) {
            carrying = null;
        }
        sleeper.ko = false;
        sleeper.hp = MAX_HP;
        sleeper.hidden = false;
        sleeper.found = false;
        sleeper.state = GuardState.PATROL;
        sleeper.pause = 0f;
        sleeper.chat = true;
        sleeper.chatWith = finder;
        shots.add(new Shot(sleeper.x, sleeper.y, sleeper.x, sleeper.y, true));       // the kick
        Exchange e = new Exchange();
        e.finder = finder;
        e.sleeper = sleeper;
        e.idx = 0;
        e.timer = 0f;
        exchanges.add(e);
    }

    private void updateExchanges(float dt) {
        for (int i = exchanges.size() - 1; i >= 0; i--) {
            Exchange e = exchanges.get(i);
            // anything that disturbs either of them ends it: they go back to being ordinary guards
            if (e.finder.state != GuardState.PATROL || e.sleeper.state != GuardState.PATROL || !e.finder.chat || !e.sleeper.chat) {
                endExchange(e);
                exchanges.remove(i);
                continue;
            }
            e.timer -= dt;
            if (e.timer > 0f) {
                continue;
            }
            switch (e.idx) {
                case 0:
                    speak(e.finder, KICK_LINES[rng.nextInt(KICK_LINES.length)]);
                    e.timer = 1.1f;
                    break;
                case 1:
                    speak(e.sleeper, SLEEPER_LINES[rng.nextInt(SLEEPER_LINES.length)]);
                    e.timer = 1.4f;
                    break;
                case 2:
                    speak(e.finder, SCOLD_LINES[rng.nextInt(SCOLD_LINES.length)]);
                    e.timer = 1.4f;
                    break;
                default:
                    endExchange(e);
                    // the woken guard is left uneasy: something put him down, so he looks around before going back
                    speak(e.sleeper, WARY_LINES[rng.nextInt(WARY_LINES.length)]);
                    beginSearch(e.sleeper);
                    if (escalates) {
                        raiseViolenceAlarm(e.sleeper, e.sleeper.x, e.sleeper.y, "I was jumped! Someone is here!");     // and he remembers it, and says so
                    }
                    exchanges.remove(i);
                    continue;
            }
            e.idx++;
        }
    }

    private void endExchange(Exchange e) {
        for (Guard g : new Guard[]{e.finder, e.sleeper}) {
            g.chat = false;
            g.chatWith = null;
            float best = Float.MAX_VALUE;
            for (int k = 0; k < g.route.length; k++) {
                float dx = g.route[k][0] - g.x;
                float dy = g.route[k][1] - g.y;
                float d = dx * dx + dy * dy;
                if (d < best) {
                    best = d;
                    g.next = k;
                }
            }
        }
    }

    private void chatStep(Guard g, float dt) {
        if (g.chatWith != null) {
            g.facing = turnToward(g.facing, angleTo(g.x, g.y, g.chatWith.x, g.chatWith.y), PATROL_TURN_RATE * dt);
        }
    }

    /** Why guards turned suspicious (a line each, for the log): what called it, where the cause was, and whether the guard could see the player. */
    private final List<String> notices = new ArrayList<String>();

    public List<String> drainNotices() {
        List<String> out = new ArrayList<String>(notices);
        notices.clear();
        return out;
    }

    private void noticeLog(Guard g, float x, float y, boolean fromNoise) {
        if (notices.size() >= 100) {
            return;
        }
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        String by = st.length > 4 ? st[3].getMethodName() + " <- " + st[4].getMethodName() : "?";
        notices.add(String.format("t=%.1f guard at (%d,%d) %s -> SUSPICIOUS by %s: %s at %dU from the player; guard sees the player %b, is %dU away; player sneaking %b",
                time, (int) g.x, (int) g.y, g.state, by, fromNoise ? "noise" : "sight/other", (int) Math.hypot(x - px, y - py), sees(g), (int) distToPlayer(g), sneaking));
    }

    private void becomeSuspicious(Guard g, float x, float y, boolean fromNoise) {
        if (g.state != GuardState.SUSPICIOUS) {
            noticeLog(g, x, y, fromNoise);
            g.hurry = false;
            g.state = GuardState.SUSPICIOUS;
            g.stuck = 0f;
            g.chaseClock = 0f;
            g.noticeLeft = NOTICE_PAUSE;
            say(g, fromNoise ? NOISE_LINES : SIGHT_LINES);
        }
        g.targetX = x;
        g.targetY = y;
    }

    private void becomeAlert(Guard g) {
        sound("alert", g.x, g.y);
        if (tutorialMode && !g.spar) {
            tutorialSpot(g);                                    // training: he notices, he does not sound the alarm
            return;
        }
        if (g.state != GuardState.ALERT) {
            say(g, ALERT_LINES);
            alertFlash = 1f;
            notice(g, true);
            if (reflexSlowMo && !slowMoUsed) {
                slowMoUsed = true;                          // once a map: the first guard to spot the player
                slowMoLeft = SLOWMO_SECONDS;
            }
        }
        g.chastise = null;
        g.wasAlert = true;
        g.trackTime = 1.5f;
        squadTrack = 1.5f;
        g.witness = false;
        g.hurry = false;
        g.bodyAlarm = false;
        g.state = GuardState.ALERT;
        g.stuck = 0f;
        g.chaseClock = 0f;
        g.targetX = px;
        g.targetY = py;
        everSeen = true;
        lastAlertX = px;
        lastAlertY = py;
        beginRadio(g);
    }

    private void patrol(Guard g, float dt) {
        if (huntStep(g, dt)) {
            return;
        }
        if (g.post) {
            float[] spot = g.route[0];
            if (Math.hypot(spot[0] - g.x, spot[1] - g.y) > 3f) {
                moveToward(g, spot[0], spot[1], g.speed, PATROL_TURN_RATE, dt, true);       // back to the post after a distraction
            } else {
                g.facing = turnToward(g.facing, angleTo(g.x, g.y, g.route[1][0], g.route[1][1]), PATROL_TURN_RATE * dt);
            }
            return;
        }
        if (g.watching > 0f) {
            g.watching -= dt;                                   // stopped to look in: his head follows the player
            g.facing = turnToward(g.facing, angleTo(g.x, g.y, px, py), 110f * dt);
            return;
        }
        if (g.pause > 0f) {
            g.pause -= dt;
            g.sweepClock += dt;
            float want = g.sweepBase + (float) Math.sin(g.sweepClock * 2.2f) * 40f;
            g.facing = turnToward(g.facing, want, PATROL_TURN_RATE * dt);
            return;
        }
        float[] to = g.route[g.next];
        float dx = to[0] - g.x;
        float dy = to[1] - g.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float stepLen = g.speed * dt;
        if (dist <= stepLen) {
            g.x = to[0];
            g.y = to[1];
            int arrived = g.next;
            g.next = (g.next + 1) % g.route.length;
            g.pause = WAYPOINT_PAUSE;
            if (g.watch && g.lookSpots != null && arrived < g.lookSpots.length && g.lookSpots[arrived]) {
                g.watching = LOOK_IN_TIME;
                g.pause = 0f;
            }
            g.sweepClock = 0f;
            float[] after = g.route[g.next];
            g.sweepBase = angleTo(g.x, g.y, after[0], after[1]);
            return;
        }
        moveToward(g, to[0], to[1], g.speed, PATROL_TURN_RATE, dt, true);
    }

    /**
     * Walk to the guard's target. Used by both suspicious and alert guards;
     * they differ in speed and in what happens on arrival. Arriving, or making
     * almost no headway for a second and a half (a target it cannot reach),
     * turns into a search.
     */
    private void chase(Guard g, float dt, float speed, boolean turnFirst) {
        float dx = g.targetX - g.x;
        float dy = g.targetY - g.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist < 8f) {
            if (g.state == GuardState.ALERT && distToPlayer(g) < 60f && clearLine(g)) {
                g.facing = turnToward(g.facing, angleTo(g.x, g.y, px, py), CHASE_TURN_RATE * dt);
                return;                                 // he is on top of the player: not a lost trail, so no searching
            }
            beginSearch(g);
            return;
        }
        float stepLen = speed * dt;
        float moved = moveToward(g, g.targetX, g.targetY, speed, CHASE_TURN_RATE, dt, turnFirst);
        // a guard turning on the spot before it sets off is not stuck
        boolean turning = turnFirst
                && Math.abs(angleDiff(g.facing, angleTo(g.x, g.y, g.targetX, g.targetY))) > 25f;
        if (!turning) {
            g.stuck = moved < 0.3f * stepLen ? g.stuck + dt : 0f;
        }
        // and nobody hunts one spot forever, whatever the walls are doing
        g.chaseClock += dt;
        if (g.stuck > 1.5f || g.chaseClock > 15f) {
            beginSearch(g);
        }
    }

    // ---- movement and pathing ----
    /**
     * Moves a guard one step toward (tx, ty), around walls if need be.
     *
     * @param turnFirst stand still and turn until facing the way to go, so a
     * patrolling guard does not slide sideways
     * @return how far it actually moved
     */
    private float moveToward(Guard g, float tx, float ty, float speed, float turnRate, float dt, boolean turnFirst) {
        float wx = tx;
        float wy = ty;
        if (lineFree(g.x, g.y, tx, ty)) {
            g.path = null;
        } else {
            g.repathTimer -= dt;
            if (g.path == null || g.repathTimer <= 0f
                    || Math.abs(g.pathGoalX - tx) + Math.abs(g.pathGoalY - ty) > 12f) {
                g.path = findPath(g.x, g.y, tx, ty);
                g.pathIdx = 0;
                g.pathGoalX = tx;
                g.pathGoalY = ty;
                g.repathTimer = 0.6f;
            }
            if (g.path == null || g.path.isEmpty()) {
                return 0f;                              // no way there
            }
            while (g.pathIdx < g.path.size() - 1) {
                float[] pt = g.path.get(g.pathIdx);
                float ddx = pt[0] - g.x;
                float ddy = pt[1] - g.y;
                if (ddx * ddx + ddy * ddy > 36f) {
                    break;
                }
                g.pathIdx++;
            }
            float[] pt = g.path.get(g.pathIdx);
            wx = pt[0];
            wy = pt[1];
        }
        float want = angleTo(g.x, g.y, wx, wy);
        g.facing = turnToward(g.facing, want, turnRate * dt);
        if (turnFirst && Math.abs(angleDiff(g.facing, want)) > 25f) {
            return 0f;
        }
        float dx = wx - g.x;
        float dy = wy - g.y;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) {
            return 0f;
        }
        float stepLen = Math.min(speed * dt, len);
        float nx = g.x + dx / len * stepLen;
        float ny = g.y + dy / len * stepLen;
        float ox = g.x;
        float oy = g.y;
        if (!hitsWall(nx, g.y, GUARD_RADIUS)) {
            g.x = nx;
        }
        if (!hitsWall(g.x, ny, GUARD_RADIUS)) {
            g.y = ny;
        }
        float mx = g.x - ox;
        float my = g.y - oy;
        return (float) Math.sqrt(mx * mx + my * my);
    }

    /** True if a guard can walk the straight line without touching a wall. */
    private boolean lineFree(float x1, float y1, float x2, float y2) {
        for (Rect w : grown) {
            if (w.blocks(x1, y1, x2, y2)) {
                return false;
            }
        }
        return true;
    }

    private void buildNav() {
        for (Rect w : walls) {
            if (navIgnore.contains(w)) {
                continue;                               // an unlocked door: guards may plan a route through it
            }
            grown.add(new Rect(w.x - GUARD_RADIUS, w.y - GUARD_RADIUS, w.w + 2 * GUARD_RADIUS, w.h + 2 * GUARD_RADIUS));
        }
        for (Rect w : bars) {
            grown.add(new Rect(w.x - GUARD_RADIUS, w.y - GUARD_RADIUS, w.w + 2 * GUARD_RADIUS, w.h + 2 * GUARD_RADIUS));
        }
        navBlocked = new boolean[navCols * navRows];
        for (int c = 0; c < navCols; c++) {
            for (int r = 0; r < navRows; r++) {
                float cx = c * CELL + CELL / 2f;
                float cy = r * CELL + CELL / 2f;
                boolean blocked = false;
                for (Rect w : walls) {
                    if (!navIgnore.contains(w) && w.overlapsCircle(cx, cy, GUARD_RADIUS + 1f)) {
                        blocked = true;
                        break;
                    }
                }
                for (Rect b : bars) {
                    if (b.overlapsCircle(cx, cy, GUARD_RADIUS + 1f)) {
                        blocked = true;
                        break;
                    }
                }
                navBlocked[r * navCols + c] = blocked;
            }
        }
    }

    private int cellOf(float v, int max) {
        return Math.max(0, Math.min(max - 1, (int) (v / CELL)));
    }

    /** The open cell nearest to (c, r), searched in widening squares, or -1. */
    private int nearestFree(int c, int r) {
        for (int ring = 0; ring <= 8; ring++) {
            int best = -1;
            float bestD = Float.MAX_VALUE;
            for (int dc = -ring; dc <= ring; dc++) {
                for (int dr = -ring; dr <= ring; dr++) {
                    if (Math.max(Math.abs(dc), Math.abs(dr)) != ring) {
                        continue;
                    }
                    int cc = c + dc;
                    int rr = r + dr;
                    if (cc < 0 || rr < 0 || cc >= navCols || rr >= navRows || navBlocked[rr * navCols + cc]) {
                        continue;
                    }
                    float d = dc * dc + dr * dr;
                    if (d < bestD) {
                        bestD = d;
                        best = rr * navCols + cc;
                    }
                }
            }
            if (best >= 0) {
                return best;
            }
        }
        return -1;
    }

    /**
     * A* over the grid from (sx, sy) to (tx, ty), pulled straight where it can
     * be. Returns the points to walk through, ending at the target, or null if
     * there is no way.
     */
    private List<float[]> findPath(float sx, float sy, float tx, float ty) {
        int start = nearestFree(cellOf(sx, navCols), cellOf(sy, navRows));
        int goal = nearestFree(cellOf(tx, navCols), cellOf(ty, navRows));
        if (start < 0 || goal < 0) {
            return null;
        }
        int n = navCols * navRows;
        float[] cost = new float[n];
        int[] parent = new int[n];
        Arrays.fill(cost, Float.MAX_VALUE);
        Arrays.fill(parent, -1);
        boolean[] closed = new boolean[n];
        PriorityQueue<float[]> open = new PriorityQueue<float[]>(64, (a, b) -> Float.compare(a[0], b[0]));
        cost[start] = 0f;
        open.add(new float[]{heuristic(start, goal), start});
        boolean found = false;
        while (!open.isEmpty()) {
            int cur = (int) open.poll()[1];
            if (closed[cur]) {
                continue;
            }
            closed[cur] = true;
            if (cur == goal) {
                found = true;
                break;
            }
            int cc = cur % navCols;
            int cr = cur / navCols;
            for (int dc = -1; dc <= 1; dc++) {
                for (int dr = -1; dr <= 1; dr++) {
                    if (dc == 0 && dr == 0) {
                        continue;
                    }
                    int nc = cc + dc;
                    int nr = cr + dr;
                    if (nc < 0 || nr < 0 || nc >= navCols || nr >= navRows || navBlocked[nr * navCols + nc]) {
                        continue;
                    }
                    if (dc != 0 && dr != 0
                            && (navBlocked[cr * navCols + nc] || navBlocked[nr * navCols + cc])) {
                        continue;                           // no cutting a corner
                    }
                    int ni = nr * navCols + nc;
                    float step = (dc != 0 && dr != 0) ? 1.4142f : 1f;
                    float nCost = cost[cur] + step;
                    if (nCost < cost[ni]) {
                        cost[ni] = nCost;
                        parent[ni] = cur;
                        open.add(new float[]{nCost + heuristic(ni, goal), ni});
                    }
                }
            }
        }
        if (!found) {
            return null;
        }
        List<float[]> cells = new ArrayList<float[]>();
        for (int i = goal; i != -1; i = parent[i]) {
            cells.add(0, new float[]{(i % navCols) * CELL + CELL / 2f, (i / navCols) * CELL + CELL / 2f});
        }
        List<float[]> pts = new ArrayList<float[]>();
        pts.add(new float[]{sx, sy});
        pts.addAll(cells);
        pts.add(new float[]{tx, ty});
        // pull the string: skip any point the guard can walk past in a straight line
        List<float[]> out = new ArrayList<float[]>();
        int i = 0;
        int last = pts.size() - 1;
        while (i < last) {
            int j = last;
            while (j > i + 1 && !lineFree(pts.get(i)[0], pts.get(i)[1], pts.get(j)[0], pts.get(j)[1])) {
                j--;
            }
            out.add(pts.get(j));
            i = j;
        }
        return out;
    }

    private float heuristic(int a, int b) {
        float dx = Math.abs(a % navCols - b % navCols);
        float dy = Math.abs(a / navCols - b / navCols);
        return Math.max(dx, dy) + 0.4142f * Math.min(dx, dy);
    }

    private void beginSearch(Guard g) {
        g.hurry = false;
        g.chastise = null;
        if (g.radioState == 1 || g.radioState == 2) {
            startSpeaking(g);                           // lost you before calling: calls now
        }
        g.state = GuardState.SEARCH;
        g.searchLeft = g.witness ? SEARCH_TIME * WITNESS_SEARCH_FACTOR : SEARCH_TIME;
        g.look = null;
        g.sweepBase = g.facing;
        g.sweepClock = 0f;
    }

    /**
     * Plans where a searching guard looks: first toward the side the player was
     * last heading (or a random side), for longest, then the other way briefly,
     * then back toward the first side, a little less far. Holds vary a little.
     */
    private void planLook(Guard g) {
        float side;
        float lean = 1f;
        if (!Float.isNaN(g.lastHeading)) {
            float d = angleDiff(g.lastHeading, g.sweepBase);
            side = d >= 0f ? 1f : -1f;
            lean = 0.6f + 0.4f * Math.min(1f, Math.abs(d) / 90f);   // the further off to one side, the more it commits
        } else {
            side = rng.nextBoolean() ? 1f : -1f;
        }
        float v = 0.8f + rng.nextFloat() * 0.4f;
        if (g.witness) {
            g.look = new float[][]{
                {side * 85f * lean, 1.0f * v},
                {-side * 80f, 0.8f * v},
                {side * 60f * lean, 0.8f * v},
                {-side * 55f, 0.6f * v},
                {side * 25f, 0.6f * v},
            };
        } else {
            g.look = new float[][]{
                {side * 65f * lean, 0.9f * v},
                {-side * 50f, 0.4f * v},
                {side * 35f * lean, 0.6f * v},
            };
        }
        g.lookIdx = 0;
        g.lookHold = 0f;
    }

    private void search(Guard g, float dt) {
        g.searchLeft -= dt;
        g.sweepClock += dt;
        if (g.look == null) {
            planLook(g);
        }
        if (g.lookIdx < g.look.length) {
            float[] step = g.look[g.lookIdx];
            float want = g.sweepBase + step[0];
            g.facing = turnToward(g.facing, want, SEARCH_TURN_RATE * dt);
            if (Math.abs(angleDiff(g.facing, want)) < 1f) {
                g.lookHold += dt;
                if (g.lookHold >= step[1]) {
                    g.lookIdx++;
                    g.lookHold = 0f;
                }
            }
        }
        boolean lookedEverywhere = g.lookIdx >= g.look.length || g.searchLeft < -2f;
        if (g.searchLeft <= 0f && lookedEverywhere) {
            boolean quietEnd = g.witness || g.bodyAlarm || cautionLeft > 0f;   // no "must have been nothing" after a body, or on caution
            g.look = null;
            g.bodyAlarm = false;
            if (g.witness) {
                // not satisfied: sweep the ground around the body before going back
                g.witness = false;
                g.hunt.clear();
                g.homeSweep = false;
                for (int k = 0; k < 3; k++) {
                    float[] p = openPointNear(g.targetX, g.targetY, 170f, g.x, g.y);
                    if (p != null) {
                        g.hunt.add(p);
                    }
                }
                cautionLeft = Math.max(cautionLeft, 20f);
                cautionMax = Math.max(cautionMax, cautionLeft);
            }
            g.state = GuardState.PATROL;
            g.pause = 0f;
            g.radioState = 0;
            if (g.wasAlert) {
                g.wasAlert = false;
                raiseCaution(g.x, g.y);
            }
            if (!quietEnd) {
                say(g, GIVE_UP_LINES);
            }
            // resume at the waypoint nearest to where the search ended
            float best = Float.MAX_VALUE;
            for (int i = 0; i < g.route.length; i++) {
                float dx = g.route[i][0] - g.x;
                float dy = g.route[i][1] - g.y;
                float d = dx * dx + dy * dy;
                if (d < best) {
                    best = d;
                    g.next = i;
                }
            }
        }
    }

    // ---- caution ----
    /**
     * The station learns someone is about: a body, a shot, a lost intruder. Guards on
     * patrol drop their loops and sweep the area round the disturbance for a while.
     */
    private void raiseCaution(float x, float y) {
        boolean fresh = cautionLeft <= 0f;
        cautionMax = CAUTION_BASE_TIME + CAUTION_TIME_PER_DIFFICULTY * Math.min(difficulty, 4);
        cautionLeft = cautionMax;
        cautionX = x;
        cautionY = y;
        if (fresh) {
            barks.add("(radio) Someone is on the station. Sweep the rooms.");
        }
        // rank patrolling guards by distance so the nearest few go to the spot itself
        List<Guard> byDistance = new ArrayList<Guard>();
        for (Guard g : guards) {
            if (g.state == GuardState.PATROL && !g.frozen) {
                byDistance.add(g);
            }
        }
        final float cx = x, cy = y;
        java.util.Collections.sort(byDistance, new java.util.Comparator<Guard>() {
            public int compare(Guard a, Guard b) {
                float da = (a.x - cx) * (a.x - cx) + (a.y - cy) * (a.y - cy);
                float db = (b.x - cx) * (b.x - cx) + (b.y - cy) * (b.y - cy);
                return Float.compare(da, db);
            }
        });
        for (int i = 0; i < byDistance.size(); i++) {
            Guard g = byDistance.get(i);
            g.hunt.clear();
            g.pause = 0f;
            g.homeSweep = i >= 2;
            if (!g.homeSweep) {
                float[] first = openPointNear(x, y, 40f);
                if (first != null) {
                    g.hunt.add(first);
                }
            }
            planHunt(g, g.homeSweep ? 3 : 2);
        }
    }

    /** Adds up to n more sweep points near the disturbance, chosen at random. */
    private void planHunt(Guard g, int n) {
        if (g.homeSweep) {
            for (int i = 0; i < n; i++) {
                float[] wp = g.route[rng.nextInt(g.route.length)];
                float[] p = openPointNear(wp[0], wp[1], 220f, g.x, g.y);
                if (p != null) {
                    g.hunt.add(p);
                }
            }
            return;
        }
        for (int i = 0; i < n; i++) {
            float[] last = g.hunt.isEmpty() ? new float[]{cautionX, cautionY} : g.hunt.get(g.hunt.size() - 1);
            float[] p = openPointNear(last[0], last[1], 450f);
            if (p != null) {
                g.hunt.add(p);
            }
        }
    }

    /** A random spot within r of (x, y) that a guard can stand on and walk to, or null. */
    private float[] openPointNear(float x, float y, float r) {
        return openPointNear(x, y, r, cautionX, cautionY);
    }

    /** As above, reachable from (fromX, fromY). */
    private float[] openPointNear(float x, float y, float r, float fromX, float fromY) {
        for (int tries = 0; tries < 25; tries++) {
            double a = rng.nextDouble() * Math.PI * 2.0;
            float d = (float) Math.sqrt(rng.nextDouble()) * r;
            float tx = Math.max(GUARD_RADIUS + 2f, Math.min(width - GUARD_RADIUS - 2f, x + (float) Math.cos(a) * d));
            float ty = Math.max(GUARD_RADIUS + 2f, Math.min(height - GUARD_RADIUS - 2f, y + (float) Math.sin(a) * d));
            if (hitsWall(tx, ty, GUARD_RADIUS + 2f)) {
                continue;
            }
            if (lineFree(fromX, fromY, tx, ty) || findPath(fromX, fromY, tx, ty) != null) {
                return new float[]{tx, ty};
            }
        }
        return null;
    }

    /** One step of a patrolling guard on caution. False if there is nothing to sweep and it should patrol. */
    private boolean huntStep(Guard g, float dt) {
        if (cautionLeft <= 0f) {
            return false;
        }
        if (g.pause > 0f) {
            g.pause -= dt;
            g.sweepClock += dt;
            float want = g.sweepBase + (float) Math.sin(g.sweepClock * 3f) * 75f;
            g.facing = turnToward(g.facing, want, CHASE_TURN_RATE * dt);
            return true;
        }
        if (g.hunt.isEmpty()) {
            planHunt(g, 2);
            if (g.hunt.isEmpty()) {
                return false;
            }
        }
        float[] t = g.hunt.get(0);
        float dx = t[0] - g.x;
        float dy = t[1] - g.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float speed = g.speed * CAUTION_SPEED;
        if (dist <= speed * dt + 4f || g.stuck > 1.5f) {
            g.hunt.remove(0);
            g.stuck = 0f;
            g.pause = CAUTION_PAUSE;
            g.sweepBase = g.facing;
            g.sweepClock = 0f;
            return true;
        }
        float moved = moveToward(g, t[0], t[1], speed, PATROL_TURN_RATE * 1.4f, dt, true);
        g.stuck = moved < speed * dt * 0.2f ? g.stuck + dt : 0f;
        return true;
    }

    /** True while the player is in the cone of any living guard (as in MGS3, where the counter does not drop while you are seen). */
    public boolean cautionHeld;

    private void updateCaution(float dt) {
        float before = cautionLeft;
        cautionHeld = false;
        if (cautionLeft > 0f) {
            for (Guard g : guards) {
                if (!g.frozen && g.state != GuardState.DEAD && sees(g)) {
                    cautionHeld = true;                     // caution does not run down while someone is looking at you
                    break;
                }
            }
            if (!cautionHeld) {
                cautionLeft = Math.max(0f, cautionLeft - dt);
            }
        }
        cautionBlend = approach(cautionBlend, cautionLeft > 0f ? 1f : 0f, CAUTION_BLEND_RATE * dt);
        if (before > 0f && cautionLeft <= 0f) {
            for (Guard g : guards) {
                g.hunt.clear();
                g.homeSweep = false;
                g.stuck = 0f;
                float best = Float.MAX_VALUE;
                for (int i = 0; i < g.route.length; i++) {
                    float dx = g.route[i][0] - g.x;
                    float dy = g.route[i][1] - g.y;
                    float d = dx * dx + dy * dy;
                    if (d < best) {
                        best = d;
                        g.next = i;
                    }
                }
            }
        }
    }

    // ---- the keycard: on a guard, and a short scene ----
    private void huddleStep(Guard g, float dt) {
        Guard other = g == carrier ? partner : carrier;
        if (other != null) {
            g.facing = turnToward(g.facing, angleTo(g.x, g.y, other.x, other.y), PATROL_TURN_RATE * dt);
        }
    }

    private void updateScene(float dt) {
        if (carrier == null || !carrier.huddle) {
            return;
        }
        // anything that disturbs them (a guard no longer calmly on patrol, the station on caution) ends the scene
        if (cautionLeft > 0f || carrier.state != GuardState.PATROL || partner.state != GuardState.PATROL) {
            endScene();
            return;
        }
        if (!sceneActive) {
            float dx = px - huddleX;
            float dy = py - huddleY;
            if (dx * dx + dy * dy <= HUDDLE_TRIGGER * HUDDLE_TRIGGER) {
                sceneActive = true;
                sceneIdx = 0;
                sceneTimer = 0.6f;
            }
            return;
        }
        sceneTimer -= dt;
        if (sceneTimer > 0f) {
            return;
        }
        if (sceneIdx < SCENE_LINES.length) {
            speak(sceneIdx % 2 == 0 ? partner : carrier, SCENE_LINES[sceneIdx]);
            sceneIdx++;
            sceneTimer = SCENE_LINE_GAP;
        } else {
            endScene();
        }
    }

    /** The talk is over: both go back to their routes, from the waypoint nearest to where they stand. */
    private void endScene() {
        for (Guard g : new Guard[]{carrier, partner}) {
            if (g == null || !g.huddle) {
                continue;
            }
            g.huddle = false;
            float best = Float.MAX_VALUE;
            for (int i = 0; i < g.route.length; i++) {
                float dx = g.route[i][0] - g.x;
                float dy = g.route[i][1] - g.y;
                float d = dx * dx + dy * dy;
                if (d < best) {
                    best = d;
                    g.next = i;
                }
            }
        }
        sceneActive = false;
    }

    // ---- inventory and toasts ----
    /** A short notice for the bottom of the view (a pickup, a swap), not the dialog log. */
    private void toast(String text) {
        toasts.add(text);
        while (toasts.size() > 12) {
            toasts.remove(0);
        }
    }

    /** A sound the panel should play: a key (see the panel's sound table) and where it came from. */
    public static class Sfx {
        public final String key;
        public final float x, y;

        Sfx(String key, float x, float y) {
            this.key = key;
            this.x = x;
            this.y = y;
        }
    }

    private final List<Sfx> sfx = new ArrayList<Sfx>();
    private float dryAt;

    private void sound(String key, float x, float y) {
        if (sfx.size() < 64) {                                  // nobody may be listening (the tests): it must not pile up
            sfx.add(new Sfx(key, x, y));
        }
    }

    public List<Sfx> drainSounds() {
        List<Sfx> out = new ArrayList<Sfx>(sfx);
        sfx.clear();
        return out;
    }

    public List<String> drainToasts() {
        List<String> out = new ArrayList<String>(toasts);
        toasts.clear();
        return out;
    }

    public int slotsUsed() {
        return (distractionsLeft > 0 ? 1 : 0) + (hasTool ? 1 : 0) + (hasSidearm ? 1 : 0) + (hasRifle ? 1 : 0) + (hasBaton ? 1 : 0);
    }

    public boolean owns(String weapon) {
        return ("pistol".equals(weapon) && hasSidearm) || ("rifle".equals(weapon) && hasRifle) || ("baton".equals(weapon) && hasBaton);
    }

    /** What is in the hands: the chosen weapon if they still have it, otherwise the best they have, otherwise fists. */
    public String activeWeapon() {
        if (held != null && owns(held)) {
            return held;
        }
        for (String w : new String[]{"pistol", "rifle", "baton"}) {
            if (owns(w)) {
                return w;
            }
        }
        return "fist";
    }

    /** The slots, in the order they are shown: guns first, then the bracket, then the distractions. */
    public List<String> slotItems() {
        List<String> out = new ArrayList<String>();
        for (String w : new String[]{"pistol", "rifle", "baton"}) {
            if (owns(w)) {
                out.add(w);
            }
        }
        if (hasTool) {
            out.add("tool");
        }
        if (distractionsLeft > 0) {
            out.add("distract");
        }
        return out;
    }

    public int heldAmmo() {
        String w = activeWeapon();
        return "rifle".equals(w) ? rifleAmmo : ("pistol".equals(w) ? ammo : 0);
    }

    public int heldAmmoMax() {
        String w = activeWeapon();
        return "rifle".equals(w) ? RIFLE_AMMO_MAX : ("pistol".equals(w) ? SIDEARM_AMMO : 0);
    }

    private String weaponName(String w) {
        return "rifle".equals(w) ? "Rifle (" + rifleAmmo + ")" : ("pistol".equals(w) ? "Sidearm (" + ammo + ")" : ("baton".equals(w) ? "Baton" : "Fists"));
    }

    /** Takes the weapon in this slot of the display (1-4), if it is a weapon. */
    public boolean selectSlot(int index) {
        List<String> items = slotItems();
        if (index < 0 || index >= items.size() || !owns(items.get(index))) {
            return false;
        }
        held = items.get(index);
        toast("Holding: " + weaponName(held));
        hints.event("swap", px, py, this);
        return true;
    }

    /** The next weapon they have. */
    public boolean cycleWeapon() {
        List<String> ws = new ArrayList<String>();
        for (String w : new String[]{"pistol", "rifle", "baton"}) {
            if (owns(w)) {
                ws.add(w);
            }
        }
        if (ws.size() < 2) {
            return false;
        }
        held = ws.get((ws.indexOf(activeWeapon()) + 1) % ws.size());
        toast("Holding: " + weaponName(held));
        hints.event("swap", px, py, this);
        return true;
    }

    /** Puts the held weapon (or, with none, the bracket) down at their feet, to be picked up again after a moment. */
    public boolean dropHeld() {
        String w = activeWeapon();
        Pickup pk = null;
        if ("pistol".equals(w)) {
            pk = new Pickup("sidearm", px, py);
            pk.count = ammo;
            hasSidearm = false;
        } else if ("rifle".equals(w)) {
            pk = new Pickup("rifle", px, py);
            pk.count = rifleAmmo;
            hasRifle = false;
        } else if ("baton".equals(w)) {
            pk = new Pickup("baton", px, py);
            hasBaton = false;
        } else if (hasTool) {
            pk = new Pickup("tool", px, py);
            hasTool = false;
        }
        if (pk == null) {
            return false;
        }
        pk.freeAt = time + 1.5f;
        pickups.add(pk);
        toast("Dropped: " + ("sidearm".equals(pk.type) ? "sidearm" : pk.type));
        sound("drop", px, py);
        hints.event("dropped", px, py, this);
        return true;
    }

    /** A guard goes down: his gear lies where he fell (a baton, or the rifle of a gunner), once. */
    private void dropGear(Guard g) {
        if (!escalates || g.gearDropped || g.flood) {
            return;
        }
        g.gearDropped = true;
        if (lethalKnown && g.role == ROLE_GUN) {
            Pickup rifle = new Pickup("rifle", g.x, g.y);
            rifle.count = 5 + rng.nextInt(4);
            pickups.add(rifle);
        } else {
            pickups.add(new Pickup("baton", g.x, g.y));
        }
    }

    /** What the player is trying to do now, from where they are; null when there is nothing to say. */
    public String objective() {
        if (status != Status.RUNNING) {
            return null;
        }
        if (tutorialMode) {
            return tutorialObjective();
        }
        Lock door = lockNamed("D");
        Lock east = lockNamed("X");
        Lock vent = lockNamed("V");
        if (vent != null) {
            if (!vent.open) {
                return hasTool ? "Open the vent" : "Find a way out of the cell";
            }
            return "Follow the shaft";
        }
        if (east != null) {
            if (fightStarted) {
                return exitSealed() ? "Break the security detail" : "Reach the shuttle lock";
            }
            if (east.open) {
                return "Go through the east door";
            }
            return hasKey2 ? "Open the east door" : "Take the officer's card from the sergeant";
        }
        if (door != null) {
            if (door.open) {
                return "Reach the hub";
            }
            if (!hasKey) {
                if (ventExit != null && !ventOpen) {
                    return hasTool ? "Open the vent" : "Find a way out of the cell";
                }
                return "Get the keycard from the guard";
            }
            if (hasRoland && !rolandFree) {
                return "Free Roland";
            }
            return "Open the door";
        }
        return null;
    }

    /** An optional thing to do alongside, or null. */
    public String sideObjective() {
        if (allies.isEmpty() || allyBreakout || lockNamed("X") == null) {
            return null;
        }
        int people = 0;
        for (Ally a : allies) {
            people += a.isRoland ? 0 : 1;
        }
        if (alliesFreed() < people) {
            return "Optional: free the prisoners";
        }
        Lock armory = lockNamed("S");
        return armory != null && !armory.open ? "Optional: open the armory so they can arm" : null;
    }

    private void updateObjective() {
        String now = objective();
        if (now != null && !now.equals(lastObjective)) {
            if (lastObjective != null) {
                toast("Objective: " + now);
            }
            lastObjective = now;
        }
    }

    /** A guard carrying the keycard has been put down: it drops where he fell. */
    private void dropKey(Guard g) {
        dropGear(g);
        if (g.flood && !g.ammoDropped) {
            g.ammoDropped = true;
            pickups.add(new Pickup("ammo", g.x, g.y));         // his sidearm, for whoever has one
        }
        if (g.hasKey2) {
            g.hasKey2 = false;
            pickups.add(new Pickup("key2", g.x, g.y));
            toast("An officer's card comes off his belt");
        }
        if (!g.hasKey) {
            return;
        }
        g.hasKey = false;
        pickups.add(new Pickup("key", g.x, g.y));
        toast("A keycard comes off his belt");
    }

    // ---- radioing it in ----
    /** A guard has just gone alert: pick between pursuing and calling a moment later, or breaking off to call from cover. */
    private void beginRadio(Guard g) {
        beginRadio(g, px, py);
    }

    /** As above, but hiding from (fromX, fromY): the player normally, the body for a guard who found one. */
    private void beginRadio(Guard g, float fromX, float fromY) {
        // calls happen at every difficulty: a call is also how the word gets out that the player kills.
        // Whether it brings anyone (callBackup) is what depends on difficulty.
        if (g.radioState != 0 || !mayCall(g)) {
            return;
        }
        if (rng.nextFloat() < BREAK_OFF_CHANCE) {
            float[] spot = coverFrom(g, fromX, fromY);
            if (spot != null) {
                g.radioState = 2;
                g.radioX = spot[0];
                g.radioY = spot[1];
                g.radioTimer = 5f;                      // a guard that cannot get there calls anyway
                g.radioTimerMax = 5f;
                return;
            }
        }
        g.radioState = 1;
        g.radioTimer = g.witness
                ? WITNESS_RADIO_DELAY_MIN + rng.nextFloat() * (WITNESS_RADIO_DELAY_MAX - WITNESS_RADIO_DELAY_MIN)
                : RADIO_DELAY_MIN + rng.nextFloat() * (RADIO_DELAY_MAX - RADIO_DELAY_MIN);
        g.radioTimerMax = g.radioTimer;
    }

    /** True while a guard is deciding, running to cover or speaking, or a call is going out. */
    public boolean callInProgress() {
        if (!pendingCalls.isEmpty()) {
            return true;
        }
        for (Guard o : guards) {
            if (o.state != GuardState.DEAD && !o.frozen && (o.radioState == 1 || o.radioState == 2 || o.radioState == 3)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Only one call at a time, and none while the station is already on caution from an earlier one. A guard who knows the player
     * kills may still call to say so, if that has not been reported.
     */
    private boolean mayCall(Guard g) {
        if (callInProgress()) {
            return false;
        }
        return cautionLeft <= 0f || (g.knowsLethal && !lethalKnown);
    }

    /** A spot near the guard the player cannot see, farther from the player than the guard is, or null. */
    private float[] coverFrom(Guard g, float fromX, float fromY) {
        float own = (float) Math.hypot(g.x - fromX, g.y - fromY);
        for (int tries = 0; tries < 30; tries++) {
            double a = rng.nextDouble() * Math.PI * 2.0;
            float d = 60f + rng.nextFloat() * (BREAK_OFF_RANGE - 60f);
            float tx = g.x + (float) Math.cos(a) * d;
            float ty = g.y + (float) Math.sin(a) * d;
            if (tx < GUARD_RADIUS || ty < GUARD_RADIUS || tx > width - GUARD_RADIUS || ty > height - GUARD_RADIUS
                    || hitsWall(tx, ty, GUARD_RADIUS + 2f)) {
                continue;
            }
            if (Math.hypot(tx - fromX, ty - fromY) < own) {
                continue;
            }
            boolean hidden = false;
            for (Rect w : walls) {
                if (w.blocks(fromX, fromY, tx, ty)) {
                    hidden = true;
                    break;
                }
            }
            if (hidden && (lineFree(g.x, g.y, tx, ty) || findPath(g.x, g.y, tx, ty) != null)) {
                return new float[]{tx, ty};
            }
        }
        return null;
    }

    /**
     * One step of an alert guard that is calling it in. True while it is busy
     * (running to cover, or standing and speaking) so it does not chase meanwhile.
     */
    private boolean radioBusy(Guard g, float dt) {
        switch (g.radioState) {
            case 1:
                g.radioTimer -= dt;
                if (g.radioTimer <= 0f) {
                    startSpeaking(g);
                }
                return false;                           // still chasing while it decides
            case 2:
                g.radioTimer -= dt;
                float dx = g.radioX - g.x;
                float dy = g.radioY - g.y;
                if (Math.sqrt(dx * dx + dy * dy) < 8f || g.radioTimer <= 0f) {
                    startSpeaking(g);
                    return true;
                }
                moveToward(g, g.radioX, g.radioY, ALERT_SPEED, CHASE_TURN_RATE, dt, false);
                return true;
            case 3:
                g.radioTimer -= dt;
                if (g.radioTimer <= 0f) {
                    g.radioState = 4;
                }
                return true;                            // stands still while it speaks
            default:
                return false;
        }
    }

    private void startSpeaking(Guard g) {
        g.radioState = 3;
        g.radioTimer = RADIO_SPEECH;
        speak(g, "(guard) Control, I have an intruder -");
        pendingCalls.add(new PendingCall(g, RADIO_SPEECH));
    }

    private void updatePendingCalls(float dt) {
        for (int i = pendingCalls.size() - 1; i >= 0; i--) {
            PendingCall pc = pendingCalls.get(i);
            pc.left -= dt;
            if (pc.left <= 0f) {
                pendingCalls.remove(i);
                if (pc.caller.knowsLethal && !lethalKnown) {
                    lethalKnown = true;
                    barks.add("(radio) Hostile is armed and has killed. Weapons free.");
                }
                raiseCaution(pc.caller.targetX, pc.caller.targetY);     // the station knows now, at every difficulty
                callBackup(pc.caller);
            }
        }
    }

    // ---- backup ----
    /** Radio call and reinforcements, by difficulty. Called when a guard goes alert. */
    private void callBackup(Guard caller) {
        if (difficulty < 1) {
            return;
        }
        barks.add("(radio) Intruder spotted - all units respond!");
        raiseCaution(caller.targetX, caller.targetY);
        // only the nearest few answer; the rest go on caution and keep their own patch covered
        List<Guard> near = new ArrayList<Guard>();
        for (Guard other : guards) {
            if (other == caller || other.frozen || other.state == GuardState.ALERT || other.state == GuardState.DEAD) {
                continue;
            }
            float dx = other.x - caller.x;
            float dy = other.y - caller.y;
            if (dx * dx + dy * dy <= BACKUP_RADIUS * BACKUP_RADIUS) {
                near.add(other);
            }
        }
        final float cx = caller.x, cy = caller.y;
        java.util.Collections.sort(near, new java.util.Comparator<Guard>() {
            public int compare(Guard p, Guard q) {
                return Float.compare((p.x - cx) * (p.x - cx) + (p.y - cy) * (p.y - cy),
                        (q.x - cx) * (q.x - cx) + (q.y - cy) * (q.y - cy));
            }
        });
        int limit = Math.min(BACKUP_MAX, BACKUP_MAX_BASE + difficulty);
        for (int i = 0; i < near.size() && i < limit; i++) {
            Guard other = near.get(i);
            float[] spot = openPointNear(caller.targetX, caller.targetY, RESPONDER_SPREAD, caller.targetX, caller.targetY);
            other.state = GuardState.ALERT;
            other.radioState = 4;
            other.hurry = false;
            other.bodyAlarm = false;
            other.homeSweep = false;
            other.hunt.clear();
            other.path = null;
            other.stuck = 0f;
            other.targetX = spot != null ? spot[0] : caller.targetX;
            other.targetY = spot != null ? spot[1] : caller.targetY;
        }
        if (reinforcementsLeft > 0 && reinforceTimer < 0f) {
            reinforceTimer = REINFORCE_DELAY;
        }
    }

    private void updateReinforcements(float dt) {
        if (reinforceTimer < 0f) {
            return;
        }
        reinforceTimer -= dt;
        if (reinforceTimer > 0f) {
            return;
        }
        float sx = exit.x + exit.w / 2f;
        float sy = exit.y + exit.h / 2f;
        Guard arrival = new Guard(60f, new float[][]{{sx, sy}, {sx - 140f, sy}});
        arrival.state = GuardState.ALERT;
        arrival.radioState = 4;
        arrival.targetX = lastAlertX;
        arrival.targetY = lastAlertY;
        guards.add(arrival);
        barks.add("(radio) Reinforcements on site.");
        reinforcementsLeft--;
        // another one follows while someone is still hunting
        boolean hunting = false;
        for (Guard g : guards) {
            if (g.state == GuardState.ALERT) {
                hunting = true;
            }
        }
        reinforceTimer = (reinforcementsLeft > 0 && hunting) ? REINFORCE_DELAY : -1f;
    }

    // ---- pickups and the sidearm ----
    private void updatePickups() {
        for (int i = pickups.size() - 1; i >= 0; i--) {
            Pickup pk = pickups.get(i);
            float dx = pk.x - px;
            float dy = pk.y - py;
            if (dx * dx + dy * dy > (PICKUP_RADIUS + PLAYER_RADIUS) * (PICKUP_RADIUS + PLAYER_RADIUS) || time < pk.freeAt) {
                continue;
            }
            String type = pk.type;
            if ("sidearm".equals(type) || "rifle".equals(type) || "baton".equals(type) || "tool".equals(type)) {
                boolean rifle = "rifle".equals(type);
                boolean pistol = "sidearm".equals(type);
                boolean owned = pistol ? hasSidearm : (rifle ? hasRifle : ("baton".equals(type) ? hasBaton : hasTool));
                if (owned) {
                    if (pistol || rifle) {
                        // a gun they already have: the rounds in it go into theirs
                        int give = pk.count > 0 ? pk.count : (pistol ? SIDEARM_AMMO : 5);
                        if (pistol) {
                            ammo = Math.min(SIDEARM_AMMO, ammo + give);
                        } else {
                            rifleAmmo = Math.min(RIFLE_AMMO_MAX, rifleAmmo + give);
                        }
                        toast("+" + give + " rounds");
                        pickups.remove(i);
                    }
                    continue;
                }
                if (slotsUsed() >= INVENTORY_SLOTS) {
                    if (!pk.refused) {
                        pk.refused = true;
                        toast("Inventory full: press G to drop something");
                hints.event("bagFull", px, py, this);
                    }
                    continue;
                }
                boolean wasEmpty = activeWeapon().equals("fist");
                if (pistol) {
                    hasSidearm = true;
                    ammo = pk.count > 0 ? pk.count : SIDEARM_AMMO;
                    toast("Sidearm (" + ammo + ")");
                } else if (rifle) {
                    hasRifle = true;
                    rifleAmmo = pk.count > 0 ? pk.count : 5;
                    toast("Rifle (" + rifleAmmo + ")");
                } else if ("baton".equals(type)) {
                    hasBaton = true;
                    toast("Baton");
                } else {
                    hasTool = true;
                    toast("Bracket");
                }
                if (wasEmpty && !"tool".equals(type)) {
                    held = pistol ? "pistol" : (rifle ? "rifle" : "baton");
                }
            } else if ("distract".equals(type)) {
                if (distractionsLeft >= START_DISTRACTIONS) {
                    continue;                                   // a full hand leaves it there for later
                }
                distractionsLeft = START_DISTRACTIONS;
                throwablesFound = true;
                toast("Something to throw: E");
                hints.event("gotThrowable", px, py, this);
            } else if ("key".equals(type)) {
                hasKey = true;
                toast("Keycard");
            } else if ("key2".equals(type)) {
                hasKey2 = true;
                toast("Officer's card");
            } else if ("ammo".equals(type)) {
                if (!hasSidearm && !hasRifle) {
                    continue;
                }
                if (hasSidearm) {
                    ammo = Math.min(SIDEARM_AMMO, ammo + 2);
                } else {
                    rifleAmmo = Math.min(RIFLE_AMMO_MAX, rifleAmmo + 2);
                }
                toast("+2 rounds");
            }
            sound("sidearm".equals(type) || "rifle".equals(type) || "baton".equals(type) ? "pickGun" : "pick", pk.x, pk.y);
            pickups.remove(i);
        }
    }

    /**
     * Fires toward (tx, ty): a bullet leaves at BULLET_SPEED and flies until it
     * meets a wall, a guard, or SIDEARM_RANGE. The shot itself is loud right
     * away; what it hits makes a second, smaller noise. False if there is no
     * sidearm, no ammo, the gun is still cycling, or the run is over.
     */
    public boolean fire(float tx, float ty) {
        if (crawling()) {
            return false;                                       // no room in the shaft
        }
        if (grabbed != null) {
            return grabStrike();
        }
        String weapon = activeWeapon();
        if ("baton".equals(weapon)) {
            return playerMelee(tx, ty);                         // with a baton in hand the trigger is a swing
        }
        boolean rifle = "rifle".equals(weapon);
        boolean loaded = rifle ? (hasRifle && rifleAmmo > 0) : (hasSidearm && ammo > 0);
        if (!loaded || fireCooldown > 0f || status != Status.RUNNING || carrying != null) {
            if (!loaded && (rifle ? hasRifle : hasSidearm) && status == Status.RUNNING && carrying == null && time >= dryAt) {
                dryAt = time + 0.5f;
                sound("dry", px, py);                           // the click of an empty gun
            }
            return false;
        }
        float dx = tx - px;
        float dy = ty - py;
        float angle = (dx * dx + dy * dy) < 1f ? 0f : angleTo(px, py, tx, ty);
        if (rifle) {
            rifleAmmo--;
        } else {
            ammo--;
        }
        playerFlash = 1f;
        sound("shot", px, py);
        fireCooldown = rifle ? RIFLE_COOLDOWN : FIRE_COOLDOWN;
        shake = Math.max(shake, SHAKE_FIRE * (rifle ? 1.3f : 1f));

        Bullet b = rifle ? new Bullet(px, py, angle, true, BULLET_SPEED * 1.25f, SIDEARM_RANGE * 1.5f) : new Bullet(px, py, angle, true);
        b.damage = rifle ? RIFLE_DAMAGE : PISTOL_DAMAGE;
        bullets.add(b);
        shots.add(new Shot(px, py, px + b.dx * 16f, py + b.dy * 16f, false));   // muzzle flash

        float noise = SHOT_NOISE_RADIUS * (rifle ? 1.3f : 1f);                  // a rifle carries further
        Ripple boom = new Ripple(px, py, noise, 1f, true);
        boom.shot = true;
        ripples.add(boom);
        makeNoise(px, py, noise, true);
        return true;
    }

    /**
     * A loud noise at (x, y): every guard who is not already hunting and is within
     * the radius (each wall in between counts as extra distance) turns suspicious
     * toward it and hurries there. A gunshot does more: see the body of the method.
     * True if anyone heard.
     */
    private boolean makeNoise(float x, float y, float radius, boolean gunshot) {
        List<Guard> hearers = new ArrayList<Guard>();
        for (Guard g : guards) {
            if (g.frozen || g.state == GuardState.DEAD || g.state == GuardState.ALERT) {
                continue;
            }
            float gx = g.x - x;
            float gy = g.y - y;
            float heardAt = (float) Math.sqrt(gx * gx + gy * gy) + SHOT_WALL_MUFFLE * wallsBetween(g.x, g.y, x, y);
            if (heardAt <= radius) {
                becomeSuspicious(g, x, y, true);
                g.hurry = true;
                hearers.add(g);
            }
        }
        if (gunshot && !hearers.isEmpty()) {
            // a gunshot puts everyone who hears it into the modified alert (alert speed, red cone, a harder
            // search); the nearest calls it in, and the station goes on caution when that call goes out
            Guard nearest = null;
            float nd = Float.MAX_VALUE;
            for (Guard g : hearers) {
                float d = (g.x - x) * (g.x - x) + (g.y - y) * (g.y - y);
                g.hurry = false;
                g.witness = true;
                g.bodyAlarm = true;
                g.noticeLeft = Math.min(g.noticeLeft, 0.2f);
                if (d < nd) {
                    nd = d;
                    nearest = g;
                }
            }
            beginRadio(nearest, x, y);
        }
        return !hearers.isEmpty();
    }

    private void updateBullets(float dt) {
        for (int i = bullets.size() - 1; i >= 0; i--) {
            Bullet b = bullets.get(i);
            float move = Math.min(b.speed * dt, b.range - b.travelled);
            if (!advanceBullet(b, move)) {
                bullets.remove(i);
            }
        }
    }

    /** Test and tool helper: lets every bullet in the air land, without moving anyone else. */
    public void flushBullets() {
        for (int guard = 0; guard < 500 && !bullets.isEmpty(); guard++) {
            updateBullets(0.01f);
        }
    }

    /** Moves a bullet one step. False once it has hit something or run out of range. */
    private boolean advanceBullet(Bullet b, float move) {
        float wall = rayDistance(b.x, b.y, b.angle, move);
        boolean hitWall = wall < move - 0.001f;
        float reach = hitWall ? wall : move;
        Guard hit = null;
        float hitAt = reach;
        boolean hitPlayer = false;
        if (!b.fromPlayer) {
            float gx = px - b.x;
            float gy = py - b.y;
            float along = gx * b.dx + gy * b.dy;
            float off = Math.abs(gx * b.dy - gy * b.dx);
            float pr = PLAYER_RADIUS + 2f;
            if (off <= pr && woundGrace <= 0f) {
                float half = (float) Math.sqrt(pr * pr - off * off);
                float enter = along - half;
                if (along + half >= 0f && enter <= reach) {
                    hitPlayer = true;
                    hitAt = Math.max(0f, enter);
                }
            }
        }
        float playerAt = hitAt;
        for (Guard g : guards) {
            if (g.state == GuardState.DEAD || g == b.owner) {
                continue;                                   // a guard's own bullet cannot hit him; others can
            }
            float gx = g.x - b.x;
            float gy = g.y - b.y;
            // the bullet's path this step is a capsule, so a guard walking into it between steps
            // cannot slip through the gap, and one overlapping the start is hit at once
            float along = gx * b.dx + gy * b.dy;
            float off = Math.abs(gx * b.dy - gy * b.dx);
            if (off > GUARD_HIT_RADIUS) {
                continue;
            }
            float half = (float) Math.sqrt(GUARD_HIT_RADIUS * GUARD_HIT_RADIUS - off * off);
            float enter = along - half;
            if (along + half < 0f || enter > reach) {
                continue;
            }
            float at = Math.max(0f, enter);
            if (at <= hitAt) {
                hit = g;
                hitAt = at;
            }
        }
        Ally allyHit = null;
        if (!b.fromPlayer) {
            float allyAt = hitAt;
            for (Ally a : allies) {
                if (!a.freed || a.dead) {
                    continue;
                }
                float ax = a.x - b.x;
                float ay = a.y - b.y;
                float along = ax * b.dx + ay * b.dy;
                float off = Math.abs(ax * b.dy - ay * b.dx);
                float rr = allyRadius(a) + 2f;
                if (off > rr) {
                    continue;
                }
                float half = (float) Math.sqrt(rr * rr - off * off);
                float enter = along - half;
                if (along + half < 0f || enter > reach) {
                    continue;
                }
                float at = Math.max(0f, enter);
                if (at <= allyAt) {
                    allyHit = a;
                    allyAt = at;
                }
            }
        }
        if (allyHit != null) {
            bloodDir = (float) Math.toRadians(b.angle);
            hurtAlly(allyHit, b.damage);
            ripples.add(new Ripple(allyHit.x, allyHit.y, IMPACT_NOISE_RADIUS, 0.5f, false));
            return false;
        }
        Light lampHit = null;
        float lampAt = hitAt;
        for (Light lamp : lights) {
            if (!lamp.on) {
                continue;
            }
            float lx = lamp.x - b.x;
            float ly = lamp.y - b.y;
            float along = lx * b.dx + ly * b.dy;
            float off = Math.abs(lx * b.dy - ly * b.dx);
            if (off > LIGHT_HIT_RADIUS) {
                continue;
            }
            float half = (float) Math.sqrt(LIGHT_HIT_RADIUS * LIGHT_HIT_RADIUS - off * off);
            float enter = along - half;
            if (along + half < 0f || enter > reach) {
                continue;
            }
            float at = Math.max(0f, enter);
            if (at <= lampAt) {
                lampHit = lamp;
                lampAt = at;
            }
        }
        if (lampHit != null) {
            breakLight(lampHit, b.fromPlayer);              // the fixture takes the round
            return false;
        }
        if (hit != null && hitPlayer) {
            if (playerAt <= hitAt) {
                hit = null;                                 // the player was nearer
            } else {
                hitPlayer = false;                          // a guard stood in the way
            }
        }
        if (hit == null && !hitWall && !hitPlayer) {
            b.x += b.dx * move;
            b.y += b.dy * move;
            b.travelled += move;
            return b.travelled < b.range;
        }
        float ix = b.x + b.dx * hitAt;
        float iy = b.y + b.dy * hitAt;
        shots.add(new Shot(ix, iy, ix, iy, hit != null || hitPlayer));
        Ripple thud = new Ripple(ix, iy, IMPACT_NOISE_RADIUS, 0.7f, b.fromPlayer);
        thud.shot = true;
        ripples.add(thud);
        bloodDir = (float) Math.toRadians(b.angle);
        if (hit != null && b.fromPlayer) {
            shootGuard(hit, b);
        } else if (hit != null) {
            hurtGuard(hit, b.damage, true);                 // friendly fire: a guard's round costs a guard hit points
        }
        if (hitPlayer) {
            wound(b.damage);
        }
        makeNoise(ix, iy, IMPACT_NOISE_RADIUS, false);
        if (hit != null && b.fromPlayer && hit.state == GuardState.DEAD) {
            witnessKill(hit);                               // after the noise, so seeing it wins over hearing it
        }
        return false;
    }

    /**
     * A round from the player or a prisoner hits a guard: it takes its damage (double from behind, if he does not know the player is there); at zero
     * he is dead, and short of that he reels, bleeds and, if it was the player's round, turns on the player.
     */
    private void shootGuard(Guard g, Bullet b) {
        int dmg = SHOTS_KILL ? 999 : b.damage;
        boolean unaware = g.state != GuardState.ALERT;      // the shot itself may have stirred him (a noise) but he has not found the player
        if (!b.fromAlly && unaware && Math.abs(angleDiff(g.facing, b.angle)) <= BACKSHOT_ARC_DEG) {
            dmg *= 2;                                       // in the back
        }
        g.hp -= dmg;
        g.bloody = true;
        if (g.hp <= 0) {
            killGuard(g);
            if (b.fromAlly) {
                kills--;                                    // not the player's doing
            }
            return;
        }
        popup(g.x, g.y, "-" + dmg, POP_DAMAGE);
        spatter(g.x, g.y, 3, 10f, 4f);
        double away = Math.toRadians(b.angle);
        knock(g, away, 6f);
        hitMark(g, away + Math.PI);
        g.stun = Math.max(STUN_TIME, g.brokenTime);
        g.meleeTime = 0f;
        g.aimTime = 0f;
        g.burstLeft = 0;
        if (!b.fromAlly) {
            g.attackedByPlayer = ATTACK_MEMORY;
            if (g.state != GuardState.ALERT) {
                g.targetX = px;
                g.targetY = py;
                becomeAlert(g);
            }
        }
    }

    public void wound() {
        wound(WOUND_DAMAGE);
    }

    public void wound(int damage) {
        if (tutorialMode) {
            woundFlash = 1f;                                    // training: felt, not counted
            shake = Math.max(shake, 0.6f);
            return;
        }
        if (fightStarted) {
            float[] cover = nearestCover();
            if (cover != null) {
                hints.event("fightWound", cover[0], cover[1], this);
            }
        }
        spatter(px, py, 3, 12f, 4f);
        bleedLeft = 25f;
        dripX = px;
        dripY = py;
        wounds += damage;
        popup(px, py, "-" + damage, POP_DAMAGE);
        woundGrace = WOUND_GRACE;
        shake = 1f;
        woundFlash = 1f;
        alertFlash = 1f;
        if (wounds >= MAX_WOUNDS) {
            status = Status.CAUGHT;
        }
    }

    /**
     * An alert guard who knows the player kills raises a weapon when he has a clear
     * line within range. He stands and aims for GUARD_AIM_TIME (the aim stops tracking
     * a little before the shot), fires, then carries on hunting until he can shoot
     * again. True while he is standing and aiming.
     */
    private boolean guardShoot(Guard g, float dt) {
        return guardShoot(g, dt, GUARD_AIM_TIME);
    }

    /** As above, taking `aimTime` seconds to line up the shot (longer when leaning out from cover); the aim locks 0.15 s before. */
    private boolean guardShoot(Guard g, float dt, float aimTime) {
        g.shotCooldown = Math.max(0f, g.shotCooldown - dt);
        if (g.burstLeft > 0) {
            // the rest of a volley: he stays put and fires again, aimed at where the player is now
            g.burstTimer -= dt;
            if (g.burstTimer <= 0f) {
                g.burstLeft--;
                g.burstTimer = GUARD_BURST_GAP;
                float[] tgt = lead(g, shootTarget(g));
                if (clearTo(g, tgt[0], tgt[1])) {
                    g.aimX = tgt[0];
                    g.aimY = tgt[1];
                }
                if (!allyInLineOfFire(g, g.aimX, g.aimY)) {
                    fireGuardBullet(g);
                }
                if (g.burstLeft == 0) {
                    g.shotCooldown = GUARD_FIRE_COOLDOWN;
                }
            }
            g.facing = turnToward(g.facing, angleTo(g.x, g.y, g.aimX, g.aimY), CHASE_TURN_RATE * dt);
            return true;
        }
        float[] tgt = shootTarget(g);
        float d = (float) Math.hypot(tgt[0] - g.x, tgt[1] - g.y);
        boolean can = lethalKnown && g.role == ROLE_GUN && d <= GUARD_FIRE_RANGE && d > MELEE_START && g.shotCooldown <= 0f && clearTo(g, tgt[0], tgt[1])
                && !allyInLineOfFire(g, tgt[0], tgt[1]);
        if (!can) {
            g.aimTime = 0f;
            return false;
        }
        g.aimTime += dt;
        g.aimNeeded = aimTime;
        if (g.aimTime < aimTime - (GUARD_AIM_TIME - GUARD_AIM_LOCK)) {
            float[] led = lead(g, tgt);
            g.aimX = led[0];
            g.aimY = led[1];
        }
        g.facing = turnToward(g.facing, angleTo(g.x, g.y, g.aimX, g.aimY), CHASE_TURN_RATE * dt);
        if (g.aimTime >= aimTime) {
            fireGuardBullet(g);
            g.aimTime = 0f;
            g.burstLeft = GUARD_BURST - 1;
            g.burstTimer = GUARD_BURST_GAP;
            if (g.burstLeft == 0) {
                g.shotCooldown = GUARD_FIRE_COOLDOWN;
            }
        }
        return true;
    }

    /** True if another living guard stands in the way of a shot from g toward (tx, ty). */
    private boolean allyInLineOfFire(Guard g, float tx, float ty) {
        float dx = tx - g.x;
        float dy = ty - g.y;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1f) {
            return false;
        }
        for (Guard o : guards) {
            if (o == g || o.state == GuardState.DEAD) {
                continue;
            }
            if (distToSegment(o.x, o.y, g.x, g.y, tx, ty) <= GUARD_HIT_RADIUS + 4f
                    && (o.x - g.x) * dx + (o.y - g.y) * dy > 0f) {
                return true;
            }
        }
        return false;
    }

    /** Distance from the point (x, y) to the segment a-b. */
    private static float distToSegment(float x, float y, float ax, float ay, float bx, float by) {
        float abx = bx - ax;
        float aby = by - ay;
        float len2 = abx * abx + aby * aby;
        float t = len2 < 0.0001f ? 0f : Math.max(0f, Math.min(1f, ((x - ax) * abx + (y - ay) * aby) / len2));
        float cx = ax + abx * t;
        float cy = ay + aby * t;
        return (float) Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
    }

    /** One bullet from a guard toward his aim point, with the bang. */
    private void fireGuardBullet(Guard g) {
        g.flash = 1f;
        sound("shot", g.x, g.y);
        float angle = angleTo(g.x, g.y, g.aimX, g.aimY) + stray(GUARD_SPREAD_DEG, g.recoilDeg);
        g.recoilDeg += RECOIL_DEG;
        Bullet b = new Bullet(g.x, g.y, angle, false, GUARD_BULLET_SPEED, GUARD_FIRE_RANGE + 80f);
        b.damage = GUARD_BULLET_DAMAGE;
        b.owner = g;
        b.x += b.dx * 12f;
        b.y += b.dy * 12f;
        bullets.add(b);
        shots.add(new Shot(g.x, g.y, g.x + b.dx * 16f, g.y + b.dy * 16f, false));
        Ripple boom = new Ripple(g.x, g.y, SHOT_NOISE_RADIUS, 1f, false);
        boom.shot = true;
        ripples.add(boom);
        makeNoise(g.x, g.y, SHOT_NOISE_RADIUS, true);
    }

    /**
     * An alert guard who has reached the player swings: he stands and raises his arm for
     * MELEE_WINDUP (the swing carries on even if the player steps away), then strikes.
     * The blow lands only if the player is still within MELEE_REACH with a clear line, and
     * not in the grace after a wound. A landed blow wounds and knocks the player back.
     * True while he is swinging or recovering in place.
     */
    private boolean guardMelee(Guard g, float dt) {
        g.meleeCooldown = Math.max(0f, g.meleeCooldown - dt);
        if (g.meleeTime <= 0f) {
            if (allyTakesPriority(g) && Math.hypot(g.foeAlly.x - g.x, g.foeAlly.y - g.y) <= MELEE_START * DUEL_RANGE) {
                return false;                                   // someone is hitting him: he turns on them
            }
            if (DUEL_VS_PLAYER && g.meleeCooldown > 0f && distToPlayer(g) <= MELEE_START * DUEL_RANGE && clearLine(g) && Math.hypot(vx, vy) < DUEL_PLAYER_SPEED) {
                duelGuard(g, px, py, dt);                       // between blows he moves round a little, and the rest of his hunting carries on
                return false;
            }
            if (g.meleeCooldown > 0f || distToPlayer(g) > MELEE_START || !clearLine(g)) {
                return false;
            }
            g.meleeTime = dt;                           // starts the swing
            return true;
        }
        g.meleeTime += dt;
        g.facing = turnToward(g.facing, angleTo(g.x, g.y, px, py), CHASE_TURN_RATE * dt);
        if (g.meleeTime >= MELEE_WINDUP) {
            g.meleeTime = 0f;
            g.meleeCooldown = MELEE_COOLDOWN;
            g.lungeTime = LUNGE_TIME;
                sound("swing", g.x, g.y);
            if (woundGrace <= 0f && distToPlayer(g) <= MELEE_REACH && clearLine(g)) {
                float dx = px - g.x;
                float dy = py - g.y;
                float len = (float) Math.sqrt(dx * dx + dy * dy);
                if (len > 0.001f) {
                    float nx = px + dx / len * KNOCKBACK;
                    float ny = py + dy / len * KNOCKBACK;
                    if (!hitsWall(nx, py, PLAYER_RADIUS)) {
                        px = nx;
                    }
                    if (!hitsWall(px, ny, PLAYER_RADIUS)) {
                        py = ny;
                    }
                }
                vx = 0f;
                vy = 0f;
                impactFx((px + g.x) / 2f, (py + g.y) / 2f, Math.atan2(py - g.y, px - g.x), 0);
                wound();
            }
        }
        return true;
    }

    // ---- how hunting guards divide the work ----
    /**
     * Hunting guards share out the fight. With no word out that the player kills, up to
     * two close in with batons and the rest stand off on a ring. Once it is known, one
     * guard rushes (when there are two or more) and up to two hold at range and shoot;
     * the rest hold. Every guard gets his own spot round the player, spread apart, so they
     * come from different sides instead of in a line.
     */
    private void assignRoles() {
        List<Guard> hunters = new ArrayList<Guard>();
        for (Guard g : guards) {
            boolean busy = g.radioState == 2 || g.radioState == 3;
            if (g.state == GuardState.ALERT && !g.frozen && g.stun <= 0f && !busy && (g.trackTime > 0f || squadTrack > 0f)) {
                hunters.add(g);
            } else {
                g.role = ROLE_NONE;
            }
        }
        int n = hunters.size();
        if (n == 0) {
            return;
        }
        // the ring is built round where the player was last seen (the radio shares it), not where they are now
        final float cx = lastAlertX, cy = lastAlertY;
        // nearest first; a guard already on a role gets a little preference so roles do not flicker
        java.util.Collections.sort(hunters, new java.util.Comparator<Guard>() {
            public int compare(Guard a, Guard b) {
                float da = (float) Math.hypot(a.x - cx, a.y - cy) - (a.role != ROLE_NONE ? 25f : 0f);
                float db = (float) Math.hypot(b.x - cx, b.y - cy) - (b.role != ROLE_NONE ? 25f : 0f);
                return Float.compare(da, db);
            }
        });
        int meleeTokens = lethalKnown ? 0 : 2;
        int gunTokens = lethalKnown ? n : 0;                  // weapons free: everyone tries to shoot, nobody just chases
        for (int i = 0; i < n; i++) {
            Guard g = hunters.get(i);
            g.role = i < meleeTokens ? ROLE_MELEE : (i < meleeTokens + gunTokens ? ROLE_GUN : ROLE_HOLD);
        }
        // spread them round the player: anchor the nearest where he already is, then share out the circle
        float[] bearing = new float[n];
        for (int i = 0; i < n; i++) {
            bearing[i] = angleTo(cx, cy, hunters.get(i).x, hunters.get(i).y);
        }
        final float anchor = bearing[0];
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        final float[] bear = bearing;
        java.util.Arrays.sort(order, new java.util.Comparator<Integer>() {
            public int compare(Integer a, Integer b) {
                float da = (bear[a] - anchor + 720f) % 360f;
                float db = (bear[b] - anchor + 720f) % 360f;
                return Float.compare(da, db);
            }
        });
        float sep = Math.max(60f, Math.min(120f, 360f / n));
        for (int k = 0; k < n; k++) {
            Guard g = hunters.get(order[k]);
            float angle = anchor + k * sep;
            if (g.role == ROLE_HOLD) {
                angle += (time * HOLD_ORBIT_DEG_PER_SEC) * ((order[k] % 2 == 0) ? 1f : -1f);   // circling, in alternating directions
            }
            float radius = g.role == ROLE_MELEE ? MELEE_SLOT_RADIUS : (g.role == ROLE_GUN ? GUN_SLOT_RADIUS : HOLD_SLOT_RADIUS);
            float[] slot = slotOnRing(cx, cy, angle, radius, g.role == ROLE_GUN, g, hunters);
            g.slotX = slot[0];
            g.slotY = slot[1];
        }
    }

    /**
     * A point on the ring round the player. It has to be open floor; a gunner's also has to be
     * in the player's line of sight, so the angle is nudged either way until one is.
     */
    private float[] slotOnRing(float cx, float cy, float angle, float radius, boolean needsLine, Guard self, List<Guard> hunters) {
        float[] offsets = {0f, 20f, -20f, 40f, -40f, 60f, -60f, 90f, -90f, 120f, -120f};
        for (int pass = 0; pass < 2; pass++) {
            float[] found = slotPass(cx, cy, angle, radius, needsLine, self, hunters, offsets, pass == 0);
            if (found != null) {
                return found;
            }
        }
        return new float[]{cx, cy};
    }

    /**
     * One look for a slot. With keepClear set, a gunner's slot must not put another hunter between him and
     * the player, and any other slot must not lie in a gunner's line of fire; the second pass relaxes that.
     */
    private float[] slotPass(float cx, float cy, float angle, float radius, boolean needsLine, Guard self, List<Guard> hunters,
            float[] offsets, boolean keepClear) {
        for (float off : offsets) {
            double a = Math.toRadians(angle + off);
            float sx = cx + (float) Math.cos(a) * radius;
            float sy = cy + (float) Math.sin(a) * radius;
            if (sx < GUARD_RADIUS || sy < GUARD_RADIUS || sx > width - GUARD_RADIUS || sy > height - GUARD_RADIUS
                    || hitsWall(sx, sy, GUARD_RADIUS + 2f)) {
                continue;
            }
            if (needsLine) {
                boolean blocked = false;
                for (Rect w : walls) {
                    if (w.blocks(cx, cy, sx, sy)) {
                        blocked = true;
                        break;
                    }
                }
                if (blocked) {
                    continue;
                }
            }
            if (keepClear) {
                boolean inTheWay = false;
                for (Guard h : hunters) {
                    if (h == self) {
                        continue;
                    }
                    if (self.role == ROLE_GUN) {
                        inTheWay |= distToSegment(h.x, h.y, sx, sy, cx, cy) < GUARD_HIT_RADIUS + 12f;
                    } else if (h.role == ROLE_GUN) {
                        inTheWay |= distToSegment(sx, sy, h.x, h.y, cx, cy) < GUARD_HIT_RADIUS + 12f;
                    }
                }
                if (inTheWay) {
                    continue;
                }
            }
            return new float[]{sx, sy};
        }
        return null;
    }

    /** Stands at his spot, facing the player; walks there if he is not yet. */
    private void holdAt(Guard g, float dt) {
        float dx = g.slotX - g.x;
        float dy = g.slotY - g.y;
        if (dx * dx + dy * dy > 100f) {
            moveToward(g, g.slotX, g.slotY, ALERT_SPEED, CHASE_TURN_RATE, dt, false);
        } else {
            g.facing = turnToward(g.facing, angleTo(g.x, g.y, lastAlertX, lastAlertY), CHASE_TURN_RATE * dt);
        }
    }

    /** Guards do not stand on top of each other: overlapping pairs are eased apart. */
    private void separateGuards() {
        float min = GUARD_RADIUS * 2f;
        for (int i = 0; i < guards.size(); i++) {
            Guard a = guards.get(i);
            if (a.frozen || a.state == GuardState.DEAD) {
                continue;
            }
            for (int j = i + 1; j < guards.size(); j++) {
                Guard b = guards.get(j);
                if (b.frozen || b.state == GuardState.DEAD) {
                    continue;
                }
                float dx = b.x - a.x;
                float dy = b.y - a.y;
                float d2 = dx * dx + dy * dy;
                if (d2 >= min * min || d2 < 0.0001f) {
                    continue;
                }
                float d = (float) Math.sqrt(d2);
                float push = (min - d) / 2f;
                float ux = dx / d;
                float uy = dy / d;
                if (!hitsWall(a.x - ux * push, a.y - uy * push, GUARD_RADIUS)) {
                    a.x -= ux * push;
                    a.y -= uy * push;
                }
                if (!hitsWall(b.x + ux * push, b.y + uy * push, GUARD_RADIUS)) {
                    b.x += ux * push;
                    b.y += uy * push;
                }
            }
        }
    }

    // ---- the player's own melee ----
    /**
     * A blow toward (tx, ty): everyone in front of the player within reach loses a hit point.
     * It is the non-lethal option: a guard brought down to nothing is knocked out, not killed.
     * One who is hit and still standing is staggered and knows where you are. The thump is
     * heard nearby. False while carrying a body or still recovering from the last swing.
     */
    public boolean playerMelee(float tx, float ty) {
        if (crawling()) {
            return false;                                       // no room in the shaft
        }
        if (grabbed != null) {
            return grabStrike();
        }
        if (status != Status.RUNNING || carrying != null || playerMeleeCooldown > 0f) {
            return false;
        }
        float dx = tx - px;
        float dy = ty - py;
        float angle = (dx * dx + dy * dy) < 1f ? 0f : angleTo(px, py, tx, ty);
        playerMeleeCooldown = PLAYER_MELEE_COOLDOWN;
        swingTime = PLAYER_SWING_SHOW;
        swingAngle = angle;
        sound("swing", px, py);
        Ripple thump = new Ripple(px, py, MELEE_NOISE_RADIUS, 0.6f, true);
        ripples.add(thump);
        List<Guard> struck = new ArrayList<Guard>();
        for (Guard g : guards) {
            if (g.frozen || g.state == GuardState.DEAD) {
                continue;
            }
            float d = distToPlayer(g);
            if (d > PLAYER_MELEE_REACH + GUARD_RADIUS || !clearLine(g)) {
                continue;
            }
            if (Math.abs(angleDiff(angle, angleTo(px, py, g.x, g.y))) <= PLAYER_MELEE_ARC_DEG) {
                struck.add(g);
            }
        }
        // who is on his guard is settled before the blow's own noise can alert anyone
        List<Guard> guarding = new ArrayList<Guard>();
        for (Guard g : struck) {
            if (blocksBlow(g)) {
                guarding.add(g);
            }
        }
        makeNoise(px, py, MELEE_NOISE_RADIUS, false);
        boolean putDown = false;
        for (Guard g : struck) {
            if (guarding.contains(g)) {
                blockBlow(g, px, py, true);
                continue;
            }
            bloodDir = (float) Math.toRadians(angle);
            hurtGuard(g, "baton".equals(activeWeapon()) ? BATON_DAMAGE : FIST_DAMAGE);
            putDown |= g.state == GuardState.DEAD;
        }
        for (Guard g : struck) {
            witnessBlow(g);
        }
        if (!struck.isEmpty()) {
            shake = Math.max(shake, putDown ? SHAKE_MELEE_KO : SHAKE_MELEE_HIT);   // a blow that lands is felt
        }
        for (Light lamp : lights) {
            if (!lamp.on || Math.hypot(lamp.x - px, lamp.y - py) > PLAYER_MELEE_REACH + 4f
                    || Math.abs(angleDiff(angle, angleTo(px, py, lamp.x, lamp.y))) > PLAYER_MELEE_ARC_DEG) {
                continue;
            }
            boolean walled = false;
            for (Rect w : walls) {
                if (w.blocks(px, py, lamp.x, lamp.y)) {
                    walled = true;
                    break;
                }
            }
            if (!walled) {
                breakLight(lamp, true);                     // a swing at a fixture within reach
                shake = Math.max(shake, SHAKE_MELEE_HIT);
            }
        }
        return true;
    }

    /** One guard takes damage: at zero he is knocked out; otherwise he reels, and is alert to the player. */
    private void hurtGuard(Guard g, int damage) {
        hurtGuard(g, damage, false);
    }

    /** As above; friendly fire staggers a guard but does not turn him on the player, and at zero he is dead, not knocked out. */
    private void hurtGuard(Guard g, int damage, boolean friendly) {
        g.hp -= damage;
        g.bloody = true;
        if (friendly) {
            popup(g.x, g.y, g.hp <= 0 ? "Dead" : "-" + damage, POP_DAMAGE);
            shots.add(new Shot(g.x, g.y, g.x, g.y, true));
            if (!Float.isNaN(bloodDir)) {
                knock(g, bloodDir, 6f);
                hitMark(g, bloodDir + Math.PI);
            }
            spatter(g.x, g.y, 3, 10f, 4f);
            if (g.hp <= 0) {
                bloodPool(g.x, g.y, 24f);
                dropKey(g);
                g.state = GuardState.DEAD;
                g.ko = false;
                g.lethal = false;
                g.found = false;
                g.path = null;
                g.hurry = false;
                g.bodyAlarm = false;
                g.meleeTime = 0f;
                g.aimTime = 0f;
                g.burstLeft = 0;
                g.role = ROLE_NONE;
                return;
            }
            speak(g, FRIENDLY_LINES[rng.nextInt(FRIENDLY_LINES.length)]);
            g.stun = STUN_TIME;
            g.meleeTime = 0f;
            g.aimTime = 0f;
            g.burstLeft = 0;
            return;
        }
        shots.add(new Shot(g.x, g.y, g.x, g.y, true));
        spatter(g.x, g.y, 2, 8f, 3f);                           // a blow leaves only traces
        popup(g.x, g.y, g.hp <= 0 ? "KO" : "-" + damage, g.hp <= 0 ? POP_KO : POP_DAMAGE);
        g.attackedByPlayer = ATTACK_MEMORY;
        impactFx((px + g.x) / 2f, (py + g.y) / 2f, Math.atan2(g.y - py, g.x - px), 0);
        if (g.hp <= 0) {
            bloodPool(g.x, g.y, 12f);
            dropKey(g);
            g.state = GuardState.DEAD;
            g.ko = true;
            g.lethal = false;
            g.koTimer = KO_WAKE_TIME;
            hints.event("ko", g.x, g.y, this);
            g.found = false;
            g.path = null;
            g.hurry = false;
            g.bodyAlarm = false;
            g.meleeTime = 0f;
            g.aimTime = 0f;
            g.role = ROLE_NONE;
            barks.add("(you) He is out cold.");
            return;
        }
        double away = Math.atan2(g.y - py, g.x - px);
        knock(g, away, HIT_PUSH);
        hitMark(g, away + Math.PI);
        g.stun = Math.max(STUN_TIME, g.brokenTime);                // a broken guard stays open while his guard is down
        g.meleeTime = 0f;
        g.aimTime = 0f;
        g.burstLeft = 0;
        if (g.state != GuardState.ALERT) {
            g.targetX = px;
            g.targetY = py;
            becomeAlert(g);
        }
    }

    private void killGuard(Guard g) {
        g.bloody = true;
        popup(g.x, g.y, "Dead", POP_DAMAGE);
        hints.event("kill", g.x, g.y, this);
        bloodPool(g.x, g.y, 24f);
        dropKey(g);
        g.state = GuardState.DEAD;
        g.lethal = true;
        g.ko = false;
        g.path = null;
        g.hurry = false;
        g.bodyAlarm = false;
        kills++;
    }

    /** Guards who can see the victim saw the killing: they know, go to the body, and one of them calls it in. */
    private void witnessKill(Guard victim) {
        boolean first = true;
        for (Guard g : guards) {
            if (g == victim || g.frozen || g.state == GuardState.DEAD || g.state == GuardState.ALERT) {
                continue;
            }
            if (seesPoint(g, victim.x, victim.y) || sees(g)) {
                g.knowsLethal = true;
                g.witness = true;
                if (first) {
                    first = false;
                    discoverBody(g, victim);
                } else {
                    becomeSuspicious(g, victim.x, victim.y, false);
                    g.hurry = false;
                    g.bodyAlarm = true;
                    g.noticeLeft = Math.min(g.noticeLeft, 0.2f);
                }
            }
        }
    }

    // ---- hit feedback ----
    /** Pushes a guard `dist` units along `angle` (radians), easing out over a fifth of a second or so. */
    private void knock(Guard g, double angle, float dist) {
        g.kbVx += (float) Math.cos(angle) * dist * KNOCK_RATE;
        g.kbVy += (float) Math.sin(angle) * dist * KNOCK_RATE;
    }

    private void popup(float x, float y, String text, int kind) {
        float dx = (popupCount++ % 3 - 1) * 9f;
        popups.add(new Popup(x, y, text, kind, dx));
        while (popups.size() > 24) {
            popups.remove(0);
        }
    }

    private void dust(float x, float y, double angle, int n, double spread, float speed, float size, float alpha) {
        for (int i = 0; i < n; i++) {
            Puff p = new Puff();
            double a = angle + (fxRng.nextDouble() - 0.5) * spread;
            float v = speed * (0.5f + fxRng.nextFloat() * 0.7f);
            p.x = x;
            p.y = y;
            p.vx = (float) Math.cos(a) * v;
            p.vy = (float) Math.sin(a) * v;
            p.r0 = size * 0.5f;
            p.r1 = size * (1f + fxRng.nextFloat() * 0.6f);
            p.life = 0.4f + fxRng.nextFloat() * 0.2f;
            p.alpha = alpha;
            puffs.add(p);
        }
        while (puffs.size() > 90) {
            puffs.remove(0);
        }
    }

    private void spark(float x, float y, double angle, float len, float life, boolean block) {
        Spark k = new Spark();
        k.x = x;
        k.y = y;
        k.angle = (float) angle;
        k.len = len;
        k.life = life;
        k.block = block;
        sparks.add(k);
        while (sparks.size() > 40) {
            sparks.remove(0);
        }
    }

    /**
     * What a blow throws up at the point it lands (`dir` is the way the blow went, radians): a hit kicks a fan of dust along it, a turned
     * blow throws yellow sparks back, a suit's hit also throws a ring.
     */
    /** A blow near the player: the picture stops for a beat and the view is thrown along it. Not again until a moment after. */
    private void feel(float x, float y, double dir, float stop, float power, float shakeAmt) {
        if (Math.hypot(x - px, y - py) > FEEL_RANGE || (hitStopCool > 0f && stop <= hitStop)) {
            return;
        }
        hitStop = Math.max(hitStop, stop);
        hitStopCool = 0.22f;
        kickX = (float) Math.cos(dir) * power;
        kickY = (float) Math.sin(dir) * power;
        shake = Math.max(shake, shakeAmt);
    }

    private void impactFx(float x, float y, double dir, int kind) {
        sound(kind == 1 ? "block" : (kind == 2 ? "suit" : "hit"), x, y);
        if (kind == 1) {
            feel(x, y, dir, HIT_STOP_BLOCK, 0.4f, 0.2f);
        } else if (kind == 2) {
            feel(x, y, dir, HIT_STOP_SUIT, 1f, 0.45f);
        } else {
            feel(x, y, dir, HIT_STOP_HIT, 0.8f, 0.3f);
        }
        if (kind == 1) {
            for (int i = 0; i < 6; i++) {
                spark(x, y, dir + Math.PI + (fxRng.nextDouble() - 0.5) * 2.2, 3f + fxRng.nextFloat() * 4f, 0.16f, true);
            }
            return;
        }
        if (kind == 2) {
            for (int i = 0; i < 10; i++) {
                dust(x, y, i / 10.0 * Math.PI * 2.0, 1, 0.2, 70f, 5f, 0.5f);
            }
            dust(x, y, dir, 6, 0.9, 120f, 5.5f, 0.5f);
            return;
        }
        dust(x, y, dir, 6, 1.1, 85f, 4.2f, 0.5f);
    }

    private void knock(Ally a, double angle, float dist) {
        a.kbVx += (float) Math.cos(angle) * dist * KNOCK_RATE;
        a.kbVy += (float) Math.sin(angle) * dist * KNOCK_RATE;
    }

    private void hitMark(Ally a, double fromAngle) {
        a.hitTime = HIT_FLASH;
        a.hitAngle = (float) fromAngle;
    }

    /** The flash of a hit on the side the blow came from (`fromAngle`, radians). */
    private void hitMark(Guard g, double fromAngle) {
        g.hitTime = HIT_FLASH;
        g.hitAngle = (float) fromAngle;
    }

    private void updateKnock(float dt) {
        float decay = (float) Math.exp(-KNOCK_RATE * dt);
        for (int i = popups.size() - 1; i >= 0; i--) {
            popups.get(i).age += dt;
            if (popups.get(i).age > POP_LIFE) {
                popups.remove(i);
            }
        }
        for (int i = puffs.size() - 1; i >= 0; i--) {
            Puff p = puffs.get(i);
            p.age += dt;
            float slow = (float) Math.exp(-4f * dt);
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.vx *= slow;
            p.vy *= slow;
            if (p.age > p.life) {
                puffs.remove(i);
            }
        }
        for (int i = sparks.size() - 1; i >= 0; i--) {
            sparks.get(i).age += dt;
            if (sparks.get(i).age > sparks.get(i).life) {
                sparks.remove(i);
            }
        }
        for (Ally a : allies) {
            a.hitTime = Math.max(0f, a.hitTime - dt);
            a.brokenTime = Math.max(0f, a.brokenTime - dt);
            a.blockTime = Math.max(0f, a.blockTime - dt);
            a.blockDecay = Math.max(0f, a.blockDecay - dt);
            if (a.blockDecay <= 0f) {
                a.blockHits = 0;
            }
            if (a.kbVx == 0f && a.kbVy == 0f) {
                continue;
            }
            float rad = allyRadius(a);
            a.skidT -= dt;
            if (a.skidT <= 0f && Math.hypot(a.kbVx, a.kbVy) > 40f) {
                a.skidT = 0.045f;
                dust(a.x, a.y, Math.atan2(-a.kbVy, -a.kbVx), 1, 0.6, 26f, 2.6f, 0.28f);
            }
            float nx = a.x + a.kbVx * dt;
            float ny = a.y + a.kbVy * dt;
            if (hitsWall(nx, a.y, rad)) {
                a.kbVx = 0f;
            } else {
                a.x = nx;
            }
            if (hitsWall(a.x, ny, rad)) {
                a.kbVy = 0f;
            } else {
                a.y = ny;
            }
            a.kbVx *= decay;
            a.kbVy *= decay;
            if (Math.abs(a.kbVx) < 1f && Math.abs(a.kbVy) < 1f) {
                a.kbVx = 0f;
                a.kbVy = 0f;
            }
        }
        for (Guard g : guards) {
            g.hitTime = Math.max(0f, g.hitTime - dt);
            g.brokenTime = Math.max(0f, g.brokenTime - dt);
            g.blockDecay = Math.max(0f, g.blockDecay - dt);
            if (g.blockDecay <= 0f) {
                g.blockHits = 0;
            }
            if (g.kbVx == 0f && g.kbVy == 0f) {
                continue;
            }
            g.skidT -= dt;
            if (g.skidT <= 0f && Math.hypot(g.kbVx, g.kbVy) > 40f) {
                g.skidT = 0.045f;
                dust(g.x, g.y, Math.atan2(-g.kbVy, -g.kbVx), 1, 0.6, 26f, 2.6f, 0.28f);
            }
            float nx = g.x + g.kbVx * dt;
            float ny = g.y + g.kbVy * dt;
            if (hitsWall(nx, g.y, GUARD_RADIUS)) {
                g.kbVx = 0f;
            } else {
                g.x = nx;
            }
            if (hitsWall(g.x, ny, GUARD_RADIUS)) {
                g.kbVy = 0f;
            } else {
                g.y = ny;
            }
            g.kbVx *= decay;
            g.kbVy *= decay;
            if (Math.abs(g.kbVx) < 1f && Math.abs(g.kbVy) < 1f) {
                g.kbVx = 0f;
                g.kbVy = 0f;
            }
        }
    }

    // ---- violence escalates ----
    /** A guard goes into the modified alert over something done to one of his own: as on finding a body, he calls it in. */
    private void raiseViolenceAlarm(Guard g, float x, float y, String line) {
        speak(g, line);
        becomeSuspicious(g, x, y, false);
        g.hurry = false;
        g.bodyAlarm = true;
        g.witness = true;
        g.noticeLeft = Math.min(g.noticeLeft, 0.3f);
        g.lookLeft = 2.5f;
        g.lookX = x;
        g.lookY = y;
        notice(g, false);
        beginRadio(g, x, y);
    }

    /** Guards who can see a man struck or put down (even quietly) go into the modified alert. */
    private void witnessBlow(Guard victim) {
        if (!escalates) {
            return;
        }
        for (Guard g : guards) {
            if (g == victim || g.frozen || g.state == GuardState.DEAD || g == grabbed
                    || (g.state != GuardState.PATROL && g.state != GuardState.SUSPICIOUS) || g.chat || g.chastise != null) {
                continue;
            }
            if (Math.hypot(g.x - victim.x, g.y - victim.y) > BLOOD_NOTICE_RANGE * 1.6f || !seesPoint(g, victim.x, victim.y)) {
                continue;
            }
            raiseViolenceAlarm(g, victim.x, victim.y, "He is attacking us!");
        }
    }

    /** A guard on his guard, facing the player, turns a blow from the front. */
    private boolean blocksBlow(Guard g) {
        return blocksBlowFrom(g, px, py);
    }

    private boolean blocksBlowFrom(Guard g, float fx, float fy) {
        return escalates && (g.state == GuardState.ALERT || g.state == GuardState.SUSPICIOUS) && g.stun <= 0f && g.brokenTime <= 0f
                && Math.abs(angleDiff(g.facing, angleTo(g.x, g.y, fx, fy))) <= BLOCK_ARC_DEG;
    }

    /**
     * A blow from (fx, fy) that a guard turns: he staggers and is pushed a little, and the second in a row breaks his guard (stunned and
     * thrown back, open to the next blows). `felt` is the player's own blow, which shakes the view.
     */
    private void blockBlow(Guard g, float fx, float fy, boolean felt) {
        if (felt) {
            g.attackedByPlayer = ATTACK_MEMORY;
            hints.event("blocked", g.x, g.y, this);
        }
        g.blockTime = 0.25f;                                    // he turns it with his baton
        g.stun = Math.max(g.stun, BLOCK_STAGGER);
        g.meleeTime = 0f;
        double away = Math.toRadians(angleTo(fx, fy, g.x, g.y));
        g.blockHits++;
        g.blockDecay = BLOCK_MEMORY;
        if (g.blockHits >= BLOCK_BREAK_HITS) {
            // the second blocked blow in a row breaks his guard: he is open and reeling, and blows land as normal
            g.blockHits = 0;
            g.stun = BREAK_STUN;
            g.brokenTime = BREAK_STUN;
            sound("break", g.x, g.y);
            feel(g.x, g.y, Math.atan2(g.y - py, g.x - px), HIT_STOP_BREAK, 1f, 0.7f);
            g.aimTime = 0f;
            g.burstLeft = 0;
            knock(g, away, 24f);
            popup(g.x, g.y, "Broken!", POP_BREAK);
            if (felt) {
                shake = Math.max(shake, SHAKE_MELEE_KO);
            }
        } else {
            knock(g, away, 7f);
            popup(g.x, g.y, "Blocked", POP_BLOCK);
        }
        impactFx((fx + g.x) / 2f, (fy + g.y) / 2f, away, 1);
        hitMark(g, away + Math.PI);
    }

    /**
     * An alert guard on his own, in the clear and out of reach, with no other hunter near and no gun, does not charge: he keeps his
     * distance, watches, and waits for company (or gives up waiting). False when he should hunt as normal.
     */
    private boolean loneWait(Guard g, float dt) {
        if (!escalates || g.flood || fightStarted || g.foeAlly != null || (lethalKnown && g.role == ROLE_GUN)) {
            g.waitTime = 0f;
            return false;
        }
        for (Guard o : guards) {
            if (o != g && !o.frozen && o.state == GuardState.ALERT && Math.hypot(o.x - g.x, o.y - g.y) <= LONE_MATE_RANGE) {
                g.waitTime = 0f;
                return false;                                   // company: they hunt together
            }
        }
        boolean backup = false;
        for (Guard o : guards) {
            if (o != g && !o.frozen && !o.flood && o.state != GuardState.DEAD && Math.hypot(o.x - g.x, o.y - g.y) <= LONE_BACKUP_RANGE) {
                backup = true;
                break;
            }
        }
        if (!backup) {
            g.waitTime = 0f;
            return false;                                       // nobody is coming: there is nothing to wait for
        }
        g.waitTime += dt;
        float d = distToPlayer(g);
        if (g.waitTime > LONE_WAIT_MAX || !clearLine(g) || d > LONE_COME_ON) {
            return false;
        }
        g.facing = turnToward(g.facing, angleTo(g.x, g.y, px, py), CHASE_TURN_RATE * dt);
        if (d < LONE_BACK_OFF) {
            float bx = g.x + (g.x - px) / Math.max(1f, d) * 40f;
            float by = g.y + (g.y - py) / Math.max(1f, d) * 40f;
            float moved = moveToward(g, bx, by, ALERT_SPEED * 0.9f, CHASE_TURN_RATE, dt, false);
            if (moved < 0.3f * ALERT_SPEED * 0.9f * dt) {
                g.waitTime = LONE_WAIT_MAX + 1f;                // backed into a corner: he fights, and does not go back to waiting
                return false;
            }
            g.facing = turnToward(g.facing, angleTo(g.x, g.y, px, py), CHASE_TURN_RATE * dt);
        }
        return true;
    }

    // ---- silent takedown ----
    /**
     * Knocks out the guard right behind the player, quietly. He has to be close,
     * facing away, not already hunting, and in the clear. He drops as a body that
     * can be found, but it is not a killing, and he wakes up after a while.
     */
    public boolean takedown() {
        if (status != Status.RUNNING || takedownCooldown > 0f || carrying != null) {
            return false;
        }
        Guard best = takedownTarget();
        takedownCooldown = TAKEDOWN_COOLDOWN;
        if (best == null) {
            return false;
        }
        // lunge: step in behind him
        float behind = (float) Math.toRadians(best.facing + 180f);
        float lx = best.x + (float) Math.cos(behind) * 14f;
        float ly = best.y + (float) Math.sin(behind) * 14f;
        if (!hitsWall(lx, ly, PLAYER_RADIUS)) {
            px = lx;
            py = ly;
            vx = 0f;
            vy = 0f;
        }
        dropKey(best);
        best.state = GuardState.DEAD;
        best.ko = true;
        best.lethal = false;
        best.koTimer = KO_WAKE_TIME;
        hints.event("ko", best.x, best.y, this);
        best.found = false;
        best.path = null;
        best.hurry = false;
        best.bodyAlarm = false;
        barks.add("(you) He goes down without a sound.");
        witnessBlow(best);
        return true;
    }

    /** The guard a takedown would hit right now, or null. The views ring him so the player can tell. */
    public Guard takedownTarget() {
        if (status != Status.RUNNING || carrying != null) {
            return null;
        }
        Guard best = null;
        float bestDist = TAKEDOWN_RANGE;
        for (Guard g : guards) {
            if (g.frozen || g.state == GuardState.DEAD || g.state == GuardState.ALERT) {
                continue;
            }
            float d = distToPlayer(g);
            if (d > bestDist) {
                continue;
            }
            float toPlayer = angleTo(g.x, g.y, px, py);
            if (Math.abs(angleDiff(g.facing, toPlayer)) < TAKEDOWN_BEHIND_DEG || !clearLine(g)) {
                continue;
            }
            best = g;
            bestDist = d;
        }
        return best;
    }

    // ---- grab and question ----
    /** The guard a grab would take right now, or null. Anyone close and in the clear who is not already hunting. The views ring him. */
    public Guard grabTarget() {
        if (status != Status.RUNNING || carrying != null || grabbed != null) {
            return null;
        }
        Guard best = null;
        float bestDist = GRAB_RANGE;
        for (Guard g : guards) {
            if (g.frozen || g.state == GuardState.DEAD || g.state == GuardState.ALERT) {
                continue;
            }
            float d = distToPlayer(g);
            if (d > bestDist || !clearLine(g)) {
                continue;
            }
            best = g;
            bestDist = d;
        }
        return best;
    }

    public boolean grab(Guard g) {
        float toPlayer = angleTo(g.x, g.y, px, py);
        grabSilent = Math.abs(angleDiff(g.facing, toPlayer)) >= TAKEDOWN_BEHIND_DEG;
        grabbed = g;
        hints.event("grabbed", g.x, g.y, this);
        grabTime = 0f;
        grabHits = 0;
        grabShouted = false;
        grabQuestioned = false;
        questionProgress = 0f;
        g.chat = false;
        g.huddle = false;
        g.chastise = null;
        g.path = null;
        // he is held just ahead of the player, on the side he was already on
        float dx = g.x - px;
        float dy = g.y - py;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) {
            dx = 1f;
            dy = 0f;
            len = 1f;
        }
        grabOffX = dx / len * 15f;
        grabOffY = dy / len * 15f;
        barks.add(grabSilent ? "(you) You clamp a hand over his mouth." : "(you) He sees you. You grab him anyway.");
        return true;
    }

    /** Let go. A guard let go of face to face, or who broke free, is alert; one taken from behind only knows something is wrong. */
    public boolean releaseGrab() {
        Guard g = grabbed;
        if (g == null) {
            return false;
        }
        grabbed = null;
        questioning = false;
        if (g.state == GuardState.DEAD) {
            return true;
        }
        if (grabSilent && !grabShouted) {
            becomeSuspicious(g, px, py, false);
        } else {
            becomeAlert(g);
        }
        g.facing = angleTo(g.x, g.y, px, py);
        return true;
    }

    /** One blow on the guard in the grip. Enough of them, from behind or not, finish him for good. */
    private boolean grabStrike() {
        if (grabbed == null || status != Status.RUNNING || playerMeleeCooldown > 0f) {
            return false;
        }
        playerMeleeCooldown = 0.12f;
        shake = Math.max(shake, SHAKE_MELEE_HIT);
        grabHits++;
        witnessBlow(grabbed);
        ripples.add(new Ripple(px, py, 70f, 0.4f, true));
        makeNoise(px, py, 70f, false);
        if (grabHits >= GRAB_KILL_PRESSES) {
            Guard g = grabbed;
            grabbed = null;
            questioning = false;
            killGuard(g);
            shake = Math.max(shake, SHAKE_MELEE_KO);
            barks.add("(you) He stops struggling.");
            witnessKill(g);
        }
        return true;
    }

    /** E while holding him: put him to sleep without killing him. */
    private boolean knockOutHeld() {
        Guard g = grabbed;
        if (g == null || status != Status.RUNNING) {
            return false;
        }
        grabbed = null;
        questioning = false;
        dropKey(g);
        g.state = GuardState.DEAD;
        g.ko = true;
        g.lethal = false;
        g.koTimer = KO_WAKE_TIME;
        hints.event("ko", g.x, g.y, this);
        g.found = false;
        g.path = null;
        g.hurry = false;
        g.bodyAlarm = false;
        barks.add("(you) He goes limp, breathing.");
        witnessBlow(g);
        return true;
    }

    private void updateGrab(float dt) {
        Guard g = grabbed;
        if (g == null) {
            questionProgress = 0f;
            return;
        }
        if (g.state == GuardState.DEAD) {
            grabbed = null;
            return;
        }
        float nx = px + grabOffX;
        float ny = py + grabOffY;
        if (!hitsWall(nx, ny, GUARD_RADIUS)) {
            g.x = nx;
            g.y = ny;
        }
        grabTime += dt;
        if (!grabSilent && !grabShouted && grabTime >= GRAB_SHOUT) {
            grabShouted = true;
            speak(g, ALERT_LINES[rng.nextInt(ALERT_LINES.length)]);
            makeNoise(g.x, g.y, GRAB_SHOUT_RADIUS, false);
            becomeAlert(g);
        }
        if (questioning && !grabQuestioned) {
            questionProgress += dt;
            if (questionProgress >= QUESTION_TIME) {
                grabQuestioned = true;
                hints.event("questioned", g.x, g.y, this);
                revealLeft = REVEAL_TIME;
                boolean card = false;
                boolean officer = false;
                for (Guard o : guards) {
                    card |= o.hasKey && o.state != GuardState.DEAD;
                    officer |= o.hasKey2 && o.state != GuardState.DEAD;
                }
                speak(g, officer ? "(guard) Alright! The sergeant has the officer's card, he does not leave the east door. The rest of us are on patrol, I swear."
                        : card ? "(guard) Alright! The corridor... the man at the far end has the card. They are all on patrol, I swear."
                        : "(guard) Alright! They are on patrol, all of them. That is all I know.");
            }
        } else if (!questioning && !grabQuestioned) {
            questionProgress = Math.max(0f, questionProgress - dt * 2f);
        }
        if (grabTime >= (grabSilent ? GRAB_HOLD_BEHIND : GRAB_HOLD_FRONT)) {
            barks.add("(you) He wrenches free.");
            grabShouted = true;                             // a guard who broke free knows
            releaseGrab();
        }
    }

    private void updateKnockedOut(float dt) {
        for (Guard g : guards) {
            if (g.state != GuardState.DEAD || !g.ko) {
                continue;
            }
            g.koTimer -= dt;
            if (g.koTimer > 0f) {
                continue;
            }
            if (carrying == g) {
                carrying = null;
            }
            g.ko = false;
            g.hp = MAX_HP;
            g.hidden = false;
            g.found = false;
            g.state = GuardState.SEARCH;
            g.searchLeft = SEARCH_TIME;
            g.look = null;
            g.sweepBase = g.facing;
            g.radioState = 0;
            speak(g, "(guard) Ugh... what happened?");
            if (escalates) {
                raiseViolenceAlarm(g, g.x, g.y, "I was jumped! Someone is here!");       // first, so his call can go out
            }
            raiseCaution(g.x, g.y);
        }
    }

    /** True if a guard could walk from one point to another (straight, or round the walls). Used to check levels. */
    public boolean reachable(float sx, float sy, float tx, float ty) {
        return lineFree(sx, sy, tx, ty) || findPath(sx, sy, tx, ty) != null;
    }

    // ---- thrown distractions ----
    /**
     * Where a distraction thrown at (tx, ty) would land: the target if it is in
     * range and the way is clear, otherwise as far along the line as it can get.
     */
    public float[] landingFor(float tx, float ty) {
        float dx = tx - px;
        float dy = ty - py;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist < 1f) {
            return new float[]{px, py};
        }
        float angle = angleTo(px, py, tx, ty);
        float reach = Math.min(dist, THROW_RANGE);
        float free = rayDistance(px, py, angle, reach);
        float d = free < reach ? Math.max(0f, free - 4f) : reach;
        double rad = Math.toRadians(angle);
        return new float[]{px + (float) Math.cos(rad) * d, py + (float) Math.sin(rad) * d};
    }

    /** Throws a distraction toward (tx, ty). False if there are none left or the run is over. */
    public boolean throwDistraction(float tx, float ty) {
        if (crawling()) {
            return false;                                       // no room in the shaft
        }
        if (grabbed != null) {
            return knockOutHeld();
        }
        if (distractionsLeft <= 0 || status != Status.RUNNING || carrying != null) {
            return false;
        }
        float[] land = landingFor(tx, ty);
        float dx = land[0] - px;
        float dy = land[1] - py;
        float flight = Math.max(0.15f, (float) Math.sqrt(dx * dx + dy * dy) / THROW_SPEED);
        thrownItems.add(new Thrown(px, py, land[0], land[1], flight));
        distractionsLeft--;
        hints.event("thrown", land[0], land[1], this);
        return true;
    }

    private void updateThrown(float dt) {
        for (int i = thrownItems.size() - 1; i >= 0; i--) {
            Thrown th = thrownItems.get(i);
            th.t += dt;
            if (th.t < th.flight) {
                continue;
            }
            thrownItems.remove(i);
            Ripple rp = new Ripple(th.x1, th.y1, DISTRACTION_RADIUS, 1f, false);
            rp.thrown = true;
            ripples.add(rp);
            sound("land", th.x1, th.y1);
            for (Light lamp : lights) {
                if (lamp.on && Math.hypot(lamp.x - th.x1, lamp.y - th.y1) <= LIGHT_HIT_RADIUS + 8f) {
                    breakLight(lamp, true);                 // it lands on the fixture
                }
            }
            for (Guard g : guards) {
                if (g.frozen || (g.state != GuardState.PATROL && g.state != GuardState.SEARCH)) {
                    continue;
                }
                float gx = g.x - th.x1;
                float gy = g.y - th.y1;
                if (gx * gx + gy * gy > DISTRACTION_RADIUS * DISTRACTION_RADIUS) {
                    continue;
                }
                boolean blocked = false;
                for (Rect w : walls) {
                    if (w.blocks(th.x1, th.y1, g.x, g.y)) {
                        blocked = true;
                        break;
                    }
                }
                if (!blocked) {
                    becomeSuspicious(g, th.x1, th.y1, true);
                    hints.event("lured", g.x, g.y, this);
                }
            }
        }
    }

    // ---- sound rings ----
    private void emitPlayerRipples(float dt, boolean moved, boolean sneak) {
        if (!moved) {
            playerStepClock = 0f;                 // the first step after standing still rings at once
            return;
        }
        playerStepClock -= dt;
        if (playerStepClock > 0f) {
            return;
        }
        if (sneak) {
            ripples.add(new Ripple(px, py, SNEAK_RIPPLE_RADIUS, 0.35f, true));
            playerStepClock = STEP_INTERVAL_SNEAK;
        } else {
            ripples.add(new Ripple(px, py, NOISE_RANGE, 1f, true));
            playerStepClock = STEP_INTERVAL_WALK;
        }
    }

    private void emitGuardRipples(Guard g, float dt, float x0, float y0) {
        float dx = g.x - x0;
        float dy = g.y - y0;
        if (dx * dx + dy * dy < 1e-6f) {
            g.stepClock = 0f;
            return;
        }
        g.stepClock -= dt;
        if (g.stepClock > 0f) {
            return;
        }
        boolean hurrying = g.state == GuardState.ALERT || g.state == GuardState.SUSPICIOUS;
        ripples.add(new Ripple(g.x, g.y, hurrying ? 46f : 28f, hurrying ? 0.55f : 0.35f, false));
        g.stepClock = hurrying ? 0.3f : 0.5f;
    }

    private void trackMark(Guard g, float dt) {
        if (g.state != g.markState) {
            g.markState = g.state;
            g.markAge = 0f;
        } else {
            g.markAge += dt;
        }
    }

    // ---- what guards say ----
    private static final String[] SIGHT_LINES = {"Hey - who's there?", "Did something move?", "Is someone there?"};
    private static final String[] NOISE_LINES = {"What was that noise?", "Did you hear something?", "Huh? Who goes there?"};
    private static final String[] ALERT_LINES = {"Intruder!", "There! Stop right there!", "I've got you!"};
    private static final String[] BODY_LINES = {"Man down! Over here!", "There is a body!", "Someone killed him!"};
    private static final String[] FRIENDLY_LINES = {"Watch your fire!", "Hey! Friendly!", "I am on your side!"};
    private static final String[] GIVE_UP_LINES = {"Must have been nothing.", "Probably just a rat.", "Nothing here."};

    private void say(Guard g, String[] lines) {
        if (lines == GIVE_UP_LINES) {
            sound("lost", g.x, g.y);
        }
        speak(g, lines[rng.nextInt(lines.length)]);
    }

    /**
     * A character says something: it goes to the dialog log as given, and shows over the speaker's head
     * (without any "(guard)" tag). A speaker has one thing on show at a time. A null speaker is Roland.
     */
    private void speak(Guard who, String line) {
        String prefix = "";
        String text = line;
        int close = line.indexOf(')');
        if (line.startsWith("(") && close > 0 && line.substring(1, close).matches("[a-z ]+")) {
            prefix = line.substring(0, close + 1) + " ";
            text = line.substring(close + 1).trim();
        }
        int q1 = text.indexOf('"');
        int q2 = text.lastIndexOf('"');
        if (q1 >= 0 && q2 > q1) {
            text = text.substring(q1 + 1, q2);                  // only what was said, not the narration round it
        } else if (who == null) {
            barks.add(line);                                    // Roland doing something, not saying it: log only
            return;
        }
        for (int i = speeches.size() - 1; i >= 0; i--) {
            if (speeches.get(i).who == who && speeches.get(i).ally == null) {
                speeches.remove(i);
            }
        }
        Speech sp = new Speech(who, text, rng.nextInt());
        speeches.add(sp);
        // the log gets what the player could make out at this distance
        String heard = heardText(sp);
        if (heard != null) {
            if (who == null) {
                sound("talk", rolandX, rolandY);                // the guards' chatter was too much: only Roland and the prisoners get it
            }
            String out = who == null ? line : prefix + heard;
            barks.add(out);
            spoken.add(out);                                    // said, not narrated: the bubble is enough
        }
    }

    /** A prisoner says something: a bubble over them (always clear, like Roland's) and a line in the log. */
    /** Lines that are spoken (shown over heads) rather than narrated: kept in barks for the tests, left out of the dialog log. */
    private final java.util.Set<String> spoken = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<String, Boolean>());

    /** What goes in the dialog panel: everything but what characters say. */
    public List<String> drainDialog() {
        List<String> out = new ArrayList<String>();
        for (String b : barks) {
            if (!spoken.contains(b)) {
                out.add(b);
            }
        }
        barks.clear();
        spoken.clear();
        return out;
    }

    private void speakAlly(Ally a, String text) {
        for (int i = speeches.size() - 1; i >= 0; i--) {
            if (speeches.get(i).ally == a) {
                speeches.remove(i);
            }
        }
        speeches.add(new Speech(null, a, text, rng.nextInt()));
        sound("talk", a.x, a.y);
        String line = "(" + a.name + ") " + text;
        barks.add(line);
        spoken.add(line);
    }

    // ---- hearing what is said ----
    /** Words are clear this close (counting each wall as extra distance), and fade out to nothing at SPEECH_HEAR_RANGE. */
    public static final float SPEECH_CLEAR_RANGE = 200f;
    public static final float SPEECH_HEAR_RANGE = 300f;
    public static final String INAUDIBLE = "*inaudible*";

    /** 1 when a speaker can be understood perfectly, 0 when not at all. Roland, who is with you, is always clear. */
    public float speechClarity(Guard who) {
        if (who == null) {
            return 1f;
        }
        return clarityAt(soundDistance(who.x, who.y, px, py));
    }

    private static float clarityAt(float d) {
        return Math.max(0f, Math.min(1f, 1f - (d - SPEECH_CLEAR_RANGE) / (SPEECH_HEAR_RANGE - SPEECH_CLEAR_RANGE)));
    }

    /** How far a sound effectively travels between two points: see soundDistance. */
    public static final float SOUND_CORNER_PENALTY = 15f;

    /**
     * How far sound effectively has to travel from one point to another. In a clear line, just the distance. Behind a wall,
     * the better of two ways: through it (the distance plus SHOT_WALL_MUFFLE for each wall) or round it (the length of the
     * way a guard would walk, plus a little for the corner). So standing just round the edge of a wall hears nearly as well
     * as in the open, and a solid wall with no way round still muffles.
     */
    public float soundDistance(float ax, float ay, float bx, float by) {
        float dx = bx - ax;
        float dy = by - ay;
        float straight = (float) Math.sqrt(dx * dx + dy * dy);
        int walls = wallsBetween(ax, ay, bx, by);
        if (walls == 0) {
            return straight;
        }
        float through = straight + SHOT_WALL_MUFFLE * walls;
        List<float[]> way = findPath(ax, ay, bx, by);
        if (way == null) {
            return through;
        }
        float length = 0f;
        float lx = ax, ly = ay;
        for (float[] p : way) {
            length += (float) Math.hypot(p[0] - lx, p[1] - ly);
            lx = p[0];
            ly = p[1];
        }
        return Math.min(through, length + SOUND_CORNER_PENALTY);
    }

    /** The sound distance to a line's speaker, worked out at most every 0.15 s. */
    private float clarityDistance(Speech sp) {
        if (sp.distCache < 0f || time - sp.distCacheTime > 0.15f) {
            sp.distCache = soundDistance(sp.who.x, sp.who.y, px, py);
            sp.distCacheTime = time;
        }
        return sp.distCache;
    }

    /**
     * What the player makes of a line right now: the whole thing up close; further off some letters drop out (vowels first),
     * and at the edge of hearing hardly any are left. Too far to hear anything: "*inaudible*" if the speaker is in sight,
     * otherwise null (nothing at all).
     */
    public String heardText(Speech sp) {
        if (sp.who == null || canSee(sp.who.x, sp.who.y)) {
            return sp.text;                                     // someone you can see is always understood: the rooms are small
        }
        return heardTextAt(sp, sp.who == null ? 0f : clarityDistance(sp), false);
    }

    /**
     * What is made out of a line from a speaker at the given sound distance. A speaker in sight is always clear; one out of sight
     * (behind a wall, in the dark) is clear up close, garbled further off, and nothing at all at the edge of earshot.
     */
    public String heardTextAt(Speech sp, float soundDistance, boolean inSight) {
        if (inSight) {
            return sp.text;
        }
        float c = clarityAt(soundDistance);
        if (c >= 0.999f) {
            return sp.text;
        }
        if (c <= 0.08f) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sp.text.length(); i++) {
            char ch = sp.text.charAt(i);
            if (!Character.isLetterOrDigit(ch)) {
                sb.append(ch);
                continue;
            }
            int h = sp.seed * 31 + i * 7919;
            h ^= h >>> 15;
            h *= 0x2c1b3c6d;
            h ^= h >>> 12;
            float r = (h & 0xffff) / 65536f;
            boolean vowel = "aeiouAEIOU".indexOf(ch) >= 0;
            sb.append(r < (vowel ? c - 0.2f : c) ? ch : '.');
        }
        return sb.toString();
    }

    private void updateSpeeches(float dt) {
        for (int i = speeches.size() - 1; i >= 0; i--) {
            Speech sp = speeches.get(i);
            sp.age += dt;
            if (sp.age >= sp.life || (sp.who != null && sp.who.state == GuardState.DEAD)) {
                speeches.remove(i);
            }
        }
    }

    /** What guards have said since the last call, oldest first. The panel prints these into the dialog. */
    public List<String> drainBarks() {
        List<String> out = new ArrayList<String>(barks);
        barks.clear();
        return out;
    }

    // ---- geometry ----
    private float distToPlayer(Guard g) {
        float dx = px - g.x;
        float dy = py - g.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /** No wall between the guard and the player. */
    /** Whether a guard's cone covers a point: in range, in the cone and with nothing in the way. */
    private boolean seesPoint(Guard g, float x, float y) {
        float fr = coneRange * (1f - (1f - FOCUS_RANGE) * g.focus);
        float fh = coneHalf * (1f - (1f - FOCUS_ANGLE) * g.focus);
        float dx = x - g.x;
        float dy = y - g.y;
        if (dx * dx + dy * dy > fr * fr) {
            return false;
        }
        if (Math.abs(angleDiff(g.facing, angleTo(g.x, g.y, x, y))) > fh) {
            return false;
        }
        if (!noDarkness() && sightCost(g.x, g.y, x, y) > fr) {
            return false;
        }
        for (Rect w : walls) {
            if (w.blocks(g.x, g.y, x, y)) {
                return false;
            }
        }
        return true;
    }

    /** How many walls lie between two points. */
    public int wallsBetween(float x1, float y1, float x2, float y2) {
        int n = 0;
        for (Rect w : walls) {
            if (w.blocks(x1, y1, x2, y2)) {
                n++;
            }
        }
        return n;
    }

    /**
     * How far a shot's sound front reaches along a bearing, for drawing: it
     * stops briefly at the first wall, then carries on weaker on the far side.
     */
    public float hearDistance(float x, float y, float angleDeg, float radius) {
        float d = rayDistance(x, y, angleDeg, radius);
        if (d >= radius) {
            return radius;
        }
        return Math.max(d, radius - SHOT_WALL_MUFFLE);
    }

    private boolean clearLine(Guard g) {
        for (Rect w : walls) {
            if (w.blocks(g.x, g.y, px, py)) {
                return false;
            }
        }
        return true;
    }

    /**
     * What a guard's cone is heading toward: full range if it is alert (hiding
     * in front of a guard that is already chasing you does not help), the
     * shortened range if you are sneaking, full range otherwise.
     */
    private float rangeScaleTarget(Guard g) {
        if (g.state == GuardState.ALERT) {
            return 1f;
        }
        return sneaking ? SNEAK_RANGE_FACTOR : 1f;
    }

    /** How far this guard's cone reaches right now. */
    public float rangeOf(Guard g) {
        return coneRange * g.rangeScale * (1f + CAUTION_CONE_RANGE * cautionBlend) * hunterFov(g) * (1f - (1f - FOCUS_RANGE) * g.focus);
    }

    /** Guards who are alert or in the modified alert see 15% further and wider than usual. */
    private float hunterFov(Guard g) {
        return (g.state == GuardState.ALERT || g.witness) ? HUNTER_FOV_BONUS : 1f;
    }

    /** Half the width of a guard's cone, in degrees, right now. */
    public float halfAngleOf(Guard g) {
        return getConeHalfDeg() * hunterFov(g) * (1f - (1f - FOCUS_ANGLE) * g.focus);
    }

    /** Cone, range and line of sight. */
    public boolean sees(Guard g) {
        float dist = distToPlayer(g);
        if (dist > rangeOf(g)) {
            return false;
        }
        float diff = Math.abs(angleDiff(g.facing, angleTo(g.x, g.y, px, py)));
        if (diff > halfAngleOf(g)) {
            return false;
        }
        if (!noDarkness() && sightCost(g.x, g.y, px, py) > rangeOf(g)) {
            return false;                                   // too much of the way is dark
        }
        return clearLine(g);
    }

    public boolean inDark(float x, float y) {
        if (brokenLights > 0) {
            int c = (int) (x / FOG_CELL);
            int row = (int) (y / FOG_CELL);
            if (c >= 0 && row >= 0 && c < fogCols && row < fogRows && lightDark(row * fogCols + c)) {
                return true;
            }
        }
        for (Rect r : darks) {
            if (x >= r.x && x <= r.x + r.w && y >= r.y && y <= r.y + r.h) {
                return true;
            }
        }
        return false;
    }

    /** How much of a guard's sight range the straight line from (ax, ay) to (bx, by) uses up: its length, with dark stretches counting extra. */
    public float sightCost(float ax, float ay, float bx, float by) {
        float dx = bx - ax;
        float dy = by - ay;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (noDarkness() || len < 0.001f) {
            return len;
        }
        float step = 6f;
        float cost = 0f;
        for (float t = 0f; t < len; t += step) {
            float seg = Math.min(step, len - t);
            float mx = ax + dx * ((t + seg / 2f) / len);
            float my = ay + dy * ((t + seg / 2f) / len);
            cost += seg * (inDark(mx, my) ? DARK_SIGHT_COST : 1f);
        }
        return cost;
    }

    /**
     * How far a guard's view reaches along a bearing: to the first wall or to his range, whichever is nearer, with
     * dark stretches using up the range faster. The views draw cones with it.
     */
    public float visionRay(float x, float y, float angleDeg, float range) {
        float wall = rayDistance(x, y, angleDeg, range);
        if (noDarkness()) {
            return wall;
        }
        double rad = Math.toRadians(angleDeg);
        float cx = (float) Math.cos(rad);
        float cy = (float) Math.sin(rad);
        float step = 6f;
        float cost = 0f;
        float d = 0f;
        while (d < wall) {
            float seg = Math.min(step, wall - d);
            boolean dark = inDark(x + cx * (d + seg / 2f), y + cy * (d + seg / 2f));
            float c = seg * (dark ? DARK_SIGHT_COST : 1f);
            if (cost + c > range) {
                return d + (range - cost) / (dark ? DARK_SIGHT_COST : 1f);
            }
            cost += c;
            d += seg;
        }
        return wall;
    }

    /**
     * Distance along a ray (degrees, 0 = +x) to the first wall, capped at
     * maxRange. The panel uses it to draw cones that stop at walls, matching
     * what sees() actually allows.
     */
    public float rayDistance(float x, float y, float angleDeg, float maxRange) {
        double a = Math.toRadians(angleDeg);
        float dx = (float) Math.cos(a);
        float dy = (float) Math.sin(a);
        float best = maxRange;
        for (Rect w : walls) {
            float tmin = 0f;
            float tmax = best;
            float[] o = {x, y};
            float[] d = {dx, dy};
            float[] lo = {w.x, w.y};
            float[] hi = {w.x + w.w, w.y + w.h};
            boolean hit = true;
            for (int i = 0; i < 2 && hit; i++) {
                if (Math.abs(d[i]) < 1e-6f) {
                    if (o[i] < lo[i] || o[i] > hi[i]) {
                        hit = false;
                    }
                } else {
                    float t1 = (lo[i] - o[i]) / d[i];
                    float t2 = (hi[i] - o[i]) / d[i];
                    if (t1 > t2) {
                        float tmp = t1;
                        t1 = t2;
                        t2 = tmp;
                    }
                    tmin = Math.max(tmin, t1);
                    tmax = Math.min(tmax, t2);
                    if (tmin > tmax) {
                        hit = false;
                    }
                }
            }
            if (hit && tmin < best) {
                best = tmin;
            }
        }
        return best;
    }

    public float getConeRange() {
        return coneRange;
    }

    public float getConeHalfDeg() {
        return coneHalf + CAUTION_CONE_DEG * cautionBlend;
    }

    public static float angleTo(float x1, float y1, float x2, float y2) {
        return (float) Math.toDegrees(Math.atan2(y2 - y1, x2 - x1));
    }

    /** Signed smallest difference a-b in degrees, -180..180. */
    public static float angleDiff(float a, float b) {
        float d = (a - b) % 360f;
        if (d > 180f) {
            d -= 360f;
        }
        if (d < -180f) {
            d += 360f;
        }
        return d;
    }

    /** Moves value toward target by at most maxStep. */
    public static float approach(float value, float target, float maxStep) {
        if (Math.abs(target - value) <= maxStep) {
            return target;
        }
        return value + Math.signum(target - value) * maxStep;
    }

    /** Rotates current toward target by at most maxStep degrees, the short way. */
    public static float turnToward(float current, float target, float maxStep) {
        float d = angleDiff(target, current);
        if (Math.abs(d) <= maxStep) {
            return target;
        }
        return current + Math.signum(d) * maxStep;
    }
}
