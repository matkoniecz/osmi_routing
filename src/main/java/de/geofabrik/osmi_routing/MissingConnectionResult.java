/*
 *  © 2019 Geofabrik GmbH
 *
 *  Code in this file is a copy of the PbfBlobResult class which belongs
 *  to GraphHopper and Osmosis.
 *  Copyright 2012 - 2017 GraphHopper GmbH, licensed unter Apache License
 *  version 2.
 *
 *  This file is part of osmi_routing.
 *
 *  osmi_routing is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License.
 *
 *  osmi_routing is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with osmi_simple_views. If not, see <http://www.gnu.org/licenses/>.
 */

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
