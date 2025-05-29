package data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import java.util.HashMap;
import java.util.Map;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

public class armaa_alesteMeleeSwap extends BaseHullMod{
	private String meleeSlot =  "C_ARML";
	private Map<Integer,String> defSlots = new HashMap<>();
	{
		defSlots.put(0,"armaa_aleste_blade_LeftArm");
		defSlots.put(1,"armaa_alestePilebunkerLeft");		
	}
    private final Map<Integer,String> SWITCH = new HashMap<>();
    {
       SWITCH.put(1,"armaa_aleste_blade");
       SWITCH.put(0,"armaa_alestePilebunker");
    }
    private final Map<String,String> SWITCH_TO = new HashMap<>();
    {
       SWITCH_TO.put("armaa_alestePilebunkerLeft","armaa_aleste_blade_LeftArm");
       SWITCH_TO.put("armaa_alestePilebunkerLeft_s","armaa_aleste_blade_LeftArm_s");	   
       SWITCH_TO.put("armaa_aleste_blade_LeftArm","armaa_alestePilebunkerLeft");
       SWITCH_TO.put("armaa_aleste_blade_LeftArm_s","armaa_alestePilebunkerLeft_s");	
    }
    private final Map<String,String> SWITCH_TO_HULLMOD = new HashMap<>();
    {
       SWITCH_TO_HULLMOD.put("armaa_alestePilebunkerLeft","armaa_alestePilebunker");
       SWITCH_TO_HULLMOD.put("armaa_aleste_blade_LeftArm","armaa_aleste_blade");
       SWITCH_TO_HULLMOD.put("armaa_alestePilebunkerLeft_s","armaa_alestePilebunker");	   
       SWITCH_TO_HULLMOD.put("armaa_aleste_blade_LeftArm_s","armaa_aleste_blade");		   
    }	
       
    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) 
	{
		if(stats != null)
		{
			boolean toSwitch = true;			
			for(int i=0; i<SWITCH.size(); i++)
			{
				if(stats.getVariant().getHullMods().contains(SWITCH.get(i)))
				{
					toSwitch = false;
					break;
				}
			}					
			ShipVariantAPI variant=stats.getVariant();
			if(!toSwitch)
				return;
			boolean random = false;
			String currentWep = variant.getWeaponId(meleeSlot);
			if(currentWep == null)
			{
				if(Math.random() > 0.50f)
				currentWep = "armaa_aleste_blade_LeftArm";
				else
				currentWep = "armaa_alestePilebunkerLeft";					
			}
			variant.clearSlot(meleeSlot);
			variant.addWeapon(meleeSlot,SWITCH_TO.get(currentWep));
			variant.addMod(SWITCH_TO_HULLMOD.get(SWITCH_TO.get(currentWep)));
		}
    }
	
 	@Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) 
	{
		if(isForModSpec)
			return;
		
	}		

		    
    @Override
    public String getDescriptionParam(int index, HullSize hullSize) { 
        if (index == 0) return "A";
        if (index == 1) return "B";
        if (index == 2) return "C";
        if (index == 3) return "D";              
        return null;
    }
    
    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return ( ship.getHullSpec().getHullId().startsWith("armaa_"));	
    }
}
