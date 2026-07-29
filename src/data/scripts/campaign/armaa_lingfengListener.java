package data.scripts.campaign;

import com.fs.starfarer.api.campaign.BaseCampaignEventListenerAndScript;
import com.fs.starfarer.api.Global;

public class armaa_lingfengListener extends BaseCampaignEventListenerAndScript {
    
    private long days;
    
    public armaa_lingfengListener() {
        this.days = Global.getSector().getClock().getTimestamp();
        
    }
    
    @Override
    public void reportEconomyTick(int iterIndex)
    {
        if (Global.getSector().getClock().getElapsedDaysSince(days) > 7) {
            Global.getSector().getMemoryWithoutUpdate().set("$armaa_lingfengReadyToChat", true);
            Global.getSector().removeListener(this);
        }        
    }
    
    @Override
    public void reportEconomyMonthEnd() 
    {
        
    }
}
