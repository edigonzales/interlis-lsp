package ch.so.agi.lsp.interlis.glsp;

/**
 * Connection details for the embedded GLSP websocket server.
 */
public class GlspEndpoint {
    private String protocol;
    private String host;
    private int port;
    private String path;
    private String diagramType;

    public GlspEndpoint() {
    }

    public GlspEndpoint(String protocol, String host, int port, String path, String diagramType) {
        this.protocol = protocol;
        this.host = host;
        this.port = port;
        this.path = path;
        this.diagramType = diagramType;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getDiagramType() {
        return diagramType;
    }

    public void setDiagramType(String diagramType) {
        this.diagramType = diagramType;
    }
}
