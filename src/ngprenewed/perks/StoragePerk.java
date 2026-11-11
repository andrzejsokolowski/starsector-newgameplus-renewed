package ngprenewed.perks;

import com.fs.starfarer.api.characters.ShipSkillEffect;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

/**
 * The Deep Holds perk: +10% cargo, fuel and crew capacity on every ship in the fleet. It is a hidden
 * skill on the player (data/characters/skills/ngp_storage_perk.skill), so it lasts for the game it
 * was bought in and the game applies it to every ship the same way it applies Bulk Transport.
 */
public class StoragePerk implements ShipSkillEffect {

    public static final String SKILL_ID = "ngp_storage_perk";
    public static final float PERCENT = 10f;

    @Override
    public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {
        stats.getCargoMod().modifyPercent(id, PERCENT);
        stats.getFuelMod().modifyPercent(id, PERCENT);
        stats.getMaxCrewMod().modifyPercent(id, PERCENT);
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
        stats.getCargoMod().unmodifyPercent(id);
        stats.getFuelMod().unmodifyPercent(id);
        stats.getMaxCrewMod().unmodifyPercent(id);
    }

    @Override
    public String getEffectDescription(float level) {
        return "+" + (int) PERCENT + "% cargo, fuel and crew capacity";
    }

    @Override
    public String getEffectPerLevelDescription() {
        return null;
    }

    @Override
    public ScopeDescription getScopeDescription() {
        return ScopeDescription.ALL_SHIPS;
    }
}
