package ch.so.agi.lsp.interlis;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.TransferDescription;
import org.junit.jupiter.api.Test;

class InterlisDocxExporterTest {

    @Test
    void createsNonEmptyDocxFromEmptyTransferDescription() throws Exception {
        TransferDescription td = new TransferDescription() {
            @Override
            public Model[] getModelsFromLastFile() {
                return new Model[0];
            }
        };

        byte[] bytes = InterlisDocxExporter.renderDocx(td, "Example");
        assertNotNull(bytes);
        assertTrue(bytes.length > 0, "Expected exporter to produce non-empty DOCX");
    }
}
