package com.prayalert;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import lombok.Getter;
import net.runelite.client.ui.overlay.infobox.InfoBox;

class PrayAlertInfobox extends InfoBox
{
	private final PrayAlertPlugin plugin;

	@Getter
	private final com.prayalert.model.OverheadPrayer displayed;

	@Getter
	private final boolean ruinous;

	PrayAlertInfobox(BufferedImage image, PrayAlertPlugin plugin, com.prayalert.model.OverheadPrayer displayed, boolean ruinous)
	{
		super(image, plugin);
		this.plugin = plugin;
		this.displayed = displayed;
		this.ruinous = ruinous;
	}

	@Override
	public String getText()
	{
		PrayAlertPlugin.AlertView alert = plugin.getAlert();
		if (alert == null || !plugin.showInfoboxCountdown() || !alert.hasCountdown())
		{
			return null;
		}
		return Integer.toString(alert.getTicksLeft());
	}

	@Override
	public Color getTextColor()
	{
		PrayAlertPlugin.AlertView alert = plugin.getAlert();
		if (alert != null && alert.isOneTick() && plugin.recolourWhenOneTick())
		{
			return com.prayalert.model.OverheadPrayer.ONE_TICK_COLOR;
		}
		return Color.WHITE;
	}

	@Override
	public String getTooltip()
	{
		PrayAlertPlugin.AlertView alert = plugin.getAlert();
		if (alert == null || alert.getTooltip() == null)
		{
			return "";
		}
		return alert.getTooltip();
	}

	/**
	 * Shown until SpriteManager has the prayer icon. A blank image looks like a missing infobox.
	 */
	static BufferedImage fallbackIcon(com.prayalert.model.OverheadPrayer prayer)
	{
		int size = 30;
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		graphics.setColor(prayer.color());
		graphics.fillOval(1, 1, size - 2, size - 2);
		graphics.setColor(Color.WHITE);
		graphics.setFont(graphics.getFont().deriveFont(Font.BOLD, 9f));
		String text = prayer.shortLabel();
		FontMetrics metrics = graphics.getFontMetrics();
		while (text.length() > 1 && metrics.stringWidth(text) > size - 4)
		{
			text = text.substring(0, text.length() - 1);
			metrics = graphics.getFontMetrics();
		}
		int x = (size - metrics.stringWidth(text)) / 2;
		int y = (size - metrics.getHeight()) / 2 + metrics.getAscent();
		graphics.drawString(text, x, y);
		graphics.dispose();
		return image;
	}
}
