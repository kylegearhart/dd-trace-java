package datadog.trace.instrumentation.apachehttpclient5;

import datadog.trace.bootstrap.instrumentation.api.UTF8BytesString;
import datadog.trace.bootstrap.instrumentation.decorator.HttpClientDecorator;
import java.net.URI;
import java.net.URISyntaxException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;

public class ApacheHttpClientDecorator extends HttpClientDecorator<HttpRequest, HttpResponse> {

  public static final CharSequence HTTP_REQUEST = UTF8BytesString.create("http.request");
  public static final CharSequence APACHE_HTTP_CLIENT =
      UTF8BytesString.create("apache-httpclient5");
  public static final ApacheHttpClientDecorator DECORATE = new ApacheHttpClientDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"httpclient5", "apache-httpclient5", "apache-http-client5"};
  }

  @Override
  protected CharSequence component() {
    return APACHE_HTTP_CLIENT;
  }

  @Override
  protected String method(final HttpRequest httpRequest) {
    return httpRequest.getMethod();
  }

  @Override
  protected URI url(final HttpRequest request) throws URISyntaxException {
    return request.getUri();
  }

  @Override
  protected int status(final HttpResponse httpResponse) {
    return httpResponse.getCode();
  }
}
