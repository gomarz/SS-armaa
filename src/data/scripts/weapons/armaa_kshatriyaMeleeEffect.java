package data.scripts.weapons;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.AnimationAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.magiclib.util.MagicAnim;
import java.awt.Color;
import java.util.List;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.FaderUtil;
import data.scripts.util.armaa_utils;

public class armaa_kshatriyaMeleeEffect implements EveryFrameWeaponEffectPlugin {

    private boolean runOnce = false, lockNloaded = false;
    private ShipSystemAPI system;
    private ShipAPI ship;
    private SpriteAPI sprite;
    private AnimationAPI anim;
	private float currentRotateL=0;
    private float currentRotateR=0;
	private float sway = 0f;
	private final float maxbinderrotate=40f;  
    private WeaponAPI head, armL, armL2, armR, pauldronL, pauldronR, torso,vernier,gun, shoulderwep, headGlow;	
    private final Vector2f ZERO = new Vector2f();
    private int limbInit = 0;
	private int numModules = 4;
	private float swingLevelR = 0f;
	private boolean swingingR = false;
	private boolean cooldownR = false;
	private IntervalUtil animInterval = new IntervalUtil(0.012f, 0.012f);

    private float overlap = 0, heat = 0, originalRArmPos = 0f, originalArmPos = 0f, originalArmPos2 = 0f, originalShoulderPos = 0f, originalVernierPos = 0f;
    private final float TORSO_OFFSET = -45, LEFT_ARM_OFFSET = -60, RIGHT_ARM_OFFSET = -25, MAX_OVERLAP = 10, LPAULDRONOFFSET = -5;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{

        ship = weapon.getShip();
        sprite = ship.getSpriteAPI();
        system = ship.getSystem();
		
		if(engine.isPaused())
			return;
        
        float ltarget=0;
        float rtarget=0;

		if(ship.getEngineController().isAccelerating()){
			ltarget+=maxbinderrotate/2; //=Math.min(maxbinderrotate, ltarget +((maxbinderrotate-overlap)*amount*5));
			rtarget-=maxbinderrotate/2;
			if(sway > -1)
				sway-=0.08;
			
		} else if (ship.getEngineController().isDecelerating()|| ship.getEngineController().isAcceleratingBackwards()){            
			ltarget-=maxbinderrotate/2;
			rtarget+=maxbinderrotate/2;
			if(sway < 1)
				sway+=0.08;
		}
		if(ship.getEngineController().isStrafingLeft()){            
			ltarget+=maxbinderrotate/3;
			rtarget+=maxbinderrotate/1.5f;
		} else if (ship.getEngineController().isStrafingRight()){            
			ltarget-=maxbinderrotate/1.5f;
			rtarget-=maxbinderrotate/3;
		}
		if(ship.getEngineController().isTurningLeft()){          
			ltarget-=maxbinderrotate/2;
			rtarget-=maxbinderrotate/2;            
		} else if (ship.getEngineController().isTurningRight()){                      
			ltarget+=maxbinderrotate/2;
			rtarget+=maxbinderrotate/2;
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

		float sineA = 0, sinceB = 0, sineC=0, sinceD=0;	

		if (weapon != null)
		{
			//weapon.setCurrAngle(weapon.getCurrAngle() + RIGHT_ARM_OFFSET);
//
			if(weapon.getChargeLevel()<1)
			{
				sineC=MagicAnim.smooth(weapon.getChargeLevel());
				sinceD=MagicAnim.smoothNormalizeRange(weapon.getChargeLevel(),0.3f,1f);
			} 
			else 
			{                
				sineC =1;
				sinceD =1;
			}
			
		if(weapon.isFiring() && weapon.getAmmo() > 0 && !ship.getFluxTracker().isOverloaded())
			armaa_utils.makeWeaponAfterImages(ship,weapon,0.05f,amount);

			if(weapon.getCooldownRemaining() <= 0f && !weapon.isFiring())
				cooldownR = false;
			
			if(!swingingR && !cooldownR && weapon.getChargeLevel() > 0f)
			{
				weapon.setCurrAngle(weapon.getCurrAngle() + (sineC * (TORSO_OFFSET)*0.30f) *weapon.getChargeLevel());
			}
			if(weapon.getChargeLevel() >= 1f)
			{
				swingingR = true;
			}
			
			if(swingingR && weapon.getCurrAngle() != weapon.getShip().getFacing()+90f)
			{
				animInterval.advance(amount);
				weapon.setCurrAngle((float)Math.min(weapon.getCurrAngle()+swingLevelR,weapon.getCurrAngle()+weapon.getArc()/2));
			}
			
			if(swingingR == true && (weapon.getChargeLevel() <= 0f))
			{
				swingingR = false;
				swingLevelR = 0f;
				cooldownR = true;
			}
			
			if(animInterval.intervalElapsed())
			{
				if(cooldownR)
				swingLevelR-=0.5;					
				else
				swingLevelR+=0.5;
			}

			if(swingLevelR > 9)
			{
				swingLevelR = 9;
				cooldownR = true;
			}
			if(swingingR == false)
			{
				swingLevelR = 0f;
			}

		}
		
		// Modules
		List<ShipAPI> children = ship.getChildModulesCopy();
		if(children != null)
		{
			for(ShipAPI m: children)	
			{
				m.ensureClonedStationSlotSpec();
				if(m.getStationSlot() == null)
				{
					numModules--;
					continue;
				}
				if(numModules >= 2)
				{
					//ship.blockCommandForOneFrame(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK); 
				}
				if(ship.getSystem().isActive())
					m.useSystem();	
				if(ship.getFluxTracker().isVenting())
				{
					m.getFluxTracker().ventFlux();
				}
				if(ship.isPullBackFighters() == false)
					m.setPullBackFighters(false);
				else
					m.setPullBackFighters(true);
				if(ship.getShipTarget() != null)
					m.setShipTarget(ship.getShipTarget());
				//m.setCollisionClass(CollisionClass.SHIP);
				if(m.getLayer() != CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER )
					m.setLayer(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER );
					m.getShield().toggleOn();		
					if(engine.getCustomData().get(""+m.getStationSlot().getId()+"_centerX")== null)
						engine.getCustomData().put(""+m.getStationSlot().getId()+"_centerX",m.getSpriteAPI().getCenterX());
					if(engine.getCustomData().get(""+m.getStationSlot().getId()+"_centerY") == null)
						engine.getCustomData().put(""+m.getStationSlot().getId()+"_centerY",m.getSpriteAPI().getCenterY());					
					float x = (float)engine.getCustomData().get(""+m.getStationSlot().getId()+"_centerX");
					float y = (float)engine.getCustomData().get(""+m.getStationSlot().getId()+"_centerY");

					if(m.getStationSlot().getId().equals("BINDER_01") || m.getStationSlot().getId().equals("BINDER_02"))
					{
						if(!m.getStationSlot().getId().equals("BINDER_01"))
						{
							if(engine.getCustomData().get(ship.getId()+"_BINDER_01") instanceof ShipAPI)
							{
								ShipAPI binder = (ShipAPI)engine.getCustomData().get(ship.getId()+"_BINDER_01");
								boolean frontmod = binder.isHulk();							
								if(frontmod == true)
								{
									if(m.getFacing() != ship.getFacing()+25f)
										m.setFacing(ship.getFacing()+25f);
									m.getShield().toggleOff();	
									m.getShield().forceFacing(ship.getFacing()+25f);
									continue;
								}
							}
							
							m.setFacing(m.getFacing()+currentRotateR);
							m.getSpriteAPI().setCenter(x,y - (5*sway));
							m.getShield().forceFacing(m.getFacing()-sway*5);							
						}
						
						else
						{
							engine.getCustomData().put(ship.getId()+"_BINDER_01",m);							
							m.setFacing(m.getFacing()+currentRotateR/2);
							m.getSpriteAPI().setCenter(x,y - (4*sway)/2);
							m.getShield().forceFacing(m.getFacing()-65f-sway*4);							
						}
					}
					
					else
					{
						if(!m.getStationSlot().getId().equals("BINDER_04"))
						{
							if(engine.getCustomData().get(ship.getId()+"_BINDER_04") instanceof ShipAPI)
							{
								ShipAPI binder = (ShipAPI)engine.getCustomData().get(ship.getId()+"_BINDER_04");
								boolean frontmod = binder.isHulk();							
								if(frontmod == true)
								{
									if(m.getFacing() != ship.getFacing()-25f)
										m.setFacing(ship.getFacing()-25f);
									m.getShield().toggleOff();	
									m.getShield().forceFacing(ship.getFacing()-25f);
									continue;
								}
							}
							m.setFacing(m.getFacing()+currentRotateL);
							m.getSpriteAPI().setCenter(x,y - (5*sway));
							m.getShield().forceFacing(m.getFacing()+sway*5);							
						}
						
						else
						{
							engine.getCustomData().put(ship.getId()+"_BINDER_04",m);	
							m.setFacing(m.getFacing()+currentRotateL/2);
							m.getSpriteAPI().setCenter(x,y - (4*sway)/2);
							m.getShield().forceFacing(m.getFacing()+65f+sway*4);						
						}
					}
			}

			
		}			
	}
}
