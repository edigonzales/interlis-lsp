package ch.so.agi.lsp.interlis.workspace;

/** A rendering of a saved compiler snapshot, without starting another compilation. */
public record MermaidDiagram(String source, boolean hideEmptyMembersBox, String status) {}
