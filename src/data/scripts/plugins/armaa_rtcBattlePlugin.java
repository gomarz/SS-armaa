package data.scripts.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;
import java.util.*;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;
import java.awt.Color;
import com.fs.starfarer.api.util.IntervalUtil;

// unused for now
public class armaa_rtcBattlePlugin extends BaseEveryFrameCombatPlugin
{
	// for atmo battle
	boolean warning = true;
    protected CombatEngineAPI engine;
	private IntervalUtil interval = new IntervalUtil(.025f, .05f);
	private boolean playedMusic = false;
	private boolean perfMode = false;
	private float bossStage = 1f, ratio = 0f, bgStage = 0f;
	private boolean spawnedBoss = false, spawnedCorpse = false;
	
	public Color shiftColor(Color start, Color end, float ratio)
	{
		Color intermediateColor = Color.WHITE;
        int steps = 100; // Number of steps in the transition
        long duration = 1500; // Duration of the transition in milliseconds		
		if(ratio >= 1)
			return end;
		
		int red = (int) (start.getRed() * (1 - ratio) + end.getRed() * ratio);
		int green = (int) (start.getGreen() * (1 - ratio) + end.getGreen() * ratio);
		int blue = (int) (start.getBlue() * (1 - ratio) + end.getBlue() * ratio);
		int alpha = (int) (start.getAlpha() * (1 - ratio) + end.getAlpha() * ratio);
		intermediateColor = new Color(red, green, blue, alpha);	
		
		return intermediateColor;
		
		
	}

    @Override
    public void advance(float amount, List<InputEventAPI> events)
    {
		if(!playedMusic)
		{	
			// doesn't work
			//Global.getCombatEngine().addEntity()
			Global.getSoundPlayer().playCustomMusic(1,1,"music_armaa_ax_bounty",true);
			playedMusic = true;
			if(engine.getFleetManager(0).getCurrStrength() < 100 && Global.getSector().getMemoryWithoutUpdate().get("$armaa_killedJeniusGuardian") == null)
			{
				ShipAPI ally = engine.getFleetManager(0).spawnShipOrWing("hyperion_Attack",new Vector2f(0,-5000),0,5f);
				ally.setName("ISS Rogue");
				engine.getCombatUI().addMessage(1,ally,Color.green,ally.getName(),Color.green,":",Color.white,"Commander, this is the ISS Rogue. We've been retasked to assist. Following your lead.");		
			}		
			engine.setDoNotEndCombat(true); 
		}
		if(engine == null)
			return;

		if(ratio >= 0.1f)
			MagicRender.screenspace(
				Global.getSettings().getSprite("misc", "armaa_atmo2"),
				MagicRender.positioning.CENTER, 
				new Vector2f(0,0), 
				new Vector2f(0,0), 
				new Vector2f(Global.getSettings().getScreenWidth()*(1.2f),Global.getSettings().getScreenWidth()*(1.2f)), 
				new Vector2f(0,0),
				0f, 
				0f, //spin 
				new Color(50,50,50,255), 
				false, 
				0f, 
				0f, 
				0f, 
				0f, 
				0f, 
				0f, 
				-1, 
				0f, 
				CombatEngineLayers.CLOUD_LAYER
			);															
	}

    @Override
    public void init(CombatEngineAPI engine)
    {
        this.engine = engine;
    }
}
