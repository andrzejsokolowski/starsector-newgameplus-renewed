package ngprenewed;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.Set;

/** Keeps Legacy Station on the intel screen so it can always be found on the map. */
public class LedgerIntel extends BaseIntelPlugin {

    public static final String TAG = "Legacy Station";

    private final SectorEntityToken ledger;

    public LedgerIntel(SectorEntityToken ledger) {
        this.ledger = ledger;
        setImportant(true);
    }

    @Override
    public String getName() {
        return LedgerSpawner.NAME;
    }

    @Override
    public String getIcon() {
        return "graphics/icons/station_research.png";
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return ledger;
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(TAG);
        return tags;
    }

    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        Color title = getTitleColor(mode);
        info.addPara(getName(), title, 0f);
        SaveState state = SaveState.get();
        if (state != null) {
            info.addPara("%s career points to spend", 3f, getBulletColorForMode(mode),
                    Misc.getHighlightColor(), String.valueOf(state.budget()));
        }
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        Color h = Misc.getHighlightColor();
        info.addPara("An automated exchange that settles debts no one remembers incurring. It knows your name, "
                + "and it knows every captain you have ever been.", 10f);
        if (ledger != null && ledger.getContainingLocation() != null) {
            info.addPara("It waits in %s.", 10f, h, ledger.getContainingLocation().getNameWithLowercaseType());
        }
        SaveState state = SaveState.get();
        if (state != null) {
            info.addPara("Points to spend: %s of %s.", 10f, h,
                    String.valueOf(state.budget()), String.valueOf(state.startingBudget()));
            info.addPara("Career points across all your lives: %s.", 3f, h, String.valueOf(Career.total()));
        }
    }

    @Override
    public boolean shouldRemoveIntel() {
        return ledger == null || !ledger.isAlive();
    }
}
