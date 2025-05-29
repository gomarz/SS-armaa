package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import org.lazywizard.lazylib.MathUtils;
    


public class armaa_gareggaEffect implements EveryFrameWeaponEffectPlugin{    

    private boolean runOnce=false;
    private WeaponAPI reference;
    private ShipAPI ship;
	private ShipwideAIFlags flags;
	private WeaponAPI armL, armR, shoulderR, head, headGlow, shoulderL,turretL,turretR;
    private float overlap=0, overlap2=0, overlap3=0;
	private float currentRotateL=0;
    private float currentRotateR=0;
	private final float TORSO_OFFSET = -45, LEFT_ARM_OFFSET = -75, RIGHT_ARM_OFFSET = -25, MAX_OVERLAP = 7, maxlegRotate=22.5f;  

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
					case "E_LSHOULDER":
						shoulderL = w;
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
					
					case "WS 005":
                    if(head == null) {
                        turretR = w;
                    }
                    break;
					
					case "WS 006":
                    if(head == null) {
                        turretL = w;
                    }
                    break;
					//break;
				}
            }
        }
        
        if (armL==null) {
            return;
        }
		
	
		//-----
        
        float sineA=0, sinceB=0;   
		
		float global=ship.getFacing();
        float aim=MathUtils.getShortestRotation(global, weapon.getCurrAngle());
		//*End animation math stuff
		
			
		//Right arm + shoulder
        weapon.setCurrAngle(ship.getFacing() + MathUtils.getShortestRotation(ship.getFacing(),armL.getCurrAngle())*0.7f);
		//turretL.setCurrAngle(ship.getFacing() + MathUtils.getShortestRotation(ship.getFacing(),armL.getCurrAngle())*0.7f);
		if(armR != null)
		{
			shoulderR.setCurrAngle(ship.getFacing() + MathUtils.getShortestRotation(ship.getFacing(),armR.getCurrAngle())*0.7f);
			//turretR.setCurrAngle(ship.getFacing() + MathUtils.getShortestRotation(ship.getFacing(),armR.getCurrAngle())*0.7f);
			shoulderR.getSprite().setCenterY(armR.getBarrelSpriteAPI().getCenterY());
		}

        weapon.getSprite().setCenterY(armL.getBarrelSpriteAPI().getCenterY());
		//turretR.getSprite().setCenterY(armR.getBarrelSpriteAPI().getCenterY());  

		//headGlow.setCurrAngle(head.getCurrAngle());
		
		ship.syncWeaponDecalsWithArmorDamage();
		//Eye glow
		/*
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
		*/

    }
}

