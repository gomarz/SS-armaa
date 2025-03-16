package data.scripts.weapons;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.AnimationAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.magiclib.util.MagicAnim;
import java.awt.Color;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.FaderUtil;
import data.scripts.util.armaa_utils;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;

/**
 *Base script by
 * Tartiflette
 * additional modifications by shoi
 */
public class armaa_leynosEffect extends BaseCombatLayeredRenderingPlugin implements EveryFrameWeaponEffectPlugin  {

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
				case "WS002":
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
		String foo = (String)engine.getCustomData().get("armaa_corpseTracker_"+ship.getId());
		if(foo == null)
			engine.getCustomData().put("armaa_corpseTracker_"+ship.getId(),"-");
		if(gun == null) return;		
		if(ship.isHulk() && foo != null)
		{
			if(Math.random() > 0.50f)
				head.getSprite().setColor(new Color(0,0,0,0));
			if(Math.random() > 0.50f)
			{
				pauldronL.getSprite().setColor(new Color(0,0,0,0));
				armL.getSprite().setColor(new Color(0,0,0,0));
			}
			if(Math.random() > 0.50f)
			{
				pauldronR.getSprite().setColor(new Color(0,0,0,0));
				armR.getSprite().setColor(new Color(0,0,0,0));
				gun.getSprite().setColor(new Color(0,0,0,0));
				if(gun.getBarrelSpriteAPI() != null)
					gun.getBarrelSpriteAPI().setColor(new Color(0,0,0,0));					
			}
		}
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
		boolean noanim = true;
		
		if(armL.getSpec().getWeaponId().equals("armaa_leynosBoostKnuckle"))
			noanim  = false;
		if(armL != null)
		{
			if(armL.getAnimation() != null)
				if(armL.getAnimation().getNumFrames() > 1)
				{
					if(ship.getHullSpec().getHullId().equals("armaa_leynos_frig_comet"))
						armL.getAnimation().setFrame(1);
					else if(ship.getHullSpec().getHullId().equals("armaa_leynos_frig_lt"))
					{
						armL.getAnimation().setFrame(2);
					}
				}
			if(!noanim)	
			{
				if(armL.getChargeLevel()<1)
				{
					sineA=MagicAnim.smoothNormalizeRange(armL.getChargeLevel(),0.5f,1f);
					sinceB=MagicAnim.smoothNormalizeRange(armL.getChargeLevel(),0.5f,1f);
					//if(!windingup)
					sinceG=MagicAnim.smoothNormalizeRange(armL.getChargeLevel(),0.0f,0.33f)*1*reverse;
				}
				else 
				{                
					sineA =1;
					sinceB =1;
				}
				
				if(ship.getPhaseCloak() != null)
					if(ship.getPhaseCloak().getEffectLevel() > 0)
					{
						sineC=MagicAnim.smoothNormalizeRange(ship.getPhaseCloak().getEffectLevel(),0.0f,1f);
					}
				
				if(armL.getChargeLevel() > 0.33 && sinceG >0)
				{
						reverse-=0.04;
						
				}
				
				if(armL.getChargeLevel() <= 0)
					reverse = 1f;
				armL.getSprite().setCenterY(originalArmPos-(16*sinceB)+(8*sinceG));
			}
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
					//armL.setCurrAngle(armL.getCurrAngle()+(sineC*(TORSO_OFFSET)));
					weapon.setCurrAngle(global + (sineA * (TORSO_OFFSET) + aim * 0.3f)+currentRotateR+(sineC*(TORSO_OFFSET)));
				}		

				//armL angle
				
			}				
		}

		if(gun != null)
		{
			if(gun.getAnimation() != null)
				if(gun.getAnimation().getNumFrames() > 1 && ship.getHullSpec().getHullId().equals("armaa_leynos_frig_comet"))
				{
					gun.getAnimation().setFrame(1);
				}
		}
		if (armR != null)
		{
			armR.setCurrAngle(gun.getCurrAngle() + RIGHT_ARM_OFFSET  + ((sineC*(TORSO_OFFSET))));
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
				if(shoulderwep != null)
				shoulderwep.setCurrAngle(shoulderwep.getCurrAngle() + sineC*TORSO_OFFSET*.70f);
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
