package data.scripts.weapons;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import java.awt.Color;
import java.util.List;
import com.fs.starfarer.api.Global;

//need this to hide decos after module is dead. sigh
public class armaa_corsairDecoHider implements EveryFrameWeaponEffectPlugin {

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{
		ShipAPI ship = weapon.getShip();
		ship.syncWeaponDecalsWithArmorDamage();
		List<ShipAPI> children = ship.getChildModulesCopy();
		if(children != null)
		{
			for(ShipAPI module: children)	
			{
				module.ensureClonedStationSlotSpec();

				if(module.getStationSlot() == null || !engine.isEntityInPlay(module))
				{
							weapon.getSprite().setColor(new Color(0,0,0,0));
				}
			}
		}		
		
	}
}
