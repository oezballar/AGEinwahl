package com.ozballar.ageinwahl.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ozballar.ageinwahl.domain.Teilnehmer;
import com.ozballar.ageinwahl.repository.TeilnehmerRepository;

class TeilnehmerServiceTests {

    @Test
    void speichertEinzelnenTeilnehmer() {
        RepositoryAufruf aufruf = new RepositoryAufruf();
        TeilnehmerRepository teilnehmerRepository = repositoryProxy(TeilnehmerRepository.class, aufruf);
        FakeEinwahlEntdeckerangebotService einwahlService = new FakeEinwahlEntdeckerangebotService();
        FakeEinwahlAGService einwahlAGService = new FakeEinwahlAGService();
        FakeEinwahlVormittagsAGService einwahlVormittagsAGService = new FakeEinwahlVormittagsAGService();
        TeilnehmerService teilnehmerService = new TeilnehmerService(teilnehmerRepository, einwahlService, einwahlAGService, einwahlVormittagsAGService);
        Teilnehmer teilnehmer = teilnehmer(1, "Max", "Muster");

        teilnehmerService.speichern(teilnehmer);

        assertEquals("save", aufruf.methodenname);
        assertEquals(List.of(teilnehmer), aufruf.argumente);
        assertEquals(List.of(teilnehmer), einwahlService.teilnehmer);
        assertEquals(List.of(teilnehmer), einwahlAGService.teilnehmer);
        assertEquals(List.of(teilnehmer), einwahlVormittagsAGService.teilnehmer);
    }

    @Test
    void liefertEinsAlsNaechsteNrWennNochKeineTeilnehmerVorhandenSind() {
        RepositoryAufruf aufruf = new RepositoryAufruf();
        TeilnehmerRepository teilnehmerRepository = repositoryProxy(TeilnehmerRepository.class, aufruf);
        TeilnehmerService teilnehmerService = new TeilnehmerService(
                teilnehmerRepository,
                new FakeEinwahlEntdeckerangebotService(),
                new FakeEinwahlAGService(),
                new FakeEinwahlVormittagsAGService()
        );

        assertEquals(1, teilnehmerService.naechsteNr());
    }

    @Test
    void ermitteltNaechsteNrAusVorhandenenTeilnehmern() {
        RepositoryAufruf aufruf = new RepositoryAufruf(List.of(
                teilnehmer(1, "Max", "Muster"),
                teilnehmer(3, "Erika", "Muster")
        ));
        TeilnehmerRepository teilnehmerRepository = repositoryProxy(TeilnehmerRepository.class, aufruf);
        TeilnehmerService teilnehmerService = new TeilnehmerService(
                teilnehmerRepository,
                new FakeEinwahlEntdeckerangebotService(),
                new FakeEinwahlAGService(),
                new FakeEinwahlVormittagsAGService()
        );

        assertEquals(4, teilnehmerService.naechsteNr());
    }

    @Test
    void speichertMehrereTeilnehmerAufEinmal() {
        RepositoryAufruf aufruf = new RepositoryAufruf();
        TeilnehmerRepository teilnehmerRepository = repositoryProxy(TeilnehmerRepository.class, aufruf);
        FakeEinwahlEntdeckerangebotService einwahlService = new FakeEinwahlEntdeckerangebotService();
        FakeEinwahlAGService einwahlAGService = new FakeEinwahlAGService();
        FakeEinwahlVormittagsAGService einwahlVormittagsAGService = new FakeEinwahlVormittagsAGService();
        TeilnehmerService teilnehmerService = new TeilnehmerService(teilnehmerRepository, einwahlService, einwahlAGService, einwahlVormittagsAGService);
        List<Teilnehmer> teilnehmer = List.of(
                teilnehmer(1, "Max", "Muster"),
                teilnehmer(2, "Erika", "Muster")
        );

        teilnehmerService.mehrereSpeichern(teilnehmer);

        assertEquals("save", aufruf.methodenname);
        assertEquals(List.of(teilnehmer(2, "Erika", "Muster")), aufruf.argumente);
        assertEquals(teilnehmer, einwahlService.teilnehmer);
        assertEquals(teilnehmer, einwahlAGService.teilnehmer);
        assertEquals(teilnehmer, einwahlVormittagsAGService.teilnehmer);
    }

    @Test
    void bietetCrudUndNamenssucheAn() {
        RepositoryAufruf aufruf = new RepositoryAufruf();
        TeilnehmerRepository teilnehmerRepository = repositoryProxy(TeilnehmerRepository.class, aufruf);
        TeilnehmerService teilnehmerService = new TeilnehmerService(teilnehmerRepository, new FakeEinwahlEntdeckerangebotService(), new FakeEinwahlAGService(), new FakeEinwahlVormittagsAGService());

        teilnehmerService.findeNachNr(1);
        assertEquals("findById", aufruf.methodenname);
        assertEquals(List.of(1), aufruf.argumente);

        teilnehmerService.findeNachVornameUndName("Max", "Muster");
        assertEquals("findByVornameAndName", aufruf.methodenname);
        assertEquals(List.of("Max", "Muster"), aufruf.argumente);

        teilnehmerService.findeAlle();
        assertEquals("findAll", aufruf.methodenname);
        assertEquals(List.of(), aufruf.argumente);

        teilnehmerService.loescheNachNr(1);
        assertEquals("deleteById", aufruf.methodenname);
        assertEquals(List.of(1), aufruf.argumente);
    }

    @SuppressWarnings("unchecked")
    private static <T> T repositoryProxy(Class<T> repositoryTyp, RepositoryAufruf aufruf) {
        return (T) Proxy.newProxyInstance(
                repositoryTyp.getClassLoader(),
                new Class<?>[]{repositoryTyp},
                (proxy, method, args) -> {
                    aufruf.methodenname = method.getName();
                    aufruf.argumente = args == null ? List.of() : Arrays.asList(args);

                    if (method.getReturnType().equals(Optional.class)) {
                        return Optional.empty();
                    }
                    if (Iterable.class.isAssignableFrom(method.getReturnType())) {
                        return args == null || args.length == 0 ? aufruf.vorhandeneTeilnehmer : args[0];
                    }
                    if (method.getReturnType().equals(Void.TYPE)) {
                        return null;
                    }
                    return args == null || args.length == 0 ? null : args[0];
                }
        );
    }

    private static Teilnehmer teilnehmer(Integer nr, String vorname, String name) {
        return new Teilnehmer(nr, vorname, name, "2a");
    }

    private static class RepositoryAufruf {
        private String methodenname;
        private List<Object> argumente;
        private List<Teilnehmer> vorhandeneTeilnehmer = List.of();

        private RepositoryAufruf() {
        }

        private RepositoryAufruf(List<Teilnehmer> vorhandeneTeilnehmer) {
            this.vorhandeneTeilnehmer = vorhandeneTeilnehmer;
        }
    }

    private static class FakeEinwahlEntdeckerangebotService extends EinwahlEntdeckerangebotService {
        private List<Teilnehmer> teilnehmer = List.of();

        private FakeEinwahlEntdeckerangebotService() {
            super(null, null, null);
        }

        @Override
        public void erstelleEintraegeFuerTeilnehmer(Teilnehmer teilnehmer) {
            this.teilnehmer = java.util.stream.Stream.concat(this.teilnehmer.stream(), java.util.stream.Stream.of(teilnehmer)).toList();
        }
    }

    private static class FakeEinwahlAGService extends EinwahlAGService {
        private List<Teilnehmer> teilnehmer = List.of();

        private FakeEinwahlAGService() {
            super(null, null, null);
        }

        @Override
        public void erstelleEintraegeFuerTeilnehmer(Teilnehmer teilnehmer) {
            this.teilnehmer = java.util.stream.Stream.concat(this.teilnehmer.stream(), java.util.stream.Stream.of(teilnehmer)).toList();
        }
    }

    private static class FakeEinwahlVormittagsAGService extends EinwahlVormittagsAGService {
        private List<Teilnehmer> teilnehmer = List.of();

        private FakeEinwahlVormittagsAGService() {
            super(null, null, null);
        }

        @Override
        public void erstelleEintraegeFuerTeilnehmer(Teilnehmer teilnehmer) {
            this.teilnehmer = java.util.stream.Stream.concat(this.teilnehmer.stream(), java.util.stream.Stream.of(teilnehmer)).toList();
        }
    }
}
