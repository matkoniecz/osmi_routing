package de.geofabrik.osmi_routing;

import java.util.List;

public class MissingConnectionResult {
    
    private List<MissingConnection> results;
    private boolean complete;
    private boolean success;
    private Exception ex;

    public MissingConnectionResult() {
        complete = false;
        success = false;
        ex = new RuntimeException("no success result stored");
    }

    public void storeSuccessResult(List<MissingConnection> foundResults) {
        results = foundResults;
        complete = true;
        success = true;
    }

    public void storeFailureResult(Exception ex) {
        complete = true;
        success = false;
        this.ex = ex;
    }

    public boolean isComplete() {
        return complete;
    }

    public boolean isSuccess() {
        return success;
    }

    public Exception getException() {
        return ex;
    }

    public List<MissingConnection> getEntities() {
        return results;
    }
}
