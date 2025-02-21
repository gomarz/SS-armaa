package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipEngineControllerAPI.ShipEngineAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import org.magiclib.util.MagicIncompatibleHullmods;
import org.magiclib.util.ui.StatusBarData;
import org.magiclib.util.MagicUI;
import org.lazywizard.lazylib.MathUtils;
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
import org.lazywizard.lazylib.combat.CombatUtils;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL11.glPopAttrib;
import static org.lwjgl.opengl.GL11.glPopMatrix;
import org.lazywizard.lazylib.MathUtils;
import java.io.IOException;
import org.lwjgl.opengl.Display;


public class armaa_KarmaMod_Command extends BaseHullMod 
{

	protected float karma = 0;
	private static final float BONUS_AMT = 1.15f;
	protected static int karmaThreshold = 2000;
	private static final Color AFTERIMAGE_COLOR = new Color(149, 206, 240, 102);
	private static final float AFTERIMAGE_THRESHOLD = 0.4f;
	private static final List<String> SAFE_MODS = new ArrayList<>();
	private List<ShipAPI> targets = new ArrayList<>();
	private List<ShipAPI> toRemove = new ArrayList<>();

	
    // sprite path - necessary if loaded here and not in settings.json
    public static final String SPRITE_PATH = "graphics/fx/shields256.png";
    public static final Color COLOR = new Color(100, 47, 255, 185);
    public static final float ROTATION_SPEED = 20f;
	private SpriteAPI sprite = null;
	private boolean loaded = false;
	private float rotation = 0f;
	private static final float EFFECT_RANGE = 1000f;

    static 
	{
        /* No shields on my modules */
        SAFE_MODS.add("armaa_legmodule");
        SAFE_MODS.add("reduced_explosion");
		SAFE_MODS.add("always_detaches");
        SAFE_MODS.add("novent");
        SAFE_MODS.add("leg_engine");
		
	}


    @Override
    public void advanceInCombat(ShipAPI ship, float amount) 
	{
		List<ShipAPI> children = ship.getChildModulesCopy();
		
		if(Global.getCombatEngine().getCustomData().get("armaa_karmaTotal"+ship.getId()) instanceof Float)
		{
			float total = (float)Global.getCombatEngine().getCustomData().get("armaa_karmaTotal"+ship.getId());
			
			if(total > 0)
			{
				total-= 0.001f;
				Global.getCombatEngine().getCustomData().put("armaa_karmaTotal"+ship.getId(),total);
			}
			
			
		}
		
		if(!(Global.getCombatEngine().getCustomData().get("armaa_hullmodsDone?"+ship.getId()) instanceof Boolean))
		{
			//boolean value = (boolean)Global.getCombatEngine().getCustomData().get("armaa_hullmodsDone?"+ship.getId());
		
			//Global.getCombatEngine().maintainStatusForPlayerShip(ship.getId(), "graphics/icons/hullsys/entropy_amplifier.png","DEBUG",(Boolean.toString(value)), false);
		Global.getCombatEngine().getCustomData().put("armaa_hullmodsDone?"+ship.getId(),false);

		}
		
		if(Global.getCombatEngine().getCustomData().get("armaa_hullmodsDone?"+ship.getId()) instanceof Boolean)
		{
			//if((boolean)Global.getCombatEngine().getCustomData().get("armaa_hullmodsDone?"+ship.getId()) != true)
			//Global.getCombatEngine().maintainStatusForPlayerShip(ship.getId(), "graphics/icons/hullsys/entropy_amplifier.png","DEBUG","TRUE", false);	
			
			if((boolean)Global.getCombatEngine().getCustomData().get("armaa_hullmodsDone?"+ship.getId()) != true)
			{
				if(children != null && !children.isEmpty()) 
				{
					for (ShipAPI module : children) 
					{
						module.ensureClonedStationSlotSpec();

						Collection<String> hmods = ship.getVariant().getHullMods();
						List<String> hmod = new ArrayList<>();
						hmod.addAll(hmods);
						
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
				
		if(Global.getCombatEngine().getCustomData().get("armaa_karmaTotal"+ship.getId()) instanceof Float)
		{
			String id = "armaa_karma"+ship.getId();
			float total = (float)Global.getCombatEngine().getCustomData().get("armaa_karmaTotal"+ship.getId());
			float mult = total * BONUS_AMT;
			if(total > 0)
			{
				if (sprite == null) 
				{
				// Load sprite if it hasn't been loaded yet - not needed if you add it to settings.json
					if (!loaded) {
						try {
							Global.getSettings().loadTexture(SPRITE_PATH);
						} catch (IOException ex) {
							throw new RuntimeException("Failed to load sprite '" + SPRITE_PATH + "'!", ex);
						}

						loaded = true;
					}
					
					sprite = Global.getSettings().getSprite(SPRITE_PATH);
				}

				final Vector2f loc = ship.getLocation();
				final ViewportAPI view = Global.getCombatEngine().getViewport();
				if (view.isNearViewport(loc, EFFECT_RANGE)) {
					glPushAttrib(GL_ENABLE_BIT);
					glMatrixMode(GL_PROJECTION);
					glPushMatrix();
					glViewport(0, 0, Display.getWidth(), Display.getHeight());
					glOrtho(0.0, Display.getWidth(), 0.0, Display.getHeight(), -1.0, 1.0);
					glEnable(GL_TEXTURE_2D);
					glEnable(GL_BLEND);
					final float radius = (EFFECT_RANGE * 2f) / view.getViewMult();
					sprite.setSize(radius, radius);
					sprite.setColor(COLOR);
					sprite.setAlphaMult(0.3f*total);
					sprite.renderAtCenter(view.convertWorldXtoScreenX(loc.x), view.convertWorldYtoScreenY(loc.y));
					sprite.setAngle(rotation);
					glPopMatrix();
					glPopAttrib();
				}

				// Spin it
				rotation += ROTATION_SPEED * amount;
				if (rotation > 360f){
					rotation -= 360f;
				}
			}

				if( total > 0)
				for (ShipAPI target : CombatUtils.getShipsWithinRange(ship.getLocation(), 1000f))
				{	
					if(target.getOwner() != ship.getOwner()) continue;
					
					else if(target == ship) continue;
					
					else if(children.contains(target)) continue;
					
					else if(target.isHulk()) continue;
					
					else if(targets.contains(target)) continue;
					
						else
						targets.add(target);
				}
				if(!targets.isEmpty())
				for (ShipAPI target : targets)
				{
					boolean player = target == Global.getCombatEngine().getPlayerShip();
					MutableShipStatsAPI stats = target.getMutableStats();
					
					if (MathUtils.getDistance(target.getLocation(), ship.getLocation()) <= 1000f && total > 0 && target != ship) 
					{
						float shipTimeMult = 1f + (BONUS_AMT - 1f) * total;
						//float shipTimeMult = 1f + (2f - 1f) * effectLevel;
						stats.getTimeMult().modifyMult(id, shipTimeMult);
						
						if(player)
						{
							Global.getCombatEngine().getTimeMult().modifyMult(id, 1f/shipTimeMult);
							Global.getCombatEngine().maintainStatusForPlayerShip(ship.getId(), "graphics/icons/hullsys/entropy_amplifier.png", "Time Flow Altered","+"+(int)((shipTimeMult-1f)*100)+"% TIME DILATION ", false);	
						}
						
						target.setJitter(ship, new Color(100,142,255,100), 1f*total, 5, 5,25);

					}
				
					else
					{
						stats.getTimeMult().unmodify(id);
						if(player)
							Global.getCombatEngine().getTimeMult().unmodify(id);
					
						toRemove.add(target);
					}
				}
			
				if(!toRemove.isEmpty())
				{
					targets.removeAll(toRemove);
					toRemove.removeAll(toRemove);
				}
				
				if(!targets.isEmpty() && total > 0)
				{
					targets.removeAll(targets);
				}
				StatusBarData bar = new StatusBarData(ship,total,Color.red,Color.green,0f,"KARMA",(int)total);
				bar.drawToScreen(new Vector2f());			
				MagicUI.drawSystemBar(
					ship,
					Color.red,
					total,
					0f
				);
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

