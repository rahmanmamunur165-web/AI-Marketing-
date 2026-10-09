import * as cheerio from 'cheerio';

export interface ExtractedEmailRecord {
  email: string;
  domain: string;
  sourceUrl: string;
  companyName: string | null;
  validationStatus: 'ROLE_ACCOUNT' | 'PUBLIC_BUSINESS' | 'SYNTAX_VALID';
}

const EMAIL_REGEX = /[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,10}/g;

const ROLE_PREFIXES = new Set([
  'info', 'contact', 'sales', 'support', 'hello', 'team', 'office',
  'press', 'media', 'help', 'billing', 'careers', 'jobs', 'inquiries',
  'partnerships', 'privacy', 'admin'
]);

const IGNORED_EXTENSIONS = new Set([
  'png', 'jpg', 'jpeg', 'gif', 'svg', 'webp', 'ico', 'js', 'css', 'pdf', 'zip'
]);

const IGNORED_DOMAINS = new Set([
  'example.com', 'sample.com', 'domain.com', 'w3.org', 'schema.org', 'sentry.io', 'cloudflare.com'
]);

export function extractPublicEmailsFromHtml(
  html: string,
  pageUrl: string,
  rootDomain: string
): ExtractedEmailRecord[] {
  const $ = cheerio.load(html);
  const foundMap = new Map<string, ExtractedEmailRecord>();

  const companyName = detectCompany($, rootDomain);

  // 1. Parse mailto links
  $('a[href^="mailto:"]').each((_, el) => {
    const href = $(el).attr('href') || '';
    const rawEmail = href.replace(/^mailto:/i, '').split('?')[0].trim().toLowerCase();
    if (isValidBusinessEmail(rawEmail)) {
      foundMap.set(rawEmail, {
        email: rawEmail,
        domain: rawEmail.split('@')[1],
        sourceUrl: pageUrl,
        companyName,
        validationStatus: classifyEmail(rawEmail),
      });
    }
  });

  // 2. Parse text content
  const bodyText = $('body').text();
  const textMatches = bodyText.match(EMAIL_REGEX) || [];
  for (const raw of textMatches) {
    const clean = raw.trim().toLowerCase();
    if (isValidBusinessEmail(clean) && !foundMap.has(clean)) {
      foundMap.set(clean, {
        email: clean,
        domain: clean.split('@')[1],
        sourceUrl: pageUrl,
        companyName,
        validationStatus: classifyEmail(clean),
      });
    }
  }

  return Array.from(foundMap.values());
}

function classifyEmail(email: string): 'ROLE_ACCOUNT' | 'PUBLIC_BUSINESS' {
  const prefix = email.split('@')[0];
  return ROLE_PREFIXES.has(prefix) ? 'ROLE_ACCOUNT' : 'PUBLIC_BUSINESS';
}

function isValidBusinessEmail(email: string): boolean {
  if (!email.includes('@')) return false;
  const parts = email.split('@');
  if (parts.length !== 2) return false;
  const [local, domain] = parts;
  if (local.length < 2 || domain.length < 4) return false;

  const ext = domain.split('.').pop() || '';
  if (IGNORED_EXTENSIONS.has(ext)) return false;
  if (IGNORED_DOMAINS.has(domain)) return false;

  return true;
}

function detectCompany($: cheerio.CheerioAPI, fallbackDomain: string): string | null {
  const ogSiteName = $('meta[property="og:site_name"]').attr('content');
  if (ogSiteName && ogSiteName.trim().length > 0) return ogSiteName.trim();

  const title = $('title').text();
  if (title) {
    const clean = title.split(/[-|•]/)[0].trim();
    if (clean.length > 0 && clean.length < 40) return clean;
  }

  return fallbackDomain.replace(/^www\./, '').split('.')[0];
}
