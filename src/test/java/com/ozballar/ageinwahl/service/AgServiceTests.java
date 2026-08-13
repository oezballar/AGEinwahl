package com.ozballar.ageinwahl.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ozballar.ageinwahl.domain.Ag;
import com.ozballar.ageinwahl.repository.AgRepository;

class AgServiceTests {

    @Test
    void speichertEinzelneAg() {
        RepositoryAufruf aufruf = new RepositoryAufruf();
        AgRepository agRepository = repositoryProxy(AgRepository.class, aufruf);
        FakeEinwahlEntdeckerangebotService einwahlService = new FakeEinwahlEntdeckerangebotService();
        FakeEinwahlAGService einwahlAGService = new FakeEinwahlAGService();
        FakeEinwahlVormittagsAGService einwahlVormittagsAGService = new FakeEinwahlVormittagsAGService();
        AgService agService = new AgService(agRepository, einwahlService, einwahlAGService, einwahlVormittagsAGService);
        Ag ag = ag("Sport");

        agService.speichern(ag);

        assertEquals("save", aufruf.methodenname);
        assertEquals(List.of(ag), aufruf.argumente);
        assertEquals(List.of(ag), einwahlService.ags);
        assertEquals(List.of(ag), einwahlAGService.ags);
        assertEquals(List.of(ag), einwahlVormittagsAGService.ags);
    }

    @Test
    void speichertMehrereAgsAufEinmal() {
        RepositoryAufruf aufruf = new RepositoryAufruf();
        AgRepository agRepository = repositoryProxy(AgRepository.class, aufruf);
        FakeEinwahlEntdeckerangebotService einwahlService = new FakeEinwahlEntdeckerangebotService();
        FakeEinwahlAGService einwahlAGService = new FakeEinwahlAGService();
        FakeEinwahlVormittagsAGService einwahlVormittagsAGService = new FakeEinwahlVormittagsAGService();
        AgService agService = new AgService(agRepository, einwahlService, einwahlAGService, einwahlVormittagsAGService);
        List<Ag> ags = List.of(ag("Sport"), ag("Musik"));

        agService.mehrereSpeichern(ags);

        assertEquals("save", aufruf.methodenname);
        assertEquals(List.of(ag("Musik")), aufruf.argumente);
        assertEquals(ags, einwahlService.ags);
        assertEquals(ags, einwahlAGService.ags);
        assertEquals(ags, einwahlVormittagsAGService.ags);
    }

    @Test
    void bietetCrudUndTitelsucheAn() {
        RepositoryAufruf aufruf = new RepositoryAufruf();
        AgRepository agRepository = repositoryProxy(AgRepository.class, aufruf);
        AgService agService = new AgService(agRepository, new FakeEinwahlEntdeckerangebotService(), new FakeEinwahlAGService(), new FakeEinwahlVormittagsAGService());

        agService.findeNachId(1);
        assertEquals("findById", aufruf.methodenname);
        assertEquals(List.of(1), aufruf.argumente);

        agService.findeNachTitel("Sport");
        assertEquals("findByTitel", aufruf.methodenname);
        assertEquals(List.of("Sport"), aufruf.argumente);

        agService.findeAlle();
        assertEquals("findAll", aufruf.methodenname);
        assertEquals(List.of(), aufruf.argumente);

        agService.loescheNachId(1);
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
                        return args == null || args.length == 0 ? List.of() : args[0];
                    }
                    if (method.getReturnType().equals(Void.TYPE)) {
                        return null;
                    }
                    return args == null || args.length == 0 ? null : args[0];
                }
        );
    }

    private static Ag ag(String titel) {
        return new Ag(
                null,
                Ag.Wochentag.MONTAG,
                Ag.Zeit.NACHMITTAG,
                Ag.Kategorie.AG,
                titel,
                "Verantwortlicher",
                "Ort",
                10,
                List.of(1, 2)
        );
    }

    private static class RepositoryAufruf {
        private String methodenname;
        private List<Object> argumente;
    }

    private static class FakeEinwahlEntdeckerangebotService extends EinwahlEntdeckerangebotService {
        private List<Ag> ags = List.of();

        private FakeEinwahlEntdeckerangebotService() {
            super(null, null, null);
        }

        @Override
        public void erstelleEintraegeFuerAg(Ag ag) {
            ags = java.util.stream.Stream.concat(ags.stream(), java.util.stream.Stream.of(ag)).toList();
        }
    }

    private static class FakeEinwahlAGService extends EinwahlAGService {
        private List<Ag> ags = List.of();

        private FakeEinwahlAGService() {
            super(null, null, null);
        }

        @Override
        public void erstelleEintraegeFuerAg(Ag ag) {
            ags = java.util.stream.Stream.concat(ags.stream(), java.util.stream.Stream.of(ag)).toList();
        }
    }

    private static class FakeEinwahlVormittagsAGService extends EinwahlVormittagsAGService {
        private List<Ag> ags = List.of();

        private FakeEinwahlVormittagsAGService() {
            super(null, null, null);
        }

        @Override
        public void erstelleEintraegeFuerAg(Ag ag) {
            ags = java.util.stream.Stream.concat(ags.stream(), java.util.stream.Stream.of(ag)).toList();
        }
    }
}
