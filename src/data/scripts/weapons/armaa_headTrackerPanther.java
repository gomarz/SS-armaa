package data.scripts.weapons;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.VectorUtils;
import java.util.*;

public class armaa_headTrackerPanther implements EveryFrameWeaponEffectPlugin {

    private boolean runOnce = false;
    private WeaponAPI rArm;
    private final IntervalUtil interval = new IntervalUtil(0.06f, 0.06f);

    public void init(WeaponAPI weapon)
    {
        runOnce = true;
		//need to grab another weapon so some effects are properly rendered, like lighting from glib
        for (WeaponAPI w : weapon.getShip().getAllWeapons()) 
		{
            switch (w.getSlot().getId()) {
                case "A_GUN":
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
		if(engine.isPaused())
			return;
		if(!runOnce)
			init(weapon);
		ShipAPI ship = weapon.getShip();
		List<ShipAPI> children = ship.getChildModulesCopy();
		for(ShipAPI module: children)	
		{
			module.ensureClonedStationSlotSpec();
			if(!engine.isEntityInPlay(module))
			{
				ship.getMutableStats().getAcceleration().modifyMult(ship.getId(),1.25f);
				ship.getMutableStats().getMaxSpeed().modifyMult(ship.getId(),1.25f);
				ship.getMutableStats().getMaxTurnRate().modifyMult(ship.getId(),1.25f);		
				ship.getMutableStats().getTurnAcceleration().modifyMult(ship.getId(),1.25f);		
			}			
		}
		if(ship.getSelectedGroupAPI() == null)
			return;
		float targetAngle = ship.getFacing();
		if(ship.getMouseTarget() != null)
		{
			targetAngle = VectorUtils.getAngle(ship.getLocation(),ship.getMouseTarget());
		}
		if(ship.getShipTarget() != null)
		{
			targetAngle = VectorUtils.getAngle(ship.getLocation(),ship.getShipTarget().getLocation());			
		}
		String activeId = ship.getSelectedGroupAPI().getActiveWeapon().getSlot().getId();
		if(activeId.equals(rArm.getSlot().getId()))
		{
			targetAngle = rArm.getCurrAngle(); //ship.getSelectedGroupAPI().getActiveWeapon().getCurrAngle();
		}

		if(weapon.getCurrAngle() != targetAngle)
		{
			float shortestDist = (targetAngle - weapon.getCurrAngle() + 180) % 360 - 180;
			int direction = shortestDist >= 0 ? 1 : -1;
			float currentAngle = (weapon.getCurrAngle() + direction * Math.min(1f, Math.abs(shortestDist))) % 360;			
			if(targetAngle != weapon.getCurrAngle())
				weapon.setCurrAngle(currentAngle);
		}
    }
}
