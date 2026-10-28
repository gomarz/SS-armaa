package data.scripts.campaign.stealth;

import java.util.ArrayList;
import java.util.List;

import data.scripts.campaign.stealth.armaa_stealthSim.Rect;

/**
 * A stealth level: the walls, where the player starts, the exit, the guards and
 * their routes, and anything lying about to pick up. No game classes in here.
 *
 * Levels are written as text so they can be drawn and edited without touching
 * Java. One character is one tile, TILE units square, and the first row of the
 * map is the top of the room.
 *
 *   #  wall
 *   .  floor (a space works too)
 *   P  where the player starts
 *   E  the exit; the tiles marked E form its rectangle
 *   C, R, D  tiles of a locked door (each letter is one door); walls until opened with the key
 *   r  where Roland waits
 *   B  bars: they stop a body but not a glance (a guard can look through them)
 *   V  the vent grate: a door the player works loose by holding the interact key with a tool in hand
 *   1-8  the door at the end of a tutorial room: locked until that room's lesson is done
 *   F  a second exit (with "next2"); E and F let a level end in two different places
 *
 * After the map come "meta" lines, which use tile coordinates written as
 * column,row with row 0 at the top, same as the map:
 *
 *   guard SPEED MIN_DIFFICULTY : c,r c,r c,r      a patrol loop; the guard
 *                                                 only exists at that difficulty or above
 *   item sidearm c,r                              a pickup
 *   interact TYPE c,r                             something to examine (bunk, sink, vent, ...)
 *   guard SPEED MIN_DIFFICULTY key : ...          the same, and this guard carries the keycard (he drops it when put down)
 *   ventexit c,r [c,r]                            where the vent comes out; the second spot is where the keycard
 *                                                 guard and a partner stand talking when the player comes out there
 *   keyspot c,r                    candidate spots; one of each is chosen at random
 *   suit c,r                                       where the one suit of power armor is kept; the first marine to reach it puts it on
 *   arm c,r                                        a spot where a freed prisoner arms themselves (the armory)
 *   escalate                                       violence escalates here (witnessed blows, a second man found down, blocked blows, lone guards waiting for backup)
 *   rally c,r                                      where freed prisoners (and Roland) wait in their room
 *   fight COL c,r                                  the breakout: when the player passes column COL, guards flood in alert from tile c,r
 *   prisoner PERSON_ID c,r LOCK                    someone in a cage (the view looks the person up in ImportantPeople for their name and portrait); LOCK is the cage door's letter (a, b or c in the map), which is overridden by holding Space
 *   light c,r [R] [wall]                           a light fixture on a floor tile, lighting R tiles round it (default 7); breaking it darkens that floor. "wall" mounts it flush on the nearest wall
 *   dark c0,r0 c1,r1                              a dark zone (tile rectangle, inclusive): a guard sees less far through it
 *   fog R                                         fog of war: the player sees R units, with line of sight
 *   hide c,r                                      somewhere to stow a body out of sight
 *   next NAME                                     the level that follows when the exit is reached
 *   next2 NAME                                    the level that follows when the second exit (F) is reached
 *   crawl c0,r0 c1,r1                             a tile rectangle (inclusive) where the player crawls: slower, quiet, nothing to fight with
 *   cell c0,r0 c1,r1                              the player's cell: guards do not notice the player while he is inside it
 *   drone down                                    the drone starts lying on the floor and wakes after a few seconds
 *   tutorial                                      training rules: guards that would raise the alarm only notice (and the player is told), nobody can wound the player
 *   lesson ID c0,r0 c1,r1                         a room of the tutorial (tile rectangle) and the lesson in it, for the objective line (see armaa_stealthHints.LESSONS)
 *   trip NAME c,r R                               fires the event NAME, once, when the player comes within R units of the tile
 *   blood c,r SIZE                                a pool of blood already on the floor
 *   guard SPEED MIN_DIFFICULTY post : A B                 stands at A looking toward B and never patrols (back at A, facing B, after a distraction)
 *   guard SPEED MIN_DIFFICULTY watch : ...        a patrol with look-in stops: a waypoint written c,r! is where he stops and looks into the cell
 *
 * Runs of wall tiles are merged into as few rectangles as possible, because
 * every sight line and sound ring is tested against every wall.
 */
public class armaa_stealthLevel {

    public static final float TILE = 20f;

    public static class GuardSpec {
        public final float speed;
        public final int minDifficulty;
        public final float[][] route;
        /** This guard carries the keycard. */
        public final boolean carriesKey;
        /** This guard carries the second keycard (the one the east door wants). */
        public final boolean carriesKey2;
        /** He stops at the waypoints in lookSpots and looks into the player's cell, turning to follow them. */
        public boolean watch;
        /** Stands at the first point looking at the second, and does not patrol. */
        public boolean post;
        public boolean[] lookSpots;
        /** A sparring guard: in the tutorial he fights for real (the rest only notice), though nothing he does can wound the player. */
        public boolean spar;

        public GuardSpec(float speed, int minDifficulty, float[][] route) {
            this(speed, minDifficulty, route, false, false);
        }

        public GuardSpec(float speed, int minDifficulty, float[][] route, boolean carriesKey) {
            this(speed, minDifficulty, route, carriesKey, false);
        }

        public GuardSpec(float speed, int minDifficulty, float[][] route, boolean carriesKey, boolean carriesKey2) {
            this.carriesKey2 = carriesKey2;
            this.speed = speed;
            this.minDifficulty = minDifficulty;
            this.route = route;
            this.carriesKey = carriesKey;
        }
    }

    /** Someone in a cage: the id of the person (as registered in ImportantPeople), where they stand, and the name of the lock (a, b, c in the map) on their door. */
    public static class Prisoner {
        public final String id;
        public final float x, y;
        public final String lock;

        public Prisoner(String id, float x, float y, String lock) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.lock = lock;
        }
    }

    public static class Pickup {
        public final String type;
        public final float x, y;
        /** Rounds in a dropped gun. */
        public int count;
        /** Not to be picked up again before this time (something the player has just dropped). */
        public float freeAt;
        /** The player has been told there was no room for it. */
        public boolean refused;

        public Pickup(String type, float x, float y) {
            this.type = type;
            this.x = x;
            this.y = y;
        }
    }

    /** Something the player can examine with the interact key. */
    public static class Interact {
        public final String type;
        public final float x, y;

        public Interact(String type, float x, float y) {
            this.type = type;
            this.x = x;
            this.y = y;
        }
    }

    public float width, height;
    /** Locked doors, by name (C, R, D, N, S, X in the map; X wants the second card). They start as walls and open with the key. */
    public final java.util.Map<String, Rect> locks = new java.util.LinkedHashMap<String, Rect>();
    public final List<Interact> interacts = new ArrayList<Interact>();
    public final List<Prisoner> prisoners = new ArrayList<Prisoner>();
    /** World x past which the breakout starts (the player stepping onto the walkway), or -1 for none. */
    /** Violence escalates here: witnessed blows, a second man found down, a woken guard who reports, blocked blows, lone guards that wait for backup. */
    public boolean escalates;
    public float fightX = -1f;
    /** Where freed prisoners gather before the breakout, or -1. */
    public float rallyX = -1f, rallyY;
    /** Where freed prisoners stand to arm themselves, one spot each. */
    public final List<float[]> armSpots = new ArrayList<float[]>();
    /** Where the one suit of power armor is kept, or null. */
    public float[] suitSpot;
    /** Where the guards flood in from. */
    public float floodX, floodY;
    /** Where the cell vent can come out; one is picked at random per run. */
    public final List<float[]> ventExits = new ArrayList<float[]>();
    /** Where the key might be; one is picked at random per run. */
    public final List<float[]> keySpots = new ArrayList<float[]>();
    /** Places a body can be stowed out of sight (lockers, nooks). */
    public final List<float[]> hides = new ArrayList<float[]>();
    /** How far the player can see, in units, or 0 for no fog of war. */
    public float fogRadius;
    /** Dark zones: guards see less far through them. */
    public final List<Rect> darks = new ArrayList<Rect>();
    /** Light fixtures: {x, y, radius}. Breaking one darkens the part of the floor it lit. */
    public final List<float[]> lights = new ArrayList<float[]>();
    /** Where Roland waits, or null. */
    public float[] rolandAt;
    /** Name of the level that follows this one when the exit is reached, or null. */
    public String next;
    public final List<Rect> walls = new ArrayList<Rect>();
    public float startX, startY;
    public Rect exit;
    /** The second exit and where it leads, or null. */
    public Rect exit2;
    public String next2;
    /** Things that stop a body but not sight: the bars of a cell. */
    public final List<Rect> bars = new ArrayList<Rect>();
    /** Where the player crawls (the vent shaft). */
    public final List<Rect> crawls = new ArrayList<Rect>();
    /** The player's cell, or null. */
    public Rect cell;
    /** The drone starts on the floor. */
    public boolean droneDown;
    /** Training rules, the lesson rooms, the trip wires and the blood already on the floor. */
    public boolean tutorial;
    public final List<Object[]> lessons = new ArrayList<Object[]>();      // {id, Rect}
    public final List<Object[]> trips = new ArrayList<Object[]>();        // {name, float[]{x, y, r}}
    public final List<float[]> bloods = new ArrayList<float[]>();         // {x, y, size}
    public final List<GuardSpec> guards = new ArrayList<GuardSpec>();
    public final List<Pickup> pickups = new ArrayList<Pickup>();

    /**
     * The original single room, kept as the room the unit tests are written
     * against. It is 810 x 400 with two bulkheads, three crates and two guards,
     * plus a third guard from difficulty 2.
     */
    public static armaa_stealthLevel classic() {
        armaa_stealthLevel l = new armaa_stealthLevel();
        l.width = 810f;
        l.height = 400f;
        float t = 8f;
        l.walls.add(new Rect(0, 0, l.width, t));
        l.walls.add(new Rect(0, l.height - t, l.width, t));
        l.walls.add(new Rect(0, 0, t, l.height));
        l.walls.add(new Rect(l.width - t, 0, t, l.height));
        l.walls.add(new Rect(250, 0, t, 140));
        l.walls.add(new Rect(250, 260, t, 140));
        l.walls.add(new Rect(520, 0, t, 90));
        l.walls.add(new Rect(520, 210, t, 190));
        l.walls.add(new Rect(140, 70, 50, 40));
        l.walls.add(new Rect(330, 250, 60, 50));
        l.walls.add(new Rect(610, 250, 60, 60));
        l.startX = 40f;
        l.startY = 200f;
        l.exit = new Rect(735, 150, 55, 100);
        l.guards.add(new GuardSpec(55f, 0, new float[][]{{150f, 320f}, {150f, 175f}, {215f, 175f}, {215f, 320f}}));
        l.guards.add(new GuardSpec(50f, 0, new float[][]{{400f, 70f}, {400f, 330f}}));
        l.guards.add(new GuardSpec(55f, 2, new float[][]{{690f, 110f}, {690f, 300f}}));
        return l;
    }

    /** Builds a level from map rows and meta lines. Throws IllegalArgumentException on a malformed level. */
    public static armaa_stealthLevel parse(String[] map, String[] meta) {
        armaa_stealthLevel l = new armaa_stealthLevel();
        int rows = map.length;
        int cols = 0;
        for (String row : map) {
            cols = Math.max(cols, row.length());
        }
        l.width = cols * TILE;
        l.height = rows * TILE;

        boolean[][] wall = new boolean[rows][cols];
        boolean[][] bar = new boolean[rows][cols];
        float exit2MinX = Float.MAX_VALUE, exit2MinY = Float.MAX_VALUE, exit2MaxX = -1f, exit2MaxY = -1f;
        float exitMinX = Float.MAX_VALUE, exitMinY = Float.MAX_VALUE, exitMaxX = -1f, exitMaxY = -1f;
        boolean haveStart = false;
        float[][] lockBox = new float[128][];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < map[r].length(); c++) {
                char ch = map[r].charAt(c);
                float x0 = c * TILE;
                float y0 = (rows - 1 - r) * TILE;
                if (ch == '#') {
                    wall[r][c] = true;
                } else if (ch == 'B') {
                    bar[r][c] = true;
                } else if (ch == 'F') {
                    exit2MinX = Math.min(exit2MinX, x0);
                    exit2MinY = Math.min(exit2MinY, y0);
                    exit2MaxX = Math.max(exit2MaxX, x0 + TILE);
                    exit2MaxY = Math.max(exit2MaxY, y0 + TILE);
                } else if (ch == 'P') {
                    l.startX = x0 + TILE / 2f;
                    l.startY = y0 + TILE / 2f;
                    haveStart = true;
                } else if (ch == 'C' || ch == 'R' || ch == 'D' || ch == 'N' || ch == 'S' || ch == 'X' || ch == 'V' || (ch >= '1' && ch <= '8') || ch == 'a' || ch == 'b' || ch == 'c' || ch == 'd') {
                    float[] b = lockBox[ch];
                    if (b == null) {
                        b = new float[]{x0, y0, x0 + TILE, y0 + TILE};
                        lockBox[ch] = b;
                    } else {
                        b[0] = Math.min(b[0], x0);
                        b[1] = Math.min(b[1], y0);
                        b[2] = Math.max(b[2], x0 + TILE);
                        b[3] = Math.max(b[3], y0 + TILE);
                    }
                } else if (ch == 'r') {
                    l.rolandAt = new float[]{x0 + TILE / 2f, y0 + TILE / 2f};
                } else if (ch == 'E') {
                    exitMinX = Math.min(exitMinX, x0);
                    exitMinY = Math.min(exitMinY, y0);
                    exitMaxX = Math.max(exitMaxX, x0 + TILE);
                    exitMaxY = Math.max(exitMaxY, y0 + TILE);
                }
            }
        }
        if (!haveStart) {
            throw new IllegalArgumentException("level has no P (player start)");
        }
        if (exitMaxX < 0f) {
            throw new IllegalArgumentException("level has no E (exit)");
        }
        for (char name : new char[]{'C', 'R', 'D', 'N', 'S', 'X', 'V', '1', '2', '3', '4', '5', '6', '7', '8', 'a', 'b', 'c', 'd'}) {
            float[] b = lockBox[name];
            if (b != null) {
                l.locks.put(String.valueOf(name), new Rect(b[0], b[1], b[2] - b[0], b[3] - b[1]));
            }
        }
        l.exit = new Rect(exitMinX, exitMinY, exitMaxX - exitMinX, exitMaxY - exitMinY);
        if (exit2MaxX >= 0f) {
            l.exit2 = new Rect(exit2MinX, exit2MinY, exit2MaxX - exit2MinX, exit2MaxY - exit2MinY);
        }
        mergeWalls(wall, rows, cols, l.walls);
        mergeWalls(bar, rows, cols, l.bars);

        for (String line : meta) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("guard")) {
                String[] halves = line.split(":");
                String[] head = halves[0].trim().split("\\s+");
                float speed = Float.parseFloat(head[1]);
                int minDiff = head.length > 2 ? Integer.parseInt(head[2]) : 0;
                boolean carriesKey = head.length > 3 && "key".equals(head[3]);
                boolean carriesKey2 = head.length > 3 && "key2".equals(head[3]);
                boolean watch = head.length > 3 && "watch".equals(head[3]);
                boolean spar = head.length > 3 && "spar".equals(head[3]);
                boolean post = head.length > 3 && "post".equals(head[3]);
                String[] pts = halves[1].trim().split("\\s+");
                float[][] route = new float[pts.length][2];
                boolean[] looks = new boolean[pts.length];
                for (int i = 0; i < pts.length; i++) {
                    if (pts[i].endsWith("!")) {
                        looks[i] = true;
                        pts[i] = pts[i].substring(0, pts[i].length() - 1);
                    }
                    float[] p = tile(pts[i], rows);
                    route[i][0] = p[0];
                    route[i][1] = p[1];
                }
                if (route.length < 2) {
                    throw new IllegalArgumentException("a guard needs at least two waypoints: " + line);
                }
                GuardSpec spec = new GuardSpec(speed, minDiff, route, carriesKey, carriesKey2);
                spec.watch = watch;
                spec.spar = spar;
                spec.post = post;
                spec.lookSpots = looks;
                l.guards.add(spec);
            } else if (line.startsWith("interact")) {
                String[] parts = line.split("\\s+");
                float[] p = tile(parts[2], rows);
                l.interacts.add(new Interact(parts[1], p[0], p[1]));
            } else if (line.startsWith("suit")) {
                l.suitSpot = tile(line.split("\\s+")[1], rows);
            } else if (line.startsWith("arm ")) {
                l.armSpots.add(tile(line.split("\\s+")[1], rows));
            } else if (line.startsWith("escalate")) {
                l.escalates = true;
            } else if (line.startsWith("rally")) {
                float[] p = tile(line.split("\\s+")[1], rows);
                l.rallyX = p[0];
                l.rallyY = p[1];
            } else if (line.startsWith("fight")) {
                String[] parts = line.split("\\s+");
                l.fightX = Integer.parseInt(parts[1]) * TILE;
                float[] p = tile(parts[2], rows);
                l.floodX = p[0];
                l.floodY = p[1];
            } else if (line.startsWith("prisoner")) {
                String[] parts = line.split("\\s+");
                float[] p = tile(parts[2], rows);
                l.prisoners.add(new Prisoner(parts[1], p[0], p[1], parts[3]));
            } else if (line.startsWith("ventexit")) {
                String[] parts = line.split("\\s+");
                float[] exit = tile(parts[1], rows);
                if (parts.length > 2) {
                    float[] huddle = tile(parts[2], rows);
                    l.ventExits.add(new float[]{exit[0], exit[1], huddle[0], huddle[1]});
                } else {
                    l.ventExits.add(exit);
                }
            } else if (line.startsWith("light")) {
                String[] parts = line.split("\\s+");
                float[] p = tile(parts[1], rows);
                float tiles = 7f;
                float mounted = 0f;
                for (int i = 2; i < parts.length; i++) {
                    if ("wall".equals(parts[i])) {
                        mounted = 1f;
                    } else {
                        tiles = Float.parseFloat(parts[i]);
                    }
                }
                l.lights.add(new float[]{p[0], p[1], tiles * TILE, mounted});
            } else if (line.startsWith("dark")) {
                String[] parts = line.split("\\s+");
                String[] a = parts[1].split(",");
                String[] b = parts[2].split(",");
                int c0 = Integer.parseInt(a[0].trim()), r0 = Integer.parseInt(a[1].trim());
                int c1 = Integer.parseInt(b[0].trim()), r1 = Integer.parseInt(b[1].trim());
                l.darks.add(new Rect(c0 * TILE, (rows - 1 - r1) * TILE, (c1 - c0 + 1) * TILE, (r1 - r0 + 1) * TILE));
            } else if (line.startsWith("fog")) {
                l.fogRadius = Float.parseFloat(line.split("\\s+")[1]);
            } else if (line.startsWith("hide")) {
                l.hides.add(tile(line.split("\\s+")[1], rows));
            } else if (line.startsWith("keyspot")) {
                l.keySpots.add(tile(line.split("\\s+")[1], rows));
            } else if (line.startsWith("next2")) {
                l.next2 = line.split("\\s+")[1];
            } else if (line.startsWith("next")) {
                l.next = line.split("\\s+")[1];
            } else if (line.startsWith("crawl") || line.startsWith("cell")) {
                String[] parts = line.split("\\s+");
                String[] a = parts[1].split(",");
                String[] b = parts[2].split(",");
                int c0 = Integer.parseInt(a[0].trim()), r0 = Integer.parseInt(a[1].trim());
                int c1 = Integer.parseInt(b[0].trim()), r1 = Integer.parseInt(b[1].trim());
                Rect zone = new Rect(c0 * TILE, (rows - 1 - r1) * TILE, (c1 - c0 + 1) * TILE, (r1 - r0 + 1) * TILE);
                if (line.startsWith("crawl")) {
                    l.crawls.add(zone);
                } else {
                    l.cell = zone;
                }
            } else if (line.startsWith("drone")) {
                l.droneDown = line.contains("down");
            } else if (line.startsWith("tutorial")) {
                l.tutorial = true;
            } else if (line.startsWith("lesson")) {
                String[] parts = line.split("\\s+");
                String[] a = parts[2].split(",");
                String[] b = parts[3].split(",");
                int c0 = Integer.parseInt(a[0].trim()), r0 = Integer.parseInt(a[1].trim());
                int c1 = Integer.parseInt(b[0].trim()), r1 = Integer.parseInt(b[1].trim());
                l.lessons.add(new Object[]{parts[1], new Rect(c0 * TILE, (rows - 1 - r1) * TILE, (c1 - c0 + 1) * TILE, (r1 - r0 + 1) * TILE)});
            } else if (line.startsWith("trip")) {
                String[] parts = line.split("\\s+");
                float[] at = tile(parts[2], rows);
                l.trips.add(new Object[]{parts[1], new float[]{at[0], at[1], Float.parseFloat(parts[3])}});
            } else if (line.startsWith("blood")) {
                String[] parts = line.split("\\s+");
                float[] at = tile(parts[1], rows);
                l.bloods.add(new float[]{at[0], at[1], Float.parseFloat(parts[2])});
            } else if (line.startsWith("item")) {
                String[] parts = line.split("\\s+");
                float[] p = tile(parts[2], rows);
                l.pickups.add(new Pickup(parts[1], p[0], p[1]));
            } else {
                throw new IllegalArgumentException("unknown meta line: " + line);
            }
        }
        return l;
    }

    /** "c,r" in tile coordinates (row 0 at the top) to the centre of that tile in world units. */
    private static float[] tile(String s, int rows) {
        String[] cr = s.split(",");
        int c = Integer.parseInt(cr[0].trim());
        int r = Integer.parseInt(cr[1].trim());
        return new float[]{c * TILE + TILE / 2f, (rows - 1 - r) * TILE + TILE / 2f};
    }

    /** Horizontal runs, then identical runs in consecutive rows merged into one rectangle. */
    private static void mergeWalls(boolean[][] wall, int rows, int cols, List<Rect> out) {
        // open[c0][c1] -> first row of a still-growing block
        java.util.Map<Long, Integer> open = new java.util.HashMap<Long, Integer>();
        for (int r = 0; r <= rows; r++) {
            java.util.Set<Long> thisRow = new java.util.HashSet<Long>();
            if (r < rows) {
                int c = 0;
                while (c < cols) {
                    if (wall[r][c]) {
                        int start = c;
                        while (c < cols && wall[r][c]) {
                            c++;
                        }
                        thisRow.add(key(start, c - 1));
                    } else {
                        c++;
                    }
                }
            }
            // blocks that did not continue into this row are finished
            List<Long> finished = new ArrayList<Long>();
            for (java.util.Map.Entry<Long, Integer> e : open.entrySet()) {
                if (!thisRow.contains(e.getKey())) {
                    finished.add(e.getKey());
                }
            }
            for (Long k : finished) {
                emit(out, k, open.remove(k), r - 1, rows);
            }
            for (Long k : thisRow) {
                if (!open.containsKey(k)) {
                    open.put(k, r);
                }
            }
        }
    }

    private static long key(int c0, int c1) {
        return ((long) c0 << 32) | (c1 & 0xffffffffL);
    }

    private static void emit(List<Rect> out, long k, int r0, int r1, int rows) {
        int c0 = (int) (k >> 32);
        int c1 = (int) k;
        float x = c0 * TILE;
        float w = (c1 - c0 + 1) * TILE;
        float y = (rows - 1 - r1) * TILE;
        float h = (r1 - r0 + 1) * TILE;
        out.add(new Rect(x, y, w, h));
    }
}
