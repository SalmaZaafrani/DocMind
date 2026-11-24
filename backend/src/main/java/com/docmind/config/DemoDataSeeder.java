package com.docmind.config;

import java.util.Arrays;
import java.util.Comparator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.docmind.auth.User;
import com.docmind.auth.UserRepository;
import com.docmind.document.DocumentRepository;
import com.docmind.document.DocumentService;

/** Crée le compte de démonstration et lui ajoute trois documents d'exemple. */
@Component
public class DemoDataSeeder implements CommandLineRunner {

    public static final String DEMO_EMAIL = "demo@docmind.app";
    public static final String DEMO_PASSWORD = "demo1234";

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final boolean enabled;
    private final UserRepository users;
    private final DocumentRepository documents;
    private final DocumentService documentService;
    private final PasswordEncoder encoder;

    public DemoDataSeeder(@Value("${docmind.demo.enabled}") boolean enabled, UserRepository users,
                          DocumentRepository documents, DocumentService documentService, PasswordEncoder encoder) {
        this.enabled = enabled;
        this.users = users;
        this.documents = documents;
        this.documentService = documentService;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        if (!enabled) {
            return;
        }
        User demo = users.findByEmailIgnoreCase(DEMO_EMAIL)
                .orElseGet(() -> users.save(new User(DEMO_EMAIL, "Compte démo", encoder.encode(DEMO_PASSWORD))));
        if (documents.countByOwnerId(demo.getId()) > 0) {
            return;
        }
        try {
            Resource[] samples = new PathMatchingResourcePatternResolver().getResources("classpath:samples/*.md");
            Arrays.sort(samples, Comparator.comparing(Resource::getFilename, Comparator.nullsLast(String::compareTo)));
            for (Resource sample : samples) {
                byte[] data = sample.getInputStream().readAllBytes();
                documentService.ingest(demo.getId(), sample.getFilename(), "text/markdown", data);
            }
            log.info("Compte démo prêt : {} / {} ({} documents)", DEMO_EMAIL, DEMO_PASSWORD, samples.length);
        } catch (Exception e) {
            log.warn("Documents de démonstration non importés : {}", e.getMessage());
        }
    }
}
