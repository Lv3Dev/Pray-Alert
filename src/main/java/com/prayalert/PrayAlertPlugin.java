package com.prayalert;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.prayalert.model.AnimationRule;
import com.prayalert.model.AttackTimerRule;
import com.prayalert.model.DurationMode;
import com.prayalert.model.Entry;
import com.prayalert.model.Folder;
import com.prayalert.model.OverheadPrayer;
import com.prayalert.model.PrayAlertSave;
import com.prayalert.model.ProjectileRule;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Deque;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ParamID;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.AreaSoundEffectPlayed;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.events.SoundEffectPlayed;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;

@Slf4j
@PluginDescriptor(
	name = "Pray Alert",
	description = "Highlights an overhead prayer when a configured boss animation or projectile fires",
	tags = {"prayer", "boss", "animation", "projectile"}
)
public class PrayAlertPlugin extends Plugin
{
	static final String CONFIG_GROUP = "prayalert";
	static final String ENTRIES_KEY = "entries";

	@Inject
	private Client client;

	@Inject
	private Gson gson;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private InfoBoxManager infoBoxManager;

	@Inject
	private SpriteManager spriteManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private PrayAlertConfig config;

	@Inject
	private PrayAlertOverlay overlay;

	private final List<ActiveAnimation> activeAnimations = new ArrayList<>();
	private final List<ActiveAttackTimer> activeTimers = new ArrayList<>();
	private final List<LearnSample> learnSamples = new ArrayList<>();
	private final Map<String, CurseRuntime> curseRuntimes = new java.util.HashMap<>();
	private final Map<OverheadPrayer, Integer> componentCache = new EnumMap<>(OverheadPrayer.class);

	private PrayAlertSave data = new PrayAlertSave();
	private ActiveProjectile activeProjectile;
	private PrayAlertInfobox infobox;
	private boolean infoboxSpriteLoaded;
	private final Set<Integer> spriteMisses = new HashSet<>();
	private NavigationButton navButton;
	private PrayAlertPanel panel;
	private long sequence;
	private int cachedBookKey = Integer.MIN_VALUE;

	@Override
	protected void startUp()
	{
		load();
		overlayManager.add(overlay);
		panel = new PrayAlertPanel(this);
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
		}
		navButton = NavigationButton.builder()
			.tooltip("Pray Alert")
			.icon(createIcon())
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		log.info("Pray Alert started");
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		clearRuntime();
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		panel = null;
		log.info("Pray Alert stopped");
	}

	@Provides
	PrayAlertConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PrayAlertConfig.class);
	}

	public synchronized void update(Runnable mutation)
	{
		if (mutation != null)
		{
			mutation.run();
		}
		persist();
		if (clientThread != null)
		{
			clientThread.invoke(this::refreshAndSync);
		}
	}

	public void addRootEntry()
	{
		update(() ->
		{
			Entry entry = new Entry();
			entry.setTitle("Entry " + nextFreeNumber(entryTitles(), "Entry "));
			entry.setCollapsed(true);
			data.getEntries().add(entry);
		});
	}

	public void addFolder()
	{
		update(() ->
		{
			Folder folder = new Folder();
			folder.setTitle("Folder " + nextFreeNumber(folderTitles(), "Folder "));
			folder.setCollapsed(true);
			data.getFolders().add(folder);
		});
	}

	public void addEntryToFolder(String folderId)
	{
		if (folderId == null)
		{
			return;
		}
		update(() ->
		{
			Folder folder = findFolder(folderId);
			if (folder == null)
			{
				return;
			}
			Entry entry = new Entry();
			entry.setTitle("Entry " + nextFreeNumber(entryTitles(), "Entry "));
			entry.setCollapsed(true);
			folder.getEntries().add(entry);
		});
	}

	public void deleteEntry(String entryId)
	{
		if (entryId == null)
		{
			return;
		}
		update(() ->
		{
			data.getEntries().removeIf(entry -> entryId.equals(entry.getId()));
			for (Folder folder : data.getFolders())
			{
				if (folder.getEntries() != null)
				{
					folder.getEntries().removeIf(entry -> entryId.equals(entry.getId()));
				}
			}
			activeAnimations.removeIf(alert -> entryId.equals(alert.entryId));
			activeTimers.removeIf(timer -> entryId.equals(timer.entryId));
			if (activeProjectile != null && entryId.equals(activeProjectile.entryId))
			{
				activeProjectile = null;
			}
			curseRuntimes.remove(entryId);
		});
	}

	public void deleteFolder(String folderId)
	{
		if (folderId == null)
		{
			return;
		}
		update(() ->
		{
			Folder folder = findFolder(folderId);
			if (folder == null)
			{
				return;
			}
			if (folder.getEntries() != null)
			{
				for (Entry entry : folder.getEntries())
				{
					if (entry == null || entry.getId() == null)
					{
						continue;
					}
					String entryId = entry.getId();
					activeAnimations.removeIf(alert -> entryId.equals(alert.entryId));
					activeTimers.removeIf(timer -> entryId.equals(timer.entryId));
					if (activeProjectile != null && entryId.equals(activeProjectile.entryId))
					{
						activeProjectile = null;
					}
					curseRuntimes.remove(entryId);
				}
			}
			data.getFolders().remove(folder);
		});
	}

	public synchronized List<Folder> copyFolders()
	{
		return new ArrayList<>(data.getFolders());
	}

	public synchronized List<Entry> copyRootEntries()
	{
		return new ArrayList<>(data.getEntries());
	}

	public boolean showPrayerInfobox()
	{
		return config != null && config.showPrayerInfobox();
	}

	public boolean showInfoboxCountdown()
	{
		return config != null && config.showTickCountdownInfobox();
	}

	public boolean showPrayerTabCountdown()
	{
		return config != null && config.showTickCountdownPrayerTab();
	}

	public boolean recolourWhenOneTick()
	{
		return config != null && config.recolourOneTick();
	}

	public Color prayerFill(OverheadPrayer prayer)
	{
		Color color = highlightColor(prayer);
		if (color == null)
		{
			return prayer == null ? null : prayer.fill();
		}
		return color;
	}

	public Color prayerOutline(OverheadPrayer prayer)
	{
		Color color = highlightColor(prayer);
		if (color == null || prayer == null)
		{
			return prayer == null ? Color.WHITE : prayer.color();
		}
		return new Color(color.getRed(), color.getGreen(), color.getBlue());
	}

	public synchronized void clearAttackTimerSamples(String entryId, int animationId)
	{
		learnSamples.removeIf(sample -> entryId != null && entryId.equals(sample.entryId) && sample.animationId == animationId);
	}

	private Color highlightColor(OverheadPrayer prayer)
	{
		if (config == null || prayer == null)
		{
			return null;
		}
		switch (prayer)
		{
			case PROTECT_FROM_MISSILES:
				return config.rangeHighlight();
			case PROTECT_FROM_MAGIC:
				return config.mageHighlight();
			case PROTECT_FROM_MELEE:
			default:
				return config.meleeHighlight();
		}
	}

	@Subscribe
	public synchronized void onAnimationChanged(AnimationChanged event)
	{
		if (client == null || event == null || !(event.getActor() instanceof NPC))
		{
			return;
		}

		NPC npc = (NPC) event.getActor();
		int animationId = npc.getAnimation();
		dropEndedAnimations(npc, animationId);

		if (NightmareCurse.isStartAnimation(animationId))
		{
			startCurse(npc, true);
		}

		for (Entry entry : enabledEntries())
		{
			AnimationRule matched = matchAnimation(entry, animationId);
			if (matched != null)
			{
				upsertAnimation(npc, entry, matched);
			}
			if (entry.isCurseEnabled() && NightmareCurse.isStandardAttack(animationId))
			{
				consumeCurseCharge(entry, npc);
			}
			upsertAttackTimer(npc, entry, animationId);
		}
		syncInfobox();
	}

	@Subscribe
	public synchronized void onChatMessage(ChatMessage event)
	{
		if (event == null || !curseArmed())
		{
			return;
		}
		String message = event.getMessage();
		if (NightmareCurse.isStartMessage(message))
		{
			if (startCurse(null, false))
			{
				syncInfobox();
			}
		}
		else if (NightmareCurse.isEndMessage(message))
		{
			if (endCurse())
			{
				syncInfobox();
			}
		}
	}

	@Subscribe
	public synchronized void onSoundEffectPlayed(SoundEffectPlayed event)
	{
		onCurseEndSound(event == null ? -1 : event.getSoundId());
	}

	@Subscribe
	public synchronized void onAreaSoundEffectPlayed(AreaSoundEffectPlayed event)
	{
		onCurseEndSound(event == null ? -1 : event.getSoundId());
	}

	@Subscribe
	public synchronized void onGameTick(GameTick tick)
	{
		Iterator<ActiveAnimation> alerts = activeAnimations.iterator();
		while (alerts.hasNext())
		{
			ActiveAnimation alert = alerts.next();
			if (!animationStillRelevant(alert))
			{
				alerts.remove();
				continue;
			}
			if (alert.untilEnds)
			{
				continue;
			}
			// The GameTick of the tick the animation started does not consume a count,
			// so "For N ticks" stays visible for N ticks when the animation packet arrives first.
			if (alert.fresh)
			{
				alert.fresh = false;
				continue;
			}
			alert.ticksLeft--;
			if (alert.ticksLeft <= 0)
			{
				alerts.remove();
			}
		}
		tickAttackTimers();
		tickCurseRuntimes();
		syncInfobox();
	}

	@Subscribe
	public synchronized void onClientTick(ClientTick tick)
	{
		refreshProjectile();
		syncInfobox();
	}

	@Subscribe
	public synchronized void onProjectileMoved(ProjectileMoved event)
	{
		// ProjectileSpawned was replaced by ProjectileMoved. The live projectile deque is still scanned.
		refreshProjectile(event == null ? null : event.getProjectile());
		syncInfobox();
	}

	@Subscribe
	public synchronized void onNpcDespawned(NpcDespawned event)
	{
		if (event == null || event.getNpc() == null)
		{
			return;
		}
		forgetNpc(event.getNpc());
		syncInfobox();
	}

	@Subscribe
	public synchronized void onActorDeath(ActorDeath event)
	{
		if (event == null || !(event.getActor() instanceof NPC))
		{
			return;
		}
		forgetNpc((NPC) event.getActor());
		syncInfobox();
	}

	@Subscribe
	public synchronized void onGameStateChanged(GameStateChanged event)
	{
		if (event == null || event.getGameState() == null)
		{
			return;
		}
		switch (event.getGameState())
		{
			case LOADING:
			case HOPPING:
			case LOGIN_SCREEN:
			case CONNECTION_LOST:
				clearRuntime();
				break;
			default:
				break;
		}
	}

	@Subscribe
	public synchronized void onConfigChanged(ConfigChanged event)
	{
		if (event == null || !CONFIG_GROUP.equals(event.getGroup()) || ENTRIES_KEY.equals(event.getKey()))
		{
			return;
		}
		syncInfobox();
	}

	public synchronized AlertView getAlert()
	{
		if (activeProjectile != null && !projectileStillValid(activeProjectile))
		{
			activeProjectile = null;
			refreshProjectile();
		}
		if (activeProjectile != null && projectileStillValid(activeProjectile))
		{
			int ticks = AlertMath.ticksLeft(activeProjectile.projectile.getRemainingCycles());
			if (ticks > 0)
			{
				return viewFor(activeProjectile.entryId, activeProjectile.prayer, true, ticks, true);
			}
			activeProjectile = null;
		}

		ActiveAnimation best = null;
		Iterator<ActiveAnimation> alerts = activeAnimations.iterator();
		while (alerts.hasNext())
		{
			ActiveAnimation alert = alerts.next();
			if (!animationStillRelevant(alert))
			{
				alerts.remove();
				continue;
			}
			if (best == null || alert.sequence > best.sequence)
			{
				best = alert;
			}
		}
		if (best != null)
		{
			return viewFor(best.entryId, best.prayer, false, -1, false);
		}

		ActiveAttackTimer timer = soonestTimer();
		if (timer == null)
		{
			return null;
		}
		return viewFor(timer.entryId, timer.prayer, false, timer.ticksLeft, true);
	}

	public Widget prayerWidget(OverheadPrayer prayer)
	{
		if (prayer == null || client == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return null;
		}

		Widget book = client.getWidget(InterfaceID.Prayerbook.UNIVERSE);
		if (book == null || book.isHidden())
		{
			return null;
		}

		int componentId = componentId(prayer);
		if (componentId <= 0)
		{
			return null;
		}

		Widget widget = client.getWidget(componentId);
		if (widget == null || widget.isHidden())
		{
			return null;
		}
		return widget;
	}

	private void refreshAndSync()
	{
		synchronized (this)
		{
			refreshProjectile();
			syncInfobox();
		}
	}

	private void load()
	{
		data = new PrayAlertSave();
		if (configManager == null || gson == null)
		{
			data.normalize();
			return;
		}
		String json = configManager.getConfiguration(CONFIG_GROUP, ENTRIES_KEY);
		if (json == null || json.trim().isEmpty())
		{
			data.normalize();
			return;
		}
		try
		{
			PrayAlertSave loaded = gson.fromJson(json, PrayAlertSave.class);
			if (loaded != null)
			{
				data = loaded;
			}
		}
		catch (RuntimeException ex)
		{
			log.debug("Could not load pray alert entries", ex);
			data = new PrayAlertSave();
		}
		data.normalize();
	}

	private void persist()
	{
		if (configManager == null || gson == null || data == null)
		{
			return;
		}
		try
		{
			configManager.setConfiguration(CONFIG_GROUP, ENTRIES_KEY, gson.toJson(data));
		}
		catch (RuntimeException ex)
		{
			log.debug("Could not save pray alert entries", ex);
		}
	}

	private void clearRuntime()
	{
		activeAnimations.clear();
		activeTimers.clear();
		learnSamples.clear();
		activeProjectile = null;
		curseRuntimes.clear();
		componentCache.clear();
		cachedBookKey = Integer.MIN_VALUE;
		removeInfobox();
	}

	private void forgetNpc(NPC npc)
	{
		activeAnimations.removeIf(alert -> alert.npc == npc);
		activeTimers.removeIf(timer -> timer.npc == npc);
		learnSamples.removeIf(sample -> sample.npc == npc);
		for (CurseRuntime runtime : curseRuntimes.values())
		{
			if (runtime.source == npc)
			{
				runtime.active = false;
				runtime.charges = 0;
				runtime.lingerTicks = 0;
				runtime.source = null;
			}
		}
	}

	private void dropEndedAnimations(NPC npc, int animationId)
	{
		Iterator<ActiveAnimation> alerts = activeAnimations.iterator();
		while (alerts.hasNext())
		{
			ActiveAnimation alert = alerts.next();
			if (alert.npc == npc && alert.untilEnds && alert.animationId != animationId)
			{
				alerts.remove();
			}
		}
	}

	private boolean startCurse(NPC npc, boolean restart)
	{
		boolean started = false;
		for (Entry entry : enabledEntries())
		{
			if (entry == null || !entry.isCurseEnabled())
			{
				continue;
			}
			CurseRuntime runtime = curseRuntimes.computeIfAbsent(entry.getId(), id -> new CurseRuntime());
			boolean running = runtime.active && (runtime.charges > 0 || runtime.lingerTicks > 0);
			if (!restart && running)
			{
				if (npc != null && runtime.source == null)
				{
					runtime.source = npc;
				}
				continue;
			}
			runtime.active = true;
			runtime.charges = NightmareCurse.ATTACKS_UNTIL_CLEAR;
			runtime.lingerTicks = 0;
			if (npc != null)
			{
				runtime.source = npc;
			}
			started = true;
		}
		return started;
	}

	private boolean endCurse()
	{
		boolean ended = false;
		for (CurseRuntime runtime : curseRuntimes.values())
		{
			if (!runtime.active && runtime.lingerTicks <= 0 && runtime.charges <= 0)
			{
				continue;
			}
			runtime.active = false;
			runtime.charges = 0;
			runtime.lingerTicks = 0;
			runtime.source = null;
			ended = true;
		}
		return ended;
	}

	private void onCurseEndSound(int soundId)
	{
		if (!NightmareCurse.isEndSound(soundId) || !curseHasProgressed())
		{
			return;
		}
		if (endCurse())
		{
			syncInfobox();
		}
	}

	private boolean curseArmed()
	{
		for (Entry entry : enabledEntries())
		{
			if (entry != null && entry.isCurseEnabled())
			{
				return true;
			}
		}
		return false;
	}

	private boolean curseHasProgressed()
	{
		for (CurseRuntime runtime : curseRuntimes.values())
		{
			if (runtime.active && runtime.charges < NightmareCurse.ATTACKS_UNTIL_CLEAR)
			{
				return true;
			}
		}
		return false;
	}

	private void consumeCurseCharge(Entry entry, NPC npc)
	{
		CurseRuntime runtime = curseRuntimes.get(entry.getId());
		if (runtime == null || !runtime.active || runtime.charges <= 0)
		{
			return;
		}
		if (runtime.source != null && runtime.source != npc)
		{
			return;
		}
		if (runtime.source == null)
		{
			runtime.source = npc;
		}
		runtime.charges--;
		if (runtime.charges <= 0)
		{
			// The attack that spends the last charge stays shuffled. The wear-off message or the
			// end sound still clears it immediately. This only runs out if both of those are missed.
			runtime.lingerTicks = NightmareCurse.LINGER_TICKS;
		}
	}

	private void clearCurseRuntime(String entryId)
	{
		CurseRuntime runtime = curseRuntimes.get(entryId);
		if (runtime == null)
		{
			return;
		}
		runtime.active = false;
		runtime.charges = 0;
		runtime.lingerTicks = 0;
		runtime.source = null;
	}

	private void tickCurseRuntimes()
	{
		for (CurseRuntime runtime : curseRuntimes.values())
		{
			if (runtime.source != null && runtime.source.isDead())
			{
				runtime.active = false;
				runtime.charges = 0;
				runtime.lingerTicks = 0;
				runtime.source = null;
				continue;
			}
			if (runtime.lingerTicks > 0)
			{
				runtime.lingerTicks--;
				if (runtime.lingerTicks == 0 && runtime.charges <= 0)
				{
					runtime.active = false;
				}
			}
		}
	}

	private AnimationRule matchAnimation(Entry entry, int animationId)
	{
		if (entry.getAnimationRules() == null || animationId <= 0)
		{
			return null;
		}
		for (AnimationRule rule : entry.getAnimationRules())
		{
			if (rule == null || rule.getPrayer() == null || rule.getAnimationId() <= 0)
			{
				continue;
			}
			if (rule.getAnimationId() == animationId)
			{
				return rule;
			}
		}
		return null;
	}

	private void upsertAnimation(NPC npc, Entry entry, AnimationRule rule)
	{
		for (ActiveAnimation alert : activeAnimations)
		{
			if (alert.npc == npc && entry.getId().equals(alert.entryId) && alert.animationId == rule.getAnimationId())
			{
				alert.prayer = rule.getPrayer();
				alert.untilEnds = rule.getDurationMode() != DurationMode.FOR_TICKS;
				alert.ticksLeft = Math.max(1, rule.getTicks());
				alert.fresh = true;
				alert.sequence = ++sequence;
				return;
			}
		}

		ActiveAnimation alert = new ActiveAnimation();
		alert.npc = npc;
		alert.entryId = entry.getId();
		alert.animationId = rule.getAnimationId();
		alert.prayer = rule.getPrayer();
		alert.untilEnds = rule.getDurationMode() != DurationMode.FOR_TICKS;
		alert.ticksLeft = Math.max(1, rule.getTicks());
		alert.fresh = true;
		alert.sequence = ++sequence;
		activeAnimations.add(alert);
	}

	private boolean animationStillRelevant(ActiveAnimation alert)
	{
		if (alert == null || alert.npc == null || alert.npc.isDead() || alert.prayer == null)
		{
			return false;
		}
		Entry entry = findEntry(alert.entryId);
		if (!effectivelyEnabled(entry))
		{
			return false;
		}
		if (alert.untilEnds && alert.npc.getAnimation() != alert.animationId)
		{
			return false;
		}
		return true;
	}

	private void refreshProjectile()
	{
		refreshProjectile(null);
	}

	private void refreshProjectile(Projectile extra)
	{
		if (client == null || client.getGameState() != GameState.LOGGED_IN)
		{
			activeProjectile = null;
			return;
		}
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			activeProjectile = null;
			return;
		}

		Deque<Projectile> projectiles = client.getProjectiles();
		ActiveProjectile best = null;
		if (projectiles != null)
		{
			for (Projectile projectile : projectiles)
			{
				best = considerProjectile(best, projectile, player);
			}
		}
		if (extra != null && (projectiles == null || !containsProjectile(projectiles, extra)))
		{
			best = considerProjectile(best, extra, player);
		}
		activeProjectile = best;
	}

	private ActiveProjectile considerProjectile(ActiveProjectile best, Projectile projectile, Player player)
	{
		if (projectile == null || projectile.getRemainingCycles() <= 0 || !targetsPlayer(projectile, player))
		{
			return best;
		}
		MatchedProjectile matched = matchProjectile(projectile.getId());
		if (matched == null)
		{
			return best;
		}
		if (best != null && !sooner(projectile, best.projectile))
		{
			return best;
		}
		ActiveProjectile candidate = new ActiveProjectile();
		candidate.projectile = projectile;
		candidate.entryId = matched.entryId;
		candidate.prayer = matched.prayer;
		return candidate;
	}

	private boolean projectileStillValid(ActiveProjectile alert)
	{
		if (alert == null || alert.projectile == null || alert.prayer == null)
		{
			return false;
		}
		if (alert.projectile.getRemainingCycles() <= 0)
		{
			return false;
		}
		if (!effectivelyEnabled(findEntry(alert.entryId)))
		{
			return false;
		}
		if (client == null)
		{
			return false;
		}
		Deque<Projectile> projectiles = client.getProjectiles();
		return projectiles != null && containsProjectile(projectiles, alert.projectile);
	}

	private static boolean containsProjectile(Iterable<Projectile> projectiles, Projectile projectile)
	{
		for (Projectile candidate : projectiles)
		{
			if (candidate == projectile)
			{
				return true;
			}
		}
		return false;
	}

	private static boolean sooner(Projectile candidate, Projectile current)
	{
		if (candidate.getRemainingCycles() != current.getRemainingCycles())
		{
			return candidate.getRemainingCycles() < current.getRemainingCycles();
		}
		return candidate.getEndCycle() < current.getEndCycle();
	}

	private boolean targetsPlayer(Projectile projectile, Player player)
	{
		if (projectile == null || player == null)
		{
			return false;
		}
		Actor target = projectile.getTargetActor();
		if (target == player)
		{
			return true;
		}
		WorldPoint targetPoint = projectile.getTargetPoint();
		WorldPoint playerPoint = player.getWorldLocation();
		return targetPoint != null && playerPoint != null && targetPoint.equals(playerPoint);
	}

	private MatchedProjectile matchProjectile(int projectileId)
	{
		if (projectileId <= 0)
		{
			return null;
		}
		for (Entry entry : enabledEntries())
		{
			if (entry.getProjectileRules() == null)
			{
				continue;
			}
			for (ProjectileRule rule : entry.getProjectileRules())
			{
				if (rule == null || rule.getPrayer() == null || rule.getProjectileId() <= 0)
				{
					continue;
				}
				if (rule.getProjectileId() == projectileId)
				{
					MatchedProjectile matched = new MatchedProjectile();
					matched.entryId = entry.getId();
					matched.prayer = rule.getPrayer();
					return matched;
				}
			}
		}
		return null;
	}

	private AlertView viewFor(String entryId, OverheadPrayer needed, boolean projectile, int ticksLeft, boolean countdown)
	{
		if (needed == null)
		{
			return null;
		}
		Entry entry = findEntry(entryId);
		boolean curse = isCurseActive(entry);
		OverheadPrayer displayed = curse ? needed.shuffled() : needed;
		boolean ruinous = isRuinousBook();
		String tooltip = curse
			? "Click " + displayed.buttonName(ruinous) + " (activates " + needed.getDisplayName() + ")"
			: needed.getDisplayName();
		return new AlertView(needed, displayed, curse, projectile, countdown, ticksLeft, tooltip, ruinous);
	}

	private boolean isCurseActive(Entry entry)
	{
		if (entry == null || !entry.isCurseEnabled() || !effectivelyEnabled(entry))
		{
			return false;
		}
		CurseRuntime runtime = curseRuntimes.get(entry.getId());
		return runtime != null && runtime.active && (runtime.charges > 0 || runtime.lingerTicks > 0);
	}

	private boolean isRuinousBook()
	{
		return client != null
			&& client.getGameState() == GameState.LOGGED_IN
			&& client.getVarbitValue(VarbitID.PRAYERBOOK) == 1;
	}

	public void setCurseEnabled(Entry entry, boolean enabled)
	{
		if (entry == null)
		{
			return;
		}
		update(() ->
		{
			entry.setCurseEnabled(enabled);
			if (!enabled)
			{
				clearCurseRuntime(entry.getId());
			}
		});
	}

	private List<Entry> enabledEntries()
	{
		List<Entry> enabled = new ArrayList<>();
		for (Entry entry : data.getEntries())
		{
			if (effectivelyEnabled(entry))
			{
				enabled.add(entry);
			}
		}
		for (Folder folder : data.getFolders())
		{
			if (folder == null || !folder.isEnabled() || folder.getEntries() == null)
			{
				continue;
			}
			for (Entry entry : folder.getEntries())
			{
				if (entry != null && entry.isEnabled())
				{
					enabled.add(entry);
				}
			}
		}
		return enabled;
	}

	private boolean effectivelyEnabled(Entry entry)
	{
		if (entry == null || !entry.isEnabled())
		{
			return false;
		}
		Folder folder = folderOf(entry.getId());
		return folder == null || folder.isEnabled();
	}

	private Folder folderOf(String entryId)
	{
		if (entryId == null)
		{
			return null;
		}
		for (Folder folder : data.getFolders())
		{
			if (folder == null || folder.getEntries() == null)
			{
				continue;
			}
			for (Entry entry : folder.getEntries())
			{
				if (entry != null && entryId.equals(entry.getId()))
				{
					return folder;
				}
			}
		}
		return null;
	}

	private Entry findEntry(String entryId)
	{
		if (entryId == null)
		{
			return null;
		}
		for (Entry entry : data.getEntries())
		{
			if (entry != null && entryId.equals(entry.getId()))
			{
				return entry;
			}
		}
		for (Folder folder : data.getFolders())
		{
			if (folder == null || folder.getEntries() == null)
			{
				continue;
			}
			for (Entry entry : folder.getEntries())
			{
				if (entry != null && entryId.equals(entry.getId()))
				{
					return entry;
				}
			}
		}
		return null;
	}

	private Folder findFolder(String folderId)
	{
		for (Folder folder : data.getFolders())
		{
			if (folder != null && folderId.equals(folder.getId()))
			{
				return folder;
			}
		}
		return null;
	}

	private int componentId(OverheadPrayer prayer)
	{
		int bookKey = bookKey();
		if (bookKey != cachedBookKey)
		{
			componentCache.clear();
			cachedBookKey = bookKey;
		}
		Integer cached = componentCache.get(prayer);
		if (cached != null)
		{
			return cached;
		}
		int resolved = resolveComponent(prayer);
		if (resolved <= 0)
		{
			resolved = fallbackComponent(prayer);
		}
		if (resolved > 0)
		{
			componentCache.put(prayer, resolved);
		}
		return resolved;
	}

	private int bookKey()
	{
		if (client == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return Integer.MIN_VALUE;
		}
		int book = client.getVarbitValue(VarbitID.PRAYERBOOK);
		int deadeye = client.getVarbitValue(VarbitID.PRAYER_DEADEYE_UNLOCKED);
		int vigour = client.getVarbitValue(VarbitID.PRAYER_MYSTIC_VIGOUR_UNLOCKED);
		return book | (deadeye << 1) | (vigour << 2);
	}

	private int resolveComponent(OverheadPrayer prayer)
	{
		if (client == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return -1;
		}
		EnumComposition prayerEnum = prayerBookEnum();
		if (prayerEnum == null)
		{
			return -1;
		}
		int[] itemIds = prayerEnum.getIntVals();
		if (itemIds == null)
		{
			return -1;
		}

		boolean ruinous = isRuinousBook();
		String wantName = prayer.buttonName(ruinous);
		int wantLevel = ruinous ? prayer.getRuinousLevel() : prayer.getNormalLevel();
		int levelMatch = -1;
		for (int itemId : itemIds)
		{
			ItemComposition definition = client.getItemDefinition(itemId);
			if (definition == null)
			{
				continue;
			}
			int component = definition.getIntValue(ParamID.OC_PRAYER_COMPONENT);
			if (component <= 0)
			{
				continue;
			}
			String name = definition.getName();
			if (name != null && name.equalsIgnoreCase(wantName))
			{
				return component;
			}
			if (levelMatch < 0 && definition.getIntValue(ParamID.OC_PRAYER_LEVEL) == wantLevel)
			{
				levelMatch = component;
			}
		}
		return levelMatch;
	}

	private EnumComposition prayerBookEnum()
	{
		if (client.getVarbitValue(VarbitID.PRAYERBOOK) == 1)
		{
			return client.getEnum(EnumID.PRAYERS_RUINOUS);
		}
		boolean deadeye = client.getVarbitValue(VarbitID.PRAYER_DEADEYE_UNLOCKED) != 0;
		boolean vigour = client.getVarbitValue(VarbitID.PRAYER_MYSTIC_VIGOUR_UNLOCKED) != 0;
		if (deadeye && vigour)
		{
			return client.getEnum(EnumID.PRAYERS_NORMAL_DEADEYE_MYSTIC_VIGOUR);
		}
		if (deadeye)
		{
			return client.getEnum(EnumID.PRAYERS_NORMAL_DEADEYE);
		}
		if (vigour)
		{
			return client.getEnum(EnumID.PRAYERS_NORMAL_MYSTIC_VIGOUR);
		}
		return client.getEnum(EnumID.PRAYERS_NORMAL);
	}

	private int fallbackComponent(OverheadPrayer prayer)
	{
		if (isRuinousBook())
		{
			return -1;
		}
		switch (prayer)
		{
			case PROTECT_FROM_MAGIC:
				return InterfaceID.Prayerbook.PRAYER13;
			case PROTECT_FROM_MISSILES:
				return InterfaceID.Prayerbook.PRAYER14;
			case PROTECT_FROM_MELEE:
			default:
				return InterfaceID.Prayerbook.PRAYER15;
		}
	}

	private void syncInfobox()
	{
		AlertView alert = getAlert();
		if (alert == null || alert.getDisplayed() == null || !showPrayerInfobox())
		{
			removeInfobox();
			return;
		}
		OverheadPrayer displayed = alert.getDisplayed();
		boolean ruinous = alert.isRuinousBook();
		if (infoBoxManager == null)
		{
			return;
		}
		if (infobox == null || infobox.getDisplayed() != displayed || infobox.isRuinous() != ruinous)
		{
			removeInfobox();
			PrayAlertInfobox created = new PrayAlertInfobox(PrayAlertInfobox.fallbackIcon(displayed), this, displayed, ruinous);
			infoBoxManager.addInfoBox(created);
			infobox = created;
			infoboxSpriteLoaded = false;
		}
		if (!infoboxSpriteLoaded)
		{
			BufferedImage sprite = loadPrayerSprite(displayed.spriteId(ruinous));
			if (sprite != null)
			{
				infobox.setImage(sprite);
				infoBoxManager.updateInfoBoxImage(infobox);
				infoboxSpriteLoaded = true;
			}
		}
		infobox.setTooltip(alert.getTooltip());
	}

	private BufferedImage loadPrayerSprite(int spriteId)
	{
		if (spriteManager == null || client == null || spriteId <= 0 || !client.isClientThread())
		{
			return null;
		}
		try
		{
			BufferedImage sprite = spriteManager.getSprite(spriteId, 0);
			if (sprite != null && sprite.getWidth() > 0 && sprite.getHeight() > 0)
			{
				return sprite;
			}
		}
		catch (RuntimeException | AssertionError ex)
		{
			if (spriteMisses.add(spriteId))
			{
				log.debug("Could not load prayer sprite {}", spriteId, ex);
			}
			return null;
		}
		if (spriteMisses.add(spriteId))
		{
			log.debug("Prayer sprite {} is not loaded yet", spriteId);
		}
		return null;
	}

	private void removeInfobox()
	{
		if (infobox != null && infoBoxManager != null)
		{
			infoBoxManager.removeInfoBox(infobox);
		}
		infobox = null;
		infoboxSpriteLoaded = false;
	}

	private static BufferedImage createIcon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(new Color(190, 40, 40));
		graphics.fillOval(1, 5, 4, 4);
		graphics.setColor(new Color(40, 170, 60));
		graphics.fillOval(6, 5, 4, 4);
		graphics.setColor(new Color(50, 90, 210));
		graphics.fillOval(11, 5, 4, 4);
		graphics.dispose();
		return image;
	}

	private void upsertAttackTimer(NPC npc, Entry entry, int animationId)
	{
		if (entry.getAttackTimers() == null || animationId <= 0)
		{
			return;
		}
		AttackTimerRule matched = null;
		for (AttackTimerRule rule : entry.getAttackTimers())
		{
			if (rule == null || rule.getPrayer() == null || rule.getAnimationId() <= 0)
			{
				continue;
			}
			if (rule.getAnimationId() == animationId && npcMatches(npc, rule))
			{
				matched = rule;
				break;
			}
		}
		if (matched == null)
		{
			return;
		}

		int delay = Math.max(1, matched.getDelay());
		if (matched.isLearnDelay() && !matched.isDelayLearned())
		{
			Integer learned = observeAttackGap(npc, entry.getId(), matched.getAnimationId());
			if (learned == null)
			{
				return;
			}
			matched.setDelay(learned);
			matched.setDelayLearned(true);
			delay = learned;
			persist();
			if (panel != null)
			{
				panel.refresh();
			}
		}
		for (ActiveAttackTimer timer : activeTimers)
		{
			if (timer.npc == npc && entry.getId().equals(timer.entryId) && timer.animationId == matched.getAnimationId())
			{
				timer.prayer = matched.getPrayer();
				timer.delay = delay;
				timer.ticksLeft = delay;
				timer.fresh = true;
				timer.sequence = ++sequence;
				return;
			}
		}

		ActiveAttackTimer timer = new ActiveAttackTimer();
		timer.npc = npc;
		timer.entryId = entry.getId();
		timer.animationId = matched.getAnimationId();
		timer.prayer = matched.getPrayer();
		timer.delay = delay;
		timer.ticksLeft = delay;
		timer.fresh = true;
		timer.sequence = ++sequence;
		activeTimers.add(timer);
	}

	private Integer observeAttackGap(NPC npc, String entryId, int animationId)
	{
		if (npc == null || entryId == null || client == null)
		{
			return null;
		}
		int now = client.getTickCount();
		LearnSample sample = null;
		for (LearnSample candidate : learnSamples)
		{
			if (candidate.npc == npc && entryId.equals(candidate.entryId) && candidate.animationId == animationId)
			{
				sample = candidate;
				break;
			}
		}
		if (sample == null)
		{
			sample = new LearnSample();
			sample.npc = npc;
			sample.entryId = entryId;
			sample.animationId = animationId;
			learnSamples.add(sample);
		}
		int previous = sample.lastTick;
		sample.lastTick = now;
		if (previous < 0)
		{
			return null;
		}
		int gap = now - previous;
		if (gap < 1 || gap > 99)
		{
			return null;
		}
		sample.gaps.add(gap);
		if (sample.gaps.size() > 6)
		{
			sample.gaps.remove(0);
		}
		return AttackTiming.learnedDelay(sample.gaps);
	}

	private void tickAttackTimers()
	{
		Iterator<ActiveAttackTimer> timers = activeTimers.iterator();
		while (timers.hasNext())
		{
			ActiveAttackTimer timer = timers.next();
			if (!timerStillRelevant(timer))
			{
				timers.remove();
				continue;
			}
			// The tick the animation is seen does not consume a count, so the full delay is visible.
			// At 0 the highlight ends. The next sighting of this animation starts it again.
			if (timer.fresh)
			{
				timer.fresh = false;
				continue;
			}
			if (timer.ticksLeft > 0)
			{
				timer.ticksLeft--;
			}
			if (timer.ticksLeft <= 0)
			{
				timers.remove();
			}
		}
	}

	private ActiveAttackTimer soonestTimer()
	{
		ActiveAttackTimer best = null;
		Iterator<ActiveAttackTimer> timers = activeTimers.iterator();
		while (timers.hasNext())
		{
			ActiveAttackTimer timer = timers.next();
			if (!timerStillRelevant(timer) || timer.ticksLeft <= 0)
			{
				timers.remove();
				continue;
			}
			if (best == null || timer.ticksLeft < best.ticksLeft || (timer.ticksLeft == best.ticksLeft && timer.sequence < best.sequence))
			{
				best = timer;
			}
		}
		return best;
	}

	private boolean timerStillRelevant(ActiveAttackTimer timer)
	{
		if (timer == null || timer.npc == null || timer.npc.isDead() || timer.prayer == null)
		{
			return false;
		}
		return effectivelyEnabled(findEntry(timer.entryId));
	}

	private boolean npcMatches(NPC npc, AttackTimerRule rule)
	{
		if (npc == null || rule == null)
		{
			return false;
		}
		if (rule.getNpcId() != null && rule.getNpcId() > 0)
		{
			return npc.getId() == rule.getNpcId();
		}
		String wanted = cleanNpcName(rule.getNpcName());
		if (wanted == null || wanted.isEmpty())
		{
			return false;
		}
		String actual = cleanNpcName(npcDefinitionName(npc));
		return actual != null && actual.equalsIgnoreCase(wanted);
	}

	private String npcDefinitionName(NPC npc)
	{
		NPCComposition composition = npc.getTransformedComposition();
		if (composition == null)
		{
			composition = npc.getComposition();
		}
		if (composition == null && client != null)
		{
			composition = client.getNpcDefinition(npc.getId());
		}
		return composition == null ? null : composition.getName();
	}

	private static String cleanNpcName(String name)
	{
		if (name == null)
		{
			return null;
		}
		return name.replace('\u00a0', ' ').trim();
	}

	private List<String> entryTitles()
	{
		List<String> titles = new ArrayList<>();
		for (Entry entry : data.getEntries())
		{
			if (entry != null)
			{
				titles.add(entry.getTitle());
			}
		}
		for (Folder folder : data.getFolders())
		{
			if (folder == null || folder.getEntries() == null)
			{
				continue;
			}
			for (Entry entry : folder.getEntries())
			{
				if (entry != null)
				{
					titles.add(entry.getTitle());
				}
			}
		}
		return titles;
	}

	private List<String> folderTitles()
	{
		List<String> titles = new ArrayList<>();
		for (Folder folder : data.getFolders())
		{
			if (folder != null)
			{
				titles.add(folder.getTitle());
			}
		}
		return titles;
	}

	static int nextFreeNumber(Iterable<String> titles, String prefix)
	{
		Set<Integer> used = new HashSet<>();
		if (titles != null && prefix != null)
		{
			for (String title : titles)
			{
				if (title == null || !title.startsWith(prefix))
				{
					continue;
				}
				try
				{
					int value = Integer.parseInt(title.substring(prefix.length()).trim());
					if (value > 0)
					{
						used.add(value);
					}
				}
				catch (NumberFormatException ex)
				{
					// Titles that are not "Prefix N" do not consume a number.
				}
			}
		}
		int number = 1;
		while (used.contains(number))
		{
			number++;
		}
		return number;
	}

	@Getter
	public static final class AlertView
	{
		private final OverheadPrayer needed;
		private final OverheadPrayer displayed;
		private final boolean curseActive;
		private final boolean projectile;
		private final boolean countdown;
		private final int ticksLeft;
		private final String tooltip;
		private final boolean ruinousBook;

		private AlertView(OverheadPrayer needed, OverheadPrayer displayed, boolean curseActive, boolean projectile, boolean countdown, int ticksLeft, String tooltip, boolean ruinousBook)
		{
			this.needed = needed;
			this.displayed = displayed;
			this.curseActive = curseActive;
			this.projectile = projectile;
			this.countdown = countdown;
			this.ticksLeft = ticksLeft;
			this.tooltip = tooltip;
			this.ruinousBook = ruinousBook;
		}

		public boolean hasCountdown()
		{
			return countdown && ticksLeft >= 0;
		}

		public boolean isOneTick()
		{
			return countdown && ticksLeft == 1;
		}
	}

	private static final class ActiveAnimation
	{
		private NPC npc;
		private String entryId;
		private OverheadPrayer prayer;
		private int animationId;
		private boolean untilEnds;
		private int ticksLeft;
		private boolean fresh;
		private long sequence;
	}

	private static final class LearnSample
	{
		private NPC npc;
		private String entryId;
		private int animationId;
		private int lastTick = -1;
		private final List<Integer> gaps = new ArrayList<>();
	}

	private static final class ActiveAttackTimer
	{
		private NPC npc;
		private String entryId;
		private OverheadPrayer prayer;
		private int animationId;
		private int delay;
		private int ticksLeft;
		private boolean fresh;
		private long sequence;
	}

	private static final class ActiveProjectile
	{
		private Projectile projectile;
		private String entryId;
		private OverheadPrayer prayer;
	}

	private static final class MatchedProjectile
	{
		private String entryId;
		private OverheadPrayer prayer;
	}

	private static final class CurseRuntime
	{
		private boolean active;
		private int charges;
		private int lingerTicks;
		private NPC source;
	}
}
