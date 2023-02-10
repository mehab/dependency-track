package org.dependencytrack.parser.vulndb.model;

public record Status(String organizationName, String userNameRequesting, String userEmailRequesting,
                     String subscriptionEndDate,
                     String apiCallsAllowedPerMonth, String apiCallsMadeThisMonth, String vulnDbStatistics,
                     String rawStatus) {
}
