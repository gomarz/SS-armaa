/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
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
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;

public class armaa_RajanyaModuleAttack extends BaseCombatLayeredRenderingPlugin {

    private static final float PHASE1_DURATION    = 1f;   // orb rises/charges
    private static final float PHASE2_DURATION    = 2f;   // orb flies outward
    private static final float PHASE4_FADE        = 0.4f;   // orb fades after proj spawns

    private static final float BURST_SPEED        = 600f;   // outward speed in phase 2
    private static final float SPAWN_INTERVAL     = 0.3f;   // time between orb spawns

    private static final float ORB_BASE_SIZE      = 18f;    // core radius in world units
    private static final float ORB_GLOW_MULT      = 2.8f;   // outer glow radius = size * mult
    private static final int   ORB_SEGMENTS       = 32;     // smoothness

    // EMP arc
    private static final float EMP_INTERVAL_MIN   = 2.5f;
    private static final float EMP_INTERVAL_MAX   = 4.5f;
    private static final float EMP_BASE_DAMAGE    = 50f;
    private static final float EMP_FLUX_DAMAGE    = 450f;   // extra damage at 100% flux
    private static final float EMP_ARC_RANGE      = 500f;
    private static final float EMP_ARC_WIDTH      = 12f;

    // Orb colors
private static final float[] COLOR_CORE_INNER  = {0.55f, 0.78f, 0.65f};
private static final float[] COLOR_CORE_OUTER  = {0.42f, 0.70f, 0.60f};
private static final float[] COLOR_GLOW_CENTER = {0.42f, 0.70f, 0.60f};

    private final ShipAPI  sourceShip;
    private final int      totalCount;
    private final int      owner;
    private final Vector2f originOffset;
    private final float    bossFluxLevel; // captured at fire time
    private float          startDelay;

    private int   spawnedCount = 0;
    private float spawnTimer   = 0f;
    private boolean expired    = false;

    private final List<OrbLaser> orbs = new ArrayList<>();
    private Vector2f lastCenter = null;

    public armaa_RajanyaModuleAttack(int owner, int count, Vector2f originOffset,
                                      ShipAPI sourceShip, float startDelay, float bossFluxLevel) {
        this.owner         = owner;
        this.totalCount    = count;
        this.originOffset  = new Vector2f(originOffset);
        this.startDelay    = startDelay;
        this.bossFluxLevel = Math.max(0f, Math.min(1f, bossFluxLevel));
        this.sourceShip    = sourceShip != null ? sourceShip
                : Global.getCombatEngine().getFleetManager(owner)
                        .spawnShipOrWing("armaa_bit_kshatriya_wing",
                                new Vector2f(-10000f, -10000f), 0f);
    }

    @Override
    public boolean isExpired() {
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
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine.isPaused()) return;

        ShipAPI player = engine.getPlayerShip();
        //if (player == null || !player.isAlive() || player.isHulk()) return;

        if (startDelay > 0f) {
            startDelay -= amount;
            return;
        }

        // Track viewport drift so orbs stay locked to screen space
        Vector2f center = engine.getViewport().getCenter();
        if (lastCenter == null) lastCenter = new Vector2f(center);
        float dx = center.x - lastCenter.x;
        float dy = center.y - lastCenter.y;
        lastCenter.set(center.x, center.y);

        if (dx != 0f || dy != 0f) {
            for (OrbLaser orb : orbs) {
                orb.x += dx;
                orb.y += dy;
            }
        }

        // Spawn orbs
        if (spawnedCount < totalCount) {
            spawnTimer += amount;
            if (spawnTimer >= SPAWN_INTERVAL) {
                spawnTimer = 0f;
                spawnOrb(center);
                spawnedCount++;
            }
        }

        // Advance orbs
        Iterator<OrbLaser> it = orbs.iterator();
        while (it.hasNext()) {
            OrbLaser orb = it.next();
            orb.elapsed += amount;

            if (orb.phase == 1) {

                orb.size = 0.3f + (orb.elapsed / PHASE1_DURATION) * 0.7f;
                orb.alpha = Math.min(1f, orb.elapsed / 0.3f);

                orb.pulseTimer += amount * 4f;
                orb.pulseBrightness = 0.7f + (float) Math.sin(orb.pulseTimer) * 0.3f;

                if (orb.elapsed >= PHASE1_DURATION) {
                    orb.phase = 2;
                    orb.elapsed = 0f;
                    // Pick random outward direction and fly offscreen
                    float angle = (float) (Math.random() * Math.PI * 2f);
                    orb.vx = (float) Math.cos(angle) * BURST_SPEED;
                    orb.vy = (float) Math.sin(angle) * BURST_SPEED;
                }

            } else if (orb.phase == 2) {
                // Fly outward toward screen edge
                orb.x += orb.vx * amount;
                orb.y += orb.vy * amount;
                orb.size = 1.0f + (orb.elapsed / PHASE2_DURATION) * 0.3f;
                orb.pulseBrightness = 1f;

                ViewportAPI vp = engine.getViewport();
                float margin = 60f;
                boolean offscreen = orb.x < vp.getLLX() - margin
                        || orb.x > vp.getLLX() + vp.getVisibleWidth() + margin
                        || orb.y < vp.getLLY() - margin
                        || orb.y > vp.getLLY() + vp.getVisibleHeight() + margin;

                if (offscreen || orb.elapsed >= PHASE2_DURATION) {
                    // Spawn the actual homing projectile at this position
                    spawnProjectile(orb);
                    orb.phase = 3;
                    orb.elapsed = 0f;
                }

            } else if (orb.phase == 3) {
                // Fade out
                orb.alpha -= amount / PHASE4_FADE;
                if (orb.alpha <= 0f) {
                    it.remove();
                }
            }
        }

        if (spawnedCount >= totalCount && orbs.isEmpty()) {
            expired = true;
        }
    }

    private void spawnOrb(Vector2f center) {
        OrbLaser orb = new OrbLaser();
        orb.x = center.x + originOffset.y + (float)(Math.random() - 0.5f) * 20f;
        orb.y = center.y + originOffset.x + (float)(Math.random() - 0.5f) * 20f;
        orb.phase = 1;
        orbs.add(orb);

        // Spawn charge effect
        Global.getCombatEngine().spawnExplosion(
                new Vector2f(orb.x, orb.y),
                new Vector2f(),
                new Color(0.05f, 0.2f, 0.7f, 0.6f),
                30f, 0.2f
        );
    }

    private void spawnProjectile(OrbLaser orb) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (sourceShip == null || !engine.isEntityInPlay(sourceShip)) return;

        WeaponAPI weapon = engine.createFakeWeapon(sourceShip, "resonatormrm");
        
        if (weapon == null) return;
        DamagingProjectileAPI proj = (DamagingProjectileAPI) engine.spawnProjectile(
                sourceShip, weapon, weapon.getId(),
                new Vector2f(orb.x, orb.y),
                0f,
                new Vector2f()
        );
        // Aim at nearest visible enemy
        ShipAPI target = pickNearestEnemy(proj);


        if (proj != null) {
            // Attach EMP arc script to the projectile
           engine.addPlugin(new EmpArcScript(proj, owner, bossFluxLevel));
        }

        // Spawn exit flash
        engine.spawnExplosion(
                new Vector2f(orb.x, orb.y),
                new Vector2f(),
                new Color(0.2f, 0.5f, 1.0f, 0.8f),
                50f, 0.15f
        );
    }

    private ShipAPI pickNearestEnemy(DamagingProjectileAPI proj) {
        ShipAPI nearest = null;
        float nearestDist = Float.MAX_VALUE;
        for (ShipAPI ship : CombatUtils.getShipsWithinRange(proj.getLocation(), 4000f)) {
            if (ship.getOwner() == owner) continue;
            if (!ship.isAlive() || ship.isHulk()) continue;
            if (ship.getHullSize() == ShipAPI.HullSize.FIGHTER) continue;
            float dist = MathUtils.getDistance(ship, proj);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = ship;
            }
        }
        return nearest;
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (layer != CombatEngineLayers.BELOW_SHIPS_LAYER) return;
        if (orbs.isEmpty()) return;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendEquation(GL14.GL_FUNC_ADD);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        for (OrbLaser orb : orbs) {
            if (orb.alpha <= 0f) continue;
            float size   = ORB_BASE_SIZE * orb.size;
            float bright = orb.pulseBrightness;
            renderOrb(orb.x, orb.y, size, orb.alpha, bright);
        }

        GL11.glPopAttrib();
    }

    private void renderOrb(float x, float y, float size, float alpha, float bright) {
        float glowSize = size * ORB_GLOW_MULT;
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glColor4f(
                COLOR_GLOW_CENTER[0] * bright,
                COLOR_GLOW_CENTER[1] * bright,
                COLOR_GLOW_CENTER[2] * bright,
                alpha * 0.15f
        );
        GL11.glVertex2f(x, y);
        for (int i = 0; i <= ORB_SEGMENTS; i++) {
            float angle = (float)(i * 2.0 * Math.PI / ORB_SEGMENTS);
            GL11.glColor4f(COLOR_GLOW_CENTER[0], COLOR_GLOW_CENTER[1], COLOR_GLOW_CENTER[2], 0f);
            GL11.glVertex2f(
                    x + (float) Math.cos(angle) * glowSize,
                    y + (float) Math.sin(angle) * glowSize
            );
        }
        GL11.glEnd();

        // Mid glow
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glColor4f(
                COLOR_CORE_OUTER[0] * bright,
                COLOR_CORE_OUTER[1] * bright,
                COLOR_CORE_OUTER[2] * bright,
                alpha * 0.3f
        );
        GL11.glVertex2f(x, y);
        for (int i = 0; i <= ORB_SEGMENTS; i++) {
            float angle = (float)(i * 2.0 * Math.PI / ORB_SEGMENTS);
            GL11.glColor4f(
                    COLOR_CORE_OUTER[0] * bright,
                    COLOR_CORE_OUTER[1] * bright,
                    COLOR_CORE_OUTER[2] * bright,
                    0f
            );
            GL11.glVertex2f(
                    x + (float) Math.cos(angle) * size * 1.4f,
                    y + (float) Math.sin(angle) * size * 1.4f
            );
        }
        GL11.glEnd();

        // Bright core
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glColor4f(
                COLOR_CORE_INNER[0] * bright,
                COLOR_CORE_INNER[1] * bright,
                COLOR_CORE_INNER[2] * bright,
                alpha * 0.55f
        );
        GL11.glVertex2f(x, y);
        for (int i = 0; i <= ORB_SEGMENTS; i++) {
            float angle = (float)(i * 2.0 * Math.PI / ORB_SEGMENTS);
            GL11.glColor4f(
                    COLOR_CORE_OUTER[0] * bright,
                    COLOR_CORE_OUTER[1] * bright,
                    COLOR_CORE_OUTER[2] * bright,
                    alpha * 0.2f
            );
            GL11.glVertex2f(
                    x + (float) Math.cos(angle) * size,
                    y + (float) Math.sin(angle) * size
            );
        }
        GL11.glEnd();
    }

    private static class EmpArcScript extends BaseEveryFrameCombatPlugin {

        private final DamagingProjectileAPI proj;
        private final int   owner;
        private final float bossFluxLevel;
        private final IntervalUtil empInterval;

        EmpArcScript(DamagingProjectileAPI proj, int owner, float bossFluxLevel) {
            this.proj          = proj;
            this.owner         = owner;
            this.bossFluxLevel = bossFluxLevel;
            // Higher flux = faster EMP interval
            float intervalMax = EMP_INTERVAL_MAX - bossFluxLevel * 1.5f;
            float intervalMin = EMP_INTERVAL_MIN - bossFluxLevel * 1.0f;
            this.empInterval = new IntervalUtil(
                    Math.max(0.5f, intervalMin),
                    Math.max(1.0f, intervalMax)
            );
        }

        @Override
        public void advance(float amount, java.util.List events) {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine.isPaused()) return;

            if (proj == null || !engine.isEntityInPlay(proj) || proj.isFading()) {
                engine.removePlugin(this);
                return;
            }

            empInterval.advance(amount);
            if (!empInterval.intervalElapsed()) return;

            // Find nearest enemy to arc to
            ShipAPI target = null;
            float nearestDist = EMP_ARC_RANGE;
            for (ShipAPI ship : engine.getShips()) {
                if (ship.getOwner() == owner) continue;
                if (!ship.isAlive() || ship.isHulk()) continue;
                if(ship.isPhased())
                    continue;
                float dist = org.lazywizard.lazylib.MathUtils.getDistance(
                        proj.getLocation(), ship.getLocation());
                if (dist < nearestDist) {
                    nearestDist = dist;
                    target = ship;
                }
            }

            if (target == null) return;

            float empDamage = EMP_BASE_DAMAGE + bossFluxLevel * EMP_FLUX_DAMAGE;

            engine.spawnEmpArc(
                    proj.getSource(),
                    proj.getLocation(),
                    null,
                    target,
                    DamageType.ENERGY,
                    0f,          // energy damage
                    empDamage,   // EMP damage
                    EMP_ARC_RANGE,
                    "tachyon_lance_emp_impact",
                    EMP_ARC_WIDTH,
                    new Color(100, 180, 255, 200),
                    Color.WHITE
            );

            // Particle burst at arc origin
            engine.addHitParticle(
                    proj.getLocation(),
                    new Vector2f(),
                    30f, 1f, 0.3f,
                    new Color(150, 200, 255, 200)
            );
        }
    }

    private static class OrbLaser {
        float x, y;
        float vx, vy;
        float elapsed     = 0f;
        float size        = 0.1f;
        float alpha       = 0f;
        float pulseTimer  = 0f;
        float pulseBrightness = 1f;
        int   phase       = 1;
    }
}