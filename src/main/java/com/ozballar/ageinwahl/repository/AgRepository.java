package com.ozballar.ageinwahl.repository;

import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

import com.ozballar.ageinwahl.domain.Ag;

public interface AgRepository extends CrudRepository<Ag, Integer> {

    Optional<Ag> findByTitel(String titel);
}
