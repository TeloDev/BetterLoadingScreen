package alexiil.mods.load.coremod;

import alexiil.mods.load.ProgressDisplayer;
import alexiil.mods.load.Translation;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin.MCVersion;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin.SortingIndex;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin.TransformerExclusions;
import java.io.File;
import java.lang.reflect.Method;
import java.util.Map;

@MCVersion("1.7.10")
@TransformerExclusions({"alexiil.mods.load.coremod"})
@SortingIndex(Integer.MAX_VALUE - 80)
// A big number
public class LoadingScreenLoadPlugin implements cpw.mods.fml.relauncher.IFMLLoadingPlugin {

    private static Method disableSplashMethodRef;

    @Override
    public String[] getASMTransformerClass() {
        return new String[] {"alexiil.mods.load.coremod.BetterLoadingScreenTransformer"};
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        File coremodLocation = (File) data.get("coremodLocation");
        Translation.addTranslations(coremodLocation);
        ProgressDisplayer.start(coremodLocation);
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
