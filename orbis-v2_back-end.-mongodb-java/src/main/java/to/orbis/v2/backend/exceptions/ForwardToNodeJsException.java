package to.orbis.v2.backend.exceptions;

public class ForwardToNodeJsException extends RuntimeException {
    private final String networkEventId;
    private final String bodyJson;

    public ForwardToNodeJsException() {
        super("Forward request to Node.js worker due to empty array result");
        this.networkEventId = null;
        this.bodyJson = null;
    }

    public ForwardToNodeJsException(String networkEventId) {
        super("Forward request to Node.js worker due to network event");
        this.networkEventId = networkEventId;
        this.bodyJson = null;
    }

    public ForwardToNodeJsException(String networkEventId, String bodyJson) {
        super("Forward request to Node.js worker with body");
        this.networkEventId = networkEventId;
        this.bodyJson = bodyJson;
    }

    public String getNetworkEventId() {
        return networkEventId;
    }

    public String getBodyJson() {
        return bodyJson;
    }
}
