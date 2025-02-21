package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.input.Keyboard;
import org.lazywizard.lazylib.MathUtils;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import org.apache.log4j.Logger;

public class armaa_alesteSkinSwap extends BaseHullMod{
	private List<String> decoSlots = new ArrayList<String>();
	{
		decoSlots.add("A_GUN");
		decoSlots.add("B_TORSO");
		decoSlots.add("D_PAULDRONL");
		decoSlots.add("D_PAULDRONR");
		decoSlots.add("E_HEAD");
	}
	private Map<String,String> defSlots = new HashMap<>();
	{
		defSlots.put("A_GUN","armaa_aleste_variablerifle");
		defSlots.put("B_TORSO","armaa_aleste_torso_ace");
		defSlots.put("D_PAULDRONL","armaa_aleste_leftPauldron");
		defSlots.put("D_PAULDRONR","armaa_aleste_rightPauldron");
		defSlots.put("E_HEAD","armaa_alesteHead");
	}
    private final Map<Integer,String> SWITCH = new HashMap<>();
    {
       SWITCH.put(0,"armaa_alesteSkin_sephira");
       SWITCH.put(1,"armaa_alesteSkin_default");
    }
       
    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) 
	{
		if(stats != null)
		{
			boolean toSwitch = true;			
			for(int i=0; i<SWITCH.size(); i++){
				if(stats.getVariant().getHullMods().contains(SWITCH.get(i)))
				{
					toSwitch = false;
					break;
				}
			}					
			ShipVariantAPI variant=stats.getVariant();
			if(!toSwitch)
				return;
			for(String decoSlot : decoSlots)
			{
				String newPart = "";				
				if(variant.getWeaponSpec(decoSlot) == null)
				{
					if(defSlots.get(decoSlot) == null)
						return;
					newPart = defSlots.get(decoSlot);
					variant.addWeapon(decoSlot,newPart);
					continue;
				}
				else if(!defSlots.containsValue(variant.getWeaponSpec(decoSlot).getWeaponId()))
				{
					if(defSlots.containsValue(variant.getWeaponSpec(decoSlot).getWeaponId().substring(0, variant.getWeaponSpec(decoSlot).getWeaponId().length() - 2)))
					{
						variant.addMod("armaa_alesteSkin_default");
						newPart = defSlots.get(decoSlot);
					}
					else
						return;
					//return;
				}					
				else if(!variant.getWeaponSpec(decoSlot).getWeaponId().contains("_s"))
				{
					newPart = variant.getWeaponSpec(decoSlot).getWeaponId()+"_s";
				}
				else
				{
					newPart =  variant.getWeaponSpec(decoSlot).getWeaponId().substring(0, variant.getWeaponSpec(decoSlot).getWeaponId().length() - 2);
				}
				variant.clearSlot(decoSlot);
				if(!newPart.equals("armaa_aleste_blade_RightArm_s"))
				variant.addWeapon(decoSlot,newPart);
			}
			if(variant.getWeaponSpec(decoSlots.get(0)) == null)
				return;
			String currentMelee = variant.getWeaponId("C_ARML");
			variant.clearSlot("C_ARML");
			if(variant.getWeaponSpec(decoSlots.get(0)).getWeaponId().contains("_s"))
			{
				variant.addMod("armaa_alesteSkin_sephira");
				if(currentMelee.contains("_s"))
				{
					variant.addWeapon("C_ARML",currentMelee);
				}
				else
					variant.addWeapon("C_ARML",currentMelee+"_s");
					
			}
			else
			{				
				variant.addMod("armaa_alesteSkin_default");
				if(!currentMelee.contains("_s"))
				{
					variant.addWeapon("C_ARML",currentMelee);
				}
				else
				{
					currentMelee = currentMelee.replace("_s", "");
					variant.addWeapon("C_ARML",currentMelee);
				}
			}
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
