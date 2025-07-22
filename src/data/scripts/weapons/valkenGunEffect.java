package data.scripts.weapons;

import java.awt.Color;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import org.magiclib.util.MagicRender;
import org.lazywizard.lazylib.MathUtils;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;

public class valkenGunEffect implements OnHitEffectPlugin {
    
    private final Color PARTICLE_COLOR = new Color(200, 55, 200);
    private final float PARTICLE_SIZE = 4f;
    private final float PARTICLE_BRIGHTNESS = 150;
    private final float PARTICLE_DURATION = 1f;
	private static final int PARTICLE_COUNT = 2;

    private final float EXPLOSION_SIZE = 15f;      
    private final Color EXPLOSION_COLOR = new Color(200, 50, 200);
    
    private final float FLASH_SIZE = 7.5f;
    private final Color FLASH_COLOR = new Color(255, 166, 239);
    
    private final float GLOW_SIZE = 10f;
	private static final float CONE_ANGLE = 150f;
    private static final float A_2 = CONE_ANGLE / 2;
	
		    // constant that effects the lower end of the particle velocity
    private static final float VEL_MIN = 0.15f;
    // constant that effects the upper end of the particle velocity
    private static final float VEL_MAX = 0.25f;
    
    @Override
        public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine)  {
        
        if(MagicRender.screenCheck(0.1f, point)){
			
			engine.addSmoothParticle(point, new Vector2f(), 50f, 0.8f, 0.25f, new Color(200, 55, 200, 255)); 
			engine.addHitParticle(point, new Vector2f(), GLOW_SIZE + (float)Math.random()*5, 1, 0.1f, PARTICLE_COLOR); 				


			float speed = projectile.getVelocity().length();
            float facing = projectile.getFacing();
            for (int i = 0; i <= PARTICLE_COUNT; i++)
            {
                float angle = MathUtils.getRandomNumberInRange(facing - A_2,
                        facing + A_2);
                float vel = MathUtils.getRandomNumberInRange(speed * -VEL_MIN,
                        speed * -VEL_MAX);
                Vector2f vector = MathUtils.getPointOnCircumference(null,
                        vel,
                        angle);
                engine.addHitParticle(point,
                        vector,
                        PARTICLE_SIZE,
                        PARTICLE_BRIGHTNESS,
                        PARTICLE_DURATION,
                        PARTICLE_COLOR);
            }

        }        
    }   
}