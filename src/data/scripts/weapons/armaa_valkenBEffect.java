package data.scripts.weapons;
import com.fs.starfarer.api.AnimationAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.magiclib.util.MagicAnim;
import java.awt.Color;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.util.Misc;
import org.magiclib.util.MagicFakeBeam;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;

/**
 *
 * @author Tartiflette
 */
public class armaa_valkenBEffect implements EveryFrameWeaponEffectPlugin,OnFireEffectPlugin, OnHitEffectPlugin {

    private boolean runOnce = false, lockNloaded = false;
    private ShipSystemAPI system;
    private ShipAPI ship;
    private SpriteAPI sprite;
    private AnimationAPI anim, aGlow, aGlow2;
    private WeaponAPI head, armL, armR, pauldronL, pauldronR, torso, wGlow, hGlow, cannon;

    private float delay = 0.1f;
    private float timer = 0;
    private int lastWeaponAmmo = 0;
	private float swingLevel = 0f;
	private float swingLevel2 = 0f;
	private boolean swinging = false;
		private float reverse = 1f;
	private boolean cooldown = false;
    private static final Vector2f ZERO = new Vector2f();
	private IntervalUtil animInterval = new IntervalUtil(0.012f, 0.012f);
    public float TURRET_OFFSET = 30f;
    private int limbInit = 0;
	private static final Color MUZZLE_FLASH_COLOR = new Color(100, 200, 255, 50);
    private static final Color MUZZLE_FLASH_COLOR_ALT = new Color(100, 100, 255, 100);
    private static final Color MUZZLE_FLASH_COLOR_GLOW = new Color(255, 255, 255, 50);
    private static final float MUZZLE_FLASH_DURATION = 0.15f;
    private static final float MUZZLE_FLASH_SIZE = 10.0f;

    private float overlap = 0, heat = 0,originalRArmPos = 0f;
    private final float TORSO_OFFSET = -45, LEFT_ARM_OFFSET = -60, RIGHT_ARM_OFFSET = -25, MAX_OVERLAP = 10;

    public void init()
    {
        runOnce = true;
		
	//ship.setCollisionClass(CollisionClass.FIGHTER);
        for (WeaponAPI w : ship.getAllWeapons()) {
            switch (w.getSlot().getId()) {
                case "B_TORSO":
                    if(torso==null) {
                        torso = w;
                        limbInit++;
                    }
                    break;
                case "C_ARML":
                    if(armL==null) {
                        armL = w;
                        limbInit++;
                    }
                    break;
                case "C_ARMR":
                    if(armR==null) {
                        armR = w;
                        limbInit++;
						originalRArmPos = armR.getSprite().getCenterY();
                    }
                    break;
                case "D_PAULDRONL":
                    if(pauldronL == null) {
                        pauldronL = w;
                        limbInit++;
                    }
                    break;
                case "D_PAULDRONR":
                    if(pauldronR == null) {
                        pauldronR = w;
                        limbInit++;
                    }
                    break;
                case "E_HEAD":
                    if(head == null) {
                        head = w;
                        limbInit++;
                    }
                    break;
                case "A_GUN":
                    //originalRArmPos = w.getSprite().getCenterY();
                    break;
            }
        }

    }
    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {

        ship = weapon.getShip();
        sprite = ship.getSpriteAPI();
        system = ship.getSystem();
        anim = weapon.getAnimation();

        init();

		if (ship.getEngineController().isAccelerating()) {
			if (overlap > (MAX_OVERLAP - 0.1f)) {
				overlap = MAX_OVERLAP;
			} else {
				overlap = Math.min(MAX_OVERLAP, overlap + ((MAX_OVERLAP - overlap) * amount * 5));
			}
		} else if (ship.getEngineController().isDecelerating() || ship.getEngineController().isAcceleratingBackwards()) {
			if (overlap < -(MAX_OVERLAP - 0.1f)) {
				overlap = -MAX_OVERLAP;
			} else {
				overlap = Math.max(-MAX_OVERLAP, overlap + ((-MAX_OVERLAP + overlap) * amount * 5));
			}
		} else {
			if (Math.abs(overlap) < 0.1f) {
				overlap = 0;
			} else {
				overlap -= (overlap / 2) * amount * 3;
			}
		}



		float global = ship.getFacing();
		float aim = MathUtils.getShortestRotation(global, weapon.getCurrAngle());
		float sineA = 0, sinceB = 0, sineC=0, sinceD=0, sinceG=0;
		
		if(weapon.getChargeLevel()<1)
		{
			sineA=MagicAnim.smoothNormalizeRange(weapon.getChargeLevel(),0.25f,1f);
			sinceB=MagicAnim.smoothNormalizeRange(weapon.getChargeLevel(),0.25f,1f);
			sinceG=MagicAnim.smoothNormalizeRange(weapon.getChargeLevel(),0.0f,0.25f)*1*reverse;
		} 
		else 
		{                
			sineA =1;
			sinceB =1;
		}
   
			sineC =0;

		if(weapon.getChargeLevel() > 0.33 && sinceG >0)
		{
				reverse-=amount+0.08;
				
		}
		
		if(weapon.getChargeLevel() <= 0)
			reverse = 1f;
		weapon.getSprite().setCenterY(originalRArmPos-(8*sinceB)+(8*sinceG));
		if (torso != null)
			torso.setCurrAngle(global + (sineA * (-TORSO_OFFSET) + (sinceG*TORSO_OFFSET) + aim * 0.3f));

//
		if(weapon.getCooldownRemaining() <= 0f && !weapon.isFiring())
			cooldown = false;
		
            if (weapon != null) {
                weapon.setCurrAngle(weapon.getCurrAngle()-(sineA*(TORSO_OFFSET/7)*.7f)+(sinceG*TORSO_OFFSET*.5f));
            }
		if(weapon.getChargeLevel() >= 1f)
		{
			swinging = true;
		}
		
		if(swinging == true && (weapon.getChargeLevel() <= 0f))
		{
			swinging = false;
			swingLevel = 0f;
			cooldown = true;
		}
		
		if(animInterval.intervalElapsed())
		{
			swingLevel-=0.5;
			swingLevel2+=0.5;
		}

		if(swingLevel > 9)
			swingLevel = 9;
		if(swinging == false)
		{
			swingLevel = 0f;
		}
//
		if (armR != null)
			armR.setCurrAngle(weapon.getCurrAngle() + RIGHT_ARM_OFFSET);

		if (pauldronR != null)
			pauldronR.setCurrAngle(global + sineA * (-TORSO_OFFSET) + (sinceG*TORSO_OFFSET) * 0.5f + aim * 0.75f + RIGHT_ARM_OFFSET * 0.5f);
			//pauldronR.setCurrAngle(global + sineA * TORSO_OFFSET * 0.5f + aim * 0.75f + RIGHT_ARM_OFFSET * 0.5f);
	
            if (armL != null) {
                armL.setCurrAngle(
                        global
                                +
                                ((aim + LEFT_ARM_OFFSET) * sinceB)
                                +
                                ((overlap + aim * 0.25f) * (1 - sinceB))
                );
            }

		if (pauldronL != null)
			pauldronL.setCurrAngle(torso.getCurrAngle() + MathUtils.getShortestRotation(torso.getCurrAngle(), armL.getCurrAngle()) * 0.6f);
		if (wGlow != null)
			wGlow.setCurrAngle(weapon.getCurrAngle());

		if (hGlow != null) {
			hGlow.setCurrAngle(head.getCurrAngle());
		}

            
	}
	public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine)  
	{
		
	}
	
	public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) 
	{
            Vector2f origin = new Vector2f(weapon.getLocation());
            Vector2f offset = new Vector2f(TURRET_OFFSET, -15f);
            VectorUtils.rotate(offset, weapon.getCurrAngle(), offset);
            Vector2f.add(offset, origin, origin);
            float shipFacing = weapon.getCurrAngle();
            Vector2f shipVelocity = weapon.getShip().getVelocity();
			shipVelocity = MathUtils.getPointOnCircumference(ship.getVelocity(), (float) Math.random() * 20f, weapon.getCurrAngle() + 90f
					- (float) Math.random()
					* 180f);
									if (Math.random() > 0.75) {
				engine.spawnExplosion(origin, shipVelocity, MUZZLE_FLASH_COLOR_ALT, MUZZLE_FLASH_SIZE * 0.5f, MUZZLE_FLASH_DURATION);
			} else {
				engine.spawnExplosion(origin, shipVelocity, MUZZLE_FLASH_COLOR, MUZZLE_FLASH_SIZE, MUZZLE_FLASH_DURATION);
			}
			engine.addSmoothParticle(origin, shipVelocity, MUZZLE_FLASH_SIZE * 3f, 1f, MUZZLE_FLASH_DURATION * 2f, MUZZLE_FLASH_COLOR_GLOW);
			
			//engine.removeEntity(projectile);
	}     		
}
