package ngprenewed.feats;

import achievementsrenewed.api.AchievementScript;
import achievementsrenewed.api.AchievementSpec;
import com.fs.starfarer.api.Global;

/** The check behind a feat's own achievement: 1 once this game has done the feat, else 0. */
final class FeatAchievement implements AchievementScript {

    private final Feat feat;

    FeatAchievement(Feat feat) {
        this.feat = feat;
    }

    @Override
    public int count(AchievementSpec spec) {
        if (Global.getSector() == null) return 0;
        return Feats.achieved(feat) ? 1 : 0;
    }
}
