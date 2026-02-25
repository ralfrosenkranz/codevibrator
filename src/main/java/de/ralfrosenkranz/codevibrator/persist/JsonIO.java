package de.ralfrosenkranz.codevibrator.persist;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class JsonIO {
    static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private JsonIO() {}

    static <T> T read(Path path, Class<T> type) throws IOException {
        return MAPPER.readValue(Files.readAllBytes(path), type);
    }

    static void write(Path path, Object value) throws IOException {
        Files.createDirectories(path.getParent());
        MAPPER.writeValue(path.toFile(), value);
    }
}
