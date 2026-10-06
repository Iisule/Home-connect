"use client";

import Link from "next/link";
import { useLanguage } from "./LanguageProvider";

export function Header() {
  const { lang, setLang, t } = useLanguage();

  return (
    <header className="border-b-[3px] border-indigo bg-sand">
      <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-5">
        <Link href="/" className="flex items-baseline gap-2">
          <span className="font-display text-2xl font-medium text-indigo">Naija</span>
          <span className="font-display text-2xl font-medium text-clay">Proptech</span>
        </Link>

        <nav className="flex items-center gap-6">
          <Link href="/discover" className="font-sans text-[15px] text-soot hover:text-indigo">
            {t("navDiscover")}
          </Link>
          <Link href="/onboarding" className="font-sans text-[15px] text-soot hover:text-indigo">
            {t("navOnboarding")}
          </Link>
          <button
            onClick={() => setLang(lang === "en" ? "ha" : "en")}
            className="rounded-control border-[1.5px] border-indigo px-3 py-1.5 font-sans text-[13px] font-medium text-indigo hover:bg-indigo hover:text-sand"
          >
            {t("langToggle")}
          </button>
        </nav>
      </div>
      <div className="h-2 bg-lattice bg-latticeTile" />
    </header>
  );
}
