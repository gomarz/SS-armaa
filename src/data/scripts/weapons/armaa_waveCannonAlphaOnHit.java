package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import java.awt.Color;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

/**
 * Impact nebula for the hybrid charge cannon's fighter (alpha) bolt. Tier is read from the
 * projectile spec id (armaa_waveCannon_alpha_tN_shot) so all four .proj files share this class.
 */
public class armaa_waveCannonAlphaOnHit implements OnHitEffectPlugin {

    private static final Vector2f ZERO = new Vector2f();
    private static final float CONE_HALF_ANGLE = 35f;
    private static final Color NEBULA_COLOR = new Color(110, 170, 235, 120);
    private static final Color NEBULA_CORE = new Color(200, 230, 255, 140);

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point,
            boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        if (point == null) {
            return;
        }
        int tier = tierOf(projectile);
        float scale = 0.6f + 0.25f * tier;          // tier 1 ~0.85x ... tier 4 ~1.6x
        int count = 4 + tier * 4;
        float baseSize = 40f * scale;
        Vector2f targetVel = target != null ? target.getVelocity() : ZERO;

        // blowback: spray back along the bolt's path in a cone (use facing without +180 to push through instead)
        engine.addHitParticle(point, ZERO, 300f*scale, 1f*scale,0.1f, Color.white);
        float backAngle = projectile.getFacing() + 180f;
        for (int i = 0; i < count; i++) {
            float angle = backAngle + (((float) Math.random() * 2f) - 1f) * CONE_HALF_ANGLE;
            float speed = (40f + (float) Math.random() * 110f) * scale;
            Vector2f vel = MathUtils.getPointOnCircumference(ZERO, speed, angle);
            // inherit a bit of the target's motion so the cloud doesn't slide off a moving ship
            vel.x += targetVel.x * 0.5f;
            vel.y += targetVel.y * 0.5f;

            Vector2f pos = MathUtils.getPointOnCircumference(point,
                    (float) Math.random() * baseSize * 0.4f, (float) Math.random() * 360f);
            float size = baseSize * (0.6f + (float) Math.random() * 0.7f);
            float endSizeMult = 1.8f + (float) Math.random() * 0.8f;
            float duration = 0.7f + (float) Math.random() * 0.6f + 0.1f * tier;
            engine.addNebulaParticle(pos, vel, size, endSizeMult, 0.15f, 0.55f, duration,
                    i % 3 == 0 ? NEBULA_CORE : NEBULA_COLOR);
        }
    }

    private int tierOf(DamagingProjectileAPI proj) {
        String id = proj.getProjectileSpecId();
        if (id != null) {
            int i = id.indexOf("_t");
            if (i >= 0 && i + 2 < id.length() && Character.isDigit(id.charAt(i + 2))) {
                return Math.max(1, Math.min(4, id.charAt(i + 2) - '0'));
            }
        }
        return 1;
    }
}
