// frontend/src/app/features/association/models/association.model.ts
// TypeScript interfaces matching backend Association DTOs

/**
 * Summary representation of an association, used in search results / list views.
 * Maps to backend AssociationSummaryResponse.
 */
export interface Association {
  id: number;
  name: string;
  slug: string;
  city: string;
  postalCode: string;
  category: string;
  status: string;
  logoUrl?: string;
}

/**
 * Full detail of a single association.
 * Maps to backend AssociationDetailResponse.
 */
export interface AssociationDetail {
  id: number;
  name: string;
  slug: string;
  description?: string;
  address?: string;
  city: string;
  postalCode: string;
  department?: string;
  region?: string;
  phone?: string;
  email?: string;
  website?: string;
  logoUrl?: string;
  helloassoSlug?: string;
  category: string;
  status: string;
  lastSyncedAt?: string;
  createdAt: string;
  updatedAt: string;
}

/**
 * Search request parameters for the association search endpoint.
 * All fields are optional; omitted fields are not sent as query params.
 */
export interface AssociationSearchRequest {
  city?: string;
  category?: string;
  keyword?: string;
  page?: number;
  size?: number;
}

/**
 * Generic paginated response matching Spring Boot Page<T>.
 * Re-usable for any entity.
 */
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
