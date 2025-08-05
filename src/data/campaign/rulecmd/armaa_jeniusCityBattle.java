package data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin.DataForEncounterSide;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin.FleetMemberData;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

import com.fs.starfarer.api.impl.campaign.procgen.DefenderDataOverride;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.*;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageGenFromSeed.SDMParams;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageGenFromSeed.SalvageDefenderModificationPlugin;

import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Drops;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec.DropData;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl.BaseFIDDelegate;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl.FIDConfig;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Random;

public class armaa_jeniusCityBattle extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, final Map<String, MemoryAPI> memoryMap) {
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        ArrayList<FleetMemberAPI> removedShips = new ArrayList();
        for (FleetMemberAPI member : Global.getSector().getPlayerFleet().getMembersWithFightersCopy()) {
            float crew = 0f;
            if (member.isCapital()) {
                member.getRepairTracker().setMothballed(true);
                removedShips.add(member);
            } else if ((member.isCruiser()) && member.getStats().getDynamic().getMod(Stats.FLEET_GROUND_SUPPORT).isUnmodified()) {
                member.getRepairTracker().setMothballed(true);
                removedShips.add(member);
            } else {
                continue;
            }
            crew += member.getCrewComposition().getCrewInt();
            Global.getSector().getPlayerFleet().getMemoryWithoutUpdate().set("$nonAtmoShipsCrew_" + member.getId(), crew);
        }
        Global.getSector().getPlayerFleet().getMemoryWithoutUpdate().set("$nonAtmoShips", removedShips);
        FleetParamsV3 fparams = new FleetParamsV3(
                Global.getSector().getEntityById("nekki1").getLocationInHyperspace(),
                "armaarmatura_arusthai",
                null,
                FleetTypes.PATROL_SMALL,
                Math.max(400f, Global.getSector().getPlayerFleet().getFleetPoints() * 1.15f), // combatPts
                0f, // freighterPts
                0f, // tankerPts
                0f, // transportPts
                0f, // linerPts
                0f, // utilityPts
                2f // qualityMod
        );
        final CampaignFleetAPI enemyFleet = FleetFactoryV3.createFleet(fparams);
        for(int i = 0; i < 7; i++)
        {
            enemyFleet.getFleetData().addFleetMember("armaa_musha_frig_sniper_standard");
            enemyFleet.getFleetData().addFleetMember("armaa_musha_frig_standard");
        }
            enemyFleet.getFleetData().addFleetMember("armaa_altagrave_standard");        
        //FleetFactoryV3.pruneFleet(999,0,enemyFleet,Global.getSector().getPlayerFleet().getFleetData().getEffectiveStrength(),new Random());
        //FleetFactoryV3.applyDamageToFleet(enemyFleet,0.40f,true,new Random());
        //Global.getSector().getPlayerFleet().getMemoryWithoutUpdate().set("$inAtmoBattle", true);
        FleetFactoryV3.addCommanderAndOfficersV2(enemyFleet, fparams, new Random());
        final SectorEntityToken entity = dialog.getInteractionTarget();
        Misc.setDefenderOverride(entity, new DefenderDataOverride(Factions.PIRATES, 1f, 100, 200));
        final MemoryAPI memory = getEntityMemory(memoryMap);
        dialog.setInteractionTarget(enemyFleet);
        enemyFleet.getMemoryWithoutUpdate().set("$inCityBattle", "-");
        final FIDConfig config = new FIDConfig();
        config.leaveAlwaysAvailable = true;
        config.showCommLinkOption = false;
        config.showEngageText = false;
        config.showFleetAttitude = false;
        config.showTransponderStatus = false;
        config.showWarningDialogWhenNotHostile = false;
        config.alwaysAttackVsAttack = true;
        config.impactsAllyReputation = true;
        config.impactsEnemyReputation = false;
        config.pullInAllies = true;
        config.pullInEnemies = true;
        config.pullInStations = false;
        config.lootCredits = true;
        config.firstTimeEngageOptionText = "Engage the Arusthai navy";
        config.afterFirstTimeEngageOptionText = "Re-engage the Arusthai navy";
        config.noSalvageLeaveOptionText = "Continue";
        config.dismissOnLeave = false;
        config.printXPToDialog = true;
        long seed = memory.getLong(MemFlags.SALVAGE_SEED);
        config.salvageRandom = Misc.getRandom(seed, 75);
        final FleetInteractionDialogPluginImpl plugin = new FleetInteractionDialogPluginImpl(config);

        final InteractionDialogPlugin originalPlugin = dialog.getPlugin();
        config.delegate = new BaseFIDDelegate() {
            @Override
            public void notifyLeave(InteractionDialogAPI dialog) {
                // nothing in there we care about keeping; clearing to reduce savefile size
                enemyFleet.getMemoryWithoutUpdate().clear();
                // there's a "standing down" assignment given after a battle is finished that we don't care about
                enemyFleet.clearAssignments();
                enemyFleet.deflate();

                dialog.setPlugin(originalPlugin);
                dialog.setInteractionTarget(entity);

                //Global.getSector().getCampaignUI().clearMessages();
                if (plugin.getContext() instanceof FleetEncounterContext) {
                    FleetEncounterContext context = (FleetEncounterContext) plugin.getContext();
                    if (context.didPlayerWinEncounterOutright()) {

                        SDMParams p = new SDMParams();
                        p.entity = entity;
                        p.factionId = enemyFleet.getFaction().getId();

                        SalvageDefenderModificationPlugin plugin = Global.getSector().getGenericPlugins().pickPlugin(
                                SalvageDefenderModificationPlugin.class, p);
                        if (plugin != null) {
                            plugin.reportDefeated(p, entity, enemyFleet);
                        }

                        memory.unset("$hasDefenders");
                        memory.unset("$defenderFleet");
                        memory.set("$defenderFleetDefeated", true);
                        entity.removeScriptsOfClass(FleetAdvanceScript.class);
                        FireBest.fire(null, dialog, memoryMap, "BeatDefendersContinue");
                    } else {
                        boolean persistDefenders = false;
                        if (context.isEngagedInHostilities()) {
                            persistDefenders |= !Misc.getSnapshotMembersLost(enemyFleet).isEmpty();
                            for (FleetMemberAPI member : enemyFleet.getFleetData().getMembersListCopy()) {
                                if (member.getStatus().needsRepairs()) {
                                    persistDefenders = true;
                                    break;
                                }
                            }
                        }

                        if (persistDefenders) {
                            if (!entity.hasScriptOfClass(FleetAdvanceScript.class)) {
                                enemyFleet.setDoNotAdvanceAI(true);
                                enemyFleet.setContainingLocation(entity.getContainingLocation());
                                // somewhere far off where it's not going to be in terrain or whatever
                                enemyFleet.setLocation(1000000, 1000000);
                                entity.addScript(new FleetAdvanceScript(enemyFleet));
                            }
                            memory.expire("$defenderFleet", 10); // defenders may have gotten damaged; persist them for a bit
                        }
                        dialog.dismiss();
                    }
                } else {
                    dialog.dismiss();
                }
                CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
                ArrayList<FleetMemberAPI> removedShips = new ArrayList();

                if (Global.getSector().getPlayerFleet().getMemoryWithoutUpdate().contains("$nonAtmoShips")) {
                    removedShips = (ArrayList<FleetMemberAPI>) Global.getSector().getPlayerFleet().getMemoryWithoutUpdate().get("$nonAtmoShips");
                }

                for (FleetMemberAPI member : removedShips) {
                    float crew = (float) Global.getSector().getPlayerFleet().getMemoryWithoutUpdate().get("$nonAtmoShipsCrew_" + member.getId());
                    member.getRepairTracker().setMothballed(false);
                    if (member.getCrewComposition().getCrew() != crew) {
                        Global.getSector().getPlayerFleet().getCargo().addCrew((int) Math.abs(member.getCrewComposition().getCrew() - crew));
                        member.getCrewComposition().setCrew(crew);
                    }
                }
                Global.getSector().getPlayerFleet().getMemoryWithoutUpdate().unset("$nonAtmoShips");
            }

            @Override
            public void battleContextCreated(InteractionDialogAPI dialog, BattleCreationContext bcc) {
                bcc.aiRetreatAllowed = false;
                bcc.objectivesAllowed = false;
                bcc.enemyDeployAll = false;
            }

            @Override
            public void postPlayerSalvageGeneration(InteractionDialogAPI dialog, FleetEncounterContext context, CargoAPI salvage) {
                DataForEncounterSide winner = context.getWinnerData();
                DataForEncounterSide loser = context.getLoserData();

                if (winner == null || loser == null) {
                    return;
                }

                float playerContribMult = context.computePlayerContribFraction();

                List<DropData> dropRandom = new ArrayList<DropData>();
                List<DropData> dropValue = new ArrayList<DropData>();

                float valueMultFleet = Global.getSector().getPlayerFleet().getStats().getDynamic().getValue(Stats.BATTLE_SALVAGE_MULT_FLEET);
                float valueModShips = context.getSalvageValueModPlayerShips();

                for (FleetMemberData data : winner.getEnemyCasualties()) {
                    // add at least one of each weapon that was present on the OMEGA ships, since these
                    // are hard to get; don't want them to be too RNG
                    if (data.getMember() != null && context.getBattle() != null) {
                        CampaignFleetAPI fleet = context.getBattle().getSourceFleet(data.getMember());

                        if (fleet != null && fleet.getFaction().getId().equals(Factions.OMEGA)) {
                            for (String slotId : data.getMember().getVariant().getNonBuiltInWeaponSlots()) {
                                String weaponId = data.getMember().getVariant().getWeaponId(slotId);
                                if (weaponId == null) {
                                    continue;
                                }
                                if (salvage.getNumWeapons(weaponId) <= 0) {
                                    WeaponSpecAPI spec = Global.getSettings().getWeaponSpec(weaponId);
                                    if (spec.hasTag(Tags.NO_DROP)) {
                                        continue;
                                    }

                                    salvage.addWeapons(weaponId, 1);
                                }
                            }
                        }

                        if (fleet != null
                                && fleet.getFaction().getCustomBoolean(Factions.CUSTOM_NO_AI_CORES_FROM_AUTOMATED_DEFENSES)) {
                            continue;
                        }
                    }
                    if (config.salvageRandom.nextFloat() < playerContribMult) {
                        DropData drop = new DropData();
                        drop.chances = 1;
                        drop.value = -1;
                        switch (data.getMember().getHullSpec().getHullSize()) {
                            case CAPITAL_SHIP:
                                drop.group = Drops.AI_CORES3;
                                drop.chances = 2;
                                break;
                            case CRUISER:
                                drop.group = Drops.AI_CORES3;
                                break;
                            case DESTROYER:
                                drop.group = Drops.AI_CORES2;
                                break;
                            case FIGHTER:
                            case FRIGATE:
                                drop.group = Drops.AI_CORES1;
                                break;
                        }
                        if (drop.group != null) {
                            dropRandom.add(drop);
                        }
                    }
                }
                float fuelMult = Global.getSector().getPlayerFleet().getStats().getDynamic().getValue(Stats.FUEL_SALVAGE_VALUE_MULT_FLEET);
                //float fuel = salvage.getFuel();
                //salvage.addFuel((int) Math.round(fuel * fuelMult));

                CargoAPI extra = SalvageEntity.generateSalvage(config.salvageRandom, valueMultFleet + valueModShips, 1f, 1f, fuelMult, dropValue, dropRandom);
                for (CargoStackAPI stack : extra.getStacksCopy()) {
                    if (stack.isFuelStack()) {
                        stack.setSize((int) (stack.getSize() * fuelMult));
                    }
                    salvage.addFromStack(stack);
                }
            }
        };
        dialog.setPlugin(plugin);
        plugin.init(dialog);
        return true;
    }
}
