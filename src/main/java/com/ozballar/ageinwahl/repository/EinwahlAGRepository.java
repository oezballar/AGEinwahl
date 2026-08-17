package com.ozballar.ageinwahl.repository;

import org.springframework.data.repository.CrudRepository;

import com.ozballar.ageinwahl.domain.EinwahlAG;

public interface EinwahlAGRepository extends CrudRepository<EinwahlAG, Integer> {

    void deleteByTeilnehmerId(Integer teilnehmerId);
}
