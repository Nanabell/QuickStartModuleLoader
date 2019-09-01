/*
 * This file is part of QuickStart Module Loader, licensed under the MIT License (MIT). See the LICENSE.txt file
 * at the root of this project for more details.
 */
package uk.co.drnaylor.quickstart;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import uk.co.drnaylor.quickstart.enums.LoadingStatus;
import uk.co.drnaylor.quickstart.enums.ModulePhase;

import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Internal specification of a module.
 */
public final class ModuleMetadata<M extends Module> {

    private final Class<? extends M> moduleClass;
    private final List<String> softDeps;
    private final List<String> deps;
    @Nullable private final String description;
    private final String name;
    private final String id;
    private final boolean runtimeDisableable;
    private LoadingStatus status;
    private final boolean isMandatory;
    private ModulePhase phase = ModulePhase.DISCOVERED;

    ModuleMetadata(Class<M> moduleClass, boolean isDisableable, ModuleData data) {
        this(moduleClass,
                data.id(),
                data.name(),
                data.description(),
                data.status(),
                data.isRequired(),
                isDisableable,
                Arrays.asList(data.softDependencies()),
                Arrays.asList(data.dependencies()));
    }

    ModuleMetadata(Class<M> moduleClass, boolean isDisableable, String id, String name, String description, LoadingStatus status,
            boolean isMandatory) {
        this(moduleClass, id, name, description, status, isMandatory, isDisableable, Lists.newArrayList(), Lists.newArrayList());
    }

    ModuleMetadata(Class<M> moduleClass,
            String id,
            String name,
            String description,
            LoadingStatus status,
            boolean isMandatory,
            boolean isDisableable,
            List<String> softDeps,
            List<String> deps) {
        Preconditions.checkNotNull(moduleClass);
        Preconditions.checkNotNull(id);
        Preconditions.checkNotNull(name);
        Preconditions.checkNotNull(status);
        Preconditions.checkNotNull(deps);
        Preconditions.checkNotNull(softDeps);

        this.id = id;
        this.moduleClass = moduleClass;
        this.runtimeDisableable = isDisableable;
        this.name = name;
        this.description = description != null ? description : "";
        this.status = isMandatory ? LoadingStatus.FORCELOAD : status;
        this.isMandatory = isMandatory;
        this.softDeps = softDeps;
        this.deps = deps;
    }

    /**
     * Gets the {@link Class} that represents the {@link Module}
     *
     * @return The {@link Class}
     */
    public Class<? extends M> getModuleClass() {
        return moduleClass;
    }

    /**
     * Gets the internal ID of the module.
     *
     * @return The ID of the module.
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the human friendly name of the module.
     *
     * @return The name of the module.
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the description of the module. May be empty.
     *
     * @return The description, if any.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets the {@link LoadingStatus} for the module
     *
     * @return The {@link LoadingStatus}
     */
    public LoadingStatus getStatus() {
        return status;
    }

    /**
     * Sets the {@link LoadingStatus} for the module.
     *
     * @param status The {@link LoadingStatus}
     */
    void setStatus(LoadingStatus status) {
        Preconditions.checkState(phase.canSetLoadingPhase());
        Preconditions.checkState(!isMandatory);
        this.status = status;
    }

    /**
     * Gets whether the module is mandatory. This is equivalent to {@link LoadingStatus#FORCELOAD}, but it cannot be
     * changed via a config file.
     *
     * @return <code>true</code> if the module cannot be turned off.
     */
    public boolean isMandatory() {
        return isMandatory;
    }

    /**
     * Gets the current {@link ModulePhase} of the module.
     *
     * @return The phase of the module.
     */
    public ModulePhase getPhase() {
        return phase;
    }

    /**
     * Sets the phase of the module.
     *
     * @param phase The {@link ModulePhase}
     */
    void setPhase(ModulePhase phase) {
        this.phase = phase;
    }

    /**
     * Gets modules that should load before this one, if they are to be enabled.
     *
     * @return The list of the dependencies' IDs.
     */
    public List<String> getSoftDependencies() {
        return softDeps;
    }

    /**
     * Gets modules that <strong>must</strong> be loaded before this one.
     *
     * @return The list of the dependencies' IDs.
     */
    public List<String> getDependencies() {
        return deps;
    }

    /**
     * Returns whether this module can be enabled/disabled at runtime.
     *
     * @return <code>true</code> if so.
     */
    public boolean isRuntimeAlterable() {
        return runtimeDisableable;
    }
}
