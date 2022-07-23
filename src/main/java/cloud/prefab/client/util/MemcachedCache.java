package cloud.prefab.client.util;

import net.spy.memcached.MemcachedClientIF;

public class MemcachedCache implements Cache {

  private final MemcachedClientIF memcachedClientIF;

  public MemcachedCache(MemcachedClientIF memcachedClient) {
    this.memcachedClientIF = memcachedClient;
  }

  @Override
  public byte[] get(String s) {
    return (byte[]) memcachedClientIF.get(s);
  }

  @Override
  public void set(String key, int expiryInSeconds, byte[] bytes) {
    memcachedClientIF.set(key, expiryInSeconds, bytes);
  }
}
