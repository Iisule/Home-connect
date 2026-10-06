/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,

  // NEXT_PUBLIC_* vars are inlined at build time and readable in the browser - fine here, since the
  // Spring Boot API URL is not a secret. Set this in .env.local for dev and in Vercel's project
  // settings for production; nothing in the codebase needs to change between the two.
  //   Local:      NEXT_PUBLIC_API_URL=http://127.0.0.1:8080
  //   Production: NEXT_PUBLIC_API_URL=https://api.naijaproptech.example.com
  env: {
    NEXT_PUBLIC_API_URL: process.env.NEXT_PUBLIC_API_URL || "http://127.0.0.1:8080",
  },

  // Proxies same-origin "/api/*" and "/media/*" requests to the real Spring Boot backend, so the
  // browser never makes a cross-origin call and CORS never comes into play once deployed on Vercel.
  //
  // IMPORTANT: vercel.json's own "rewrites" field only accepts a literal destination string - it
  // cannot read an environment variable, so a "$API_ORIGIN"-style placeholder there silently never
  // resolves. Next.js's rewrites() function runs at request time on the server and CAN read
  // process.env, so the proxy target is configured purely through environment variables (set
  // API_ORIGIN in Vercel's project settings) and this file never needs to change between environments.
  async rewrites() {
    const apiOrigin = process.env.API_ORIGIN || process.env.NEXT_PUBLIC_API_URL || "http://127.0.0.1:8080";
    return [
      { source: "/api/:path*", destination: `${apiOrigin}/api/:path*` },
      { source: "/media/:path*", destination: `${apiOrigin}/media/:path*` },
    ];
  },

  images: {
    // Property photos are served by the Spring Boot backend's own /media/** path (see backend WebConfig),
    // which lives on a different origin/port than the Next.js app in local dev.
    remotePatterns: [
      { protocol: "http", hostname: "127.0.0.1", port: "8080" },
      { protocol: "http", hostname: "localhost", port: "8080" },
      { protocol: "https", hostname: "**" },
    ],
  },
};

module.exports = nextConfig;
