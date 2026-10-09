package com.prayalert.model;

import lombok.Data;

@Data
public class ProjectileRule
{
	private int projectileId;
	private OverheadPrayer prayer = OverheadPrayer.PROTECT_FROM_MAGIC;
}
