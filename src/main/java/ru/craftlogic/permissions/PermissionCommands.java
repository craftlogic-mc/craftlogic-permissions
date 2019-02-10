package ru.craftlogic.permissions;

import net.minecraft.command.CommandException;
import net.minecraft.command.WrongUsageException;
import ru.craftlogic.api.command.*;
import ru.craftlogic.api.server.PlayerManager;
import ru.craftlogic.api.text.Text;
import ru.craftlogic.api.world.OfflinePlayer;
import ru.craftlogic.permissions.GroupManager.Group;
import ru.craftlogic.permissions.UserManager.User;

import java.util.*;

public class PermissionCommands implements CommandRegistrar {
    @Command(name = "perm", syntax = {
        "group <group:PermGroup> permissions [add|delete] <value>...",
        "group <group:PermGroup> permissions",
        "group <group:PermGroup> metadata set <key> <value>...",
        "group <group:PermGroup> metadata unset <key>",
        "group <group:PermGroup> metadata <key>",
        "group <group:PermGroup> metadata",
        "group <group:PermGroup> [create] <value>...",
        "group <group:PermGroup> [create|delete|users]",
        "group <group:PermGroup>",
        "user <username:OfflinePlayer> groups [add|delete] <value>...",
        "user <username:OfflinePlayer> groups",
        "user <username:OfflinePlayer> permissions [add|delete] <value>...",
        "user <username:OfflinePlayer> permissions",
        "user <username:OfflinePlayer> metadata set <key> <value>...",
        "user <username:OfflinePlayer> metadata unset <key>",
        "user <username:OfflinePlayer> metadata <key>",
        "user <username:OfflinePlayer> metadata",
        "user <username:OfflinePlayer>"
    }, aliases = {"perms", "permission", "permissions"})
    public static void commandPerm(CommandContext ctx) throws Exception {
        PermissionManager permissionManager = (PermissionManager) ctx.server().getPermissionManager();
        String defaultGroupName = permissionManager.getDefaultGroupName();
        PlayerManager playerManager = ctx.server().getPlayerManager();
        switch (ctx.constant(0)) {
            case "group": {
                String groupName = ctx.get("group").asString();
                Group group = permissionManager.getGroup(groupName);
                if (ctx.hasConstant(1)) {
                    switch (ctx.constant(1)) {
                        case "permissions": {
                            if (ctx.has("value")) {
                                String perm = ctx.get("value").asString();
                                switch (ctx.action()) {
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
                            if (ctx.hasConstant(2)) {
                                String key = ctx.get("key").asString();
                                switch (ctx.constant(2)) {
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
                    }
                } else if (ctx.hasAction()) {
                    if (ctx.action().equals("create")) {
                        if (group != null) {
                            throw new CommandException("commands.perm.group.create.exists", groupName);
                        }
                        createGroup(permissionManager, ctx, groupName);
                    } else {
                        switch (ctx.action()) {
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
                                    for (User user : group.users()) {
                                        UUID id = user.id();
                                        OfflinePlayer p = playerManager.getOffline(id);
                                        if (p != null && p.getName() != null) {
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
                    User user = permissionManager.getUser(player);
                    if (ctx.hasConstant(1)) {
                        switch (ctx.constant(1)) {
                            case "groups": {
                                if (ctx.has("value")) {
                                    String groupName = ctx.get("value").asString();
                                    Group group = permissionManager.getGroup(groupName);
                                    if (group != null) {
                                        switch (ctx.action()) {
                                            case "add": {
                                                boolean added = user.groups.add(group);
                                                ctx.sendMessage("commands.perm.user.groups.add." + (added ? "success" : "unable"), groupName, username);
                                                if (added) {
                                                    permissionManager.save(true);
                                                }
                                                break;
                                            }
                                            case "delete": {
                                                boolean deleted = user.groups.remove(group);
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
                                    switch (ctx.action()) {
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
                                if (ctx.hasConstant(2)) {
                                    String key = ctx.get("key").asString();
                                    switch (ctx.constant(2)) {
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

    private static void sendUserInfo(CommandContext ctx, String username, User user) {
        ctx.sendMessage(
            Text.translation("commands.perm.info.user.header")
                .gray()
                .arg(username, Text::darkGray)
        );
        sendGroups(user, username, ctx);
    }

    private static void sendGroupInfo(CommandContext ctx, Group group) {
        Group parent = group.parent();
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

    private static void sendPermissions(User user, String username, CommandContext ctx) {
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

    private static void sendMetadata(User user, String username, CommandContext ctx) {
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

    private static void sendPermissions(Group group, CommandContext ctx) {
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

    private static void sendMetadata(Group group, CommandContext ctx) {
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

    private static void sendGroups(User user, String username, CommandContext ctx) {
        ctx.sendMessage("commands.perm.info.user.groups");

        for (Group g : user.groups) {
            ctx.sendMessage(
                Text.string("- ")
                    .appendText(g.name, d ->
                        d.darkGray().suggestCommand("/perm user " + username + " groups delete " + g.name)
                    )
            );
        }
    }

    private static void createGroup(PermissionManager permissionManager, CommandContext ctx, String groupName) throws Exception {
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
        Group group = permissionManager.groupManager.new Group(groupName, parent, new HashSet<>(), new HashMap<>(), priority);
        permissionManager.groupManager.groups.put(groupName, group);
        permissionManager.save(true);
        ctx.sendMessage("commands.perm.group.create.success", groupName);
    }

    @ArgumentCompleter(type = "PermGroup")
    public static Set<String> completerPermGroup(ArgumentCompletionContext ctx) {
        return ((PermissionManager)ctx.server().getPermissionManager()).getAllGroups();
    }
}
