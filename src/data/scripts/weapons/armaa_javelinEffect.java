package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import org.magiclib.util.MagicLensFlare;
import org.magiclib.util.MagicRender;
import java.awt.Color;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;

public class armaa_javelinEffect implements OnHitEffectPlugin {

    private final int NUM_PARTICLES = 14;
    private final Color PARTICLE_COLOR = new Color(50, 142, 255, 255);

    @Override
        public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine)  {
        
        if(MagicRender.screenCheck(0.2f, point)){
            // Spawn visual effects
            //flash
            engine.addSmoothParticle(
                    point,
                    new Vector2f(),
                    200,
                    2,
                    0.30f,
                    Color.WHITE
            );
            engine.addSmoothParticle(
                    point,
                    new Vector2f(),
                    200,
                    2,
                    0.20f,
                    Color.WHITE
            );
            engine.addSmoothParticle(
                    point,
                    new Vector2f(),
                    200,
                    2,
                    0.1f,
                    Color.WHITE
            );
            MagicLensFlare.createSmoothFlare(
                    engine,
                    projectile.getSource(),
                    point, 
                    15, 
                    400,
                    0,
                    new Color(0,150,255),
                    Color.DARK_GRAY
            );
            
            //cloud
            for(int i=0; i<30; i++){
                int grey=MathUtils.getRandomNumberInRange(20, 100);
                engine.addSmokeParticle(
                        MathUtils.getRandomPointInCircle(point, 40),
                        MathUtils.getRandomPointInCone(
                                new Vector2f(),
                                30,
                                projectile.getFacing()+90,
                                projectile.getFacing()+270
                        ),
                        MathUtils.getRandomNumberInRange(30, 60),
                        1,
                        MathUtils.getRandomNumberInRange(2, 5),
                        new Color(
                                (int)grey/10,
                                (int)grey,
                                (int)(grey/MathUtils.getRandomNumberInRange(1.5f, 2)),
                                MathUtils.getRandomNumberInRange(8, 32)
                        )
                );
            }
            for (int x = 0; x < NUM_PARTICLES; x++) {
                engine.addHitParticle(
                        point, 
                        MathUtils.getRandomPointInCone(new Vector2f(), x*10, projectile.getFacing()+90, projectile.getFacing()+270),
                        7f,
                        1f,
                        2-(x/10),
                        PARTICLE_COLOR);
            }
        }   
    }
}
