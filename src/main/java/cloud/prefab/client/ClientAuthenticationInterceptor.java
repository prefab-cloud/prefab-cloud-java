package cloud.prefab.client;

import cloud.prefab.client.util.MavenInfo;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

public class ClientAuthenticationInterceptor implements ClientInterceptor {

  public static final Metadata.Key<String> CUSTOM_HEADER_KEY = Metadata.Key.of(
    "auth",
    Metadata.ASCII_STRING_MARSHALLER
  );

  public static final Metadata.Key<String> CLIENT_HEADER_KEY = Metadata.Key.of(
    "client",
    Metadata.ASCII_STRING_MARSHALLER
  );

  private static final String CLIENT_HEADER_VALUE = String.format(
    "%s.%s",
    MavenInfo.getInstance().getArtifactId(),
    MavenInfo.getInstance().getVersion()
  );

  private final String apikey;

  public ClientAuthenticationInterceptor(String apikey) {
    this.apikey = apikey;
  }

  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
    MethodDescriptor<ReqT, RespT> methodDescriptor,
    CallOptions callOptions,
    Channel channel
  ) {
    return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
      channel.newCall(methodDescriptor, callOptions)
    ) {
      @Override
      public void start(Listener<RespT> responseListener, Metadata headers) {
        headers.put(CLIENT_HEADER_KEY, CLIENT_HEADER_VALUE);
        headers.put(CUSTOM_HEADER_KEY, apikey);
        super.start(
          new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(
            responseListener
          ) {},
          headers
        );
      }
    };
  }
}
