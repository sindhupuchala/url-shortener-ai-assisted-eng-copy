package com.urlshortener.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "click_events")
@Getter
@Setter
@NoArgsConstructor
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "url_id", nullable = false)
    private Long urlId;

    @Column(name = "clicked_at", nullable = false)
    private Instant clickedAt;

    @Column(name = "referrer", length = 512)
    private String referrer;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    public ClickEvent(Long urlId, Instant clickedAt, String referrer, String userAgent) {
        this.urlId = urlId;
        this.clickedAt = clickedAt;
        this.referrer = referrer;
        this.userAgent = userAgent;
    }
}
