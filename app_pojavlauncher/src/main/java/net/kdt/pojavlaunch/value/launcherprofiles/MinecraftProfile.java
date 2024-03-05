package net.kdt.pojavlaunch.value.launcherprofiles;

import androidx.annotation.Keep;

@Keep
public class MinecraftProfile {

	public String name;
	public String type;
	public String created;
	public String lastUsed;
	public String icon;
	public int language;
	public boolean followGameLanguage;
	public String lastVersionId;
	public String gameDir;
	public String javaDir;
	public String javaArgs;
	public String logConfig;
	public boolean logConfigIsXML;
	public String pojavRendererName;
	public String controlFile;
	public MinecraftResolution[] resolution;


	public static MinecraftProfile createTemplate(){
		MinecraftProfile TEMPLATE = new MinecraftProfile();
		TEMPLATE.name = "New";
		TEMPLATE.language = 0;
		TEMPLATE.followGameLanguage = true;
		TEMPLATE.lastVersionId = "latest-release";
		return TEMPLATE;
	}

	public static MinecraftProfile getDefaultProfile(){
		MinecraftProfile defaultProfile = new MinecraftProfile();
		defaultProfile.name = "Default";
		defaultProfile.language = 0;
		defaultProfile.followGameLanguage = true;
		defaultProfile.lastVersionId = "1.7.10";
		return defaultProfile;
	}

	public MinecraftProfile(){}

	public MinecraftProfile(MinecraftProfile profile){
		name = profile.name;
		type = profile.type;
		created = profile.created;
		lastUsed = profile.lastUsed;
		icon = profile.icon;
		language = profile.language;
		followGameLanguage = profile.followGameLanguage;
		lastVersionId = profile.lastVersionId;
		gameDir = profile.gameDir;
		javaDir = profile.javaDir;
		javaArgs = profile.javaArgs;
		logConfig = profile.logConfig;
		logConfigIsXML = profile.logConfigIsXML;
		pojavRendererName = profile.pojavRendererName;
		controlFile = profile.controlFile;
		resolution = profile.resolution;
	}
}
