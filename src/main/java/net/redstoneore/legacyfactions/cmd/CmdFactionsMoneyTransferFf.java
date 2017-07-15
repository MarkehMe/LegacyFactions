package net.redstoneore.legacyfactions.cmd;

import org.bukkit.ChatColor;

import net.redstoneore.legacyfactions.EconomyParticipator;
import net.redstoneore.legacyfactions.Factions;
import net.redstoneore.legacyfactions.Permission;
import net.redstoneore.legacyfactions.Lang;
import net.redstoneore.legacyfactions.entity.Conf;
import net.redstoneore.legacyfactions.integration.vault.VaultEngine;


public class CmdFactionsMoneyTransferFf extends FCommand {

	// -------------------------------------------------- //
	// CONSTRUCT
	// -------------------------------------------------- //

	public CmdFactionsMoneyTransferFf() {
		this.aliases.addAll(Conf.cmdAliasesMoneyTransferFf);

		this.requiredArgs.add("amount");
		this.requiredArgs.add("faction");
		this.requiredArgs.add("faction");

		this.permission = Permission.MONEY_F2F.node;

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
		double amount = this.argAsDouble(0, 0d);
		EconomyParticipator from = this.argAsFaction(1);
		if (from == null) return;
		
		EconomyParticipator to = this.argAsFaction(2);
		if (to == null) return;

		boolean success = VaultEngine.transferMoney(fme, from, to, amount);

		if (success && Conf.logMoneyTransactions) {
			Factions.get().log(ChatColor.stripColor(Factions.get().getTextUtil().parse(Lang.COMMAND_MONEYTRANSFERFF_TRANSFER.toString(), fme.getName(), VaultEngine.moneyString(amount), from.describeTo(null), to.describeTo(null))));
		}
	}

	@Override
	public String getUsageTranslation() {
		return Lang.COMMAND_MONEYTRANSFERFF_DESCRIPTION.toString();
	}
}
