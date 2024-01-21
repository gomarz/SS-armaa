package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import java.util.HashMap;
import java.util.Map;
import org.lazywizard.lazylib.MathUtils;
import org.apache.log4j.Logger;

public class armaa_vxSkinSwap extends BaseHullMod{
        
    private final Map<Integer,String> SWITCH = new HashMap<>();
    {
        SWITCH.put(0,"armaa_vxSkin_AASV");
        SWITCH.put(1,"armaa_vxSkin_rocketStars");
		SWITCH.put(2,"armaa_vxSkin_midline");
    }

    private final Map<Integer,String> SWITCH_WEP = new HashMap<>();
    {
        SWITCH_WEP.put(2,"armaa_kouto_variablerifle");
        SWITCH_WEP.put(1,"armaa_valkenx_frig_variablerifle");
		SWITCH_WEP.put(0,"armaa_valkenx_frig_ml_variablerifle");
    }
    private final Map<String,Integer> SWITCHWEP = new HashMap<>();
    {
        SWITCHWEP.put("armaa_kouto_variablerifle",1);
        SWITCHWEP.put("armaa_valkenx_frig_variablerifle",0);
		SWITCHWEP.put("armaa_valkenx_frig_ml_variablerifle",2);
    }		
	
    private final String decoGunID = "A_GUN";
	private String currentSkin = "armaa_vxSkin_AASV";
	
    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) 
	{
		if(stats == null)
			return;
		//Logger logger = Logger.getLogger(this.getClass());	

		boolean toSwitchLeft = true;
		int selected = 0;
        for(int i=0; i<SWITCH.size(); i++){
            if(stats.getVariant().getHullMods().contains(SWITCH.get(i)))
			{
                toSwitchLeft = false;
				break;
            }
        }		

        if(toSwitchLeft){	
		boolean random=false;
            if(stats.getVariant().getWeaponSpec(decoGunID) != null)
			{
				selected = SWITCHWEP.get(stats.getVariant().getWeaponSpec(decoGunID).getWeaponId());
            } 
			else
			{
                selected=MathUtils.getRandomNumberInRange(0, SWITCHWEP.size()-1);
                random=true;
            }
            
			stats.getVariant().clearSlot(decoGunID);
			stats.getVariant().clearSlot("F_LEGS");

		    stats.getVariant().addMod(SWITCH.get(selected));
			//if(selected != 2)
			//{
				stats.getVariant().addWeapon("F_LEGS", "armaa_ht_legs");
			//}
			
			//else
			//{
			//	stats.getVariant().addWeapon("F_LEGS", "armaa_valkazard_legs");				
			//}
			
				stats.getVariant().addWeapon(decoGunID, SWITCH_WEP.get(selected));
	
            if(random){
                stats.getVariant().autoGenerateWeaponGroups();
            }
        }
    }
    
    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id){
        
        if(ship.getOriginalOwner()<0){
            //undo fix for harvests put in cargo
            if(
                    Global.getSector()!=null && 
                    Global.getSector().getPlayerFleet()!=null && 
                    Global.getSector().getPlayerFleet().getCargo()!=null && 
                    Global.getSector().getPlayerFleet().getCargo().getStacksCopy()!=null &&
                    !Global.getSector().getPlayerFleet().getCargo().getStacksCopy().isEmpty()
                    ){
                for (CargoStackAPI s : Global.getSector().getPlayerFleet().getCargo().getStacksCopy()){
                    if(
                            s.isWeaponStack() && (
                                SWITCH_WEP.containsValue(s.getWeaponSpecIfWeapon().getWeaponId()) || 
                                s.getWeaponSpecIfWeapon().getWeaponId().equals("armaa_ht_legs") || s.getWeaponSpecIfWeapon().getWeaponId().equals("armaa_valkazard_legs")
                                ) 
                            ){
                        Global.getSector().getPlayerFleet().getCargo().removeStack(s);
                    }
                }
            }
        }
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
