package data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.util.Misc.Token;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import java.util.Map;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import java.util.List;
import org.magiclib.util.MagicCampaign;

public class armaa_bellatorCMD extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        String action = params.get(0).getString(memoryMap);
        MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
        if (memory == null) {
            return false; // should not be possible unless there are other big problems already
        }
        if ("spawnBellator".equals(action)) {
                final CampaignFleetAPI mrcGuardFleet = (CampaignFleetAPI) MagicCampaign.createFleetBuilder()
                        .setFleetName("Escaping Mutineers")
                        .setFleetFaction("mercenary")
                        .setFleetType("taskForce")
                        .setFlagshipName("Bellator")
                        .setFlagshipVariant("armaa_bellator_boss")
                        .setFlagshipAlwaysRecoverable(true)
                        .setQualityOverride(5f)
                        .setMinFP(150)
                        .setReinforcementFaction("armaarmatura_pirates")
                        .setSpawnLocation(Global.getSector().getPlayerFleet())
                        .setSupportAutofit(true)
                        .setIsImportant(true)
                        .create();
                mrcGuardFleet.setMoveDestinationOverride(Global.getSector().getEntityById("nekki3").getLocation().x, Global.getSector().getEntityById("nekki3").getLocation().y);
                mrcGuardFleet.setTransponderOn(false);
                mrcGuardFleet.setDiscoverable(true);
                mrcGuardFleet.setNoEngaging(10f);
                mrcGuardFleet.setLocation(mrcGuardFleet.getLocation().x+600, mrcGuardFleet.getLocation().y+200);
                mrcGuardFleet.getMemoryWithoutUpdate().set("$canOnlyBeEngagedWhenVisibleToPlayer", true);
                mrcGuardFleet.addAssignmentAtStart(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN,Global.getSector().getEconomy().getMarketsCopy().get(0).getPrimaryEntity(),999f,"Escaping",null);
                mrcGuardFleet.getAbility(Abilities.GO_DARK).activate();
                mrcGuardFleet.addAbility(Abilities.TRANSVERSE_JUMP);
                mrcGuardFleet.getAbility(Abilities.TRANSVERSE_JUMP).activate();                
                mrcGuardFleet.setId("armaa_escapingBellator");
                mrcGuardFleet.addEventListener(new FleetEventListener() {
                    @Override
                    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet,
                            FleetDespawnReason reason, Object param) {
                        if (reason == FleetDespawnReason.REACHED_DESTINATION || reason == FleetDespawnReason.PLAYER_FAR_AWAY ) {
                            Global.getSector().getMemoryWithoutUpdate()
                                  .set("$armaa_bellatorEscaped", true);
                            mrcGuardFleet.removeEventListener(this);
                        } else {
                            // Escaped or let go
                            Global.getSector().getMemoryWithoutUpdate()
                                  .set("$armaa_bellatorDefeated", true);
                            mrcGuardFleet.removeEventListener(this);
                        }
                    }
                    @Override
                    public void reportBattleOccurred(CampaignFleetAPI fleet,
                            CampaignFleetAPI primaryWinner, BattleAPI battle) 
                    {
                        if(!fleet.getFlagship().getHullId().contains("bellator"))
                        {
                            Global.getSector().getMemoryWithoutUpdate()
                                  .set("$armaa_bellatorDefeated", true); 
                            mrcGuardFleet.removeEventListener(this);                            
                        }
                    }
                });
        }
        return false;
    }
}
