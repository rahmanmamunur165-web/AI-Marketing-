import crypto from 'node:crypto';
import { google } from 'googleapis';

const ALGORITHM = 'aes-256-gcm';

export class GoogleOAuthService {
  private oauth2Client;
  private encryptionKey: Buffer;

  constructor() {
    this.oauth2Client = new google.auth.OAuth2(
      process.env.GOOGLE_CLIENT_ID,
      process.env.GOOGLE_CLIENT_SECRET,
      process.env.GOOGLE_REDIRECT_URI
    );

    const rawKey = process.env.TOKEN_ENCRYPTION_KEY || '01234567890123456789012345678901';
    this.encryptionKey = crypto.createHash('sha256').update(rawKey).digest();
  }

  generateAuthUrl(userId: string): { url: string; state: string } {
    const state = crypto.randomBytes(24).toString('hex') + ':' + userId;
    const url = this.oauth2Client.generateAuthUrl({
      access_type: 'offline',
      prompt: 'consent',
      scope: [
        'https://www.googleapis.com/auth/gmail.send',
        'https://www.googleapis.com/auth/userinfo.email',
        'https://www.googleapis.com/auth/userinfo.profile',
      ],
      state,
    });
    return { url, state };
  }

  async exchangeCodeForTokens(code: string) {
    const { tokens } = await this.oauth2Client.getToken(code);
    this.oauth2Client.setCredentials(tokens);

    const oauth2 = google.oauth2({ version: 'v2', auth: this.oauth2Client });
    const userInfo = await oauth2.userinfo.get();

    return {
      email: userInfo.data.email || '',
      refreshToken: tokens.refresh_token,
      encryptedRefreshToken: tokens.refresh_token ? this.encryptToken(tokens.refresh_token) : null,
      grantedScopes: tokens.scope || '',
    };
  }

  encryptToken(token: string): string {
    const iv = crypto.randomBytes(16);
    const cipher = crypto.createCipheriv(ALGORITHM, this.encryptionKey, iv);
    let encrypted = cipher.update(token, 'utf8', 'hex');
    encrypted += cipher.final('hex');
    const authTag = cipher.getAuthTag().toString('hex');
    return `${iv.toString('hex')}:${authTag}:${encrypted}`;
  }

  decryptToken(encryptedPayload: string): string {
    const [ivHex, authTagHex, encryptedText] = encryptedPayload.split(':');
    const decipher = crypto.createDecipheriv(ALGORITHM, this.encryptionKey, Buffer.from(ivHex, 'hex'));
    decipher.setAuthTag(Buffer.from(authTagHex, 'hex'));
    let decrypted = decipher.update(encryptedText, 'hex', 'utf8');
    decrypted += decipher.final('utf8');
    return decrypted;
  }

  async sendEmailViaGmailApi(params: {
    encryptedRefreshToken: string;
    senderEmail: string;
    to: string;
    subject: string;
    bodyText: string;
  }): Promise<{ messageId: string }> {
    const refreshToken = this.decryptToken(params.encryptedRefreshToken);
    const client = new google.auth.OAuth2(
      process.env.GOOGLE_CLIENT_ID,
      process.env.GOOGLE_CLIENT_SECRET,
      process.env.GOOGLE_REDIRECT_URI
    );
    client.setCredentials({ refresh_token: refreshToken });

    const gmail = google.gmail({ version: 'v1', auth: client });

    // RFC 2822 email format
    const utf8Subject = `=?utf-8?B?${Buffer.from(params.subject).toString('base64')}?=`;
    const messageParts = [
      `From: <${params.senderEmail}>`,
      `To: <${params.to}>`,
      `Subject: ${utf8Subject}`,
      'Content-Type: text/plain; charset=utf-8',
      'MIME-Version: 1.0',
      '',
      params.bodyText,
    ];
    const message = messageParts.join('\r\n');
    const encodedMessage = Buffer.from(message)
      .toString('base64')
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=+$/, '');

    const res = await gmail.users.messages.send({
      userId: 'me',
      requestBody: { raw: encodedMessage },
    });

    return { messageId: res.data.id || 'submitted' };
  }
}
