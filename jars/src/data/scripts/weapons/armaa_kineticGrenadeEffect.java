package data.scripts.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.util.vector.Vector2f;

public class armaa_kineticGrenadeEffect extends BaseEveryFrameCombatPlugin {
   private final float DAMPING = 0.1F;
   private CombatEngineAPI engine;
   private final DamagingProjectileAPI proj;
   private CombatEntityAPI target;
   private Vector2f lead = new Vector2f();
   private Vector2f ZERO = new Vector2f();
   private boolean launch = true;
   private IntervalUtil trailtimer = new IntervalUtil(0.15F, 0.15F);
   private IntervalUtil timer = new IntervalUtil(0.2F, 0.3F);
   private IntervalUtil interval = new IntervalUtil(0.015F, 0.025F);
   private IntervalUtil interval2 = new IntervalUtil(0.039F, 0.039F);
   protected List<List> trailOfTrails;
   protected List<armaa_curvyLaserAI> trails;
   private static final Color MUZZLE_FLASH_COLOR = new Color(200, 100, 255, 50);
   private static final Color MUZZLE_FLASH_COLOR_ALT = new Color(255, 255, 255, 100);
   private static final Color MUZZLE_FLASH_COLOR_GLOW = new Color(255, 75, 255, 50);
   private static final float MUZZLE_FLASH_DURATION = 0.1F;
   private static final float MUZZLE_FLASH_SIZE = 3.0F;
   private boolean inRange = false;
   private int count = 0;
   private int total = 0;
   private int beamLength = 4;
   private int beamNo = 0;
   private float angle = 0.0F;
   private float angleIncrease = 0.0F;
   private int side = 1;
   private String targetPointKey;
   private static final float CONE_ANGLE = 180.0F;
   private static final float A_2 = 90.0F;
   private List<DamagingProjectileAPI> alreadyRegisteredProjectiles = new ArrayList();

   public armaa_kineticGrenadeEffect(DamagingProjectileAPI var1, ShipAPI var2) {
      this.proj = var1;
      WeaponAPI var3 = var1.getWeapon();
      this.engine = Global.getCombatEngine();
   }

   public void advance(float var1, List<InputEventAPI> var2) {
      if (!this.engine.isPaused()) {
         if (Math.random() > 0.75D) {
            this.engine.spawnExplosion(this.proj.getLocation(), this.proj.getVelocity(), MUZZLE_FLASH_COLOR_ALT, 0.6F, 0.1F);
         } else {
            this.engine.spawnExplosion(this.proj.getLocation(), this.proj.getVelocity(), MUZZLE_FLASH_COLOR, 6.0F, 0.1F);
         }

         this.engine.addSmoothParticle(this.proj.getLocation(), this.proj.getVelocity(), 18.0F, 1.0F, 0.2F, MUZZLE_FLASH_COLOR_GLOW);
      }

      if (this.proj == null || this.proj.didDamage() || this.proj.isFading() || !Global.getCombatEngine().isEntityInPlay(this.proj)) {
         Global.getCombatEngine().removePlugin(this);
      }
   }
}
