"use client";

import Link from "next/link";
import { useLanguage } from "@/components/LanguageProvider";

export default function HomePage() {
  const { t } = useLanguage();

  return (
    <div>
      <section className="mx-auto max-w-6xl px-6 py-20">
        <div className="max-w-2xl">
          <h1 className="font-display text-5xl font-medium leading-[1.1] text-indigo">
            {t("heroTitle")}
          </h1>
          <p className="mt-6 max-w-[60ch] font-sans text-lg leading-relaxed text-soot/80">
            {t("heroBody")}
          </p>
          <div className="mt-10 flex gap-4">
            <Link
              href="/discover"
              className="rounded-control bg-indigo px-6 py-3 font-sans text-[15px] font-medium text-sand hover:bg-indigo-deep"
            >
              {t("navDiscover")}
            </Link>
            <Link
              href="/onboarding"
              className="rounded-control border-[1.5px] border-indigo px-6 py-3 font-sans text-[15px] font-medium text-indigo hover:bg-sandDeep"
            >
              {t("navOnboarding")}
            </Link>
          </div>
        </div>
      </section>

      <section className="border-t-[3px] border-indigo bg-sandDeep/40 py-16">
        <div className="mx-auto grid max-w-6xl grid-cols-1 gap-10 px-6 md:grid-cols-3">
          <FeatureBlock
            title="Every home is field-audited"
            body="A field officer physically visits each property before it's listed, confirming it exists exactly as described."
          />
          <FeatureBlock
            title="Landmark-based directions"
            body="No street addresses required - every listing carries directions a person can actually follow: 'third gate past the red water tank.'"
          />
          <FeatureBlock
            title="Works from a basic phone"
            body="Agents without a smartphone can list a property entirely by USSD, State by LGA by village."
          />
        </div>
      </section>
    </div>
  );
}

function FeatureBlock({ title, body }: { title: string; body: string }) {
  return (
    <div className="border-l-[3px] border-clay pl-5">
      <h3 className="font-display text-xl text-indigo">{title}</h3>
      <p className="mt-2 font-sans text-[15px] leading-relaxed text-soot/75">{body}</p>
    </div>
  );
}
