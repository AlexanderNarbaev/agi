/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  poweredByHeader: false,
  compress: true,
  productionBrowserSourceMaps: false,
  experimental: {
    optimizePackageImports: ['three', '@react-three/drei'],
  },
  // MATRIX landing page builds into static assets; no serverless functions needed
  output: 'standalone',
};

module.exports = nextConfig;
