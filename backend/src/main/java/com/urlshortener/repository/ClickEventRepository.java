package com.urlshortener.repository;

import com.urlshortener.entity.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    long countByUrlId(Long urlId);

    List<ClickEvent> findByUrlIdOrderByClickedAtAsc(Long urlId);
}
