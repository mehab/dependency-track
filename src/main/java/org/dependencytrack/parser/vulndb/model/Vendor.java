package org.dependencytrack.parser.vulndb.model;

import java.util.List;

public record Vendor(int id, String name, String shortName, String vendorUrl, List<Product> products) implements ApiObject{

    @Override
    public int getId() {
        return this.id();
    }
}
