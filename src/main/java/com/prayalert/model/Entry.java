package com.prayalert.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
public class Entry
{
	private String id = UUID.randomUUID().toString();
	private String title = "";
	private boolean enabled = true;
	private boolean collapsed;
	private List<AnimationRule> animationRules = new ArrayList<>();
	private List<ProjectileRule> projectileRules = new ArrayList<>();
	private List<AttackTimerRule> attackTimers = new ArrayList<>();
	private boolean curseEnabled;
}
