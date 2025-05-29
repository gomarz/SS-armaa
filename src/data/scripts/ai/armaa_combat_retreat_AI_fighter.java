package data.scripts.ai;

import java.util.List;
import java.util.Random;

import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.Point;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SoundAPI;
import com.fs.starfarer.api.combat.ArmorGridAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.FighterLaunchBayAPI;
import com.fs.starfarer.api.combat.ShipAIConfig;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.util.IntervalUtil;
//import com.fs.starfarer.combat.CombatEngine;

//based on code from Sundog's ICE repair drone and Dark.Revenant's Imperium Titan. Copied Maltese AI

public class armaa_combat_retreat_AI_fighter extends BaseShipAI {
	private final ShipwideAIFlags flags = new ShipwideAIFlags();
    private final ShipAIConfig config = new ShipAIConfig();
    
    public ShipAPI carrier;
    private ShipAPI fighterbay;
    private CombatFleetManagerAPI combatfleet;
    private CombatFleetManagerAPI fleetManager;
    private ShipAPI fightercopy;
    private List<FighterLaunchBayAPI> carrierdata;
    private boolean carrierworking;
    private ShipAPI target;
    private Vector2f targetOffset;
    private Point cellToFix = new Point();
    private Random rng = new Random();
    private ArmorGridAPI armorGrid;
    private SoundAPI repairSound;
    private float max, cellSize;
    private int gridWidth, gridHeight, cellCount;
    private boolean returning = false;
    private boolean spark = false;
    private float targetFacingOffset = Float.MIN_VALUE;
    private float range = 4000f;
    private boolean needrepair = false;
    private float HPPercent = 0.50f;
    private float CRmarker = 0.4f;
    private float CRrestored = 0.20f;
    private float CRmax = 1f;
   // protected float refit_timer = 0f;
	private boolean hasLanded = false;
	private boolean intervalCheck = false;
	private float pptMax = 180f;
    private String timer = "0";
	private float nearest = 10000f;

    private final IntervalUtil interval = new IntervalUtil(0.25f, 0.33f);
    private final IntervalUtil countdown = new IntervalUtil(4f, 4f);
	private final IntervalUtil landingTimer = new IntervalUtil(20f, 20f);
    public final IntervalUtil BASE_REFIT = new IntervalUtil(20f, 20f);
	private final IntervalUtil BASE_REFIT_CR = new IntervalUtil(20f, 20f);

    
    Vector2f getDestination() {
        return new Vector2f();
    }

    public armaa_combat_retreat_AI_fighter(ShipAPI ship) {
		super(ship);		
	}

    @Override
    public void advance(float amount) {
		
        goToDestination();

    }

    @Override
    public boolean needsRefit() 
	{						
        return true;
    }

    @Override
    public void cancelCurrentManeuver() {
    }

    @Override
    public void evaluateCircumstances() {

    }
    
    
    void goToDestination() 
	{
    	
		float mapWidth = Global.getCombatEngine().getMapWidth() / 2f, mapHeight = Global.getCombatEngine().getMapHeight() / 2f;
		Vector2f rawLL = new Vector2f(-mapWidth, -mapHeight),rawUR = new Vector2f(mapWidth, mapHeight);
		Vector2f landingLoc = null;
		if(ship.getOwner() == 0)
		landingLoc = new Vector2f((rawLL.x+rawUR.x/2), rawLL.y);
	
		else landingLoc = new Vector2f((rawLL.x+rawUR.x/2),rawUR.y);
    	
        Vector2f to = landingLoc;
        float distance = MathUtils.getDistance(ship, to);
        
    	float distToCarrier = (float)(MathUtils.getDistanceSquared(landingLoc,ship.getLocation()) / Math.pow(ship.getCollisionRadius(),2));
    	if(target == carrier && distToCarrier < 1.0f || ship.isLanding() == true) {
            float f= 1.0f-Math.min(1,distToCarrier);
            if(returning == false) f = f*0.1f;
          //  turnToward(target.getFacing());
            ship.getLocation().x = (to.x * (f*0.1f) + ship.getLocation().x * (2 - f*0.1f)) / 2;
            ship.getLocation().y = (to.y * (f*0.1f) + ship.getLocation().y * (2 - f*0.1f)) / 2;
         //   ship.getVelocity().x = (target.getVelocity().x * f + ship.getVelocity().x * (2 - f)) / 2;
          //  ship.getVelocity().y = (target.getVelocity().y * f + ship.getVelocity().y * (2 - f)) / 2;
			if(!ship.isLanding() && !ship.isFinishedLanding())
				ship.beginLandingAnimation(ship);
        }else {
        	targetFacingOffset = Float.MIN_VALUE;
            float angleDif = MathUtils.getShortestRotation(ship.getFacing(), VectorUtils.getAngle(ship.getLocation(), to));

            if(Math.abs(angleDif) < 30){
                accelerate();
            } else {
                turnToward(to);
                //decelerate();
            }        
            strafeToward(to);
        }   
    }

    @Override
    public ShipwideAIFlags getAIFlags() {
        return flags;
    }

    @Override
    public void setDoNotFireDelay(float amount) {
    }

    @Override
    public ShipAIConfig getConfig() {
        return config;
    }
    
    public void init() {
    	
			
    }
    
}
