package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.WeaponAPI;
import org.lazywizard.lazylib.MathUtils;
import java.awt.Color;
import java.util.List;
    


public class armaa_trihanderEffect implements EveryFrameWeaponEffectPlugin{    

    private boolean runOnce=false;
    private WeaponAPI reference;
    private ShipAPI ship;
	private WeaponAPI armL, armR, shoulderR, head, headGlow;
    private float overlap=0, overlap2 = 0;
	private final float TORSO_OFFSET = -45, LEFT_ARM_OFFSET = -75, RIGHT_ARM_OFFSET = -25, MAX_OVERLAP = 10;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        
        if(!runOnce)
		{
            runOnce=true;
            ship=weapon.getShip();
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
					//break;
				}
            }
        }
        
        if (armL==null) {
            return;
        }
		
		List<ShipAPI> children = ship.getChildModulesCopy();
		
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
        } 
		else {
            if(Math.abs(overlap)<0.1f){
                overlap=0;   
            }else{
                overlap-=(overlap/2)*amount*3;   
            }
        }

		if(ship.getEngineController().isStrafingLeft())
		{
			
			if(overlap2>(MAX_OVERLAP-0.1f)){
				overlap2=MAX_OVERLAP;
            } else {
                overlap2=Math.min(MAX_OVERLAP, overlap2 +((MAX_OVERLAP-overlap2)*amount*5));
            }
		}
			
        float sineA=0, sinceB=0;   
		
		float global=ship.getFacing();
        float aim=MathUtils.getShortestRotation(global, weapon.getCurrAngle());
		
        armL.setCurrAngle(
                        global
                        +  
                        ((aim+LEFT_ARM_OFFSET)*sinceB)
                        +
                        ((overlap+aim*0.25f)*(1-sinceB))
        );
		
				if(children != null)
				for(ShipAPI m: children)
				{
					m.ensureClonedStationSlotSpec();
					if( m.getStationSlot() != null)
					{
						if( m.getStationSlot().getId().equals("WS0001"))
							m.setFacing(global
							-   
							((aim+LEFT_ARM_OFFSET)*sinceB)
							-
							((overlap+overlap2+aim*0.25f)*(1-sinceB)));
						
						else if( m.getStationSlot().getId().equals("WS0002"))
						m.setFacing(armL.getCurrAngle());
					}

						
				}
		
        weapon.setCurrAngle(ship.getFacing() + MathUtils.getShortestRotation(ship.getFacing(),armL.getCurrAngle())*0.7f);
		shoulderR.setCurrAngle(ship.getFacing() + MathUtils.getShortestRotation(ship.getFacing(),armR.getCurrAngle())*0.7f);
        shoulderR.getSprite().setCenterY(armR.getBarrelSpriteAPI().getCenterY());       
			   // pauldronR.setCurrAngle(global + sineA * TORSO_OFFSET * 0.5f + aim * 0.75f + RIGHT_ARM_OFFSET * 0.5f);

		headGlow.setCurrAngle(head.getCurrAngle());
		
		ship.syncWeaponDecalsWithArmorDamage();
		

		
		if(!ship.isHulk())
		{
			headGlow.getSprite().setAlphaMult(2);
			headGlow.getSprite().setColor(new Color(255,0,0,255));
		}
		else
		{
			headGlow.getSprite().setColor(new Color(0,0,0,255));
		}

    }
}

