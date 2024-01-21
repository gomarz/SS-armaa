package data.scripts.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.EngagementResultForFleetAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin.DataForEncounterSide;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.Misc;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class armaa_drugsAreBad extends BaseCampaignEventListener implements EveryFrameScript {
   transient EngagementResultAPI batResult;
   transient boolean isWin = false;
   static long previousXP = Long.MAX_VALUE;
   private int numEngagements = 0;
   public TextPanelAPI textPanelForXPGain = null;
   private Random salvageRandom = null;

   public armaa_drugsAreBad() {
      super(true);
   }

   public static long getReAdjustedXp() {
      long var0 = Global.getSector().getPlayerStats().getXP() - previousXP;
      return var0;
   }

   public TextPanelAPI getTextPanelForXPGain() {
      return this.textPanelForXPGain;
   }

   public void setTextPanelForXPGain(TextPanelAPI var1) {
      this.textPanelForXPGain = var1;
   }

   public Random getSalvageRandom() {
      return this.salvageRandom;
   }

   public void setSalvageRandom(Random var1) {
      this.salvageRandom = var1;
   }

   public boolean runWhilePaused() {
      return false;
   }

   public boolean isDone() {
      return false;
   }

   public void advance(float var1) {
      long var2 = Global.getSector().getPlayerStats().getXP();
      if (previousXP == Long.MAX_VALUE) {
         previousXP = var2;
      } else if (previousXP < var2) {
         long var4 = Math.min(10000L, Global.getSector().getPlayerStats().getXP() - previousXP);
         previousXP = var2;
      }

   }

   public DataForEncounterSide getDataFor(CampaignFleetAPI var1, BattleAPI var2, List<DataForEncounterSide> var3) {
      CampaignFleetAPI var4 = var2.getCombinedFor(var1);
      if (var4 == null) {
         return new DataForEncounterSide(var1);
      } else {
         Iterator var5 = var3.iterator();

         DataForEncounterSide var6;
         do {
            if (!var5.hasNext()) {
               DataForEncounterSide var7 = new DataForEncounterSide(var4);
               var3.add(var7);
               return var7;
            }

            var6 = (DataForEncounterSide)var5.next();
         } while(var6.getFleet() != var4);

         return var6;
      }
   }

   protected void clearNoSourceMembers(EngagementResultForFleetAPI var1, BattleAPI var2) {
      Iterator var3 = var1.getDeployed().iterator();

      FleetMemberAPI var4;
      while(var3.hasNext()) {
         var4 = (FleetMemberAPI)var3.next();
         if (var2.getSourceFleet(var4) == null) {
            var3.remove();
         }
      }

      var3 = var1.getReserves().iterator();

      while(var3.hasNext()) {
         var4 = (FleetMemberAPI)var3.next();
         if (var2.getSourceFleet(var4) == null) {
            var3.remove();
         }
      }

      var3 = var1.getDestroyed().iterator();

      while(var3.hasNext()) {
         var4 = (FleetMemberAPI)var3.next();
         if (var2.getSourceFleet(var4) == null) {
            var3.remove();
         }
      }

      var3 = var1.getDisabled().iterator();

      while(var3.hasNext()) {
         var4 = (FleetMemberAPI)var3.next();
         if (var2.getSourceFleet(var4) == null) {
            var3.remove();
         }
      }

      var3 = var1.getRetreated().iterator();

      while(var3.hasNext()) {
         var4 = (FleetMemberAPI)var3.next();
         if (var2.getSourceFleet(var4) == null) {
            var3.remove();
         }
      }

   }

   public void reportPlayerEngagement(EngagementResultAPI var1) {
      this.batResult = var1;
      ++this.numEngagements;
   }

   public void reportBattleOccurred(CampaignFleetAPI var1, BattleAPI var2) {
      if (var2.isPlayerInvolved()) {
         this.isWin = var2 != null && var1 != null && var2.getPlayerSide() != null ? var2.getPlayerSide().contains(var1) : null;
         ArrayList var3 = new ArrayList();
         long var4 = getReAdjustedXp();
         if (var4 > 0L) {
            EngagementResultForFleetAPI var6 = this.batResult.getWinnerResult();
            EngagementResultForFleetAPI var7 = this.batResult.getLoserResult();
            this.clearNoSourceMembers(var6, var2);
            this.clearNoSourceMembers(var7, var2);
            DataForEncounterSide var8 = this.getDataFor(var6.getFleet(), var2, var3);
            DataForEncounterSide var9 = this.getDataFor(var7.getFleet(), var2, var3);
            if (var2.isPlayerSide(var2.getSideFor(var9.getFleet()))) {
               ;
            }

            EngagementResultForFleetAPI var14 = var7.isPlayer() ? var7 : var6;
            ArrayList var15 = new ArrayList();
            var15.addAll(var14.getDestroyed());
            var15.addAll(var14.getDisabled());
            var15.addAll(var14.getRetreated());
            Iterator var16 = Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy().iterator();

            while(true) {
               FleetMemberAPI var17;
               do {
                  do {
                     do {
                        if (!var16.hasNext()) {
                           this.numEngagements = 0;
                           this.batResult = null;
                           return;
                        }

                        var17 = (FleetMemberAPI)var16.next();
                     } while(!var17.getHullId().equals("armaa_valkazard"));
                  } while(!(Global.getSector().getPlayerFleet().getCargo().getCommodityQuantity("drugs") > 0.0F));
               } while(!var14.getDeployed().contains(var17) && !var15.contains(var17));

               String var18 = this.numEngagements > 1 ? "units" : "unit";
               Global.getSector().getCampaignUI().addMessage(this.numEngagements + " " + var18 + " of " + "drugs".toString() + " consumed to replenish combat stims aboard " + var17.getShipName(), Misc.getNegativeHighlightColor());
               Global.getSoundPlayer().playUISound("ui_cargo_drugs", 1.0F, 1.0F);
               Global.getSector().getPlayerFleet().getCargo().removeCommodity("drugs", (float)this.numEngagements);
            }
         }
      }
   }
}
