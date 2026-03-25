package ch.so.agi.lsp.interlis.live;

public record DocumentSnapshot(String uri, String path, String text, Integer version) {
    public DocumentSnapshot {
        text = text != null ? text : "";
    }
}
