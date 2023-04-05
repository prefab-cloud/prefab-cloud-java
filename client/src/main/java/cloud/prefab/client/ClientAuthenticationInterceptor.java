package cloud.prefab.client;

import cloud.prefab.client.util.MavenInfo;
import cloud.prefab.domain.GreetingServiceGrpc;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import java.util.Set;

public class ClientAuthenticationInterceptor implements ClientInterceptor {

  public static final String CUSTOM_HEADER_KEY = "auth";
  public static final Metadata.Key<String> CUSTOM_HEADER_METADATA_KEY = Metadata.Key.of(
    CUSTOM_HEADER_KEY,
    Metadata.ASCII_STRING_MARSHALLER
  );

  public static final String CLIENT_HEADER_KEY = "client";

  public static final Metadata.Key<String> CLIENT_HEADER_METADATA_KEY = Metadata.Key.of(
    "client",
    Metadata.ASCII_STRING_MARSHALLER
  );

  public static final String CLIENT_HEADER_VALUE = String.format(
    "%s.%s",
    MavenInfo.getInstance().getArtifactId(),
    MavenInfo.getInstance().getVersion()
  );

  private final String apikey;

  public ClientAuthenticationInterceptor(String apikey) {
    this.apikey = apikey;
  }

  private static final Set<String> NO_AUTH_SERVICE_NAMES = Set.of(
    GreetingServiceGrpc.SERVICE_NAME
  );

  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
    MethodDescriptor<ReqT, RespT> methodDescriptor,
    CallOptions callOptions,
    Channel channel
  ) {
    return new ForwardingClientCall.SimpleForwardingClientCall<>(
      channel.newCall(methodDescriptor, callOptions)
    ) {
      @Override
      public void start(Listener<RespT> responseListener, Metadata headers) {
        headers.put(CLIENT_HEADER_METADATA_KEY, CLIENT_HEADER_VALUE);
        if (!NO_AUTH_SERVICE_NAMES.contains(methodDescriptor.getServiceName())) {
          headers.put(CUSTOM_HEADER_METADATA_KEY, apikey);
        }
        super.start(
          new ForwardingClientCallListener.SimpleForwardingClientCallListener<>(
            responseListener
          ) {},
          headers
        );
      }
    };
  }
}
