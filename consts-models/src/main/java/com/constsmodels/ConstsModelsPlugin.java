package com.constsmodels;

import com.google.inject.Provides;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Model;
import net.runelite.api.Player;
import net.runelite.api.Renderable;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.ImageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * consts-Models: export any model in the game, or a whole area, to look at or to use elsewhere. Right-click anything
 * and pick Export Model, or use the sidebar to capture yourself or the area around you. Each capture opens in a
 * viewer to turn and inspect, and saves as OBJ or PLY for 3D software, into a folder you choose. Built on the Model
 * Exporter plugin by Bram91.
 *
 * <p>Its files go through RuneLite's {@link Filepath}, as the Plugin Hub asks. Captures are kept in the plugin's own
 * folder, .runelite/plugin-data/consts-models, and models and pictures are saved there too unless a folder is chosen
 * for the session. The first time, RuneLite moves the folder used before 1.1, .runelite/consts-models, there.
 */
@PluginDescriptor(
    name = "consts-Models",
    description = "Export any model or the area around you: right-click Export Model, turn it in a viewer, save it as OBJ or PLY",
    tags = {"model", "models", "export", "exporter", "3d", "obj", "ply", "blender", "diorama", "scene"},
    // The Plugin Hub's name for it; and the folder it used in .runelite before 1.1, which RuneLite moves into
    // plugin-data
    internalName = "consts-models",
    legacyDataDirectory = "consts-models"
)
public class ConstsModelsPlugin extends Plugin
{
    private static final Logger log = LoggerFactory.getLogger(ConstsModelsPlugin.class);

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private EventBus eventBus;

    @Inject
    private ChatMessageManager chatMessageManager;

    @Inject
    private ScheduledExecutorService executor;

    @Inject
    private ConstsModelsConfig config;

    // The plugin's own folder, made at startup, and every capture kept in it to open again in any session; both null
    // if it couldn't be made, and folderProblem says why. This session's meshes stay in memory too.
    private volatile Filepath home;
    private volatile ModelLibrary library;
    private volatile String folderProblem = "its folder isn't ready yet";
    private final Map<String, Mesh> sessionMeshes = new ConcurrentHashMap<>();

    // A folder chosen in the sidebar, saved to instead until RuneLite closes: RuneLite hands one over for the session
    // only, and nothing turns a path kept in the settings back into one
    private volatile Filepath chosen;

    private TextureColors textures;
    private ExportMenu exportMenu;
    private ConstsModelsPanel panel;
    private NavigationButton navButton;

    private final ViewerActions viewerActions = new ViewerActions()
    {
        @Override
        public void save(Mesh mesh, ExportFormat format)
        {
            ConstsModelsPlugin.this.save(mesh, format);
        }

        @Override
        public void saveImage(Mesh mesh, BufferedImage image)
        {
            ConstsModelsPlugin.this.saveImage(mesh, image);
        }

        @Override
        public void copyPath()
        {
            ConstsModelsPlugin.this.copyPath();
        }

        @Override
        public ExportFormat defaultFormat()
        {
            return config.format();
        }

        @Override
        public int background()
        {
            return config.background();
        }

        @Override
        public void setBackground(int rgb)
        {
            config.background(rgb);
        }
    };

    @Provides
    ConstsModelsConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(ConstsModelsConfig.class);
    }

    @Override
    protected void startUp() throws Exception
    {
        textures = new TextureColors(client);

        exportMenu = injector.getInstance(ExportMenu.class);
        eventBus.register(exportMenu);
        exportMenu.startUp();

        panel = new ConstsModelsPanel(this, config);
        BufferedImage icon = ImageUtil.loadImageResource(getClass(), "panel_icon.png");
        navButton = NavigationButton.builder()
            .tooltip("consts-Models")
            .icon(icon)
            .priority(8)
            .panel(panel)
            .build();
        clientToolbar.addNavigation(navButton);

        // The plugin's folder, moved from where it was before 1.1 the first time, then the captures kept from other
        // sessions, for the All models tab. Off the Swing thread, and before any capture is kept or saved: the
        // executor runs one task at a time.
        executor.execute(() ->
        {
            try
            {
                Filepath folder = getPluginDirectory();
                folder.createDirectories();
                library = new ModelLibrary(folder.joinSegment("captures"));
                home = folder;
            }
            catch (IOException | RuntimeException e)
            {
                log.warn("Couldn't make the plugin's folder", e);
                folderProblem = "couldn't make its folder in .runelite/plugin-data (" + e.getMessage() + ")";
                chat("Captures can't be kept or saved: " + folderProblem + ".");
                return;
            }
            List<ModelLibrary.Capture> kept = library.list();
            SwingUtilities.invokeLater(() ->
            {
                panel.refresh();
                panel.setLibrary(kept);
            });
        });
    }

    @Override
    protected void shutDown() throws Exception
    {
        exportMenu.shutDown();
        eventBus.unregister(exportMenu);
        clientToolbar.removeNavigation(navButton);
        sessionMeshes.clear();
        SwingUtilities.invokeLater(ModelViewer::closeAll);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        // The sidebar shows the settings and the area's size, so it follows them while open
        if (ConstsModelsConfig.GROUP.equals(event.getGroup()))
        {
            SwingUtilities.invokeLater(panel::refresh);
        }
    }

    /** A model as a mesh, named for what it is ("NPC Goblin"). Call it on the client thread. */
    Mesh capture(Renderable renderable, String name)
    {
        return ModelCapture.capture(ModelCapture.modelOf(renderable), name, textures);
    }

    /** A model loaded from the game's cache, like an item's, as a mesh. Call it on the client thread. */
    Mesh captureModel(Model model, String name)
    {
        return ModelCapture.capture(model, name, textures);
    }

    /** Opens a capture in the viewer and lists it in the sidebar. Call it from any thread. */
    void show(Mesh mesh)
    {
        if (mesh.isEmpty())
        {
            chat("There was no model to export.");
            return;
        }
        long time = System.currentTimeMillis();
        SwingUtilities.invokeLater(() -> ModelViewer.open(mesh, viewerActions));
        executor.execute(() ->
        {
            try
            {
                ModelLibrary.Capture capture = library().keep(mesh, time);
                sessionMeshes.put(capture.id, mesh);
                SwingUtilities.invokeLater(() -> panel.added(capture));
            }
            catch (IOException | RuntimeException e)
            {
                log.warn("Couldn't keep {}", mesh.name, e);
                chat("Couldn't keep " + ConstsModelsPanel.displayName(mesh.name) + " in the sidebar: " + e.getMessage());
            }
        });
    }

    /** Opens a capture from the sidebar in a new viewer. Call it on the Swing thread. */
    void open(ModelLibrary.Capture capture)
    {
        Mesh kept = sessionMeshes.get(capture.id);
        if (kept != null)
        {
            ModelViewer.open(kept, viewerActions);
            return;
        }
        executor.execute(() ->
        {
            try
            {
                Mesh mesh = library().load(capture);
                SwingUtilities.invokeLater(() -> ModelViewer.open(mesh, viewerActions));
            }
            catch (IOException | RuntimeException e)
            {
                log.warn("Couldn't open {}", capture.meshFile, e);
                chat("Couldn't open " + ConstsModelsPanel.displayName(capture.name) + ": " + e.getMessage());
            }
        });
    }

    /** Deletes a capture for good, from its trash can in the sidebar. Files saved from the viewer stay. */
    void delete(ModelLibrary.Capture capture)
    {
        executor.execute(() ->
        {
            if (library != null && library.delete(capture))
            {
                sessionMeshes.remove(capture.id);
                SwingUtilities.invokeLater(() -> panel.removed(capture));
            }
            else
            {
                chat("Couldn't delete " + ConstsModelsPanel.displayName(capture.name) + ".");
            }
        });
    }

    ViewerActions viewerActions()
    {
        return viewerActions;
    }

    /** Your own player, from the sidebar. */
    void captureMe()
    {
        clientThread.invoke(() ->
        {
            Player me = client.getLocalPlayer();
            if (client.getGameState() != GameState.LOGGED_IN || me == null)
            {
                chat("Log in to export yourself.");
                return;
            }
            show(capture(me, "Player " + me.getName()));
        });
    }

    /** Everything around you, out to the configured radius, from the sidebar. */
    void captureArea()
    {
        clientThread.invoke(() ->
        {
            if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
            {
                chat("Log in to capture the area around you.");
                return;
            }
            show(SceneCapture.capture(client, config.captureRadius(), textures));
        });
    }

    /** The captures kept, or why there are none. Call it on the executor. */
    private ModelLibrary library() throws IOException
    {
        ModelLibrary kept = library;
        if (kept == null)
        {
            throw new IOException(folderProblem);
        }
        return kept;
    }

    /** Where files are saved: the folder chosen in the sidebar this session, else the plugin's own; null if neither. */
    Filepath outputFolder()
    {
        Filepath folder = chosen;
        return folder != null ? folder : home;
    }

    /** A folder chosen in the sidebar: files are saved there until RuneLite closes. */
    void setOutputFolder(Filepath folder)
    {
        chosen = folder;
    }

    /** The folder to save in, made again if it's gone. Call it on the executor. */
    private Filepath saveFolder() throws IOException
    {
        Filepath folder = outputFolder();
        if (folder == null)
        {
            throw new IOException(folderProblem);
        }
        folder.createDirectories();
        return folder;
    }

    /**
     * Puts the folder files are saved in on the clipboard, and says where to paste it: the Plugin Hub lets no plugin
     * open the computer's file browser.
     */
    void copyPath()
    {
        Filepath folder = outputFolder();
        if (folder == null)
        {
            chat("There's no folder to copy: " + folderProblem + ".");
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(folder.toString()), null);
        chat("Copied " + folder + ". Paste it in Finder's Go to Folder (Cmd+Shift+G) or File Explorer's address bar.");
    }

    void save(Mesh mesh, ExportFormat format)
    {
        executor.execute(() ->
        {
            try
            {
                Filepath folder = saveFolder();
                Filepath file = MeshExporter.export(mesh, format, folder);
                chat("Saved " + file.getFileName() + " in " + folder + ".");
            }
            catch (IOException | RuntimeException e)
            {
                log.warn("Couldn't save {} as {}", mesh.name, format, e);
                chat("Couldn't save " + mesh.name + ": " + e.getMessage());
            }
        });
    }

    void saveImage(Mesh mesh, BufferedImage image)
    {
        executor.execute(() ->
        {
            try
            {
                Filepath folder = saveFolder();
                Filepath file = folder.joinSegment(fileName(mesh.name, new Date()) + ".png");
                try (OutputStream out = file.openOutputStream())
                {
                    ImageIO.write(image, "png", out);
                }
                chat("Saved " + file.getFileName() + " in " + folder + ".");
            }
            catch (IOException | RuntimeException e)
            {
                log.warn("Couldn't save a picture of {}", mesh.name, e);
                chat("Couldn't save a picture of " + mesh.name + ": " + e.getMessage());
            }
        });
    }

    /** A file name for a capture: its name with anything a file system dislikes taken out, then the time. */
    static String fileName(String name, Date when)
    {
        String safe = name == null ? "" : name.replaceAll("<[^>]*>", "").replaceAll("[^A-Za-z0-9 ()_-]", "").trim();
        if (safe.isEmpty())
        {
            safe = "Model";
        }
        return safe + " " + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(when);
    }

    void chat(String message)
    {
        String text = new ChatMessageBuilder()
            .append(ChatColorType.HIGHLIGHT)
            .append("consts-Models: ")
            .append(ChatColorType.NORMAL)
            .append(message)
            .build();
        chatMessageManager.queue(QueuedMessage.builder()
            .type(ChatMessageType.CONSOLE)
            .runeLiteFormattedMessage(text)
            .build());
    }
}
