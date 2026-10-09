package com.prayalert;

/**
 * Phosani's Nightmare curse, from the cache names and the in-fight game messages.
 * Animation 8599 is {@code NIGHTMARE_ATTACK_CONFUSION}. Sound 4260 is {@code noa_confusion_end},
 * and 4330 is the paired {@code noa_confusion_end_swirl_scream}. The curse lifts after 5 attacks.
 */
final class NightmareCurse
{
	static final int START_ANIMATION = 8599;
	static final int END_SOUND = 4260;
	static final int END_SWIRL_SOUND = 4330;
	static final int MELEE_ATTACK = 8594;
	static final int MAGIC_ATTACK = 8595;
	static final int RANGED_ATTACK = 8596;
	static final int ATTACKS_UNTIL_CLEAR = 5;
	/**
	 * Game ticks to keep the fifth attack shuffled when the wear-off message and the end sound
	 * never arrive. Her prayer window lasts a few ticks after the attack animation.
	 */
	static final int LINGER_TICKS = 5;

	private NightmareCurse()
	{
	}

	static boolean isStartAnimation(int animationId)
	{
		return animationId == START_ANIMATION;
	}

	static boolean isStandardAttack(int animationId)
	{
		return animationId == MELEE_ATTACK || animationId == MAGIC_ATTACK || animationId == RANGED_ATTACK;
	}

	static boolean isEndSound(int soundId)
	{
		return soundId == END_SOUND || soundId == END_SWIRL_SOUND;
	}

	static boolean isStartMessage(String message)
	{
		return plain(message).contains("the nightmare has cursed you");
	}

	static boolean isEndMessage(String message)
	{
		String text = plain(message);
		return text.contains("nightmare") && text.contains("curse wear off");
	}

	private static String plain(String message)
	{
		if (message == null || message.isEmpty())
		{
			return "";
		}
		return message.replaceAll("<[^>]*>", "").toLowerCase(java.util.Locale.ENGLISH);
	}
}
