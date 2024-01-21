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

//Original code by Tartiflette
public class armaa_weaponSwap_monitor extends BaseHullMod{
        
    public Map<Integer,String> RIGHT_SELECTOR = new HashMap<>();
    {
        RIGHT_SELECTOR.put(0, "armaa_monitor_rightArm_gauss");
        RIGHT_SELECTOR.put(1, "armaa_monitor_rightArm");

    }
	
    public Map<Integer,String> LEFT_SELECTOR = new HashMap<>();
    {
        LEFT_SELECTOR.put(0, "armaa_monitor_leftArm_gauss");
        LEFT_SELECTOR.put(1, "armaa_monitor_leftArm");

    }	
    	
    private final Map<String, Integer> SWITCH_TO_RIGHT = new HashMap<>();
    {
		SWITCH_TO_RIGHT.put("armaa_monitor_rightArm_gauss",1);
        SWITCH_TO_RIGHT.put("armaa_monitor_rightArm",0);
    }
	
    private final Map<String, Integer> SWITCH_TO_LEFT = new HashMap<>();
    {
		SWITCH_TO_LEFT.put("armaa_monitor_leftArm_gauss",1);
        SWITCH_TO_LEFT.put("armaa_monitor_leftArm",0);
    }	
    
    private final Map<Integer,String> RIGHTSWITCH = new HashMap<>();
    {
        RIGHTSWITCH.put(0,"armaa_selector_gauss");
        RIGHTSWITCH.put(1,"armaa_selector_hellbore");
    }    

    private final Map<Integer,String> LEFTSWITCH = new HashMap<>();
    {
        LEFTSWITCH.put(0,"armaa_selector_gauss_l");
        LEFTSWITCH.put(1,"armaa_selector_hellbore_l");
    }  	
    
    private final String leftslotID = "E_LARM"; 
    private final String rightslotID = "E_RARM";     
    
    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) 
	{
        //trigger a weapon switch if none of the selector hullmods are present
		boolean toSwitchRight = true;
		boolean toSwitchLeft = true;
		int numMods = 0;
        for(int i=0; i<SWITCH_TO_RIGHT.size(); i++){
            if(stats.getVariant().getHullMods().contains(RIGHTSWITCH.get(i))){
                toSwitchRight=false;
				numMods++;
            }
        }
        for(int i=0; i<SWITCH_TO_LEFT.size(); i++){
            if(stats.getVariant().getHullMods().contains(LEFTSWITCH.get(i))){
                toSwitchLeft=false;
				numMods++;
            }
        }		
        if(toSwitchRight){        
            //select new fire mode
            int selected;       
            boolean random=false;
            if(stats.getVariant().getWeaponSpec(rightslotID)!=null){
                selected=SWITCH_TO_RIGHT.get(stats.getVariant().getWeaponSpec(rightslotID).getWeaponId());
                
            } else {
                selected=MathUtils.getRandomNumberInRange(0, SWITCH_TO_RIGHT.size()-1);
                random=true;
            }
            
            //add the proper hullmod
            stats.getVariant().addMod(RIGHTSWITCH.get(selected));

            //clear the weapons to replace			
			//if(ship.getHullmods.contains(SWITCH)
			//{
				stats.getVariant().clearSlot(rightslotID);
				//select and place the proper weapon              
				String toInstallRight=RIGHT_SELECTOR.get(selected);
				stats.getVariant().addWeapon(rightslotID, toInstallRight);
			//}
            
            if(random){
                stats.getVariant().autoGenerateWeaponGroups();
            }
        }
         if(toSwitchLeft){        
            //select new fire mode
            int selected;       
            boolean random=false;
            if(stats.getVariant().getWeaponSpec(leftslotID)!=null){
                selected=SWITCH_TO_LEFT.get(stats.getVariant().getWeaponSpec(leftslotID).getWeaponId());
                
            } else {
                selected=MathUtils.getRandomNumberInRange(0, SWITCH_TO_LEFT.size()-1);
                random=true;
            }           
            //add the proper hullmod
            stats.getVariant().addMod(LEFTSWITCH.get(selected));

            //clear the weapons to replace			
			//if(ship.getHullmods.contains(SWITCH)
			//{
				stats.getVariant().clearSlot(leftslotID);
				//select and place the proper weapon              
				String toInstallLeft=LEFT_SELECTOR.get(selected);
				stats.getVariant().addWeapon(leftslotID, toInstallLeft);
			//}
            
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
                                RIGHT_SELECTOR.containsValue(s.getWeaponSpecIfWeapon().getWeaponId())
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
        // Allows any ship with a diableavionics hull id
        return ( ship.getHullSpec().getHullId().startsWith("armaa_"));	
    }
}
