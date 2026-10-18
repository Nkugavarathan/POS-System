import "./globals.css";
import Providers from "./providers";

export const metadata = {
  title: { default: "Supermarket POS", template: "%s · Supermarket POS" },
  description: "Point of sale and back office",
};

export const viewport = {
  width: "device-width",
  initialScale: 1,
  themeColor: "#0f766e",
};

export default function RootLayout({ children }) {
  return (
    <html lang="en">
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
