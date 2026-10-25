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
import net.runelite.api.Actor;
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
 * viewer to turn and inspect, and saves as OBJ, PLY or GLB (a player's or NPC's animations too) for 3D software, into
 * a folder you choose. Built on the Model Exporter plugin by Bram91.
 *
 * <p>Its files go through RuneLite's {@link Filepath}, as the Plugin Hub asks. Captures are kept in the plugin's own
 * folder, .runelite/plugin-data/consts-models, and models and pictures are saved there too unless a folder is chosen
 * for the session. The first time, RuneLite moves the folder used before 1.1, .runelite/consts-models, there.
 */
@PluginDescriptor(
    name = "consts-Models",
    description = "Export any model or the area around you: right-click Export Model, turn it in a viewer, "
        + "save it as OBJ, PLY or GLB, animations and all",
    tags = {"model", "models", "export", "exporter", "3d", "obj", "ply", "glb", "gltf", "animation", "blender",
        "diorama", "scene"},
    // Its name on the Plugin Hub, and its folder in .runelite before 1.1, which RuneLite moves into plugin-data
    internalName = "consts-models",
    legacyDataDirectory = "consts-models"
)
public class ConstsModelsPlugin extends Plugin
{
    private static final Logger log = LoggerFactory.getLogger(ConstsModelsPlugin.class);

    // What Java's errors mean when their message is only the file, or nothing. By class name, since the Hub's rules
    // keep most of those file error classes out of a plugin
    private static final Map<String, String> MEANINGS = Map.of(
        "AccessDeniedException", "access denied",
        "NoSuchFileException", "not found",
        "FileAlreadyExistsException", "already exists",
        "EOFException", "it ends too soon");

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

    // The plugin's own folder, where every capture is kept to open again in any session. It's made at startup; if
    // that fails it's null, and the next capture, or save with no folder chosen, tries again. This session's meshes
    // stay in memory too.
    private volatile Filepath pluginFolder;
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

        // The plugin's folder, with the captures kept from other sessions for the All models tab. Off the Swing thread,
        // and before any capture is kept or saved: the executor runs one task at a time.
        executor.execute(() ->
        {
            // Afresh at each start, so this sidebar gets its captures even if a task left from before set it again
            pluginFolder = null;
            try
            {
                pluginFolder();
            }
            catch (IOException | RuntimeException e)
            {
                log.warn("Couldn't make the plugin's folder", e);
                chat("Captures can't be kept yet: " + reason(e) + ". Your next capture tries again.");
            }
        });
    }

    @Override
    protected void shutDown() throws Exception
    {
        exportMenu.shutDown();
        eventBus.unregister(exportMenu);
        clientToolbar.removeNavigation(navButton);
        sessionMeshes.clear();
        // Made again at the next start, or after it if that fails: until then there's none to show or use
        pluginFolder = null;
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

    /**
     * A model as a mesh, named for what it is ("NPC Goblin"). A player, NPC or pet comes with its pose animations
     * (idle, turns, walks and run) unless it's busy with an action. Call it on the client thread.
     */
    Mesh capture(Renderable renderable, String name)
    {
        Mesh mesh = ModelCapture.capture(ModelCapture.modelOf(renderable), name, textures);
        if (renderable instanceof Actor && !mesh.isEmpty())
        {
            mesh = mesh.withClips(PoseCapture.capture(client, (Actor) renderable, mesh.vertexCount()));
        }
        return mesh;
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
                chat("Couldn't keep " + ConstsModelsPanel.displayName(mesh.name) + " in the sidebar: " + reason(e));
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
                Mesh mesh = capture.load();
                SwingUtilities.invokeLater(() -> ModelViewer.open(mesh, viewerActions));
            }
            catch (IOException | RuntimeException e)
            {
                log.warn("Couldn't open {}", capture.meshFile, e);
                chat("Couldn't open " + ConstsModelsPanel.displayName(capture.name) + ": " + reason(e));
            }
        });
    }

    /** Deletes a capture for good, from its trash can in the sidebar. Files saved from the viewer stay. */
    void delete(ModelLibrary.Capture capture)
    {
        executor.execute(() ->
        {
            if (capture.delete())
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

    /**
     * The plugin's own folder. The first time, RuneLite moves the folder used before 1.1 there, and the captures kept
     * in it go to the sidebar. One that couldn't be made is tried again the next time, and the exception says why.
     * Call it on the executor.
     */
    private Filepath pluginFolder() throws IOException
    {
        Filepath folder = pluginFolder;
        if (folder != null)
        {
            return folder;
        }
        try
        {
            folder = getPluginDirectory();
            ModelLibrary.makeFolder(folder);
        }
        catch (IOException | RuntimeException e)
        {
            throw new IOException("couldn't make its folder in .runelite/plugin-data (" + reason(e) + ")", e);
        }
        pluginFolder = folder;
        List<ModelLibrary.Capture> kept = library().list();
        SwingUtilities.invokeLater(() ->
        {
            panel.refresh();
            panel.setLibrary(kept);
        });
        return folder;
    }

    /** The captures kept, in the plugin's folder. Call it on the executor. */
    private ModelLibrary library() throws IOException
    {
        return new ModelLibrary(pluginFolder().joinSegment("captures"));
    }

    /** Where files are saved: the folder chosen in the sidebar this session, else the plugin's own; null if neither. */
    Filepath outputFolder()
    {
        Filepath folder = chosen;
        return folder != null ? folder : pluginFolder;
    }

    /** A folder chosen in the sidebar: files are saved there until RuneLite closes. */
    void setOutputFolder(Filepath folder)
    {
        chosen = folder;
    }

    /** The folder to save in, made again if it's gone: the one chosen this session, else the plugin's own. */
    private Filepath saveFolder() throws IOException
    {
        Filepath folder = chosen;
        if (folder == null)
        {
            folder = pluginFolder();
        }
        ModelLibrary.makeFolder(folder);
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
            chat("There's no folder to copy yet. Choose one, or capture something to make the plugin's own.");
            return;
        }
        try
        {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(folder.toString()), null);
        }
        catch (IllegalStateException e)
        {
            // Another program holds the clipboard, as happens on Windows: the path is in chat to copy by hand
            chat("Couldn't copy the path, the clipboard is busy. The folder is " + folder + ".");
            return;
        }
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
                chat("Couldn't save " + mesh.name + ": " + reason(e));
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
                chat("Couldn't save a picture of " + mesh.name + ": " + reason(e));
            }
        });
    }

    /**
     * What went wrong, for chat: the error's message, and what it means when that's only the file, as Java's file
     * errors give it: "/x/Goblin.obj (access denied)". Every other message says why itself.
     */
    static String reason(Exception e)
    {
        String meaning = MEANINGS.get(e.getClass().getSimpleName());
        String message = e.getMessage();
        if (message == null || message.isEmpty())
        {
            return meaning != null ? meaning : e.getClass().getSimpleName();
        }
        return meaning != null ? message + " (" + meaning + ")" : message;
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
