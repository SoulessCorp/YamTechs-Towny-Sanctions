package yt.yamtechs.sanctions;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public final class AllyTeamCommand implements TabExecutor {
    private static final String PREFIX = ChatColor.DARK_GRAY + "[" + ChatColor.AQUA + "AllyTeam"
            + ChatColor.DARK_GRAY + "] " + ChatColor.GRAY;
    private static final Pattern DURATION = Pattern.compile("(?i)^(permanent|perm|[1-9][0-9]*[smhd])$");

    private final SanctionsPlugin plugin;
    private final AllyTeamManager teams;

    public AllyTeamCommand(SanctionsPlugin plugin, AllyTeamManager teams) {
        this.plugin = plugin;
        this.teams = teams;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> create(sender, args);
            case "invite" -> invite(sender, args);
            case "accept" -> accept(sender, args);
            case "leave" -> leave(sender);
            case "leader" -> transferLeadership(sender, args);
            case "disband" -> disband(sender);
            case "info" -> info(sender, args);
            case "list" -> list(sender);
            case "proposals", "votes" -> proposals(sender);
            case "vote" -> vote(sender, args);
            default -> help(sender);
        }
        return true;
    }

    private void create(CommandSender sender, String[] args) {
        Nation nation = kingNation(sender);
        if (nation == null) {
            sender.sendMessage(PREFIX + "Only a Towny nation king can create an AllyTeam.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(PREFIX + "Usage: /allyteam create <name>");
            return;
        }
        String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        AllyTeam team = teams.create(name, nation);
        if (team == null) {
            sender.sendMessage(PREFIX + "That team name is already used, or your nation is already in an AllyTeam.");
            return;
        }
        sender.sendMessage(PREFIX + "Created " + ChatColor.AQUA + team.getName()
                + ChatColor.GRAY + ". Your nation is its first member and leader.");
    }

    private void invite(CommandSender sender, String[] args) {
        Nation issuer = kingNation(sender);
        AllyTeam team = ownTeam(issuer);
        if (team == null || !team.isLeader(issuer.getUUID())) {
            sender.sendMessage(PREFIX + "Only the king of your AllyTeam's leader nation can invite nations.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(PREFIX + "Usage: /allyteam invite <nation>");
            return;
        }
        String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        Nation target = TownyAPI.getInstance().getNation(name);
        if (target == null) {
            sender.sendMessage(PREFIX + "Towny nation not found: " + ChatColor.YELLOW + name);
            return;
        }
        if (!teams.invite(team, target)) {
            sender.sendMessage(PREFIX + "That nation is already in an AllyTeam or this team.");
            return;
        }
        sender.sendMessage(PREFIX + "Invited " + target.getName() + " to " + team.getName() + ".");
        notifyNation(target, PREFIX + "Your nation was invited to " + ChatColor.AQUA + team.getName()
                + ChatColor.GRAY + ". Its king can run /allyteam accept " + team.getName() + ".");
    }

    private void accept(CommandSender sender, String[] args) {
        Nation nation = kingNation(sender);
        if (nation == null) {
            sender.sendMessage(PREFIX + "Only a Towny nation king can accept an invitation.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(PREFIX + "Usage: /allyteam accept <team name>");
            return;
        }
        String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        AllyTeam team = teams.findTeamByName(name);
        if (team == null || !team.getInvitations().containsKey(nation.getUUID())) {
            sender.sendMessage(PREFIX + "Your nation has no invitation to that team.");
            return;
        }
        if (!teams.accept(team, nation)) {
            sender.sendMessage(PREFIX + "Your nation is already in an AllyTeam.");
            return;
        }
        sender.sendMessage(PREFIX + "Your nation joined " + ChatColor.AQUA + team.getName() + ChatColor.GRAY + ".");
    }

    private void leave(CommandSender sender) {
        Nation nation = kingNation(sender);
        AllyTeam team = ownTeam(nation);
        if (team == null) {
            sender.sendMessage(PREFIX + "Your nation is not in an AllyTeam.");
            return;
        }
        if (team.isLeader(nation.getUUID())) {
            sender.sendMessage(PREFIX + "The leader nation cannot leave. Transfer leadership or disband the team.");
            return;
        }
        teams.leave(team, nation);
        sender.sendMessage(PREFIX + "Your nation left " + team.getName() + ".");
    }

    private void transferLeadership(CommandSender sender, String[] args) {
        Nation issuer = kingNation(sender);
        AllyTeam team = ownTeam(issuer);
        if (team == null || !team.isLeader(issuer.getUUID())) {
            sender.sendMessage(PREFIX + "Only the current leader nation's king can transfer leadership.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(PREFIX + "Usage: /allyteam leader <member nation>");
            return;
        }
        Nation target = TownyAPI.getInstance().getNation(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        if (target == null || !team.containsNation(target.getUUID())) {
            sender.sendMessage(PREFIX + "Choose a nation that already belongs to this AllyTeam.");
            return;
        }
        teams.transferLeadership(team, target);
        sender.sendMessage(PREFIX + target.getName() + " is now the AllyTeam leader.");
    }

    private void disband(CommandSender sender) {
        Nation nation = kingNation(sender);
        AllyTeam team = ownTeam(nation);
        if (team == null || !team.isLeader(nation.getUUID())) {
            sender.sendMessage(PREFIX + "Only the king of the leader nation can disband this AllyTeam.");
            return;
        }
        int removed = teams.disband(team);
        sender.sendMessage(PREFIX + "Disbanded " + team.getName() + "; removed " + removed
                + " sanction(s) against that team.");
    }

    private void info(CommandSender sender, String[] args) {
        AllyTeam team;
        if (args.length == 1) {
            Nation nation = currentNation(sender);
            team = nation == null ? null : teams.findTeamForNation(nation.getUUID());
        } else {
            team = teams.findTeamByName(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        }
        if (team == null) {
            sender.sendMessage(PREFIX + "AllyTeam not found. Use /allyteam list.");
            return;
        }
        sender.sendMessage(ChatColor.DARK_GRAY + "--- " + ChatColor.AQUA + team.getName()
                + ChatColor.GRAY + " ---");
        sender.sendMessage(PREFIX + "Leader: " + team.getLeaderNationName());
        sender.sendMessage(PREFIX + "Members: " + String.join(", ", team.getMembers().values()));
        if (!team.getInvitations().isEmpty()) {
            sender.sendMessage(PREFIX + "Invited: " + String.join(", ", team.getInvitations().values()));
        }
        List<SanctionVote> pending = teams.proposalsFor(team);
        sender.sendMessage(PREFIX + "Open sanction votes: " + pending.size());
        pending.forEach(proposal -> sender.sendMessage(ChatColor.GRAY + "#" + proposal.getId().toString().substring(0, 8)
                + " " + voteAction(proposal) + " " + proposal.getTargetNationName() + " — " + proposal.yesCount() + "/"
                + proposal.electorateSize() + " yes, " + proposal.noCount() + " no"));
    }

    private void list(CommandSender sender) {
        List<AllyTeam> all = teams.allTeams();
        if (all.isEmpty()) {
            sender.sendMessage(PREFIX + "No AllyTeams have been created yet.");
            return;
        }
        sender.sendMessage(PREFIX + "AllyTeams:");
        for (AllyTeam team : all) {
            sender.sendMessage(ChatColor.AQUA + team.getName() + ChatColor.GRAY + " — leader: "
                    + team.getLeaderNationName() + ", member nations: " + team.getMembers().size());
        }
    }

    private void proposals(CommandSender sender) {
        Nation nation = currentNation(sender);
        AllyTeam team = nation == null ? null : teams.findTeamForNation(nation.getUUID());
        if (team == null) {
            sender.sendMessage(PREFIX + "Your nation is not in an AllyTeam.");
            return;
        }
        List<SanctionVote> pending = teams.proposalsFor(team);
        if (pending.isEmpty()) {
            sender.sendMessage(PREFIX + "No open sanction votes.");
            return;
        }
        sender.sendMessage(PREFIX + "Open votes for " + team.getName() + ":");
        for (SanctionVote proposal : pending) {
            sender.sendMessage(ChatColor.GRAY + "#" + proposal.getId().toString().substring(0, 8)
                    + " " + voteAction(proposal) + " " + proposal.getTargetNationName() + " — " + proposal.yesCount() + "/"
                    + proposal.electorateSize() + " yes, " + proposal.noCount() + " no. Vote with /allyteam vote "
                    + proposal.getId().toString().substring(0, 8) + " yes|no");
        }
    }

    private void vote(CommandSender sender, String[] args) {
        Nation nation = kingNation(sender);
        AllyTeam team = ownTeam(nation);
        if (team == null) {
            sender.sendMessage(PREFIX + "Only a nation king in an AllyTeam can vote.");
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("sanction")) {
            proposeSanction(sender, nation, team, args);
            return;
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("lift")) {
            proposeLift(sender, nation, team, args);
            return;
        }
        if (args.length != 3 || !(args[2].equalsIgnoreCase("yes") || args[2].equalsIgnoreCase("no"))) {
            sender.sendMessage(PREFIX + "Usage: /allyteam vote <proposal-id> <yes|no>");
            return;
        }
        SanctionVote proposal = teams.findProposal(args[1]);
        if (proposal == null || !proposal.getTeamId().equals(team.getId())) {
            sender.sendMessage(PREFIX + "Open proposal not found. Use /allyteam proposals.");
            return;
        }
        AllyTeamManager.VoteResult result = teams.castVote(proposal, nation, args[2].equalsIgnoreCase("yes"));
        switch (result) {
            case RECORDED -> sender.sendMessage(PREFIX + "Vote recorded. Current tally: " + proposal.yesCount()
                    + " yes, " + proposal.noCount() + " no; more votes may be needed.");
            case PASSED -> sender.sendMessage(PREFIX + "Your vote passed the sanction.");
            case FAILED -> sender.sendMessage(PREFIX + "Your vote closed the proposal without a majority.");
            case NOT_ELIGIBLE -> sender.sendMessage(PREFIX + "Your nation was not a member when the vote opened.");
            case ALREADY_VOTED -> sender.sendMessage(PREFIX + "Your nation has already voted on this proposal.");
            default -> sender.sendMessage(PREFIX + "Vote remains open.");
        }
    }

    private void proposeSanction(CommandSender sender, Nation proposer, AllyTeam team, String[] args) {
        int forIndex = -1;
        for (int i = 2; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("--for")) { forIndex = i; break; }
        }
        if (forIndex < 3 || forIndex + 1 >= args.length || !DURATION.matcher(args[forIndex + 1]).matches()) {
            sender.sendMessage(PREFIX + "Usage: /allyteam vote sanction <nation> --for <duration> [reason]");
            return;
        }
        Nation target = TownyAPI.getInstance().getNation(String.join(" ", Arrays.copyOfRange(args, 2, forIndex)));
        if (target == null) {
            sender.sendMessage(PREFIX + "Towny nation not found.");
            return;
        }
        if (team.containsNation(target.getUUID())) {
            sender.sendMessage(PREFIX + "An AllyTeam cannot sanction one of its own member nations.");
            return;
        }
        Long duration = parseDuration(args[forIndex + 1]);
        if (duration == null) {
            sender.sendMessage(PREFIX + "Invalid or excessively large duration.");
            return;
        }
        int reasonIndex = forIndex + 2;
        if (reasonIndex < args.length && args[reasonIndex].equalsIgnoreCase("--reason")) reasonIndex++;
        String reason = reasonIndex < args.length
                ? String.join(" ", Arrays.copyOfRange(args, reasonIndex, args.length)) : "No reason given.";
        SanctionVote proposal = teams.proposeSanction(team, proposer, target, duration, reason);
        if (proposal == null) {
            sender.sendMessage(PREFIX + "A vote for that nation is already open, or it cannot be sanctioned.");
            return;
        }
        if (teams.proposalsFor(team).stream().anyMatch(open -> open.getId().equals(proposal.getId()))) {
            sender.sendMessage(PREFIX + "Vote #" + proposal.getId().toString().substring(0, 8)
                    + " opened. Your nation voted yes. A majority of all member nations must vote yes; ties fail.");
        } else {
            sender.sendMessage(PREFIX + "Your team has one member; its yes vote passed the sanction immediately.");
        }
    }

    private void proposeLift(CommandSender sender, Nation proposer, AllyTeam team, String[] args) {
        Nation target = TownyAPI.getInstance().getNation(String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
        if (target == null) {
            sender.sendMessage(PREFIX + "Towny nation not found.");
            return;
        }
        SanctionVote proposal = teams.proposeLift(team, proposer, target);
        if (proposal == null) {
            sender.sendMessage(PREFIX + "Your team has no active sanction against that nation, or a vote is already open.");
            return;
        }
        if (teams.proposalsFor(team).stream().anyMatch(open -> open.getId().equals(proposal.getId()))) {
            sender.sendMessage(PREFIX + "Lift vote #" + proposal.getId().toString().substring(0, 8)
                    + " opened. Your nation voted yes; a majority of all member nations is required.");
        } else {
            sender.sendMessage(PREFIX + "Your one-member team approved the lift immediately.");
        }
    }

    private String voteAction(SanctionVote proposal) {
        return proposal.isLifting() ? "lift" : "sanction";
    }

    private Long parseDuration(String raw) {
        String value = raw.toLowerCase(Locale.ROOT);
        if (value.equals("perm") || value.equals("permanent")) return -1L;
        char unit = value.charAt(value.length() - 1);
        long multiplier = switch (unit) {
            case 's' -> 1_000L;
            case 'm' -> 60_000L;
            case 'h' -> 3_600_000L;
            case 'd' -> 86_400_000L;
            default -> 0L;
        };
        try {
            return Math.multiplyExact(Long.parseLong(value.substring(0, value.length() - 1)), multiplier);
        } catch (NumberFormatException | ArithmeticException exception) {
            return null;
        }
    }

    private Nation kingNation(CommandSender sender) {
        if (!(sender instanceof Player player)) return null;
        Nation nation = TownyAPI.getInstance().getNation(player);
        Resident resident = TownyAPI.getInstance().getResident(player);
        return nation != null && resident != null && nation.isKing(resident) ? nation : null;
    }

    private Nation currentNation(CommandSender sender) {
        return sender instanceof Player player ? TownyAPI.getInstance().getNation(player) : null;
    }

    private AllyTeam ownTeam(Nation nation) {
        return nation == null ? null : teams.findTeamForNation(nation.getUUID());
    }

    private void notifyNation(Nation nation, String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Nation onlineNation = TownyAPI.getInstance().getNation(player);
            if (onlineNation != null && onlineNation.getUUID().equals(nation.getUUID())) {
                player.sendMessage(message);
            }
        }
    }

    private void help(CommandSender sender) {
        sender.sendMessage(ChatColor.DARK_GRAY + "--- " + ChatColor.AQUA + "AllyTeam" + ChatColor.DARK_GRAY + " ---");
        sender.sendMessage(ChatColor.YELLOW + "/allyteam create <name>" + ChatColor.GRAY + " - form a team for your nation");
        sender.sendMessage(ChatColor.YELLOW + "/allyteam invite <nation>" + ChatColor.GRAY + " - invite a nation to your team");
        sender.sendMessage(ChatColor.YELLOW + "/allyteam accept <team name>" + ChatColor.GRAY + " - accept an invitation");
        sender.sendMessage(ChatColor.YELLOW + "/allyteam vote sanction <nation> --for <duration> [reason]" + ChatColor.GRAY + " - propose an embargo");
        sender.sendMessage(ChatColor.YELLOW + "/allyteam vote lift <nation>" + ChatColor.GRAY + " - vote to lift a team embargo");
        sender.sendMessage(ChatColor.YELLOW + "/allyteam vote <proposal-id> <yes|no>" + ChatColor.GRAY + " - cast your nation's vote");
        sender.sendMessage(ChatColor.YELLOW + "/allyteam list | info [team] | proposals | leave | leader <nation> | disband");
        sender.sendMessage(ChatColor.GRAY + "A majority of all member nations is required; a tie fails. Votes stay open for 24 hours.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return matching(List.of("create", "invite", "accept", "leave", "leader", "disband", "info", "list", "proposals", "vote", "help"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("accept")) {
            return matching(teams.allTeams().stream().map(AllyTeam::getName).toList(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("vote")) {
            return matching(List.of("sanction", "lift"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("vote") && args[1].equalsIgnoreCase("sanction")) {
            return matching(TownyAPI.getInstance().getNations().stream().map(Nation::getName).toList(), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("vote") && args[1].equalsIgnoreCase("lift")) {
            Nation nation = currentNation(sender);
            AllyTeam team = nation == null ? null : teams.findTeamForNation(nation.getUUID());
            return team == null ? List.of() : matching(team.getMembers().values().stream().toList(), args[2]);
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("vote") && !args[1].equalsIgnoreCase("sanction")) {
            if (args.length == 3) return matching(List.of("yes", "no"), args[2]);
        }
        if (args.length >= 4 && args[0].equalsIgnoreCase("vote") && args[1].equalsIgnoreCase("sanction")
                && args[args.length - 1].equalsIgnoreCase("--for")) {
            return List.of("30m", "12h", "7d", "permanent");
        }
        return List.of();
    }

    private List<String> matching(List<String> options, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();
    }
}
