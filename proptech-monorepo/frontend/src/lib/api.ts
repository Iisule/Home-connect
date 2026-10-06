/**
 * Thin fetch wrapper for the Spring Boot API.
 *
 * In the browser we call the same-origin "/api/..." path, which vercel.json rewrites to the real
 * backend (avoiding CORS entirely once deployed). In local dev without the Vercel rewrite layer,
 * NEXT_PUBLIC_API_URL is used directly instead - set it in .env.local.
 */
const API_BASE =
  typeof window !== "undefined" && window.location.hostname !== "localhost" && window.location.hostname !== "127.0.0.1"
    ? "" // same-origin; vercel.json rewrites /api/* to the backend
    : process.env.NEXT_PUBLIC_API_URL;

export class ApiError extends Error {
  status: number;
  code?: string;
  details?: Record<string, unknown>;
  constructor(status: number, message: string, code?: string, details?: Record<string, unknown>) {
    super(message);
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

function authHeaders(): Record<string, string> {
  if (typeof window === "undefined") return {};
  const token = window.localStorage.getItem("proptech_token");
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      ...(options.body instanceof FormData ? {} : { "Content-Type": "application/json" }),
      ...authHeaders(),
      ...(options.headers || {}),
    },
  });

  if (!res.ok) {
    let body: any = {};
    try {
      body = await res.json();
    } catch {
      /* non-JSON error body */
    }
    throw new ApiError(res.status, body.message || res.statusText, body.code, body.details);
  }
  if (res.status === 204) return undefined as T;
  return res.json();
}

export const api = {
  get: <T>(path: string) => request<T>(path, { method: "GET" }),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: "POST", body: body instanceof FormData ? body : JSON.stringify(body ?? {}) }),
};

export function saveToken(token: string) {
  if (typeof window !== "undefined") window.localStorage.setItem("proptech_token", token);
}

export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem("proptech_token");
}

// --- Types mirroring the backend DTOs (see backend/src/main/java/ng/proptech/dto) ------------------------

export interface StateSummary { id: string; name: string; code: string }
export interface LgaSummary { id: string; name: string; isUrban: boolean }
export interface SettlementSummary { id: string; name: string }
export interface EstateSummary { id: string; name: string; isGated: boolean }

export type PropertyType = "SELF_CONTAIN" | "ROOM_AND_PARLOUR" | "TWO_BEDROOM" | "THREE_BEDROOM" | "DUPLEX" | "SHOP";

export interface PropertySummary {
  id: string;
  title: string;
  propertyType: PropertyType;
  lgaName: string;
  settlementName: string;
  estateName: string | null;
  landmarkDescription: string;
  primaryImageUrl: string | null;
  status: string;
  aiFlagged: boolean;
  competingListingCount: number;
  lowestAnnualRent: number | null;
  createdAt: string;
}

export interface AgentOffer {
  listingId: string;
  agentId: string;
  agentName: string;
  annualRent: number;
  agencyFee: number;
  totalPayable: number;
  agentRating: number;
  dealsClosed: number;
  avgResponseMinutes: number;
  status: string;
}

export interface PropertyDetail {
  id: string;
  title: string;
  propertyType: PropertyType;
  lgaName: string;
  settlementName: string;
  estateName: string | null;
  landmarkDescription: string;
  photoUrls: string[];
  status: string;
  offers: AgentOffer[];
}

export interface AuthResponse {
  token: string;
  tokenType: string;
  expiresInMinutes: number;
  user: { id: string; fullName: string; email: string; phoneNumber: string; role: string; ninVerified: boolean };
}

export interface CheckoutResponse {
  escrowId: string;
  annualRent: number;
  agencyFee: number;
  totalAmount: number;
  status: string;
}

export interface ConfirmPaymentResponse {
  escrowId: string;
  status: string;
  otpForDemoOnly: string;
}

export interface ReleaseResponse {
  escrowId: string;
  status: string;
  platformFee: number;
  logisticsFee: number;
  netToAgent: number;
  payoutReference: string;
}

export function formatNaira(amount: number | null | undefined): string {
  if (amount == null) return "\u2014";
  return "\u20a6" + amount.toLocaleString("en-NG", { maximumFractionDigits: 0 });
}
