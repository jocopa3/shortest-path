package shortestpath;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PluginPathManager {
    private List<PluginIdentifier> pluginPriorities;
    private Map<PluginIdentifier, PathParameters> pluginMap;
    private PathParameters currentPath;

    PluginPathManager() {
        pluginPriorities = new ArrayList<>(32);
        pluginMap = new HashMap<>(32);
    }

    public PathParameters getCurrentParameters() {
        return currentPath;
    }

    public List<PluginIdentifier> getPluginPriorities() {
        return pluginPriorities;
    }

    public PathParameters getParameters(PluginIdentifier pluginIdentifier) {
        return pluginMap.get(pluginIdentifier);
    }

    public void addPlugin(PluginIdentifier pluginIdentifier) {
        if (!pluginPriorities.contains(pluginIdentifier)) {
            pluginPriorities.add(pluginIdentifier);
        }
    }

    public void removePlugin(PluginIdentifier pluginIdentifier) {
        pluginPriorities.remove(pluginIdentifier);
        pluginMap.remove(pluginIdentifier);
    }

    // Returns true if the current path was changed
    public boolean requestPath(PathParameters parameters) {
        if (Objects.equals(currentPath, parameters)) {
            return false;
        }

        int priority = pluginPriorities.indexOf(parameters.getRequester());
        pluginMap.put(parameters.getRequester(), parameters);
        if (priority < 0) {
            pluginPriorities.add(parameters.getRequester());
        }

        PathParameters oldPath = currentPath;
        currentPath = getHighestPriorityPath();
        return !Objects.equals(oldPath, currentPath);
    }

    // Current path will become stale; the caller is expected to request the newly returned path
    PathParameters setNewPriorities(@NonNull List<PluginIdentifier> priorities) {
        if (priorities.size() != pluginPriorities.size()) {
            throw new IllegalArgumentException("new priorities size must match");
        }

        pluginPriorities = priorities;
        return getHighestPriorityPath();
    }

    // Current path will become stale; the caller is expected to request the newly returned path
    PathParameters clearPath(PluginIdentifier requester) {
        pluginMap.remove(requester);
        return getHighestPriorityPath();
    }

    private PathParameters getHighestPriorityPath() {
        for (int i = 0; i < pluginPriorities.size(); ++i) {
            PathParameters parameters = pluginMap.get(pluginPriorities.get(i));
            if (parameters != null && parameters.isVisible()) {
                return parameters;
            }
        }

        return null;
    }

    public void shutDown() {
        // TODO: save order
    }
}
