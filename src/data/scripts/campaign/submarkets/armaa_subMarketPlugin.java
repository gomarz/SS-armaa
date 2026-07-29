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
import com.fs.starfarer.api.util.Misc;

public class armaa_subMarketPlugin extends BaseSubmarketPlugin {

    private final RepLevel MIN_STANDING = RepLevel.FAVORABLE;

    @Override
    public void init(SubmarketAPI submarket) {
        super.init(submarket);
        getCargo().addHullmods("armaa_wingCommander", 1);
    }

    @Override
    public float getTariff() {
        if (Misc.getCommissionFactionId() == "tritachyon") {
            return 0.35f;
        } else {
            return 0.3f;
        }
    }

    @Override
    public String getTooltipAppendix(CoreUIAPI ui) {
        RepLevel level = market.getFaction().getRelationshipLevel(Global.getSector().getFaction(Factions.PLAYER));

        return super.getTooltipAppendix(ui);
    }

    @Override
    public boolean isEnabled(CoreUIAPI ui) {

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
                    (float)(Math.random()*360f), // combat
                    35f, // freighter
                    35f, // tanker
                    35f, // transport
                    35f, // liner
                    35f, // utilityPts
                    null, // qualityOverride
                    0f, // qualityMod
                    ShipPickMode.PRIORITY_ONLY,
                    doctrineOverride);

            pruneWeapons(0f);

            addWeapons(1, 10, 5, submarket.getFaction().getId());
            addWeapons(1, 10, 5, "independent");
            addWeapons(1, 10, 5, "mercenary");
            addFighters(1, 3, 6, submarket.getFaction().getId());
            addFighters(1, 3, 5, "mercenary");
            //pruneShips((float)Math.random());
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
