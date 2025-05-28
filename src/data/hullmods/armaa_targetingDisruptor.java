package data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.Global;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.awt.*;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;

public class armaa_targetingDisruptor extends BaseHullMod {

    public static Map DAMAGE_MAP = new HashMap();
    static {
        DAMAGE_MAP.put(HullSize.FIGHTER, 1f);
        DAMAGE_MAP.put(HullSize.FRIGATE, 1.20f);
        DAMAGE_MAP.put(HullSize.DESTROYER, 1.10f);
        DAMAGE_MAP.put(HullSize.CRUISER, 0.90f);
        DAMAGE_MAP.put(HullSize.CAPITAL_SHIP, 0.80f);
    }
	private List<ShipAPI> targets;		
	private int autoAimMalus = 20;
	private IntervalUtil interval = new IntervalUtil(0.05f,0.05f);		
	public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {

	}
    @Override
    public void advanceInCombat(ShipAPI ship, float amount)
    {
		interval.advance(amount);
		if(interval.intervalElapsed())
		{
			if(Global.getCombatEngine().getCustomData().get("armaa_targetingDisruptorShips_"+ship.getId()) instanceof List)
				targets = (List)Global.getCombatEngine().getCustomData().get("armaa_targetingDisruptorShips_"+ship.getId());
			
			else
				targets = new ArrayList<>();
			List<ShipAPI> toRemove = new ArrayList<>();	
			for(ShipAPI target:CombatUtils.getShipsWithinRange(ship.getLocation(), 1000f))
			{
				if(target.getHullSize() != HullSize.CRUISER && target.getHullSize() != HullSize.CAPITAL_SHIP)
					continue;
				if(target == ship)
					continue;
				if(ship.getOwner() == target.getOwner())
					continue;
				
				if(!targets.contains(target))
				{
					Global.getCombatEngine().addFloatingText(target.getLocation(), "Targeting disrupted!", 12f, Color.yellow, target, 1f, 1f); 
					Global.getCombatEngine().spawnEmpArcVisual(ship.getLocation(), ship, target.getLocation(), target, 30f, Color.yellow, Color.white); 
					targets.add(target);
				}
			}			
			for(ShipAPI nearShip: targets)
			{
				if(MathUtils.getDistance(nearShip, ship) <= 1000f)
				{
					if(nearShip.getOwner() == ship.getOwner())
						continue;
					float distance = MathUtils.getDistance(ship, nearShip);
					float mult = 1f; // default no debuff

					if(Math.random() < 0.01f)
					{
						Global.getCombatEngine().addFloatingText(nearShip.getLocation(), "Targeting disrupted!", 12f, Color.yellow, nearShip, 1f, 1f); 
						Global.getCombatEngine().spawnEmpArcVisual(nearShip.getLocation(), ship, nearShip.getLocation(), nearShip, 30f, Color.white, Color.white); 
					}
					if (distance <= 700f) {
						mult = (float)DAMAGE_MAP.get(nearShip.getHullSize()); // 20% autoaim accuracy penalty at max effect
					} else if (distance <= 1000f) {
						float ratio = (distance - 700f) / 300f; // 0 at 700, 1 at 1000
						mult = (float)DAMAGE_MAP.get(nearShip.getHullSize()) + (1f - (float)DAMAGE_MAP.get(nearShip.getHullSize())) * ratio;
					}				
					nearShip.getMutableStats().getAutofireAimAccuracy().modifyMult("armaa_targetingDisruptor",mult);
					
				}
				else
				{
					nearShip.getMutableStats().getAutofireAimAccuracy().unmodify("armaa_targetingDisruptor");
					toRemove.add(nearShip);					
				}				
			}
			for(ShipAPI remove:toRemove)
			{
				targets.remove(remove);
			}				
			Global.getCombatEngine().getCustomData().put("armaa_targetingDisruptorShips_"+ship.getId(),targets);			
		}
	}		
	public String getDescriptionParam(int index, HullSize hullSize) {
		if (index == 0) return "" + DAMAGE_MAP.get(HullSize.FRIGATE)+"x /"+DAMAGE_MAP.get(HullSize.DESTROYER)+"x";
		//if (index == 1) return "" + (int) BEAM_DAMAGE_PENALTY;
		if (index == 1) return "" + DAMAGE_MAP.get(HullSize.CRUISER)+"x /"+DAMAGE_MAP.get(HullSize.CAPITAL_SHIP)+"x";
		if (index == 2) return "" + autoAimMalus+"%";		
		return null;
	}
}
