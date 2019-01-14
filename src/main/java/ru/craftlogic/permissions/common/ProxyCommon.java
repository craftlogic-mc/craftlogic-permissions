package ru.craftlogic.permissions.common;

import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import ru.craftlogic.api.event.server.ServerAddManagersEvent;
import ru.craftlogic.api.network.AdvancedMessageHandler;
import ru.craftlogic.permissions.PermissionManager;
import ru.craftlogic.util.ReflectiveUsage;

@ReflectiveUsage
public class ProxyCommon extends AdvancedMessageHandler {
    public void preInit() {

    }

    public void init() {

    }

    public void postInit() {

    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onServerAddManagers(ServerAddManagersEvent event) {
        event.addManager(PermissionManager.class, PermissionManager::new);
    }
}
