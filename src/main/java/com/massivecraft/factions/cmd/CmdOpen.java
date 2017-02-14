package com.massivecraft.factions.cmd;

import com.massivecraft.factions.Permission;
import com.massivecraft.factions.TL;
import com.massivecraft.factions.entity.Conf;
import com.massivecraft.factions.entity.FPlayer;
import com.massivecraft.factions.entity.FPlayerColl;

public class CmdOpen extends FCommand {

    public CmdOpen() {
        super();
        this.aliases.add("open");

        //this.requiredArgs.add("");
        this.optionalArgs.put("yes/no", "flip");

        this.permission = Permission.OPEN.node;
        this.disableOnLock = false;

        senderMustBePlayer = true;
        senderMustBeMember = false;
        senderMustBeModerator = true;
        senderMustBeAdmin = false;
    }

    @Override
    public void perform() {
        // if economy is enabled, they're not on the bypass list, and this command has a cost set, make 'em pay
        if (!payForCommand(Conf.econCostOpen, TL.COMMAND_OPEN_TOOPEN, TL.COMMAND_OPEN_FOROPEN)) {
            return;
        }

        myFaction.setOpen(this.argAsBool(0, !myFaction.getOpen()));

        String open = myFaction.getOpen() ? TL.COMMAND_OPEN_OPEN.toString() : TL.COMMAND_OPEN_CLOSED.toString();

        // Inform
        for (FPlayer fplayer : FPlayerColl.getInstance().getOnlinePlayers()) {
            if (fplayer.getFactionId().equals(myFaction.getId())) {
                fplayer.msg(TL.COMMAND_OPEN_CHANGES, fme.getName(), open);
                continue;
            }
            fplayer.msg(TL.COMMAND_OPEN_CHANGED, myFaction.getTag(fplayer.getFaction()), open);
        }
    }

    @Override
    public TL getUsageTranslation() {
        return TL.COMMAND_OPEN_DESCRIPTION;
    }

}
