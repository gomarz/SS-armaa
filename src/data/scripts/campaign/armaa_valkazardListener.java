package data.scripts.campaign;

import com.fs.starfarer.api.campaign.BaseCampaignEventListenerAndScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.EngagementResultForFleetAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import java.util.ArrayList;
import java.util.List;

public class armaa_valkazardListener extends BaseCampaignEventListenerAndScript {

    private long days;

    public armaa_valkazardListener() {

    }

    // we just want to see if the player utterly lost against this fleet specifically
    // they can lose valk at some point between here and goint to Sera, but
    // I think making it out of the fight should be enough
    @Override
    public void reportPlayerEngagement(EngagementResultAPI result) {
        EngagementResultForFleetAPI loser = result.getLoserResult();
        EngagementResultForFleetAPI winner = result.getWinnerResult();

            if (!result.didPlayerWin()) 
            {
                List<FleetMemberAPI> deadships = new ArrayList();
                deadships.addAll(loser.getDestroyed());
                deadships.addAll(loser.getDisabled());
                int count = deadships.size();
                //Global.getLogger(this.getClass()).info(deadships);
                  //Global.getLogger(this.getClass()).info(deadships.size());
                    //Global.getLogger(this.getClass()).info(Global.getSector().getPlayerFleet().getNumShips());
                if(deadships.size() >= Global.getSector().getPlayerFleet().getNumShips())
                {
                    for( FleetMemberAPI member : Global.getSector().getPlayerFleet().getMembersWithFightersCopy())
                    {
                        //Global.getLogger(this.getClass()).info(member.getShipName());

                        if(deadships.contains(member))
                            count--;                           
                    }
                }
                
                    boolean totaloss = (count == 0);
                    if (totaloss) {
                        //Global.getLogger(this.getClass()).info(loser.getFleet().getNumShips());
                        //Global.getLogger(this.getClass()).info(loser.getDestroyed().size());
                        //Global.getLogger(this.getClass()).info(loser.getReserves().size());                        
                        Global.getSector().getMemoryWithoutUpdate().set("$armaa_badEndTrigger", true);
                    }
            }
        Global.getSector().removeListener(this);
    }
}
