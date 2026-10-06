package ng.proptech.domain;

/** Lifecycle of a physical property. PENDING_MERGE = flagged as an AI duplicate, awaiting the agent's decision. */
public enum PropertyStatus {
    PENDING_AUDIT, VERIFIED, PENDING_MERGE, MERGED, RESERVED, RENTED, REJECTED
}
