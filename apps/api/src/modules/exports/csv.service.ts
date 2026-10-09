export interface CsvContactItem {
  email: string;
  domain: string;
  companyName: string | null;
  sourceUrl: string;
  discoveredAt: Date | string;
  validationStatus: string;
}

const DANGEROUS_FORMULA_CHARS = new Set(['=', '+', '-', '@', '\t', '\r', '%']);

export function sanitizeFieldForCsv(value: string | null | undefined): string {
  if (!value) return '""';
  let str = value.trim();

  // Defense against CSV / Spreadsheet Formula Injection (CWE-1236)
  if (DANGEROUS_FORMULA_CHARS.has(str.charAt(0))) {
    str = `'${str}`; // Prepend single quote
  }

  // RFC 4180 Escaping: double quotes inside must be escaped as two double quotes
  const escaped = str.replace(/"/g, '""');
  return `"${escaped}"`;
}

export function buildContactCsv(records: CsvContactItem[]): string {
  // UTF-8 BOM prefix for Microsoft Excel compatibility
  const BOM = '\uFEFF';
  const headers = 'email,domain,company_name,source_url,discovered_at,validation_status\n';

  const rows = records.map((r) => {
    const email = sanitizeFieldForCsv(r.email);
    const domain = sanitizeFieldForCsv(r.domain);
    const company = sanitizeFieldForCsv(r.companyName || '');
    const sourceUrl = sanitizeFieldForCsv(r.sourceUrl);
    const dateStr = sanitizeFieldForCsv(new Date(r.discoveredAt).toISOString());
    const status = sanitizeFieldForCsv(r.validationStatus);
    return `${email},${domain},${company},${sourceUrl},${dateStr},${status}`;
  });

  return BOM + headers + rows.join('\n');
}
