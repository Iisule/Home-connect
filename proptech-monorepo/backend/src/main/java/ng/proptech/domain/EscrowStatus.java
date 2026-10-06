package ng.proptech.domain;

/** Escrow state machine. LOCKED = too many wrong OTP attempts. */
public enum EscrowStatus {
    PENDING_PAYMENT, FUNDED, RELEASED, LOCKED, REFUNDED
}
