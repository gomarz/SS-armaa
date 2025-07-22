package data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.*;
import java.util.List;
import java.util.ArrayList;

public class armaa_legEngineCheck extends BaseHullMod {
	
	private boolean isWeaponSwapped = false;
	//private ShipAPI anchor;
	private boolean anchordead=false;	
	private boolean runOnce = false;
	protected List<ShipAPI> toRemove = new ArrayList<ShipAPI>();
	//protected List<ShipAPI> children = new ArrayList<ShipAPI>();
	
		@Override
		public void advanceInCombat(ShipAPI ship, float amount) 
		{
			ShipAPI parent = ship.getParentStation();
			CombatEngineAPI engine = Global.getCombatEngine();
			
			if(parent != null)
			getBP(parent,engine);
		
			else return;
			
					if(Global.getCombatEngine().getCustomData().get("armaa_backpack"+parent.getId()) instanceof String)
					{
						String str =(String)Global.getCombatEngine().getCustomData().get("armaa_backpack"+parent.getId());

						
						if(str == parent.getId())
						{
							
							List<ShipAPI> children = parent.getChildModulesCopy();
									boolean match = true;
									for(ShipAPI m: children)
									{
										m.ensureClonedStationSlotSpec();
										
										if(m.getStationSlot()!= null)
										{
											if(m.getStationSlot().getId().equals("SKIRT"))
											{
												//backpack still exists or is alive
												match = false;
												//break;
											}
										}
									}
									
							if(match == true)
							{
								ship.setHitpoints(0);
							}
						}
					}
		}

	public void getBP(ShipAPI ship, CombatEngineAPI engine)
	{
		List<ShipAPI> children = ship.getChildModulesCopy();

		for(ShipAPI m: children)
		{
			m.ensureClonedStationSlotSpec();

			if(m.getStationSlot() != null) 
			{
				if(!m.getStationSlot().getId().equals("SKIRT"))
					continue;
				
				else
				{
					
					Global.getCombatEngine().getCustomData().put("armaa_backpack"+ship.getId(),ship.getId());
					
					break;
				}	
			}

		}
	}

}

