/*
 * This file is part of Dependency-Track.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) Steve Springett. All Rights Reserved.
 */
package org.dependencytrack.policy;

import alpine.common.logging.Logger;
import org.dependencytrack.model.Component;
import org.dependencytrack.model.Policy;
import org.dependencytrack.model.PolicyCondition;
import org.dependencytrack.model.PolicyViolation;
import org.dependencytrack.model.Project;
import org.dependencytrack.model.Tag;
import org.dependencytrack.persistence.QueryManager;
import org.dependencytrack.util.NotificationUtil;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * A lightweight policy engine that evaluates a list of components against
 * all defined policies. Each policy is evaluated using individual policy
 * evaluators. Additional evaluators can be easily added in the future.
 *
 * @author Steve Springett
 * @since 4.0.0
 */
public class PolicyEngine {

    private static final Logger LOGGER = Logger.getLogger(PolicyEngine.class);

    private final List<PolicyEvaluator> evaluators = new ArrayList<>();

    public PolicyEngine() {
        evaluators.add(new SeverityPolicyEvaluator());
        evaluators.add(new CoordinatesPolicyEvaluator());
        evaluators.add(new LicenseGroupPolicyEvaluator());
        evaluators.add(new LicensePolicyEvaluator());
        evaluators.add(new PackageURLPolicyEvaluator());
        evaluators.add(new CpePolicyEvaluator());
        evaluators.add(new SwidTagIdPolicyEvaluator());
        evaluators.add(new VersionPolicyEvaluator());
        evaluators.add(new ComponentAgePolicyEvaluator());
        evaluators.add(new ComponentHashPolicyEvaluator());
        evaluators.add(new CwePolicyEvaluator());
    }

    public List<PolicyViolation> evaluate(final List<Component> components) {
        LOGGER.info("Evaluating " + components.size() + " component(s) against applicable policies");
        List<PolicyViolation> violations = new ArrayList<>();
        try (final QueryManager qm = new QueryManager()) {
            final List<Policy> policies = qm.getAllPolicies();
            for (final Component c: components) {
                final Component component = qm.getObjectById(Component.class, c.getId());
                violations.addAll(this.evaluate(qm, policies, component));
            }
        }
        LOGGER.info("Policy analysis complete");
        return violations;
    }

    private List<PolicyViolation> evaluate(final QueryManager qm, final List<Policy> policies, final Component component) {
        final List<PolicyViolation> policyViolations = new ArrayList<>();
        for (final Policy policy : policies) {
            if (policy.isGlobal() || isPolicyAssignedToProject(policy, component.getProject())
                    || isPolicyAssignedToProjectTag(policy, component.getProject())) {
                LOGGER.debug("Evaluating component (" + component.getUuid() +") against policy (" + policy.getUuid() + ")");
                final List<PolicyConditionViolation> policyConditionViolations = new ArrayList<>();
                int policyConditionsViolated = 0;
                for (final PolicyEvaluator evaluator : evaluators) {
                    evaluator.setQueryManager(qm);
                    final List<PolicyConditionViolation> policyConditionViolationsFromEvaluator = evaluator.evaluate(policy, component);
                    if (!policyConditionViolationsFromEvaluator.isEmpty()) {
                        policyConditionViolations.addAll(policyConditionViolationsFromEvaluator);
                        policyConditionsViolated += (int) policyConditionViolationsFromEvaluator.stream()
                                .map(pcv -> pcv.getPolicyCondition().getId())
                                .sorted()
                                .distinct()
                                .count();
                    }
                }
                if (Policy.Operator.ANY == policy.getOperator()) {
                    if (policyConditionsViolated > 0) {
                        policyViolations.addAll(createPolicyViolations(qm, policyConditionViolations));
                    }
                } else if (Policy.Operator.ALL == policy.getOperator()) {
                    if (policyConditionsViolated == policy.getPolicyConditions().size()) {
                        policyViolations.addAll(createPolicyViolations(qm, policyConditionViolations));
                    }
                }
            }
        }
        qm.reconcilePolicyViolations(component, policyViolations);
        for (final PolicyViolation pv: qm.getAllPolicyViolations(component)) {
            NotificationUtil.analyzeNotificationCriteria(qm, pv);
        }
        return policyViolations;
    }

    private boolean isPolicyAssignedToProject(Policy policy, Project project) {
        if (policy.getProjects() == null || policy.getProjects().size() == 0) {
            return false;
        }
        return (policy.getProjects().stream().anyMatch(p -> p.getId() == project.getId()) || (Boolean.TRUE.equals(policy.isIncludeChildren()) && isPolicyAssignedToParentProject(policy, project)));
    }

    private List<PolicyViolation> createPolicyViolations(final QueryManager qm, final List<PolicyConditionViolation> pcvList) {
        final List<PolicyViolation> policyViolations = new ArrayList<>();
        for (PolicyConditionViolation pcv: pcvList) {
            final PolicyViolation pv = new PolicyViolation();
            pv.setComponent(pcv.getComponent());
            pv.setPolicyCondition(pcv.getPolicyCondition());
            pv.setType(determineViolationType(pcv.getPolicyCondition().getSubject()));
            pv.setTimestamp(new Date());
            policyViolations.add(qm.addPolicyViolationIfNotExist(pv));
        }
        return policyViolations;
    }

    private PolicyViolation.Type determineViolationType(final PolicyCondition.Subject subject) {
        switch(subject) {
            case CWE:
            case SEVERITY:
                return PolicyViolation.Type.SECURITY;
            case AGE:
            case COORDINATES:
            case PACKAGE_URL:
            case CPE:
            case SWID_TAGID:
            case COMPONENT_HASH:
            case VERSION:
                return PolicyViolation.Type.OPERATIONAL;
            case LICENSE:
            case LICENSE_GROUP:
                return PolicyViolation.Type.LICENSE;
        }
        return null;
    }

    private boolean isPolicyAssignedToProjectTag(Policy policy, Project project) {
        if (policy.getTags() == null || policy.getTags().size() == 0) {
            return false;
        }
        for(Tag projectTag : project.getTags()){
            return policy.getTags().stream().anyMatch(p -> p.getId() == projectTag.getId());
        }
        return false;
    }

    private boolean isPolicyAssignedToParentProject(Policy policy, Project child) {
        if (child.getParent() == null) {
            return false;
        }
        if (policy.getProjects().stream().anyMatch(p -> p.getId() == child.getParent().getId())) {
            return true;
        } 
        return isPolicyAssignedToParentProject(policy, child.getParent());
    }
}
