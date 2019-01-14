package ru.craftlogic.permissions.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.client.FMLClientHandler;
import ru.craftlogic.permissions.common.ProxyCommon;
import ru.craftlogic.util.ReflectiveUsage;

@ReflectiveUsage
public class ProxyClient extends ProxyCommon {
    private final Minecraft client = FMLClientHandler.instance().getClient();

    @Override
    public void preInit() {
        super.preInit();
    }

    @Override
    public void init() {
        super.init();
    }

    @Override
    public void postInit() {
        super.postInit();
    }
}
