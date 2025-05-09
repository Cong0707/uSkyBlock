package dk.lockfuglsang.minecraft.command;

import dk.lockfuglsang.minecraft.po.I18nUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Convenience implementation of the Command
 */
public abstract class AbstractCommand implements Command {
    private final String[] aliases;
    private final String permission;
    private final String description;
    private final String usage;
    private final String[] params;
    private CompositeCommand parent;
    private final Map<String, String> featurePerms = new HashMap<>();
    private final Set<UUID> permissionOverride;

    public AbstractCommand(String name, String permission, String params, String description, String usage, UUID... permissionOverride) {
        this.aliases = name.split("\\|");
        this.permission = permission;
        this.description = I18nUtil.tr(description);
        this.usage = I18nUtil.tr(usage);
        this.params = params != null && !params.trim().isEmpty() ? params.split(" ") : new String[0];
        this.permissionOverride = new HashSet<>(Arrays.asList(permissionOverride));
    }

    public AbstractCommand(String name, String permission, String params, String description) {
        this(name, permission, params, description, null);
    }

    public AbstractCommand(String name, String permission, String description) {
        this(name, permission, null, description, null);
    }

    public AbstractCommand(String name, String description) {
        this(name, null, null, description, null);
    }

    @Override
    public String getName() {
        return aliases[0];
    }

    public String[] getAliases() {
        return aliases;
    }

    @Override
    public String getPermission() {
        return permission;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getUsage() {
        return usage;
    }

    @Override
    public String[] getParams() {
        return params;
    }

    @Override
    public TabCompleter getTabCompleter() {
        return null;
    }

    @Override
    public CompositeCommand getParent() {
        return parent;
    }

    @Override
    public void setParent(CompositeCommand parent) {
        this.parent = parent;
    }

    @Override
    public void accept(CommandVisitor visitor) {
        if (visitor != null) {
            visitor.visit(this);
        }
    }

    public void addFeaturePermission(String perm, String description) {
        featurePerms.put(perm, description);
    }

    @Override
    public Map<String, String> getFeaturePermissions() {
        return Collections.unmodifiableMap(featurePerms);
    }

    public boolean hasPermissionOverride(CommandSender sender) {
        if (sender instanceof Player) {
            return permissionOverride.contains(((Player) sender).getUniqueId()) || (parent != null && parent.hasPermissionOverride(sender));
        }
        return false;
    }

    @Override
    public boolean hasPermission(CommandSender sender, String permission) {
        return hasPermissionOverride(sender) || sender.hasPermission(permission);
    }
}
