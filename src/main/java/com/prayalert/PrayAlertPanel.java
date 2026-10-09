package com.prayalert;

import com.prayalert.model.AnimationRule;
import com.prayalert.model.AttackTimerRule;
import com.prayalert.model.DurationMode;
import com.prayalert.model.Entry;
import com.prayalert.model.Folder;
import com.prayalert.model.OverheadPrayer;
import com.prayalert.model.ProjectileRule;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;

class PrayAlertPanel extends PluginPanel
{
	private static ImageIcon onSwitcher;
	private static ImageIcon offSwitcher;
	private static ImageIcon folderIcon;

	private final PrayAlertPlugin plugin;
	private boolean rebuilding;

	PrayAlertPanel(PrayAlertPlugin plugin)
	{
		super(true);
		this.plugin = plugin;
		rebuild();
	}

	private void rebuild()
	{
		int scroll = 0;
		if (getScrollPane() != null)
		{
			scroll = getScrollPane().getVerticalScrollBar().getValue();
		}

		rebuilding = true;
		removeAll();

		add(header());

		List<Folder> folders = plugin.copyFolders();
		List<Entry> roots = plugin.copyRootEntries();
		if (folders.isEmpty() && roots.isEmpty())
		{
			JLabel empty = label("No entries yet.");
			empty.setBorder(new EmptyBorder(4, 2, 4, 2));
			add(empty);
		}

		for (Folder folder : folders)
		{
			add(folderCard(folder));
		}
		for (Entry entry : roots)
		{
			add(entryCard(entry));
		}

		rebuilding = false;
		revalidate();
		repaint();

		int restore = scroll;
		SwingUtilities.invokeLater(() ->
		{
			if (getScrollPane() != null)
			{
				getScrollPane().getVerticalScrollBar().setValue(restore);
			}
		});
	}

	void refresh()
	{
		if (SwingUtilities.isEventDispatchThread())
		{
			rebuild();
		}
		else
		{
			SwingUtilities.invokeLater(this::rebuild);
		}
	}

	private JPanel header()
	{
		JPanel header = new JPanel();
		header.setLayout(new BorderLayout(0, 4));
		header.setOpaque(false);

		JLabel title = label("Pray Alert");
		title.setForeground(ColorScheme.BRAND_ORANGE);
		header.add(title, BorderLayout.NORTH);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		buttons.setOpaque(false);
		JButton add = button("+", () ->
		{
			plugin.addRootEntry();
			rebuild();
		});
		add.setToolTipText("Add entry");
		buttons.add(add);
		buttons.add(folderButton(() ->
		{
			plugin.addFolder();
			rebuild();
		}));
		header.add(buttons, BorderLayout.CENTER);
		return header;
	}

	private JPanel folderCard(Folder folder)
	{
		JLabel title = label(folder.getTitle());
		JPanel card = card();
		card.add(folderHeader(folder, title), BorderLayout.NORTH);
		if (!folder.isCollapsed())
		{
			JPanel body = new JPanel();
			body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
			body.setOpaque(false);
			body.setBorder(new EmptyBorder(4, 0, 0, 0));

			JPanel titleRow = new JPanel(new BorderLayout(4, 0));
			titleRow.setOpaque(false);
			titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
			titleRow.add(label("Title"), BorderLayout.WEST);
			JTextField titleField = new JTextField(folder.getTitle() == null ? "" : folder.getTitle());
			onText(titleField, () ->
			{
				String text = titleField.getText() == null ? "" : titleField.getText().trim();
				if (text.isEmpty())
				{
					return;
				}
				plugin.update(() -> folder.setTitle(text));
				title.setText(text);
			});
			titleRow.add(titleField, BorderLayout.CENTER);
			titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, titleRow.getPreferredSize().height));
			body.add(titleRow);
			body.add(Box.createVerticalStrut(4));

			if (folder.getEntries() != null)
			{
				for (Entry entry : folder.getEntries())
				{
					if (entry == null)
					{
						continue;
					}
					JPanel entryCard = entryCard(entry);
					entryCard.setAlignmentX(Component.LEFT_ALIGNMENT);
					body.add(entryCard);
					body.add(Box.createVerticalStrut(4));
				}
			}
			JButton add = button("+", () ->
			{
				plugin.addEntryToFolder(folder.getId());
				rebuild();
			});
			add.setToolTipText("Add entry");
			add.setAlignmentX(Component.LEFT_ALIGNMENT);
			body.add(add);
			card.add(body, BorderLayout.CENTER);
		}
		return card;
	}

	private JPanel folderHeader(Folder folder, JLabel title)
	{
		JPanel header = new JPanel(new BorderLayout(2, 0));
		header.setOpaque(false);
		header.add(button(folder.isCollapsed() ? ">" : "v", () ->
		{
			plugin.update(() -> folder.setCollapsed(!folder.isCollapsed()));
			rebuild();
		}), BorderLayout.WEST);

		header.add(title, BorderLayout.CENTER);

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
		actions.setOpaque(false);
		JToggleButton enabled = pill(folder.isEnabled());
		enabled.setToolTipText(folder.isEnabled() ? "Disable folder" : "Enable folder");
		enabled.addItemListener(event ->
		{
			if (rebuilding)
			{
				return;
			}
			plugin.update(() -> folder.setEnabled(enabled.isSelected()));
			enabled.setToolTipText(enabled.isSelected() ? "Disable folder" : "Enable folder");
		});
		actions.add(enabled);
		actions.add(removeButton(() -> confirmDelete(
			"Delete folder \"" + folder.getTitle() + "\" and the entries inside it?",
			"Delete folder",
			() -> plugin.deleteFolder(folder.getId()))));
		header.add(actions, BorderLayout.EAST);
		return header;
	}

	private JPanel entryCard(Entry entry)
	{
		JLabel title = label(entry.getTitle());
		JPanel card = card();
		card.add(entryHeader(entry, title), BorderLayout.NORTH);
		if (!entry.isCollapsed())
		{
			card.add(entryBody(entry, title), BorderLayout.CENTER);
		}
		return card;
	}

	private JPanel entryHeader(Entry entry, JLabel title)
	{
		JPanel header = new JPanel(new BorderLayout(2, 0));
		header.setOpaque(false);
		header.add(button(entry.isCollapsed() ? ">" : "v", () ->
		{
			plugin.update(() -> entry.setCollapsed(!entry.isCollapsed()));
			rebuild();
		}), BorderLayout.WEST);

		header.add(title, BorderLayout.CENTER);

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
		actions.setOpaque(false);
		JToggleButton enabled = pill(entry.isEnabled());
		enabled.setToolTipText(entry.isEnabled() ? "Disable entry" : "Enable entry");
		enabled.addItemListener(event ->
		{
			if (rebuilding)
			{
				return;
			}
			plugin.update(() -> entry.setEnabled(enabled.isSelected()));
			enabled.setToolTipText(enabled.isSelected() ? "Disable entry" : "Enable entry");
		});
		actions.add(enabled);
		actions.add(removeButton(() -> confirmDelete(
			"Delete \"" + entry.getTitle() + "\"?",
			"Delete entry",
			() -> plugin.deleteEntry(entry.getId()))));
		header.add(actions, BorderLayout.EAST);
		return header;
	}

	private JPanel entryBody(Entry entry, JLabel headerTitle)
	{
		JPanel body = new JPanel(new GridBagLayout());
		body.setOpaque(false);
		body.setBorder(new EmptyBorder(4, 0, 0, 0));
		int row = 0;

		body.add(label("Title"), constraints(row, 0, 0));
		JTextField title = new JTextField(entry.getTitle() == null ? "" : entry.getTitle());
		onText(title, () ->
		{
			String text = title.getText() == null ? "" : title.getText().trim();
			if (text.isEmpty())
			{
				return;
			}
			plugin.update(() -> entry.setTitle(text));
			headerTitle.setText(text);
		});
		GridBagConstraints titleConstraints = constraints(row, 1, 1);
		titleConstraints.gridwidth = 2;
		body.add(title, titleConstraints);
		row++;

		body.add(sectionLabel("Animations"), constraints(row, 0, 1));
		body.add(button("+", () ->
		{
			plugin.update(() ->
			{
				AnimationRule rule = new AnimationRule();
				rule.setDurationMode(DurationMode.UNTIL_ANIMATION_ENDS);
				entry.getAnimationRules().add(rule);
			});
			rebuild();
		}), constraints(row, 2, 0));
		row++;

		if (entry.getAnimationRules() != null)
		{
			for (AnimationRule rule : entry.getAnimationRules())
			{
				if (rule == null)
				{
					continue;
				}
				row = addAnimationRule(body, entry, rule, row);
			}
		}

		body.add(sectionLabel("Projectiles"), constraints(row, 0, 1));
		body.add(button("+", () ->
		{
			plugin.update(() -> entry.getProjectileRules().add(new ProjectileRule()));
			rebuild();
		}), constraints(row, 2, 0));
		row++;

		if (entry.getProjectileRules() != null)
		{
			for (ProjectileRule rule : entry.getProjectileRules())
			{
				if (rule == null)
				{
					continue;
				}
				row = addProjectileRule(body, entry, rule, row);
			}
		}

		body.add(sectionLabel("Attack timers"), constraints(row, 0, 1));
		body.add(button("+", () ->
		{
			plugin.update(() ->
			{
				if (entry.getAttackTimers() == null)
				{
					entry.setAttackTimers(new java.util.ArrayList<>());
				}
				entry.getAttackTimers().add(new AttackTimerRule());
			});
			rebuild();
		}), constraints(row, 2, 0));
		row++;

		if (entry.getAttackTimers() != null)
		{
			for (AttackTimerRule rule : entry.getAttackTimers())
			{
				if (rule == null)
				{
					continue;
				}
				row = addAttackTimer(body, entry, rule, row);
			}
		}

		addCurse(body, entry, row);
		return body;
	}

	private int addAnimationRule(JPanel body, Entry entry, AnimationRule rule, int row)
	{
		JSpinner id = numberSpinner(rule.getAnimationId(), 0, Integer.MAX_VALUE);
		id.addChangeListener(event -> plugin.update(() -> rule.setAnimationId(spinnerValue(id))));
		body.add(id, constraints(row, 0, 0.3));

		JComboBox<OverheadPrayer> prayer = prayerCombo(rule.getPrayer());
		prayer.addActionListener(event -> plugin.update(() -> rule.setPrayer((OverheadPrayer) prayer.getSelectedItem())));
		body.add(prayer, constraints(row, 1, 0.7));

		body.add(removeButton(() ->
		{
			plugin.update(() -> entry.getAnimationRules().remove(rule));
			rebuild();
		}), constraints(row, 2, 0));
		row++;

		boolean forTicks = rule.getDurationMode() == DurationMode.FOR_TICKS;
		JToggleButton untilEnds = pill(!forTicks);
		untilEnds.setToolTipText("Highlight until the animation changes");
		JToggleButton tickMode = pill(forTicks);
		tickMode.setToolTipText("Highlight for a set number of ticks");
		JSpinner ticks = numberSpinner(Math.max(1, rule.getTicks()), 1, 99);
		ticks.setEnabled(forTicks);
		ticks.addChangeListener(event -> plugin.update(() -> rule.setTicks(Math.max(1, spinnerValue(ticks)))));
		// Action events are clicks. setSelected on the other pill must not count as a click.
		boolean[] syncing = {false};
		untilEnds.addActionListener(event ->
		{
			if (rebuilding || syncing[0])
			{
				return;
			}
			syncing[0] = true;
			try
			{
				boolean until = untilEnds.isSelected();
				tickMode.setSelected(!until);
				ticks.setEnabled(!until);
				plugin.update(() -> rule.setDurationMode(until ? DurationMode.UNTIL_ANIMATION_ENDS : DurationMode.FOR_TICKS));
			}
			finally
			{
				syncing[0] = false;
			}
		});
		tickMode.addActionListener(event ->
		{
			if (rebuilding || syncing[0])
			{
				return;
			}
			syncing[0] = true;
			try
			{
				boolean ticksOn = tickMode.isSelected();
				untilEnds.setSelected(!ticksOn);
				ticks.setEnabled(ticksOn);
				plugin.update(() -> rule.setDurationMode(ticksOn ? DurationMode.FOR_TICKS : DurationMode.UNTIL_ANIMATION_ENDS));
			}
			finally
			{
				syncing[0] = false;
			}
		});
		body.add(modeRow(untilEnds, "Until animation ends", null), span(row));
		row++;
		body.add(modeRow(tickMode, "For N ticks", ticks), span(row));
		row++;
		return row;
	}

	private int addProjectileRule(JPanel body, Entry entry, ProjectileRule rule, int row)
	{
		JSpinner id = numberSpinner(rule.getProjectileId(), 0, Integer.MAX_VALUE);
		id.addChangeListener(event -> plugin.update(() -> rule.setProjectileId(spinnerValue(id))));
		body.add(id, constraints(row, 0, 0.3));

		JComboBox<OverheadPrayer> prayer = prayerCombo(rule.getPrayer());
		prayer.addActionListener(event -> plugin.update(() -> rule.setPrayer((OverheadPrayer) prayer.getSelectedItem())));
		body.add(prayer, constraints(row, 1, 0.7));

		body.add(removeButton(() ->
		{
			plugin.update(() -> entry.getProjectileRules().remove(rule));
			rebuild();
		}), constraints(row, 2, 0));
		return row + 1;
	}

	private int addAttackTimer(JPanel body, Entry entry, AttackTimerRule rule, int row)
	{
		JTextField npcId = new JTextField(rule.getNpcId() == null ? "" : Integer.toString(rule.getNpcId()));
		npcId.setToolTipText("NPC id. Used instead of the name when set.");
		onText(npcId, () ->
		{
			Integer id = parseOptionalId(npcId.getText());
			plugin.update(() -> rule.setNpcId(id));
		});
		body.add(labeled("NPC id", npcId), constraints(row, 0, 0.45));

		JTextField npcName = new JTextField(rule.getNpcName() == null ? "" : rule.getNpcName());
		npcName.setToolTipText("NPC name. Used when the id is blank.");
		onText(npcName, () -> plugin.update(() -> rule.setNpcName(npcName.getText() == null ? "" : npcName.getText().trim())));
		body.add(labeled("Name", npcName), constraints(row, 1, 0.55));

		body.add(removeButton(() ->
		{
			plugin.update(() -> entry.getAttackTimers().remove(rule));
			rebuild();
		}), constraints(row, 2, 0));
		row++;

		JSpinner animation = numberSpinner(rule.getAnimationId(), 0, Integer.MAX_VALUE);
		animation.setToolTipText("Attack animation id");
		animation.addChangeListener(event -> plugin.update(() -> rule.setAnimationId(spinnerValue(animation))));
		body.add(labeled("Animation", animation), constraints(row, 0, 0.4));

		JComboBox<OverheadPrayer> prayer = prayerCombo(rule.getPrayer());
		prayer.addActionListener(event -> plugin.update(() -> rule.setPrayer((OverheadPrayer) prayer.getSelectedItem())));
		body.add(prayer, constraints(row, 1, 0.6));
		row++;

		JToggleButton learn = pill(rule.isLearnDelay());
		learn.setToolTipText("Measure the delay from attacks you see");
		learn.addItemListener(event ->
		{
			if (rebuilding)
			{
				return;
			}
			boolean on = learn.isSelected();
			plugin.update(() ->
			{
				rule.setLearnDelay(on);
				if (on)
				{
					rule.setDelayLearned(false);
				}
			});
			plugin.clearAttackTimerSamples(entry.getId(), rule.getAnimationId());
			rebuild();
		});
		body.add(modeRow(learn, "Learn delay", null), span(row));
		row++;

		boolean waiting = rule.isLearnDelay() && !rule.isDelayLearned();
		JSpinner delay = numberSpinner(Math.max(1, rule.getDelay()), 1, 99);
		delay.setEnabled(!rule.isLearnDelay());
		delay.setToolTipText(waiting ? "Waiting for a few attacks" : "Ticks until the next attack");
		delay.addChangeListener(event -> plugin.update(() -> rule.setDelay(Math.max(1, spinnerValue(delay)))));
		body.add(labeled(waiting ? "Delay (waiting)" : "Delay", delay), span(row));
		return row + 1;
	}

	private void addCurse(JPanel body, Entry entry, int row)
	{
		JToggleButton enabled = pill(entry.isCurseEnabled());
		enabled.setToolTipText(entry.isCurseEnabled() ? "Disable curse" : "Enable curse");
		enabled.addItemListener(event ->
		{
			if (rebuilding)
			{
				return;
			}
			plugin.setCurseEnabled(entry, enabled.isSelected());
			rebuild();
		});
		JLabel curseLabel = sectionLabel("Curse");
		curseLabel.setToolTipText("Shuffles the highlight while Phosani's curse is active");
		body.add(modeRow(enabled, null, curseLabel), span(row));
	}

	private void confirmDelete(String message, String dialogTitle, Runnable delete)
	{
		int choice = JOptionPane.showConfirmDialog(this, message, dialogTitle, JOptionPane.OK_CANCEL_OPTION);
		if (choice == JOptionPane.OK_OPTION)
		{
			delete.run();
			rebuild();
		}
	}

	private void onText(JTextField field, Runnable action)
	{
		field.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent event)
			{
				fire();
			}

			@Override
			public void removeUpdate(DocumentEvent event)
			{
				fire();
			}

			@Override
			public void changedUpdate(DocumentEvent event)
			{
				fire();
			}

			private void fire()
			{
				if (!rebuilding)
				{
					action.run();
				}
			}
		});
	}

	private static Integer parseOptionalId(String text)
	{
		if (text == null)
		{
			return null;
		}
		String trimmed = text.trim();
		if (trimmed.isEmpty())
		{
			return null;
		}
		try
		{
			int parsed = Integer.parseInt(trimmed);
			return parsed > 0 ? parsed : null;
		}
		catch (NumberFormatException ex)
		{
			return null;
		}
	}

	private static int spinnerValue(JSpinner spinner)
	{
		Object value = spinner.getValue();
		if (value instanceof Number)
		{
			return ((Number) value).intValue();
		}
		return 0;
	}

	private static JSpinner numberSpinner(int value, int min, int max)
	{
		int safe = value;
		if (safe < min)
		{
			safe = min;
		}
		if (safe > max)
		{
			safe = max;
		}
		JSpinner spinner = new JSpinner(new SpinnerNumberModel(safe, min, max, 1));
		Component editor = spinner.getEditor();
		if (editor instanceof JSpinner.DefaultEditor)
		{
			((JSpinner.DefaultEditor) editor).getTextField().setColumns(4);
		}
		Dimension size = spinner.getPreferredSize();
		spinner.setMaximumSize(new Dimension(Integer.MAX_VALUE, size.height));
		return spinner;
	}

	private static JComboBox<OverheadPrayer> prayerCombo(OverheadPrayer selected)
	{
		JComboBox<OverheadPrayer> combo = new JComboBox<>(OverheadPrayer.values());
		combo.setRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
			{
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (value instanceof OverheadPrayer)
				{
					setText(((OverheadPrayer) value).shortLabel());
				}
				return this;
			}
		});
		if (selected != null)
		{
			combo.setSelectedItem(selected);
		}
		return combo;
	}

	/**
	 * PluginToggleButton is package-private, so this builds the same pill from its switcher image.
	 */
	private static JToggleButton pill(boolean selected)
	{
		loadSwitchers();
		JToggleButton toggle = new JToggleButton();
		SwingUtil.removeButtonDecorations(toggle);
		int width = Math.max(25, offSwitcher.getIconWidth());
		int height = Math.max(14, offSwitcher.getIconHeight());
		Dimension size = new Dimension(width, height);
		toggle.setPreferredSize(size);
		toggle.setMinimumSize(size);
		applyPillIcon(toggle, selected);
		toggle.setSelected(selected);
		toggle.addItemListener(event -> applyPillIcon(toggle, toggle.isSelected()));
		toggle.setFocusable(false);
		return toggle;
	}

	/** Normal and selected icons both match the pill state. */
	private static void applyPillIcon(JToggleButton toggle, boolean selected)
	{
		ImageIcon icon = selected ? onSwitcher : offSwitcher;
		toggle.setIcon(icon);
		toggle.setSelectedIcon(icon);
	}

	private static void loadSwitchers()
	{
		if (onSwitcher != null && offSwitcher != null)
		{
			return;
		}
		try (InputStream in = ImageUtil.class.getClassLoader().getResourceAsStream("net/runelite/client/plugins/config/switcher_on.png"))
		{
			if (in != null)
			{
				BufferedImage on = ImageIO.read(in);
				if (on != null)
				{
					BufferedImage off = ImageUtil.flipImage(ImageUtil.luminanceScale(ImageUtil.grayscaleImage(on), 0.61f), true, false);
					onSwitcher = new ImageIcon(on);
					offSwitcher = new ImageIcon(off);
					return;
				}
			}
		}
		catch (IOException ex)
		{
			// Fall through to a drawn pill.
		}
		onSwitcher = drawnPill(new Color(40, 170, 60), true);
		offSwitcher = drawnPill(new Color(90, 90, 90), false);
	}

	private static ImageIcon drawnPill(Color color, boolean on)
	{
		BufferedImage image = new BufferedImage(26, 14, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(color);
		graphics.fillRoundRect(0, 0, 26, 14, 14, 14);
		graphics.setColor(Color.WHITE);
		graphics.fillOval(on ? 13 : 1, 1, 12, 12);
		graphics.dispose();
		return new ImageIcon(image);
	}

	private JButton folderButton(Runnable action)
	{
		JButton button = new JButton(folderIcon());
		button.setMargin(new Insets(1, 4, 1, 4));
		button.setFocusable(false);
		button.setToolTipText("Add folder");
		button.addActionListener(event -> action.run());
		return button;
	}

	private static ImageIcon folderIcon()
	{
		if (folderIcon != null)
		{
			return folderIcon;
		}
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(new Color(214, 168, 70));
		graphics.fillRoundRect(1, 5, 14, 9, 2, 2);
		graphics.fillRoundRect(1, 3, 7, 4, 2, 2);
		graphics.dispose();
		folderIcon = new ImageIcon(image);
		return folderIcon;
	}

	private JPanel card()
	{
		JPanel card = new JPanel(new BorderLayout());
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(new EmptyBorder(4, 4, 4, 4));
		return card;
	}

	private JButton button(String text, Runnable action)
	{
		JButton button = new JButton(text);
		button.setMargin(new Insets(1, 4, 1, 4));
		button.setFocusable(false);
		button.addActionListener(event -> action.run());
		return button;
	}

	private JButton removeButton(Runnable action)
	{
		JButton button = button("x", action);
		button.setToolTipText("Remove");
		return button;
	}

	private static JPanel modeRow(JToggleButton toggle, String caption, Component extra)
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		row.setOpaque(false);
		row.add(toggle);
		if (caption != null)
		{
			row.add(label(caption));
		}
		if (extra != null)
		{
			row.add(extra);
		}
		return row;
	}

	private static GridBagConstraints span(int row)
	{
		GridBagConstraints constraints = constraints(row, 0, 1);
		constraints.gridwidth = 3;
		return constraints;
	}

	private static JPanel labeled(String caption, Component field)
	{
		JPanel panel = new JPanel(new BorderLayout(0, 1));
		panel.setOpaque(false);
		JLabel label = label(caption);
		panel.add(label, BorderLayout.NORTH);
		panel.add(field, BorderLayout.CENTER);
		return panel;
	}

	private static JLabel label(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		return label;
	}

	private static JLabel sectionLabel(String text)
	{
		JLabel label = label(text);
		label.setForeground(ColorScheme.BRAND_ORANGE);
		return label;
	}

	private static GridBagConstraints constraints(int row, int column, double weightX)
	{
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = column;
		constraints.gridy = row;
		constraints.weightx = weightX;
		constraints.fill = weightX > 0 ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
		constraints.anchor = GridBagConstraints.WEST;
		constraints.insets = new Insets(1, 2, 1, 2);
		return constraints;
	}
}
