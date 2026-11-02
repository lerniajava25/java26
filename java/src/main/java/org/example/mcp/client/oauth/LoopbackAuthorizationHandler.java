package org.example.mcp.client.oauth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.awt.*;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Desktop-style handler: opens the system browser and receives the redirect on {@code http://127.0.0.1:<port><path>}.
 * <p>
 * Pass a fixed port if the redirect URI must be known in advance (pre-registered client, Client ID Metadata
 * Document); port 0 picks a free port, which is fine with dynamic client registration.
 */
public final class LoopbackAuthorizationHandler implements AuthorizationHandler, AutoCloseable {

    private final HttpServer server;
    private final String path;
    private final URI redirectUri;
    private final Consumer<URI> browserOpener;
    private final Duration timeout;

    private CompletableFuture<Map<String, String>> pending;   // guarded by this
    private String expectedState;                             // guarded by this

    public LoopbackAuthorizationHandler() throws IOException {
        this(0, "/callback");
    }

    public LoopbackAuthorizationHandler(int port, String path) throws IOException {
        this(port, path, LoopbackAuthorizationHandler::openSystemBrowser, Duration.ofMinutes(5));
    }

    public LoopbackAuthorizationHandler(int port, String path, Consumer<URI> browserOpener, Duration timeout) throws IOException {
        if (!path.startsWith("/")) throw new IllegalArgumentException("path must start with '/'");
        this.path = path;
        this.browserOpener = browserOpener;
        this.timeout = timeout;
        // Bind to the loopback address only: nobody else on the network may reach the callback.
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 0);
        this.server.createContext("/", this::handle);
        this.server.start();
        this.redirectUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    @Override
    public URI redirectUri() {
        return redirectUri;
    }

    @Override
    public Map<String, String> authorize(URI authorizationUri, String state) throws IOException, InterruptedException {
        CompletableFuture<Map<String, String>> future = new CompletableFuture<>();
        synchronized (this) {
            if (pending != null) throw new IllegalStateException("A sign-in is already in progress");
            pending = future;
            expectedState = state;
        }
        try {
            browserOpener.accept(authorizationUri);
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new OAuthException("Timed out waiting for the user to finish signing in", null, e);
        } catch (ExecutionException e) {
            throw new IOException(e.getCause());
        } finally {
            synchronized (this) {
                pending = null;
                expectedState = null;
            }
        }
    }

    private void handle(HttpExchange ex) throws IOException {
        try {
            if (!path.equals(ex.getRequestURI().getPath())) {
                respond(ex, 404, "Not found");
                return;
            }
            if (!"GET".equals(ex.getRequestMethod())) {
                respond(ex, 405, "Method not allowed");
                return;
            }
            Map<String, String> params = parseQuery(ex.getRequestURI().getRawQuery());
            CompletableFuture<Map<String, String>> f;
            String expected;
            synchronized (this) {
                f = pending;
                expected = expectedState;
            }
            if (params == null) {
                respond(ex, 400, "Invalid request.");
                return;
            }
            if (f == null || expected == null) {
                respond(ex, 400, "No sign-in is in progress.");
                return;
            }
            String st = params.get("state");
            // Not an answer to our request (stale tab, other local program): ignore it and keep waiting.
            if (st == null || !MessageDigest.isEqual(st.getBytes(UTF_8), expected.getBytes(UTF_8))) {
                respond(ex, 400, "Invalid request.");
                return;
            }
            if (f.complete(params))
                respond(ex, 200, "Sign-in finished. You can close this window and return to the application.");
            else respond(ex, 400, "This sign-in link has already been used.");
        } finally {
            ex.close();
        }
    }

    /**
     * Returns null if a parameter occurs twice (RFC 6749 forbids that).
     */
    static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> out = new HashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) return out;
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            String k = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), UTF_8);
            String v = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), UTF_8);
            if (out.put(k, v) != null) return null;
        }
        return out;
    }

    private static void respond(HttpExchange ex, int status, String text) throws IOException {
        byte[] body = ("<!doctype html><meta charset=utf-8><title>MCP sign-in</title><p>" + text + "</p>").getBytes(UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, body.length);
        ex.getResponseBody().write(body);
    }

    /**
     * Opens the default browser, or prints the URL if there is no desktop (headless, SSH, container).
     */
    static void openSystemBrowser(URI uri) {
        try {
            if (!GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri);
                return;
            }
        } catch (Exception | LinkageError ignored) {
            // fall through to printing the URL
        }
        System.err.println("Open this URL in a browser to sign in:\n" + uri);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
