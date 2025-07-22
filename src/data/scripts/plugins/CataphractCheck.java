package data.scripts.plugins;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import java.util.List;
//import java.


public class CataphractCheck extends BaseModPlugin {
    boolean NewChange;
    boolean NewChange2;
  
    public class CataphractCheckMarket extends BaseCampaignEventListener {
        public CataphractCheckMarket() {
            super(false);
        }
        @Override
        public void reportPlayerOpenedMarket(MarketAPI market) {
                NewChange = false;
                CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();

				for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) 
				{
					List<String> wings = member.getVariant().getWings();
					ShipVariantAPI ship = member.getVariant();
					boolean hasMechs = false;
					//apparently the list isn't empty even when the carrier has no wings...
					if(!wings.isEmpty())
					for(int i = 0; i < wings.size();i++)
					{
						if(ship.getWing(i) == null)
							break;
						if(ship.getWing(i).getTags().contains("cataphract"))
						{
							hasMechs = true;
							if (!ship.hasHullMod("cataphractBonus")) 
							{
								ship.addMod("cataphractBonus");
								NewChange = true;
							}
						}											
					}
					
					if(!hasMechs)
					{
						
						ship.removeMod("cataphractBonus");
						//member.updateStats();
					}

					member.updateStats();
					
				}
 
                if (NewChange) {Global.getSoundPlayer().playUISound("ui_cargo_handweapons_drop", 1f, 0.5f);}
            }
        }
    }