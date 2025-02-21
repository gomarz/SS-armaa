package data.scripts.campaign.econ;

import com.fs.starfarer.api.impl.campaign.econ.*;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

public class armaa_ViceDemand extends BaseMarketConditionPlugin 
{

	public void apply(String id) 
	{
		market.getDemand(Commodities.DRUGS).getDemand().modifyFlat(id, Math.max(10f, market.getSize() * 5f));
	}

	public void unapply(String id) 
	{
		market.getDemand(Commodities.DRUGS).getDemand().unmodify(id);
	}
	

	protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
		super.createTooltipAfterDescription(tooltip, expanded);
		
		tooltip.addPara("%s demand", 
				10f, Misc.getHighlightColor(),
				"-" + (int)market.getDemand(Commodities.DRUGS).getDemand().getBaseValue());
	}

}