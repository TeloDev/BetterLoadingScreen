package alexiil.mods.load.imgur;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.config.Configuration;

import alexiil.mods.load.BetterLoadingScreen;

public class ImgurCacheManager {

    private static final boolean OFFLINE_MODE = Boolean.getBoolean("bls.offlineMode");
    private static final String IMGUR_CACHE_DIR = "bls-imgur-cache";

    private final Map<String, AbstractTexture> textureCache = new ConcurrentHashMap<>();

    private String appClientId;
    private String galleryId;
    private int requestTimeout;

    private volatile boolean cancelSetup;

    public void loadConfig(Configuration config) {
        appClientId = config.getString(
                "imgurAppClientId",
                "imgur",
                "",
                "The client ID of your imgur application. Required to access the imgur api.");
        galleryId = config
                .getString("imgurGalleryId", "imgur", "", "ID of the imgur gallery/album. For example: Ks0TrYE");
        requestTimeout = config.getInt(
                "imgurRequestTimeout",
                "imgur",
                5000,
                100,
                Integer.MAX_VALUE,
                "Request timeout (ms) for imgur requests");
    }

    public AbstractTexture getCachedTexture(ResourceLocation location) {
        if (!location.getResourceDomain().equals(IMGUR_CACHE_DIR)) return null;

        return textureCache.get(location.getResourcePath());
    }

    public void cleanUp() {
        textureCache.values().forEach(AbstractTexture::deleteGlTexture);
        textureCache.clear();

        cancelSetup = true;
    }

    public void setupImgurGallery(Consumer<ResourceLocation> textureLocationConsumer) {
        Path cacheFolder = Paths.get(IMGUR_CACHE_DIR);
        if (Files.notExists(cacheFolder)) {
            try {
                Files.createDirectory(cacheFolder);
            } catch (IOException e) {
                BetterLoadingScreen.log.error("Error while creating imgur cache directory", e);
                return;
            }
        }

        List<String> cachedImageIDs = getCachedImageIDs();
        if (cachedImageIDs == null) return;

        // Load any image that is already cached. This avoids waiting for the imgur api call to finish to get something
        // rendering
        loadAnyImageFromDisk(cachedImageIDs, textureLocationConsumer);

        CompletableFuture.runAsync(() -> {
            try (ImgurClient client = OFFLINE_MODE ? null : new ImgurClient(appClientId, requestTimeout)) {
                Consumer<String> imageHandler = imageID -> {
                    // This will leave behind cached images that are no longer in the gallery
                    synchronized (cachedImageIDs) {
                        cachedImageIDs.remove(imageID);
                    }

                    if (cancelSetup) return;

                    // Should only be the image that might have been loaded in loadAnyImageFromDisk()
                    if (textureCache.containsKey(imageID)) return;

                    Path imageFile = getCachedImagePath(imageID);

                    try {
                        if (Files.exists(getCachedImagePath(imageID))) {
                            // Read from disk
                            readAndCacheImageFromStream(
                                    imageID,
                                    new BufferedInputStream(Files.newInputStream(imageFile), 1024 * 1024),
                                    false);
                        } else {
                            if (OFFLINE_MODE) return;

                            readAndCacheImageFromStream(
                                    imageID,
                                    new ByteArrayInputStream(client.fetchImage(imageID)),
                                    true);
                        }
                    } catch (IOException e) {
                        BetterLoadingScreen.log.error("Error while loading imgur image", e);
                        return;
                    }

                    synchronized (textureLocationConsumer) {
                        textureLocationConsumer.accept(new ResourceLocation(IMGUR_CACHE_DIR, imageID));
                    }
                };

                if (OFFLINE_MODE) {
                    cachedImageIDs.stream().parallel().forEach(imageHandler);
                } else {
                    client.fetchGalleryImageIDs(galleryId, true).stream().parallel().forEach(imageHandler);
                }
            } catch (Exception e) {
                BetterLoadingScreen.log.error("Error while fetching imgur gallery", e);
            }
        }).thenRunAsync(() -> {
            if (OFFLINE_MODE) return;

            // Delete cached images that are no longer in the gallery
            try {
                for (String id : cachedImageIDs) {
                    Files.deleteIfExists(getCachedImagePath(id));
                }
            } catch (IOException e) {
                BetterLoadingScreen.log.error("Error while deleting unused cached imgur images", e);
            }
        });
    }

    private void loadAnyImageFromDisk(List<String> cachedImageIDs, Consumer<ResourceLocation> textureLocationConsumer) {
        if (cachedImageIDs.isEmpty()) return;

        String imageID = cachedImageIDs.get(ThreadLocalRandom.current().nextInt(cachedImageIDs.size()));
        try {
            readAndCacheImageFromDisk(imageID);
        } catch (IOException e) {
            BetterLoadingScreen.log.error("Error while loading first cached imgur image", e);
            return;
        }

        synchronized (textureLocationConsumer) {
            textureLocationConsumer.accept(new ResourceLocation(IMGUR_CACHE_DIR, imageID));
        }
    }

    private void readAndCacheImageFromStream(String imageID, InputStream imageStream, boolean saveToDisk)
            throws IOException {
        BufferedImage image = ImageIO.read(imageStream);
        textureCache.put(imageID, new LateInitDynamicTexture(image, image.getWidth(), image.getHeight()));

        if (saveToDisk && Files.notExists(getCachedImagePath(imageID))) writeImageToCache(imageID, image);
    }

    private void readAndCacheImageFromDisk(String imageID) throws IOException {
        readAndCacheImageFromStream(
                imageID,
                new BufferedInputStream(Files.newInputStream(getCachedImagePath(imageID)), 1024 * 1024),
                false);
    }

    private void writeImageToCache(String imageID, BufferedImage image) throws IOException {
        ImageIO.write(
                image,
                "png",
                new BufferedOutputStream(Files.newOutputStream(getCachedImagePath(imageID)), 1024 * 1024));
    }

    private static Path getCachedImagePath(String imageID) {
        return Paths.get(IMGUR_CACHE_DIR).resolve(imageID + ".png");
    }

    private List<String> getCachedImageIDs() {
        try (Stream<Path> cacheFolderStream = Files.list(Paths.get(IMGUR_CACHE_DIR))) {
            return cacheFolderStream.map(path -> path.getFileName().toString().replace(".png", ""))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            BetterLoadingScreen.log.error("Error while iterating imgur cache folder", e);
            return null;
        }
    }
}
