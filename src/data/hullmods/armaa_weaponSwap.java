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
public class armaa_weaponSwap extends BaseHullMod{
        
    public Map<Integer,String> LEFT_SELECTOR = new HashMap<>();
    {
        LEFT_SELECTOR.put(0, "armaa_koutoBazooka");
        LEFT_SELECTOR.put(1, "armaa_alesteLeftArm");
        LEFT_SELECTOR.put(2, "armaa_aleste_rifle_left");
        LEFT_SELECTOR.put(3, "armaa_aleste_grenade_left");
        LEFT_SELECTOR.put(4, "armaa_aleste_blade_LeftArm");
    }
    
    public Map<Integer,String> RIGHT_SELECTOR = new HashMap<>();
    {
        RIGHT_SELECTOR.put(0, "armaa_aleste_heavyrifle_right");
        RIGHT_SELECTOR.put(1, "armaa_aleste_rightArm");
		RIGHT_SELECTOR.put(2, "armaa_aleste_flamer_right");
		RIGHT_SELECTOR.put(3, "armaa_aleste_blade_RightArm");
		RIGHT_SELECTOR.put(4, "armaa_leynos_minigun_right");
    }
    
    private final Map<String, Integer> SWITCH_TO_LEFT = new HashMap<>();
    {
        SWITCH_TO_LEFT.put("armaa_aleste_grenade_left",2);
        SWITCH_TO_LEFT.put("armaa_koutoBazooka",3);
        SWITCH_TO_LEFT.put("armaa_aleste_rifle_left",1);
        SWITCH_TO_LEFT.put("armaa_alesteLeftArm",4);
		SWITCH_TO_LEFT.put("armaa_aleste_blade_LeftArm",0);
    }
	
    private final Map<String, Integer> SWITCH_TO_RIGHT = new HashMap<>();
    {
		SWITCH_TO_RIGHT.put("armaa_aleste_flamer_right",1);
        SWITCH_TO_RIGHT.put("armaa_aleste_heavyrifle_right",4);
        SWITCH_TO_RIGHT.put("armaa_aleste_rightArm",0);
		SWITCH_TO_RIGHT.put("armaa_aleste_blade_RightArm",2);
		SWITCH_TO_RIGHT.put("armaa_leynos_minigun_right",3);
    }
    
    private final Map<Integer,String> LEFTSWITCH = new HashMap<>();
    {
        LEFTSWITCH.put(0,"armaa_selector_bazooka");
        LEFTSWITCH.put(1,"armaa_selector_stake");
		LEFTSWITCH.put(2,"armaa_selector_lrifle_left");
		LEFTSWITCH.put(3,"armaa_selector_grenade");
        LEFTSWITCH.put(4,"armaa_selector_blade");		
    }
    private final Map<Integer,String> RIGHTSWITCH = new HashMap<>();
    {
        RIGHTSWITCH.put(0,"armaa_selector_heavyrifle");
        RIGHTSWITCH.put(1,"armaa_selector_lrifle");
        RIGHTSWITCH.put(2,"armaa_selector_flamer");
		RIGHTSWITCH.put(3,"armaa_selector_blade_right");
		RIGHTSWITCH.put(4,"armaa_selector_minigun");
    }    
    
    private final String leftslotID = "C_ARML"; 
    private final String rightslotID = "A_GUN";     
    
    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) 
	{

        //trigger a weapon switch if none of the selector hullmods are present
        boolean toSwitchLeft=true;
		boolean toSwitchRight = true;
		int numMods = 0;
        for(int i=0; i<SWITCH_TO_LEFT.size(); i++){
            if(stats.getVariant().getHullMods().contains(LEFTSWITCH.get(i))){
                toSwitchLeft=false;
				numMods++;
            }
        }
        for(int i=0; i<SWITCH_TO_RIGHT.size(); i++){
            if(stats.getVariant().getHullMods().contains(RIGHTSWITCH.get(i))){
                toSwitchRight=false;
				numMods++;
            }
        }
		
		//If there isn't both a hullmod for the left AND right arm, we need to switch something

        //remove the weapons to change and swap the hullmod for the next fire mode
        if(toSwitchLeft){        
            //select new fire mode
            int selected;       
            boolean random=false;
            if(stats.getVariant().getWeaponSpec(leftslotID)!=null && SWITCH_TO_LEFT.get(stats.getVariant().getWeaponSpec(leftslotID).getWeaponId()) != null){
                selected=SWITCH_TO_LEFT.get(stats.getVariant().getWeaponSpec(leftslotID).getWeaponId());
				//stake
                
            } else {
                selected=MathUtils.getRandomNumberInRange(0, SWITCH_TO_LEFT.size()-1);
                random=true;
            }
            
            //add the proper hullmod
            stats.getVariant().addMod(LEFTSWITCH.get(selected));

            //clear the weapons to replace
			//if(ship.getHullmods.contains(SWITCH))
			//{
				stats.getVariant().clearSlot(leftslotID);
				String toInstallLeft=LEFT_SELECTOR.get(selected);  
				stats.getVariant().addWeapon(leftslotID, toInstallLeft);
		//	}            
            if(random){
                stats.getVariant().autoGenerateWeaponGroups();
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
                                LEFT_SELECTOR.containsValue(s.getWeaponSpecIfWeapon().getWeaponId()) || 
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
