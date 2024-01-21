// 
// Decompiled by Procyon v0.5.36
// 

package data.scripts.shipsystems.ai;

import com.fs.starfarer.api.combat.FluxTrackerAPI;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipCommand;

import java.util.ArrayList;
import java.util.Random;

public class armaa_evasionAI implements ShipSystemAIScript
{
    private ShipAPI ship;
    private CombatEngineAPI engine;
    private ShipwideAIFlags flags;
    private ShipSystemAPI system;
    private IntervalUtil tracker;
    boolean phase = false;
	private ArrayList<ShipCommand> command = new ArrayList<ShipCommand>();
    public armaa_evasionAI() {
       this.tracker = new IntervalUtil(0.5f, 1.0f);
    }
	
{
    command.add(ShipCommand.STRAFE_LEFT);
    command.add(ShipCommand.STRAFE_RIGHT);
    command.add(ShipCommand.ACCELERATE_BACKWARDS);
    //...
}

private ShipCommand randomCommand() {
    int pick = new Random().nextInt(command.size());
    return command.get(pick);
}	
    
    public void init(final ShipAPI ship, final ShipSystemAPI system, final ShipwideAIFlags flags, final CombatEngineAPI engine) {
        this.ship = ship;
        this.flags = flags;
        this.engine = engine;
        this.system = ship.getSystem();

		//at this point we know the ship has  a system
		if(system != null)
		{
			//check if it has phase
			if(ship.getPhaseCloak() != null)
			{
				//We know it has a phase system, check if it is our system
				if(ship.getPhaseCloak().getId().equals("armaa_havocDrive") || ship.getPhaseCloak().getId().equals("armaa_plasmaJets"))
				{
					this.system = ship.getPhaseCloak();
					phase = true;
				}
			}
		}
		
		//if there's no system
		else
		{
			phase = true;
			this.system = ship.getPhaseCloak();
			
		}
    }
    
    public void advance(final float amount, final Vector2f missileDangerDir, final Vector2f collisionDangerDir, final ShipAPI target) {
		this.tracker.advance(amount);
        if (engine == null) {
            return;
        }
        if (engine.isPaused()) {
            return;
        }
       if (tracker.intervalElapsed()) {
            float dicision = 0.0f;
            if (flags.hasFlag(ShipwideAIFlags.AIFlags.PURSUING)) {
                dicision += 0.5f;
            }
            if (flags.hasFlag(ShipwideAIFlags.AIFlags.BACKING_OFF)) {
                dicision += 0.25f;
            }
            if (flags.hasFlag(ShipwideAIFlags.AIFlags.RUN_QUICKLY)) {
                dicision += 1f;
            }
            if (flags.hasFlag(ShipwideAIFlags.AIFlags.MANEUVER_TARGET)) {
                dicision += 0.5f;
            }		
            if (flags.hasFlag(ShipwideAIFlags.AIFlags.TURN_QUICKLY)) {
                dicision += 0.75f;
            }
            if (flags.hasFlag(ShipwideAIFlags.AIFlags.HARASS_MOVE_IN)) {
                dicision += 0.5f;
            }				
            if (flags.hasFlag(ShipwideAIFlags.AIFlags.NEEDS_HELP)) {
                dicision += 1f;
            }
            if (flags.hasFlag(ShipwideAIFlags.AIFlags.HAS_INCOMING_DAMAGE)) {
                dicision+= 1f;
				//ship.giveCommand(ShipCommand.STRAFE_LEFT,null,0);
            }
            if (flags.hasFlag(ShipwideAIFlags.AIFlags.IN_CRITICAL_DPS_DANGER)) {
                dicision+=1f;
            }
            if (flags.hasFlag(ShipwideAIFlags.AIFlags.SAFE_FROM_DANGER_TIME)) {
                dicision -= 0.5f;
            }
            final FluxTrackerAPI fluxTracker = ship.getFluxTracker();
            if (dicision >= 1f) 
			{
				if(ship.isFighter())
					ship.giveCommand(randomCommand(),ship.getMouseTarget(),0);
				if(phase)
					ship.giveCommand(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK, null,0); 
				else
					ship.useSystem();
            }
            else if (system.isActive()) {
                //ship.useSystem();
            }
        }
    }
}