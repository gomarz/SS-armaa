package data.scripts.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyPlayerHostileActListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD.NexTempData;
import java.util.Map;

/**
 * Detects when a sat bomb occurs.
 * @author Histidine
 */
public class armaa_satBombListener implements ColonyPlayerHostileActListener {
	
	public static final boolean ALWAYS_RUN = true;
	
	public static void addIfNeeded() {
		// already added?
		if (Global.getSector().getListenerManager().hasListenerOfClass(armaa_satBombListener.class))
			return;
				
		Global.getSector().getListenerManager().addListener(new armaa_satBombListener(), true);
	}

	public void reportSaturationBombardmentFinished(
			InteractionDialogAPI dialog, final MarketAPI market, MarketCMD.TempData actionData) {
		
		Global.getLogger(this.getClass()).info("Sat bomb occured!");
		
		
		if (Global.getSettings().getModManager().isModEnabled("nexerelin") && actionData instanceof NexTempData) {
			NexTempData ntd = (NexTempData)actionData;
			if (ntd.satBombLimitedHatred) return;	// do nothing if the sat bomb didn't enrage everyone
		}
		
		if (actionData != null) {
			for (FactionAPI angery : actionData.willBecomeHostile) {
				// FIXME: Hivers will always be here since they care about atrocities
				if (angery.getId().equals("HIVER") || angery.getId().equals("tahlan_legioinfernalis"))
					return;
			}
		}
		
		Global.getSector().getMemoryWithoutUpdate().set("$armaa_dawnHeardOfAtrocities", true);
		Map<String, MemoryAPI> memoryMap = null;
		if (dialog != null) memoryMap = dialog.getPlugin().getMemoryMap();
	}	
	
	// unused
	public void reportRaidToDisruptFinished(InteractionDialogAPI dialog,
							MarketAPI market, MarketCMD.TempData actionData, Industry industry) {}
		
	public void reportRaidForValuablesFinishedBeforeCargoShown(InteractionDialogAPI dialog, 
								MarketAPI market, MarketCMD.TempData actionData,
								CargoAPI cargo) {}
	
	public void reportTacticalBombardmentFinished(InteractionDialogAPI dialog,
			MarketAPI market, MarketCMD.TempData actionData) {}
}
