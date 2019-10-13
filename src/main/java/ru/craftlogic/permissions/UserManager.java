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
            Map<Group, Long> groups = new HashMap<>();

            if (u.has("groups")) {
                if (u.get("groups").isJsonArray()) {
                    for (JsonElement group : u.getAsJsonArray("groups")) {
                        String groupName = group.getAsString();
                        if (!this.permissionManager.groupManager.groups.containsKey(groupName)) {
                            getLogger().error("User '" + id + "' is a member of an unknown group named '" + groupName + "' Ignoring it...");
                            continue;
                        }
                        groups.put(this.permissionManager.groupManager.groups.get(groupName), null);
                    }
                } else {
                    for (Map.Entry<String, JsonElement> e : u.getAsJsonObject("groups").entrySet()) {
                        String groupName = e.getKey();
                        Long expiration = e.getValue() == null ? null : e.getValue().getAsLong();
                        if (!this.permissionManager.groupManager.groups.containsKey(groupName)) {
                            getLogger().error("User '" + id + "' is a member of an unknown group named '" + groupName + "' Ignoring it...");
                            continue;
                        }
                        groups.put(this.permissionManager.groupManager.groups.get(groupName), expiration);
                    }
                }
            }
            User user = new User(id, groups, permissions, metadata);
            this.users.put(id, user);
            for (Map.Entry<Group, Long> e : groups.entrySet()) {
                Group group = e.getKey();
                Long expiration = e.getValue();
                this.permissionManager.groupManager.groupUsersCache.computeIfAbsent(group, k -> new HashMap<>()).put(user, expiration);
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
                JsonObject groups = new JsonObject();
                for (Iterator<Map.Entry<Group, Long>> iterator = u.groups.entrySet().iterator(); iterator.hasNext(); ) {
                    Map.Entry<Group, Long> e = iterator.next();
                    Group group = e.getKey();
                    Long expiration = e.getValue();
                    if (expiration == null || expiration < System.currentTimeMillis()) {
                        groups.add(group.name, new JsonPrimitive(expiration));
                    } else {
                        iterator.remove();
                    }
                }
                user.add("groups", groups);
            }
            if (user.size() > 0) {
                users.add(entry.getKey().toString(), user);
            }
        }
    }

    public Map<Group, Long> getGroups(UUID id) {
        Map<Group, Long> groups = new HashMap<>();
        Group defaultGroup = this.permissionManager.getDefaultGroup();
        if (defaultGroup != null) {
            groups.put(defaultGroup, null);
        }
        User user = this.users.get(id);
        if (user != null) {
            groups.putAll(user.groups);
        }
        return groups;
    }

    public User getUser(UUID id) {
        return this.users.computeIfAbsent(id, User::new);
    }

    public class User {
        public final UUID id;
        public final Map<Group, Long> groups;
        public final Set<String> permissions;
        public final Map<String, String> metadata;

        User(UUID id) {
            this(id, new HashMap<>(), new HashSet<>(), new HashMap<>());
        }

        public User(UUID id, Map<Group, Long> groups, Set<String> permissions, Map<String, String> metadata) {
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
                for (Iterator<Map.Entry<Group, Long>> iterator = this.groups.entrySet().iterator(); iterator.hasNext(); ) {
                    Map.Entry<Group, Long> entry = iterator.next();
                    if (entry.getValue() == null || entry.getValue() < System.currentTimeMillis()) {
                        permissions.addAll(entry.getKey().permissions(true));
                    } else {
                        iterator.remove();
                    }
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
                for (Iterator<Map.Entry<Group, Long>> iterator = this.groups.entrySet().iterator(); iterator.hasNext(); ) {
                    Map.Entry<Group, Long> entry = iterator.next();
                    if (entry.getValue() == null || entry.getValue() < System.currentTimeMillis()) {
                        metadata.putAll(entry.getKey().metadata(true));
                    } else {
                        iterator.remove();
                    }
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