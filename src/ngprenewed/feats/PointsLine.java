package ngprenewed.feats;

import achievementsrenewed.api.AchievementSpec;
import achievementsrenewed.api.RewardText;

/**
 * The line Achievements - Renewed (0.5.0 and later) shows under every achievement: the career points it
 * pays. The same text as a feat's own reward, so a feat shows it once. Points before Rule Book's
 * multiplier, which belongs to the save, not the profile.
 */
public final class PointsLine implements RewardText {

    @Override
    public String rewardFor(AchievementSpec spec) {
        int points = Rewards.points(spec);
        return points > 0 ? "+" + points + " career points" : null;
    }
}
