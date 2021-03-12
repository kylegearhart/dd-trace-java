package datadog.trace.instrumentation.netty40.client;

import static io.netty.handler.codec.http.HttpHeaders.Names.HOST;

import datadog.trace.bootstrap.instrumentation.api.UTF8BytesString;
import datadog.trace.bootstrap.instrumentation.decorator.HttpClientDecorator;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import java.net.URI;
import java.net.URISyntaxException;

public class NettyHttpClientDecorator extends HttpClientDecorator<HttpRequest, HttpResponse> {

  public static final CharSequence NETTY_CLIENT = UTF8BytesString.create("netty-client");
  public static final CharSequence NETTY_CLIENT_REQUEST =
      UTF8BytesString.create("netty.client.request");

  public static final NettyHttpClientDecorator DECORATE = new NettyHttpClientDecorator("http://");
  public static final NettyHttpClientDecorator DECORATE_SECURE =
      new NettyHttpClientDecorator("https://");

  private final String uriPrefix;

  public NettyHttpClientDecorator(String uriPrefix) {
    this.uriPrefix = uriPrefix;
  }

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"netty", "netty-4.0"};
  }

  @Override
  protected CharSequence component() {
    return NETTY_CLIENT;
  }

  @Override
  protected String method(final HttpRequest httpRequest) {
    return httpRequest.getMethod().name();
  }

  @Override
  protected URI url(final HttpRequest request) throws URISyntaxException {
    if (request.getMethod().equals(HttpMethod.CONNECT)) {
      return new URI(uriPrefix + request.getUri());
    }
    final URI uri = new URI(request.getUri());
    if ((uri.getHost() == null || uri.getHost().equals("")) && request.headers().contains(HOST)) {
      return new URI(uriPrefix + request.headers().get(HOST) + request.getUri());
    } else {
      return uri;
    }
  }

  @Override
  protected int status(final HttpResponse httpResponse) {
    return httpResponse.getStatus().code();
  }
}
