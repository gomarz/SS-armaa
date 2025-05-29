package data.scripts.plugins;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.input.InputEventAPI;
import java.util.ArrayList;
import java.util.List;

//import org.lazywizard.console.commands.ToggleAI;

//this script manages things that otherwise wouldn't be possible via hullmod; primarily ensuring fake fighters being only ship left on field doesnt end combat 
//and ensuring its always selectable in UI
public class armaa_FighterHaxPlugin extends BaseEveryFrameCombatPlugin
{
    protected CombatEngineAPI engine;
	protected List<ShipAPI> cataphrachtii = new ArrayList<ShipAPI>();
	protected List<ShipAPI> toRemove = new ArrayList<ShipAPI>();
	protected CombatTaskManagerAPI ctm;
	protected CombatFleetManagerAPI cfm;
	protected CombatUIAPI ui;
	private String origPersonality = "steady";
	boolean canEnd = false;
	
    @Override
    public void advance(float amount, List<InputEventAPI> events)
    {
		if(engine == null)
			return;
			
		cfm = engine.getFleetManager(FleetSide.PLAYER);
		ctm = cfm.getTaskManager(false);
		ui = engine.getCombatUI();
		
        for (ShipAPI ship : engine.getShips())
        {
			if(!ship.isFighter())
				continue;
			if(ship.getWing() == null)
				continue;
			
			if(ship.getHullLevel() <= .30f || ship.getWing().getSourceShip().isPullBackFighters() && ship.getHullLevel() < .7f)
				ship.getWing().orderReturn(ship);
		}	
	}

    @Override
    public void init(CombatEngineAPI engine)
    {
        this.engine = engine;
    }
}
