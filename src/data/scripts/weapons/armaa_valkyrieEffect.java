package data.scripts.weapons;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.Global;
import java.awt.Color;
import java.util.List;
import org.lwjgl.util.vector.Vector2f;

public class armaa_valkyrieEffect implements EveryFrameWeaponEffectPlugin  {
	
    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{

        ShipAPI ship = weapon.getShip();
		
		List<ShipAPI> children = ship.getChildModulesCopy();
		if(children != null)
			for(ShipAPI m: children)	
			{
				if(m.getStationSlot() == null)
					continue;
				if(m.controlsLocked() == false)
				{
					m.setControlsLocked(true);

				}
				else
				{
					for(WeaponAPI wep : m.getAllWeapons())
					{
						if(wep.getSlot().getId().equals("TRUE_GUN2"))
							wep.setCurrAngle(m.getFacing()+90f);
					}
				}
				if(ship.areAnyEnemiesInRange())
				{
					if(m.isRetreating())
						m.setRetreating(false, false);
					m.setControlsLocked(false);
					m.setStationSlot(null);
				}				
		
				m.ensureClonedStationSlotSpec();
				if(m.getAllWings() == null || m.getAllWings().size() == 0)
					continue;
				for(ShipAPI fighter: m.getAllWings().get(0).getWingMembers())
				{
					m.getAllWings().get(0).orderReturn(fighter);
				}
			}

	}
}
