package skaro.pokeapi;

import io.netty.resolver.DefaultAddressResolverGroup;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

@Configuration
@Import(PokeApiReactorCachingConfiguration.class)
@EnableCaching
public class MyPokeApiReactorCachingConfiguration {

	// 1. 定义缓存管理器 (使用简单的内存缓存)
	@Bean
	public CacheManager cacheManager() {
		return new ConcurrentMapCacheManager();
	}

	@Bean
	public ConnectionProvider connectionProvider() {
	    return ConnectionProvider.builder("Auto refresh & no connection limit")
		    .maxIdleTime(Duration.ofSeconds(10))
		    .maxConnections(500)
		    .pendingAcquireMaxCount(-1)
		    .build();
	}

	@Bean
	public HttpClient httpClient(ConnectionProvider connectionProvider) {
		return HttpClient.create(connectionProvider)
                  .compress(true)
                  .resolver(DefaultAddressResolverGroup.INSTANCE);
	}
}