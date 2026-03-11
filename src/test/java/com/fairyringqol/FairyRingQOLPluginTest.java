package com.fairyringqol;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class FairyRingQOLPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(FairyRingQOLPlugin.class);
		RuneLite.main(args);
	}
}