/*
 * Copyright (c) 2016-2017 Daniel Ennis (Aikar) - MIT License
 *
 *  Permission is hereby granted, free of charge, to any person obtaining
 *  a copy of this software and associated documentation files (the
 *  "Software"), to deal in the Software without restriction, including
 *  without limitation the rights to use, copy, modify, merge, publish,
 *  distribute, sublicense, and/or sell copies of the Software, and to
 *  permit persons to whom the Software is furnished to do so, subject to
 *  the following conditions:
 *
 *  The above copyright notice and this permission notice shall be
 *  included in all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 *  EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 *  MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 *  NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 *  LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 *  OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 *  WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package co.aikar.commands;

import co.aikar.commands.apachecommonslang.ApacheCommonsLangUtil;
import co.aikar.util.MapSet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

interface RootCommand {
    void addChild(BaseCommand command);
    CommandManager getManager();

    MapSet<String, RegisteredCommand> getSubCommands();
    List<BaseCommand> getChildren();

    String getCommandName();
    default void addChildShared(List<BaseCommand> children, MapSet<String, RegisteredCommand> subCommands, BaseCommand command) {
        command.subCommands.forEachEntry((key, registeredCommand) -> {
            if (BaseCommand.isSpecial(key)) {
                return;
            }
            Set<RegisteredCommand> registered = subCommands.get(key);
            if (!registered.isEmpty()) {
                BaseCommand prevBase = registered.iterator().next().scope;
                if (prevBase != registeredCommand.scope) {
                    this.getManager().log(LogLevel.ERROR, "ACF Error: " + command.getName() + " registered subcommand " + key + " for root command " + getCommandName() + " - but it is already defined in " + prevBase.getName());
                    this.getManager().log(LogLevel.ERROR, "2 subcommands of the same prefix may not be spread over 2 different classes. Ignoring this.");
                    return;
                }
            }
            subCommands.add(key, registeredCommand);
        });

        children.add(command);
    }

    default BaseCommand execute(CommandIssuer sender, String commandLabel, String[] args) {
        BaseCommand command = getBaseCommand(args);

        command.execute(sender, commandLabel, args);
        return command;
    }

    default BaseCommand getBaseCommand(String[] args) {
        BaseCommand command = getDefCommand();
        for (int i = args.length; i >= 0; i--) {
            String checkSub = ApacheCommonsLangUtil.join(args, " ", 0, i).toLowerCase();
            Set<RegisteredCommand> registeredCommands = getSubCommands().get(checkSub);
            if (!registeredCommands.isEmpty()) {
                command = registeredCommands.iterator().next().scope;
                break;
            }
        }
        return command;
    }

    default List<String> getTabCompletions(CommandIssuer sender, String alias, String[] args) {
        return getTabCompletions(sender, alias, args, false);
    }

    default List<String> getTabCompletions(CommandIssuer sender, String alias, String[] args, boolean commandsOnly) {
        Set<String> completions = new HashSet<>();
        getChildren().forEach(child -> {
            if (!commandsOnly) {
                completions.addAll(child.tabComplete(sender, alias, args));
            }
            completions.addAll(child.getCommandsForCompletion(sender, args));
        });
        return new ArrayList<>(completions);
    }


    default RegisteredCommand getDefaultRegisteredCommand() {
        BaseCommand defCommand = this.getDefCommand();
        if (defCommand != null) {
            return defCommand.getDefaultRegisteredCommand();
        }
        return null;
    }

    default BaseCommand getDefCommand(){
        return null;
    }


    default String getDescription() {
        final RegisteredCommand cmd = this.getDefaultRegisteredCommand();
        if (cmd != null) {
            return cmd.helpText != null ? cmd.helpText : "";
        }
        BaseCommand defCommand = getDefCommand();
        if (defCommand != null && defCommand.description != null) {
            return defCommand.description;
        }
        return "";
    }


    default String getUsage() {
        final RegisteredCommand cmd = this.getDefaultRegisteredCommand();
        if (cmd != null) {
            return cmd.syntaxText != null ? cmd.syntaxText : "";
        }
        return "";
    }
}
