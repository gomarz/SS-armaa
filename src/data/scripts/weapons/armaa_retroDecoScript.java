package data.scripts.weapons;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;


public class armaa_retroDecoScript implements EveryFrameWeaponEffectPlugin {

    private boolean runOnce = false;
	private WeaponAPI vernier;

    public void init(WeaponAPI weapon)
    {
        runOnce = true;
		//need to grab another weapon so some effects are properly rendered, like lighting from glib
		String num = weapon.getSlot().getId();
		num = num.replaceAll("[^0-9]", "");
		int val = Integer.parseInt(num);
		String ourVernier = "VERNIER_"+val;
        for (WeaponAPI w : weapon.getShip().getAllWeapons()) 
		{
			if(w.getSlot().getId().equals(ourVernier) && vernier == null)
			{
				vernier = w;
				break;				
			}

        }
    }
    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{
		if(!runOnce)
			init(weapon);
		if(vernier== null)
			return;
		
		weapon.setCurrAngle(vernier.getCurrAngle());
		
		
    }
}
