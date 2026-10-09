import dns from 'node:dns/promises';
import { URL } from 'node:url';

export interface SsrCheckResult {
  valid: boolean;
  reason?: string;
  normalizedUrl?: string;
  hostname?: string;
}

const BLOCKED_HOSTS = new Set([
  'localhost',
  '127.0.0.1',
  '::1',
  '0.0.0.0',
  'metadata.google.internal',
  '169.254.169.254',
  'instance-data',
]);

export async function validateUrlAgainstSsrf(inputUrl: string): Promise<SsrCheckResult> {
  try {
    const parsed = new URL(inputUrl);

    // Protocol enforcement
    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
      return { valid: false, reason: 'Only HTTP and HTTPS protocols are allowed.' };
    }

    // Safe Port enforcement
    if (parsed.port && parsed.port !== '80' && parsed.port !== '443') {
      return { valid: false, reason: `Port ${parsed.port} is restricted. Only standard web ports (80, 443) permitted.` };
    }

    const host = parsed.hostname.toLowerCase();

    // Check blocklist & cloud metadata names
    if (
      BLOCKED_HOSTS.has(host) ||
      host.endsWith('.local') ||
      host.endsWith('.internal') ||
      host.endsWith('.localhost') ||
      host.includes('metadata.google')
    ) {
      return { valid: false, reason: 'Target host is in a reserved or private cloud metadata range.' };
    }

    // DNS Resolution check to prevent DNS rebinding to internal IP
    const addresses = await dns.lookup(host, { all: true });
    for (const record of addresses) {
      if (isPrivateIp(record.address)) {
        return { valid: false, reason: `Domain resolves to forbidden private/internal IP: ${record.address}` };
      }
    }

    return {
      valid: true,
      normalizedUrl: parsed.toString(),
      hostname: host,
    };
  } catch (error: any) {
    return { valid: false, reason: error.message || 'Malformed URL format' };
  }
}

export function isPrivateIp(ip: string): boolean {
  // IPv4 Loopback (127.0.0.0/8)
  if (ip.startsWith('127.')) return true;
  // Class A Private (10.0.0.0/8)
  if (ip.startsWith('10.')) return true;
  // Class B Private (172.16.0.0/12)
  if (/^172\.(1[6-9]|2\d|3[01])\./.test(ip)) return true;
  // Class C Private (192.168.0.0/16)
  if (ip.startsWith('192.168.')) return true;
  // Link Local & AWS/GCP Metadata (169.254.0.0/16)
  if (ip.startsWith('169.254.')) return true;
  // IPv6 loopback & link-local
  if (ip === '::1' || ip.startsWith('fe80:')) return true;

  return false;
}
