package data.scripts.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import java.util.*;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;
import org.magiclib.util.MagicFakeBeam;
import java.awt.Color;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.MathUtils;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;

import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;


public class armaa_atmosphericBossBattlePlugin extends BaseEveryFrameCombatPlugin
{
	// for atmo battle
	boolean warning = true;
	boolean wave1Spawn = false;
	boolean bossSpawn = false;
	boolean wave2Spawn = false;
    protected CombatEngineAPI engine;
	private IntervalUtil interval = new IntervalUtil(.025f, .05f);
	private IntervalUtil interval2 = new IntervalUtil(.05f, .05f);
	private IntervalUtil interval3 = new IntervalUtil(2f, 2f);
	private IntervalUtil attackInterval = new IntervalUtil(3f, 3f);	
	private IntervalUtil attackInterval2 = new IntervalUtil(2f, 2f);		
	private float spin = 0f;
    public static Map MANUVER_MALUS = new HashMap();
    static {
        MANUVER_MALUS.put(HullSize.FIGHTER, 0.90f);
        MANUVER_MALUS.put(HullSize.FRIGATE, 0.80f);
        MANUVER_MALUS.put(HullSize.DESTROYER, 0.70f);
        MANUVER_MALUS.put(HullSize.CRUISER, 0.60f);
        MANUVER_MALUS.put(HullSize.CAPITAL_SHIP, 0.50f);
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

    @Override
    public void advance(float amount, List<InputEventAPI> events)
    {
		if(engine == null)
			return;
        Color startColor = new Color(85, 0, 0, 255); // Starting color: (200, 0, 0)
        Color endColor = new Color(15, 30, 75, 100);   // Ending color: (0, 0, 200)
        Color startColorBG = new Color(100, 50, 0, 255); // Starting color: (200, 0, 0)
        Color endColorBG = new Color(175, 175, 175, 255);   // Ending color: (0, 0, 200)
        Color endColorBG2 = new Color(0, 0, 0, 0);   // Ending color: (0, 0, 200)		
		engine.setBackgroundColor(new Color(150,75,75,255));
		engine.setBackgroundGlowColor(shiftColor(endColor,startColor));
		if(!wave1Spawn)
		{									
			wave1Spawn = true;
			engine.getFleetManager(1).spawnShipOrWing("hound_d_pirates_Overdriven",new Vector2f(-100,-10000),90f,10f);
			engine.getFleetManager(1).spawnShipOrWing("hound_d_pirates_Overdriven",new Vector2f(0,-10000),90f,10f);	
			engine.getFleetManager(1).spawnShipOrWing("hound_d_pirates_Overdriven",new Vector2f(100f,-10000),90f,10f);				
		}
		if(!engine.isPaused())
		{
			float ratio = (float) engine.getElapsedInContactWithEnemy() / 100;
			interval3.advance(amount);
			float mult = engine.getViewport().getViewMult();
			Global.getCombatEngine().maintainStatusForPlayerShip("AceSystem3", "graphics/ui/icons/icon_repair_refit.png", "mult",""+ ratio,false);		
			float minX = engine.getViewport().getLLX(); // Leftmost X-coordinate of the viewport
			float maxX = engine.getViewport().getLLX() + engine.getViewport().getVisibleWidth(); // Rightmost X-coordinate of the viewport	
			float minY = engine.getViewport().getLLY(); // Leftmost X-coordinate of the viewport
			float maxY = engine.getViewport().getLLY() + engine.getViewport().getVisibleHeight(); // Rightmost X-coordinate of the viewport
			if(ratio<60)
				MagicRender.screenspace(
					Global.getSettings().getSprite("misc", "armaa_atmo2"),
					MagicRender.positioning.CENTER, 
					new Vector2f(0,0), 
					new Vector2f(0,0), 
					new Vector2f(Global.getSettings().getScreenWidth()*(1.8f),Global.getSettings().getScreenWidth()*(1.8f)), 
					new Vector2f(0,0),
					spin, 
					0f, //spin 
					shiftColor(endColorBG,new Color(0,0,0,255)), 
					false, 
					0f, 
					0f, 
					0f, 
					0f, 
					0f, 
					0f, 
					1, 
					0f, 
					CombatEngineLayers.CLOUD_LAYER
				);
				if(ratio > 0.60f)
				{
					float size = Math.max(Global.getSettings().getScreenWidth(),Global.getSettings().getScreenWidth()*((1.5f+3f)/((ratio*2)-0.60f)));
					MagicRender.screenspace(
						Global.getSettings().getSprite("misc", "armaa_atmo"),
						MagicRender.positioning.CENTER,
						new Vector2f(0,0), 
						new Vector2f(0,0), 
						new Vector2f(size,size), 
						new Vector2f(50,50), 
						spin,
						0f, //spin 
						shiftColor(endColorBG2,new Color(155,125,125,255)), 
						false, 
						0f, 
						0, 
						0f, 
						0f, 
						0f, 
						0f, 
						1, 
						0f, 
						CombatEngineLayers.CLOUD_LAYER  
					);
				}
				spin-= amount;
				float shipSize = Math.min(1f,(engine.getTotalElapsedTime(false)/100));
				SpriteAPI spr2 = Global.getSettings().getSprite(Global.getSettings().getHullSpec("invictus").getSpriteName());
				//SpriteAPI spr = Global.getSettings().getSprite("misc", "armaa_atmo_station");
				if(shipSize < 1)
				{
					MagicRender.screenspace(
					spr2,
					MagicRender.positioning.CENTER,				
					new Vector2f(0+500f*shipSize,1000-(2000f*(shipSize*2))),
					new Vector2f(0,0),
					new Vector2f((spr2.getWidth()*(4f)/mult),(spr2.getHeight()*(4f))/mult),
					new Vector2f(0,0),		
					0f,
					0f,
					new Color(0,0,0,0.25f),
					false,
					0f,
					0f,
					0f,
					0f,
					0f,
					0f,
					amount,
					0f,
					CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER
					);
					if(attackInterval2.intervalElapsed())
					{
						for(int i = 0; i < 3; i++)
						{
							Vector2f vec = (Vector2f)engine.getCustomData().get("armaa_atmoWarningLoc"+i);
							if(warning)
							{
								vec = new Vector2f(MathUtils.getRandomNumberInRange(minX,maxX),MathUtils.getRandomNumberInRange(minY,maxY));
								MagicRender.battlespace(
								Global.getSettings().getSprite("ceylon", "armaa_ceylontarget"),
								vec,
								new Vector2f(0,0),
								new Vector2f(1050,1050),
								new Vector2f(-250f,-250f),		
								0f,
								10f,
								new Color(255,75,0,150),
								false,
								0f,
								0f,
								0f,
								0f,
								0f,
								0.2f,
								1f,
								0.5f,
								CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER
								);
								MagicRender.battlespace(
								Global.getSettings().getSprite("ceylon", "armaa_ceylontarget"),
								vec,
								new Vector2f(0,0),
								new Vector2f(1150,1150),
								new Vector2f(-250f,-250f),		
								0f,
								-10f,
								new Color(255,150,0,150),
								false,
								0f,
								0f,
								0f,
								0f,
								0f,
								0.2f,
								1f,
								0.5f,
								CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER
								);					
								warning = false;
								engine.getCustomData().put("armaa_atmoWarningLoc"+i,vec);							
							}
							else if(shipSize < 0.30f)
							{
								for (ShipAPI ship : engine.getShips())
								{
									if(ship.getOwner() != 1)
										continue;
									if(!engine.getCustomData().containsKey("armaa_atmoWarningLoc"+i))
										continue;
									vec = (Vector2f)engine.getCustomData().get("armaa_atmoWarningLoc"+i);
									DamagingExplosionSpec spec = new DamagingExplosionSpec(1f, 1000f, 500f, 500f, 50f, CollisionClass.HITS_SHIPS_AND_ASTEROIDS , CollisionClass.HITS_SHIPS_AND_ASTEROIDS, 5f, 10f, 1f, 10, Color.yellow, Color.red);
									engine.spawnDamagingExplosion(spec, ship, vec, true); 										
									engine.spawnExplosion(vec, new Vector2f(), new Color(250,150,75,150), 1000f, 1f);
									break;
								}
								warning = true;
							}							
						}
					}
				}
				for(int i = 0; i < 2 && !wave2Spawn && ratio > 0.50f; i++)
				{
					shipSize = Math.min(1f,ratio-0.50f);
					float xPos = i*100;
					float yPos = -1000;
					if(i != 1)
						yPos = - 600;
					spr2 = Global.getSettings().getSprite(Global.getSettings().getHullSpec("hound").getSpriteName());
					//SpriteAPI spr = Global.getSettings().getSprite("misc", "armaa_atmo_station");
					MagicRender.screenspace(
					spr2,
					MagicRender.positioning.CENTER,				
					new Vector2f(xPos,yPos+(2000f*(shipSize))),
					new Vector2f(0,0),
					new Vector2f(spr2.getWidth()*(shipSize),spr2.getHeight()*(shipSize)),
					new Vector2f(0,0),		
					180f*shipSize,
					0f,
					new Color(0.3f*shipSize,0.3f*shipSize,0.3f*shipSize,1f),
					false,
					0f,
					0f,
					0f,
					0f,
					0f,
					0f,
					amount,
					0f,
					CombatEngineLayers.BELOW_SHIPS_LAYER
					);
					if(!wave2Spawn && shipSize >= 1)
					{								
						float newX = engine.getViewport().getCenter().getX()+xPos;
						float newY = engine.getViewport().getCenter().getY()+(yPos+(1000f*(shipSize)));
						engine.getFleetManager(1).spawnShipOrWing("armaa_valkenx_ss_assault",new Vector2f(newX,10000),270f,1f);	
						engine.getFleetManager(1).spawnShipOrWing("armaa_valkenx_ss_assault",new Vector2f(newX+100f,10000),270f,1f);						
						engine.getFleetManager(1).spawnShipOrWing("armaa_valkenx_ss_assault",new Vector2f(newX,10000),270f,1f);	
						engine.getFleetManager(1).spawnShipOrWing("armaa_valkenx_ss_assault",new Vector2f(newX+100f,10000),270f,1f);	
						PersonAPI pilot = OfficerManagerEvent.createOfficer(engine.getFleetManager(0).getDefaultCommander().getFaction(),5, true);							
						engine.getFleetManager(0).spawnShipOrWing("armaa_altagrave_ex_Standard",new Vector2f(0f,-10000f),90f,20f).setCaptain(pilot);						
						engine.getFleetManager(0).spawnShipOrWing("glimmer_Assault",new Vector2f(0f,-10000f),90f,15f);	
						engine.getFleetManager(0).spawnShipOrWing("glimmer_Assault",new Vector2f(100f,-10000f),90f,15f);	
						engine.getFleetManager(0).spawnShipOrWing("glimmer_Assault",new Vector2f(-100f,-10000f),90f,15f);								
						wave2Spawn = true;
						break;
					}						
				}			
			float bgStage = (float) engine.getElapsedInContactWithEnemy() / 100;			
			if(bgStage > 1f && !bossSpawn)
			{

				float size = Math.min(1f,(engine.getElapsedInContactWithEnemy()/100)-0.5f);
			    SpriteAPI spr = Global.getSettings().getSprite(Global.getSettings().getHullSpec("invictus").getSpriteName());
				//SpriteAPI spr = Global.getSettings().getSprite("misc", "armaa_atmo_station");
				MagicRender.screenspace(
				spr,
				MagicRender.positioning.CENTER,				
				new Vector2f(0,-1000+(2000f*(size))),
				new Vector2f(0,0),
				new Vector2f(spr.getWidth()*(size),spr.getHeight()*(size)),
				new Vector2f(0,0),		
				180f*size,
				0f,
				new Color(0.10f*size,0.10f*size,0.10f*size,1f),
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
				if(!bossSpawn && size >= 1)
				{
					engine.getFleetManager(1).spawnShipOrWing("armaa_invictus_Standard",new Vector2f(0,10000f),270f,20f).setCaptain(Global.getSector().getImportantPeople().getPerson("armaa_kade"));	
					engine.getFleetManager(1).spawnShipOrWing("armaa_valkazard_boss",new Vector2f(0,10000f),270f,20f).setCaptain(Global.getSector().getImportantPeople().getPerson("armaa_ironking"));						
					//engine.getFleetManager(0).spawnShipOrWing("armaa_garegga_xiv_standard",new Vector2f(-0,-0),0f,0f);				
					bossSpawn = true;
				}				
			}			
			if(interval3.intervalElapsed() && ratio > 0.55f)
			{		
				for(int i = 0; i < Math.random()*20*mult; i++)
				{
					int random = MathUtils.getRandomNumberInRange(0,3);
					if(random == 1)
						random = 0;
					float xVel = (float)(MathUtils.getRandomNumberInRange(-800,800));
					float cloudSize = (float)(MathUtils.getRandomNumberInRange(20,40));
					MagicRender.battlespace(
					Global.getSettings().getSprite("backgrounds", "star2"),
					new Vector2f(MathUtils.getRandomNumberInRange(minX,maxX),MathUtils.getRandomNumberInRange(engine.getViewport().getCenter().y-engine.getViewport().getVisibleHeight(),engine.getViewport().getCenter().y+engine.getViewport().getVisibleHeight())),
					new Vector2f(xVel,MathUtils.getRandomNumberInRange(-800,800)),
					new Vector2f(cloudSize,cloudSize),
					new Vector2f(1f,1f),		
					0f,
					0f,
					shiftColor(endColor,new Color(255, 150, 6,255)),
					false,
					0f,
					0f,
					0f,
					0f,
					0f,
					0.5f*mult,
					2f*mult,
					0.5f*mult,
					CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER
					);
				}
				
				for(int i = 0; i < Math.random()*15*mult; i++)
				{
					float xVel = (float)(MathUtils.getRandomNumberInRange(-100,100));
					float cloudSize = (float)(MathUtils.getRandomNumberInRange(1200,1800)*(Math.random()*1.25));
					MagicRender.battlespace(
					Global.getSettings().getSprite("misc", "armaa_atmo_cloud"),
					new Vector2f(MathUtils.getRandomNumberInRange(minX,maxX),MathUtils.getRandomNumberInRange(minY,maxY)),
					new Vector2f(xVel,-MathUtils.getRandomNumberInRange(1800,2000)),
					new Vector2f(cloudSize,cloudSize),
					new Vector2f(50f,50f),		
					90f,
					0f,
					shiftColor(endColor,new Color(75,30,30,150)),
					false,
					0f,
					0f,
					0f,
					0f,
					0f,
					0.5f*mult,
					2f*mult,
					0.5f*mult,
					CombatEngineLayers.BELOW_SHIPS_LAYER
					);
				}
				for(int i = 0; i < Math.random()*15*mult; i++)
				{
					float xVel = (float)(MathUtils.getRandomNumberInRange(-500,500));
					float cloudSize = (float)(MathUtils.getRandomNumberInRange(1000,1000));
					MagicRender.battlespace(
					Global.getSettings().getSprite("misc", "armaa_atmo_cloud"),
					new Vector2f(MathUtils.getRandomNumberInRange(minX,maxX),MathUtils.getRandomNumberInRange(minY,maxY)),
					new Vector2f(xVel,-3000),
					new Vector2f(cloudSize,cloudSize),
					new Vector2f(0f,0f),		
					90f,
					0f,
					shiftColor(endColor,new Color(80,75,75,100)),
					false,
					0f,
					0f,
					0f,
					0f,
					0f,
					0.4f*mult,
					2f*mult,
					0.5f*mult,
					CombatEngineLayers.ABOVE_SHIPS_LAYER
					);
				}
				
			}
			if(interval3.intervalElapsed() && ratio > 0.50f && ratio < 0.80f)
			{
				for(int i = 0; i < Math.random()*10*mult; i++)
				{
					float xVel = (float)(MathUtils.getRandomNumberInRange(-500,500));
					float cloudSize = (float)(MathUtils.getRandomNumberInRange(4000,5000));
					MagicRender.battlespace(
					Global.getSettings().getSprite("misc", "armaa_atmo_cloud"),
					new Vector2f(MathUtils.getRandomNumberInRange(minX,maxX),MathUtils.getRandomNumberInRange(minY,maxY)),
					new Vector2f(xVel,-2000),
					new Vector2f(cloudSize,cloudSize),
					new Vector2f(0f,0f),		
					90f,
					0f,
					new Color(50,25,25,255),
					false,
					0f,
					0f,
					0f,
					0f,
					0f,
					0.2f*mult,
					2f*mult,
					0.2f*mult,
					CombatEngineLayers.BELOW_SHIPS_LAYER
					);
				}	
				
			}
			else if(interval3.intervalElapsed() && ratio < 0.60f)
			{
				List<ShipAPI> ships = engine.getShips();
				Collections.shuffle(ships);				
				for (ShipAPI ship : ships)
				{
					if(ship == engine.getPlayerShip())
					{
						float mapWidth = Global.getCombatEngine().getMapWidth() / 2f, mapHeight = Global.getCombatEngine().getMapHeight() / 2f;
						Vector2f rawLL = new Vector2f(-mapWidth, -mapHeight),rawUR = new Vector2f(mapWidth, mapHeight);
						MagicFakeBeam.spawnFakeBeam(
							engine, 
							new Vector2f(MathUtils.getRandomNumberInRange(minX,maxX),maxY), 
							40000f, 
							VectorUtils.getAngle(new Vector2f(MathUtils.getRandomNumberInRange(minX,maxX),maxY),new Vector2f(engine.getPlayerShip().getLocation().getX(),engine.getPlayerShip().getLocation().getY())), 
							40f, 
							1f, 
							1f, 
							40f, 
							new Color(255,0,0), 
							Color.red, 
							100f, 
							DamageType.ENERGY, 
							0f, 
							ships.get(1)
						);
						break;
					}
				}
				for(int i = 0; i < Math.random()*5*mult; i++)
				{
					float xVel = (float)(MathUtils.getRandomNumberInRange(-100,100));
					float cloudSize = (float)(MathUtils.getRandomNumberInRange(600,800)*(Math.random()*2));
					MagicRender.battlespace(
					Global.getSettings().getSprite("misc", "armaa_atmo_cloud"),
					new Vector2f(MathUtils.getRandomNumberInRange(minX,maxX),engine.getViewport().getCenter().y+engine.getViewport().getVisibleHeight()),
					new Vector2f(xVel,-MathUtils.getRandomNumberInRange(200,1000)*mult),
					new Vector2f(MathUtils.getRandomNumberInRange(cloudSize*0.75f,cloudSize*1.25f),MathUtils.getRandomNumberInRange(cloudSize*0.75f,cloudSize*1.25f)),
					new Vector2f(0f,0f),		
					0f,
					0f,
					new Color(255,255,255,100),
					false,
					0f,
					0f,
					0f,
					0f,
					0f,
					0.2f*mult,
					1f*mult,
					0.5f*mult,
					CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER
					);
				}
				
				for(int i = 0; i < Math.random()*15*mult; i++)
				{
					float xVel = (float)(MathUtils.getRandomNumberInRange(-250,250));
					float cloudSize = (float)(MathUtils.getRandomNumberInRange(1000,2400)*(Math.random()*2));
					MagicRender.battlespace(
					Global.getSettings().getSprite("misc", "armaa_atmo_cloud"),
					new Vector2f(MathUtils.getRandomNumberInRange(minX,maxX),engine.getViewport().getCenter().y+engine.getViewport().getVisibleHeight()),
					new Vector2f(xVel,-MathUtils.getRandomNumberInRange(100,300)*mult),
					new Vector2f(MathUtils.getRandomNumberInRange(cloudSize*0.75f,cloudSize*1.25f),MathUtils.getRandomNumberInRange(cloudSize*0.75f,cloudSize*1.25f)),
					new Vector2f(50f,50f),		
					0f,
					0f,
					new Color(150,150,150,225),
					false,
					0f,
					0f,
					0f,
					0f,
					0f,
					0.4f*mult,
					2f*mult,
					1f*mult,
					CombatEngineLayers.BELOW_SHIPS_LAYER
					);
				}
				
			}
		}
		if(!engine.isPaused())
		{
			float bgStage = (float) engine.getElapsedInContactWithEnemy() / 100;			
			interval.advance(amount);
			attackInterval.advance(amount);
			attackInterval2.advance(amount);			
			/*
			Vector2f vec = (Vector2f)engine.getCustomData().get("armaa_atmoWarningLoc"+0);

			if(vec != null && !warning)
			{
				
				float elapsed = attackInterval.getElapsed();
				float maxinterval = attackInterval.getMaxInterval();
				float ratio = Math.min(1f,elapsed/maxinterval);
				SpriteAPI enemySpr = Global.getSettings().getSprite(Global.getSettings().getHullSpec("armaa_valkenx").getSpriteName());
				MagicRender.battlespace(
				enemySpr,
				vec,
				new Vector2f(0,0),
				new Vector2f(enemySpr.getWidth()*ratio,enemySpr.getWidth()*ratio),
				new Vector2f(0,0),		
				-10f,
				0f,
				new Color(0.5f*ratio,0.5f*ratio,0.5f*ratio,1f),
				false,
				0f,
				0f,
				0f,
				0f,
				0f,
				0f,
				amount,
				0f,
				CombatEngineLayers.FRIGATES_LAYER
				);		
			}
			*/
			
			if(attackInterval.intervalElapsed() && bgStage < 0.15f)
			{
			
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
			}										
		}	
	}

    @Override
    public void init(CombatEngineAPI engine)
    {
        this.engine = engine;
    }
}
