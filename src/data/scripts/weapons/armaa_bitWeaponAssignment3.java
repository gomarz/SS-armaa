package data.scripts.weapons;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.util.Misc;
;
import org.lwjgl.util.vector.Vector2f;
import java.util.*; 
import java.util.List;
import com.fs.starfarer.api.combat.CombatUIAPI;
import java.awt.Color;
import org.magiclib.util.MagicIncompatibleHullmods;
import java.util.Collection;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;

public class armaa_bitWeaponAssignment3 implements EveryFrameWeaponEffectPlugin {
	
	private boolean isWeaponSwapped = false;
	
		public boolean isBattling()
		{
			boolean check = false;
			CombatEngineAPI engine = Global.getCombatEngine();
			CombatUIAPI ui = engine.getCombatUI();
			
			if(ui != null)
			{
				check = true;
			}
			
			return check;
		}
		
    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
		{
			ShipAPI ship = weapon.getShip();
			//CombatEngineAPI engine = Global.getCombatEngine();			
			WeaponAPI wep = null;
			for(WeaponAPI w: ship.getAllWeapons())
			{
				if(w.getSlot().getId().equals("DUMMYBIT"))
					wep = w;
			}				
			//Refit screen check
			if(ship.getOriginalOwner() == -1)
			{
					weapon.getSprite().setColor(new Color(255,255,255,255));
					if(weapon.getBarrelSpriteAPI() != null)
						weapon.getBarrelSpriteAPI().setColor(new Color(255,255,255,255));
			}

			else
			{
				Color invis = new Color(0f,0f,0f,0f);
				if(wep != null)
				{				
					if(wep.getSprite() != null)
						wep.getSprite().setColor(invis);
					
					if(wep.getUnderSpriteAPI() != null)
						wep.getUnderSpriteAPI().setColor(invis);		

					if(wep.getBarrelSpriteAPI() != null)
						wep.getBarrelSpriteAPI().setColor(invis);
					
					if(wep.getGlowSpriteAPI() != null)
						wep.getGlowSpriteAPI().setColor(invis);	
				}
				if(weapon != null)				
					weapon.getSprite().setColor(new Color(255,255,255,0));
			}

			if(ship.isAlive() && !isWeaponSwapped && wep != null)
			{
				//CombatEngineAPI engine = Global.getCombatEngine();
				List<FighterWingAPI> wings = ship.getAllWings();
			
				for(FighterWingAPI wing : wings)
				{
					if(wing == null || wing.getWingMembers() == null || wing.getWingMembers().isEmpty())
						continue;
					
					ShipAPI fighter = wing.getLeader();
					if(fighter == null)
						continue;
					
					for(int i = 0; i < wing.getWingMembers().size();i++)
					{
						fighter = wing.getWingMembers().get(i);
						if(fighter == null)
							continue;
						if(fighter.getAllWeapons().isEmpty())
							continue;
						if(!wing.getWingId().equals("armaa_bit_wing"))
						{
							continue;
						}
						MutableShipStatsAPI stats = fighter.getMutableStats();
						ShipVariantAPI OGVariant = stats.getVariant().clone();
						ShipVariantAPI newVariant = stats.getVariant().clone();

						String str = (String) Global.getCombatEngine().getCustomData().get("armaa_modularDroneWeaponId"+ship.getId());
						if(str == null) 
							str = "No weapon";
						if(engine.getPlayerShip() == ship)
						Global.getCombatEngine().maintainStatusForPlayerShip("AceSystem444", "graphics/ui/icons/icon_repair_refit.png","Drone Weapon", str + " installed. ",true);
						if(!fighter.getAllWeapons().get(0).getId().equals(str))
						{
							if(ship.getVariant().getWeaponSpec("DUMMYBIT") != null)
							{
								Global.getCombatEngine().getCustomData().put("armaa_modularDroneWeaponId"+ship.getId(),ship.getVariant().getWeaponId("DUMMYBIT"));
								//stats.getVariant().setOriginalVariant(null);
								fighter.getFleetMember().setVariant(newVariant,true,true);
								wep.disable(true);
								Color invis = new Color(0f,0f,0f,0f);		
								if(wep.getSprite() != null)
									wep.getSprite().setColor(invis);
								
								if(wep.getUnderSpriteAPI() != null)
									wep.getUnderSpriteAPI().setColor(invis);		

								if(wep.getBarrelSpriteAPI() != null)
									wep.getBarrelSpriteAPI().setColor(invis);
								
								if(wep.getGlowSpriteAPI() != null)
									wep.getGlowSpriteAPI().setColor(invis);									
												stats.getVariant().clearSlot("WS0001");								
												stats.getVariant().addWeapon("WS0001",ship.getVariant().getWeaponId("DUMMYBIT"));
												stats.getVariant().getWeaponSpec("WS0001").addTag("FIRE_WHEN_INEFFICIENT");
												ship.removeWeaponFromGroups(wep);
												wing.orderReturn(fighter);
								//isWeaponSwapped = true;
							}
						}
					}//
						
				}
			}
		}
}

