package com.prayalert;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("prayalert")
public interface PrayAlertConfig extends Config
{
	@ConfigItem(
		keyName = "showPrayerInfobox",
		name = "Show prayer infobox",
		description = "Show an infobox for the overhead that blocks the incoming attack"
	)
	default boolean showPrayerInfobox()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showTickCountdownInfobox",
		name = "Show tick countdown on infobox",
		description = "Show remaining ticks on the infobox for projectiles and attack timers"
	)
	default boolean showTickCountdownInfobox()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showTickCountdownPrayerTab",
		name = "Show tick countdown on prayer tab",
		description = "Draw remaining ticks on the highlighted prayer for projectiles and attack timers"
	)
	default boolean showTickCountdownPrayerTab()
	{
		return true;
	}

	@ConfigItem(
		keyName = "recolourOneTick",
		name = "Recolour when 1 tick left",
		description = "Use orange for the infobox text and prayer highlight outline when one tick remains"
	)
	default boolean recolourOneTick()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "meleeHighlight",
		name = "Melee highlight",
		description = "Colour and opacity of the melee prayer tab overlay",
		position = 5
	)
	default Color meleeHighlight()
	{
		return new Color(255, 0, 0, 80);
	}

	@Alpha
	@ConfigItem(
		keyName = "rangeHighlight",
		name = "Range highlight",
		description = "Colour and opacity of the range prayer tab overlay",
		position = 6
	)
	default Color rangeHighlight()
	{
		return new Color(0, 255, 0, 80);
	}

	@Alpha
	@ConfigItem(
		keyName = "mageHighlight",
		name = "Mage highlight",
		description = "Colour and opacity of the mage prayer tab overlay",
		position = 7
	)
	default Color mageHighlight()
	{
		return new Color(0, 0, 255, 80);
	}
}
