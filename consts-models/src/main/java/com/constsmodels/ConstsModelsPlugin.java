package com.constsmodels;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
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
import net.runelite.client.RuneLite;
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
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * consts-Models: export any model in the game, or a whole area, to look at or to 3D print. Right-click anything and
 * pick Export Model, or use the sidebar to capture yourself or the area around you. Each capture opens in a viewer
 * to turn and inspect, and saves as STL or 3MF for a printer or OBJ or PLY for 3D software, into a folder you choose.
 * Built on the Model Exporter plugin by Bram91.
 */
@PluginDescriptor(
    name = "consts-Models",
    description = "Export any model or the area around you to look at or 3D print: right-click Export Model, turn it in a viewer, save STL, 3MF, OBJ or PLY",
    tags = {"model", "models", "export", "exporter", "3d", "print", "printing", "stl", "3mf", "obj", "diorama", "scene"}
)
public class ConstsModelsPlugin extends Plugin
{
    private static final Logger log = LoggerFactory.getLogger(ConstsModelsPlugin.class);

    /** Where files go until a folder is chosen. */
    static final File DEFAULT_FOLDER = new File(RuneLite.RUNELITE_DIR, "consts-models");

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

    // Every capture, kept to open again in any session; this session's meshes stay in memory too
    private final ModelLibrary library = new ModelLibrary(new File(DEFAULT_FOLDER, "captures"));
    private final Map<String, Mesh> sessionMeshes = new ConcurrentHashMap<>();

    private TextureColors textures;
    private ExportMenu exportMenu;
    private ConstsModelsPanel panel;
    private NavigationButton navButton;

    private final ViewerActions viewerActions = new ViewerActions()
    {
        @Override
        public void save(Mesh mesh, ExportFormat format, PrintColors colors, PrintSettings settings)
        {
            ConstsModelsPlugin.this.save(mesh, format, colors, settings);
        }

        @Override
        public void savePrint(Printable printable, ExportFormat format, PrintColors colors)
        {
            ConstsModelsPlugin.this.savePrint(printable, format, colors);
        }

        @Override
        public void saveImage(Mesh mesh, BufferedImage image)
        {
            ConstsModelsPlugin.this.saveImage(mesh, image);
        }

        @Override
        public void openFolder()
        {
            ConstsModelsPlugin.this.openFolder();
        }

        @Override
        public ExportFormat defaultFormat()
        {
            return config.format();
        }

        @Override
        public PrintSettings printSettings()
        {
            return ConstsModelsPlugin.this.printSettings();
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

        // The captures kept from other sessions, for the All models tab
        executor.execute(() ->
        {
            List<ModelLibrary.Capture> kept = library.list();
            SwingUtilities.invokeLater(() -> panel.setLibrary(kept));
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
        // The sidebar shows the area's size and the folder, so it follows the settings while open
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
                ModelLibrary.Capture capture = library.keep(mesh, time);
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
                Mesh mesh = library.load(capture);
                SwingUtilities.invokeLater(() -> ModelViewer.open(mesh, viewerActions));
            }
            catch (IOException | RuntimeException e)
            {
                log.warn("Couldn't open {}", capture.meshFile, e);
                chat("Couldn't open " + ConstsModelsPanel.displayName(capture.name) + ": " + e.getMessage());
            }
        });
    }

    /** Deletes a capture for good, once the sidebar has asked. Files saved from the viewer stay. */
    void delete(ModelLibrary.Capture capture)
    {
        executor.execute(() ->
        {
            if (library.delete(capture))
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

    PrintSettings printSettings()
    {
        return new PrintSettings(config.printSizeMm(), config.addBase(), config.maxColors());
    }

    /** Where files are saved: the folder chosen in the sidebar, or the default one. */
    File outputFolder()
    {
        String chosen = config.outputFolder();
        return chosen == null || chosen.isBlank() ? DEFAULT_FOLDER : new File(chosen);
    }

    void setOutputFolder(File folder)
    {
        config.outputFolder(folder == null ? "" : folder.getAbsolutePath());
    }

    void openFolder()
    {
        File folder = outputFolder();
        if (!folder.isDirectory() && !folder.mkdirs())
        {
            chat("Couldn't make the folder " + folder + ".");
            return;
        }
        LinkBrowser.open(folder.getAbsolutePath());
    }

    void save(Mesh mesh, ExportFormat format, PrintColors colors, PrintSettings viewer)
    {
        File folder = outputFolder();
        PrintSettings settings = viewer != null ? viewer : printSettings();
        executor.execute(() ->
        {
            try
            {
                if (!folder.isDirectory() && !folder.mkdirs())
                {
                    throw new IOException("couldn't make " + folder);
                }
                File file = MeshExporter.export(mesh, format, folder, settings, colors);
                chat("Saved " + file.getName() + " in " + folder + ".");
            }
            catch (IOException | RuntimeException e)
            {
                log.warn("Couldn't save {} as {}", mesh.name, format, e);
                chat("Couldn't save " + mesh.name + ": " + e.getMessage());
            }
        });
    }

    void savePrint(Printable printable, ExportFormat format, PrintColors colors)
    {
        File folder = outputFolder();
        String name = printable.model.name;
        executor.execute(() ->
        {
            try
            {
                if (!folder.isDirectory() && !folder.mkdirs())
                {
                    throw new IOException("couldn't make " + folder);
                }
                File file = MeshExporter.export(printable, colors, format, folder);
                chat("Saved " + file.getName() + " in " + folder + ".");
            }
            catch (IOException | RuntimeException e)
            {
                log.warn("Couldn't save the print of {} as {}", name, format, e);
                chat("Couldn't save " + name + ": " + e.getMessage());
            }
        });
    }

    void saveImage(Mesh mesh, BufferedImage image)
    {
        File folder = outputFolder();
        executor.execute(() ->
        {
            try
            {
                if (!folder.isDirectory() && !folder.mkdirs())
                {
                    throw new IOException("couldn't make " + folder);
                }
                File file = new File(folder, fileName(mesh.name, new Date()) + ".png");
                ImageIO.write(image, "png", file);
                chat("Saved " + file.getName() + " in " + folder + ".");
            }
            catch (IOException e)
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
