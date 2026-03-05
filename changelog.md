V3.2.5
------

- Assault Pod
	- Hullsize is now Fighter
	- should prevent the bugs of locking up battles and spamming the screen with  destruciton notifications when using many

- Panther
	DP: 4->8

- Sera now correctly gives 1 Rajanya as reward instead of it's BP

- New wing: MUSHA M3

- Valken X
	- Wing Size: 3->2

- By Their Silence
	- Fixed dialog bug when turning FLINT over to MRC

- GuarDUAL
	- Shield Efficiency: 0.85 -> 0.90
	- Shield Arc: 120->160
	- Tweaked AI

- MUSHA Sniper
	- Ship System effect now properly scales based on target size

- MORGANA
	- Morgana B has no supply cost/repair time. this should prevent Morgana ending battles showing it's at 50% hull / armor (even though it's really not, and just the module is dead)

- NEXERELIN
	- MRC <-> Diktat Max relation: -40->-10
V3.2.4
------
- Added UI Tags for modular hullmods
- used "empty" sprite for hardpoint/turretsprites that were left blank
- Updated cataphract complement ui, fixed bug that made it only checked first slot
- Fixed WINGCOM not being usable with Converted Hangar ships
- fixed valken x not having auto repair unit built in
- fixed bug where some strikecraft would be invincible if carrier was killed while it was repairing
- add 'no_combat_chatter' tag to assault pod

- MUSHA Sniper
	- tweaked sprite
	- HP: 1100->1000
	- Armor: 175->300
	- Small missile -> small composite
	- DP: 3-> 4

V3.2.3
------
- Fixed nullpointer if guardual had no missiles slotted
- fixed nullpointer caused by giving ship command to vent(somehow)
- fixed exception thrown by valkazard if countershield gauge was < 0
- fixed malformed chatter text
V3.2.2
------
- Musha Sniper
	- Defense: Damper-> Front Shield
	- Railgun-> Pinion Lance (just a name change)
		- Flux per shot: 40 -> 400
- Hi-MAC
	- Hotkeys are no longer hardcoded to WASD
	- they are tied to keys for accelerating,turning left,decelerating, and turning right, respectively

- ACS
	- Fixed automaton being locked to ship

Spriggan Powered
	- DP: 5 -> 6

GuarDUAL
	- Acceleration: 240->200
	- Increased decleration in mech form (240->480)
	- fixed weird missile positioning of some weapons
	- Adjusted turret fire offsets, should fire slightly more to the left (should help firing at small targets)

- fixed bug where strikecraft traveldrive would never deactivate
- fixed bug where music would sometime never end post crown of cinders
- fixed bug with notification queuing(?)
- fixed bug where MRC would award player 0 credits during reprisals
- minor rules.csv tweaks

V3.2.1
-----
- Fixed Emergency Recall working on non strikecraft
- Added armaa_launches_from_ships tag
	- add's the armaa travel drive to any strikecraft with tag
- added minor tweaks to reduce performance hit from units w/ animations
- Added dialogue option to get pirate fleets to stand down if friendly with MRC (only if they originated from fort exsedol)
- Removed missile debuff / blocked EMR for ships with cataphracht hullmod
- Added GuarDUAL/ GuarDUAL FV to ATAC reward
- GuarDUAL
	- Added Automated Repair Unit
	- Flux Capacity: 2000 -> 2250
	- Flux Dissipation: 175->225
	- Can transform now by using Middle Mouse Button
	- Raising shields no longer forces transformation
		- Shield arc in fighter mode is 50% base arc
		- Shields take 25% more damage
- New Wing: GuarDUAL (FV)
	- Identical to GuarDUAL, sans PERCEPT, flux cap/diss, and shields
	- 21 OP
- PERCEPT
	- chargeup: 0.30->0.25
	- cooldown: 3->1.5
	- regen: 0.06->0.08
	- missile intercept range: 700->800
	- max # of missile strikes: 1 -> 4 (based on flux level)
- Valkyrie(AA)
	- Aded more variants
	- Can appear carrying MUSHA + MUSHA Sniper, Aleste + MUSHA, MUSHA + Panther(for the hegemony), etc
- Spriggan Powered
	- OP: 30 -> 40
- Added MUSHA (Sniper)
	- 3 DP
	- 15 OP
	- Sniper Rifle
		- 1000 range, kinetic
	- Atropos (Single)
	- Targeting Laser
		- Functionally identical to graviton beam
	- Ship System: Called Shot
		- simple "qte" that increases damage based on threshold
		- ranges from miss->okay->good->perfect
- fixed some typos in rules.csv

V3.2RC3
-----
- Valkyrie(AA)
	- Aleste -> Aleste Early Type
		- Ship System: Microburn->Booster
		- OP: 30->15
	- CIVILIAN tag -> CARRIER tag
- Added Bakraid(TT) desc

- Broadsword(WINGCOM)
	- Small composite -> small ballistic
	- adjusted sprite

- Fixed reduce travel drive speed for strikecraft

- Dawn will now leave the fleet if:
	- Sat Bombing (permanent)
	- Diktat Commission (may or may not be permanent)
V3.2RC2
-----
- Fixed Corsair XIV having built-in SO
- Fixed Panther(XIV) not firing one of its weapons when still connected to Bakraid
- Fixed Recall Device never firing
- Fixed potential NPE when landing
- Added some missing wings to tritachyon

- Valkazard
	- Should now be more proactive with using melee weapon under AI Control

- Aleste 
	- Should now be more proactive with using melee weapon under AI Control
	- Shield arc: 360->300
	- Laser Blade:
		- DMG: 900->1250
		- energy / sec: 0(lol)->700

- MUSHA
	- 8 OP -> 12 OP
	- Added 1x Light Crusher Cannon
	- INTERCEPTOR -> SUPPORT
	- Light Crusher Cannon
		- Range: 700->800
- M3 Pod
	- Burst Size: 10 -> 4

- Expanded on the MRC Sleeper start
	- Added new dialog when encountering the initial fleet
	- added new dialog after encountering the initial fleet
	- added new dock/event on new meshan & fort exsedol post encounter

- Fixed edge case that allowed MRC to launch reprisals against itself and allies
- Fixed bug where Dawn on enemy side would chatter when voice was enabled
- Fixed gravion's terrain disappearing after a certain point in Descent
- Added Combat Chatter profile for Dawn


v3.2
------
======Additions======
- Add 'performance mode' lunalib setting
	- reduces most special fx in the mission battles
- Added SP option to circumvent FP limit for starting ACoC
- Add new voice lines for Dawn
- Add 4 new dawn events (spooky redacted, PK, shrine completion)


- New Wing: MUSHA
	- 8 OP, 3 wingsize
	- slow, mid-long range, prioritize fighters
- New Ship: MUSHA Sniper
	- 3 DP
- New Ship: Bassline
	- Combat Carrier
- Added some playable vanilla strikecraft analogues
	- Low OP, but several beneficial built-in hullmods including WINGCOM
		- New(?) Ship: Broadsword (WINGCOM)
			- 1x composite, 2x built-in LMG
		- New Ship: Sarissa (WINGCOM)
		- New Ship: Trident (WINGCOM)
			- Ship System: Reserve Deployment
			- 1x rear facing energy turret
- New Mission: Descent
	- If didn't start with Valkazard, can be obtained/encountered in the climax of this mission
- New Mission: By Their Silence
	- can be triggered once >= 25 reputation with MRC Liason on Fort Exsedol
- ATAC "Event"
	- Progress accumulates from destroying ships with ArmaA ships present in the fleet
	- Grants various rewards + minor buffs geared towards strikecraft
	
======Fixes======
- Fixed Kouto (E-Type) missing desc
- Fixed 'no description..yet' for arma faction start
- Fixed strikecraft not launching from converted hangar ships
- Fixed D-modded GuarDUALs causing CTD if mousing over Variable Unit hullmod
- FIxed enemy Panther's spawning at 5000% CR
- Arma Armatura now knows their XIV BPs (this means they will show up in the special submarket)
- Fixed some bugs that could occur if mod was loaded into an existing save
- Fixed reference to Raven at abandoned station
- Fixed HI-MAC triggering when not double tapping the same activation key (I.E tapping A, then tapping D)
- Fixed HI-MAC charge going negative if used after flame out
- Fixed ships with HI-MAC being a little too suicidal when system was active, should deactivate and use shield more often
- Fixed potential bug with strikecraft not being properly recognized as dead if destroyed while landed
- Fixed bug where strikecraft would automatically activate travel drive when launching from carrier
- Fixed bug where strikecraft would initially launch from bay with wrong orientation
- Fixed PERCEPT orb not changing target if current target was already dead
- Fixed strikecraft always accelerating forward post launch, instead of in the direction they are facing
- Increased performance of missions with special backgrounds
- Added extra check for assault pod removal (will self-remove once replacement rate decreases below 90%
	- It can never naturally replace new fighters, so this won't cause any issues

======Tweaks======
- Jenius becomes Pirate owned instead of Independent after completing ACoC
	- briefly has 'Arusthai' ownership during the mission to prevent rep loss to pirates during story related raid
- Bonus incoming damage dealt to strikecraft by fighter damage modifier had no upper bound. 
	- Now caps out at a 25% bonus.
	- Added floaty text to make it even more obvious ship is being hit by something w/ the damage modifier
	- New hullmod to alter/adjust this modifier:
		- Targeting Profile Disruptor
			-Effect: Nullifies bonus incoming damage, but:
				- +20% damage taken from all frigates
				- +10% from all destroyers
				- -10% from all cruisers
				- -20% from all capitals
				- From 1000 SU, capitals and cruisers autofire accuracy decreases as the ship gets closer (up to 20%), non-stacking
- Strikecraft hullmod specifies ships benefit from Frigate-based bonuses
- Strikecraft should no longer suffer malfunctions caused by low CR after repairing fully

- C-Stim Dispatcher
	- Uptime: 0.7s->1s
	- Downtime: 10s->6s
	- Fixed bug that caused ability to proc even when shields were raised
- Juno Mk. III
	- Chargeup: 1.5 -> 1

- Altagrave
	- Exceliza Grenade Launcher
		- Burst Size: 2 -> 4
		- Range: 600->800
		- Chargedown: 5->4
		- Damage: 500->550
	- Vajra
		- Range: 600->1000
		
- Valken
	- OP: 7->4
	- Refit Time:9->5
	- Revised description
	- Revised sprite
	- LAG -> M1 Pod
	- Matching their description, may explode if torso is disabled
		- deals up to HP+(ammo*weaponDamage)*0.25 in small AOE

- Valken X
	- Armor:100->80
	- Add Hi-MAC hullmod
	- LS-20
		- Chargeup: 0.33->0.11
		- Chargedown: 0.33->0.55
		- should reduce whiffed melee attacks due to the windup

- Spriggan Powered
	- Medium Ballistic -> Small Ballistic
	- Added 2x Small Missiles
	- Removed drone wing
	- HP: 700->800
	- Armor: 100->200

- Watchdog
	- OP: 85->100
	- sprite tweaks
	- Shield Efficiency: 1.1->1.05
	- Add 2x small composite hardpoints
	- Large Hybrid->Large Ballistic
	- Siege Mode
		- Speed Reduction: 50%->20%
		- No longer disables shields
- Watchdog(XIV)
	- Ship System: Reload->Siege Mode
	- Carries two barrettas instead of one

- Zanac
	- Top Speed: 90->100
	- Large Energy -> Large Universal
	- Zanac (MRC) monoeye will more aggressively track object's ship's targeting
	
- Gunhed
	- Armor:250->200

- Panther (XIV)
	- Can now detach even if overloaded
	- Will detach with the CR Bakraid deployed with at the start of the battle

- GuarDUAL
	- gfx tweak
	- Improved(?) AI
	- DP: 15->13
	- Mode BETA
		- Non missile RoF: 0.50x -> 1.00x
	- Mode ALPHA
		- Non missile RoF: 1.00x->1.50x
		- should help offset limited weapon slots
	- Top Speed: 250->240
	- Flux Capacity: 1750->2000
	- tweaked fire offset for head weapon
	- Percept (MODE A projectile)
		- EMP: 25->45
	- PERCEPT (System)
		- chargeup: 1->0.30
- Bakraid
	- clamped CR to 1(100%) to prevent 50000% CR bug when seperating
- Aleste SII
	- Ship System: Fuller Auto
		- fixed incorrect system desc, said increases RoF by 30% and Time Dilation by 20%, but reduces Weapon flux cost by 30% and time dilation by 25%
	- Updated Sephiran skin (thx mayu)
- Zanac(MRC)
	-Ship System
		- in combat stated only affected ballistics, when it affects energy+ballistic
		- Stated flux reduction was 10%, when actual reduciton was 20% 

- Prodromos
	- Ship System
		- None-> Manuevering Jets
		- Support Range: 0 -> 6000

- Assault Pod
	- Burst Size: 1->2
	- Spawns 2 battle armors instead of 4 per pod
	- Added Weapon: TB-11 Harpoon (Same as Aleste)
	- Added Engine
	- Replaced sprite with a better, but equally worse one
	- Pod's will now self destruct if replacement rate decreases below 95%, or HP decreases below 50%

v3.1.5
-------
- stopped some system only weapon from being purchaseable
- removed dev invictus being generated from planetfall armada
- Added fallback fleet in ACoC that spawns if indie armada gets destroyed + misc other hopeful fallback precautions
- fixed NPE that could occur when PERCEPT AI checked missile without a weaponspec


V3.1.4
-------
- Fixed bug that could occur if multiple guardians were generated in the fleet during Jenius planetfall
- Fixed bug that could occur when refusing the Crown of Cinders quest where player would still be in dialogue with Kade
- Fixed bug that could lead to drone fleets chasing each other into the fringes of Gamlin
- Further tweaked Reprisal fleet spawn behavior
- Add better handling for market selection for reprisal origin

V3.1.3
-------
======Hotfix======
- Fixed compile w/ jdk 23 error
- Fixed MRC HQ continuing to demand resources when inactive/hidden on a colony
- Adjusted MRC Reprisal always spawning same fleet size
	- Shouldn't always be ~9 fleets now
V3.1.2
-------
- Fixed strikecraft taking damage from hyperspace storms
- fixed leynos damper having no system description
- fixed Aleste(WINGCOM) firing while docked
- Slightly tweaked Aleste LPC sprite
- GuarDUAL
	- reduced system regen rate(.1->0.06)
	- fixed weapon hiding not working correctly if a hybrid mount was empty
- improved reprisal autoresolve logic
- changed Crown of Cinders drone fleet faction from pirate to one that should be always hostile
V3.1.1
--------
- Added MRC submarket to fort exsedol
- fixed gravion orbiting into jenius
- moved mrc reprisal autoresolve check to economy tick

v3.1B
--------
======= Hotfixes ========
-fixed checking for memkey that didn't exist yet
-fixed percept orb detonating on friendly ships
-fixed spriggan having small ballistic instead of medium
-fixed guardual nullpointer when no missiles slotted
-fixed aleste(wingcom) arms being strippable
-fixed aleste(frig) having so_ftr hullmod
-fixed alternate boss causing CTD when destroyed in atmo battle
-fixed AL 4096x soundplayer crash
======= Fixes =======
- Added extra nullchecks with altagrave
- Added extra nullchecks with wingcom
- Aleste with laser blade(s) should try to intercept missiles with them now
- Reduced knockback on Laser Blades
- fixed Dawn not receiving PPT bonus from Ace skill
- fixed Ace skill giving Combat Endurance CR bonus
- fixed zanac ex appearing in illegal weapons dealer event
- fixed landed strikecraft vector misalignment
- fixed carrier not firing its weapons if strikecraft landed bay was within
	the line of fire
- fixed ceylon appearing in illegal weapons dealer event
- fixed wrong design type for zanac ex
- fixed fighter SO CTD
- fixed missing hullmod from Einhander(MRC)
- fixed strikecraft spontaneously exploding if they began landing on a carrier that was destroyed
- fixed bug where strikecraft would path to invalid ships when attempting to refit
- fixed dawn's pop up dialogs triggering in the wrong circumstances
	- this SHOULD stop it from happening when you interact with anything on the campaign layer
- fixed some badly/inaccurately named variants
- Deanime-fied Dawn, Pariah, and Sarge a bit more
- removed some duplicate src files
- fixed some rulecmd being in com.fs.starfarer.api.impl.campaign.rulecmd package
- fixed gallant Juno damage dps being 900(should be 700)
	- chargeup: 1.5 -> 1.25
	- range: 1369->1000
	- no longer yeets the ship backwards

====== Features ======

- Revised Gamlin system

- MRC no longer starts as a full-fledged faction, acting more like a subfaction
	- Fort Exsedol is now pirate-owned with "MRC HQ" industry
	- While at least one of these HQs are active, MRC will target hostile systems and launch reprisals
	- With Nexerelin, successful reprisals will flip the affected market to pirate, independent, or pather ownership, depending on the circumstances
	- Player can attack factions hostile to MRC in the affected system for rewards
	- can be toggled on/off w/ lunalib

- New Quest: Crown of Cinders
	- Navigate the initial onset of a planetary conflict

- Hull & Armor values of most ships w/ strikecraft hullmod has been somewhat increased

- Added GuarDUAL
	- dual mode unit w/ fighter & mech mode
		- Can transform by double tapping X
	- 1x Medium ballistic, 2x small missile, 1x small hybrid
	- Ship System: PERCEPT
	- 15 DP

- Added Guppy
	- cheap interceptor
	- 1x light mortar, 1x vulcan cannon
- Added Gunhed
	- Replaces Gunhazard
	- Heavy armored, but slow
	- 1x Mining laser, 1x Piledriver

- Added Aleste (WINGCOM)

- Added Gallant (WINGCOM)

===== Tweaks =====
- Valkazard is no longer obtainable outside of starting with it(for now)
	- add extra dialogue if player somehow acquires one anyway and presents to sera

- Aleste S-II
	- Hi-MAC is now built-in
	- Shield Efficiency: 1.0->0.9
- VX Custom
	- Hi-MAC is now built-in
	- Shield Efficiency: 1.0->0.9

- Xyphos(WINGCOM)
	- Shield Efficiency: 1.0->0.9

-Valkyrie (AA)
	- DP: 5-> 8
	- Removed Converted Hangar
	- Wing is now built-in: Prodromos Battle Armor
	- Carries 1x Aleste (WINGCOM)
		- Detach when in combat range
		- Destroyed if mothership is destroyed
-Leynos
	- Anti-Beam Coat
		- Reduces Beam damage based on flux level(up to 60%)
	- Tweaked Sprite & animation
	- Adjusted Med Ballistic slot location + arc

-Watchdog
	- Adjusted AI
	- made watchdogs shield buff effect much more obvious

-Valken III
	- Revised sprite

-Added commission text for MRC

-Added MRC HQ industry
	- can request 'reprisals' here

-Added custom system music to Gamlin

-Cataphracts no longer consume HP/CR during raid actions

-Removed Nekki system
	- Planets in the system now reside in Gamlin

v3.0.6.1
--------
- removed fighter being spawned into fleet
- aleste system: microburn->booster
- removed everyframe from RCL


v3.0.6
---------
=additions=
- Split off portraits to submod, armaa_anime
- Added Rajanya
	-

- Added some configurable options w/ LunaLib
	- HI-MAC input delay
	- Strikecraft repair threshold
	- WINGCOM Chatter
	- Dawn Voice Lines
- Sera will now offer some help to the player during PL Crisis
- added player ethos (humanitarian, freedom, cynical, etc) changes to some mod-added dialogue options
- MRC
	- Fort Exsedol
		- Battlestation->Star Fortress
		- Added Heavy Industry
		- Added custom patrol
		- Revised faction desc

=balans=
-Kouto
	- Has strong shield, probably way too durable
	- HP: 700-> 500
	- Armor: 125-> 50
	- EGN Mine Launcher
		- Burst Size: 3->1
		- Cooldown: 10->3
-Valken X
	- 2x the armor of broadsword is a bit excessive
	- Armor: 200->100

-Aleste SII
	- Ship System: Rampage Drive -> Microburn 
		- not durable enough to benefit from damage res buff
		- AI will charge into unfavorable situations
		- Harpoon is enough to get into melee range, anyhow

-Watchdog
	- fixed variants missing weapons
	- revised ship and system desc
	- Added 1x med ballistic to left arm
	- Shield Efficiency: 0.8->1.2
	- Escort Package is now built-in
	- Ship System:
		- RoF Bonus: 1.25x -> 1.10x
		- No longer increases recoil

=bug fix=
- units with shields gain +25% speed/manuver bonus if it is destroyed
- Dawn will be removed from contact list if recruited
- Fixed some inconsistent unit names
- increased valkazard laser blade beam speed to be in line with other laser blades (1000->4000)
- added NEVER_RENDER_IN_CAMPAIGN hint to some deco weapons
- fixed some spontaneous wingcom CTDs
- fixed recoilless rifles having recoil
- fixed broken rules interaction if player went to gilead without dawn first, then returned with her

v3.0.5
---------
- removed some unused weapons/graphics
- fixed bug where strikecraft would sometimes elect to land on an enemy carrier
- Zanac - increased turret arm arc from 90 -> 120
- added desc for Zanac EX Magna Beam
- actually lowered kouto armor val to 150 this time
- fixed bug where panther would spawn using wrong fleetside (appear as an ally)

v3.0.4
----------
- kouto top speed: 140->130
- kouto armor: 200-> 150
- fixed bug where strikecraft landing would be set as "retreating", preventing it from docking until new orders were issued
v3.0.3
-----------
- fixed notifications around mazalot/galatia, etc not being flagged as already shown and appearing multiple times
- fixed NPE if bakraid seperated when parent ship had no valid fleetdata
- Dawn will now refuse to join the player if they are affiliated with the Diktat
- add more campaign layer interaction events w/ Dawn
- Fixed another NPE null fighter wing
- swapped leynos & kouto bazooka weapons back to use phase_mine behavior
- fixed malformed restricted/no_dealer tags
- Can now only turn in a valkazard once
- Fixed Panther CR not matching CR of Bakraid on seperation
- Fixed detached player Panthers using wrong fleet commander for stats if ally fleet was larger than players
- Lowered rep needed for alternative condition to be introduced to Sera by Dawn (50 rep -> 30)
- Fixed bug where Dawn would sometimes not offer Sera's comms-id even when conditions were met
- Increased Kouto LPCs bazooka projectile HP so they aren't instantly destroyed by PD
- Kouto Bazooka dmg type: Kinetic-> Energy; ammo limit removed; burst size 1->3
- Kouto shield type: NONE->OMNI
- Strikecraft should be able to land on station modules with bays once more
- Strikecraft should be able to land on carriers without any slotted LPCs
- Simplified carrier landing AI logic
- Fixed bug where strikecraft would try to repair at a carrier with 0 cr, and endlessly hover over it
- Fixed UI issue where when wingcom pilots exceeded level cap(this isnt a bug), cap was still listed as level-1
- fixed Fenris teleporting strikecraft between each other, for real this time (?)

v3.0.2
------------
- fixed bug with hel corsair right shoulder anim
- fixed bug with hel corsair shipsystem being set to plasma jets
	- should be weaponry overdrive if tahlan is enabled
- Reduced Garegga(H) propensity to self-delete via enemy ship explosion
- fixed swordsman aleste not having a left arm
- added "no_sell","no_dealer","restricted","no_drop" tags to swordsman aleste, watchdog xiv
- fixed crash when using karma system
- fixed bad variant for garegga_xiv
- fixed weapon groups for panther variant
v3.0.1
------------
- fixed missing .csv weapons
- fixed version # in mod_info
- fixed missing strings for armaa_rampagedrive
- fixed bad filename that would cause crash for linux
- fixed bug where Panther's shield collision class wouldn't match parent ships (caused explosions to destroy it/collide with other objects)
- fixed bug that would cause wingcom info panel to appear blank
- reduced visual recoil of pulverizer slightly
v3.0
------------
- New ship: Garegga(H)
	-
	-
	-

- New ship: Bakraid

- New ship: Panther

- New ship: Corsair(ISEN)


- Zanac
	-Firebolt Feeder
		- weapon flux cost reduction: 10%->20%
-Aleste S-II
	- Add weapon: Pilebunker
	- Add Weapon: Harpoon
	- Armor: 250->300
	- Reduced flux cap/diss somewhat (i dont remember what it was before whoopsie)
	- Reduced top speed
-Valken III
	- added no_dealer, restricted tag

-Valken X
	- Wingsize: 2->3
	- Refit Time: 13->11
-Spriggan
	- Wingsize: 2->3
	- Ammo: 4->6
	- Ship System: None-> Flare Launcher
	
-Watchdog
	- Guardian Protocol
		- Speed Reduction: 100%-> 50%
		- Turn Rate Reduction: 30% -> 50%
		- Add 1x Medium Missile
		- OP: 80->85
		- Shield Arc: 140->300
-Watchdog XIV
	- Increased size of shipsystem qte thing
	- made fail state more clear
	- fixed shipsystem increasing weapon flux cost by 2x when activated 

- Increasing CR per deployment for some ships to reduce supply recovery cost
- fixed MRC not actually taking the credits they want to rob you for (how nice)
- fixed bug that made enemy alestes and watchdogs never use their ship system vs player allies (the yellow guys)
- fixed bug that made aleste variable rifle glow white whenever a jitter effect was applied
- fixed bug that would cause crash if fighter had a null wing and a wingcom pilot, somehow
- fixed bug that would cause crash if DHull did not have a parent hull, somehow
- fixed wingcom crash that would occur due to bad class path
- fixed zanac ex having an inaccessible hybrid mount below magna beam
- fixed watchdog xiv having an inaccessible hybrid mount below barretta
- fixed fenris carrier chain teleporting strikecraft (probably)
- revised the hegemony bounty. Should now drop properly drop BP with all the hegemony variants.
- removed some unused weapons/sprites

v2.3.6
------------
- fixed forced burn effect occuring after deploying strikecraft from carrier
- removed tag that prevented strikecraft from using CH watchdog for repairs
- added some extra null checks
v2.3.5
------------
- fixed crash that could occur from adding mod to game mid save
- adjusted watchdog small turret arcs to converge
- adjusted aleste small turret arcs to converge
- removed medium hybrid from swordsman aleste variant
- fixed crashed that could occur if player attempts to talk to sera before meeting dawn in bar
- fixed crash that would occur if strikecraft were deployed without carriers
- fixed MRC not being hostile to hegemony like their faction desc suggets
- fixed crash caused by trying to build in cataphract2 hullmod using progressive s-mods

v2.3.4
------------
- add faction start banners for nex (thx mayuyu)

v2.3.3
-----
- fixed rulecmd print debug message in campaign
- removed empty built-in slot on pilotable aleste
- fixed D-modded Hel Corsair losing its legio-specific hullmods
	- tweaked shield usage on modules
- sharpened exsedol illustration
- fixed recoil sprite for watchdog(H)
- corsair
	- shield arc: 120->180
- added sephiran skin for aleste
- fixed particles effects from hi-mac not matching engine color if it was shifted
- fixed bad rank id for sera
- fixed memory leak with syko stim everyframe
- fixed assault burn effects not ending if flamed out while active
- added OP cost to HI-MAC

v2.3.2
-----
- fixed jarring music transition
- fixed assault burn being broken if repeateadly double tapping W while already burning
- fixed being able to assault burn, boost when flamed out
- unhid meshan admin at start of the game
	- starting comm-link just gives a generic request denial until unlocked
- hid dawn at start of the game
	- can now be met at the bar initially

v2.3
------
==New Features / Content==

- Valkazard can be returned for $150k or $300k depending on dialogue choice

- Characters now begin with low-importance contact on nex armaa start (also changes some dialogue)

- Added planet & bar flavor text to New Meshan

- Added another character to New Meshan

- Added some portraits to Nex Rocket Stars mercenary group

- Added mini-faction(?) MRC
	- 

- Cataphract SII hullmod can be S-Modded
	- provides some scaling buff based on level of pilot

New Hullmod: HI-MAC Manuever System
	- (it's basically AC6 Quickboost/Assault Boost)
	- Built in to Hel Corsair, can be acquired at high rep with the armaa admin


New Ship: Zanac
	- Fast-attack Destroyer
	- TODO: leg anim stuff/refinement
	- Two skins: Red (known by MRC) and Grey (known by AA)
		- TODO: Skin swap?
	- 1x Large Energy, 1x Medium Hybrid, 2x Small Universals, built-in Aux Thrusters

New Ship: NotKshatriya (TODO, sandbagged for next update)
		
New Ship: Watchdog (H)
	- 1 Built-in, 2x Med Missiles, 2x small Missiles

New Ship: Corsair
	- Pirate Variant, Hegemony bounty variant
	- 2 large ballistic, 2 smalls hybrid
	- Pirate version has built-in hullmod that reduces ROF

New Bounties: 
	-Corsair(H)
	-Watchdog(H)
	-Hel Corsair
	-Zanac EX
		- TODO: No Description...yet

==Tweaks / Balance Changes==
- Strikecraft wont immediately try to refit after spending missile ammo while enemies are still around
- Revised AI for returning to carrier. No longer uses Civilian waypoints for navigation
- Dance Party on New Meshan has ended (market music has been replaced with something a bit more chill)
- Fixed Nex ability CTD
- Fixed small fleets (Academy Shuttle services, mercenary patrols) always using armaa warthogs
- Fixed potential slowdown that could occur when command UI was open
- Fixed Einhander (P) missing variable rifle hullmod
- Fixed Einhander having no legs
- Fixed Spriggan Powered missing variable rifle hullmod
- Valkazard now has innate +30% refit rate penalty
- fixed typo in specialized service bays hullmod desc
- Syko Stims
	- consume 3 recreational drugs instead of 1
	- The amount consumed over the entire fleet is reported instead of per fleet member now
	- Increased Time Dilation amount to 3x, but reduced duration to 0.7s
	- Increased cooldown: 5s -> 20s
	- fixed potential memory leak
- remove ilorin wing
- remove bihander wing
- remove einhander wing
- removed functionally useless ships (AA Broadsword, AA Warthog)
- tweaked valkenx sprite for the 18907401th time
- tweaked Aleste(LPC) sprite
- tweaked Aleste(S-II) sprite
- tweaked Einhander & Einhander+ sprite
- tweaked laser blade visuals
- fixed phased strikecraft taking damage while phased if they flame out
- only one raid capable unit is deployed instead of every single one (this should reduce the large supply cost hit if fielding many post raid)
- fixed hullmod conflict appearing for conflict with built-in hmods
- removed range restriction on laser blade 
	- damage remains the same up until beam length is >= base range * 1.5. from this point on, damage is gradually reduced (up to a 50% penalty)
	- damage is determined by the length of beam on impact, so attacking up close still confers full damage, even with range increased.

- Aleste
	- Left arm is now locked to Laser Blade
	- Right arm is now a modular hybrid turret
	- Ship System: Lunge
		- Disables shields and charges towards target. If colliding with target, deals HE damage on contact

- Spriggan Powered:
	- Drones: 2->3
		- Refit Time: 15->10
		- Support Range: 1500->2000
		- Armor: 150-> 50
	- Added ALWAYS_PANIC tag
	- Added NEVER_DODGE_MISSILES tag
	- Medium Ballistic -> Small Hybrid
	- Shield durability is increased

- VX Custom:
	- Removed WINGCOM
		- Ship System: Phase Skimmer -> Manuvering Jets
		- Small missile -> small universal
		- Increased flux dissipation 
		- Shield Arc: 90->180

- Watchdog:
	- Removed one large ( ); )
	- Remaining large is now a hybrid turret
	- Speed: 60->80
	- OP: 85->70
	- Ship System: Guardian Protocol
		- Increases range, disables shields and movement
		- Nearby ships shield strength increased by 10%

- Spriggan(LPC):
	- Ship System: Flare Launcher -> Booster
	- Armor: 100->50
	- RCL-48 Recoilless Rifle
		- Range: 600->500
		- Damage: 400->350
		- Burst Size: 3->2
		- Ammo: 6->4
		- Chargedown: 2.8->2.5

- BA Assault Pod
	- Fixed preventing combat from ending

- Valken(P)
	- Ship system -> maneuveringjets

v2.2.3
------
- Kouto
	-Minigun firerate has been lowered considerably

- added illustration for new meshan
- fix bad portrait name for Ceylon HVB
- added new hullmod icon for wingcom
- fixed buggy spriggan wing
- revised valken sprite, again
- fixed spriggan lpc not being sold
- fixed valken(p) not being sold
- added spriggan to cataphract bp
- added cataphract bps to arma sleeper start
- garegga dp: 10->9
- watchdog dp: 10->11
- fixed log clutter while iterating through dialogue keys
- fixed CTD that could occur due to misnamed deco weapon
- made initial encounter in valkazard start more FUN

v2.2.2
------


- Spriggan Powered
	- Armor: 225->200
	- Top Speed: 150->140
	- Drone Count: 4->3
		- Engagement Range: 1000->500
		- Replacement Rate: 8->10
	- Shield Module
		- HP: 500->800
		- Armor:800->500
	- Weapons Pack
		- Armor: 200->150

- Wingcom
	- Added encounter dialogue for:
		-Factions:
			- Agni
			- Sindrian Diktat
			- Sindrian Fuel Company (PAGSM)
			- UAF
			- Wanted
		-Ships:
			- Ziggurat
			- Guardian
			- Songbird
			- ASDF-03

- Fixed Valkazard body mods not being removed 
- Fixed Valkazard Laser Blade Hullmod being listed as bazooka
- Fixed cores not actually being removed if ship with overlord suite is destroyed

- Fixed strikecraft never refitting because game considered them to be retreating(?????????)
v2.2.1
-------
- minor bugfix / tweaks
- fixed aleste bazooka hullmod weapon select being labeled "BLADE"
- fixed some bad Unapplicable text for overlord suite
- added hard limit to # of times squad members can banter amongst each other per engagement
- fixed squad chatter dialogue order
- added faction specific intro text for wingcom
- fixed strikecraft sometimes not dying with carrier if it was destroyed
- fixed strikecraft not returning to carrier if they had an active target
-fixed incorrect tags on ceylon decos
v2.2
--------
-balans-
- Increased DP value of Valkazard derelict defenders from range of (75-150) to (300-400)
Valkazard
	- DP: 15->20
Leynos
	- removed WINGCOM ;(
	- front shield -> damper field
	- System:
		Amalgamate Feed -> Blink
			- Dramatically increase time dilation for 1 second, reduces weapon cooldown time

Ilorin
	- removed from spawn pool

Recordbreaker -> Spriggan
	- Same weapon as Ilorin w/ increased ammo
	- 1x Compact Sabot (2 sub projectiles vs 5,  no emp )
	- 18 OP
	- Revised sprite

Aleste(S) -> Aleste(S-II)
	- HP: 1000->900
	- Every Aleste now carries left arm laser blade in addition to the selectable left arm weapon
	- System: Microburn -> Lunge
		- Charges directly at target, attacking with melee weapon once in range

Fixed Bihander dropping/appearing in blueprint package

-bugfix-
- Fixed Watchdog randomly turning system on and off under certain conditions

- Fixed slowdown that can occur if using wingcom hullmod on ship with all AI fighters that previously only used manned fighters

- Fixed yet another altagrave ex shotgun bug

- Added docking timeout (if ship cannot dock after a certain period of time, the process will abort)

- Fixed ships being locked in unplayable state due to AI being reset while docked

- fixed perf hit due to logging that wasnt disabled

- fixed massively inflated squad sizes that could occur if using drone wings with WINGCOM

- fixed overabundance of skyMind refs being saved in persistent data; fleetmember used as key instead of captain of ship

-new features-

- WINGCOM
	- Pilots have a small chance to level past threshold for max officer level, up to level 12
	- Level up chance increases as number of engagements (consecutive battles before victory/retreat) ship is involved in increases

- Fenris
	- Cataphract Carrier
	- 50% Strikecraft refit bonus
	- Enables Cataphract Strike ability during Nex invasions 

- Nexerelin Ground Battle Ability: Cataphract Strike
	- Attack an industry with chosen unit
	- Damage is altered based off of various statistics such as pilot level, CR, etc

- Spriggan, 18 OP Bomber
	- Fills same role as Ilorin
- Spriggan Powered, 7 DP
	- 1 Med Ballistic, 1 Med Missile
	- Uses physical shield for defense
	- Destruction of back module disables missile weapon
- Ceylon
	- HVB support carrier




v2.1.3
--------
- Added new strikecraft oriented hullmod that can be found via exploration, and one other series of hullmods

- Added TMI mod compat

-
=-------Fixes/Tweaks----=

- Modules fitted with WINGCOM should now work properly

- Fixed Strikecraft not properly receiving carrier bonus

- LPC Aleste should now deploy with random loadouts
	- Reduced firerate by 30%

- Fixed Altagrave EX doing inordinate amounts of damage while ship system was active

- Fixed crash caused by Einhander/Einhander P having fighter hullSize while retreating

- Fixed crash caused by accessing WINGCOM Squad Manager when no key for the captain existed

- Fixed some typos for Valkazard's SILVERSWORD hullmod
	- Silversword regen rate increased by 25%

- Garegga's Silversword now properly shows afterimages for all parts of ship

- WINGCOM chatter disable config option actually works now

- Adjusted chatter probability to be a bit less spammy
	- the chatter chance further decreases as wing size increases
	- chatter chance increases for strikecraft that are on screen (this chatter is displayed over the ship)

- Revised "midline" skin for VX Custom

-Strikecraft burn speed is now reduced to 0 if there are no larger ships in the fleet
	- this behavior can be ignored with independent_of_carrier tag

-WINGCOM range penalty for destroyers, cruisers, and capital ships reduced (was: 70%/60%/50%/40% now: 70%/40%/30%/20%)

-Altagrave
	-Top Speed:110 ->120
	-Shield Efficiency: .65->.6
	-KARMA
		- No longer resets to zero when maxed
		- Slowly decreases while above zero

-Kouto E-Type & Kouto
	- Top Speed: 160->140

-VX Custom
	- Top Speed: 180->170
	- Armor: 170->160
	- Shield Arc: 70->90
	- Slightly reduced turn rate/ acceleration

- Fixed bug where wingcom pilots would not properly learn elite skills after max level was reached

==========
v2.1.2b
-------
- fixed bug that would cause wingcom pilots to be extremely chatty

- added another skin for vx custom

v2.1.2
-------
- Fixed bug that could sometimes prevent pilots from gaining rep/ levelling up
- Fixed bug where pilot would level up when upgrading a skill to elite (they should only level when gaining a new skill)
- Max level pilots can now continue gaining elite skills, if they still have slots for them remaining
- VX Custom  
  - reduced shield arc from 140 -> 70
  - Fixed bug that would cause arms to disappear
  - Added Weapon Swap to Altagrave G, readded to market
- Reduced Altagrave Vajra's f/s from 1.1->.8
- Fixed nullpointer that could occur during landing caused by edge cases

v2.1.1
--------

- uncommented loc that prevented weapons from being pruned on arma market (oops)
- removed debug text that appeared when allied strikecraft were landing
- added skin swap for vx custom since I can't decide if the high-techy blue or red is better
- replaced references of dollars to credits in some dialogue lines

- Added "strikecraft_with_bays" tag
	- if wingcom is equipped fighters will launch directly from the ship

- Added combat dialogue for enemy wingcom squads relevant to players acheivements/actions during the run
	- used transverse jump
	- war crimes
	- Janus device
	- working/worked w/ baird
-Valkazard
	- SILVERSWORD
	- System now increases ammo regen of clip based weapons


v2.1
------------------
- Added null checks to prevent some crashes
- Revised strikecraft hullmod text 
- Tweaked arma submarket hull frequency once again
- Stock is halved but the rarer hulls should appear more often

v2.09
------------------
- Slight tweaks to VX Custom sprite, added arm recoil anim(s)
- balance tweaks
	- reduced HP and flux stats slightly for all mechs
	- switched hullsize to frigate (except for gunhaz)
	- removed expanded magazine/misslerack restriction
- Refactored launch script

v2.084
-------------------
- Fixed Valkazard being locked in an invisible/uncontrollable state if docking while under player control

- Adusted refit logic so that ships with a loadout consisting of weapons with finite ammo will immediately attempt to resupply when exhausted instead of flying around uselessly

- Fixed ships with strikecraft_large && strikecraft_medium still being able to land on banned ship sizes

- Fixed some weirdness with the takeoff script

- Fixed debug text appearing when AI strikecraft land

- Fixed bug where AI would linger over carrier instead of docking (?)

v2.083
-------------------
= New Features =
- AI strikecraft(enemies and NPC allies) with WINGCOM and an officer piloting will have pilots assigned to their fighters like the player
	- Level varies based on the officers level + a bit of rng
	- Squad names are (currently) just (parent ship name) + Squadron

- New ship, VX Custom

- Added two tags: strikecraft_medium and strikecraft_large
	- ships with strikecraft_medium can only land on cruisers and capitals
	- ships with strikecraft_large can only land on capitals

= Bugfixes / QoL =
- AI strikecraft should launch from their own carriers properly

- Strikecraft should no longer 'clown car' from carriers
	- Basically, the number of strikecraft that can deploy from carriers is equal to the number valid launch bays on the field at time of deployment
	- Once all slots are taken remaining strikecraft will deploy from edge like normal ships

- Fixed pilotable kouto never appearing for sale
- Fixed strikecraft not being affected by CR Reduction / Reduced PPT in coronas/event horizon
- Fixed visual bug with Valkazard Combat Stims appearing disabled on hullmod, even when recreational drugs present
- Fixed bug where WINGCOM fighters would land on parent ship with no native bays if no other carriers were present instead of retreating
- Fixed strikecraft being locked in an invisible/uncontrollable state if player tried ordering one while docked 

Watchdog
- Watchdog sprite revised by Selkie
	- Removed 2 medium turrets
	- Added WINGCOM
	- Adjusted AI to better attempt to keep distance

- Garegga sprite revised by Selkie

- Curvy Laser subprojectiles are now interceptable targets (versus only the parent projectile being interceptable)
	- fixed outdated description text stating impact damage was 4500, should be 3000

v2.07
-------------------
- fixed flickering arrow bug on command ui for strikecraft
- restricted curvy laser use for remnant to a specific variant that appears infrequently
- Misc changes/fixes
- Fixed crash caused by carriers that somehow dont have launchbays, for real this time
- WINGCOM
	- Previously, fighters with wingcom installed on parent ship would get increased RoF + damage bonus. the damage bonus has been removed.

- Fixed huge supply consumption by salvage gunhazard 
- Fixed description bug with valkazard
- Valkazard
	- High Intensity Beam
	- Max Ammo: 8
	- Ammo/Sec : 0.13->0
	- Burst Delay : 4->20
v2.06
-------------------
- Added some more dialogue text
- Fixed another potential crash caused by carriers that somehow don't have launchbays
- fixed crash caused by transferring command to wingcom ship using officer that never was assigned a wingcom ship before
- Fix bug with aleste swordsman variant
- Fix bug with hullmod order on Leynos
- Leynos Railgun Flux Cost: 225->90 (2.25 ->.9)
	- EMP Damage: 100-> 175
- fixed typo with mobile armor tag
- fixed watchdog weapon swapping
- adjusted watchdog system AI
	- Should be a bit smarter with using it
- Fixed incorrect display of elite skills for wingcom pilots
- Added small chance for wingcom hullmod to appear for sale on submarket @ meshan
- Increased engagement range reduction for wingcom by 10% for all hullsizes
- Strikecraft can no longer use fighter clamps from Roider Union
- Enemy ships with WINGCOM hullmod that have a squadron will now use the correct colors to identify them instead of friendly/player colors (this case shouldn't happen in normal gameplay outside of mod shenanigans / traitor command ... for now)

v2.05
-------------------

- fix special strings (such as faction names) not properly appearing during chatter

- Added clarification that wingcom needs crewed fighters to function

- Added info for if player has no squadrons on intel screen

- added null checks for every dumb thing possible



v2.041
--------------------
-fix damage crash

v2.04
--------------------

- fixed crash by transferring command into ship with a wingcom officer
- Lowered valkazard CR Deployment cost to 30%
- raised threat level for: einhander, einhander P, valkazard
	- einhander, einhander P, valkazard can now capture points
- Fixed bug with AI strikecraft locking up if carrier was destroyed just before it finished landing

v2.03
--------------------
- fixed crash with null carrier
- fixed various crash inducing NPEs
- fixed strikecraft instantly repairing hull and armor by docking and immediately undocking
- new ship: Einhänder Plus (technically already released, but now with an actual desc.)
	- Einhänder with almost all built-ins removed in favor of modular slots

- fixed einhander crash with dummy weapon

v2.02
--------------------
- Blocked Front Shield Emitter from being installed on Valkazard's Counter Shield Torso
- Fixed invisible launch bug, probably
	- strikecraft should properly mirror the angle of the bay launched from instead of always launching at 90 degrees
- Strikecraft distribution should be a bit less heavily weighted on a single carrier on deploy
- Fixed laser blade not properly accounting for bonus beam/energy weapon damage outside of EWM
- Can now dock at carriers by targetting them and activating autopilot
- Fixed NPE caused by WINGCOM chatter (hopefully)
- Increased break chance for all strikecraft (0.5->0.7)

=raiding=
- Added functionality for custom ground support values: ground_support_custom in modSettings
- Reduced Ground Support Bonus for all pilotable cataphracts
	- Generic support(hullId unspecified): 15->5
	- Einhänder: 25->11
	- Aleste: 15->8
	- Leynos: 15->8
	- Valkazard: 25->15
	- Pilot level(if any) is added to ground support value 
- Cataphracts can be damaged during raid deployments
	- Base chance to incur damage scales by ratio of attacker vs defender strength
	- Higher attack strength and pilot level reduces actual damage received, while higher defender strength increases it

V2.01
--------------------
- Fixed crash form mousing over wingcom
- Fixed crash from mousing over Silversword
- Fixed crash caused by enemy fleet having a null faction(fleet commander?) or something
- Fixed crash for nonexistent portrait for hvb
- Fixed case sensitivity issue with a sprite
V2.0
--------------------
- Curvy Laser
	- Reduced number of projectiles generated by 50% to improve performance
	- Proj Speed: 525->650

- New Ship, Valkazard
	- Added new start for starting w/ (ArmaA(Pirates)) (w/ super cool special snowflake nex start text by timid! wowee)
	- Can be found via exploration otherwise
- New Ship, Broadsword(AA)
- New Ship, Warthog(AA)
- New Ship, Kouto E-Type
	- 8 DP
	- fleet support, grants damage buff to random number of enemy ships based on ECM rating
	- Gun is modular hybrid slot, but since I can't alter weapon muzzle effects done """incorrectly""" it only use weapons on a whitelist by default
		- if you want to slap anything you want on it, can edit this in modsettings by adding "ALL" to the whitelist

- New fighter, Valken(P)
	- Carries Anti-Ship Sword, HE weapon with limited uses
- New Fighter, Prodromos Battle Armor
	- Fragile but effective anti-fighter/PD unit

- Advanced Optics(AA)
	- Can no longer be built in
	- Reduced Range: 100->50
	- OP: 5-> 3
- Removed debug text that appeared when using Curvy Laser

-Laser Blade(s)
	- Increased on-hit knockback. Knockback effect is produced so long as blades are in contact with objects (so spamming blades on top of things should push you further back than just hitting a ship with the end of the blade. Hitting small objects such as fighters and missiles should produce no knockback, usually)

- WINGCOM SUITE:
	- Can now be used on standard carriers
	- Ships that shouldn't be able to equip, but somehow have it anyway, will have their fighters launched from offscreen/other carriers as other wingcom units do
		- This is determined based on weither the ship inherently has fighter bays or not
	- optimized code a bit
	- Externalized strings to modSettings
	- added dialog lines for remaining vanilla fighters
	- added dialog lines for a few mod fighters
		- Additional lines can be added with magiclibs modsettings by using "special_wing_lines" map (look at armaa's mod settings for an example)
	- Removed glow/jitter effect
	- wingmen will now use different dialog based on the character's 'voice'
	- wingmen will appear in battle as a pilot of their respective fighter
		- can also gain new skills over time and level up
	- Adjusted font colors for more uniformity with normal hullmods
	- In smaller battles where ships deploy closer to the center of the map, wingmen will now spawn a few thousand SU away instead of from deployment zone if no carrier is present
        - Max stat bonus conferred by relationship reduced to 30% (was 75% for defensive and 37% for offensive)
	- Added support for multiple wings
	- Implemented GUI for changing squad name/callsigns. Heavily based off of a butchered carcass of Nexerelin's Insurance GUI. Thanks, Histi!
	- Added random little blurbs that squadrons will blather on deployment
	- Fixed ships with WINGCOM being considered for relationship modifiers even if they did not participate in combat
	- Fixed retreated ships with WINGCOM not being considered for relationship modifiers
	- Added fluff text for characters with 'villain' voice
	- Added fluff text for characters with 'soldier' voice
	- Wingcom will now check for hullId first before resorting to baseHullId (for fighter skins)

- Strikecraft:
	- AI navigating back to carrier has been improved
	- Will now launch from valid carriers (if any) on deployment
	- Carriers can only supply as many Strikecraft as the number of fighter bays they possess (I.E. a Condor can resupply only two strikecraft at once, as opposed to an unlimited number)
	- Doubled carrier replacement rate decrease while rearming.
	- Fixed Strikecraft changing to Frigate HullSize if they took damage that frame
		- Should fix inconsistency with some onhit effects that used hullsize, fighter damage bonuses from skills, etc
	- Fixed shield damage not being displayed
	- Slightly increased retreat area 2000->2050 in attempt to fix retreat CTD caused by AI attempting to retreat while fighter hullsize
	- Hull and armor now gradually restore over time on carrier
	- Carrier Refit can now be cancelled at any time
		- HP and Armor replenish over time in carrier, so not an all or nothing decision
		- PPT and Ammo are only restored if full refit time is spent
	- added modSettings for custom configuration of refit penalties for (ideally) mod weapons and hullmods, and squadron chatter

	- Aleste
		- Reduced damage by 17%
		- No longer deals EMP damage
		- Increase Flux Cost: 350 f/s

- Aleste(S)
	- Shield Efficiency: 0.8->1
	- DP: 6->7
- Xyphos(AA)
	- DP: 5->7

- Leynos
	- DP: 6->7
	- Added Rugged Construction Hullmod
	- Now uses the better(?) Vanilla Damper AI implementation
- Gunhazard
	- DP: 3->4
	- Added Rugged Construction Hullmod

- Revised wings so each LPC has more clearly defined role
	- Valken-X: HE Fighter
	- Kouto: KE Fighter
	- Aleste: Obligatory Superfighter
	- Bihander: Obligatory Superfighter^2
	- Valken: Fodder
	- Prodromos: Interceptor
	- Gallant: KE Bomber
	- Ilorin: HE Bomber

- added legs decos to Aleste, Kouto, Valken-X, Leynos,Valken

- Fixed torso tracking problem with valkazard

- fixed missing description for valkazard system

- Revised Sprite for Kouto, Valken X

- Reduced value of Cataphract & Advanced Cataphract BP package

- Removed submarket rep/transponder requirement for gamlin

- fixed bug where player was not properly referred to using gender appropriate title(based on portrait) when landing

- fixed crashed caused by destroying altagrave module with no weapons assigned

- fixed squadron members not being generated unless wingcom hullmod was moused over

juno. mk iii
	- dmg/s: 900->800
	- chargedown: 0.5s -> 1s
-Altagrave
	-Corrected negative modifyPercent for Weapon Flux Cost Mod
	- Replaced ear killing shotgun sound for Altagrave Ex
	- Altagrave Ex: Plasma Jets -> Havoc Drive
	- Deco Left Arm: -> Laser Blade XL
	- Added two small missile mounts
		- Mounts will be destroyed/rendered unusable if backpack module is lost

- Valken-eX
	- Replacement Time: 10->13
	- OP: 10->11
	- Removed Ion Cannon (High Delay)
	- Chaingun: chargedown:1->0.5
		-Damage: 50->25
		-Flux/Shot: 0->10
		-Burst Size: 5->6
	- Laser Blade: 
		-Damage: 350->400
		- Range: 45->60
	System (Right Click): Overboost -> Havoc Drive

- Kouto
	- Revised loadout for anti-shield role

- Aleste
	- Replaced Stake Driver with Rynex Pulse Rifle
	- CLAW Drone: Increased arc to 360 deg, Ship System: Temporal Shell

-Pila Drone(Built-in)
	- Removed Phase Damper special system
	- New System: Temporal Shell

- fixed outdated description for kouto

- Altagrave can now swap between Vajra and Grenade Launcher
	- Also overcompensates with a large laser blade, may remove before actual release tho
- Watchdog can now swap between Hellbore and Gauss Cannon
	- Siege Mode: Shield Damage Reduction: 50%->10%

- Einhander:
	- Drone weapon is now equipped directly on the main ship
	- Increased OP: 30->39
	- Max damage bonus from ship system: 70%->25%

-----
V1.5
------------------------------------
New Ship: Leynos - 6DP / Leynos(RS) - 10DP
-Same schtick as the Aleste, but more durable + heavier weapons
-Damper Field in lieu of standard shield

New Ship: Xyphos(AA) - 4DP
- more offensively tuned Xyphos with an attached Xyphos wing
- Effectively a mobile support wing

New Ship: Gunhazard - 3DP
- Live fast, die faster
- Very mobile with good armor rating, but extremely low HP 

New Ship: Watchdog - 10DP
- Fire Support; poor mobility and flux stats but enhanced range and firepower for its class

New Hullmod: WINGCOM Suite
- Adds fighter bay with 70% range reduction, increased replacement rate of 70%,and increased refit time of 25%
- Instead of launching from the host ship, new fighters either arrive from the deployment zone or from nearby carrier    returned to for refit
- If piloted by officer, winning battles improves effectiveness of the wing, while being disabled, destroyed, or losing has chance to reduce it
- Wing members have a small chance to be promoted, akin to vanilla officer promotion event

-Added Nex Mercenary company

-Only Einhander,Garegga, and altagrave were known to indies + PL, added remaining hulls
-Slightly bumped up frequency so they show up ocassionaly instead of once every few cycles

	-Fixed issue that caused custom Aleste/Einhander .skins to crash due to invalid ground support bonus by checking base hull instead

Strikecraft:
- Damage dealt popup/numbers should now properly display when taking damage
- Fixed bug where game would crash if player took control of enemy strikecraft and then retreated with it (.....somehow)
- Strikecraft will now always be disabled/destroyed after combat if hull is fully depleted, instead of a 50% chance of not needing to be recovered
- Fixed bug where landing ship would only choose bay 0 to land at
- Replacement Time reduction when refitting now occurs during refit time instead of after
- Fixed bug where combat would not end if an allied / enemy strikecraft were on the field after full retreat on the player's side
- Trimmed description to be more concise and less fluffy
- Strikecraft will (should?) no longer attempt to fly directly through enemy fleet to reach a carrier

-Altagrave's will purge all modules once the core ship's HP decreases below 55%
	- This is a (AI) buff and should improve survivability somewhat
	- AMWS for all head variants DMG: 350->400, IGNORES_FLARES, adjusted offset for better accuracy
	- Karma ship system now benefits from Systems Expertise
	- Karma Seekers: 
		- Reduced number of projectiles generated
		-Launch Speed: 1500-> 800
		-Proj Speed: 1000->700
        - Fixed Ear killing shotgun sound used by altagrave ex
	- Default Altagrave's main weapon can no longer be intercepted by PD
		- Chargedown: 0.12 -> 0.2
		- Adjusted firing FX
Ilorin:
-Removed ECCM from Ilorin
-Ilorin Rocket Speed: 150->50
-Burst Size: 4->8 (now fires entire clip at once)

Valken X:
- Removed Vulcan Cannon
- Laser Blade DMG: 125->200

-Valken:
- swapped light mortar for light assault gun
- System now affects all weapons
- armor: whatever it was before -> 25
- adjusted sprite yet again

-Kouto
- Revised weapon loadout
- Wing Size: 2->1

Aleste:
- Changed name of playable version to Aleste(S) to better distinguish playable and LPC version
- increased flux capacity
-Heavy Rynex Laser will no longer target fighters
-Dispersal Mortar will immediately detonate in close proximity to enemies
-Added Laser Blade as an option for right arm
-Added SCTR-40 Minigun as an option for right arm
-Laser Blade trails reduced to a more reasonable width/length
-Fixed Laser Blade not doing additional emp damage on hit
-Fixed Laser Blade damage not scaling with energy weapon mastery bonus
-Fixed bug where laser blade trails from different ships would merge into eldritch map spanning megatrails 


V1.4.8RC3
--------------
-Added landing beacon to Einhander
-Fixed alternating weapon bug with Garegga's Pulse laser
- Aleste:
	-Laser Blade:
		-Laser Blade attacks are now swings instead of stabs; should generally be easier to connect with
		-First strike with Laser Blade now slightly knocks back the user 
		-Added Hit and swing fx
		- Burst Damage is dealt on first strike
V1.4.8RC2
--------------
- Stopped recordbreaker from always being in the title screen battles
- Added more details on what fighter-related skills apply to Strikecraft to Strikecraft hmod desc
- Strikecraft will no longer kill themselves if the carrier they are on retreats
	- will retreat as well
- Fixed crash caused by Strikecraft retreating while on carrier

- Refit rate should be properly clamped and no longer go below 30% 
- Max Refit Time Penalty (w/ missiles equipped) 30%->35%
- Refitting at carrier now reduces that carriers replacement rate
- Reversed LPC removal from indies/persean/pirates
- Adjusted CR bar color to match native UI
- Added combat UI notification on CR loss for strikecraft when CR <= 40%
- adjusted starship legends hull damage impact on rating for aleste & einhander to account for repairs to hull
-added minor turret angle offsets to all weapons - should improve accuracy esp. against smaller objects like fighters and missiles

-Einhänder:
-DP: 8->11
-Phase Cost & Upkeep: 0.08->0.1
-Phase Damper Cooldown: 2->3
-Cr/Deployment ->40%
-Fixed bug where permanent time dilation would be applied when player-piloted einhander was destroyed in certain scenarios

-Garegga:
-DP: 8->9

-Garegga(TT):
-Added glow sprite to pulse lasers
-System: Plasma Jets -> Silversword
-Fixed incorrect turret arcs

-Aleste:
-Added glow sprite to Rynex Pulse Rifle
-DP: 5->6
-Ground Support Bonus: 25->15
-Stake Driver:
-Burst Size: 2->6
-Ammo: I forgot->18
-Stake Driver proj is now bit more visually prominent
-Minor fx/sfx tweaks to Laser Blade

V1.4.8
--------
-Added Overload FX + SFX for Strikecraft
-Fixed missing desc for exceliza grenade launcher
Aleste:
-Increased LS-99 Turn Rate
-Added missing desc for microburn
-Fixed aleste's microburn having 2 charges when it should have 3
- Added FX + SFX for Aleste's microburn

V1.4.7
---------

-Removed LPCs from vanilla factions since there's no easy way to control their usage in fleets
-Scavengers still have access

- Fixed missing description for Gallant LPC

- Cataphract(Hullmod)
	-Reduced Ground Support Bonus: 30->25
	-Ships with hullmod can no longer install Advanced Optics

- Aleste
	- Equipped missiles with per shot damage > 200 will now increase refit time(Multiplied by number of barrels for LINKED, by 2 for DUAL_LINKED, and by the number of missiles for MIRVs), up to 30%
	- AIleste equipped with LS-99 will actively attempt to use the melee weapon with ...varying degrees of success (Thanks Sinosauropteryx!)
	- Reduced flux cost of Dispersal Grenade

V1.4.6
----------
- Fixed hulls not being procurable in nex
- Soft removal of Einhander (LPC version)
- Soft removal of Recordbreaker
- added missing bp to nex starts; adjusted starting ships for carrier(small) start
- Lowered indie reputation requirement for meshan submarket to favorable (25->10)
- Submarket now only sells armaa ships, but stock has been reduced by 25%
- All blueprints should now be discoverable through exploration, or added to the pool of BPs the historian offers

- ADDITIONS:
	-Aleste(pilotable) - 5 DP
	-Mechanically similar to Einhänder
	-More modular, less flashy
	-2x universal hardpoints
	-Revamped sprite to more closely resemble high-tech,minor variations between pilotable and lpc version
	-left and right built-ins can be swapped
		- Laser Blade, 
		- Stake Driver, 
		- Pulse Rifle, 
		- Plasma Flamer, 
		- Heavy Pulse Rifle
		- Dispersal Grenade

----balance changes---

Altagrave:
	-fixed shotgun bug with altagrave[ex]
	-Increased DP: 15->18
	-Increased price to better match ships around DP value
	-removed emp emitter from modules
	-increased bullet HP for Vajra: 300->450

-Valken X:
	-renamed LS-99 to LS-2001
	-DPS: 300->125

-Valken:
	-Wing Size: 4->3
	-IR Pulse Laser(High Delay)->IR Pulse Laser
	-Fixed weird deco weapon spacing
	-minor sprite adjustments

-Ilorin:
	-minor sprite adjustments
	-Sol rocket pod: 15 ammo->8; burst size: 15->4
	-removed fast missile racks

-Strikecraft(Hullmod)
	- Refit Time: 20->30
	- Fix inordinately long refit time caused by landing with very little hull remaining
	- AI will return to carrier to resupply only when all weapon ammo is exhausted, previously would return as soon as one weapon was empty

-Einhänder:
	- Reduced scale of some on-hit effects
	- Armor: 300->275
	- DP: 6->8
	- Added High Maintenance Hullmod

V1.4.5d
----------
-Crushed the elusive FighterAI bug/crash, hopefully once and for all
-fixed bad logic that allowed Einhänder to land on enemy carriers
-add temporary workaround to vanilla's infinite overload bug affecting fighters 

V1.4.5
----------
- fixed crash caused by case sensitivity for linux (probably?)

-Einhänder:
	- Flux Caps: 1100->1000
	- Flux Diss: 175->200
	- Juno Mk. I
		-Burst Size: 8->10
	- AS Glaive:
		- DMG: 450->500
	- Will now opt for the nearest viable carrier to land on instead of traveling to one across the map
	- Can now land on station hangar modules
	- If friendly carrier with launch bays is targetted before activating autopilot, will attempt to use it to refit
	- Minimum refit rate can no longer reach lower threshold than 30%
	- Added random chatter text based on condition when landing(only if flagship)

V1.4.4
----------
- Reduced Trihänder spam in Tri-Tachyon fleets and markets
- Garegga:
	Ship System:
		-Weapon RoF: +30%->+40%
-Trihänder:
	-Pulse Laser Flux/Shot: 100->80

-Fixed up some descs, courtesy of Avanitia

-Added Altagrave(s) and Garegga to known hulls for the independent faction, at reduced frequency

-Garegga now can appear on black market
-Garegga + Trihander indevo support
-removed sound files that were in jar for some reason

-Einhänder:
	- DP: 6->7
	- Added slightly different after-action raid report based on if player is piloting an Einhänder or not
	- Fixed extra 1 DP being deducted on deployment
	-Replaced Repair/Rearm drone system with a modified version of Harupea's Combat Docking Module(https://fractalsoftworks.com/forum/index.php?topic=20415.0)
		-AIhänder will attempt to resupply at various thresholds determined by pilot personality type
		-Activating autopilot on player-controlled einhander will have it return to carrier if:
			- CR <= 40% or
			- HP <= 50%
			- non-ammo regen weapon exhausted
		- Once landed, 'assigned' the carrier as a mothership and will attempt to return to it in the future
		- Refit restores 20% CR and fully replenishes hp, armor, ammo, and PPT
		- Refit time is proportional to the carrier's replacement rate, with reduced rates increasing refit time
		- Destruction of the carrier will also destroy Einhänder
	-AI use should be much more viable now
	-Autopilot is immediately engaged upon opening Command UI
		-Should prevent crashes caused by null AI pointer/AIhänder ceasing to move with
		autopilot enabled
	-Juno Mk. I:
		DMG: FRAG->ENERGY; 
		-Range: 500->600
		-Cooldown: 1.6->1
		-Altered enhanced Juno projectile/fx to more closely resemble the other weapons in the Juno fam
		-110 DP/SHOT -> 85 DP/SHOT; 
		-FLUX/SHOT-> 55->85
	-Can now use Ship System without disabling Phase Damper Field
	-Phase Damper Field
		-Should now benefit from Phase Mastery
		-Time Dilation: 55%->85%
		-DMG Resistance: 35%->15%
		-Damage Dealt: +0% ->-15% when active
	-Energy Surge
		- Max Damage Bonus: 100%->70%
		- Can now use with defensive systems
	-AIhänder without any explicit orders assigned will retreat to nearest carrier with highest CR for repairs if HP or CR drops below a certain threshold
		-70% for cautious+timid
		-50% for steady 
		-30% for aggressive
		-10% for reckless

-Bihänder:
- Juno Mk. II Damage/Sec: 1500->1050

-Set Altagrave/garegga to priorityhulls at the special market on New Meshan, which should generally increase their frequency there
-Fix Altagrave Karma desc to include details on both variants
-Fix Altagrave[G] desc
-Fix Altagrave[C] desc
-Increase Flux cap/diss for Altagrave[G]
-Caps:5850->6000
-Dis:360->400
----------
V1.4.3c
-----------
- minor hullmod description fixes
- fixed occurence where Einhänder would suffer an infinite overload
-added magiclib and lazylib as dependencies in modinfo.json


V1.4.3b
-----------
-  Trihänder Pulse Lasers reflect the statistics of .95a pulse laser
- fixed crash caused by emp arc with Einhander Experimental

V1.4.3
-----------
- 0.95a compat 
- removed some glib stuff temporarily
- removed some drone stuff since it was causing a crash 
V1.4.2
-----------
- Fixed Altagrave[G]'s getting [EX]'s backpack and EX being CC'd in missing modules. / [C] backpack module missing drones

V1.4.1
------------
-Fixed crash caused by duplicate class name

V1.4
------------
ADDITIONS:
	-Aleste:
		-  14 OP, single wing with two support drones
		-  +20 ground bonus to equipped carrier
		-  Primarily energy+frag DPS with some KE and HE
	-Gallup-9A:
		-  15 op 
		-  wingsize: 2 single shot sparkly beam bomber
		- cannot move or turn during firing sequence, very vulnerable
	-Garegga:
		- Dakka
		- 1x small energy, 2x small composites, 2x built-ins
		- Short-Range
		- 8 DP
	-Trihänder:
		- Tri-tachyon modified Garegga
	-Einhänder:
		- Single wing - 25 OP
		- +30 ground bonus to equipped carrier
		- only can be obtained by finding its blueprint through normal exploration
		- only here in prep for some 0.95a modding features, may be altered considerably 

-Added two new Altagrave variants, revised [C] variant
	-Altagrave[G]
		-Exceliza Grenade Launcher, 300 shield radius, Plasma Jets as ship system
		-upsized central small hybrid to medium energy
	-???
		- HVB, appears at level 30 + certain fleet strength
	-Altagrave[C]
		- Syrinx MRM
		- Backpack Module: Active Flare Launcher
		- Karma[C]: Grants all friendly ships(except itself) within 1000 SU a degrading time dilation bonus based on  the total of the karma gauge. At full charge, the bonus granted is 15%, and maximum duration is 20 seconds.

-Added 3 new nex starts for base Altagrave, Altagrave[G], and Garegga

MISC:
-Reduced LPC frequency chance in fleets across the board by 40-60% depending on rarity
-Fixed LPCs never appearing in PL markets/fleets due to a incorrectly named faction file
-Implemented MagicSettings missile resistance modsetting for Altagrave's system
-Changed portrait for meshan admin + fixed missing parameters in cataphract defense base planet condition
-armaa lpcs should now appear in persean league markets/fleets

Valken-X:
- nerfed paintjob saturation by 40%
- minor gfx changes to make head more prominent/distinguishable
-Removed omni shields
-HP -> 575->850
- Light Mortar -> Ion Cannon(High Delay)
- Assault Chaingun(Fighter) DMG -> 45->60
-LS-99 MOONLIGHT Range: 35->45; Cooldown: .33->1 
	-interruptibleBurst -> true
	-beamFireOnlyOnFullCharge -> true
	-empDamage: 500->50

Einhänder:
-Removed Delicate Machinery
-AS Glaive Ammo:4->10
-Hullmods can no longer be equipped to the dummy drone (these had no effect, so nothing was lost)
- Pila Drone system: Teleporter->Temporal Shell(2x dilation)

Bihänder:
- Ship System: Phase Damper Field -> None
	- Can actually be killed before firing beam now

Kouto:
- Engagement Range: 2000 -> 4000
- Plasma Gatling: 
	- Range: 500->600
	- Damage: 15->30
	- Recoil:
		-Min: 15->0
		-Max: 30->10

	- Damage Type: ENERGY-> FRAG
-Shield Radius: 70->360

Altagrave:
-There appears to be a bug with ships that use modules that makes them much more aggressive than they should normally be. This seemingly is fixed in 0.9.5a, but if you want to prevent this behavior from occuring with AItagrave's, destroying their back module with a console command or assigning them a ship to escort resolves the issue.
-For this reason, modules no longer confer any statistical bonus/malus to speed/manuverability.

-Fixed weird interaction with shipsystem and missiles using custom AI
-DP: 14->15
-Shield Radius -> 120
-Seeker Damage 250->150
-HP -> 2500->3000
-Flux Cap -> 5750-> 5850
-Armor -> 450 -> 375
-Leg modules: system -> low-grade EMP
-Backpack Module: System-> Pila UWS
-Modules vent & overload with core




V1.2.4b
------------
-Cleaned up some descriptions courtesy of Avanitia
-Increased hull frequency for Altagrave on New Meshan's market by 15%

Fighters:

-Reduced Valken engagement range to standard 4k su

-Reduced Valken wing size from 5->4

-Increased Ilorin engagement range to 4k su, speed 110->140, OP->16

Bihänder:
-Increased Juno Mk. II DPS from 400->1.5k


Altagrave:
-Increased DP: 12->14
-Modules no longer consume additional fleet points on deployment

-Added BACK_OFF AI flag to encourage AI altagrave's to back down in certain scenarios. It's not 100% reliable but should improve survivability somewhat. AI gonna AI

-Previouly ship system would absorb up to 2000 points of damage, and when maxing out gauge generate 10 projectiles worth 2.5k damage in total, even if the amount absorbed exceeded the threshold.

-Adjusted system to generate a scaling number of seeker projectiles based off the total amount of damage absorbed, up to 5,000 at 20 projectiles. The default amount generated has been decreased to 8 from 10 to equate to the base threshold of absorbed damage, however.

-added small trail fx to seeker projectiles.

V1.2.3
---------
-Altagrave's built in hullmod is now properly hidden and wont be found as loot

-Attempted to improve AI performance with Altagrave:
	-Generally speaking, Alta is flux hungry, and wont fire main gun after a certain threshold
	-AI cannot perceive that modules are important to its survival, very often eats hits to the side of modules that easily could have been avoided by a player as a result

	-Converted Front Shield to Omni Shield
		-Omni Shield doesnt extend to some parts of the legs, for now this is intentional
	-Increased collision radius 67->100
	-increased shield radius-> 67->90
	-base flux: 5500->5700
	-base dissipation: 350->360 
	-Shield Efficiency: 0.7->0.66
	-Vajra flux/shot: 120->110

-cr recovery %/day: 8%->6%

V1.2.2RC2
--------
Nex:

-removed random folder in mission folder

-Super Ship start: Replaced Altagrave and Condor with Altagrave[C] and Wayfarer
	-Differences between the two are mostly superficial, [C] has built-in Op Center and is more suitable as a flag

-Added Ground Support bonus for new fighter wings. Non-cataphract fighters were conferring additional bonus if a single cataphract wing was added, this was fixed.

-Added Juno Mk. III, a modular medium fire support beam cannon. Available on New Meshan, may need further tweaks

Altagrave:
-Standard Altagrave now has very low chance of appearing in scavenger fleets

-Adjusted base variant auto groups

-Karma Seekers: Damage type to Kinetic
	-reverted damage back to 250 from 200

-Added additional small hybrid mount; increased OP by 5

-Altagrave should be less inclined to suicidally charge at things 2x its size or into swarms of enemies, not 100% what caused this issue, may have some relation to weapon loadout

-Added normal maps for gLib support and shadow effect to some turrets/decos

-Limb animation now slightly affected by turning/strafing left or right

-new head sprite for Altagrave[C]

Vajra:
	-Drastically reduced tracking effect of Vajra, should be much easier to hit smaller ships, both under player and AI control
	-Increased projectile speed: 950->1000

-increased armor on all modules to match parent hull armor: 400->450
	- Won't do much against heavy hits, but will help mitigate issue with getting papercut to death by fighters

-swapped active flare launcher to Low-Grade EMP Emitter (Same system as Recordbreaker) on backpack module

-Modules now mirror the hullmods applied to the parent ship

-Increased OP of all modules to 1000 to prevent any unintended behavior caused by hullmods that readd themselves in certain scenarios

-Added hullmod describing modules and their effects

-Altagrave[C] would never spawn in markets with modules, is now a seperate hull instead of being skin-based [Used exact same ID for the hull that was used by the skin file, so save compatible; tested and confirmed myself]

Recordbreaker:
Armor: 45->30
HP: 450->400

Valken-X:
-Fixed deco weapon being listed in Armaments
-Plasma Blade was set at an ammo of 1, removed ammo limitation

Bihänder:
-Altered Juno Mk. II for better visual consistency with other Juno family weapons


V1.2RC2
--------
-Removed Omni-Shields from Altagrave's legs, increased armor by 200, HP by 200
-Gave Altagrave[C] a Default Role so that it actually appears in markets
-All projectiles weren't properly being tallied when absorbed, corrected issue
-Karma increases much faster, increased threshold as a result: 1000->2000
-Reduced amount of projectiles generated from Karma: 15->10
-Altagrave System projectiles DMG: 250->200
V1.2RC1
----------

New Ship: Altagrave
- 12DP with modules that increase speed while they are intact;
- 1x Medium Synergy, 1x Small Universal
- 2x built-ins
	-Vajra
		energy-based projectile weapon with some missile characteristics, has slight homing capability, can be intercepted
- System: K.A.R.M.A
	-Absorbing projectiles increases "Enlightenment"
	-Enlightenment grants a scaling bonus to Weapon RoF and Flux Cost Reduction as damage absorbed increases, up to 30%
	-Exceeding/reaching max karma generates an explosion that damages anything within 300 su and flings homing projectiles
		-Projectiles will home in on selected target after a few second delay, otherwise random target is chosen
	-Bonus is lost once karma is maxed.

New Mission: Battle of Meshan 1A9
- Survive against waves of redacted

Nexerelin:
-Added start with Altagrave
-Added basic BP to all starts

New Wing: Valken-X - 10 OP
System: Overboost
	- brief, but significant increase to speed + manuverability for 1 second
- Assault Chaingun(F), 50% potency of original ACG fired in 5 shot bursts
- LS-99 MOONLIGHT, short-range plasma blade
- Wing Size: 2
- Not very effective vs shields, but overboost + the LS-99 makes it lethal at close range and an effective dogfighter

New Wing: Recordbreaker - 10 OP
- Fast interceptor with weak EMP emitter suited for anti-fighter/missile and escort. Best paired with other fighter types
- Wing Size: 2

-New Star System: Gamlin
-New Meshan, Size 5 Colony
-Has special submarket accesible at high indie rep that sells all cataphracts, rarely Einhander or Altagrave will appear

-Adjusted spawn rate of all wings, they should actually appear occassionally in various fleets

Einhänder:
- fixed fx bug caused during interaction with some approlight systems
- fixed crash caused when running simulation during armaa mission with Einhänder
- Phase Damper Field Time Dilation: 60%->55%
- Phase Damper Field Beam Resistance: 50%->55%
- Phase Damper Field Base Resistance: 40%->35%
- Vulcan Cannon Turn Rate: 90 -> 75
- AS Glaive Range was 650, when should have been 550->550
- Einhander receives damage bonus as flux level increases when using ship system. Exceeding flux leads to overload
- Repair Drones will now always deploy so long as there are no enemies within 2000 su

--Fighters--
-General all-around buffs. They should be more effective overall

Valken:
-Fixed bug that prevented valken from ever spawning in fleets, or appearing in simulator
HP: 300->450
Armor: 15->30
Flux Dissipation:60->80

Kouto: 
DR-79 Plasma Gatling: 10 -> 15 DMG
	-Bullet Size reduced by 50%

Overshield granted 10x damage resistance instead of 5x, corrected to proper value
Increased flux stats for overall improved shield efficiency
Reduced Seeker flare count to 1
HP-> 425-> 500
Armor -> 50-> 35
Max Flux -> 700
Dissipation ->  150
OP:12->13
Refit Time: 25->15

Ilorin:
OP: 12->14
Crew Size: 1->3

-removed Juno. Mk III from drop table

-added armaa_ prefix to all ship systems

Known Bugs:
- Einhänder damage weighting with Starship Legends gets weird in some scenarios, will be fixed in next version of legends 


V1.1.4
---------
- Fixed damage bug with Starship Legends (
- Added Nex Carrier(small) start
- Can now immediately issue orders to Einhänder instead of having to open Command UI and unpausing
- Fixed "bug" where Einhänder being the last living friendly remaining would result in an instant loss 

Cataphract Complement:
-Each wing now grants a different ground support bonus:
Valken, Kouto: 4
Bihander: 16
Ilorin: 12

Einhänder:
-Phase Damper Field Time Dilation: 50%->60%
-Phase Damper Field Beam Resistance: 40%->50%
-Beam Coat Beam Resistance: 35%->40%
-Beam Coat tooltip stated total stacking beam resistance was 77% when it was in reality around 60%, corrected to reflect new values. Max resistance is 70%
-With these changes beam heavy enemies and burst PD beams shouldn't be as insurmountable, while remaining a threat
-AS Glaive size: Medium->Small, this will give it a more accurate (and reduced weapon health) HP for its physical size
-Increased texture scroll speed on AS Glaive's trail
-Added role for Homing Laser
-Vulcan Cannon: Chargedown: .1 -> 0.05, Damage: 35->25, Range: 350->250; Replaced projectile for vanilla vulcan_shot

Kouto:
-Added System: Overshield
-Shield Radius: 90->75
-Blank Torso Weapon-> Seeker Flares

Valken:
-Wing Size: 3->5
-IR Pulse Laser: Energy/Shot: 0->50
-Flux Dissipation: 25->60
-Adjusted sprite
-Vulcan Cannon: Chargedown: .1 -> 0.05, replaced projectile for vanilla vulcan_shot
-Refit Time: 10->9
-Changed Engine Style from lowtech to midline

Ilorin:
-Rocket Pod Ammo: 9->15
-Added System: Fast Missile Racks
-Minor description tweaks
-replaced midline tags with low tech

V1.1.3
------------
-small fx adjustment on Einhänder + Bihänder
-Add system desc for P-Damper Field (Bihander ver)
-Fix bug where Pila would never equipped their assigned weapon

V1.1.2
---------
-Fixed crash when deploying AI-controlled Einhänder in certain order
-Fixed crash when deploying Einhänder without any weapons assigned to the Pila

V1.1.1
---------
Einhänder:
-Increase turn rate 100->110
Offense Mode: Speed+Manuverability:40%->50%
Corrected system desc; stated 30% dmg increase, replaced with more accurate description
Juno DMG: 100->110;
DP: 7 -> 6 
-Juno hit fx with system active reduced in intensity
-misc fx adjustments
- AI controlled Einhänder will autonomously return to carrier at low CR
---------
Bihander:
-System: Damper-> Production P-Damper
-HP: 700
-Armor: 150->200
Replaced Micromissile -> Homing Laser
---
v1.1
-------------------
-Added fake_fighter tag to Einhänder, which will resolve some targeting and fuze logic bugs with several THI scripted weapons and how they interact with frigates that setHullSize to fighter via script. (Note to other mods that might do similar things, please add the "fake_fighter" tag to the pertinent vessels in ship_data.csv! credit to MesoTroniK )
-Fixed crash with Einhänder caused by attempting to retreat from the top of the map during 'escape' battles
-Fixed bug that wiped faction blueprints from Tritachyon/Independent fleets
-Adjusted rarity of all fighters so they are less common in NPC fleets
-Added compatibility for some IndEvo features ( i think )

=== Einhänder ===
-Synergy Mount has been axed. It just looks too weird with some combos. I'll probably make an alternate version with mounts replacing some built-ins
-Added built-in drone wing with customizable small hybrid mount as replacement.
-Can equip weapons up to 9OP, but flux reserves might not be enough for it to actually fire them all (I.E) you can actually mount an AM blaster on the drone, but it can't actually fire. I'll add hard restrictions to this later
-Added Phase Field hullmod
==-New System-==
-Phase Charge->Offense Mode
Damage Bonus: 35%->30%
Speed Bonus: 65%->40%
Manuverability Bonus: 50%->40%
Reduce RoF by 50%
Increases Recoil by 100%
replaces Juno standard projectile with enhanced alternative that does 135 Energy Damage and 200 EMP Damage with arc chance
-----
-small sprite alterations

OP: 35->30
HP: 700->750
-Reduced armor from 310->300.
-Increased turn accel 115 -> 180
-Top Speed: 160->165
-CR to Deploy: 16%->20%
-CR loss per second: .25%->.20%
-Turn rate: 115 -> 120
-Increased decel 120->170
-Increased accel 170->190
-Max Flux: 1200->1100 
-Mass:  75-> 60
-AS Glaive Range: 500->550; Damage: 400->450; Ammo: 4->6
-Juno DMG 90 -> 100
-Juno EMP DMG 60->0
-Juno Chargeup 1.05-> 0.525
-Juno Chargedown 2->1.6
-Juno Min Spread 0->3
-Juno Max Spread 10->30
-Burst size: 9->8

-Homing Laser bonus damage chance: 50%->25%
-Burst size: 4->2

=== Valken ===
-Increased speed to 230
-Hull: 550>300
-Armor: 40->15
-Wing Size: 3->4
- Pulse Laser DMG: 50->30
- Light Mortar Ammo: 0-> 15
- Replacement Time: 8-> 12


=== Kouto ===
Shield Efficiency 75%->100%
Blazer Rifle -> DR-79 Gatling Plasma
=== Bihänder ===
- New Sprite
- OP: 12->14
- Wing Size: 2->1
- AP Javelin -> Juno Mk. II: Burst Charge Beam
= Light Machine Gun -> Micro Rail Rifle
- added new sfx + fx

-Added new wing: Ilorin 
- High HP and Armor
- Slow speed & manuverability
- Armed with annihilator Rocket Pod & Autocannon

-Added new wing: Pila UWS
Short-range drone wing used by the Einhänder

=========================
v1.0.6
------------------------
-Cataphract Complement now grants +4 bonus for every cataphract wing
-Fixed bug where ships without the CARRIER tag would not receive Cataphract Complement hullmod
-Added info on ammo resupply drone for strikecraft hullmod

====================
1.0.5
--------------------
-Any carrier with at least 1 Cataphract wing will now get the hullmod "cataphract complement" that grants small ground support bonus, applied when visiting markets 

=== Einhänder ===
-can start active vent during phase instead of having to exit phase first, and then venting.

- Increased OP from 30 -> 35

- Added small SYNERGY hardpoint

- Decreased armor from 325 -> 310.

-Juno-
- Increased Juno's per shot damage from 83 -> 100
- Burst size decreased 16->9
- EMP Damage Increased 30->60 
- Juno now has a 25% chance to create EMP arcs on impact that do 3x EMP damage(150) and 1/2 its per shot damage(50) in energy
- Chargedown decreased 3->2

-AS Javelin renamed -> AS Glaive ((II has weapon with very similar name))
- Damage decreased from 500 -> 400
- Ammo decreased from 10 -> 4
- chargedown decreased from 6->3
- EMP Damage increased 0 -> 200
- No longer fully reloads between engagements

-Homing Laser-
- Chargedown decreased from 12->10

- Limited Ammo weapons can be reloaded by returning to carrier, if the weapons max ammo count is at least 2 (no spamming reapers ): )

- Einhänder has been fully switched to fighter hullsize, outside of a few edge cases. Gameplay wise this means it **should** suffer or benefit from all the same effects that fighters do, including custom ones (so long as that effect checks if a ship is a **fighter** and not checking its **fighter_wing**), including custom ones, and AI will react and evaluate it as as a fighter also. Thanks to MesoTronIk for bringing this to my attention.

-Fighter Doctrine Lv2 bonus now applies to the Einhänder as well

=== Valken === 
-Fixed severed Valken arms showing up in markets.

-Replaced Valken's AAF system with new system: GU-14 Firebolt Feeder
+25% Damage, +25% ROF, -10% Flux Cost for ballistics

----------------
1.0

-initial release