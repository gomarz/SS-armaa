package data.scripts.weapons;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.IntervalUtil;
import org.magiclib.util.MagicAnim;
import java.awt.Color;
import java.util.List;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;


/**
 *Base script by
 * Tartiflette
 * additional modifications by shoi
 */
public class armaa_koutoEffectmk3 implements EveryFrameWeaponEffectPlugin  {

    private boolean runOnce = false, lockNloaded = false;
    private ShipSystemAPI system;
    private ShipAPI ship;
	
	private IntervalUtil interval = new IntervalUtil(1f,1f);
    private WeaponAPI head, armL, armR, pauldronL, pauldronR, gun, shoulderwep, headGlow,trueWeapon;
	private float sinceB = 0;
    private final Vector2f ZERO = new Vector2f();
    private int limbInit = 0;
	private float currentRotateL=0;
    private float currentRotateR=0;
	private final float maxlegRotate=22.5f;  
	private float reverse = 1f;
	private boolean deathRoll = false;
	private boolean windingup = false;
    private float overlap = 0, heat = 0, originalRArmPos = 0f, originalArmPos = 0f, originalShoulderPos = 0f, originalVernierPos = 0f, originalRShoulderPos = 0f;
	private float originalShieldPos = 0f;
    private final float TORSO_OFFSET = -45, LEFT_ARM_OFFSET = -70, RIGHT_ARM_OFFSET = -25, MAX_OVERLAP = 10, LPAULDRONOFFSET = -5;

    public void init()
    {
        runOnce = true;
        for (WeaponAPI w : ship.getAllWeapons()) {
            switch (w.getSlot().getId()) {
                case "A_GUN":
                    if(gun==null) {
                        gun = w;
						//if(w.getBarrelSpriteAPI() != null)
						originalRArmPos = w.getSprite().getCenterY();
                    }
                    break;
                case "C_ARML":
                    if(armL==null) {
                        armL = w;
						originalArmPos = armL.getSprite().getCenterY();
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
                    }
                    break;
                case "D_PAULDRONR":
                    if(pauldronR == null) {
                        pauldronR = w;
						originalRShoulderPos = pauldronL.getSprite().getCenterY();
                    }
                    break;
                case "E_HEAD":
                    if(head == null) {
                        head = w;
                    }
                    break;
				case "WS0002":
                   		shoulderwep = w;
                    break;
				case "H_GLOW":
                   		headGlow = w;
                    break;
                case "TRUE_GUN":
                    if(trueWeapon==null) 
					{
                        trueWeapon = w;
                    }
                    break;
            }
        }
		
		List<ShipAPI> children = ship.getChildModulesCopy();
		if(children != null)
			for(ShipAPI m: children)	
			{
				m.setCollisionClass(CollisionClass.FIGHTER);
				if(m.getStationSlot().getId().equals("SHIELD"))
				{
					originalShieldPos = m.getSpriteAPI().getCenterY();
				}
			}
    }
    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{

        ship = weapon.getShip();
        system = ship.getSystem();
		
		if(!runOnce)
        init();
		if(engine.isPaused())
			return;

		if(ship.getOwner() == 100 && !deathRoll)
		{
			head.getSprite().setColor(new Color(0,0,0,0));
			gun.getSprite().setColor(new Color(0,0,0,0));
			if(Math.random() > 0.50f)
			{
				pauldronL.getSprite().setColor(new Color(0,0,0,0));
				armL.getSprite().setColor(new Color(0,0,0,0));
			}
			if(Math.random() > 0.50f)
			{
				pauldronR.getSprite().setColor(new Color(0,0,0,0));
				if(armR != null)
				armR.getSprite().setColor(new Color(0,0,0,0));
			}
			for (WeaponAPI w : ship.getAllWeapons()) 
			{
				if(Math.random() > 0.50f)
					weapon.getSprite().setColor(new Color(0,0,0,0));
			}
			deathRoll = true;
			return;
		}
		ship.syncWeaponDecalsWithArmorDamage();			
		if(gun == null) return;		
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


		float sineA = 0, sineC=0, sinceD=0, sinceG=0;	
		float global = ship.getFacing();
		float aim = MathUtils.getShortestRotation(global, gun.getCurrAngle());
		float aim2 = MathUtils.getShortestRotation(global, armL.getCurrAngle());
		boolean noanim = !ship.getHullSpec().getHullId().equals("armaa_kouto") ? true : false;
			//noanim  = true;
		if(armL != null && !noanim)
		{
	
			if(armL.getChargeLevel() > 0)
			{
				sineA=MagicAnim.smoothNormalizeRange(armL.getChargeLevel(),0.5f,1f);
				sinceB=MagicAnim.smoothNormalizeRange(armL.getChargeLevel(),0.3f,1f);
			}
			else 
			{                
				sineA =1;
				sinceB =1;
			}

			
			//armL.getSprite().setCenterY(originalArmPos-(16*sinceB)+(8*sinceG));
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
					if(ship.areAnyEnemiesInRange() && (trueWeapon != null && trueWeapon.getAmmo() > 0))
						sinceB+= amount;
					
					else
						sinceB-= amount;
					if(sinceB < 0)
						sinceB = 0;
					else if(sinceB > 1)
						sinceB = 1;
					armL.setCurrAngle(global+((aim+LEFT_ARM_OFFSET)*sinceB)+((overlap+aim*0.25f)*(1-sinceB)));
					weapon.setCurrAngle(global + (sineA * (TORSO_OFFSET) + aim * 0.3f)+currentRotateR);
					
					if(trueWeapon != null && trueWeapon.getCooldown() > 0)
					{
						gun.getSprite().setCenterY(originalRArmPos+(2*trueWeapon.getCooldownRemaining()/trueWeapon.getCooldown()));
						pauldronR.getSprite().setCenterY(originalRShoulderPos+(2*trueWeapon.getCooldownRemaining()/trueWeapon.getCooldown()));

						if(sinceB >= 0.95)
						{
							armL.getSprite().setCenterY(originalArmPos+(2*trueWeapon.getCooldownRemaining()/trueWeapon.getCooldown()));
							pauldronL.getSprite().setCenterY(originalShoulderPos+(2*trueWeapon.getCooldownRemaining()/trueWeapon.getCooldown()));

						}
						
					}
				}		
			}				
		}

		if (armR != null)
		{
			armR.setCurrAngle(gun.getCurrAngle() + RIGHT_ARM_OFFSET  + ((sineC*(TORSO_OFFSET))));
		}			

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
				pauldronL.setCurrAngle(weapon.getCurrAngle()+MathUtils.getShortestRotation(weapon.getCurrAngle(),armL.getCurrAngle())*0.6f); 
		}
		
		if(headGlow != null)
		{
			headGlow.setCurrAngle(head.getCurrAngle());
		}
		List<ShipAPI> children = ship.getChildModulesCopy();
		if(children != null)
			for(ShipAPI m: children)	
			{
				if(m.getStationSlot() == null)
					continue;
					
				m.ensureClonedStationSlotSpec();
				m.setHullSize(HullSize.FIGHTER);
				if(m.getStationSlot().getId().equals("MODULE"))
				{
					String key = "moduleRepair_isDestroyed";

						if(shoulderwep == null)
							continue;
							m.setFacing(shoulderwep.getCurrAngle());
					if(Global.getCombatEngine().getCustomData().containsKey(key+"_"+m.getId()))
					{
						shoulderwep.disable();
						Color invis = new Color(0f,0f,0f,0f);		
						if(shoulderwep.getSprite() != null)
							shoulderwep.getSprite().setColor(invis);
						
						if(shoulderwep.getUnderSpriteAPI() != null)
							shoulderwep.getUnderSpriteAPI().setColor(invis);		

						if(shoulderwep.getBarrelSpriteAPI() != null)
							shoulderwep.getBarrelSpriteAPI().setColor(invis);	
					}

					else
					{
						Color def = new Color(1f,1f,1f,1f);
						if(shoulderwep.getSprite() != null)
							shoulderwep.getSprite().setColor(def);
						
						if(shoulderwep.getUnderSpriteAPI() != null)
							shoulderwep.getUnderSpriteAPI().setColor(def);		

						if(shoulderwep.getBarrelSpriteAPI() != null)
							shoulderwep.getBarrelSpriteAPI().setColor(def);							
					}
				}	
				
				else
				{
					if(armL != null)
					{
						m.setFacing(armL.getCurrAngle());
						if(ship.areAnyEnemiesInRange() && trueWeapon != null)
							m.getSpriteAPI().setCenterY(originalShieldPos+(2*trueWeapon.getCooldownRemaining()/trueWeapon.getCooldown()));

					}
				}
			}

	}
}
