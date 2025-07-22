package data.scripts.campaign;

import com.fs.starfarer.api.campaign.listeners.ShowLootListener;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
public class armaa_lootCleaner implements ShowLootListener
{
	
	@Override
	public void reportAboutToShowLootToPlayer(CargoAPI loot, InteractionDialogAPI dialog) 
	{
		for (CargoStackAPI s : loot.getStacksCopy())
		{		
			if(s.isWeaponStack())
			{		
				if(s.getWeaponSpecIfWeapon().getWeaponId().contains("armaa_"))
				{
					if(s.getWeaponSpecIfWeapon().getType() == WeaponType.DECORATIVE || s.getWeaponSpecIfWeapon().getAIHints().contains(AIHints.SYSTEM))
					{
						loot.removeStack(s);
					}
				}
			}					
		}
		CargoAPI inventory = Global.getSector().getPlayerFleet().getCargo();
		for (CargoStackAPI s : inventory.getStacksCopy())
		{		
			if(s.isWeaponStack())
			{		
				if(s.getWeaponSpecIfWeapon().getWeaponId().contains("armaa_"))
				{
					if(s.getWeaponSpecIfWeapon().getType() == WeaponType.DECORATIVE || s.getWeaponSpecIfWeapon().getAIHints().contains(AIHints.SYSTEM))
					{
						inventory.removeStack(s);
					}
				}
			}					
		}
	}
}
