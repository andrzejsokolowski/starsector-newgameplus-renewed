package ngprenewed.feats;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

/**
 * The base game sets no flag for beating an Omega fleet, so this listener sets one: when a battle the
 * player is in ends with the player's side winning and an Omega fleet on the other side. The feats
 * file reads the flag like any other. Added on every load, never saved.
 */
public class OmegaBattleListener extends BaseCampaignEventListener {

    public static final String FLAG = "$ngp_defeatedOmega";

    public OmegaBattleListener() {
        super(false);
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI primaryWinner, BattleAPI battle) {
        try {
            if (battle == null || primaryWinner == null || !battle.isPlayerInvolved()) return;
            if (!battle.onPlayerSide(primaryWinner)) return;
            for (CampaignFleetAPI fleet : battle.getNonPlayerSideSnapshot()) {
                if (fleet != null && fleet.getFaction() != null && Factions.OMEGA.equals(fleet.getFaction().getId())) {
                    Global.getSector().getMemoryWithoutUpdate().set(FLAG, true);
                    return;
                }
            }
        } catch (Exception e) {
            Global.getLogger(OmegaBattleListener.class).warn("New Game Plus - Renewed: battle check failed.", e);
        }
    }
}
