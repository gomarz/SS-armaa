package data.scripts.campaign;

import com.fs.starfarer.api.campaign.BaseCampaignEventListenerAndScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;
import static com.fs.starfarer.api.util.Misc.random;

public class armaa_cassianListener extends BaseCampaignEventListenerAndScript {

    private long days;

    public armaa_cassianListener() {
        this.days = Global.getSector().getClock().getTimestamp();

    }

    @Override
    public void reportEconomyMonthEnd() {
        Global.getSector().getMemoryWithoutUpdate().set("$armaa_cassianReadyToChat", true);
        PersonAPI cassian = Global.getSector().getFaction("armaarmatura").createRandomPerson(FullName.Gender.MALE, random);
        //cassian.setPersonality(Personality.AGGRESSIVE);
        cassian.setVoice(Voices.ARISTO);
        cassian.setPortraitSprite("graphics/armaa/portraits/armaa_cassian.png");
        cassian.setId("armaa_cassian");
        cassian.getName().setFirst("Cassian");
        cassian.getName().setLast("Pha");
        cassian.setRankId(Ranks.HOUSE_LEADER_MALE);
        cassian.setPostId(Ranks.POST_UNKNOWN);
        Global.getSector().getImportantPeople().addPerson(cassian);
        Global.getSector().removeListener(this);
    }
}
