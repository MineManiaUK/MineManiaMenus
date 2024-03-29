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

package com.github.minemaniauk.minemaniamenus.command.type;

import com.github.minemaniauk.minemaniamenus.User;
import com.github.minemaniauk.minemaniamenus.command.BaseCommandType;
import com.github.minemaniauk.minemaniamenus.command.CommandStatus;
import com.github.minemaniauk.minemaniamenus.command.CommandSuggestions;
import com.github.minemaniauk.minemaniamenus.inventory.GameRoomInvitesInventory;
import com.github.smuddgge.squishyconfiguration.interfaces.ConfigurationSection;

/**
 * Represents the invites command type.
 */
public class Invites extends BaseCommandType {

    @Override
    public String getName() {
        return "invites";
    }

    @Override
    public String getSyntax() {
        return "/[name]";
    }

    @Override
    public CommandSuggestions getSuggestions(ConfigurationSection section, User user) {
        return null;
    }

    @Override
    public CommandStatus onConsoleRun(ConfigurationSection section, String[] arguments) {
        return new CommandStatus().playerCommand();
    }

    @Override
    public CommandStatus onPlayerRun(ConfigurationSection section, String[] arguments, User user) {
        new GameRoomInvitesInventory().open(user.getPlayer());
        return new CommandStatus();
    }
}
