package data.scripts.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

public class armaa_ShaftDescentRenderer extends BaseCombatLayeredRenderingPlugin {

    private float speed = 1f;
    private boolean expiring = false; // stop spawning, wait for particles to die
    private boolean reversed = false; // when true, spawn from edges toward center
    private List<ShaftParticle> particles = new ArrayList<>();
    private float particleSpawnTimer = 0f;
    private static final float PARTICLE_SPAWN_INTERVAL = 0.10f;
    private static final int PARTICLES_PER_SPAWN = 1;

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public void setReversed(boolean r) {
        this.reversed = r;
    }

    /** Stop spawning new particles and expire once existing ones die out */
    public void expire() {
        expiring = true;
        cleanup();
    }

    @Override
    public boolean isExpired() {
        return expiring && particles.isEmpty();
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
        if (Global.getCombatEngine().isPaused()) return;

        float screenW = Global.getSettings().getScreenWidth();
        float screenH = Global.getSettings().getScreenHeight();
        float maxDist = (float) Math.sqrt(screenW * screenW + screenH * screenH);

        for (ShaftParticle p : particles) {
            p.life -= amount;
            float lifeRatio = p.life / p.maxLife;
            float ageFactor = 1f - lifeRatio;

            float accel = 0.2f + ageFactor * ageFactor * 6f;
            p.x += p.vx * accel * amount;
            p.y += p.vy * accel * amount;

            p.currentSize = p.size * (0.1f + ageFactor * ageFactor * 2f);

            if (lifeRatio > 0.8f) {
                p.alpha = 1f - ((lifeRatio - 0.8f) / 0.2f);
            } else if (lifeRatio < 0.2f) {
                p.alpha = lifeRatio / 0.2f;
            } else {
                p.alpha = 1f;
            }
        }

        particles.removeIf(p -> p.life <= 0f
                || Math.sqrt(p.x * p.x + p.y * p.y) > maxDist);

        // Only spawn new particles if not expiring
        if (!expiring) {
            particleSpawnTimer += amount;
            if (particleSpawnTimer >= PARTICLE_SPAWN_INTERVAL) {
                particleSpawnTimer = 0f;
                for (int i = 0; i < PARTICLES_PER_SPAWN; i++) {
                    particles.add(new ShaftParticle(reversed));
                }
            }
        }
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (layer != CombatEngineLayers.BELOW_SHIPS_LAYER) return;
        if (particles.isEmpty()) return;

        float cx = viewport.getCenter().x;
        float cy = viewport.getCenter().y;

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendEquation(GL14.GL_FUNC_ADD);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        for (ShaftParticle p : particles) {
            float len = p.currentSize * 3f * Math.abs(speed);
            float angle = (float) Math.atan2(p.vy, p.vx);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            float px = cx + p.x;
            float py = cy + p.y;

            GL11.glColor4f(0.75f, 0.65f, 0.5f, p.alpha * 0.6f);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2f(px - sin * p.currentSize * 0.5f, py + cos * p.currentSize * 0.5f);
            GL11.glVertex2f(px + sin * p.currentSize * 0.5f, py - cos * p.currentSize * 0.5f);
            GL11.glVertex2f(px + cos * len + sin * p.currentSize * 0.5f, py + sin * len - cos * p.currentSize * 0.5f);
            GL11.glVertex2f(px + cos * len - sin * p.currentSize * 0.5f, py + sin * len + cos * p.currentSize * 0.5f);
            GL11.glEnd();
        }

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL14.glBlendEquation(GL14.GL_FUNC_ADD);
    }

    private static class ShaftParticle {

        float x, y, vx, vy, alpha, size, currentSize, life, maxLife;

        ShaftParticle(boolean reversed) {
            float screenW = Global.getSettings().getScreenWidth();
            float screenH = Global.getSettings().getScreenHeight();
            float screenEdgeRadius = (float) Math.sqrt(screenW * screenW + screenH * screenH) * 0.5f;

            float angle = (float) (Math.random() * Math.PI * 2f);
            float spd = 200f + (float) Math.random() * 300f;

            if (!reversed) {
                // Normal: spawn near center, move outward
                float spawnRadius = (float) (Math.random() * 100f);
                x = (float) Math.cos(angle) * spawnRadius;
                y = (float) Math.sin(angle) * spawnRadius;
                vx = (float) Math.cos(angle) * spd;
                vy = (float) Math.sin(angle) * spd;
            } else {
                // Reversed: spawn near screen edge, move inward toward center
                float spawnRadius = screenEdgeRadius * (0.7f + (float) Math.random() * 0.3f);
                x = (float) Math.cos(angle) * spawnRadius;
                y = (float) Math.sin(angle) * spawnRadius;
                vx = -(float) Math.cos(angle) * spd;
                vy = -(float) Math.sin(angle) * spd;
            }

            size = 1f + (float) Math.random() * 4f;
            currentSize = 0f;
            maxLife = 4f + (float) Math.random() * 2f;
            life = maxLife;
            alpha = 0f;
        }
    }
}