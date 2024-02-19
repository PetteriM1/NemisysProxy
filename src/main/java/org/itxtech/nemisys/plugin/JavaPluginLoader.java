package org.itxtech.nemisys.plugin;

import org.itxtech.nemisys.Server;
import org.itxtech.nemisys.event.plugin.PluginDisableEvent;
import org.itxtech.nemisys.event.plugin.PluginEnableEvent;
import org.itxtech.nemisys.utils.PluginException;
import org.itxtech.nemisys.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/**
 * Created by Nukkit Team.
 */
public class JavaPluginLoader implements PluginLoader {

    private final Server server;

    private final Map<String, Class> classes = new HashMap<>();
    private final Map<String, PluginClassLoader> classLoaders = new HashMap<>();

    public JavaPluginLoader(Server server) {
        this.server = server;
    }

    @Override
    public Plugin loadPlugin(File file) throws Exception {
        PluginDescription description = this.getPluginDescription(file);
        if (description != null) {
            this.server.getLogger().info(this.server.getLanguage().translateString("Loading {%0}", description.getFullName()));
            File dataFolder = new File(file.getParentFile(), description.getName());
            if (dataFolder.exists() && !dataFolder.isDirectory()) {
                throw new IllegalStateException("Projected dataFolder '" + dataFolder.toString() + "' for " + description.getName() + " exists and is not a directory");
            }

            String className = description.getMain();
            PluginClassLoader classLoader = new PluginClassLoader(this, this.getClass().getClassLoader(), file);
            this.classLoaders.put(description.getName(), classLoader);
            PluginBase plugin;
            try {
                Class javaClass = classLoader.loadClass(className);

                try {
                    Class<? extends PluginBase> pluginClass = javaClass.asSubclass(PluginBase.class);

                    plugin = pluginClass.newInstance();
                    this.initPlugin(plugin, description, dataFolder, file);

                    return plugin;
                } catch (ClassCastException e) {
                    throw new PluginException("main class `" + description.getMain() + "' does not extend PluginBase");
                } catch (InstantiationException | IllegalAccessException e) {
                    Server.getInstance().getLogger().logException(e);
                }

            } catch (ClassNotFoundException e) {
                throw new PluginException("Couldn't load plugin " + description.getName() + ": main class not found");
            }
        }

        return null;
    }

    @Override
    public Plugin loadPlugin(String filename) throws Exception {
        return this.loadPlugin(new File(filename));
    }

    @Override
    public PluginDescription getPluginDescription(File file) {
        try {
            JarFile jar = new JarFile(file);
            JarEntry entry = jar.getJarEntry("nemisys.yml");
            if (entry == null) {
                entry = jar.getJarEntry("plugin.yml");
                if (entry == null) {
                    return null;
                }
            }
            InputStream stream = jar.getInputStream(entry);
            return new PluginDescription(Utils.readFile(stream));
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public PluginDescription getPluginDescription(String filename) {
        return this.getPluginDescription(new File(filename));
    }

    @Override
    public Pattern[] getPluginFilters() {
        return new Pattern[]{Pattern.compile("^.+\\.jar$")};
    }

    private void initPlugin(PluginBase plugin, PluginDescription description, File dataFolder, File file) {
        plugin.init(this, this.server, description, dataFolder, file);
        plugin.onLoad();
    }

    @Override
    public void enablePlugin(Plugin plugin) {
        if (plugin instanceof PluginBase && !plugin.isEnabled()) {
            this.server.getLogger().info(this.server.getLanguage().translateString("Enabling {%0}", plugin.getDescription().getFullName()));

            ((PluginBase) plugin).setEnabled(true);

            this.server.getPluginManager().callEvent(new PluginEnableEvent(plugin));
        }
    }

    @Override
    public void disablePlugin(Plugin plugin) {
        if (plugin instanceof PluginBase && plugin.isEnabled()) {
            this.server.getLogger().info(this.server.getLanguage().translateString("Disabling {%0}", plugin.getDescription().getFullName()));

            this.server.getPluginManager().callEvent(new PluginDisableEvent(plugin));

            ((PluginBase) plugin).setEnabled(false);
        }
    }

    Class<?> getClassByName(final String name) {
        Class<?> cachedClass = classes.get(name);

        if (cachedClass != null) {
            return cachedClass;
        } else {
            for (PluginClassLoader loader : this.classLoaders.values()) {

                try {
                    cachedClass = loader.findClass(name, false);
                } catch (ClassNotFoundException ignored) {}
                if (cachedClass != null) {
                    return cachedClass;
                }
            }
        }
        return null;
    }

    void setClass(final String name, final Class<?> clazz) {
        if (!classes.containsKey(name)) {
            classes.put(name, clazz);
        }
    }
}
