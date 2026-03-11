package com.fairyringqol;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class FairyRingQOlPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(FairyRingQolPlugin.class);
		RuneLite.main(args);
	}
}