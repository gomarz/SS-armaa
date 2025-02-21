//by Tartiflette, this script raise the damage recieved by a target when "painted" by a specific weapon
//feel free to use it, credit is appreciated but not mandatory
package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class armaa_spikeThrowerEffect implements EveryFrameWeaponEffectPlugin {

    private boolean runOnce=false, empty=false;
    
    private Map<CombatEntityAPI, Boolean> detonation= new HashMap<>(); //to check for synch detonations    
    private List<CombatEntityAPI> hit = new ArrayList<>(); //to transmit the target from the projectile to the pike missile    
        
    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        
        if(!runOnce){
            runOnce=true;
            detonation.clear();
            hit.clear();
        }
        
        if (engine.isPaused()) {
            return;
        }
        
        if(!detonation.isEmpty()){
            for(Iterator<CombatEntityAPI> iter=detonation.keySet().iterator(); iter.hasNext(); ){
                CombatEntityAPI entry = iter.next();
                if(detonation.get(entry)){
                    iter.remove();
                }                
            }   
        }
        
        if(weapon.getAmmo()==0){
            if(!empty){
                empty=true;
                Global.getSoundPlayer().playSound(
                        "armaa_stake_empty",
                        1,
                        2,
                        weapon.getLocation(),
                        weapon.getShip().getVelocity()
                );
            }
        } else if(empty){
            empty=false;
        }
        
    }
    
    public void putHIT(CombatEntityAPI target) {
        hit.add(target);
    }
    
    public List<CombatEntityAPI> getHITS(){
        return hit;
    }
    
    public void setDetonation(CombatEntityAPI target){
        if(hit.contains(target)){
            hit.remove(target);
        }
        if(!detonation.containsKey(target)){
            detonation.put(target, false);
        }
    }
    
    public boolean getDetonation(CombatEntityAPI target){
        if(detonation.containsKey(target)){
            return detonation.get(target);
        } else {
            return true;
        }
    }
    
    public void applyDetonation(CombatEntityAPI target){
        if(detonation.containsKey(target)){
            detonation.put(target, true);
        }
    }
}
