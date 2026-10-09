package com.prayalert.model;

import lombok.Data;

@Data
public class AnimationRule
{
	private int animationId;
	private OverheadPrayer prayer = OverheadPrayer.PROTECT_FROM_MELEE;
	private DurationMode durationMode = DurationMode.UNTIL_ANIMATION_ENDS;
	private int ticks = 2;
}
