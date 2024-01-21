package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import org.lazywizard.lazylib.MathUtils;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.combat.ai.BasicShipAI;
import data.scripts.ai.armaa_combat_docking_AI;
import java.awt.Color;
import java.util.List;
import org.magiclib.util.MagicAnim;
    


public class armaa_altagraveEffect implements EveryFrameWeaponEffectPlugin{    

    private boolean runOnce=false;
    private WeaponAPI reference;
    private ShipAPI ship;
	private ShipwideAIFlags flags;
	private WeaponAPI armL, armR, shoulderR, head, headGlow, module1, module2;
    private float overlap=0, overlap2=0, overlap3=0;
	private float currentRotateL=0;
    private float currentRotateR=0;
	private float sway = 0f;
	private final float TORSO_OFFSET = -45, LEFT_ARM_OFFSET = -75, RIGHT_ARM_OFFSET = -25, MAX_OVERLAP = 7, maxlegRotate=22.5f;  
	private float swingLevel = 0f;
	private boolean windingUp = false;
	private boolean swinging = false;
	private boolean cooldown = false;
	private IntervalUtil animInterval = new IntervalUtil(0.012f, 0.012f);
	private boolean lostmodules = false;
    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
  
        if(!runOnce)
		{
            runOnce=true;
            ship=weapon.getShip();
			flags = ship.getAIFlags();
            for(WeaponAPI w : ship.getAllWeapons())
			{
				switch (w.getSlot().getId()) 
				{
					case "E_LARM":
                    if(armL==null) 
					{
                        armL = w;
                    }
					break;
					case "E_RARM":
					if(armR==null)
					{
						armR= w;
					}
					break;
					case "E_RSHOULDER":
						shoulderR = w;
						break;
						
					case "E_HEAD_GLOW":
                    if(headGlow == null) {
                        headGlow = w;
                    }
                    break;
					
					case "E_HEAD":
                    if(head == null) {
                        head = w;
                    }
                    break;
					case "WS0005":
                    if(module1 == null) {
                        module1 = w;
                    }
                    break;
					case "WS0007":
                    if(module2 == null) {
                        module2 = w;
                    }
                    break;
					//break;
				}
            }
        }
        
        if (armL==null) {
            return;
        }
		
		//*Animation math stuff. I wonder if there is a cleaner way to do this
		if(ship.getEngineController().isAccelerating()){
            if(overlap>(MAX_OVERLAP-0.1f)){
                overlap=MAX_OVERLAP;
            } else {
                overlap=Math.min(MAX_OVERLAP, overlap +((MAX_OVERLAP-overlap)*amount*5));
            }
			
        } else if(ship.getEngineController().isDecelerating()|| ship.getEngineController().isAcceleratingBackwards()){         
            if(overlap<-(MAX_OVERLAP-0.1f)){
                overlap=-MAX_OVERLAP;
            } else {   
                overlap=Math.max(-MAX_OVERLAP, overlap +((-MAX_OVERLAP+overlap)*amount*5));
            }
        } else {
            if(Math.abs(overlap)<0.1f){
                overlap=0;   
            }else{
                overlap-=(overlap/2)*amount*3;   
            }
        }
		
		if(ship.getEngineController().isStrafingLeft() || ship.getEngineController().isTurningLeft())
		{
			
			if(overlap2>(MAX_OVERLAP-0.1f)){
				overlap2=MAX_OVERLAP;
            } else {
                overlap2=Math.min(MAX_OVERLAP, overlap2 +((MAX_OVERLAP-overlap2)*amount*5));
            }
		}
		
		else {
            if(Math.abs(overlap2)<0.1f){
                overlap2=0;   
            }else{
                overlap2-=(overlap2/2)*amount*3;   
            }
        }
		
		if(ship.getEngineController().isStrafingRight() || ship.getEngineController().isTurningRight())
		{
			
			if(overlap3>(MAX_OVERLAP-0.1f)){
				overlap3=MAX_OVERLAP;
            } else {
                overlap3=Math.min(MAX_OVERLAP, overlap3 +((MAX_OVERLAP-overlap3)*amount*5));
            }
		}
		
		else {
            if(Math.abs(overlap3)<0.1f){
                overlap3=0;   
            }else{
                overlap3-=(overlap3/2)*amount*3;   
            }
        }
		
		//More code borrowed from tart since I cannot into math
		//ship.getEngineController() LEG MOVEMENT ROTATIONS     
        
        float ltarget=0;
        float rtarget=0;
        float sineA=0, sinceB=0;
            float sineC = 0;
	
		if(armL.getChargeLevel()<1)
		{
			sineC=MagicAnim.smoothNormalizeRange(armL.getChargeLevel(),0.3f,0.9f);
		} 
		else 
		{                
			sineC =1;
		}		
		
		if(!engine.isPaused())
		{
			if(armL != null && armL.getId().equals("armaa_altagrave_blade"))
			{
				
				//armL.getSprite().setCenterY(originalArmPos-(16*sinceB));

				if(armL.getCooldownRemaining() <= 0f && !armL.isFiring())
					cooldown = false;
				
				if(!swinging && !cooldown && armL.getChargeLevel() > 0f)
				{
					armL.setCurrAngle(armL.getCurrAngle() + (sineC * TORSO_OFFSET*0.30f) *armL.getChargeLevel());
				}
				if(armL.getChargeLevel() >= 1f)
				{
					swinging = true;
				}
				
				if(swinging && armL.getCurrAngle() != armL.getShip().getFacing()+45f)
				{
					animInterval.advance(amount);
					armL.setCurrAngle((float)Math.min(armL.getCurrAngle()+swingLevel,armL.getCurrAngle()+armL.getArc()/2));
				}
				
				if(swinging == true && (armL.getChargeLevel() <= 0f))
				{
					swinging = false;
					swingLevel = 0f;
					cooldown = true;
				}
				
				if(animInterval.intervalElapsed())
				{
					swingLevel+=0.5;
				}

				if(swingLevel > 9)
					swingLevel = 9;
				if(swinging == false)
				{
					swingLevel = 0f;
				}
				//if(swinging)
				//ship.setFacing(ship.getFacing()+40f*armL.getChargeLevel());
			}
			
			if(ship.getEngineController().isAccelerating()){
				ltarget+=maxlegRotate/2; //=Math.min(maxlegRotate, ltarget +((maxlegRotate-overlap)*amount*5));
				rtarget-=maxlegRotate/2;
				if(sway > -1)
					sway-=0.08;
				
			} else if (ship.getEngineController().isDecelerating()|| ship.getEngineController().isAcceleratingBackwards()){            
				ltarget-=maxlegRotate/2;
				rtarget+=maxlegRotate/2;
				if(sway < 1)
					sway+=0.08;
			}
			if(ship.getEngineController().isStrafingLeft()){            
				ltarget+=maxlegRotate/3;
				rtarget+=maxlegRotate/1.5f;
			} else if (ship.getEngineController().isStrafingRight()){            
				ltarget-=maxlegRotate/1.5f;
				rtarget-=maxlegRotate/3;
			}
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
			
			if(sway > 0)
				sway-=0.05;
			else
				sway+= 0.05;
		}
		
		float global=ship.getFacing();
        float aim=MathUtils.getShortestRotation(global, weapon.getCurrAngle());
		//*End animation math stuff
		
		//Animate Left arm and legs
		if(!ship.getVariant().getHullSpec().getHullId().equals("armaa_altagrave_ex") && !armL.getId().equals("armaa_altagrave_blade"))
		{	
			armL.setCurrAngle(
							global
							+  
							((aim+LEFT_ARM_OFFSET)*sinceB)
							+
							((overlap+overlap3-overlap2+aim*0.25f)*(1-sinceB))
			);
		}

		
		List<ShipAPI> children = ship.getChildModulesCopy();
		if(children != null && !lostmodules)
			for(ShipAPI m: children)	
			{
				m.ensureClonedStationSlotSpec();

				if( m.getStationSlot() != null)
				{
					boolean playerShip = Global.getCombatEngine().getPlayerShip() == ship ? true : false;
					float threshold = playerShip ? .50f : .70f; 
					if(ship.getHullLevel() <= threshold)
					{
						m.getFluxTracker().showOverloadFloatyIfNeeded("Emergency Purge!", Color.yellow, 4f, true);
						
						m.setStationSlot(null);
						//m.setHitpoints(1f);
						//m.setShipAI(new armaa_combat_docking_AI(m));
						continue;
					}
					if( m.getStationSlot().getId().equals("WS0001"))
					{
						m.setFacing(ship.getFacing()+currentRotateR);
						m.getSpriteAPI().setCenter(67,79 - (5*sway));
					}
					
					else if( m.getStationSlot().getId().equals("WS0002"))
					{
						m.setFacing(ship.getFacing()+currentRotateL);
						m.getSpriteAPI().setCenter(41,79 - (5*sway));
					}
				}
				if(m.getHullSpec().getHullName().equals("Backpack Module") && m.getStationSlot() == null)
				{
					if(module1 != null)
					{
						module1.disable(true);
						module1.getSprite().setColor(new Color(0,0,0,0));
						ship.removeWeaponFromGroups(module1);
					}
					if(module2 != null)
					{
						module2.disable(true);
						module2.getSprite().setColor(new Color(0,0,0,0));
						ship.removeWeaponFromGroups(module2);
					}
					
					lostmodules = true;
				}					
			}
		
		//Right arm + shoulder
        weapon.setCurrAngle(ship.getFacing() + MathUtils.getShortestRotation(ship.getFacing(),armL.getCurrAngle())*0.7f);
		shoulderR.setCurrAngle(ship.getFacing() + MathUtils.getShortestRotation(ship.getFacing(),armR.getCurrAngle())*0.7f);
        shoulderR.getSprite().setCenterY(armR.getBarrelSpriteAPI().getCenterY());       

		headGlow.setCurrAngle(head.getCurrAngle());
		
		ship.syncWeaponDecalsWithArmorDamage();
		//Eye glow
		if(!ship.isHulk())
		{
			headGlow.getSprite().setAlphaMult(1.3f);
			if(ship.getVariant().getHullSpec().getHullId().equals("armaa_altagrave"))
			{
				headGlow.getSprite().setColor(new Color(255,255,150,255));
				headGlow.getSprite().setAlphaMult(1.3f);
				
			}
			
			else if(ship.getVariant().getHullSpec().getHullId().equals("armaa_altagrave_c"))
			{
				headGlow.getSprite().setColor(new Color(0,50,150,200));
				headGlow.getSprite().setAlphaMult(1f);
			}
		
			else
			{
				headGlow.getSprite().setColor(new Color(155,0,0,200));
				headGlow.getSprite().setAlphaMult(1f);
			}
		}
		else
		{
			headGlow.getSprite().setColor(new Color(0,0,0,255));
		}

    }
}

