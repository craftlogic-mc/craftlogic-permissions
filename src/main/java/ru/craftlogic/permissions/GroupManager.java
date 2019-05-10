package ru.craftlogic.permissions;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.apache.logging.log4j.Logger;
import ru.craftlogic.api.util.ConfigurableManager;

import java.nio.file.Path;
import java.util.*;

public class GroupManager extends ConfigurableManager {
    private final PermissionManager permissionManager;
    final Map<String, Group> groups = new HashMap<>();
    final Map<Group, Map<UserManager.User, Long>> groupUsersCache = new HashMap<>();

    public GroupManager(PermissionManager permissionManager, Path configPath, Logger logger) {
        super(permissionManager.getServer(), configPath, logger);
        this.permissionManager = permissionManager;
    }

    @Override
    protected void load(JsonObject groups) {
        String defaultGroupName = this.permissionManager.getDefaultGroupName();
        if (groups.size() == 0) {
            getLogger().warn("There's no groups to load! At all...");
            this.setDirty(true);
        }
        if (!groups.has(defaultGroupName)) {
            getLogger().warn("Default group is missing! Creating empty one...");
            groups.add(defaultGroupName, new JsonObject());
            this.setDirty(true);
        }
        Map<String, JsonObject> groupCache = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : groups.entrySet()) {
            if (!(entry.getValue() instanceof JsonObject)) {
                getLogger().error("Group '" + entry.getKey() + "' must be an object! Ignoring it...");
                continue;
            }
            groupCache.put(entry.getKey(), (JsonObject) entry.getValue());
        }
        for (Map.Entry<String, JsonObject> entry : groupCache.entrySet()) {
            String groupName = entry.getKey();
            JsonObject g = entry.getValue();
            String parentName = defaultGroupName;
            if (g.has("parent") && !groupCache.containsKey(parentName = g.get("parent").getAsString())) {
                getLogger().error("Group '" + groupName + "' has undefined parent '" + parentName + "'! Ignoring it...");
            }
            if (parentName.equals(groupName) && !groupName.equals(defaultGroupName)) {
                getLogger().error("Group '" + groupName + "' cannot be a child of itself!");
            }
            Set<String> permissions = new HashSet<>();
            if (g.has("permissions")) {
                JsonArray p = g.getAsJsonArray("permissions");
                for (JsonElement element : p) {
                    permissions.add(element.getAsString());
                }
            }
            Map<String, String> metadata = new HashMap<>();
            if (g.has("metadata")) {
                JsonObject m = g.getAsJsonObject("metadata");
                for (Map.Entry<String, JsonElement> e : m.entrySet()) {
                    metadata.put(e.getKey(), e.getValue().getAsString());
                }
            }
            int priority = g.has("priority") ? g.get("priority").getAsInt() : 0;
            this.groups.put(groupName, new Group(groupName, parentName, permissions, metadata, priority));
        }
        groupCache.clear();
    }

    @Override
    protected void save(JsonObject groups) {
        String defaultGroupName = permissionManager.getDefaultGroupName();
        for (Map.Entry<String, Group> entry : this.groups.entrySet()) {
            Group g = entry.getValue();
            JsonObject group = new JsonObject();
            if (!(g.name.equals(defaultGroupName) && g.parent.equals(defaultGroupName))) {
                group.addProperty("parent", g.parent);
            }
            if (!g.permissions.isEmpty()) {
                JsonArray permissions = new JsonArray();
                for (String permission : g.permissions) {
                    permissions.add(new JsonPrimitive(permission));
                }
                group.add("permissions", permissions);
            }
            if (!g.metadata.isEmpty()) {
                JsonObject metadata = new JsonObject();
                for (Map.Entry<String, String> e : g.metadata.entrySet()) {
                    metadata.addProperty(e.getKey(), e.getValue());
                }
                group.add("metadata", metadata);
            }
            if (g.priority != 0) {
                group.addProperty("priority", g.priority);
            }
            groups.add(entry.getKey(), group);
        }
    }

    public class Group implements Comparable<Group> {
        final String name, parent;
        final Set<String> permissions;
        final Map<String, String> metadata;
        int priority;

        public Group(String name, String parent, Set<String> permissions, Map<String, String> metadata, int priority) {
            this.name = name;
            this.parent = parent;
            this.permissions = permissions;
            this.metadata = metadata;
            this.priority = priority;
        }

        public String name() {
            return this.name;
        }

        public Group parent() {
            Group parent = GroupManager.this.groups.get(this.parent);
            if (parent == this) {
                return null;
            } else {
                return parent;
            }
        }

        public Set<String> permissions(boolean inherit) {
            Set<String> permissions = new HashSet<>(this.permissions);
            if (inherit) {
                Group parent = this.parent();
                if (parent != null) {
                    permissions.addAll(parent.permissions(true));
                }
            }
            return permissions;
        }

        public Map<String, String> metadata(boolean inherit) {
            Map<String, String> metadata = new HashMap<>(this.metadata);
            if (inherit) {
                Group parent = this.parent();
                if (parent != null) {
                    metadata.putAll(parent.metadata(true));
                }
            }
            return metadata;
        }

        public Map<UserManager.User, Long> users() {
            Map<UserManager.User, Long> result = new HashMap<>();
            if (!this.name.equals(GroupManager.this.permissionManager.getDefaultGroupName())) {
                Map<UserManager.User, Long> cachedUsers = GroupManager.this.groupUsersCache.get(this);
                if (cachedUsers != null) {
                    result.putAll(cachedUsers);
                }
            }
            return result;
        }

        @Override
        public int compareTo(Group o) {
            return Integer.compare(this.priority, o.priority);
        }
    }
}