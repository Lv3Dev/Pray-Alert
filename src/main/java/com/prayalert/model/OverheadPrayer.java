package com.prayalert.model;

import java.awt.Color;
import lombok.Getter;
import net.runelite.api.gameval.SpriteID;

@Getter
public enum OverheadPrayer
{
	PROTECT_FROM_MELEE("Protect from Melee", "Dampen Melee", 43, 71),
	PROTECT_FROM_MISSILES("Protect from Missiles", "Dampen Ranged", 40, 69),
	PROTECT_FROM_MAGIC("Protect from Magic", "Dampen Magic", 37, 67);

	public static final Color ONE_TICK_COLOR = new Color(255, 140, 0);

	private final String displayName;
	private final String ruinousName;
	private final int normalLevel;
	private final int ruinousLevel;

	OverheadPrayer(String displayName, String ruinousName, int normalLevel, int ruinousLevel)
	{
		this.displayName = displayName;
		this.ruinousName = ruinousName;
		this.normalLevel = normalLevel;
		this.ruinousLevel = ruinousLevel;
	}

	public String buttonName(boolean ruinous)
	{
		return ruinous ? ruinousName : displayName;
	}

	public String shortLabel()
	{
		switch (this)
		{
			case PROTECT_FROM_MELEE:
				return "Melee";
			case PROTECT_FROM_MISSILES:
				return "Range";
			case PROTECT_FROM_MAGIC:
			default:
				return "Mage";
		}
	}

	/**
	 * Phosani's curse rotates which button activates the needed overhead.
	 * Magic is shown on the melee button, missiles on the magic button, and melee on the missiles button.
	 */
	public OverheadPrayer shuffled()
	{
		switch (this)
		{
			case PROTECT_FROM_MAGIC:
				return PROTECT_FROM_MELEE;
			case PROTECT_FROM_MISSILES:
				return PROTECT_FROM_MAGIC;
			case PROTECT_FROM_MELEE:
			default:
				return PROTECT_FROM_MISSILES;
		}
	}

	public Color color()
	{
		switch (this)
		{
			case PROTECT_FROM_MELEE:
				return Color.RED;
			case PROTECT_FROM_MISSILES:
				return Color.GREEN;
			case PROTECT_FROM_MAGIC:
			default:
				return Color.BLUE;
		}
	}

	public Color fill()
	{
		Color color = color();
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), 80);
	}

	public int spriteId(boolean ruinous)
	{
		if (ruinous)
		{
			switch (this)
			{
				case PROTECT_FROM_MAGIC:
					return SpriteID.IconPrayerZaros01_30x30.DAMPEN_MAGIC;
				case PROTECT_FROM_MISSILES:
					return SpriteID.IconPrayerZaros01_30x30.DAMPEN_RANGED;
				case PROTECT_FROM_MELEE:
				default:
					return SpriteID.IconPrayerZaros01_30x30.DAMPEN_MELEE;
			}
		}

		switch (this)
		{
			case PROTECT_FROM_MAGIC:
				return SpriteID.Prayeron.PROTECT_FROM_MAGIC;
			case PROTECT_FROM_MISSILES:
				return SpriteID.Prayeron.PROTECT_FROM_MISSILES;
			case PROTECT_FROM_MELEE:
			default:
				return SpriteID.Prayeron.PROTECT_FROM_MELEE;
		}
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
