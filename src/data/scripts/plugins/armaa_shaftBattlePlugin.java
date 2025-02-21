package data.scripts.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import java.util.*;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.*;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lazywizard.lazylib.CollisionUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;
import org.magiclib.util.MagicFakeBeam;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import java.awt.Color;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.lwjgl.input.Keyboard;
import org.magiclib.util.MagicRender.*;
import org.lazywizard.lazylib.MathUtils;


import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;


public class armaa_shaftBattlePlugin extends BaseEveryFrameCombatPlugin
{
	// for atmo battle
	boolean warning = true;
    protected CombatEngineAPI engine;
	private IntervalUtil interval3 = new IntervalUtil(2f, 2f);
	private boolean playSecondPhase, playedSecondPhase = false;
	private IntervalUtil attackInterval = new IntervalUtil(3f, 3f);	
	private float spin = 0f;
	private float depth = 0.01f;
	private boolean startedMusic = false;
	private boolean finalBossSpawn = false;
    public static Map MANUVER_MALUS = new HashMap();
	boolean reinforcementsTriggered = false;
    static {
        /*mass_mult.put(HullSize.FRIGATE, 3f);
        mass_mult.put(HullSize.DESTROYER, 3f);
        mass_mult.put(HullSize.CRUISER, 2f);
        mass_mult.put(HullSize.CAPITAL_SHIP, 2f); massy*/
        MANUVER_MALUS.put(HullSize.FIGHTER, 0.90f);
        MANUVER_MALUS.put(HullSize.FRIGATE, 0.80f);
        MANUVER_MALUS.put(HullSize.DESTROYER, 0.60f);
        MANUVER_MALUS.put(HullSize.CRUISER, 0.50f);
        MANUVER_MALUS.put(HullSize.CAPITAL_SHIP, 0.40f);
    }
	
	public Color shiftColor(Color start, Color end)
	{
		Color intermediateColor = Color.WHITE;
        int steps = 100; // Number of steps in the transition
        long duration = 1500; // Duration of the transition in milliseconds
		float ratio = (float) engine.getElapsedInContactWithEnemy() / steps;		
		if(ratio >= 1)
			return end;
		
		int red = (int) (start.getRed() * (1 - ratio) + end.getRed() * ratio);
		int green = (int) (start.getGreen() * (1 - ratio) + end.getGreen() * ratio);
		int blue = (int) (start.getBlue() * (1 - ratio) + end.getBlue() * ratio);
		int alpha = (int) (start.getAlpha() * (1 - ratio) + end.getAlpha() * ratio);
		intermediateColor = new Color(red, green, blue, alpha);	
		
		return intermediateColor;
		
		
	}
	
	public Vector2f calculateMidpoint()
	{
		ShipAPI pship = engine.getPlayerShip();
		Vector2f velocity = engine.getPlayerShip().getVelocity();		
		Vector2f vec = new Vector2f(pship.getLocation().getX()+velocity.getX(),pship.getLocation().getY()+velocity.getY());
		//engine.getViewport().set(0,0,1536,834);
		Vector2f vec2 = new Vector2f(engine.getViewport().convertScreenXToWorldX(Global.getSettings().getMouseX()),engine.getViewport().convertScreenYToWorldY(Global.getSettings().getMouseY()));
	// Calculate the midpoint
		float midpointX = (vec.x + vec2.x) / 2.0f;
		float midpointY = (vec.y + vec2.y) / 2.0f;

		// Viewport boundaries in world coordinates
		float minX = vec.getX()-834.0f; // Replace with actual minimum x boundary
		float maxX = vec.getX()+1536.0f; // Replace with actual maximum x boundary
		float minY = vec.getY()-1536.0f; // Replace with actual minimum y boundary
		float maxY =  vec.getY()+834.0f; // Replace with actual maximum y boundary

		// Clamp the midpoint to the boundaries
		midpointX = MathUtils.clamp(midpointX, minX, maxX);
		midpointY = MathUtils.clamp(midpointY, minY, maxY);
		//Update midpoint gradually from last midpoint over an interval
		//if interval isnt elapsed, just set midpoint
		//otherwise, increment midpoint values until == midpoint
		return new Vector2f(midpointX, midpointY);
	}
	
    @Override
    public void advance(float amount, List<InputEventAPI> events)
    {
		if(engine == null)
			return;
		if(!startedMusic)
		{
			startedMusic = true;
			Global.getSoundPlayer().playCustomMusic(0,0,"music_armaa_mission_descent",true);		
		}
		if(!playedSecondPhase && playSecondPhase)
		{
			attackInterval.advance(amount);
		}
		if(attackInterval.intervalElapsed())
		{		
			Global.getSoundPlayer().playCustomMusic(0,0,"music_armaa_pirate_encounter_hostile",true);	
			playedSecondPhase = true;
		}
		Color bgColor = new Color(40,25,10,150);
		Color endColor = new Color(25,25,25,255);
		Color endColor2 = new Color(25,25,25,120);	
		Color endColor3 = new Color(0,0,0,0);			
		engine.setBackgroundGlowColor(shiftColor(bgColor,endColor));
		engine.setBackgroundColor(endColor);		
		engine.maintainStatusForPlayerShip("AceSystem2","graphics/ui/icons/icon_repair_refit.png","",""+engine.getViewport().getViewMult(),true);
		if(!reinforcementsTriggered && engine.getElapsedInContactWithEnemy() > 1)
		{			
			reinforcementsTriggered = true;
		}		
		if(!engine.isPaused())
		{
			float ratio = (float) engine.getElapsedInContactWithEnemy() / 100;
			interval3.advance(amount);		
			float elapsed = interval3.getElapsed();
			float maxinterval = interval3.getMaxInterval();
			float rate = Math.min(1f,elapsed/maxinterval);
			float mult = engine.getViewport().getViewMult();	
			float minX = engine.getViewport().getLLX(); // Leftmost X-coordinate of the viewport
			float maxX = engine.getViewport().getLLX() + engine.getViewport().getVisibleWidth(); // Rightmost X-coordinate of the viewport	
			float minY = engine.getViewport().getLLY(); // Leftmost X-coordinate of the viewport
			float maxY = engine.getViewport().getLLY() + engine.getViewport().getVisibleHeight(); // Rightmost X-coordinate of the viewport	
			float xMove = engine.getPlayerShip() != null ? engine.getPlayerShip().getVelocity().getX() != 0 ? 1 : 0 : 0;
			float yMove = engine.getPlayerShip() != null ? engine.getPlayerShip().getVelocity().getY() : 0;			
			//if(interval2.intervalElapsed())
			//{	
			String str = "armaa_shaft";
			float initialWidth = Global.getSettings().getScreenWidth();
			float initialHeight = Global.getSettings().getScreenWidth();
			float currentWidth = initialWidth;
			float currentHeight = initialHeight;
			if(engine.getCustomData().get("armaa_bgHeight")== null)
			{
				Vector2f midpoint = calculateMidpoint();
				engine.getViewport().setCenter(midpoint);			
				engine.getCustomData().put("armaa_bgHeight",currentWidth);				
			}
			else
			{
				float val = (Float)engine.getCustomData().get("armaa_bgHeight");
				if(val > initialWidth*6.2f)
					val = initialWidth;
				currentWidth = val * (1f+0.015f);
				currentHeight = currentWidth;
				engine.getCustomData().put("armaa_bgHeight",currentWidth);					
			}
			MagicRender.screenspace(
				Global.getSettings().getSprite("misc", str),
				MagicRender.positioning.CENTER, 
				new Vector2f(0,0), 
				new Vector2f(0,0), 
				new Vector2f(currentWidth/12,currentHeight/12),
				new Vector2f(0,0),
				0f, 
				amount*5f, //spin 
				shiftColor(new Color(.2f,.2f,.2f,1f),endColor),	
				false, 
				0f, 
				0f, 
				0f, 
				0f, 
				0f, 
				0f, 
				amount, 
				0f, 
				CombatEngineLayers.CLOUD_LAYER
			);			
			MagicRender.screenspace(
				Global.getSettings().getSprite("misc", str),
				MagicRender.positioning.CENTER, 
				new Vector2f(0,0), 
				new Vector2f(0,0), 
				new Vector2f(currentWidth/6,currentHeight/6),
				new Vector2f(0,0),
				0f, 
				amount*5f, //spin 
				shiftColor(new Color(.3f,.3f,.3f,1f),endColor),
				false, 
				0f, 
				0f, 
				0f, 
				0f, 
				0f, 
				0f, 
				amount, 
				0f, 
				CombatEngineLayers.CLOUD_LAYER
			);
			MagicRender.screenspace(
				Global.getSettings().getSprite("misc", str+"_lights"),
				MagicRender.positioning.CENTER, 
				new Vector2f(0,0), 
				new Vector2f(0,0), 
				new Vector2f(currentWidth/6,currentHeight/6), 
				new Vector2f(0,0),
				0f, 
				amount*5f, //spin 
				shiftColor(endColor3,new Color(1f,1f,0.7f,1f)),
				false, 
				0f, 
				0f, 
				0f, 
				0f, 
				0f, 
				0f, 
				amount, 
				0f, 
				CombatEngineLayers.CLOUD_LAYER
			);					
			MagicRender.screenspace(
				Global.getSettings().getSprite("misc", str),
				MagicRender.positioning.CENTER, 
				new Vector2f(0,0), 
				new Vector2f(0,0), 
				new Vector2f(currentWidth,currentHeight), 
				new Vector2f(0,0),
				0f, 
				amount*5f, //spin 
				shiftColor(new Color(.3f,.3f,.3f,1f),endColor),					
				false, 
				0f, 
				0f, 
				0f, 
				0f, 
				0f, 
				0f, 
				amount, 
				0f, 
				CombatEngineLayers.CLOUD_LAYER
			);	
			MagicRender.screenspace(
				Global.getSettings().getSprite("misc", str+"_lights"),
				MagicRender.positioning.CENTER, 
				new Vector2f(0,0), 
				new Vector2f(0,0), 
				new Vector2f(currentWidth,currentHeight), 
				new Vector2f(0,0),
				0f, 
				amount*5f, //spin 
				shiftColor(endColor3,new Color(1f,1f,0.7f,1f)),
				false, 
				0f, 
				0f, 
				0f, 
				0f, 
				0f, 
				0f, 
				amount, 
				0f, 
				CombatEngineLayers.CLOUD_LAYER
			);
			float vel = MathUtils.getRandomNumberInRange(-200,200);
		
			depth+=amount;
			spin+=amount;
			if(depth > 3.5f)
				depth = 0.01f;
			float xVel = (float)(MathUtils.getRandomNumberInRange(-100,100));
			float cloudSize = (float)(MathUtils.getRandomNumberInRange(300,600));			
			MagicRender.screenspace(
			Global.getSettings().getSprite("misc", "armaa_atmo_cloud"),
			MagicRender.positioning.CENTER, 
			new Vector2f(0,0), 
			new Vector2f(vel,vel), 			
			new Vector2f(cloudSize,cloudSize),
			new Vector2f(100f,100f),		
			0f,
			spin/5f,
			shiftColor(new Color(30,20,5,255),new Color(0,0,0,255)),
			false,
			0f,
			0f,
			0f,
			0f,
			0f,
			.2f,
			.2f,
			.3f,
			CombatEngineLayers.BELOW_SHIPS_LAYER
			);
			MagicRender.screenspace(
			Global.getSettings().getSprite("misc", "armaa_atmo_cloud"),
			MagicRender.positioning.CENTER, 
			new Vector2f(0,0), 
			new Vector2f(vel,vel), 			
			new Vector2f(cloudSize*4,cloudSize*3),
			new Vector2f(100f,100f),		
			spin/10f,
			0,
			shiftColor(getSmokeColor(),new Color(0,0,0,255)),
			true,
			0f,
			0f,
			0f,
			0f,
			0f,
			.2f,
			.2f,
			.3f,
			CombatEngineLayers.ABOVE_SHIPS_LAYER
			);
			MagicRender.screenspace(
			Global.getSettings().getSprite("misc", "armaa_atmo_cloud"),
			MagicRender.positioning.CENTER, 
			new Vector2f(0,0), 
			new Vector2f(vel,vel), 			
			new Vector2f(cloudSize,cloudSize),
			new Vector2f(100f,100f),		
			0f,
			spin/2,
			shiftColor(new Color(30,20,5,255),new Color(0,0,0,255)),
			false,
			0f,
			0f,
			0f,
			0f,
			0f,
			.2f,
			.2f,
			.3f,
			CombatEngineLayers.BELOW_SHIPS_LAYER
			);
			if(Math.random() < 0.20f)
			{

			}			
		}
		
		if(!engine.isPaused())
		{
			float bgStage = (float) engine.getElapsedInContactWithEnemy() / 100;			
			Vector2f vec = (Vector2f)engine.getCustomData().get("armaa_atmoWarningLoc"+0);
			if(bgStage >= 1 && !finalBossSpawn)
			{
				finalBossSpawn = true;
				/* Maybe save this for later
				String bossStr ="armaa_valkazard_boss";
				boolean haveNex = Global.getSettings().getModManager().isModEnabled("nexerelin");					
				if(haveNex && Global.getSector().getPlayerMemoryWithoutUpdate().get("$nex_startingFactionId").equals("armaarmatura_pirates"))
				{
					bossStr ="armaa_altagrave_ex_Standard";					
				}					
				ShipAPI s = engine.getFleetManager(1).spawnShipOrWing(bossStr,calculateMidpoint(),90f,20f);
				*/
				ShipAPI s = engine.getFleetManager(1).spawnShipOrWing("armaa_morgana_boss",calculateMidpoint(),90f,20f);
				s.setCaptain(Misc.getAICoreOfficerPlugin(Commodities.ALPHA_CORE).createPerson(Commodities.ALPHA_CORE,"remnant",new Random()));
				s.setAnimatedLaunch();
				//Global.getSoundPlayer().playCustomMusic(1,0,null,true);	
				//playSecondPhase = true;			
				
			}
			for(CombatEntityAPI asteroid: engine.getAsteroids())
			{
				engine.removeEntity(asteroid);
			}
			for (ShipAPI ship : engine.getShips())
			{
				ship.getMutableStats().getMaxSpeed().modifyMult(ship.getId()+"_atmo",(float)MANUVER_MALUS.get(ship.getHullSize()));
				ship.getMutableStats().getMaxTurnRate().modifyMult(ship.getId()+"_atmo",(float)MANUVER_MALUS.get(ship.getHullSize()));
				ship.getMutableStats().getAcceleration().modifyMult(ship.getId()+"_atmo",(float)MANUVER_MALUS.get(ship.getHullSize()));
				ship.getMutableStats().getTurnAcceleration().modifyMult(ship.getId()+"_atmo",(float)MANUVER_MALUS.get(ship.getHullSize()));	
				Global.getCombatEngine().maintainStatusForPlayerShip("atmo", "graphics/ui/icons/icon_repair_refit.png","In Atmoshpere", "Manuverability reduced",true);	
				if(!ship.isAlive() || ship.isHulk() || !engine.isEntityInPlay(ship))
				{
					engine.removeEntity(ship);					
				}				
			}										
		}	
	}
	
	private Color getSmokeColor() {
		Random rand = new Random();
		// Define the range for fiery red-orange shades
		float r = 0.04f + rand.nextFloat() * 0.1f; // Minimal red for a cold feel
		float g = 0.03f + rand.nextFloat() * 0.04f; // Cool greenish-blue
		float b = 0.02f + rand.nextFloat() * 0.02f; // Vivid blue tones

		// Return the color with adjusted alpha based on the ratio
		return new Color(r, g, b, .1f); 
	}		

    @Override
    public void init(CombatEngineAPI engine)
    {
        this.engine = engine;
    }
}
