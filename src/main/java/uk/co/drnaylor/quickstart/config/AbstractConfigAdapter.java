/*
 * This file is part of QuickStart Module Loader, licensed under the MIT License (MIT). See the LICENSE.txt file
 * at the root of this project for more details.
 */
package uk.co.drnaylor.quickstart.config;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.ConfigurationOptions;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import ninja.leaping.configurate.transformation.TransformAction;
import uk.co.drnaylor.quickstart.annotations.DoNotSave;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.Nullable;

/**
 * This is the base of all configuration adapters that can be attached to {@link AbstractAdaptableConfig} files. It
 * essentially controls a subnode of the config file, as defined by the module when this is attached to the
 * {@link AbstractConfigAdapter}.
 *
 * <p>
 *      There are two states to this object: unattached and attached.
 * </p>
 * <ul>
 *     <li>
 *          The <strong>unattached</strong> state is where it hasn't been assigned to an {@link AbstractAdaptableConfig}
 *          yet, so no actual configuration manipulation can take place.
 *     </li>
 *     <li>
 *          The <strong>attached</strong> state is where this adapter <em>has</em> been assigned to an {@link AbstractAdaptableConfig}
 *          and can manipulate the config section it has been assigned.
 *     </li>
 * </ul>
 * <p>
 *     This state can be checked by calling {@link #isAttached()}. To attach, pass this object as an argument to the
 *     {@link AbstractAdaptableConfig#attachConfigAdapter(String, AbstractConfigAdapter)} method.
 * </p>
 *
 * @param <R> The class that represents the structure of the node. This can be a {@link ConfigurationNode} (see the {@link SimpleNodeConfigAdapter}).
 */
public abstract class AbstractConfigAdapter<R> {

    private AbstractAdaptableConfig<?, ?> attachedConfig = null;
    private Supplier<ConfigurationNode> nodeGetter = null;
    private Supplier<ConfigurationNode> nodeCreator = null;
    private Consumer<ConfigurationNode> nodeSaver = null;
    @Nullable private String module = null;
    @Nullable private String header = null;

    final void attachConfig(String module,
            AbstractAdaptableConfig<?, ?> adapter,
            Supplier<ConfigurationNode> nodeGetter,
            Consumer<ConfigurationNode> nodeSaver,
            Supplier<ConfigurationNode> nodeCreator,
            @Nullable String header) {

        Preconditions.checkState(attachedConfig == null);
        this.module = module;
        this.attachedConfig = adapter;
        this.nodeGetter = nodeGetter;
        this.nodeSaver = nodeSaver;
        this.nodeCreator = nodeCreator;
        this.header = header;

        onAttach(module, adapter);
    }

    final void detachConfig() {
        Preconditions.checkState(attachedConfig != null);
        onDetach(this.module, this.attachedConfig);

        this.module = null;
        this.attachedConfig = null;
        this.nodeGetter = null;
        this.nodeSaver = null;
        this.nodeCreator = null;
        this.header = null;
    }

    /**
     * Runs when the config has been attached.
     *
     * @param module The module it has been attached to.
     * @param adapter The {@link AbstractAdaptableConfig} that it has been attached to.
     */
    public void onAttach(String module, AbstractAdaptableConfig<?, ?> adapter) {}

    /**
     * Runs when the config has been detached.
     *
     * @param module The module it is about to be detached from.
     * @param adapter The {@link AbstractAdaptableConfig} that it is about to be detached from.
     */
    public void onDetach(String module, AbstractAdaptableConfig<?, ?> adapter) {}

    /**
     * Returns whether this adapter has been attached to a config file.
     *
     * @return <code>true</code> if attached.
     */
    public final boolean isAttached() {
        return attachedConfig != null;
    }

    /**
     * Gets the defaults for this {@link AbstractConfigAdapter}.
     *
     * @return A {@link ConfigurationNode} that represents the defaults to merge in.
     */
    public final ConfigurationNode getDefaults() {
        return generateDefaults(nodeGetter.get());
    }

    /**
     * Gets the {@link AbstractAdaptableConfig} that this adapter is attached to, if it has been attached.
     *
     * @return An {@link Optional}, which is empty if the adapter has not been attached yet.
     */
    public final Optional<AbstractAdaptableConfig<?, ?>> getConfig() {
        return Optional.ofNullable(attachedConfig);
    }

    /**
     * Gets the data that this adapter manages.
     *
     * @return An object of type {@link R}.
     * @see #convertFromConfigurateNode(ConfigurationNode)
     * @throws ObjectMappingException if the object could not be created.
     */
    public final R getNode() throws ObjectMappingException {
        Preconditions.checkState(attachedConfig != null, "You must attach this adapter before using it.");

        return convertFromConfigurateNode(nodeGetter.get());
    }

    /**
     * Saves the data that this adapter manages back to the config manager.
     *
     * @param data An object of type {@link R}.
     * @see #insertIntoConfigurateNode(ConfigurationNode, Object)
     * @throws ObjectMappingException if the object could not be saved.
     */
    public final void setNode(R data) throws ObjectMappingException {
        Preconditions.checkState(attachedConfig != null, "You must attach this adapter before using it.");

        ConfigurationNode node = insertIntoConfigurateNode(getNewNode(), data);
        if (this.header != null && node instanceof CommentedConfigurationNode) {
            CommentedConfigurationNode ccn = (CommentedConfigurationNode) node;
            ccn.setComment(this.header);
        }

        nodeSaver.accept(node);
    }

    /**
     * Updates the {@link ConfigurationNode} in memory with any changes that may occur due to a change in {@link R}.
     * @throws ObjectMappingException if the object could not be created and/or saved.
     */
    final void refreshConfigurationNode() throws ObjectMappingException {
        if (this.getClass().isAnnotationPresent(DoNotSave.class)) {
            if (this.header != null) {
                ConfigurationNode cn = nodeGetter.get();
                if (cn instanceof CommentedConfigurationNode) {
                    nodeSaver.accept(((CommentedConfigurationNode) cn).setComment(this.header));
                }
            }
        } else {
            setNode(getNode());
        }
    }

    /**
     * Convenience method that allows a new {@link ConfigurationNode} of the correct type to be produced.
     *
     * @return The node.
     */
    protected final ConfigurationNode getNewNode() {
        Preconditions.checkState(attachedConfig != null, "You must attach this adapter before using it.");
        return nodeCreator.get();
    }

    /**
     * Gets the module that this adapter has been assigned to, if it has been attached.
     *
     * @return An {@link Optional}, which is empty if the adapter has not been attached yet.
     */
    protected Optional<String> getAssignedModule() {
        return Optional.ofNullable(module);
    }

    /**
     * Manually transform a configuration node. This happens before {@link #getTransformations()}
     * is called.
     *
     * @param node The node to transform.
     */
    protected void manualTransform(ConfigurationNode node) {};

    /**
     * Gets the transformations that are required to this section.
     *
     * @return The transformations that are required.
     */
    protected List<Transformation> getTransformations() {
        return Lists.newArrayList();
    }

    /**
     * Provides the default set of data for this adapter.
     *
     * @param node An empty node to populate.
     * @return The populated node.
     */
    protected abstract ConfigurationNode generateDefaults(ConfigurationNode node);

    /**
     * Converts from {@link ConfigurationNode} to an object of type {@link R}.
     *
     * <p>
     *     The return value from this object represents the root of the module's config section - that is,
     *     is located at the node "module" in the config tree when saved.
     * </p>
     *
     * @param node The node to convert into the object of type {@link R}
     * @return The object of type {@link R}
     * @throws ObjectMappingException if the object could not be created.
     */
    protected abstract R convertFromConfigurateNode(ConfigurationNode node) throws ObjectMappingException;

    /**
     * Converts from an object of type {@link R} to a {@link ConfigurationNode} of the correct type.
     *
     * <p>
     *     The return value from this object will be the root of the module's config section - that is,
     *     will be located at the node "module" in the config tree when saved.
     * </p>
     *
     * @param data The object to convert into a config node.
     * @param newNode A new node with the adapter's {@link ConfigurationOptions}, created by {@link #getNewNode()}
     * @return The {@link ConfigurationNode}
     * @throws ObjectMappingException if the object could not be created.
     */
    protected abstract ConfigurationNode insertIntoConfigurateNode(ConfigurationNode newNode, R data) throws ObjectMappingException;

    /**
     * Represents a transformation to be made to the configuration BEFORE it is completely loaded.
     */
    protected static final class Transformation {
        private final Object[] objectPath;
        private final TransformAction action;

        /**
         * Move a top level key to a new key.
         *
         * @param topLevel The key to move the configuration from.
         * @param to The key to move the configuration to.
         * @return The {@link Transformation}
         */
        public static Transformation moveTopLevel(String topLevel, String... to) {
            return new Transformation(new Object[] { topLevel }, (i, v) -> to);
        }

        /**
         * Move a config from a key.
         *
         * @param from The key to move from.
         * @return An object where the destination node is specified.
         */
        public static From moveFrom(String... from) {
            return new From(from);
        }

        /**
         * Creates the transformation that is required.
         *  @param objectPath The node to transform relative to this configuration object.
         * @param action The {@link TransformAction} containing the transformation.
         */
        public Transformation(Object[] objectPath, TransformAction action) {
            this.objectPath = objectPath;
            this.action = action;
        }

        Object[] getObjectPath() {
            return objectPath;
        }

        TransformAction getAction() {
            return action;
        }

        public final static class From {

            private final Object[] from;

            private From(Object[] from) {
                this.from = from;
            }

            /**
             * The key to move the config node to.
             *
             * @param to The key.
             * @return The {@link Transformation}
             */
            public Transformation to(String... to) {
                return new Transformation(from, (i, v) -> to);
            }
        }
    }
}
