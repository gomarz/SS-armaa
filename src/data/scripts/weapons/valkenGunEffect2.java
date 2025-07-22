package data.scripts.weapons;

import java.awt.Color;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.combat.*;
import org.lazywizard.lazylib.MathUtils;
import org.magiclib.util.MagicRender;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.combat.EmpArcEntityAPI;

public class valkenGunEffect2 implements OnHitEffectPlugin {
    
    private final Color PARTICLE_COLOR = new Color(255, 166, 239);
    private final float PARTICLE_SIZE = 4f;
    private final float PARTICLE_BRIGHTNESS = 150f;
    private final float PARTICLE_DURATION = 1f;
	private static final int PARTICLE_COUNT = 8;
	private final int max = 40;
	private final int min = 10;
    private final Color EMP_COLOR = new Color(255, 0, 255);
	    // constant that effects the lower end of the particle velocity
    private static final float VEL_MIN = 0.25f;
    // constant that effects the upper end of the particle velocity
    private static final float VEL_MAX = 0.35f;
	    // one half of the angle. used internally, don't mess with thos
	private static final float CONE_ANGLE = 150f;
    private static final float A_2 = CONE_ANGLE / 2;
	    private static final Color COLOR1 = new Color(255,0,0,255);
    private static final Color COLOR2 = new Color(245,188,44,255);
	    private static final Vector2f ZERO = new Vector2f();
	
	
    
    private final Color FLASH_COLOR = new Color(255, 255, 20);
    
    @Override
        public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine)  
	{
		
			float emp = projectile.getEmpAmount();
			float dam = projectile.getDamageAmount();
			
			if(MagicRender.screenCheck(0.2f, point)){
				if((float) Math.random() >= 0.50f)
				{
					engine.addSmoothParticle(point, ZERO, 150f * 1, 1, 0.75f, COLOR1);
					engine.addSmoothParticle(projectile.getLocation(), new Vector2f(0,0), 130, 2, 0.15f, Color.white);
					engine.addHitParticle(projectile.getLocation(), new Vector2f(0,0), 109, 1, 0.4f, new Color(200,100,25));
				    engine.spawnExplosion(projectile.getLocation(), new Vector2f(0,0), Color.BLACK, 60*2, 3);
					engine.spawnExplosion(projectile.getLocation(),new Vector2f(0,0), Color.blue, 50*2, 1f);
					
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
				
				for(int i = 0; i < 2; i++)
				{
					if((float) Math.random() > 0.75f && target instanceof ShipAPI && !shieldHit)
					{
					  EmpArcEntityAPI arc =   engine.spawnEmpArc(projectile.getSource(),
                            point,
							target,
                            target,
                            DamageType.ENERGY,
                            dam,
                            emp,
                            100000f,
                            "tachyon_lance_emp_impact",
                            15f,
                            EMP_COLOR,
                            FLASH_COLOR); 
							
					 arc = 	engine.spawnEmpArc(projectile.getSource(),
                            point,
							projectile,
                            projectile,
                            DamageType.ENERGY,
                            0f,
                            0f,
                            100000f,
                            "tachyon_lance_emp_impact",
                            10f,
                            EMP_COLOR,
                            FLASH_COLOR); 
							
					}
				}
				
    }   
}