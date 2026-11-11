package ngprenewed;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.Misc;

/** Pays career points for levels gained. Each save pays each level once. */
public final class LevelTracker {

    private LevelTracker() {
    }

    /** Pays for any level above the highest one this save was paid for. */
    public static void payPendingLevels(SaveState state) {
        int level = Global.getSector().getPlayerStats().getLevel();
        int paid = Career.levelsPaid(state.saveId());
        if (level <= paid) return;
        int points = Career.pay(state.saveId(), (level - paid) * (float) Settings.pointsPerLevel());
        Career.setLevelsPaid(state.saveId(), level);
        if (points > 0 && Global.getSector().getCampaignUI() != null) {
            Global.getSector().getCampaignUI().addMessage(
                    "Level up: +" + points + " career point" + (points == 1 ? "" : "s"),
                    Misc.getPositiveHighlightColor());
        }
    }
}
