package data.scripts.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.CombatTaskManagerAPI;
import com.fs.starfarer.api.combat.FighterLaunchBayAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponGroupAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import java.util.Iterator;
import java.util.List;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Vector2f;

public class armaa_strikeCraftRepairTracker extends BaseEveryFrameCombatPlugin {
   private final IntervalUtil BASE_REFIT = new IntervalUtil(25.0F, 25.0F);
   private CombatFleetManagerAPI fleetManager;
   private CombatTaskManagerAPI ctm;
   private ShipAPI carrier;
   private ShipAPI ship;
   private Vector2f landingLocation;
   private boolean hasLanded = false;
   private int bayNo;
   private WeaponSlotAPI w;
   private float pptMax = 180.0F;
   private String timer = "0";
   private float HPPercent = 0.5F;
   private float CRmarker = 0.4F;
   private float CRrestored = 0.2F;
   private float CRmax = 1.0F;
   private float BaseTimer = 0.3F;
   private FighterLaunchBayAPI bay;

   public armaa_strikeCraftRepairTracker(ShipAPI var1, ShipAPI var2, Vector2f var3, int var4) {
      this.carrier = var2;
      this.ship = var1;
      this.landingLocation = var3;
      this.bayNo = var4;
      boolean var5 = var1.isAlly();
      this.fleetManager = Global.getCombatEngine().getFleetManager(var1.getOwner());
      this.ctm = this.fleetManager.getTaskManager(var5);
      Iterator var6 = var2.getHullSpec().getAllWeaponSlotsCopy().iterator();

      while(var6.hasNext()) {
         WeaponSlotAPI var7 = (WeaponSlotAPI)var6.next();
         if (var7.getWeaponType() == WeaponType.LAUNCH_BAY) {
            if (Global.getCombatEngine().getPlayerShip() == var1) {
            }

            this.w = var7;
            new Vector2f(var2.getLocation().x + var7.getLocation().y, var2.getLocation().y + var7.getLocation().x);
            if (Math.random() <= 0.5D) {
               break;
            }
         }
      }

   }

   public void lerp(float var1, float var2, float var3, float var4, float var5) {
      float var6 = Math.abs(var1 - var3);
      float var7 = Math.abs(var2 - var4);
      float var8 = var6 / var5;
      float var9 = var7 / var5;
      Vector2f var10 = this.ship.getLocation();
      this.ship.getLocation().setX(var10.getX() + var8);
      this.ship.getLocation().setY(var10.getY() + var9);
   }

   public void advance(float var1, List<InputEventAPI> var2) {
      if (!Global.getCombatEngine().isPaused()) {
         if (this.carrier == null || !this.carrier.isAlive() || !Global.getCombatEngine().isEntityInPlay(this.carrier)) {
            this.takeOff(this.ship, this.landingLocation, true);
            Global.getCombatEngine().getCustomData().remove("armaa_repairTracker_" + this.ship.getId());
            Global.getCombatEngine().removePlugin(this);
         }

         if (this.ship.isFinishedLanding() || this.hasLanded) {
            armaa_utils.setLocation(this.ship, this.landingLocation);
            ShipAPI var3 = Global.getCombatEngine().getPlayerShip();
            if (this.w != null) {
               this.landingLocation = new Vector2f(this.carrier.getLocation().x + this.w.getLocation().y, this.carrier.getLocation().y + this.w.getLocation().x);
               this.ship.setFacing(this.w.getAngle());
            } else {
               this.landingLocation = this.carrier.getLocation();
            }

            if (this.carrier != null) {
               if (this.fleetManager.getRetreatedCopy().contains(this.carrier.getFleetMember())) {
                  Global.getCombatEngine().getFleetManager(this.ship.getOwner()).getTaskManager(true).orderRetreat(this.fleetManager.getDeployedFleetMember(this.ship), false, false);
               }

               if (this.carrier.getHitpoints() <= 0.0F) {
                  this.ship.setHullSize(HullSize.FRIGATE);
                  armaa_utils.destroy(this.ship);
                  return;
               }
            }

            Global.getCombatEngine().getCustomData().put("armaa_hangarIsOpen" + this.carrier.getId() + "_" + this.bayNo, true);
            Iterator var4 = this.fleetManager.getRetreatedCopy().iterator();

            while(var4.hasNext()) {
               FleetMemberAPI var5 = (FleetMemberAPI)var4.next();
               if (this.fleetManager.getShipFor(var5) == this.carrier && this.fleetManager.getShipFor(var5) == this.carrier) {
                  this.fleetManager.addToReserves(this.ship.getFleetMember());
                  Global.getCombatEngine().removeEntity(this.ship);
               }
            }

            if (!this.hasLanded) {
               this.hasLanded = true;
               Global.getCombatEngine().getCustomData().put("armaa_hangarIsOpen" + this.carrier.getId() + "_" + this.bayNo, true);
            }

            float var21 = this.getCarrierRefitRate();
            if (this.carrier.getVariant().hasHullMod("armaa_serviceBays")) {
               var21 += 0.5F;
            }

            float var22 = 0.0F;
            if (Global.getCombatEngine().getCustomData().get("armaa_strikecraftTotalMalus" + this.ship.getId()) instanceof Float) {
               var22 = (Float)Global.getCombatEngine().getCustomData().get("armaa_strikecraftTotalMalus" + this.ship.getId());
            }

            float var6 = Math.max(this.ship.getHullLevel() * 1.5F, 1.0F);
            float var7 = var1 * var6 * (1.0F - var22) * var21;
            float var8 = Math.max(var7, var1 * this.BaseTimer);
            this.BASE_REFIT.advance(var8);
            float var9 = this.BASE_REFIT.getElapsed();
            float var10 = this.BASE_REFIT.getMaxInterval();
            float var11 = (float)Math.round(var9 / var10 * 100.0F);
            this.timer = String.valueOf(var11);
            float var12 = this.ship.getCurrentCR() / 1.0F;
            float var13 = (this.ship.getMaxHitpoints() - this.ship.getHitpoints()) * this.ship.getHullLevel() * var8 / var10 * var9;
            float var14 = (1.0F - this.ship.getCurrentCR()) * var12 * var8 / var10 * var9 / 10.0F;
            float var15 = armaa_utils.getArmorPercent(this.ship);
            float var16 = Math.min(this.ship.getCurrentCR() + var14 * var1, this.CRmax);
            this.ship.getMutableStats().getCRLossPerSecondPercent().modifyMult(this.ship.getId(), 0.001F);
            this.ship.getMutableStats().getHullDamageTakenMult().modifyMult("invincible", 0.0F);
            this.ship.getMutableStats().getArmorDamageTakenMult().modifyMult("invincible", 0.0F);
            this.ship.setHitpoints(Math.min(this.ship.getHitpoints() + var13 * (var9 / var10), this.ship.getMaxHitpoints()));
            armaa_utils.setArmorPercentage(this.ship, var15 + (1.0F - var15) * (var8 / var10) * var9 * var1);
            if (this.ship.getCurrentCR() <= this.CRmarker) {
               this.ship.setCurrentCR(var16);
            }

            ((FighterLaunchBayAPI)this.carrier.getLaunchBaysCopy().get(this.bayNo)).setCurrRate(((FighterLaunchBayAPI)this.carrier.getLaunchBaysCopy().get(this.bayNo)).getCurrRate() - var1 * var8);
            String var17 = "";
            if (this.ship == var3 && this.BASE_REFIT.getElapsed() >= 0.0F) {
               Global.getCombatEngine().maintainStatusForPlayerShip("AceSystem2", "graphics/ui/icons/icon_repair_refit.png", this.getBlinkyString("REPAIR STATUS"), this.ship.getHullLevel() * 100.0F + "%", true);
               var17 = Global.getSettings().getControlStringForEnumName("C2_TOGGLE_AUTOPILOT");
               Global.getCombatEngine().maintainStatusForPlayerShip("AceSystem", "graphics/ui/icons/icon_repair_refit.png", "RESTORING PPT/AMMO (PRESS " + var17 + ")to abort", this.timer + "%", true);
            }

            boolean var18 = Keyboard.isKeyDown(Keyboard.getKeyIndex(var17));
            if (this.BASE_REFIT.intervalElapsed() || var18) {
               this.takeOff(this.ship, this.landingLocation, var18);
               this.ship.setHullSize(HullSize.FRIGATE);
               this.ship.setShipSystemDisabled(false);
               this.ship.resetDefaultAI();
               this.ctm.orderSearchAndDestroy(this.fleetManager.getDeployedFleetMember(this.ship), false);
               Iterator var19 = this.ship.getWeaponGroupsCopy().iterator();

               while(var19.hasNext()) {
                  WeaponGroupAPI var20 = (WeaponGroupAPI)var19.next();
                  if (!var20.getActiveWeapon().usesAmmo()) {
                     var20.toggleOn();
                  }
               }

               Global.getCombatEngine().getCustomData().remove("armaa_repairTracker_" + this.ship.getId());
               Global.getCombatEngine().removePlugin(this);
            }

         }
      }
   }

   private float getCarrierRefitRate() {
      return this.carrier == null ? 1.0F : this.carrier.getSharedFighterReplacementRate();
   }

   private String getBlinkyString(String var1) {
      long var2 = (long)Math.floor((double)(Global.getCombatEngine().getTotalElapsedTime(true) / 0.2F));
      return var2 % 2L == 0L ? var1 + ": " : "";
   }

   private void takeOff(ShipAPI var1, Vector2f var2, boolean var3) {
      boolean var4 = false;
      Global.getSoundPlayer().playSound("fighter_takeoff", 1.0F, 1.0F, var1.getLocation(), new Vector2f());
      var1.getMutableStats().getHullDamageTakenMult().unmodify("invincible");
      var1.getMutableStats().getArmorDamageTakenMult().unmodify("invincible");
      var1.setInvalidTransferCommandTarget(false);
      var1.setCollisionClass(CollisionClass.SHIP);
      var1.getFluxTracker().stopOverload();
      var1.getFluxTracker().setCurrFlux(0.0F);
      Global.getCombatEngine().getCustomData().remove("armaa_strikecraftisLanding" + var1.getId());
      Global.getCombatEngine().getCustomData().put("armaa_strikecraft_hasWaypoint" + var1.getId(), false);
      Global.getCombatEngine().getCustomData().put("armaa_hangarIsOpen" + this.carrier.getId() + "_" + this.bayNo, false);
      armaa_utils.setLocation(var1, var2);
      if (!var3) {
         if (var1.getCurrentCR() <= this.CRmarker) {
            var1.setCurrentCR(this.CRmarker + 0.1F);
         }

         var1.clearDamageDecals();
         var1.setHitpoints(var1.getMaxHitpoints());
         this.pptMax = var1.getVariant().getHullSpec().getNoCRLossTime();
         if (var1.getMutableStats().getPeakCRDuration().computeEffective(0.0F) < var1.getTimeDeployedForCRReduction()) {
            var1.getMutableStats().getPeakCRDuration().modifyFlat(var1.getId(), var1.getTimeDeployedForCRReduction());
         }

         var1.clearDamageDecals();
         armaa_utils.setArmorPercentage(var1, 100.0F);
         List var5 = var1.getAllWeapons();
         Iterator var6 = var5.iterator();

         while(var6.hasNext()) {
            WeaponAPI var7 = (WeaponAPI)var6.next();
            if (var7.usesAmmo()) {
               var7.resetAmmo();
            }
         }
      }

      var1.setAnimatedLaunch();
      var1.setControlsLocked(false);
      var1.setShipSystemDisabled(false);
      var1.getMutableStats().getCRLossPerSecondPercent().unmodify(var1.getId());
      Iterator var8 = var1.getChildModulesCopy().iterator();

      while(var8.hasNext()) {
         ShipAPI var9 = (ShipAPI)var8.next();
         var9.setHitpoints(var9.getMaxHitpoints());
         var9.getFluxTracker().stopOverload();
         var9.getFluxTracker().setCurrFlux(0.0F);
         var9.clearDamageDecals();
         armaa_utils.setArmorPercentage(var9, 100.0F);
         var9.setAnimatedLaunch();
      }

      if (Global.getCombatEngine().getPlayerShip() == var1 && var1.getShipTarget() == this.carrier) {
         var1.setShipTarget((ShipAPI)null);
      }

   }
}
