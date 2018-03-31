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

import co.aikar.util.MapSet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@SuppressWarnings("WeakerAccess")
public class CommandHelp {
    private final CommandManager manager;
    private final CommandIssuer issuer;
    private final List<HelpEntry> helpEntries = new ArrayList<>();
    private final String commandName;
    final String commandPrefix;
    private int page;
    private int perPage;
    List<String> search;
    private HelpEntry selectedEntry;
    private int totalResults;
    private int totalPages;
    private boolean lastPage;

    public CommandHelp(CommandManager manager, RootCommand rootCommand, CommandIssuer issuer) {
        this.manager = manager;
        this.issuer = issuer;
        this.perPage = manager.defaultHelpPerPage;
        this.commandPrefix = manager.getCommandPrefix(issuer);
        this.commandName = rootCommand.getCommandName();


        MapSet<String, RegisteredCommand> subCommands = rootCommand.getSubCommands();
        Set<RegisteredCommand> seen = new HashSet<>();
        subCommands.forEachEntry((key, command) -> {
            if (BaseCommand.isSpecial(key)) {
                return;
            }

            if (command.hasPermission(issuer) && !seen.contains(command)) {
                this.helpEntries.add(new HelpEntry(this, command));
                seen.add(command);
            }
        });
    }

    @UnstableAPI // Not sure on this one yet even when API becomes unstable
    protected void updateSearchScore(HelpEntry help) {
        if (this.search == null || this.search.isEmpty()) {
            help.setSearchScore(1);
            return;
        }
        final RegisteredCommand<?> cmd = help.getRegisteredCommand();

        int searchScore = 0;
        for (String word : this.search) {
            Pattern pattern = Pattern.compile(".*" + Pattern.quote(word) + ".*", Pattern.CASE_INSENSITIVE);
            for (String subCmd : cmd.registeredSubcommands) {
                Pattern subCmdPattern = Pattern.compile(".*" + Pattern.quote(subCmd) + ".*", Pattern.CASE_INSENSITIVE);
                if (pattern.matcher(subCmd).matches()) {
                    searchScore += 3;
                } else if (subCmdPattern.matcher(word).matches()) {
                    searchScore++;
                }
            }


            if (pattern.matcher(help.getDescription()).matches()) {
                searchScore += 2;
            }
            if (pattern.matcher(help.getParameterSyntax()).matches()) {
                searchScore++;
            }
            if (help.getSearchTags() != null && pattern.matcher(help.getSearchTags()).matches()) {
                searchScore += 2;
            }
        }
        help.setSearchScore(searchScore);
    }

    public CommandManager getManager() {
        return manager;
    }

    public boolean isExactMatch(String command) {
        for (HelpEntry helpEntry : helpEntries) {
            if (helpEntry.getCommand().endsWith(" " + command)) {
                selectedEntry = helpEntry;
                return true;
            }
        }
        return false;
    }

    public void showHelp() {
        showHelp(issuer);
    }

    public void showHelp(CommandIssuer issuer) {
        CommandHelpFormatter formatter = manager.getHelpFormatter();
        if (selectedEntry != null) {
            formatter.showDetailedHelp(this, selectedEntry);
            return;
        }

        List<HelpEntry> helpEntries = getHelpEntries();
        Iterator<HelpEntry> results = helpEntries.stream()
                .filter(HelpEntry::shouldShow)
                .sorted(Comparator.comparingInt(helpEntry -> helpEntry.getSearchScore() * -1)).iterator();
        if (!results.hasNext()) {
            issuer.sendMessage(MessageType.ERROR, MessageKeys.NO_COMMAND_MATCHED_SEARCH, "{search}", ACFUtil.join(this.search, " "));
            helpEntries = getHelpEntries();
            results = helpEntries.iterator();
        }
        this.totalResults = helpEntries.size();
        int min = (this.page - 1) * this.perPage; // TODO: per page configurable?
        int max = min + this.perPage;
        this.totalPages = (int) Math.ceil((float) totalResults / (float) this.perPage);
        int i = 0;
        if (min >= totalResults) {
            issuer.sendMessage(MessageType.HELP, MessageKeys.HELP_NO_RESULTS);
            return;
        }

        List<HelpEntry> printEntries = new ArrayList<>();
        while (results.hasNext()) {
            HelpEntry e = results.next();
            if (i >= max) {
                break;
            }
            if (i++ < min) {
                continue;
            }
            printEntries.add(e);
        }
        this.lastPage = !(min > 0 || results.hasNext());

        if (search == null) {
            formatter.showAllResults(this, printEntries);
        } else {
            formatter.showSearchResults(this, printEntries);
        }

    }

    public List<HelpEntry> getHelpEntries() {
        return helpEntries;
    }

    public void setPerPage(int perPage) {
        this.perPage = perPage;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public void setPage(int page, int perPage) {
        this.setPage(page);
        this.setPerPage(perPage);
    }

    public void setSearch(List<String> search) {
        this.search = search;
        getHelpEntries().forEach(this::updateSearchScore);
    }

    public CommandIssuer getIssuer() {
        return issuer;
    }

    public String getCommandName() {
        return commandName;
    }

    public String getCommandPrefix() {
        return commandPrefix;
    }

    public int getPage() {
        return page;
    }

    public int getPerPage() {
        return perPage;
    }

    public List<String> getSearch() {
        return search;
    }

    public HelpEntry getSelectedEntry() {
        return selectedEntry;
    }

    public int getTotalResults() {
        return totalResults;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public boolean isLastPage() {
        return lastPage;
    }
}
