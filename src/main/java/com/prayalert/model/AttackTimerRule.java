package com.prayalert.model;

import lombok.Data;

@Data
public class AttackTimerRule
{
	private Integer npcId;
	private String npcName = "";
	private int animationId;
	private OverheadPrayer prayer = OverheadPrayer.PROTECT_FROM_MELEE;
	private int delay = 5;
	private boolean learnDelay;
	private boolean delayLearned;
}
