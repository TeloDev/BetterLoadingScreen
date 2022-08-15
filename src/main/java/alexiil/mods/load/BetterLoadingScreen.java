package alexiil.mods.load;

import alexiil.mods.load.ModLoadingListener.State;
import alexiil.mods.load.git.Commit;
import alexiil.mods.load.git.GitHubUser;
import alexiil.mods.load.git.Release;
import com.google.common.eventbus.EventBus;
import cpw.mods.fml.client.event.ConfigChangedEvent.OnConfigChangedEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.FMLModContainer;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.event.FMLConstructionEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerAboutToStartEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
        modid = Lib.Mod.ID,
        version = Lib.Mod.VERSION,
        name = Lib.Mod.NAME,
        acceptedMinecraftVersions = "[1.7.10]",
        guiFactory = "alexiil.mods.load.gui.ConfigGuiFactory",
        acceptableRemoteVersions = "*")
public class BetterLoadingScreen {
    @Instance(Lib.Mod.ID)
    public static BetterLoadingScreen instance;

    public static final Logger log = LogManager.getLogger(Lib.Mod.ID);
    private static List<GitHubUser> contributors = null;
    private static List<Commit> commits = null;
    private static List<Release> releases = null;
    private static Commit thisCommit = null;
    public static ModMetadata meta;

    @EventHandler
    public void construct(FMLConstructionEvent event) throws IOException {
        ModLoadingListener thisListener = null;
        for (ModContainer mod : Loader.instance().getActiveModList()) {
            if (mod instanceof FMLModContainer) {
                EventBus bus = null;
                try {
                    // It's a bit questionable to be changing FML itself, but reflection is better than ASM transforming
                    // forge
                    Field f = FMLModContainer.class.getDeclaredField("eventBus");
                    f.setAccessible(true);
                    bus = (EventBus) f.get(mod);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
                if (bus != null) {
                    if (mod.getModId().equals(Lib.Mod.ID)) {
                        thisListener = new ModLoadingListener(mod);
                        bus.register(thisListener);
                    } else bus.register(new ModLoadingListener(mod));
                }
            }
        }
        if (thisListener != null) {
            ModLoadingListener.doProgress(State.CONSTRUCT, thisListener);
        }
    }

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(instance);
        FMLCommonHandler.instance().bus().register(instance);
        meta = event.getModMetadata();
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void guiOpen(GuiOpenEvent event) throws IOException {
        ProgressDisplayer.close();
    }

    @SubscribeEvent
    public void configChanged(OnConfigChangedEvent event) {
        if (Objects.equals(event.modID, Lib.Mod.ID)) ProgressDisplayer.cfg.save();
    }

    @EventHandler
    @SideOnly(Side.SERVER)
    public void serverAboutToStart(FMLServerAboutToStartEvent event) throws IOException {
        ProgressDisplayer.close();
    }

    public static void initSiteVersioning() {}

    public static List<Commit> getCommits() {
        if (contributors == null) initSiteVersioning();
        return commits;
    }

    public static Commit getCurrentCommit() {
        if (contributors == null) initSiteVersioning();
        return thisCommit;
    }

    public static List<Release> getReleases() {
        if (contributors == null) initSiteVersioning();
        return releases;
    }
}
