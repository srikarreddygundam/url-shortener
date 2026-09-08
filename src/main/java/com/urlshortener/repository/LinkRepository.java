package com.urlshortener.repository;

import com.urlshortener.domain.Link;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LinkRepository extends JpaRepository<Link, Long> {

  Optional<Link> findByCode(String code);
}
