/*
 * This file is part of QuickStart Module Loader, licensed under the MIT License (MIT). See the LICENSE.txt file
 * at the root of this project for more details.
 */
package uk.co.drnaylor.quickstart.config;

import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;

public abstract class AbstractConfigAdapter<N extends ConfigurationNode, T extends ConfigurationLoader<N>, R> {

    public abstract CommentedConfigurationNode getDefaults();

    public abstract R getData(N node);

    public abstract N setData(R data);
}
