package com.carenest.service.account.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TemporaryPasswordGeneratorTest {

    private final TemporaryPasswordGenerator generator = new TemporaryPasswordGenerator();

    @Test
    void generatesTenCharactersWithUpperLowerDigitAndNoAmbiguousCharacters() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String password = generator.generate();
            assertThat(password)
                    .hasSize(10)
                    .matches(".*[A-Z].*")
                    .matches(".*[a-z].*")
                    .matches(".*[2-9].*");
            assertThat(password).doesNotContainAnyWhitespaces().matches("[^0O1lI]*");
            seen.add(password);
        }
        assertThat(seen).hasSizeGreaterThan(195);
    }
}
