package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import org.lazywizard.lazylib.MathUtils;
import org.magiclib.util.MagicRender;
import java.awt.Color;
import java.util.List;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.graphics.SpriteAPI;
    
import org.lwjgl.input.Keyboard;

public class armaa_watchdogEffect implements EveryFrameWeaponEffectPlugin{    

    private boolean runOnce=false;
    private WeaponAPI reference;
    private ShipAPI ship;
	private ShipwideAIFlags flags;
	private WeaponAPI armL, armR, shoulderR, head, headGlow, shoulderL,turretL,turretR,decoL,decoR;
    private float overlap=0, overlap2=0, overlap3=0;
	private float currentRotateL=0;
    private float currentRotateR=0;
	private float originalShoulderPos = 0, originalShoulderPosL;
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
					case "A_GUN2":
					if(armR==null)
					{
						armR= w;
					}
					break;
					case "E_RSHOULDER":
						shoulderR = w;
						originalShoulderPos = shoulderR.getSprite().getCenterY();  
						break;
					case "E_LSHOULDER":
						shoulderL = w;
						originalShoulderPosL = shoulderL.getSprite().getCenterY();  
						break;					
					case "WS0003":
                        turretR = w;
                    break;
					
					case "WS0001":
						if(turretL == null)
                        turretL = w;
                    break;
					case "E_DECO":
						if(decoL == null)
                        decoL = w;
                    break;
					case "E_DECO_R":
                        decoR = w;
                    break;
					//break;
				}
            }
        }
        
        if (armL==null) {
            return;
        }
		
        float sineA=0, sinceB=0;   
		
		float global=ship.getFacing();
        float aim=MathUtils.getShortestRotation(global, weapon.getCurrAngle());
		//*End animation math stuff
		
			
		//Left arm + shoulder
        //weapon.setCurrAngle(ship.getFacing() + MathUtils.getShortestRotation(ship.getFacing(),armL.getCurrAngle())*0.7f);
		//weapon.getSprite().setCenterY(originalShoulderPos+(2*armL.getCooldownRemaining()/armL.getCooldown()));
		//turretL.setCurrAngle(ship.getFacing() + MathUtils.getShortestRotation(ship.getFacing(),armL.getCurrAngle())*0.7f);
		if(armR != null)
		{
			shoulderR.setCurrAngle(ship.getFacing() + MathUtils.getShortestRotation(ship.getFacing(),armR.getCurrAngle())*0.7f);
			shoulderR.getSprite().setCenterY(originalShoulderPos+(2*armR.getCooldownRemaining()/armR.getCooldown()));
		}

		if(decoL != null && turretL != null)
		{
			decoL.setCurrAngle(turretL.getCurrAngle());
		}
		
		if(decoR != null && turretR != null)
		{
			decoR.setCurrAngle(turretR.getCurrAngle());
		}

		ship.syncWeaponDecalsWithArmorDamage();

    }
}

