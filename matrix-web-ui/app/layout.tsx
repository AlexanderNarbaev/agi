import type { Metadata, Viewport } from 'next';
import Plausible from '@/components/analytics/Plausible';
import './globals.css';

export const metadata: Metadata = {
  metadataBase: new URL('https://matrix.ai'),
  title: {
    default: 'MATRIX — Hybrid Neuro-Symbolic AI',
    template: '%s | MATRIX',
  },
  description:
    'Production-grade hybrid AI with explainability built in. BIR + HDC + MCTS. No LLM in runtime. GDPR-compliant. On-prem option.',
  keywords: [
    'explainable AI',
    'neuro-symbolic AI',
    'hybrid AI',
    'GDPR AI',
    'on-prem AI',
    'BIR',
    'HDC',
    'MCTS',
    'alternative to LLM',
    'auditable AI',
  ],
  authors: [{ name: 'MATRIX Team' }],
  creator: 'MATRIX',
  publisher: 'MATRIX',
  formatDetection: {
    email: false,
    address: false,
    telephone: false,
  },
  openGraph: {
    type: 'website',
    locale: 'en_US',
    url: 'https://matrix.ai',
    siteName: 'MATRIX',
    title: 'MATRIX — Hybrid Neuro-Symbolic AI',
    description: 'Production-grade hybrid AI with explainability built in.',
    images: [
      {
        url: '/og-image.svg',
        width: 1200,
        height: 630,
        alt: 'MATRIX — Hybrid Neuro-Symbolic AI',
      },
    ],
  },
  twitter: {
    card: 'summary_large_image',
    title: 'MATRIX — Hybrid Neuro-Symbolic AI',
    description: 'Production-grade hybrid AI with explainability built in.',
    images: ['/og-image.svg'],
  },
  robots: {
    index: true,
    follow: true,
    googleBot: {
      index: true,
      follow: true,
      'max-image-preview': 'large',
      'max-snippet': -1,
      'max-video-preview': -1,
    },
  },
  alternates: {
    canonical: 'https://matrix.ai',
    languages: {
      'en-US': 'https://matrix.ai',
      'ru-RU': 'https://matrix.ai/ru',
    },
  },
};

export const viewport: Viewport = {
  themeColor: '#0a0e1a',
  width: 'device-width',
  initialScale: 1,
  maximumScale: 5,
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" className="dark">
      <body className="bg-matrix-bg text-matrix-text min-h-screen antialiased">
        {children}
        <Plausible />
      </body>
    </html>
  );
}
