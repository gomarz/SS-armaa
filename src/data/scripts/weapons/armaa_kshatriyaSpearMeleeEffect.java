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

public class armaa_kshatriyaSpearMeleeEffect implements EveryFrameWeaponEffectPlugin {
	
    private SpriteAPI sprite;
    private AnimationAPI anim;
	private float currentRotateL=0;
    private float currentRotateR=0;
	private float sway = 0f;
	private float ogSpikePos = 0f;
	private final float maxbinderrotate=40f;  
    private final Vector2f ZERO = new Vector2f();
	private int numModules = 4;
	private float wepRecoilMax = 30f;
	private float recoil = 0f;	
	private boolean runOnce = false;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{
		if(engine.isPaused())
			return;
		if(!runOnce)
		{
			ogSpikePos = weapon.getBarrelSpriteAPI().getCenterY();
			runOnce = true;
		}		
		ShipAPI ship = weapon.getShip();
		if(engine.getCustomData().get("armaa_drillHit_"+ship.getId()) != null)
		{	
			engine.getCustomData().remove("armaa_drillHit_"+ship.getId());
			recoil=Math.max(0,recoil-10);
		}
		else if(recoil < wepRecoilMax)
				recoil++;

		weapon.getBarrelSpriteAPI().setCenterY(ogSpikePos-(recoil*weapon.getCooldownRemaining()/weapon.getCooldown()));
		
		if(ship.getShipTarget() != null && MathUtils.getDistance(ship,ship.getShipTarget()) < 500 && !weapon.isDisabled())
		{
			if(ship.getAI() != null )
			{
				ship.blockCommandForOneFrame(ShipCommand.DECELERATE);
				engine.headInDirectionWithoutTurning(ship, weapon.getCurrAngle(), ship.getMaxSpeed());
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
