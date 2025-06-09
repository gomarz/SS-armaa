package data.scripts.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import java.util.*;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import java.awt.Color;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.lazywizard.lazylib.MathUtils;

public class armaa_cityBattlePlugin extends BaseEveryFrameCombatPlugin
{

    protected CombatEngineAPI engine;
	private final IntervalUtil interval2 = new IntervalUtil(.05f, .3f);
	private float ratio = 0f;
	private final IntervalUtil interval3 = new IntervalUtil(5f, 5f);
	private final IntervalUtil spinInterval = new IntervalUtil(0.08f, 0.08f);        
	private float spin = 0f;
    public final static Map MANUVER_MALUS = new HashMap();
	boolean reinforcementsTriggered = false, wave2Triggered = false, timeUp = false;
	ShipAPI ceylon, morgana;
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
	
    @Override
    public void advance(float amount, List<InputEventAPI> events)
    {
		if(engine == null)
			return;
        Color startColor = new Color(150, 150, 150, 244); // Starting color: (200, 0, 0)
        Color endColor = new Color(15, 30, 75, 0);   // Ending color: (0, 0, 200)	
		float elapsed = interval3.getElapsed();
		float maxinterval = interval3.getMaxInterval();
		float rate = Math.min(1f,elapsed/maxinterval);
		if(!reinforcementsTriggered)
		{
			engine.setBackgroundGlowColor(shiftColor(new Color(50,50,50,80),new Color(75,50,0,25)));	
			engine.getFleetManager(0).setSuppressDeploymentMessages(true);			
			Global.getSoundPlayer().playCustomMusic(1,1,"music_armaa_citybattle",true);		
			PersonAPI pilot = OfficerManagerEvent.createOfficer(engine.getFleetManager(0).getDefaultCommander().getFaction(),5, true);				
			PersonAPI pilot3 = Global.getSector().getImportantPeople().getPerson("armaa_redeye");	
			ShipAPI f = engine.getFleetManager(0).spawnShipOrWing("armaa_xyphos_standard",new Vector2f(-500,0),270f,0f);	
			f.setOwner(0);
			f.setOriginalOwner(0); 
			f = engine.getFleetManager(0).spawnShipOrWing("omen_PD",new Vector2f(-100,0),90f,0f);
			f = engine.getFleetManager(0).spawnShipOrWing("armaa_garegga_xiv_carrier_standard",new Vector2f(0,0),270f,0f);
			f.setCaptain(pilot3);
			f.getChildModulesCopy().get(0).setCaptain(pilot3);
			engine.getCombatUI().addMessage(1,f,Misc.getBasePlayerColor(),"Deadeye",Color.white,":",Color.white,""+Global.getSector().getPlayerPerson().getRank()+" "+Global.getSector().getPlayerPerson().getName().getLast()+"?! They're throwing everything they have at us! We'll follow your lead!");
			f = engine.getFleetManager(0).spawnShipOrWing("tempest_Attack",new Vector2f(-100,0),90f,6000f);			
			f = engine.getFleetManager(0).spawnShipOrWing("brawler_pather_Raider",new Vector2f(-500,6000),80f,3f);				
			f = engine.getFleetManager(0).spawnShipOrWing("armaa_gallant_frig_standard",new Vector2f(-500,1050),90f,0f);
			engine.getFleetManager(0).setSuppressDeploymentMessages(false); 	
			reinforcementsTriggered = true;
		}
		
		if(ratio < 0.80f)
		{
			MagicRender.screenspace(
				Global.getSettings().getSprite("misc", "armaa_atmo3"),
				MagicRender.positioning.CENTER, 
				new Vector2f(0,Global.getSettings().getScreenWidth()*1+3*ratio-Global.getSettings().getScreenWidth()*1*rate+3*ratio), 
				new Vector2f(0,0), 
				new Vector2f(Global.getSettings().getScreenWidth()*1+3*ratio,Global.getSettings().getScreenWidth()*1+3*ratio), 
				new Vector2f(0,0),
				0f, 
				0f, //spin 
				shiftColor(new Color (50,50,50,255),new Color(150,150,125,255)),
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
				Global.getSettings().getSprite("misc", "armaa_atmo3"),
				MagicRender.positioning.CENTER, 
				new Vector2f(0,-Global.getSettings().getScreenWidth()*1*rate+3*ratio), 
				new Vector2f(0,0), 
				new Vector2f(Global.getSettings().getScreenWidth()*1+3*ratio,Global.getSettings().getScreenWidth()*1+3*ratio), 
				new Vector2f(0,0),
				0, 
				0f, //spin 
				shiftColor(new Color (50,50,50,255),new Color(150,150,125,255)),
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
		}
		else if(ratio >= 0.8f)
		MagicRender.screenspace(
			Global.getSettings().getSprite("misc", "armaa_city2"),
			MagicRender.positioning.CENTER, 
			new Vector2f(0,0), 
			new Vector2f(0,0), 
			new Vector2f(Global.getSettings().getScreenWidth()*1.2f,Global.getSettings().getScreenWidth()*1.2f), 
			new Vector2f(0,0),
			spin/10f, 
			0f, //spin 
			shiftColor(new Color (50,50,50,255),new Color(75,75,100,255)),
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
		if(!engine.isPaused())
		{
			ratio = (float) engine.getElapsedInContactWithEnemy() / 100;
			interval3.advance(amount);
			interval2.advance(amount);			
			float mult = engine.getViewport().getViewMult();	
			float minX = engine.getViewport().getLLX(); // Leftmost X-coordinate of the viewport
                        // Leftmost X-coordinate of the viewport
			float maxX = engine.getViewport().getLLX() + engine.getViewport().getVisibleWidth();
                    // Leftmost X-coordinate of the viewport
                    // Rightmost X-coordinate of the viewport	
			float maxY = engine.getViewport().getLLY() + engine.getViewport().getVisibleHeight(); // Rightmost X-coordinate of the viewport	

			if(ratio > 3 && !timeUp || (ceylon != null && !ceylon.isAlive() && !timeUp))
			{
				
				if(ceylon != null && ceylon.isAlive())
				{
					engine.getCombatUI().addMessage(1,ceylon,Color.orange,ceylon.getName(),Color.white,":",Color.white,"...the city is lost. All remaining units, pull back.");
					ceylon.setRetreating(true,true);
					engine.getFleetManager(1).getTaskManager(false).orderRetreat(engine.getFleetManager(1).getDeployedFleetMember(ceylon),true,false);					
					ceylon = null;						
				}
				if(morgana != null && morgana.isAlive())
				{
					engine.getCombatUI().addMessage(1,morgana,Color.orange,morgana.getName(),Color.white,":",Color.white,"Rapier-1 and Rapier-2, withdrawing..");
					morgana.setRetreating(true,true);
					engine.getFleetManager(1).getTaskManager(false).orderRetreat(engine.getFleetManager(1).getDeployedFleetMember(ceylon),true,false);		
					morgana = null;
				}
				timeUp = true;
				//engine.getFleetManager(1
			}
			if(!wave2Triggered && ( (ratio > 0.1f && engine.getFleetManager(1).getCurrStrength() < 25) || ratio >= 2f ) )
			{
				//Global.getSoundPlayer().playCustomMusic(1,1,"music_armaa_ax_bounty",true);				
				boolean losing = engine.getFleetManager(1).getCurrStrength() < 25;
				PersonAPI pilot = OfficerManagerEvent.createOfficer(engine.getFleetManager(0).getDefaultCommander().getFaction(),5, true);				
				ShipAPI f = engine.getFleetManager(1).spawnShipOrWing("armaa_ceylon_boss",new Vector2f(0,10000),270f,15f);
				engine.getFleetManager(1).spawnShipOrWing("heron_Attack",new Vector2f(200,10000),270f,15f);
				engine.getFleetManager(1).spawnShipOrWing("armaa_bassline_standard",new Vector2f(-200,10000),270f,15f);	
				engine.getFleetManager(1).spawnShipOrWing("armaa_bassline_standard",new Vector2f(400,10000),270f,15f);	
				engine.getFleetManager(1).spawnShipOrWing("armaa_bassline_standard",new Vector2f(-400,10000),270f,15f);						
				f.setName("DAS Ceylon");
				f.setCaptain(pilot);					
				if(losing)
				{
					engine.getCombatUI().addMessage(1,f,Color.red,f.getName(),Color.white,":",Color.white,"Didn't expect you to carve through the vanguard like that. This was supposed to be a trap. All remaining units, close in and engage!");
					
				}
				else
					engine.getCombatUI().addMessage(1,f,Color.red,f.getName(),Color.white,":",Color.white,"DAS Ceylon inbound. Scum in the outskirts put up a fight.. pointless, of course. Still more trash to clear. Beginning approach.");
				pilot = OfficerManagerEvent.createOfficer(engine.getFleetManager(0).getDefaultCommander().getFaction(),10, true);
				for(int i = 0; i < 12; i++)
				{
					f = engine.getFleetManager(1).spawnShipOrWing("armaa_morganamp_standard",new Vector2f(MathUtils.getRandomNumberInRange(-2000,2000),MathUtils.getRandomNumberInRange(-10000,10000)),270f,3f);
					f.setAnimatedLaunch();
				}		
				f.setCaptain(pilot);
				wave2Triggered = true;
			}					
			spinInterval.advance(amount);
			if(spinInterval.intervalElapsed())
				spin++;

			if(ratio >= .75f && ratio < .80f)
			{
				float xVel = (float)(MathUtils.getRandomNumberInRange(-300,300));	
				if(Math.random() < 0.30f)
				MagicRender.screenspace(
					Global.getSettings().getSprite("misc", "armaa_atmo_cloud"),
					MagicRender.positioning.CENTER, 
					new Vector2f(0,0), 
					new Vector2f(xVel,-MathUtils.getRandomNumberInRange(-600,600)), 
					new Vector2f(Global.getSettings().getScreenWidth()*1.2f,Global.getSettings().getScreenWidth()*1.2f), 
					new Vector2f(0,0),
					spin, 
					0f, //spin 
					new Color (50,50,50,75), 
					false, 
					0f, 
					0f, 
					0f, 
					0f, 
					0f, 
					0.2f, 
					2f, 
					1.5f, 
					CombatEngineLayers.BELOW_SHIPS_LAYER
				);					
			}
			if(interval3.intervalElapsed())
			{

				if((ratio > 0.45f) && (ratio < 0.80f ))
				{
					ShipAPI f = engine.getFleetManager(1).spawnShipOrWing("talon_wing",new Vector2f(MathUtils.getRandomNumberInRange(-1000,1000),MathUtils.getRandomNumberInRange(4000,10000)),270f,5f);							
					if(f.getWing() == null)
					{
					}
					else
						for( ShipAPI fighter:f.getWing().getWingMembers())
						{
							fighter.setAnimatedLaunch();
						}
					if(ratio <= 0.25f)
					{
						String wing = Math.random() > 0.30f ? "gladius_wing" : "armaa_valkenx_wing";
						engine.getFleetManager(0).setSuppressDeploymentMessages(true); 
						f = engine.getFleetManager(0).spawnShipOrWing(wing,new Vector2f(MathUtils.getRandomNumberInRange(-1000,1000),-6000),90f,5f);
						engine.getFleetManager(0).setSuppressDeploymentMessages(false); 						
						f.setAlly(true);							
						for( ShipAPI fighter:f.getWing().getWingMembers())
						{
							fighter.setAnimatedLaunch();
						}
					}					
				}				
				if(ratio > 1 && interval2.intervalElapsed())
				for(int i = 0; i < Math.random()*2; i++)
				{
					float xVel = (float)(MathUtils.getRandomNumberInRange(-1000,1000));
					float cloudSize = (float)(MathUtils.getRandomNumberInRange(1200,2200)*(Math.random()*2));
					MagicRender.battlespace(
					Global.getSettings().getSprite("misc", "armaa_atmo_cloud"),
					new Vector2f(MathUtils.getRandomNumberInRange(minX,maxX),maxY),
					new Vector2f(xVel,-MathUtils.getRandomNumberInRange(400,800)),
					new Vector2f(cloudSize,cloudSize),
					new Vector2f(150f,150f),		
					90f,
					0f,
					new Color(20,20,25,170),
					false,
					0f,
					0f,
					0f,
					0f,
					0f,
					0.2f,
					2f,
					0.5f,
					CombatEngineLayers.BELOW_SHIPS_LAYER
					);
				}							
			}
		}
		
		if(!engine.isPaused())
		{
			if(morgana != null && interval2.intervalElapsed())
				for(FleetMemberAPI member :engine.getFleetManager(1).getRetreatedCopy())
				{
					if(member.getHullSpec().getBaseHullId().equals("armaa_morgana"))
						Global.getSector().getMemoryWithoutUpdate().set("$armaa_morganaEscaped",true);
					
					else if(member.getHullSpec().getBaseHullId().equals("armaa_ceylon"))
						Global.getSector().getMemoryWithoutUpdate().set("$armaa_ceylonEscaped",true);						
				}
			if(interval2.intervalElapsed())
				for(CombatEntityAPI asteroid: engine.getAsteroids())
				{
					engine.removeEntity(asteroid);
				}
			
			for (ShipAPI ship : engine.getShips())
			{
					if(!ship.isAlive() || ship.isHulk() || !engine.isEntityInPlay(ship))
					{
						engine.removeEntity(ship);
					}				
				if(ceylon == null && wave2Triggered)
					if(ship.getHullSpec().getBaseHullId().contains("armaa_ceylon"))
						if(ship.getOwner() == 1)
							ceylon = ship;
				if(morgana == null && wave2Triggered)
					if(ship.getHullSpec().getBaseHullId().equals("armaa_morgana"))
						if(ship.getOwner() == 1)
							morgana = ship;						

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
