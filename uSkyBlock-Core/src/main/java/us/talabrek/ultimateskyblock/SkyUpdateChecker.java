package us.talabrek.ultimateskyblock;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import us.talabrek.ultimateskyblock.api.plugin.UpdateChecker;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SkyUpdateChecker implements UpdateChecker {

    private final Logger logger;
    private final PluginConfig config;
    private final uSkyBlock plugin;

    private String latestVersion;

    private final Gson gson = new Gson();

    @Inject
    public SkyUpdateChecker(
        @NotNull uSkyBlock plugin,
        @NotNull PluginConfig config,
        @NotNull Logger logger
    ) {
        this.plugin = plugin;
        this.config = config;
        this.logger = logger;
    }

    /**
     * Triggers an update of the latest version info from the uSkyBlock website, and will log an INFO message if
     * an update is available.
     */
    public void checkForUpdates() {
        URI uri = URL_RELEASE;

        if (config.getYamlConfig().getString("plugin-updates.branch", "RELEASE").equalsIgnoreCase("STAGING")) {
            uri = URL_STAGING;
        }

        fetchLatestVersion(uri).thenAccept(version -> {
            latestVersion = version;
            if (latestVersion == null) {
                logger.info("Failed to check for new uSkyBlock versions.");
            }

            if (isUpdateAvailable()) {
                logger.info("There is a new version of uSkyBlock available: " + getLatestVersion());
                logger.info("Visit https://www.uskyblock.ovh/get to download.");
            }
        });
    }

    public boolean isUpdateAvailable() {
        if (latestVersion != null) {
            return isNewerVersion(getCurrentVersion(), getLatestVersion());
        }
        return false;
    }

    public @Nullable String getLatestVersion() {
        return latestVersion;
    }

    public @NotNull String getCurrentVersion() {
        return plugin.getDescription().getVersion();
    }

    public CompletableFuture<String> fetchLatestVersion(URI uri) {
        CompletableFuture<String> future = new CompletableFuture<>();
        future.completeAsync(() -> {
            String userAgent = "uSkyBlock-Plugin/v" + getCurrentVersion() + " (www.uskyblock.ovh)";
            try (var httpclient = HttpClients.custom().setUserAgent(userAgent).build()) {

                int CONNECTION_TIMEOUT_MS = 10 * 1000; // Timeout in millis.
                RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectionRequestTimeout(CONNECTION_TIMEOUT_MS)
                    .setConnectTimeout(CONNECTION_TIMEOUT_MS)
                    .setSocketTimeout(CONNECTION_TIMEOUT_MS)
                    .build();

                HttpGet request = new HttpGet(uri);
                request.setConfig(requestConfig);
                HttpResponse response = httpclient.execute(request);
                HttpEntity entity = response.getEntity();

                int status = response.getStatusLine().getStatusCode();
                if (status < 200 || status >= 300) {
                    return null;
                }

                if (entity != null) {
                    JsonObject obj = gson.fromJson(EntityUtils.toString(entity), JsonObject.class);
                    if (obj.has("version")) {
                        return obj.get("version").getAsString();
                    }
                }
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Exception while trying to fetch latest plugin version.", ex);
            }

            return null;
        });

        return future;
    }

    public boolean isNewerVersion(String currentVersion, String newVersion) {
        ComparableVersion current = new ComparableVersion(currentVersion);
        ComparableVersion target = new ComparableVersion(newVersion);
        return target.compareTo(current) > 0;
    }
}
