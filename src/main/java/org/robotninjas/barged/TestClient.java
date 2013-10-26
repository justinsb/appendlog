package org.robotninjas.barged;

import com.google.common.util.concurrent.RateLimiter;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

import javax.ws.rs.core.MediaType;
import java.nio.ByteBuffer;

public class TestClient {

  private static final String BASE_URL = "http://localhost:9991/";

  public static void main(String... args) {

    Client client = Client.create();
    client.setFollowRedirects(true);

    RateLimiter limiter = RateLimiter.create(10000);
    for (long i = 0; i < 100000; i++) {
      limiter.acquire();
      ByteBuffer buffer = ByteBuffer.allocate(8);
      buffer.putLong(i).rewind();
      WebResource webResource = client.resource(BASE_URL);
      ClientResponse response = webResource.path(Long.toString(i))
        .entity(buffer.array(), MediaType.APPLICATION_OCTET_STREAM_TYPE)
        .post(ClientResponse.class);

      System.out.println(response.getStatus());
    }


  }

}
