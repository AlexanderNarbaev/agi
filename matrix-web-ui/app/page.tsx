import { Suspense } from 'react';
import Navbar from '@/components/Navbar';
import Hero from '@/components/sections/Hero';
import Features from '@/components/sections/Features';
import LiveDemo from '@/components/sections/LiveDemo';
import Pricing from '@/components/sections/Pricing';
import Trust from '@/components/sections/Trust';
import Contact from '@/components/sections/Contact';
import Footer from '@/components/Footer';

export default function HomePage() {
  return (
    <>
      <Navbar />
      <main>
        <Suspense fallback={<div className="h-screen bg-matrix-bg" />}>
          <Hero />
        </Suspense>
        <Features />
        <LiveDemo />
        <Pricing />
        <Trust />
        <Contact />
      </main>
      <Footer />
    </>
  );
}
