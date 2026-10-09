import React, { useState } from 'react';
import {
  LayoutDashboard,
  Radar,
  Users,
  Send,
  Settings,
  Mail,
  Download,
  Shield,
  CheckCircle,
  AlertTriangle,
  Play,
  Trash2,
  ExternalLink
} from 'lucide-react';

interface Contact {
  id: string;
  email: string;
  domain: string;
  companyName: string;
  sourceUrl: string;
  validationStatus: string;
  isSuppressed: boolean;
}

export function App() {
  const [activeTab, setActiveTab] = useState<'dashboard' | 'scanner' | 'contacts' | 'campaigns' | 'settings'>('dashboard');
  const [scanUrl, setScanUrl] = useState('');
  const [isScanning, setIsScanning] = useState(false);
  const [scanProgress, setScanProgress] = useState({ pages: 0, emails: 0 });

  const [contacts, setContacts] = useState<Contact[]>([
    {
      id: '1',
      email: 'contact@stripe.com',
      domain: 'stripe.com',
      companyName: 'Stripe',
      sourceUrl: 'https://stripe.com/contact',
      validationStatus: 'ROLE_ACCOUNT',
      isSuppressed: false,
    },
    {
      id: '2',
      email: 'press@github.com',
      domain: 'github.com',
      companyName: 'GitHub',
      sourceUrl: 'https://github.com/about/press',
      validationStatus: 'ROLE_ACCOUNT',
      isSuppressed: false,
    },
    {
      id: '3',
      email: 'hello@figma.com',
      domain: 'figma.com',
      companyName: 'Figma',
      sourceUrl: 'https://figma.com/contact',
      validationStatus: 'ROLE_ACCOUNT',
      isSuppressed: false,
    },
  ]);

  const handleStartScan = (e: React.FormEvent) => {
    e.preventDefault();
    if (!scanUrl) return;
    setIsScanning(true);
    setScanProgress({ pages: 1, emails: 0 });

    let count = 0;
    const interval = setInterval(() => {
      count++;
      setScanProgress({ pages: count * 4, emails: Math.floor(count * 1.5) });
      if (count >= 5) {
        clearInterval(interval);
        setIsScanning(false);
        const domain = scanUrl.replace(/^https?:\/\//, '').split('/')[0];
        setContacts((prev) => [
          ...prev,
          {
            id: String(Date.now()),
            email: `inquiries@${domain}`,
            domain,
            companyName: domain.split('.')[0],
            sourceUrl: scanUrl,
            validationStatus: 'ROLE_ACCOUNT',
            isSuppressed: false,
          },
        ]);
      }
    }, 600);
  };

  const handleExportCsv = () => {
    const BOM = '\uFEFF';
    const header = 'email,domain,company_name,source_url,discovered_at,validation_status\n';
    const rows = contacts.map(c => `"${c.email}","${c.domain}","${c.companyName}","${c.sourceUrl}","${new Date().toISOString()}","${c.validationStatus}"`).join('\n');
    const blob = new Blob([BOM + header + rows], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', `contactflow_export_${Date.now()}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  return (
    <div className="flex h-screen bg-slate-50 text-slate-900 font-sans">
      {/* Sidebar */}
      <aside className="w-64 border-r border-slate-200 bg-white flex flex-col justify-between">
        <div>
          <div className="p-6 border-b border-slate-100 flex items-center space-x-3">
            <div className="w-9 h-9 rounded-xl bg-indigo-600 flex items-center justify-center text-white font-bold">
              CF
            </div>
            <div>
              <h1 className="font-bold text-base text-slate-900">ContactFlow</h1>
              <p className="text-xs text-slate-500">Public Business Intelligence</p>
            </div>
          </div>

          <nav className="p-4 space-y-1">
            {[
              { id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard },
              { id: 'scanner', label: 'Website Scanner', icon: Radar },
              { id: 'contacts', label: 'Contacts', icon: Users },
              { id: 'campaigns', label: 'Campaigns', icon: Send },
              { id: 'settings', label: 'Settings & OAuth', icon: Settings },
            ].map((tab) => {
              const Icon = tab.icon;
              const isActive = activeTab === tab.id;
              return (
                <button
                  key={tab.id}
                  onClick={() => setActiveTab(tab.id as any)}
                  className={`w-full flex items-center space-x-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors ${
                    isActive
                      ? 'bg-indigo-50 text-indigo-700 font-semibold'
                      : 'text-slate-600 hover:bg-slate-50 hover:text-slate-900'
                  }`}
                >
                  <Icon className={`w-4 h-4 ${isActive ? 'text-indigo-600' : 'text-slate-400'}`} />
                  <span>{tab.label}</span>
                </button>
              );
            })}
          </nav>
        </div>

        <div className="p-4 border-t border-slate-100 bg-slate-50/50">
          <div className="flex items-center space-x-2 text-xs text-slate-500">
            <div className="w-2 h-2 rounded-full bg-emerald-500"></div>
            <span>Gmail API: Connected</span>
          </div>
        </div>
      </aside>

      {/* Main Content */}
      <main className="flex-1 overflow-y-auto p-8">
        {activeTab === 'dashboard' && (
          <div className="max-w-6xl mx-auto space-y-6">
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-2xl font-bold text-slate-900">Dashboard</h2>
                <p className="text-sm text-slate-500">Real-time public business email intelligence and outreach.</p>
              </div>
              <div className="flex space-x-3">
                <button
                  onClick={() => setActiveTab('scanner')}
                  className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-medium rounded-lg flex items-center space-x-2"
                >
                  <Radar className="w-4 h-4" />
                  <span>New Scan</span>
                </button>
                <button
                  onClick={handleExportCsv}
                  className="px-4 py-2 bg-white border border-slate-200 hover:bg-slate-50 text-slate-700 text-sm font-medium rounded-lg flex items-center space-x-2"
                >
                  <Download className="w-4 h-4" />
                  <span>Export CSV</span>
                </button>
              </div>
            </div>

            {/* Stat Cards */}
            <div className="grid grid-cols-4 gap-4">
              {[
                { title: 'Total Contacts', value: contacts.length, icon: Users, sub: 'Deduplicated in workspace' },
                { title: 'Domains Scanned', value: '4', icon: Radar, sub: 'Polite crawls completed' },
                { title: 'Campaigns Sent', value: '2', icon: Send, sub: 'Via official Gmail API' },
                { title: 'Suppression Rules', value: '1', icon: Shield, sub: 'Zero-spam enforced' },
              ].map((card, i) => {
                const Icon = card.icon;
                return (
                  <div key={i} className="p-5 bg-white border border-slate-200 rounded-xl">
                    <div className="flex items-center justify-between text-slate-500 mb-2">
                      <span className="text-xs font-semibold uppercase">{card.title}</span>
                      <Icon className="w-4 h-4 text-indigo-500" />
                    </div>
                    <div className="text-2xl font-bold text-slate-900">{card.value}</div>
                    <div className="text-xs text-slate-400 mt-1">{card.sub}</div>
                  </div>
                );
              })}
            </div>

            {/* Compliance Banner */}
            <div className="p-4 bg-indigo-50/70 border border-indigo-100 rounded-xl flex items-start space-x-3 text-sm text-indigo-950">
              <Shield className="w-5 h-5 text-indigo-600 flex-shrink-0 mt-0.5" />
              <div>
                <span className="font-semibold">Compliant Public Data Standard:</span> ContactFlow extracts strictly public, visibly published business contacts from permitted web pages. Private mailboxes, paywalls, and captchas are never bypassed.
              </div>
            </div>
          </div>
        )}

        {activeTab === 'scanner' && (
          <div className="max-w-4xl mx-auto space-y-6">
            <h2 className="text-2xl font-bold text-slate-900">Website Scanner</h2>
            <div className="p-6 bg-white border border-slate-200 rounded-xl space-y-4">
              <form onSubmit={handleStartScan} className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1">Target Website URL</label>
                  <input
                    type="url"
                    value={scanUrl}
                    onChange={(e) => setScanUrl(e.target.value)}
                    placeholder="https://company.com"
                    required
                    className="w-full px-3.5 py-2.5 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  />
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-xs text-slate-500">Max page depth: 3 · Politeness delay: 400ms · SSRF Protection enabled</span>
                  <button
                    type="submit"
                    disabled={isScanning}
                    className="px-5 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-semibold rounded-lg disabled:opacity-50 flex items-center space-x-2"
                  >
                    <Play className="w-4 h-4" />
                    <span>{isScanning ? 'Scanning...' : 'Start Polite Crawl'}</span>
                  </button>
                </div>
              </form>

              {isScanning && (
                <div className="p-4 bg-slate-50 border border-slate-200 rounded-lg space-y-2">
                  <div className="flex justify-between text-xs font-semibold text-slate-600">
                    <span>Crawling pages: {scanProgress.pages}/25</span>
                    <span>Found emails: {scanProgress.emails}</span>
                  </div>
                  <div className="w-full bg-slate-200 h-2 rounded-full overflow-hidden">
                    <div className="bg-indigo-600 h-full animate-pulse" style={{ width: '65%' }}></div>
                  </div>
                </div>
              )}
            </div>
          </div>
        )}

        {activeTab === 'contacts' && (
          <div className="max-w-6xl mx-auto space-y-6">
            <div className="flex justify-between items-center">
              <div>
                <h2 className="text-2xl font-bold text-slate-900">Discovered Contacts</h2>
                <p className="text-sm text-slate-500">{contacts.length} unique business contacts extracted.</p>
              </div>
              <button
                onClick={handleExportCsv}
                className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700 flex items-center space-x-2"
              >
                <Download className="w-4 h-4" />
                <span>Export CSV</span>
              </button>
            </div>

            <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
              <table className="w-full text-left text-sm">
                <thead className="bg-slate-50 border-b border-slate-200 text-xs uppercase font-semibold text-slate-500">
                  <tr>
                    <th className="p-4">Email</th>
                    <th className="p-4">Domain</th>
                    <th className="p-4">Company</th>
                    <th className="p-4">Source URL</th>
                    <th className="p-4">Validation Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {contacts.map((c) => (
                    <tr key={c.id} className="hover:bg-slate-50/50">
                      <td className="p-4 font-semibold text-slate-900">{c.email}</td>
                      <td className="p-4 text-slate-600">{c.domain}</td>
                      <td className="p-4 text-slate-600">{c.companyName}</td>
                      <td className="p-4 text-slate-400 text-xs truncate max-w-xs">{c.sourceUrl}</td>
                      <td className="p-4">
                        <span className="px-2 py-1 bg-emerald-50 text-emerald-700 text-xs font-semibold rounded">
                          {c.validationStatus}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {activeTab === 'campaigns' && (
          <div className="max-w-4xl mx-auto space-y-6">
            <h2 className="text-2xl font-bold text-slate-900">Campaign Outreach</h2>
            <div className="p-6 bg-white border border-slate-200 rounded-xl space-y-4">
              <h3 className="font-semibold text-base">Compliant Sending Approval Workflow</h3>
              <p className="text-sm text-slate-500">
                Emails are sent directly through your authenticated Google Account using the Gmail API (<code className="bg-slate-100 px-1 py-0.5 rounded text-xs">gmail.send</code> scope). Automatic mass dispatch is restricted; every campaign requires explicit user approval and auto-checks suppression rules.
              </p>
            </div>
          </div>
        )}

        {activeTab === 'settings' && (
          <div className="max-w-4xl mx-auto space-y-6">
            <h2 className="text-2xl font-bold text-slate-900">Settings & Google OAuth</h2>
            <div className="p-6 bg-white border border-slate-200 rounded-xl space-y-4">
              <h3 className="font-semibold text-base">Google Account Connection</h3>
              <p className="text-sm text-slate-500">Connected Account: <strong className="text-slate-800">demo.outreach@gmail.com</strong></p>
              <div className="p-3 bg-slate-50 border border-slate-200 rounded text-xs text-slate-600">
                Requested scopes: <code className="text-indigo-600">gmail.send</code>, <code className="text-indigo-600">userinfo.email</code>. Inbox reading and folder modifications are strictly forbidden.
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
