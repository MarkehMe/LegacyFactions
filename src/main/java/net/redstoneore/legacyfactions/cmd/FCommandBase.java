package net.redstoneore.legacyfactions.cmd;

import mkremins.fanciful.FancyMessage;
import net.redstoneore.legacyfactions.FactionsPluginBase;
import net.redstoneore.legacyfactions.config.Config;
import net.redstoneore.legacyfactions.entity.FPlayer;
import net.redstoneore.legacyfactions.entity.Faction;
import net.redstoneore.legacyfactions.integration.vault.VaultEngine;
import net.redstoneore.legacyfactions.lang.Lang;
import net.redstoneore.legacyfactions.util.LocationUtil;
import net.redstoneore.legacyfactions.util.PermUtil;
import net.redstoneore.legacyfactions.util.TextUtil;

import org.apache.commons.lang.time.DurationFormatUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;


public abstract class FCommandBase<T extends FactionsPluginBase> {

	// The sub-commands to this command
	private List<FCommandBase<?>> subCommands = Lists.newArrayList();

	public void addSubCommand(FCommandBase<?> subCommand) {
		subCommand.commandChain.addAll(this.commandChain);
		subCommand.commandChain.add(this);
		this.subCommands.add(subCommand);
	}
	
	public ArrayList<FCommandBase<?>> getSubcommands() {
		return Lists.newArrayList(this.subCommands);
	}
	
	public boolean removeSubcommand(FCommandBase<?> command) { 
		return this.subCommands.remove(command);
	}

	// The different names this commands will react to
	public List<String> aliases = Lists.newArrayList();
	
	public boolean allowNoSlashAccess = false;

	public boolean allowNoSlashAccess() {
		return this.allowNoSlashAccess;
	}
	
	public void allowNoSlashAccess(boolean allow) {
		this.allowNoSlashAccess = allow;
	}
	

	// Information on the args
	public List<String> requiredArgs = Lists.newArrayList();
	public LinkedHashMap<String, String> optionalArgs = new LinkedHashMap<String, String>();
	public boolean errorOnToManyArgs = true;

	// FIELD: Help Short
	// This field may be left blank and will in such case be loaded from the permissions node instead.
	// Thus make sure the permissions node description is an action description like "eat hamburgers" or "do admin stuff".
	private String helpShort = null;

	public void setHelpShort(String val) {
		this.helpShort = val;
	}

	public String getHelpShort() {
		return this.getHelpShort(true);
	}
	public String getHelpShort(Boolean colours) {
		if (this.helpShort == null) {
			return getUsageTranslation().toString();
		}

		return this.helpShort;
	}

	public abstract String getUsageTranslation();

	public List<String> helpLong = Lists.newArrayList();
	public CommandVisibility visibility = CommandVisibility.VISIBLE;

	// Some information on permissions
	public boolean senderMustBePlayer;
	public String permission = null;

	// Information available on execution of the command
	public CommandSender sender; // Will always be set
	public Player me; // Will only be set when the sender is a player
	public boolean senderIsConsole;
	public List<String> args; // Will contain the arguments, or and empty list if there are none.
	public List<FCommandBase<?>> commandChain = new ArrayList<FCommandBase<?>>(); // The command chain used to execute this command

	// The commandChain is a list of the parent command chain used to get to this command.
	public void execute(CommandSender sender, List<String> args, List<FCommandBase<?>> commandChain) {
		// Set the execution-time specific variables
		this.sender = sender;
		if (sender instanceof Player) {
			this.me = (Player) sender;
			this.senderIsConsole = false;
		} else {
			this.me = null;
			this.senderIsConsole = true;
		}
		this.args = args;
		this.commandChain = commandChain;

		// Is there a matching sub command?
		if (args.size() > 0) {
			for (FCommandBase<?> subCommand : this.subCommands) {
				if (subCommand.aliases.contains(args.get(0).toLowerCase())) {
					if (subCommand.isAvailable()) {
						args.remove(0);
						commandChain.add(this);
						subCommand.execute(sender, args, commandChain);
						
						return;
					}
				}
			}
		}

		if (!validCall(this.sender, this.args)) {
			return;
		}

		if (!this.isEnabled()) {
			return;
		}
		
		this.perform();
	}

	public void execute(CommandSender sender, List<String> args) {
		execute(sender, args, new ArrayList<>());
	}

	// This is where the command action is performed.
	public abstract void prePerform();
	public abstract void perform();
	
	/**
	 * Override this if you want to change the availability of this command, returning false will
	 * make it seem invisible:<br>
	 * - It won't show in help<br>
	 * - It will fail if you try to use it<br>
	 * ALWAYAS call this method or check against LocationUtil
	 * @return true if it is available
	 */
	public boolean isAvailable() {
		if (this.sender instanceof Player) {
			return LocationUtil.isFactionsDisableIn((Player)this.sender);
		}
		return true;
	}


	// -------------------------------------------- //
	// Call Validation
	// -------------------------------------------- //

	/**
	 * In this method we validate that all prerequisites to perform this command has been met.
	 */
	// TODO: There should be a boolean for silence
	public boolean validCall(CommandSender sender, List<String> args) {
		return validSenderType(sender, true) && validSenderPermissions(sender, true) && validArgs(args, sender);

	}

	public boolean isEnabled() {
		return true;
	}

	public boolean validSenderType(CommandSender sender, boolean informSenderIfNot) {
		if (this.senderMustBePlayer && !(sender instanceof Player)) {
			if (informSenderIfNot) {
				Lang.GENERIC_PLAYERONLY.getBuilder()
					.parse()
					.sendTo(sender);
			}
			return false;
		}
		return true;
	}

	public boolean validSenderPermissions(CommandSender sender, boolean informSenderIfNot) {
		return this.permission == null || PermUtil.get().has(sender, this.permission, informSenderIfNot);
	}

	public boolean validArgs(List<String> args, CommandSender sender) {
		if (args.size() < this.requiredArgs.size()) {
			if (sender != null) {
				Lang.GENERIC_ARGS_TOOFEW.getBuilder()
					.parse()
					.sendTo(sender);
				
				sender.sendMessage(this.getUseageTemplate());
			}
			return false;
		}

		if (args.size() > this.requiredArgs.size() + this.optionalArgs.size() && this.errorOnToManyArgs) {
			if (sender != null) {
				// Get the to many string slice
				List<String> theToMany = args.subList(this.requiredArgs.size() + this.optionalArgs.size(), args.size());
				this.sendMessage(Lang.GENERIC_ARGS_TOOMANY.getBuilder().parse().toString(), TextUtil.implode(theToMany, " "));
				this.sendMessage(this.getUseageTemplate());
			}
			return false;
		}
		return true;
	}

	public boolean validArgs(List<String> args) {
		return this.validArgs(args, null);
	}

	// -------------------------------------------- //
	// Help and Usage information
	// -------------------------------------------- //

	public String getUseageTemplate(List<FCommandBase<?>> commandChain, boolean addShortHelp) {
		return getUseageTemplate(commandChain, addShortHelp, true);
	}
	public String getUseageTemplate(List<FCommandBase<?>> commandChain, boolean addShortHelp, boolean colours) {
		StringBuilder ret = new StringBuilder();
		
		if (colours) ret.append(TextUtil.get().parseTags("<c>"));
		
		
		ret.append('/');

		for (FCommandBase<?> mc : commandChain) {
			ret.append(TextUtil.implode(mc.aliases, ","));
			ret.append(' ');
		}

		ret.append(TextUtil.implode(this.aliases, ","));

		List<String> args = new ArrayList<>();

		for (String requiredArg : this.requiredArgs) {
			args.add("<" + requiredArg + ">");
		}

		for (Entry<String, String> optionalArg : this.optionalArgs.entrySet()) {
			String val = optionalArg.getValue();
			if (val == null) {
				val = "";
			} else {
				val = "=" + val;
			}
			args.add("[" + optionalArg.getKey() + val + "]");
		}

		if (args.size() > 0) {
			if (colours) {
				ret.append(TextUtil.get().parseTags("<p> "));
			} else {
				ret.append(" ");
			}
			ret.append(TextUtil.implode(args, " "));
		}

		if (addShortHelp) {
			if (colours)  {
				ret.append(TextUtil.get().parseTags(" <i>"));
			} else {
				ret.append(" ");
			}
			ret.append(this.getHelpShort(false));
		}

		return ret.toString();
	}

	public String getUseageTemplate(boolean addShortHelp) {
		return getUseageTemplate(this.commandChain, addShortHelp);
	}
	public String getUseageTemplate(boolean addShortHelp, boolean colours) {
		return getUseageTemplate(this.commandChain, addShortHelp, colours);
	}
	

	public String getUseageTemplate() {
		return getUseageTemplate(false);
	}

	// -------------------------------------------- //
	// Message Sending Helpers
	// -------------------------------------------- //

	public void sendMessage(String str, Object... args) {
		this.sender.sendMessage(TextUtil.get().parse(str, args));
	}

	public void sendMessage(Lang translation, Object... args) {
		this.sender.sendMessage(TextUtil.get().parse(translation.toString(), args));
	}

	public void sendMessage(String msg) {
		this.sender.sendMessage(TextUtil.get().parse(msg));
	}

	public void sendMessage(List<String> messages) {
		messages.forEach(message -> this.sendMessage(message));
	}

	public void sendMessage(FancyMessage message) {
		message.send(this.sender);
	}
	
	public void sendFancyMessage(FancyMessage message) {
		message.send(this.sender);
	}

	public void sendFancyMessage(List<FancyMessage> messages) {
		messages.forEach(message -> this.sendFancyMessage(message));
	}
	
	public void sendMessage(Lang lang) {
		this.sendMessage(lang.toString());
	}
	
	public List<String> getToolTips(FPlayer player) {
		List<String> lines = new ArrayList<>();
		
		Config.tooltips.get("show").forEach(tooltip -> {
			lines.add(ChatColor.translateAlternateColorCodes('&', replaceFPlayerTags(tooltip, player)));
		});
		
		return lines;
	}

	public List<String> getToolTips(Faction faction) {
		List<String> lines = new ArrayList<>();
		
		Config.tooltips.get("list").forEach(tooltip -> {
			lines.add(ChatColor.translateAlternateColorCodes('&', replaceFactionTags(tooltip, faction)));
		});
		
		return lines;
	}

	public String replaceFPlayerTags(String s, FPlayer player) {
		if (s.contains("{balance}")) {
			String balance = VaultEngine.isSetup() ? VaultEngine.getUtils().getFriendlyBalance(player) : "no balance";
			s = s.replace("{balance}", balance);
		}
		if (s.contains("{lastSeen}")) {
			String humanized = DurationFormatUtils.formatDurationWords(System.currentTimeMillis() - player.getLastLoginTime(), true, true) + " ago";
			String lastSeen = player.isOnline() ? ChatColor.GREEN + "Online" : (System.currentTimeMillis() - player.getLastLoginTime() < 432000000 ? ChatColor.YELLOW + humanized : ChatColor.RED + humanized);
			s = s.replace("{lastSeen}", lastSeen);
		}
		if (s.contains("{power}")) {
			String power = player.getPowerRounded() + "/" + player.getPowerMaxRounded();
			s = s.replace("{power}", power);
		}
		if (s.contains("{group}")) {
			String group = VaultEngine.getUtils().getPrimaryGroup(Bukkit.getOfflinePlayer(UUID.fromString(player.getId())));
			s = s.replace("{group}", group);
		}
		return s;
	}

	public String replaceFactionTags(String s, Faction faction) {
		if (s.contains("{power}")) {
			s = s.replace("{power}", String.valueOf(faction.getPowerRounded()));
		}
		if (s.contains("{maxPower}")) {
			s = s.replace("{maxPower}", String.valueOf(faction.getPowerMaxRounded()));
		}
		if (s.contains("{leader}")) {
			FPlayer fLeader = faction.getOwner();
			String leader = fLeader == null ? "Server" : fLeader.getName().substring(0, fLeader.getName().length() > 14 ? 13 : fLeader.getName().length());
			s = s.replace("{leader}", leader);
		}
		if (s.contains("{chunks}")) {
			s = s.replace("{chunks}", String.valueOf(faction.getLandRounded()));
		}
		if (s.contains("{members}")) {
			s = s.replace("{members}", String.valueOf(faction.memberCount()));

		}
		if (s.contains("{online}")) {
			s = s.replace("{online}", String.valueOf(faction.getOnlinePlayers().size()));
		}
		return s;
	}

	// -------------------------------------------- //
	// Argument Readers
	// -------------------------------------------- //

	// Is set? ======================
	public boolean argIsSet(int idx) {
		return this.args.size() >= idx + 1;
	}

	// STRING ======================
	public String argAsString(int idx, String def) {
		if (this.args.size() < idx + 1) {
			return def;
		}
		return this.args.get(idx);
	}

	public String argAsString(int idx) {
		return this.argAsString(idx, null);
	}

	// INT ======================
	public Integer strAsInt(String str, Integer def) {
		if (str == null) {
			return def;
		}
		try {
			return Integer.parseInt(str);
		} catch (Exception e) {
			return def;
		}
	}

	public Integer argAsInt(int idx, Integer def) {
		return strAsInt(this.argAsString(idx), def);
	}

	public Integer argAsInt(int idx) {
		return this.argAsInt(idx, null);
	}

	// Double ======================
	public Double strAsDouble(String str, Double def) {
		if (str == null) {
			return def;
		}
		try {
			return Double.parseDouble(str);
		} catch (Exception e) {
			return def;
		}
	}

	public Double argAsDouble(int idx, Double def) {
		return strAsDouble(this.argAsString(idx), def);
	}

	public Double argAsDouble(int idx) {
		return this.argAsDouble(idx, null);
	}

	// TODO: Go through the str conversion for the other arg-readers as well.
	// Boolean ======================
	public Boolean strAsBool(String str) {
		str = str.toLowerCase();
		return str.startsWith("y") || str.startsWith("t") || str.startsWith("on") || str.startsWith("+") || str.startsWith("1");
	}

	public Boolean argAsBool(int idx, boolean def) {
		String str = this.argAsString(idx);
		if (str == null) {
			return def;
		}

		return strAsBool(str);
	}

	public Boolean argAsBool(int idx) {
		return this.argAsBool(idx, false);
	}

	// World ======================
	public World strAsWorld(String name, World defaultValue, boolean msg) {
		World result = defaultValue;

		if (name != null) {
			World world = Bukkit.getWorld(name);
			if (world != null) {
				result = world;
			}
		}

		if (msg && result == null) {
			Lang.GENERIC_NOWORLDFOUND.getBuilder()
				.parse()
				.replace("<world>", name)
				.sendTo(this.sender);
		}
		
		return result;
	}

	public World argAsWorld(int idx, World def, boolean msg) {
		return this.strAsWorld(this.argAsString(idx), def, msg);
	}

	public World argAsWorld(int idx, World def) {
		return this.argAsWorld(idx, def, true);
	}

	public World argAsWorld(int idx) {
		return this.argAsWorld(idx, null);
	}
	// PLAYER ======================
	public Player strAsPlayer(String name, Player def, boolean msg) {
		Player ret = def;

		if (name != null) {
			Player player = Bukkit.getServer().getPlayer(name);
			if (player != null) {
				ret = player;
			}
		}

		if (msg && ret == null) {
			this.sendMessage(Lang.GENERIC_NOPLAYERFOUND, name);
		}

		return ret;
	}

	public Player argAsPlayer(int idx, Player def, boolean msg) {
		return this.strAsPlayer(this.argAsString(idx), def, msg);
	}

	public Player argAsPlayer(int idx, Player def) {
		return this.argAsPlayer(idx, def, true);
	}

	public Player argAsPlayer(int idx) {
		return this.argAsPlayer(idx, null);
	}

	// BEST PLAYER MATCH ======================
	public Player strAsBestPlayerMatch(String name, Player def, boolean msg) {
		Player ret = def;

		if (name != null) {
			List<Player> players = Bukkit.getServer().matchPlayer(name);
			if (players.size() > 0) {
				ret = players.get(0);
			}
		}

		if (msg && ret == null) {
			this.sendMessage(Lang.GENERIC_NOPLAYERMATCH, name);
		}

		return ret;
	}

	public Player argAsBestPlayerMatch(int idx, Player def, boolean msg) {
		return this.strAsBestPlayerMatch(this.argAsString(idx), def, msg);
	}

	public Player argAsBestPlayerMatch(int idx, Player def) {
		return this.argAsBestPlayerMatch(idx, def, true);
	}

	public Player argAsBestPlayerMatch(int idx) {
		return this.argAsPlayer(idx, null);
	}

}
