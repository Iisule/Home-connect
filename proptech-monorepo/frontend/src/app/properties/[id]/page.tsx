"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Image from "next/image";
import { useLanguage } from "@/components/LanguageProvider";
import { api, formatNaira, type PropertyDetail, type AgentOffer } from "@/lib/api";

const TYPE_LABELS: Record<string, string> = {
  SELF_CONTAIN: "Self Contain",
  ROOM_AND_PARLOUR: "Room & Parlour",
  TWO_BEDROOM: "2 Bedroom",
  THREE_BEDROOM: "3 Bedroom",
  DUPLEX: "Duplex",
  SHOP: "Shop",
};

export default function PropertyDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const { t } = useLanguage();

  const [property, setProperty] = useState<PropertyDetail | null>(null);
  const [activePhoto, setActivePhoto] = useState(0);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .get<PropertyDetail>(`/api/properties/${id}`)
      .then(setProperty)
      .catch(() => setError("This listing could not be loaded."));
  }, [id]);

  if (error) return <div className="mx-auto max-w-6xl px-6 py-12 font-sans text-clay">{error}</div>;
  if (!property) return <div className="mx-auto max-w-6xl px-6 py-12 font-sans text-soot/60">Loading{"\u2026"}</div>;

  const sortedOffers = [...property.offers].sort((a, b) => a.totalPayable - b.totalPayable);

  return (
    <div className="mx-auto max-w-6xl px-6 py-12">
      <div className="grid grid-cols-1 gap-10 lg:grid-cols-[1.1fr_0.9fr]">
        {/* Photo gallery */}
        <div>
          <div className="relative h-96 w-full border-[3px] border-indigo bg-sandDeep">
            {property.photoUrls[activePhoto] && (
              <Image src={property.photoUrls[activePhoto]} alt={property.title} fill className="object-cover" unoptimized />
            )}
          </div>
          {property.photoUrls.length > 1 && (
            <div className="mt-3 flex gap-2 overflow-x-auto">
              {property.photoUrls.map((url, i) => (
                <button
                  key={url}
                  onClick={() => setActivePhoto(i)}
                  className={`relative h-16 w-24 flex-shrink-0 border-[2px] ${i === activePhoto ? "border-clay" : "border-indigo/30"}`}
                >
                  <Image src={url} alt="" fill className="object-cover" unoptimized />
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Details */}
        <div>
          <h1 className="font-display text-3xl text-indigo">{property.title}</h1>
          <p className="mt-1 font-sans text-[15px] text-soot/60">
            {property.settlementName}
            {property.estateName ? `, ${property.estateName}` : ""}, {property.lgaName}
          </p>
          <p className="mt-2 inline-block border border-millet px-2 py-0.5 font-sans text-[12px] font-medium text-millet">
            {TYPE_LABELS[property.propertyType] ?? property.propertyType}
          </p>

          <div className="mt-6 border-l-[3px] border-clay pl-5">
            <h2 className="font-display text-base text-indigo">{t("landmark")}</h2>
            <p className="mt-1 font-sans text-[15px] leading-relaxed text-soot/80">{property.landmarkDescription}</p>
          </div>
        </div>
      </div>

      {/* Comparative agent grid */}
      <section className="mt-14">
        <h2 className="font-display text-2xl text-indigo">{t("compareAgents")}</h2>
        <div className="mt-6 overflow-x-auto border-[3px] border-indigo">
          <table className="w-full min-w-[720px] border-collapse font-sans text-[14px]">
            <thead>
              <tr className="border-b-[3px] border-indigo bg-sandDeep/60 text-left">
                <Th>Agent</Th>
                <Th align="right">{t("agentOffer")}</Th>
                <Th align="right">{t("agencyFee")}</Th>
                <Th align="right">{t("totalPayable")}</Th>
                <Th align="center">{t("rating")}</Th>
                <Th align="center">{t("dealsClosed")}</Th>
                <Th align="center">{t("avgResponse")}</Th>
                <Th align="right"></Th>
              </tr>
            </thead>
            <tbody>
              {sortedOffers.map((o, i) => (
                <AgentRow key={o.listingId} offer={o} best={i === 0} onChoose={() => router.push(`/checkout/${o.listingId}`)} />
              ))}
              {sortedOffers.length === 0 && (
                <tr>
                  <td colSpan={8} className="px-4 py-8 text-center text-soot/50">
                    No active agent offers on this property yet.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}

function Th({ children, align = "left" }: { children?: React.ReactNode; align?: "left" | "right" | "center" }) {
  const alignClass = { left: "text-left", right: "text-right", center: "text-center" }[align];
  return <th className={`px-4 py-3 font-medium text-soot/70 ${alignClass}`}>{children}</th>;
}

function AgentRow({ offer, best, onChoose }: { offer: AgentOffer; best: boolean; onChoose: () => void }) {
  const { t } = useLanguage();
  return (
    <tr className={`border-b border-sandDeep ${best ? "bg-millet/10" : ""}`}>
      <td className="px-4 py-3 font-medium text-soot">{offer.agentName}</td>
      <td className="px-4 py-3 text-right">{formatNaira(offer.annualRent)}</td>
      <td className="px-4 py-3 text-right">{formatNaira(offer.agencyFee)}</td>
      <td className="px-4 py-3 text-right font-display text-clay">{formatNaira(offer.totalPayable)}</td>
      <td className="px-4 py-3 text-center">{offer.agentRating.toFixed(1)}</td>
      <td className="px-4 py-3 text-center">{offer.dealsClosed}</td>
      <td className="px-4 py-3 text-center">{offer.avgResponseMinutes} {t("minutes")}</td>
      <td className="px-4 py-3 text-right">
        <button onClick={onChoose} className="rounded-control bg-indigo px-4 py-1.5 text-[13px] font-medium text-sand hover:bg-indigo-deep">
          {t("chooseThisAgent")}
        </button>
      </td>
    </tr>
  );
}
