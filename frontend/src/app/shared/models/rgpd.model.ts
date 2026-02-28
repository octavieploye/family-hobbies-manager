// frontend/src/app/shared/models/rgpd.model.ts
// TypeScript interfaces for RGPD consent and data export

export type ConsentType = 'TERMS_OF_SERVICE' | 'DATA_PROCESSING' | 'MARKETING_EMAIL' | 'THIRD_PARTY_SHARING';

/**
 * Current consent status for a single consent type.
 * Maps to backend ConsentResponse DTO.
 */
export interface ConsentStatus {
  id: number;
  userId: number;
  consentType: ConsentType;
  granted: boolean;
  version: string;
  consentedAt: string;
}

/**
 * Request payload for recording a consent decision.
 * Maps to backend ConsentRequest DTO.
 */
export interface ConsentRequest {
  consentType: ConsentType;
  granted: boolean;
  /** Server-side populated — client IP address at the time of consent. */
  ipAddress?: string;
  /** Server-side populated — browser user agent string at the time of consent. */
  userAgent?: string;
}

/**
 * Full user data export for RGPD portability.
 * Maps to backend UserDataExportResponse DTO.
 */
export interface UserDataExport {
  userId: number;
  email: string;
  firstName: string;
  lastName: string;
  phone: string | null;
  role: string;
  status: string;
  createdAt: string;
  lastLoginAt: string | null;
  family: FamilyExport | null;
  consentHistory: ConsentStatus[];
  exportedAt: string;
}

/**
 * Family data within a user data export.
 */
export interface FamilyExport {
  familyId: number;
  familyName: string;
  members: FamilyMemberExport[];
}

/**
 * Family member data within a user data export.
 */
export interface FamilyMemberExport {
  memberId: number;
  firstName: string;
  lastName: string;
  dateOfBirth: string | null;
  relationship: string;
}

/**
 * Configuration for consent type display with French labels, descriptions, and required flag.
 */
export const CONSENT_LABELS: Record<ConsentType, { label: string; description: string; required: boolean }> = {
  TERMS_OF_SERVICE: {
    label: "Conditions d'utilisation",
    description: "J'accepte les conditions g\u00e9n\u00e9rales d'utilisation de la plateforme.",
    required: true,
  },
  DATA_PROCESSING: {
    label: 'Traitement des donn\u00e9es',
    description: "J'autorise le traitement de mes donn\u00e9es personnelles conform\u00e9ment \u00e0 la politique de confidentialit\u00e9.",
    required: true,
  },
  MARKETING_EMAIL: {
    label: 'Emails marketing',
    description: "J'accepte de recevoir des communications marketing par email.",
    required: false,
  },
  THIRD_PARTY_SHARING: {
    label: 'Partage avec des tiers',
    description: "J'autorise le partage de mes donn\u00e9es avec les associations partenaires.",
    required: false,
  },
};
