package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.FighterWingAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

public class armaa_AWACS implements EveryFrameWeaponEffectPlugin {
   private static final float[] COLOR_NORMAL = new float[]{1.0F, 0.39215687F, 0.078431375F};
   private static final float MAX_JITTER_DISTANCE = 0.2F;
   private static final float MAX_OPACITY = 1.0F;
   private static final float RANGE_BOOST = 100.0F;
   private static final float DAMAGE_MALUS = 0.8F;
   private static float EFFECT_RANGE = 2000.0F;
   public static final float ROTATION_SPEED = 5.0F;
   public static final Color COLOR = new Color(15, 215, 55, 200);
   private static final String AWACS_ID = "AWACS_ID";
   private float rotation = 0.0F;
   private float opacity = 0.0F;
   private SpriteAPI sprite = Global.getSettings().getSprite("misc", "eis_ceylonrad");
   private List<ShipAPI> targetList = new ArrayList();
   private List<ShipAPI> enemyList = new ArrayList();
   private static final EnumSet<WeaponType> WEAPON_TYPES;
   private IntervalUtil interval = new IntervalUtil(0.05F, 0.1F);

   public void advance(float var1, CombatEngineAPI var2, WeaponAPI var3) {
      if (var2 != null && var2.isUIShowingHUD() && !var2.isUIShowingDialog() && !var2.getCombatUI().isShowingCommandUI()) {
         ShipAPI var4 = var3.getShip();
         if (var4 != null) {
            float var5 = var4.getAllWings().isEmpty() ? 0.25F : Math.max(0.25F, (float)(((FighterWingAPI)var4.getAllWings().get(0)).getWingMembers().size() / ((FighterWingAPI)var4.getAllWings().get(0)).getSpec().getNumFighters()));
            if (!var4.getAllWings().isEmpty()) {
               Global.getCombatEngine().maintainStatusForPlayerShip("adrenal", "graphics/ui/icons/icon_search_and_destroy.png", "Wing Members", "" + ((FighterWingAPI)var4.getAllWings().get(0)).getWingMembers().size() / ((FighterWingAPI)var4.getAllWings().get(0)).getSpec().getNumFighters(), false);
            }

            boolean var6 = var4 == var2.getPlayerShip();
            if (!var4.getSystem().isActive() && var6 && !var4.isHulk() && !var4.isPiece() && var4.isAlive() && !var4.getSystem().isOutOfAmmo()) {
               this.opacity = Math.min(1.0F, this.opacity + 4.0F * var1);
            } else {
               this.opacity = Math.max(0.0F, this.opacity - 2.0F * var1);
            }

            Vector2f var7 = var4.getLocation();
            ViewportAPI var8 = Global.getCombatEngine().getViewport();
            if (var8.isNearViewport(var7, EFFECT_RANGE * var5)) {
               GL11.glPushAttrib(8192);
               GL11.glMatrixMode(5889);
               GL11.glPushMatrix();
               GL11.glViewport(0, 0, Display.getWidth(), Display.getHeight());
               GL11.glOrtho(0.0D, (double)Display.getWidth(), 0.0D, (double)Display.getHeight(), -1.0D, 1.0D);
               GL11.glEnable(3553);
               GL11.glEnable(3042);
               float var9 = Global.getSettings().getScreenScaleMult();
               float var10 = var4.getMutableStats().getSystemRangeBonus().computeEffective(EFFECT_RANGE * var5);
               if (var4.isFrigate() || var4.isDestroyer()) {
                  var10 = (float)((double)var10 * 0.75D);
               }

               float var11 = (var10 + var4.getCollisionRadius()) * 2.0F * var9 / var8.getViewMult();
               this.sprite.setSize(var11, var11);
               this.sprite.setColor(var4.getOwner() == 0 ? COLOR : new Color(215, 21, 16, 186));
               this.sprite.setAdditiveBlend();
               this.sprite.setAlphaMult(0.4F * this.opacity);
               this.sprite.renderAtCenter(var8.convertWorldXtoScreenX(var7.x) * var9, var8.convertWorldYtoScreenY(var7.y) * var9);
               this.sprite.setAngle(this.rotation);
               GL11.glPopMatrix();
               GL11.glPopAttrib();
            }

            if (!Global.getCombatEngine().isPaused()) {
               this.rotation += 5.0F * var1;
               if (this.rotation > 360.0F) {
                  this.rotation -= 360.0F;
               }

               Iterator var12 = CombatUtils.getShipsWithinRange(var4.getLocation(), EFFECT_RANGE * var5).iterator();

               while(true) {
                  while(var12.hasNext()) {
                     ShipAPI var14 = (ShipAPI)var12.next();
                     if (var14.getOwner() == var4.getOwner() && !this.targetList.contains(var14)) {
                        this.targetList.add(var14);
                     } else if (var14.getOwner() != var4.getOwner() && !var14.isHulk() && !this.enemyList.contains(var14)) {
                        this.enemyList.add(var14);
                     }
                  }

                  ArrayList var13 = new ArrayList();
                  Iterator var15 = this.targetList.iterator();

                  ShipAPI var16;
                  while(var15.hasNext()) {
                     var16 = (ShipAPI)var15.next();
                     if (MathUtils.getDistance(var16.getLocation(), var4.getLocation()) <= EFFECT_RANGE * var5) {
                        if (!var4.getVariant().hasHullMod("fourteenth")) {
                           var16.getMutableStats().getEnergyWeaponRangeBonus().modifyFlat("AWACS_ID", 100.0F);
                           var16.getMutableStats().getBallisticWeaponRangeBonus().modifyFlat("AWACS_ID", 100.0F);
                        } else {
                           var16.getMutableStats().getMaxSpeed().modifyPercent("AWACS_ID", 15.0F);
                        }

                        if (var4.getSystem() != null && var4.getSystem().isActive()) {
                        }

                        var16.setWeaponGlow(0.7F, COLOR, WEAPON_TYPES);
                     } else {
                        var16.getMutableStats().getEnergyWeaponRangeBonus().unmodify("AWACS_ID");
                        var16.getMutableStats().getBallisticWeaponRangeBonus().unmodify("AWACS_ID");
                        var16.getMutableStats().getAutofireAimAccuracy().unmodify("AWACS_ID");
                        var16.getMutableStats().getProjectileSpeedMult().unmodify("AWACS_ID");
                        var16.getMutableStats().getMaxSpeed().unmodify("AWACS_ID");
                        var16.setWeaponGlow(0.0F, COLOR, WEAPON_TYPES);
                        var13.add(var16);
                     }
                  }

                  if (var4.getSystem() != null && var4.getSystem().isActive()) {
                     var15 = CombatUtils.getMissilesWithinRange(var4.getLocation(), EFFECT_RANGE * var5).iterator();

                     while(var15.hasNext()) {
                        MissileAPI var17 = (MissileAPI)var15.next();
                        if (var17.getOwner() != var4.getOwner()) {
                           var17.getVelocity().setX(var17.getVelocity().x / 2.0F);
                           var17.getVelocity().setY(var17.getVelocity().y / 2.0F);
                        }
                     }

                     var15 = this.enemyList.iterator();

                     while(var15.hasNext()) {
                        var16 = (ShipAPI)var15.next();
                        if (MathUtils.getDistance(var16.getLocation(), var4.getLocation()) <= EFFECT_RANGE * var5) {
                           if (!var4.getVariant().hasHullMod("fourteenth")) {
                              var16.getMutableStats().getEnergyWeaponDamageMult().modifyMult("AWACS_ID", 0.8F);
                              var16.getMutableStats().getBallisticWeaponDamageMult().modifyMult("AWACS_ID", 0.8F);
                              var16.getMutableStats().getMissileWeaponDamageMult().modifyMult("AWACS_ID", 0.8F);
                           }

                           var16.setWeaponGlow(0.7F, COLOR, WEAPON_TYPES);
                        } else {
                           var16.getMutableStats().getEnergyWeaponDamageMult().unmodify("AWACS_ID");
                           var16.getMutableStats().getBallisticWeaponDamageMult().unmodify("AWACS_ID");
                           var16.getMutableStats().getMissileWeaponDamageMult().unmodify("AWACS_ID");
                           var13.add(var16);
                        }
                     }
                  }

                  var15 = var13.iterator();

                  while(var15.hasNext()) {
                     var16 = (ShipAPI)var15.next();
                     this.targetList.remove(var16);
                  }

                  return;
               }
            }
         }
      }
   }

   static {
      WEAPON_TYPES = EnumSet.of(WeaponType.MISSILE, WeaponType.BALLISTIC, WeaponType.ENERGY);
   }
}
