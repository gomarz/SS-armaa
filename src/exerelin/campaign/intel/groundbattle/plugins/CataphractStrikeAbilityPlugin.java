package exerelin.campaign.intel.groundbattle.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_VisualCustomPanel;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.intel.groundbattle.GBConstants;
import exerelin.campaign.intel.groundbattle.GroundBattleAI;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import exerelin.campaign.intel.groundbattle.GroundBattleRoundResolve;
import exerelin.campaign.intel.groundbattle.GroundUnit;
import exerelin.campaign.intel.groundbattle.IndustryForBattle;
import exerelin.campaign.intel.groundbattle.dialog.AbilityDialogPlugin;

import static exerelin.campaign.intel.groundbattle.plugins.FireSupportAbilityPlugin.CLOSE_SUPPORT_DAMAGE_MULT;

import exerelin.campaign.ui.InteractionDialogCustomPanelPlugin;
import exerelin.campaign.ui.CustomPanelPluginWithInput.RadioButtonEntry;
import exerelin.campaign.ui.CustomPanelPluginWithInput;
import exerelin.utilities.NexUtilsGUI;
import exerelin.utilities.StringHelper;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.lazywizard.lazylib.MathUtils;

public class CataphractStrikeAbilityPlugin extends AbilityPlugin {

    public static final float ENTRY_HEIGHT = 60;
    public static final float BUTTON_WIDTH = 80;
    public static float BASE_DAMAGE = 10;    // for comparison, fire support on a size 6 does 72
    public static int NUM_HITS = 1;
    public static float CR_TO_FIRE = 0.3f;
    public static float DISRUPT_TIME_MULT = 0.25f;

    public static final Set<String> CARRIER_IDS = new HashSet<>();

    static {
        CARRIER_IDS.add("armaa_whitebase");
    }

    public transient FleetMemberAPI cataphract;

    public float getDamage(FleetMemberAPI cataphract) {
        float damage = BASE_DAMAGE + (cataphract.getMemberStrength()) +
                cataphract.getStats().getDynamic().getMod(Stats.FLEET_GROUND_SUPPORT).getFlatBonus();
        if (!cataphract.getCaptain().isDefault())
            damage += cataphract.getCaptain().getStats().getLevel() * 1.5f;
        if (cataphract.getStats().getEnergyWeaponDamageMult().getPercentMods().get("cr_effect") != null)
            damage *= 1 + (cataphract.getStats().getEnergyWeaponDamageMult().getPercentMods().get("cr_effect").getValue()) / 100f;
        return Math.round(damage);
    }

    @Override
    public void activate(InteractionDialogAPI dialog, PersonAPI user) {
        super.activate(dialog, user);

        Set<GroundUnit> enemies = new HashSet<>();
        float totalDamage = 0;

        logActivation(user);    // so it displays before the unit destruction messages, if any
        float damageTaken = 0f;
        IndustryForBattle ifb = target;
        boolean isAttacker = getSide().isAttacker();
        float damage = getDamage(cataphract);
        float attrition = getSide().getDropAttrition().getModifiedValue() / 100;
        damage *= 1 - attrition;
        if (ifb.isContested()) damage *= CLOSE_SUPPORT_DAMAGE_MULT;
        float sideStrength = Math.max(1, ifb.getStrength(getSide().isAttacker()) + Math.round(damage));
        float enemyStrength = Math.max(1, ifb.getStrength(!getSide().isAttacker()));
        damageTaken = Math.min(1f, (1f - attrition) * (enemyStrength / (sideStrength + enemyStrength))) * (1f -
                (cataphract.getCaptain().getStats().getLevel() / 100f) * 1.5f);
        damageTaken = MathUtils.getRandomNumberInRange(damageTaken / 2, damageTaken);
        for (GroundUnit unit : ifb.getUnits()) {
            if (unit.isAttacker() != side.isAttacker()) {
                enemies.add(unit);
            }
        }

        boolean enemyHeld = ifb.heldByAttacker != side.isAttacker();

        GroundBattleRoundResolve resolve = new GroundBattleRoundResolve(getIntel());
        resolve.distributeDamage(ifb, !side.isAttacker(), Math.round(damage));
        for (GroundUnit unit : new ArrayList<>(ifb.getUnits())) {
            if (unit.getSize() <= 0)
                unit.destroyUnit(0);
            else
                resolve.checkReorganize(unit);
        }
        //log.info(cataphract.getStats().getDynamic().getMod(Stats.FLEET_GROUND_SUPPORT).getFlatBonus());
        //log.info("Applying " + damage + " damage on " + ifb.getName());
        //log.info("Side strength: " + sideStrength + " Enemy Strength: " + enemyStrength);
        boolean canDisrupt = !ifb.getPlugin().getDef().hasTag("resistBombard")
                && !ifb.getPlugin().getDef().hasTag("noBombard");
        if (enemyHeld && sideStrength > enemyStrength) {
            Industry ind = ifb.getIndustry();
            float disruptTime = getDisruptionTime(ind) * DISRUPT_TIME_MULT;
            ind.setDisrupted(disruptTime + ind.getDisruptedDays(), true);
        }


        // print results
        if (dialog != null) {
            Color h = Misc.getHighlightColor();
            dialog.getTextPanel().setFontSmallInsignia();
            dialog.getTextPanel().addPara("Inflicted %s damage across %s units. %s suffered %s damage.",
                    h, Math.round(damage) + "", enemies.size() + "", cataphract.getShipName(), Math.round(damageTaken * 100f) + "%");
            float disruptTime = getDisruptionTime(ifb.getIndustry()) * DISRUPT_TIME_MULT;
            if (enemyHeld && sideStrength > enemyStrength) {
                dialog.getTextPanel().addPara("Successfully penetrated enemy defenses! %s disrupted for %s days.",
                        h, target.getName(), (int) disruptTime + "");
            }
            ;
        }

        cataphract.getRepairTracker().applyCREvent(-CR_TO_FIRE, "armaa_cataphractStrike", getDef().name);
        cataphract.getStatus().applyHullFractionDamage(damageTaken);
        getIntel().reapply();

        if (dialog != null)
            dialog.getTextPanel().setFontInsignia();

        cataphract = null;
    }

    @Override
    public List<IndustryForBattle> getTargetIndustries() {
        List<IndustryForBattle> targets = new ArrayList<>();
        for (IndustryForBattle ifb : getIntel().getIndustries()) {
            if (!ifb.containsEnemyOf(side.isAttacker())) continue;
            targets.add(ifb);
        }
        return targets;
    }

    @Override
    public boolean targetsIndustry() {
        return true;
    }

    public float getDisruptionTime(Industry ind) {
        return ind.getSpec().getDisruptDanger().disruptionDays;
    }

    @Override
    public void dialogAddIntro(InteractionDialogAPI dialog) {
        dialog.getTextPanel().addPara("Deploys a Cataphract on a strike mission against the target.");
        TooltipMakerAPI tooltip = dialog.getTextPanel().beginTooltip();
        generateTooltip(tooltip);
        dialog.getTextPanel().addTooltip();

        addCooldownDialogText(dialog);
    }

    @Override
    public void dialogAddVisualPanel(final InteractionDialogAPI dialog) {
        float pad = 0;
        float width = Nex_VisualCustomPanel.PANEL_WIDTH - 4;
        Color h = Misc.getHighlightColor();
        FactionAPI faction = PlayerFactionStore.getPlayerFaction();
        Color base = faction.getBaseUIColor();
        Color dark = faction.getDarkUIColor();
        Color bright = faction.getBrightUIColor();

        Nex_VisualCustomPanel.createPanel(dialog, true);
        CustomPanelAPI panel = Nex_VisualCustomPanel.getPanel();
        TooltipMakerAPI info = Nex_VisualCustomPanel.getTooltip();
        InteractionDialogCustomPanelPlugin plugin = Nex_VisualCustomPanel.getPlugin();

        List<FleetMemberAPI> cataphracts = getCataphracts(Global.getSector().getPlayerFleet());

        List<RadioButtonEntry> allButtons = new ArrayList<>();

        for (final FleetMemberAPI member : cataphracts) {
            CustomPanelAPI itemPanel = panel.createCustomPanel(width, ENTRY_HEIGHT, null);
            TooltipMakerAPI image = NexUtilsGUI.createFleetMemberImageForPanel(itemPanel, member, ENTRY_HEIGHT, ENTRY_HEIGHT);
            itemPanel.addUIElement(image).inTL(4, 0);

            TooltipMakerAPI text = itemPanel.createUIElement(width - PANEL_WIDTH - 4, ENTRY_HEIGHT, false);
            String name = member.getShipName();
            text.addPara(name, 0, h, member.getShipName());

            float cr = member.getRepairTracker().getCR();
            String hp = member.getStatus().getHullFraction() * 100 + "";
            boolean enough = haveEnoughCR(member);
            String crStr = StringHelper.toPercent(cr);
            LabelAPI label = text.addPara(crStr + " " + "CR /" + " HP: " + hp + "%", pad);
            label.setHighlight(crStr, hp);
            label.setHighlightColor(enough ? h : Misc.getNegativeHighlightColor());

            float str = getDamage(member);
            String strength = str + "";
            text.addPara("Unit Strength : " + strength, 0, h, strength);

            if (enough) {
                ButtonAPI button = text.addAreaCheckbox(StringHelper.getString("select", true),
                        member, base, dark, bright, BUTTON_WIDTH, 16, pad);
                button.setChecked(cataphract == member);
                RadioButtonEntry rbe = new RadioButtonEntry(button, "select_" + member.getId()) {
                    @Override
                    public void onToggleImpl() {
                        cataphract = member;
                        //dialogAddVisualPanel(dialog);
                        dialogSetEnabled(dialog);
                    }
                };
                plugin.addButton(rbe);
                allButtons.add(rbe);
            }

            itemPanel.addUIElement(text).rightOfTop(image, 3);

            info.addCustom(itemPanel, 3);
            //dialog.getTextPanel().addPara("Adding Olympus " + member.getShipName());
        }
        for (RadioButtonEntry rbe : allButtons) {
            rbe.buttons = allButtons;
        }
        Nex_VisualCustomPanel.addTooltipToPanel();
    }

    @Override
    public void addDialogOptions(InteractionDialogAPI dialog) {
        super.addDialogOptions(dialog);
        dialogSetEnabled(dialog);
    }

    protected void dialogSetEnabled(InteractionDialogAPI dialog) {
        dialog.getOptionPanel().setEnabled(AbilityDialogPlugin.OptionId.ACTIVATE, cataphract != null);
    }

    @Override
    public void dialogOnDismiss(InteractionDialogAPI dialog) {
        cataphract = null;
    }

    @Override
    public void generateTooltip(TooltipMakerAPI tooltip) {
        float opad = 10;
        Color h = Misc.getHighlightColor();
        float attrition = getSide().getDropAttrition().getModifiedValue() / 100;
        String str = "Launches %s attack that inflicts damage based on the chosen unit's strength. " +
                "Damage is reduced by $market's drop attrition of %s, and increased by %s if combat is ongoing on the target industry." +
                "Deployed units can be damaged during the mission, and are capable of disrupting the targeted industry if total unit strength combined" +
                " with the strength of allied forces present on the industry exceeds that of the enemy force.";
        str = StringHelper.substituteToken(str, "$market", side.getIntel().getMarket().getName());
        tooltip.addPara(str, 0, h, (int) 1 + "", StringHelper.toPercent(attrition),
                StringHelper.toPercent(FireSupportAbilityPlugin.CLOSE_SUPPORT_DAMAGE_MULT - 1));
        tooltip.addPara("Cataphracts are inserted into the heart of the combat zone via an assault pod. " +
                "This ensures they can undertake the mission with some level of suprise while somewhat mitigating the effect of ground defenses.", opad);
        tooltip.addPara("The selected Cataphract will consume %s CR to conduct this attack. In addition, only Cataphracts with at least %s HP can be selected.", opad,
                h, StringHelper.toPercent(CR_TO_FIRE), "50%");
    }

    @Override
    public Pair<String, Map<String, Object>> getDisabledReason(PersonAPI user) {
        CampaignFleetAPI fleet = null;
        if (user != null) {
            if (user.isPlayer()) fleet = Global.getSector().getPlayerFleet();
            else fleet = user.getFleet();
        }

        if (side.getData().containsKey(GBConstants.TAG_PREVENT_BOMBARDMENT_SUPER)) {
            Map<String, Object> params = new HashMap<>();

            String id = "bombardmentPrevented";
            String desc = GroundBattleIntel.getString("ability_ignisPluvia_prevented");
            params.put("desc", desc);
            return new Pair<>(id, params);
        }
        if (fleet != null) {
            List<FleetMemberAPI> cataphracts = getCataphracts(fleet);
            if (cataphracts.isEmpty()) {
                Map<String, Object> params = new HashMap<>();

                String id = "noMembers";
                String desc = GroundBattleIntel.getString("ability_ignisPluvia_noMembers");
                params.put("desc", desc);
                return new Pair<>(id, params);
            }
        }
        float[] strengths = getNearbyFleetStrengths();
        {
            float ours = strengths[0];
            float theirs = strengths[1];
            if (ours < theirs * 2) {
                Map<String, Object> params = new HashMap<>();

                String id = "enemyPresence";
                String ourStr = String.format("%.0f", ours);
                String theirStr = String.format("%.0f", theirs);

                String desc = String.format(GroundBattleIntel.getString("ability_bombard_enemyPresence"), ourStr, theirStr);
                params.put("desc", desc);
                return new Pair<>(id, params);
            }
        }

        Pair<String, Map<String, Object>> reason = super.getDisabledReason(user);
        return reason;
    }

    @Override
    public boolean showIfDisabled(Pair<String, Map<String, Object>> disableReason) {
        return hasAnyCataphracts(Global.getSector().getPlayerFleet());
    }

    @Override
    public boolean hasActivateConfirmation() {
        return false;
    }

    @Override
    public boolean shouldCloseDialogOnActivate() {
        return false;
    }

    @Override
    public float getAIUsePriority(GroundBattleAI ai) {
        return 30;
    }

    @Override
    public boolean aiExecute(GroundBattleAI ai, PersonAPI user) {
        // find a fleet to execute ability
        // NPCs can't use player fleet
        List<CampaignFleetAPI> fleets = getIntel().getSupportingFleets(side.isAttacker());
        if (fleets.isEmpty()) return false;

        CampaignFleetAPI fleet = null;
        cataphract = null;

        for (CampaignFleetAPI candidate : fleets) {
            if (candidate.isPlayerFleet()) continue;
            if (candidate.getAI() != null) {
                if (candidate.getAI().isFleeing() || candidate.getAI().isMaintainingContact())
                    continue;
            }
            for (FleetMemberAPI member : getCataphracts(candidate)) {
                if (haveEnoughCR(member)) {
                    cataphract = member;
                    fleet = candidate;
                    break;
                }
            }
            if (cataphract != null) break;
        }

        if (cataphract == null) return false;
        user = fleet.getCommander();
        return super.aiExecute(ai, user);
    }

    public boolean hasAnyCataphracts(CampaignFleetAPI fleet) {
        for (FleetMemberAPI member : fleet.getFleetData().getCombatReadyMembersListCopy()) {
            if (isCataphract(member)) return true;
        }
        return false;
    }

    public List<FleetMemberAPI> getCataphracts(CampaignFleetAPI fleet) {
        List<FleetMemberAPI> members = new ArrayList<>();
        if (fleet == null) return members;
        for (FleetMemberAPI candidate : fleet.getFleetData().getCombatReadyMembersListCopy()) {
            if (isCataphract(candidate)) {
                members.add(candidate);
            }
        }
        return members;
    }

    public boolean isCataphract(FleetMemberAPI member) {
        return !(!member.getVariant().getTags().contains("armaa_nexGroundCapable") && !member.getVariant().hasHullMod("cataphract2") && !member.getVariant().hasHullMod("cataphract") &&!member.getVariant().hasHullMod("armaa_variableUnit"));
    }

    public boolean haveEnoughCR(FleetMemberAPI member) {
        String hullId = member.getHullSpec().getBaseHullId();
        if (hullId == null) hullId = member.getHullSpec().getHullId();

        //if (true) {
        return member.getRepairTracker().getCR() >= CR_TO_FIRE && member.getStatus().getHullFraction() >= 0.5f;
        //}

        //return true;
    }
}