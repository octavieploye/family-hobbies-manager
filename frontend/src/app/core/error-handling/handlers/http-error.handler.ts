import { HttpErrorResponse } from '@angular/common/http';
import { ApiError, ErrorCode, isApiError } from '../models';

export interface ParsedError {
  status: number;
  errorCode: ErrorCode;
  message: string;
  details?: { field: string; message: string }[];
  isRetryable: boolean;
}

export function parseHttpError(error: HttpErrorResponse): ParsedError {
  // If the backend returned a structured ApiError
  if (error.error && isApiError(error.error)) {
    const apiError = error.error as ApiError;
    return {
      status: apiError.status,
      errorCode: apiError.errorCode as ErrorCode,
      message: mapToFrenchMessage(apiError.status, apiError.message),
      details: apiError.details,
      isRetryable: isRetryableError(apiError.status),
    };
  }

  // Network error (no response from server)
  if (error.status === 0) {
    return {
      status: 0,
      errorCode: ErrorCode.NETWORK_ERROR,
      message: 'Connexion au serveur impossible. Vérifiez votre connexion internet.',
      isRetryable: true,
    };
  }

  // Generic HTTP error (no structured body)
  return {
    status: error.status,
    errorCode: mapStatusToErrorCode(error.status),
    message: mapToFrenchMessage(error.status, error.message),
    isRetryable: isRetryableError(error.status),
  };
}

function mapToFrenchMessage(status: number, fallback: string): string {
  const messages: Record<number, string> = {
    400: 'Les données envoyées sont invalides.',
    401: 'Votre session a expiré. Veuillez vous reconnecter.',
    403: 'Vous n\'avez pas les droits nécessaires pour cette action.',
    404: 'La ressource demandée est introuvable.',
    409: 'Un conflit a été détecté. Les données ont peut-être été modifiées.',
    422: 'L\'opération ne peut pas être effectuée en raison d\'une règle métier.',
    429: 'Trop de requêtes. Veuillez patienter avant de réessayer.',
    500: 'Une erreur interne est survenue. Veuillez réessayer ultérieurement.',
    502: 'Le service est temporairement indisponible.',
    503: 'Le service est en maintenance. Veuillez réessayer dans quelques instants.',
    504: 'Le serveur a mis trop de temps à répondre.',
  };
  return messages[status] || fallback || 'Une erreur inattendue est survenue.';
}

function mapStatusToErrorCode(status: number): ErrorCode {
  const mapping: Record<number, ErrorCode> = {
    400: ErrorCode.VALIDATION_FAILED,
    401: ErrorCode.UNAUTHORIZED,
    403: ErrorCode.FORBIDDEN,
    404: ErrorCode.RESOURCE_NOT_FOUND,
    409: ErrorCode.CONFLICT,
    422: ErrorCode.UNPROCESSABLE_ENTITY,
    429: ErrorCode.TOO_MANY_REQUESTS,
    500: ErrorCode.INTERNAL_SERVER_ERROR,
    502: ErrorCode.BAD_GATEWAY,
    503: ErrorCode.SERVICE_UNAVAILABLE,
    504: ErrorCode.GATEWAY_TIMEOUT,
  };
  return mapping[status] || ErrorCode.UNKNOWN;
}

function isRetryableError(status: number): boolean {
  return [0, 408, 429, 500, 502, 503, 504].includes(status);
}
