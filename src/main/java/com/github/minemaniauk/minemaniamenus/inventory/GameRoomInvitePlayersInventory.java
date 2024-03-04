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

package com.github.minemaniauk.minemaniamenus.inventory;

import com.github.minemaniauk.minemaniamenus.MessageManager;
import com.github.minemaniauk.minemaniamenus.MineManiaMenus;
import com.github.minemaniauk.minemaniamenus.PublicTaskContainer;
import com.github.smuddgge.velocityinventory.Inventory;
import com.github.smuddgge.velocityinventory.InventoryItem;
import com.github.smuddgge.velocityinventory.action.ActionResult;
import com.github.smuddgge.velocityinventory.action.action.ClickAction;
import com.github.smuddgge.velocityinventory.action.action.CloseAction;
import com.github.smuddgge.velocityinventory.action.action.OpenAction;
import com.velocitypowered.api.proxy.Player;
import dev.simplix.protocolize.api.inventory.InventoryClick;
import dev.simplix.protocolize.api.inventory.InventoryClose;
import dev.simplix.protocolize.data.ItemType;
import dev.simplix.protocolize.data.inventory.InventoryType;
import net.querz.nbt.tag.CompoundTag;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.UUID;

public class GameRoomInvitePlayersInventory extends Inventory {

    private final @NotNull UUID gameRoomIdentifier;

    public GameRoomInvitePlayersInventory(@NotNull UUID gameRoomIdentifier) {
        super(InventoryType.GENERIC_9X6);

        this.gameRoomIdentifier = gameRoomIdentifier;

        // Custom inventory character.
        this.setTitle(MessageManager.convertToLegacy("&8&lInvite Players"));

        // Add open action.
        final UUID uuid = UUID.randomUUID();
        this.addAction(new OpenAction() {
            @Override
            public @NotNull ActionResult onOpen(@NotNull Player player, @NotNull Inventory inventory) {
                GameRoomInvitePlayersInventory.this.onOpen(player);
                PublicTaskContainer.getInstance().runLoopTask(
                        () -> GameRoomInvitePlayersInventory.this.onOpen(player),
                        Duration.ofSeconds(2),
                        "GameRoomInvitePlayersInventory" + uuid
                );
                return new ActionResult();
            }
        });

        this.addAction(new CloseAction() {
            @Override
            public @NotNull ActionResult onClose(@NotNull InventoryClose inventoryClose, @NotNull Inventory inventory) {
                PublicTaskContainer.getInstance().stopTask("GameRoomInvitePlayersInventory" + uuid);
                return new ActionResult();
            }
        });
    }

    private void onOpen(@NotNull Player player) {
        int slot = -1;
        for (Player invitePlayer : MineManiaMenus.getInstance().getProxyServer().getAllPlayers()) {
            slot++;
            if (slot > 44) continue;

            // Create skull texture.
            CompoundTag tag = new CompoundTag();
            tag.putString("SkullOwner", invitePlayer.getGameProfile().getName());

            // Set the player's item.
            this.setItem(new InventoryItem()
                    .setMaterial(ItemType.PLAYER_HEAD)
                    .setNBT(tag)
                    .setName("&6&lInvite &f&l" + invitePlayer.getGameProfile().getName())
                    .setLore("&7Click to send a invite to this player.",
                            "&eComing Soon...")
                    .addSlots(slot)
            );
        }

        this.setItem(new InventoryItem()
                .setMaterial(ItemType.LIME_STAINED_GLASS_PANE)
                .setName("&a&lBack To Game Room")
                .setLore("&7Click to go back to the game room.")
                .addSlots(45, 53)
                .addClickAction(new ClickAction() {
                    @Override
                    public @NotNull ActionResult onClick(@NotNull InventoryClick inventoryClick, @NotNull Inventory inventory) {
                        new GameRoomInventory(GameRoomInvitePlayersInventory.this.gameRoomIdentifier).open(player);
                        return new ActionResult();
                    }
                })
        );
    }
}