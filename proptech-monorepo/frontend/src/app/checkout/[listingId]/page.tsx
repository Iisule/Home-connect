"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { useLanguage } from "@/components/LanguageProvider";
import {
  api,
  formatNaira,
  getToken,
  saveToken,
  type AuthResponse,
  type CheckoutResponse,
  type ConfirmPaymentResponse,
  type ReleaseResponse,
} from "@/lib/api";

type Stage = "auth" | "checkout" | "paid" | "released";

export default function CheckoutPage() {
  const { listingId } = useParams<{ listingId: string }>();
  const { t } = useLanguage();

  const [stage, setStage] = useState<Stage>(getToken() ? "checkout" : "auth");
  const [error, setError] = useState<string | null>(null);

  // Tenant quick auth (login existing account, or register in one step)
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fullName, setFullName] = useState("");
  const [phone, setPhone] = useState("");
  const [isNewTenant, setIsNewTenant] = useState(true);
  const [authing, setAuthing] = useState(false);

  // Checkout / escrow state
  const [checkout, setCheckout] = useState<CheckoutResponse | null>(null);
  const [loadingCheckout, setLoadingCheckout] = useState(false);
  const [paying, setPaying] = useState(false);
  const [otp, setOtp] = useState<string | null>(null); // demo-only: a real deployment SMSes this
  const [escrowId, setEscrowId] = useState<string | null>(null);

  // Agent-side release
  const [releaseCode, setReleaseCode] = useState("");
  const [releasing, setReleasing] = useState(false);
  const [releaseResult, setReleaseResult] = useState<ReleaseResponse | null>(null);

  useEffect(() => {
    if (stage !== "checkout" || checkout) return;
    setLoadingCheckout(true);
    setError(null);
    api
      .post<CheckoutResponse>("/api/escrow/checkout", { listingId })
      .then((res) => {
        setCheckout(res);
        setEscrowId(res.escrowId);
      })
      .catch((err) => setError(err.message || "Could not start checkout."))
      .finally(() => setLoadingCheckout(false));
  }, [stage, checkout, listingId]);

  async function handleAuth(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setAuthing(true);
    try {
      const res = isNewTenant
        ? await api.post<AuthResponse>("/api/auth/register", {
            fullName, email, phoneNumber: phone, password, role: "TENANT",
          })
        : await api.post<AuthResponse>("/api/auth/login", { email, password });
      saveToken(res.token);
      setStage("checkout");
    } catch (err: any) {
      setError(err.message || "Could not sign in.");
    } finally {
      setAuthing(false);
    }
  }

  async function handlePay() {
    if (!escrowId) return;
    setError(null);
    setPaying(true);
    try {
      const res = await api.post<ConfirmPaymentResponse>(`/api/escrow/${escrowId}/confirm-payment`, {
        paymentReference: "DEMO-" + Date.now(),
      });
      setOtp(res.otpForDemoOnly);
      setStage("paid");
    } catch (err: any) {
      setError(err.message || "Payment simulation failed.");
    } finally {
      setPaying(false);
    }
  }

  async function handleRelease(e: React.FormEvent) {
    e.preventDefault();
    if (!escrowId) return;
    setError(null);
    setReleasing(true);
    try {
      const res = await api.post<ReleaseResponse>(`/api/escrow/${escrowId}/release`, { otp: releaseCode });
      setReleaseResult(res);
      setStage("released");
    } catch (err: any) {
      setError(err.message || "That code did not work.");
    } finally {
      setReleasing(false);
    }
  }

  return (
    <div className="mx-auto max-w-2xl px-6 py-12">
      <h1 className="font-display text-3xl text-indigo">{t("checkoutTitle")}</h1>
      <p className="mt-2 font-sans text-[15px] text-soot/70">{t("checkoutBody")}</p>

      {error && <p className="mt-6 border-l-[3px] border-clay pl-4 font-sans text-clay">{error}</p>}

      {stage === "auth" && (
        <form onSubmit={handleAuth} className="mt-8 flex flex-col gap-4 border-[3px] border-indigo bg-white/40 p-6">
          <div className="flex gap-2 font-sans text-[13px]">
            <button type="button" onClick={() => setIsNewTenant(true)}
              className={`border-b-[3px] px-3 py-2 ${isNewTenant ? "border-clay text-indigo" : "border-sandDeep text-soot/40"}`}>
              New tenant
            </button>
            <button type="button" onClick={() => setIsNewTenant(false)}
              className={`border-b-[3px] px-3 py-2 ${!isNewTenant ? "border-clay text-indigo" : "border-sandDeep text-soot/40"}`}>
              I already have an account
            </button>
          </div>

          {isNewTenant && (
            <>
              <Field label={t("fullName")}>
                <input className="input" value={fullName} onChange={(e) => setFullName(e.target.value)} required />
              </Field>
              <Field label={t("phone")}>
                <input className="input" value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="080..." required />
              </Field>
            </>
          )}
          <Field label={t("email")}>
            <input className="input" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
          </Field>
          <Field label={t("password")}>
            <input className="input" type="password" minLength={8} value={password} onChange={(e) => setPassword(e.target.value)} required />
          </Field>
          <button type="submit" disabled={authing} className="btn-primary mt-2 self-start">
            {authing ? "\u2026" : t("continueBtn")}
          </button>
        </form>
      )}

      {stage === "checkout" && (
        <div className="mt-8 border-[3px] border-indigo bg-white/40 p-6">
          {loadingCheckout || !checkout ? (
            <p className="font-sans text-soot/60">{"\u2026"}</p>
          ) : (
            <>
              <PriceLine label="Annual rent" value={checkout.annualRent} />
              <PriceLine label={t("agencyFee")} value={checkout.agencyFee} />
              <div className="mt-4 flex items-center justify-between border-t-[3px] border-indigo pt-4">
                <span className="font-display text-lg text-indigo">{t("totalPayable")}</span>
                <span className="font-display text-2xl text-clay">{formatNaira(checkout.totalAmount)}</span>
              </div>
              <button onClick={handlePay} disabled={paying} className="btn-primary mt-6 w-full">
                {paying ? "\u2026" : t("payNow")}
              </button>
            </>
          )}
        </div>
      )}

      {(stage === "paid" || stage === "released") && otp && (
        <div className="mt-8 border-[3px] border-clay bg-clay/5 p-6">
          <h2 className="font-display text-lg text-clay">{t("otpTitle")}</h2>
          <p className="mt-1 font-sans text-[14px] leading-relaxed text-soot/75">{t("otpBody")}</p>
          <p className="mt-4 text-center font-display text-5xl tracking-[0.3em] text-indigo">{otp}</p>
        </div>
      )}

      {stage === "paid" && (
        <div className="mt-8 border-[3px] border-indigo bg-white/40 p-6">
          <h2 className="font-display text-lg text-indigo">{t("releaseFunds")}</h2>
          <form onSubmit={handleRelease} className="mt-4 flex flex-col gap-4">
            <Field label={t("otpInputLabel")}>
              <input
                className="input text-center text-2xl tracking-[0.3em]"
                value={releaseCode}
                onChange={(e) => setReleaseCode(e.target.value.replace(/\D/g, "").slice(0, 4))}
                maxLength={4}
                inputMode="numeric"
                required
              />
            </Field>
            <button type="submit" disabled={releasing || releaseCode.length !== 4} className="btn-primary self-start">
              {releasing ? "\u2026" : t("releaseBtn")}
            </button>
          </form>
        </div>
      )}

      {stage === "released" && releaseResult && (
        <div className="mt-8 border-[3px] border-millet bg-millet/10 p-6">
          <p className="font-display text-lg text-millet">{t("released")}</p>
          <dl className="mt-4 grid grid-cols-2 gap-y-2 font-sans text-[14px] text-soot/80">
            <dt>3% platform fee</dt><dd className="text-right">{formatNaira(releaseResult.platformFee)}</dd>
            <dt>Logistics fee</dt><dd className="text-right">{formatNaira(releaseResult.logisticsFee)}</dd>
            <dt>Net paid to agent</dt><dd className="text-right font-medium text-indigo">{formatNaira(releaseResult.netToAgent)}</dd>
          </dl>
          <p className="mt-3 font-sans text-[12px] text-soot/50">Payout ref: {releaseResult.payoutReference}</p>
        </div>
      )}
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

function PriceLine({ label, value }: { label: string; value: number }) {
  return (
    <div className="flex items-center justify-between py-1.5 font-sans text-[15px] text-soot/80">
      <span>{label}</span>
      <span>{formatNaira(value)}</span>
    </div>
  );
}
