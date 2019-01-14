package ru.craftlogic.permissions;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import ru.craftlogic.api.CraftAPI;
import ru.craftlogic.api.network.AdvancedNetwork;
import ru.craftlogic.permissions.common.ProxyCommon;

@Mod(modid = CraftPermissions.MOD_ID, version = CraftPermissions.VERSION, dependencies = "required-after:" + CraftAPI.MOD_ID)
public class CraftPermissions {
    public static final String MOD_ID = CraftAPI.MOD_ID + "-permissions";
    public static final String VERSION = "0.2.0-BETA";

    @SidedProxy(clientSide = "ru.craftlogic.permissions.client.ProxyClient", serverSide = "ru.craftlogic.permissions.common.ProxyCommon")
    public static ProxyCommon PROXY;
    public static final AdvancedNetwork NETWORK = new AdvancedNetwork(MOD_ID);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(PROXY);
        PROXY.preInit();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        NETWORK.openChannel();
        PROXY.init();
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        PROXY.postInit();
    }
}

