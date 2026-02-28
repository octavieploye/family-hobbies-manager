import { APIRequestContext, request } from '@playwright/test';
import { API_CONFIG } from '../fixtures/test-data';

/**
 * Helper for direct REST API calls to the backend through api-gateway.
 *
 * Used for:
 * - Test setup: creating seed data via API instead of UI
 * - Test teardown: cleaning up created resources
 * - Verification: checking backend state independent of the UI
 * - Webhook simulation: triggering payment webhooks
 *
 * All methods use the api-gateway base URL (default: http://localhost:8080).
 */
export class ApiHelper {
  private context: APIRequestContext | null = null;
  private authToken: string | null = null;

  /**
   * Initialize the API request context.
   */
  async init(): Promise<void> {
    this.context = await request.newContext({
      baseURL: API_CONFIG.baseUrl,
      extraHTTPHeaders: {
        'Content-Type': 'application/json',
        'Accept-Language': 'fr-FR',
      },
    });
  }

  /**
   * Dispose the API request context.
   */
  async dispose(): Promise<void> {
    if (this.context) {
      await this.context.dispose();
      this.context = null;
    }
  }

  /**
   * Authenticate and store the JWT token for subsequent requests.
   */
  async authenticate(email: string, password: string): Promise<string> {
    this.ensureContext();
    const response = await this.context!.post('/api/auth/login', {
      data: { email, password },
    });

    if (!response.ok()) {
      throw new Error(
        `Authentication failed: ${response.status()} ${response.statusText()}`
      );
    }

    const body = await response.json();
    this.authToken = body.token;
    return this.authToken!;
  }

  /**
   * Make an authenticated GET request.
   */
  async get<T>(endpoint: string): Promise<T> {
    this.ensureContext();
    this.ensureAuthenticated();

    const response = await this.context!.get(endpoint, {
      headers: { Authorization: `Bearer ${this.authToken}` },
    });

    if (!response.ok()) {
      throw new Error(
        `GET ${endpoint} failed: ${response.status()} ${response.statusText()}`
      );
    }

    return response.json() as Promise<T>;
  }

  /**
   * Make an authenticated POST request.
   */
  async post<T>(endpoint: string, data: unknown): Promise<T> {
    this.ensureContext();
    this.ensureAuthenticated();

    const response = await this.context!.post(endpoint, {
      data,
      headers: { Authorization: `Bearer ${this.authToken}` },
    });

    if (!response.ok()) {
      throw new Error(
        `POST ${endpoint} failed: ${response.status()} ${response.statusText()}`
      );
    }

    return response.json() as Promise<T>;
  }

  /**
   * Make an authenticated DELETE request.
   */
  async delete(endpoint: string): Promise<void> {
    this.ensureContext();
    this.ensureAuthenticated();

    const response = await this.context!.delete(endpoint, {
      headers: { Authorization: `Bearer ${this.authToken}` },
    });

    if (!response.ok()) {
      throw new Error(
        `DELETE ${endpoint} failed: ${response.status()} ${response.statusText()}`
      );
    }
  }

  /**
   * Simulate a HelloAsso webhook callback.
   * This bypasses the signature validation for test purposes.
   */
  async simulateWebhook(
    eventType: string,
    payload: Record<string, unknown>
  ): Promise<void> {
    this.ensureContext();

    const response = await this.context!.post('/api/webhooks/helloasso', {
      data: {
        eventType,
        data: payload,
      },
      headers: {
        'X-HelloAsso-Signature': 'e2e-test-signature',
        'X-E2E-Test': 'true',
      },
    });

    if (!response.ok()) {
      throw new Error(
        `Webhook simulation failed: ${response.status()} ${response.statusText()}`
      );
    }
  }

  /**
   * Verify that a payment has the expected status in the database.
   */
  async verifyPaymentStatus(
    paymentId: string,
    expectedStatus: string
  ): Promise<boolean> {
    const payment = await this.get<{ status: string }>(
      `/api/payments/${paymentId}`
    );
    return payment.status === expectedStatus;
  }

  /**
   * Get the current user's notification count.
   */
  async getUnreadNotificationCount(): Promise<number> {
    const response = await this.get<{ count: number }>(
      '/api/notifications/unread/count'
    );
    return response.count;
  }

  /**
   * Request a RGPD data export via API.
   */
  async requestDataExport(): Promise<{ exportId: string; status: string }> {
    return this.post('/api/users/me/export', {});
  }

  /**
   * Clean up a test user by email (for registration test teardown).
   */
  async deleteTestUser(email: string): Promise<void> {
    // Authenticate as admin first
    const adminToken = this.authToken;
    await this.authenticate('admin@test.familyhobbies.fr', 'Admin1234!');
    await this.delete(`/api/admin/users/by-email/${encodeURIComponent(email)}`);
    // Restore previous token
    this.authToken = adminToken;
  }

  private ensureContext(): void {
    if (!this.context) {
      throw new Error(
        'ApiHelper not initialized. Call init() before making requests.'
      );
    }
  }

  private ensureAuthenticated(): void {
    if (!this.authToken) {
      throw new Error(
        'Not authenticated. Call authenticate() before making authenticated requests.'
      );
    }
  }
}
