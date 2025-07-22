package data.scripts.weapons;

import java.awt.Color;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.combat.*;
import org.lazywizard.lazylib.MathUtils;
import org.magiclib.util.MagicRender;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;

public class armaa_shotgunEffect implements OnHitEffectPlugin {
    
    private final Color PARTICLE_COLOR = new Color(255, 0, 0);
    private final float PARTICLE_SIZE = 4f;
    private final float PARTICLE_BRIGHTNESS = 150f;
    private final float PARTICLE_DURATION = 1f;
	private static final int PARTICLE_COUNT = 1;
	private final int max = 40;
	private final int min = 10;
    private final Color EMP_COLOR = new Color(250, 236, 40);
	    // constant that effects the lower end of the particle velocity
    private static final float VEL_MIN = 0.1f;
    // constant that effects the upper end of the particle velocity
    private static final float VEL_MAX = 0.15f;
	    // one half of the angle. used internally, don't mess with thos
	private static final float CONE_ANGLE = 150f;
    private static final float A_2 = CONE_ANGLE / 2;
	private static final float PUSH_CONSTANT = 300f;
	
    
    private final Color FLASH_COLOR = new Color(255, 10, 20);
    
    @Override
        public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine)  
	{
		
		  float targetMass;
        if (target instanceof ShipAPI) {
            targetMass = ((ShipAPI) target).getMassWithModules();
        } else {
            targetMass = target.getMass();
        }
        if (targetMass == 0f) targetMass = 1f;
        Vector2f dir = Vector2f.sub(target.getLocation(), projectile.getLocation(), new Vector2f());
        if (dir.lengthSquared() > 0f) dir.normalise();
        float pushYou = PUSH_CONSTANT / targetMass;
        Vector2f.add((Vector2f)dir.scale(pushYou),target.getVelocity(),target.getVelocity());
    
		
			float emp = projectile.getEmpAmount();
			float dam = projectile.getDamageAmount();
			
			if(MagicRender.screenCheck(0.2f, point)){
				if((float) Math.random() >= 0.90f)
				{
					engine.addSmoothParticle(projectile.getLocation(), new Vector2f(0,0), 200, 2, 0.15f, Color.white);
					engine.addHitParticle(projectile.getLocation(), new Vector2f(0,0), 100, 1, 0.4f, new Color(200,100,25));
					engine.spawnExplosion(projectile.getLocation(), new Vector2f(0,0), Color.DARK_GRAY, 125, 1);
					engine.spawnExplosion(projectile.getLocation(), new Vector2f(0,0), Color.BLACK, 60*5, 2);
					
				}
				
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
                        EMP_COLOR);
            }
			
			}
				
				
    }   
}