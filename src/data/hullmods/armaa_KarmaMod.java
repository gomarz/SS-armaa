package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import org.magiclib.util.ui.StatusBarData;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import org.lazywizard.lazylib.FastTrig;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;
import com.fs.starfarer.api.graphics.SpriteAPI;
import java.awt.*;
import java.util.*; 
import java.util.List;
import java.util.Collection;
//import java.util.Set;

public class armaa_KarmaMod extends BaseHullMod {

protected float karma = 0;
private static final float BONUS_AMT = 30f;
protected static int karmaThreshold = 2000;
private static final Color AFTERIMAGE_COLOR = new Color(149, 206, 240, 102);
private static final float AFTERIMAGE_THRESHOLD = 0.4f;
private static final List<String> SAFE_MODS = new ArrayList<>();
private static final List<String> BAD_MODS = new ArrayList<>();
    static 
	{
        /* No shields on my modules */
        SAFE_MODS.add("armaa_legmodule");
        SAFE_MODS.add("reduced_explosion");
		SAFE_MODS.add("always_detaches");
        SAFE_MODS.add("novent");
        SAFE_MODS.add("leg_engine");
		SAFE_MODS.add("neural_interface");
		SAFE_MODS.add("neural_integrator");
	}
    static 
	{
        /* No shields on my modules */
		BAD_MODS.add("neural_interface");
		BAD_MODS.add("neural_integrator");
	}

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
				
		if(Global.getCombatEngine().getCustomData().get("armaa_karmaTotal"+ship.getId()) instanceof Float)
		{
			String id = "armaa_karma"+ship.getId();
			MutableShipStatsAPI stats = ship.getMutableStats();
			float total = (float)Global.getCombatEngine().getCustomData().get("armaa_karmaTotal"+ship.getId());
			float mult = total * BONUS_AMT;
			
			if(total > 0)
			{
				 boolean player = ship == Global.getCombatEngine().getPlayerShip();
				total-= 0.001f;
				Global.getCombatEngine().getCustomData().put("armaa_karmaTotal"+ship.getId(),total);
				if (player)
				{
                    Global.getCombatEngine().maintainStatusForPlayerShip(ship.getId(), "graphics/icons/hullsys/entropy_amplifier.png", "Enlightened","+"+(int)mult+"% WEP ROF / " + "-"+(int)mult*2+"% WEP FLUX COST", false);		
                }
				
				stats.getBallisticRoFMult().modifyPercent(id, mult);
				stats.getEnergyRoFMult().modifyPercent(id, mult);
				stats.getBallisticWeaponFluxCostMod().modifyMult(id, 1f - (mult*2 * 0.01f));				
				stats.getEnergyWeaponFluxCostMod().modifyMult(id, 1f - (mult*2 * 0.01f));
				stats.getMaxSpeed().modifyPercent(id, mult);
				stats.getMaxTurnRate().modifyPercent(id,mult);
				//afterimage shit from tahlan
				ship.getMutableStats().getDynamic().getStat("armaa_NNAfterimageTracker").modifyFlat("armaa_NNAfterimageTrackerNullerID", -1);
				ship.getMutableStats().getDynamic().getStat("armaa_NNAfterimageTracker").modifyFlat("armaa_NNAfterimageTrackerID",
                ship.getMutableStats().getDynamic().getStat("armaa_NNAfterimageTracker").getModifiedValue() + amount);
				
				if(ship.getMutableStats().getDynamic().getStat("armaa_NNAfterimageTracker").getModifiedValue() > AFTERIMAGE_THRESHOLD) 
				{

					// Sprite offset fuckery - Don't you love trigonometry?
					SpriteAPI sprite = ship.getSpriteAPI();
					float offsetX = sprite.getWidth()/2 - sprite.getCenterX();
					float offsetY = sprite.getHeight()/2 - sprite.getCenterY();

					float trueOffsetX = (float)FastTrig.cos(Math.toRadians(ship.getFacing()-90f))*offsetX - (float)FastTrig.sin(Math.toRadians(ship.getFacing()-90f))*offsetY;
					float trueOffsetY = (float)FastTrig.sin(Math.toRadians(ship.getFacing()-90f))*offsetX + (float)FastTrig.cos(Math.toRadians(ship.getFacing()-90f))*offsetY;
					if(!ship.isHulk())
					{
						MagicRender.battlespace(
								Global.getSettings().getSprite(ship.getHullSpec().getSpriteName()),
								new Vector2f(ship.getLocation().getX()+trueOffsetX,ship.getLocation().getY()+trueOffsetY),
								new Vector2f(0, 0),
								new Vector2f(ship.getSpriteAPI().getWidth(), ship.getSpriteAPI().getHeight()),
								new Vector2f(0, 0),
								ship.getFacing()-90f,
								0f,
								AFTERIMAGE_COLOR,
								true,
								0.1f,
								0.1f,
								1f,
								0f,
								0f,
								0f,
								0f,
								0f,
								CombatEngineLayers.BELOW_SHIPS_LAYER);
					}
							
							for(ShipAPI m:ship.getChildModulesCopy())
							{
								if(m != null && Global.getCombatEngine().isEntityInPlay(m))
								{
									float modtrueOffsetX = (float)FastTrig.cos(Math.toRadians(m.getFacing()-90f))*offsetX - (float)FastTrig.sin(Math.toRadians(m.getFacing()-90f))*offsetY;
									float modtrueOffsetY = (float)FastTrig.sin(Math.toRadians(m.getFacing()-90f))*offsetX + (float)FastTrig.cos(Math.toRadians(m.getFacing()-90f))*offsetY;

									MagicRender.battlespace(
											Global.getSettings().getSprite(ship.getHullSpec().getSpriteName()),
											new Vector2f(ship.getLocation().getX()+trueOffsetX,ship.getLocation().getY()+trueOffsetY),
											new Vector2f(0, 0),
											new Vector2f(ship.getSpriteAPI().getWidth(), ship.getSpriteAPI().getHeight()),
											new Vector2f(0, 0),
											ship.getFacing()-90f,
											0f,
											AFTERIMAGE_COLOR,
											true,
											0.1f,
											0.1f,
											1f,
											0f,
											0f,
											0f,
											0f,
											0f,
											CombatEngineLayers.BELOW_SHIPS_LAYER);
								}
								
							}

							ship.getMutableStats().getDynamic().getStat("armaa_NNAfterimageTracker").modifyFlat("armaa_NNAfterimageTrackerID",
							ship.getMutableStats().getDynamic().getStat("armaa_NNAfterimageTracker").getModifiedValue() - AFTERIMAGE_THRESHOLD);
				}
						
			}
			
			else
			{
				stats.getBallisticRoFMult().unmodify(id);
				stats.getEnergyRoFMult().unmodify(id);
			
				stats.getBallisticWeaponFluxCostMod().unmodify(id);
				stats.getEnergyWeaponFluxCostMod().unmodify(id);
				
				stats.getMaxSpeed().unmodify(id);
				stats.getMaxTurnRate().unmodify(id);

			}
			StatusBarData bar = new StatusBarData(ship,total,Color.red,Color.green,0f,"",(int)total);
			bar.drawToScreen(new Vector2f());			

				
				
		}
		
		if(Global.getCombatEngine().getCustomData().get("armaa_hasGainedKarma"+ship.getId()) instanceof Boolean)
			if((boolean)Global.getCombatEngine().getCustomData().get("armaa_hasGainedKarma"+ship.getId()))
			{
				float value = (float)Global.getCombatEngine().getCustomData().get("armaa_gainedKarmaAount"+ship.getId());
				//addKarma(value);
				
				if(Global.getCombatEngine().getCustomData().get("armaa_karmaTotal"+ship.getId()) instanceof Float)
				{
					float oldTotal = (float)Global.getCombatEngine().getCustomData().get("armaa_karmaTotal"+ship.getId());
					float newTotal = oldTotal+value;
					Global.getCombatEngine().getCustomData().put("armaa_karmaTotal"+ship.getId(),newTotal);
					
				}
				
				Global.getCombatEngine().getCustomData().put("armaa_hasGainedKarma"+ship.getId(),false);
				Global.getCombatEngine().getCustomData().put("armaa_gainedKarmaAount"+ship.getId(),0f);
			}
			
		if(!(Global.getCombatEngine().getCustomData().get("armaa_hullmodsDone?"+ship.getId()) instanceof Boolean))
		{
			Global.getCombatEngine().getCustomData().put("armaa_hullmodsDone?"+ship.getId(),false);
		}
		
		if(Global.getCombatEngine().getCustomData().get("armaa_hullmodsDone?"+ship.getId()) instanceof Boolean)
		{
			if((boolean)Global.getCombatEngine().getCustomData().get("armaa_hullmodsDone?"+ship.getId()) != true)
			{
				List<ShipAPI> children = ship.getChildModulesCopy();
				if(children != null && !children.isEmpty()) 
				{
					for (ShipAPI module : children) 
					{
						module.ensureClonedStationSlotSpec();

						Collection<String> hmods = ship.getVariant().getHullMods();
						List<String> hmod = new ArrayList<>();
						hmod.addAll(hmods);
						hmod.removeAll(BAD_MODS);						
						Collection<String> mhmods = module.getVariant().getHullMods();
						List<String> mhmod = new ArrayList<>();
						mhmod.addAll(mhmods);
						mhmod.removeAll(SAFE_MODS);


						if(hmods.isEmpty())
							continue;
						for(int i = 0; i < hmod.size();i++)
						{
							if(!module.getVariant().hasHullMod(hmod.get(i)))
							{
								module.getVariant().addMod(hmod.get(i));
							}
						}
						if(mhmod.isEmpty())
							continue;
						for(int i = 0; i < mhmod.size();i++)
						{
							if(!ship.getVariant().hasHullMod(mhmod.get(i)))
							{
								module.getVariant().removeMod(mhmod.get(i));
							}
						}
					}
					Global.getCombatEngine().getCustomData().put("armaa_hullmodsDone?"+ship.getId(),true);
					Global.getCombatEngine().getCustomData().put("armaa_hullmodsDoneCheck"+ship.getId(),true);
				}
			}
		
		}
			
		
	}

    

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) 
	{
		if(Global.getCombatEngine() != null)
		{
			Global.getCombatEngine().getCustomData().put("armaa_hullmodsDone?"+ship.getId(),false);

			Global.getCombatEngine().getCustomData().put("armaa_karmaTotal"+ship.getId(),0f);
		}
    }
	
	public static void addKarma(float value)
	{
		//karma = karma + value;
	}
	
	public static float getKarma(String id)
	{
		float value = 0f;
				if(Global.getCombatEngine().getCustomData().get("armaa_karmaTotal"+id) instanceof Float)
				{
					value = (float)Global.getCombatEngine().getCustomData().get("armaa_karmaTotal"+id);					
				}
		return value;
	}
	
	public static float getKarmaThreshold()
	{
		return karmaThreshold;
	}
	
	}

