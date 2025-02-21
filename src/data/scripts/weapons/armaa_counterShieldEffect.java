package data.scripts.weapons;
import com.fs.starfarer.api.AnimationAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.magiclib.util.MagicAnim;
import org.magiclib.util.MagicRender;
import org.magiclib.util.ui.StatusBarData;
import java.awt.Color;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import org.lazywizard.lazylib.combat.CombatUtils;

import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.combat.listeners.DamageListener;
import com.fs.starfarer.api.combat.*;
import data.scripts.util.armaa_utils;

public class armaa_counterShieldEffect implements EveryFrameWeaponEffectPlugin 
{

    private boolean runOnce = false, lockNloaded = false;
	private float MAX_GAUGE = 1000f;
	private float gauge = 0f;
	private final float gaugeDecay = 0f;
	private boolean even = false;
    private Color BASE_SHIELD_COLOR;
	private boolean isFiring = false;
	private IntervalUtil interval = new IntervalUtil(0.04f,0.04f);
	
    public void init(ShipAPI ship)
    {
        runOnce = true;
		if(ship.getShield() != null)
		{
			ship.addListener(new ShieldDamageListener(ship));
			BASE_SHIELD_COLOR = ship.getShield().getInnerColor();
		}
		MAX_GAUGE = ship.getMaxFlux();
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{	
		ShipAPI ship = weapon.getShip();
		
		if(ship.getOwner() == -1)
		{
			gauge = 0;
		}
		
		if(Global.getCombatEngine().isCombatOver())
		{
			gauge = 0;
		}	
		ShieldAPI shield = ship.getShield();
		if(!runOnce)
			init(ship);
		//"fringeColor":[255,68,204,255],

		if(ship.getShield() != null)
		{
			float r = Math.max((BASE_SHIELD_COLOR.getRed()/255f)-(gauge/MAX_GAUGE),.2f);
			float g = Math.max((BASE_SHIELD_COLOR.getGreen()/255f)-(gauge/MAX_GAUGE),.67f);
			float b = Math.min((BASE_SHIELD_COLOR.getBlue()/255f)+(gauge/MAX_GAUGE),1f);
			shield.setInnerColor(new Color(r,g,b,Math.min((gauge/MAX_GAUGE)+.3f,1f)));
			StatusBarData bar = new StatusBarData(ship,gauge/MAX_GAUGE,new Color(r,g,b,1f),new Color(r,g,b,1f),0f,"test",1);
			bar.drawToScreen(new Vector2f());

			
			if(gauge > 0f)
				gauge -=  ship.getFluxTracker().isOverloaded() ? 1f:0.5f;

			if(weapon.isDisabled() || engine.isPaused())
				return;
			
			else if(gauge <= 1f)
			{
				isFiring = false;
			}
			
			if(gauge >= MAX_GAUGE*0.7f &&(ship.getFluxLevel() >= 0.65f) && Global.getCombatEngine().getPlayerShip() != ship)
			{
				if(ship.getHullLevel() >= 0.6f)
				{
					ship.giveCommand(ShipCommand.VENT_FLUX,null,0); 
					
				}
			}
					
			if(gauge >= MAX_GAUGE || (ship.getFluxLevel() >= 0.65f &&ship.getFluxTracker().isVenting()))
			{
				if(!isFiring && gauge > 0)
				{
					SpriteAPI waveSprite = Global.getSettings().getSprite("misc", "armaa_sfxpulse");
					if (waveSprite != null)
					{
						MagicRender.objectspace(
									waveSprite,
									ship,
									new Vector2f(),
									new Vector2f(),
									new Vector2f(25f,25f),
									new Vector2f(500f,500f),
									5f,
									5f,
									true,
									new Color(150,155,155,155), 
									true,
									.3f,
									0f,
									.3f,
									true
							);
					}
					isFiring = true;
				}
				if(gauge >= MAX_GAUGE)
					gauge = MAX_GAUGE;
			}
			float shipRadius = armaa_utils.effectiveRadius(ship);	
			float randRange = (float) shipRadius * 0.5f * 1f;
			randRange = (float) Math.sqrt(shipRadius) * 0.75f * 1f;
			ship.setJitter(ship, new Color(50/255f,175f/255f,255f/255f,Math.min(1f*(gauge/MAX_GAUGE),1f)), 1f * (gauge/MAX_GAUGE), 4, 25);

			if(isFiring == true)
				interval.advance(amount);		
				
			if(interval.intervalElapsed() && gauge >= 1f && isFiring)
			{			
				String weaponId = "armaa_valkazard_countershield_secondary";
				Vector2f muzzleLocation = weapon.getSpec().getTurretFireOffsets().get(0); 
				for(int i = 0; i < 1; i++)
				{

					ShipAPI target1 = null;
					if(ship.getWeaponGroupFor(weapon)!=null )
					{
						//WEAPON IN AUTOFIRE
						if(ship.getWeaponGroupFor(weapon).isAutofiring()  //weapon group is autofiring
								&& ship.getSelectedGroupAPI()!=ship.getWeaponGroupFor(weapon)){ //weapon group is not the selected group
							target1 = ship.getWeaponGroupFor(weapon).getAutofirePlugin(weapon).getTargetShip();
						}
						else 
						{
							target1 = ship.getShipTarget();
							
						}
					}
					
					if(target1 == null)
					{
						for(ShipAPI target: CombatUtils.getShipsWithinRange(ship.getLocation(),2000f))
						{
							if(target.getOwner() == ship.getOwner())
								continue;
							
							if(!target.isAlive() || target.isHulk())
								continue;
							
							target = target1;
						}
					}
					//float angle = ship.getFacing();
					//float arc = 120f;
					//float r1 = angle+(arc/2);
					//float r2 = angle-(arc/2);
					float angle = even ? ship.getFacing()+45f : ship.getFacing()-45f;
					DamagingProjectileAPI proj = (DamagingProjectileAPI)engine.spawnProjectile(weapon.getShip(),weapon,weaponId,MathUtils.getRandomPointInCircle(weapon.getLocation(),10f),MathUtils.getRandomNumberInRange(angle-(45/2),angle+(45/2)),new Vector2f());
					float variance = MathUtils.getRandomNumberInRange(-0.6f,0f);
					Global.getSoundPlayer().playSound("vajra_fire", 1f+variance, 1f+variance, proj.getLocation(), proj.getVelocity());

					if(MagicRender.screenCheck(0.25f, proj.getLocation())){
						engine.addSmoothParticle(proj.getLocation(), new Vector2f(), 60, 0.5f, 0.25f, new Color(50/255f,175f/255f,255f/255f,Math.min(1f*(gauge/MAX_GAUGE),1f)));
						//engine.addHitParticle(proj.getLocation(), new Vector2f(), 50, 1f, 0.1f, Color.white);
					}
					
					if(gauge - proj.getDamage().getDamage() >= 0)
					gauge -= proj.getDamage().getDamage();
					else
					{
						gauge = 0;
						isFiring = false;
					}
					if (engine.isEntityInPlay(proj) && !proj.didDamage()) 
					{
						engine.addPlugin(new armaa_counterShieldScript(proj, target1));
						//alreadyRegisteredProjectiles.add(projectile);
					}
					even = even == true ? false : true;
					
				}					
			}
		}		
    }
	
	public class ShieldDamageListener implements DamageListener 
	{
		ShipAPI ship;
		public ShieldDamageListener(ShipAPI ship) {
			this.ship = ship;
		}

		public void reportDamageApplied(Object source, CombatEntityAPI target, ApplyDamageResultAPI result) 
		{
			float totalDamage = result.getDamageToShields();
						
			gauge+=totalDamage;
		}	
	}
	
}
