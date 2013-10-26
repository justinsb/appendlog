package org.robotninjas.barged;

import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.google.common.base.Predicate;
import com.google.common.util.concurrent.RateLimiter;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;

import javax.annotation.Nullable;
import javax.ws.rs.core.MediaType;
import java.nio.ByteBuffer;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import static com.github.rholder.retry.StopStrategies.neverStop;
import static com.github.rholder.retry.WaitStrategies.fixedWait;
import static com.google.common.base.Predicates.not;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.robotninjas.barged.TestClient.SuccessPredicate.Success;

public class TestClient {

  private static final String BASE_URL = "http://localhost:9991/";

  public static void main(String... args) {

    Client client = Client.create();
    client.setFollowRedirects(true);

    RateLimiter limiter = RateLimiter.create(10000);

    Retryer<ClientResponse> retryer =
      RetryerBuilder.<ClientResponse>newBuilder()
      .retryIfResult(not(Success))
      .withStopStrategy(neverStop())
      .withWaitStrategy(fixedWait(2, SECONDS))
      .build();

    for (long i = 0; i < 100000; i++) {

      limiter.acquire();

      System.out.println("Sending key: " + i);

      try {

        retryer.call(new PutCommand(client, BASE_URL, i));

      } catch (ExecutionException e) {
        e.printStackTrace();
      } catch (RetryException e) {
        e.printStackTrace();
      }

    }

  }

  private static final class PutCommand implements Callable<ClientResponse> {

    private final Client client;
    private final String url;
    private final long v;
    private final ByteBuffer buffer = ByteBuffer.allocate(8);

    private PutCommand(Client client, String url, long v) {
      this.client = client;
      this.url = url;
      this.v = v;
    }

    @Override
    public ClientResponse call() throws Exception {
      return client.resource(url)
        .path(Long.toString(v))
        .entity(buffer.array(), MediaType.APPLICATION_OCTET_STREAM_TYPE)
        .post(ClientResponse.class);
    }

  }

  static enum SuccessPredicate implements Predicate<ClientResponse> {

    Success;

    @Override
    public boolean apply(@Nullable ClientResponse input) {
      return input.getStatus() == 200;
    }

  }

}
