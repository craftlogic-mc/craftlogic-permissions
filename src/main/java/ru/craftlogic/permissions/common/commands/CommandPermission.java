package ru.craftlogic.permissions.common.commands;

import net.minecraft.command.CommandException;
import net.minecraft.command.WrongUsageException;
import ru.craftlogic.api.command.CommandBase;
import ru.craftlogic.api.command.CommandContext;
import ru.craftlogic.api.server.PlayerManager;
import ru.craftlogic.api.text.Text;
import ru.craftlogic.api.world.OfflinePlayer;
import ru.craftlogic.permissions.GroupManager;
import ru.craftlogic.permissions.PermissionManager;
import ru.craftlogic.permissions.UserManager;

import java.util.*;

import static ru.craftlogic.api.CraftMessages.parseDuration;

public class CommandPermission extends CommandBase {
    public CommandPermission() {
        super("perm", 4,
            "group <group:PermGroup> permissions add|delete <value>",
            "group <group:PermGroup> permissions",
            "group <group:PermGroup> metadata set <key> <value>...",
            "group <group:PermGroup> metadata unset <key>",
            "group <group:PermGroup> metadata <key>",
            "group <group:PermGroup> metadata",
            "group <group:PermGroup> create <value>...",
            "group <group:PermGroup> create|delete|users",
            "group <group:PermGroup>",
            "user <username:OfflinePlayer> groups add|delete <value>",
            "user <username:OfflinePlayer> groups add|delete <value> <expiration>",
            "user <username:OfflinePlayer> groups",
            "user <username:OfflinePlayer> permissions add|delete <value>",
            "user <username:OfflinePlayer> permissions",
            "user <username:OfflinePlayer> metadata set <key> <value>...",
            "user <username:OfflinePlayer> metadata unset <key>",
            "user <username:OfflinePlayer> metadata <key>",
            "user <username:OfflinePlayer> metadata",
            "user <username:OfflinePlayer>"
        );
        Collections.addAll(aliases, "perms", "permissions", "permission");
    }

    @Override
    protected void execute(CommandContext ctx) throws Throwable {
        PermissionManager permissionManager = (PermissionManager) ctx.server().getPermissionManager();
        String defaultGroupName = permissionManager.getDefaultGroupName();
        PlayerManager playerManager = ctx.server().getPlayerManager();
        long current = System.currentTimeMillis();
        switch (ctx.action(0)) {
            case "group": {
                String groupName = ctx.get("group").asString();
                GroupManager.Group group = permissionManager.getGroup(groupName);
                if (ctx.hasAction(1)) {
                    switch (ctx.action(1)) {
                        case "permissions": {
                            if (group == null) {
                                throw new CommandException("commands.perm.group.notFound", groupName);
                            }
                            if (ctx.has("value")) {
                                String perm = ctx.get("value").asString();
                                switch (ctx.action(2)) {
                                    case "add": {
                                        boolean added = group.permissions.add(perm);
                                        ctx.sendMessage("commands.perm.group.permissions.add." + (added ? "success" : "unable"), perm, groupName);
                                        if (added) {
                                            permissionManager.save(true);
                                        }
                                        break;
                                    }
                                    case "delete": {
                                        boolean deleted = group.permissions.remove(perm);
                                        ctx.sendMessage("commands.perm.group.permissions.delete." + (deleted ? "success" : "unable"), perm, groupName);
                                        if (deleted) {
                                            permissionManager.save(true);
                                        }
                                        break;
                                    }
                                }
                            } else {
                                sendPermissions(group, ctx);
                            }
                            break;
                        }
                        case "metadata": {
                            if (group == null) {
                                throw new CommandException("commands.perm.group.notFound", groupName);
                            }
                            if (ctx.hasAction(2)) {
                                String key = ctx.get("key").asString();
                                switch (ctx.action(2)) {
                                    case "set": {
                                        String value = ctx.get("value").asString();
                                        boolean updated = group.metadata.put(key, value) == null;
                                        ctx.sendMessage("commands.perm.group.metadata.set." + (updated ? "success" : "unable"), key, value, groupName);
                                        if (updated) {
                                            permissionManager.save(true);
                                        }
                                        break;
                                    }
                                    case "unset": {
                                        boolean deleted = group.metadata.remove(key) != null;
                                        ctx.sendMessage("commands.perm.group.metadata.unset." + (deleted ? "success" : "unable"), key, groupName);
                                        if (deleted) {
                                            permissionManager.save(true);
                                        }
                                        break;
                                    }
                                }
                            } else {
                                sendMetadata(group, ctx);
                            }
                            break;
                        }
                        case "create": {
                            if (group != null) {
                                throw new CommandException("commands.perm.group.create.exists", groupName);
                            }
                            createGroup(permissionManager, ctx, groupName);
                            break;
                        }
                        case "delete": {
                            if (group == null) {
                                throw new CommandException("commands.perm.group.notFound", groupName);
                            }
                            if (groupName.equals(defaultGroupName)) {
                                throw new CommandException("commands.perm.group.delete.unable", groupName);
                            } else {
                                boolean removed = permissionManager.groupManager.groups.remove(groupName, group);
                                if (removed) {
                                    permissionManager.save(true);
                                }
                                ctx.sendMessage("commands.perm.group.delete.success", groupName);
                            }
                            break;
                        }
                        case "users": {
                            if (group == null) {
                                throw new CommandException("commands.perm.group.notFound", groupName);
                            }
                            if (groupName.equalsIgnoreCase(defaultGroupName)) {
                                ctx.sendMessage("commands.perm.info.group.members.everyone");
                            } else {
                                List<String> users = new ArrayList<>();
                                for (Map.Entry<UserManager.User, Long> e : group.users().entrySet()) {
                                    UserManager.User user = e.getKey();
                                    long expiration = e.getValue();
                                    UUID id = user.id();
                                    OfflinePlayer p = playerManager.getOffline(id);
                                    if (expiration > current) {
                                        String suffix = ", expires in: " + parseDuration(expiration - current);
                                        if (p != null && p.getName() != null) {
                                            users.add(id.toString() + " (" + p.getName() + ")" + suffix);
                                        } else {
                                            users.add(id.toString() + suffix);
                                        }
                                    } else if (p != null && p.getName() != null) {
                                        users.add(id.toString() + " (" + p.getName() + ")");
                                    } else {
                                        users.add(id.toString());
                                    }
                                }
                                ctx.sendMessage("commands.perm.info.group.members", users.toString());
                            }
                            break;
                        }
                    }
                } else {
                    if (group == null) {
                        throw new CommandException("commands.perm.group.notFound", groupName);
                    }
                    sendGroupInfo(ctx, group);
                }
                break;
            }
            case "user": {
                String username = ctx.get("username").asString();
                OfflinePlayer player = playerManager.getOffline(username);
                if (player == null) {
                    try {
                        player = playerManager.getOffline(UUID.fromString(username));
                    } catch (IllegalArgumentException ignored) {}
                }
                if (player != null) {
                    UserManager.User user = permissionManager.getUser(player);
                    if (ctx.hasAction(1)) {
                        switch (ctx.action(1)) {
                            case "groups": {
                                if (ctx.has("value")) {
                                    String groupName = ctx.get("value").asString();
                                    long expiration = ctx.getIfPresent("expiration", arg -> arg.asDuration() + current)
                                        .orElse(0L);
                                    GroupManager.Group group = permissionManager.getGroup(groupName);
                                    if (group != null) {
                                        switch (ctx.action(2)) {
                                            case "add": {
                                                boolean added = !user.groups.containsKey(group);
                                                if (added) {
                                                    user.groups.put(group, expiration);
                                                }
                                                ctx.sendMessage("commands.perm.user.groups.add." + (added ? "success" : "unable"), groupName, username);
                                                if (added) {
                                                    permissionManager.save(true);
                                                }
                                                break;
                                            }
                                            case "delete": {
                                                boolean deleted = user.groups.containsKey(group);
                                                if (deleted) {
                                                    user.groups.remove(group);
                                                }
                                                ctx.sendMessage("commands.perm.user.groups.delete." + (deleted ? "success" : "unable"), groupName, username);
                                                if (deleted) {
                                                    permissionManager.save(true);
                                                }
                                                break;
                                            }
                                        }
                                    } else {
                                        throw new CommandException("commands.perm.group.notFound", groupName);
                                    }
                                } else {
                                    sendGroups(user, player.getName(), ctx);
                                }
                                break;
                            }
                            case "permissions": {
                                if (ctx.has("value")) {
                                    String perm = ctx.get("value").asString();
                                    switch (ctx.action(2)) {
                                        case "add": {
                                            boolean added = user.permissions.add(perm);
                                            ctx.sendMessage("commands.perm.user.permissions.add." + (added ? "success" : "unable"), perm, username);
                                            if (added) {
                                                permissionManager.save(true);
                                            }
                                            break;
                                        }
                                        case "delete": {
                                            boolean deleted = user.permissions.remove(perm);
                                            ctx.sendMessage("commands.perm.user.permissions.delete." + (deleted ? "success" : "unable"), perm, user);
                                            if (deleted) {
                                                permissionManager.save(true);
                                            }
                                            break;
                                        }
                                    }
                                } else {
                                    sendPermissions(user, player.getName(), ctx);
                                }
                                break;
                            }
                            case "metadata": {
                                if (ctx.hasAction(2)) {
                                    String key = ctx.get("key").asString();
                                    switch (ctx.action(2)) {
                                        case "set": {
                                            String value = ctx.get("value").asString();
                                            boolean updated = user.metadata.put(key, value) == null;
                                            ctx.sendMessage("commands.perm.user.metadata.set." + (updated ? "success" : "unable"), key, value, player.getName());
                                            if (updated) {
                                                permissionManager.save(true);
                                            }
                                            break;
                                        }
                                        case "unset": {
                                            boolean deleted = user.metadata.remove(key) != null;
                                            ctx.sendMessage("commands.perm.user.metadata.unset." + (deleted ? "success" : "unable"), key, player.getName());
                                            if (deleted) {
                                                permissionManager.save(true);
                                            }
                                            break;
                                        }
                                    }
                                } else {
                                    sendMetadata(user, player.getName(), ctx);
                                }
                            }
                        }
                    } else {
                        sendUserInfo(ctx, username, user);
                    }
                } else {
                    throw new CommandException("commands.generic.userNeverPlayed", username);
                }
                break;
            }
        }
    }

    private void sendUserInfo(CommandContext ctx, String username, UserManager.User user) {
        ctx.sendMessage(
            Text.translation("commands.perm.info.user.header")
                .gray()
                .arg(username, Text::darkGray)
        );
        sendGroups(user, username, ctx);
    }

    private void sendGroupInfo(CommandContext ctx, GroupManager.Group group) {
        GroupManager.Group parent = group.parent();
        ctx.sendMessage(
            Text.translation("commands.perm.info.group.header")
                .gray()
                .arg(group.name, Text::darkGray)
        );
        if (parent != null) {
            ctx.sendMessage(
                Text.translation("commands.perm.info.group.parent")
                    .arg(parent.name(), Text::darkGray)
            );
        }
        sendPermissions(group, ctx);
    }

    private void sendPermissions(UserManager.User user, String username, CommandContext ctx) {
        ctx.sendMessage(
            Text.string("/").gray()
                .appendTranslate("commands.perm.info.permissions", Text::gray)
        );

        for (String s : user.permissions(false)) {
            ctx.sendMessage(
                Text.string("| ").gray()
                    .appendText(s, d ->
                        d.darkGray().suggestCommand("/perm user " + username + " permissions delete " + s)
                    )
            );
        }
    }

    private void sendMetadata(UserManager.User user, String username, CommandContext ctx) {
        ctx.sendMessage(
            Text.string("/").gray()
                .appendTranslate("commands.perm.info.metadata", Text::gray)
        );

        for (Map.Entry<String, String> e : user.metadata(false).entrySet()) {
            ctx.sendMessage(
                Text.string("| " + e.getKey() + " = ").gray()
                    .appendText(e.getValue(), d ->
                        d.darkGray().suggestCommand("/perm user " + username + " metadata unset  " + e.getKey())
                    )
            );
        }
    }

    private void sendPermissions(GroupManager.Group group, CommandContext ctx) {
        ctx.sendMessage(
            Text.string("/").gray()
                .appendTranslate("commands.perm.info.permissions", Text::gray)
        );

        for (String s : group.permissions(false)) {
            ctx.sendMessage(
                Text.string("| ").gray()
                    .appendText(s, d ->
                        d.darkGray().suggestCommand("/perm group " + group.name + " permissions delete " + s)
                    )
            );
        }
    }

    private void sendMetadata(GroupManager.Group group, CommandContext ctx) {
        ctx.sendMessage(
            Text.string("/").gray()
                .appendTranslate("commands.perm.info.metadata", Text::gray)
        );

        for (Map.Entry<String, String> e : group.metadata(false).entrySet()) {
            ctx.sendMessage(
                Text.string("| " + e.getKey() + " = ").gray()
                    .appendText(e.getValue(), d ->
                        d.darkGray().suggestCommand("/perm group " + group.name + " metadata unset " + e.getKey())
                    )
            );
        }
    }

    private void sendGroups(UserManager.User user, String username, CommandContext ctx) {
        ctx.sendMessage("commands.perm.info.user.groups");

        for (Map.Entry<GroupManager.Group, Long> e : user.groups.entrySet()) {
            GroupManager.Group g = e.getKey();
            long expiration = e.getValue();
            long current = System.currentTimeMillis();;
            ctx.sendMessage(
                Text.string("- ")
                    .appendText(g.name + (expiration > current ? " (expires in " + parseDuration(expiration - current) + ")" : ""), d ->
                        d.darkGray().suggestCommand("/perm user " + username + " groups delete " + g.name)
                    )
            );
        }
    }

    private void createGroup(PermissionManager permissionManager, CommandContext ctx, String groupName) throws Exception {
        int priority = 0;
        String parent = permissionManager.getDefaultGroupName();
        if (ctx.has("value")) {
            String value = ctx.get("value").asString();
            if (value.contains(" ")) {
                String[] vals = value.split(" ");
                switch (vals.length) {
                    case 2:
                        priority = Integer.parseInt(vals[1]);
                    case 1:
                        parent = vals[0];
                        break;
                    default:
                        throw new WrongUsageException("commands.perm.usage");
                }
            }
        }
        GroupManager.Group group = permissionManager.groupManager.new Group(groupName, parent, new HashSet<>(), new HashMap<>(), priority);
        permissionManager.groupManager.groups.put(groupName, group);
        permissionManager.save(true);
        ctx.sendMessage("commands.perm.group.create.success", groupName);
    }
}
