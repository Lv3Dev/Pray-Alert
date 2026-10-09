package com.prayalert;

import java.util.Arrays;
import java.util.List;

final class AttackTiming
{
	private AttackTiming()
	{
	}

	/**
	 * Two measured gaps are enough to estimate the attack speed. The result is the middle gap.
	 */
	static Integer learnedDelay(List<Integer> gaps)
	{
		if (gaps == null || gaps.size() < 2)
		{
			return null;
		}
		int[] values = new int[gaps.size()];
		for (int i = 0; i < gaps.size(); i++)
		{
			Integer gap = gaps.get(i);
			if (gap == null || gap < 1)
			{
				return null;
			}
			values[i] = gap;
		}
		Arrays.sort(values);
		int middle = values.length / 2;
		if (values.length % 2 == 1)
		{
			return values[middle];
		}
		return (values[middle - 1] + values[middle] + 1) / 2;
	}
}
