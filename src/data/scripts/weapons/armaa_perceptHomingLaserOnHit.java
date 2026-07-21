package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;

import org.magiclib.util.MagicRender;

import org.lwjgl.util.vector.Vector2f;

import org.lazywizard.lazylib.MathUtils;

import java.awt.Color;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;

public class armaa_perceptHomingLaserOnHit implements OnHitEffectPlugin {

    // -- stuff for tweaking particle characteristics ------------------------
    // color of spawned particles
    private static final Color PARTICLE_COLOR = new Color(100, 150, 255, 155);
    // size of spawned particles (possibly in pixels?)
    private static final float PARTICLE_SIZE = 8f;
    // brightness of spawned particles (i have no idea what this ranges from)
    private static final float PARTICLE_BRIGHTNESS = 150f;
    // how long the particles last (i'm assuming this is in seconds)
    private static final float PARTICLE_DURATION = 1f;
    private static final int PARTICLE_COUNT = 5;

    // -- particle geometry --------------------------------------------------
    // cone angle in degrees
    private static final float CONE_ANGLE = 150f;
    // constant that effects the lower end of the particle velocity
    private static final float VEL_MIN = 0.5f;
    // constant that effects the upper end of the particle velocity
    private static final float VEL_MAX = 0.8f;

    // one half of the angle. used internally, don't mess with thos
    private static final float A_2 = CONE_ANGLE / 2;

    @Override
    public void onHit(DamagingProjectileAPI projectile,
            CombatEntityAPI target,
            Vector2f point,
            boolean shieldHit,
            ApplyDamageResultAPI damageResult,
            CombatEngineAPI engine) {
            // do visual effects ---------------------------------------------
                if (MagicRender.screenCheck(0.2f, point)) {

            //engine.spawnExplosion(point, new Vector2f(), new Color(100, 200, 255, 50), 25f, 0.1f);

            float speed = Math.min(100f,projectile.getVelocity().length());
            float facing = projectile.getFacing();

            for (int i = 0; i <= PARTICLE_COUNT; i++) {
                float angle = MathUtils.getRandomNumberInRange(facing - A_2,
                        facing + A_2);
                float vel = MathUtils.getRandomNumberInRange(speed * -VEL_MIN,
                        speed * -VEL_MAX);
                Vector2f vector = MathUtils.getPointOnCircumference(null,
                        vel,
                        angle);
                engine.addHitParticle(point,
                        vector,
                        PARTICLE_SIZE*(float)(Math.random()),
                        PARTICLE_BRIGHTNESS,
                        PARTICLE_DURATION*(float)(Math.random()),
                        PARTICLE_COLOR);
            }
                        MagicRender.battlespace(
                    Global.getSettings().getSprite("misc", "armaa_sfxpulse"),
                    point,
                    new Vector2f(),
                    new Vector2f(projectile.getCollisionRadius(), projectile.getCollisionRadius()),
                    new Vector2f(300f, 300f),
                    5f,
                    5f,
                    new Color(250, 100, 0, 100),
                    true,
                    .1f,
                    .15f,
                    .20f
            );
            if(!(target instanceof ShipAPI))
                return;
            
            float pierceChance = ((ShipAPI) target).getHardFluxLevel() - 0.3f;
            pierceChance *= projectile.getSource().getMutableStats().getDynamic().getValue(Stats.SHIELD_PIERCED_MULT);
            ShipAPI ship = projectile.getSource();
            boolean piercedShield = shieldHit && (float) Math.random() < pierceChance;
            float fluxLevel = engine.getCustomData().containsKey("armaa_percept_homing_fluxLevel_"+ship.getId()) ? (float)engine.getCustomData().get("armaa_percept_homing_fluxLevel_"+ship.getId()) : 0;
            if ((float) Math.random() > Math.max(0.1f,0.75f-fluxLevel) && (!shieldHit || piercedShield)) {
                float emp = projectile.getDamage().getFluxComponent() * 1f;
                float dam = projectile.getDamage().getDamage() * 1f;
                engine.spawnEmpArcPierceShields(
                        projectile.getSource(), point, target, target,
                        DamageType.ENERGY,
                        dam, // damage
                        emp*(fluxLevel*1.3f), // emp
                        100000f, // max range
                        "tachyon_lance_emp_impact",
                        4f + 4f,
                        PARTICLE_COLOR,
                        Color.white
                );
            }
        }
    }
}
