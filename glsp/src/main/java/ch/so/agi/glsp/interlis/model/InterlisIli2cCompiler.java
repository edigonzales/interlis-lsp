package ch.so.agi.glsp.interlis.model;

import ch.interlis.ili2c.Ili2cSettings;
import ch.interlis.ili2c.config.Configuration;
import ch.interlis.ili2c.config.FileEntry;
import ch.interlis.ili2c.config.FileEntryKind;
import ch.interlis.ili2c.metamodel.TransferDescription;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Lightweight copy of the ili2c compiler helper used by the language server.
 */
final class InterlisIli2cCompiler {
    TransferDescription compile(Path file) throws IOException {
        if (file == null) {
            return null;
        }
        Ili2cSettings settings = new Ili2cSettings();
        ch.interlis.ili2c.Main.setDefaultIli2cPathMap(settings);
        settings.setIlidirs(Ili2cSettings.DEFAULT_ILIDIRS);

        Configuration cfg = new Configuration();
        cfg.addFileEntry(new FileEntry(file.toString(), FileEntryKind.ILIMODELFILE));
        cfg.setAutoCompleteModelList(true);
        cfg.setGenerateWarnings(true);

        try {
            return ch.interlis.ili2c.Main.runCompiler(cfg, settings, null);
        } catch (Exception ex) {
            throw new IOException("ili2c compilation failed", ex);
        }
    }
}
