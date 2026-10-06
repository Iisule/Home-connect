/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./src/**/*.{js,ts,jsx,tsx,mdx}"],
  theme: {
    extend: {
      colors: {
        sand: "#EDE3CE",       // sun-dried adobe wall - base background
        sandDeep: "#DCCEA9",   // shaded adobe - panel backgrounds, borders
        indigo: {
          DEFAULT: "#22345C",  // Kofar Mata dye - primary brand + interactive
          deep: "#182644",
          light: "#3A5188",
        },
        clay: "#8A3A24",       // baked clay red - money, urgency, OTP only
        soot: "#2B2A24",       // warm near-black - body text
        millet: "#3F6B4A",     // verified / success
      },
      fontFamily: {
        display: ["var(--font-fraunces)", "Georgia", "serif"],
        sans: ["var(--font-plex)", "system-ui", "sans-serif"],
      },
      borderRadius: {
        control: "4px", // the ONLY radius used - on buttons/inputs, never on panels
      },
      backgroundImage: {
        // Diamond-lattice motif referencing zaure wall relief carving; used sparingly as a section divider.
        lattice:
          "linear-gradient(135deg, rgba(34,52,92,0.08) 25%, transparent 25%), linear-gradient(225deg, rgba(34,52,92,0.08) 25%, transparent 25%), linear-gradient(45deg, rgba(34,52,92,0.08) 25%, transparent 25%), linear-gradient(315deg, rgba(34,52,92,0.08) 25%, transparent 25%)",
      },
      backgroundSize: {
        latticeTile: "16px 16px",
      },
    },
  },
  plugins: [],
};
