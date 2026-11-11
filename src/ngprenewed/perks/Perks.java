package ngprenewed.perks;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.AfterShipCreationSkillEffect;
import com.fs.starfarer.api.characters.DescriptionSkillEffect;
import com.fs.starfarer.api.characters.FleetStatsSkillEffect;
import com.fs.starfarer.api.characters.ShipSkillEffect;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.listeners.HullDamageAboutToBeTakenListener;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.fleet.MutableFleetStatsAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;

/**
 * The perks bought at Legacy Station (0.8.0, Andrzej 2026-11-11). Each one is a hidden skill on the player
 * (data/characters/skills/ngp_perk_*.skill) under the aptitude ngp_perks, so it lasts for the game it was
 * bought in. Ship perks apply to every ship in the fleet the way Bulk Transport does; fleet perks change
 * the fleet's own stats.
 */
public final class Perks {

    public static final String APTITUDE = "ngp_perks";
    public static final String APTITUDE_SKILL = "aptitude_ngp_perks";
    private static final String SECOND_IN_COMMAND = "second_in_command";
    private static final String HIDDEN_TAG = "npc_only";
    private static final Logger log = Global.getLogger(Perks.class);

    private Perks() {
    }

    /**
     * Second-in-Command lists skills of other aptitudes on its Non-Executive Skills page, but leaves out
     * npc_only ones. With it enabled the perks drop that tag, so they show there; without it they stay
     * hidden, so the vanilla screen never offers them for skill points.
     */
    public static void showInSecondInCommand() {
        if (!Global.getSettings().getModManager().isModEnabled(SECOND_IN_COMMAND)) return;
        int shown = 0;
        for (String id : Global.getSettings().getSkillIds()) {
            SkillSpecAPI spec = Global.getSettings().getSkillSpec(id);
            if (spec == null || !APTITUDE.equals(spec.getGoverningAptitudeId())) continue;
            if (spec.getTags() != null) spec.getTags().remove(HIDDEN_TAG);
            if (!spec.hasTag(HIDDEN_TAG)) shown++;
        }
        log.info("New Game Plus - Renewed: " + shown + " perk skills shown in Second-in-Command.");
    }

    private abstract static class ShipPerk implements ShipSkillEffect {
        @Override
        public String getEffectPerLevelDescription() {
            return null;
        }

        @Override
        public ScopeDescription getScopeDescription() {
            return ScopeDescription.ALL_SHIPS;
        }
    }

    private abstract static class FleetPerk implements FleetStatsSkillEffect {
        @Override
        public String getEffectPerLevelDescription() {
            return null;
        }

        @Override
        public ScopeDescription getScopeDescription() {
            return ScopeDescription.FLEET;
        }
    }

    public static class TimeFlow extends ShipPerk {
        @Override
        public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {
            stats.getTimeMult().modifyMult(id, 1.01f);
        }

        @Override
        public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
            stats.getTimeMult().unmodifyMult(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "+1% time flow";
        }
    }

    public static class Armor extends ShipPerk {
        @Override
        public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {
            stats.getArmorBonus().modifyFlat(id, 50f);
        }

        @Override
        public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
            stats.getArmorBonus().unmodifyFlat(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "+50 armor";
        }
    }

    public static class Maintenance extends ShipPerk {
        @Override
        public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {
            stats.getSuppliesPerMonth().modifyMult(id, 0.9f);
        }

        @Override
        public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
            stats.getSuppliesPerMonth().unmodifyMult(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "-10% supplies per month";
        }
    }

    public static class Readiness extends ShipPerk {
        @Override
        public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {
            stats.getMaxCombatReadiness().modifyFlat(id, 0.05f, "Combat Readiness perk");
        }

        @Override
        public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
            stats.getMaxCombatReadiness().unmodifyFlat(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "+5% maximum combat readiness";
        }
    }

    /**
     * Once per battle, a hit that would destroy a ship is cancelled and the ship repairs 10% of its
     * maximum hull instead. The listener is made per ship per battle, so "once" resets every battle.
     */
    public static class LastStand extends ShipPerk implements AfterShipCreationSkillEffect {
        @Override
        public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {
        }

        @Override
        public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
        }

        @Override
        public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
            if (ship != null && !ship.hasListenerOfClass(LastStandListener.class)) {
                ship.addListener(new LastStandListener(ship));
            }
        }

        @Override
        public void unapplyEffectsAfterShipCreation(ShipAPI ship, String id) {
            if (ship != null) ship.removeListenerOfClass(LastStandListener.class);
        }

        @Override
        public String getEffectDescription(float level) {
            return "Once per battle, repairs 10% of hull instead of being destroyed";
        }
    }

    static final class LastStandListener implements HullDamageAboutToBeTakenListener {
        private final ShipAPI ship;
        private boolean used;

        LastStandListener(ShipAPI ship) {
            this.ship = ship;
        }

        @Override
        public boolean notifyAboutToTakeHullDamage(Object param, ShipAPI target, Vector2f point, float damageAmount) {
            if (used || target != ship || !ship.isAlive() || ship.isHulk()) return false;
            if (damageAmount < ship.getHitpoints()) return false;
            used = true;
            ship.setHitpoints(Math.min(ship.getMaxHitpoints(), ship.getHitpoints() + ship.getMaxHitpoints() * 0.1f));
            if (Global.getCombatEngine() != null) {
                Global.getCombatEngine().addFloatingText(ship.getLocation(), "Last Stand", 24f,
                        Misc.getPositiveHighlightColor(), ship, 0.5f, 1f);
            }
            return true;
        }
    }

    public static class SlowBurn extends FleetPerk {
        @Override
        public void apply(MutableFleetStatsAPI stats, String id, float level) {
            stats.getDynamic().getMod(Stats.MOVE_SLOW_SPEED_BONUS_MOD).modifyFlat(id, 1f);
        }

        @Override
        public void unapply(MutableFleetStatsAPI stats, String id) {
            stats.getDynamic().getMod(Stats.MOVE_SLOW_SPEED_BONUS_MOD).unmodifyFlat(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "+1 to the highest burn level that counts as moving slowly";
        }
    }

    public static class MaxBurn extends FleetPerk {
        @Override
        public void apply(MutableFleetStatsAPI stats, String id, float level) {
            stats.getFleetwideMaxBurnMod().modifyFlat(id, 1f, "Max Burn perk");
        }

        @Override
        public void unapply(MutableFleetStatsAPI stats, String id) {
            stats.getFleetwideMaxBurnMod().unmodifyFlat(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "+1 maximum burn level";
        }
    }

    public static class Sensors extends FleetPerk {
        @Override
        public void apply(MutableFleetStatsAPI stats, String id, float level) {
            stats.getSensorRangeMod().modifyPercent(id, 10f, "Sensor Range perk");
        }

        @Override
        public void unapply(MutableFleetStatsAPI stats, String id) {
            stats.getSensorRangeMod().unmodifyPercent(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "+10% sensor range";
        }
    }

    /** The text of the perks' aptitude, as Second-in-Command shows it. */
    public static class Aptitude implements DescriptionSkillEffect {
        @Override
        public String getString() {
            return "Perks bought at Legacy Station with career points. They last for this game.";
        }

        @Override
        public Color[] getHighlightColors() {
            return new Color[0];
        }

        @Override
        public String[] getHighlights() {
            return new String[0];
        }

        @Override
        public Color getTextColor() {
            return Misc.getTextColor();
        }
    }
}
