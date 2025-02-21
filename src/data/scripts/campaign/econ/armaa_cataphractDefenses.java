package data.scripts.campaign.econ;

import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class armaa_cataphractDefenses extends BaseMarketConditionPlugin {

    public static final float DEFENSE_BONUS = 20f;

    @Override
    public void apply(String id) {
        super.apply(id);
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).modifyMult(id,1f+DEFENSE_BONUS/100, "Cataphract Defense Force");
    }

    @Override
    public void unapply(String id) {
        super.unapply(id);
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodify(id);
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);

        if (market == null) {
            return;
        }
        tooltip.addPara("1.%sx  ground defenses.", 10f, Misc.getHighlightColor(),
                "+" + (int)DEFENSE_BONUS);
    }
}
