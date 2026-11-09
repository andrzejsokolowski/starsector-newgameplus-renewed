# New Game Plus - Renewed

These instructions apply to this repository, including when it is opened without the parent mod workspace.
The workspace `AGENTS.md` one folder up still governs releases, packaging, Git identity and communication.

## Notion

Tasks, history and test lists: the **New Game Plus - Renewed board** in Andrzej's Notion, under
**Starsector mods** (`collection://7476da3a-5800-4de4-a36c-4a13c7ba0857`). Ticket ids start with NGP.
Read the board with `notion-query-data-sources` mode `view` on `view://41320ca7-5d20-81eb-b035-000ceab4a4b8`.
The design and every decision Andrzej made: Claude Information page "New Game Plus - Renewed: design"
(`41320ca75d2081f480a5c920fc677241`). Sister mod: Rule Book - Renewed (board RBR).

## Licence and clean room

No code, text or art from Unofficial New Game Plus (Originem, all rights reserved). This mod is
written from scratch. StarterPack (Andrzej's own mod) may be reused freely.

## Build

`./gradlew releaseZip` compiles against the game and LunaLib (`gradle.properties` names the game
folder), runs the checks and writes `NewGamePlusRenewed.zip` in this folder.

## Layout

- `Career` - lifetime career points in `saves/common/ngp_renewed/career.json`. Never spent. Per save:
  highest level paid, points earned, fraction carried. `pay()` applies Rule Book - Renewed's multiplier
  from sector memory `$rbr_ngpMultiplier` (float, default 1).
- `SaveState` - per save, a plain map in sector persistent data `ngp_renewed_state`: save id, budget
  snapshot (`budgetBase` = career earned when the save got its state, so only other saves' points
  count), points spent, purchases per item, delivery bay cargo, the Ledger entity.
- `CampaignScript` (transient, real-time 1 s) - pays levels (`LevelTracker`) and places the Ledger
  (`LedgerSpawner`) once no dialog is open.
- `data/campaign/ngp_store_items.csv` - every store item. Kinds: story_points, skill_points, levels,
  credits, commodity, special_item, perk (a hidden player skill). `stock` -1 = no limit per game.
  The Armoury tab (kind weapon_pack) is built in code from the loaded weapons, not the CSV. Cargo goes to the delivery bay and
  reaches the player through a loot screen (never straight into cargo).
- `store/StorePanel` - the store screen. All buttons in ONE tooltip element covering the whole panel
  at inTL(0,0) (offset elements never get clicks). Row text in separate button-free elements.
- `ships/` - the Shipyard items (special items with a right-click dialog). S-mod slots and stat boosts
  are variant tags read by the permanent hull mod `ngp_ledger_refit`; extra OP is the permanent hull
  mod `ngp_ledger_rails` with negative OP cost. `SaveState` records each ship's work by member id and
  `ShipItems.sync` puts it back if a refit drops it.
- `perks/StoragePerk` - the +10% capacity perk, a hidden `npc_only` skill on the player.
- `rules.csv` hands the Ledger's dialog to `LedgerDialog` via `NGP_OpenLedger`.
