package alexiil.mods.load;

import alexiil.mods.load.ProgressDisplayer.IDisplayer;
import net.minecraftforge.common.config.Configuration;

public class MinecraftDisplayerWrapper implements IDisplayer {
    private MinecraftDisplayer mcDisp;
    private Configuration cfg;

    @Override
    public void open(Configuration cfg) {
        this.cfg = cfg;
    }

    @Override
    public void displayProgress(String text, float percent) {
        if (mcDisp == null) {
            try {
                mcDisp = new MinecraftDisplayer();
                mcDisp.open(cfg);
            } catch (Throwable t) {
                BetterLoadingScreen.log.error("Failed to load Minecraft Displayer!");
                t.printStackTrace();
                mcDisp = null;
            }
            cfg.save();
        }
        if (mcDisp != null) mcDisp.displayProgress(text, percent);
    }

    @Override
    public void close() {
        if (mcDisp != null) mcDisp.close();
    }

    public static void playFinishedSound() {
        MinecraftDisplayer.playFinishedSound();
    }
}
