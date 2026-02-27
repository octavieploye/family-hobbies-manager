// frontend/src/app/features/family/models/family.model.ts

/**
 * Represents a family entity returned by GET /families/me.
 * Matches backend FamilyResponse DTO field-for-field.
 */
export interface Family {
  id: number;
  name: string;
  createdBy: number;
  members: FamilyMember[];
  createdAt: string;
  updatedAt: string;
}

/**
 * Represents a family member within a family.
 * Matches backend FamilyMemberResponse DTO field-for-field.
 */
export interface FamilyMember {
  id: number;
  familyId: number;
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  age: number;
  relationship: string;
  medicalNote?: string;
  createdAt: string;
  updatedAt?: string;
}

/**
 * Request payload for creating or updating a family.
 * Matches backend FamilyRequest DTO.
 */
export interface FamilyRequest {
  name: string;
}

/**
 * Request payload for adding or updating a family member.
 * Matches backend FamilyMemberRequest DTO.
 */
export interface FamilyMemberRequest {
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  relationship: string;
  medicalNote?: string;
}
