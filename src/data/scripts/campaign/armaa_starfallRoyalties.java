package data.scripts.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.campaign.econ.MonthlyReport.FDNode;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.util.Misc;

/**
 * Monthly royalties from THE KING'S OWN. Same shape as vanilla's
 * GalatianAcademyStipend: an income node on the Fleet section of the monthly
 * report, paid on the last economy tick of each month.
 *
 * The amount and the number of payments are fixed when the quest ends
 * (armaa_starfallCMD payout), so a saved game keeps the terms it was given.
 * Payments are counted rather than timed, so the run is exactly `months` long.
 */
public class armaa_starfallRoyalties implements EconomyTickListener, TooltipCreator {

    public static final String ACTIVE_KEY = "$armaa_sfo_royaltiesActive";
    private static final String NODE_ID = "armaa_sfo_royalties";

    protected int amount;
    protected int months;
    protected int paid = 0;

    public armaa_starfallRoyalties(int amount, int months) {
        this.amount = amount;
        this.months = months;
        Global.getSector().getListenerManager().addListener(this);
        Global.getSector().getMemoryWithoutUpdate().set(ACTIVE_KEY, true);
    }

    @Override
    public void reportEconomyTick(int iterIndex) {
        if (!Global.getSettings().getBoolean("enableStipend")) {
            return;
        }
        int lastIterInMonth = (int) Global.getSettings().getFloat("economyIterPerMonth") - 1;
        if (iterIndex != lastIterInMonth) {
            return;
        }
        if (paid >= months) {
            Global.getSector().getListenerManager().removeListener(this);
            Global.getSector().getMemoryWithoutUpdate().unset(ACTIVE_KEY);
            return;
        }

        MonthlyReport report = SharedData.getData().getCurrentReport();
        FDNode fleetNode = report.getNode(MonthlyReport.FLEET);
        FDNode node = report.getNode(fleetNode, NODE_ID);
        node.income = amount;
        node.name = "Royalties: The King's Own";
        node.icon = Global.getSettings().getSpriteName("income_report", "generic_income");
        node.tooltipCreator = this;
        paid++;
    }

    @Override
    public void reportEconomyMonthEnd() {
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
        tooltip.addPara("A monthly payment of %s in royalties from the broadcast of THE KING'S OWN.",
                0f, Misc.getHighlightColor(), Misc.getDGSCredits(amount));

        int remaining = months - paid;
        if (remaining > 0) {
            tooltip.addPara("You should continue receiving it for another %s months.", 10f,
                    Misc.getHighlightColor(), "" + remaining);
        } else {
            tooltip.addPara("This month's payment was the last.", 10f);
        }
    }

    @Override
    public float getTooltipWidth(Object tooltipParam) {
        return 450;
    }

    @Override
    public boolean isTooltipExpandable(Object tooltipParam) {
        return false;
    }
}
