/** Download failures carry a user-facing explanation and retain the original technical cause. */
export class TemplateDownloadError extends Error {
  constructor(message: string, readonly technicalCause?: unknown) {
    super(message);
    this.name = "TemplateDownloadError";
  }
}

export const TEMPLATE_FETCH_TIMEOUT_MS = 3000;

export function describeTemplateFailure(error: unknown, url: string, timedOut = false): string {
  const prefix = `Could not load the INTERLIS template from ${url}.`;
  if (timedOut) return `${prefix} The request timed out after ${TEMPLATE_FETCH_TIMEOUT_MS / 1000} seconds. Check the server, network, VPN or proxy.`;
  const seen = new Set<unknown>();
  for (let cause: any = error; cause && !seen.has(cause); cause = cause.cause) {
    seen.add(cause);
    const code = String(cause.code ?? "");
    if (["ENOTFOUND", "EAI_AGAIN"].includes(code)) return `${prefix} The server name could not be resolved. Check the address, DNS, network or VPN.`;
    if (["ETIMEDOUT", "UND_ERR_CONNECT_TIMEOUT", "UND_ERR_HEADERS_TIMEOUT", "UND_ERR_BODY_TIMEOUT"].includes(code)) return `${prefix} The network request timed out. Check the server, network, VPN or proxy.`;
    if (["ECONNREFUSED", "ENETUNREACH", "EHOSTUNREACH", "ECONNRESET"].includes(code)) return `${prefix} A connection could not be established or was interrupted. Check the server, network, VPN or proxy.`;
    if (code.startsWith("ERR_TLS_") || code.startsWith("ERR_SSL_") || ["DEPTH_ZERO_SELF_SIGNED_CERT", "SELF_SIGNED_CERT_IN_CHAIN", "UNABLE_TO_VERIFY_LEAF_SIGNATURE", "UNABLE_TO_GET_ISSUER_CERT_LOCALLY", "CERT_HAS_EXPIRED"].includes(code)) {
      return `${prefix} A secure connection could not be established. Check the server certificate and proxy settings.`;
    }
  }
  const detail = error instanceof Error ? error.message : String(error);
  return `${prefix} ${detail}`;
}

export async function fetchTemplateContent(url: string, fetcher: typeof fetch = fetch): Promise<string> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), TEMPLATE_FETCH_TIMEOUT_MS);
  try {
    const response = await fetcher(url, { signal: controller.signal });
    if (!response.ok) {
      throw new TemplateDownloadError(
        `Could not load the INTERLIS template from ${url}. The server returned HTTP ${response.status}${response.statusText ? ` ${response.statusText}` : ""}. Check the address and server access.`,
        new Error(`HTTP ${response.status} ${response.statusText}`)
      );
    }
    const content = await response.text();
    if (!content.trim()) throw new TemplateDownloadError(`Could not load the INTERLIS template from ${url}. The server returned an empty response.`);
    return content;
  } catch (error) {
    if (error instanceof TemplateDownloadError) throw error;
    throw new TemplateDownloadError(describeTemplateFailure(error, url, controller.signal.aborted), error);
  } finally {
    clearTimeout(timeout);
  }
}
