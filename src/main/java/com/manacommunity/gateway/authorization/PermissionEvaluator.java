package com.manacommunity.gateway.authorization;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Evaluates whether a user possesses the required roles or permissions
 * to access a given resource.
 */
@Slf4j
@Component
public class PermissionEvaluator {

    /**
     * Checks if the user has at least one of the required roles.
     *
     * @param userRoles     the roles assigned to the current user
     * @param requiredRoles the roles required to access the resource
     * @return true if the user holds at least one required role
     */
    public boolean hasAnyRole(List<String> userRoles, Collection<String> requiredRoles) {
        if (requiredRoles == null || requiredRoles.isEmpty()) {
            return true;
        }
        if (userRoles == null || userRoles.isEmpty()) {
            return false;
        }
        return userRoles.stream().anyMatch(requiredRoles::contains);
    }

    /**
     * Checks if the user has at least one of the required permissions.
     *
     * @param userPermissions     the permissions assigned to the current user
     * @param requiredPermissions the permissions required to access the resource
     * @return true if the user holds at least one required permission
     */
    public boolean hasAnyPermission(List<String> userPermissions, Collection<String> requiredPermissions) {
        if (requiredPermissions == null || requiredPermissions.isEmpty()) {
            return true;
        }
        if (userPermissions == null || userPermissions.isEmpty()) {
            return false;
        }
        return userPermissions.stream().anyMatch(requiredPermissions::contains);
    }

    /**
     * Checks if the user has all of the required permissions.
     *
     * @param userPermissions     the permissions assigned to the current user
     * @param requiredPermissions the permissions required to access the resource
     * @return true if the user holds all required permissions
     */
    public boolean hasAllPermissions(List<String> userPermissions, Collection<String> requiredPermissions) {
        if (requiredPermissions == null || requiredPermissions.isEmpty()) {
            return true;
        }
        if (userPermissions == null || userPermissions.isEmpty()) {
            return false;
        }
        return userPermissions.containsAll(requiredPermissions);
    }

    /**
     * Returns the user's effective roles, never null.
     */
    public List<String> sanitizeRoles(List<String> roles) {
        return roles != null ? roles : Collections.emptyList();
    }

    /**
     * Returns the user's effective permissions, never null.
     */
    public List<String> sanitizePermissions(List<String> permissions) {
        return permissions != null ? permissions : Collections.emptyList();
    }
}
