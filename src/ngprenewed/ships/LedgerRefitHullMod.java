package ngprenewed.ships;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * The permanent, unremovable hull mod that carries the Ledger's ship work: extra S-mod slots and stat
 * boosts. What a ship has is written in its variant tags (see {@link ShipItems}), so the hull mod
 * itself holds no data and works in the refit screen, in combat and in the simulator alike.
 */
public class LedgerRefitHullMod extends BaseHullMod {

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        ShipVariantAPI variant = stats.getVariant();
        if (variant == null) return;
        int slots = ShipItems.extraSModSlots(variant);
        if (slots > 0) stats.getDynamic().getMod(Stats.MAX_PERMANENT_HULLMODS_MOD).modifyFlat(id, slots);
        for (Boost boost : ShipItems.boosts(variant)) boost.apply(stats, id);
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return true;
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width,
                                          boolean isForModSpec) {
        if (ship == null || ship.getVariant() == null) return;
        ShipVariantAPI variant = ship.getVariant();
        float pad = 10f;
        int slots = ShipItems.extraSModSlots(variant);
        if (slots > 0) {
            tooltip.addPara("S-mod slots: %s", pad, Misc.getHighlightColor(), "+" + slots);
            pad = 3f;
        }
        for (Boost boost : ShipItems.boosts(variant)) {
            tooltip.addPara(boost.label + ": %s", pad, Misc.getHighlightColor(), boost.amount);
            pad = 3f;
        }
    }
}
