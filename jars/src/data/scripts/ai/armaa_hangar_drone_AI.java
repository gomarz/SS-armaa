package data.scripts.ai;

import data.scripts.util.armaa_utils;

import java.awt.Color;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;

import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.Point;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SoundAPI;
import com.fs.starfarer.api.combat.*;

import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.util.IntervalUtil;
//import com.fs.starfarer.combat.CombatEngine;
import com.fs.starfarer.combat.entities.Ship;
import com.fs.starfarer.api.combat.CombatTaskManagerAPI;

//based on code from Sundog's ICE repair drone and Dark.Revenant's Imperium Titan. Copied Maltese AI

public class armaa_hangar_drone_AI extends BaseShipAI {
	private final ShipwideAIFlags flags = new ShipwideAIFlags();
    private final ShipAIConfig config = new ShipAIConfig();
    
    public ShipAPI carrier;
	private float targetFacingOffset = Float.MIN_VALUE;
    private ShipAPI target;
    private Vector2f targetOffset;
	private Vector2f landingLoc;
	private IntervalUtil rotateTimer = new IntervalUtil(1f,1f);
    private boolean returning = false;
	private boolean rearm = false;
	private boolean hasbombingtarget = false;
	private ShipAPI parent;
	private WeaponAPI weapon;
	private float angle;


    
    Vector2f getDestination() {
        return new Vector2f();
    }

    public armaa_hangar_drone_AI(ShipAPI ship, ShipAPI parent, WeaponAPI weapon, float angle) {
		super(ship);
		this.parent = parent;
		this.weapon = weapon;
		this.angle = angle;

	}

    @Override
    public void advance(float amount) {
		
		if(ship.getShield() != null)
			if(ship.getShield().isOff())
			ship.getShield().toggleOn();
        goToDestination(amount);
		 if(ship.isFinishedLanding())
			 Global.getCombatEngine().removeEntity(ship);

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
    
    
    void goToDestination(float amount) 
	{
		/*
		double x1 = ship.getLocation().getX() - parent.getLocation().getX(); 
		double y1 = ship.getLocation().getY() - referencePoint.getY();
		// apply the rotation matrix (i think, i don't really understand this part)
		double x2 = x1 * Math.cos(rotationAngle) - y1 * Math.sin(rotationAngle);
		double y2 = x1 * Math.sin(rotationAngle) + y1 * Math.cos(rotationAngle);

		// move everything back into position
		this.x = x2 + referencePoint.getX();
		this.y = y2 + referencePoint.getY();
		*/
		rotateTimer.advance(amount);
		//if(rotateTimer.intervalElpased())
		landingLoc = parent.getLocation();
          Vector2f to = landingLoc;
          float distance = MathUtils.getDistance(ship, to);
    	  float distToCarrier = (float)(MathUtils.getDistanceSquared(parent.getLocation(),ship.getLocation()) / Math.pow(ship.getCollisionRadius(),2));
		 float f= 1.0f-Math.min(1,distToCarrier);
    	  if(distance <= ship.getCollisionRadius())			  
		  {
			  
		  }
		else 
		{
			VectorUtils.rotateAroundPivot(ship.getLocation(),parent.getLocation(),angle+4f);
			ship.getVelocity().set(parent.getVelocity());
        	targetFacingOffset = Float.MIN_VALUE;
            float angleDif = MathUtils.getShortestRotation(ship.getFacing(), VectorUtils.getAngle(ship.getLocation(), to));

            if(Math.abs(angleDif) < 30 )
			{
                accelerate();
            } 
			else
			{
				
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
