import { ErrorCode } from './error-code.enum';

export interface FieldError {
  field: string;
  message: string;
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  correlationId?: string;
  errorCode: ErrorCode;
  details?: FieldError[];
}

export function isApiError(obj: unknown): obj is ApiError {
  return (
    typeof obj === 'object' &&
    obj !== null &&
    'status' in obj &&
    'message' in obj &&
    'errorCode' in obj
  );
}
