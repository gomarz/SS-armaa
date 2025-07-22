package data.scripts.weapons;

import java.awt.Color;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.combat.*;
import org.magiclib.util.MagicRender;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;

public class armaa_mutilatorEffect implements OnHitEffectPlugin {
    
    private final Color PARTICLE_COLOR = new Color(255, 0, 0);
    private final float PARTICLE_SIZE = 2f;
    private final float PARTICLE_BRIGHTNESS = 115f;
    private final float PARTICLE_DURATION = 1f;
	private final int max = 40;
	private final int min = 10;
    private final Color EMP_COLOR = new Color(250, 236, 40);
	    // constant that effects the lower end of the particle velocity
    private static final float VEL_MIN = 0.25f;
    // constant that effects the upper end of the particle velocity
    private static final float VEL_MAX = 0.35f;
	    // one half of the angle. used internally, don't mess with thos
	private static final float CONE_ANGLE = 150f;
    private static final float A_2 = CONE_ANGLE / 2;
	private static final float PUSH_CONSTANT = 300f;
	
    
    private final Color FLASH_COLOR = new Color(255, 10, 20);
    
    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) 
	{
	
			float emp = projectile.getEmpAmount();
			float dam = projectile.getDamageAmount();
			
			if(MagicRender.screenCheck(0.2f, point)){
				if((float) Math.random() >= 0.50f)
				{
					engine.addSmoothParticle(projectile.getLocation(), new Vector2f(0,0), projectile.getDamageAmount(), 2, 0.15f, Color.white);
					engine.addHitParticle(projectile.getLocation(), new Vector2f(0,0), 100, 1, 0.35f, new Color(200,100,25));
					engine.spawnExplosion(projectile.getLocation(), new Vector2f(0,0), Color.BLACK, 60*2, 3);
					
				}
				
			}
							
    }   
}