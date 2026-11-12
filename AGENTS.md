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

Unofficial New Game Plus (Originem): Andrzej got the author's yes to continue its work and said on
2026-11-10 that its code and art may be used here too. Its licence still reads "DO NOT copy entirely.
All rights reserved", so take only parts, credit Originem in the file and in CREDITS, and change the
CC0 licence line of the forum post for anything taken. Nothing of it is used so far. StarterPack
(Andrzej's own mod) may be reused freely.

## Build

`./gradlew releaseZip` compiles against the game and LunaLib (`gradle.properties` names the game
folder), runs the checks and writes `NewGamePlusRenewed.zip` in this folder.

## Layout

- `Career` - lifetime career points in `saves/common/ngp_renewed/career.json`. Never spent. Per save:
  highest level paid, points earned, fraction carried. `pay()` applies Rule Book - Renewed's multiplier
  from sector memory `$rbr_ngpMultiplier` (float, default 1).
- `SaveState` - per save, a plain map in sector persistent data `ngp_renewed_state`: save id,
  points spent (the budget is `Career.total()` minus that, so points earned in this game count at once:
  0.7.5, Andrzej saw 0 to spend with 167 career points; `budgetBase` is still written but unused), purchases per item, delivery bay cargo, the Ledger entity.
- `CampaignScript` (transient, real-time 1 s) - pays levels (`LevelTracker`) and places the Ledger
  (`LedgerSpawner`) once no dialog is open.
- `data/campaign/ngp_store_items.csv` - every store item. Kinds: story_points, skill_points, levels,
  credits, commodity, special_item, perk (a hidden player skill). `stock` -1 = no limit per game.
  The Armory tab (kind weapon_pack, one per faction) is built in code from the loaded factions, not the CSV.
  Every colony item (an id in `ItemEffectsRepo.ITEM_EFFECTS` or tag `colony_item`: vanilla, AoTD Tenebrian,
  UAF Auroran...) is added to Rare by `StoreCatalog.addColonyItems` on every game load, 1 per game, price
  10 + base value / 25000, 10 to 60. Item descriptions say plainly what the item does: no flavor lines
  (Andrzej, 2026-11-11: "PLEASE REMOVE EVERY FLUFF TEXT"). American spelling.
  Kind peerless_point goes through `compat/PeerlessBridge` (Peerless Skills API, compileOnly). Cargo goes to the delivery bay and
  reaches the player through a loot screen (never straight into cargo).
- `feats/Feats` - `data/campaign/ngp_feats.csv` (id, name, points, requiresMod, memory sector|player,
  flag, check flag|equals|skill, value). Flag may join keys with & (all) or | (any). getBoolean also
  reads the text "true" that rules.csv stores. Paid once per PROFILE: `Career.payFeat` marks `feats.<id>` in career.json in the same write.
  `OmegaBattleListener` sets `$ngp_defeatedOmega` (the game has no flag for it).
  Every feat is an achievement in Achievements - Renewed (required since 0.6.0), registered from
  code in onApplicationLoad as `ngp_<feat id>` with `FeatAchievement` as its check. Column `shared`
  names another mod's achievement with the same condition (Rule Book's `rbr_atg` and so on); when it
  exists the feat registers none of its own. Feats sit in their mod's tab (`tab` = mod name) or the
  `vanilla` tab, never an NGP tab.
  Feat names and hints are Andrzej's own (0.8.3, from his Achievements - Renewed 0.5.1 review), many
  of them jokes on purpose: copy his words exactly, fix only typos, and ask him before changing a name. Every word of a name
  starts with a capital letter (0.8.4, ACR 0.5.2).
- `feats/Rewards` (0.7.0, Andrzej: "Aren't achievements feats?") - EVERY achievement in
  Achievements - Renewed pays once per profile, except Rule Book challenges (`rbr_challenge_*`, or
  category Challenges of `rulebook_renewed`), which pay nothing. Points: the feat's points, else
  `data/campaign/ngp_achievement_points.csv`, else 5. `FeatPayer` (listener) pays at once,
  `Rewards.check` in CampaignScript pays anything done but unpaid. Paid ids sit in career.json
  `achievements`; the old `feats` map still counts.
  `PointsLine` (0.8.2) is the Achievements - Renewed 0.5.0 `RewardText`: "+N career points" under every
  achievement, the same text as a feat's own reward so it shows once. The Omega feat shares `acr_win_omega`
  since 0.8.2; `Rewards.alreadyPaid` also counts a feat paid under its own `ngp_` id, so a feat that starts
  sharing never pays twice.
- Rule Book - Renewed's achievement `rbr_ngp_ledger` ("Use a consumable item") reads sector memory
  `$ngp_usedConsumable` (set by `ShipItems.apply`, 0.7.2) and, for older versions, the hull mod id
  `ngp_ledger_refit`. Never rename either.
- `store/StorePanel` (0.7.5 rebuild) - the store screen, about 86% of the screen. Built like Achievements -
  Renewed's screen: per tab a container panel made once with a scrolling TooltipMakerAPI (smooth wheel
  scrolling) holding a content panel of labels; hidden tabs are moved off screen. Tabs, rows, icons and
  buttons are drawn in renderBelow and hit-tested in advance() from `Mouse` state, reading the content
  panel's real position each frame. No ButtonAPI: in 0.7.4 the tab buttons drew but never got clicks.
  `delegate()` is a separate object so advance runs once a frame.
- `ships/` - the Shipyard items (special items with a right-click dialog). S-mod slots and stat boosts
  are variant tags read by the permanent hull mod `ngp_ledger_refit`; extra OP is the permanent hull
  mod `ngp_ledger_rails` with negative OP cost. `SaveState` records each ship's work by member id and
  `ShipItems.sync` puts it back if a refit drops it.
- `perks/` - perks (Perks tab), hidden `npc_only` skills on the player under the aptitude `ngp_perks`
  (aptitude_data.csv, skill `aptitude_ngp_perks`). `StoragePerk` plus the classes in `Perks`. With
  Second-in-Command enabled, `Perks.showInSecondInCommand` removes `npc_only` at load so SiC lists them on
  its Non-Executive Skills page (it skips npc_only skills and aptitudes); without it they stay hidden so
  the vanilla screen never sells them for skill points. Last Stand cancels the first lethal hull hit per
  ship per battle (HullDamageAboutToBeTakenListener) and repairs 10% of max hull.
- `rules.csv` hands the Ledger's dialog to `LedgerDialog` via `NGP_OpenLedger`.
- Players see the station as **Legacy Station** (0.7.4, Andrzej disliked "the Ledger"); classes and ids
  keep the old `ledger` names so saves load. Its entity type needs the tag `has_interaction_dialog`, or the
  game draws it but never lets the player click it; `LedgerSpawner.fixUp` adds the tag and the name to
  stations in older saves. The hover tooltip needs a `customDescriptionId` (`ngp_legacy_station` in
  data/strings/descriptions.csv): without one the game shows no tooltip for a custom entity. Sector entity
  memory `$playLocationMusicDuringEnc` keeps the system's music during the visit. fixUp sets both too.
