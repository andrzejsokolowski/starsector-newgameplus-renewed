package ngprenewed;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import achievementsrenewed.api.Achievements;
import ngprenewed.feats.FeatPayer;
import ngprenewed.feats.Feats;
import ngprenewed.feats.Rewards;
import ngprenewed.feats.OmegaBattleListener;
import ngprenewed.store.StoreCatalog;

/**
 * Entry point. Loads the store catalogue at startup, and on every game load makes sure the save has
 * its state and the campaign script that pays level points and places the Ledger.
 */
public class NGPModPlugin extends BaseModPlugin {

    @Override
    public void onApplicationLoad() {
        StoreCatalog.load();
        Feats.load();
        ngprenewed.perks.Perks.showInSecondInCommand();
        Rewards.load();
    }

    @Override
    public void onGameLoad(boolean newGame) {
        Career.reload();
        if (SaveState.get() == null) SaveState.create();
        StoreCatalog.addColonyItems();
        if (!Global.getSector().hasTransientScript(CampaignScript.class)) {
            Global.getSector().addTransientScript(new CampaignScript());
        }
        Global.getSector().addTransientListener(new OmegaBattleListener());
        Achievements.addListener(new FeatPayer());
    }
}
