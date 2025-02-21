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

public class armaa_damperAI implements ShipSystemAIScript
{
    private ShipAPI ship;
    private CombatEngineAPI engine;
    private ShipwideAIFlags flags;
    private ShipSystemAPI system;
    private IntervalUtil tracker;
    
    public armaa_damperAI() {
        this.tracker = new IntervalUtil(0.5f, 0.5f);
    }
    
    public void init(final ShipAPI ship, final ShipSystemAPI system, final ShipwideAIFlags flags, final CombatEngineAPI engine) {
        this.ship = ship;
        this.flags = flags;
        this.engine = engine;
        this.system = ship.getPhaseCloak();
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
            if (flags.hasFlag(ShipwideAIFlags.AIFlags.BACKING_OFF)) {
                dicision += 0.1f;
            }
            if (flags.hasFlag(ShipwideAIFlags.AIFlags.RUN_QUICKLY)) {
                dicision += 0.1f;
            }				
            if (flags.hasFlag(ShipwideAIFlags.AIFlags.HAS_INCOMING_DAMAGE)) {
                dicision+= 1.9f-(1f*ship.getHullLevel());
            }
            if (flags.hasFlag(ShipwideAIFlags.AIFlags.IN_CRITICAL_DPS_DANGER)) {
                dicision+=1;
            }
            final FluxTrackerAPI fluxTracker = ship.getFluxTracker();
            if (dicision >= 1f) 
			{
				ship.giveCommand(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK, new Vector2f(),0); 
            }
            else if (system.isActive()) {
                //ship.useSystem();
            }
        }
    }
}