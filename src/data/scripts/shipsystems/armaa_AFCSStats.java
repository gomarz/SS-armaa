package data.scripts.shipsystems;

import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.Global;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.combat.ShipAPI;
import java.awt.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.magiclib.util.MagicRender;
import java.util.ArrayList;
import java.util.List;
import com.fs.starfarer.api.impl.campaign.ids.Stats;

public class armaa_AFCSStats extends BaseShipSystemScript {

	private boolean carriersNearby = false;
	private boolean runOnce = false;
	private	Vector2f takeoffLoc = null;
	private final static float DAMAGE_BONUS = 1.25f;
	private final static float SYSTEM_RANGE = 1000f;
	public Color TEXT_COLOR = new Color(255,55,55,255);
	public Object KEY_SHIP = new Object();
	public Object KEY_TARGET = new Object();
	private IntervalUtil interval = new IntervalUtil(1f,1f);	
	public class TargetData {
		public ShipAPI ship;
		public ShipAPI target;
		public String key;
		public EveryFrameCombatPlugin targetEffectPlugin;
		public float currDamMult;
		public float elaspedAfterInState;
		public float increment;
		public IntervalUtil interval;
		public TargetData(ShipAPI ship, ShipAPI target) {
			this.ship = ship;
			this.target = target;
		}
	}	
	public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) 
	{
		ArrayList<ShipAPI> friendlyShips = new ArrayList<ShipAPI>();
		ArrayList<ShipAPI> hostileShips = new ArrayList<ShipAPI>();
		float friendlyECM = 0f;
		float enemyECM = 0f;
		ShipAPI user = (ShipAPI)stats.getEntity();
        float ADJUSTED_RANGE = stats.getSystemRangeBonus().computeEffective(SYSTEM_RANGE);
		float bonus = user.getMutableStats().getDynamic().getMod(Stats.ELECTRONIC_WARFARE_FLAT).getFlatBonus()*user.getMutableStats().getDynamic().getMod(Stats.ELECTRONIC_WARFARE_FLAT).getBonusMult();
		//friendlyECM += bonus;
		SpriteAPI waveSprite = Global.getSettings().getSprite("misc", "armaa_sfxpulse");

		//why the FUCK is this not working?
		//fallback to plan B: copy IS
		if (waveSprite != null)
		{
			interval.advance(.025f);
			//hasSprite = true;
			if(interval.intervalElapsed())
			MagicRender.objectspace(
						waveSprite,
						user,
						new Vector2f(),
						new Vector2f(),
						new Vector2f(1,1),
						new Vector2f(ADJUSTED_RANGE,ADJUSTED_RANGE),
						5f,
						5f,
						true,
						new Color(90,165,255,55), 
						true,
						.5f,
						1f,
						.5f,
						true
				);
		}
		for(ShipAPI ship : CombatUtils.getShipsWithinRange(stats.getEntity().getLocation(),ADJUSTED_RANGE))
		{
			if(!ship.isAlive() || ship.isHulk())
				continue;
			
			if(ship.getOwner() == user.getOwner())
			{
				friendlyShips.add(ship);
				bonus = Math.max(0,ship.getMutableStats().getDynamic().getMod(Stats.ELECTRONIC_WARFARE_FLAT).getFlatBonus()*ship.getMutableStats().getDynamic().getMod(Stats.ELECTRONIC_WARFARE_FLAT).getBonusMult());
				if(ship == user)
					bonus*=2;
				friendlyECM += bonus;
			}
			
			else
			{
				hostileShips.add(ship);
				bonus = Math.max(0,ship.getMutableStats().getDynamic().getMod(Stats.ELECTRONIC_WARFARE_FLAT).getFlatBonus()*ship.getMutableStats().getDynamic().getMod(Stats.ELECTRONIC_WARFARE_FLAT).getBonusMult());
				enemyECM += bonus;
			}
		}
	
		//if(friendlyECM >= enemyECM)
		//{
			Object executed = Global.getCombatEngine().getCustomData().get("AFCSS_ACTIVE_"+id);
			
			ArrayList<ShipAPI> affectedShips = new ArrayList<ShipAPI>();
			
			if(executed instanceof ArrayList)
			{
				affectedShips = (ArrayList<ShipAPI>) executed;
			}
			
			else
			{
				for(int i = 0; i < hostileShips.size(); i++)
				{
					float countermeasures = hostileShips.get(i).getMutableStats().getEccmChance().getModifiedValue() == 0 ? 1 : hostileShips.get(i).getMutableStats().getEccmChance().getModifiedValue();
					
					if(Math.random() > (Math.max(1,enemyECM))/(friendlyECM*countermeasures))
						affectedShips.add(hostileShips.get(i));	
					
					else
					{
						ShipAPI resister = hostileShips.get(i);
						if (resister.getFluxTracker().showFloaty() && !affectedShips.contains(resister) && !Global.getCombatEngine().hasAttachedFloaty(resister)) 
							resister.getFluxTracker().showOverloadFloatyIfNeeded("EW attack thwarted!", TEXT_COLOR, 4f, true);						
					}
					 
				}
			}
			
			for(int i = 0; i < affectedShips.size(); i++)
			{
					ShipAPI target = affectedShips.get(i);
				if(1 == 1)
				{
					ShipAPI ship  = (ShipAPI)stats.getEntity();
					String targetDataKey = ship.getId() + "_afcss_target_data_"+target.getId();
					Object targetDataObj = Global.getCombatEngine().getCustomData().get(targetDataKey); 
					
					if (state == State.IN && targetDataObj == null) {
						Global.getCombatEngine().getCustomData().put(targetDataKey, new TargetData(ship, target));
							if (target.getFluxTracker().showFloaty() || 
									ship == Global.getCombatEngine().getPlayerShip() ||
									target == Global.getCombatEngine().getPlayerShip()) {
								target.getFluxTracker().showOverloadFloatyIfNeeded("Broadcasting weakpoints!", TEXT_COLOR, 4f, true);
							}
					} else if (state == State.IDLE && targetDataObj != null) 
					{
						Global.getCombatEngine().getCustomData().remove(targetDataKey);
						((TargetData)targetDataObj).currDamMult = 1f;
						targetDataObj = null;
					}				
					if (targetDataObj == null || ((TargetData) targetDataObj).target == null) continue;
					final TargetData targetData = (TargetData) targetDataObj;
					targetData.currDamMult = 1f + (DAMAGE_BONUS - 1f) * effectLevel;
					targetData.key = targetDataKey;
					if (targetData.targetEffectPlugin == null) {
						targetData.targetEffectPlugin = new BaseEveryFrameCombatPlugin() 
						{
							boolean hasSprite = false;
							IntervalUtil interval = new IntervalUtil(2f,2f);
							@Override
							public void advance(float amount, List<InputEventAPI> events) {
								if (Global.getCombatEngine().isPaused()) return;
								if (targetData.target == Global.getCombatEngine().getPlayerShip()) { 
									Global.getCombatEngine().maintainStatusForPlayerShip(KEY_TARGET, 
											targetData.ship.getSystem().getSpecAPI().getIconSpriteName(),
											targetData.ship.getSystem().getDisplayName(), 
											"" + (int)((targetData.currDamMult - 1f) * 100f) + "% more damage taken", true);
								}
								
								
								interval.advance(amount);
								SpriteAPI waveSprite = Global.getSettings().getSprite("misc", "armaa_target");

								//why the FUCK is this not working?
								//fallback to plan B: copy IS
								if (waveSprite != null)
								{
									float radius = targetData.target.getCollisionRadius()*3;
									if(interval.intervalElapsed())
									MagicRender.objectspace(
												waveSprite,
												targetData.target,
												new Vector2f(),
												new Vector2f(),
												new Vector2f(radius,radius),
												new Vector2f(-100f,-100f),
												0f,
												25f,
												true,
												new Color(255,0,0,155), 
												true,
												.5f,
												.5f,
												.5f,
												true
										);
								}
								
								if (targetData.currDamMult <= 1f || !targetData.ship.isAlive() || !targetData.ship.getSystem().isActive()) 
								{
									targetData.target.getMutableStats().getHullDamageTakenMult().unmodify("AFCSS");
									targetData.target.getMutableStats().getArmorDamageTakenMult().unmodify("AFCSS");
									targetData.target.getMutableStats().getShieldDamageTakenMult().unmodify("AFCSS");
									targetData.target.getMutableStats().getEmpDamageTakenMult().unmodify("AFCSS");
									//targetData == null;
									Global.getCombatEngine().getCustomData().put(targetData.key, null);
									Global.getCombatEngine().removePlugin(targetData.targetEffectPlugin);
								} else 
								{
									targetData.target.getMutableStats().getHullDamageTakenMult().modifyMult("AFCSS", targetData.currDamMult);
									targetData.target.getMutableStats().getArmorDamageTakenMult().modifyMult("AFCSS", targetData.currDamMult);
									targetData.target.getMutableStats().getShieldDamageTakenMult().modifyMult("AFCSS", targetData.currDamMult);
									targetData.target.getMutableStats().getEmpDamageTakenMult().modifyMult("AFCSS", targetData.currDamMult);
								}
							}
						};
						Global.getCombatEngine().addPlugin(targetData.targetEffectPlugin);
					}				
					
					if (effectLevel > 0) 
					{
						if (state != State.IN) {
							targetData.elaspedAfterInState += Global.getCombatEngine().getElapsedInLastFrame();
						}

						float targetJitterLevel = effectLevel;
										
						Color color = new Color(255,165,90,55);
						
						if (targetJitterLevel > 0) {
							//target.setJitterUnder(KEY_TARGET, JITTER_UNDER_COLOR, targetJitterLevel, 5, 0f, 15f);
							targetData.target.setJitter(KEY_TARGET, color, targetJitterLevel, 3, 0f, 5f);
						}
					}
				}
				if(Global.getCombatEngine().getCustomData().get("AFCSS_ACTIVE_"+id) == null)
					Global.getCombatEngine().getCustomData().put("AFCSS_ACTIVE_"+id, affectedShips);
			}
		//}
		
		
		
	}
	
	public void unapply(MutableShipStatsAPI stats, String id) 
	{

		Global.getCombatEngine().getCustomData().put("AFCSS_ACTIVE_"+id, null);			
		//affectedShips.clear();

	}
	
	public StatusData getStatusData(int index, State state, float effectLevel) 
	{
		if (index == 0) {
			return new StatusData("Active. . .", false);
		}
		return null;
	}

}
