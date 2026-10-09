import express, { Request, Response, NextFunction } from 'express';
import helmet from 'helmet';
import cors from 'cors';
import cookieParser from 'cookie-parser';
import rateLimit from 'express-rate-limit';
import { z } from 'zod';
import { validateUrlAgainstSsrf } from './security/ssrf.js';
import { buildContactCsv } from './modules/exports/csv.service.js';
import { GoogleOAuthService } from './modules/google-oauth/oauth.service.js';

const app = express();
const PORT = process.env.PORT || 4000;
const oauthService = new GoogleOAuthService();

// Standard Security Headers & Middlewares
app.use(helmet());
app.use(
  cors({
    origin: process.env.APP_BASE_URL || 'http://localhost:5173',
    credentials: true,
  })
);
app.use(express.json({ limit: '1mb' }));
app.use(cookieParser(process.env.SESSION_SECRET || 'secret'));

// Global Rate Limiter
app.use(
  rateLimit({
    windowMs: 15 * 60 * 1000,
    max: 200,
    message: { error: 'Too many requests, please try again later.' },
  })
);

// Health check
app.get('/health', (req, res) => {
  res.json({ status: 'ok', service: 'ContactFlow API', timestamp: new Date().toISOString() });
});

// Mock/Simple Session Auth Middleware for development
function requireAuth(req: Request, res: Response, next: NextFunction) {
  // In production, check signed session cookie or JWT
  req.userId = 'demo-user-id';
  next();
}

declare global {
  namespace Express {
    interface Request {
      userId?: string;
    }
  }
}

// ================= API V1 ROUTES ================= //

// --- Authentication ---
app.post('/api/v1/auth/register', (req, res) => {
  res.json({ message: 'User registered successfully', userId: 'demo-user-id' });
});

app.post('/api/v1/auth/login', (req, res) => {
  res.cookie('cf_session', 'demo-token', { httpOnly: true, secure: process.env.NODE_ENV === 'production', sameSite: 'lax' });
  res.json({ message: 'Logged in successfully', user: { email: 'demo@contactflow.app' } });
});

app.get('/api/v1/auth/me', requireAuth, (req, res) => {
  res.json({ user: { id: req.userId, email: 'demo@contactflow.app' } });
});

app.post('/api/v1/auth/logout', (req, res) => {
  res.clearCookie('cf_session');
  res.json({ message: 'Logged out successfully' });
});

// --- Scans ---
const ScanInputSchema = z.object({
  url: z.string().url(),
  maxPages: z.number().int().min(1).max(100).default(25),
});

app.post('/api/v1/scans', requireAuth, async (req, res) => {
  const parsed = ScanInputSchema.safeParse(req.body);
  if (!parsed.success) {
    return res.status(400).json({ error: parsed.error.format() });
  }

  // SSRF Defense Verification before scheduling scan
  const ssrfCheck = await validateUrlAgainstSsrf(parsed.data.url);
  if (!ssrfCheck.valid) {
    return res.status(400).json({ error: `Security check failed: ${ssrfCheck.reason}` });
  }

  // Enqueue job in BullMQ
  const scanJob = {
    id: 'scan_' + Date.now(),
    submittedUrl: ssrfCheck.normalizedUrl,
    domain: ssrfCheck.hostname,
    status: 'QUEUED',
    maxPages: parsed.data.maxPages,
    createdAt: new Date().toISOString(),
  };

  res.status(202).json({ message: 'Scan job queued', scan: scanJob });
});

app.get('/api/v1/scans', requireAuth, (req, res) => {
  res.json({ scans: [] });
});

app.post('/api/v1/scans/:id/cancel', requireAuth, (req, res) => {
  res.json({ message: `Scan ${req.params.id} cancelled` });
});

// --- Contacts ---
app.get('/api/v1/contacts', requireAuth, (req, res) => {
  res.json({ contacts: [], total: 0 });
});

app.delete('/api/v1/contacts/:id', requireAuth, (req, res) => {
  res.json({ message: 'Contact deleted' });
});

app.post('/api/v1/contacts/bulk-delete', requireAuth, (req, res) => {
  res.json({ message: 'Contacts deleted' });
});

// --- CSV Export with formula injection sanitization ---
app.post('/api/v1/exports/csv', requireAuth, (req, res) => {
  const sampleContacts = [
    {
      email: 'contact@example.com',
      domain: 'example.com',
      companyName: 'Example Inc',
      sourceUrl: 'https://example.com/contact',
      discoveredAt: new Date(),
      validationStatus: 'ROLE_ACCOUNT',
    },
  ];

  const csvContent = buildContactCsv(sampleContacts);
  res.setHeader('Content-Type', 'text/csv; charset=utf-8');
  res.setHeader('Content-Disposition', 'attachment; filename="contactflow_export.csv"');
  res.send(csvContent);
});

// --- Google OAuth & Gmail Integration ---
app.get('/api/v1/integrations/google/connect', requireAuth, (req, res) => {
  const { url } = oauthService.generateAuthUrl(req.userId || 'demo');
  res.json({ authUrl: url });
});

app.get('/api/v1/integrations/google/callback', async (req, res) => {
  const code = req.query.code as string;
  if (!code) return res.status(400).send('Authorization code missing');

  try {
    const tokens = await oauthService.exchangeCodeForTokens(code);
    res.redirect(`${process.env.APP_BASE_URL}/settings?google_connected=true&email=${encodeURIComponent(tokens.email)}`);
  } catch (err: any) {
    res.status(500).send(`OAuth Error: ${err.message}`);
  }
});

app.get('/api/v1/integrations/google/status', requireAuth, (req, res) => {
  res.json({ isConnected: true, email: 'demo@gmail.com', scopes: 'gmail.send, profile, email' });
});

app.delete('/api/v1/integrations/google/disconnect', requireAuth, (req, res) => {
  res.json({ message: 'Google account disconnected' });
});

// --- Campaigns ---
app.post('/api/v1/campaigns', requireAuth, (req, res) => {
  res.status(201).json({ message: 'Campaign draft created', campaignId: 'camp_' + Date.now() });
});

app.get('/api/v1/campaigns', requireAuth, (req, res) => {
  res.json({ campaigns: [] });
});

app.post('/api/v1/campaigns/:id/approve', requireAuth, (req, res) => {
  res.json({ message: `Campaign ${req.params.id} approved. Outreach queued with rate limiting.` });
});

// --- Suppressions ---
app.get('/api/v1/suppressions', requireAuth, (req, res) => {
  res.json({ suppressions: [] });
});

app.post('/api/v1/suppressions', requireAuth, (req, res) => {
  res.status(201).json({ message: 'Email added to suppression list' });
});

app.listen(PORT, () => {
  console.log(`[ContactFlow API] Running on port ${PORT}`);
});
