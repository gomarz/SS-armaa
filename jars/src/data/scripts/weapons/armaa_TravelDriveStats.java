package data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript.State;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript.StatusData;
import data.scripts.util.armaa_utils;
import java.awt.Color;
import java.util.Iterator;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

public class armaa_TravelDriveStats extends BaseShipSystemScript {
   private boolean carriersNearby = false;
   private boolean runOnce = false;
   private WeaponSlotAPI w;
   private ShipAPI carrier;

   public void apply(MutableShipStatsAPI var1, String var2, State var3, float var4) {
      ShipAPI var5 = (ShipAPI)var1.getEntity();
      boolean var6 = var5.getFacing() == 90.0F;
      if (!Global.getCombatEngine().isPaused()) {
         if (var5.getOwner() == 1) {
            var6 = var5.getFacing() == 270.0F;
         }

         boolean var7 = Global.getCombatEngine().getCustomData().get("armaa_carrierDeployDone_" + var5.getId()) instanceof Boolean;
         if (this.getRandomCarrier(var5, true) != null && !var5.isRetreating() && var6 && !var7) {
            if (!var5.isLanding() && !this.runOnce) {
               this.carrier = this.getRandomCarrier(var5, false);
               Vector2f var8 = null;
               Iterator var9 = this.carrier.getHullSpec().getAllWeaponSlotsCopy().iterator();

               while(var9.hasNext()) {
                  WeaponSlotAPI var10 = (WeaponSlotAPI)var9.next();
                  if (var10.getWeaponType() == WeaponType.LAUNCH_BAY) {
                     if (Global.getCombatEngine().getPlayerShip() == var5) {
                        Global.getSoundPlayer().playSound("ui_noise_static", 1.0F + MathUtils.getRandomNumberInRange(-0.3F, 0.3F), 1.0F, this.carrier.getLocation(), new Vector2f());
                        this.carrier.getFluxTracker().showOverloadFloatyIfNeeded("Good luck out there!", Color.white, 2.0F, true);
                     }

                     var5.setFacing(this.carrier.getFacing() + var10.getAngle());
                     var8 = new Vector2f(var10.computePosition(this.carrier));
                     if (Math.random() <= 0.5D) {
                        break;
                     }
                  }
               }

               if (var8 == null) {
                  var8 = this.carrier.getLocation();
               }

               var5.setLaunchingShip(this.carrier);
               armaa_utils.setLocation(var5, var8);
               var5.setAnimatedLaunch();
               Global.getSoundPlayer().playSound("fighter_takeoff", 1.0F, 1.0F, var5.getLocation(), new Vector2f());
               CombatUtils.applyForce(var5, var5.getFacing(), MathUtils.getRandomNumberInRange(1.0F, 100.0F));
               this.runOnce = true;
            }
         } else {
            if (var3 == State.OUT) {
               var1.getMaxSpeed().unmodify(var2);
            } else {
               var1.getMaxSpeed().modifyFlat(var2, 100.0F * var4);
               var1.getAcceleration().modifyFlat(var2, 100.0F * var4);
            }

            Global.getCombatEngine().getCustomData().put("armaa_carrierDeployDone_" + var5.getId(), true);
         }

      }
   }

   public void unapply(MutableShipStatsAPI var1, String var2) {
      ShipAPI var3 = (ShipAPI)var1.getEntity();
      var1.getMaxSpeed().unmodify(var2);
      var1.getMaxTurnRate().unmodify(var2);
      var1.getTurnAcceleration().unmodify(var2);
      var1.getAcceleration().unmodify(var2);
      var1.getDeceleration().unmodify(var2);
   }

   public StatusData getStatusData(int var1, State var2, float var3) {
      return var1 == 0 ? new StatusData("increased engine power", false) : null;
   }

   private ShipAPI getRandomCarrier(ShipAPI var1, boolean var2) {
      ShipAPI var3 = null;
      Iterator var4 = CombatUtils.getShipsWithinRange(var1.getLocation(), 20000.0F).iterator();

      while(true) {
         ShipAPI var5;
         int var6;
         do {
            do {
               do {
                  do {
                     do {
                        label56:
                        do {
                           while(var4.hasNext()) {
                              var5 = (ShipAPI)var4.next();
                              var6 = 0;
                              if (Global.getCombatEngine().getCustomData().get("armaa_launchSlots" + var5.getId()) instanceof Integer) {
                                 var6 = (Integer)Global.getCombatEngine().getCustomData().get("armaa_launchSlots" + var5.getId());
                              } else {
                                 Global.getCombatEngine().getCustomData().put("armaa_launchSlots" + var5.getId(), var6);
                              }

                              if (var1.getHullSpec().hasTag("strikecraft_medium")) {
                                 if (var5.isDestroyer()) {
                                    continue;
                                 }
                                 continue label56;
                              } else if (!var1.getHullSpec().hasTag("strikecraft_large") || !var5.isCruiser() && !var5.isDestroyer()) {
                                 continue label56;
                              }
                           }

                           return var3;
                        } while(var6 >= var5.getNumFighterBays());
                     } while(var5.getOwner() != var1.getOwner());
                  } while(var5.isFighter());
               } while(var5.isFrigate());
            } while(var5 == var1);
         } while(var5.getOwner() == var1.getOwner() && var5.isAlly() && !var1.isAlly());

         if (!var5.isHulk() && var5.getNumFighterBays() > 0 && var5.getHullSpec().getFighterBays() > 0) {
            var3 = var5;
            if (!var2) {
               Global.getCombatEngine().getCustomData().put("armaa_launchSlots" + var5.getId(), var6 + 1);
               return var5;
            }
         }
      }
   }
}
