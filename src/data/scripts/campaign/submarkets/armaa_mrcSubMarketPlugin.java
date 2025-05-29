package data.scripts.campaign.submarkets;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CoreUIAPI;
import com.fs.starfarer.api.campaign.FactionAPI.ShipPickMode;
import com.fs.starfarer.api.campaign.FactionDoctrineAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;

public class armaa_mrcSubMarketPlugin extends BaseSubmarketPlugin {

    private final RepLevel MIN_STANDING = RepLevel.FAVORABLE;

    @Override
    public void init(SubmarketAPI submarket) {
        super.init(submarket);
    }


    @Override
    public float getTariff() {
            return 0.1f;
    }

    @Override
    public String getTooltipAppendix(CoreUIAPI ui) {
        RepLevel level = market.getFaction().getRelationshipLevel(Global.getSector().getFaction(Factions.PLAYER));

        return super.getTooltipAppendix(ui);
    }

    @Override
    public boolean isEnabled(CoreUIAPI ui) 
	{

        return true;
    }

    @Override
    public void updateCargoPrePlayerInteraction() {
        sinceLastCargoUpdate = 0f;

        if (okToUpdateShipsAndWeapons()) {
            sinceSWUpdate = 0f;

            getCargo().getMothballedShips().clear();

            float quality = 0.1f;

            FactionDoctrineAPI doctrineOverride = submarket.getFaction().getDoctrine().clone();
            addShips(submarket.getFaction().getId(),
                    225f, // combat
                    0f, // freighter
                    0f, // tanker
                    0f, // transport
                    0f, // liner
                    0f, // utilityPts
                    null, // qualityOverride
                    0f, // qualityMod
                    ShipPickMode.PRIORITY_THEN_ALL,
                    doctrineOverride);

            pruneWeapons(0.1f);

            addWeapons(3, 10, 5, submarket.getFaction().getId());

            addFighters(3, 5, 5, submarket.getFaction().getId());
			
            //pruneShips(0.2f);
        }

        getCargo().sort();
    }

    @Override
    public boolean isIllegalOnSubmarket(CargoStackAPI stack, TransferAction action) {
        return action == TransferAction.PLAYER_SELL;
    }

    @Override
    public boolean isIllegalOnSubmarket(String commodityId, TransferAction action) {
        return action == TransferAction.PLAYER_SELL;
    }

    @Override
    public boolean isIllegalOnSubmarket(FleetMemberAPI member, TransferAction action) {
        return action == TransferAction.PLAYER_SELL;
    }

    @Override
    public String getIllegalTransferText(FleetMemberAPI member, TransferAction action) {
        return "Sales only!";
    }

    @Override
    public String getIllegalTransferText(CargoStackAPI stack, TransferAction action) {
        return "Sales only!";
    }

    @Override
    public boolean isParticipatesInEconomy() {
        return false;
    }


}