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
            List<String> permissions = new ArrayList<>();
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
            List<Group> groups = new ArrayList<>();

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
            String prefix = u.has("prefix") ? u.get("prefix").getAsString() : null;
            String suffix = u.has("suffix") ? u.get("suffix").getAsString() : null;
            User user = new User(id, groups, permissions, metadata, prefix, suffix);
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
            if (u.prefix != null) {
                user.addProperty("prefix", u.prefix);
            }
            if (u.suffix != null) {
                user.addProperty("suffix", u.suffix);
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
        final List<Group> groups;
        final List<String> permissions;
        final Map<String, String> metadata;
        String prefix, suffix;

        User(UUID id) {
            this(id, new ArrayList<>(), new ArrayList<>(), new HashMap<>(), null, null);
        }

        public User(UUID id, List<Group> groups, List<String> permissions, Map<String, String> metadata, String prefix, String suffix) {
            this.id = id;
            this.groups = groups;
            this.permissions = permissions;
            this.metadata = metadata;
            this.prefix = prefix;
            this.suffix = suffix;
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

        public String prefix() {
            if (this.prefix != null) {
                return this.prefix;
            } else {
                Map<Integer, String> prefixes = new TreeMap<>();
                for (Group group : this.groups) {
                    String prefix = group.prefix();
                    if (prefix != null && !prefix.isEmpty()) {
                        prefixes.put(group.priority, prefix);
                    }
                }
                if (!prefixes.isEmpty()) {
                    return prefixes.get(0);
                } else {
                    return "";
                }
            }
        }

        public String suffix() {
            if (this.suffix != null) {
                return this.suffix;
            } else {
                Map<Integer, String> suffixes = new TreeMap<>();
                for (Group group : this.groups) {
                    String suffix = group.suffix();
                    if (suffix != null && !suffix.isEmpty()) {
                        suffixes.put(group.priority, suffix);
                    }
                }
                if (!suffixes.isEmpty()) {
                    return suffixes.get(0);
                } else {
                    return "";
                }
            }
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