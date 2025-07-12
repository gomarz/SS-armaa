package data.scripts.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import java.util.*;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;
import java.awt.Color;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import data.scripts.util.armaa_utils;
import org.lwjgl.input.Keyboard;


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
	private IntervalUtil interval3 = new IntervalUtil(2f, 2f);
	private static class armaa_purgedata {
		boolean keyPressed = false;
		boolean ejectKeyPressed = false;
		long startTime = System.currentTimeMillis();
    }	
	public boolean isKeyDoubleTapped(ShipAPI ship) {
		if (engine.getPlayerShip() != ship)
			return false;

		boolean xPressed = Keyboard.isKeyDown(Keyboard.getKeyIndex("X"));
		boolean result = false;
		
		String key = "armaa_armorEject" + "_" + ship.getId();

		armaa_purgedata data = (armaa_purgedata) engine.getCustomData().get(key);
		if (data == null) {
			data = new armaa_purgedata();
			engine.getCustomData().put(key, data);
		}

		if (xPressed && !data.ejectKeyPressed) 
		{
			long now = System.currentTimeMillis();			
			if (now > data.startTime + 300) 
			{
				data.startTime = now;

			} 
			else 
			{
				result = true;
			}
			data.ejectKeyPressed = true;
		} else if(!xPressed) {
			// Key is not pressed
			data.ejectKeyPressed = false;
		}

		Global.getCombatEngine().getCustomData().put(key, data);
		return result;
	}


    @Override
    public void advance(float amount, List<InputEventAPI> events)
    {
		if(engine == null)
			return;

		if(!engine.isPaused())
		{
			if(engine.getContext().getOtherFleet() != null && engine.getContext().getOtherFleet().getMemoryWithoutUpdate().contains("$inAtmoBattle") && engine.getCustomData().get("armaa_atmoPlugin") == null)
			{
				engine.addPlugin(new armaa_atmosphericBattlePlugin());
				engine.getCustomData().put("armaa_atmoPlugin","-");
			}
			else if(engine.getContext().getOtherFleet() != null && engine.getContext().getOtherFleet().getMemoryWithoutUpdate().contains("$inAtmoBossBattle") && engine.getCustomData().get("armaa_atmoPlugin") == null)
			{
				engine.addPlugin(new armaa_atmosphericBossBattlePlugin());
				engine.getCustomData().put("armaa_atmoPlugin","-");
			}			
			else if(engine.getContext().getOtherFleet() != null && engine.getContext().getOtherFleet().getMemoryWithoutUpdate().contains("$inCityBattle") && engine.getCustomData().get("armaa_atmoPlugin") == null)
			{
				engine.addPlugin(new armaa_cityBattlePlugin());
				engine.getCustomData().put("armaa_atmoPlugin","-");
			}
			else if(engine.getContext().getOtherFleet() != null && engine.getContext().getOtherFleet().getMemoryWithoutUpdate().contains("$inShaftBattle") && engine.getCustomData().get("armaa_atmoPlugin") == null)
			{
				engine.addPlugin(new armaa_shaftBattlePlugin());
				engine.getCustomData().put("armaa_atmoPlugin","-");
			}
			else if(engine.getContext().getOtherFleet() != null && engine.getContext().getOtherFleet().getMemoryWithoutUpdate().contains("$inGravionBattle") && engine.getCustomData().get("armaa_atmoPlugin") == null)
			{
				engine.addPlugin(new armaa_gasGiantBattlePlugin());
				engine.getCustomData().put("armaa_atmoPlugin","-");
			}				
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

					if(ship.getVariant().getHullMods().contains("armaa_comboUnit"))
					{
						if(!cataphrachtii.contains(ship) && !ship.controlsLocked())
						{
							cataphrachtii.add(ship);
						}
					}
				}
				
			}
		}
		if(engine.isCombatOver() || engine.isEnemyInFullRetreat())
		{
			for(ShipAPI ship : engine.getShips())
			{
				if(modulesToUpdate.get(ship) != null)
				{
					if(ship.getOwner() == 0)
					{
						FleetMemberAPI member = modulesToUpdate.get(ship);
						member.getStatus().setHullFraction(ship.getHullLevel());
						member.getStatus().repairDisabledABit();
						member.getStatus().repairFully();
						member.updateStats();
					}
					modulesToUpdate.remove(ship);

				}
			}
		}
		/*if(cataphrachtii.isEmpty() || engine.isEnemyInFullRetreat() || engine.isCombatOver() || engine.getFleetManager(0).getTaskManager(false).isInFullRetreat())
		{
			engine.setDoNotEndCombat(false);
			canEnd = true;
		}*/
		
		if(cataphrachtii.isEmpty())
		{
			return;
		}

		if(!cataphrachtii.isEmpty())
		{
			interval2.advance(amount);
			if(interval2.intervalElapsed())
			for(ShipAPI ship:cataphrachtii)
			{
				if(!ship.isAlive())
				{
					toRemove.add(ship);
					continue;
				}
				boolean doubletapped = isKeyDoubleTapped(ship);
				if(!doubletapped && engine.getPlayerShip() == ship)
					continue;
					
				List<ShipAPI> children = ship.getChildModulesCopy();

				if(children == null || children.size() == 0)
					continue;
				if(children != null)
				{
					for(ShipAPI module: children)	
					{
						module.ensureClonedStationSlotSpec();
						if(module.getStationSlot() != null && !module.controlsLocked())
						{
							if(!module.isAlive() || module.getLocation().getY() == -1000000f)
							{
								module.setControlsLocked(true); 
								ship.getFluxTracker().showOverloadFloatyIfNeeded("Core unit destroyed! Controls locked!", Color.red, 10f, true);	
								ship.setControlsLocked(true);
								module.setStationSlot(null);
								toRemove.add(ship);	
								ship.resetDefaultAI();						
							}
							if(doubletapped || engine.getPlayerShip() != ship && (ship.getHullLevel() < 0.50f || ship.getFluxTracker().isOverloaded()))
							{
								module.getFluxTracker().showOverloadFloatyIfNeeded("Emergency Purge!", Color.blue, 4f, true);								
								FleetMemberAPI f = Global.getFactory().createFleetMember(FleetMemberType.SHIP,module.getVariant().clone());
								
								//If you're one of the player's ships, we should set commander to player
								//Else, default behavior
								PersonAPI commander = ship.getCaptain();
								if(ship.getFleetMember().getFleetData() != null)
									commander = ship.getOwner() == 0 && !ship.isAlly() ? ship.getCaptain() : ship.getFleetMember().getFleetData().getCommander();
								f.setFleetCommanderForStats(commander,ship.getFleetMember().getFleetDataForStats());
								f.setCaptain(ship.getCaptain());
								f.setOwner(ship.getOwner());
								ship.getFleetMember().getCrewComposition().transfer(2,f.getCrewComposition());
								f.getRepairTracker().setCR(Math.min(1f,ship.getCRAtDeployment()));
								f.updateStats();
								if(ship.getOwner() == 0 && !ship.isAlly())
								{
									engine.getFleetManager(ship.getOwner()).addToReserves(f);
									if(module.getFleetMember() != null && ship.getFleetMember().getFleetData() != null)
									ship.getFleetMember().getFleetData().removeFleetMember(module.getFleetMember());
								}
								ShipAPI s = null;
								s = engine.getFleetManager(ship.getOwner()).spawnFleetMember(f,module.getLocation(),module.getFacing(),0f);	
								if(ship.isAlly())
									s.setAlly(true);
								s.setHitpoints(module.getHitpoints());
								if(engine.getPlayerShip() == ship)
								{
									engine.getCustomData().put("armaa_playerTransferTarget_"+ship.getId(),s);
									engine.setPlayerShipExternal(s);	
								}

								module.fadeToColor(module,new Color(0,0,0,0),9999f,9999f,9999f);
								f = null;		
								//s = null;
								if(!engine.isSimulation() && ship.getOwner() == 0 && !ship.isAlly())
								{
									//doesnt work LOL
									Map<String,FleetMemberAPI> modulePair = new HashMap<>();
									modulesToUpdate.put(s,module.getFleetMember());
								}		
								
								ship.setControlsLocked(true);
								module.setControlsLocked(true);
								
								if(s.getCaptain() != ship.getCaptain())
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
