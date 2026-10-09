package com.prayalert;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import com.prayalert.model.OverheadPrayer;

class PrayAlertOverlay extends Overlay
{
	@Inject
	private PrayAlertPlugin plugin;

	@Inject
	private PrayAlertOverlay()
	{
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGH);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (plugin == null)
		{
			return null;
		}

		PrayAlertPlugin.AlertView alert = plugin.getAlert();
		if (alert == null || alert.getDisplayed() == null)
		{
			return null;
		}

		Widget widget = plugin.prayerWidget(alert.getDisplayed());
		if (widget == null || widget.isHidden())
		{
			return null;
		}

		Rectangle bounds = widget.getBounds();
		if (bounds == null || bounds.width <= 0 || bounds.height <= 0)
		{
			return null;
		}

		boolean oneTick = alert.isOneTick() && plugin.recolourWhenOneTick();
		Color outline = oneTick ? OverheadPrayer.ONE_TICK_COLOR : plugin.prayerOutline(alert.getDisplayed());

		Graphics2D g = (Graphics2D) graphics.create();
		try
		{
			g.setColor(plugin.prayerFill(alert.getDisplayed()));
			g.fill(bounds);
			g.setColor(outline);
			g.setStroke(new BasicStroke(2f));
			g.draw(bounds);

			if (plugin.showPrayerTabCountdown() && alert.hasCountdown())
			{
				drawCountdown(g, bounds, Integer.toString(alert.getTicksLeft()), oneTick ? outline : Color.WHITE);
			}
		}
		finally
		{
			g.dispose();
		}
		return null;
	}

	private static void drawCountdown(Graphics2D graphics, Rectangle bounds, String text, Color color)
	{
		graphics.setFont(FontManager.getRunescapeBoldFont());
		FontMetrics metrics = graphics.getFontMetrics();
		int x = bounds.x + (bounds.width - metrics.stringWidth(text)) / 2;
		int y = bounds.y + (bounds.height - metrics.getHeight()) / 2 + metrics.getAscent();
		graphics.setColor(Color.BLACK);
		graphics.drawString(text, x + 1, y + 1);
		graphics.setColor(color);
		graphics.drawString(text, x, y);
	}
}
