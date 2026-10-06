package ng.proptech.domain;

/** Account roles. Spring Security authorities are ROLE_ + name. */
public enum Role {
    TENANT, AGENT, FIELD_OFFICER, ADMIN
}
