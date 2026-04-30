package data.scripts.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import java.awt.Color;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.EnumSet;
import org.magiclib.util.MagicRender;

public class armaa_RajanyaLaserAttack extends BaseCombatLayeredRenderingPlugin {

    // --- Tunables ---
    private static final float PHASE1_DURATION = 0.4f;
    private static final float PHASE2_DURATION = 0.4f;  // increased for more coast time
    private static final float PHASE3_DURATION = 2.0f;  // increased for visible arc
    private static final float PHASE4_FADE_DURATION = 0.25f;
    private static final float BURST_SPEED = 500f;
    private static final float COAST_SPEED = 320f;
    private static final float LOCK_SPEED = 350f;       // reduced for wider arc
    private static final float SPAWN_RADIUS = 25f;
    private static final float WARNING_PULSE_SLOW = 0.4f;
    private static final float WARNING_PULSE_FAST = 0.12f;
    private static final float CORE_WIDTH = 4f;
    private static final float OUTER_WIDTH = 8f;
    private static final int TRAIL_LENGTH = 72;
    private static final float SPAWN_INTERVAL = 0.15f;

    // Phase 3 arc tuning — lower steer = wider, more sweeping arc
    private static final float PHASE3_STEER_RATE = 0.22f; // reduced for gentler arc

    private static final float BRIGHTNESS_SIZE_MIN = 0.1f;
    private static final float BRIGHTNESS_SIZE_MAX = 1f;

    private final ShipAPI sourceShip;
    private final List<FakeLaser> lasers = new ArrayList<>();
    private boolean expired = false;

    private final int totalCount;
    private int spawnedCount = 0;
    private float spawnTimer = 0f;
    private int owner = 0;

    private Vector2f lastCenter = null;

    public armaa_RajanyaLaserAttack(int owner, int count) {
        // hacky
        this.sourceShip = Global.getCombatEngine().getFleetManager(owner).spawnShipOrWing("broadsword_wing", new Vector2f(-10000, -10000), 0f);
        this.totalCount = count;
        this.owner = owner;
    }

    @Override
    public boolean isExpired() {
        if (expired) {
            Global.getCombatEngine().removeEntity(sourceShip);
        }
        return expired;
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(CombatEngineLayers.BELOW_SHIPS_LAYER);
    }

    @Override
    public float getRenderRadius() {
        return Float.MAX_VALUE;
    }

    @Override
    public void advance(float amount) {
        if (Global.getCombatEngine().isPaused()) {
            return;
        }

        ShipAPI player = Global.getCombatEngine().getPlayerShip();
        if (player == null) {
            return;
        }

        Vector2f center = Global.getCombatEngine().getViewport().getCenter();

        if (lastCenter == null) {
            lastCenter = new Vector2f(center);
        }

        float centerDx = center.x - lastCenter.x;
        float centerDy = center.y - lastCenter.y;
        lastCenter.set(center.x, center.y);

        if (!lasers.isEmpty() && (centerDx != 0f || centerDy != 0f)) {
            for (FakeLaser laser : lasers) {
                laser.x += centerDx;
                laser.y += centerDy;
                for (float[] pt : laser.trail) {
                    pt[0] += centerDx;
                    pt[1] += centerDy;
                }
            }
        }

        if (spawnedCount < totalCount) {
            spawnTimer += amount;
            if (spawnTimer >= SPAWN_INTERVAL) {
                spawnTimer = 0f;
                spawnLaser(center);
                spawnedCount++;
            }
        }

        Iterator<FakeLaser> it = lasers.iterator();
        while (it.hasNext()) {
            FakeLaser laser = it.next();
            laser.elapsed += amount;

            float playerX = player.getLocation().x;
            float playerY = player.getLocation().y;

            if (laser.phase == 1) {
                laser.x += laser.vx * amount;
                laser.y += laser.vy * amount;
                ViewportAPI vp = Global.getCombatEngine().getViewport();
                float margin = 80f;
                float minX = vp.getLLX() + margin;
                float maxX = vp.getLLX() + vp.getVisibleWidth() - margin;
                float minY = vp.getLLY() + margin;
                float maxY = vp.getLLY() + vp.getVisibleHeight() - margin;

                laser.recordTrail();

                if (laser.x < minX || laser.x > maxX || laser.y < minY || laser.y > maxY) {
                    laser.phase = 3;
                    laser.elapsed = 0f;
                    float spd = (float) Math.sqrt(laser.vx * laser.vx + laser.vy * laser.vy);
                    if (spd > 0) {
                        laser.vx = (laser.vx / spd) * COAST_SPEED;
                        laser.vy = (laser.vy / spd) * COAST_SPEED;
                    }
                }
                laser.size = 0.1f + (laser.elapsed / PHASE1_DURATION) * 0.5f;
                laser.alpha = Math.min(1f, laser.elapsed / 0.2f);

                laser.warningTimer += amount;
                if (laser.warningTimer >= WARNING_PULSE_SLOW) {
                    laser.warningTimer = 0f;
                    laser.warningVisible = !laser.warningVisible;
                }

                if (laser.elapsed >= PHASE1_DURATION) {
                    laser.phase = 2;
                    laser.elapsed = 0f;
                    float spd = (float) Math.sqrt(laser.vx * laser.vx + laser.vy * laser.vy);
                    if (spd > 0) {
                        laser.vx = (laser.vx / spd) * COAST_SPEED;
                        laser.vy = (laser.vy / spd) * COAST_SPEED;
                    }
                }

            } else if (laser.phase == 2) {
                laser.x += laser.vx * amount;
                laser.y += laser.vy * amount;
                laser.recordTrail();
                laser.size = 0.6f + (laser.elapsed / PHASE2_DURATION) * 0.3f;

                laser.warningTimer += amount;
                if (laser.warningTimer >= WARNING_PULSE_SLOW) {
                    laser.warningTimer = 0f;
                    laser.warningVisible = !laser.warningVisible;
                }

                if (laser.elapsed >= PHASE2_DURATION) {
                    laser.phase = 3;
                    laser.elapsed = 0f;
                    laser.aimTarget = pickRandomVisibleEnemy();
                }

            } else if (laser.phase == 3) {
                float tx = playerX, ty = playerY;
                if (laser.aimTarget != null && Global.getCombatEngine().isEntityInPlay(laser.aimTarget)
                        && !laser.aimTarget.isHulk()) {
                    tx = laser.aimTarget.getLocation().x;
                    ty = laser.aimTarget.getLocation().y;
                }
                float dx = tx - laser.x;
                float dy = ty - laser.y;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist > 0) {
                    float targetVx = (dx / dist) * LOCK_SPEED;
                    float targetVy = (dy / dist) * LOCK_SPEED;
                    float steer = Math.min(1f, amount * PHASE3_STEER_RATE);
                    laser.vx += (targetVx - laser.vx) * steer;
                    laser.vy += (targetVy - laser.vy) * steer;
                    float spd = (float) Math.sqrt(laser.vx * laser.vx + laser.vy * laser.vy);
                    if (spd > 0) {
                        laser.vx = (laser.vx / spd) * LOCK_SPEED;
                        laser.vy = (laser.vy / spd) * LOCK_SPEED;
                    }
                }

                laser.x += laser.vx * amount;
                laser.y += laser.vy * amount;
                laser.recordTrail();
                laser.size = 0.9f + (laser.elapsed / PHASE3_DURATION) * 0.6f;

                laser.warningTimer += amount;
                if (laser.warningTimer >= WARNING_PULSE_FAST) {
                    laser.warningTimer = 0f;
                    laser.warningVisible = !laser.warningVisible;
                }

                if (laser.elapsed >= PHASE3_DURATION) {
                    if (sourceShip != null && Global.getCombatEngine().isEntityInPlay(sourceShip)) {
                        WeaponAPI weapon = Global.getCombatEngine().createFakeWeapon(sourceShip, "armaa_percept_homing");
                        if (weapon != null) {
                            float aimX = tx, aimY = ty;
                            float adx = aimX - laser.x;
                            float ady = aimY - laser.y;
                            float angle = (float) Math.toDegrees(Math.atan2(ady, adx));
                            angle += (float) (Math.random() - 0.5f) * 20f;
                            DamagingProjectileAPI proj = (DamagingProjectileAPI) Global.getCombatEngine().spawnProjectile(
                                    sourceShip, weapon, weapon.getId(),
                                    new Vector2f(laser.x, laser.y),
                                    angle, sourceShip.getVelocity());
                            laser.trackedProj = proj;
                        }
                    }
                    laser.phase = 4;
                    laser.elapsed = 0f;
                }

            } else if (laser.phase == 4) {
                if (laser.trackedProj != null && Global.getCombatEngine().isEntityInPlay(laser.trackedProj)) {
                    laser.x = laser.trackedProj.getLocation().x;
                    laser.y = laser.trackedProj.getLocation().y;
                    laser.vx = laser.trackedProj.getVelocity().x;
                    laser.vy = laser.trackedProj.getVelocity().y;
                    laser.recordTrail();
                }
                laser.alpha -= amount / PHASE4_FADE_DURATION;
                if (laser.alpha <= 0f || laser.trackedProj == null
                        || !Global.getCombatEngine().isEntityInPlay(laser.trackedProj)) {
                    it.remove();
                    continue;
                }
            }
        }

        if (spawnedCount >= totalCount && lasers.isEmpty()) {
            expired = true;
        }
    }

    private void spawnLaser(Vector2f center) {
        FakeLaser laser = new FakeLaser();

        float angle = (float) (Math.random() * Math.PI * 2f);
        float radius = (float) (Math.random() * SPAWN_RADIUS);
        laser.x = center.x + (float) Math.cos(angle) * radius;
        laser.y = center.y + (float) Math.sin(angle) * radius;

        float burstAngle = (float) (Math.random() * Math.PI * 2f);
        laser.vx = (float) Math.cos(burstAngle) * BURST_SPEED;
        laser.vy = (float) Math.sin(burstAngle) * BURST_SPEED;

        MagicRender.battlespace(
                Global.getSettings().getSprite("misc", "armaa_sfxpulse"),
                new Vector2f(laser.x, laser.y),
                new Vector2f(),
                new Vector2f(),
                new Vector2f(25f, 25f),
                0f,
                5f,
                new Color(0.1f, 0.2f, 0.3f, 1f),
                true,
                .05f,
                .2f,
                .2f
        );

        laser.warningVisible = true;
        Global.getCombatEngine().spawnExplosion(new Vector2f(laser.x, laser.y), new Vector2f(), new Color(0.0f, 0.1f, 0.2f, 0.8f), 25f, 0.1f);
        Global.getSoundPlayer().playSound("vajra_fire", 0.6f, 1f, new Vector2f(laser.x, laser.y), new Vector2f());
        lasers.add(laser);
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (layer != CombatEngineLayers.BELOW_SHIPS_LAYER) {
            return;
        }
        if (lasers.isEmpty()) {
            return;
        }

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendEquation(GL14.GL_FUNC_ADD);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        for (FakeLaser laser : lasers) {
            List<float[]> trail = laser.trail;
            int trailSize = trail.size();
            if (trailSize < 2) {
                continue;
            }

            float brightness = Math.min(1f, Math.max(0f,
                    (laser.size - BRIGHTNESS_SIZE_MIN) / (BRIGHTNESS_SIZE_MAX - BRIGHTNESS_SIZE_MIN)));

            float outerR = 0.2f * brightness;
            float outerG = 0.7f * brightness;
            float outerB = 1.0f;
            float coreR = 0.8f * brightness;
            float coreG = 0.95f * brightness;
            float coreB = 1.0f;

            float[] perpX = new float[trailSize];
            float[] perpY = new float[trailSize];

            for (int i = 0; i < trailSize; i++) {
                float tx = 0f, ty = 0f;
                if (i > 0) {
                    float dx = trail.get(i - 1)[0] - trail.get(i)[0];
                    float dy = trail.get(i - 1)[1] - trail.get(i)[1];
                    float len = (float) Math.sqrt(dx * dx + dy * dy);
                    if (len > 0) { tx += dx / len; ty += dy / len; }
                }
                if (i < trailSize - 1) {
                    float dx = trail.get(i)[0] - trail.get(i + 1)[0];
                    float dy = trail.get(i)[1] - trail.get(i + 1)[1];
                    float len = (float) Math.sqrt(dx * dx + dy * dy);
                    if (len > 0) { tx += dx / len; ty += dy / len; }
                }
                float tlen = (float) Math.sqrt(tx * tx + ty * ty);
                if (tlen > 0) { tx /= tlen; ty /= tlen; }
                perpX[i] = -ty;
                perpY[i] = tx;
            }

            for (int i = 0; i < trailSize - 1; i++) {
                float t0 = 1f - (float) i / trailSize;
                float t1 = 1f - (float) (i + 1) / trailSize;
                float ax = trail.get(i)[0], ay = trail.get(i)[1];
                float bx = trail.get(i + 1)[0], by = trail.get(i + 1)[1];

                float ow0 = OUTER_WIDTH * laser.size * t0 * 0.5f;
                float ow1 = OUTER_WIDTH * laser.size * t1 * 0.5f;
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glColor4f(outerR, outerG, outerB, laser.alpha * t0 * 0.35f);
                GL11.glVertex2f(ax - perpX[i] * ow0, ay - perpY[i] * ow0);
                GL11.glVertex2f(ax + perpX[i] * ow0, ay + perpY[i] * ow0);
                GL11.glColor4f(outerR, outerG, outerB, laser.alpha * t1 * 0.35f);
                GL11.glVertex2f(bx + perpX[i + 1] * ow1, by + perpY[i + 1] * ow1);
                GL11.glVertex2f(bx - perpX[i + 1] * ow1, by - perpY[i + 1] * ow1);
                GL11.glEnd();

                float cw0 = CORE_WIDTH * laser.size * t0 * 0.5f;
                float cw1 = CORE_WIDTH * laser.size * t1 * 0.5f;
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glColor4f(coreR, coreG, coreB, laser.alpha * t0 * 0.9f);
                GL11.glVertex2f(ax - perpX[i] * cw0, ay - perpY[i] * cw0);
                GL11.glVertex2f(ax + perpX[i] * cw0, ay + perpY[i] * cw0);
                GL11.glColor4f(coreR, coreG, coreB, laser.alpha * t1 * 0.9f);
                GL11.glVertex2f(bx + perpX[i + 1] * cw1, by + perpY[i + 1] * cw1);
                GL11.glVertex2f(bx - perpX[i + 1] * cw1, by - perpY[i + 1] * cw1);
                GL11.glEnd();
            }

            if (laser.warningVisible && laser.phase != 4) {
                float wx = laser.x;
                float wy = laser.y;
                float ws = 12f * laser.size;
                float warningAlpha = laser.phase == 3 ? laser.alpha : laser.alpha * 0.6f;
                float wr = laser.phase == 3 ? 1.0f : 0.9f;
                float wg = laser.phase == 3 ? 0.2f : 0.8f;
                float wb = 0.1f;

                GL11.glColor4f(wr, wg, wb, warningAlpha);
                GL11.glLineWidth(1.5f);
                GL11.glBegin(GL11.GL_LINE_STRIP);
                GL11.glVertex2f(wx - ws, wy + ws * 0.5f);
                GL11.glVertex2f(wx - ws, wy - ws * 0.5f);
                GL11.glVertex2f(wx - ws * 0.5f, wy - ws * 0.5f);
                GL11.glEnd();
                GL11.glBegin(GL11.GL_LINE_STRIP);
                GL11.glVertex2f(wx + ws * 0.5f, wy - ws * 0.5f);
                GL11.glVertex2f(wx + ws, wy - ws * 0.5f);
                GL11.glVertex2f(wx + ws, wy + ws * 0.5f);
                GL11.glEnd();
            }
        }

        GL11.glPopAttrib();
    }

    private ShipAPI pickRandomVisibleEnemy() {
        ViewportAPI vp = Global.getCombatEngine().getViewport();
        List<ShipAPI> candidates = new ArrayList<>();
        for (ShipAPI ship : Global.getCombatEngine().getShips()) {
            if (ship.getOwner() == owner) continue;
            if (ship.isHulk() || !ship.isAlive()) continue;
            Vector2f loc = ship.getLocation();
            if (loc.x < vp.getLLX() || loc.x > vp.getLLX() + vp.getVisibleWidth()) continue;
            if (loc.y < vp.getLLY() || loc.y > vp.getLLY() + vp.getVisibleHeight()) continue;
            candidates.add(ship);
        }
        if (candidates.isEmpty()) return null;
        return candidates.get((int) (Math.random() * candidates.size()));
    }

    private static class FakeLaser {
        float x, y;
        float vx, vy;
        float elapsed = 0f;
        float size = 0.1f;
        float alpha = 0f;
        int phase = 1;
        float warningTimer = 0f;
        boolean warningVisible = true;
        ShipAPI aimTarget = null;
        DamagingProjectileAPI trackedProj = null;
        List<float[]> trail = new ArrayList<>();

        void recordTrail() {
            trail.add(0, new float[]{x, y});
            if (trail.size() > TRAIL_LENGTH) {
                trail.remove(trail.size() - 1);
            }
        }
    }
}