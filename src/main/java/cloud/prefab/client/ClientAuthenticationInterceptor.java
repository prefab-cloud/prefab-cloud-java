package cloud.prefab.client;

import io.grpc.*;

public class ClientAuthenticationInterceptor implements ClientInterceptor {

  public static final Metadata.Key<String> CUSTOM_HEADER_KEY =
      Metadata.Key.of("auth", Metadata.ASCII_STRING_MARSHALLER);

  public static final Metadata.Key<String> CLIENT_HEADER_KEY =
      Metadata.Key.of("client", Metadata.ASCII_STRING_MARSHALLER);

  private String apikey;

  public ClientAuthenticationInterceptor(String apikey) {
    this.apikey = apikey;
  }

  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> methodDescriptor, CallOptions callOptions, Channel channel) {

    return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(channel.newCall(methodDescriptor, callOptions)) {
      @Override
      public void start(Listener<RespT> responseListener, Metadata headers) {
        headers.put(CLIENT_HEADER_KEY, "prefab-cloud-java.0.1.0");
        headers.put(CUSTOM_HEADER_KEY, apikey);
        super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(responseListener) {
        }, headers);
      }
    };
  }

}
