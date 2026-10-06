"use client";

import { useState } from "react";
import { useLanguage } from "@/components/LanguageProvider";
import { api, saveToken, type AuthResponse } from "@/lib/api";

type Step = "account" | "nin" | "done";

export default function OnboardingPage() {
  const { t } = useLanguage();
  const [step, setStep] = useState<Step>("account");
  const [error, setError] = useState<string | null>(null);

  // Account fields
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [creating, setCreating] = useState(false);

  // NIN fields
  const [nin, setNin] = useState("");
  const [ninFullName, setNinFullName] = useState("");
  const [ninSlip, setNinSlip] = useState<File | null>(null);
  const [verifying, setVerifying] = useState(false);
  const [ninResult, setNinResult] = useState<{ verified: boolean; reasonIfFailed: string | null } | null>(null);

  async function handleCreateAccount(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setCreating(true);
    try {
      const res = await api.post<AuthResponse>("/api/auth/register", {
        fullName, email, phoneNumber: phone, password, role: "AGENT",
      });
      saveToken(res.token);
      setNinFullName(fullName);
      setStep("nin");
    } catch (err: any) {
      setError(err.message || "Could not create your account.");
    } finally {
      setCreating(false);
    }
  }

  async function handleSubmitNin(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setVerifying(true);
    try {
      const form = new FormData();
      form.append("metadata", new Blob([JSON.stringify({ nin, ninFullName })], { type: "application/json" }));
      if (ninSlip) form.append("ninSlip", ninSlip);
      const res = await api.post<{ verified: boolean; reasonIfFailed: string | null }>("/api/verification/nin", form);
      setNinResult(res);
      if (res.verified) setStep("done");
    } catch (err: any) {
      setError(err.message || "Verification failed.");
    } finally {
      setVerifying(false);
    }
  }

  return (
    <div className="mx-auto max-w-2xl px-6 py-12">
      <h1 className="font-display text-3xl text-indigo">{t("onboardTitle")}</h1>
      <p className="mt-2 font-sans text-[15px] text-soot/70">{t("onboardBody")}</p>

      <ol className="mt-8 flex gap-2 font-sans text-[13px]">
        <StepPill label={t("stepAccount")} active={step === "account"} done={step !== "account"} />
        <StepPill label={t("stepNin")} active={step === "nin"} done={step === "done"} />
        <StepPill label={t("stepDone")} active={step === "done"} done={false} />
      </ol>

      {error && <p className="mt-6 border-l-[3px] border-clay pl-4 font-sans text-clay">{error}</p>}

      {step === "account" && (
        <form onSubmit={handleCreateAccount} className="mt-8 flex flex-col gap-4 border-[3px] border-indigo bg-white/40 p-6">
          <TextField label={t("fullName")} value={fullName} onChange={setFullName} required />
          <TextField label={t("email")} value={email} onChange={setEmail} type="email" required />
          <TextField label={t("phone")} value={phone} onChange={setPhone} placeholder="080..." required />
          <TextField label={t("password")} value={password} onChange={setPassword} type="password" required minLength={8} />
          <button type="submit" disabled={creating} className="btn-primary mt-2 self-start">
            {creating ? "\u2026" : t("continueBtn")}
          </button>
        </form>
      )}

      {step === "nin" && (
        <form onSubmit={handleSubmitNin} className="mt-8 flex flex-col gap-4 border-[3px] border-indigo bg-white/40 p-6">
          <TextField label={t("ninNumber")} value={nin} onChange={setNin} maxLength={11} pattern="\d{11}" required />
          <TextField label={t("ninName")} value={ninFullName} onChange={setNinFullName} required />
          <label className="flex flex-col gap-1.5">
            <span className="font-sans text-[13px] font-medium text-soot/70">{t("ninSlip")}</span>
            <input
              type="file"
              accept="image/*"
              onChange={(e) => setNinSlip(e.target.files?.[0] ?? null)}
              className="font-sans text-[14px]"
            />
          </label>
          <button type="submit" disabled={verifying} className="btn-primary mt-2 self-start">
            {verifying ? "\u2026" : t("submitNin")}
          </button>
          {ninResult && !ninResult.verified && (
            <p className="border-l-[3px] border-clay pl-4 font-sans text-[14px] text-clay">{t("ninRejected")}</p>
          )}
        </form>
      )}

      {step === "done" && (
        <div className="mt-8 border-[3px] border-millet bg-millet/10 p-6">
          <p className="font-display text-lg text-millet">{t("ninVerified")}</p>
        </div>
      )}
    </div>
  );
}

function StepPill({ label, active, done }: { label: string; active: boolean; done: boolean }) {
  return (
    <li
      className={`border-b-[3px] px-3 py-2 ${
        active ? "border-clay text-indigo" : done ? "border-millet text-soot/60" : "border-sandDeep text-soot/40"
      }`}
    >
      {label}
    </li>
  );
}

function TextField(props: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  type?: string;
  required?: boolean;
  placeholder?: string;
  minLength?: number;
  maxLength?: number;
  pattern?: string;
}) {
  const { label, value, onChange, type = "text", ...rest } = props;
  return (
    <label className="flex flex-col gap-1.5">
      <span className="font-sans text-[13px] font-medium text-soot/70">{label}</span>
      <input
        className="input"
        type={type}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        {...rest}
      />
    </label>
  );
}
