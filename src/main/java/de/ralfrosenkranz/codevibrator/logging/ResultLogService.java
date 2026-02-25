package de.ralfrosenkranz.codevibrator.logging;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ResultLogService {
    public Path write(Path dailyDir, String timestamp, String prefix, ResultLog log) throws IOException {
        Path path = dailyDir.resolve(prefix + "_result_" + timestamp + ".log.txt");
        Files.writeString(path, log.toText());
        return path;
    }
}
