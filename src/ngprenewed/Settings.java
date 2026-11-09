package ngprenewed;

import lunalib.lunaSettings.LunaSettings;

/** LunaLib settings, read live so a change applies at once. Every read falls back to the default. */
public final class Settings {

    public static final String MOD_ID = "ngp_renewed";

    private Settings() {
    }

    /** Points added on top of the earned career total (negative takes points away). */
    public static int bonusPoints() {
        try {
            Integer value = LunaSettings.getInt(MOD_ID, "ngp_bonusPoints");
            return value == null ? 0 : value;
        } catch (Throwable t) {
            return 0;
        }
    }

    public static int pointsPerLevel() {
        try {
            Integer value = LunaSettings.getInt(MOD_ID, "ngp_pointsPerLevel");
            return value == null ? 1 : Math.max(0, value);
        } catch (Throwable t) {
            return 1;
        }
    }

    public static double costMult() {
        try {
            Double value = LunaSettings.getDouble(MOD_ID, "ngp_costMult");
            return value == null ? 1.0 : Math.max(0.0, value);
        } catch (Throwable t) {
            return 1.0;
        }
    }
}
