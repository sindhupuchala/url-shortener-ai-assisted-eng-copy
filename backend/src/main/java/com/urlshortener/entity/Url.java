package com.urlshortener.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "urls")
@Getter
@Setter
@NoArgsConstructor
public class Url {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Nullable at persist time on purpose: the code is base62(id), so it can only be computed
     * after the IDENTITY insert has produced an id, then set via a follow-up update in the same
     * transaction. Never null once the surrounding UrlService call completes.
     */
    @Column(name = "code", unique = true, length = 16)
    private String code;

    @Column(name = "custom_alias", unique = true, length = 64)
    private String customAlias;

    @Column(name = "original_url", nullable = false, length = 2048)
    private String originalUrl;

    @Column(name = "owner_id", nullable = false, length = 64)
    private String ownerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    /** The code actually used to resolve the link: the custom alias if set, otherwise the generated code. */
    public String resolvableKey() {
        return customAlias != null ? customAlias : code;
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }
}
