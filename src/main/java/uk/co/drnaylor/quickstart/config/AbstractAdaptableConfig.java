/*
 * This file is part of QuickStart Module Loader, licensed under the MIT License (MIT). See the LICENSE.txt file
 * at the root of this project for more details.
 */
package uk.co.drnaylor.quickstart.config;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.ConfigurationOptions;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.commented.SimpleCommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import ninja.leaping.configurate.objectmapping.Setting;
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable;
import ninja.leaping.configurate.transformation.ConfigurationTransformation;
import uk.co.drnaylor.quickstart.exceptions.IncorrectAdapterTypeException;
import uk.co.drnaylor.quickstart.exceptions.NoModuleException;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;

/**
 * A configuration manager that allows for {@link AbstractConfigAdapter} to be attached.
 *
 * @param <N> The type of {@link ConfigurationNode} that this configuration will handle.
 * @param <T> The type of {@link ConfigurationLoader} that this configuration will use.
 */
public class AbstractAdaptableConfig<N extends ConfigurationNode, T extends ConfigurationLoader<N>> {

    private Map<String, AbstractConfigAdapter<?>> moduleConfigAdapters = Maps.newHashMap();

    private final T loader;
    private N node;
    private final Supplier<ConfigurationNode> nodeCreator;
    private final Function<ConfigurationOptions, ConfigurationOptions> optionsTransformer;

    public AbstractAdaptableConfig(T loader) throws IOException {
        this(loader, loader::createEmptyNode, x -> x);
    }

    public AbstractAdaptableConfig(T loader, Supplier<ConfigurationNode> nodeCreator, Function<ConfigurationOptions, ConfigurationOptions> optionsTransformer) throws IOException {
        Preconditions.checkNotNull(loader);
        Preconditions.checkNotNull(nodeCreator);

        this.loader = loader;
        this.nodeCreator = nodeCreator;
        this.optionsTransformer = optionsTransformer;
        load();
    }

    @SuppressWarnings("unchecked")
    public final ConfigurationNode createNode() {
        return this.nodeCreator.get();
    }

    /**
     * (Re)loads the configuration file into the base node using the selected loader.
     *
     * @throws IOException if the file could not be loaded.
     */
    public void load() throws IOException {
        this.node = loader.load(optionsTransformer.apply(loader.getDefaultOptions()));
    }

    /**
     * Gets all the config adapters associated with this configuration.
     *
     * @return An {@link ImmutableMap} that contains all the adapters.
     */
    public final Map<String, ? extends AbstractConfigAdapter<?>> getAllConfigAdapters() {
        return ImmutableMap.copyOf(moduleConfigAdapters);
    }

    /**
     * Gets a currently attached {@link AbstractConfigAdapter}
     *
     * @param module The module to get the adapter for
     * @param adapterClass The {@link Class} of the adapter.
     * @param <R> The {@link Class} of the adapter.
     * @return The adapter.
     * @throws NoModuleException If the module has not had a config adapter attached to it.
     * @throws IncorrectAdapterTypeException If the specified {@link Class} is incorrect.
     */
    public final <R extends AbstractConfigAdapter<?>> R getConfigAdapterForModule(String module, Class<R> adapterClass) throws NoModuleException, IncorrectAdapterTypeException {
        if (!moduleConfigAdapters.containsKey(module.toLowerCase())) {
            throw new NoModuleException(module);
        }

        AbstractConfigAdapter<?> aca = moduleConfigAdapters.get(module);
        if (adapterClass.isInstance(aca)) {
            return adapterClass.cast(aca);
        }

        throw new IncorrectAdapterTypeException();
    }

    public final void attachConfigAdapter(String module, AbstractConfigAdapter<?> configAdapter) throws IOException {
        attachConfigAdapter(module, configAdapter, null);
    }

    /**
     * Attaches a {@link AbstractConfigAdapter} to this {@link AbstractAdaptableConfig}.
     *
     * @param module The name of the module that this configuration represents.
     * @param configAdapter The {@link AbstractConfigAdapter} to attach.
     * @param header The header for the module.
     * @throws IOException if the configuration defaults could not be saved.
     * @throws IllegalArgumentException if the module has already been attached to.
     * @throws IllegalStateException if the adapter has already been attached.
     */
    @SuppressWarnings("unchecked")
    public final void attachConfigAdapter(String module, AbstractConfigAdapter<?> configAdapter, @Nullable String header) throws IOException {
        if (moduleConfigAdapters.containsKey(module.toLowerCase())) {
            throw new IllegalArgumentException();
        }

        configAdapter.attachConfig(
                module.toLowerCase(),
                this,
                () -> nodeCreator.get().setValue(node.getNode(module.toLowerCase())),
                n -> node.getNode(module.toLowerCase()).setValue(n),
                nodeCreator,
                header);
        moduleConfigAdapters.put(module.toLowerCase(), configAdapter);
    }

    /**
     * Detaches a module's {@link AbstractConfigAdapter} from this {@link AbstractAdaptableConfig}.
     *
     * @param module The name of the module that this configuration should be detached from.
     * @return if the detachment was successful.
     */
    public final boolean detachConfigAdapter(String module) {
        return moduleConfigAdapters.remove(module.toLowerCase()) != null;
    }

    /**
     * Refreshes the configuration node.
     */
    public final void refreshConfigurationNode() {
        moduleConfigAdapters.values().forEach(x -> {
            try {
                x.refreshConfigurationNode();
            } catch (ObjectMappingException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Saves the configuration.
     *
     * @throws IOException if the configuration could not be saved.
     */
    public void save() throws IOException {
        save(false);
    }

    /**
     * Saves the configuration, optionally refreshing the config nodes with the latest fields.
     *
     * @param refresh {@code true} if so.
     * @throws IOException if the configuration could not be saved.
     */
    public void save(boolean refresh) throws IOException {
        if (refresh) {
            refreshConfigurationNode();
        }

        loader.save(node);
    }

    /**
     * Saves default values from the adapter to the config file.
     *
     * @param processNoMergeIfPresent If <code>true</code>, {@link NoMergeIfPresent} annotations will be honoured.
     *
     * @throws IOException Thrown if the configuration could not be saved.
     */
    public void saveAdapterDefaults(boolean processNoMergeIfPresent) throws IOException {
        CommentedConfigurationNode n = SimpleCommentedConfigurationNode.root();

        Stack<String> moduleStack = new Stack<>();
        List<Object[]> doNotMerge = Lists.newArrayList();
        moduleConfigAdapters.forEach((k, v) -> {

            // Configurate does something I wasn't expecting. If we set a single value with a key on a node, it seems
            // to be set as the root - which causes havoc! So, we get the parent if it exists, because that's the
            // actual null node we're interested in.
            ConfigurationNode cn = v.getDefaults();
            if (cn.getParent() != null) {
                cn = cn.getParent();
            }

            if (processNoMergeIfPresent) {
                if (v instanceof TypedAbstractConfigAdapter) {
                    Object o = ((TypedAbstractConfigAdapter) v).getDefaultObject();
                    getDoNotMerge(moduleStack, o.getClass(), doNotMerge);
                }

                if (!doNotMerge.isEmpty()) {
                    for (Object[] keys : doNotMerge) {
                        ConfigurationNode toCheck = node.getNode(k).getNode((Object[])keys);
                        if (!toCheck.isVirtual() && toCheck.getValue() != null) {
                            cn.getNode((Object[])keys).setValue(null);
                            cn.getNode((Object[])keys).getParent().removeChild(keys[keys.length - 1]);
                        }
                    }

                    doNotMerge.clear();
                }
            }

            n.getNode(k.toLowerCase()).setValue(cn);
        });

        node.mergeValuesFrom(n);

        // Now, we do transformations.
        moduleConfigAdapters.forEach((k, v) -> {
            ConfigurationNode nodeToTransform = node.getNode(k.toLowerCase());
            if (!nodeToTransform.isVirtual()) {
                v.manualTransform(nodeToTransform);
                List<AbstractConfigAdapter.Transformation> transformations = v.getTransformations();
                if (!transformations.isEmpty() && v.isAttached()) {
                    ConfigurationTransformation.Builder ctBuilder = ConfigurationTransformation.builder();
                    transformations.forEach(x -> ctBuilder.addAction(x.getObjectPath(), x.getAction()));
                    ctBuilder.build().apply(nodeToTransform);
                }

                node.getNode(k.toLowerCase()).setValue(nodeToTransform);
            }
        });

        save();
    }

    private void getDoNotMerge(Stack<String> keySoFar, Class<?> configSerialisable, List<Object[]> doNotMergeList) {
        for (Field field : configSerialisable.getDeclaredFields()) {
            field.setAccessible(true);
            if (field.isAnnotationPresent(Setting.class)) {
                String value = field.getAnnotation(Setting.class).value();
                if (value.equals("")) {
                    value = field.getName();
                }

                keySoFar.push(value);
                if (field.isAnnotationPresent(NoMergeIfPresent.class)) {
                    doNotMergeList.add(keySoFar.toArray(new String[keySoFar.size()]));
                } else if (field.getType().isAnnotationPresent(ConfigSerializable.class)) {
                    getDoNotMerge(keySoFar, field.getType(), doNotMergeList);
                }

                keySoFar.pop();
            }
        }
    }
}
