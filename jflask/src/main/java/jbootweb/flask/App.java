package jbootweb.flask;

import com.sun.net.httpserver.HttpHandler;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Hashtable;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;

import jbootweb.util.Log;
import jbootweb.util.http.AbstractResourceHandler;
import jbootweb.util.http.ContentTypeProvider;
import jbootweb.util.http.DefaultContentTypeProvider;
import jbootweb.util.http.FileHandler;
import jbootweb.util.http.ResourceHandler;
import jbootweb.util.http.WebServer;

/**
 * Encapsulates the server side of a web app: an HTTP server and some route
 * handlers.
 * <p>
 * The App can be extended with some handlers:
 *
 * <pre>
 * public class MyApp extends App {
 *   &#064;Route(value = &quot;/hello/:name&quot;)
 *   public String hello(String name) {
 *     return &quot;Hello &quot; + name;
 *   }
 * }
 * ...
 * new MyApp().start()
 * </pre>
 *
 * Or the App can be extended by calling scan():
 *
 * <pre>
 * public class MyApp {
 *   &#064;Route(value = &quot;/hello/:name&quot;)
 *   public String hello(String name) {
 *     return &quot;Hello &quot; + name;
 *   }
 * }
 * ...
 * App app = new App()
 * app.scan(new MyApp());
 * app.start();
 * </pre>
 *
 * @author pcdv
 */
public class App {

  private int port = 8080;

  private ExecutorService pool;

  private WebServer srv;

  private final Map<String, HttpHandler> handlers = new Hashtable<>();

  private ContentTypeProvider mime = new DefaultContentTypeProvider();

  public App() {
    // in case we are extended by a subclass with annotations
    scan(this);
  }

  public void setPort(int port) {
    checkNotStarted();
    this.port = port;
  }

  public void setExecutorService(ExecutorService pool) {
    checkNotStarted();
    this.pool = pool;
  }

  private void checkNotStarted() {
    if (srv != null)
      throw new IllegalStateException("Already started");
  }

  /**
   * Scans specified object for route handlers.
   *
   * @param obj
   * @see Route
   */
  public void scan(Object obj) {
    for (Method method : obj.getClass().getMethods()) {
      Route ann = method.getAnnotation(Route.class);
      if (ann != null) {
        String route = ann.value();
        String verb = ann.method();
        addHandler(route, verb, method, obj);
      }
    }
  }

  private void addHandler(String route, String verb, Method m, Object obj) {
    String[] tok = route.split("/+");

    // split the static and dynamic part of the route (i.e. /app/hello/:name =>
    // "/app/hello" + "/:name")
    StringBuilder root = new StringBuilder(80);
    StringBuilder rest = new StringBuilder(80);
    int i = 0;
    for (; i < tok.length; i++) {
      if (tok[i].isEmpty())
        continue;
      if (tok[i].startsWith(":") || tok[i].startsWith("*"))
        break;
      root.append('/').append(tok[i]);
    }

    for (; i < tok.length; i++) {
      rest.append('/').append(tok[i]);
    }

    getContext(root.toString()).addHandler(rest.toString(), verb, m, obj);
  }

  /**
   * Gets or creates a Context for specified root URI.
   */
  private Context getContext(String rootURI) {
    HttpHandler c = handlers.get(rootURI);

    if (c == null) {
      Log.info("Creating context for " + rootURI);
      handlers.put(rootURI, c = new Context(rootURI));
    }
    else if (!(c instanceof Context))
      throw new IllegalStateException("A handler is already registered for: "
                                      + rootURI);
    return (Context) c;
  }

  public void start() throws IOException {
    srv = new WebServer(port, pool);
    srv.setContentTypeProvider(mime);
    for (Entry<String, HttpHandler> e : handlers.entrySet())
      srv.addHandler(e.getKey(), e.getValue());
  }

  public int getPort() {
    // retrieves the actual port even if port 0 was given (for testing)
    if (srv != null)
      return srv.getPort();
    return port;
  }

  public void destroy() {
    srv.close();
  }

  /**
   * Serves the contents of a given path (which may be a directory on the file
   * system or nested in a jar from the classpath) from a given root URI.
   *
   * @param rootURI
   * @param localPath NB: should end with a '/'
   * @return
   * @return this
   */
  public App servePath(String rootURI, String path) {
    File file = new File(path);
    AbstractResourceHandler h;
    if (file.exists() && file.isDirectory())
      h = new FileHandler(mime, rootURI, path);
    else
      h = new ResourceHandler(mime, rootURI, path);

    handlers.put(rootURI, h);
    if (srv != null)
      srv.addHandler(rootURI, h);

    return this;
  }

  public void setContentTypeProvider(ContentTypeProvider mime) {
    this.mime = mime;
  }
}