# Pray Alert

Pray Alert highlights an overhead prayer when a configured animation, projectile, or attack timer fires. You add the ids yourself. No bosses are built in. It is a local plugin.

## Sidebar

The Pray Alert panel has two add buttons. `+` adds an entry named with the next free number, such as Entry 1. The folder button adds a folder the same way. New entries and folders start collapsed. Open one to edit it. There is no separate edit screen.

`x` removes the entry, folder, or rule it sits on. Entries and folders ask before they are deleted. A rule's `x` removes that rule. The `x` is the same size as `+`.

Each entry and each folder has an on/off pill. A folder that is off disables every entry inside it. An entry that is off does not fire. Changes are saved as you make them.

## Animations

An animation rule is an animation id and the overhead that blocks that hit. The prayer choices are Melee, Range, and Mage.

Two pills choose how long the highlight stays up. They cannot both be on. **Until animation ends** is on for a new rule. The highlight stays until that NPC changes animation. **For N ticks** is off. Turning it on turns the other pill off and enables the tick box. The count starts at 2 and must be at least 1. Animation alerts do not show a tick countdown.

## Projectiles

A projectile rule is a projectile id and the overhead that blocks it. The highlight follows the soonest matching projectile that is coming at you, and it shows the ticks still left. It drops off when the projectile ends.

## Attack timers

An attack timer watches an NPC for an attack animation, then counts down to the next time that animation should play. The count is the gap until the next attack. The hit that just started the timer is not the one being counted.

The NPC can be an id or a name. An id is used when it is filled in. A blank id falls back to the name, ignoring case. The animation id is the attack animation. The delay is in ticks, at least 1, and starts at 5.

**Learn delay** is off by default, and the delay box stays editable. Turn it on after the NPC and animation are set. The delay box greys out. The plugin waits until it has seen that animation three times, takes the middle gap, saves that number, and starts the countdown on the attack that locked it in. Later attacks reset the countdown to the saved delay. Turning Learn delay on again measures from scratch. Turning it off leaves the saved number editable.

The countdown is drawn while it is running. When it reaches 0 the highlight goes away, so a safespot that stops the NPC attacking clears the overlay. The next time that NPC plays the attack animation, the countdown starts again. It also clears when the NPC dies, despawns, or the entry is disabled.

## Which alert is shown

When more than one alert is active, one of them is shown:

1. The soonest projectile aimed at you.
2. Otherwise the newest animation alert.
3. Otherwise the attack timer with the fewest ticks left.

## Prayer tab

While the prayer book is open, the prayer you need is filled and outlined. Melee, Range, and Mage each have a colour in the plugin settings, and the colour includes opacity. The outline uses that same colour at full strength. With **Recolour when 1 tick left** on, a projectile or attack timer that has 1 tick left uses an orange outline and orange countdown text. The fill stays the colour you picked.

**Show tick countdown on prayer tab** draws the remaining ticks on that prayer. Animation alerts have no number. Closing the prayer book hides this highlight.

The same highlight works on the normal book and the ruinous book. Melee, Range, and Mage map to Protect from Melee, Missiles, and Magic, or to Dampen Melee, Ranged, and Magic.

## Infobox

**Show prayer infobox** adds one infobox for the prayer currently shown. It uses the prayer book icon. Until that icon is loaded, a coloured circle with a short label stands in. The infobox stays up when the prayer book is closed.

**Show tick countdown on infobox** puts the remaining ticks on projectiles and attack timers. Animation alerts leave the text blank.

## Curse

The Curse pill on an entry is for Phosani's Nightmare. It is off by default. Turning it on is enough. The plugin watches for the curse on its own.

While the curse is active, the highlighted button is rotated from the prayer that actually blocks the hit. A hit that needs Mage highlights Melee. A hit that needs Range highlights Mage. A hit that needs Melee highlights Range. The infobox tooltip names the button to click and the prayer that click activates.

The curse starts when she plays animation 8599, the confusion attack, or when the game says she has cursed you and shuffled your prayers. It ends when the game says the curse has worn off, or when the curse-end sound plays. That sound is 4260, with 4330 as the paired sting. If neither end signal arrives, it clears after her next 5 attacks. The attack that spends the last one is still shown shuffled. Leaving the area, or the NPC that cursed you dying, also clears it.

## What is saved

Entries, folders, rules, pills, colours, and a learned attack delay are saved. Curse charges and the attacks watched while Learn delay is still measuring are kept only for the current session.
