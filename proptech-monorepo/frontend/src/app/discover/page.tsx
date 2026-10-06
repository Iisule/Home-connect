"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { useLanguage } from "@/components/LanguageProvider";
import { api, formatNaira, type StateSummary, type LgaSummary, type SettlementSummary, type PropertySummary, type PropertyType } from "@/lib/api";

const PROPERTY_TYPES: { value: PropertyType | ""; label: string }[] = [
  { value: "", label: "\u2014" },
  { value: "SELF_CONTAIN", label: "Self Contain" },
  { value: "ROOM_AND_PARLOUR", label: "Room & Parlour" },
  { value: "TWO_BEDROOM", label: "2 Bedroom" },
  { value: "THREE_BEDROOM", label: "3 Bedroom" },
  { value: "DUPLEX", label: "Duplex" },
  { value: "SHOP", label: "Shop" },
];

export default function DiscoverPage() {
  const { t } = useLanguage();

  const [states, setStates] = useState<StateSummary[]>([]);
  const [lgas, setLgas] = useState<LgaSummary[]>([]);
  const [settlements, setSettlements] = useState<SettlementSummary[]>([]);

  const [stateId, setStateId] = useState("");
  const [lgaId, setLgaId] = useState("");
  const [settlementId, setSettlementId] = useState("");
  const [propertyType, setPropertyType] = useState<PropertyType | "">("");

  const [results, setResults] = useState<PropertySummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.get<StateSummary[]>("/api/geo/states").then(setStates).catch(() => setError("Could not reach the API."));
  }, []);

  useEffect(() => {
    setLgas([]); setLgaId(""); setSettlements([]); setSettlementId("");
    if (stateId) api.get<LgaSummary[]>(`/api/geo/states/${stateId}/lgas`).then(setLgas);
  }, [stateId]);

  useEffect(() => {
    setSettlements([]); setSettlementId("");
    if (lgaId) api.get<SettlementSummary[]>(`/api/geo/lgas/${lgaId}/settlements`).then(setSettlements);
  }, [lgaId]);

  useEffect(() => {
    setLoading(true);
    setError(null);
    const params = new URLSearchParams();
    if (lgaId) params.set("lgaId", lgaId);
    if (settlementId) params.set("settlementId", settlementId);
    if (propertyType) params.set("propertyType", propertyType);
    api
      .get<PropertySummary[]>(`/api/properties?${params.toString()}`)
      .then(setResults)
      .catch(() => setError("Could not load listings."))
      .finally(() => setLoading(false));
  }, [lgaId, settlementId, propertyType]);

  return (
    <div className="mx-auto max-w-6xl px-6 py-12">
      <h1 className="font-display text-3xl text-indigo">{t("navDiscover")}</h1>

      <div className="mt-8 grid grid-cols-2 gap-4 border-[3px] border-indigo bg-white/40 p-6 md:grid-cols-4">
        <Field label={t("filterState")}>
          <select className="select" value={stateId} onChange={(e) => setStateId(e.target.value)}>
            <option value="">{"\u2014"}</option>
            {states.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
        </Field>
        <Field label={t("filterLga")}>
          <select className="select" value={lgaId} disabled={!stateId} onChange={(e) => setLgaId(e.target.value)}>
            <option value="">{"\u2014"}</option>
            {lgas.map((l) => <option key={l.id} value={l.id}>{l.name}</option>)}
          </select>
        </Field>
        <Field label={t("filterSettlement")}>
          <select className="select" value={settlementId} disabled={!lgaId} onChange={(e) => setSettlementId(e.target.value)}>
            <option value="">{"\u2014"}</option>
            {settlements.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
        </Field>
        <Field label={t("filterType")}>
          <select className="select" value={propertyType} onChange={(e) => setPropertyType(e.target.value as PropertyType | "")}>
            {PROPERTY_TYPES.map((pt) => <option key={pt.value} value={pt.value}>{pt.label}</option>)}
          </select>
        </Field>
      </div>

      <p className="mt-6 font-sans text-sm text-soot/60">
        {loading ? "\u2026" : `${results.length} ${t("resultsCount")}`}
      </p>

      {error && <p className="mt-4 font-sans text-clay">{error}</p>}

      {!loading && results.length === 0 && !error && (
        <p className="mt-10 border-l-[3px] border-clay pl-5 font-sans text-soot/70">{t("noResults")}</p>
      )}

      <div className="mt-6 grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
        {results.map((p) => (
          <Link
            key={p.id}
            href={`/properties/${p.id}`}
            className="group border-[3px] border-indigo bg-white/30 transition-colors hover:bg-white/60"
          >
            <div className="relative h-44 w-full bg-sandDeep">
              {p.primaryImageUrl && (
                <Image src={p.primaryImageUrl} alt={p.title} fill className="object-cover" unoptimized />
              )}
            </div>
            <div className="p-4">
              <h3 className="font-display text-lg text-indigo">{p.title}</h3>
              <p className="mt-1 font-sans text-[13px] text-soot/60">
                {p.settlementName}, {p.lgaName}
              </p>
              <p className="mt-3 line-clamp-2 font-sans text-[13px] text-soot/70">{p.landmarkDescription}</p>
              <div className="mt-4 flex items-center justify-between border-t border-sandDeep pt-3">
                <span className="font-sans text-[13px] text-soot/60">
                  {p.competingListingCount} {t("competingAgents")}
                </span>
                <span className="font-display text-lg text-clay">
                  {t("from")} {formatNaira(p.lowestAnnualRent)}
                </span>
              </div>
            </div>
          </Link>
        ))}
      </div>
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="flex flex-col gap-1.5">
      <span className="font-sans text-[13px] font-medium text-soot/70">{label}</span>
      {children}
    </label>
  );
}
