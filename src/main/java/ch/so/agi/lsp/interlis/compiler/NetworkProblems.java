package ch.so.agi.lsp.interlis.compiler;

import ch.ehi.basics.logging.EhiLogger;
import ch.ehi.basics.logging.LogListener;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Conservative classification: a missing model or file alone is never a network diagnosis. */
public final class NetworkProblems {
    private NetworkProblems() {}

    public enum Kind { TIMEOUT, DNS, CONNECTION, TLS, HTTP }

    public record Problem(Kind kind, String target, Integer httpStatus) {
        public String describe() {
            String destination = target == null ? "the remote server" : target;
            return switch (kind) {
                case TIMEOUT -> "The request to " + destination + " timed out. Check the server, network, VPN or proxy.";
                case DNS -> "The server name for " + destination + " could not be resolved. Check the address, DNS, network or VPN.";
                case CONNECTION -> "A connection to " + destination + " could not be established. Check the server, network, VPN or proxy.";
                case TLS -> "A secure connection to " + destination + " could not be established. Check the server certificate and proxy settings.";
                case HTTP -> "The request to " + destination + " returned HTTP " + httpStatus + ". Check the address and server access.";
            };
        }
    }

    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\\\"']+");
    private static final Pattern HTTP = Pattern.compile("Server returned HTTP response code: ([45]\\d\\d)\\b");

    public static Problem fromText(String text, String fallbackTarget) {
        if (text == null) return null;
        Kind kind;
        Integer status = null;
        if (text.contains("java.net.SocketTimeoutException") || text.contains("java.net.http.HttpTimeoutException")) kind = Kind.TIMEOUT;
        else if (text.contains("java.net.UnknownHostException") || text.contains("java.nio.channels.UnresolvedAddressException")) kind = Kind.DNS;
        else if (text.contains("java.net.ConnectException") || text.contains("java.net.NoRouteToHostException")
                || (text.contains("java.net.SocketException") && (text.contains("Connection reset")
                || text.contains("Broken pipe") || text.contains("Network is unreachable")))) kind = Kind.CONNECTION;
        else if (text.contains("javax.net.ssl.SSL") || text.contains("java.security.cert.CertificateException")
                || text.contains("java.security.cert.CertPathValidatorException")
                || text.contains("sun.security.provider.certpath.SunCertPathBuilderException")) kind = Kind.TLS;
        else {
            var match = HTTP.matcher(text);
            if (!match.find()) return null;
            kind = Kind.HTTP;
            status = Integer.valueOf(match.group(1));
        }
        return new Problem(kind, target(text, fallbackTarget), status);
    }

    public static Problem fromException(Throwable failure, String fallbackTarget) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
            Kind kind = null;
            if (cause instanceof SocketTimeoutException || cause instanceof java.net.http.HttpTimeoutException) kind = Kind.TIMEOUT;
            else if (cause instanceof UnknownHostException || cause instanceof java.nio.channels.UnresolvedAddressException) kind = Kind.DNS;
            else if (cause instanceof ConnectException || cause instanceof NoRouteToHostException) kind = Kind.CONNECTION;
            else if (cause instanceof java.net.SocketException && fromText(cause.toString(), null) != null) kind = Kind.CONNECTION;
            else if (cause instanceof SSLException || cause instanceof CertificateException
                    || cause instanceof java.security.cert.CertPathValidatorException) kind = Kind.TLS;
            if (kind != null) return new Problem(kind, target(cause.getMessage(), fallbackTarget), null);
            Problem logged = fromText(cause.getMessage(), fallbackTarget);
            if (logged != null) return logged;
        }
        return null;
    }

    private static String target(String text, String fallback) {
        Set<String> urls = new LinkedHashSet<>();
        if (text != null) {
            var matcher = URL.matcher(text);
            while (matcher.find()) urls.add(matcher.group().replaceFirst("[),.;]+$", ""));
        }
        if (urls.size() == 1) return urls.iterator().next();
        return urls.isEmpty() ? fallback : null;
    }

    public static String summary(List<Problem> problems) {
        StringBuilder text = new StringBuilder();
        for (Problem problem : new LinkedHashSet<>(problems)) {
            text.append("Network warning: ").append(problem.describe()).append(System.lineSeparator());
        }
        return text.toString();
    }

    /** EhiLogger is global; only events emitted by this operation's thread belong to this collector. */
    public static final class Collector implements AutoCloseable {
        private final Thread owner = Thread.currentThread();
        private final String fallbackTarget;
        private final Set<Problem> problems = new LinkedHashSet<>();
        private final LogListener listener;

        public Collector(String fallbackTarget) {
            this.fallbackTarget = fallbackTarget;
            listener = event -> {
                if (Thread.currentThread() != owner) return;
                Problem problem = fromException(event.getException(), target(event.getEventMsg(), fallbackTarget));
                if (problem == null) problem = fromText(event.getEventMsg(), fallbackTarget);
                if (problem != null) problems.add(problem);
            };
            EhiLogger.getInstance().addListener(listener);
        }

        public void record(Throwable failure) {
            Problem problem = fromException(failure, fallbackTarget);
            if (problem != null) problems.add(problem);
        }

        public List<Problem> problems() { return List.copyOf(problems); }

        @Override public void close() { EhiLogger.getInstance().removeListener(listener); }
    }
}
