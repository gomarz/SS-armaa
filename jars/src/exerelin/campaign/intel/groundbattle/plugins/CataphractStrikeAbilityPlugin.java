package exerelin.campaign.intel.groundbattle.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.MutableStat.StatMod;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_VisualCustomPanel;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.intel.groundbattle.GroundBattleAI;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import exerelin.campaign.intel.groundbattle.GroundBattleRoundResolve;
import exerelin.campaign.intel.groundbattle.GroundUnit;
import exerelin.campaign.intel.groundbattle.IndustryForBattle;
import exerelin.campaign.intel.groundbattle.dialog.AbilityDialogPlugin.OptionId;
import exerelin.campaign.ui.InteractionDialogCustomPanelPlugin;
import exerelin.campaign.ui.CustomPanelPluginWithInput.RadioButtonEntry;
import exerelin.utilities.NexUtilsGUI;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;

public class CataphractStrikeAbilityPlugin extends AbilityPlugin {
   private static final Logger log = Logger.getLogger(CataphractStrikeAbilityPlugin.class);
   public static final float ENTRY_HEIGHT = 60.0F;
   public static final float BUTTON_WIDTH = 80.0F;
   public static float BASE_DAMAGE = 10.0F;
   public static int NUM_HITS = 1;
   public static float CR_TO_FIRE = 0.3F;
   public static float DISRUPT_TIME_MULT = 0.25F;
   public static final Set<String> CARRIER_IDS = new HashSet();
   public transient FleetMemberAPI cataphract;

   public float getDamage(FleetMemberAPI cataphract) {
      float damage = BASE_DAMAGE + cataphract.getMemberStrength() + cataphract.getStats().getDynamic().getMod("ground_support").getFlatBonus();
      if (!cataphract.getCaptain().isDefault()) {
         damage += (float)cataphract.getCaptain().getStats().getLevel() * 1.5F;
      }

      if (cataphract.getStats().getEnergyWeaponDamageMult().getPercentMods().get("cr_effect") != null) {
         damage *= 1.0F + ((StatMod)cataphract.getStats().getEnergyWeaponDamageMult().getPercentMods().get("cr_effect")).getValue() / 100.0F;
      }

      return (float)Math.round(damage);
   }

   public void activate(InteractionDialogAPI dialog, PersonAPI user) {
      super.activate(dialog, user);
      Set<GroundUnit> enemies = new HashSet();
      float totalDamage = 0.0F;
      this.logActivation(user);
      float damageTaken = 0.0F;
      IndustryForBattle ifb = this.target;
      boolean isAttacker = this.getSide().isAttacker();
      float damage = this.getDamage(this.cataphract);
      float attrition = this.getSide().getDropAttrition().getModifiedValue() / 100.0F;
      damage *= 1.0F - attrition;
      if (ifb.isContested()) {
         damage *= FireSupportAbilityPlugin.CLOSE_SUPPORT_DAMAGE_MULT;
      }

      float sideStrength = Math.max(1.0F, ifb.getStrength(this.getSide().isAttacker()) + (float)Math.round(damage));
      float enemyStrength = Math.max(1.0F, ifb.getStrength(!this.getSide().isAttacker()));
      damageTaken = Math.min(1.0F, (1.0F - attrition) * (enemyStrength / (sideStrength + enemyStrength))) * (1.0F - (float)this.cataphract.getCaptain().getStats().getLevel() / 100.0F * 1.5F);
      damageTaken = MathUtils.getRandomNumberInRange(damageTaken / 2.0F, damageTaken);
      Iterator i$ = ifb.getUnits().iterator();

      while(i$.hasNext()) {
         GroundUnit unit = (GroundUnit)i$.next();
         if (unit.isAttacker() != this.side.isAttacker()) {
            enemies.add(unit);
         }
      }

      boolean enemyHeld = ifb.heldByAttacker != this.side.isAttacker();
      GroundBattleRoundResolve resolve = new GroundBattleRoundResolve(this.getIntel());
      resolve.distributeDamage(ifb, !this.side.isAttacker(), (float)Math.round(damage));
      Iterator i$ = (new ArrayList(ifb.getUnits())).iterator();

      while(i$.hasNext()) {
         GroundUnit unit = (GroundUnit)i$.next();
         if (unit.getSize() <= 0) {
            unit.destroyUnit(0.0F);
         } else {
            resolve.checkReorganize(unit);
         }
      }

      boolean var10000;
      if (!ifb.getPlugin().getDef().hasTag("resistBombard") && !ifb.getPlugin().getDef().hasTag("noBombard")) {
         var10000 = true;
      } else {
         var10000 = false;
      }

      float disruptTime;
      if (enemyHeld && sideStrength > enemyStrength) {
         Industry ind = ifb.getIndustry();
         disruptTime = this.getDisruptionTime(ind) * DISRUPT_TIME_MULT;
         ind.setDisrupted(disruptTime + ind.getDisruptedDays(), true);
      }

      if (dialog != null) {
         Color h = Misc.getHighlightColor();
         dialog.getTextPanel().setFontSmallInsignia();
         dialog.getTextPanel().addPara("Inflicted %s damage across %s units. %s suffered %s damage.", h, new String[]{Math.round(damage) + "", enemies.size() + "", this.cataphract.getShipName(), Math.round(damageTaken * 100.0F) + "%"});
         disruptTime = this.getDisruptionTime(ifb.getIndustry()) * DISRUPT_TIME_MULT;
         if (enemyHeld && sideStrength > enemyStrength) {
            dialog.getTextPanel().addPara("Successfully penetrated enemy defenses! %s disrupted for %s days.", h, new String[]{this.target.getName(), (int)disruptTime + ""});
         }
      }

      this.cataphract.getRepairTracker().applyCREvent(-CR_TO_FIRE, "armaa_cataphractStrike", this.getDef().name);
      this.cataphract.getStatus().applyHullFractionDamage(damageTaken);
      this.getIntel().reapply();
      if (dialog != null) {
         dialog.getTextPanel().setFontInsignia();
      }

      this.cataphract = null;
   }

   public List<IndustryForBattle> getTargetIndustries() {
      List<IndustryForBattle> targets = new ArrayList();
      Iterator i$ = this.getIntel().getIndustries().iterator();

      while(i$.hasNext()) {
         IndustryForBattle ifb = (IndustryForBattle)i$.next();
         if (ifb.containsEnemyOf(this.side.isAttacker())) {
            targets.add(ifb);
         }
      }

      return targets;
   }

   public boolean targetsIndustry() {
      return true;
   }

   public float getDisruptionTime(Industry ind) {
      return ind.getSpec().getDisruptDanger().disruptionDays;
   }

   public void dialogAddIntro(InteractionDialogAPI dialog) {
      dialog.getTextPanel().addPara("Deploys a Cataphract on a strike mission against the target.");
      TooltipMakerAPI tooltip = dialog.getTextPanel().beginTooltip();
      this.generateTooltip(tooltip);
      dialog.getTextPanel().addTooltip();
      this.addCooldownDialogText(dialog);
   }

   public void dialogAddVisualPanel(final InteractionDialogAPI dialog) {
      float pad = 0.0F;
      float width = 596.0F;
      Color h = Misc.getHighlightColor();
      FactionAPI faction = PlayerFactionStore.getPlayerFaction();
      Color base = faction.getBaseUIColor();
      Color dark = faction.getDarkUIColor();
      Color bright = faction.getBrightUIColor();
      Nex_VisualCustomPanel.createPanel(dialog, true);
      CustomPanelAPI panel = Nex_VisualCustomPanel.getPanel();
      TooltipMakerAPI info = Nex_VisualCustomPanel.getTooltip();
      InteractionDialogCustomPanelPlugin plugin = Nex_VisualCustomPanel.getPlugin();
      List<FleetMemberAPI> cataphracts = this.getCataphracts(Global.getSector().getPlayerFleet());
      List<RadioButtonEntry> allButtons = new ArrayList();
      Iterator i$ = cataphracts.iterator();

      while(i$.hasNext()) {
         final FleetMemberAPI member = (FleetMemberAPI)i$.next();
         CustomPanelAPI itemPanel = panel.createCustomPanel(width, 60.0F, (CustomUIPanelPlugin)null);
         TooltipMakerAPI image = NexUtilsGUI.createFleetMemberImageForPanel(itemPanel, member, 60.0F, 60.0F);
         itemPanel.addUIElement(image).inTL(4.0F, 0.0F);
         TooltipMakerAPI text = itemPanel.createUIElement(width - PANEL_WIDTH - 4.0F, 60.0F, false);
         String name = member.getShipName();
         text.addPara(name, 0.0F, h, new String[]{member.getShipName()});
         float cr = member.getRepairTracker().getCR();
         String hp = member.getStatus().getHullFraction() * 100.0F + "";
         boolean enough = this.haveEnoughCR(member);
         String crStr = StringHelper.toPercent(cr);
         LabelAPI label = text.addPara(crStr + " " + "CR /" + " HP: " + hp + "%", pad);
         label.setHighlight(new String[]{crStr, hp});
         label.setHighlightColor(enough ? h : Misc.getNegativeHighlightColor());
         float str = this.getDamage(member);
         String strength = str + "";
         text.addPara("Unit Strength : " + strength, 0.0F, h, new String[]{strength});
         if (enough) {
            ButtonAPI button = text.addAreaCheckbox(StringHelper.getString("select", true), member, base, dark, bright, 80.0F, 16.0F, pad);
            button.setChecked(this.cataphract == member);
            RadioButtonEntry rbe = new RadioButtonEntry(button, "select_" + member.getId()) {
               public void onToggleImpl() {
                  CataphractStrikeAbilityPlugin.this.cataphract = member;
                  CataphractStrikeAbilityPlugin.this.dialogSetEnabled(dialog);
               }
            };
            plugin.addButton(rbe);
            allButtons.add(rbe);
         }

         itemPanel.addUIElement(text).rightOfTop(image, 3.0F);
         info.addCustom(itemPanel, 3.0F);
      }

      RadioButtonEntry rbe;
      for(i$ = allButtons.iterator(); i$.hasNext(); rbe.buttons = allButtons) {
         rbe = (RadioButtonEntry)i$.next();
      }

      Nex_VisualCustomPanel.addTooltipToPanel();
   }

   public void addDialogOptions(InteractionDialogAPI dialog) {
      super.addDialogOptions(dialog);
      this.dialogSetEnabled(dialog);
   }

   protected void dialogSetEnabled(InteractionDialogAPI dialog) {
      dialog.getOptionPanel().setEnabled(OptionId.ACTIVATE, this.cataphract != null);
   }

   public void dialogOnDismiss(InteractionDialogAPI dialog) {
      this.cataphract = null;
   }

   public void generateTooltip(TooltipMakerAPI tooltip) {
      float opad = 10.0F;
      Color h = Misc.getHighlightColor();
      float attrition = this.getSide().getDropAttrition().getModifiedValue() / 100.0F;
      String str = "Launches %s attack that inflicts damage based on the chosen unit's strength. Damage is reduced by $market's drop attrition of %s, and increased by %s if combat is ongoing on the target industry.Deployed units can be damaged during the mission, and are capable of disrupting the targeted industry if total unit strength combined with the strength of allied forces present on the industry exceeds that of the enemy force.";
      str = StringHelper.substituteToken(str, "$market", this.side.getIntel().getMarket().getName());
      tooltip.addPara(str, 0.0F, h, new String[]{"1", StringHelper.toPercent(attrition), StringHelper.toPercent(FireSupportAbilityPlugin.CLOSE_SUPPORT_DAMAGE_MULT - 1.0F)});
      tooltip.addPara("Cataphracts are inserted into the heart of the combat zone via an assault pod. This ensures they can undertake the mission with some level of suprise while somewhat mitigating the effect of ground defenses.", opad);
      tooltip.addPara("The selected Cataphract will consume %s CR to conduct this attack. In addition, only Cataphracts with at least %s HP can be selected.", opad, h, new String[]{StringHelper.toPercent(CR_TO_FIRE), "50%"});
   }

   public Pair<String, Map<String, Object>> getDisabledReason(PersonAPI user) {
      CampaignFleetAPI fleet = null;
      if (user != null) {
         if (user.isPlayer()) {
            fleet = Global.getSector().getPlayerFleet();
         } else {
            fleet = user.getFleet();
         }
      }

      String id;
      if (this.side.getData().containsKey("preventBombardmentSuper")) {
         Map<String, Object> params = new HashMap();
         String id = "bombardmentPrevented";
         id = GroundBattleIntel.getString("ability_ignisPluvia_prevented");
         params.put("desc", id);
         return new Pair(id, params);
      } else {
         if (fleet != null) {
            List<FleetMemberAPI> cataphracts = this.getCataphracts(fleet);
            if (cataphracts.isEmpty()) {
               Map<String, Object> params = new HashMap();
               id = "noMembers";
               String desc = GroundBattleIntel.getString("ability_ignisPluvia_noMembers");
               params.put("desc", desc);
               return new Pair(id, params);
            }
         }

         float[] strengths = this.getNearbyFleetStrengths();
         float ours = strengths[0];
         float theirs = strengths[1];
         if (ours < theirs * 2.0F) {
            Map<String, Object> params = new HashMap();
            String id = "enemyPresence";
            String ourStr = String.format("%.0f", ours);
            String theirStr = String.format("%.0f", theirs);
            String desc = String.format(GroundBattleIntel.getString("ability_bombard_enemyPresence"), ourStr, theirStr);
            params.put("desc", desc);
            return new Pair(id, params);
         } else {
            Pair<String, Map<String, Object>> reason = super.getDisabledReason(user);
            return reason;
         }
      }
   }

   public boolean showIfDisabled(Pair<String, Map<String, Object>> disableReason) {
      return this.hasAnyCataphracts(Global.getSector().getPlayerFleet());
   }

   public boolean hasActivateConfirmation() {
      return false;
   }

   public boolean shouldCloseDialogOnActivate() {
      return false;
   }

   public float getAIUsePriority(GroundBattleAI ai) {
      return 30.0F;
   }

   public boolean aiExecute(GroundBattleAI ai, PersonAPI user) {
      List<CampaignFleetAPI> fleets = this.getIntel().getSupportingFleets(this.side.isAttacker());
      if (fleets.isEmpty()) {
         return false;
      } else {
         CampaignFleetAPI fleet = null;
         this.cataphract = null;
         Iterator i$ = fleets.iterator();

         while(i$.hasNext()) {
            CampaignFleetAPI candidate = (CampaignFleetAPI)i$.next();
            if (!candidate.isPlayerFleet() && (candidate.getAI() == null || !candidate.getAI().isFleeing() && !candidate.getAI().isMaintainingContact())) {
               Iterator i$ = this.getCataphracts(candidate).iterator();

               while(i$.hasNext()) {
                  FleetMemberAPI member = (FleetMemberAPI)i$.next();
                  if (this.haveEnoughCR(member)) {
                     this.cataphract = member;
                     fleet = candidate;
                     break;
                  }
               }

               if (this.cataphract != null) {
                  break;
               }
            }
         }

         if (this.cataphract == null) {
            return false;
         } else {
            user = fleet.getCommander();
            return super.aiExecute(ai, user);
         }
      }
   }

   public boolean hasAnyCataphracts(CampaignFleetAPI fleet) {
      Iterator i$ = fleet.getFleetData().getMembersListCopy().iterator();

      FleetMemberAPI member;
      do {
         if (!i$.hasNext()) {
            return false;
         }

         member = (FleetMemberAPI)i$.next();
      } while(!this.isCataphract(member));

      return true;
   }

   public List<FleetMemberAPI> getCataphracts(CampaignFleetAPI fleet) {
      List<FleetMemberAPI> members = new ArrayList();
      if (fleet == null) {
         return members;
      } else {
         Iterator i$ = fleet.getFleetData().getMembersListCopy().iterator();

         while(i$.hasNext()) {
            FleetMemberAPI candidate = (FleetMemberAPI)i$.next();
            if (this.isCataphract(candidate)) {
               members.add(candidate);
            }
         }

         return members;
      }
   }

   public boolean isCataphract(FleetMemberAPI member) {
      return member.getVariant().hasHullMod("cataphract2") || member.getVariant().hasHullMod("cataphract");
   }

   public boolean haveEnoughCR(FleetMemberAPI member) {
      String hullId = member.getHullSpec().getBaseHullId();
      if (hullId == null) {
         hullId = member.getHullSpec().getHullId();
      }

      if (!member.getVariant().hasHullMod("cataphract2") && !member.getVariant().hasHullMod("cataphract")) {
         return false;
      } else {
         return member.getRepairTracker().getCR() >= CR_TO_FIRE && member.getStatus().getHullFraction() >= 0.5F;
      }
   }

   static {
      CARRIER_IDS.add("armaa_whitebase");
   }
}
