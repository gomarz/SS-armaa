package data.scripts.fleets;


import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;

public class armaa_LuddObserverFleetScript implements EveryFrameScript, FleetEventListener {
    private final CampaignFleetAPI luddFleet;
    private boolean triggered = false;
    private boolean done = false;
    private static final float TRIGGER_RANGE = 2000f;

    public armaa_LuddObserverFleetScript(CampaignFleetAPI luddFleet) {
        this.luddFleet = luddFleet;
    }

    @Override
    public void advance(float amount) {
        if (done || triggered) return;
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        // Different star system, bail early
        if (player.getContainingLocation() != luddFleet.getContainingLocation()) return;
        // Don't intercept if hostile
        if (player.isHostileTo(luddFleet)) return;

        float dist = MathUtils.getDistance(luddFleet, player.getLocation());
        if (dist < TRIGGER_RANGE) {
            Misc.makeImportant(luddFleet, "let me tell you somethin");
            triggered = true;
            triggerIntercept(player);
        }
    }

    private void triggerIntercept(CampaignFleetAPI player) {
       
        luddFleet.clearAssignments();
        luddFleet.addAssignment(
            FleetAssignment.INTERCEPT,
            player,
            5f
        );
        luddFleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN,Global.getSector().getEconomy().getMarketsCopy().get(0).getPrimaryEntity(),999f,"Returning home",null);    
        done = true;
    }

    // FleetEventListener - fleet got destroyed or otherwise removed
    @Override
    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, 
        FleetDespawnReason reason, Object param) {
        done = true;
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI fleet, 
        CampaignFleetAPI primaryWinner, BattleAPI battle) {
        // Could handle "player fought the church fleet" consequences here
    }

    @Override
    public boolean isDone() { return done; }

    @Override
    public boolean runWhilePaused() { return false; }
}