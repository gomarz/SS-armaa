package data.scripts.util;

import java.util.Iterator;
import java.util.List;
import java.util.EnumSet;

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

public class armaa_reloadEveryFrame extends BaseEveryFrameCombatPlugin
{
	private ShipAPI ship;

	private static float DAMAGE_BONUS = 2f;
	private float count = 0.4f;
	private boolean recoiling = false;
	private IntervalUtil buffTimer = new IntervalUtil(3f,3f);
    public armaa_reloadEveryFrame(ShipAPI ship) 
	{	
        this.ship = ship;	
    }

    @Override
	public void advance(float amount, List<InputEventAPI> events)
	{
		//don't run while paused because duh
        if (Global.getCombatEngine().isPaused()) {
            return;
        }
		
        boolean player = ship == Global.getCombatEngine().getPlayerShip();
		MutableShipStatsAPI stats = ship.getMutableStats();
		stats.getBallisticRoFMult().modifyMult(ship.getId(),DAMAGE_BONUS);
		stats.getBallisticWeaponFluxCostMod().modifyMult(ship.getId(),DAMAGE_BONUS);
		stats.getBallisticWeaponDamageMult().modifyMult(ship.getId(),DAMAGE_BONUS);
		ship.setWeaponGlow(buffTimer.getIntervalDuration() - buffTimer.getElapsed(), Color.red, EnumSet.of(WeaponAPI.WeaponType.BALLISTIC));
		buffTimer.advance(amount);
		for(WeaponAPI weapon: ship.getAllWeapons())
		{
			if(weapon.getId().equals("armaa_barretta"))
			{
				if(weapon.getChargeLevel() == 1)
				{
					Global.getSoundPlayer().playSound("armaa_rcl_fire", 0.8f, 1f, ship.getLocation(),new Vector2f());
					float angle = 180+weapon.getCurrAngle();
					Vector2f point= MathUtils.getPoint(weapon.getFirePoint(0), 45, angle);					
					for(int i=0; i<20; i++){                    
						Vector2f vel = MathUtils.getRandomPointInCone(new Vector2f(), 100, angle-20, angle+20);
						vel.scale((float)Math.random());
						Vector2f.add(vel, weapon.getShip().getVelocity(), vel);
						float grey = MathUtils.getRandomNumberInRange(0.5f, 0.75f);
						Global.getCombatEngine().addSmokeParticle(
								MathUtils.getRandomPointInCircle(weapon.getFirePoint(0), 5),
								vel,
								MathUtils.getRandomNumberInRange(5, 20),
								MathUtils.getRandomNumberInRange(0.25f, 0.75f),
								MathUtils.getRandomNumberInRange(0.25f, 1f),
								new Color(grey,grey,grey,MathUtils.getRandomNumberInRange(0.25f, 0.75f))
						);
					}
			//debris
					for(int i=0; i<10; i++){                    
						Vector2f vel = MathUtils.getRandomPointInCone(new Vector2f(), 250, angle-20, angle+20);
						Vector2f.add(vel, weapon.getShip().getVelocity(), vel);
						Global.getCombatEngine().addHitParticle(
								point,
								vel,
								MathUtils.getRandomNumberInRange(2, 4),
								1,
								MathUtils.getRandomNumberInRange(0.05f, 0.25f),
								new Color(255,125,50)
						);
					}
					//flash
						Vector2f vel = MathUtils.getRandomPointInCone(new Vector2f(), 100, angle-20, angle+20);
						vel.scale((float)Math.random());
						Vector2f.add(vel, weapon.getShip().getVelocity(), vel);
					Global.getCombatEngine().addHitParticle(
							point,
							vel,
							100,
							0.5f,
							0.5f,
							new Color(255,20,10)
					);
					Global.getCombatEngine().addHitParticle(
							point,
							vel,
							80,
							0.75f,
							0.15f,
							new Color(255,200,50)
					);
					Global.getCombatEngine().addHitParticle(
							point,
							vel,
							50,
							1,
							0.05f,
							new Color(255,200,150)
					);

					point= MathUtils.getPoint(weapon.getFirePoint(0), 0, angle);
					//flash
					Global.getCombatEngine().addHitParticle(
							point,
							ship.getVelocity(),
							100,
							0.5f,
							0.5f,
							new Color(255,20,10)
					);
					Global.getCombatEngine().addHitParticle(
							point,
							ship.getVelocity(),
							80,
							0.75f,
							0.15f,
							new Color(255,200,50)
					);
					Global.getCombatEngine().addHitParticle(
							point,
							ship.getVelocity(),
							50,
							1,
							0.05f,
							new Color(255,200,150)
					);					
					if(!recoiling)
					{
						recoiling = true;
					}
				}
				
				if(recoiling && count > 0f)
				{
					count-= amount;
					ship.setFacing(ship.getFacing()-count);	
				}

				if(count <= 0f)
				{
					count = 0.4f;
					recoiling = false;
				}
			}
		}

		Global.getCombatEngine().maintainStatusForPlayerShip("eis_wingClipper", "graphics/ui/icons/icon_repair_refit.png","Air Superiority", "Time Dilation: +",false);

		
		if(buffTimer.intervalElapsed() || !ship.isAlive())
		{
			stats.getBallisticWeaponFluxCostMod().unmodify(ship.getId());
			stats.getBallisticWeaponDamageMult().unmodify(ship.getId());
			stats.getBallisticRoFMult().unmodify(ship.getId());
			ship.setWeaponGlow(0f, new Color(255, 125, 50, 220), EnumSet.of(WeaponAPI.WeaponType.BALLISTIC, WeaponAPI.WeaponType.MISSILE, WeaponAPI.WeaponType.ENERGY));
			Global.getCombatEngine().removePlugin(this);			
		}
	}

}