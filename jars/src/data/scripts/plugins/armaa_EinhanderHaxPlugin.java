package data.scripts.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import java.util.*;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.*;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lazywizard.lazylib.CollisionUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;
import java.awt.Color;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.lwjgl.input.Keyboard;

//import org.lazywizard.console.commands.ToggleAI;

//this script manages things that otherwise wouldn't be possible via hullmod; primarily ensuring fake fighters being only ship left on field doesnt end combat 
//and ensuring its always selectable in UI
public class armaa_EinhanderHaxPlugin extends BaseEveryFrameCombatPlugin
{
    protected CombatEngineAPI engine;
	protected List<ShipAPI> cataphrachtii = new ArrayList<ShipAPI>();
	protected List<ShipAPI> toRemove = new ArrayList<ShipAPI>();
	protected Map<ShipAPI,FleetMemberAPI> modulesToUpdate = new HashMap();
	private static final String DATA_KEY = "armaa_purge_data";
	int count = 0;
	boolean canEnd = false;
	private IntervalUtil interval = new IntervalUtil(.025f, .05f);
	private IntervalUtil interval2 = new IntervalUtil(.05f, .05f);
	
	private static class armaa_purgedata {
		boolean keyPressed = false;
		boolean ejectKeyPressed = false;
		long startTime = System.currentTimeMillis();
    }	
	public boolean isKeyDoubleTapped(ShipAPI ship) 
	{
		if(engine.getPlayerShip() != ship)
			return false;
		
		boolean xPressed = Keyboard.isKeyDown(Keyboard.getKeyIndex("X"));
		
		String key = "armaa_armorEject" + "_" + ship.getId();
		
		armaa_purgedata data = (armaa_purgedata) engine.getCustomData().get(key);
        if (data == null) {
            data = new armaa_purgedata();
            engine.getCustomData().put(key, data);
        }		

			if(xPressed)
			{
				long now = System.currentTimeMillis();	  
				if(now > data.startTime + 300)
				{
					data.startTime = now;
					data.keyPressed = false;
					return false;
				}
				else
				{
					if(data.keyPressed)
					{
						data.keyPressed = false;
						return true;
					}
					
					else
						data.keyPressed = true;
				}
			}		
			if(!data.keyPressed){
			  data.ejectKeyPressed = false;
			}
		return false;
	}
	
    @Override
    public void advance(float amount, List<InputEventAPI> events)
    {
		if(engine == null)
			return;
			
		interval.advance(amount);
		if(interval.intervalElapsed())
		{
			for (MissileAPI missile : engine.getMissiles())
			{
				if(missile.getWeapon() != null)
				{
					if(!missile.getWeapon().getId().equals("armaa_sprigganTorso"))
						continue;
						if(missile.isFizzling() || missile.isFading())
						{
							if(MagicRender.screenCheck(0.25f, missile.getLocation())){
								engine.addSmoothParticle(missile.getLocation(), new Vector2f(), 50, 0.5f, 0.15f, Color.blue);
								engine.addHitParticle(missile.getLocation(), new Vector2f(), 50, 1f, 0.25f, new Color(250,192,250,255));
							}
							engine.removeEntity(missile);
							return;
						}
					}
				}
			
			for (ShipAPI ship : engine.getShips())
			{
				if(ship.isFighter())
					continue;
				if(ship.getHullSpec().getHullId().contains("armaa_garegga_xiv"))
				{
					if(!cataphrachtii.contains(ship))
					{
						cataphrachtii.add(ship);
					}
				}
			}
			
		}
		if(engine.isCombatOver() || engine.isEnemyInFullRetreat())
		{
			for(ShipAPI ship : engine.getShips())
			{
				//Global.getCombatEngine().maintainStatusForPlayerShip("debug", "graphics/ui/icons/icon_repair_refit.png","Retreat OK" ,"" ,true);
				if(modulesToUpdate.get(ship) != null)
				{
					FleetMemberAPI member = modulesToUpdate.get(ship);
					member.getStatus().setHullFraction(ship.getHullLevel());
					member.getStatus().repairDisabledABit();
					member.getStatus().repairFully();
					member.updateStats();
					modulesToUpdate.remove(ship);
					//Global.getCombatEngine().maintainStatusForPlayerShip("debug", "graphics/ui/icons/icon_repair_refit.png",""+member.getStatus().isDetached(0),"" ,true);
					
				}
			}
		}
		if(cataphrachtii.isEmpty() || engine.isEnemyInFullRetreat() || engine.isCombatOver() || engine.getFleetManager(0).getTaskManager(false).isInFullRetreat())
		{
			engine.setDoNotEndCombat(false);
			canEnd = true;
			//Global.getCombatEngine().maintainStatusForPlayerShip("debug", "graphics/ui/icons/icon_repair_refit.png","Retreat OK" ,"" ,true);
		}
		
		if(cataphrachtii.isEmpty())
		{
			return;
		}

		if(!cataphrachtii.isEmpty())
		{
			for(ShipAPI ship:cataphrachtii)
			{
				List<ShipAPI> children = ship.getChildModulesCopy();
				if(children != null)
				{
					for(ShipAPI module: children)	
					{
						module.ensureClonedStationSlotSpec();
						if(module.getStationSlot() != null && !module.controlsLocked())
						{
							module.blockCommandForOneFrame(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK);
							if(!module.isAlive() || module.getLocation().getY() == -1000000f)
							{
								module.setControlsLocked(true); 
								ship.getFluxTracker().showOverloadFloatyIfNeeded("Core unit destroyed! Controls locked!", Color.red, 10f, true);	
								ship.setControlsLocked(true);
								module.setStationSlot(null);
								toRemove.add(ship);	
								ship.resetDefaultAI();						
							}
							if(isKeyDoubleTapped(ship) || engine.getPlayerShip() != ship && ship.getHullLevel() < 0.5f)
							{
								module.getFluxTracker().showOverloadFloatyIfNeeded("Emergency Purge!", Color.blue, 4f, true);								
								FleetMemberAPI f = Global.getFactory().createFleetMember(FleetMemberType.SHIP,module.getVariant().clone());
								engine.getFleetManager(ship.getOwner()).addToReserves(f);
								ship.getFleetMember().getFleetData().removeFleetMember(module.getFleetMember());
								ShipAPI s = engine.getFleetManager(ship.getOwner()).spawnFleetMember(f,module.getLocation(),module.getFacing(),0f);
								if(ship.isAlly())
									s.setAlly(true);
								s.setHitpoints(module.getHitpoints());
								if(engine.getPlayerShip() == ship)
								{
									engine.getCustomData().put("armaa_playerTransferTarget_"+ship.getId(),s);
									engine.setPlayerShipExternal(s);	
								}
								module.setControlsLocked(true); 
								module.fadeToColor(module,new Color(0,0,0,0),9999f,9999f,9999f);
								f = null;		
								//s = null;
								if(!engine.isSimulation())
								{
									//doesnt work LOL
									Map<String,FleetMemberAPI> modulePair = new HashMap<>();
									modulesToUpdate.put(s,module.getFleetMember());
								}
								//module.setStationSlot(null);								
								ship.setControlsLocked(true);
								s.setCaptain(ship.getCaptain());
								ship.setCaptain(null);
								toRemove.add(ship);								
							}
						}				
					}
				}
			}
		}
			if(!toRemove.isEmpty())
			{
				cataphrachtii.removeAll(toRemove);
			}
	}

    @Override
    public void init(CombatEngineAPI engine)
    {
        this.engine = engine;
    }
}
