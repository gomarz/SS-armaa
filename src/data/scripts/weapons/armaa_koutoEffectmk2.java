package data.scripts.weapons;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.AnimationAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.magiclib.util.MagicAnim;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;

/**
 *Base script by
 * Tartiflette
 * additional modifications by shoi
 */
public class armaa_koutoEffectmk2 extends BaseCombatLayeredRenderingPlugin implements EveryFrameWeaponEffectPlugin  {

    private boolean runOnce = false, lockNloaded = false;
    private ShipSystemAPI system;
    private ShipAPI ship;
	
    private SpriteAPI sprite;
    private AnimationAPI anim, aGlow, aGlow2;
    private WeaponAPI head, armL, armR, pauldronL, pauldronR, torso,vernier,gun, shoulderwep, headGlow;	
    private final Vector2f ZERO = new Vector2f();
    private int limbInit = 0;
	private float currentRotateL=0;
    private float currentRotateR=0;
	private final float maxlegRotate=22.5f;  
	private float reverse = 1f;
	private boolean windingup = false;
    private float overlap = 0, heat = 0, originalRArmPos = 0f, originalArmPos = 0f, originalShoulderPos = 0f, originalVernierPos = 0f;
    private final float TORSO_OFFSET = -45, LEFT_ARM_OFFSET = -60, RIGHT_ARM_OFFSET = -25, MAX_OVERLAP = 10, LPAULDRONOFFSET = -5;

    public void init()
    {
        runOnce = true;
        for (WeaponAPI w : ship.getAllWeapons()) {
            switch (w.getSlot().getId()) {
                case "A_GUN":
                    if(gun==null) {
                        gun = w;
						if(w.getBarrelSpriteAPI() != null)
						originalRArmPos = w.getBarrelSpriteAPI().getCenterY();
                        limbInit++;
                    }
                    break;
                case "C_ARML":
                    if(armL==null) {
                        armL = w;
						originalArmPos = armL.getSprite().getCenterY();
                        limbInit++;
                    }
                    break;
                case "C_ARMR":
                    if(armR==null) {
                        armR = w;
                        limbInit++;
                    }
                    break;
                case "D_PAULDRONL":
                    if(pauldronL == null) {
                        pauldronL = w;
						originalShoulderPos = pauldronL.getSprite().getCenterY();
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
				case "WS001":
                   		shoulderwep = w;
                    break;
				case "H_GLOW":
                   		headGlow = w;
                    break;
            }
        }
    }
    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{

        ship = weapon.getShip();
        sprite = ship.getSpriteAPI();
        system = ship.getSystem();
		
		if(!runOnce)
        init();
		if(gun == null) return;
		if(engine.isPaused())
			return;
        anim = gun.getAnimation();
		ship.syncWeaponDecalsWithArmorDamage();
		
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
        float ltarget=0;
        float rtarget=0;		
			if(ship.getEngineController().isTurningLeft()){          
				ltarget-=maxlegRotate/2;
				rtarget-=maxlegRotate/2;            
			} else if (ship.getEngineController().isTurningRight()){                      
				ltarget+=maxlegRotate/2;
				rtarget+=maxlegRotate/2;
			}
			
			float rtl = MathUtils.getShortestRotation(currentRotateL, ltarget);
			if (Math.abs(rtl)<0.5f){
				currentRotateL=ltarget;
			} else if (rtl>0) {
				currentRotateL+=0.4f;
			} else {
				currentRotateL-=0.4f;
			}
			
			float rtr = MathUtils.getShortestRotation(currentRotateR, rtarget);
			if (Math.abs(rtr)<0.5f){
				currentRotateR=rtarget;
			} else if (rtr>0) {
				currentRotateR+=0.4f;
			} else {
				currentRotateR-=0.4f;
			}


		float sineA = 0, sinceB = 0, sineC=0, sinceD=0, sinceG=0;	
		float global = ship.getFacing();
		float aim = MathUtils.getShortestRotation(global, gun.getCurrAngle());
		float aim2 = MathUtils.getShortestRotation(global, armL.getCurrAngle());
		boolean noanim = false;
			noanim  = true;
		if(armL != null && !noanim)
		{
			if(armL.getChargeLevel()<1)
			{
				sineA=MagicAnim.smoothNormalizeRange(armL.getChargeLevel(),0.5f,1f);
				sinceB=MagicAnim.smoothNormalizeRange(armL.getChargeLevel(),0.5f,1f);
			}
			else 
			{                
				sineA =1;
				sinceB =1;
			}

			
			armL.getSprite().setCenterY(originalArmPos-(16*sinceB)+(8*sinceG));
		}

		if (weapon != null)
		{
			if(armL != null)
			{
				if(!noanim)
				{
					weapon.setCurrAngle(global + (sineA * (TORSO_OFFSET) - (sinceG*TORSO_OFFSET) + ((sineC*(TORSO_OFFSET)))) + aim * 0.3f+currentRotateR);
					armL.setCurrAngle(armL.getCurrAngle()+(sineA*(TORSO_OFFSET/7)*.7f)-(sinceG*TORSO_OFFSET*.5f)+(sineC*(TORSO_OFFSET)));
				}
			
				else
				{
					weapon.setCurrAngle(global + (sineA * (TORSO_OFFSET) + aim * 0.3f)+currentRotateR);
				}		

				//armL angle
				//Arm animation 
			}				
		}

		if (armR != null)
		{
			armR.setCurrAngle(gun.getCurrAngle() + RIGHT_ARM_OFFSET  + ((sineC*(TORSO_OFFSET))));
//

		}			
//
		if (pauldronR != null)
		{
			pauldronR.setCurrAngle(global + sineA * (TORSO_OFFSET) - (sinceG*TORSO_OFFSET) + ((sineC*(TORSO_OFFSET))) * 0.5f + aim * 0.75f + RIGHT_ARM_OFFSET * 0.5f + currentRotateR* 0.75f);
			if(gun.getBarrelSpriteAPI() != null)
			pauldronR.getSprite().setCenterY(gun.getBarrelSpriteAPI().getCenterY()-40f);
			if(sineC > 0)
			{
				gun.setCurrAngle(gun.getCurrAngle() + sineC*TORSO_OFFSET*.70f);
			}
		}
	
		if (pauldronL != null)
		{
			if(armL != null)
			pauldronL.setCurrAngle(global + sineA * (TORSO_OFFSET) - (sinceG*TORSO_OFFSET) * 0.5f + ((sineC*(TORSO_OFFSET))) + aim2 * 0.75f - RIGHT_ARM_OFFSET * 0.5f +currentRotateL* 0.75f);
			pauldronL.getSprite().setCenterY(originalShoulderPos-(8*sinceB));
		}
		
		if(headGlow != null)
		{
			headGlow.setCurrAngle(head.getCurrAngle());
		}
    }
}
