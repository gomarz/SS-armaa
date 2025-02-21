package data.scripts.util;

import java.util.Iterator;
import java.util.List;

import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import java.awt.Color;

import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lazywizard.lazylib.FastTrig;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import org.magiclib.util.MagicUI;
import org.magiclib.util.MagicIncompatibleHullmods;
import org.magiclib.util.MagicRender;
import data.scripts.util.armaa_utils;
import com.fs.starfarer.api.ui.Alignment;

import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

import org.lazywizard.lazylib.VectorUtils;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.input.InputEventAPI;

public class armaa_sykoEveryFrame extends BaseEveryFrameCombatPlugin
{
	private ShipAPI ship;
	private float MIN_PROJECTILE_DMG = 40f;
	private float GRAZE_DAMAGE_BONUS = 10f;
	private float GRAZE_TIME_BONUS = 1.6f;
	private float GRAZE_TIME_BONUS_MIN = 1.1f;
	private float MAX_TIME_MULT = 1f;
	private float MIN_TIME_MULT = 0.1f;
	private float shieldMult = 0.5f;
	private float AFTERIMAGE_THRESHOLD = 0.1f;
	private IntervalUtil buffTimer = new IntervalUtil(3f,3f);
	private IntervalUtil coolDownTimer = new IntervalUtil(9f,9f);	
	private boolean dodgeBonus = false;
	private boolean cooldown = false;
	private float multBonus = 1f;
	//private Logger logger;
    public armaa_sykoEveryFrame(ShipAPI ship, float minDamage, float damageBonus, float maxTimeBonus, float minTimeBonus, float buffDuration, float cooldownDuration ) 
	{	
        this.ship = ship;	
		MIN_PROJECTILE_DMG = minDamage;
		GRAZE_DAMAGE_BONUS = damageBonus;
		GRAZE_TIME_BONUS = maxTimeBonus;
		GRAZE_TIME_BONUS_MIN = minTimeBonus;
		buffTimer = new IntervalUtil(buffDuration,buffDuration);
		coolDownTimer = new IntervalUtil(cooldownDuration,cooldownDuration);
    }
	
    public armaa_sykoEveryFrame(ShipAPI ship) 
	{	
        this.ship = ship;	
    }

    @Override
	public void advance(float amount, List<InputEventAPI> events)
	{
		if(Global.getCombatEngine() == null || ship== null || ship.getHitpoints() <= 0 || !Global.getCombatEngine().isEntityInPlay(ship) || Global.getCombatEngine().isCombatOver() || ship.getOwner() == -1 || ship.isPiece()) 
		{
			Global.getCombatEngine().getTimeMult().unmodify(ship.getId()+"_graze");	
			Global.getCombatEngine().getCustomData().remove("armaa_sykoStims_"+ship.getId());
			ship = null;			
			Global.getCombatEngine().removePlugin(this);
			return;
		}
		
        if (Global.getCombatEngine().isPaused()) {
            return;
        }
		
			float time = coolDownTimer.getElapsed();
			float max = coolDownTimer.getMaxInterval();
			float status = time/max;		

			MagicUI.drawInterfaceStatusBar(
			 ship, status,
			 Color.RED,
			 Color.RED,
			 status,
			 "Graze",
			 (int)((1f-status)*100)
			);
		float radius = shieldMult == 1f ? ship.getCollisionRadius() : ship.getShieldRadiusEvenIfNoShield();
        boolean player = ship == Global.getCombatEngine().getPlayerShip();
		boolean hasCopium = false;
		boolean inCampaign = Global.getCombatEngine().isInCampaign();
		hasCopium = ship.getOwner() == 0 ? !inCampaign || Global.getSector().getPlayerFleet().getCargo().getCommodityQuantity(Commodities.DRUGS) > 0 : true;
		
		if(!dodgeBonus&&!cooldown && hasCopium && !ship.isFinishedLanding())
		{
			
			List<DamagingProjectileAPI> possibleTargets = new ArrayList<>(100);
			possibleTargets.addAll(CombatUtils.getMissilesWithinRange(ship.getLocation(), radius));
			possibleTargets.addAll(CombatUtils.getProjectilesWithinRange(ship.getLocation(), radius));
			float dmg = 0f;
			for (DamagingProjectileAPI proj : possibleTargets) 
			{
				if (proj.getOwner() == ship.getOwner() || proj.isFading() || proj.getCollisionClass() == CollisionClass.NONE) 
					continue;
			
				if((proj instanceof MissileAPI))
				{
					MissileAPI m = (MissileAPI)proj;
					if(m.isFlare())
						continue;
				}
				
				if(!Global.getCombatEngine().isEntityInPlay(proj))
					continue;
				
				if(proj.didDamage() || proj.getBaseDamageAmount() <= 0f)
					continue;
					
				if (proj.getDamageType() == DamageType.FRAGMENTATION) 
				{
					dmg += proj.getDamageAmount() * 0.5f + proj.getEmpAmount() * 0.5f;
				} 
				else 
				{
					dmg += proj.getDamageAmount() + proj.getEmpAmount() * 0.25f;
				}			
				
				if(CollisionUtils.isPointWithinCollisionCircle(proj.getLocation(), ship))
				{			
					if(!CollisionUtils.getCollides(proj.getLocation(),proj.getVelocity(),ship.getLocation(), 25f) && dmg >= 40f)
					{
						if(proj.didDamage())
							continue;
						
						SpriteAPI waveSprite = Global.getSettings().getSprite("misc", "armaa_sfxpulse");
						if (waveSprite != null)
						{
							MagicRender.battlespace(
										waveSprite,
										ship.getLocation(),
										new Vector2f(),
										new Vector2f(5f,5f),
										new Vector2f(200f,200f),
										15f,
										15f,
										new Color(255,255,255,155), 
										true,
										0.3f,
										0f,
										0.3f
								);								
						}						
						Global.getCombatEngine().addFloatingText(ship.getLocation(), "Grazed!", 15f,Color.white,proj,0f,0f);
						Global.getSoundPlayer().playSound("ui_neural_transfer_begin", .7f, 1f, ship.getLocation(),new Vector2f());
						CombatUtils.applyForce(proj,-proj.getFacing(),5f);
						dodgeBonus = true;
						// Stronger effect if shields are down
						shieldMult = (ship.getShield() == null || ship.getShield().isOff()) ? 1f:0.5f;
					}
				}
			}
		}
		
		boolean enemiesInRange = false;
		float closest = 99999f;
		float effectLevel = 1f;

		if(dodgeBonus)
		{
			buffTimer.advance(amount);
			float elapsed = buffTimer.getElapsed();
			float maxinterval = buffTimer.getMaxInterval();
			effectLevel = elapsed/maxinterval;
			multBonus = (GRAZE_TIME_BONUS*(1f-effectLevel))*shieldMult;
			float refit_timer = Math.round((elapsed/maxinterval)*100f);
    		String timer = String.valueOf(refit_timer);
			if(player)
			Global.getCombatEngine().maintainStatusForPlayerShip("adrenal", "graphics/ui/icons/icon_search_and_destroy.png","Grazed","DMG +" + (int)(GRAZE_DAMAGE_BONUS*(1f-effectLevel))+"%",false);

			armaa_utils.makeAfterImages(ship,AFTERIMAGE_THRESHOLD,amount);

		}
		
		if(buffTimer.intervalElapsed() && dodgeBonus)
		{
			dodgeBonus = false;
			cooldown = true;
			
		}
		if(cooldown)
		{
			coolDownTimer.advance(amount);
			effectLevel = 0f;
			float elapsed = coolDownTimer.getElapsed();
			float maxinterval = coolDownTimer.getMaxInterval();
			float refit_timer = Math.round((elapsed/maxinterval)*100f*shieldMult);
    		String timer = String.valueOf(refit_timer);
			
			if(player)
			Global.getCombatEngine().maintainStatusForPlayerShip("adrenal", "graphics/ui/icons/icon_repair_refit.png","Crash", timer+"%" ,true);
			
		}
		
		if(coolDownTimer.intervalElapsed() && cooldown)
		{
			cooldown = false;
			effectLevel = 0f;
		}
		
		ship.getMutableStats().getTimeMult().modifyMult(ship.getId()+"_graze", MAX_TIME_MULT+multBonus);		
		
		if(cooldown || dodgeBonus)
		{
			ship.getMutableStats().getEnergyWeaponDamageMult().modifyPercent(ship.getId()+"_graze",(GRAZE_DAMAGE_BONUS*(1f-effectLevel))*shieldMult); 
			ship.getMutableStats().getBallisticWeaponDamageMult() .modifyPercent(ship.getId()+"_graze",(GRAZE_DAMAGE_BONUS*(1f-effectLevel))*shieldMult);
		}
		
		else
		{
			multBonus = 0;
			ship.getMutableStats().getEnergyWeaponDamageMult().unmodify(ship.getId()+"_graze");
			ship.getMutableStats().getBallisticWeaponDamageMult().unmodify(ship.getId()+"_graze");			
		}

		if (player && ship.isAlive()) 
		{
			if(!ship.isLanding() && !ship.isFinishedLanding())
			{
				float mult = 1f/((MAX_TIME_MULT+multBonus));
				Global.getCombatEngine().getTimeMult().modifyMult(ship.getId()+"_graze", mult);	
			}
		}
		else 
		{
			Global.getCombatEngine().getTimeMult().unmodify(ship.getId()+"_graze");
		}		
	}
}