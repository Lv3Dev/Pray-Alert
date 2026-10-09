package com.prayalert.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
public class Folder
{
	private String id = UUID.randomUUID().toString();
	private String title = "";
	private boolean enabled = true;
	private boolean collapsed;
	private List<Entry> entries = new ArrayList<>();
}
