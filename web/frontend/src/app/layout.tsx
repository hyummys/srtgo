import type { Metadata, Viewport } from 'next';
import '@/styles/globals.css';
import Header from '@/components/layout/Header';
import BottomNav from '@/components/layout/BottomNav';

export const metadata: Metadata = {
  title: 'SRTgo - 열차 예약',
  description: 'SRT/KTX 열차 예약 도우미',
};

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  maximumScale: 1,
  userScalable: false,
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="ko">
      <body className="min-h-screen bg-gray-50">
        <Header />
        <main className="max-w-5xl mx-auto px-4 py-4 pb-20 md:pb-4">
          {children}
        </main>
        <BottomNav />
      </body>
    </html>
  );
}
