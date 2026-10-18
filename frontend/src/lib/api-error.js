/**
 * Normalized API error built from the backend's RFC 9457 problem details.
 * `code` is the stable contract (see docs/04-api-contracts.md); `message` is safe to show.
 */
export class ApiError extends Error {
  constructor({ status, code, message, fieldErrors = [], details = {}, correlationId = null }) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
    this.details = details;
    this.correlationId = correlationId;
  }
}

export function isApiError(error) {
  return error instanceof ApiError;
}

export function toApiError(axiosError) {
  if (isApiError(axiosError)) return axiosError;
  const response = axiosError?.response;
  if (!response) {
    const timeout = axiosError?.code === "ECONNABORTED";
    return new ApiError({
      status: 0,
      code: timeout ? "TIMEOUT" : "NETWORK_ERROR",
      message: timeout
        ? "The server took too long to respond. Check the connection and try again."
        : "Cannot reach the server. Check the network connection.",
    });
  }
  const body = response.data && typeof response.data === "object" ? response.data : {};
  return new ApiError({
    status: response.status,
    code: body.code || `HTTP_${response.status}`,
    message: body.detail || body.title || `Request failed (${response.status})`,
    fieldErrors: Array.isArray(body.fieldErrors) ? body.fieldErrors : [],
    details: body.details || {},
    correlationId: body.correlationId || response.headers?.["x-correlation-id"] || null,
  });
}

/** A user-facing message for any thrown value. */
export function errorMessage(error, fallback = "Something went wrong.") {
  if (isApiError(error)) {
    return error.correlationId && error.status >= 500
      ? `${error.message} (ref ${error.correlationId.slice(0, 8)})`
      : error.message;
  }
  return error?.message || fallback;
}

/**
 * Copies server-side field errors into react-hook-form. Returns true when at least one
 * field error was applied (so the caller can skip a generic toast).
 */
export function applyFieldErrors(error, setError, fieldMap = {}) {
  if (!isApiError(error) || error.fieldErrors.length === 0) return false;
  let applied = false;
  for (const fe of error.fieldErrors) {
    const name = fieldMap[fe.field] || fe.field;
    if (name) {
      setError(name, { type: "server", message: fe.message });
      applied = true;
    }
  }
  return applied;
}
