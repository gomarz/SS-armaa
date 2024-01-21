package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.WeaponAPI;
import java.util.Iterator;
import org.lazywizard.lazylib.MathUtils;

public class armaa_watchdogEffect implements EveryFrameWeaponEffectPlugin {
   private boolean runOnce = false;
   private WeaponAPI reference;
   private ShipAPI ship;
   private ShipwideAIFlags flags;
   private WeaponAPI armL;
   private WeaponAPI armR;
   private WeaponAPI shoulderR;
   private WeaponAPI head;
   private WeaponAPI headGlow;
   private WeaponAPI shoulderL;
   private WeaponAPI turretL;
   private WeaponAPI turretR;
   private WeaponAPI decoL;
   private WeaponAPI decoR;
   private float overlap = 0.0F;
   private float overlap2 = 0.0F;
   private float overlap3 = 0.0F;
   private float currentRotateL = 0.0F;
   private float currentRotateR = 0.0F;
   private final float TORSO_OFFSET = -45.0F;
   private final float LEFT_ARM_OFFSET = -75.0F;
   private final float RIGHT_ARM_OFFSET = -25.0F;
   private final float MAX_OVERLAP = 7.0F;
   private final float maxlegRotate = 22.5F;

   public void advance(float var1, CombatEngineAPI var2, WeaponAPI var3) {
      this.runOnce = true;
      this.ship = var3.getShip();
      this.flags = this.ship.getAIFlags();
      Iterator var4 = this.ship.getAllWeapons().iterator();

      while(var4.hasNext()) {
         WeaponAPI var5 = (WeaponAPI)var4.next();
         String var6 = var5.getSlot().getId();
         byte var7 = -1;
         switch(var6.hashCode()) {
         case -1726101283:
            if (var6.equals("WS0001")) {
               var7 = 5;
            }
            break;
         case -1726101281:
            if (var6.equals("WS0003")) {
               var7 = 4;
            }
            break;
         case -409432462:
            if (var6.equals("E_LSHOULDER")) {
               var7 = 3;
            }
            break;
         case 420912762:
            if (var6.equals("E_DECO_R")) {
               var7 = 7;
            }
            break;
         case 1630742648:
            if (var6.equals("E_RSHOULDER")) {
               var7 = 2;
            }
            break;
         case 2065240167:
            if (var6.equals("E_DECO")) {
               var7 = 6;
            }
            break;
         case 2065475114:
            if (var6.equals("E_LARM")) {
               var7 = 0;
            }
            break;
         case 2065653860:
            if (var6.equals("E_RARM")) {
               var7 = 1;
            }
         }

         switch(var7) {
         case 0:
            if (this.armL == null) {
               this.armL = var5;
            }
            break;
         case 1:
            if (this.armR == null) {
               this.armR = var5;
            }
            break;
         case 2:
            this.shoulderR = var5;
            break;
         case 3:
            this.shoulderL = var5;
            break;
         case 4:
            this.turretR = var5;
            break;
         case 5:
            if (this.turretL == null) {
               this.turretL = var5;
            }
            break;
         case 6:
            if (this.decoL == null) {
               this.decoL = var5;
            }
            break;
         case 7:
            this.decoR = var5;
         }
      }

      if (this.armL != null) {
         float var8 = 0.0F;
         float var9 = 0.0F;
         float var10 = this.ship.getFacing();
         MathUtils.getShortestRotation(var10, var3.getCurrAngle());
         var3.setCurrAngle(this.ship.getFacing() + MathUtils.getShortestRotation(this.ship.getFacing(), this.armL.getCurrAngle()) * 0.7F);
         this.shoulderR.setCurrAngle(this.ship.getFacing() + MathUtils.getShortestRotation(this.ship.getFacing(), this.armR.getCurrAngle()) * 0.7F);
         this.shoulderR.getSprite().setCenterY(this.armR.getBarrelSpriteAPI().getCenterY());
         if (this.decoL != null && this.turretL != null) {
            this.decoL.setCurrAngle(this.turretL.getCurrAngle());
         }

         if (this.decoR != null && this.turretR != null) {
            this.decoR.setCurrAngle(this.turretR.getCurrAngle());
         }

         var3.getSprite().setCenterY(this.armL.getBarrelSpriteAPI().getCenterY());
         this.ship.syncWeaponDecalsWithArmorDamage();
      }
   }
}
