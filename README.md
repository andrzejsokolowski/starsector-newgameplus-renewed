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
- **Feats** pay once per profile, never again in any save: At the Gates 20, the Planetkiller
  recovered 20, an Omega fleet beaten 30, the Ziggurat defeated 20, the Galatia Academy story 10, the
  Usurpers 10, the Knight Errant 10, a duel won 5, Kanta's protection 5. Feats from other mods
  count when that mod is installed: Hall of Triumph: Isa's masterwork 10, Gan Eden 30. Secrets of the Frontier: A Memory 15, The Wayward Star 30, A Lost Thread 10, Legacy of Hypnos 10, Annex-Barrow 15, Hope for Hallowhall 10. Random Assortment of Things: the Singularity 30. Knights of Ludd: Tri-Tachyon black site 20, Elysia 20, Dawn 15, Dusk 15. Heggie Tales 15. Sol Renewed: The Deterrent 30, Transferring Sol 5. Nexerelin: Salvation 30. Sephira Conclave: Operation Burning Storm 20. Liminal: the Liminal Crisis 20. What We Left Behind: the IAIIC crisis 30, the Omega Manufactorum 20. Domain Phase Lab: Project Prometheus 20, the Helheim Supremacy 10. Diable Avionics: the First Relay 20. Emergent Threats: A Thousand Cuts 20, Shattered Fortress 15. Halo Dynamics: the Culann mission 15. Core of Babel: Babel Protocol 15. Crab Shack: Dream No More 20. Tahlan Additions: The Elegy of Opis 15, The Illustrious 10. United Aurora Federation: Tilted Dagger 15. The list is `data/campaign/ngp_feats.csv`; other
  mods can add lines to it.
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
- **Armoury:** one weapon pack per faction, two of every weapon that faction knows, 20 points each.
  Modded factions get their own packs, and blueprints a faction learns during the game are included.
  Weapons the game never sells or drops are left out.
- **Other mods:** Tenebrian Nanoforge with AoTD - Vaults of Knowledge (20 points), Arc Sustainer with
  Sephira Conclave (16 points), a Peerless point with Peerless Skills (20 points); 1 per game each.
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
