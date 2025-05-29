package data.scripts.shipsystems;

import java.awt.Color;
import java.util.List;
import com.fs.starfarer.api.util.IntervalUtil;

import data.scripts.weapons.armaa_AWACS;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipSystemAPI.SystemState;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.Misc;
import org.magiclib.util.MagicRender;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.graphics.SpriteAPI;

public class armaa_AWACSSys extends BaseShipSystemScript {
	public static Object KEY_SHIP = new Object();
	public static Object KEY_TARGET = new Object();
	
	public static float DAM_MULT = 1.25f;
	protected static float RANGE = 2000f;
	
	public static Color TEXT_COLOR = new Color(255,55,55,255);
	
	public static Color JITTER_COLOR = new Color(255,50,50,75);
	public static Color JITTER_UNDER_COLOR = new Color(255,100,100,155);

	
	public static class TargetData {
		public ShipAPI ship;
		public ShipAPI target;
		public EveryFrameCombatPlugin targetEffectPlugin;
		public float currDamMult;
		public float elaspedAfterInState;
		public TargetData(ShipAPI ship, ShipAPI target) {
			this.ship = ship;
			this.target = target;
		}
	}
	
	
	public void apply(MutableShipStatsAPI stats, final String id, State state, float effectLevel) {
		ShipAPI ship = null;
		if (stats.getEntity() instanceof ShipAPI) {
			ship = (ShipAPI) stats.getEntity();
		} else {
			return;
		}
		
		final String targetDataKey = ship.getId() + "_entropy_target_data";
		
		Object targetDataObj = Global.getCombatEngine().getCustomData().get(targetDataKey); 
		if (state == State.IN && targetDataObj == null) {
			ShipAPI target = findTarget(ship);
			Global.getCombatEngine().getCustomData().put(targetDataKey, new TargetData(ship, target));
			if (target != null) {
				if (target.getFluxTracker().showFloaty() || 
						ship == Global.getCombatEngine().getPlayerShip() ||
						target == Global.getCombatEngine().getPlayerShip()) {
					target.getFluxTracker().showOverloadFloatyIfNeeded("Weaknesses Analyzed!", TEXT_COLOR, 4f, true);
				}
			}
		} else if (state == State.IDLE && targetDataObj != null) {
			Global.getCombatEngine().getCustomData().remove(targetDataKey);
			((TargetData)targetDataObj).currDamMult = 1f;
			targetDataObj = null;
		}
		if (targetDataObj == null || ((TargetData) targetDataObj).target == null) return;
		
		final TargetData targetData = (TargetData) targetDataObj;
        float scale = armaa_AWACS.getEffectLevel();
		targetData.currDamMult = 1f + (DAM_MULT - 1f) * effectLevel;
		if (targetData.targetEffectPlugin == null) {
			targetData.targetEffectPlugin = new BaseEveryFrameCombatPlugin() 
			{
				boolean hasSprite = false;
				IntervalUtil interval = new IntervalUtil(1.5f,1.5f);
				@Override				
				public void advance(float amount, List<InputEventAPI> events) 
				{
					if (Global.getCombatEngine().isPaused()) return;
					
					if (targetData.target == Global.getCombatEngine().getPlayerShip()) { 
						Global.getCombatEngine().maintainStatusForPlayerShip(KEY_TARGET, 
								targetData.ship.getSystem().getSpecAPI().getIconSpriteName(),
								targetData.ship.getSystem().getDisplayName(), 
								"" + (int)((targetData.currDamMult - 1f) * 100f) + "% more damage taken", true);
					}
					
								interval.advance(amount);
								SpriteAPI waveSprite = Global.getSettings().getSprite("ceylon", "armaa_ceylontarget");

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
												new Color(215,21,16,255), 
												true,
												.2f,
												1f,
												.3f,
												true
										);
								}
					
					if (targetData.currDamMult <= 1f || !targetData.ship.isAlive() || !targetData.ship.getSystem().isActive()) {
						targetData.target.getMutableStats().getHullDamageTakenMult().unmodify(id);
						targetData.target.getMutableStats().getArmorDamageTakenMult().unmodify(id);
						targetData.target.getMutableStats().getShieldDamageTakenMult().unmodify(id);
						targetData.target.getMutableStats().getEmpDamageTakenMult().unmodify(id);
						Global.getCombatEngine().getCustomData().remove(targetDataKey);
						Global.getCombatEngine().removePlugin(targetData.targetEffectPlugin);
					} else {
						targetData.target.getMutableStats().getHullDamageTakenMult().modifyMult(id, targetData.currDamMult);
						targetData.target.getMutableStats().getArmorDamageTakenMult().modifyMult(id, targetData.currDamMult);
						targetData.target.getMutableStats().getShieldDamageTakenMult().modifyMult(id, targetData.currDamMult);
						targetData.target.getMutableStats().getEmpDamageTakenMult().modifyMult(id, targetData.currDamMult);
					}
				}
			};
			Global.getCombatEngine().addPlugin(targetData.targetEffectPlugin);
		}
		
		
		if (effectLevel > 0) {
			if (state != State.IN) {
				targetData.elaspedAfterInState += Global.getCombatEngine().getElapsedInLastFrame();
			}
			float shipJitterLevel = 0;
			if (state == State.IN) {
				shipJitterLevel = effectLevel;
			} else {
				float durOut = 0.5f;
				shipJitterLevel = Math.max(0, durOut - targetData.elaspedAfterInState) / durOut;
			}
			float targetJitterLevel = effectLevel;
			
			float maxRangeBonus = 50f;
			float jitterRangeBonus = shipJitterLevel * maxRangeBonus;
			
			Color color = JITTER_COLOR;
			if (shipJitterLevel > 0) {
				//ship.setJitterUnder(KEY_SHIP, JITTER_UNDER_COLOR, shipJitterLevel, 21, 0f, 3f + jitterRangeBonus);
				ship.setJitter(KEY_SHIP, color, shipJitterLevel, 4, 0f, 0 + jitterRangeBonus * 1f);
			}
			
			if (targetJitterLevel > 0) {
				//target.setJitterUnder(KEY_TARGET, JITTER_UNDER_COLOR, targetJitterLevel, 5, 0f, 15f);
				targetData.target.setJitter(KEY_TARGET, color, targetJitterLevel, 3, 0f, 5f);
			}
		}
	}
	
	
	public void unapply(MutableShipStatsAPI stats, String id) {
		
	}
	
	protected ShipAPI findTarget(ShipAPI ship) {
		float range = getMaxRange(ship);
		boolean player = ship == Global.getCombatEngine().getPlayerShip();
		ShipAPI target = ship.getShipTarget();
		if (target != null) {
			float dist = Misc.getDistance(ship.getLocation(), target.getLocation());
			float radSum = ship.getCollisionRadius() + target.getCollisionRadius();
			if (dist > range + radSum) target = null;
		} else {
			if (target == null || target.getOwner() == ship.getOwner()) {
				if (player) {
					target = Misc.findClosestShipEnemyOf(ship, ship.getMouseTarget(), HullSize.FIGHTER, range, true);
				} else {
					Object test = ship.getAIFlags().getCustom(AIFlags.MANEUVER_TARGET);
					if (test instanceof ShipAPI) {
						target = (ShipAPI) test;
						float dist = Misc.getDistance(ship.getLocation(), target.getLocation());
						float radSum = ship.getCollisionRadius() + target.getCollisionRadius();
						if (dist > range + radSum) target = null;
					}
				}
			}
			if (target == null) {
				target = Misc.findClosestShipEnemyOf(ship, ship.getLocation(), HullSize.FIGHTER, range, true);
			}
		}
		
		return target;
	}
	
	
	public static float getMaxRange(ShipAPI ship) {
        float effectLevel = armaa_AWACS.getEffectLevel();
		return ship.getMutableStats().getSystemRangeBonus().computeEffective(RANGE*effectLevel);
		//return RANGE;
	}

	
	public StatusData getStatusData(int index, State state, float effectLevel) {
		if (effectLevel > 0) {
			if (index == 0) {
				float damMult = 1f + (DAM_MULT - 1f) * effectLevel;
				return new StatusData("" + (int)((damMult - 1f) * 100f) + "% more damage to target", false);
			}
		}
		return null;
	}


	@Override
	public String getInfoText(ShipSystemAPI system, ShipAPI ship) {
		if (system.isOutOfAmmo()) return null;
		if (system.getState() != SystemState.IDLE) return null;
		
		ShipAPI target = findTarget(ship);
		if (target != null && target != ship) {
			return "READY";
		}
		if ((target == null) && ship.getShipTarget() != null) {
			return "OUT OF RANGE";
		}
		return "NO TARGET";
	}

	
	@Override
	public boolean isUsable(ShipSystemAPI system, ShipAPI ship) {
		//if (true) return true;
		ShipAPI target = findTarget(ship);
		return target != null && target != ship;
	}

}








