package data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.util.Misc.Token;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import java.util.Map;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddShip;
import data.scripts.campaign.armaa_valkazardListener;
import java.util.ArrayList;
import java.util.List;

public class armaa_valkazardCMD extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        String action = params.get(0).getString(memoryMap);
        CampaignFleetAPI pf = Global.getSector().getPlayerFleet();
        MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
        if (memory == null) {
            return false; // should not be possible unless there are other big problems already
        }
        if ("addTracker".equals(action)) {
            Global.getSector().addListener(new armaa_valkazardListener());
            return true;
        }

        if ("upgradeValk".equals(action)) {
            for (FleetMemberAPI member : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
                if (member.getHullId().contains("armaa_valkazard")) {
                    ShipVariantAPI variant = member.getVariant().clone();
                    FleetMemberAPI valk = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "armaa_valkazard_standard_kai");

                    ShipVariantAPI kaiVaraint = valk.getVariant();
                    ArrayList<String> hullmodsToRemove = new ArrayList();
                    for (String hullmod : variant.getHullMods()) {
                        if (hullmod.contains("selector")) {
                            hullmodsToRemove.add(hullmod);
                        }
                    }
                    for (String hullmod : hullmodsToRemove) {
                        variant.getHullMods().remove(hullmod);
                    }
                    for (String hullmod : variant.getPermaMods()) {
                        boolean sMod = variant.getSModdedBuiltIns().contains(hullmod);
                        if (sMod) {
                            Global.getLogger(this.getClass()).info("Adding perma mod " + hullmod);
                            kaiVaraint.removePermaMod(hullmod);
                            kaiVaraint.addPermaMod(hullmod, sMod);
                        }
                    }
                    for (String hullmod : variant.getSMods()) {
                        Global.getLogger(this.getClass()).info("Adding S mod " + hullmod);
                        kaiVaraint.removePermaMod(hullmod);
                        kaiVaraint.addPermaMod(hullmod, true);
                    }
                    for (String hullmod : variant.getSModdedBuiltIns()) {
                        Global.getLogger(this.getClass()).info("Adding BUILT IN S mod " + hullmod);
                        kaiVaraint.getHullMods().remove(hullmod);
                        Global.getLogger(this.getClass()).info(kaiVaraint.getHullMods() + "");
                        kaiVaraint.removePermaMod(hullmod);
                        kaiVaraint.addPermaMod(hullmod, true);
                    }
                    for (String hullmod : variant.getNonBuiltInHullmods()) {
                        boolean sMod = variant.getSMods().contains(hullmod);
                        if (sMod) {
                            Global.getLogger(this.getClass()).info("Adding S mod " + hullmod);
                            kaiVaraint.addPermaMod(hullmod, sMod);
                        } else {
                            Global.getLogger(this.getClass()).info("Adding mod " + hullmod);
                            kaiVaraint.addMod(hullmod);
                        }
                    }
                    for (String hullmod : variant.getSuppressedMods()) {
                        Global.getLogger(this.getClass()).info(variant.getSuppressedMods() + "");
                        if (!kaiVaraint.getSuppressedMods().contains(hullmod)) {
                            boolean sMod = variant.getSMods().contains(hullmod);
                            if (sMod) {
                                Global.getLogger(this.getClass()).info("Adding supressed S mod " + hullmod);
                                kaiVaraint.addPermaMod(hullmod, sMod);
                            } else {
                                Global.getLogger(this.getClass()).info("Adding suppressed mod " + hullmod);
                                kaiVaraint.addMod(hullmod);
                            }
                        }
                    }
                    //variant.getW
                    variant.clear();
                    variant.removePermaMod("armaa_valkazardWeaponSwap");
                    //valk.setVariant(kaiVaraint, false, true);

                    String id = member.getId();
                    member.getFleetData().scuttle(member);
                    valk.setId(id);
                    //valk.getVariant()
                    AddShip.addShipGainText(member, dialog.getTextPanel());
                    Global.getSector().getPlayerFleet().getFleetData().addFleetMember(valk);
                    return true;
                }
            }
            return false;
        }
        if ("CheckValkKaiHasNoWings".equals(action)) {
            for (FleetMemberAPI member : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
                if (member.getHullId().contains("armaa_valkazard_kai")) {
                    if (member.getVariant().getModuleSlots().isEmpty()) {
                        return true;
                    }
                }
            }
            return false;
        }
        if ("upgradeValkKai".equals(action)) {
            for (FleetMemberAPI member : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
                if (member.getHullId().contains("armaa_valkazard_kai")) {
                    ShipVariantAPI variant = member.getVariant().clone();
                    FleetMemberAPI valk = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "armaa_valkazard_standard_kai");

                    ShipVariantAPI kaiVaraint = valk.getVariant();
                    ArrayList<String> hullmodsToRemove = new ArrayList();
                    for (String hullmod : variant.getHullMods()) {
                        if (hullmod.contains("selector")) {
                            hullmodsToRemove.add(hullmod);
                        }
                    }
                    for (String hullmod : hullmodsToRemove) {
                        variant.getHullMods().remove(hullmod);
                    }
                    for (String hullmod : variant.getPermaMods()) {
                        boolean sMod = variant.getSModdedBuiltIns().contains(hullmod);
                        if (sMod) {
                            Global.getLogger(this.getClass()).info("Adding perma mod " + hullmod);
                            kaiVaraint.removePermaMod(hullmod);
                            kaiVaraint.addPermaMod(hullmod, sMod);
                        }
                    }
                    for (String hullmod : variant.getSMods()) {
                        Global.getLogger(this.getClass()).info("Adding S mod " + hullmod);
                        kaiVaraint.removePermaMod(hullmod);
                        kaiVaraint.addPermaMod(hullmod, true);
                    }
                    for (String hullmod : variant.getSModdedBuiltIns()) {
                        Global.getLogger(this.getClass()).info("Adding BUILT IN S mod " + hullmod);
                        kaiVaraint.getHullMods().remove(hullmod);
                        Global.getLogger(this.getClass()).info(kaiVaraint.getHullMods() + "");
                        kaiVaraint.removePermaMod(hullmod);
                        kaiVaraint.addPermaMod(hullmod, true);
                    }
                    for (String hullmod : variant.getNonBuiltInHullmods()) {
                        boolean sMod = variant.getSMods().contains(hullmod);
                        if (sMod) {
                            Global.getLogger(this.getClass()).info("Adding S mod " + hullmod);
                            kaiVaraint.addPermaMod(hullmod, sMod);
                        } else {
                            Global.getLogger(this.getClass()).info("Adding mod " + hullmod);
                            kaiVaraint.addMod(hullmod);
                        }
                    }
                    for (String hullmod : variant.getSuppressedMods()) {
                        Global.getLogger(this.getClass()).info(variant.getSuppressedMods() + "");
                        if (!kaiVaraint.getSuppressedMods().contains(hullmod)) {
                            boolean sMod = variant.getSMods().contains(hullmod);
                            if (sMod) {
                                Global.getLogger(this.getClass()).info("Adding supressed S mod " + hullmod);
                                kaiVaraint.addPermaMod(hullmod, sMod);
                            } else {
                                Global.getLogger(this.getClass()).info("Adding suppressed mod " + hullmod);
                                kaiVaraint.addMod(hullmod);
                            }
                        }
                    }
                    //variant.getW
                    variant.clear();
                    variant.removePermaMod("armaa_valkazardWeaponSwap");
                    //valk.setVariant(kaiVaraint, false, true);

                    String id = member.getId();
                    member.getFleetData().scuttle(member);
                    valk.setId(id);
                    //valk.getVariant()
                    AddShip.addShipGainText(valk, dialog.getTextPanel());
                    Global.getSector().getPlayerFleet().getFleetData().addFleetMember(valk);
                    return true;
                }
            }
            return false;
        }
        if ("hasValkazard".equals(action)) {
            boolean hasValkazard = false;
            for (FleetMemberAPI member : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
                if (member.getHullSpec().getBaseHullId().equals("armaa_valkazard")) {
                    hasValkazard = true;
                    break;
                }
            }
            return hasValkazard;
        }
        if ("canGetUpgradeForValk".equals(action)) {

            return Global.getSector().getPlayerPerson().getStats().getLevel() >= 15;

        }
        if ("hasValkKai".equals(action)) {
            for (FleetMemberAPI member : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
                //Global.getLogger(this.getClass()).info(member.getHullSpec().getBaseHullId());
                if (member.getHullSpec().getBaseHullId().contains("armaa_valkazard_kai")) {
                    //Global.getLogger(this.getClass()).info("foo");
                    return true;
                }
                for (String slot : member.getVariant().getModuleSlots()) {
                    if (member.getVariant().getModuleVariant(slot).getHullSpec().getBaseHullId().contains("armaa_valkazard_kai")) {
                        return true;
                    }
                }
            }
            return false;
        }
        return false;
    }
}
