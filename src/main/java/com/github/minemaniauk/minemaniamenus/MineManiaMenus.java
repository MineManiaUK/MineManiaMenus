/*
 * MineManiaMenus
 * Used for interacting with the database and message broker.
 *
 * Copyright (C) 2023  MineManiaUK Staff
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.minemaniauk.minemaniamenus;

import com.github.kerbity.kerb.client.listener.EventListener;
import com.github.kerbity.kerb.packet.event.Event;
import com.github.kerbity.kerb.packet.event.Priority;
import com.github.minemaniauk.api.MineManiaAPI;
import com.github.minemaniauk.api.MineManiaAPIContract;
import com.github.minemaniauk.api.database.collection.UserCollection;
import com.github.minemaniauk.api.database.record.UserRecord;
import com.github.minemaniauk.api.kerb.event.gameroom.GameRoomInviteEvent;
import com.github.minemaniauk.api.kerb.event.player.PlayerChatEvent;
import com.github.minemaniauk.api.kerb.event.useraction.UserActionHasPermissionListEvent;
import com.github.minemaniauk.api.kerb.event.useraction.UserActionIsOnlineEvent;
import com.github.minemaniauk.api.kerb.event.useraction.UserActionIsVanishedEvent;
import com.github.minemaniauk.api.kerb.event.useraction.UserActionMessageEvent;
import com.github.minemaniauk.api.user.MineManiaUser;
import com.github.minemaniauk.minemaniamenus.command.BaseCommandType;
import com.github.minemaniauk.minemaniamenus.command.Command;
import com.github.minemaniauk.minemaniamenus.command.CommandHandler;
import com.github.minemaniauk.minemaniamenus.command.type.Invites;
import com.github.minemaniauk.minemaniamenus.command.type.MainMenu;
import com.github.minemaniauk.minemaniamenus.configuration.ConfigurationManager;
import com.github.minemaniauk.minemaniamenus.dependencys.MiniPlaceholdersDependency;
import com.github.minemaniauk.minemaniamenus.dependencys.ProtocolizeDependency;
import com.github.smuddgge.squishyconfiguration.ConfigurationFactory;
import com.github.smuddgge.squishyconfiguration.interfaces.Configuration;
import com.github.smuddgge.squishydatabase.Query;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import dev.simplix.protocolize.api.Protocolize;
import dev.simplix.protocolize.api.SoundCategory;
import dev.simplix.protocolize.api.player.ProtocolizePlayer;
import dev.simplix.protocolize.data.Sound;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;

/**
 * Represents the main plugin class.
 */
@Plugin(
        id = "minemaniamenus",
        name = "MineManiaMenus",
        version = "1.0.0"
)
public class MineManiaMenus implements MineManiaAPIContract {

    private static MineManiaMenus instance;
    private final ComponentLogger componentLogger;
    private final ProxyServer server;
    private CommandHandler commandHandler;

    private final @NotNull Configuration configuration;
    private final @NotNull MineManiaAPI api;

    @Inject
    public MineManiaMenus(ProxyServer server, @DataDirectory final Path folder, ComponentLogger componentLogger) {
        MineManiaMenus.instance = this;
        this.server = server;
        this.componentLogger = componentLogger;

        // Set up the configuration file.
        this.configuration = ConfigurationFactory.YAML
                .create(folder.toFile(), "config")
                .setDefaultPath("config.yml");
        this.configuration.load();

        // Set up the mine mania api connection.
        this.api = MineManiaAPI.createAndSet(
                this.configuration,
                this
        );

        ConfigurationManager.initialise(folder.toFile());

        // Register event listeners.
        this.api.getKerbClient().registerListener(Priority.HIGH, new EventListener<GameRoomInviteEvent>() {
            @Override
            public @Nullable Event onEvent(GameRoomInviteEvent event) {
                Optional<Player> optionalPlayer = MineManiaMenus.this.getPlayer(
                        MineManiaMenus.this.getUser(UUID.fromString(event.getGameRoomInvite().toPlayerUuid))
                );

                if (optionalPlayer.isEmpty()) return event;

                new User(optionalPlayer.get()).sendMessage("&6&l> &7You have been invited to play &f"
                        + event.getGameRoom().getGameType().getName()
                        + " &7with &f"
                        + event.getGameRoom().getOwner().getName()
                        + "&7. &7Run the command &e/invites &7to join the game room."
                );
                return event;
            }
        });
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {

        // Log header.
        MessageManager.logHeader();

        // Reload configuration to load custom placeholders correctly.
        ConfigurationManager.reload();

        // Append all command types.
        this.commandHandler = new CommandHandler();

        this.commandHandler.addType(new MainMenu());
        this.commandHandler.addType(new Invites());

        this.reloadCommands();

        // Check for dependencies.
        if (!ProtocolizeDependency.isEnabled()) {
            MessageManager.log("&7[Dependencies] Could not find optional dependency &fProtocolize");
            MessageManager.log("&7[Dependencies] Inventories and sounds will be disabled.");
            MessageManager.log(ProtocolizeDependency.getDependencyMessage());
        }

        if (!MiniPlaceholdersDependency.isEnabled()) {
            MessageManager.log("&7[Dependencies] Could not find optional dependency &fMini Placeholders");
            MessageManager.log("&7[Dependencies] This optional plugin lets you use mini placeholders, not to be confused with leaf placeholders.");
            MessageManager.log(MiniPlaceholdersDependency.getDependencyMessage());
        }
    }

    @Subscribe
    public void onPlayerJoin(ServerConnectedEvent event) {
        final String uuid = event.getPlayer().getUniqueId().toString();

        UserRecord record = this.getAPI().getDatabase()
                .getTable(UserCollection.class)
                .getUserRecord(event.getPlayer().getUniqueId())
                .orElse(null);

        if (record == null) {
            UserRecord first = new UserRecord();
            first.mc_name = event.getPlayer().getUsername();
            first.mc_uuid = uuid;

            this.getAPI().getDatabase()
                    .getTable(UserCollection.class)
                    .insertRecord(first);
            return;
        }

        if (record.getMinecraftUuid().toString().equalsIgnoreCase(uuid)) return;

        record.mc_uuid = uuid;

        this.getAPI().getDatabase()
                .getTable(UserCollection.class)
                .insertRecord(record);
    }

    @Override
    public @NotNull MineManiaUser getUser(@NotNull UUID uuid) {
        UserRecord record = this.getAPI().getDatabase()
                .getTable(UserCollection.class)
                .getFirstRecord(new Query().match("mc_uuid", uuid.toString()));

        if (record == null) {
            Player player = this.getProxyServer().getPlayer(uuid).orElse(null);

            if (player == null) {
                throw new RuntimeException("Player is not in database or online. There uuid is " + uuid);
            }

            return new MineManiaUser(uuid, player.getUsername());
        }

        return new MineManiaUser(uuid, record.getMinecraftName());
    }

    @Override
    public @NotNull MineManiaUser getUser(@NotNull String name) {
        UserRecord record = this.getAPI().getDatabase()
                .getTable(UserCollection.class)
                .getFirstRecord(new Query().match("mc_name", name));

        if (record == null) {
            Player player = this.getProxyServer().getPlayer(name).orElse(null);

            if (player == null) {
                throw new RuntimeException("Player is not in database or online. There name is " + name);
            }

            return new MineManiaUser(player.getUniqueId(), name);
        }

        return new MineManiaUser(record.getMinecraftUuid(), name);
    }

    @Override
    public @Nullable UserActionHasPermissionListEvent onHasPermission(@NotNull UserActionHasPermissionListEvent userActionHasPermissionListEvent) {
        return null;
    }

    @Override
    public @Nullable UserActionIsOnlineEvent onIsOnline(@NotNull UserActionIsOnlineEvent userActionIsOnlineEvent) {
        return null;
    }

    @Override
    public @Nullable UserActionIsVanishedEvent onIsVanished(@NotNull UserActionIsVanishedEvent userActionIsVanishedEvent) {
        return null;
    }

    @Override
    public @Nullable UserActionMessageEvent onMessage(@NotNull UserActionMessageEvent userActionMessageEvent) {
        return null;
    }

    @Override
    public @NotNull PlayerChatEvent onChatEvent(@NotNull PlayerChatEvent playerChatEvent) {
        return playerChatEvent;
    }

    /**
     * Used to get the plugin's component logger.
     *
     * @return The component logger.
     */
    public ComponentLogger getComponentLogger() {
        return componentLogger;
    }

    /**
     * Used to get the instance of the proxy server.
     *
     * @return The proxy server.
     */
    public ProxyServer getProxyServer() {
        return server;
    }

    /**
     * Used to get the registered api connection.
     *
     * @return Teh registered api connection.
     */
    public MineManiaAPI getAPI() {
        return this.api;
    }

    /**
     * Used to get the number of players online for a specific server.
     *
     * @param serverName The name of the server.
     * @return The number of players online.
     */
    public int getAmountOnline(String serverName) {
        Optional<RegisteredServer> optionalRegisteredServer = this.server.getServer(serverName);
        return optionalRegisteredServer.map(registeredServer -> getAmountOnline(optionalRegisteredServer.get())).orElse(0);
    }

    /**
     * Used to get the number of players online for a specific server.
     *
     * @param registeredServer The instance of the registered server.
     * @return The number of players online, not including vanished players.
     */
    public int getAmountOnline(@NotNull RegisteredServer registeredServer) {
        int amount = 0;
        for (Player player : registeredServer.getPlayersConnected()) {
            if (new User(player).isVanished()) continue;
            amount++;
        }
        return amount;
    }

    /**
     * Used to reload the plugin's commands.
     */
    public void reloadCommands() {
        this.commandHandler.unregister();

        for (String identifier : ConfigurationManager.getCommands().getAllIdentifiers()) {
            String commandTypeString = ConfigurationManager.getCommands().getCommandType(identifier);
            if (commandTypeString == null) continue;

            BaseCommandType commandType = this.commandHandler.getType(commandTypeString);

            if (commandType == null) {
                MessageManager.warn("Invalid command type for : " + identifier);
                continue;
            }

            Command command = new Command(identifier, commandType);
            this.commandHandler.append(command);
        }

        this.commandHandler.register();
    }

    /**
     * Used to get the list of online players.
     * This will not include vanished players.
     *
     * @return The list of online players.
     */
    public @NotNull List<String> getPlayers() {
        List<String> players = new ArrayList<>();

        for (Player player : this.server.getAllPlayers()) {
            players.add(player.getGameProfile().getName());
        }

        return players;
    }

    /**
     * Used to play a sound for a player.
     *
     * @param sound      The sound to play.
     * @param playerUuid The players uuid.
     */
    public void playSound(Sound sound, UUID playerUuid) {
        // Check if the protocolize plugin is enabled.
        if (!ProtocolizeDependency.isEnabled()) {
            MessageManager.warn("Tried to use sounds when the dependency is not enabled.");
            MessageManager.log("&7" + ProtocolizeDependency.getDependencyMessage());
            return;
        }

        ProtocolizePlayer player = Protocolize.playerProvider().player(playerUuid);
        player.playSound(sound, SoundCategory.MASTER, 1f, 1f);
    }

    /**
     * Used to play a sound for a player.
     *
     * @param sound      The sound as a string.
     * @param playerUuid The players uuid.
     */
    public void playSound(String sound, UUID playerUuid) {
        if (sound == null) return;
        if (sound.equals("none")) return;

        try {
            this.playSound(Sound.valueOf(sound.toUpperCase(Locale.ROOT)), playerUuid);
        } catch (IllegalArgumentException illegalArgumentException) {
            MessageManager.warn("Invalid sound : " + sound + " : ");
            illegalArgumentException.printStackTrace();
        }
    }

    /**
     * Used to get a player that is unable to vanish on a server.
     *
     * @param registeredServer The instance of the server.
     * @return The requested player.
     */
    public Player getNotVanishablePlayer(RegisteredServer registeredServer) {
        for (Player player : registeredServer.getPlayersConnected()) {
            User user = new User(player);

            if (user.isNotVanishable()) return player;
        }

        return null;
    }

    /**
     * Used to get a filtered list of players.
     * <ul>
     *     <li>Filters players with the permission.</li>
     * </ul>
     *
     * @param permission      The permission to filter.
     * @param permissions     The possible permissions to filter.
     * @param includeVanished If the filtered players should
     *                        include vanished players.
     * @return List of filtered players.
     */
    public List<User> getFilteredPlayers(String permission, List<String> permissions, boolean includeVanished) {
        List<User> players = new ArrayList<>();

        for (Player player : MineManiaMenus.getInstance().getProxyServer().getAllPlayers()) {
            User user = new User(player);

            // If the player has the permission node
            if (!user.hasPermission(permission)) continue;

            // Check if it's there the highest permission
            if (!Objects.equals(user.getHighestPermission(permissions), permission)) continue;

            // If includes vanished players and they are not vanished
            if (!includeVanished && user.isVanished()) continue;

            players.add(user);
        }

        return players;
    }

    /**
     * Used to get a filtered list of players on a server.
     * <ul>
     *     <li>Filters players with the permission.</li>
     * </ul>
     *
     * @param server          The instance of a server.
     * @param permission      The permission to filter.
     * @param includeVanished If the filtered players should
     *                        include vanished players.
     * @return List of filtered players.
     */
    public List<User> getFilteredPlayers(RegisteredServer server, String permission, boolean includeVanished) {
        List<User> players = new ArrayList<>();

        for (Player player : server.getPlayersConnected()) {
            User user = new User(player);

            // If the player has the permission node
            if (!user.hasPermission(permission)) continue;

            // If includes vanished players and they are not vanished
            if (!includeVanished && user.isVanished()) continue;

            players.add(user);
        }

        return players;
    }

    /**
     * Used to get a list of registered server names.
     *
     * @return The list of server names.
     */
    public List<String> getServerNames() {
        List<String> servers = new ArrayList<>();

        for (RegisteredServer server : MineManiaMenus.getInstance().getProxyServer().getAllServers()) {
            servers.add(server.getServerInfo().getName());
        }

        return servers;
    }

    /**
     * Used to get a random player from a server.
     *
     * @param server The instance of a server.
     * @return A random player.
     */
    public User getRandomUser(RegisteredServer server) {
        for (Player player : server.getPlayersConnected()) {
            return new User(player);
        }
        return null;
    }

    /**
     * Used to convert a mine mania user to a connected player.
     *
     * @param user The user to convert.
     * @return The instance of the connected player.
     */
    public @NotNull Optional<Player> getPlayer(@NotNull MineManiaUser user) {
        for (Player player : this.server.getAllPlayers()) {
            if (player.getUniqueId().equals(user.getUniqueId())) return Optional.of(player);
        }
        return Optional.empty();
    }

    /**
     * Used to attempt to get the instance of a server
     * from its name.
     *
     * @param name The name of the server.
     * @return The optional server.
     */
    public @NotNull Optional<RegisteredServer> getServer(@NotNull String name) {
        for (RegisteredServer registeredServer : this.server.getAllServers()) {
            if (registeredServer.getServerInfo().getName().equalsIgnoreCase(name)) return Optional.of(registeredServer);
        }
        return Optional.empty();
    }

    /**
     * Used to get this instance.
     *
     * @return This instance.
     */
    public static MineManiaMenus getInstance() {
        return instance;
    }
}
