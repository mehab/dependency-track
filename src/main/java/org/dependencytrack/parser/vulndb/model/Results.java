package org.dependencytrack.parser.vulndb.model;

import java.util.List;

public record Results<T>(int page, int total, List<T> results, String rawResults, String errorCondition) {
}
