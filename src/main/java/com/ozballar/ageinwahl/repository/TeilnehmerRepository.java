package com.ozballar.ageinwahl.repository;

import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

import com.ozballar.ageinwahl.domain.Teilnehmer;

public interface TeilnehmerRepository extends CrudRepository<Teilnehmer, Integer> {

    Optional<Teilnehmer> findByVornameAndName(String vorname, String name);
}
