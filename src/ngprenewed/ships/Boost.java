package ngprenewed.ships;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;

/** The stats one Stat tuning item can raise. Each ship can take each boost once. */
public enum Boost {
    HULL("Hull integrity", "+10%"),
    ARMOR("Armor", "+10%"),
    FLUX_CAPACITY("Flux capacity", "+10%"),
    FLUX_DISSIPATION("Flux dissipation", "+10%"),
    SPEED("Top speed", "+10%"),
    RANGE("Ballistic and energy weapon range", "+10%"),
    PEAK("Peak performance time", "+20%"),
    UPKEEP("Supplies per month", "-10%");

    public final String label;
    public final String amount;

    Boost(String label, String amount) {
        this.label = label;
        this.amount = amount;
    }

    /** Variant tag that marks this boost on a ship. */
    public String tag() {
        return "ngp_boost_" + name().toLowerCase();
    }

    public static Boost fromTag(String tag) {
        for (Boost boost : values()) {
            if (boost.tag().equals(tag)) return boost;
        }
        return null;
    }

    public void apply(MutableShipStatsAPI stats, String id) {
        switch (this) {
            case HULL:
                stats.getHullBonus().modifyPercent(id, 10f);
                break;
            case ARMOR:
                stats.getArmorBonus().modifyPercent(id, 10f);
                break;
            case FLUX_CAPACITY:
                stats.getFluxCapacity().modifyPercent(id, 10f);
                break;
            case FLUX_DISSIPATION:
                stats.getFluxDissipation().modifyPercent(id, 10f);
                break;
            case SPEED:
                stats.getMaxSpeed().modifyPercent(id, 10f);
                break;
            case RANGE:
                stats.getBallisticWeaponRangeBonus().modifyPercent(id, 10f);
                stats.getEnergyWeaponRangeBonus().modifyPercent(id, 10f);
                break;
            case PEAK:
                stats.getPeakCRDuration().modifyPercent(id, 20f);
                break;
            case UPKEEP:
                stats.getSuppliesPerMonth().modifyMult(id, 0.9f);
                break;
            default:
                break;
        }
    }
}
