package data.scripts.weapons;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.util.IntervalUtil;


public class armaa_shoulderTracker implements EveryFrameWeaponEffectPlugin {

    private boolean runOnce = false, lockNloaded = false;
    private WeaponAPI lArm,rArm;
	public int frame = 7;
    private final IntervalUtil interval = new IntervalUtil(0.06f, 0.06f);

    public void init(WeaponAPI weapon)
    {
        runOnce = true;
		//need to grab another weapon so some effects are properly rendered, like lighting from glib
        for (WeaponAPI w : weapon.getShip().getAllWeapons()) 
		{
            switch (w.getSlot().getId()) {
                case "A_GUN":
                    if(lArm==null) {
                        lArm = w;
                    }
                    break;
                case "A_GUN2":
                    if(rArm==null) {
                        rArm = w;
                    }
                    break;
            }
        }
    }
    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{
		if(!runOnce)
			init(weapon);
		ShipAPI ship = weapon.getShip();

		if(weapon.getSlot().getId().equals("LPAULDRON"))
			weapon.setCurrAngle(lArm.getCurrAngle());
	
		else
			weapon.setCurrAngle(rArm.getCurrAngle());
		
    }
}
