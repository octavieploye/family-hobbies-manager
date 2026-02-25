package com.familyhobbies.userservice.entity;

public enum UserRole {

    /** Family user (parent/guardian). Default role assigned at registration. */
    FAMILY,

    /** Association manager. Can view subscriber lists, mark attendance. */
    ASSOCIATION,

    /** Platform administrator. Full CRUD on all entities. */
    ADMIN
}
