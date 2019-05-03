package de.geofabrik.osmi_routing;

import java.util.List;

public interface OutputListener {

    /**
     * Provides the listener with the list of results to be written.
     * <p>
     *
     * @param results The list of results.
     */
    void complete(List<MissingConnection> results);

    /**
     * Notifies the listener that an error occurred during processing.
     */
    void error(Exception ex);

}
