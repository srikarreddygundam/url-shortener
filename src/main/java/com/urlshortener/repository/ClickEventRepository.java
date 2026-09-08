package com.urlshortener.repository;

import com.urlshortener.domain.ClickEvent;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

  long countByLinkId(Long linkId);

  Optional<ClickEvent> findTopByLinkIdOrderByOccurredAtDesc(Long linkId);
}
