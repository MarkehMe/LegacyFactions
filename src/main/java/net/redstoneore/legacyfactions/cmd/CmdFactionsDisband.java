package net.redstoneore.legacyfactions.cmd;

import net.redstoneore.legacyfactions.Factions;
import net.redstoneore.legacyfactions.Permission;
import net.redstoneore.legacyfactions.Role;
import net.redstoneore.legacyfactions.config.CommandAliases;
import net.redstoneore.legacyfactions.config.Config;
import net.redstoneore.legacyfactions.entity.FPlayer;
import net.redstoneore.legacyfactions.entity.FPlayerColl;
import net.redstoneore.legacyfactions.entity.Faction;
import net.redstoneore.legacyfactions.entity.FactionColl;
import net.redstoneore.legacyfactions.entity.VaultAccount;
import net.redstoneore.legacyfactions.event.EventFactionsChange;
import net.redstoneore.legacyfactions.event.EventFactionsChange.ChangeReason;
import net.redstoneore.legacyfactions.flag.Flags;
import net.redstoneore.legacyfactions.event.EventFactionsDisband;
import net.redstoneore.legacyfactions.integration.vault.VaultEngine;
import net.redstoneore.legacyfactions.lang.Lang;
import net.redstoneore.legacyfactions.scoreboards.FTeamWrapper;
import org.bukkit.Bukkit;

public class CmdFactionsDisband extends FCommand {

	// -------------------------------------------------- //
	// INSTANCE
	// -------------------------------------------------- //
	
	private static CmdFactionsDisband instance = new CmdFactionsDisband();
	public static CmdFactionsDisband get() { return instance; }
	
	// -------------------------------------------------- //
	// CONSTRUCT
	// -------------------------------------------------- //

	private CmdFactionsDisband() {
		this.aliases.addAll(CommandAliases.cmdAliasesDisband);
		
		this.optionalArgs.put("faction tag", "yours");
		
		this.permission = Permission.DISBAND.getNode();
		this.disableOnLock = true;

		this.senderMustBePlayer = false;
		this.senderMustBeMember = false;
		this.senderMustBeModerator = false;
		this.senderMustBeColeader = false;
		this.senderMustBeAdmin = false;
	}

	// -------------------------------------------------- //
	// METHODS
	// -------------------------------------------------- //

	@Override
	public void perform() {
		// The faction, default to your own.. but null if console sender.
		Faction faction = this.argAsFaction(0, fme == null ? null : myFaction);
		if (faction == null) return;

		boolean isMyFaction = fme == null ? false : faction == myFaction;

		if (isMyFaction) {
			if (!assertMinRole(Role.ADMIN)) {
				return;
			}
		} else {
			if (!Permission.DISBAND_ANY.has(sender, true)) {
				return;
			}
		}

		if (!faction.isNormal()) {
			sendMessage(Lang.COMMAND_DISBAND_IMMUTABLE.toString());
			return;
		}
		
		if (faction.getFlag(Flags.PERMANENT)) {
			sendMessage(Lang.COMMAND_DISBAND_MARKEDPERMANENT.toString());
			return;
		}

		
		EventFactionsDisband disbandEvent = new EventFactionsDisband(me, faction.getId(), true, EventFactionsDisband.DisbandReason.DISBAND_COMMAND);
		disbandEvent.call();
		if (disbandEvent.isCancelled()) return;

		// Send event for each player in the faction
		faction.getMembers().forEach(fplayer -> {
			EventFactionsChange changeEvent = new EventFactionsChange(fplayer, faction, FactionColl.get().getWilderness(), false, ChangeReason.DISBAND);
			Bukkit.getServer().getPluginManager().callEvent(changeEvent);
		});

		// Inform all players
		for (FPlayer fplayer : FPlayerColl.all(true)) {
			String who = senderIsConsole ? Lang.GENERIC_SERVERADMIN.toString() : fme.describeTo(fplayer);
			if (fplayer.getFaction() == faction) {
				if (fplayer == fme) {
					fplayer.sendMessage(Lang.COMMAND_DISBAND_BROADCAST_YOURSYOU);
				} else {
					fplayer.sendMessage(Lang.COMMAND_DISBAND_BROADCAST_YOURS, who);
				}
			} else {
				fplayer.sendMessage(Lang.COMMAND_DISBAND_BROADCAST_NOTYOURS, who, faction.getTag(fplayer));
			}
		}
		if (Config.logFactionDisband) {
			//TODO: Format this correctly and translate.
			Factions.get().log("The faction " + faction.getTag() + " (" + faction.getId() + ") was disbanded by " + (senderIsConsole ? "console command" : fme.getName()) + ".");
		}

		if (VaultEngine.getUtils().shouldBeUsed() && !this.senderIsConsole) {
			//Give all the faction's money to the disbander
			double amount = VaultEngine.getUtils().getBalance(faction.getAccountId());
			VaultAccount.get(faction).transfer(VaultAccount.get(fme), amount, VaultAccount.get(fme));
			
			if (amount > 0.0) {
				String amountString = VaultEngine.getUtils().moneyString(amount);
				sendMessage(Lang.COMMAND_DISBAND_HOLDINGS, amountString);
				//TODO: Format this correctly and translate
				Factions.get().log(fme.getName() + " has been given bank holdings of " + amountString + " from disbanding " + faction.getTag() + ".");
			}
		}

		FactionColl.get().removeFaction(faction.getId());
		FTeamWrapper.applyUpdates(faction); // TODO: should this be put into removeFaction ?
	}

	@Override
	public String getUsageTranslation() {
		return Lang.COMMAND_DISBAND_DESCRIPTION.toString();
	}
	
}
