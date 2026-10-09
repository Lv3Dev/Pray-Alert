package com.prayalert.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
public class PrayAlertSave
{
	private List<Folder> folders = new ArrayList<>();
	private List<Entry> entries = new ArrayList<>();

	public void normalize()
	{
		if (folders == null)
		{
			folders = new ArrayList<>();
		}
		if (entries == null)
		{
			entries = new ArrayList<>();
		}

		folders.removeIf(folder -> folder == null);
		entries.removeIf(entry -> entry == null);

		for (Folder folder : folders)
		{
			if (folder.getId() == null || folder.getId().trim().isEmpty())
			{
				folder.setId(UUID.randomUUID().toString());
			}
			if (folder.getTitle() == null || folder.getTitle().trim().isEmpty())
			{
				folder.setTitle("Folder");
			}
			else
			{
				folder.setTitle(folder.getTitle().trim());
			}
			if (folder.getEntries() == null)
			{
				folder.setEntries(new ArrayList<>());
			}
			folder.getEntries().removeIf(entry -> entry == null);
			for (Entry entry : folder.getEntries())
			{
				normalizeEntry(entry);
			}
		}

		for (Entry entry : entries)
		{
			normalizeEntry(entry);
		}
	}

	private static void normalizeEntry(Entry entry)
	{
		if (entry.getId() == null || entry.getId().trim().isEmpty())
		{
			entry.setId(UUID.randomUUID().toString());
		}
		if (entry.getTitle() == null || entry.getTitle().trim().isEmpty())
		{
			entry.setTitle("Untitled");
		}
		else
		{
			entry.setTitle(entry.getTitle().trim());
		}
		if (entry.getAnimationRules() == null)
		{
			entry.setAnimationRules(new ArrayList<>());
		}
		if (entry.getProjectileRules() == null)
		{
			entry.setProjectileRules(new ArrayList<>());
		}
		if (entry.getAttackTimers() == null)
		{
			entry.setAttackTimers(new ArrayList<>());
		}
		entry.getAnimationRules().removeIf(rule -> rule == null);
		entry.getProjectileRules().removeIf(rule -> rule == null);
		entry.getAttackTimers().removeIf(rule -> rule == null);

		for (AnimationRule rule : entry.getAnimationRules())
		{
			if (rule.getPrayer() == null)
			{
				rule.setPrayer(OverheadPrayer.PROTECT_FROM_MELEE);
			}
			if (rule.getDurationMode() == null)
			{
				rule.setDurationMode(DurationMode.UNTIL_ANIMATION_ENDS);
			}
			if (rule.getTicks() < 1)
			{
				rule.setTicks(2);
			}
		}
		for (ProjectileRule rule : entry.getProjectileRules())
		{
			if (rule.getPrayer() == null)
			{
				rule.setPrayer(OverheadPrayer.PROTECT_FROM_MAGIC);
			}
		}
		for (AttackTimerRule rule : entry.getAttackTimers())
		{
			if (rule.getPrayer() == null)
			{
				rule.setPrayer(OverheadPrayer.PROTECT_FROM_MELEE);
			}
			if (rule.getDelay() < 1)
			{
				rule.setDelay(5);
			}
			if (!rule.isLearnDelay())
			{
				rule.setDelayLearned(false);
			}
			if (rule.getNpcId() != null && rule.getNpcId() <= 0)
			{
				rule.setNpcId(null);
			}
			if (rule.getNpcName() == null)
			{
				rule.setNpcName("");
			}
			else
			{
				rule.setNpcName(rule.getNpcName().trim());
			}
		}
	}
}
