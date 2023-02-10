package org.dependencytrack.parser.vulndb.model;

import java.util.List;

public record Version(int id, String name, boolean affected, List<Cpe> cpes) implements ApiObject{
    @Override
    public int getId() {
        return this.id();
    }
}
