package data.scripts.shipsystems;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import data.scripts.util.armaa_utils;
import org.lazywizard.lazylib.MathUtils;
import org.magiclib.util.MagicLensFlare;


	public class armaa_RecallDeviceStats extends BaseShipSystemScript {
	public static final Object KEY_JITTER = new Object();
	public static final Color JITTER_COLOR = new Color(100,165,255,155);
    private static final Color BASIC_FLASH_COLOR = new Color(184, 155, 218, 200);
    private static final Color BASIC_GLOW_COLOR = new Color(180, 161, 255, 200);

	
	public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
		ShipAPI ship = null;
		if (stats.getEntity() instanceof ShipAPI) {
			ship = (ShipAPI) stats.getEntity();
		} else {
			return;
		}
		
		ShipAPI fighter = null;
		if (effectLevel > 0) {
			float jitterLevel = effectLevel;
			
			boolean firstTime = false;
			final String fightersKey = ship.getId() + "_recall_device_target";
			ShipAPI target = ship.getShipTarget();
			if(target != null && target.getOwner() == ship.getOwner() && target.getVariant().hasHullMod("strikeCraft"))
			{
				fighter = target;
	
			}
			else
			{
				List<ShipAPI> fighters = null;
				if (!Global.getCombatEngine().getCustomData().containsKey(fightersKey)) {
					fighters = getFighters(ship);
					Global.getCombatEngine().getCustomData().put(fightersKey, fighters);
					firstTime = true;
				} else {
					fighters = (List<ShipAPI>) Global.getCombatEngine().getCustomData().get(fightersKey);
				}
				if (fighters == null) { // shouldn't be possible, but still
					fighters = new ArrayList<ShipAPI>();
				}				
				for(ShipAPI fighter2 : fighters) 
				{	
					if (fighter2.isHulk()) continue;
					String recalledKey = "armaa_recalledFighter_"+ship.getId();
					ShipAPI recalledFighter = null;
					if(Global.getCombatEngine().getCustomData().get(recalledKey) == null)
					{
						Global.getCombatEngine().getCustomData().put(recalledKey,fighter2);
					}					
					else
					{
						fighter =(ShipAPI)Global.getCombatEngine().getCustomData().get(recalledKey);
						break;
					}
						fighter = fighter2;
						break;
				}
			}
			if(fighter == null)
			{
				return;
			}
			float maxRangeBonus = fighter.getCollisionRadius() * 1f;
			float jitterRangeBonus = 5f + jitterLevel * maxRangeBonus;
			
			if (firstTime) {
				Global.getSoundPlayer().playSound("system_phase_skimmer", 1f, 0.5f, fighter.getLocation(), fighter.getVelocity());
			}
			
			fighter.setJitter(KEY_JITTER, JITTER_COLOR, jitterLevel, 10, 0f, jitterRangeBonus);
			if (fighter.isAlive()) {
				//fighter.setPhased(true);
			}
			
			if (state == State.IN) {
				float alpha = 1f - effectLevel * 0.5f;
				fighter.setExtraAlphaMult(alpha);
			}

			if (effectLevel == 1) {
				if (ship != null) 
				{
					//fighter.setPhased(false);		
					armaa_utils.setLocation(fighter, MathUtils.getRandomPointInCircle(ship.getLocation(), 500f));
					fighter.setExtraAlphaMult(1f);
					MagicLensFlare.createSharpFlare(Global.getCombatEngine(),fighter,fighter.getLocation(),5f,100f,0f,BASIC_FLASH_COLOR,Color.white);
					Global.getCombatEngine().addSmoothParticle(fighter.getLocation(), new Vector2f(), 100f, 0.7f, 0.1f, BASIC_FLASH_COLOR);
					Global.getCombatEngine().addSmoothParticle(fighter.getLocation(), new Vector2f(), 150f, 0.7f, 1f, BASIC_GLOW_COLOR);
					Global.getCombatEngine().addHitParticle(fighter.getLocation(), new Vector2f(), 200f, 1f, 0.05f, Color.white);	
				} 
			}
		}
	}

	public static boolean isStrikeCraftNearCarrier(ShipAPI ship) 
	{
		List<ShipAPI> result = new ArrayList<ShipAPI>();
		
		for (ShipAPI carrier : Global.getCombatEngine().getShips()) 
		{
			if(ship.getOwner() != carrier.getOwner())
				continue;
			if(!carrier.hasLaunchBays())
				continue;
			if(carrier.getVariant().hasHullMod("strikeCraft"))
				continue;
			
			if(carrier.isAlive() && MathUtils.getDistance(ship,carrier) < 1000f)				
			{
				return true;
			}	
		}
		return false;
	}
	
	public static List<ShipAPI> getFighters(ShipAPI carrier) 
	{
		List<ShipAPI> result = new ArrayList<ShipAPI>();
		
		for (ShipAPI ship : Global.getCombatEngine().getShips()) 
		{
			if(ship.getOwner() != carrier.getOwner())
				continue;
			
			if(!ship.getVariant().hasHullMod("strikeCraft"))
				continue;
			if(ship.getHullLevel() <= 0.50f || ship.getCurrentCR() < .4f || ship.getFluxTracker().isOverloaded() || ship.getHullLevel() < 0.70f && !ship.areSignificantEnemiesInRange())	
			{
				if(ship.isAlive() && MathUtils.getDistance(ship,carrier) > 1000f && !ship.controlsLocked() && !ship.isRetreating() && !isStrikeCraftNearCarrier(ship))				
				{
					result.add(ship);
				}
			}
		}
		
		return result;
	}
	
	
	public void unapply(MutableShipStatsAPI stats, String id) {
		ShipAPI ship = null;
		if (stats.getEntity() instanceof ShipAPI) {
			ship = (ShipAPI) stats.getEntity();
		} else {
			return;
		}
		
		final String fightersKey = ship.getId() + "_recall_device_target";
		String recalledKey = "armaa_recalledFighter_"+ship.getId();	
		Global.getCombatEngine().getCustomData().remove(fightersKey);
		Global.getCombatEngine().getCustomData().remove(recalledKey);		
//		for (ShipAPI fighter : getFighters(ship)) {
//			fighter.setPhased(false);
//			fighter.setCopyLocation(null, 1f, fighter.getFacing());
//		}
	}
	
	
	public StatusData getStatusData(int index, State state, float effectLevel) {
		return null;
	}

	
}








