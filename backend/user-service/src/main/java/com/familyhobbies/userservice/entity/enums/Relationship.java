package com.familyhobbies.userservice.entity.enums;

/**
 * Defines the relationship of a family member to the family group.
 * Stored as VARCHAR in the database via {@code @Enumerated(EnumType.STRING)}.
 */
public enum Relationship {
    PARENT,
    CHILD,
    SPOUSE,
    SIBLING,
    OTHER
}
