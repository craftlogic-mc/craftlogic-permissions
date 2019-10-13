package ru.craftlogic.permissions;

import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import net.minecraft.util.JsonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.craftlogic.api.server.Server;
import ru.craftlogic.api.util.ConfigurableManager;
import ru.craftlogic.api.world.OfflinePlayer;
import ru.craftlogic.common.command.CommandManager;
import ru.craftlogic.permissions.GroupManager.Group;
import ru.craftlogic.permissions.UserManager.User;
import ru.craftlogic.permissions.common.commands.CommandPermission;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class PermissionManager extends ConfigurableManager implements ru.craftlogic.api.permission.PermissionManager {
    private static final Logger LOGGER = LogManager.getLogger("PermissionManager");

    private final Path configFile;
    private boolean enabled;
    public final UserManager userManager;
    public final GroupManager groupManager;

    public PermissionManager(Server server, Path settingsDirectory) {
        super(server, settingsDirectory.resolve("permissions.json"), LOGGER);
        this.configFile = settingsDirectory.resolve("permissions.json");
        this.userManager = new UserManager(this, settingsDirectory.resolve("permissions/users.json"), LOGGER);
        this.groupManager = new GroupManager(this, settingsDirectory.resolve("permissions/groups.json"), LOGGER);
    }

    @Override
    public boolean isEnabled() {
        return this.enabled;
    }

    @Override
    protected String getModId() {
        return CraftPermissions.MOD_ID;
    }

    @Override
    public Path getConfigFile() {
        return this.configFile;
    }

    @Override
    public Logger getLogger() {
        return LOGGER;
    }

    @Override
    public void registerCommands(CommandManager commandManager) {
        commandManager.registerArgumentType("PermGroup", false, ctx ->
            ((PermissionManager)ctx.server().getPermissionManager()).getAllGroups()
        );
        commandManager.registerCommand(new CommandPermission());
    }

    @Override
    protected void load(JsonObject config) {
        this.enabled = JsonUtils.getBoolean(config, "enabled");
        if (this.enabled) {
            try {
                this.groupManager.load();
                this.userManager.load();
            } catch (IOException e) {
                e.printStackTrace();
            }
            LOGGER.info("Load complete!");
        }
    }

    @Override
    protected void save(JsonObject config) {
        config.addProperty("enabled", this.enabled);
        if (this.enabled) {
            try {
                this.groupManager.save(true);
                this.userManager.save(true);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean hasPermissions(GameProfile profile, Collection<String> permissions) {
        User user = this.userManager.getUser(profile.getId());
        return user.hasPermissions(permissions);
    }

    @Override
    public String getPermissionMetadata(GameProfile profile, String meta) {
        User user = this.userManager.getUser(profile.getId());
        return user.getPermissionMetadata(meta);
    }

    public String getDefaultGroupName() {
        return "default";
    }

    public Group getDefaultGroup() {
        return this.groupManager.groups.get(getDefaultGroupName());
    }

    public Group getGroup(String name) {
        return this.groupManager.groups.get(name);
    }

    public Set<String> getAllGroups() {
        return ImmutableSet.copyOf(this.groupManager.groups.keySet());
    }

    public Map<Group, Long> getGroups(OfflinePlayer player) {
        return this.getGroups(player.getId());
    }

    public Map<Group, Long> getGroups(UUID id) {
        return this.userManager.getGroups(id);
    }

    public User getUser(OfflinePlayer player) {
        return this.getUser(player.getId());
    }

    public User getUser(UUID id) {
        return this.userManager.getUser(id);
    }
}
