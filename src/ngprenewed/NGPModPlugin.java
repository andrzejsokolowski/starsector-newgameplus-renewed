package ngprenewed;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import ngprenewed.store.StoreCatalog;

/**
 * Entry point. Loads the store catalogue at startup, and on every game load makes sure the save has
 * its state and the campaign script that pays level points and places the Ledger.
 */
public class NGPModPlugin extends BaseModPlugin {

    @Override
    public void onApplicationLoad() {
        StoreCatalog.load();
    }

    @Override
    public void onGameLoad(boolean newGame) {
        Career.reload();
        if (SaveState.get() == null) SaveState.create();
        if (!Global.getSector().hasTransientScript(CampaignScript.class)) {
            Global.getSector().addTransientScript(new CampaignScript());
        }
    }
}
