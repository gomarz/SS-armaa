package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.FighterLaunchBayAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.FighterWingAPI.ReturningFighter;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.ai.armaa_combat_docking_AI_fighter;
import data.scripts.util.armaa_utils;
import java.awt.Color;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Random;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

public class armaa_wingCommander extends BaseHullMod {
   private Object KEY_JITTER = new Object();
   private Color COLOR = new Color(0, 106, 0, 50);
   private static final float RETREAT_AREA_SIZE = 2000.0F;
   private boolean hasLanded;
   private Vector2f landingLoc = new Vector2f();
   public static final Color JITTER_UNDER_COLOR = new Color(50, 125, 150, 50);
   public static final float MAX_TIME_MULT = 1.1F;
   public static final float ENGAGEMENT_REDUCTION = 0.4F;
   public static final int BOMBER_COST_MOD = 10000;
   private final Color HL = Global.getSettings().getColor("hColor");

   public void applyEffectsBeforeShipCreation(HullSize var1, MutableShipStatsAPI var2, String var3) {
      var2.getDynamic().getMod("bomber_cost_mod").modifyFlat(var3, 10000.0F);
      var2.getDynamic().getMod("bomber_cost_mod").modifyFlat(var3, 10000.0F);
      var2.getFighterWingRange().modifyMult(var3, 0.6F);
   }

   public boolean affectsOPCosts() {
      return true;
   }

   public String getUnapplicableReason(ShipAPI var1) {
      return var1 == null ? "Can not be installed" : "Can not be installed";
   }

   public void addPostDescriptionSection(TooltipMakerAPI var1, HullSize var2, ShipAPI var3, float var4, boolean var5) {
      var1.addSectionHeading("=== S Q U A D R O N   I N F O ===", Alignment.MID, 15.0F);
      FighterWingSpecAPI var6 = var3.getVariant().getWing(0);
      int var7 = 0;
      if (var6 != null) {
         var7 = var3.getVariant().getWing(0).getNumFighters();
      }

      if (var3 != null && var3.getVariant() != null) {
         if (var6 == null) {
            var1.addPara("No Wing assigned.", 10.0F, this.HL, new String[0]);
         } else {
            String var8 = var3.getCaptain().getNameString();
            if (!var3.getCaptain().isDefault()) {
               for(int var9 = 0; var9 < var7; ++var9) {
                  PersonAPI var10 = null;
                  String var11;
                  if (Global.getSector().getPersistentData().get("armaa_wingCommander_wingman_" + var9 + "_" + var3.getCaptain().getId()) instanceof PersonAPI) {
                     var10 = (PersonAPI)Global.getSector().getPersistentData().get("armaa_wingCommander_wingman_" + var9 + "_" + var3.getCaptain().getId());
                     var11 = (String)Global.getSector().getPersistentData().get("armaa_wingCommander_wingman_" + var9 + "_" + "callsign_" + var3.getCaptain().getId());
                  } else {
                     var10 = OfficerManagerEvent.createOfficer(Global.getSector().getFaction("player"), 1, true);
                     var11 = OfficerManagerEvent.createOfficer(Global.getSector().getFaction("player"), 1, true).getName().getLast();
                     Global.getSector().getPersistentData().put("armaa_wingCommander_wingman_" + var9 + "_" + var3.getCaptain().getId(), var10);
                     Global.getSector().getPersistentData().put("armaa_wingCommander_wingman_" + var9 + "_" + "callsign_" + var3.getCaptain().getId(), var11);
                  }

                  var1.addSectionHeading(var10.getName().getFirst() + " \"" + var11 + "\" " + var10.getName().getLast(), Alignment.MID, 15.0F);
                  var1.beginImageWithText(var10.getPortraitSprite(), 64.0F).addPara("blah", 3.0F, Color.red, new String[0]);
                  var1.addImageWithText(8.0F);
               }
            }
         }
      }

   }

   public void advanceInCombat(ShipAPI var1, float var2) {
      if (var1.getOriginalOwner() != -1) {
         if (!var1.getLaunchBaysCopy().isEmpty()) {
            FighterLaunchBayAPI var3 = (FighterLaunchBayAPI)var1.getLaunchBaysCopy().get(0);
            if (var3.getWing() != null) {
               int var4;
               ShipAPI var5;
               for(var4 = 0; var4 < var3.getWing().getWingMembers().size(); ++var4) {
                  var5 = (ShipAPI)var3.getWing().getWingMembers().get(var4);
                  if (var5 != null && !var5.isHulk() && var5 != null) {
                     float var6 = 1.1F;
                     var5.getMutableStats().getTimeMult().modifyMult(var1.getId(), var6);
                     var5.setWeaponGlow(1.0F, Misc.setAlpha(JITTER_UNDER_COLOR, 50), EnumSet.allOf(WeaponType.class));
                     var5.setJitterUnder(var5, JITTER_UNDER_COLOR, 1.0F, 5, 3.0F, 6.0F);
                     if (var5.isLiftingOff()) {
                        float var7 = Global.getCombatEngine().getMapWidth() / 2.0F;
                        float var8 = Global.getCombatEngine().getMapHeight() / 2.0F;
                        Vector2f var9 = new Vector2f(-var7, -var8);
                        Vector2f var10 = new Vector2f(var7, var8);
                        Vector2f var11 = new Vector2f((var9.x + var10.x) / 2.0F, (var9.y + var10.y) / 2.0F - var10.y);
                        if (Global.getCombatEngine().getCustomData().get("armaa_wingCommander_landingLocation_" + var1.getId() + "_" + var4) instanceof Vector2f) {
                           var11 = (Vector2f)Global.getCombatEngine().getCustomData().get("armaa_wingCommander_landingLocation_" + var1.getId() + "_" + var4);
                        }

                        armaa_utils.setLocation(var5, var11);
                     }
                  }
               }

               for(var4 = 0; var4 < var3.getWing().getReturning().size(); ++var4) {
                  var5 = ((ReturningFighter)var3.getWing().getReturning().get(var4)).fighter;
                  if (var5 != null && var5 != null && var3.getWing().isReturning(var5)) {
                     armaa_combat_docking_AI_fighter var13 = new armaa_combat_docking_AI_fighter(var5);
                     if (var5.getShipAI() != var13) {
                     }

                     var5.setShipAI(var13);
                     var13.init();
                     if (var5.isLanding()) {
                        Iterator var14 = CombatUtils.getShipsWithinRange(var1.getLocation(), 100.0F).iterator();

                        label83:
                        while(true) {
                           ShipAPI var15;
                           do {
                              do {
                                 do {
                                    if (!var14.hasNext()) {
                                       break label83;
                                    }

                                    var15 = (ShipAPI)var14.next();
                                 } while(var15.getOwner() != var5.getOwner());
                              } while(var15.isFighter());
                           } while(var15.isFrigate() && !var15.isStationModule());

                           if (var15.hasLaunchBays()) {
                              Random var16 = new Random();
                              int var17 = var16.nextInt(var15.getLaunchBaysCopy().size());
                              ShipAPI var12 = ((FighterLaunchBayAPI)var15.getLaunchBaysCopy().get(var17)).getWing().getLeader();
                              if (var12 != null) {
                                 Global.getCombatEngine().getCustomData().put("armaa_wingCommander_landingLocation_" + var1.getId() + "_" + var4, var12.getWing().getSource().getLandingLocation(var12));
                                 break;
                              }
                           }
                        }
                     }

                     if (var5.isFinishedLanding()) {
                        var3.land(var5);
                        Global.getCombatEngine().removeEntity(var5);
                     }
                  }
               }
            }

         }
      }
   }

   public void applyEffectsAfterShipCreation(ShipAPI var1, String var2) {
   }

   public String getDescriptionParam(int var1, HullSize var2) {
      float var3 = 10.000002F;
      if (var1 == 0) {
         return "40.0%";
      } else if (var1 == 1) {
         return "wingmen will return to nearby carriers to rearm and refit";
      } else if (var1 == 2) {
         return "replacement craft will enter the combat area from the deployment zone";
      } else if (var1 == 3) {
         return "" + (int)var3 + "%";
      } else {
         return var1 == 4 ? "Bombers" : null;
      }
   }
}
