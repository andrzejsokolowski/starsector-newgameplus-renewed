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

## Settings (LunaLib)

- **Extra career points:** added to (or, below zero, taken from) your career total. Set your total
  by hand here.
- **Points per level:** career points paid per level.
- **Store price multiplier:** every price is multiplied by this (default 1.0).

## With Rule Book - Renewed

When Rule Book - Renewed is installed, its active rules multiply the career points you earn.

## Requirements

LunaLib. Starsector 0.98a.
