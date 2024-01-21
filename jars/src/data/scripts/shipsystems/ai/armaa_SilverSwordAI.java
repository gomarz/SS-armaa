package data.scripts.shipsystems.ai;

import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAIConfig;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import data.scripts.shipsystems.armaa_SilverSysStats;
import data.scripts.util.StolenUtils;
import java.util.Iterator;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

public class armaa_SilverSwordAI implements ShipSystemAIScript {
   private static final float HYSTERESIS_TIME_ACTIVE = 2.0F;
   private static final float HYSTERESIS_TIME = 4.0F;
   private static final float RE_EVAL_TIME = 1.0F;
   private CombatEngineAPI engine;
   private ShipwideAIFlags flags;
   private ShipAPI ship;
   private ShipSystemAPI system;
   private float maxDesire = 0.0F;
   private static final boolean DEBUG = false;
   private final Object STATUSKEY1 = new Object();
   private final Object STATUSKEY2 = new Object();
   private final Object STATUSKEY3 = new Object();
   private final Object STATUSKEY4 = new Object();
   private final Object STATUSKEY5 = new Object();
   private float desireShow = 0.0F;
   private float unfilteredDesire = 0.0F;
   private float targetDesireShow = 0.0F;
   private float reEvalTimer = 0.0F;
   private boolean forceTimidAI = false;
   private boolean forceAggressiveAI = false;
   private final ShipAIConfig savedConfig = new ShipAIConfig();

   public void advance(float var1, Vector2f var2, Vector2f var3, ShipAPI var4) {
      if (this.engine != null) {
         if (!this.engine.isPaused()) {
            if (!this.system.isActive() || this.system.isStateActive()) {
               float var5 = 0.0F;
               float var6 = armaa_SilverSysStats.getGauge(this.ship);
               float var7 = 1.0F;
               boolean var8 = false;
               boolean var9;
               boolean var10;
               if (this.system.isActive()) {
                  var9 = var6 < 0.25F;
                  var10 = var6 >= 0.5F;
               } else {
                  var9 = var6 < 0.5F;
                  var10 = var6 >= 0.75F;
               }

               boolean var11 = false;
               boolean var12 = false;
               boolean var13 = false;
               float var14 = this.ship.getFluxLevel();
               float var15 = this.ship.getHardFluxLevel();
               if (this.system.isActive()) {
                  var14 *= StolenUtils.lerp(1.0F, 1.5F, this.system.getEffectLevel() * var7);
                  var15 *= StolenUtils.lerp(1.0F, 1.5F, this.system.getEffectLevel() * var7);
               }

               if (var14 >= 0.7F) {
                  var11 = true;
               }

               if (var15 >= 0.5F) {
                  if (var11) {
                     var12 = true;
                  } else {
                     var11 = true;
                  }
               }

               if (var14 >= 0.9F) {
                  var12 = true;
               }

               if (var15 >= 0.7F) {
                  if (var12) {
                     var13 = true;
                  } else {
                     var12 = true;
                  }
               }

               if (var14 >= 1.1F) {
                  var13 = true;
               }

               if (var15 >= 0.9F) {
                  var13 = true;
               }

               boolean var16 = false;
               if (this.ship.getHullLevel() <= 0.33333334F) {
                  var16 = true;
               }

               if (this.ship.getCurrentCR() <= 0.2F) {
                  var16 = true;
               }

               float var17 = 1000.0F;
               Iterator var18 = this.ship.getUsableWeapons().iterator();

               while(var18.hasNext()) {
                  WeaponAPI var19 = (WeaponAPI)var18.next();
                  if (var19.getType() != WeaponType.MISSILE && var19.getRange() > var17) {
                     var17 = var19.getRange();
                  }
               }

               AssignmentInfo var26 = this.engine.getFleetManager(this.ship.getOwner()).getTaskManager(this.ship.isAlly()).getAssignmentFor(this.ship);
               Vector2f var27;
               if (var26 != null && var26.getTarget() != null && var26.getType() != CombatAssignmentType.AVOID) {
                  var27 = var26.getTarget().getLocation();
               } else {
                  var27 = null;
               }

               Object var20;
               if (this.flags.getCustom(AIFlags.MANEUVER_TARGET) instanceof CombatEntityAPI) {
                  var20 = (CombatEntityAPI)this.flags.getCustom(AIFlags.MANEUVER_TARGET);
               } else {
                  var20 = this.ship.getShipTarget();
               }

               if (this.flags.hasFlag(AIFlags.RUN_QUICKLY)) {
                  if (var9 && !var16) {
                     var5 += 0.75F;
                  } else {
                     ++var5;
                  }
               } else if (this.flags.hasFlag(AIFlags.BACKING_OFF)) {
                  if (var9 && !var16) {
                     var5 += 0.5F;
                  } else {
                     ++var5;
                  }
               }

               if (this.flags.hasFlag(AIFlags.DO_NOT_PURSUE) || this.flags.hasFlag(AIFlags.DO_NOT_BACK_OFF) && !this.flags.hasFlag(AIFlags.PURSUING)) {
                  if (var9) {
                     var5 -= 0.5F;
                  } else {
                     var5 -= 0.25F;
                  }
               }

               if (this.flags.hasFlag(AIFlags.PURSUING) && !var9) {
                  var5 += 0.25F;
               }

               if (this.flags.hasFlag(AIFlags.HARASS_MOVE_IN)) {
                  var5 += 0.5F;
               }

               if (this.flags.hasFlag(AIFlags.NEEDS_HELP)) {
                  if (var8 && !var16) {
                     ++var5;
                  } else {
                     var5 += 2.0F;
                  }
               }

               if (this.flags.hasFlag(AIFlags.HAS_INCOMING_DAMAGE) && this.flags.hasFlag(AIFlags.IN_CRITICAL_DPS_DANGER)) {
                  if (var12) {
                     if (var16) {
                        var5 += 2.0F;
                     } else if (!var8) {
                        ++var5;
                     }
                  } else if (var11) {
                     if (var16) {
                        ++var5;
                     } else if (!var8) {
                        var5 += 0.5F;
                     }
                  } else if (var16) {
                     var5 += 0.5F;
                  } else if (!var8) {
                     var5 += 0.25F;
                  }
               } else if (this.flags.hasFlag(AIFlags.HAS_INCOMING_DAMAGE) || this.flags.hasFlag(AIFlags.IN_CRITICAL_DPS_DANGER)) {
                  if (var12) {
                     if (var16) {
                        ++var5;
                     } else if (!var8) {
                        var5 += 0.75F;
                     }
                  } else if (var11) {
                     if (var16) {
                        var5 += 0.5F;
                     } else if (!var8) {
                        var5 += 0.25F;
                     }
                  } else if (var16) {
                     var5 += 0.25F;
                  }
               }

               if (this.flags.hasFlag(AIFlags.TURN_QUICKLY)) {
                  var5 += 0.75F;
               }

               boolean var21 = false;
               if (var20 != null && MathUtils.getDistance((CombatEntityAPI)var20, this.ship) < var17 - this.ship.getCollisionRadius()) {
                  var21 = true;
               }

               if (var20 != null && !var21 && !var11 && var10 && !var16) {
                  var5 += 0.5F;
               }

               float var22 = 500.0F;
               if (var26 != null && (var26.getType() == CombatAssignmentType.ENGAGE || var26.getType() == CombatAssignmentType.HARASS || var26.getType() == CombatAssignmentType.INTERCEPT || var26.getType() == CombatAssignmentType.LIGHT_ESCORT || var26.getType() == CombatAssignmentType.MEDIUM_ESCORT || var26.getType() == CombatAssignmentType.HEAVY_ESCORT || var26.getType() == CombatAssignmentType.STRIKE)) {
                  var22 = var17;
               }

               if (var27 != null && MathUtils.getDistance(var27, this.ship.getLocation()) >= var22 && !var21) {
                  if (var20 != null && MathUtils.getDistance((CombatEntityAPI)var20, var27) <= var17) {
                     if (!var11 && var10 && !var16) {
                        var5 += 0.5F;
                        if (this.system.isActive()) {
                           var5 += 0.25F;
                        }
                     }
                  } else if (var20 != null) {
                     if (!var11 && var10 && !var16) {
                        var5 += 0.25F;
                        if (this.system.isActive()) {
                           var5 += 0.25F;
                        }
                     }
                  } else if (!var11 && var10 && !var16) {
                     var5 += 0.75F;
                     if (this.system.isActive()) {
                        var5 += 0.5F;
                     }
                  }
               } else if (this.flags.hasFlag(AIFlags.SAFE_VENT)) {
                  var5 -= 0.5F;
               }

               if (var26 != null && var26.getType() == CombatAssignmentType.RETREAT && (!var8 || var16)) {
                  ++var5;
               }

               if (var13) {
                  var5 *= 1.3F;
                  if (this.system.isActive() && var7 > 1.0F) {
                     var5 += 1.5F * ((float)Math.sqrt((double)(var7 - 1.0F)) + 1.0F);
                  } else {
                     ++var5;
                  }
               } else if (var12) {
                  var5 *= 1.2F;
                  if (this.system.isActive() && var7 > 1.0F) {
                     var5 += 0.75F * ((float)Math.sqrt((double)(var7 - 1.0F)) + 1.0F);
                  } else {
                     var5 += 0.5F;
                  }
               } else if (var11) {
                  var5 *= 1.1F;
                  if (this.system.isActive() && var7 > 1.0F) {
                     var5 += 0.375F * ((float)Math.sqrt((double)(var7 - 1.0F)) + 1.0F);
                  } else {
                     var5 += 0.25F;
                  }
               }

               if (var5 > this.maxDesire) {
                  this.maxDesire = var5;
               } else if (this.system.isActive()) {
                  this.maxDesire -= var1 / 2.0F * (this.maxDesire - var5);
               } else {
                  this.maxDesire -= var1 / 4.0F * (this.maxDesire - var5);
               }

               float var24;
               float var25;
               if (this.system.isActive()) {
                  if (var8) {
                     var25 = 3.0F;
                  } else {
                     var25 = 1.0F;
                  }

                  if (var16) {
                     var24 = 0.25F + (1.0F - var6) * var7 * var25;
                  } else {
                     var24 = 0.5F + (1.0F - var6) * var7 * var25;
                  }
               } else {
                  if (var8) {
                     var25 = 2.0F;
                  } else {
                     var25 = 1.0F;
                  }

                  if (var16) {
                     var24 = 0.375F + (1.0F - var6) * var7 * var25;
                  } else {
                     var24 = 0.75F + (1.0F - var6) * var7 * var25;
                  }
               }

               if (!this.ship.getFluxTracker().isOverloadedOrVenting() && armaa_SilverSysStats.isUsable(this.ship, this.system)) {
                  if (this.system.isActive()) {
                     if (this.maxDesire < var24) {
                        this.ship.useSystem();
                     }
                  } else if (this.maxDesire >= var24) {
                     this.ship.useSystem();
                  }
               }

               if (this.reEvalTimer > 0.0F) {
                  this.reEvalTimer -= var1;
               }

               if (var9 && (var12 || var11 && (this.flags.hasFlag(AIFlags.NEEDS_HELP) || var16))) {
                  if (this.forceAggressiveAI) {
                     this.forceAggressiveAI = false;
                     this.reEvalTimer = 0.0F;
                     this.restoreAIConfig(this.ship);
                  }

                  this.flags.setFlag(AIFlags.BACK_OFF, 0.2F);
                  this.flags.setFlag(AIFlags.DO_NOT_PURSUE, 0.2F);
                  this.flags.setFlag(AIFlags.DO_NOT_USE_FLUX, 0.2F);
                  this.flags.setFlag(AIFlags.DO_NOT_AUTOFIRE_NON_ESSENTIAL_GROUPS, 0.2F);
                  this.flags.unsetFlag(AIFlags.DO_NOT_BACK_OFF);
                  if (this.reEvalTimer <= 0.0F && !this.forceTimidAI) {
                     this.forceTimidAI = true;
                     this.reEvalTimer = 1.0F;
                     this.saveAIConfig(this.ship);
                     this.ship.getShipAI().getConfig().backingOffWhileNotVentingAllowed = true;
                     this.ship.getShipAI().getConfig().personalityOverride = StolenUtils.getLessAggressivePersonality(CombatUtils.getFleetMember(this.ship), this.ship);
                     this.ship.getShipAI().forceCircumstanceEvaluation();
                     this.ship.getShipAI().cancelCurrentManeuver();
                  }
               } else if (this.reEvalTimer <= 0.0F && this.forceTimidAI) {
                  this.forceTimidAI = false;
                  this.reEvalTimer = 1.0F;
                  this.flags.unsetFlag(AIFlags.BACK_OFF);
                  this.flags.unsetFlag(AIFlags.DO_NOT_PURSUE);
                  this.flags.unsetFlag(AIFlags.DO_NOT_USE_FLUX);
                  this.flags.unsetFlag(AIFlags.DO_NOT_AUTOFIRE_NON_ESSENTIAL_GROUPS);
                  this.restoreAIConfig(this.ship);
                  this.ship.getShipAI().forceCircumstanceEvaluation();
               }

               if (var10 && !var11 && !var16) {
                  if (this.forceTimidAI) {
                     this.forceTimidAI = false;
                     this.reEvalTimer = 0.0F;
                     this.restoreAIConfig(this.ship);
                  }

                  this.flags.setFlag(AIFlags.DO_NOT_BACK_OFF, 0.2F);
                  this.flags.unsetFlag(AIFlags.BACK_OFF);
                  this.flags.unsetFlag(AIFlags.DO_NOT_PURSUE);
                  this.flags.unsetFlag(AIFlags.DO_NOT_USE_FLUX);
                  this.flags.unsetFlag(AIFlags.DO_NOT_AUTOFIRE_NON_ESSENTIAL_GROUPS);
                  if (this.reEvalTimer <= 0.0F && !this.forceAggressiveAI) {
                     this.forceAggressiveAI = true;
                     this.reEvalTimer = 1.0F;
                     this.saveAIConfig(this.ship);
                     this.ship.getShipAI().getConfig().backingOffWhileNotVentingAllowed = false;
                     this.ship.getShipAI().getConfig().personalityOverride = StolenUtils.getMoreAggressivePersonality(CombatUtils.getFleetMember(this.ship), this.ship);
                     this.ship.getShipAI().forceCircumstanceEvaluation();
                  }
               } else if (this.reEvalTimer <= 0.0F && this.forceAggressiveAI) {
                  this.forceAggressiveAI = false;
                  this.reEvalTimer = 1.0F;
                  this.flags.unsetFlag(AIFlags.DO_NOT_BACK_OFF);
                  this.restoreAIConfig(this.ship);
                  this.ship.getShipAI().forceCircumstanceEvaluation();
                  this.ship.getShipAI().cancelCurrentManeuver();
               }
            }

         }
      }
   }

   public void init(ShipAPI var1, ShipSystemAPI var2, ShipwideAIFlags var3, CombatEngineAPI var4) {
      this.ship = var1;
      this.flags = var3;
      this.system = var2;
      this.engine = var4;
   }

   private void saveAIConfig(ShipAPI var1) {
      if (var1.getShipAI().getConfig() != null) {
         this.savedConfig.backingOffWhileNotVentingAllowed = var1.getShipAI().getConfig().backingOffWhileNotVentingAllowed;
         this.savedConfig.personalityOverride = var1.getShipAI().getConfig().personalityOverride;
      }

   }

   private void restoreAIConfig(ShipAPI var1) {
      if (var1.getShipAI().getConfig() != null) {
         var1.getShipAI().getConfig().backingOffWhileNotVentingAllowed = this.savedConfig.backingOffWhileNotVentingAllowed;
         var1.getShipAI().getConfig().personalityOverride = this.savedConfig.personalityOverride;
      }

   }
}
