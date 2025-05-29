package data.scripts.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;
import java.util.*;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;
import java.awt.Color;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.MathUtils;




public class armaa_shaftBattlePlugin extends BaseEveryFrameCombatPlugin
{
	// for atmo battle
	boolean warning = true;
    protected CombatEngineAPI engine;
	private IntervalUtil effectInterval = new IntervalUtil(0.05f,1.5f);
	private IntervalUtil interval3 = new IntervalUtil(2f, 2f);
	private boolean playSecondPhase, playedSecondPhase = false;
	private IntervalUtil attackInterval = new IntervalUtil(3f, 3f);	
	private float spin = 0f;
	private float depth = 0.01f;
	private boolean startedMusic = false;
	private boolean finalBossSpawn = false;
	boolean reinforcementsTriggered = false;
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
		Color bgColor = new Color(30,15,0,150);
		Color endColor = new Color(25,25,25,255);
		Color endColor2 = new Color(25,25,25,120);	
		Color endColor3 = new Color(0,0,0,0);			
		engine.setBackgroundGlowColor(shiftColor(bgColor,endColor));
		engine.setBackgroundColor(endColor);		
		if(!reinforcementsTriggered && engine.getElapsedInContactWithEnemy() > 1)
		{			
			reinforcementsTriggered = true;
		}
			String str = "armaa_shaft";
			float initialWidth = Global.getSettings().getScreenWidth();
			float initialHeight = Global.getSettings().getScreenWidth();
			float currentWidth = initialWidth;
			float currentHeight = initialHeight;
			if(engine.getCustomData().get("armaa_bgHeight")== null)
			{			
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
				-1, 
				0f, 
				CombatEngineLayers.CLOUD_LAYER
			);			
			effectInterval.advance(amount);
			float cloudSize = (float)(MathUtils.getRandomNumberInRange(300,600));				
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
				-1, 
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
				-1, 
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
				-1, 
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
				-1, 
				0f, 
				CombatEngineLayers.CLOUD_LAYER
			);
			float vel = MathUtils.getRandomNumberInRange(-200,200);		
	
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

			depth+=amount;
			spin+=amount;
			MagicRender.screenspace(
			Global.getSettings().getSprite("misc", "armaa_atmo_cloud"),
			MagicRender.positioning.CENTER, 
			new Vector2f(0,0), 
			new Vector2f(vel,vel), 			
			new Vector2f(cloudSize,cloudSize),
			new Vector2f(100f,100f),		
			0f,
			spin/5f,
			shiftColor(new Color(0,10,20,255),new Color(0,0,0,255)),
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
			if(effectInterval.intervalElapsed())
			{
				MagicRender.screenspace(
				Global.getSettings().getSprite("misc", "armaa_atmo_cloud"),
				MagicRender.positioning.CENTER, 
				new Vector2f(0,0), 
				new Vector2f(vel,vel), 			
				new Vector2f(cloudSize,cloudSize),
				new Vector2f(100f,100f),		
				0f,
				spin/2,
				shiftColor(new Color(20,10,0,255),new Color(0,0,0,255)),
				false,
				0f,
				0f,
				0f,
				0f,
				0f,
				-1,
				-1,
				1f,
				CombatEngineLayers.BELOW_SHIPS_LAYER
				);					
				float xVel = (float)(MathUtils.getRandomNumberInRange(-100,100));
				MagicRender.screenspace(
				Global.getSettings().getSprite("misc", "armaa_atmo_cloud"),
				MagicRender.positioning.CENTER, 
				new Vector2f(0,0), 
				new Vector2f(vel,vel), 			
				new Vector2f(cloudSize,cloudSize),
				new Vector2f(100f,100f),		
				0f,
				spin/5f,
				shiftColor(new Color(20,0,10,255),new Color(0,0,0,255)),
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
			}			
			if(Math.random() < 0.20f)
			{

			}	
			if(Math.random() < 0.20f && engine.getFleetManager(1).getCurrStrength() < 5)
			{			
				Vector2f loc = new Vector2f(MathUtils.getRandomNumberInRange(minX,maxX),MathUtils.getRandomNumberInRange(minY,maxY));				
				engine.spawnExplosion(loc, new Vector2f(), new Color(250,200,75,150), 200f, 1f);
				Global.getSoundPlayer().playUISound("gate_explosion", MathUtils.getRandomNumberInRange(0.5f,1.5f), 10f);					
			}			
		}
		
		if(!engine.isPaused())
		{
			float bgStage = (float) engine.getElapsedInContactWithEnemy() / 100;			
			Vector2f vec = (Vector2f)engine.getCustomData().get("armaa_atmoWarningLoc"+0);

			for(CombatEntityAPI asteroid: engine.getAsteroids())
			{
				engine.removeEntity(asteroid);
			}
			for (ShipAPI ship : engine.getShips())
			{
				ship.getMutableStats().getSensorProfile().modifyMult(ship.getId()+"_atmo",0.50f);					
				ship.getMutableStats().getSensorStrength().modifyMult(ship.getId()+"_atmo",0.50f);	
				Global.getCombatEngine().maintainStatusForPlayerShip("atmo", "graphics/ui/icons/icon_repair_refit.png","EM Interference", "Sensor range reduced",true);	
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
		float r = 0.1f + rand.nextFloat() * 0.2f; // Minimal red for a cold feel
		float g = 0.03f + rand.nextFloat() * 0.1f; // Cool greenish-blue
		float b = 0.01f + rand.nextFloat() * 0.1f; // Vivid blue tones

		// Return the color with adjusted alpha based on the ratio
		return new Color(r, g, b, .1f); 
	}		

    @Override
    public void init(CombatEngineAPI engine)
    {
        this.engine = engine;
    }
}
