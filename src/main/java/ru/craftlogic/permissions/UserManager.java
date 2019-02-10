package ru.craftlogic.permissions;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.apache.logging.log4j.Logger;
import ru.craftlogic.api.util.ConfigurableManager;
import ru.craftlogic.permissions.GroupManager.Group;

import java.nio.file.Path;
import java.util.*;

public class UserManager extends ConfigurableManager {
    final Map<UUID, User> users = new HashMap<>();
    private final PermissionManager permissionManager;

    public UserManager(PermissionManager permissionManager, Path configPath, Logger logger) {
        super(permissionManager.getServer(), configPath, logger);
        this.permissionManager = permissionManager;
    }

    @Override
    protected void load(JsonObject users) {
        for (Map.Entry<String, JsonElement> entry : users.entrySet()) {
            UUID id = UUID.fromString(entry.getKey());
            if (!(entry.getValue() instanceof JsonObject)) {
                getLogger().error("User entry '" + id + "' isn't an object! Ignoring it...");
                continue;
            }
            JsonObject u = (JsonObject) entry.getValue();
            Set<String> permissions = new HashSet<>();
            if (u.has("permissions")) {
                for (JsonElement element : u.getAsJsonArray("permissions")) {
                    permissions.add(element.getAsString());
                }
            }
            Map<String, String> metadata = new HashMap<>();
            if (u.has("metadata")) {
                for (Map.Entry<String, JsonElement> e : u.getAsJsonObject("metadata").entrySet()) {
                    metadata.put(e.getKey(), e.getValue().getAsString());
                }
            }
            Set<Group> groups = new HashSet<>();

            if (u.has("groups")) {
                for (JsonElement group : u.getAsJsonArray("groups")) {
                    String groupName = group.getAsString();
                    if (!this.permissionManager.groupManager.groups.containsKey(groupName)) {
                        getLogger().error("User '" + id + "' is a member of an unknown group named '" + groupName + "' Ignoring it...");
                        continue;
                    }
                    groups.add(this.permissionManager.groupManager.groups.get(groupName));
                }
            }
            User user = new User(id, groups, permissions, metadata);
            this.users.put(id, user);
            for (Group group : groups) {
                this.permissionManager.groupManager.groupUsersCache.computeIfAbsent(group, k -> new ArrayList<>()).add(user);
            }
        }
    }

    @Override
    protected void save(JsonObject users) {
        for (Map.Entry<UUID, User> entry : this.users.entrySet()) {
            User u = entry.getValue();
            JsonObject user = new JsonObject();
            if (!u.permissions.isEmpty()) {
                JsonArray permissions = new JsonArray();
                for (String permission : u.permissions) {
                    permissions.add(new JsonPrimitive(permission));
                }
                user.add("permissions", permissions);
            }
            if (!u.metadata.isEmpty()) {
                JsonObject metadata = new JsonObject();
                for (Map.Entry<String, String> e : u.metadata.entrySet()) {
                    metadata.addProperty(e.getKey(), e.getValue());
                }
                user.add("metadata", metadata);
            }
            if (!u.groups.isEmpty()) {
                JsonArray groups = new JsonArray();
                for (Group group : u.groups) {
                    groups.add(new JsonPrimitive(group.name));
                }
                user.add("groups", groups);
            }
            if (user.size() > 0) {
                users.add(entry.getKey().toString(), user);
            }
        }
    }

    public Collection<Group> getGroups(UUID id) {
        Set<Group> groups = new HashSet<>();
        Group defaultGroup = this.permissionManager.getDefaultGroup();
        if (defaultGroup != null) {
            groups.add(defaultGroup);
        }
        User user = this.users.get(id);
        if (user != null) {
            groups.addAll(user.groups);
        }
        return groups;
    }

    public User getUser(UUID id) {
        return this.users.computeIfAbsent(id, User::new);
    }

    public class User {
        final UUID id;
        final Set<Group> groups;
        final Set<String> permissions;
        final Map<String, String> metadata;

        User(UUID id) {
            this(id, new HashSet<>(), new HashSet<>(), new HashMap<>());
        }

        public User(UUID id, Set<Group> groups, Set<String> permissions, Map<String, String> metadata) {
            this.id = id;
            this.groups = groups;
            this.permissions = permissions;
            this.metadata = metadata;
        }

        public UUID id() {
            return this.id;
        }

        public Set<String> permissions(boolean inherit) {
            Set<String> permissions = new HashSet<>(this.permissions);
            if (inherit) {
                Group defaultGroup = UserManager.this.permissionManager.getDefaultGroup();
                if (defaultGroup != null) {
                    permissions.addAll(defaultGroup.permissions(true));
                }
                for (Group group : this.groups) {
                    permissions.addAll(group.permissions(true));
                }
            }
            return permissions;
        }

        public Map<String, String> metadata(boolean inherit) {
            Map<String, String> metadata = new HashMap<>(this.metadata);
            if (inherit) {
                Group defaultGroup = UserManager.this.permissionManager.getDefaultGroup();
                if (defaultGroup != null) {
                    metadata.putAll(defaultGroup.metadata(true));
                }
                for (Group group : this.groups) {
                    metadata.putAll(group.metadata(true));
                }
            }
            return metadata;
        }

        public boolean hasPermissions(String... permissions) {
            return this.hasPermissions(Arrays.asList(permissions));
        }

        public boolean hasPermissions(Collection<String> permissions) {
            if (permissions.isEmpty()) {
                return true;
            }
            Set<String> ps = this.permissions(true);
            if (ps.contains("*")) {
                return true;
            }
            for (String permission : permissions) {
                if (!ps.contains(permission) || ps.contains("-" + permission))
                    return false;
            }
            return true;
        }

        public String getPermissionMetadata(String meta) {
            Map<String, String> md = this.metadata(true);
            return md.get(meta);
        }
    }
}