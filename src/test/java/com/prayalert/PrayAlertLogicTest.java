package com.prayalert;

import com.google.gson.Gson;
import com.prayalert.model.AnimationRule;
import com.prayalert.model.AttackTimerRule;
import com.prayalert.model.DurationMode;
import com.prayalert.model.Entry;
import com.prayalert.model.Folder;
import com.prayalert.model.OverheadPrayer;
import com.prayalert.model.PrayAlertSave;
import com.prayalert.model.ProjectileRule;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.junit.Assert;
import org.junit.Test;

public class PrayAlertLogicTest
{
	@Test
	public void ticksLeftUsesThirtyClientCycles()
	{
		Assert.assertEquals(30, AlertMath.CYCLES_PER_TICK);
		Assert.assertEquals(0, AlertMath.ticksLeft(0));
		Assert.assertEquals(0, AlertMath.ticksLeft(-10));
		Assert.assertEquals(1, AlertMath.ticksLeft(1));
		Assert.assertEquals(1, AlertMath.ticksLeft(30));
		Assert.assertEquals(2, AlertMath.ticksLeft(31));
		Assert.assertEquals(2, AlertMath.ticksLeft(60));
	}

	@Test
	public void curseRotatesTheDisplayedOverhead()
	{
		Assert.assertEquals(OverheadPrayer.PROTECT_FROM_MELEE, OverheadPrayer.PROTECT_FROM_MAGIC.shuffled());
		Assert.assertEquals(OverheadPrayer.PROTECT_FROM_MAGIC, OverheadPrayer.PROTECT_FROM_MISSILES.shuffled());
		Assert.assertEquals(OverheadPrayer.PROTECT_FROM_MISSILES, OverheadPrayer.PROTECT_FROM_MELEE.shuffled());
		Assert.assertEquals(OverheadPrayer.PROTECT_FROM_MAGIC, OverheadPrayer.PROTECT_FROM_MAGIC.shuffled().shuffled().shuffled());
	}

	@Test
	public void saveRoundTripOmitsCurseCharges()
	{
		PrayAlertSave save = new PrayAlertSave();
		Folder folder = new Folder();
		folder.setTitle("Bosses");
		folder.setEnabled(false);

		Entry entry = new Entry();
		entry.setTitle("Phosani");
		entry.setEnabled(true);
		entry.setCurseEnabled(true);

		AnimationRule animation = new AnimationRule();
		animation.setAnimationId(8595);
		animation.setPrayer(OverheadPrayer.PROTECT_FROM_MAGIC);
		animation.setDurationMode(DurationMode.FOR_TICKS);
		animation.setTicks(3);
		entry.getAnimationRules().add(animation);

		ProjectileRule projectile = new ProjectileRule();
		projectile.setProjectileId(1767);
		projectile.setPrayer(OverheadPrayer.PROTECT_FROM_MISSILES);
		entry.getProjectileRules().add(projectile);
		folder.getEntries().add(entry);
		save.getFolders().add(folder);

		String json = new Gson().toJson(save);
		Assert.assertFalse(json.contains("charges"));
		Assert.assertFalse(json.contains("curseAnimationId"));
		Assert.assertFalse(json.contains("curseVarbitId"));
		Assert.assertFalse(json.contains("curseAttacksUntilClear"));
		Assert.assertTrue(json.contains("\"enabled\":false"));
		Assert.assertTrue(json.contains("\"curseEnabled\":true"));

		PrayAlertSave loaded = new Gson().fromJson(json, PrayAlertSave.class);
		loaded.normalize();
		Entry loadedEntry = loaded.getFolders().get(0).getEntries().get(0);
		Assert.assertEquals("Phosani", loadedEntry.getTitle());
		Assert.assertTrue(loadedEntry.isCurseEnabled());
		Assert.assertEquals(DurationMode.FOR_TICKS, loadedEntry.getAnimationRules().get(0).getDurationMode());
		Assert.assertEquals(OverheadPrayer.PROTECT_FROM_MISSILES, loadedEntry.getProjectileRules().get(0).getPrayer());
		Assert.assertFalse(loaded.getFolders().get(0).isEnabled());
	}

	@Test
	public void oldCurseFieldsLoadAsThePillOnly()
	{
		String oldJson = "{"
			+ "\"folders\":[{\"title\":\"Bosses\",\"enabled\":false,\"entries\":[{"
			+ "\"title\":\"Phosani\",\"enabled\":true,\"curseEnabled\":true,"
			+ "\"curseAnimationId\":8594,\"curseAttacksUntilClear\":5,\"curseVarbitId\":10132"
			+ "}]}]}";

		PrayAlertSave loaded = new Gson().fromJson(oldJson, PrayAlertSave.class);
		loaded.normalize();
		Entry loadedEntry = loaded.getFolders().get(0).getEntries().get(0);
		Assert.assertTrue(loadedEntry.isCurseEnabled());
		Assert.assertEquals("Phosani", loadedEntry.getTitle());

		String json = new Gson().toJson(loaded);
		Assert.assertTrue(json.contains("\"curseEnabled\":true"));
		Assert.assertFalse(json.contains("curseAnimationId"));
		Assert.assertFalse(json.contains("curseVarbitId"));
		Assert.assertFalse(json.contains("curseAttacksUntilClear"));
	}

	@Test
	public void curseSignalsAreTheConfusionAttack()
	{
		Assert.assertTrue(NightmareCurse.isStartAnimation(8599));
		Assert.assertFalse(NightmareCurse.isStartAnimation(8306));
		Assert.assertFalse(NightmareCurse.isStartAnimation(8594));

		Assert.assertTrue(NightmareCurse.isStandardAttack(8594));
		Assert.assertTrue(NightmareCurse.isStandardAttack(8595));
		Assert.assertTrue(NightmareCurse.isStandardAttack(8596));
		Assert.assertFalse(NightmareCurse.isStandardAttack(8599));
		Assert.assertFalse(NightmareCurse.isStandardAttack(8597));

		Assert.assertTrue(NightmareCurse.isEndSound(4260));
		Assert.assertTrue(NightmareCurse.isEndSound(4330));
		Assert.assertFalse(NightmareCurse.isEndSound(5321));
		Assert.assertFalse(NightmareCurse.isEndSound(4255));

		Assert.assertTrue(NightmareCurse.isStartMessage("The Nightmare has cursed you, shuffling your prayers!"));
		Assert.assertTrue(NightmareCurse.isStartMessage("<col=ff0000>The Nightmare has cursed you, shuffling your prayers!</col>"));
		Assert.assertFalse(NightmareCurse.isStartMessage("The Nightmare's infection has worn off."));

		Assert.assertTrue(NightmareCurse.isEndMessage("You feel the effects of the Nightmare's curse wear off."));
		Assert.assertTrue(NightmareCurse.isEndMessage("<col=ff0000>You feel the effects of the Nightmare's curse wear off.</col>"));
		Assert.assertFalse(NightmareCurse.isEndMessage("The Nightmare has cursed you, shuffling your prayers!"));
		Assert.assertFalse(NightmareCurse.isEndMessage(null));
		Assert.assertEquals(5, NightmareCurse.ATTACKS_UNTIL_CLEAR);
	}

	@Test
	public void attackTimerRoundTripKeepsNpcDelayAndPrayer()
	{
		PrayAlertSave save = new PrayAlertSave();
		Entry entry = new Entry();
		entry.setTitle("Entry 1");
		AttackTimerRule timer = new AttackTimerRule();
		timer.setNpcId(9416);
		timer.setNpcName("Fragment of Seren");
		timer.setAnimationId(836);
		timer.setPrayer(OverheadPrayer.PROTECT_FROM_MAGIC);
		timer.setDelay(4);
		entry.getAttackTimers().add(timer);
		save.getEntries().add(entry);

		PrayAlertSave loaded = new Gson().fromJson(new Gson().toJson(save), PrayAlertSave.class);
		loaded.normalize();
		AttackTimerRule loadedTimer = loaded.getEntries().get(0).getAttackTimers().get(0);
		Assert.assertEquals(Integer.valueOf(9416), loadedTimer.getNpcId());
		Assert.assertEquals("Fragment of Seren", loadedTimer.getNpcName());
		Assert.assertEquals(836, loadedTimer.getAnimationId());
		Assert.assertEquals(OverheadPrayer.PROTECT_FROM_MAGIC, loadedTimer.getPrayer());
		Assert.assertEquals(4, loadedTimer.getDelay());
	}

	@Test
	public void normalizeRepairsBlankAttackTimers()
	{
		Entry entry = new Entry();
		entry.setTitle("Entry 1");
		AttackTimerRule timer = new AttackTimerRule();
		timer.setNpcId(0);
		timer.setNpcName("  Seren  ");
		timer.setDelay(0);
		timer.setPrayer(null);
		entry.getAttackTimers().add(timer);
		PrayAlertSave save = new PrayAlertSave();
		save.getEntries().add(entry);
		save.normalize();

		AttackTimerRule loaded = save.getEntries().get(0).getAttackTimers().get(0);
		Assert.assertNull(loaded.getNpcId());
		Assert.assertEquals("Seren", loaded.getNpcName());
		Assert.assertEquals(5, loaded.getDelay());
		Assert.assertEquals(OverheadPrayer.PROTECT_FROM_MELEE, loaded.getPrayer());
	}

	@Test
	public void learnedDelayUsesTheMiddleGap()
	{
		Assert.assertNull(AttackTiming.learnedDelay(java.util.Collections.singletonList(5)));
		Assert.assertEquals(Integer.valueOf(5), AttackTiming.learnedDelay(Arrays.asList(5, 5)));
		Assert.assertEquals(Integer.valueOf(5), AttackTiming.learnedDelay(Arrays.asList(4, 6)));
		Assert.assertEquals(Integer.valueOf(5), AttackTiming.learnedDelay(Arrays.asList(4, 5, 9)));
	}

	@Test
	public void nextFreeNumberSkipsUsedTitles()
	{
		Assert.assertEquals(1, PrayAlertPlugin.nextFreeNumber(null, "Entry "));
		Assert.assertEquals(3, PrayAlertPlugin.nextFreeNumber(Arrays.asList("Entry 1", "Entry 2", "Folder 1"), "Entry "));
		Assert.assertEquals(2, PrayAlertPlugin.nextFreeNumber(Arrays.asList("Entry 1", "Entry 3", "Entry "), "Entry "));
		Assert.assertEquals(1, PrayAlertPlugin.nextFreeNumber(Arrays.asList("Entry", "My entry"), "Entry "));
	}

	@Test
	public void prayerSpritesMatchThePrayerBookIcons()
	{
		Assert.assertEquals(127, OverheadPrayer.PROTECT_FROM_MAGIC.spriteId(false));
		Assert.assertEquals(128, OverheadPrayer.PROTECT_FROM_MISSILES.spriteId(false));
		Assert.assertEquals(129, OverheadPrayer.PROTECT_FROM_MELEE.spriteId(false));
		Assert.assertEquals(4847, OverheadPrayer.PROTECT_FROM_MAGIC.spriteId(true));
		Assert.assertEquals(4848, OverheadPrayer.PROTECT_FROM_MISSILES.spriteId(true));
		Assert.assertEquals(4849, OverheadPrayer.PROTECT_FROM_MELEE.spriteId(true));
		Assert.assertEquals("Melee", OverheadPrayer.PROTECT_FROM_MELEE.shortLabel());
		Assert.assertEquals("Range", OverheadPrayer.PROTECT_FROM_MISSILES.shortLabel());
		Assert.assertEquals("Mage", OverheadPrayer.PROTECT_FROM_MAGIC.shortLabel());
	}

	@Test
	public void fallbackInfoboxIconIsVisible()
	{
		BufferedImage image = PrayAlertInfobox.fallbackIcon(OverheadPrayer.PROTECT_FROM_MELEE);
		Assert.assertTrue(image.getWidth() > 0);
		int argb = image.getRGB(image.getWidth() / 2, image.getHeight() / 2);
		Assert.assertTrue((argb >>> 24) != 0);
	}

	@Test
	public void sidebarUsesIconButtonsPillsAndCollapsedEditing() throws Exception
	{
		PrayAlertPlugin plugin = new PrayAlertPlugin();
		SwingUtilities.invokeAndWait(() ->
		{
			plugin.addRootEntry();
			plugin.addFolder();
			PrayAlertPanel panel = new PrayAlertPanel(plugin);
			List<Component> components = new ArrayList<>();
			collect(panel, components);

			Assert.assertTrue(buttonText(components).contains("Entry 1"));
			Assert.assertTrue(buttonText(components).contains("Folder 1"));
			Assert.assertFalse(buttonText(components).contains("Edit"));
			Assert.assertFalse(buttonText(components).contains("+ Add"));
			Assert.assertFalse(buttonText(components).contains("+ Folder"));
			Assert.assertFalse(buttonText(components).contains("Protect from"));
			Assert.assertFalse(buttonText(components).contains("Attack timers"));
			Assert.assertNotNull(findButton(components, "+"));
			Assert.assertNotNull(findByTooltip(components, "Add folder"));
			Assert.assertTrue(findByTooltip(components, "Add folder").getText() == null
				|| findByTooltip(components, "Add folder").getText().isEmpty());
			Assert.assertTrue(hasToggle(components));
			Assert.assertFalse(hasCheckBox(components));
			JButton add = findButton(components, "+");
			JButton remove = findButton(components, "x");
			Assert.assertNotNull(add);
			Assert.assertNotNull(remove);
			Assert.assertNull(findButton(components, "X"));
			Assert.assertEquals(add.getFont().getSize(), remove.getFont().getSize());
			Assert.assertEquals(add.getMargin(), remove.getMargin());
			Assert.assertTrue(plugin.copyRootEntries().get(0).isCollapsed());
			Assert.assertTrue(plugin.copyFolders().get(0).isCollapsed());

			AnimationRule rule = new AnimationRule();
			rule.setPrayer(OverheadPrayer.PROTECT_FROM_MAGIC);
			plugin.copyRootEntries().get(0).getAnimationRules().add(rule);
			plugin.copyRootEntries().get(0).setCollapsed(false);
			PrayAlertPanel expandedPanel = new PrayAlertPanel(plugin);
			components.clear();
			collect(expandedPanel, components);
			String expanded = buttonText(components);
			Assert.assertTrue(expanded.contains("Attack timers"));
			Assert.assertTrue(expanded.contains("Animations"));
			Assert.assertTrue(expanded.contains("Projectiles"));
			Assert.assertTrue(expanded.contains("Curse"));
			Assert.assertTrue(expanded.contains("Until animation ends"));
			Assert.assertTrue(expanded.contains("For N ticks"));
			AbstractButton untilEnds = findByTooltip(components, "Highlight until the animation changes");
			AbstractButton forTicks = findByTooltip(components, "Highlight for a set number of ticks");
			Assert.assertNotNull(untilEnds);
			Assert.assertNotNull(forTicks);
			Assert.assertTrue(untilEnds.isSelected());
			Assert.assertFalse(forTicks.isSelected());
			Assert.assertTrue(orangeScore(untilEnds) > orangeScore(forTicks));
			JSpinner tickSpinner = spinnerBeside(components, "For N ticks");
			Assert.assertNotNull(tickSpinner);
			Assert.assertFalse(tickSpinner.isEnabled());
			Assert.assertEquals(DurationMode.UNTIL_ANIMATION_ENDS, rule.getDurationMode());
			forTicks.doClick();
			Assert.assertFalse(untilEnds.isSelected());
			Assert.assertTrue(forTicks.isSelected());
			Assert.assertTrue(tickSpinner.isEnabled());
			Assert.assertEquals(DurationMode.FOR_TICKS, rule.getDurationMode());
			untilEnds.doClick();
			Assert.assertTrue(untilEnds.isSelected());
			Assert.assertFalse(forTicks.isSelected());
			Assert.assertFalse(tickSpinner.isEnabled());
			Assert.assertEquals(DurationMode.UNTIL_ANIMATION_ENDS, rule.getDurationMode());
			Assert.assertFalse(expanded.contains("Curse animation"));
			Assert.assertFalse(expanded.contains("Attacks until clear"));
			Assert.assertFalse(expanded.contains("Varbit"));
			Assert.assertFalse(expanded.contains("Protect from"));
			AbstractButton curse = findByTooltip(components, "Enable curse");
			Assert.assertNotNull(curse);
			Assert.assertTrue(curse.getPreferredSize().height > 0);
			curse.doClick();
			components.clear();
			collect(expandedPanel, components);
			String withCurse = buttonText(components);
			Assert.assertTrue(withCurse.contains("Curse"));
			Assert.assertFalse(withCurse.contains("Curse animation"));
			Assert.assertFalse(withCurse.contains("Attacks until clear"));
			Assert.assertFalse(withCurse.contains("Varbit"));
			Assert.assertNull(findByTooltip(components, "Enable curse"));
			Assert.assertNotNull(findByTooltip(components, "Disable curse"));
			Assert.assertTrue(plugin.copyRootEntries().get(0).isCurseEnabled());
			JComboBox<?> prayer = findCombo(components);
			Assert.assertNotNull(prayer);
			Assert.assertEquals("Mage", renderedComboText(prayer));
		});
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static String renderedComboText(JComboBox combo)
	{
		javax.swing.ListCellRenderer renderer = combo.getRenderer();
		Component rendered = renderer.getListCellRendererComponent(new JList(), combo.getSelectedItem(), -1, false, false);
		return rendered instanceof JLabel ? ((JLabel) rendered).getText() : null;
	}

	private static int orangeScore(AbstractButton button)
	{
		int width = Math.max(1, button.getPreferredSize().width);
		int height = Math.max(1, button.getPreferredSize().height);
		button.setSize(width, height);
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		button.paint(graphics);
		graphics.dispose();
		int score = 0;
		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				int argb = image.getRGB(x, y);
				int alpha = (argb >>> 24) & 255;
				int red = (argb >> 16) & 255;
				int green = (argb >> 8) & 255;
				int blue = argb & 255;
				if (alpha > 20 && red > green && green > blue && red > 120)
				{
					score++;
				}
			}
		}
		return score;
	}

	private static JSpinner spinnerBeside(List<Component> components, String caption)
	{
		for (Component component : components)
		{
			if (!(component instanceof JLabel) || !caption.equals(((JLabel) component).getText()))
			{
				continue;
			}
			Container parent = component.getParent();
			if (parent == null)
			{
				continue;
			}
			for (Component child : parent.getComponents())
			{
				if (child instanceof JSpinner)
				{
					return (JSpinner) child;
				}
			}
		}
		return null;
	}

	private static JComboBox<?> findCombo(List<Component> components)
	{
		for (Component component : components)
		{
			if (component instanceof JComboBox)
			{
				return (JComboBox<?>) component;
			}
		}
		return null;
	}

	private static String buttonText(List<Component> components)
	{
		StringBuilder text = new StringBuilder();
		for (Component component : components)
		{
			if (component instanceof AbstractButton)
			{
				text.append(((AbstractButton) component).getText()).append('\n');
			}
			else if (component instanceof javax.swing.JLabel)
			{
				text.append(((javax.swing.JLabel) component).getText()).append('\n');
			}
		}
		return text.toString();
	}

	private static JButton findButton(List<Component> components, String text)
	{
		for (Component component : components)
		{
			if (component instanceof JButton && text.equals(((JButton) component).getText()))
			{
				return (JButton) component;
			}
		}
		return null;
	}

	private static AbstractButton findByTooltip(List<Component> components, String tooltip)
	{
		for (Component component : components)
		{
			if (component instanceof AbstractButton && tooltip.equals(((AbstractButton) component).getToolTipText()))
			{
				return (AbstractButton) component;
			}
		}
		return null;
	}

	private static boolean hasToggle(List<Component> components)
	{
		for (Component component : components)
		{
			if (component instanceof JToggleButton)
			{
				return true;
			}
		}
		return false;
	}

	private static boolean hasCheckBox(List<Component> components)
	{
		for (Component component : components)
		{
			if (component instanceof JCheckBox)
			{
				return true;
			}
		}
		return false;
	}

	private static void collect(Component component, List<Component> out)
	{
		out.add(component);
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				collect(child, out);
			}
		}
	}
}
