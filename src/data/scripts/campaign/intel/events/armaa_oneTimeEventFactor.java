/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package data.scripts.campaign.intel.events;

import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseOneTimeFactor;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

public class armaa_oneTimeEventFactor extends BaseOneTimeFactor {

    private String cause;

    public armaa_oneTimeEventFactor(int points, String cause) {
        super(points);
        this.cause = cause;
    }

    @Override
    public String getDesc(BaseEventIntel intel) {
        return "Data gleaned from" + " " + cause;

    }

    @Override
    public TooltipMakerAPI.TooltipCreator getMainRowTooltip(BaseEventIntel intel) {
        return new BaseFactorTooltip() {
            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                tooltip.addPara("Uploaded system data from this unit",
                        0f);
            }
        };
    }
}
