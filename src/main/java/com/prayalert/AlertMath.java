package com.prayalert;

import net.runelite.api.Constants;

final class AlertMath
{
	static final int CYCLES_PER_TICK = Constants.GAME_TICK_LENGTH / Constants.CLIENT_TICK_LENGTH;

	private AlertMath()
	{
	}

	static int ticksLeft(int remainingCycles)
	{
		if (remainingCycles <= 0 || CYCLES_PER_TICK <= 0)
		{
			return 0;
		}
		return (int) Math.ceil(remainingCycles / (double) CYCLES_PER_TICK);
	}
}
