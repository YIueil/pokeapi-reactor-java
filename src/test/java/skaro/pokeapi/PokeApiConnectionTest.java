package skaro.pokeapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import skaro.pokeapi.client.PokeApiClient;
import skaro.pokeapi.resource.Name;
import skaro.pokeapi.resource.language.Language;
import skaro.pokeapi.resource.pokemon.Pokemon;
import skaro.pokeapi.resource.pokemonspecies.PokemonSpecies;
import skaro.pokeapi.resource.version.Version;
import skaro.pokeapi.utils.locale.PokeApiLocaleUtils;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = MyPokeApiReactorCachingConfiguration.class, properties = {
        "spring.datasource.url=jdbc:sqlite:pokemon.db",
        "spring.datasource.driver-class-name=org.sqlite.JDBC"
})
class PokeApiConnectionTest {

    @Autowired
    private PokeApiClient pokeApiClient;

    @Test
    public void testJdbcSelect () {
        String sql = "SELECT * FROM sqlite_master";

        // 使用 try-with-resources 自动关闭连接、Statement 和 ResultSet
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:pokemon.db");
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {

            }

        } catch (SQLException e) {
            e.printStackTrace();
            // 实际项目中建议抛出 RuntimeException 或自定义异常
            throw new RuntimeException("查询数据库失败", e);
        }
    }

    @Test
    public void testJdbcInsert () {
        String sql = "insert into t_language (iso3166, iso639, name, official) values (?, ?, ?, ?)";

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:pokemon.db");
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, "iso3166");
            pstmt.setString(2, "iso639");
            pstmt.setString(3, "name");
            pstmt.setBoolean(4, false);

            int insertRowNumber = pstmt.executeUpdate();// 返回受影响的行数
            System.out.println("插入行: " + insertRowNumber);
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("插入数据失败", e);
        }
    }

    @Test
    public void testGetLanguage () {
        pokeApiClient.getResource(Language.class)
                .flatMapMany(languageList -> Flux.fromIterable(languageList.getResults()))
                .doOnNext(language -> {
                    System.out.println(language.getName());
                }).collectList()
                .block();
    }

    /**
     * 获取游戏世代版本信息
     */
    @Test
    public void testGetVersionInfo() {
        pokeApiClient.getResource(Version.class) // 1. 获取全部分版组列表 (分页)
                .flatMapMany(resultList -> Flux.fromIterable(resultList.getResults())) // 2. 将列表转为响应式 Flux 流
                .concatMap(namedResource -> pokeApiClient.followResource(() -> namedResource, Version.class))
                .doOnNext(version -> {
                    System.out.println(version);
                })
                .collectList() // 收集成列表以供阻塞等待
                .block(Duration.ofMinutes(1)); // 阻塞主线程直到所有数据抓取完毕，防止测试提前结束
    }
    /**
     * 测试 1：阻塞式获取 (Blocking)
     * 适合快速验证 API 是否连通，数据结构是否匹配
     */
    @Test
    void testGetPokemonBlocking() {
        // 获取皮卡丘 (ID: 25)
        Pokemon pikachu = pokeApiClient.getResource(Pokemon.class, "25").block();
        
        assertNotNull(pikachu);
        assertEquals("pikachu", pikachu.getName());
        System.out.println("成功获取数据！名称: " + pikachu.getName());
        System.out.println("基础经验值: " + pikachu.getBaseExperience());
    }

    @Test
    public void testPrintChineseInfo() {
        printChineseInfo(1);
    }

    public void printChineseInfo(int id) {
        PokemonSpecies pokemonSpecies = pokeApiClient.getResource(Pokemon.class, String.valueOf(id))
                .flatMap(p -> pokeApiClient.followResource(p::getSpecies, PokemonSpecies.class))
                .block();

        // 使用作者的工具类获取中文名
        String nameCN = PokeApiLocaleUtils.getInLocale(pokemonSpecies, "zh-Hans")
                .map(Name::getName)
                .orElse(pokemonSpecies.getName());

        // 同样的方法也可以尝试用于获取描述（如果描述类也实现了 Localizable 接口）
        System.out.println("全国图鉴 ID: " + id);
        System.out.println("中文名: " + nameCN);

    }

    /**
     * 测试 2：响应式获取 (Reactive)
     * 使用 Project Reactor 官方推荐的 StepVerifier 验证流
     */
    @Test
    void testGetPokemonReactive() {
        var pokemonMono = pokeApiClient.getResource(Pokemon.class, "bulbasaur");

        StepVerifier.create(pokemonMono)
                .assertNext(pokemon -> {
                    assertEquals("bulbasaur", pokemon.getName());
                    assertNotNull(pokemon.getTypes());
                    System.out.println("响应式验证通过，属性数量: " + pokemon.getTypes().size());
                })
                .expectComplete()
                .verify();
    }
}