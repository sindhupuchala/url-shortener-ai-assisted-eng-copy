package com.urlshortener.repository;

import com.urlshortener.entity.Url;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UrlRepository extends JpaRepository<Url, Long> {

    List<Url> findByOwnerIdOrderByCreatedAtDesc(String ownerId);

    Optional<Url> findByCode(String code);

    Optional<Url> findByCustomAlias(String customAlias);

    boolean existsByCustomAlias(String customAlias);

    default Optional<Url> findByResolvableKey(String key) {
        return findByCustomAlias(key).or(() -> findByCode(key));
    }

    @Modifying
    @Query("delete from Url u where u.expiresAt is not null and u.expiresAt < :cutoff")
    int deleteByExpiresAtBefore(Instant cutoff);
}
