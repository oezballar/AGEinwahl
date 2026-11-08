package com.ozballar.ageinwahl.service;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ozballar.ageinwahl.domain.BesuchteAg;
import com.ozballar.ageinwahl.repository.BesuchteAgRepository;

@Service
public class BesuchteAgService {

    private final BesuchteAgRepository repository;
    private final JdbcTemplate jdbcTemplate;

    public BesuchteAgService(BesuchteAgRepository repository, JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Iterable<BesuchteAg> findeAlle() {
        return repository.findAll();
    }

    public Map<Integer, Set<String>> nachTeilnehmer() {
        return StreamSupport.stream(repository.findAll().spliterator(), false)
                .collect(Collectors.groupingBy(
                        BesuchteAg::teilnehmerId,
                        Collectors.mapping(BesuchteAg::agTitel, Collectors.toSet())
                ));
    }

    @Transactional
    public void ersetzeAlle(Map<Integer, Set<String>> besuchteAgs) {
        jdbcTemplate.update("DELETE FROM besuchte_ag");
        besuchteAgs.forEach((teilnehmerId, ags) -> ags.forEach(agTitel ->
                jdbcTemplate.update(
                        "INSERT INTO besuchte_ag (teilnehmer_id, ag_titel) VALUES (?, ?)",
                        teilnehmerId, agTitel)));
    }
}
