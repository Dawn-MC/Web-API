package valandur.webapi;

import com.google.inject.Inject;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.yaml.YAMLConfigurationLoader;
import org.apache.commons.lang3.tuple.Triple;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.slf4j.Logger;
import org.spongepowered.api.asset.AssetManager;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.game.state.GameStoppedServerEvent;
import org.spongepowered.api.event.message.MessageChannelEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.text.Text;
import valandur.webapi.handlers.*;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

@Plugin(
        id = "webapi",
        name = "Web-API",
        url = "https://github.com/Valandur",
        description = "Access Minecraft through a Web API",
        version = "1.0-SNAPSHOT",
        authors = {
                "Valandur"
        }
)
public class WebAPI {

    private static WebAPI instance;
    public static WebAPI getInstance() {
        return WebAPI.instance;
    }

    private static List<Triple<Date, Player, Text>> chatMessages = new ArrayList<>();
    public static List<Triple<Date, Player, Text>> getChatMessages() {
        return WebAPI.chatMessages;
    }

    @Inject
    private Logger logger;
    public static Logger getLogger() {
        return WebAPI.instance.logger;
    }

    @Inject
    private static AssetManager assets;
    public static AssetManager getAssetManager() {
        return WebAPI.assets;
    }

    @Inject
    @DefaultConfig(sharedRoot = true)
    private ConfigurationLoader<CommentedConfigurationNode> configManager;
    private ConfigurationNode rootNode;

    private String serverHost;
    private int serverPort;
    private Server server;

    @Listener
    public void onPreInitialization(GamePreInitializationEvent event) {
        WebAPI.instance = this;

        try {
            rootNode = configManager.load();
            serverHost = rootNode.getNode("server", "host").getString("localhost");
            serverPort = rootNode.getNode("server", "port").getInt(8080);
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    @Listener
    public void onInitialization(GameInitializationEvent event) {
        logger.info("Starting Web Server...");

        //Log.setLog(new JettyLogger());

        String swaggerYaml = "";

        try {
            StringWriter sink = new StringWriter();
            URL url = this.getClass().getResource("/assets/webapi/swagger.yaml");
            YAMLConfigurationLoader loader = YAMLConfigurationLoader.builder().setURL(url).setSink(() -> new BufferedWriter(sink)).build();
            ConfigurationNode swaggerNode = loader.load();
            loader.save(swaggerNode);
            swaggerYaml = sink.toString().replaceFirst("<host>", serverHost + ":" + serverPort);
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            server = new Server();

            // HTTP connector
            ServerConnector http = new ServerConnector(server);
            http.setHost(serverHost);
            http.setPort(serverPort);
            http.setIdleTimeout(30000);
            server.addConnector(http);

            // Handlers
            List<ContextHandler> handlers = new ArrayList<ContextHandler>();

            handlers.add(newContext("/", new AssetHandler("redoc.html")));
            handlers.add(newContext("/api-docs", new AssetHandler(swaggerYaml, "application/json")));

            handlers.add(newContext("/info", new InfoHandler()));
            handlers.add(newContext("/cmd", new CmdHandler()));
            handlers.add(newContext("/players", new PlayerHandler()));
            handlers.add(newContext("/worlds", new WorldHandler()));
            handlers.add(newContext("/chat", new ChatHandler()));
            handlers.add(newContext("/plugins", new PluginHandler()));

            ContextHandlerCollection contextCollection = new ContextHandlerCollection();
            contextCollection.setHandlers(handlers.toArray(new Handler[handlers.size()]));

            server.setHandler(contextCollection);
            server.start();

        } catch (Exception e) {
            e.printStackTrace();
        }

        logger.info("Web server running on " + server.getURI());
    }

    private static ContextHandler newContext(String path, Handler handler) {
        ContextHandler context = new ContextHandler();
        context.setContextPath(path);
        context.setHandler(handler);
        return context;
    }

    @Listener
    public void onServerStart(GameStartedServerEvent event) {

    }

    @Listener
    public void onServerStop(GameStoppedServerEvent event) {
        if (server != null) {
            try {
                server.stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Listener
    public void onMessage(MessageChannelEvent.Chat event) {
        Optional<Player> player = event.getCause().first(Player.class);
        if (!player.isPresent()) return;

        WebAPI.chatMessages.add(Triple.of(new Date(), player.get(), event.getRawMessage()));
    }
}
