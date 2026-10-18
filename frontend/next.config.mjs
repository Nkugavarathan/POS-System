/**
 * The browser only talks to this Next.js origin. /api/* is proxied to the Spring Boot
 * backend, so authentication cookies stay first-party (SameSite=Strict, HttpOnly) and no
 * CORS is needed. BACKEND_URL is read at build/start time (default: local dev backend).
 */
const backendUrl = process.env.BACKEND_URL || "http://localhost:8080";

const securityHeaders = [
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "Referrer-Policy", value: "no-referrer" },
  // Camera is needed by the barcode scanner on this origin only.
  { key: "Permissions-Policy", value: "camera=(self), microphone=(), geolocation=()" },
];

/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  poweredByHeader: false,
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${backendUrl}/api/:path*` }];
  },
  async headers() {
    return [{ source: "/:path*", headers: securityHeaders }];
  },
};

export default nextConfig;
