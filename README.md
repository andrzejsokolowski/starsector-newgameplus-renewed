# New Game Plus - Renewed

Every level you gain, in any save, earns career points. Every new game opens a store, the Ledger,
next to your starting fleet. Spend your career points there on a head start.

## How it works

- **Career points** are kept outside your saves (in `saves/common`), so deleting saves never touches
  them. They are never spent: every new game gets a budget equal to the career points you earned in
  your other saves.
- **Levels:** each level you gain pays 1 career point (LunaLib setting). Each save pays each level
  once. Levels bought in the store pay nothing. Points earned in a game go to your next games, not to
  the store of the game you earned them in.
- **The Ledger** appears near your fleet when a game starts (or the first time you load an older save
  with the mod installed). It stays for the whole game and is marked on the intel screen.
- **The store** has tabs and a scrolling list. Click Buy, then Confirm. Story points, skill points,
  levels and credits apply at once. Goods wait in the delivery bay and you take them through the usual
  loot screen.
- **Shipyard:** S-mod slot (+1), D-mod removal, Stat tuning and Extra ordnance points (2 / 6 / 15 / 40
  by hull size). They arrive as cargo items; right-click one to fit it to a ship. Stat tuning raises
  one stat on one ship (hull, armor, flux capacity, flux dissipation, top speed or weapon range +10%,
  peak performance time +20%, supply use -10%); each ship can take each stat once. The work shows on
  the ship as the Ledger Refit and Ledger Mount Rails hull mods, which cannot be removed.
- **Perk:** +10% cargo, fuel and crew capacity for the rest of the game (Logistics tab, once per game).
- **Armoury:** one weapon pack per faction, two of every weapon that faction knows, 10 points each.
  Modded factions get their own packs, and blueprints a faction learns during the game are included.
  Weapons the game never sells or drops are left out.
- **Other mods:** Tenebrian Nanoforge with AoTD - Vaults of Knowledge (10 points), Arc Sustainer with
  Sephira Conclave (8 points), a Peerless point with Peerless Skills (10 points); 1 per game each.
  They only appear when that mod is installed.

## Settings (LunaLib)

- **Extra career points:** added to (or, below zero, taken from) your career total. Set your total
  by hand here.
- **Points per level:** career points paid per level.
- **Store price multiplier:** every price is multiplied by this (default 1.0).

## With Rule Book - Renewed

When Rule Book - Renewed is installed, its active rules multiply the career points you earn.

## Requirements

LunaLib. Starsector 0.98a.
